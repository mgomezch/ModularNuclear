# ModularNuclear refactoring backlog

From a review of the code on 2026-11-06. Items are grouped by kind and ordered by priority within each group (P0 first). Paths are relative to `src/main/java/com/gtnewhorizons/modularnuclear/`.

Decisions already made apply to every item:

- **GUI interaction goes through ModularUI 2 synced actions**, not custom packets.
- **Config matching is exact.** No fuzzy, normalized, permuted or localized-name matching. Whether explicit wildcards stay is an open question (see C1); the default is to drop them.
- **Caching stays.** It is how the mod gets its performance. The work is to make caches uniform, not fewer.
- **Pipes never tick.** The pump checks its loop on a schedule. A loop keeps running for a few seconds after a pipe breaks, until the next check finds the break, as GTNH multiblocks do between structure checks.

---

## Bugs and stability

### B1 (P0): The coolant bath eject packet runs off-thread and is unchecked (done)

**Done:** the eject is now the ModularUI 2 synced action `CoolantBathGridWidget.EJECT_ACTION`, registered in `CoolantBathGui`. The packet is gone. Verified in game: click-to-eject moves the item to the output bus and the grid updates. The bath's server-to-client slot sync, `PacketSyncCoolantBath`, is the only custom packet left. It feeds the in-world item renderer as well as the GUI, so it stays for now.

`network/PacketEjectCoolantBathSlot` calls `MTECoolantBath.ejectSlot` from `onMessage`, which in 1.7.10 runs on the Netty thread. It therefore mutates `mSlots` and the output bus while the server thread ticks the same bath, which can duplicate or lose items or crash.

It also does not check that the player is near the bath or has its GUI open. Any client can eject from any bath in its dimension, and `getTileEntity` at arbitrary coordinates can load chunks.

**Do:**

- Replace the packet with a ModularUI 2 synced action on the bath's GUI (as `ReactorGridPanel` does for the reactor). It runs on the server thread and only while that player has the GUI open.
- Delete the packet class and its registration in `CoolantLoops`.
- Check that nothing else registers custom GUI packets.

### B2 (P1): The server writes player-facing text, saves it, and uses it as logic

`util/CoolantLocalization` localizes on the server by reflecting into `StatCollector`. The localized text ends up in:

- pump status and failure reasons;
- `CrawlResult.failureReason`;
- `moderatorName` in the reactor grid sync;
- NBT (`mLoopStatus`).

`MTECoolantPump.applyCrawlResult` also detects "loop too large" with `reason.equals(CoolantLocalization.get(...))`. On a dedicated server every client sees the server's language.

**Do:**

- Carry lang keys and arguments (or enums) end to end; `setLoopStatusKey` already half-does this.
- Make conditions enum or flag values, never string comparisons.
- Localize only on the client.
- Save keys and arguments to NBT, not text.
- Remove the `StatCollector` reflection.

### B3 (P1): Broken YAML config is silently ignored (done)

**Done:** a config file that does not parse, is not a map, or holds a value of the wrong kind now throws `ConfigException` naming the file (and, for syntax errors, the line). That stops the game at startup. Config files are read and written as UTF-8. Covered by `ConfigStrictnessTest`. No leniency flag was added.


`config/ReactorDataRegistry.loadFromDir` (and the coolant loop equivalent) logs a parse error and falls back to the defaults for that file. A typo in `fuels.yaml` quietly changes balance.

**Do:** fail loudly at startup with the file, the line and the problem. At the least, refuse to start unless a config flag says otherwise. Read files as UTF-8 explicitly; `FileReader` uses the platform charset.

### B4 (P2): Too many `catch (Throwable)` blocks

There are about 192 of them, 177 routed through `Swallowed.note`, which logs once per call site. Many guard calls that cannot fail (`stack.getItem()`, `resource.getFluid()`, `Item.getIdFromItem`). Real bugs become one log line and then silent wrong behavior.

**Do:**

- Keep catches only at real optional-mod boundaries, and narrow them to the linkage errors `Swallowed.isMissingOptionalCode` already names.
- Remove the rest and let real errors surface.
- Do it package by package, one commit each.

### B5 (P2): The client never gets the server's config

NEI handlers, tooltips and GUIs read the client's local YAML. On a server with tuned configs, players see wrong numbers.

**Do:** sync the effective config (or a hash of it plus the data) from server to client on login. Make client-side lookups use the synced copy while connected and the local one in singleplayer and NEI-only contexts.

---

## Config matching

### C1 (P1): Make resource matching exact

`config/ReactorDataRegistry.matchesFluid` and `ReactorConfigData.matchesFluid` and `matchesPermutedDescriptors` match patterns against:

- registry names;
- unlocalized names, with and without the `fluid.` prefix;
- localized names, which depend on the language;
- normalized strings;
- "permuted descriptors" (stripping `state`, `excited`, `depleted`).

The registry scan around `ReactorDataRegistry` line 1050 does the same for name-to-fluid resolution. Item matching (`matchesItem`, `ModularNuclearConfig`'s pattern matcher) should be checked the same way.

**Do:**

- Match fluids by exact registry name, and items by exact `modid:name` plus meta.
- Remove normalization, descriptor permutation, unlocalized-name matching and localized-name matching.
- Report config entries that match nothing at load (a warning, or an error under B3's strictness).
- Wildcards (open question; default to removing): the defaults use them in `ModularNuclearConfig` (`IC2:reactorVent*`, `IC2:reactorHeatSwitch*`, `gregtech:gt.reactorUranium*`, `*depleted*`, `*fuelrodempty*`, `gregtech:gt.reactorCoolant*`, ...). `tiers.yaml` uses `'*'` to mean "every coolant". If they go, expand the defaults into exact lists, and replace `'*'` with an explicit `all: true` or by leaving the list out.
- Update the v2 manual's config section.

---

## Performance

### P1 (P1): Reactor grid GUI sync is all-or-nothing

`ReactorGridPanel` registers a `GenericSyncValue` over `MTENuclearReactor.collectGridSyncData`. While the GUI is open, that builds a full grid snapshot every tick for each viewer, copying every `ItemStack`. Flux values change every cycle, so the whole grid, item NBT included, is resent about every 5 ticks.

**Do:**

- Split it into a slow part (items, fluids, rod types and names), synced when it changes, and a fast part (temperatures, fluxes, insertion), sent as a compact numeric array.
- Better still, sync per cell with dirty flags.
- Fix `ReactorGridCellData.equals`: it uses a tolerance, so it is not transitive and disagrees with `hashCode`. Use quantized values instead.

### P2 (P1): Reactor metrics history is resent whole

`MTENuclearReactor.collectMetricsHistory` copies all 100 snapshots every tick, and the whole history is resent every cycle. `ReactorMetricsHistory.addSnapshot` trims with `ArrayList.remove(0)`. `recordMetricsCycleSnapshot` builds boxed `List<Double>` every cycle.

**Do:**

- Store the history in a ring buffer with a version number.
- Sync only the snapshots newer than the client's version, with a full resync on open.
- Use primitive arrays for the per-cycle temperature and reactivity data.
- Check the pump's metrics history for the same pattern.

### P3 (P1): Pump loop crawling, identifiers and NBT

The background for the plan below:

- `MTECoolantPump` re-crawls every `LOOP_RECRAWL_INTERVAL_TICKS` (200) and checks the footprint every `LOOP_CHECK_INTERVAL_TICKS` (100), keyed on `aTick % N`.
- `aTick` counts from when each tile loaded, so every pump loaded at server start crawls on the same tick: a thundering herd.
- Each crawl allocates a new graph keyed by strings like `"pipe_x_y_z"`, plus new `LoopSegment`s. It is then diffed against the old graph through string-keyed maps (`replaceSegments`, `graphNodes`, `graphEdges`, `getDeviceId`).
- `rebindRestoredLoop` runs a whole crawl in one tick (`while (mActiveCrawl != null) stepActiveCrawl()`).

**Keep:**

- Periodic re-crawls and footprint checks: pipes do not tick, so the pump has to check.
- A few seconds of running on a broken loop until the next check.

**Do:**

- **Stagger the schedules.** Give each pump its own phase, e.g. from a hash of its position or a random offset saved in NBT, so pumps never crawl or check on the same tick as each other. Look for other `aTick % N` schedules with the same problem (11 sites).
- **Use typed identifiers.** Replace string node, segment and device ids with small value classes holding separate fields: block position (x, y, z, dimension) plus kind or port. Give them proper `equals` and `hashCode`, or pack them into `long` keys where that fits. That cuts allocation and makes comparisons fast.
- **Spread out the post-load crawl** over ticks, with the loop held (like the reactor's `holdForPassagePumps`), or make it unnecessary through the NBT changes below.
- **Shrink the NBT.** Stop saving every pipe position and graph node (`saveNBTData`'s `pipePositions` and `graphNodes`). Save the loop abstractly: segments with their hydraulic and thermal state, device bindings by position, and what the footprint check needs (or rebuild the footprint on the first scheduled check). The goal stays the same: after a reload the loop runs immediately, without waiting for a crawl.
- **Make the engine state a typed NBT schema.** It is currently saved as a generic `Map<String,Object>` (`exportState` / `LoopStateNbt`).

### P4 (P2): Bath intake scans every tick (done)

**Done:** one pass over the buses fills every empty slot it can. A pass that takes nothing waits 20 ticks before the next one, and any eject resets the wait. Verified in game: a filled bus still loads all 20 slots.


`MTECoolantBath.onPostTick` scans every input bus slot for every empty bath slot, every tick.

**Do:** scan only when a bus's contents changed (or on an interval), and stop at the first empty slot filled per pass.

### P5 (P3): Small per-cycle waste (done)

**Done:** the reactor reads the biome temperature on its 100-tick schedule and on structure checks; `getAmbientTemperature` returns the cached value. The monitor-cover check compares item and meta, resolved once, in a shared `MonitorCover`. The pump's `CoolantFluidProperty.get(name)` was left as is: it is already one cached map lookup.


- `MTENuclearReactor.hasComputerMonitorCover` allocates `ItemList.Cover_Screen.get(1L)` every cycle and scans covers. Cache the result and update it on cover change.
- `onPostTick` calls `getAmbientTemperature()` every cycle, although `mCachedAmbientTemp` exists.
- In the pump's step, when `mEngine.getFluid()` is null it looks up `CoolantFluidProperty.get(name)` by string every tick. Resolve it once.

---

## Architecture and maintenance

### A1 (P1): Make the caches uniform

There are about 27 static caches, each written its own way:

- `LongKeyMap` caches in `ReactorDataRegistry` and `CoolantBathRegistry`;
- `ConcurrentHashMap`s in `ReactorConfigData` and `ReactorDataRegistry`;
- copy-on-write `IdentityHashMap`s in `FluidRoles` and `ItemRoles`;
- `NuclearSimulationEngine.fluidAllowedCache`.

They are invalidated by at least three separate generation counters (`ReactorConfigData.generation`, `ModularNuclearConfig.generation`, `NuclearFuelClassification.configGeneration`). One of them is a non-volatile `int` read from both threads. Some caches are never cleared, e.g. `CoolantBathRegistry.COMPATIBLE`.

**Do:**

- Keep every cache, but build them from one small set of components: a long-keyed one, an identity-keyed one and a name-keyed one, each with the same miss and sentinel handling and thread-safety story.
- Tie them all to one config generation, or register them with one invalidation hook that every config reload fires.
- Document which caches the WASM simulator also uses.

### A2 (P1): Split the god classes

`MTECoolantPump` (3.3k lines) and `MTENuclearReactor` (3.2k lines) are also the files that change most. Possible splits:

- **Pump:** loop lifecycle and crawling, filling and draining, rotor and impeller, fluid safety checks, and the GUI and telemetry.
- **Reactor:** the cycle (prepare / feeds / simulate / outputs / overheating / end), structure checking, meltdown and explosions, telemetry and sync.

`NuclearReactorPhysics` and the `multi/util` helpers have started this; continue it.

### A3 (P1): Stop writing other machines' fields

The reactor writes hatch fields directly, about 52 times: `hatch.mInputFluid = null`, `bus.mInventory[SLOT_INPUT] = null`, `rod.mInventory[SLOT_ROD] = null`, then `markTileDirty()` by hand.

**Do:** give hatches methods that keep their own invariants (e.g. `voidNonFuelContents()`), with dirty marking and sync inside.

### A4 (P2): Add a loop-device registry

Recognizing a loop device is an if-chain repeated in the crawler, in `rebindRestoredLoop` and elsewhere: `findAdjacentFluidHeater` / `findAdjacentPHE` / `findAdjacentDegasser` / `findAdjacentCoolantBath`, plus `instanceof` checks. Adding a device type means editing all of them.

**Do:** have one lookup that answers which loop device owns the block at a position, and let devices register themselves.

### A5 (P2): Engine parameters are global and mutable

`NuclearSimulationEngine`'s static setters are shared by the game, the tests and the simulator. `withParameters` and the thread-local override exist, but the default is a global. This caused test-order dependence: running one test class alone broke the next full run.

**Do:** pass `EngineParameters` explicitly (or through a context object) to `simulate` and the helpers. Have the game build its instance from config, and give each test its own.

### A6 (P2): Remove test hooks from game code (done)

**Done:** the monitor-cover flags are gone (tests override `hasComputerMonitorCover`). The reservoir's simulated tank is used only while the pump has no world, and it is no longer saved. That fixed a real bug: in a world, an empty Railcraft tank fell back to the simulated 100000 L, and a full one took any amount. `mSimulatedFluidTempCelsius` and `mSimulatedBiomeTempCelsius` remain: only tests set them, and they are not saved.


- `mSimulateComputerMonitorCover` is on both `MTENuclearReactor` and `MTECoolantPump`.
- `CoolantPumpReservoir.simulatedCoolantLiters` defaults to 100000, is returned when `world == null`, and is saved to NBT.

**Do:** move these into test subclasses or fakes, and stop saving them.

### A7 (P3): Keep the simulator out of the mod jar

About 115 classes under `nuclear/standalone` (the CLI, `NuclearSimulationWebServer` with its HTML template in Java, `NuclearSimWasmBridge`) ship in the mod jar. TeaVM's limits also reach into game code: no `String.replace` anywhere the bridge reaches (`WasmStrings`).

**Do:** move the standalone simulator into its own source set or subproject that depends on the shared engine. Keep the shared engine as plain Java that both sides use. The HTML template could become a resource file.

### A8 (P3): Small cleanups (done)

**Done:** all five. The stacked Javadocs are moved or dropped. The step helpers return a `Step` enum. The cheese facades are gone. Railcraft's `testIfMasterBlock` is looked up once. The registry aliases are gone: the missing-mappings handler already remaps old worlds, and the nh-dev scripts now use the `modularnuclear:` names.


- 9 places where an old Javadoc block was left stuck above a newer one (e.g. above `MTENuclearReactor.holdForPassagePumps`, and in `MTECoolantPump` above `triggerCatastrophicExplosion`).
- Tri-state `Boolean ...Result` returns in `MTECoolantPump.stepCoolantLoop`: replace them with a small result enum.
- Cheese recipe facades on `MTEHatchNuclearBus`: callers should use `CheeseRecipeRegistry` directly.
- Reflection in `CoolantPumpReservoir` (calling Railcraft's `testIfMasterBlock`, looked up on every call): cache the `Method`, or find a public route.
- Reflection in `CoolantLoopBlocks` (block aliases): check whether it is still needed.

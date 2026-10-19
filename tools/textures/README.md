# ModularNuclear — Texture Provenance & Asset Manifest

In strict compliance with GT New Horizons policies against AI-generated art assets and third-party copyright redistribution risks:
- **Zero third-party files are extracted or bundled** into the mod's distribution jar.
- **Upstream mod textures are referenced strictly at runtime** via Minecraft Forge's cross-mod icon registration.
- **All custom textures are 100% original procedural artwork** synthesized from scratch using pure Python math, harmonic wave equations, and procedural geometry.

---

## 1. Runtime Delegated Assets (Zero Bundled Files)

| Item | Target Texture Domain & Name | Upstream Mod | License | Notes |
| :--- | :--- | :--- | :--- | :--- |
| `ItemCardModularNuclear` | `nuclearcontrol:cardReactor` | Nuclear-Control 2 | GPL-3.0 | Directly registers the Nuclear-Control sensor card texture in memory at client boot. Zero files are bundled in the JAR. |
| `ItemKitModularNuclear` | `nuclearcontrol:kitReactor` | Nuclear-Control 2 | GPL-3.0 | Directly registers the Nuclear-Control sensor kit texture in memory at client boot. Zero files are bundled in the JAR. |

---

## 2. Procedural Asset Manifest

All custom textures are synthesized from scratch during Gradle builds via `generateProceduralTextures` and `generateFuelStatsCharts`:

| Asset Path | Generator Script | Technical Pipeline & Mathematical Formulation | License |
| :--- | :--- | :--- | :--- |
| `textures/gui/nei/neutron_interaction_atlas.png`<br>`textures/gui/nei/long_arrow.png` | `generate_nei_atlas.py` | 256×256 2D canvas synthesis using Python PIL: vector circle rendering, arrow shaft primitives, and text overlays with anti-aliasing. | Pure Procedural Code (MIT) |
| `textures/blocks/OVERLAY_FRONT_FISSION_REACTOR.png`<br>`textures/blocks/OVERLAY_FRONT_FISSION_REACTOR_ACTIVE.png` | `generate_reactor_overlays.py` | 16×16 base overlay + 16×64 4-frame animation strip. Combines metallic bezel rendering with animated core glow oscillations $\sin(2\pi f / 4)$. | Pure Procedural Code (MIT) |
| `textures/blocks/fluids/corium_still.png`<br>`textures/blocks/fluids/corium_flow.png` | `generate_corium.py` | Pure Python standard library (`struct`, `zlib`, `math`). 16×512 (32 frames) still and 32×1024 (32 frames) flow. Uses approved low-contrast grit palette (seed 2041, base `#FFC000`, dark `#F09418`, light `#FFE068`, 38% contrast, 10% dark density, 29% light density). Uses continuous quantile thermal simmer looping and vertical flow coordinate offset. Companion live tuning tool: `corium_texture_studio.html`. | Pure Procedural Code (MIT) |
| `textures/blocks/fluids/heavywater_*`<br>`textures/blocks/fluids/highpressuredistilledwater_*`<br>`textures/blocks/fluids/highpressureheavywater_*` | `generate_water_fluids.py` | Harmonic multi-octave 2D wave equations $W(u, v, \tau) = \sum A_i \sin(k_{xi}u + k_{yi}v \pm \omega_i \tau)$. Spatially periodic (100% seamlessly tileable) and temporally periodic over 32 frames (100% seamlessly looping). Generates still (16×512) and downward-advection flow (32×1024) animation strips with specular caustic highlights. | Pure Procedural Code (MIT) |
| `textures/items/cellHeavyWater.png`<br>`textures/items/cellHighPressureDistilledWater.png`<br>`textures/items/cellHighPressureHeavyWater.png` | `generate_cells.py` | 16×16 cylindrical pressure-vessel fluid canister: metallic casing collar, reinforcement bands, sight-glass viewing aperture, fluid column (indigo, cerulean, violet), and diagonal glass reflection glints. | Pure Procedural Code (MIT) |
| `textures/blocks/MACHINE_CASING_NUCLEAR.png` | `generate_casing.py` | 16×16 lead-shielded bismuth steel casing: cool greenish-slate base (`#566864`), 2px outer chamfered bevel, recessed reinforced panel plate, deterministic surface grit, and 4 corner hex bolts. | Pure Procedural Code (MIT) |
| `textures/items/gt.radiovoltaic.plate.hv.png`<br>`textures/items/gt.radiovoltaic.plate.ev.png` | `generate_radiovoltaic_plates.py` | 16×16 semiconductor wafer plate: 45° beveled plate corners, silicon/graphene micro-grid, central collector busbars, and contact bonding pads. HV features Gold/Nickel busbars on Si-Ge substrate; EV features Cyan superconductor busbars on SiC-Graphene substrate. | Pure Procedural Code (MIT) |
| `textures/gui/nei/fuelstats/chart_*.png` (13 charts) | `FuelDamageCurveSolver.java` | Algorithmic 2D function plotter rendering the exact reactivity $R(T)$ and temperature damage penalty $D(T) = 1 + A \cdot (e^{k(T - T_{\text{pk}})} - 1)$ curves directly from physics parameters for all 13 nuclear fuels. | Pure Procedural Code (MIT) |

---

## 3. Simulator Webapp Icons (Separate Web Distribution)

The standalone browser simulator (`nuclear-sim-dist`) serves 66 lightweight 16×16 tile icons under `textures/sim/icons/` so users can inspect reactors visually in a browser without Minecraft installed.
- These icons are **not loaded or used by the Minecraft mod code**.
- They are used strictly by the web simulation interface for community tooling and design simulation.

---

## 4. Build Target Automation

Asset generation is hooked directly into Gradle:
- `tasks.processResources` depends on `generateProceduralTextures`.
- `tasks.named("sourcesJar")` depends on `generateProceduralTextures`.
- Input tracking (`tools/textures`) and output tracking (`src/main/resources/.../textures`) ensure instant incremental caching (`UP-TO-DATE` in < 50ms).

### Command Reference
```bash
# Inside mods/ModularNuclear:
./gradlew generateAllTextures     # Regenerates all procedural textures and fuel damage charts
python3 tools/textures/generate_all.py  # Standalone master procedural generator (0.3s)

# From nh-dev monorepo root:
make sim-textures                 # Runs texture generation across the simulator stack
```

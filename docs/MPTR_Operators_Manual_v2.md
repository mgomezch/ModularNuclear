# Modular Pressure Tube Reactor (MPTR) — Operator's Manual

**Scope.** This manual describes the internal operation of the Modular Pressure Tube Reactor as implemented in the ModularNuclear mod and its standalone simulator. It is a functional reference: it explains how the core simulation works, what each component does to neutrons and heat, how cooling is computed, and under what conditions contents are voided, the structure is damaged, or the reactor melts down. It does not prescribe a particular reactor layout.

**Source of truth.** All formulas and default values below are taken from the simulation code (`NuclearSimulationEngine`, `NuclearReactorPhysics`, `MTENuclearReactor`, the hatch classes) and the default configuration files shipped in the JAR (`physics.yaml`, `tiers.yaml`, `fuels.yaml`, `components.yaml`). Every value marked *default* can be overridden by a server's configuration directory; see [Appendix A](#appendix-a--configuration-and-global-parameters).

---

## Contents

1. [Overview](#1-overview)
2. [Structure and tiers](#2-structure-and-tiers)
3. [The core grid](#3-the-core-grid)
4. [Neutronics](#4-neutronics)
5. [Heat generation and transport](#5-heat-generation-and-transport)
6. [Fuel](#6-fuel)
7. [Control rods](#7-control-rods)
8. [Core components](#8-core-components)
9. [Cooling](#9-cooling)
10. [Byproducts and transmutation](#10-byproducts-and-transmutation)
11. [Maintenance, structural damage and repair](#11-maintenance-structural-damage-and-repair)
12. [Failure modes and hazards](#12-failure-modes-and-hazards)
13. [Instrumentation and telemetry](#13-instrumentation-and-telemetry)
14. [The standalone simulator](#14-the-standalone-simulator)
- [Appendix A — Configuration and global parameters](#appendix-a--configuration-and-global-parameters)
- [Appendix B — Declared but inactive parameters](#appendix-b--declared-but-inactive-parameters)
- [Appendix C — Formula summary](#appendix-c--formula-summary)

---

## 1. Overview

### 1.1 Design summary

The MPTR is a GregTech multiblock whose defining feature is a two-dimensional grid of **core columns**. Each column is a vertical stack of blocks; the block at the top of the column determines what the column *contains* (fuel, coolant, a moderator, a reflector, and so on) and the block at the bottom determines what *acts on* the column from below (a control rod, a high-pressure coolant outlet, or a neutron beam input).

The reactor has no recipe. Instead, it runs a continuous, discrete-time simulation of:

- **neutron transport** across the grid, separated into a *fast* population that travels in straight rays and a *thermal* population that diffuses isotropically;
- **heat** deposited by fission, absorption and moderation events, conducted between adjacent columns, and removed by coolants and active components;
- **wear** on fuel, moderators, reflectors and coolant items as a function of neutron interactions and temperature;
- **structural damage** to the reactor itself when column temperatures exceed the rating of the structure.

Power is not produced by the reactor directly, with the single exception of radiovoltaic plates. All other energy leaves the reactor as heat carried by a fluid (steam, hot IC2 coolant, hot liquid metal) or by a closed coolant loop, and is converted to EU by external machinery.

### 1.2 Units and conventions

| Quantity | Unit | Notes |
| :--- | :--- | :--- |
| Heat | HU (heat unit) | Internal energy unit of the core simulation. |
| Temperature | °C | All core temperatures are in degrees Celsius. |
| Heat capacity | HU/°C | A nuclear core hatch's column holds $16 \times 2^\text{tier}$ HU/°C (`core_hatch_heat_capacity` = 16: LV 32, EV 256, UV 4096); every other column holds `hu_per_degree` (default 32). |
| Cycle | 5 ticks | The core is simulated once per cycle (`ticksPerCycle`, default 5; 0.25 s). |
| $\Delta t$ | s | Cycle length in seconds, $\Delta t = \text{ticksPerCycle}/20 = 0.25$ s by default. |
| Neutrons | count | Neutron populations are integer counts per cycle (thermal populations are carried as real numbers during diffusion). |
| Fluid | L (mB) | 1 L = 1 mB. |

Adding $Q$ HU to a column of heat capacity $C$ raises its temperature by $Q/C$ °C. Unless a section says otherwise, the examples in this manual are of 32 HU/°C columns (buses).

### 1.3 The simulation cycle

Every 5 ticks the controller executes one full core cycle. The order of operations is fixed and matters for interpreting telemetry:

| Step | Name | What happens |
| :--- | :--- | :--- |
| 0 | Pre-cycle checks | Max-damage fuel failsafe; control rod mining-pipe housing re-scan; coolant hatch thermal-shock checks (§12.2); per-column property refresh. |
| 1 | Snapshot and emission | Each fuel column computes its temperature-dependent reactivity and emits neutrons. Prompt fission heat is queued. External neutron sources emit beam-derived neutrons. |
| 2a | Fast transport | Each emitted packet travels as a fast ray, interacting with the columns it crosses until it is absorbed, thermalized, or leaves the core. |
| 2b | Thermal diffusion | Thermalized neutrons diffuse over the grid with a 9-point stencil until the population is exhausted. |
| 3 | Heat deposition | All queued heat (fission, absorption, moderation, Wigner release) is added to the columns. |
| 4 | Conduction | Heat conducts between cardinally adjacent columns and leaks to the boundary, in 5 sub-steps. |
| 4.5 | Active components | All heat exchangers act at once on a temperature snapshot, then heat vents act. |
| 5 | Column update | Coolant boiling and turnover, coolant-cell heat absorption, fuel and moderator wear, radiovoltaic output, depletion and ejection. |
| 6 | Post-cycle | Telemetry, direct-power summation, wall-wear accounting, overheating checks (§12.3), meltdown check (§12.4). |

On the four ticks between cycles, only the active vents and heat exchangers run (step 4.5). Their throughput limits are therefore per tick, not per cycle (§9.4).

A consequence of the ordering is that **neutrons generated in a cycle are driven by absorptions recorded in the previous cycle** (§4.5). This one-cycle lag is the reactor's only form of delayed response, and it is what makes power excursions observable and controllable rather than instantaneous.

---

## 2. Structure and tiers

### 2.1 Layers

The structure is five blocks tall. In the default upright orientation:

| Layer | Contents |
| :--- | :--- |
| 1 (top) | One **core top element** per core column (see §2.4), surrounded by Nuclear Casings. |
| 2 | **Item Pipe Casings** under every core column (the tiered component), surrounded by Nuclear Casings. |
| 3 | Hollow interior under the core, casing walls. |
| 4 | Hollow interior under the core; the controller sits in the centre of the front wall. |
| 5 (bottom) | One **core bottom element** per core column (casing, control rod hatch, high-pressure outlet, or beamline input), surrounded by Nuclear Casings. |

The structure can be rotated or mirrored by wrenching the controller. Top-layer core hatches must face away from the pipe casings (outward from the core face); the structure rejects hatches that face the wrong way, which prevents two controllers from sharing core hatches.

At least 50 Nuclear Casings are required. The perimeter casings may be replaced by: exactly one maintenance hatch; at most one dynamo hatch (regular or exotic); any number of energy hatches (including multi-amp); any number of GregTech output buses and output hatches; and any number of nuclear control hatches.

### 2.2 Chamber sizes

The core is an octagonal (corner-cut) square chamber. Three sizes exist:

| Footprint | Grid | Corner cut depth | Core columns |
| :--- | :--- | :--- | :--- |
| 7×7 | 5×5 | 1 | 21 |
| 11×11 | 9×9 | 2 | 69 |
| 15×15 | 13×13 | 3 | 145 |

The corner cut is defined by $d_x + d_y < \text{depth}$, where $d_x$ and $d_y$ are a cell's distances to the nearest edges; depth is $\lfloor (n-5)/4 \rfloor + 1$. Cut cells do not exist in the simulation; to neutrons and heat they are equivalent to the outside of the core.

Larger chambers need stronger pipe casings (§2.3): the 11×11 chamber needs Platinum casings or better, and the 15×15 chamber Osmium or better; the 7×7 chamber takes any tier from Electrum. A chamber built with weaker casings does not form, and the controller reports which casing it needs. Building from the structure preview or a construction trigger raises the casing tier to the chamber's minimum.

### 2.3 Pipe casing tiers

The tier of the reactor is the tier of the Item Pipe Casings in layer 2 (all must match). Electrum is the minimum. The tier determines the maximum safe operating temperature of every core column, the temperature below which structural repair is possible, which coolants the core hatches accept, and whether high-pressure coolant loops may be attached.

| Tier | Pipe casing | Nominal voltage | Max operating temp. | Repair threshold | Coolant loops | Accepted coolants (whitelist) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 0 | Electrum | EV | 1200 °C | 120 °C | No | IC2 coolant |
| 1 | Platinum | IV | 1400 °C | 140 °C | No | + distilled water |
| 2 | Osmium | LuV | 1800 °C | 180 °C | Yes | + molten sodium |
| 3 | Quantium | ZPM | 2200 °C | 220 °C | Yes | + heavy water, molten lead |
| 4 | Fluxed Electrum | UV | 2600 °C | 260 °C | Yes | all |
| 5 | Black Plutonium | UHV | 3200 °C | 320 °C | Yes | all |

A fluid is accepted into a core hatch if it appears on the tier's whitelist, or if the reactor's tier is at least the fluid's `required_tier`. Liquid fuels carry their own `required_tier` (§6.2).

The **maximum operating temperature** is not a soft limit. Any core column above it at the end of a cycle inflicts structural damage and loses its non-fuel contents (§12.3).

### 2.4 Core column composition

**Top elements** (layer 1) define the column's material:

| Top element | Holds | Role |
| :--- | :--- | :--- |
| Nuclear core bus | One item (stack size 1) | Fuel rod, coolant cell, reflector, moderator, insulator, heat vent, heat exchanger, radiovoltaic plate, or neutron source. |
| Nuclear core hatch (LV–UHV) | One fluid | Liquid fuel or liquid coolant. Capacity $8000 \cdot 2^{\text{tier}}$ L (LV 16 000 L … UHV 4 096 000 L). The hatch tier also sets its heat-transfer conductance (§9.2). |
| Nuclear core high-pressure input hatch (cast iron … neutronium) | Nothing (fluid passage) | Inlet of a closed coolant loop. Must be paired with an output hatch of the same material at the bottom of the same column. The material sets the passage's heat capacity and heat transfer (§9.7). |

**Bottom elements** (layer 5) act on the column:

| Bottom element | Effect |
| :--- | :--- |
| Nuclear Casing | None. |
| Nuclear core control rod hatch | Holds one control rod; redstone-controlled insertion (§7). |
| Advanced nuclear core control rod hatch | Holds a control rod and a moderator follower (§7.5). |
| Nuclear core high-pressure output hatch (cast iron … neutronium) | Outlet of the closed coolant loop entering at the top of the same column; same material as its input hatch. |
| Beamline input hatch | Feeds a neutron beam into a neutron source held in the nuclear core bus at the top of the same column. Must be under a bus (§8.5). |

A control rod shares the column's temperature: the rod hatch and the top element are a single thermal node.

### 2.5 Ambient temperature

The ambient temperature is derived from the biome at the controller:

$$T_\text{amb} = \frac{100\,\tau_\text{biome} - 32}{1.8}$$

where $\tau_\text{biome}$ is Minecraft's biome temperature. The formula treats $100\,\tau$ as degrees Fahrenheit: a Plains biome ($\tau = 0.8$) gives 26.7 °C; a Desert or the Nether ($\tau = 2.0$) gives 93.3 °C; cold biomes give sub-zero ambients. Ambient temperature is the boundary condition for conduction losses (§5.3), the sink temperature for non-boiling coolants (§9.2), and the threshold for immediate coolant evaporation (§12.2).

---

## 3. The core grid

### 3.1 Cells and boundaries

Each core column is one **cell** of the grid. Every cell has:

- a temperature $T$ (°C) and a heat capacity $C$: $16 \times 2^\text{tier}$ HU/°C for a nuclear core hatch (it belongs to the hatch's structure, like its $8000 \times 2^\text{tier}$ L tank, whatever fluid it holds), 32 HU/°C for every other cell;
- interaction probabilities for fast and thermal neutrons: absorption $p_a$ and scattering $p_s$;
- a fast-neutron moderation probability $p_m$;
- a heat-transfer coefficient $c$ used for conduction;
- an insulation dampening factor $\delta \in [0,1]$ (non-zero only for insulators).

The space outside the grid and the corner-cut cells are not cells. A neutron entering either is lost from the core ("escaped"). For heat, any missing cardinal neighbour is treated as a boundary at ambient temperature.

### 3.2 Cell properties

Cell properties come from the column's contents, looked up in the component data (`components.yaml`, `fuels.yaml`). If a control rod is present under the column, its properties are combined with the contents' properties as described in §7.3.

An **empty bus** or a bus holding a neutron source has $p_a = 0.01$, $p_s = 0.02$, $p_m = 0.05$ and $c = 0.02$: it is nearly transparent to neutrons and a poor heat conductor. An **empty fluid hatch** has $p_a = 0.01$, $p_s = 0.02$, $p_m = 0.05$, $c = 0.05$.

The properties of each fuel, coolant and component are tabulated in their respective sections.

---

## 4. Neutronics

### 4.1 Neutron populations

Two populations are tracked:

- **Fast neutrons** are emitted by fuel and external sources. They travel as discrete *rays* along the four cardinal directions, are tracked individually through the grid, and can be absorbed, scattered, or moderated.
- **Thermal neutrons** are fast neutrons that have been moderated. They are modelled as a continuous density that spreads to all eight neighbouring cells.

Fuel converts absorbed neutrons into new fast neutrons in the following cycle. Thermal absorption in fuel is the principal driver of the chain reaction; fast absorption contributes much less and wears the fuel eight times faster (§6.4).

### 4.2 Emission

Each fuel cell emits, per cycle:

$$N = \operatorname{round}\Big[\big(\tilde N_0 + \operatorname{round}(\phi_t\, A_t' + \phi_f\, A_f')\big)\cdot R(T)\Big]$$

where:

- $\tilde N_0$ is the **spontaneous emission**: the fuel's effective base neutron yield multiplied by $\Delta t$ (base yields are per second), rounded stochastically (the fractional part is a probability of one extra neutron);
- $A_t'$ and $A_f'$ are the thermal and fast neutrons **absorbed by this cell's fuel in the previous cycle**;
- $\phi_t$ and $\phi_f$ are the fuel's effective thermal and fast fission multipliers, multiplied by the global multipliers (both 1.0 by default);
- $R(T)$ is the temperature reactivity factor (§4.6), evaluated at the cell's temperature at the start of the cycle.

The effective base yield and fission multipliers include the **archetype multipliers** (§6.3). For example, a Quad Uranium Fuel Rod has a base yield of 16 n/s (standard archetype, multiplier 1.0), so $\tilde N_0 = 4$ per cycle; at $R = 1$ and with 20 thermal neutrons absorbed in the previous cycle it emits $4 + \operatorname{round}(20 \times 1.25) = 29$ neutrons.

Every emitted neutron deposits **38 HU** of prompt fission heat (`fission_heat_per_neutron`) in the emitting cell.

**Ray count.** The $N$ neutrons are divided as evenly as possible among a number of rays:

| Emitter | Rays per cycle |
| :--- | :--- |
| Single / dual / quad fuel rod | 1 / 2 / 4 (`packet_count`) |
| The Core (Naquadah-32) | 16 |
| Custom fuel rod | rod size (1, 2 or 4) |
| Liquid fuel in a core hatch | $\text{amount}/8000$, stochastically rounded (a full UHV hatch emits 512 rays) |
| Neutron source | 4, one per cardinal direction |

Each fuel ray takes a uniformly random cardinal direction. Neutron-source rays are emitted in all four directions.

### 4.3 Fast-neutron transport

Each ray carries its full neutron count as a single packet. Starting from its source, it steps one cell at a time in its current direction, for at most $2(n_x + n_y)$ steps. The source cell itself is never interacted with. At each cell entered:

1. If the cell is outside the grid or a cut corner, the packet escapes. (If the optional `wall_reflection_chance` is non-zero, the packet may instead reverse direction at the outer wall; the default is 0.)
2. The cell registers the packet as fast flux.
3. With probability $p_\text{int} = \min(1,\ p_a + p_s)$ the packet **interacts**; otherwise it passes through unchanged.
4. An interacting packet is **absorbed** with probability $p_a/p_\text{int}$. Absorption ends the ray, deposits 8 HU per neutron (unless the cell is a perfect insulator, §8.3), and, in graphite, stores Wigner energy (§5.5).
5. Otherwise the packet is **scattered**. If the cell is a near-perfect reflector ($p_s \ge 0.90$ and $p_a \le 0.05$) the ray reverses direction; otherwise it takes a new uniformly random cardinal direction, which may be its original direction.
6. A scattered packet is then **moderated** with probability $p_m$. Moderation ends the ray, deposits 8 HU per neutron, and adds the packet to the thermal pool of that cell.

A ray that exhausts its step budget is counted as escaped.

Two consequences are worth stating explicitly:

- **Specular reflection.** Components with very high scattering and very low absorption (IC2 neutron reflectors, graphite, carbon and beryllium moderators) send fast rays straight back toward where they came from. A fuel cell flanked by such cells receives its neighbours' rays reflected back toward them, and its own rays reflected back past itself.
- **Moderation happens only after scattering.** A cell with high $p_m$ but low $p_s$ thermalizes little, because most packets never interact with it. Effective moderators combine high scattering with substantial moderation.

### 4.4 Thermal diffusion

Thermalized neutrons are distributed by an iterative stencil, for at most $\max(12,\ 2(n_x+n_y))$ iterations or until fewer than 0.001 neutrons remain. In each iteration, the population $\nu$ arriving at a cell is processed as:

$$\nu_\text{abs} = \nu\, p_a^{(t)}, \qquad \nu_\text{scat} = (\nu - \nu_\text{abs})\, p_s^{(t)}, \qquad \nu_\text{lost} = \nu - \nu_\text{abs} - \nu_\text{scat}$$

- $\nu_\text{abs}$ is absorbed in the cell and recorded for the cell's callbacks (fuel chain reaction, control rod, transmutation, radiovoltaics).
- $\nu_\text{scat}$ is redistributed to the eight neighbours, $1/6$ to each cardinal neighbour and $1/12$ to each diagonal neighbour. Shares sent off-grid or into cut corners escape.
- $\nu_\text{lost}$ — the survivors that neither scatter nor are absorbed — is counted as escaped.

The third rule is the most important property of the thermal model: **a thermal neutron in a cell with low thermal scattering is lost, not passed on.** Thermal neutrons only travel through cells that scatter them. Fuel ($p_s^{(t)} = 0.10$) is therefore a near-terminal medium for thermal neutrons: a thermal packet entering a fuel cell is 80 % absorbed and only 2 % passed on. Water, heavy water, moderators and reflectors are good thermal carriers; insulators and radiovoltaic plates are sinks.

Heat from thermal absorption is deposited per neutron: 47.5 HU ($38 \times 1.25$) when absorbed by fuel, and 4 HU when absorbed by anything else (including the control-rod share of a fuel column's absorption, §7.4).

### 4.5 Chain reaction and criticality

Combining §4.2–4.4, the steady neutron economy of a fuel cell is

$$N_{k+1} \approx \Big(\tilde N_0 + \phi_t A_{t,k} + \phi_f A_{f,k}\Big)\, R(T_k)$$

where $A_{t,k}$ is the thermal absorption the cell received in cycle $k$, which depends on the neutrons all its neighbours emitted in cycle $k$ and on the moderators and carriers between them. The reactor is supercritical when, around the core, each emitted neutron returns more than one neutron's worth of $\phi_t A_t + \phi_f A_f$ to fuel in the next cycle; it is subcritical when it returns less, in which case the population settles at a level sustained by spontaneous emission $\tilde N_0$.

Three features bound the chain reaction:

1. **Temperature feedback.** $R(T)$ falls above each fuel's peak temperature, so a hotter core emits fewer neutrons (§4.6).
2. **Generation lag.** Neutrons in cycle $k+1$ respond to absorptions in cycle $k$. Power therefore changes at most by the multiplication factor per 0.25 s rather than instantaneously.
3. **Leakage.** Fast rays and thermal neutrons that reach the edges of the core, cut corners, or non-scattering cells are lost.

### 4.6 Temperature reactivity coefficient

Each fuel has a peak reactivity temperature $T_\text{pk}$ and a floor temperature $T_\text{fl}$. The reactivity factor is

$$
R(T) =
\begin{cases}
0.50 & T \le 0 \\[2pt]
0.50 + 0.50\,\sin\!\left(\dfrac{\pi}{2}\dfrac{T}{T_\text{pk}}\right) & 0 < T \le T_\text{pk} \\[8pt]
0.05 + 0.95\cdot\dfrac{1 + \cos(\pi\,p)}{2},\quad p = \dfrac{T - T_\text{pk}}{T_\text{fl} - T_\text{pk}} & T_\text{pk} < T < T_\text{fl} \\[8pt]
0.05 & T \ge T_\text{fl}
\end{cases}
$$

Reactivity rises from 50 % at 0 °C to 100 % at $T_\text{pk}$, then falls along a half-cosine to 5 % at $T_\text{fl}$. Example, Uranium ($T_\text{pk} = 770$ °C, $T_\text{fl} = 2300$ °C):

| T (°C) | 20 | 200 | 400 | 600 | 770 | 1000 | 1500 | 2000 | 2300 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| R(T) | 0.52 | 0.70 | 0.86 | 0.97 | 1.00 | 0.95 | 0.56 | 0.14 | 0.05 |

Below $T_\text{pk}$ the temperature coefficient is **positive**: warming the core increases reactivity. Above $T_\text{pk}$ it is **negative**: warming decreases reactivity. A core operated above its fuels' peak temperatures is self-stabilizing — an excursion raises temperature, which lowers $R$, which lowers emission. A core operated below peak temperature has no such protection and will accelerate as it heats until it crosses $T_\text{pk}$.

The temperature at which reactivity falls to 10 % is denoted $T_{10}$; it is reached at $p = \arccos(-17/19)/\pi \approx 0.8526$ of the way from $T_\text{pk}$ to $T_\text{fl}$. $T_{10}$ also anchors the temperature wear curve (§6.4).

Reactivity is never below 5 %. Spontaneous emission therefore never stops completely while fuel remains in the core, even far above $T_\text{fl}$.

### 4.7 Escape and vessel wear

All neutrons that leave the core are counted as escaped. Escaped neutrons are accumulated over 60-second windows. At the end of each window, with $\bar N$ the mean escaped neutrons per second over the window, the reactor develops a new maintenance issue with probability

$$P = 0.01 \cdot \left(1 + 9\,\frac{\bar N^2}{\bar N^2 + 1000^2}\right)$$

i.e. a 1 % chance per minute at low leakage, rising to 10 % per minute at very high leakage ($\bar N = 1000$ n/s gives 5.5 %). Reflecting neutrons back into the core reduces this wear.

### 4.8 Displayed flux units

The GUI converts per-cycle neutron counts into nominal physical units by $1\ \text{neutron/cycle} \equiv 10^{13}\cdot(20/\text{ticksPerCycle})$ n·cm⁻²·s⁻¹, i.e. $4\times10^{13}$ n·cm⁻²·s⁻¹ at the default cycle length. Cumulative fluence is shown as $10^{13}$ n·cm⁻² per neutron. These are display conventions only.

---

## 5. Heat generation and transport

### 5.1 Heat sources

| Event | Heat deposited | Cell |
| :--- | :--- | :--- |
| Neutron emitted by fuel | 38 HU per neutron | Emitting fuel cell |
| Thermal neutron absorbed by fuel | 47.5 HU per neutron | Fuel cell |
| Thermal neutron absorbed by anything else | 4 HU per neutron | Absorbing cell |
| Fast neutron absorbed | 8 HU per neutron | Absorbing cell |
| Fast neutron moderated | 8 HU per neutron | Moderating cell |
| Wigner energy release | variable (§5.5) | Graphite moderator cell |
| Radiovoltaic excess energy | variable (§8.4) | Radiovoltaic cell |

Neutrons emitted by external neutron sources carry no prompt fission heat. Perfect insulators (dampening 1.0, i.e. Naquarite foil) absorb neutrons without receiving the 8 HU or 4 HU absorption heat.

All heat produced by neutron events in a cycle is accumulated and deposited at once in step 3, after transport is complete. Deposition therefore does not influence transport within the same cycle.

### 5.2 Conduction

After deposition, heat conducts between each pair of cardinally adjacent cells, in $N \ge 5$ sub-steps per cycle ($N$ grows with the largest coefficient and the conduction multiplier, so that no cell overshoots). In each sub-step, the heat flowing from a hotter cell A to a colder neighbour B is

$$Q_{A\to B} = 32\,(T_A - T_B)\cdot\frac{c_A + c_B}{2}\cdot\frac{\Delta t}{N}\cdot\big(1 - \max(\delta_A,\delta_B)\big)$$

with each coefficient floored at 0.01. A loses $Q/C_A$ °C and B gains $Q/C_B$ °C: between two 32 HU/°C cells the same $\Delta T$ is subtracted from A and added to B, while a hatch of larger heat capacity warms and cools more slowly than its neighbours.

Each missing cardinal neighbour (grid edge or cut corner) is a boundary at ambient temperature. The heat lost through each such face per sub-step is

$$Q_\text{loss} = 32\,(T_A - T_\text{amb})\cdot\frac{c_A\,\Delta t}{2N}\cdot(1 - \delta_A)$$

i.e. half the coupling an equivalent neighbour would have.

Conduction is diagonal-blind: corner-touching cells exchange heat only through shared cardinal neighbours. Diagonal coupling exists only for thermal neutrons.

Representative coefficients:

| Material | $c$ |
| :--- | :--- |
| High-pressure passage | 0.90 |
| Molten lead / sodium | 0.75 / 0.70 |
| Component heat vent / exchanger | 0.50 |
| IC2 coolant (fluid) | 0.50 |
| Heat vents, coolant cells | 0.40–0.60 |
| Water / heavy water | 0.30 |
| Gold / stainless / tungsten-carbide reflector | 0.30 / 0.20 / 0.10 |
| Control rods | 0.08–0.20 |
| Liquid fuels | 0.12–0.15 |
| Solid fuel rods, graphite | 0.05 |
| Empty bus | 0.02 |
| Insulators | 0.005–0.015 |

Conduction is slow. With $c = 0.05$ on both sides and $\Delta t = 0.25$ s, a fuel cell exchanges about 1.25 % of its temperature difference with each neighbour per cycle. Fuel cannot be cooled by conduction alone at significant power; it must be cooled through cells with high $c$ or by coolant in direct contact (fluid fuels in hatches cannot be cooled by their own hatch, since the hatch holds the fuel).

### 5.3 Insulation

An insulator's dampening $\delta$ scales every conductive flow across any face of the insulated cell by $(1-\delta)$, including boundary losses. The higher dampening of the two cells applies. A cell with $\delta = 1$ (Naquarite foil) is thermally isolated from all its neighbours and from the boundary.

### 5.4 Temperature limits

Temperatures are clamped below at −273.15 °C. There is no upper clamp; the limit is enforced by consequence, through structural damage (§12.3).

### 5.5 Wigner energy

Graphite moderators store **Wigner energy**: 1 HU per fast neutron absorbed or moderated in the cell (`wigner_energy_per_fast`), up to a cap of 10 000 HU per item. The stored energy is kept on the item and moves with it.

Each cycle, a graphite cell releases stored energy as heat at a rate

$$Q_\text{W} = \min\!\left(E_\text{stored},\ 2000 \cdot \frac{(T - 1500)^2}{(T - 1500)^2 + 1000^2}\right)\ \text{HU/cycle}, \qquad T > 1500\ \text{°C}$$

No energy is released at or below 1500 °C. At 2000 °C the release is 400 HU/cycle (12.5 °C/cycle), at 2500 °C it is 1000 HU/cycle (31 °C/cycle). A fully charged graphite item holds enough energy to raise its own cell by 312 °C. Release is self-reinforcing: released heat raises the cell's temperature, which increases the release rate.

Carbon and beryllium moderators do not store Wigner energy in the default configuration.

---

## 6. Fuel

### 6.1 Solid fuel rods

Default parameters for each fuel family. Single, dual and quad variants share all parameters except base yield (single : dual : quad = 1 : 2 : 4) and ray count (1, 2, 4).

| Fuel | Archetype | Base yield (single/quad, n/s) | $\phi_t$ | $\phi_f$ | $T_\text{pk}$ | $T_\text{fl}$ | $T_{10}$ | Durability |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| Glowstone | breeder | 1 | 0.60 | 0.05 | 200 | 2000 | 1735 | 80 000 |
| Lithium | breeder | 1 | 0.80 | 0.05 | 390 | 2100 | 1848 | 80 000 |
| Thorium | standard | 2 / 8 | 1.00 | 0.08 | 580 | 2200 | 1961 | 400 000 |
| Uranium | standard | 4 / 16 | 1.25 | 0.10 | 770 | 2300 | 2075 | 160 000 |
| MOX | standard | 8 / 32 | 1.50 | 0.15 | 960 | 2400 | 2188 | 80 000 |
| High Density Uranium | dense | 8 / 32 | 1.80 | 0.12 | 1150 | 2500 | 2301 | 560 000 |
| High Density Plutonium | dense | 12 / 48 | 2.10 | 0.20 | 1350 | 2600 | 2416 | 560 000 |
| Excited Uranium | excited | 16 / 64 | 2.50 | 0.15 | 1540 | 2700 | 2529 | 48 000 |
| Excited Plutonium | excited | 24 / 96 | 2.90 | 0.25 | 1730 | 2800 | 2642 | 80 000 |
| Naquadah | dense | 16 / 64 | 3.40 | 0.25 | 1920 | 2900 | 2756 | 800 000 |
| Naquadria | dense | 20 / 80 | 4.00 | 0.30 | 2110 | 3000 | 2869 | 800 000 |
| Tiberium | standard | 24 / 96 | 4.70 | 0.20 | 2300 | 3100 | 2982 | 400 000 |
| The Core (Naquadah-32) | dense | 512 | 5.50 | 0.50 | 2500 | 3200 | 3097 | 2 560 000 |

Base yields and fission multipliers in this table are before archetype multipliers. Temperatures are in °C.

All solid fuels share the same neutron cross-sections (fast absorption 0.25–0.35, thermal absorption 0.80–0.90, fast scattering 0.15, thermal scattering 0.10, moderation 0.10) and a heat-transfer coefficient of 0.05. They differ in emission, multiplication, temperature curve and durability.

Fuel rods also include the IC2 and GregTech reactor fuel cells recognized by the default fuel table (uranium, MOX, thorium, naquadah, etc.). Items not matched by the table but recognized as IC2 fuel fall back to a base yield of 4 n/s per cell (doubled for MOX).

### 6.2 Liquid fuels

Liquid fuels are held in nuclear core hatches. They emit from the hatch cell like a solid rod but with a ray count proportional to volume (§4.2).

| Liquid fuel | Archetype | Base yield (n/s) | $\phi_t$ | $\phi_f$ | $T_\text{pk}$ | $T_\text{fl}$ | Required tier |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| Thorium Based Liquid Fuel | standard | 4 | 1.00 | 0.08 | 580 | 2200 | Electrum |
| Uranium Based Liquid Fuel | standard | 8 | 1.25 | 0.10 | 770 | 2300 | Platinum |
| Plutonium Based Liquid Fuel | standard | 16 | 1.80 | 0.20 | 1350 | 2600 | Osmium |
| Uranium Hexafluoride | standard | 12 | 1.25 | 0.12 | 770 | 2300 | Osmium |
| Uranium Based Liquid Fuel (Excited) | excited | 16 | 2.50 | 0.15 | 1540 | 2700 | Quantium |
| Thorium Based Liquid Fuel (Excited) | excited | 8 | 1.50 | 0.12 | 580 | 2200 | Fluxed Electrum |
| Plutonium Based Liquid Fuel (Excited) | excited | 32 | 2.90 | 0.25 | 1730 | 2800 | Fluxed Electrum |

Liquid fuels have a thermal absorption of 0.85 and a heat-transfer coefficient of 0.12–0.15. Their base yield does not scale with volume; only the number of rays does. A large volume of liquid fuel therefore spreads the same emission into more directions, rather than emitting more.

### 6.3 Archetypes

Each fuel belongs to an archetype that scales its base yield and both fission multipliers:

| Archetype | Base yield × | Fission multipliers × | Character |
| :--- | :--- | :--- | :--- |
| standard | 1.00 | 1.00 | Reference behaviour. |
| dense | 0.50 | 1.20 | Low spontaneous emission, strong multiplication. Depends on neighbours to reach power. |
| excited | 2.00 | 0.70 | High spontaneous emission, weaker multiplication. Produces power with little help from neighbours. |
| breeder | 1.00 | 1.00 | Very low yields; used for transmutation products. |

Effective values are rounded: effective base yield is $\max(1, \operatorname{round}(N_0 \times m_N))$. For example, a Quad High Density Uranium rod has an effective yield of 16 n/s (4 per cycle) and an effective $\phi_t = 2.16$; a Quad Excited Uranium rod has 90 n/s (22.5 per cycle) and $\phi_t = 2.0$.

### 6.4 Wear and depletion

A fuel item's damage per cycle is

$$D = 0.25\,N + 8\,A_f + 1\,A_t + D_T(T)\,\Delta t$$

where $N$ is the neutrons emitted in the cycle, $A_f$ and $A_t$ are the fast and thermal neutrons absorbed by the fuel this cycle, and $D_T$ is the temperature wear rate. The fast and thermal weights are the global `fast_absorption_damage_multiplier` (8) and `thermal_absorption_damage_multiplier` (1). $D$ is scaled to the item's own damage range (by item max damage / configured durability) and rounded stochastically.

The 8:1 ratio means **fast absorption in fuel is eight times as damaging as thermal absorption** while contributing roughly a tenth of the multiplication. A core in which fuel cells see each other's fast rays directly burns fuel much faster per unit of power than one in which neutrons are moderated before reaching fuel.

**Temperature wear.** The temperature wear rate is an exponential calibrated per fuel:

$$D_T(T) = A\left(e^{k\,(T - 20)} - 1\right), \qquad T > 20\ \text{°C}$$

The constants $k$ and $A$ are solved so that:

- $D_T(T_\text{pk}) = 1$ damage per second, and
- $D_T(T_{10}) = 8 \cdot D_T(T_\text{pk})$ (the `t10_wear_factor`, default 8).

$k$ is found by bisection on $\dfrac{e^{k(T_\text{pk}-20)}-1}{e^{k(T_{10}-20)}-1} = \dfrac{1}{8}$; if a linear curve already satisfies the ratio (only for very low $T_\text{pk}$, e.g. Glowstone) $k$ is set to $10^{-4}$. Resulting constants:

| Fuel | $k$ (1/°C) | $A$ | $D_T$ at $T_\text{pk}/2$ | $D_T$ at $T_\text{fl}$ |
| :--- | :--- | :--- | :--- | :--- |
| Thorium | 0.001007 | 1.321 | 0.41 | 10.5 |
| Uranium | 0.001282 | 0.619 | 0.37 | 10.9 |
| MOX | 0.001497 | 0.324 | 0.32 | 11.1 |
| HD Uranium | 0.001685 | 0.175 | 0.27 | 11.3 |
| HD Plutonium | 0.001881 | 0.089 | 0.22 | 11.4 |
| Excited Uranium | 0.002063 | 0.045 | 0.17 | 11.4 |
| Excited Plutonium | 0.002259 | 0.022 | 0.12 | 11.4 |
| Naquadah | 0.002479 | 0.0091 | 0.08 | 11.5 |
| Tiberium | 0.003047 | 0.0010 | 0.03 | 11.5 |
| The Core | 0.003484 | 0.0002 | 0.01 | 11.5 |

Temperature wear is small compared with neutron wear at any useful power level (a fuel at its peak temperature loses 1 point per second, against durabilities of $10^4$–$10^6$). It is a penalty for sustained over-temperature operation rather than a primary depletion mechanism.

**Depletion.** When damage reaches the item's maximum, the fuel is removed from the bus and its spent product (configured `spent_product`, or the item's depleted form) is sent to the reactor's output buses. The bus is then empty and behaves as an empty bus.

**Liquid fuel burnup.** Liquid fuel is consumed by volume instead of durability. Each cycle a fuel hatch consumes

$$L = \max\big(1,\ \operatorname{round}_\text{stoch}(0.25\,N + 8\,A_f + A_t + D_T(T)\,\Delta t)\big)\ \text{L}$$

and outputs the same volume of the corresponding spent fluid. A fuel hatch consumes at least 1 L per cycle (4 L/s) while it contains fuel.

### 6.5 Custom fuel mixes

The **Actinide Fuel Compounder** produces custom fuel rods from actinide dusts. Composition is measured in units: 1 dust = 36 units, 1 small dust = 9, 1 tiny dust = 4. A single, dual or quad custom rod requires exactly 90, 180 or 360 units (2.5, 5 or 10 dusts).

| Isotope | $T_\text{pk}$ | $T_\text{fl}$ | $\phi_t$ | $\phi_f$ | Base yield | Durability |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| Th-232 | 580 | 2200 | 1.00 | 0.08 | 8 | 400 000 |
| U-238 | 673.8 | 2250 | 1.09 | 0.09 | 10 | 174 000 |
| U-233 | 1150 | 2500 | 1.80 | 0.12 | 32 | 320 000 |
| Am-241 | 1250 | 2600 | 1.90 | 0.18 | 40 | 240 000 |
| Pu-239 | 1350 | 2600 | 2.10 | 0.20 | 48 | 200 000 |
| U-235 | 1540 | 2700 | 2.50 | 0.15 | 64 | 48 000 |
| Cf-252 | 1600 | 2750 | 2.60 | 0.22 | 80 | 144 000 |
| Pu-241 | 1730 | 2800 | 2.90 | 0.25 | 96 | 80 000 |
| Naquadah | 1920 | 2900 | 3.40 | 0.25 | 64 | 800 000 |

Every property of the mix is the **unit-fraction-weighted mean** of the constituent isotopes: $T_\text{pk}$, $T_\text{fl}$, $\phi_t$, $\phi_f$, base yield (minimum 4) and durability (minimum 8000). The damage curve is solved from the resulting $T_\text{pk}$ and $T_\text{fl}$ as in §6.4. The base yield applies to a quad rod; single and dual rods use 25 % and 50 % of it. Custom rods use the standard archetype.

Mixing allows the operator to place $T_\text{pk}$ anywhere between 580 °C and 1920 °C, and to trade durability against yield and multiplication independently of the standard fuel families.

---

## 7. Control rods

### 7.1 Control rod materials

A control rod is a long rod of one of five materials, placed in a control rod hatch at the bottom of a column. Its absorption scales with insertion; its scattering scales with withdrawal.

| Rod | Max thermal absorption | Max fast absorption | Scattering (withdrawn) | $c$ |
| :--- | :--- | :--- | :--- | :--- |
| Silver | 0.50 | 0.20 | 0.05 | 0.20 |
| Boron | 0.70 | 0.35 | 0.05 | 0.08 |
| Cadmium | 0.85 | 0.50 | 0.05 | 0.12 |
| Indium | 0.95 | 0.65 | 0.05 | 0.14 |
| Hafnium | 0.99 | 0.80 | 0.05 | 0.16 |

In the reactor, a column's heat-transfer coefficient is always that of its top element; the rod coefficient listed here applies only where a rod is simulated as a column on its own.

### 7.2 Insertion

The **target insertion** $x_\text{target} \in [0,1]$ is:

- 1.0 if the rod or the reactor is in **SCRAM**;
- otherwise $s/255$ if a ProjectRed bundled cable supplies a signal $s$ on the hatch's selected channel (selected with a screwdriver; default "auto", the strongest channel);
- otherwise $r/15$ for a vanilla redstone signal of strength $r$.

The **mining-pipe housing** limits how far the rod can be withdrawn. The hatch counts up to 5 consecutive IC2 mining pipes extending from its front face (away from the core). Each pipe allows 20 % withdrawal:

$$x_\text{min} = \max(0,\ 1 - 0.2\,n_\text{pipes}), \qquad x = \max(x_\text{min},\ x_\text{target})$$

With no pipes installed the rod is always fully inserted; with five pipes it covers the full range. The pipe count is re-scanned every cycle.

The rod's own probabilities are then

$$p_{a}^\text{rod} = \max(0.01,\ x\cdot a_\text{max}), \qquad p_{s}^\text{rod} = \max(0.01,\ s_\text{rod}\,(1 - x))$$

for each neutron type, with $a_\text{max}$ the material's maximum absorption for that type.

### 7.3 Combined column properties

A control rod occupies part of the column's interaction probability. The rest is available to the column's contents. For a column with base probabilities $p_a, p_s, p_m$ (from its top element):

$$p_a^\text{col} = \min\!\big(1,\ p_a^\text{rod} + p_{a}^\text{fol} + (1 - p_a^\text{rod} - p_m^\text{fol})\,p_a\big)$$

$$p_s^\text{col} = \min\!\big(1,\ p_s^\text{rod} + p_{s}^\text{fol} + (1 - p_a^\text{rod} - p_m^\text{fol})\,p_s\big)$$

$$p_m^\text{col} = \min\!\big(1,\ p_m^\text{fol} + (1 - p_{a,f}^\text{rod} - p_m^\text{fol})\,p_m\big)$$

where the "fol" terms are the moderator follower's contributions (§7.5; zero for a standard hatch), and $p_m^\text{fol}$ applies to fast neutrons only. The rod therefore does two things at once: it adds absorption, and it *shields* the column's own contents in proportion to its insertion.

### 7.4 Absorption partition in fuel columns

When a column contains fuel and a control rod, absorbed neutrons are divided between the rod and the fuel in proportion to their probabilities:

$$n_\text{rod} = \operatorname{round}\!\left(n \cdot \frac{p_a^\text{rod}}{p_a + p_a^\text{rod}}\right), \qquad n_\text{fuel} = n - n_\text{rod}$$

Only $n_\text{fuel}$ feeds the fuel's chain reaction and wear. Rod-absorbed thermal neutrons deposit 4 HU instead of 47.5 HU. A rod under a fuel column thus reduces that column's power both by stealing its absorptions and by shielding it; a rod under a non-fuel column (e.g. a moderator or coolant column between two fuel columns) intercepts neutrons in transit.

### 7.5 Moderator follower

The advanced control rod hatch has a second slot for a moderator item. The moderator is withdrawn as the rod is inserted: its insertion is $1 - x$. Its contributions are its own probabilities scaled by $(1 - x)$:

$$p_m^\text{fol} = (1-x)\,m, \qquad p_{a}^\text{fol} = (1-x)\,a, \qquad p_{s}^\text{fol} = (1-x)\,s$$

With the rod withdrawn, the column gains a moderator in place of the absorber; with the rod inserted, the moderator is removed. This doubles the control authority of a single column: withdrawal both removes absorption and adds moderation.

### 7.6 SCRAM

Setting SCRAM on the reactor sets every attached rod's target insertion to 100 %, overriding redstone. Insertion is still bounded below by $x_\text{min}$, so SCRAM is always effective. The reactor also enters SCRAM automatically when it voids its contents with explosions disabled (§12.7).

A SCRAM reduces reactivity; it does not remove heat. Spontaneous emission continues at $R(T) \ge 0.05$, and a hot core must still be cooled.

### 7.7 Operating characteristics

- **Absorption saturates; shielding does not.** Because $p_a^\text{col}$ is capped at 1 and the rod also suppresses the column's own scattering and moderation, the marginal effect of insertion is approximately linear over most of the range for weak rods and front-loaded for hafnium.
- **Fast absorption by rods is significant.** Hafnium's 0.80 fast absorption intercepts fast rays crossing the column; a hafnium rod under a reflector column substantially reduces that reflector's effect.
- **Temperature.** A rod has the temperature of its column. A rod in a column above the tier's maximum operating temperature is voided (§12.3) and the column loses its control.

---

## 8. Core components

All items in this section are held in a nuclear core bus.

### 8.1 Reflectors

| Reflector | $p_a$ (fast / thermal) | $p_s$ (fast / thermal) | $p_m$ | $c$ | Durability | Fast scatter mode |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| Gold plate | 0.15 / 0.10 | 0.85 / 0.90 | 0 | 0.30 | indestructible | random |
| Stainless steel plate | 0.10 / 0.05 | 0.90 / 0.95 | 0 | 0.20 | indestructible | random |
| Tungsten carbide plate | 0.07 / 0.02 | 0.93 / 0.98 | 0 | 0.10 | indestructible | random |
| Neutron Reflector (IC2) | 0.01 / 0.02 | 0.95 / 0.98 | 0.20 | 0.15 | 10 000 | reverse |
| Thick Neutron Reflector | 0.005 / 0.01 | 0.98 / 0.99 | 0.25 | 0.20 | 40 000 | reverse |

Metal plates are pure scatterers with moderate absorption; they redirect fast rays randomly and carry thermal neutrons. IC2 reflectors are near-lossless, return fast rays along their incoming path (§4.3), and thermalize a fraction of them in place.

**Wear.** Each scattering callback on a reflector cell has a 1-in-20 chance to remove 1 point of durability. Callbacks occur per scattered fast packet and once per cycle for the aggregate thermal scattering, so wear grows with the number of distinct rays scattered, not with the number of neutrons. A reflector at zero durability is consumed.

### 8.2 Moderators

| Moderator | $p_a$ (fast / thermal) | $p_s$ (fast / thermal) | $p_m$ | $c$ | Durability | Spent product | Wigner |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| Graphite (long rod) | 0.002 / 0.009 | 0.93 / 0.621 | 0.50 | 0.05 | 80 000 | ash | yes |
| Carbon (long rod) | 0.003 / 0.012 | 0.90 / 0.60 | 0.45 | 0.05 | 10 000 | ash | no |
| Beryllium (long rod) | 0.001 / 0.004 | 0.96 / 0.85 | 0.65 | 0.15 | 240 000 | — | no |

All three have fast scattering ≥ 0.90 and absorption ≤ 0.05 and therefore reverse fast rays (§4.3). A fast packet entering a graphite cell has a 93.2 % chance to interact; of those, 99.8 % scatter, and of the scattered, 50 % are thermalized in place. Unthermalized packets are sent back toward their source.

The thermal scattering of a moderator determines how well it carries thermal neutrons onward once produced. Graphite passes 61.5 % of arriving thermal neutrons per iteration, beryllium 84.7 %.

**Wear.** A moderator's damage per cycle is

$$D = 8\,A_f + A_t + 0.05\,(\Phi_f + \Phi_t) + a_T\left(e^{k_T (T-20)} - 1\right)\Delta t$$

where $\Phi$ is the flux through the cell (all neutrons registered, absorbed or not). Temperature wear constants: graphite $k_T = 3\times10^{-4}$, $a_T = 1$; carbon $5\times10^{-4}$, 1; beryllium $2\times10^{-4}$, 0.5. Flux wear dominates in normal operation. On depletion one item of the stack is consumed and the spent product (ash for graphite and carbon) is sent to the output buses.

Graphite additionally stores and releases Wigner energy (§5.5).

### 8.3 Insulators

| Insulator | Dampening $\delta$ | $c$ | $p_a$ | $p_s$ |
| :--- | :--- | :--- | :--- | :--- |
| Naquarite Universal Insulator Foil | 1.00 | 0.005 | 1.00 | 0 |
| Mica Insulator Foil | 0.60 | 0.010 | 0.01 | 0.05 |
| Thermal Cloth T2 | 0.40 | 0.010 | 0.01 | 0.05 |
| Alumino-Silicate Wool | 0.30 | 0.015 | 0.01 | 0.05 |
| Thermal Cloth | 0.20 | 0.010 | 0.01 | 0.05 |

Insulators reduce conduction across all faces of their cell (§5.3). Naquarite foil is also a perfect neutron absorber: every packet entering it is absorbed, and, because $\delta = 1$, no absorption heat is deposited. It is the only component that terminates neutrons without heating. Other insulators are nearly transparent to neutrons; because their thermal scattering is only 0.05, thermal neutrons entering them are almost entirely lost (§4.4).

Insulators are indestructible.

### 8.4 Radiovoltaic plates

Radiovoltaic plates convert absorbed neutrons directly into EU.

| Plate | $p_a$ (fast / thermal) | $p_s$ | $p_m$ | Max output | 95 % output at $\Psi_{95}$ | $c$ |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| HV | 0.40 / 0.40 | 0 | 0 | 1024 EU/t | 10 /s | 0.10 |
| EV | 0.60 / 0.60 | 0 | 0 | 4096 EU/t | 10 /s | 0.10 |

With $A_f$, $A_t$ the neutrons absorbed this cycle, the weighted absorption rate is $\Psi = (4A_f + A_t)/\Delta t$ per second, and the output is

$$P = \operatorname{round}\!\left(P_\text{max}\cdot\eta\cdot\tanh\!\left(\operatorname{artanh}(0.95)\,\frac{\Psi}{\Psi_{95}}\right)\right)\ \text{EU/t}$$

with conversion efficiency $\eta = 1$ and $\Psi_{95}$ = `full_output_absorbed_per_second` (default 10). Output reaches 95 % of maximum at $\Psi = \Psi_{95}$: 10 thermal neutrons per second, or 2.5 fast ones. $\Psi = \Psi_{95}/2$ yields 72 %. Because $\Psi$ is a rate per second, the curve does not depend on the cycle length. The energy not converted is deposited as heat in the plate's cell:

$$Q = \max\!\big(0,\ 20\,(4A_f + A_t) - P\,\Delta t\big)\ \text{HU}$$

Above saturation, additional flux produces almost only heat. The summed plate output, multiplied by the maintenance efficiency (§11.1), is emitted through the dynamo hatch every tick. Plates are indestructible.

Because $p_s = 0$, a plate is a terminal sink for thermal neutrons that are not absorbed by it.

### 8.5 Neutron sources and beamline injection

A neutron source (GoodGenerator) placed in a bus, with a beamline input hatch at the bottom of the same column, emits neutrons derived from the incoming beam. Per cycle the source accumulates

$$\text{packets} \mathrel{+}= E \cdot r \cdot 0.001 \cdot \Delta t$$

where $E$ is the beam energy, $r$ the beam rate, and 0.001 the `beam_energy_to_fast_neutron_multiplier`. Each whole packet becomes 4 neutrons, emitted as four equal rays in the four cardinal directions. Source neutrons deposit no prompt heat.

A neutron source without a beam emits nothing and behaves as an empty bus. Injecting any particle other than neutrons through a beamline input hatch causes an explosion (§12.5).

Neutron sources are the means to start a core whose fuel has too little spontaneous emission (dense fuels, breeder rods), or to drive a deliberately subcritical core.

### 8.6 Coolant items, heat vents and heat exchangers

These are cooling components; see §9.3–§9.5.

### 8.7 Other bus behaviour

A bus whose temperature exceeds 65 °C and that holds a cheese item registered with the cheese extraction recipes converts it into molten cheese, removing the recipe's energy equivalent in HU from the cell. This is a fully functional, if minor, heat sink.

---

## 9. Cooling

### 9.1 Heat removal paths

| Path | Where | Mechanism | Limited by | Consumes |
| :--- | :--- | :--- | :--- | :--- |
| Fluid coolant | Core hatch | Boils or heats coolant at a sink temperature | Hatch tier conductance, ΔT to sink | Coolant (hot fluid output) |
| Coolant cells | Core bus | Stores heat in item durability | Transfer rate cap per item | Item capacity (ejected when full) |
| Heat vent | Core bus | Removes heat from its own cell to nowhere | HU/t cap | EU |
| Heat exchanger | Core bus | Equalizes temperatures of its cell and neighbours | HU/t cap | EU |
| Closed loop | HP hatches | Convective transfer to a pumped loop | Flow, fluid properties, loop hardware | Pump EU; loop coolant via radiolysis |
| Boundary loss | Grid edges | Conduction to ambient | Coefficients | Nothing |

Only heat removed by fluid coolant or by a closed loop is recoverable as power.

### 9.2 Fluid coolant in core hatches

A core hatch holding a coolant extracts heat from its own cell once per cycle, by an analytical lumped-capacitance law. The **sink temperature** is

$$T_\text{sink} =
\begin{cases}
\max(T_\text{amb},\ T_\text{boil}) & \text{boiling coolant (expansion ratio} > 1) \\
T_\text{amb} & \text{non-boiling coolant}
\end{cases}$$

If $T > T_\text{sink}$, the maximum heat the hatch can extract this cycle is

$$Q_\text{max} = C\,(T - T_\text{sink})\left(1 - e^{-U\,\eta\,\Delta t / C}\right)$$

where $C$ is the hatch's heat capacity, $\eta$ is the maintenance efficiency and $U$ is the hatch conductance:

| Hatch tier | LV | MV | HV | EV | IV | LuV | ZPM | UV | UHV |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| $U$ (HU·s⁻¹·°C⁻¹) | 3.06 | 8.12 | 22 | 57 | 151 | 400 | 1061 | 2812 | 7452 |
| $C$ (HU/°C) | 32 | 64 | 128 | 256 | 512 | 1024 | 2048 | 4096 | 8192 |
| Share of the excess removed per second | 9 % | 12 % | 15 % | 20 % | 26 % | 32 % | 40 % | 50 % | 60 % |
| Heat removed per cycle per °C above the sink (HU) | 0.76 | 2.00 | 5.27 | 14 | 36 | 95 | 249 | 646 | 1666 |
| Capacity (L) | 16 000 | 32 000 | 64 000 | 128 000 | 256 000 | 512 000 | 1 024 000 | 2 048 000 | 4 096 000 |

$U$ is $8.12 \cdot 2.65^{\text{tier}-2}$ (`base_hatch_conductance` = 8.12, `hatch_conductance_tier_ratio` = 2.65), and $C$ is $16 \cdot 2^\text{tier}$ (`core_hatch_heat_capacity` = 16). The heat capacity doubles per tier while the conductance grows 2.65 times, so a higher-tier hatch is not just bigger but made of materials that conduct better: it removes a larger share of its stored excess heat, from 20 % per second at EV to 60 % at UHV. A tier ratio of 2 would give every tier the same share. The extraction is capped by the coolant available: the hatch processes

$$V = \min\!\left(V_\text{stored},\ \operatorname{round}\frac{Q_\text{max}}{h}\right)\ \text{L}$$

of coolant, where $h$ is the coolant's heat per litre. It outputs $V \times$ (expansion ratio) litres of the hot fluid to the reactor's output hatches, and the cell's temperature drops by $V h / C$, never below $T_\text{sink}$.

**Worked example.** An EV hatch of distilled water in a cell at 600 °C: $T_\text{sink} = 100$ °C, $Q_\text{max} = 256 \times 500 \times 0.0542 = 6933$ HU. The hatch boils 393 L of water, producing 62 880 L of superheated steam per cycle (12 576 L/t), and cools its cell by 27.1 °C.

The cooling law has two consequences. First, the hatch cools only its own cell: heat from elsewhere must conduct in. Second, a boiling coolant holds its cell at or above its boiling point, and cooling power is proportional to the difference above it; a water-cooled cell must be hot to be well-cooled.

**Coolant fluids:**

| Coolant | Required tier | $T_\text{boil}$ / sink | Heat per litre | Output | Expansion | $c$ | $p_a$ (f/t) | $p_s$ (f/t) | $p_m$ | Explosion temp. |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| IC2 coolant | Electrum | ambient | 20 HU | Hot coolant | 1:1 | 0.50 | 0.03 / 0.12 | 0.45 / 0.45 | 0.05 | none |
| Distilled water | Platinum | 100 °C | 17.66 HU | Superheated steam | 1:160 | 0.30 | 0.05 / 0.10 | 0.70 / 0.70 | 0.25 | 100 °C |
| Molten sodium | Osmium | ambient | 480 HU | Molten sodium | 1:1 | 0.70 | 0.01 / 0.05 | 0.20 / 0.20 | 0.05 | 883 °C |
| Heavy water | Quantium | 101.4 °C | 17.66 HU | Heavy water steam | 1:160 | 0.30 | 0.005 / 0.01 | 0.85 / 0.85 | 0.90 | 101.4 °C |
| Molten lead | Quantium | ambient | 560 HU | Molten lead | 1:1 | 0.75 | 0.005 / 0.02 | 0.15 / 0.15 | 0.02 | 1749 °C |

Coolants are also neutronic materials. Water is a moderate moderator with significant thermal absorption; heavy water is an excellent moderator ($p_m = 0.9$) with negligible absorption; liquid metals are nearly transparent and barely moderate, so they cool a fast-spectrum core without thermalizing it. A coolant whose required tier exceeds the reactor's tier is not accepted.

The **explosion temperature** of a coolant is the temperature above which coolant flashes on contact with a dry hatch; see thermal shock (§12.2).

Steady operation requires coolant to be supplied to the hatches continuously and hot fluid to be removed from the output hatches; a hatch that runs dry stops cooling and is marked dry.

### 9.3 Coolant cells

IC2 and GregTech coolant cells (and condensators) store heat in their durability. Each cycle, a coolant cell in a cell hotter than ambient absorbs

$$n = \operatorname{round}\!\left[\min\!\left(\frac{32\,(T - T_\text{amb})}{25},\ \operatorname{round}\!\left(\frac{H_\text{max}}{100}\Delta t\right)\right)\cdot\eta\right]$$

heat points (limited by remaining room), each worth 25 HU, where $H_\text{max}$ is the item's heat capacity in points. A 10k cell absorbs at most 25 points (625 HU, 19.5 °C) per cycle; a 360k cell at most 900 points (22 500 HU, 703 °C) per cycle. When full, the cell is ejected to the output buses for external re-cooling, and the bus is left empty.

Coolant cells cool their own cell down to ambient (not to a boiling point), require no EU, and produce no recoverable power.

The transfer cap is 1 % of the item's capacity per second, so larger cells are proportionally faster as well as longer-lasting. All coolant items share a moderation probability of 0.05.

| Coolant item | $p_a$ (fast / thermal) | $p_s$ | $c$ |
| :--- | :--- | :--- | :--- |
| IC2 coolant cells (10k / 30k / 60k) | 0.02 / 0.05 | 0.45 | 0.40 |
| Helium coolant cells | 0.02 / 0.05 | 0.45 | 0.40 |
| NaK coolant cells | 0.02 / 0.05 | 0.35 | 0.50 |
| Sp coolant cells | 0.01 / 0.03 | 0.25 | 0.60 |
| Condensators | 0.03 / 0.08 | 0.40 | 0.45 |

### 9.4 Heat vents

A heat vent drives the temperature of its own cell toward ambient, consuming EU from the reactor's energy hatches. It acts **every tick** (inside the cycle after conduction, and on each of the four ticks between cycles). Per tick:

$$Q = \min\big(C\,|T - T_\text{amb}|,\ Q_\text{max}\big), \qquad E = \lceil 0.25\,Q \rceil \le E_\text{max}$$

All vents draw from the reactor's shared energy buffer. If the buffer cannot cover the combined demand of every vent that tick, all vents are throttled by the same fraction, $f = (E_\text{stored} - n_\text{vents}) / \sum E$; each moves $f\,Q$ and pays $\lceil f\,Q/4 \rceil$ EU. (The $n_\text{vents}$ EU of headroom absorbs the rounding.) With no stored energy at all, vents do nothing.

| Vent | $Q_\text{max}$ (HU/t) | $E_\text{max}$ (EU/t) | $c$ |
| :--- | :--- | :--- | :--- |
| Heat Vent | 512 | 128 | 0.40 |
| Overclocked Heat Vent | 2048 | 512 | 0.45 |
| Component Heat Vent | 4096 | 1024 | 0.40 |
| Advanced Heat Vent | 8192 | 2048 | 0.50 |

All vents cost 1 EU per 4 HU. A Heat Vent at full rate removes 2560 HU per cycle, equivalent to 80 °C of its own cell. Heat vents destroy heat; they produce nothing.

A vent drives its cell toward ambient from either direction: a vent in a cell colder than ambient heats it.

### 9.5 Heat exchangers

A heat exchanger equalizes its own cell and its up to four cardinal neighbours toward their mean temperature, every tick. With $\bar T$ the mean weighted by heat capacity, the heat to move is $\sum_{T_i > \bar T} C_i\,(T_i - \bar T)$; if this exceeds the exchanger's cap, every cell moves proportionally less. Energy cost is 1 EU per 4 HU moved.

| Exchanger | Cap (HU/t) | $E_\text{max}$ (EU/t) | $c$ |
| :--- | :--- | :--- | :--- |
| Heat Exchanger | 1024 | 256 | 0.40 |
| Component Heat Exchanger | 2048 | 512 | 0.45 |
| Advanced Heat Exchanger | 4096 | 1024 | 0.50 |

Exchangers do not remove heat; they redistribute it, typically from fuel to an adjacent coolant hatch, faster than conduction would.

**Simultaneous action.** All exchangers act at once, as a single atomic step computed from a snapshot of cell temperatures taken at the start of the step; the result does not depend on the order in which exchangers are considered. Equalizing $n$ cells toward their mean by a fraction $s$ is equivalent to pairwise flows between every pair of the exchanger's cells:

$$s\,(\bar T - T_i) = \frac{s}{n}\sum_j (T_j - T_i) \quad\Rightarrow\quad f_{ij} = w\,(T_j - T_i),\qquad w = \frac{s}{n}$$

with $s$ set by the exchanger's HU and EU caps as above. The pair exchanges $w\,\min(C_i, C_j)\,(T_j - T_i)$ HU, so a cell's temperature moves by at most $f_{ij}$ towards each partner (exactly $f_{ij}$ between equal cells, less for the larger of two unequal ones). Where exchangers overlap, a cell can receive more total weight than it can give, $S_i = \sum_e w_e\,(n_e - 1) > 1$. Simply adding up the exchangers' requests would then overshoot: a hot cell between two full-strength exchangers whose other cells are at 0 °C would be driven 80 °C past its mean twice, from 100 °C to −60 °C. Each pair weight is therefore divided by $\max(1, S_i, S_j)$. This keeps the flows symmetric, so heat is conserved, and limits every cell's total weight to at most 1, so every new temperature is a weighted average of snapshot temperatures; no cell is pushed past the hottest or coldest cell it shares an exchanger with. In the example above, the hot cell ends at exactly 0 °C and its eight partner cells each gain 12.5 °C. An exchanger that overlaps no other is unaffected by this normalization.

Each exchanger's HU and EU caps are then re-applied to its normalized flows. As with vents, if the shared energy buffer cannot cover all exchangers, every exchanger is throttled by the same fraction. All energy is drawn and all temperature changes are applied together. An exchanger with no stored energy available is excluded before normalization.

All exchangers act before any vent in the same tick, so heat an exchanger moves into a vent's cell is removed by that vent in the same tick. When energy is short, exchangers are served first and vents share whatever remains.

Vents and exchangers have no temperature limit of their own in the default configuration, but, as non-fuel contents, they are voided if their cell exceeds the tier's maximum operating temperature (§12.3).

### 9.6 Boundary loss

Every cell on the grid edge or adjacent to a cut corner loses heat to ambient through each exposed face (§5.2). This is the only passive cooling. It is small for most materials and zero through insulators.

### 9.7 Closed coolant loops

From Osmium tier upward, a column may be a **pressure-tube passage**: a high-pressure input hatch on top and a high-pressure output hatch at the bottom of the same column, connected externally to a closed loop driven by a Coolant Pump. The passage holds no fluid of its own; the loop fluid flows through it.

**Passage materials.** There is one input and one output hatch for each coolant pipe material, and both ends of a passage must be the same material. Each is crafted from the GregTech input (or output) hatch of the material's voltage tier, a huge coolant pipe of the material and a Clean Stainless Steel Casing; a screwdriver turns an input into the output of the same material and back. The material's voltage tier $t$ sets the passage's heat capacity and heat transfer:

| Material | Cast iron | Steel | Stainless steel | Titanium | Tungstensteel | Osmium | Neutronium |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| Voltage tier $t$ | LV | MV | HV | EV | IV | LuV | ZPM |
| Heat capacity $C = 16 \cdot 2^t$ (HU/°C) | 32 | 64 | 128 | 256 | 512 | 1024 | 2048 |
| Heat transfer at 75 L/s of water (HU·s⁻¹·°C⁻¹) | 9.3 | 25 | 66 | 174 | 461 | 1221 | 3236 |

At 75 L/s of water a passage removes about three times what a core hatch of the same tier conducts into its own coolant (§9.2), and more with more flow. The untiered high-pressure hatches of earlier versions are no longer accepted by the structure.

**Passage as a cell.** The passage cell has $p_a = 0.02$ (fast) / $0.05$ (thermal), $p_s = 0.80$, $c = 0.90$, and a moderation probability equal to the loop coolant's $p_m$ (0.05 if unknown). Its high conductivity makes it a strong conductive sink for adjacent fuel. All of these are set in `physics.yaml`: `coolant_passage_fast_absorption`, `coolant_passage_thermal_absorption`, `coolant_passage_scattering`, `coolant_passage_conduction` and `coolant_passage_default_moderation`.

**Convective transfer.** Each tick, the loop segment passing through the column exchanges heat with the cell. The passage is modelled as a 5 m tube of 0.10 m internal diameter (wetted area 1.571 m²). With loop velocity $v$, fluid density $\rho$, viscosity $\mu$, specific heat $c_p$ and thermal conductivity $k$:

$$Re = \frac{\rho v D}{\mu}, \qquad Pr = \frac{\mu c_p}{k}, \qquad Nu = \begin{cases} 4.36 & Re < 2300 \\ 0.023\,Re^{0.8}\,\max(0.6, Pr)^{0.4} & \text{otherwise}\end{cases}$$

$$h = m_p\,\frac{Nu\,k}{D}, \qquad \dot Q = h\,A\,(T_\text{cell} - T_\text{coolant})$$

where $m_p = m_0\,r^{\,t-2}$ for a passage of voltage tier $t$, with $m_0$ = `coolant_passage_heat_transfer_multiplier` (default 0.11) and $r$ = `coolant_passage_heat_transfer_tier_ratio` (default 2.65) in `physics.yaml`. The velocity is that of the passage's own share of the loop flow: where the loop splits into parallel branches (several passages behind one manifold, or several PHEs), each branch carries a share of the flow in proportion to its pipe size, and each device sees only its branch's flow.

The heat transferred in one tick is capped so that the cell and the segment cannot cross over in temperature. The cell's heat capacity is the passage's $C$ (above) times 128 J/HU (1 HU = 128 J at the interface). The coolant segment's temperature rises by $Q/(m_\text{seg}\,c_p)$.

Laminar flow ($Re < 2300$) transfers very little heat; turbulent flow scales with $v^{0.8}$. Flow rate is therefore the principal control on loop cooling.

**Loop hydraulics.** The pump converts EU to mechanical power at 80 W per EU, times the rotor's efficiency and the pump's maintenance efficiency. It draws $V_\text{tier}$ EU/t with one energy hatch, or $4 V_\text{tier}$ with several (512 EU/t with none). The loop flow $Q$ obeys

$$I\,\frac{dQ}{dt} = \Delta P_\text{pump}(Q) - \sum_i \Delta P_i(Q), \qquad I = \sum_i \rho\,\frac{L_i}{A_i}$$

with the pump curve $\Delta P_\text{pump} = P_\text{mech}/(Q + Q_r)$, $Q_r = \max(0.005,\ 0.02\sqrt{P_\text{mech}/5000})$ m³/s, and Darcy–Weisbach losses per segment

$$\Delta P_i = \left(f\,\frac{L_i}{D_i} + K_i\right)\frac{\rho v_i^2}{2}$$

with $f = 64/Re$ in laminar flow and the Churchill correlation otherwise. The core passage has a minor-loss coefficient $K = 2.5$ (`coolant_passage_minor_loss_k`). When the pump stops, flow coasts down under friction plus an unpowered impeller loss ($K = 8$) and a 0.98-per-tick decay. Disabling the pump's discharge hatch (for example with a soft mallet or a machine controller cover) instead **brakes** the loop: the pump keeps drawing power and applies its head against the flow, bringing it to a stop faster than coasting; once the flow reaches zero, the pump returns the loop's coolant to its reservoir at its fill rate, until the loop is empty or the reservoir is full. Heat is advected segment to segment with the flow.

Loop fluids (in the loop engine): water, distilled water, heavy water, IC2 coolant, sodium, and others, each with SI density, viscosity, specific heat, conductivity, and boiling and freezing points. Sodium's thermal conductivity (86 W·m⁻¹·K⁻¹) is two orders of magnitude above water's, which gives it far higher $h$ at a given flow.

**Molten coolants.** Molten sodium and molten lead enter the loop at exactly their melting point, or at ambient if that is hotter. The melting point is the fluid's configured freezing point (`fluids.yaml`), which defaults to the temperature GregTech declares for the fluid (sodium 370 K = 96.85 °C, lead 600 K = 326.85 °C). They have no margin: if any loop segment ever drops below that temperature, the coolant solidifies and the loop ruptures (§12.8). A passage cell colder than the melting point chills the coolant flowing through it below the melting point on its first pass, so circulating molten coolant through a cold core destroys the loop.

> [!WARNING]
> A molten-coolant loop needs a control system that circulates the coolant only while every part of the loop it passes through stays above the melting point. A typical design reads the reactor's telemetry through OpenComputers (§13.2), extracts the temperatures of the passage columns, and enables the pump (for example through a machine controller cover driven by redstone) only while the coldest passage is above the melting point plus a safety margin. If the temperatures fall towards the margin, it should run the pump less of the time, so the coolant spends longer in the passages and leaves hotter. As a last resort it should disable the discharge hatch, which brakes the flow and drains the loop back into the pump's reservoir.

**Heat delivery.** The loop delivers its heat to a **Pressurized Heat Exchanger** (PHE), which boils a secondary fluid (distilled water to steam at 1:160, or cryogenic liquids to gas). The PHE transfers heat only while the primary coolant is at least 5 °C above the secondary's boiling point (the approach temperature), consumes EU, and is limited per 20-tick batch by its rated exchange capacity and by the secondary fluid available.

**Holding the loop hot.** A PHE stops exchanging heat while it is disabled (for example by a machine controller cover driven by redstone), and draws no power. Gating the PHEs on a temperature reading, with the pump left running, holds the loop's coolant at a chosen temperature: the PHEs run while the coolant is above the band and stop once it falls below it. The passages keep their full flow, so they still pull heat out of the core quickly, but only the heat that would take the fuel past the band. This lets hot fuels run near their best reactivity instead of being cooled far below it, and a hotter loop makes higher-grade steam. Choose the band well below the casing's maximum temperature: the hotter the loop, the less margin the core has for a surge. The pump's own temperature control (above) serves a different purpose, keeping molten coolants from freezing; the two can be combined, starting a molten loop only once the passages are hot and then holding it with the PHEs. The simulator models both, per circuit: `loop_temp_min`/`loop_temp_max`/`loop_sensor` gate the pump, and `phe_temp_min`/`phe_temp_max`/`phe_sensor` gate the PHEs (reading the hot leg by default).

**PHE size and sheetmetal.** A PHE is 3 blocks wide, 3, 5, 7 or 9 high and 5 to 11 long (overall, from the controller's face to the rear wall). Its controller and its primary and secondary hatches sit on the middle layer, and every interior block of its centre column is sheetmetal (one sheetmetal per height layer between the top and bottom casings, along the inner length), all of one material: a PHE with mixed sheetmetal does not form. Its exchange area is height × (length − 2) m², from 9 m² at 3×5 to 81 m² at 9×11, times its sheetmetal's heat transfer multiplier, and the sheetmetal limits the coolant temperature the PHE withstands:

| Sheetmetal | Copper | Electrum | Platinum | Osmium | Quantium | Fluxed Electrum | Black Plutonium | Neutronium |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| Heat transfer | 1× | 2× | 4× | 8× | 16× | 32× | 64× | 128× |
| Max coolant temp. | 1084 °C | 1584 °C | 2084 °C | 4084 °C | 6584 °C | 9084 °C | 15 000 °C | 1 000 000 °C |

`heatTransferMultiplier` in `devices.yaml` (default 1.0) scales every PHE.

Each tick a PHE moves heat by convection across its effective area $A$ (exchange area × sheetmetal multiplier × `heatTransferMultiplier`), $\dot Q = h(v)\,A\,(T_\text{coolant} - T_\text{boil})$, with $h$ rising with the coolant velocity up to 5000 W·m⁻²·K⁻¹: about 45 kW per °C above the secondary's boiling point (18 HU/t per °C) for the smallest copper PHE, and 128 times that per square metre in neutronium. One batch may move at most 2.26 MJ times the PHE's voltage over 32: about 0.9k HU/t at LV, 14k HU/t at HV and 56k HU/t at EV. The PHE draws $\tfrac{15}{16}$ of its voltage every tick it runs. The coolant settles where the reactor's heat equals what the PHEs can move, so fewer, smaller or lower-tier PHEs run the loop hotter. Steam leaves at the batch's heat-weighted coolant temperature: supercritical steam at 700 °C and above, superheated steam at 300 °C and above, plain steam below. Below ZPM no turbine takes supercritical steam, so a loop that runs that hot there wastes its steam: extract heat faster (more, larger or higher-tier PHEs) to keep it below 700 °C. One HU makes as much steam through a PHE as through a distilled water hatch (about 9 L), so a loop's advantage is the steam grade.

**Radiolysis.** Every 4 neutrons (`coolant_passage_neutrons_per_radiolysis`) absorbed in a passage cell consume 2 L of loop coolant and add 2 L of dissolved gas plus 1 L of dissolved oxygen to the loop: deuterium for water, tritium for heavy water. Other loop fluids do not undergo radiolysis. Dissolved gas increases pump rotor wear (cavitation) by a factor $1 + 10\,f_\text{gas}$, where $f_\text{gas}$ is the dissolved gas volume fraction. A **Pressurized Degasser** extracts dissolved gases from the loop to output hatches.

Loop failure modes are listed in §12.8.

### 9.8 Power conversion

The reactor's recoverable heat leaves as:

| Product | Typical use |
| :--- | :--- |
| Superheated steam (from distilled water) | High-pressure steam turbines |
| Heavy water steam | Turbines (with heavy water recovery) |
| Hot IC2 coolant | Heat exchangers (to steam) |
| Molten sodium / lead | External heat exchange |
| Closed loop heat | Pressurized Heat Exchanger → steam |

Conversion efficiency depends on the turbines and exchangers used and is not part of the core simulation. The standalone simulator estimates turbine requirements and output from the steam and coolant flows (§14).

---

### 9.9 Pressurized fluid heater

A **Pressurized Fluid Heater** puts electrical heat into a closed loop: to warm a loop up before a reactor joins it, to keep a molten coolant liquid, or to hold a loop at a set temperature. It is a 3×3 multiblock 3 to 11 long:

| Layer | Contents |
| :--- | :--- |
| Front | Heat-Proof Machine Casings; the controller at the bottom centre and a high-pressure input hatch in the centre |
| Middle (1 to 9 rings) | A ring of eight EBF heating coils around one Item Pipe Casing, repeated along the length |
| Back | Heat-Proof Machine Casings; a high-pressure output hatch in the centre |

Exactly one maintenance hatch (any type) and exactly one energy hatch (single- or multi-amp; laser hatches are refused) replace front or back casings; at least 13 casings remain. All coils must be one kind and all item pipe casings one tier. The heating coil tier caps the energy hatch's tier: Cupronickel takes up to LV, Kanthal MV, and so on up the EBF's coil order.

**Heating.** While enabled, every tick the heater draws up to $32 \cdot n \cdot 4^{k}$ EU/t from its energy hatch, for $n$ rings and coil tier $k$ (0 for Cupronickel), and takes whatever is there without a powerfail when there is less. That energy becomes heat at the MPTR's own EU-per-HU rate (4 HU per EU), times the coil efficiency (25 % for Cupronickel, rising linearly to 100 % for the highest coil) and the maintenance efficiency, and raises the heater's inner temperature by that heat over its heat capacity. Disabled, it draws nothing and adds no heat.

**Exchange with the loop.** Whether enabled or not, the heater exchanges heat with the coolant flowing through it as a core passage does (§9.7), in either direction: Dittus–Boelter convection over 4 m² per ring (`heater.baseExchangeArea` in `devices.yaml`), scaled by the item pipe casing tier's passage heat transfer factor and by the maintenance efficiency. Its heat capacity is that of a passage of the casing's tier, times the number of rings. With no flow it exchanges nothing.

**Item pipe casings.** The casing tier sets the heater's heat transfer and limits. Any tier works in a loop of any pipe material: the loop's pipes are what withstand its pressure, the casings what the heat crosses. The pressure limit is that of a loop pipe of the equivalent material:

| Item pipe casing | Pressure rated as | Max pressure | Max inner temp. | Passage heat capacity per ring |
| :--- | :--- | :--- | :--- | :--- |
| Tin | Cast iron | 16 bar | 1000 °C | 32 HU/°C |
| Brass | Steel | 35 bar | 1100 °C | 64 HU/°C |
| Electrum | Stainless steel | 70 bar | 1200 °C | 128 HU/°C |
| Platinum | Titanium | 140 bar | 1400 °C | 256 HU/°C |
| Osmium | Osmium | 600 bar | 1800 °C | 512 HU/°C |
| Quantium | Neutronium | 3000 bar | 2200 °C | 1024 HU/°C |
| Fluxed Electrum | Neutronium | 3000 bar | 2600 °C | 2048 HU/°C |
| Black Plutonium | Neutronium | 3000 bar | 3200 °C | 4096 HU/°C |

Unlike other loop devices, the heater has a pressure limit: its casings'. If its inner temperature passes its limit, the heater powerfails, disconnects from the loop, and the pump treats the loop as broken: a running loop ruptures (§12.8).

**Readouts.** The controller shows its inner temperature against its limit, heat capacity, exchange area, coolant flow, heat moved per tick, and EU drawn. An energy detector cover on the controller outputs the inner temperature as redstone: 0 at ambient, full strength at the cover's threshold (or the heater's limit when no threshold is set), inverted if the cover is. ProjectRed reads a 0–255 level, and bundled cables carry it on every channel or on one chosen channel. OpenComputers can read its state.

---

## 10. Byproducts and transmutation

| Process | Trigger | Output |
| :--- | :--- | :--- |
| Fuel depletion | Fuel item reaches max damage | Spent fuel product to output buses |
| Liquid fuel burnup | Every cycle | Spent liquid fuel, same volume consumed |
| Moderator depletion | Moderator reaches durability | Ash (graphite, carbon) |
| Coolant transmutation | Fast neutrons absorbed in a coolant hatch | Distilled water: 2 L consumed → 2 L deuterium + 1 L oxygen. Heavy water: → 2 L tritium + 1 L oxygen. |
| Loop radiolysis | Neutrons absorbed in a passage | Dissolved D₂/T₂ and O₂ in the loop (§9.7) |
| Coolant cell saturation | Coolant cell full | Hot coolant cell to output buses |
| Hot coolant | Coolant processing | Hot fluid to output hatches |

**Coolant transmutation** is evaluated once per fast-absorption event in a hatch holding at least 2 L of coolant: with $n$ fast neutrons absorbed, the event occurs with probability $\min(1,\ n\cdot\chi)$, $\chi = 0.025$ for distilled water and 0.01 for heavy water. Each event consumes 2 L.

All outputs go to the reactor's GregTech output buses and hatches. If there is no room, outputs are subject to GregTech's normal output handling.

---

## 11. Maintenance, structural damage and repair

### 11.1 Maintenance efficiency

The reactor has GregTech's six maintenance issues and one structural damage value $D_s \in [0, 100]$ %. Its **maintenance efficiency** is

$$\eta = \frac{\text{issues working}}{6}\cdot\left(1 - \frac{D_s}{100}\right)$$

$\eta$ does **not** affect fuel emission directly (fuel cells use their reactivity $R(T)$). It multiplies:

- the conductance of every coolant hatch (§9.2);
- the heat absorption rate of coolant cells (§9.3);
- radiovoltaic output (§8.4).

A degraded reactor therefore loses **cooling**, not power: neutron production and heat generation are unchanged while heat removal falls. This is a positive feedback toward overheating, and the reason maintenance is a safety concern rather than an efficiency concern.

### 11.2 Sources of damage and maintenance issues

| Event | Effect |
| :--- | :--- |
| Column above max operating temperature at end of cycle | +1 % structural damage per such column, per cycle |
| Thermal shock (§12.2) | +2 % structural damage, +1 maintenance issue |
| Escaped neutrons | Chance of +1 maintenance issue per minute (§4.7) |

While $D_s > 0$, the reactor always reports at least one maintenance issue, so $\eta \le 5/6 \cdot (1 - D_s/100)$.

### 11.3 Repair

Structural damage is repaired through maintenance. Each maintenance issue fixed reduces $D_s$ by 2 %, **but only while the average core temperature is at or below the tier's repair threshold** (10 % of max operating temperature: 120 °C for Electrum through 320 °C for Black Plutonium). Because damage forces an issue to remain open, repeated repair is possible: fix the issue, it reopens, fix it again.

Repair therefore requires the operator to cool the core well below operating temperature: in practice, to SCRAM and wait, or to keep the core cold enough throughout operation.

When damage reaches 0 and all issues are fixed, a reactor that had stopped for lack of maintenance resumes.

---

## 12. Failure modes and hazards

### 12.1 Summary

| Hazard | Trigger | Consequence |
| :--- | :--- | :--- |
| Overheating | Column > tier max temp | Non-fuel contents voided, +1 % damage per column per cycle |
| Thermal shock | Coolant into dry superheated hatch, or ambient ≥ coolant explosion temp | Coolant voided, +2 % damage, +1 maintenance issue |
| Meltdown | Structural damage reaches 100 % | Internal explosion, corium, fallout, controller permanently ruined |
| Beam explosion | Non-neutron beam into a beamline input | Explosion (power 20) at the beam hatch |
| Hot structure break | Structure broken while any column > 100 °C | Fuel, depleted rods, coolant items and all core fluids voided |
| Loop rupture | Over-pressure, over-temperature, freezing, impeller melting | Explosion at the pump, coolant spill, loop failure |
| Contents voiding (explosions disabled) | Any explosion or meltdown condition | All reactor contents voided, SCRAM |

### 12.2 Thermal shock

Thermal shock models coolant flashing on contact with a superheated hatch. It is checked at the start of every cycle for every core hatch that holds coolant (liquid fuel is exempt):

1. **Immediate evaporation.** If ambient temperature is at or above the coolant's explosion temperature, the coolant shocks on arrival.
2. **Dry-hatch injection.** A hatch is marked *dry* when it runs out of coolant or is found empty at the start of a cycle. If coolant then enters a dry hatch whose temperature is above the coolant's explosion temperature, it shocks. If the hatch is at or below that temperature, the dry flag is cleared and operation resumes normally.

A thermal shock voids all coolant in that hatch, adds 2 % structural damage, opens one maintenance issue, and raises a GregTech power-failure notification. The reactor keeps running.

Explosion temperatures are 100 °C for distilled water, 101.4 °C for heavy water, 883 °C for sodium, 1749 °C for lead, and none for IC2 coolant. Because boiling coolants operate at or above their boiling point, **every water-cooled hatch is above its explosion temperature during normal operation.** Letting such a hatch run dry and refilling it while hot always causes a shock. The cell must be cooled below 100 °C before refilling, or the supply must never be interrupted.

The shock repeats every cycle while the condition persists; continuous injection into a hot dry hatch accumulates 2 % damage per cycle (8 % per second).

### 12.3 Overheating

At the end of every cycle, each core column above the tier's maximum operating temperature:

- adds 1 % structural damage;
- loses its non-fuel contents: coolant fluid in a hatch, any non-fuel item in a bus (reflectors, moderators, vents, exchangers, coolant cells, insulators, radiovoltaics, neutron sources), and the control rod item in its rod hatch.

Fuel rods and liquid fuel are **not** voided by overheating. A pressure-tube passage counts as overheating if either the cell or the incoming loop coolant exceeds the limit.

Overheating is self-aggravating: voided coolant stops cooling, voided rods stop absorbing, voided reflectors and moderators change the neutron balance, and the remaining fuel keeps producing heat. Damage accumulates quickly: ten overheating columns add 10 % per cycle, reaching meltdown in 2.5 seconds.

### 12.4 Meltdown

When structural damage reaches 100 %, the reactor melts down:

1. The controller is marked **molten down**. Its structure check fails permanently; the controller must be replaced.
2. An explosion of power 20 occurs at the centre of the hollow space beneath the core (layers 3–4).
3. **Corium** is placed in the hollow space: a sphere of radius 2 blocks around a randomly selected point under the core, plus 4 further random positions.
4. A **fallout** entity is spawned at the centre of the core.
5. The reactor shuts down.

**Corium** is a flowing fluid, with maximum light level, 6× slower flow than lava, and blast resistance 500. It solidifies into solid corium on random ticks. Any entity in contact with it is set on fire for 30 s, takes 10 lava damage, is slowed, and, unless wearing full radiation hazmat, receives GregTech radioactivity and IC2 radiation.

**Fallout** keeps the 3×3 chunks around it loaded and holds a contamination value starting at 10 000. While contamination is positive, every second, every player within a horizontal radius of

$$r = 160 \cdot \frac{\text{contamination}}{10\,000}\ \text{blocks}$$

who is not wearing full radiation hazmat receives severe radioactivity and the maximum IC2 radiation effect. On creation it adds 2 000 000 pollution to its chunk. With GregTech pollution enabled, contamination decreases only when pollution is removed from affected chunks, at 62.5 contamination per 30 000 pollution removed (4.8 million pollution removed clears it completely); the fallout persists until then. With pollution disabled, contamination decays linearly over 10 minutes.

### 12.5 Explosions

| Cause | Location | Power |
| :--- | :--- | :--- |
| Non-neutron particle beam injected | Beamline input hatch | 20 |
| Meltdown | Hollow space under core | 20 |
| Coolant loop rupture | Coolant pump | $\min(25,\ 4 + P_\text{peak}/10)$ with $P_\text{peak}$ in bar |

Radiovoltaic output is delivered through GregTech's standard dynamo routine; GregTech's own dynamo behaviour applies to it.

### 12.6 Structure break while hot

If the multiblock is broken (or the controller removed) while any core column, rod hatch or passage is above 100 °C, then:

- every core bus loses fuel rods, depleted rods and coolant cells;
- every core hatch loses its fluid, including liquid fuel.

Configuration items (reflectors, moderators, heat vents, heat exchangers, insulators, radiovoltaic plates, neutron sources) are retained. Below 100 °C, nothing is voided.

### 12.7 Behaviour with machine explosions disabled

If GregTech machine explosions are disabled, any explosion or meltdown condition instead **voids all reactor contents**: every core bus, core hatch and control rod, every input and output bus and hatch, and the stored energy in energy and dynamo hatches. Structural damage is reset to 0, the reactor enters SCRAM and shuts down. No corium or fallout is produced.

### 12.8 Coolant loop failures

| Failure | Condition | Result |
| :--- | :--- | :--- |
| Burst | Pressure in a pipe, or in a Pressurized Fluid Heater, exceeds its rating (other devices and manifolds have none) | Loop ruptures |
| Melt | Coolant temperature exceeds a pipe's or device's rating (a PHE's is its sheetmetal's; manifolds have none) | Loop ruptures |
| Freeze | Segment below the coolant's freezing point | Loop ruptures |
| Solidification | Molten coolant below its melting point (configured freezing point; defaults to GregTech's) while filled | Loop ruptures; pump refuses to fill if already below |
| Impeller melting | Pump fluid temperature reaches the rotor's melting point | Pump fails |
| Rotor wear | Continuous; ×(1 + 10·gas fraction), × thermal factor | Rotor consumed; pump stops |
| Pump power loss | Insufficient EU | Pump stops; flow coasts down; core loses loop cooling |
| Heater powerfail | A Pressurized Fluid Heater's inner temperature passes its casing's limit | Heater disconnects; a running loop ruptures |
| Loop broken | A pipe or device block of a formed loop is removed or replaced, a device unforms, or the pump's structure breaks (checked every 5 seconds, in loaded chunks) | Loop unforms; a running loop ruptures |

On rupture, the pump explodes (strength above), 80 % of the reservoir is drained, and, for fluids configured to spill, the coolant is placed in the world at the rupture.

A loop failure does not affect the reactor directly, but the passage cells stop being cooled. If the reactor depends on the loop, an overheating cascade (§12.3) follows.

### 12.9 Common precursors

- **Coolant supply interruption** in a boiling-coolant hatch: stops cooling, then shocks on refill.
- **Maintenance neglect**: reduces cooling, not power (§11.1).
- **Operating below fuel peak temperature**: positive temperature coefficient; the core accelerates as it heats (§4.6).
- **Control rod hatches without mining pipes**: the rod cannot be withdrawn; conversely, removing pipes from a running reactor forces rods in.
- **Graphite above 1500 °C**: Wigner release adds heat that increases with temperature (§5.5).
- **Fuel cells in each other's fast-ray path**: high fast absorption; rapid fuel wear (§6.4).

---

## 13. Instrumentation and telemetry

### 13.1 Nuclear control hatch

A nuclear control hatch on the perimeter outputs a redstone signal proportional to one selected metric:

| Metric | Statistic | Full-scale reference |
| :--- | :--- | :--- |
| Column temperature | min / max / avg | Tier max operating temperature |
| Coolant item durability | min / max / avg | 100 % |
| Fuel item durability | min / max / avg | 100 % |
| Coolant hatch fill | min / max / avg | 100 % |
| Fuel hatch fill | min / max / avg | 100 % |
| Structural damage | — | 100 % |

The fraction (clamped to [0, 1]) is output as $\operatorname{round}(15f)$ on vanilla redstone, or $\operatorname{round}(255f)$ on ProjectRed bundled cable. In bundled mode the hatch can either broadcast one metric on all colours or output all metrics on separate channels.

Telemetry signals are commonly wired to control rod hatches to form feedback loops. Because control rods respond to the strongest channel by default, the hatch's input channel should be set explicitly when several signals are present.

### 13.2 Other interfaces

- **GUI.** The controller GUI shows the core grid with per-column temperature, flux, absorption, contents and durability, and plots of reactor history when a computer monitor cover is attached.
- **OpenComputers.** The controller exposes an OpenComputers environment for reading reactor state.
- **IC2 Nuclear Control.** A sensor card can be linked to the reactor (by using it on the controller or a rod hatch).
- **WAILA.** Shows column state on look-at.

---

## 14. The standalone simulator

The simulator (web application and CLI) runs the same core engine (`NuclearSimulationEngine`) as the in-game reactor, compiled to WebAssembly for the browser. Columns are represented by simulator tiles that reproduce the in-game hatch logic: emission, wear, coolant turnover, coolant cells, vents and exchangers, rods, thermal shock, overheating and damage.

In addition to the in-game mechanics, the simulator provides:

- a closed-loop model (hydraulics, Dittus–Boelter convection, PHE steam generation, radiolysis);
- a turbine calculator that estimates the number and configuration of large steam turbines (regular, superheated and supercritical steam stages) required to consume the reactor's output, and the resulting EU/t;
- calibration presets (peak configurations found by automated search);
- a fuel burnup multiplier (`fuelBurnupMultiplier`, default 1.0) for exploring durability balance, which has no in-game counterpart.

The simulator enforces the game's structure rules (shared code, `ReactorStructureRules`): the core is 5×5, 9×9 or 13×13 with its octagonal corners cut, its item pipe casings meet the chamber's minimum tier (§2.3), core hatches hold only fluids the tier accepts (`tiers.yaml` and `required_tier` in `fuels.yaml` and `components.yaml`, read from the same configuration as the game), high-pressure passages appear only at tiers that allow loops, and loops run only through passages. A core that breaks a rule would not form in the game, so it does not run: the web application shows the reasons, and the CLI's JSON lists them in `structureErrors`.

Results from the simulator are subject to the same stochastic processes as the game (random ray directions, stochastic rounding, probabilistic interactions); individual runs differ cycle to cycle, and conclusions should be drawn from averages.

---

## Appendix A — Configuration and global parameters

Configuration files are read from `config/modularnuclear/`. Each file may override any subset of the defaults shipped in the JAR; omitted fields keep their defaults. Entries can be removed with `remove: true`, `enabled: false`, or by mapping the entry's id to `null`.

| File | Contents |
| :--- | :--- |
| `physics.yaml` | Global physics constants |
| `tiers.yaml` | Pipe casing tiers |
| `fuels.yaml` | Solid and liquid fuels |
| `components.yaml` | Reflectors, moderators, control rods, heat vents, heat exchangers, insulators, radiovoltaics, coolant fluids, coolant items |

**Global physics parameters (`physics.yaml`):**

| Key | Default | Meaning |
| :--- | :--- | :--- |
| `hu_per_degree` | 32 | Heat capacity of a column other than a core hatch (HU/°C) |
| `core_hatch_heat_capacity` | 16 | Heat capacity of a tier-0 core hatch (HU/°C); a tier-$t$ hatch holds $2^t$ times this |
| `fission_heat_per_neutron` | 38 | Prompt heat per emitted neutron (HU) |
| `global_thermal_fission_multiplier` | 1.0 | Scales all $\phi_t$ |
| `global_fast_fission_multiplier` | 1.0 | Scales all $\phi_f$ |
| `fast_absorption_damage_multiplier` | 8.0 | Fuel/moderator wear per fast absorption |
| `thermal_absorption_damage_multiplier` | 1.0 | Fuel/moderator wear per thermal absorption |
| `t10_wear_factor` | 8.0 | Ratio of temperature wear at $T_{10}$ to wear at $T_\text{pk}$ |
| `wall_reflection_chance` | 0.0 | Probability that a neutron reaching the outer wall is reflected |
| `beam_energy_to_fast_neutron_multiplier` | 0.001 | Beam energy × rate → neutron packets |
| `standard_nbase_multiplier` / `_fission_multiplier` | 1.0 / 1.0 | Archetype multipliers |
| `dense_nbase_multiplier` / `_fission_multiplier` | 0.5 / 1.2 | |
| `excited_nbase_multiplier` / `_fission_multiplier` | 2.0 / 0.7 | |
| `breeder_nbase_multiplier` / `_fission_multiplier` | 1.0 / 1.0 | |
| `neutron_yield_multiplier` | 1.0 | Scales every fuel's base neutron emission, on top of its archetype multiplier |
| `grid_conduction_multiplier` | 1.0 | Scales conduction between neighbouring columns |
| `base_hatch_conductance` | 8.12 | Conductance $U$ of an MV core hatch into its coolant (§9.2) |
| `hatch_conductance_tier_ratio` | 2.65 | Factor by which $U$ grows per hatch tier (§9.2) |
| `coolant_passage_heat_transfer_multiplier` | 0.11 | Scales convective transfer between an MV passage and its loop (§9.7) |
| `coolant_passage_heat_transfer_tier_ratio` | 2.65 | Factor on a passage's convective transfer per voltage tier (§9.7) |
| `coolant_passage_heat_capacity` | 16 | Heat capacity of a tier-0 passage; tier $t$ holds $2^t$ times this (§9.7) |
| `coolant_passage_conduction` | 0.9 | Conduction coefficient $c$ of a passage column |
| `coolant_passage_scattering` | 0.8 | Scattering probability of a passage column |
| `coolant_passage_fast_absorption` / `_thermal_absorption` | 0.02 / 0.05 | Absorption probabilities of a passage column |
| `coolant_passage_default_moderation` | 0.05 | Passage moderation on no loop, or with a loop fluid that has no coolant entry |
| `coolant_passage_neutrons_per_radiolysis` | 4 | Neutrons absorbed per radiolysis event |
| `coolant_passage_minor_loss_k` | 2.5 | Minor-loss coefficient of a passage in its loop |

**Main mod configuration** (Forge config) additionally sets the cycle length (`ticksPerCycle`, default 5), the fallout contamination reduction per pollution removed ($62.5/30000$), item and fluid allow/block lists, and visual effects (Cherenkov glow, fallout overlay). Tier temperatures (maximum operating temperature, repair threshold) are set only in `tiers.yaml`; the Forge config's former `reactor_tiers` entries are reported in the log and dropped.

**Engine constants (not configurable):** fast-absorption and moderation heat 8 HU per neutron; non-fuel thermal absorption heat 4 HU; fuel thermal absorption heat $1.25 \times$ fission heat; conduction floor 0.01; 5 conduction sub-steps; vent/exchanger cost 0.25 EU/HU (configurable per component); coolant cell 25 HU per heat point; wall-wear base probability 1 %/min.

## Appendix B — Declared but inactive parameters

The following keys exist in the default configuration or component data but have no effect on the simulation in the current build. They are listed so that operators do not attempt to tune them.

| Key | Location | Status |
| :--- | :--- | :--- |
| `grid_heat_conduction`, `boundary_heat_loss`, `radiation_decay_factor`, `doppler_deficit_exponent`, `wall_maintenance_neutron_threshold`, `default_ambient_temp` | `physics.yaml` | Not read by the engine. Conduction uses per-material coefficients; ambient comes from the biome; wall wear uses the fixed formula in §4.7. |
| `self_cooling_rate` (vents), `switch_transfer_rate` (exchangers) | `components.yaml` | Shown in tooltips only. Throughput is set by `max_hu_t` and `max_eu_t`. |
| `heat_per_reaction` | `fuels.yaml` | Not used; prompt heat is the global `fission_heat_per_neutron`. |
| `damage_curve` (`peak_reactivity_mult`, `peak_damage_fraction`, `end_reactivity_mult`, `end_damage_fraction`) | `fuels.yaml` | Not used; reactivity does not depend on burnup. |
| `thermal_emission_*` | moderators | Thermal emission from hot moderators is computed nowhere (default maximum is 0 anyway). |
| `heat_capacity`, `freezing_temperature` | coolant fluids (core hatches) | Not used by the core cooling model; shown in the GUI and used only for display classification. The closed-loop engine has its own fluid properties. |
| `max_temperature` | heat vents, heat exchangers | Active, but 0 (disabled) in all defaults. |

## Appendix C — Formula summary

| Quantity | Formula |
| :--- | :--- |
| Emission per fuel cell per cycle | $N = \operatorname{round}[(\tilde N_0 + \operatorname{round}(\phi_t A_t' + \phi_f A_f'))\,R(T)]$ |
| Spontaneous emission | $\tilde N_0 = \operatorname{round}_\text{stoch}(N_0\, m_N\, \Delta t)$ |
| Reactivity (rising) | $R = 0.5 + 0.5\sin(\tfrac{\pi}{2}T/T_\text{pk})$ |
| Reactivity (falling) | $R = 0.05 + 0.475\,(1 + \cos\pi p)$, $p = (T - T_\text{pk})/(T_\text{fl} - T_\text{pk})$ |
| $T_{10}$ | $T_\text{pk} + 0.8526\,(T_\text{fl} - T_\text{pk})$ |
| Fast interaction | $p_\text{int} = \min(1, p_a + p_s)$; absorb w.p. $p_a/p_\text{int}$; else scatter, then moderate w.p. $p_m$ |
| Thermal step | abs $= \nu p_a$; scatter $= (\nu - \text{abs})p_s$ to 8 neighbours (1/6, 1/12); remainder lost |
| Conduction per sub-step | $\Delta T = (T_A - T_B)\,\tfrac{c_A + c_B}{2}\,\tfrac{\Delta t}{5}\,(1 - \max\delta)$ |
| Boundary loss per face per sub-step | $\Delta T = (T - T_\text{amb})\,\tfrac{c\,\Delta t}{10}\,(1 - \delta)$ |
| Fuel wear per cycle | $D = 0.25N + 8A_f + A_t + A(e^{k(T-20)} - 1)\Delta t$ |
| Hatch cooling per cycle | $Q = C\,(T - T_\text{sink})(1 - e^{-U\eta\Delta t/C})$, $C = 16 \cdot 2^\text{tier}$ |
| Hatch conductance | $U = 8.12 \cdot 2.65^{\text{tier}-2}$ |
| Coolant processed | $V = \min(V_\text{stored}, \operatorname{round}(Q/h))$ |
| Coolant cell absorption | $\min(32\Delta T/25,\ H_\text{max}\Delta t/100)\cdot\eta$ points × 25 HU |
| Vent per tick | $Q = \min(32|T - T_\text{amb}|, Q_\text{max})$, $E = \lceil Q/4 \rceil$ |
| Exchanger pair weight | $w_{ij} = \sum_e \tfrac{s_e}{n_e} \big/ \max(1, S_i, S_j)$, $S_i = \sum_e \tfrac{s_e}{n_e}(n_e - 1)$ |
| Energy shortfall scale | $f = (E_\text{stored} - n) / E_\text{demand}$, applied to all exchangers, then to all vents |
| Rod insertion | $x = \max(1 - 0.2\,n_\text{pipes},\ x_\text{target})$ |
| Rod absorption | $p_a^\text{rod} = \max(0.01, x\,a_\text{max})$ |
| Column absorption with rod | $\min(1, p_a^\text{rod} + p_a^\text{fol} + (1 - p_a^\text{rod} - p_m^\text{fol})p_a)$ |
| Radiovoltaic output | $P = P_\text{max}\tanh(\operatorname{artanh}(0.95)\,\Psi/\Psi_{95})$, $\Psi = (4A_f + A_t)/\Delta t$ |
| Wigner release | $\min(E, 2000\,\tfrac{(T-1500)^2}{(T-1500)^2 + 1000^2})$ HU/cycle |
| Maintenance efficiency | $\eta = \tfrac{\text{working}}{6}(1 - D_s/100)$ |
| Wall-wear probability | $0.01\,(1 + 9\bar N^2/(\bar N^2 + 10^6))$ per minute |
| Ambient | $(100\tau - 32)/1.8$ |
| Fallout radius | $160 \cdot \text{contamination}/10\,000$ blocks |

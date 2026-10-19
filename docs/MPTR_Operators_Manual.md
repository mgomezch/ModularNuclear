# Modular Pressure Tube Reactor (MPTR) and Coolant Loops — Operator's Manual

> **Scope.** This document describes how the Modular Pressure Tube Reactor (mod *ModularNuclear*) and the companion convective cooling system (mod *CoolantLoops*) work internally. It covers the neutron transport model, reactivity and fission, heat generation and conduction, every class of core component, control rods, telemetry and automation interfaces, every cooling option, maintenance, and every failure mode that can destroy fuel, coolant, components, the reactor, or the surrounding area.
>
> It is not a build guide. No reference layouts are given. The aim is that an operator who has read it can predict what a given core will do before it is loaded, and can explain what a running core is doing from its telemetry.
>
> The text describes the behaviour **as implemented** in the current code. Where the implementation departs from what the design evidently intends, the text says so in an *Implementation note*. All such notes are collected in [Appendix B](#appendix-b--known-implementation-deviations).

| | |
|---|---|
| **Controller** | Nuclear Reactor (MPTR) |
| **Sizes** | Tier 1 (5×5 grid), Tier 2 (9×9), Tier 3 (13×13) |
| **Footprint** | 7×7×5, 11×11×5, 15×15×5 |
| **Pipe tiers** | Electrum (EV) through Black Plutonium (UHV) |
| **Simulation period** | 20 ticks (1 cycle = 1 second) |
| **Power paths** | Direct EU (radiovoltaics → dynamo), steam / hot coolant from hatches, loop heat via the Pressurized Heat Exchanger |
| **Coolants (conductive)** | IC2 Coolant, Distilled Water, Heavy Water |
| **Coolants (convective loop)** | Distilled Water, Heavy Water, IC2 Coolant, Sodium, Lava (Nether only), Molten Cheese |
| **Failure modes** | Thermal shock, overheat voiding, structure-break voiding, dynamo overload explosion, meltdown (corium + fallout), loop rupture / explosion, impeller destruction |
| **Status** | Work in progress, not part of GTNH, balance not final |

---

## Contents

1. [Overview and design lineage](#1-overview-and-design-lineage)
2. [Multiblock structure](#2-multiblock-structure)
3. [Timebase, units and ambient conditions](#3-timebase-units-and-ambient-conditions)
4. [The neutron simulation](#4-the-neutron-simulation)
5. [Fission, reactivity and temperature feedback](#5-fission-reactivity-and-temperature-feedback)
6. [Heat generation, conduction and boundary loss](#6-heat-generation-conduction-and-boundary-loss)
7. [Fuels](#7-fuels)
8. [Non-fuel core components](#8-non-fuel-core-components)
9. [Control rods and SCRAM](#9-control-rods-and-scram)
10. [Telemetry, control hatches and automation](#10-telemetry-control-hatches-and-automation)
11. [Cooling](#11-cooling)
12. [Maintenance and reactor damage](#12-maintenance-and-reactor-damage)
13. [Failure modes and hazards](#13-failure-modes-and-hazards)
14. [The standalone web simulator](#14-the-standalone-web-simulator)
15. [ModularNuclear data-driven YAML configuration system](#15-data-driven-yaml-configuration-and-physics-tuning)
16. [CoolantLoops data-driven YAML configuration system](#16-coolantloops-data-driven-yaml-configuration-system)
- [Appendix A — Constants](#appendix-a--constants)
- [Appendix B — Known implementation deviations](#appendix-b--known-implementation-deviations)
- [Appendix C — Source map](#appendix-c--source-map)

---

## 1. Overview and design lineage

The MPTR is a grid reactor in the tradition of the IC2 nuclear reactor. It borrows its central ideas from the *Modern Industrialization* nuclear reactor: neutrons as discrete particles moving across a grid, fast and thermal populations, and per-cell temperature. The physics was then rebuilt on GregTech 5 multiblock infrastructure and extended in several ways:

- **Pressure tubes.** Each core cell is a vertical channel through a five-layer vessel. The top of the channel holds the cell's contents (a single item, a fluid, or the inlet of a high-pressure passage). The bottom may carry a control rod drive or the outlet of a high-pressure passage. In the analogy, the pipe casings of layer 1 are the calandria tubes. Their material sets the temperature limit of the whole core.
- **Per-cell thermal state.** Every cell has its own temperature and a uniform heat capacity. Heat comes from fission and from neutron absorption, flows to neighbouring cells by conduction, leaks out at the core boundary, and is removed by coolant.
- **Temperature-dependent reactivity.** Each fuel has a reactivity curve with a peak temperature and a floor temperature. Fuel cells are therefore self-regulating to some degree. A cell well below its peak runs at reduced output. A cell far above its peak gives out very few neutrons.
- **Two heat-removal paradigms.** *Conductive* cooling drains heat from a cell into a fluid held in a hatch, producing steam or hot coolant. *Convective* cooling circulates a pumped fluid through a passage cell and on to external equipment (the CoolantLoops mod).
- **Simulated hazards.** Thermal shock, voiding of overheated cell contents, cumulative reactor damage, and a meltdown with persistent environmental contamination.

The reactor makes no electricity by itself except through radiovoltaic plates, which feed a dynamo hatch directly. All other power is indirect: steam, hot coolant, or loop heat must be converted downstream by turbines or heat exchangers.

---

## 2. Multiblock structure

### 2.1 Tiers and core geometry

The reactor is a square vessel five blocks tall. The core grid is the interior of the vessel as seen from above. Its corners are cut away to give an approximately octagonal core.

| Tier | Grid | Outer footprint | Cut depth | Removed corner cells | Usable core cells |
|---|---|---|---|---|---|
| 1 | 5×5 | 7×7×5 | 1 | 4 | **21** |
| 2 | 9×9 | 11×11×5 | 2 | 12 | **69** |
| 3 | 13×13 | 15×15×5 | 3 | 24 | **145** |

The cut depth is $d = \max\left(1, \left\lfloor\frac{n-5}{4}\right\rfloor + 1\right)$. A cell at grid position $(x, y)$ in an $n \times n$ grid is removed when

$$\min(x, n-1-x) + \min(y, n-1-y) < d.$$

Removed cells are *void* for the neutron simulation. A neutron that enters one has escaped the core (§4.5).

### 2.2 Layer by layer

The vessel is described from top (slice 0) to bottom (slice 4).

| Slice | Content inside the core footprint | Content on the ring |
|---|---|---|
| 0 (top) | One **core access** block per cell, facing **up**. Allowed: Nuclear Core Bus, Nuclear Core Hatch (LV–UHV), or the **inlet** of a High-Pressure Passage hatch | Nuclear casing / ring hatches |
| 1 | GregTech **item pipe casings**, all of one material. Their material sets the **pipe tier** | Nuclear casing / ring hatches |
| 2 | Hollow chamber (no restriction) | Nuclear casing / ring hatches |
| 3 | Hollow chamber; the **controller** is front-centre of this slice | Nuclear casing / ring hatches |
| 4 (bottom) | Per cell, facing **down**: Nuclear casing, a **Control Rod hatch**, the **outlet** of a High-Pressure Passage hatch, or a **Beam Input Hatch** (`gtnhlanth.common.hatch.MTEHatchInputBeamline`, ID 10503) | Nuclear casing / ring hatches |

The "ring" is the shell of the vessel around the core footprint. It must contain:

- at least **50 Nuclear Casings**;
- **exactly one Maintenance hatch**;
- **at most one Dynamo hatch**. Normal and multi-amperage dynamos are accepted; laser source hatches are not;
- any number of **output buses / output hatches**. They receive depleted fuel, ejected components, steam and hot coolant;
- any number of **Nuclear Control hatches** (telemetry).

Input buses, input hatches and energy hatches are **not** accepted on the ring. Everything that enters the core goes through the per-cell access blocks of slice 0, with the sole exception of external particle beams entering through bottom Beam Input Hatches on slice 4.

### 2.3 Core access blocks

| Block | ID | Holds | Notes |
|---|---|---|---|
| Nuclear Core Bus | 32100 | 1 item (stack limit 1) | Automation can insert only into an empty bus, and can never extract. Spent fuel and ejected components leave through the ring output buses. Accepts fuel rods, components, and the Neutron Source. |
| Nuclear Core Hatch (LV … UHV) | 32101–32109 | One fluid, capacity $8000 \cdot 2^{\text{tier}}$ L | LV 16 000 L, MV 32 000 L, … UHV 4 096 000 L. The hatch tier also sets its conductive cooling strength (§11.2). |
| High-Pressure Passage | — | Nothing | Top block = inlet, bottom block = outlet. They must be in the same grid column (§11.4). |
| Beam Input Hatch | 10503 | Particle beamline packets | Slice 4 only, facing **down**. Must be placed directly opposite a Nuclear Core Bus in the same grid column $(x, y)$ (§8.7). |

### 2.4 Pipe tier

The slice-1 pipe casings set the reactor's **pipe tier**. The pipe tier sets the **maximum operating temperature** $T_{max}$ that applies to every cell, and the repair-temperature threshold.

| Pipe tier | Casing material | Voltage tier | $T_{max}$ (°C) | Repair allowed below (°C) |
|---|---|---|---|---|
| 0 | Electrum | EV | 1200 | 120 |
| 1 | Platinum | IV | 1400 | 140 |
| 2 | Osmium | LuV | 1800 | 180 |
| 3 | Quantium | ZPM | 2200 | 220 |
| 4 | Fluxed Electrum | UV | 2600 | 260 |
| 5 | Black Plutonium | UHV | 3200 | 320 |
| — | (unrecognised) | — | 800 | 80 |

The repair threshold is 10% of $T_{max}$, compared against the *average* core temperature (§12).

### 2.5 Fluid tier gating

A Nuclear Core Hatch accepts a fluid only if the reactor's pipe tier is at least the fluid's required tier. The reactor operates on an explicit fluid allowlist; any fluid not recognized by the reactor is rejected upon insertion. In particular, plain water and Naquadah-based liquid fuels are not accepted and cannot be pumped into core hatches.

| Fluid | Required pipe tier |
|---|---|
| IC2 Coolant | 0 (Electrum) |
| Distilled Water | 1 (Platinum) |
| Heavy Water | 3 (Quantium) |
| Thorium liquid fuel | 0 (excited: 4) |
| Uranium liquid fuel | 1 (excited: 3) |
| Plutonium liquid fuel | 2 (excited: 4) |
| Uranium hexafluoride | 2 |
| Any other fluid (plain water, Naquadah fuels, etc.) | *Disallowed (rejected)* |

### 2.6 Structure validation

The structure check fails, and the controller reports the reason, in these cases:

- the slice-1 pipe casings are not a recognised material, or are mixed;
- a High-Pressure Passage hatch is present on a reactor with pipe casings below Osmium (LuV) ("High-pressure cooling requires Osmium or higher item pipe casings"). Note that while item pipe casings unlock reactor size tiers, players may freely install higher-tier casings on smaller reactors if desired (e.g. a 5×5 reactor with Osmium casings is fully valid and can use High-Pressure Passages);
- a passage inlet has no outlet in the same grid column, or the reverse;
- a **Beam Input Hatch** is placed beneath a cell that is not a Nuclear Core Bus in the same grid column ("Beam input hatches must be placed directly opposite a Nuclear Core Bus in the same column");
- a slice-0 block does not face up, or a slice-4 block does not face down;
- a **Nuclear Control Hatch** is not oriented strictly away from the reactor center or is not upright relative to the controller ("Nuclear control hatches must face outward away from the structure and be upright"):
  - on flat walls (front, back, left, right), it must face strictly perpendicular outward from that wall (Forward, Back, Left, or Right);
  - on corner cut-away columns, it must face one of the two outward directions for that corner quadrant (e.g. Forward or Left for Front-Left); inward and vertical facings cause structure validation to fail;
  - on lateral faces of the outer casing wall, it must be upright relative to the controller (`hatchUp == controllerUp`), strictly preventing wall-sharing between upright and inverted reactors;
- a **Maintenance Hatch** is not oriented strictly away from the reactor center or is not upright relative to the controller ("Maintenance hatch must face outward away from the structure and be upright"):
  - like Nuclear Control Hatches, on flat walls it must face perpendicular outward, and on corner cut-away columns it must face one of the outward directions; inward and vertical facings cause structure validation to fail;
  - the maintenance hatch must also share the controller's upright orientation (`hatchUp == controllerUp`), strictly preventing wall-sharing between upright and inverted reactors;
- ring requirements are not met (casing count, maintenance hatch, dynamo count).

---

## 3. Timebase, units and ambient conditions

### 3.1 The simulation cycle

The controller runs one **simulation cycle every 20 ticks**, i.e. once per second at full TPS. As long as the controller is in place and the structure is formed/valid, **the simulation cannot be stopped or paused by any external switch**:

- nuclear reactions do not have a power switch; the only way to stop them is to physically stop the flow of neutrons (e.g. by inserting control rods);
- **SCRAM** does not pause or freeze the simulation; it simply forces all control rods to 100% insertion (§9.5) while neutron transport, heat generation, conduction, and cooling continue uninterrupted;
- **soft mallet** work-allowed toggles, redstone machine disable covers, and maintenance states (even severe maintenance failure with all six issues present) have **no effect** on the core simulation loop;
- the only event that stops the simulation is structural destruction (such as a catastrophic meltdown explosion that shatters internal casings, unforming the multiblock).

Inside a cycle, the controller does the following in strict order:

1. **Thermal-shock scan** of every hatch (§13.1). If a shock occurs, the affected hatch's coolant voids, damage and maintenance issues accrue, but the simulation carries on normally for the rest of the cycle.
2. **`simulate()`**. This is neutron generation, transport, heat deposition, conduction and per-cell processing (§4–§8).
3. **Direct EU summation.** Radiovoltaic output is totalled and multiplied by maintenance efficiency.
4. **Coolant output rate accounting** (steam / hot coolant produced this cycle).
5. **Overheat check** of every cell (§13.2).
6. **Telemetry update** and Nuclear Control hatch outputs (§10).
7. **Meltdown check.** If reactor damage ≥ 100, a meltdown is triggered (§13.5).

The reactor core simulation executes at a fixed rate of exactly **one cycle per second** (1 s = 20 game ticks). All rates and quantities measured, produced, or consumed during the reactor simulation cycle are denominated strictly as **per-second** (`/s`):
- **Neutron generation, flux, and absorption**: denominated in neutrons per second (`n/s` or $\text{neutrons/cm}^2/\text{sec}$).
- **Core heat generation and removal**: denominated in Heat Units per second (`Hu/s`).
- **Coolant consumption and steam/hot coolant production**: denominated in Litres per second (`L/s`).

The word "tick" and its abbreviation `/t` are **never** used to refer to the reactor simulation cycle. The sole metric denominated in `/t` is **direct EU power output** from radiovoltaic cells, because direct electrical power is emitted into GregTech dynamo hatches continuously on **every game tick** (20 times per second), delivering true `EU/t`.

### 3.2 Heat units and electricity accounting

All thermal accounting in the MPTR is handled explicitly in **Heat Units (Hu)**. The term "EU" is reserved strictly for GregTech electrical power, such as radiovoltaic plate generation in dynamos, multiamp dynamo outputs, steam turbine generation, and electric pump motor power.

Every cell, whatever its contents, has the same heat capacity:

$$C = 32 \ \text{Hu}/{}^\circ\text{C} \qquad (\texttt{HU\_PER\_DEGREE})$$

A cell that receives 320 Hu of heat in one cycle with no losses rises by 10 °C ($1 \text{ Hu} = 128 \text{ J}$). Heat Units are the thermodynamic accounting unit for the core. Their exchange rates to coolant (§11) are set per coolant and do not conserve energy against GregTech's steam valuation. Treat them as a physical thermal currency distinct from electrical EU.

### 3.3 Ambient temperature

The ambient temperature comes from the biome at the controller:

$$T_{amb} = \frac{100 \cdot t_{biome} - 32}{1.8} \ {}^\circ\text{C}$$

where $t_{biome}$ is the vanilla biome temperature. If the biome is unavailable, $T_{amb} = 20$ °C.

| Biome (temperature) | $T_{amb}$ |
|---|---|
| Ice Plains (0.0) | −17.8 °C |
| Plains (0.8) | 26.7 °C |
| Desert, Savanna, Nether (2.0) | 93.3 °C |

Ambient temperature matters in four places:

- no cell is ever cooled below $T_{amb}$;
- boundary heat loss is proportional to $T - T_{amb}$;
- the IC2-Coolant hatch heat sink sits at $T_{amb}$;
- fuel wear has a temperature term measured from $T_{amb}$ (§7.3).

Water-based hatches are never cooled below $\max(0, T_{amb})$, and their sink is $\max(T_{amb}, T_{boil})$.

A hot biome raises every equilibrium temperature in the core. A cold biome lowers it.

---

## 4. The neutron simulation

### 4.1 Model summary

Every cycle the core simulates one generation of neutrons in a two-dimensional grid, in two stages:

1. **Fast neutrons** are born in fuel cells and travel as discrete **packets** ("rays") along the four cardinal directions. A packet random-walks across the grid and is resolved stochastically (Monte Carlo) at each cell it enters: it is absorbed, scattered, or scattered *and* moderated. Moderation turns the packet into thermal neutrons.
2. **Thermal neutrons** spread **deterministically** from the cells where they were moderated, using an eight-neighbour diffusion stencil. In each cell a fixed fraction is absorbed.

Neutrons that leave the grid edge or enter a removed corner cell have **escaped**. They are lost and do no further work (but see §12.3).

Neutron populations and interactions are grounded in the physical definitions established by the US Nuclear Regulatory Commission (NRC):
- **Neutron flux** ($\phi = n \cdot v$): A measure of the intensity of neutron radiation, determined by the rate of flow of neutrons. Calculated as neutron density $n$ ($\text{neutrons/cm}^3$) multiplied by neutron velocity $v$ ($\text{cm/sec}$), expressed in **$\text{neutrons/cm}^2/\text{sec}$** (scaled at $1.0 \times 10^{13} \text{ neutrons/cm}^2/\text{sec}$ per simulated neutron).
- **Neutron fluence** ($\Phi = \int \phi \, dt$): The time-integrated neutron flux, expressing the total neutron exposure or dose accumulated over time, measured in **$\text{neutrons/cm}^2$**.

The random-number generator is a single unseeded `java.util.Random` shared by all reactors. Two identical cores do not produce identical cycle-by-cycle results. Averaged over many cycles, they behave the same.

### 4.2 Per-cell interaction properties

Each cell presents five properties to the neutron model:

| Symbol | Meaning |
|---|---|
| $p_{a,f}$ | probability that a fast packet entering the cell interacts by **absorption** |
| $p_{s,f}$ | probability that a fast packet entering the cell interacts by **scattering** |
| $p_{m}$ | probability that a scattered fast packet is **moderated** (becomes thermal) in this cell |
| $p_{a,t}$ | fraction of thermal neutrons arriving at the cell that are **absorbed** there |
| $p_{s,t}$ | probability that non-absorbed thermal neutrons undergo **thermal scattering** and diffuse outward into adjacent cells |
| $\delta$ | insulation dampening; suppresses heat deposition and conduction (§6) |

The thermal scattering probability $p_{s,t}$ governs thermal diffusion (§4.4). Non-absorbed thermal neutrons that fail to scatter do not diffuse into adjacent cells and instead escape the active core into the reactor vessel wall.

The values for every component are given in §7 and §8. A control rod beneath a cell changes that cell's $p_{a,f}$, $p_{a,t}$, $p_{s,f}$ and $p_{s,t}$ (§9).

### 4.3 Fast-neutron packets

**Emission.** A fuel cell emits $N$ fast neutrons this cycle (§5). They are divided as evenly as possible among $k$ packets. $k$ depends on the fuel item:

| Fuel item | Packets $k$ |
|---|---|
| GT/IC2 single / dual / quad rod | 1 / 2 / 4 |
| The Core (32-cell naquadah rod) | 32 |
| Custom mixed fuel rod | 4 |
| Liquid fuel in a hatch | 4 |
| Unrecognised fuel, by name fallback | 16 / 4 / 2 / 1 |

Each packet picks one of the four cardinal directions uniformly at random. More packets do not mean more neutrons. They mean a smoother, more isotropic spread of the same neutrons. A single rod sends its entire output in one random direction each cycle, so its neighbours see strongly fluctuating flux.

**Travel.** A packet moves one cell per step, for at most $2(s_x + s_y)$ steps (20, 36 or 52 for the three tiers). It never interacts with the cell it was born in. On entering a cell:

1. If the cell lies outside the grid or is a removed corner, the packet **escapes**.
2. The cell registers the packet as fast flux.
3. Interaction roll. With probability $p_{tot} = \min(1, p_{a,f} + p_{s,f})$ an interaction occurs. Otherwise the packet passes straight through.
4. If it interacts, the interaction is an **absorption** with probability $p_{a,f}/p_{tot}$. The packet ends. The cell receives **8 Hu of heat per neutron** and is credited with a fast absorption.
5. Otherwise the interaction is a **scatter**:
   - **Direction.** If the cell is a near-perfect scatterer ($p_{s,f} \ge 0.90$ and $p_{a,f} \le 0.05$; reflectors and graphite), the packet **reverses** direction. For any other cell it picks a new random cardinal direction (which may be the same one, or a reversal).
   - **Moderation.** With probability $p_m$ the packet is **thermalised** in this cell. Its neutrons are moved into the cell's thermal pool, the cell receives 8 Hu per neutron, and the packet ends.
6. A packet that uses up its step budget is counted as escaped.

The net effect is that each cell acts on fast neutrons as one of three things:

- **Transparent:** low $p_{tot}$. Empty cells and most fluids at low density.
- **Absorbing:** high $p_{a,f}$. Radiovoltaics, absorbers, rods, Naquarite foil.
- **Scattering:** high $p_{s,f}$. Reflectors, moderators, water. A scattering cell turns packets back and, if $p_m$ is significant, thermalises them in place.

Fast absorption by **fuel** cells can induce fast fission, but with a cross section 10× to 35× lower than thermal absorption ($m_{fiss,f} \ll m_{fiss,t}$; §5.1), and induces severe physical wear on the fuel rod cladding ($8\times$ damage compared to thermal absorption; §7.3). Dedicated moderators are strongly required to thermalise fast flux into safe, productive thermal fission.

### 4.4 Thermal diffusion and scattering gating

Every cell's thermal pool is then propagated by a deterministic stencil gated by the cell's **thermal scattering probability** ($p_{s,t}$). It runs for at most $\max(12, 2(s_x + s_y))$ steps, or until fewer than 0.001 thermal neutrons remain in flight. In each step, at each cell holding $n$ thermal neutrons:

1. $n \cdot p_{a,t}$ neutrons are **absorbed** in the cell. Heat deposited per absorbed thermal neutron:
   - fuel cell: $38 \times 1.25 = 47.5$ Hu;
   - any other cell: 4 Hu.
2. From the non-absorbed survivors $n_{surv} = n(1 - p_{a,t})$, only the fraction that successfully **scatters** ($n_{scatter} = n_{surv} \cdot p_{s,t}$) is permitted to diffuse into surrounding cells. The remaining unscattered fraction:

   $$n_{unscattered} = n_{surv} \cdot (1 - p_{s,t})$$

   fails to maintain coherent diffusion within the core lattice and **escapes directly to the vessel walls** ($\text{neutronsEscaped} += n_{unscattered}$).
3. The scattered population $n_{scatter}$ is distributed to the eight neighbours via the diffusion stencil:
   - each cardinal neighbour receives $\tfrac16 \cdot n_{scatter}$;
   - each diagonal neighbour receives $\tfrac1{12} \cdot n_{scatter}$.

   (The geometric weights sum to 1.) Shares that land outside the grid boundary or in a removed corner cell also escape.
4. Neutrons still in flight when the step limit is reached are counted as escaped.

The first absorption step happens in the cell where the neutrons were moderated. A good moderator therefore needs *low* thermal absorption, or it consumes its own product. Heavy water (0.01 absorption, 0.85 thermal scatter) and graphite (0.009 absorption, 0.95 thermal scatter) are ideal. Distilled water (0.10 absorption, 0.70 thermal scatter) and IC2 coolant (0.12 absorption, 0.65 thermal scatter) are less efficient.

Because diffusion is gated by $p_{s,t}$, **material choice actively governs thermal transport**:
- **Reflectors** (beryllium, carbon: $p_{s,t} = 0.98$) almost completely reflect and scatter thermal neutrons back into adjacent core cells, preventing perimeter thermal leakage.
- **Coolant passages and moderators** ($p_{s,t} = 0.70 \text{--} 0.85$) facilitate thermal flux transfer across the lattice.
- **Fuels** ($p_{s,t} = 0.10$, $p_{a,t} = 0.80$) absorb heavily and do not diffuse unabsorbed neutrons freely across unreflected gaps, requiring surrounding moderators and reflectors to maintain criticality.
- **Absorbers** ($p_{s,t} = 0.05 \text{--} 0.15$) prevent thermal neutrons from diffusing past them.

### 4.5 Escape and the vessel wall

Neutrons count as escaped from the active core under exactly three physical conditions:
1. **Unscattered thermal remainder:** In the thermal diffusion phase (§4.4), non-absorbed neutrons that fail to scatter ($n_{\text{unscattered}} = n_{\text{surv}} \cdot (1 - p_{s,t})$) cannot diffuse through the core lattice and escape directly to the vessel walls.
2. **Exhausted step budget:** Fast packets that take all $2(s_x + s_y)$ steps without being absorbed, moderated, or exiting the grid, or thermal flux residual in flight when the step limit is reached, run out of steps fully and escape. Packets that are absorbed or moderated on their final step terminate normally and are *not* counted as escaped.
3. **Boundary and void leakage:** Fast packets or diffusing thermal neutrons that scatter into an unformed/null corner cell or across the core perimeter to an unreflected vessel wall escape. (The reactor vessel wall has zero reflection, so perimeter leakage strikes the casing directly.)

Neutrons that escape the active core strike the reactor pressure vessel and outer casings. Escaped neutrons drive vessel maintenance wear (§12.3) and, when a water pool is installed above the core, produce visible Cherenkov radiation whose intensity scales dynamically with the leakage rate (§13.7).

### 4.6 What the operator sees

The controller tracks, per cycle:

- total neutrons generated;
- fast neutrons absorbed;
- thermal neutrons absorbed;
- neutrons escaped.

The Inspect-Core window can overlay per-cell fast and thermal flux and absorption (§10.4).

> [!NOTE]
> **Implementation note.** Neutron conservation is strictly preserved: every generated neutron is accounted for exactly once as either fast-absorbed, thermal-absorbed, or escaped. Packets absorbed or moderated on their final step budget are accurately recorded without phantom leakage or double-counting. Thermal-scatter values defined for each component and displayed in NEI directly gate thermal diffusion stencil throughput and dictate core escape rates.

---

## 5. Fission, reactivity and temperature feedback

### 5.1 Neutron production

Each fuel cell computes its neutron output once per cycle, at the start of the cycle, from its own state:

$$N_{chain} = \operatorname{round}\left(A_{t}^{prev} \cdot m_{fiss,t} \cdot g_t + A_{f}^{prev} \cdot m_{fiss,f} \cdot g_f\right)$$

$$N = \operatorname{round}\left((N_{base} + N_{chain}) \cdot R(T_{cell})\right)$$

where

- $N_{base}$ is the fuel's intrinsic (spontaneous) neutron output per cycle (§7.1);
- $A_t^{prev}$ is the number of **thermal** neutrons the cell absorbed in the **previous** cycle. Only the share credited to the fuel counts; the share taken by a control rod does not (§9.3);
- $A_f^{prev}$ is the number of **fast** neutrons the cell absorbed in the **previous** cycle;
- $m_{fiss,t}$ is the fuel's **thermal fission multiplier** (§7.1);
- $m_{fiss,f}$ is the fuel's **fast fission multiplier** (§7.1). Fast neutrons induce fission with a significantly lower cross section than thermal neutrons ($m_{fiss,f} \ll m_{fiss,t}$). For fertile isotopes like U-238, $m_{fiss,f} \approx 0.03$, while for fissile isotopes like U-235 and Pu-239, $m_{fiss,f}$ ranges from $0.12 \dots 0.16$;
- $g_t, g_f$ are global thermal and fast fission multipliers (default 1.0 in `physics.yaml`);
- $R(T)$ is the fuel's **reactivity** at the cell's current temperature (§5.2).

All emitted neutrons are fast. Each emitted neutron immediately deposits **38 Hu** of prompt fission heat in the fuel cell.

Consequences:

- Fission is predominantly driven by **thermal absorption**, with a secondary, material-dependent **fast fission** contribution.
- The chain reaction has a **one-cycle lag**. Neutrons absorbed this second become fast neutrons next second.
- Maintenance state does **not** affect fission. A badly maintained reactor still makes neutrons and heat. It only loses cooling capacity (§12).

### 5.2 The reactivity curve

Every fuel has a peak temperature $T_{pk}$ and a floor temperature $T_{fl}$. The reactivity factor is:

$$
R(T) =
\begin{cases}
0.5 & T \le 0 \\[4pt]
0.5 + 0.5 \sin\left(\dfrac{\pi}{2} \cdot \dfrac{T}{T_{pk}}\right) & 0 < T \le T_{pk} \\[10pt]
0.05 + 0.95 \cdot \dfrac{1 + \cos(\pi p)}{2}, \quad p = \dfrac{T - T_{pk}}{T_{fl} - T_{pk}} & T_{pk} < T < T_{fl} \\[10pt]
0.05 & T \ge T_{fl}
\end{cases}
$$

So:

- A cold fuel cell runs at **50%**.
- Reactivity rises smoothly to **100% at the peak**.
- It falls along a raised-cosine to **5% at the floor**, and stays there.
- It is 52.5% at $\tfrac12(T_{pk}+T_{fl})$, and 10% at $T_{pk} + 0.8526\,(T_{fl}-T_{pk})$.

The curve depends only on temperature. Burnup does not change reactivity: a rod at 1% durability is exactly as reactive as a fresh one.

### 5.3 Feedback and stability

Reactivity multiplies both the base output and the chain term. Each fuel cell therefore has a strong **negative temperature coefficient above its peak** and a **positive coefficient below it**. This has three operational consequences.

1. **Start-up is self-accelerating.** A cold core starts at half output. As the fuel heats towards $T_{pk}$, output rises, which heats the fuel faster. Below the peak there is no natural brake other than cooling.
2. **Above the peak the core is self-limiting.** If cooling cannot hold the fuel at its peak, the temperature rises into the falling branch until heat production matches heat removal. Because $R$ bottoms out at 0.05, the fuel's self-limited output never reaches zero.
3. **Equilibrium can lie above $T_{max}$.** Self-limitation only helps if the fuel's temperature for a given output stays below the pipe tier's limit. Many fuels have floors of 2 200–3 200 °C, far above an Electrum core's 1 200 °C. Such a fuel can heat the core past the overheat limit long before its own reactivity collapses. Fuel selection and pipe tier must be matched (§13.2).

The loop gain of the chain reaction is set by geometry. Roughly: the fraction of a fuel cell's fast neutrons that are moderated, times the fraction of those thermal neutrons absorbed back in fuel cells, times $m_{fiss}$. If this product summed over the core exceeds 1 at the operating reactivity, output grows from cycle to cycle. Growth stops only when temperature pushes $R$ down. If it is below 1, output settles at a finite multiple of the base output. Control rods (§9) and absorbers reduce the gain directly. Moderators and reflectors increase it.

### 5.4 Average reactivity

The controller displays **average reactivity**: the mean of $R(T_{cell})$ over all fuel cells. Since R is evaluated at each cell's temperature, this is purely a *thermal* indicator.

- Values near 1.0 mean the fuel is at its peaks.
- Values well below 1.0 mean the fuel is either cold (≤ 0.5 implies essentially ambient) or overheated.
- The number alone cannot tell the two apart. Compare against core temperature.

There is no k-effective or multiplication-factor readout. The closest proxy is the trend of "neutrons generated" between cycles.

---

## 6. Heat generation, conduction and boundary loss

### 6.1 Heat sources

All core heat comes from the neutron simulation. It is accumulated during the cycle and deposited all at once after transport:

| Event | Heat deposited (Hu per neutron) | Where |
|---|---|---|
| Neutron emission (prompt fission) | 38 | emitting fuel cell |
| Fast absorption | 8 | absorbing cell |
| Fast moderation (thermalisation) | 8 | moderating cell |
| Thermal absorption in fuel | 47.5 (= 38 × 1.25) | absorbing fuel cell |
| Thermal absorption elsewhere | 4 | absorbing cell |

Absorption and moderation heat is **not deposited** in a cell whose insulation dampening is 1.0 (Naquarite foil). That cell absorbs neutrons without heating. Prompt fission heat is always deposited.

Two derived figures are useful. A fuel cell that emits $N$ neutrons and absorbs $A_t$ thermal neutrons heats itself by

$$Q_{fuel} = 38N + 47.5A_t + 8A_f \ \text{Hu per cycle},$$

i.e. $\Delta T = Q_{fuel}/32$ °C before any losses. A moderator that thermalises $M$ neutrons and absorbs $A_t$ of the resulting thermal neutrons heats by $8M + 4A_t$.

Most core heat therefore stays in the fuel. Only the moderation and absorption fractions are spread to other cells. Moderators, reflectors and absorbers do warm up, but much more slowly than fuel.

### 6.2 Conduction between cells

After heat deposition, heat is conducted between edge-adjacent cells (no diagonals). There are **5 substeps**. All flows in a substep are computed from the temperatures at the start of that substep and applied together (Jacobi iteration).

Every cell has a conduction coefficient $c$, floored at 0.01, and an insulation dampening $\delta \in [0,1]$. In each substep, for every adjacent pair with $T_A > T_B$:

$$\Delta T_{A \to B} = (T_A - T_B) \cdot \frac{c_A + c_B}{2 \cdot 5} \cdot \left(1 - \max(\delta_A, \delta_B)\right)$$

This amount is subtracted from $T_A$ and added to $T_B$. Because every cell has the same heat capacity, this conserves heat.

Over a full cycle the pair exchanges roughly $(T_A - T_B)\,\bar c$ °C each way. Representative pairings:

| Pair | $\bar c$ | Approximate fraction of ΔT equalised per cycle |
|---|---|---|
| fuel–fuel (0.05/0.05) | 0.05 | ~10% |
| fuel–graphite (0.05/0.03) | 0.04 | ~8% |
| fuel–reflector (0.05/0.15) | 0.10 | ~19% |
| fuel–coolant cell (0.05/0.40) | 0.225 | ~38% |
| fuel–IC2-coolant hatch (0.05/0.50) | 0.275 | ~44% |
| fuel–HP passage (0.05/0.90) | 0.475 | ~65% |
| anything–Naquarite foil | — | 0% |

Fuel conducts poorly. A fuel cell surrounded by other fuel cells stays much hotter than one bordered by a hatch or passage. Fuel cells in the middle of a large block are the usual first cells to overheat.

### 6.3 Boundary loss

Each side of a cell that faces the grid edge or a removed corner cell loses heat to ambient in every substep:

$$\Delta T_{loss} = (T - T_{amb}) \cdot \frac{c}{2 \cdot 5} \cdot (1 - \delta)$$

That is roughly $(T - T_{amb})\,c/2$ per exposed side per cycle. A perimeter fuel cell ($c = 0.05$) with one exposed side loses about 2.5% of its excess over ambient each cycle. Boundary loss is small but not negligible in Tier 1 cores, where 12 of the 21 cells touch the boundary.

After conduction, no cell may be below $T_{amb}$.

### 6.4 Insulators

Insulating components set $\delta$, which scales down every conduction path touching that cell, including boundary loss:

| Insulator | $\delta$ | Effect |
|---|---|---|
| Naquarite foil | 1.0 | Complete thermal break; heatless neutron sink (absorbs fast and thermal neutrons with probability 1.0) |
| Mica insulator | 0.60 | conduction reduced to 40% |
| GalaxySpace thermal cloth (Tier 2) | 0.40 | reduced to 60% |
| Basic thermal cloth | 0.20 | reduced to 80% |

The weaker insulators also act as nearly transparent cells for neutrons ($p_a = 0.01$, $p_s = 0.05$). Use them to isolate a hot fuel region from components with low temperature tolerance, or to stop a cooled region pulling heat out of fuel that should run hot.

### 6.5 Per-cell processing

After conduction, every cell runs its own update (§7–§8 and §11): fuel wear, coolant boiling, item coolant absorption, vents, radiovoltaic generation, liquid fuel burn. Cooling is applied **after** conduction within the same cycle. The temperatures shown in telemetry are the post-cooling values.

---

## 7. Fuels

### 7.1 Fuel parameters

Fuel specifications and physics behaviors are **data-driven** and defined in the YAML configuration (`config/modularnuclear/fuels.yaml` via `ReactorDataRegistry` / `FuelData`). Server administrators can fine-tune every operational parameter on an individual fuel rod or fluid basis without code modifications.

Rather than relying on runtime substring matching, each fuel entry explicitly configures:

- `packet_count`: Number of discrete neutron rays / packets emitted per 20-tick cycle (e.g. 1 for single rods, 2 for dual rods, 4 for quad rods and liquid fuels, 16 for The Core).
- `base_neutrons`: Base unscaled neutron yield emitted per cycle ($N_{base}$).
- `peak_reactivity_temp` ($T_{pk}$): Temperature (°C) of maximum reactivity.
- `floor_temp` ($T_{fl}$): Temperature (°C) where reactivity drops to zero and thermal damage accelerates.
- `packet_count`: Number of discrete neutron rays / packets emitted per 20-tick cycle (e.g. 1 for single rods, 2 for dual rods, 4 for quad rods and liquid fuels, 16 for The Core).
- `base_neutrons`: Base unscaled neutron yield emitted per cycle ($N_{base}$).
- `peak_reactivity_temp` ($T_{pk}$): Temperature (°C) of maximum reactivity.
- `floor_temp` ($T_{fl}$): Temperature (°C) where reactivity drops to zero and thermal damage accelerates.
- `fission_mult` ($m_{fiss,t}$): Thermal fission multiplication factor.
- `fast_fission_mult` ($m_{fiss,f}$): Fast fission multiplication factor (§5.1).
- `spent_product`: Depleted item or fluid produced upon reaching 0 durability (e.g. `ItemList.DepletedRodLithium`, `ItemList.DepletedRodGlowstone`, `ItemList.DepletedRodUranium4`, `thoriumbasedliquidfueldepleted`).
- `durability`: Total wear budget of the fuel rod (§7.3).
- `matching`: Item/ore/fluid identifiers mapped to this entry (e.g. `ItemList.RodUranium4`, `ore:...`, `IC2:...`, `modid:name:meta`).

The default fuel registry populates explicit entries for every fuel rod tier (Quad, Dual, and Single variants) in strict order of density to ensure unambiguous matching:

| Fuel | $T_{pk}$ (°C) | $T_{fl}$ (°C) | $m_{fiss,t}$ | $m_{fiss,f}$ | Packets (S / D / Q) | $N_{base}$ single / dual / quad | Durability | Depleted product |
|---|---|---|---|---|---|---|---|---|
| Glowstone (breeder) | 200 | 2000 | 0.60 | 0.05 | 1 / — / — | 1 | 10 000 | `DepletedRodGlowstone` |
| Lithium (breeder) | 390 | 2100 | 0.80 | 0.05 | 1 / — / — | 1 | 10 000 | `DepletedRodLithium` |
| Thorium | 580 | 2200 | 1.00 | 0.08 | 1 / 2 / 4 | 2 / 4 / 8 | 50 000 | `DepletedRodThorium[2,4]` |
| Uranium | 770 | 2300 | 1.25 | 0.10 | 1 / 2 / 4 | 4 / 8 / 16 | 20 000 | `DepletedRodUranium[2,4]` |
| MOX | 960 | 2400 | 1.50 | 0.15 | 1 / 2 / 4 | 8 / 16 / 32 | 10 000 | `DepletedRodMOX[2,4]` |
| High-Density Uranium | 1150 | 2500 | 2.16 | 0.14 | 1 / 2 / 4 | 4 / 8 / 16 | 70 000 | `DepletedRodHighDensityUranium[2,4]` |
| High-Density Plutonium | 1350 | 2600 | 2.52 | 0.24 | 1 / 2 / 4 | 6 / 12 / 24 | 70 000 | `DepletedRodHighDensityPlutonium[2,4]` |
| Excited Uranium | 1540 | 2700 | 2.00 | 0.12 | 1 / 2 / 4 | 22 / 45 / 90 | 6 000 | `DepletedRodExcitedUranium[2,4]` |
| Excited Plutonium | 1730 | 2800 | 2.32 | 0.20 | 1 / 2 / 4 | 34 / 67 / 134 | 10 000 | `DepletedRodExcitedPlutonium[2,4]` |
| Naquadah | 1920 | 2900 | 4.08 | 0.30 | 1 / 2 / 4 | 8 / 16 / 32 | 100 000 | `DepletedRodNaquadah[2,4]` |
| The Core (Naquadah-32) | 2500 | 3200 | 6.60 | 0.60 | 16 | 256 | 320 000 | `DepletedRodNaquadah32` |
| Naquadria | 2110 | 3000 | 4.80 | 0.36 | 1 / 2 / 4 | 10 / 20 / 40 | 100 000 | `DepletedRodNaquadria[2,4]` |
| Tiberium | 2300 | 3100 | 4.70 | 0.20 | 1 / 2 / 4 | 24 / 48 / 96 | 50 000 | `DepletedRodTiberium[2,4]` |

"Durability" is the item's maximum damage, the total wear budget of the rod (§7.3).

Things to read off the table:

- **Peak temperatures rise with fuel tier.** Higher-tier fuels must run hotter to reach full reactivity. They therefore need higher pipe tiers to be operated near their peak without overheating the core.
- **Fission multipliers rise with tier.** In a well-moderated core, higher-tier fuels gain proportionally more from chain reactions relative to their base output.
- **Dense fuels with low $N_{base}$ and high $m_{fiss}$** (e.g. Naquadah, High-Density variants) depend on external neutron flux to achieve high power. Unmoderated or isolated, their spontaneous emission is small, but their fission multiplication potential is immense.
- **Excited fuels with high $N_{base}$** (Excited Uranium, Excited Plutonium) serve as natural neutron sources and igniters, driving adjacent subcritical cells into productive chain reactions.

#### Fuel Archetypes & Balance Calibration Multipliers

Under the hood, fuels are grouped into four behavioral **calibration archetypes** designed to support distinct operational roles in reactor design. Rather than hardcoding these behaviors into item classes, these archetypes are governed by global calibration multipliers in `physics.yaml` (under `PhysicsData`):

| Archetype | Member Fuels | Calibration Purpose |
|---|---|---|
| **Dense** | High-Density Uranium, High-Density Plutonium, Naquadah, Naquadria, The Core | **Subcritical High-Power Sinks:** Low spontaneous neutron emission makes them mostly inert in isolation, but their enhanced thermal fission multiplier unlocks massive energy output when driven by external neutron flux. |
| **Excited** | Excited Uranium, Excited Plutonium, Excited liquid fuels | **Neutron Drivers & Igniters:** High natural emission drives neighboring subcritical cells into chain reactions, while a reduced fission multiplier reflects partial saturation. |
| **Breeder** | Lithium, Glowstone | **Transmutation Targets:** Low-temperature targets tailored to breed valuable secondary isotopes (Tritium, Sunnarium) before depleting. |
| **Standard** | Thorium, Uranium, MOX, Tiberium, Custom mixes | **Baselines:** General-purpose, balanced power producers. |

From the player's perspective, fuels simply possess the effective operational parameters shown in the table above and in NEI. The archetype classification is an internal balance and calibration mechanism, allowing server administrators and simulation tuning tools to adjust whole classes of fuels globally in `physics.yaml` without cluttering the player's interface with meta-tags:

```yaml
dense_nbase_multiplier: 0.50
dense_fission_multiplier: 1.20
excited_nbase_multiplier: 1.40
excited_fission_multiplier: 0.80
standard_nbase_multiplier: 1.00
standard_fission_multiplier: 1.00
breeder_nbase_multiplier: 1.00
breeder_fission_multiplier: 1.00
```

#### Breeder targets (Lithium and Glowstone)

Glowstone and lithium cells are breeder targets in GregTech. In the reactor core, they behave as low-temperature fuels with peak reactivity at 200 °C (glowstone) and 390 °C (lithium).

Unlike legacy mechanics where breeder targets were voided upon depletion, **they now reliably deplete into their proper breeding forms**:
- Lithium fuel rods deplete into **Depleted Lithium Fuel Rod** (`ItemList.DepletedRodLithium`), which is centrifuged in GregTech into **Tritium**.
- Glowstone fuel rods deplete into **Depleted Glowstone Fuel Rod** (`ItemList.DepletedRodGlowstone`), which is centrifuged in GregTech into **Sunnarium**.

Depleted breeder rods are ejected to the ring output bus just like any other depleted fuel rod and are never voided on normal depletion.

> [!NOTE]
> **Implementation note.** All fuel parameters (`packet_count`, `base_neutrons`, `peak_reactivity_temp`, `floor_temp`, `durability`, and `spent_product`) are authoritative in `fuels.yaml` and directly drive the in-game physics simulation, NEI display handlers, and OpenComputers telemetry.

### 7.2 What happens when a fuel item is placed

A fuel item in a Nuclear Core Bus makes the cell a **fuel cell**. Its neutron properties:

| $p_{a,f}$ | $p_{a,t}$ | $p_{s,f}$ | $p_m$ | $c$ |
|---|---|---|---|---|
| 0.25 | 0.80 | 0.15 | 0.10 | 0.05 for uranium/MOX/thorium/"fuel"-named items, 0.03 otherwise |

Fuel is a strong thermal absorber, which is what drives fission. It is also a moderate fast absorber and scatterer. About 1.5% of fast packets entering a fuel cell are thermalised in it ($p_{s,f} \cdot p_m = 0.15 \times 0.10$ per entry). The resulting thermal neutrons are absorbed by the same cell with probability 0.80 in the first stencil step. Even in an unmoderated lattice, fuel therefore has a small self-sustaining chain component.

### 7.3 Fuel wear and lifetime

Each cycle a fuel rod takes wear:

$$W = 0.25\,N + (w_f\,A_f + w_t\,A_t) + D(T)$$

where $N$ is its neutron output, $A_f$ and $A_t$ are its fast and thermal neutron absorptions (fuel share only), $w_f$ and $w_t$ are the configurable absorption damage multipliers (`fast_absorption_damage_mult`, default **8.0**, and `thermal_absorption_damage_mult`, default **1.0**), and $D(T)$ a temperature term:

$$D(T) = A \left(e^{k (T - T_{amb})} - 1\right)$$

#### Differential Fast vs. Thermal Wear (Moderator Incentive)

Fast neutron absorption causes **8× more wear** than thermal absorption by default ($w_f = 8.0$ vs $w_t = 1.0$ in `physics.yaml`):

- **Physical Rationale:** Fast neutrons carry immense kinetic energy (several MeV), severely degrading the fuel cladding and crystalline matrix through atomic displacement and radiation damage without contributing high fission yield ($m_{fiss,f} \ll m_{fiss,t}$).
- **Gameplay Design:** This creates a powerful, aggressive incentive to dedicate core cells to **moderators** (Graphite, Carbon, Heavy Water). Surrounding fuel rods with moderating materials thermalises the fast flux before it re-enters fuel cells. This simultaneous conversion drastically enhances chain-reaction heat generation while cutting neutron-induced rod degradation eight-fold.

Fractional wear is applied by stochastic rounding: a wear of 4.3 is 4 with 70% probability and 5 with 30%. When the accumulated damage reaches the rod's durability, the rod is replaced by its depleted form (§7.6).

The constants $A$ and $k$ are solved per fuel so that:

- $D(T_{pk}) = 1.0$: one durability point per cycle at peak temperature;
- $D(T_{pk}) / D(T_{10}) = 0.25$, where $T_{10} = T_{pk} + 0.852637\,(T_{fl} - T_{pk})$ is the temperature at which reactivity has fallen to 10%. Running a rod at its 10%-reactivity temperature costs four times the thermal wear of running it at peak.

Where no such exponential exists (the linear ratio already exceeds 0.25 — glowstone and lithium), $k = 10^{-4}$ is used.

| Fuel | $k$ (1/°C) | $A$ | $D(T_{fl})$ |
|---|---|---|---|
| Thorium | 0.000199 | 8.48 | ~4.6 |
| Uranium | 0.000518 | 2.105 | ~4.8 |
| MOX | 0.000754 | 0.970 | ~4.9 |
| Naquadah | 0.001617 | 0.0486 | ~5.0 |
| The Core | 0.002319 | 0.00319 | ~5.1 |

The neutron terms dominate in any productive core:

- **Isolated Rod:** An isolated uranium quad rod at peak emits 16 neutrons and absorbs little. Its wear is $0.25 \times 16 + D(770) \approx 5$ per cycle, giving a lifetime of about 4 000 cycles (~67 minutes).
- **Unmoderated Fast Lattice (Severe Penalty):** If that rod is placed in an unmoderated dense cluster where it absorbs 40 fast neutrons per cycle and emits 20, its wear is $0.25 \times 20 + 8.0 \times 40 + 1 = 5 + 320 + 1 = 326$ per cycle, destroying the rod in under 2 minutes (just 61 cycles).
- **Well-Moderated Thermal Lattice (High Efficiency):** If the rod is embedded among moderators, absorbing 40 thermal neutrons and only 2 fast neutrons per cycle while producing 60 emitted neutrons, its wear is $0.25 \times 60 + 8.0 \times 2 + 1.0 \times 40 + 1 = 15 + 16 + 40 + 1 = 72$ per cycle—burning 4.5× slower than the unmoderated cluster while outputting massive fission heat.

**Every neutron absorbed in fuel shortens its life, but fast neutrons destroy it 8× faster.** Power, moderation, and fuel economy trade off directly.

#### Durability Scaling Reconciliation & Item Synchronization

Minecraft items represent item wear through two layers:
1. **Vanilla Item Damage**: A 16-bit integer ($0 \dots 100$ in GregTech's `ItemRadioactiveCell`) used to render inventory durability bars.
2. **Intrinsic Nuclear Damage**: Stored as an NBT integer (`advDmg`) against the item's intrinsic nuclear durability budget $D_{\text{intrinsic}}$ ($40\,000$ for quad rods in `ItemRadioactiveCell`, or variable for custom compounded rods).

Server administrators can configure arbitrary fuel lifetimes in `fuels.yaml` (or via custom fuel compounding) as $D_{\text{configured}}$. To reconcile the configured physical lifetime with the item's intrinsic durability without breaking inventory tooltips, GUI durability bars, or external logistics, the reactor engine scales physical wear each cycle:

$$\text{Scale} = \frac{D_{\text{intrinsic}}}{D_{\text{configured}}}$$

The physical wear $W$ calculated each cycle is scaled to the item's intrinsic damage domain:

$$\Delta D_{\text{item}} = W \times \text{Scale}$$

Integer wear $\lfloor \Delta D_{\text{item}} \rfloor$ is applied directly, and the fractional remainder is applied via stochastic rounding:

$$P(\text{extra point}) = \Delta D_{\text{item}} - \lfloor \Delta D_{\text{item}} \rfloor$$

When $\text{Damage}_{\text{item}} \ge D_{\text{intrinsic}}$, the rod has reached exactly $100\%$ depletion according to its configured life cycle. This ensures that:
- Inventory GUI durability bars and tooltips reflect the true percentage life remaining at all times.
- Fuel lifetime can be tuned arbitrarily in server configuration without desynchronizing item state.
- Custom mixed fuel rods dynamically adapt their durability bars to their unique isotopic budgets.

### 7.4 Liquid fuels

Liquid fuels go in Nuclear Core Hatches (subject to the tier gate in §2.5). A hatch holding a liquid fuel is a fuel cell with these properties:

| $p_{a,f}$ | $p_{a,t}$ | $p_{s,f}$ | $p_m$ | $c$ | Packets |
|---|---|---|---|---|---|
| 0.25 | 0.85 | 0.10 | 0.20 | 0.15 | 4 |

Liquid fuels are explicitly defined in `fuels.yaml` with dedicated operational and physics parameters:

| Liquid fuel | $N_{base}$ | $T_{pk}$ (°C) | $T_{fl}$ (°C) | $m_{fiss,t}$ | $m_{fiss,f}$ | Spent fluid product |
|---|---|---|---|---|---|---|
| Thorium-Based Liquid Fuel | 4 | 580 | 2200 | 1.00 | 0.08 | `thoriumbasedliquidfueldepleted` |
| Excited Thorium-Based Liquid Fuel | 8 | 580 | 2200 | 1.50 | 0.12 | `thoriumbasedliquidfueldepleted` |
| Uranium-Based Liquid Fuel | 8 | 770 | 2300 | 1.25 | 0.10 | `uraniumbasedliquidfueldepleted` |
| Excited Uranium-Based Liquid Fuel | 16 | 1540 | 2700 | 2.50 | 0.15 | `uraniumbasedliquidfueldepleted` |
| Plutonium-Based Liquid Fuel | 16 | 1350 | 2600 | 1.80 | 0.20 | `plutoniumbasedliquidfueldepleted` |
| Excited Plutonium-Based Liquid Fuel | 32 | 1730 | 2800 | 2.90 | 0.25 | `plutoniumbasedliquidfueldepleted` |
| Uranium Hexafluoride | 12 | 770 | 2300 | 1.25 | 0.12 | `uraniumtetrafluoride` |

Instead of losing durability, a liquid-fuel hatch consumes $\max(1, W)$ litres per cycle, with $W$ the wear formula of §7.3. It outputs the same volume of the corresponding spent fluid to the ring output hatches. A liquid-fuel hatch **does not cool**. It is subject to thermal shock like any non-coolant hatch (§13.1), with a threshold of 100 °C.

Compared with rods, liquid fuel has a higher thermal absorption (0.85), twice the moderation and three times the conductivity. It also runs continuously as long as the hatch is topped up.

### 7.5 Custom mixed fuel (Actinide Fuel Compounder)

#### The compounder

The Actinide Fuel Compounder is a separate 3×3×3 multiblock:

- nuclear casings, at least 14;
- one input bus, one output bus, one input hatch, one energy hatch, exactly one maintenance hatch.

It compounds custom nuclear fuel mixes into **Single**, **Dual**, or **Quad Custom Fuel Rods** selected by an installed **Programmed Circuit** (`circuitIntegrated`) in the input bus. An explicit programmed circuit configuration (1, 2, or 4) is **strictly required**; the machine will not run without one. The circuit is inspected but **never consumed**.

| Circuit Configuration | Target Rod Type | Empty Rods Required | Tritium Required | Actinide Dust Units Required | Process Time @ 512 EU/t |
|---|---|---|---|---|---|
| **Circuit 1** | Single Custom Fuel Rod (`rodCustomFuel1`) | 1 | 25 L | 90 units (2.5 dusts) | 150 ticks (7.5 s) |
| **Circuit 2** | Dual Custom Fuel Rod (`rodCustomFuel2`) | 2 | 50 L | 180 units (5.0 dusts) | 300 ticks (15 s) |
| **Circuit 4** | Quad Custom Fuel Rod (`rodCustomFuel4`) | 4 | 100 L | 360 units (10.0 dusts) | 600 ticks (30 s) |

Dust units are counted in slot order: a regular dust is 36 units, a small dust 9 (¼), a tiny dust 4 (⅑). Only recognised actinide materials count. The output carries an NBT record of the mix (`CustomFuelMix` → `Units`, one entry per isotope in dust units).

> [!TIP]
> Keep only the intended mix in the input bus. Units are taken greedily in slot order, so extra material in early slots can make the machine pick a combination that misses the required unit target (90, 180, or 360), even when a valid subset exists.

#### Isotope parameters

| Isotope | $T_{pk}$ | $T_{fl}$ | $m_{fiss,t}$ (thermal) | $m_{fiss,f}$ (fast) | $N_{base}$ | Durability |
|---|---|---|---|---|---|---|
| Th-232 | 580 | 2200 | 1.00 | 0.06 | 16 | 50 000 |
| U-238 | 770 | 2300 | 1.10 | 0.03 | 16 | 40 000 |
| U-235 | 770 | 2300 | 1.60 | 0.12 | 24 | 20 000 |
| Pu-239 | 1100 | 2500 | 1.80 | 0.16 | 32 | 20 000 |
| Pu-241 | 1350 | 2600 | 2.20 | 0.20 | 40 | 15 000 |
| U-233 | 650 | 2250 | 1.50 | 0.10 | 24 | 35 000 |
| Am-241 | 1200 | 2550 | 1.70 | 0.14 | 28 | 25 000 |
| Cf-252 | 1600 | 2700 | 2.50 | 0.22 | 36 | 18 000 |
| Naquadah | 1920 | 2900 | 3.40 | 0.28 | 48 | 80 000 |

The compounded mix parameters are the **unit-weighted linear averages** of its constituents' parameters:

$$X_{mix} = \sum_i \frac{u_i}{U_{total}} X_i$$

where $U_{total} = \sum_i u_i$ (90 for Single, 180 for Dual, 360 for Quad).

#### Rod sizing and physical scaling

Single, dual, and quad custom fuel rods follow exact proportionality with non-custom rod trios:

| Rod Type | Emission Packets | Base Neutrons $N_{base}$ | Max Durability $D_{intrinsic}$ | Depleted Product |
|---|---|---|---|---|
| **Single Custom Fuel Rod** | 1 | $\max(1, \text{round}(N_{base,mix} \times 0.25))$ | Mix $D_{mix}$ (Minecraft max damage: 10 000) | `rodCustomDepletedFuel1` |
| **Dual Custom Fuel Rod** | 2 | $\max(1, \text{round}(N_{base,mix} \times 0.50))$ | Mix $D_{mix}$ (Minecraft max damage: 20 000) | `rodCustomDepletedFuel2` |
| **Quad Custom Fuel Rod** | 4 | $N_{base,mix}$ | Mix $D_{mix}$ (Minecraft max damage: 40 000) | `rodCustomDepletedFuel4` |

Everything else about custom rods follows the standard fuel rules: reactivity curves built from the averaged $T_{pk}$/$T_{fl}$, a wear curve solved for those temperatures, and the fuel neutron properties of §7.2.

The mix lets the operator **tune a fuel's peak temperature to the pipe tier**, and trade base output against durability. For example, a 50/50 Th-232/Pu-239 mix peaks at 840 °C with $m_{fiss,t} = 1.40$, $m_{fiss,f} = 0.11$, quad $N_{base} = 24$ (dual $N_{base} = 12$, single $N_{base} = 6$) and durability 35 000.

> [!NOTE]
> **Implementation note.** Custom fuel rods dynamically read their intrinsic nuclear durability budget from the compounded mix NBT (`MaxDurability`), rather than being locked to a fixed damage value. Cycle wear in the reactor is dynamically scaled by $\frac{D_{\text{intrinsic}}}{D_{\text{configured}}}$ with stochastic rounding (§7.3), keeping inventory GUI durability bars, tooltips, and depletion timings 100% accurate across all custom isotopic blends and rod sizes. The fast fission multiplier ($m_{fiss,f}$) is fully active in core physics (§5.1), allowing fast neutrons absorbed in fuel to induce chain reactions with isotope-specific cross-sections (e.g. 0.03 for fertile U-238 vs 0.12 for U-235 and 0.16 for Pu-239). The fast multiplier stored in mix NBT directly parameterizes the rod's fast fission performance.

### 7.6 Depletion products

| Rod | Product on depletion |
|---|---|
| GT/IC2 fuel rods | Their depleted form, to the ring output bus |
| Breeder rods (Lithium) | Depleted Lithium Fuel Rod (`ItemList.DepletedRodLithium`), to ring output bus (centrifuged to Tritium) |
| Breeder rods (Glowstone) | Depleted Glowstone Fuel Rod (`ItemList.DepletedRodGlowstone`), to ring output bus (centrifuged to Sunnarium) |
| Single custom fuel rod | Depleted Fuel Rod (Custom Mix) (`rodCustomDepletedFuel1`), with the original mix NBT copied |
| Dual custom fuel rod | Depleted Dual Fuel Rod (Custom Mix) (`rodCustomDepletedFuel2`), with the original mix NBT copied |
| Quad custom fuel rod | Depleted Quad Fuel Rod (Custom Mix) (`rodCustomDepletedFuel4`), with the original mix NBT copied |
| Liquid fuels | Spent fluid, 1 L per litre burned, to the output hatch |

Breeder rods undergo standard nuclear depletion into their centrifugable depleted forms; there is no secondary transmutation inside standard fuel rods.

### 7.7 Component allowlist and insertion rules

Reactor Core Buses (`MTEHatchNuclearBus`) enforce a strict, data-driven **item allowlist**:

- **Active fuel rods**: Only non-depleted, active fuel rods (IC2, GregTech, or custom fuel rods with positive durability) can be inserted into core buses.
- **Depleted and empty rods are strictly blocked**: Depleted fuel rods and empty fuel rods are completely rejected by automation and player insertion. They cannot be inserted into core hatches and can never emit neutrons.
- **Control rod segregation**: Control rods (Silver, Boron, Cadmium, Indium, and Hafnium long rods) are strictly restricted to **Nuclear Control Rod Hatches** (`MTEHatchNuclearControlRod`) and cannot be placed into core buses.
- **Hull-only components disallowed**: Components designed solely to interact with an IC2 reactor hull heat reservoir (such as IC2 Reactor Vents `IC2:reactorVentCore` and IC2 Reactor Heat Switches `IC2:reactorHeatSwitchCore`) are strictly rejected, as the MPTR reactor has no hull heat concept.
- **Non-reactor items rejected**: Ordinary items (dirt, ingots, ores, machinery) cannot be placed into reactor core hatches. The only non-nuclear item accepted is dynamic recipe inputs producing molten cheese (the extraction Easter egg).

All allowlists and blocklists are fully configurable by server operators via the structured YAML configuration system (`config/modularnuclear/fuels.yaml`, `config/modularnuclear/components.yaml`, and `config/ModularNuclear.cfg`). Generic name-matching fallbacks have been completely removed; every allowed component must be explicitly configured in YAML.

---

## 8. Non-fuel core components

Every non-fuel cell is one of the following:

- an allowlisted item in a Nuclear Core Bus;
- an allowlisted coolant fluid in a Nuclear Core Hatch;
- a High-Pressure Passage.

Component recognition, neutronic cross-sections, and thermodynamic properties are completely data-driven via `config/modularnuclear/components.yaml` and `config/modularnuclear/fuels.yaml` (§15). In addition to native IC2 reactor components, GregTech plates (such as Gold, Stainless Steel, and Tungsten Carbide plates for reflectors, and Graphite/Carbon/Beryllium long rods for moderators) are explicitly defined in YAML. Operators may expand, modify cross-sections, or tune any material without recompiling the mod.

### 8.1 Neutron and thermal property summary

| Component | $p_{a,f}$ | $p_{a,t}$ | $p_{s,f}$ | $p_m$ | $c$ | $\delta$ |
|---|---|---|---|---|---|---|
| Empty bus | 0.01 | 0.01 | 0.02 | 0.05 | 0.02 | 0 |
| Empty hatch | 0.01 | 0.01 | 0.02 | 0.05 | 0.05 | 0 |
| Neutron Source (idle) | 0.01 | 0.01 | 0.02 | 0.05 | 0.02 | 0 |
| Fuel rod | 0.25 | 0.80 | 0.15 | 0.10 | 0.05 / 0.03 | 0 |
| Moderator: Carbon | 0.003 | 0.012 | 0.90 | 0.45 | 0.05 | 0 |
| Moderator: Graphite | 0.002 | 0.009 | 0.93 | 0.50 | 0.05 | 0 |
| Moderator: Beryllium | 0.001 | 0.004 | 0.96 | 0.65 | 0.15 | 0 |
| Reflector: Gold plate | 0.15 | 0.10 | 0.85 | 0.00 | 0.30 | 0 |
| Reflector: Stainless Steel plate | 0.10 | 0.05 | 0.90 | 0.00 | 0.20 | 0 |
| Reflector: Tungsten Carbide plate | 0.07 | 0.02 | 0.93 | 0.00 | 0.10 | 0 |
| Reflector: IC2 / GT | 0.01 | 0.02 | 0.95 | 0.20 | 0.15 | 0 |
| Absorber (boron / cadmium / "control") | 0.85 | 0.95 | 0.10 | 0.05 | — | 0 |
| Radiovoltaic plate | 1.0 | 1.0 | 0 | 0 | 0.10 | 0 |
| Item coolant / vent / switch / exchanger | 0.05 | 0.05 | 0.45 | 0.05 | 0.40 | 0 |
| Naquarite foil | 1.0 | 1.0 | 0 | — | 0.01 | 1.0 |
| Other insulators | 0.01 | 0.01 | 0.05 | 0.05 | 0.01 | 0.2–0.6 |
| Cheese-extractable item (Easter egg) | 0.02 | 0.02 | 0.05 | 0.05 | 0.03 | 0 |
| Hatch: Heavy Water | 0.005 | 0.01 | 0.85 | 0.90 | 0.25 | 0 |
| Hatch: Distilled Water | 0.05 | 0.10 | 0.70 | 0.25 | 0.25 | 0 |
| Hatch: IC2 Coolant | 0.03 | 0.12 | 0.45 | 0.05 | 0.50 | 0 |
| Hatch: Liquid fuel | 0.25 | 0.85 | 0.10 | 0.20 | 0.15 | 0 |
| High-Pressure Passage | 0.02 | 0.05 | 0.80 | by loop fluid | 0.90 | 0 |

The passage moderation depends on the loop coolant (only waters moderate significantly):

| Loop fluid | Heavy water | Distilled water | IC2 coolant | Sodium | Other non-water |
|---|---|---|---|---|---|
| Passage $p_m$ | 0.90 | 0.25 | 0.05 | 0.05 | 0.05 |

Where a single value is given for absorption, it applies to both fast and thermal neutrons.

### 8.2 Empty cells

An empty bus or hatch is almost transparent. About 3% of fast packets interact in it, and 1% of thermal neutrons are absorbed. Empty cells are the cheapest way to separate fuel cells neutronically while still letting thermal neutrons cross. They conduct heat poorly and contribute boundary-like insulation between neighbours.

### 8.3 Moderators

Moderator components are completely data-driven via `config/modularnuclear/components.yaml` under `moderators`. Component recognition is **never based on fuzzy substring matching**; instead, each moderator material is explicitly configured with individual cross-sections, durability budgets, temperature wear constants, and spent products. Long rods made of Carbon, Graphite, and Beryllium (`stickLongCarbon`, `stickLongGraphite`, `stickLongBeryllium`) as well as plates, blocks, and dusts are supported directly in the Nuclear Core Bus.

#### Supported Moderator Types

| Material | Durability | Moderation ($p_m$) | Scattering ($p_s$) | Absorption ($p_a$) | Spent Product | Wigner Energy Behavior |
|---|---|---|---|---|---|---|
| **Carbon** | 10 000 | 0.45 | 0.90 / 0.60 | 0.003 / 0.012 | `ore:dustAsh` | None |
| **Graphite** | 80 000 | 0.50 | 0.93 / 0.621 | 0.002 / 0.009 | `ore:dustAsh` | Stores 1 HU / fast (saturates at 10 000 HU); releases heat > 1500 °C (up to 2 000 HU/t) |
| **Beryllium** | 240 000 | 0.65 | 0.96 / 0.85 | 0.001 / 0.004 | None (consumed) | None |

- **Carbon:** Burns out quickly with the lowest durability (10 000 points). Useful as a cheap early-game solid moderator rod.
- **Graphite:** Lasts **8× longer** than carbon (80 000 durability). It scatters 93% of fast neutrons with 50% moderation efficiency.
- **Beryllium:** Works significantly better and lasts **24× longer** than carbon (240 000 durability). With $p_m = 0.65$, $p_{s,f} = 0.96$, and high thermal conductivity ($c = 0.15$), it represents the pinnacle of solid moderator technology.

#### Moderator Durability and Wear

When freshly crafted or inserted into the reactor bus, GT rods and blocks carry no initial durability metadata. As soon as the cell receives neutron flux, the reactor tracks cumulative damage in an NBT integer tag (`advDmg`).

Each cycle, moderator wear is calculated from incident flux, absorbed neutrons, and temperature:

$$\Delta W = (A_f \cdot w_f + A_t \cdot w_t) + (F_f + F_t) \cdot 0.05 + D(T)$$

where $w_f = 8.0$ and $w_t = 1.0$ (configurable in `physics.yaml`), and temperature wear follows an exponential acceleration curve:

$$D(T) = A \left(e^{k(T - T_{\text{amb}})} - 1\right) \quad (T > T_{\text{amb}})$$

Damage is applied using stochastic rounding. When $\text{advDmg} \ge \text{durability}$, the moderator rod is depleted and converted into its configured `spentProduct` (e.g. `ore:dustAsh` for Carbon and Graphite, delivered to the reactor's output bus).

#### Graphite Wigner Energy Storage and Thermal Release Hazard

Graphite moderators feature a realistic solid-state physical phenomenon: **Wigner energy accumulation and thermal annealing release**. When fast neutrons collide with graphite, lattice atoms are knocked out of their crystalline positions, trapping potential energy within the material.

1. **Energy Storage and Saturation ($T \le 1500^\circ\text{C}$):**
   - Each fast neutron interaction (absorption or moderation event) stores **$1.0\,\text{HU}$** directly into the graphite rod's NBT tag (`wignerEnergy`), building up slowly over thousands of interactions. The core bus itself does not hold Wigner energy; extracting or transferring the rod preserves its stored defect pool.
   - **Saturation Ceiling:** The lattice defect capacity is finite; accumulation **saturates and stops completely once it reaches $10\,000\,\text{HU}$** ($E_{\max} = 10\,000\,\text{HU}$, configurable via `wigner_max_energy`). Any subsequent fast-neutron collisions after reaching 10 000 HU do not increase the stored energy pool further.
   - At normal operating temperatures ($\le 1500^\circ\text{C}$), this energy remains trapped indefinitely without decay.

2. **Thermal Release ($T > 1500^\circ\text{C}$):**
   - Once the cell temperature exceeds the onset threshold of **$1500^\circ\text{C}$**, lattice defects begin to spontaneously recombine, releasing stored energy as **direct heat**.
   - The maximum instantaneous heat release rate follows a smooth Hill saturation curve:

   $$\Delta T = \max(0, T - 1500), \quad \Delta T_{1/2} = 1000$$

   $$\text{Rate}_{\max}(T) = R_{\max} \cdot \frac{(\Delta T)^2}{(\Delta T)^2 + (\Delta T_{1/2})^2} \quad (T > 1500^\circ\text{C})$$

   where $R_{\max} = 2\,000\,\text{HU/t}$.

   - At $T \le 1500^\circ\text{C}$: Release rate is strictly **$0\,\text{HU/t}$**.
   - At $T = 2500^\circ\text{C}$ ($\Delta T = 1000^\circ\text{C} = \Delta T_{1/2}$): Reaches exactly **50% saturation**, releasing up to **$1\,000\,\text{HU/t}$**.
   - At extreme temperatures: Asymptotically approaches the ceiling of $R_{\max} = 2\,000\,\text{HU/t}$.

3. **Pool Depletion and Cessation:**
   - The actual heat deposited into the reactor core each tick is limited by the remaining stored Wigner energy:

   $$Q_{\text{release}} = \min(E_{\text{stored}},\ \text{Rate}_{\max}(T))$$

   - Each tick of active release depletes the stored pool: $E_{\text{stored}} \leftarrow E_{\text{stored}} - Q_{\text{release}}$.
   - **Crucial rule:** Once the stored Wigner pool is depleted ($E_{\text{stored}} = 0$), heat emission **ceases immediately**. Graphite never generates extra heat indefinitely without prior fast-neutron irradiation. Stored Wigner energy persists on the rod across removal and re-insertion into buses, and is destroyed only upon annealing release or when the rod wears out completely into ash.
   - **No secondary thermal neutrons are emitted**: Unlike earlier experimental models, hot graphite does not emit thermal neutrons; all stored Wigner energy is released strictly as thermal heat (HU).

> [!WARNING]
> **Thermal Exotherm Hazard**: If a graphite-moderated reactor with heavily irradiated graphite rods experiences a temporary loss of coolant flow and heats above $1500^\circ\text{C}$, the sudden annealing of stored Wigner energy can inject up to $2\,000\,\text{HU/t}$ per graphite cell. This exothermic heat dump can trigger rapid coolant boiling, pressure spikes, and catastrophic core overheating before normal control rod SCRAM can cool the structure. Operators using graphite must maintain high coolant flow margins or upgrade to liquid moderators (heavy water) or beryllium.

All curve parameters ($T_{\text{onset}}$, $T_{\text{half}}$, $R_{\max}$, and $E_{\text{fast}}$) and durability budgets are fully configurable per moderator entry in `components.yaml`.

#### NEI Integration

Pressing recipe or usage on any moderator item (or clicking the MPTR Reactor Catalyst) opens the dedicated **NEI Moderator Stats Handler** (`MPTR Moderators`). This interface displays full neutronic ratings, durability lifetime, heat transfer coefficients, and an interactive plot of either the Graphite Wigner Heat Release Curve (in cyan/blue) or the Temperature Wear Acceleration Curve (in orange) with real-time hover tooltips displaying current rates and thresholds.

### 8.4 Reflectors

Matching items: IC2 Neutron Reflector, Thick Neutron Reflector, and the GT Iridium Neutron Reflector (`gt.neutronreflector`).

A reflector interacts with 96% of fast packets and reverses each scatter. Only 20% of scatters thermalise, so it mainly **returns fast neutrons** to where they came from. It absorbs only 2% of thermal neutrons. Typical uses:

- on the core perimeter, to turn back fast neutrons that would otherwise escape;
- facing a fuel cell, to give the neutrons a second pass through the fuel and its moderating neighbours.

Reflectors conduct moderately well ($c = 0.15$).

**Wear.** Each scatter event in a damageable reflector has a 1-in-20 chance to cost one durability point. IC2 reflectors therefore wear in proportion to the number of packets they turn back. The GT Iridium Neutron Reflector is indestructible.

### 8.5 Absorbers

Matching items: names containing *boron*, *cadmium* or *control*.

An absorber captures 85% of incident fast packets and 95% of thermal neutrons. Each absorbed neutron deposits heat (8 Hu fast, 4 Hu thermal). Absorber items do not wear. They are a static alternative to control rods (§9), stronger than a fully inserted Cadmium rod and comparable to Indium or Hafnium, but they occupy the cell itself and cannot be adjusted.

### 8.6 Radiovoltaic plates

The radiovoltaic plates (HV and EV) convert absorbed neutrons directly to electricity. A plate absorbs every neutron that reaches it, fast or thermal ($p_a = 1.0$). Its raw nominal output each cycle is

$$P_{plate} = \operatorname{round}\left(P_{max} \cdot \tanh\frac{4A_f + A_t}{60}\right)$$

| Plate | $P_{max}$ |
|---|---|
| HV radiovoltaic plate | 1 024 EU/t |
| EV radiovoltaic plate | 4 096 EU/t |

Fast neutrons count four times as much as thermal ones.

The response saturates. The weighted absorption $w = 4A_f + A_t$ gives:

| $w$ | 30 | 60 | 120 | 180 |
|---|---|---|---|---|
| Fraction of $P_{max}$ | 46% | 76% | 96% | 99.5% |

A plate also heats: on top of the normal absorption heat, it receives $\max(0, 20w - P_{plate})$ Hu of excess heat per cycle. At high flux, most absorbed energy becomes heat rather than electricity, so radiovoltaics need cooling like any other cell.

The plant's aggregate direct output is the sum of all plates, scaled once by the maintenance efficiency $\eta$ (§12):

$$P_{aggregate} = \operatorname{round}\left(\sum P_{plate} \cdot \eta\right)$$

It is emitted **every tick** through the ring dynamo hatch (§13.6). Direct EU therefore scales linearly with maintenance efficiency $\eta$.

### 8.7 Neutron Source (External Beam Injection)

The **Neutron Source** (`goodgenerator:neutronSource`, `item.neutronSource`) is an existing GTNH component that can be placed inside a Nuclear Core Bus (slice 0). It acts as an open channel for externally-produced neutrons from a particle accelerator (e.g. Source Chamber) to be injected directly into the reactor core.

#### Installation & Pairing Rules
- A Neutron Source sits in a Nuclear Core Bus in slice 0.
- Directly beneath it in the same column $(x, y)$ on slice 4, a **Beam Input Hatch** (`gtnhlanth.common.hatch.MTEHatchInputBeamline`, ID 10503) must be installed facing **down**.
- A Neutron Source will **only** emit neutrons entering through the paired Beam Input Hatch on its own vertical column.
- If a Beam Input Hatch is installed without a paired Nuclear Core Bus directly opposite it, the multiblock structure validation fails (§2.6).

#### Durability and Idle Behavior
- The Neutron Source **has no durability and never wears or depletes**.
- When idle (no beam connected or beam energy = 0), it behaves like an empty nuclear bus: low absorption ($p_a = 0.01$), low scattering ($p_s = 0.02$), low moderation ($p_m = 0.05$), and low conduction ($c = 0.02$).
- It produces **zero prompt fission heat**.

#### Beamline Safety Requirement
> [!CAUTION]
> **Strict Particle Safety (Explosion Hazard)**:
> The MPTR reactor core allows **strictly neutron beams** (`Particle.NEUTRON`, ID 12) through its Beam Input Hatches. If ANY other particle (Protons, Electrons, Alpha particles, etc.) enters ANY connected beam input hatch, the reactor core **immediately explodes** with catastrophic destruction (§13.7). Always verify the source beam before opening line valves.

#### Fast Neutron Injection Mechanics
Injected neutrons entering through the beamline are **100% fast neutrons** (never thermal). Every cycle (or tick), the Neutron Source emits four discrete directional fast neutron rays into the four cardinal horizontal directions (North, East, South, West):

$$S = \max(0, \operatorname{round}(E \cdot R \cdot M))$$

where:
- $E$ is the beam energy (eV / intensity),
- $R$ is the beam packet rate (particles / sec),
- $M$ is `beam_energy_to_fast_neutron_multiplier` (default `0.001`, configured in `physics.yaml` and `ModularNuclear.cfg`),
- $S$ is the packet size emitted along each of the 4 cardinal rays. Total neutrons generated per tick is $4 \times S$.

Once injected, these fast neutron rays follow standard reactor neutron transport: they travel through adjacent cells, undergoing scattering, moderation by surrounding moderator blocks or liquid hatches, or absorption by fuel rods to drive subcritical or breed-and-burn chain reactions.

### 8.8 Item coolant cells

Matching items: Explicitly allowlisted coolant cells defined in `config/modularnuclear/components.yaml` under `coolant_items`. This includes IC2 and GT 10k, 30k, 60k, 180k, and 360k Coolant Cells (`IC2:reactorCoolantSimple`, `IC2:reactorCoolantTriple`, `IC2:reactorCoolantSix`), as well as Helium (`reactorCoolantHe*`), NaK (`reactorCoolantNaK*`), Space (`reactorCoolantSp*`), and Condensators (`IC2:reactorCondensator*`). All generic name-matching fallbacks have been eliminated.

Each cycle a coolant cell pulls heat from its own cell:

$$H = \operatorname{round}\left(\min\left(\frac{32\,(T - T_{amb})}{25},\ \max\left(1, \frac{H_{max}}{100}\right)\right) \cdot \eta\right) \ \text{IC2-Hu}$$

The amount is limited to the cell's remaining capacity. One IC2 heat unit equals 25 core Heat Units (Hu), so the cell temperature drops by $25H/32$ °C. The rate cap is 1% of capacity per cycle:

| Cell capacity | Max removal per cycle | Equivalent ΔT per cycle | Cycles to fill at cap |
|---|---|---|---|
| 10 000 IC2-Hu | 100 IC2-Hu (2 500 Hu) | 78 °C | 100 |
| 60 000 IC2-Hu | 600 IC2-Hu (15 000 Hu) | 469 °C | 100 |
| 360 000 IC2-Hu | 3 600 IC2-Hu (90 000 Hu) | 2 812 °C | 100 |

When full, the cell is **ejected intact** (full of heat) to the ring output bus for external re-cooling, and the bus is left empty. Coolant cells are consumables that buy time. They do not provide sustained cooling.

**Moderation behavior:** Coolant cells do not moderate significantly; their moderation is strictly configured at **$p_m = 0.05$** (with scattering $p_s = 0.45$ and absorption $p_a = 0.05$). A coolant cell placed adjacent to a fuel rod will absorb minor heat and scatter neutrons, but will not provide effective thermalisation.

All coolant cell configurations are strictly data-driven via YAML. Unregistered items are never recognized as coolant cells and cannot be inserted into the reactor core.

### 8.9 Heat vents and heat exchangers

Matching items: Explicitly configured Heat Vents and Heat Exchangers in `config/modularnuclear/components.yaml` (`heat_vents` and `heat_exchangers`).

> [!IMPORTANT]
> **Hull-Interacting Components Disallowed**: Items whose sole purpose is transferring heat into or out of a reactor hull (`IC2:reactorVentCore`, `IC2:reactorHeatSwitchCore`) are strictly disallowed from entering the reactor core bus. MPTR cores have no hull heat reservoir; therefore, hull-only components are rejected at the bus intake.

Both heat vents and heat exchangers have minimal moderation (**$p_m = 0.05$**, matching non-water coolants), with $p_a = 0.05$ and $p_{s,f} = 0.45$.

A vent or exchanger pulls up to $\max(20, H_{max}/50)$ IC2-Hu from its own cell each cycle, limited by its remaining capacity. Then it runs its normal IC2 behaviour against a virtual reactor in which:

- the reactor hull has **zero heat** and cannot be drawn from;
- "adjacent components" are the items in the four neighbouring buses.

The consequences:

- **Self-venting** components (Heat Vent, Advanced Heat Vent, Overclocked Heat Vent) dissipate their IC2-rated amount per cycle, typically 6, 12 and 20 IC2-Hu (150, 300 and 500 Hu). In steady state this is their cooling capacity. Intake is limited by remaining capacity, so a vent fills up but is not destroyed by its own intake.
- **Hull-drawing** vents (Reactor Heat Vent, the hull-draw part of the Overclocked Heat Vent) draw nothing, because the hull is at zero.
- **Component Heat Vents** cool heat-storing items in adjacent buses: coolant cells and other vents.
- **Heat exchangers** balance heat between adjacent heat-storing items according to IC2 rules.

Vents give small but indefinite cooling and consume nothing. Their per-cell capacity is one to two orders of magnitude below that of a coolant hatch.

### 8.10 Item Intake Validation and the Molten Cheese Easter Egg

The MPTR Core Bus enforces strict allowlisting: unrecognised items (ores, ingots, blocks, tools, unrelated mod items) are **strictly forbidden** and cannot be inserted into the core bus by automation or player interaction.

**The Molten Cheese Easter Egg:**
The sole exception to the core item filter is any item that possesses a Fluid Extraction recipe yielding Molten Cheese (`fluid.cheese` / `molten cheese`). When placed into a Nuclear Core Bus:
- If the cell temperature exceeds **65 °C**, the item is processed in place and converted into molten cheese.
- Cheese-extractable items have minimal neutronic interaction: absorption $p_a = 0.02$, scattering $p_s = 0.05$, and baseline moderation **$p_m = 0.05$**. They do not moderate above the empty-cell baseline.
- Once extracted, the molten cheese must be drained or removed according to standard fluid handling.

### 8.11 Hatch fluids as core cells

A Nuclear Core Hatch is both a **cooling device** (§11.2) and a **neutronic cell** whose properties depend on the fluid it holds:

- **Heavy water** is the best moderator in the game: 85% interaction, 90% of scatters thermalise, and it absorbs only 1% of thermal neutrons. Each fast absorption also has a small chance to **transmute** heavy water to tritium: $\min(1000, 10\,c)/1000$ per event, where $c$ is the neutron count; this consumes 1 L and produces 1 L. Heavy-water hatches are therefore also a slow tritium source.
- **Distilled water** scatters well (0.70) but moderates poorly (0.25) and absorbs 10% of thermal neutrons. Fast absorptions transmute distilled water to deuterium with chance $\min(1000, 25\,c)/1000$ (1 L → 1 L).
- **IC2 coolant** does **not** moderate significantly (**$p_m = 0.05$**), with thermal absorption of 0.12, but conducts heat best ($c = 0.50$). Only water coolants (distilled water and heavy water) provide significant moderation.
- An **empty hatch** is nearly transparent ($p_m = 0.05$). A hatch that runs dry during operation changes neutronically as well as thermally. The core can respond abruptly as moderation disappears.

### 8.12 High-Pressure Passages as core cells

A passage cell is a strong fast scatterer (0.80). Its moderation depends on the loop fluid (table in §8.1). It is the best conductor in the core ($c = 0.90$), so it pulls heat from its neighbours efficiently. Its cooling behaviour is described in §11.4.

---

## 9. Control rods and SCRAM

### 9.1 Placement and materials

A **Control Rod hatch** goes in the bottom layer (slice 4), facing down, directly beneath a core cell. It is not a cell itself. It modifies the neutron properties of the cell above it. Any cell can take a rod: fuel, moderator, or conductive hatch. A pressurized coolant loop passage column cannot have a control rod because its bottom block must be the passage outlet.

The hatch holds one **long rod** (`stickLong`) of an absorber material. The material sets the maximum absorption at full insertion:

| Rod material | Max thermal absorption $r_t$ | Max fast absorption $r_f$ |
|---|---|---|
| Silver | 0.50 | 0.20 |
| Boron | 0.70 | 0.35 |
| Cadmium | 0.85 | 0.50 |
| Indium | 0.95 | 0.65 |
| Hafnium | 0.99 | 0.80 |

### 9.2 Insertion

Insertion $i \in [0, 1]$ is re-evaluated every cycle from the hatch's inputs, in this priority:

1. **SCRAM active** → $i = 1$.
2. **ProjectRed bundled input present** → $i = s/255$, where $s$ is the analogue level on the selected channel (or the strongest channel in *Auto*). A connected bundled cable takes priority even if every channel is 0.
3. **Vanilla redstone** → $i = p/15$, where $p$ is the strongest incoming signal.

Rods have **no travel time**. A new insertion takes effect in the next cycle. A screwdriver cycles the bundled channel through *Auto*, 0, 1, …, 15.

### 9.3 Effect on the cell

The rod contributes its own probabilities:

$$p_{rod,t} = \max(0.01,\ i \cdot r_t), \qquad p_{rod,f} = \max(0.01,\ i \cdot r_f), \qquad p_{rod,s} = \max(0.01,\ 0.05\,(1 - i))$$

An empty rod hatch contributes 0.01 absorption and 0.02 scattering. These combine with the cell's own (base) values as independent processes:

$$p_a = 1 - (1 - p_{a,base})(1 - p_{rod})$$

Moderation is unchanged.

When the combined cell absorbs neutrons, the absorbed count is split between rod and cell in proportion to their probabilities. The cell's share is

$$\frac{p_{a,base}}{p_{a,base} + p_{rod}}.$$

**Only the cell's share counts towards fission** (§5.1) and fuel wear. The rod's share is simply removed. All absorption heat is deposited in the cell.

Two worked cases with a fully inserted Hafnium rod:

- **Under a fuel cell.** Thermal absorption rises from 0.80 to 0.998, but the fuel's share is $0.80/1.79 = 0.447$. The fuel's effective fission-driving thermal absorption falls from 0.80 to **0.446**, a 44% reduction. Fast absorption rises from 0.25 to 0.85 and blocks transit through the cell.
- **Under a moderator.** Graphite's fast absorption rises from 0.002 to 0.80. Most packets entering it are now absorbed rather than thermalised, and the few thermal neutrons born there are almost all absorbed (0.99) before they spread. A fully inserted rod turns a moderator into a strong absorber.

Rods are therefore far more effective under **moderators and water hatches** than under fuel. Under fuel a rod only partly suppresses that one cell's chain term. Under a moderator it cuts off the thermal supply to every surrounding fuel cell.

### 9.4 Rod wear and temperature

Rods do not wear from neutron absorption. A rod's temperature is defined as the temperature of the cell above it. If that cell exceeds the core's $T_{max}$, the rod item is **voided** and the reactor takes 1% damage (§13.2). An overheated region therefore loses its rods exactly when they are most needed.

### 9.5 SCRAM

SCRAM is a button on the controller GUI. There is **no automatic SCRAM**. No temperature, damage or flux condition triggers it, and no redstone or OpenComputers command sets it.

When SCRAM is engaged:

- **all control rods are immediately forced to 100% insertion** (regardless of redstone or fine ProjectRed signals);
- **the simulation continues running every cycle.** Neutrons continue to be generated by fuel, but are heavily absorbed by the fully inserted control rods;
- **heat conduction and cooling continue.** Conductive hatches continue to boil water and produce steam, and High-Pressure Passages continue to transfer heat to circulating coolant loops;
- if the control rods provide sufficient negative reactivity to bring the core subcritical ($k_{eff} < 1$), neutron flux decays towards zero and the core safely cools down to ambient temperature;
- if the core has insufficient control rods or excessive reactivity, fission reactions and heat generation will continue even under SCRAM.

SCRAM is an **emergency rod insertion command**, not a pause or freeze button. Disengaging SCRAM restores control of each rod back to its incoming redstone / bundled signal.

---

## 10. Telemetry, control hatches and automation

### 10.1 Controller GUI

The main screen shows:

- core (maximum) temperature and average temperature;
- average reactivity (§5.4);
- neutron flux ($\text{neutrons/cm}^2/\text{sec}$) and cumulative neutron fluence ($\text{neutrons/cm}^2$);
- per-cycle counts: neutrons generated, fast absorbed, thermal absorbed, escaped;
- reactor damage;
- the SCRAM button.

**Inspect Core** opens a grid view of all cells, with tabs:

| Tab | Shows per cell |
|---|---|
| Components | item or fluid in the cell |
| Temperature | temperature heat-map |
| Heat output | heat removed by cooling this cycle (Hu/s) |
| Neutron flux | fast / thermal flux through the cell ($\text{neutrons/cm}^2/\text{sec}$) |
| Neutron absorption | fast / thermal absorptions |
| Control rods | rod material and insertion |

Cells above 85% of $T_{max}$ blink red.

### 10.2 WAILA / HWYLA

The controller tooltip shows:

- core temperature, with a bar coloured at 50%, 75% and 90% of $T_{max}$;
- cumulative neutron fluence ($\text{neutrons/cm}^2$);
- reactor damage;
- a **MELTDOWN IMMINENT** warning at damage ≥ 90%.

### 10.3 Nuclear Control hatch

The Nuclear Control hatch is a telemetry output on the ring. It has no control logic: no setpoints, no PID loop, no ability to SCRAM. It picks one **metric** and one **statistic** over the relevant cells. That gives 18 modes, encoded as mode = 3·metric + statistic.

> [!IMPORTANT]
> **Facing, uprightness & ownership.** To prevent adjacent reactors from confusing hatch ownership, every Nuclear Control hatch must face strictly **outward away from the center of the structure** and be installed **upright relative to the controller** (`hatchUp == controllerUp`). On flat walls (front, back, left, right), only the perpendicular outward direction is permitted. In corner cut-away columns, only the two outward-facing directions for that corner quadrant are valid. Inward-facing, vertical, or inverted/sideways orientations cause the structure check to fail.
>
> The front face texture features an identifiable top indicator chevron and terminal header (glowing when active), allowing operators to visually discern orientation. Right-clicking the front face with a wrench rotates the hatch through normal, clockwise, upside-down, and counter-clockwise alignments.

| Metric | Measured over | Normalised to |
|---|---|---|
| Temperature | all cells | $T_{max}$ |
| Coolant item durability | coolant cells | % remaining |
| Fuel item durability | fuel rods | % remaining |
| Coolant hatch fill | coolant hatches | % of capacity |
| Fuel hatch fill | liquid-fuel hatches | % of capacity |
| Reactor damage | reactor | % |

Statistics: minimum, maximum, average.

Outputs, updated once per cycle:

- vanilla redstone 0–15 on the front face;
- ProjectRed bundled 0–255 on the front and back faces, in one of three modes:
  - **Multi**: channels 0–14 carry a fixed set of other metric/statistic values, and channel 15 carries the selected value;
  - **Broadcast**: the selected value on every channel;
  - **Single channel**.

Tools:

| Tool | Action |
|---|---|
| Wrench on front face | rotate hatch alignment (upright / clockwise / upside-down / counter-clockwise) |
| Wrench on other side | change facing direction |
| Screwdriver | cycle metric |
| Sneak + screwdriver | cycle bundled mode |
| Soldering iron | cycle statistic |

To get closed-loop control, the operator wires a Nuclear Control hatch, through external logic (comparators, ProjectRed gates, OpenComputers), to Control Rod hatches. For example: *max temperature* → inverted analogue → rod insertion on the moderator columns gives proportional temperature control. Every control loop has at least a one-cycle (one-second) lag, and rod effects themselves take one more cycle to show up in neutron production (§5.1). Gains must be chosen accordingly.

### 10.4 OpenComputers

The Nuclear Control hatch is also an OpenComputers component, `nuclear_control_hatch`. It is read-only.

| Method | Returns |
|---|---|
| `getReactorTelemetry()` / `getTelemetry()` | Table including `isFormed`, `isActive`, `coreTemp`, `avgTemp`, `maxSafeTemp`, `ambientTemp`, `reactivityEfficiency`, `directPowerEUt`, `neutronsProduced`, `fastNeutronsAbsorbed`, `thermalNeutronsAbsorbed`, `escapedNeutrons`, `scram`, casing name/tier, `outputCoolantName` / `outputCoolantRate`, transmutation byproducts, counts of zeroed fuel and coolant items, min/avg/max tables per metric, and `lastCycle` |
| `getGridData()` / `getReactorGrid()` | Per-cell entries with `gx`, `gy`, `type`, `temperature`, `fastFlux`, `thermalFlux`, `fastAbsorbed`, `controlRod`, fuel and coolant contents and amounts. Passage cells have no `type` field |

OpenComputers cannot write rod insertion directly. A computer drives rods through redstone or bundled-cable output cards.

### 10.5 IC2 Nuclear Control

A **Modular Nuclear sensor kit** (`kit_modular_nuclear`) applied to the controller produces a **sensor card** (`card_modular_nuclear`). The card shows up to 12 lines on an Information Panel: temperatures, reactivity, power, neutron counts, damage and status.

---

## 11. Cooling

### 11.1 Overview of heat-removal paths

Heat leaves the core by five routes. They differ by orders of magnitude in capacity:

| Route | Mechanism | Capacity (order of magnitude) | Consumes | Produces |
|---|---|---|---|---|
| Boundary loss | conduction to ambient at the grid edge | ~2.5% of excess per exposed side per cycle | — | — |
| Heat vents | IC2 self-venting items | 150–500 Hu per cycle per vent | — | — |
| Item coolant cells | heat stored in the item | 1% of capacity per cycle, finite | the cell (ejected when full) | full coolant cell |
| Conductive hatches | heat drawn into a hatch fluid | up to ~32 Hu per cycle per °C above sink | coolant fluid | steam / hot coolant |
| High-Pressure Passages | forced convection into a pumped loop | resets its cell to loop temperature between cycles (≈32 Hu per cycle per °C above loop) | small radiolysis losses | loop heat; steam via PHE |

Cooling devices act on **the cell they occupy**. Heat from fuel reaches them only by **conduction** (§6.2). The layout problem is therefore always the same: make sure every fuel cell has a short, conductive path to a cooled cell, with enough temperature difference along it.

### 11.2 Conductive cooling (Nuclear Core Hatches)

#### Heat transfer

A Nuclear Core Hatch holding a coolant fluid cools its own cell once per cycle, after conduction. The hatch has a heat-transfer strength $U$ set by its voltage tier (LV = 1, MV = 2, … UHV = 9):

$$U = 2^{\text{tier}-1} \cdot \eta$$

*(Note: any sub-LV or ULV hatch clamps to the LV baseline of $U = 1 \cdot \eta$.)* Here $\eta$ is maintenance efficiency. The heat removed in the cycle is

$$Q = 32\,(T - T_{sink})\left(1 - e^{-U/32}\right) \ \text{Hu}$$

This is an exact exponential relaxation. In each cycle the hatch removes a fixed **fraction** $1 - e^{-U/32}$ of its excess temperature over the sink temperature:

| Hatch | $U$ ($\eta = 1$) | Fraction of excess removed per cycle | Hu per cycle per °C of excess |
|---|---|---|---|
| LV | 1 | 3.1% | 0.98 |
| MV | 2 | 6.1% | 1.94 |
| HV | 4 | 11.8% | 3.76 |
| EV | 8 | 22.1% | 7.08 |
| IV | 16 | 39.3% | 12.6 |
| LuV | 32 | 63.2% | 20.2 |
| ZPM | 64 | 86.5% | 27.7 |
| UV | 128 | 98.2% | 31.4 |
| UHV | 256 | ≈100% | 32.0 |

From ZPM upwards, a hatch nearly resets its own cell to the sink temperature every cycle. Beyond that point a higher hatch tier gives little. The limit becomes conduction from the neighbouring cells into the hatch cell.

#### Coolant consumption and products

The heat removed is paid for in coolant:

$$L = \min\left(L_{stored},\ \operatorname{round}\left(\frac{Q}{h_L}\right)\right)$$

The cell then cools by $L\,h_L/32$ °C. If the hatch holds less coolant than needed, cooling is limited by what is there.

| Coolant | Sink $T_{sink}$ | $h_L$ (Hu per litre) | Product per litre | Thermal-shock threshold |
|---|---|---|---|---|
| IC2 Coolant | $T_{amb}$ | 20 | 1 L IC2 Hot Coolant | none |
| Distilled Water | $\max(T_{amb}, 100)$ | 17.65625 | 160 L Steam | 100 °C |
| Heavy Water | $\max(T_{amb}, 101.4)$ | 17.65625 | 160 L Heavy Water Steam | 101.4 °C |

Products go to the ring output hatches. The controller reports the output rate.

Worked example: an EV hatch of distilled water whose cell sits at 600 °C removes $7.08 \times 500 = 3\,540$ Hu/s. That consumes 200 L/s of water and produces 32 000 L/s of steam.

Operational consequences:

- **Water-based coolants cannot cool below 100 °C.** A water hatch next to fuel whose peak is near or below ~300 °C (glowstone, lithium) gives little cooling at normal operating temperatures. IC2 coolant sinks to ambient.
- **IC2 coolant never thermal-shocks** and is accepted from the lowest pipe tier. Its 1:1 hot-coolant product is easy to handle downstream.
- **Water hatches double as moderators** (§8.10). Changing a hatch's coolant changes the core's neutronics as well as its cooling.
- A hatch that runs **dry** stops cooling. It is also flagged, and refilling it while it is above its boiling threshold causes a **thermal shock** (§13.1).

> [!NOTE]
> **Implementation note.** In the core's accounting, one litre of distilled water absorbs 17.66 Hu of heat and yields 160 L of steam. GregTech values 160 L of steam at 80 EU in a steam turbine. Conductive cooling therefore converts heat into steam that yields roughly 4.5× EU relative to heat units. The reactor's Heat Units (Hu) represent thermal energy ($1 \text{ Hu} = 128 \text{ J}$) and must not be confused with GT electrical EU.

#### Hatch capacity

Hatch capacity is $8000 \cdot 2^{tier}$ L. At the consumption rates above, a hatch drains in minutes unless it is continuously supplied, for example through a GT fluid pipe, ME fluid interface or similar into the hatch's top face. Supply interruptions are the most common cause of dry hatches and subsequent thermal shocks.

### 11.3 Item-based cooling

Item coolant cells (§8.7) and vents (§8.8) need no fluid logistics. They are limited either in total capacity (cells) or in rate (vents), and they displace a fuel or moderator cell. Their main roles are to absorb transients during start-up and to provide small amounts of passive cooling in low-power cores.

### 11.4 High-Pressure Passages

#### Structure

A High-Pressure Passage is a pair of hatches in the same grid column:

- the **inlet** in slice 0, facing up, which is the grid cell;
- the **outlet** in slice 4, facing down.

Both are part of a CoolantLoops loop. The pump discharge leads to the inlet, and the outlet leads on towards the pump suction. Passages are accessible to any reactor using **Osmium (LuV) or higher item pipe casings**, irrespective of the size of the reactor (available on 5×5, 9×9, or 13×13). They have no Forge fluid interface; they are reachable only through the loop. Only the inlet takes part in the core simulation. The outlet is a loop connection point, and the temperature its GUI shows is not updated.

#### Heat transfer

While the loop pump is **circulating** (or decelerating), every passage exchanges heat with the loop **every tick**, independently of the reactor's 20-tick cycle. The passage is modelled as a 5 m long tube of 0.10 m bore:

- heat-transfer area $A = \pi D L = 1.571$ m²;
- flow area $\pi D^2/4 = 0.00785$ m².

Given loop volumetric flow $\dot V$ and fluid properties $\rho, \mu, c_p, k$:

$$v = \frac{\dot V}{0.00785}, \qquad Re = \frac{\rho v D}{\mu}, \qquad Pr = \frac{\mu c_p}{k}$$

$$Nu = \begin{cases} 4.36 & Re < 2300 \\ 0.023\,Re^{0.8}\,\max(0.6, Pr)^{0.4} & Re \ge 2300 \end{cases} \qquad h = \frac{Nu \cdot k}{D}$$

The energy moved in one tick ($dt = 0.05$ s) is $hA\,\Delta T\,dt$ joules, converted at **128 J = 1 Hu**, with $\Delta T = T_{cell} - T_{loop}$. The cell cools by that energy divided by 32 Hu/°C, but never by more than $\Delta T$ in one tick. Heat transfer per tick is therefore at most $32\,\Delta T$ Hu.

The table gives representative values at $\dot V = 100$ L/s ($v = 12.7$ m/s):

| Loop fluid | $Re$ | $Pr$ | $h$ (W/m²K) | Hu per tick per °C | Fraction of ΔT removed per tick | Over one 20-tick cycle |
|---|---|---|---|---|---|---|
| Distilled water | 1.34 × 10⁶ | 6.5 | 23 600 | 14.5 | 45% | ≈100% |
| Heavy water | 1.13 × 10⁶ | 8.8 | 22 700 | 13.9 | 43% | ≈100% |
| IC2 coolant | 5.5 × 10⁵ | 18.3 | 15 000 | 9.2 | 29% | ≈100% |
| Sodium | 1.69 × 10⁶ | (0.6) | 1.5 × 10⁶ | 32 (capped) | 100% | 100% |

$h$ scales with $v^{0.8}$. At 10 L/s, water drops to about 2.3 Hu per tick per °C (7% per tick). That still removes about 77% of the passage cell's excess over one cycle.

Core-side conduction happens once per cycle, but passage cooling acts on every tick in between. So at any useful flow rate, a passage pulls its cell back almost to loop temperature before the next cycle. The result is comparable to a UV/UHV conductive hatch, but consumes no coolant and has no 100 °C sink floor. Heat removal is then limited by two things:

1. **conduction from neighbouring cells into the passage** during the cycle. The high passage conductivity ($c = 0.90$) exists for this reason;
2. **the loop temperature itself** (§11.5.8).

No heat is exchanged if the flow is below 10⁻⁶ m³/s, i.e. when the pump is not running, is filling, has failed, or the loop is stopped. **Loss of flow means loss of all passage cooling.**

#### Radiolysis

Neutrons absorbed in a passage decompose the loop coolant. For every 4 neutrons absorbed, 2 L of coolant are consumed from the loop inventory, and:

- heavy water produces 2 L of tritium and 1 L of oxygen;
- distilled water produces 2 L of deuterium and 1 L of oxygen.

The products enter the loop as **dissolved gas** and increase impeller wear (§11.5.5). They must be removed with a Pressurized Degasser (§11.5.9), which also recovers them as products.

> [!NOTE]
> **Energy conservation.** Heat removal from the reactor core into the coolant loop is strictly energy-conserving: the thermal energy gained by the coolant passage segment ($\Delta E = M_{\text{seg}} c_p \Delta T$) exactly matches the heat energy removed from the reactor core tile ($Q$). The exchange is bounded by equilibrium to prevent temperature overshoot in a single tick.

### 11.5 The CoolantLoops system

#### 11.5.1 Loop topology

A loop is discovered by a **graph crawl** that starts at the pump's discharge hatch and follows pipes until it returns to the pump's suction hatch. The crawl is repeated when the pump starts and whenever the loop is revalidated. Rules:

- Pipes must be CoolantLoops **coolant pipes**. Their material must match the pump's tank material family (iron-family materials are interchangeable), and all pipes between two devices must have the same size. A pipe holding any Forge fluid breaks the loop.
- **Splits and merges require manifolds.** A plain pipe junction is invalid. A manifold must be entered along its normal axis.
- Devices recognised in-line:
  - High-Pressure Passage hatches;
  - Pressurized Heat Exchanger and Pressurized Degasser hatches. The controller is found within ±2 blocks horizontally at the same Y;
  - Loop Instruments;
  - the Creative Heat Reservoir.
- The loop must be a **directed acyclic graph** from discharge to suction. Every branch must reach the suction, and no cycles are allowed. Loop crawling resolves **asynchronously over multiple server ticks**, processing up to **1 000 steps per tick** across up to **20 ticks** (allowing circuits up to **20 000 steps** total). The pump tracks its crawl start and completion timestamps in server memory. If a circuit exceeds the 20-tick / 20 000-step ceiling, the controller aborts and displays the error: `Loop structure check could not complete because the loop is too large`.

Each element becomes a **segment** with length, diameter, minor-loss coefficient $K$, pressure rating, temperature rating and fill volume:

| Element | L (m) | D (m) | K | Fill (L) |
|---|---|---|---|---|
| Pipe (per block) | 1 | by size | 0.05 | pipe capacity |
| Manifold block | 1 | 0.10 | 0.3 | 1 000 |
| HP Passage | 5 | 0.10 | 2.5 | 1 000 |
| Degasser | — | — | 1.5 | 1 000 |
| Other device | — | — | — | 1 000 |
| Instrument | — | — | — | 10 |

The **loop fill volume** is the sum of all fill volumes. The pump must be able to supply this much fluid from its tank before circulation starts.

> [!NOTE]
> **Parallel branch flow splitting and thermal blending.** Parallel branches connected via manifolds split total loop flow in direct proportion to the capacity weights of the branching pipes (§11.5.2):
>
> $$Q_i = Q_{total} \times \frac{w_i}{\sum w_k}$$
>
> For example, a splitter manifold feeding one Medium pipe ($w = 1.0$) and one Large pipe ($w = 2.0$) divides flow into $\frac{1}{3}$ and $\frac{2}{3}$. When parallel branches merge at a manifold, their streams blend temperatures via flow-weighted enthalpy:
>
> $$T_{blend} = \frac{\sum Q_i T_i}{\sum Q_i}$$
>
> Parallel branches do not add pressure drops in series; effective circuit friction is flow-weighted across parallel paths: $\Delta P_{parallel} = \sum \frac{Q_i}{Q_{total}} \Delta P_i(Q_i)$. In-line devices on branches experience their local branch flow $Q_i = Q_{total} \cdot \text{flowFraction}$.

#### 11.5.2 Pipes

| Material | Base capacity | $P_{max}$ (bar) | $T_{max}$ (°C) |
|---|---|---|---|
| Cast Iron | 180 | 15 | 1 977 |
| Steel | 240 | 35 | 2 227 |
| Stainless Steel | 360 | 70 | 2 727 |
| Titanium | 480 | 140 | 4 727 |
| Tungstensteel | 600 | 280 | 7 227 |
| Osmium | 1 200 | 600 | 9 727 |
| Neutronium | 16 800 | 3 000 | ~10⁶ |

| Size | Capacity multiplier | Internal diameter (m) |
|---|---|---|
| Tiny | 1/6 | 0.05 |
| Small | 1/3 | 0.075 |
| Medium | 1 | 0.10 |
| Large | 2 | 0.15 |
| Huge | 4 | 0.20 |

Pipe capacity sets the fill volume and a nominal flow ceiling at the pump hatches. The ceiling is high enough that it practically never binds. Diameter sets the flow velocity, and so both the pressure drop (∝ $v^2$) and, at passages, the heat transfer.

#### 11.5.3 Manifolds

A manifold block splits or merges flow. Its faces are toggled with a wrench. Sneak + wrench sets the manifold plane. Each manifold block is one segment (1 000 L fill, $K = 0.3$). A typical arrangement:

1. an inlet manifold row distributes flow proportionally across several parallel branch pipes or passage inlets based on pipe capacity weights (§11.5.2);
2. in-line devices on each branch (passages, heat exchangers, degassers, instruments, coolant baths) experience their local branch flow $Q_i = Q_{total} \times \text{flowFraction}$;
3. the branch outlets are collected by an outlet manifold row, where stream temperatures are blended by flow-weighting ($T_{blend} = \sum Q_i T_i / \sum Q_i$);
4. the outlet manifold returns to the pump suction.

#### 11.5.4 Loop fluids

| Fluid | ρ (kg/m³) | μ (Pa·s) | $c_p$ (J/kg·K) | k (W/m·K) | Notes |
|---|---|---|---|---|---|
| Distilled water | 998 | 9.5 × 10⁻⁴ | 4 186 | 0.610 | boiling 100 °C (not modelled in loop) |
| Heavy water | 1 105 | 1.25 × 10⁻³ | 4 210 | 0.595 | best passage moderator (0.90) |
| IC2 coolant | 1 080 | 2.5 × 10⁻³ | 3 800 | 0.52 | no boiling |
| Sodium | 927 | 7 × 10⁻⁴ | 1 260 | 86 | freezes below 97.8 °C; near-zero moderation |
| Molten cheese | 1 120 | 0.557 | 3 000 | 0.481 | solidifies below 46.85 °C; very viscous |
| Lava | 3 000 | 1.0 × 10⁻³ | 1 250 | 1.5 | Nether only; dynamic viscosity 1.0 mPa·s (same as water in the Nether); solidifies below 1 000 °C |

The pump refuses to start with:

- plain water or any unknown fluid;
- gases;
- lava outside the Nether;
- a molten coolant (sodium, cheese, lava) below its melting point.

A **liquid nuclear fuel** in the loop destroys the impeller and stops the loop.

The loop has **no boiling model and no ambient heat loss**. Water loops can be run far above 100 °C without phase change.

#### 11.5.5 The coolant pump

**Structure.** The pump base is W×W×2 (W = 3, 5, 7 or 9), placed under a Railcraft tank of the same material. It has frame-box corners and contains:

- one discharge and one suction **Pressurized Fluid hatch**;
- exactly one maintenance hatch;
- one or two energy hatches;
- a GregTech **turbine rotor** in the controller.

The tank is the loop's reservoir. It supplies the fill volume and receives the fluid when the loop is drained.

**Power.** With one energy hatch the pump draws $V_{tier}$ EU/t. With two it draws $4\,V_{tier}$. The mechanical power delivered to the fluid is

$$P = EU \times 80 \times \eta_{rotor} \times \eta_{maint} \ \text{W}$$

where $\eta_{rotor}$ is the base efficiency of the installed GregTech turbine rotor (e.g. 1.00 for 100%, 1.40 for 140%), determined directly from the rotor's material and tool stats via `TurbineStatCalculator`.

**Pump curve.**

$$\dot V_{rated} = \max\left(0.005,\ 0.02\sqrt{P/5000}\right) \ \text{m}^3/\text{s}$$

$$\Delta p_{pump} = \frac{P}{\dot V + \dot V_{rated}}$$

The **shut-off (stall) head** is determined by motor mechanical power and rated slip:

$$\Delta p_{stall} = \frac{P}{\dot V_{rated}} \approx 0.0354\sqrt{P} \ \text{bar}$$

By default, pump stall head is **uncapped** (`maxPumpHeadPressurePa: .inf`), scaling naturally with electrical power input:
- **EV (2 048 EU/t):** $\Delta p_{stall} \approx 14.3\text{ bar}$ (+1 bar base $\approx 15.3\text{ bar}$). Bursts **Cast Iron (15 bar)** at stall/startup; safe for **Steel (35 bar)**.
- **IV (8 192 EU/t):** $\Delta p_{stall} \approx 28.6\text{ bar}$ (1A); with 4A ($P \times 4$), stall head doubles to $\sim 57.2\text{ bar}$, bursting **Steel (35 bar)**.
- **LuV (32 768 EU/t):** $\Delta p_{stall} \approx 57.2\text{ bar}$ (114.5 bar at 4A). Bursts **Steel (35 bar)** and can burst **Stainless Steel (70 bar)** under multi-amp or dead-head conditions; requires **Titanium (140 bar)**.
- **ZPM (131 072 EU/t):** $\Delta p_{stall} \approx 114.5\text{ bar}$ (229 bar at 4A). Bursts **Stainless Steel (70 bar)** and **Titanium (140 bar)**; requires **TungstenSteel (280 bar)**.
- **UV (524 288 EU/t):** $\Delta p_{stall} \approx 229\text{ bar}$ (458 bar at 4A). Bursts **Titanium (140 bar)** and **TungstenSteel (280 bar)**; requires **Osmium (600 bar)**.
- **UHV (2 097 152 EU/t):** $\Delta p_{stall} \approx 458\text{ bar}$ (916 bar at 4A). Bursts **TungstenSteel (280 bar)** and **Osmium (600 bar)**; requires **Neutronium (3 000 bar)**.

The operating flow is where the pump curve meets the loop's resistance curve $\Delta p_{loop}(\dot V) \propto \dot V^2$ (§11.5.6). A more powerful pump or a lower-resistance loop (larger pipes, fewer passages and manifolds, shorter runs) gives more flow.

**States.**

| State | Meaning |
|---|---|
| EMPTY | Loop not filled |
| FILLING | Fluid is transferred from the tank to the loop at 1 000 L/tick until the fill volume is reached |
| CIRCULATING | Normal operation; passages exchange heat every tick |
| DECELERATING | The discharge hatch has been disabled (e.g. by a cover); the pump applies negative head to brake the flow |
| DRAINING | Loop contents return to the tank |
| STOPPED | Idle, filled |
| FAILED | Impeller destroyed, liquid fuel ingested, or rupture |

A **power failure** stops the pump (POWER_LOSS) and requires manual re-enabling. Re-crawling clears any previous loop disconnection failure. Maintenance issues on the pump multiblock are preserved across crawls and must be repaired through standard GregTech maintenance tools or automated maintenance hatches (loop re-crawling does not auto-fix maintenance).

**Impeller wear.** Every 100 ticks the rotor takes wear of $(1 + f_{gas}) \cdot k(T_h)$, where:

- $f_{gas}$ is the loop's dissolved-gas fraction;
- $T_h = T_K / T_{melt,K}$ is the homologous temperature of the rotor material at loop temperature;
- $k$ grows steeply once the rotor softens above $T_h = 0.5$.

At $T_h \ge 1$ the impeller is **destroyed instantly**, flow drops to zero, and passage cooling stops. Choose a rotor material whose melting point is well above the hottest loop temperature, and degas the loop.

#### 11.5.6 Hydraulics and overpressure

Each tick the engine walks the loop from the discharge hatch back to suction. The starting pressure immediately downstream of the pump is 1 bar plus the pump head (from the previous tick's flow). Each segment's total pressure drop $\Delta p_{seg}$ is subtracted in sequence:

$$\Delta p_{seg} = \Delta p_{major} + \Delta p_{minor} = \left(f\,\frac{L}{D} + K\right)\frac{\rho v^2}{2}$$

where:
- $\frac{\rho v^2}{2}$ is the dynamic pressure of the fluid moving at flow velocity $v = \dot V / A_{pipe}$ (in Pa).
- **$K$ is the minor loss coefficient**, accounting for form drag, entrance/exit effects, directional disruptions, and internal restrictions in components:
  - Straight coolant pipe: $K = 0.05$ per meter block;
  - Manifold block: $K = 0.30$;
  - High-Pressure Passage: $K = 2.50$ (entrance, internal tube restrictions, exit);
  - Pressurized Degasser: $K = 1.50$.
- **$f$ is the dimensionless Darcy friction factor**, calculated from the Reynolds number $Re = \frac{\rho v D}{\mu}$:
  - **Laminar regime ($Re < 2100$):** exact Hagen–Poiseuille relation:
    $$f = \frac{64}{Re}$$
    *(clamped to 64.0 for creeping flow $Re < 1$)*.
  - **Transitional and turbulent regime ($Re \ge 2100$):** evaluated using the continuous **Churchill (1977) correlation**, which smoothly bridges smooth-pipe and fully rough turbulent regimes:
    $$f = 8 \left[ \left(\frac{8}{Re}\right)^{12} + \frac{1}{(A + B)^{1.5}} \right]^{1/12}$$
    with relative roughness $\varepsilon/D$ (pipe absolute roughness $\varepsilon = 0.000045$ m) and:
    $$A = \left[ -2.457 \ln\left( \left(\frac{7}{Re}\right)^{0.9} + 0.27\,\frac{\varepsilon}{D} \right) \right]^{16}, \qquad B = \left(\frac{37530}{Re}\right)^{16}$$

**Worked hydraulic examples from the simulation engine:**

- *Straight Medium pipe ($D = 0.10$ m, $L = 1$ m) with water at $v = 1.0$ m/s ($\dot V \approx 7.85$ L/s):*
  Fluid density $\rho = 1000$ kg/m³, $\mu = 0.001$ Pa·s $\implies Re = 100\,000$, $\varepsilon/D = 0.00045$.
  Churchill calculates $f \approx 0.0201$. Dynamic pressure is $\frac{\rho v^2}{2} = 500$ Pa.
  - Major Darcy friction: $\Delta p_{major} = 0.0201 \times \frac{1.0}{0.10} \times 500 \approx 100.5$ Pa.
  - Minor fitting loss: $\Delta p_{minor} = 0.05 \times 500 = 25.0$ Pa.
  - Total drop per meter of pipe: $\Delta p_{seg} \approx 125.5$ Pa ($0.00126$ bar). A 50-meter pipe run drops only $\sim 0.063$ bar.

- *High-Pressure Passage ($D = 0.10$ m, $L = 5.0$ m, $K = 2.50$) with water at full ceiling flow of 100 L/s:*
  Flow velocity $v = 12.73$ m/s, dynamic pressure $\frac{\rho v^2}{2} = 81\,028$ Pa.
  Reynolds number $Re \approx 1.34 \times 10^6$. Churchill yields $f \approx 0.0170$.
  - Major wall friction: $\Delta p_{major} = 0.0170 \times \left(\frac{5.0}{0.10}\right) \times 81\,028 \approx 68\,874$ Pa ($0.69$ bar).
  - Minor device loss: $\Delta p_{minor} = 2.50 \times 81\,028 \approx 202\,570$ Pa ($2.03$ bar).
  - Total passage drop: $\Delta p_{seg} \approx 271\,444$ Pa (**$2.71$ bar** per passage).
  Parallel passages split this resistance proportionally when manifolds are used; the effective pressure drop across parallel branches is flow-weighted rather than additive (§11.5.1).

The pressure in the circuit is highest **immediately downstream of the pump discharge**, where it equals 1 bar ambient plus the pump's developed head.

A segment **ruptures** if:

- its local pressure exceeds its $P_{max}$; or
- its temperature exceeds its $T_{max}$; or
- a molten coolant solidifies in it.

A rupture while **circulating** causes an **explosion** at the segment, of strength $\min(25,\ 4 + p_{peak}/10)$ with $p_{peak}$ in bar, and drains 80% of the pump tank.

Practical limits:

- **Cast Iron (15 bar)** can be burst at start-up by an EV pump, whose stall head is ~14.3 bar plus the 1 bar base.
- **Steel (35 bar)** can be burst by a 4A IV pump or LuV pump.
- **Stainless Steel (70 bar)** can be burst by a 4A LuV pump or ZPM pump.
- **Titanium (140 bar)** can be burst by a 4A ZPM pump or UV pump.
- **TungstenSteel (280 bar)** can be burst by a 4A UV pump or UHV pump.
- **Osmium (600 bar)** can be burst by a 4A UHV pump (~916 bar).
- **Neutronium (3 000 bar)** is immune to burst pressure from any pump.
- Temperature ratings of all metal pipes are above any practical loop temperature. Solidification of sodium, cheese or lava when the loop cools is the realistic temperature-related rupture.

#### 11.5.7 Heat transport around the loop

Each tick, every segment exchanges a fraction $f = \min(1,\ \dot V\,dt / V_{seg})$ of its fluid with its upstream neighbour, mixing temperatures. A temperature front moves **at most one segment per tick**. In a long loop, heat picked up at the passages takes at least as many ticks as there are segments to reach the heat exchanger.

#### 11.5.8 Pressurized Heat Exchanger (PHE)

**Structure.** 3×3×5 of heat-proof casings, with heating and cooling ducts. It contains:

- two Pressurized Fluid hatches (loop suction inlet and discharge outlet);
- one input hatch: strictly **Distilled Water** (`ic2distilledwater`, `distilledwater`, `distilled_water`). Plain Minecraft water (`water`) is strictly rejected and will not be consumed;
- one or more output hatches (steam);
- energy ($V$ or $4V$).

The loop-side roles of the two pressurized hatches are assigned by structure-iteration order: the first encountered becomes the suction inlet, the second becomes the discharge outlet.

**Operation.** When the circulating loop fluid at the PHE is above 100 °C:
The local coolant flow velocity $v = \dot V / A_{seg}$ through the internal tube bundle ($A = 24\text{ m}^2$) drives convective heat transfer into the secondary distilled water:

$$\dot Q_{conv} = h(v) \times A \times (T - 100) \times \eta \quad [\text{W}]$$

where $h(v)$ is computed by the convective heat transfer model (with zero convective transfer when flow is zero).

Secondary distilled water is vaporized using the standard GTNH latent heat of vaporization:

$$L_{vap} = 2\,260\,000\text{ J/L} = 2\,260\text{ J/mL}$$

Entering secondary distilled water on the low-pressure side is assumed to enter at the pump's ambient temperature ($T_{amb}$); the sensible heat needed to raise it from $T_{amb}$ to boiling ($100\text{ }^\circ\text{C}$) is negligible compared to the large latent heat of vaporization ($2\,260\text{ kJ/kg}$).

The amount of water vaporized in one tick ($\Delta t = 0.05\text{ s}$) is bounded by the convective heat transfer rate, available sensible heat in the coolant above $100\text{ }^\circ\text{C}$, and stored distilled water:

$$V_{water} = \min\left(V_{input},\ \left\lfloor \frac{\min(\dot Q_{conv} \cdot \Delta t,\ C_{coolant} \cdot (T - 100))}{L_{vap}} \right\rfloor\right) \quad [\text{mL/tick}]$$

where $C_{coolant} = M_{eff} \cdot C_{p,coolant}$ is the heat capacity of the coolant passing through the exchanger during that tick ($M_{eff} = \max(V_{seg},\ \dot V \cdot \Delta t) \times \rho$).

Each litre of vaporized water expands into 160 L of steam according to the coolant temperature:

| Loop temperature | Steam product | Expansion |
|---|---|---|
| 100–300 °C | Steam | 1:160 |
| 300–700 °C | IC2 Superheated Steam | 1:160 |
| ≥ 700 °C | Supercritical Steam | 1:160 |

**Strict Energy-Conserving Heat Removal.** The actual thermal energy absorbed by secondary water vaporization:

$$Q_{actual} = V_{water} \times L_{vap} \quad [\text{J}]$$
$$\dot Q_{transferred} = \frac{Q_{actual}}{\Delta t} \quad [\text{W}]$$

This exact energy is removed from the primary loop coolant at the PHE segment:

$$\Delta T_{drop} = \frac{Q_{actual}}{C_{coolant}} \quad [^\circ\text{C}]$$
$$T_{new} = \max(100.0,\ T - \Delta T_{drop})$$

The cooled coolant is immediately written to the PHE segment and discharge outlet hatch. In the subsequent tick, the pump's hydrodynamic engine advects this cooled fluid downstream, returning cold coolant to the reactor pressure tubes. If secondary distilled water runs out, $V_{water} = 0$, so no heat is removed and the loop temperature does not drop.

#### 11.5.9 Pressurized Degasser

**Structure.** 3×3×7 ($K = 1.5$ in-loop), with output hatches for the recovered gases.

**Operation.** Only while the pump is circulating. For each dissolved gas:

$$\text{extracted per tick} = \min\left(\frac{EU}{8}\,dt,\ 0.25\,\frac{V_{gas}}{V_{fill}}\,\dot V\,dt\right) \times \eta$$

Extraction is proportional to the gas concentration and to the flow through the unit. A degasser holds the dissolved-gas fraction down, reducing impeller wear. It also recovers deuterium, tritium and oxygen from radiolysis. In a heavy-water loop it is a tritium source.

#### 11.5.10 Loop Instruments and OpenComputers

A **Loop Instrument** placed in the loop reports one of:

- flow;
- temperature;
- pressure;
- gas fraction;
- impeller wear;
- fill;
- reservoir level.

Output options are vanilla redstone (0–15), fine (0–255) or ProjectRed bundled. The value is refreshed every 10 ticks.

> [!NOTE]
> **Implementation note.** The instrument's *pressure* metric is the **local pressure drop across its own segment**, not the loop pressure. Use the OpenComputers `peakPressureBar` field for overpressure monitoring.

The instrument is also an OpenComputers component, `pressurized_instrumentation_computer`. Methods include `getLoopTelemetry()`, `getPumpStatus()` and `getImpellerStatus()`. They return flow, temperatures, pressures (including `peakPressureBar`), gas content, fill and impeller condition.

#### 11.5.11 Creative Heat Reservoir

A creative-only loop device that drives the loop towards a target temperature. The target defaults to 300 °C and cycles through 20 / 100 / 300 / 600 / 1 000 °C. It exchanges heat over an area of 2 m², conserving energy. It acts as a heater when the loop is colder than the target and as a **sink** when the loop is hotter. In the current build it is the only device that can remove heat from a loop.

---

## 12. Maintenance and reactor damage

### 12.1 Maintenance efficiency

The reactor uses the standard GregTech maintenance hatch and its six tool issues. It combines the maintenance state with its own **reactor damage** $D_R$ (0–100%) into a single efficiency:

$$\eta = \frac{n_{ok}}{6} \cdot \frac{100 - D_R}{100}$$

where $n_{ok}$ is the number of tools *not* currently needing repair.

$\eta$ scales:

- the heat-transfer strength $U$ of conductive hatches (§11.2);
- the heat intake of item coolant cells and vents (§8.7–§8.8);
- aggregate radiovoltaic direct EU output linearly (§8.6);
- the convective machinery of CoolantLoops, which applies its own maintenance hatches.

$\eta$ does **not** scale neutron production or fission heat. A poorly maintained reactor makes as much heat as a healthy one but removes less of it. Neglected maintenance therefore leads to overheating, which leads to damage, which lowers $\eta$ further: a positive feedback loop.

### 12.2 Maintenance rules specific to the reactor

- **A newly built reactor starts with all six issues present.** With all six present the controller stops with *no repair* and the simulation does not run. All issues must be fixed before the first cycle.
- **Orientation and uprightness:** The Maintenance Hatch must face strictly outward away from the core structure (matching the outward-facing rule of the Nuclear Control Hatch) and must be installed **upright relative to the controller** (`hatchUp == controllerUp`). If a maintenance hatch faces inward, vertically, or upside-down relative to the controller, the multiblock structure check fails. This strictly prevents an upright reactor and an inverted/upside-down reactor from illegally wall-sharing a single maintenance hatch.
- **While $D_R > 0$, the wrench issue is forced** to remain or reappear. The reactor never runs at full efficiency while damaged.
- **Repairs reduce damage.** Each tool issue repaired reduces $D_R$ by 2 percentage points, but only while the **average core temperature is at or below 10% of $T_{max}$** (§2.4). Repeatedly fixing the forced wrench issue on a cold reactor brings damage back to zero.
- **Damage is persistent.** It is stored in the controller and kept on the dropped controller item. Breaking and replacing the controller does not reset it.

### 12.3 Damage accrual

| Event | Damage |
|---|---|
| Thermal shock | +2% per shock, plus one new maintenance issue |
| Overheating cell | +1% per overheated core-access block per cycle |
| Overheating control rod | +1% per overheated rod hatch per cycle |

Every core-access block and rod hatch whose temperature exceeds $T_{max}$ at the end of a cycle counts, **whether or not it still contains anything**. A rod hatch's temperature is that of the cell above it, so a rodded cell counts twice. High-Pressure Passage cells are not checked.

The reactor models vessel wall and structural fatigue driven by escaping neutron flux. Once per minute (every 60 cycles), the controller computes the average rate of escaped neutrons $\bar{N}_{\text{esc}}$ (in n/s) over the preceding minute and evaluates a saturating sigmoid wear response:

$$S(\bar{N}_{\text{esc}}) = \frac{\bar{N}_{\text{esc}}^2}{\bar{N}_{\text{esc}}^2 + 1\,000^2}$$

This scales the base maintenance problem rate ($P_{\text{base}} = 1\%$ per minute) by a factor between $1.0\times$ and $10.0\times$:

$$P_{\text{maint}} = P_{\text{base}} \times \left(1.0 + 9.0 \times S(\bar{N}_{\text{esc}})\right)$$

- At zero leakage ($\bar{N}_{\text{esc}} = 0$), maintenance problems occur at the baseline rate of $1\%$ per minute (an average of one issue every 100 minutes of continuous operation).
- At the midpoint of $1\,000$ escaped n/s, $S = 0.5$ and the probability rises to $5.5\%$ per minute (~18 minutes between issues).
- At extreme leakage ($\bar{N}_{\text{esc}} \gg 1\,000$ n/s), the rate saturates at $10.0\%$ per minute (~10 minutes between issues).

Operators can suppress this wear by surrounding the core perimeter with high-scattering reflector and moderator blankets (beryllium, carbon, heavy water) rather than exposing vessel walls to unmoderated escaping flux.

---

## 13. Failure modes and hazards

This section lists every mechanism by which the reactor or its loop destroys material, damages itself, or affects the world, in roughly increasing order of severity.

### 13.1 Thermal shock

**Triggers.** A Nuclear Core Hatch is shocked at the start of a cycle if either:

1. the ambient temperature is at or above the hatch fluid's boiling threshold (§11.2). With the vanilla biome range (≤ 93.3 °C) this cannot happen for water (100 / 101.4 °C). It is relevant only for modded biomes or dimensions; or
2. the hatch was flagged **dry** (it ran out of fluid while operating) and has since been **refilled while its temperature is above the threshold**.

IC2 coolant has no threshold and never shocks. Liquid fuels and any other fluid use a threshold of 100 °C.

**Effects:**

- the hatch's fluid is voided (evaporates / flashes immediately);
- reactor damage +2%;
- one additional maintenance issue;
- an overload sound plays;
- the simulation **carries on normally for the rest of the cycle** (neutrons, heat diffusion, conduction, and power continue calculating; only if cumulative damage reaches 100% does meltdown interrupt the cycle).

The *dry* flag is saved with the hatch, so it survives chunk unloading and restarts.

**Avoidance.** Never let water hatches run dry. If one has, do not refill it until its cell has cooled below 100 °C. In practice that means SCRAM is not enough (§9.5): reduce power with rods and let the cell cool through conduction, or keep the supply continuous.

### 13.2 Overheating and content voiding

At the end of each cycle, every cell is compared against the pipe tier's $T_{max}$. For each cell above it:

| Cell content | Consequence |
|---|---|
| Non-fuel item in a bus (moderator, reflector, absorber, coolant cell, vent, radiovoltaic, insulator…) | **Voided** |
| Non-fuel fluid in a hatch (coolant) | **Voided** |
| Control rod beneath the cell | **Voided** |
| Fuel rod or liquid fuel | Kept |
| Every overheated bus/hatch and rod hatch | +1% reactor damage per cycle |

Fuel is kept, so an overheated region keeps producing heat. Its reactivity falls along the curve, but never below 5%. Meanwhile the overheated region loses its moderators, absorbers, rods and coolant, i.e. exactly the components that controlled and cooled it. The core's behaviour changes abruptly:

- losing moderators and water lowers the chain reaction in that region;
- losing absorbers and rods raises it;
- losing coolant removes cooling.

Damage accrues **per overheated block per cycle**. Twenty overheated cells add 20% damage per second. A reactor that loses control over a large region can reach 100% damage, and melt down, **within a few seconds**. This is the primary path to meltdown.

### 13.3 Item and fluid loss summary

| Mechanism | Fuel | Coolant fluid | Item components | Rods | Passage / loop |
|---|---|---|---|---|---|
| Normal fuel depletion | → depleted form | — | — | — | — |
| Breeder cell (glowstone, lithium) end of life | → depleted form (`DepletedRodLithium` / `DepletedRodGlowstone`) | — | — | — | — |
| Coolant cell full | — | — | ejected intact | — | — |
| Reflector wear | — | — | destroyed at 0 durability | — | — |
| Thermal shock | — | voided (that hatch) | — | — | — |
| Overheat (> $T_{max}$) | kept | voided | voided | voided | not checked |
| Structure break while hot (§13.4) | **voided** | **voided** | kept | kept | kept |
| Meltdown (§13.5) | destroyed with the reactor | | | | |
| Loop rupture while circulating | — | — | — | — | 80% of tank drained, explosion |
| Liquid fuel in loop | — | — | — | — | impeller destroyed |

### 13.4 Structure break while hot

If the structure becomes invalid, or the controller is removed, while **any** core cell is above **100 °C**:

- all fuel items and coolant items in Nuclear Core Buses are deleted;
- all coolant fluids (and liquid fuels) in Nuclear Core Hatches are deleted.

Non-fuel, non-coolant items in Nuclear Core Buses (such as reflectors, moderators, heat vents, heat exchangers, reactor plating, thermal insulators, and radiovoltaic plates) as well as control-rod items in control rod hatches and passages are strictly preserved. Conceptually, the penalty for failing the structure check applies only to materials that must be actively and continuously supplied (fuel and coolants). Configuration items that define the spatial layout of the core—even those like moderators and reflectors that experience durability wear—are preserved to spare the operator unnecessary rebuilding tedium.

Before any maintenance that breaks the structure, the core should still ideally be cooled below 100 °C to prevent loss of active fuel rods and coolants.

### 13.5 Meltdown

**Trigger.** Reactor damage reaches 100% at the end of a cycle.

**Sequence:**

1. **Explosion** of power 4.5 at the centre of the reactor chamber.
2. **Corium** placement. A sphere of radius 2 inside the chamber layers is filled with corium, plus 4 additional corium blocks at random positions.
3. An invisible **fallout entity** is spawned at the site.

**Corium** is a flowing fluid block (lava material) with these properties:

- **Destroys any block it flows into**, except bedrock, unbreakable blocks and warded blocks.
- **On contact:**
  - sets the entity on fire for 30 s;
  - deals 10 damage;
  - applies GregTech radioactivity level 5;
  - applies IC2 radiation for 240 ticks at amplifier 3;

  unless the entity wears a **full hazmat suit**.

**Fallout entity:**

| Time after meltdown | Effect |
|---|---|
| 0 | +2 000 000 pollution in the chunk |
| every second | radiation applied to players within 160 m horizontally |
| 2 400 ticks (2 min) | all corium within ±48 blocks solidifies into **bedrock** |
| 12 000 ticks (10 min) | entity despawns |

The bedrock is permanent. A meltdown leaves a bedrock plug where the core was, along with whatever terrain the corium flowed through in the first two minutes.

Reactor damage remains at 100%. If the controller survives (or is retrieved with its damage intact) and the structure is re-formed, **it melts down again** at the first cycle unless damage is first repaired (§12.2).

### 13.6 Dynamo overload explosion

If the direct power output (radiovoltaics) exceeds what the ring dynamo hatch can carry, or there is direct power output but **no dynamo** at all, the GregTech multiblock explosion is triggered:

- every hatch and the controller explode at UV-level strength;
- 100 000 pollution.

A single EV radiovoltaic plate can exceed an EV 1A dynamo. Size the dynamo to the plates' combined $P_{max}$, not to the expected output.

Direct power is not reset by SCRAM or by disabling the machine (§9.5). Removing or downgrading the dynamo of a SCRAMmed reactor that last produced direct power will therefore also trigger the explosion.

### 13.7 Beam particle mismatch explosion

**Trigger.** Any non-neutron particle beam (`Particle` $\ne$ `Particle.NEUTRON`) enters any connected Beam Input Hatch on slice 4.

**Consequence:**
- The reactor immediately triggers `explodeReactor(false, "Incompatible particle beam entered reactor core!")`.
- Destroys the controller and surrounding hatches.
- Releases 100 000 pollution.
- All core materials in buses and hatches are voided.

### 13.8 Coolant loop failures

| Failure | Cause | Consequence |
|---|---|---|
| Loss of flow | pump unpowered (POWER_LOSS), disabled, draining, failed | **All passage cooling stops immediately.** Passage cells then heat by conduction from fuel |
| Overpressure rupture | segment pressure > $P_{max}$ (Cast Iron with EV+ pumps) | explosion if circulating; 80% of reservoir drained |
| Overtemperature rupture | segment temperature > $T_{max}$ | as above |
| Solidification | sodium < 97.8 °C, cheese < 46.85 °C, lava < 1 000 °C in a segment | rupture |
| Impeller destruction | rotor homologous temperature ≥ 1; liquid fuel in loop | flow → 0 instantly; FAILED state |
| Gas accumulation | radiolysis without degassing | accelerated impeller wear |
| Loop heat-up | no effective heat sink in survival (§11.5.8) | loop approaches core temperature; passage cooling decays |

A loop failure turns every passage into an uncooled, highly conductive cell in the middle of the core. Its neighbours lose their main heat path. Treat pump power and impeller condition as safety-critical. Monitor flow and impeller wear with instruments or OpenComputers, and drive rod insertion from those signals.

### 13.9 Output blockage

Depleted fuel, ejected coolant cells, steam and hot coolant are pushed to the ring output buses and hatches. If they are full, the products may be lost. More importantly, a conductive hatch whose product cannot be output still consumes coolant. Keep output paths clear and buffered.

### 13.10 Anatomy of a typical runaway

This sequence illustrates how the individual mechanisms combine:

1. The coolant supply to a water hatch fails. The hatch empties over a few minutes and is flagged dry. Its cell stops cooling.
2. Neighbouring fuel cells lose their heat path. Their temperature rises past the fuel's peak. Reactivity falls, but heat production continues.
3. The fuel temperature crosses $T_{max}$. Neighbouring moderators, rods and the remaining hatches are voided, and damage rises by 1% per overheated block per cycle.
4. Damage lowers $\eta$. Every other hatch in the core loses cooling strength. The overheated region spreads.
5. The operator restores the water supply. The dry, overheated hatch thermal-shocks (+2%, a maintenance issue, the fluid voided).
6. Damage reaches 100% within seconds of step 3. The reactor melts down.

The defences, in order of effectiveness:

- **continuous supply** to every hatch;
- **telemetry-driven rod control**, e.g. Nuclear Control hatch *max temperature* or *min coolant fill* driving rods under the moderators;
- **margin** between the fuels' operating temperatures and $T_{max}$;
- operator awareness that **SCRAM forces all rods to 100%** while cooling continues.

---

## 14. The standalone web simulator

The web simulator compiles a standalone port of the core engine (`StandaloneNuclearGrid`, `SimTile`, `CoolantLoopModel`, `TurbineCalculator`) to WebAssembly. It is a design aid and differs from the in-game behaviour in several respects:

| Aspect | In-game | Web simulator |
|---|---|---|
| Cycle period | 20 ticks | one `simulate()` per simulated "tick"; rates displayed per tick |
| Fuel wear | as §7.3 | multiplied by 0.005 (rods last ~200× longer) |
| Fission multiplier / heat per neutron | 1.0 / 38 Hu | initialised to 0.35 / 76 Hu (exported pages 0.351 / 80.1); loading a preset resets to 1.0 / 38 |
| Reflector moderation | 0.20 | 0.65 |
| Passage moderation | by loop fluid | fixed 0.85 |
| Empty-cell properties | as §8.1 | differ |
| The Core packets | 32 | 16 |
| Base neutrons of HDU / HDP / ExU / ExP / Tiberium | 16 / 32 / 16 / 32 / 32 (quad) | 16 / 24 / 32 / 48 / 48 |
| Overheated fuel | kept | removed |
| IC2 component exchange | via virtual IC2 reactor | dedicated neighbour pass |
| Coolant loop | CoolantLoops engine (§11.5) | separate simplified model |

**Turbine estimation.** The simulator converts coolant outputs into estimated turbine power:

- Hot coolant is routed by pipe tier:
  - EV: Large Heat Exchanger, 1 L → 400 L steam;
  - IV: Thermal Boiler, 1 L → 200 L superheated steam;
  - LuV: Extreme Heat Exchanger, superheated;
  - ZPM+: Extreme Heat Exchanger, supercritical at ≥ 8 000 L/s of coolant.
- Steam is valued at 0.5 EU/L (regular) or 1.0 EU/L (superheated and supercritical, cascading 1:1 to the next lower grade). Each grade is multiplied by the chosen rotor's tight or loose efficiency.
- At EV only regular steam in Large Steam Turbines is counted.

These are planning estimates. They do not reproduce in-game turbine mechanics.

Use the simulator to compare layouts relative to each other. Confirm absolute temperatures, fuel lifetimes and power in-game, or with the in-game constants in this document.

---

## 15. Data-driven YAML configuration and physics tuning

ModularNuclear incorporates a deeply structured, data-driven configuration system powered by SnakeYAML. Rather than relying on hardcoded tables or simple string lists, every aspect of reactor physics, fuel behavior, component interactions, and structural tiering can be configured and tuned by server operators via YAML files in the instance's config directory:

```
<minecraft_root>/config/modularnuclear/
├── physics.yaml       # Core simulation constants & transport coefficients
├── tiers.yaml         # Pressure tube tiers, temperature ceilings & fluid allowlists
├── fuels.yaml         # Deep neutronic, thermodynamic & damage-curve definitions
└── components.yaml    # Reflectors, moderators, control rods, vents, exchangers, platings, fluids
```

If these files are missing on startup, `ReactorDataRegistry` automatically generates complete default templates populated with all standard materials and extensive comments.

### 15.1 Physics configuration (`physics.yaml`)

Controls global thermodynamic constants, conduction scaling, Doppler feedback, and boundary conditions:

```yaml
huPerDegree: 32.0                 # Heat capacity per cell C (Hu/°C, legacy euPerDegree also supported)
dopplerExponent: 1.0              # Temperature exponent for Doppler broadening feedback
wallReflectionRatio: 0.0          # Fraction of escaping neutrons reflected back at the boundary
wallAbsorptionRatio: 1.0          # Fraction of escaping neutrons absorbed by the reactor casing
conductionCoeffMultiplier: 1.0    # Scaling multiplier applied to all inter-cell heat conduction
defaultAmbientTemp: 20.0          # Default fallback ambient temperature (°C)
beamEnergyToFastNeutronMultiplier: 0.001 # Multiplier converting beam (E * R) to fast neutron packet size
```

- **`huPerDegree`** (alias **`euPerDegree`**): Adjusting this scales the thermal inertia of every cell in the reactor core ($C$, default 32 Hu/°C).
- **`wallReflectionRatio`**: Setting this above 0.0 allows outer reactor casings to reflect a fraction of perimeter neutrons back into the active core grid.
- **`conductionCoeffMultiplier`**: Allows server operators to tune the rate of heat redistribution across the core without editing individual component files.
- **`beamEnergyToFastNeutronMultiplier`**: Controls the fast neutron yield per beam packet from external particle accelerators through Beam Input Hatches (default 0.001).

### 15.2 Structural and pipe tiering (`tiers.yaml`)

Defines the voltage tier, maximum safe operating temperature $T_{max}$, repair threshold, burst pressure rating, and accepted fluid allowlists for each pipe casing tier:

```yaml
tiers:
  0:
    name: "Electrum"
    voltageTier: "EV"
    maxOperatingTemperature: 1200.0
    repairTemperatureThreshold: 120.0
    maxSafePressureBar: 10.0
    allowedCoolants:
      - "ic2coolant"
      - "fluid.thorium"
  1:
    name: "Platinum"
    voltageTier: "IV"
    maxOperatingTemperature: 1400.0
    repairTemperatureThreshold: 140.0
    maxSafePressureBar: 15.0
    allowedCoolants:
      - "ic2coolant"
      - "water"
      - "fluid.thorium"
      - "fluid.uranium"
```

- **Strict fluid allowlist**: Core hatches test input fluids against `allowedCoolants` for the reactor's active pipe tier. Any unlisted fluid (such as plain Minecraft water or Naquadah-based liquid fuels) is immediately rejected without special code branches.
- **Temperature ratings**: $T_{max}$ sets the overheat voiding ceiling (§13.2). The repair threshold sets the maximum average core temperature below which maintenance can be performed.

### 15.3 Deep fuel definitions and damage curves (`fuels.yaml`)

Defines all solid fuel rods (single, dual, quad) and liquid fuels. Each fuel entry defines neutronic emission, thermal fission multiplication, base durability, optimal operating temperature, cross-sections, and non-linear damage curve parameters:

```yaml
fuels:
  uranium_single:
    name: "Uranium Fuel Rod (Single)"
    fuelType: "URANIUM"
    baseNeutrons: 4
    thermalFissionMultiplier: 1.0
    heatOutput: 38.0
    durability: 10000
    optimalTemperature: 800.0
    temperatureCoefficient: 0.8
    fastAbsorptionProbability: 0.10
    thermalAbsorptionProbability: 0.30
    scatteringProbability: 0.20
    moderationProbability: 0.0
    heatTransferCoeff: 0.05
    peakReactivityMult: 1.0
    peakDamageFraction: 0.0
    depletedItem: "IC2:reactorUraniumSimpledepleted"
    itemIdentifiers:
      - "IC2:reactorUraniumSimple"
      - "*rodUranium*"
    fluidIdentifiers: []

  mox_quad:
    name: "MOX Fuel Rod (Quad)"
    fuelType: "MOX"
    baseNeutrons: 16
    thermalFissionMultiplier: 2.0
    heatOutput: 152.0
    durability: 10000
    optimalTemperature: 1200.0
    temperatureCoefficient: 1.2
    fastAbsorptionProbability: 0.15
    thermalAbsorptionProbability: 0.40
    scatteringProbability: 0.20
    moderationProbability: 0.0
    heatTransferCoeff: 0.05
    peakReactivityMult: 2.0
    peakDamageFraction: 0.50
    depletedItem: "IC2:reactorMOXQuaddepleted"
    itemIdentifiers:
      - "IC2:reactorMOXQuad"
      - "*rodMOXQuad*"
    fluidIdentifiers: []
```

- **Reactivity damage curve (`peakReactivityMult` and `peakDamageFraction`)**: Configures the non-linear depletion curve solved by `FuelDamageCurveSolver`. Setting `peakReactivityMult: 2.0` and `peakDamageFraction: 0.5` causes the rod to peak at 2× its initial neutron emission halfway through its lifespan before decaying to zero.
- **Identifier pattern matching**: Accepts exact Forge/unlocalized item names or wildcard patterns (`*rodUranium*`).
- **Liquid fuels**: Configured via `fluidIdentifiers: ["fluid.uranium"]`, linking fluid hatch contents directly to neutronic and thermodynamic parameters.

### 15.4 Reactor components (`components.yaml`)

Controls all non-fuel core items, control rods, and coolant definitions:

- **Reflectors (`reflectors`)**:
  - `fastReflectionProbability` ($p_{r,f}$), `thermalReflectionProbability` ($p_{r,t}$), `scatteringProbability` ($p_s$), `heatTransferCoeff` ($c$), `durability`.
- **Moderators (`moderators`)**:
  - `moderationProbability` ($p_m$), `scatteringProbability` ($p_s$), `fastAbsorptionProbability` ($p_{a,f}$), `thermalAbsorptionProbability` ($p_{a,t}$), `heatTransferCoeff` ($c$).
- **Control Rods (`controlRods`)**:
  - `maxThermalAbsorption` ($r_t$), `maxFastAbsorption` ($r_f$), `scatteringProbability` ($p_s$), `heatTransferCoeff` ($c$). Allows modifying rod absorption limits and adding new absorber materials (e.g. tungsten, hafnium, boron).
- **Heat Vents & Exchangers (`heatVents`, `heatExchangers`)**:
  - `selfCoolingRate`, `reactorCoolingRate`, `adjacentSwitchRate`, `reactorSwitchRate`, `heatTransferCoeff`.
- **Platings & Insulators (`platings`, `insulators`)**:
  - `heatCapacityBoost`, `explosionResistanceBoost`, `heatTransferCoeff`.
- **Radiovoltaics (`radiovoltaics`)**:
  - `radiationCaptureRatio` (fraction of escaping/incident radiation converted), `euPerCapture` (direct EU generated per captured neutron).
- **Coolant Fluids & Items (`coolantFluids`, `coolantItems`)**:
  - `requiredPipeTier`, `heatCapacity` ($h_L$), `boilingThreshold`, `thermalShockThreshold`, `hotFluidPerLiter` conversion ratio, and `hotFluidProduct` output ID.

### 15.5 Hot reload and operational precedence

1. On server or client launch, `ReactorDataRegistry.init()` parses the YAML files.
2. Fast hash lookups index components and fuels by item name, metadata, and fluid registration.
3. If an unrecognized item or fluid is inserted, `ReactorDataRegistry` safely returns empty/null, triggering graceful rejection by access hatch filters.
4. Changes made to YAML files take effect upon world reload or server restart without modifying compiled `.jar` binaries.

---

## 16. CoolantLoops Data-Driven YAML Configuration System

In parity with ModularNuclear, the convective cooling system (*CoolantLoops*) employs a deep, data-driven YAML configuration architecture managed by `CoolantLoopDataRegistry`. On startup, configuration files are loaded from `<minecraft_dir>/config/coolantloops/`:

- `physics.yaml`: Hydrodynamic constants, convective heat transfer curve parameters, pump head and impeller drag, and passage dimensions.
- `fluids.yaml`: Complete thermodynamic and transport fluid properties (density, viscosity, specific heat, conductivity, boiling/freezing points, molten flags, radiolysis multipliers, and aliases).
- `pipes.yaml`: Material burst pressure ratings, thermal limits, pipe size-to-diameter mappings, allowed materials, and pipe roughness.
- `devices.yaml`: Multiblock device parameters (PHE exchange area, minor loss coefficients, latent heat, steam expansion ratio, degasser extraction efficiency and power draw, reservoir presets).

If missing, `CoolantLoopDataRegistry` automatically generates complete default templates populated with all calibrated values and documentation comments.

### 16.1 Hydrodynamic and Convective Physics (`physics.yaml`)

Governs the convective boundary layer model, pump mechanics, and geometric defaults:

```yaml
# Velocity at which turbulence begins in m/s (default 0.5 m/s)
vTurbulent: 0.5

# Velocity at which boundary layer saturation begins in m/s (default 3.0 m/s)
vSaturation: 3.0

# Convection slope in laminar regime in W/(m^2*K) / (m/s) (default 120.0)
hLaminarSlope: 120.0

# Scaling factor for turbulent jump in W/(m^2*K) (default 1500.0)
hTurbulentFactor: 1500.0

# Maximum asymptotic saturation coefficient in W/(m^2*K) (default 5000.0)
hMax: 5000.0

# Minor loss drag coefficient for unpowered impeller coast-down (default 8.0)
unpoweredImpellerK: 8.0

# Maximum stall head pressure in Pascals (default: .inf / uncapped; physically governed by motor power)
maxPumpHeadPressurePa: .inf

# Mechanical power output (Watts) delivered per EU drawn by pump (default 80.0)
pumpMechanicalPowerPerEU: 80.0

# Primary loop fill rate from Railcraft tank in L/tick (default 1000)
fillRateLitersPerTick: 1000

# Fallback ambient temperature in degrees Celsius (default 20.0)
defaultAmbientTemp: 20.0

# Standard reactor passage length in meters (default 5.0)
standardPassageLength: 5.0

# Standard reactor passage inner diameter in meters (default 0.10)
standardPassageDiameter: 0.10

# Commercial metal pipe standard roughness in meters (default 0.000045)
defaultPipeRoughness: 0.000045
```

- **Convection curve parameters**: `vTurbulent`, `vSaturation`, `hLaminarSlope`, `hTurbulentFactor`, and `hMax` define the piecewise convective heat transfer coefficient $h(v)$ (§11.2). Operators can tune the heat transfer efficiency or widen/narrow the turbulent transition regime without recompiling.
- **Impeller braking (`unpoweredImpellerK`)**: Sets the hydraulic drag $K$ of a de-energized pump rotor as loop inertia coasts down.
- **Pump performance**: `maxPumpHeadPressurePa` sets an optional clamp on maximum stall head pressure delivered by the pump (defaults to `.inf` / uncapped so that pump stall head $\Delta p_{stall} = P / \dot V_{rated}$ scales naturally with motor power and rotor efficiency across tiers, creating tier-appropriate pipe overpressurization hazards), and `pumpMechanicalPowerPerEU` converts EU/t drawn into mechanical fluid power in Watts.

### 16.2 Thermodynamic Fluid Properties (`fluids.yaml`)

Defines all primary coolants, secondary fluids, and custom additions. All units follow standard SI:

```yaml
fluids:
  water:
    fluidName: "water"
    density: 1000.0                   # kg/m^3
    dynamicViscosity: 0.001           # Pa*s
    specificHeat: 4184.0              # J/(kg*K)
    thermalConductivity: 0.606        # W/(m*K)
    boilingPointCelsius: 100.0        # deg C at 1 atm
    freezingPointCelsius: 0.0         # deg C at 1 atm
    isMolten: false
    radiolysisMultiplier: 1.0
    aliases:
      - "minecraft:water"

  ic2distilledwater:
    fluidName: "ic2distilledwater"
    density: 998.0
    dynamicViscosity: 0.00095
    specificHeat: 4186.0
    thermalConductivity: 0.610
    boilingPointCelsius: 100.0
    freezingPointCelsius: 0.0
    isMolten: false
    radiolysisMultiplier: 1.0
    aliases:
      - "distilledwater"
      - "distilled_water"
      - "water.distilled"

  heavywater:
    fluidName: "heavywater"
    density: 1105.0
    dynamicViscosity: 0.00125
    specificHeat: 4210.0
    thermalConductivity: 0.595
    boilingPointCelsius: 101.4
    freezingPointCelsius: 3.8
    isMolten: false
    radiolysisMultiplier: 1.0
    aliases:
      - "heavy_water"
      - "water.heavy"

  ic2coolant:
    fluidName: "ic2coolant"
    density: 1080.0
    dynamicViscosity: 0.0025
    specificHeat: 3800.0
    thermalConductivity: 0.520
    boilingPointCelsius: Infinity
    freezingPointCelsius: -25.0
    isMolten: false
    radiolysisMultiplier: 1.0
    aliases:
      - "coolant"

  sodium:
    fluidName: "sodium"
    density: 927.0
    dynamicViscosity: 0.0007
    specificHeat: 1260.0
    thermalConductivity: 86.0         # High liquid-metal conductivity
    boilingPointCelsius: Infinity
    freezingPointCelsius: 97.8
    isMolten: false
    radiolysisMultiplier: 1.0
    aliases:
      - "fluid.sodium"
      - "liquid.sodium"

  molten.cheese:
    fluidName: "molten.cheese"
    density: 1120.0
    dynamicViscosity: 0.557           # 557 cP
    specificHeat: 3000.0
    thermalConductivity: 0.481
    boilingPointCelsius: Infinity
    freezingPointCelsius: 46.85
    isMolten: true
    radiolysisMultiplier: 1.0
    aliases:
      - "cheese"
      - "moltencheese"

  lava:
    fluidName: "lava"
    density: 3000.0
    dynamicViscosity: 0.001
    specificHeat: 1250.0
    thermalConductivity: 1.5
    boilingPointCelsius: Infinity
    freezingPointCelsius: 1000.0
    isMolten: true
    radiolysisMultiplier: 1.0
    aliases:
      - "fluid.lava"
      - "minecraft:lava"
```

- **Non-boiling and molten fluids**: Setting `boilingPointCelsius: Infinity` disables loop cavitation and vapor lock (§13.7). Fluids with `isMolten: true` undergo freeze checks at startup: unheated pump loops containing molten fluids freeze and destroy the pump rotor if filled below their melting point.
- **Custom fluids**: New coolants (e.g. synthetic heat transfer aromatics, liquid lead-bismuth, molten salts) can be added simply by defining an entry with its thermophysical properties and aliases.

### 16.3 Piping Ratings and Material Allowlist (`pipes.yaml`)

Controls mechanical burst ratings, maximum operating temperatures, diameter mappings, and closed-loop material validation:

```yaml
materialPressureRatingsBar:
  castiron: 16.0
  iron: 16.0
  wroughtiron: 15.0
  steel: 35.0
  stainlesssteel: 70.0
  titanium: 140.0
  tungstensteel: 280.0
  osmium: 600.0
  neutronium: 3000.0

materialTemperatureLimitsCelsius:
  castiron: 1538.0
  iron: 1538.0
  wroughtiron: 1500.0
  steel: 1500.0
  stainlesssteel: 1700.0
  titanium: 1668.0
  tungstensteel: 3000.0
  osmium: 3033.0
  neutronium: 10000.0

thicknessDiameters:
  tiny: 0.05      # 5 cm
  small: 0.075    # 7.5 cm
  normal: 0.10    # 10 cm
  large: 0.15     # 15 cm
  huge: 0.20      # 20 cm

allowedMaterials:
  - "steel"
  - "iron"
  - "castiron"
  - "wroughtiron"
  - "stainlesssteel"
  - "titanium"
  - "tungstensteel"
  - "osmium"
  - "neutronium"

defaultRoughness: 0.000045
```

- **Burst pressures (`materialPressureRatingsBar`)**: If pressure inside a pipe exceeds this rating, the segment ruptures violently (§13.7).
- **Thermal limits (`materialTemperatureLimitsCelsius`)**: Pipes exceeding this temperature melt and rupture.
- **Allowed materials (`allowedMaterials`)**: Explicit allowlist of pipe materials acceptable for loop formation. Any unauthorized pipe material inserted into a closed loop is rejected during graph traversal with a descriptive tooltip.

### 16.4 Multiblock Devices and Exchangers (`devices.yaml`)

Configures thermodynamic exchange properties for PHE, Degasser, and Creative Reservoir:

```yaml
phe:
  exchangeArea: 24.0                  # Primary-secondary contact area in m^2 (default 24.0)
  minorLossK: 1.2                     # Flow resistance minor loss coefficient K (default 1.2)
  latentHeatJoulePerLiter: 2260000.0  # Latent heat of vaporization in J/L (default 2.26e6)
  steamExpansionRatio: 160            # Litres of steam produced per litre of boiled water (default 160)
  superheatedTempThreshold: 250.0     # Minimum primary temperature for superheated steam (°C)
  supercriticalTempThreshold: 374.0   # Minimum primary temperature for supercritical steam (°C)
  maxTempDropPerTick: 50.0            # Thermal buffer rate clamp (°C/tick)
  coolingFactor: 0.50                 # Primary fluid cooldown fraction per tick (default 0.50)

degasser:
  minorLossK: 1.5                     # Flow resistance minor loss coefficient K (default 1.5)
  gasExtractionEfficiency: 0.25       # Gas extraction fraction per pass (default 25%)
  gasExtractionPowerEuPerLiter: 8.0   # Energy consumed per litre of extracted gas (default 8.0 EU/L)

reservoir:
  exchangeArea: 10.0                  # Convective exchange surface in m^2 (default 10.0)
  temperaturePresets:                 # Click-cycle temperature presets (°C)
    - 20.0
    - 100.0
    - 250.0
    - 500.0
    - 1000.0
```

- **PHE steam thresholds**: Sets the exact temperature boundaries where standard steam transitions to superheated steam ($250^\circ\text{C}$) and supercritical steam ($374^\circ\text{C}$).
- **Degasser efficiency**: Sets the single-pass gas stripping ratio and energy consumption per liter of radiolytic gas removed.
- **Creative Reservoir**: Configures testing preset temperatures and contact surface area for sandbox loop testing.

### 16.5 Hot Reload and Runtime Integration

1. On game startup or server launch, `CoolantLoopDataRegistry.init()` initializes the configuration folder and parses all YAML files.
2. Fluid properties and aliases are automatically registered into `CoolantFluidProperty`.
3. Pipe burst pressures and thermal limits are registered into `CoolantPipingRegistry`.
4. Multiblock devices (`MTEPressurizedHeatExchanger`, `MTEPressurizedDegasser`, `MTECoolantPump`, `TileEntityCreativeHeatReservoir`) instantiate and run their convective heat transfer models directly against `CoolantLoopDataRegistry` parameters.
5. All YAML parameters can be dynamically altered without recompiling `.jar` binaries.

---

## Appendix A — Constants

### A.1 Core simulation

| Constant | Value |
|---|---|
| Cycle period | 20 ticks |
| Heat capacity per cell | 32 Hu/°C |
| Prompt fission heat | 38 Hu per emitted neutron |
| Thermal absorption heat in fuel | 47.5 Hu per neutron |
| Fast absorption / moderation heat | 8 Hu per neutron |
| Thermal absorption heat, non-fuel | 4 Hu per neutron |
| Fast packet step budget | $2(s_x + s_y)$ |
| Thermal stencil steps | $\max(12, 2(s_x + s_y))$ |
| Thermal stencil weights | 1/6 cardinal, 1/12 diagonal |
| Reflecting-scatter condition | $p_{s,f} \ge 0.90$ and $p_{a,f} \le 0.05$ |
| Conduction substeps | 5 |
| Minimum conduction coefficient | 0.01 |
| Wall reflection | 0 |
| Global thermal fission multiplier | 1.0 |
| Global fast fission multiplier | 1.0 |
| Reactivity at $T \le 0$ / floor | 0.5 / 0.05 |
| Fuel wear: per emitted / per thermal absorbed / per fast absorbed neutron | 0.25 / 1.0 / 8.0 |
| Default ambient | 20 °C |
| Structure-break voiding threshold | 100 °C |
| Repair threshold | 10% of $T_{max}$ (average temperature) |
| Damage per thermal shock / per overheated block-cycle | 2% / 1% |
| Damage repaired per tool fix | 2% |
| WAILA meltdown warning | ≥ 90% damage |
| GUI blink threshold | 85% of $T_{max}$ |

### A.2 Meltdown

| Constant | Value |
|---|---|
| Explosion power | 4.5 |
| Corium sphere radius | 2 (+4 random blocks) |
| Corium contact | fire 30 s, 10 damage, GT radioactivity 5, IC2 radiation 240 t × amplifier 3 |
| Fallout pollution | 2 000 000 |
| Fallout radiation radius | 160 m horizontal |
| Corium → bedrock | after 2 400 ticks, within ±48 blocks |
| Fallout lifetime | 12 000 ticks |

### A.3 Convective cooling

| Constant | Value |
|---|---|
| Passage geometry | L = 5 m, D = 0.10 m, A = 1.571 m² |
| Laminar Nusselt number | 4.36 (Re < 2300) |
| Turbulent correlation | Dittus–Boelter, 0.023 Re^0.8 Pr^0.4 (Pr ≥ 0.6) |
| Energy conversion | 128 J = 1 Hu |
| Minimum flow for exchange | 10⁻⁶ m³/s |
| Radiolysis | 2 L coolant per 4 neutrons absorbed |
| Pump mechanical power | EU × 80 × η_rotor × η_maint W |
| Pump head cap | 25 bar |
| Fill rate | 1 000 L/tick |
| Impeller wear interval | 100 ticks |
| Rupture explosion | strength min(25, 4 + p/10); 80% of tank drained |
| PHE coefficient | 18 171.3 W/K above 100 °C |
| PHE steam thresholds | 300 °C superheated, 700 °C supercritical |
| Instrument refresh | 10 ticks |
| Creative reservoir | targets 20/100/300/600/1000 °C, A = 2 m² |

---

## Appendix B — Known implementation deviations

The following behaviours, described in the body of this manual, appear to differ from the evident design intent. They are listed for the developer's attention. Operators should plan around the behaviour as it currently stands.

| # | Area | Behaviour | Section |
|---|---|---|---|
| 1 | Structure | *Resolved:* HP passage gating now requires Osmium (LuV) or higher item pipe casings, irrespective of reactor size | §2.6, §11.4 |
| 2 | Telemetry | Per-cell "per tick" figures are per 20-tick cycle | §3.1 |
| 3 | Neutronics | Thermal scattering values are never used | §4.2 |
| 4 | Neutronics | Packets absorbed on their final step are also counted as escaped | §4.6 |
| 5 | Neutronics | *Resolved:* Escaped neutrons dynamically drive vessel maintenance wear (1.0× to 10.0× sigmoid) and Cherenkov radiation intensity | §4.5, §12.3 |
| 6 | Neutronics | RNG is a shared unseeded `Random` | §4.1 |
| 7 | Fuel | *Resolved:* Fuel operational and neutronic parameters (packet count, base neutrons, peak/floor temps, durability, spent product) are explicitly defined in YAML (fuels.yaml) on a per-fuel basis; runtime substring parsing eliminated | §7.1 |
| 8 | Fuel | *Resolved:* Breeder rods (Lithium, Glowstone) now deplete into DepletedRodLithium and DepletedRodGlowstone (centrifuged to Tritium and Sunnarium respectively) instead of being voided | §7.1, §7.6 |
| 9 | Fuel | *Resolved:* Plutonium liquid fuels and Uranium Hexafluoride have dedicated explicit FuelData entries with correct peak temperatures and 12 base neutrons in fuels.yaml | §7.4 |
| 10 | Fuel | *Resolved:* Reconciled fuel wear scaling between configured durability and intrinsic item durability; custom rods dynamically read mix durability from NBT | §7.3, §7.5 |
| 11 | Fuel | *Resolved:* Fast neutron absorption induces chain reactions with material-specific fast fission multipliers ($m_{fiss,f}$); fast absorption wear applies configurable 8× penalty | §5.1, §7.1, §7.3, §7.5 |
| 12 | Fuel | GT depleted rods yield one cycle of neutrons; items named `fuelrod` act as permanent non-depleting fuel | §7.7 |
| 13 | Components | GT meta-item plates (graphite, beryllium, boron, cadmium) are not classified; NEI implies otherwise | §8 |
| 14 | Components | *Resolved:* Radiovoltaic output scales linearly with maintenance efficiency $\eta$ applied once to aggregate EU output | §8.6 |
| 15 | Control | *Resolved:* SCRAM forces all control rods to 100% insertion while simulation and cooling continue uninterrupted | §3.1, §9.5 |
| 16 | Control | Direct EU is not reset on SCRAM/disable | §9.5, §13.6 |
| 17 | Cooling | Conductive heat-to-steam conversion is ≈4.5× GT steam valuation | §11.2 |
| 18 | Loops | Passage-to-loop heat transfer is not energy-conserving (≈ 0.785/$\dot V$) | §11.4 |
| 19 | Loops | Parallel branches simulated in series with full flow each | §11.5.1 |
| 20 | Loops | *Resolved:* PHE removes heat from loop coolant with strict energy conservation ($\Delta T = Q_{actual}/C_p$); standard GTNH latent heat $2\,260\text{ kJ/kg}$ | §11.5.8 |
| 21 | Loops | No ambient loss and no boiling in loops; no survival heat sink | §11.5.4, §11.5.8 |
| 22 | Loops | PHE pressurized hatch roles depend on structure iteration order | §11.5.8 |
| 23 | Loops | Instrument pressure metric is a local segment drop | §11.5.10 |
| 24 | Loops | Passage outlet hatch temperature display is stale (outlet not simulated) | §11.4 |
| 25 | Hazards | `explodeReactor()` and dry-coolant shutdown paths exist but are never called | §13 |
| 26 | Hazards | Overheat check skips HP passage cells | §12.3 |
| 27 | Docs | Control-rod hatch javadoc says "top layer"; rods are in the bottom layer | §9.1 |
| 28 | Web sim | Different constants and 20× timebase difference vs in-game | §14 |
| 29 | Loops | Multi-tick loop crawling (1 000 steps/tick, up to 20 ticks / 20 000 steps total) with loop-too-large controller error | §11.5.1 |
| 30 | Loops | Manifold area-weighted parallel flow splitting, thermal blending, and non-additive parallel pressure drops; maintenance preservation across crawls | §11.5.1, §11.5.3, §11.5.5 |
| 31 | Loops | *Resolved:* PHE input hatch strictly accepts Distilled Water variants (`ic2distilledwater`, `distilledwater`, `distilled_water`); plain Minecraft water is rejected | §11.5.8 |

---

## Appendix C — Source map

Paths are relative to each mod's `src/main/java/com/gtnewhorizons/` directory.

| Topic | Source |
|---|---|
| Controller, cycle loop, hazards | `modularnuclear/common/metatileentity/multi/MTENuclearReactor.java` |
| Structure definition | `modularnuclear/common/metatileentity/multi/structure/NuclearReactorStructure.java` |
| Simulation engine (neutrons, heat, conduction) | `modularnuclear/common/nuclear/NuclearSimulationEngine.java` |
| Component properties, per-cell processing, cooling | `modularnuclear/common/metatileentity/multi/NuclearReactorPhysics.java` |
| Fuel types and reactivity curves | `modularnuclear/common/nuclear/NuclearFuelType.java` |
| Fuel wear curve solver | `modularnuclear/common/nuclear/FuelDamageCurveSolver.java` |
| Custom fuel | `modularnuclear/common/nuclear/CustomFuelMix.java`, `.../multi/MTEActinideCompounder.java` |
| Core access hatches | `modularnuclear/common/metatileentity/hatch/MTEHatchNuclearBus.java`, `MTEHatchNuclearHatch.java` |
| Control rods | `modularnuclear/common/metatileentity/hatch/MTEHatchNuclearControlRod.java` |
| Control hatch | `modularnuclear/common/metatileentity/hatch/MTEHatchNuclearControl.java` |
| HP passage | `modularnuclear/common/metatileentity/hatch/MTEHatchNuclearHighPressure.java` |
| Meltdown | `modularnuclear/common/block/BlockCorium.java`, `modularnuclear/common/entity/EntityMeltdownFallout.java` |
| OpenComputers | `modularnuclear/common/opencomputers/NuclearControlEnvironment.java` |
| Web simulator | `modularnuclear/common/nuclear/standalone/*` |
| Data-driven YAML config & registry | `modularnuclear/common/config/ReactorDataRegistry.java`, `.../config/data/*` |
| Loop engine, crawler, fluids | `coolantloops/engine/*` |
| Pump, PHE, degasser | `coolantloops/common/metatileentity/multi/*` |
| Pipes, manifolds, instruments | `coolantloops/common/metatileentity/pipe/*`, `coolantloops/common/tileentity/*` |

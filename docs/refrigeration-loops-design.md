# Refrigeration gas loops: design

Status: design for the `refrigeration-loops` branch.  This document describes what the branch builds and why; once
it lands, the operator's manual and the Notes in the code describe how things are, and this file can go.

## Goals

- Closed loops that carry a gas instead of a liquid, built from the same pump, Railcraft reservoir, high-pressure
  pipes, manifolds, instruments and hatches as liquid coolant loops.
- Two refrigeration cycles:
  - **Vapour compression** (condensing gases): compressor, condenser, expansion valve, evaporator.  R-134a,
    ammonia (R-717), propane (R-290) and CO2 (R-744).
  - **Reverse Brayton** (permanent gases): compressor, aftercooler, recuperator, turbo-expander, cold load.
    Helium, nitrogen and argon.
- An HV R-134a loop that fully replaces the Vacuum Freezer for hot ingots, through a Coolant Bath, with its heat
  rejected to ambient by a Radiator.  Later tiers recover the heat with a PHE instead.
- Reactor passages on a gas loop exchange very little heat, so gas loops don't upset the reactor's balance.
- EU cost follows the thermodynamics: a compressor spends work, and the coefficient of performance comes out of the
  cycle (a fraction of Carnot between the evaporator and the condenser).  Cooling near ambient is cheap; cryogenic
  cooling is dear.

Out of scope: the standalone web simulator (it keeps modelling liquid loops).

## The gas model

Each gas is a `GasProperty`: molar mass, ideal-gas vapour heat capacity `cp_v`, liquid density and heat capacity,
a reference latent heat, and a saturation curve fitted through its normal boiling point (or triple point, for
CO2) and its critical point:

    ln P_sat(T) = A - B / T            (Clausius-Clapeyron through two points)
    L(T) = L_0 + (cp_v - cp_l) (T - T_0)           (Kirchhoff)

| Gas | M (g/mol) | T_ref (K), P_ref (bar) | T_c (K), P_c (bar) | L at T_ref (kJ/kg) | freezes (°C) |
| :-- | --: | :-- | :-- | --: | --: |
| R-134a | 102.0 | 247.1, 1.013 | 374.2, 40.6 | 217 | -103.3 |
| Ammonia | 17.03 | 239.8, 1.013 | 405.4, 113.3 | 1370 | -77.7 |
| Propane | 44.10 | 231.0, 1.013 | 369.8, 42.5 | 426 | -187.7 |
| CO2 | 44.01 | 216.6, 5.18 | 304.1, 73.8 | 350 | -56.6 |
| Nitrogen | 28.01 | 77.4, 1.013 | 126.2, 34.0 | 199 | -210.0 |
| Argon | 39.95 | 87.3, 1.013 | 150.7, 48.6 | 161 | -189.3 |
| Helium | 4.003 | 4.22, 1.013 | 5.19, 2.27 | 20.7 | none |

The fit is good where loops run: R-134a 10.0 bar at 40 °C (real 10.2) and 1.96 bar at -10 °C (real 2.01); CO2
64.6 bar at 25 °C (real 64.3).

A segment's state is its pressure P (that of its pressure zone, below) and its specific enthalpy h.  From them:

- subcooled liquid (h at or below saturated liquid): T from `cp_l`, density `rho_l`;
- two-phase: T = T_sat(P), quality x, density from the mixture of saturated vapour (ideal gas) and liquid;
- vapour (h at or above saturated vapour): T from `cp_v` above T_sat, density P / (R T);
- above the critical pressure, liquid below T_c and gas above.

Entropy is defined to match (the ideal-gas formula for vapour, latent heat over T across the dome, `cp_l ln T`
below it), so a compressor or expander finds its isentropic outlet by bisection on h at the outlet pressure.  An
ideal gas throttled through a valve keeps its temperature, so only condensing gases cool through a valve; the
Brayton cycle cools by taking work out in an expander.

## The gas loop engine

`GasLoopEngine` extends `CoolantLoopEngine`, so the pump, its NBT, its GUI and the instruments keep working with
it.  A liquid loop has one incompressible flow; a gas loop has:

- **Pressure zones.**  Pressure stages (compressors, valves, expanders) split the loop.  A zone is a connected
  run of segments between stages, with one pressure.  Friction inside a zone is shown, not modelled in the state.
- **Zone mass.**  Each zone holds a mass of gas.  Each tick every stage moves mass from its upstream zone to its
  downstream zone, and the zone's pressure is solved so that its segments, each at its own enthalpy, hold its
  mass: `sum rho(P, h_i) V_i = m_z` (monotonic in P; safeguarded Newton in log P).  A liquid-full zone has a small
  compressibility, so an overcharged high side drives the pressure up until a pipe bursts, as in reality.
- **Enthalpy advection.**  The loop's existing implicit advection (`LoopAdvection`) carries h instead of T, with
  each segment's mass as its capacity, solved backward Euler.  A segment's inflow is interpolated along its zone
  from the flow into the zone to the flow out of it, so a zone filling or emptying spreads that over its length.  A
  stage's segment is a source at the stage's outlet enthalpy.
- **Heat exchange** acts on enthalpy: a device pulls a segment towards its own temperature, and a segment's
  temperature follows from its state.  The pull is solved implicitly in the advection step, so no exchanger,
  however strong, takes the gas past the device's temperature, and it is capped at the device's conductance times
  the temperature difference it finds, so a condenser near its saturation temperature moves no more than its
  conductance allows.  Two-phase segments exchange at constant temperature, which is what makes a condenser and an
  evaporator work.
- **Failures:** a segment above its pipe's rating bursts (`BURST`), one below the gas's freezing point freezes
  (`FROZE`), as in liquid loops.

Pressure stages (`IGasPressureStage`), each the physics of one device:

- **Compressor:** mass flow = volumetric efficiency x suction density x displacement x speed; isentropic outlet
  enthalpy with an isentropic efficiency.  It draws EU for its shaft work, at a configured joules per EU, and slows
  to what its energy hatches can give.  It doesn't run backwards.  Liquid at its suction (quality below 0.9) is
  *slugging*: it wears the compressor (maintenance issues) and it pumps less.  Its high-pressure cutout holds back
  its flow to keep its discharge zone below 70% of the zone's weakest pipe rating, and stops it at 80% until the
  zone falls back to 70%.
- **Expansion valve:** an orifice, `m = C A sqrt(2 rho_in dP)` (choked for vapour), isenthalpic.  Its opening is
  manual (screwdriver) or thermostatic: it opens and closes to hold a set superheat at the outlet of the zone it
  feeds, as a real TXV does.
- **Turbo-expander:** a nozzle (choked flow) and an isentropic expansion with an efficiency.  The work goes to a
  dynamo hatch as EU (times a generator efficiency), and what a full dynamo can't take is lost in its brake.
  Disabled, it free-wheels: the gas passes without expanding work out, and so without cooling.

A stopped compressor passes nothing; a valve or expander keeps passing flow, so a stopped loop equalizes.

## The pump on a gas loop

The pump stays the loop's owner: it crawls, keeps the chunks loaded, holds the reservoir and charges the loop from
it.  On a gas loop:

- it needs no rotor and draws power only while charging or recovering the charge;
- its *fill* is the loop's **charge**, in litres of gas at 0 °C and 1 atm.  The required charge follows from the
  loop's volume: for a condensing gas, enough to hold a set fraction of the loop's volume as liquid at ambient (30%
  by default); for a permanent gas, enough for a set charge pressure at ambient (10 bar by default).  A
  screwdriver on the controller cycles the setting;
- while charging the loop sits at equilibrium at the reservoir's temperature;
- allowing it to work runs the loop: the compressors may run only while the pump does;
- disabling its discharge hatch stops the compressors, lets the loop equalize, then recovers the charge into the
  reservoir.

## Devices

All devices enter and leave the loop through high-pressure hatches, as the existing ones do.

| Device | Structure | Does |
| :-- | :-- | :-- |
| **Gas Compressor** | 3x3, 3 to 9 long: Stable Titanium casings, gearbox rings (steel, titanium or tungstensteel) | Pressure stage; EU in |
| **Expansion Valve** | 3x3x3 Frost Proof casings, a pipe casing at the centre | Pressure stage; no power |
| **Turbo-Expander** | 3x3, 3 to 7 long: turbine casings, dynamo hatch | Pressure stage; EU out |
| **Radiator** | 3 high, 3 to 9 long: air filter vent casings, air filter turbine (fan) rings | Rejects heat to ambient; EU for the fans |
| **PHE** | as now, plus a dual-loop mode | Loop to Forge fluid, or loop to loop |
| **Coolant Bath** | as now | Evaporator for items |

Tiers: the compressor's displacement grows with its length and gearbox tier; its energy hatch tier caps its power.
The radiator's fans set its conductance per ring and the EU each ring draws; it works on liquid loops too, and
never cools below the pump's ambient temperature.  An HV R-134a loop is: steel pipes (35 bar), a steel-gearbox
compressor on an HV hatch, a radiator, a valve and a coolant bath.

**PHE dual-loop mode.**  The secondary GregTech hatches may be replaced by a second pair of high-pressure hatches.
Each pair is its own device on its loop (the crawler gives them distinct segments), and the two pairs may be on
two loops or on the same loop (a recuperator).  The PHE moves heat from the hotter side to the colder, `U = 1 /
(1/h_a + 1/h_b)` over its effective area, capped so the sides don't cross, and draws its usual power.

**PHE liquefaction.**  In single mode, with a gas loop colder than a secondary gas's boiling point by the approach
temperature, the PHE condenses the gas (air, nitrogen, oxygen, argon, helium, R-134a and so on) into its liquid,
putting the heat into the loop.  The heat per litre is the same EU-equivalent the PHE already uses for boiling
those liquids, so boiling and condensing balance and nothing is created from a round trip.

**Coolant Bath on a gas loop** runs the gas through coils holding 5% of the bath's volume, so the bath doesn't take
the loop's whole charge.  The coils exchange with the gas as the bath does with liquid, but take the gas no hotter
than 50 K past its saturation temperature (or its inlet's, if hotter): the items cool only by what the gas takes
up.  An evaporating refrigerant at -20 °C cools hot ingots quickly.

**Passages, heaters and degassers.**  A reactor passage on a gas loop exchanges at a small fraction of its liquid
rate (`gasPassageHeatTransferMultiplier`, 0.02).  A heater works on gas as on liquid.  A degasser passes gas
through without degassing.

## R-134a chemistry

GTNH has vinyl chloride, chlorine, HF and HCl but no trichloroethylene.  The chain, with GT++ catalysts in the
Chemical Plant:

1. LCR: vinyl chloride + 2 Cl2 -> trichloroethylene + 2 HCl.
2. Chemical Plant (Solid Acid Catalyst, standing in for the Lewis-acid SbCl5 of the liquid-phase process):
   trichloroethylene + 3 HF -> HCFC-133a (CF3CH2Cl) + 2 HCl.
3. Chemical Plant (Brown Metal Catalyst, nickel and aluminium, for the gas-phase fluorination over a metal
   catalyst): HCFC-133a + HF -> R-134a + HCl.

Trichloroethylene and HCFC-133a are new fluids; R-134a is a new gas.  The HCl goes back to chlorine through the
usual electrolysis.

## Energy accounting

A compressor's shaft work costs EU at `compressorJoulesPerEU` (`devices.yaml`), and an expander returns EU at the
same rate times its generator efficiency.  The default follows the manual's existing equivalence (one EU is
28,250 J of turbine heat), calibrated after the in-world test so that an HV R-134a loop costs well below the
Vacuum Freezer's EU for ingots, and a Brayton loop more than the freezer for liquid nitrogen.

## Compact machines

One-block versions of the gas loop's machines, MV to LuV, with a pump of their own: Note [Compact loop machines]
in `MTECompactLoopMachine.java` has the design, and these Notes its parts.

- Note [Pump cores and their hosts] (`CoolantPumpCore.java`): the loop a pump owns is a `CoolantPumpCore`, which
  the multiblock `MTECoolantPump` and the one-block `MTECompactLoopPump` both host.  The compact pump has no
  impeller, so it runs gas loops only.
- Note [The compact pump's tanks] (`IronTankReservoir.java`): a stack of Iron Tanks of one kind, reached through
  the bottom tank's fluid handler.
- Note [The compact bath] and Note [The compact heat exchanger].
- Note [Compact loop machine recipes] (`CompactLoopRecipes.java`).

What holds a compact loop to one to three times the Vacuum Freezer's EU efficiency is the compressor: a drive
efficiency of 9% (MV) to 23% (LuV), and a built-in pressure ratio that bounds the loop's coefficient of
performance.  An ingot holds the same heat in either kind of bath.  A loop's flow is bounded by the gas on its low
side, so a tier's machines hold as much gas as its flow needs, and its loop's charge grows with it.

### GUIs

The compact machines' GUIs follow GregTech's own one-block machines (Note [Compact machine GUIs] in
`CompactLoopMachineGui.java` has how).  Those are built in GT5-Unofficial's
`gregtech.common.gui.modularui.singleblock`, on ModularUI 2, and share these elements:

- **The panel.**  `MTETieredMachineBlockBaseGui` makes a 176 by 166 panel with the machine's name in a tab above
  it, the player's inventory along the bottom (`createInventoryRow`), and the content area above that.  Every
  machine with something to put in or take out binds the player's inventory; fluid machines do too, for cells.
- **The top right corner** (`createTopRightCornerFlow`): a small power switch and a muffler toggle, without labels.
- **The recipe area** (`MTEBasicMachineBaseGui.createItemRecipeArea`): input slots on the left, a progress bar in
  the middle and output slots on the right, centred, up to three rows of slots on each side.  Input slots carry a
  faint overlay of what goes in (an ingot, dust, a cell) and output slots an "out" mark.  The progress bar
  (`GTProgressWidget`) is a 20 by 18 picture particular to the machine (an arrow, the compressor's narrowing arrow,
  the bath's circling arrows), filled as the current operation advances; its tooltip names the voltage it needs,
  and a click on it opens the machine's recipes in NEI.
- **Fluid slots.**  A machine's fluid input sits in the bottom left corner and its fluid output in the bottom right
  (`createFluidInputSlot`, `createFluidOutputSlot`); they show the fluid as a filled square, its name and amount
  in their tooltip, and take or fill a cell held on them.  The boiler shows its water and steam as tall bars.
- **The bottom corners** (`createBottomLeftCornerFlow`, `createBottomRightCornerFlow`): toggle buttons on the left
  (auto-output of items and fluids, modes), each a small picture whose tooltip says what it does; the circuit,
  special and charger slots on the right and at the centre.
- **The error icon** (`createErrorWidget`): a red picture at the bottom centre, shown only while the machine is
  stuck (out of power, for instance), with the reason in its tooltip.
- **No text.**  A one-block machine's GUI shows no numbers on the panel.  What a player wants to know about a
  running machine is in tooltips, or on the portable scanner.  The exceptions are tanks and buffers, whose GUI is a
  black screen with the amount stored.

The compact machines map their processes onto these.  The machines the loop only flows through (compressor,
valve, turbo-expander, radiator) show the loop's gas at their inlet and outlet as two fluid slots with the
machine's arrow between them; they have no batches, so the arrow runs while the machine works, faster under more
load.  The bath shows its hot ingots in, the ingots being cooled, the bath arrow for the ingot nearest done and
the cooled ingots out.  The heat exchanger shows its gas tank, the arrow for its next litre and its liquid tank.
The pump shows the gas in its tanks, the arrow for its loop's charge and the gas in its loop, with its charge
setting as a mode button.  Pressures, temperatures, flows and power are in the tooltips of the slots and the
arrow, and on the scanner.  The machines that take or give EU (compressor, radiator, pump, turbo-expander) have GregTech's power slot
under the error icon, GregTech's own widget: GregTech's base tile charges and drains its item, with
`MTEBasicMachine`'s thresholds, so a machine on cables charges a battery and one without runs from a battery or
from redstone dust.  The turbo-expander only charges.

## Tests

- Unit: gas properties against reference points, state round trips, isentropic compression, the valve's flash,
  the zone pressure solve, mass and energy conservation, and two whole cycles run to steady state: an R-134a
  vapour-compression loop (about 2 bar and 10 bar, COP between 2 and 4) and a helium Brayton loop with a
  recuperator (below 100 K).
- In-world, on a hermetic dev instance: an HV R-134a loop cooling hot ingots in a Coolant Bath, and a helium Brayton
  loop liquefying nitrogen in a PHE, each checked for state, pressures, temperatures and EU.
- A demonstration world on the latest `-test` instance with both loops running.
- Compact machines: `CompactLoopBalanceTest` runs a loop of each tier to steady state on hot ingots and compares it
  with the Vacuum Freezer; `inworld-tests/test_compact_refrigeration_loop.py` builds an MV loop in game, cools hot
  Kanthal and has the stopped pump take its charge back.

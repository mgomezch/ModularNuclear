| Asset | Generator | Notes |
| :--- | :--- | :--- |
| `textures/gui/nei/neutron_interaction_atlas.png`<br>`textures/gui/nei/long_arrow.png` | `generate_nei_atlas.py` | |
| `textures/blocks/OVERLAY_FRONT_FISSION_REACTOR.png`<br>`textures/blocks/OVERLAY_FRONT_FISSION_REACTOR_ACTIVE.png` | `generate_reactor_overlays.py` | |
| `textures/blocks/fluids/corium_still.png`<br>`textures/blocks/fluids/corium_flow.png` | `generate_corium.py` |  |
| `textures/blocks/fluids/heavywater_*`<br>`textures/blocks/fluids/highpressuredistilledwater_*`<br>`textures/blocks/fluids/highpressureheavywater_*` | `generate_water_fluids.java` | HSB-shifting the water textures |
| `textures/items/cellHeavyWater.png`<br>`textures/items/cellHighPressureDistilledWater.png`<br>`textures/items/cellHighPressureHeavyWater.png` | `generate_cells.py` | ImageMagick + the base cell texture |
| `textures/blocks/MACHINE_CASING_NUCLEAR.png` | `generate_casing.py` | Color transforms on top of GTNH's `BLOCK_STEELPREIN.png` |
| `textures/items/gt.radiovoltaic.plate.hv.png`<br>`textures/items/gt.radiovoltaic.plate.ev.png` | `generate_radiovoltaic_plates.py` | Re-coloring `gt.neutronreflector.png` |
| `textures/gui/nei/fuelstats/chart_*.png` (13 charts) | `FuelDamageCurveSolver.java` | Reactivity / durability damage charts for fuels, just basic pixel plotting |

These are directly copied from elsewhere:

- `textures/items/card_modular_nuclear.png`: `cardReactor.png` from Nuclear-Control 2.
- `textures/items/kit_modular_nuclear.png`: `kitReactor.png` from Nuclear-Control 2.
- `textures/sim/icons/*.png`: from GTNH GT5 and IC2, used for the simulator webapp.

Regenerate with:

```bash
make sim-textures
```

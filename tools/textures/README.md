# Provenance

| Assets | Generator |
| :--- | :--- |
| `textures/gui/nei/neutron_interaction_atlas.png`<br>`textures/gui/nei/long_arrow.png` | `generate_nei_atlas.py` |
| `textures/blocks/OVERLAY_FRONT_FISSION_REACTOR.png`<br>`textures/blocks/OVERLAY_FRONT_FISSION_REACTOR_ACTIVE.png` | `generate_reactor_overlays.py` |
| `textures/blocks/fluids/corium_still.png`<br>`textures/blocks/fluids/corium_flow.png` | `generate_corium.py` |
| `textures/blocks/fluids/heavywater_*` | `generate_water_fluids.py` |
| `textures/items/cellHeavyWater.png`<br>`textures/items/cellHighPressureDistilledWater.png`<br>`textures/items/cellHighPressureHeavyWater.png` | `generate_cells.py` |
| `textures/blocks/MACHINE_CASING_NUCLEAR.png` | `generate_casing.py` |
| `textures/items/gt.radiovoltaic.plate.hv.png`<br>`textures/items/gt.radiovoltaic.plate.ev.png` | `generate_radiovoltaic_plates.py` |

# Simulator

`nuclear-sim-dist` serves tile icons under `textures/sim/icons/` to allow configuring reactors in a Web browser.  They're not loaded by Minecraft in-game.  They're only for the Web GUI as community tooling.

# Building

In `mods/ModularNuclear`:

```bash
./gradlew generateAllTextures
```

Or from mgomezch's `nh-dev` "monorepo" root:
```bash
make sim-textures
```

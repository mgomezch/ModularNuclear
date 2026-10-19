# ModularNuclear — Texture Provenance & Asset Manifest

In strict compliance with GT New Horizons policies against AI-generated art assets, **all visual media in ModularNuclear are 100% deterministic**: either synthesized procedurally through open-source code and mathematical equations, or extracted directly from existing open-source Minecraft mods (GregTech 5 Unofficial, IndustrialCraft 2, Nuclear-Control 2) under their respective licenses (LGPL-3.0, GPL-3.0, MIT).

---

## 1. Procedural Asset Manifest

All procedural textures are generated on-the-fly during Gradle builds via `generateProceduralTextures` and `generateFuelStatsCharts`.

| Asset Path | Generator Script | Technical Pipeline & Mathematical Formulation | Upstream Baseline & License |
| :--- | :--- | :--- | :--- |
| `textures/gui/nei/neutron_interaction_atlas.png`<br>`textures/gui/nei/long_arrow.png` | `generate_nei_atlas.py` | 256×256 2D canvas synthesis using Python PIL: vector circle rendering, arrow shaft primitives, and text overlays with anti-aliasing. | Pure Procedural Code (MIT) |
| `textures/blocks/OVERLAY_FRONT_FISSION_REACTOR.png`<br>`textures/blocks/OVERLAY_FRONT_FISSION_REACTOR_ACTIVE.png` | `generate_reactor_overlays.py` | 16×16 base overlay + 16×64 4-frame animation strip. Combines metallic bezel rendering with animated core glow oscillations `sin(2π * frame / 4)`. | Pure Procedural Code (MIT) |
| `textures/blocks/fluids/corium_still.png`<br>`textures/blocks/fluids/corium_flow.png` | `generate_corium.py` | Pure Python standard library (`struct`, `zlib`, `math`). 16×512 (32 frames) still and 32×1024 (32 frames) flow. Uses approved low-contrast grit palette (seed 2041, base `#FFC000`, dark `#F09418`, light `#FFE068`, 38% contrast, 10% dark density, 29% light density). Uses continuous quantile thermal simmer looping and vertical flow coordinate offset. Companion live tuning tool: `corium_texture_studio.html`. | Pure Procedural Code (MIT) |
| `textures/blocks/fluids/heavywater_*`<br>`textures/blocks/fluids/highpressuredistilledwater_*`<br>`textures/blocks/fluids/highpressureheavywater_*` | `generate_water_fluids.java` | Single-file Java source. Performs deterministic HSB (Hue-Saturation-Brightness) color space transformations on IC2's baseline distilled water animated texture strips: Heavy Water shifted to deep royal blue (`H=0.58, S=0.65, B=0.70`), HP Distilled Water to bright cerulean (`H=0.57, S=0.55, B=0.90`), HP Heavy Water to violet indigo (`H=0.72, S=0.60, B=0.65`). | IndustrialCraft 2 baseline (Open Source) + Procedural Code |
| `textures/items/cellHeavyWater.png`<br>`textures/items/cellHighPressureDistilledWater.png`<br>`textures/items/cellHighPressureHeavyWater.png` | `generate_cells.py` | 2-layer image composition: base cell border (`cell.png`) composited with color-tinted liquid mask (`cell_OVERLAY.png`) using hexadecimal tints `#2d5e82`, `#3a69a8`, `#4b3c7a`. | GregTech 5 Unofficial baseline (LGPL-3.0) + Procedural Code |
| `textures/blocks/MACHINE_CASING_NUCLEAR.png` | `generate_casing.py` | Color-grade matrix transformation on GregTech 5's Reinforced Steel Casing (`BLOCK_STEELPREIN.png`), shifting base luminance and applying nuclear lead-shielded bluish tint. | GregTech 5 Unofficial baseline (LGPL-3.0) + Procedural Code |
| `textures/items/gt.radiovoltaic.plate.hv.png`<br>`textures/items/gt.radiovoltaic.plate.ev.png` | `generate_radiovoltaic_plates.py` | Pixel-wise re-coloration of GT5's neutron reflector plate (`gt.neutronreflector.png`), applying HV Nickel-Cobalt luster and EV Graphite/Silicon semiconductor tones. | GregTech 5 Unofficial baseline (LGPL-3.0) + Procedural Code |
| `textures/gui/nei/fuelstats/chart_*.png` (13 charts) | `FuelDamageCurveSolver.java` | Algorithmic 2D function plotter rendering the exact reactivity $R(T)$ and temperature damage penalty $D(T) = 1 + A \cdot (e^{k(T - T_{\text{pk}})} - 1)$ curves directly from physics parameters for all 13 nuclear fuels. | Pure Procedural Code (MIT) |

---

## 2. Upstream Extracted Assets

External assets extracted verbatim from open-source mods on the build classpath:

| Asset Path | Upstream Mod / Archive | Upstream Path | License | Purpose |
| :--- | :--- | :--- | :--- | :--- |
| `textures/items/card_modular_nuclear.png` | Nuclear-Control 2 (`Nuclear-Control-*.jar`) | `assets/nuclearcontrol/textures/items/cardReactor.png` | GPL-3.0 | In-game sensor card item texture |
| `textures/items/kit_modular_nuclear.png` | Nuclear-Control 2 (`Nuclear-Control-*.jar`) | `assets/nuclearcontrol/textures/items/kitReactor.png` | GPL-3.0 | In-game sensor kit item texture |
| `tools/textures/baseline/BLOCK_STEELPREIN.png` | GregTech 5 Unofficial | `assets/gregtech/textures/blocks/iconsets/BLOCK_STEELPREIN.png` | LGPL-3.0 | Baseline casing texture for `generate_casing.py` |
| `tools/textures/baseline/gt.neutronreflector.png` | GregTech 5 Unofficial | `assets/gregtech/textures/items/gt.neutronreflector.png` | LGPL-3.0 | Baseline plate texture for `generate_radiovoltaic_plates.py` |
| `tools/textures/baseline/cell.png` | GregTech 5 Unofficial | `assets/gregtech/textures/items/materialicons/CUSTOM/glowstone/cell.png` | LGPL-3.0 | Empty fluid cell outline for `generate_cells.py` |
| `tools/textures/baseline/cell_OVERLAY.png` | GregTech 5 Unofficial | `assets/gregtech/textures/items/materialicons/CUSTOM/glowstone/cell_OVERLAY.png` | LGPL-3.0 | Fluid cell liquid mask for `generate_cells.py` |
| `tools/textures/baseline/distilledwater_still.png` | IndustrialCraft 2 (`ic2-*.jar`) | `assets/ic2/textures/blocks/fluids/distilledwater_still.png` | Open Source | Baseline still water strip for `generate_water_fluids.java` |
| `tools/textures/baseline/distilledwater_flow.png` | IndustrialCraft 2 (`ic2-*.jar`) | `assets/ic2/textures/blocks/fluids/distilledwater_flow.png` | Open Source | Baseline flowing water strip for `generate_water_fluids.java` |

Extraction and SHA-256 verification are automated by:
```bash
python3 tools/textures/extract_external_assets.py
```

---

## 3. Simulator Webapp Icons

The standalone browser simulator (`nuclear-sim-dist`) serves 66 lightweight 16×16 tile icons under `textures/sim/icons/` so users can inspect reactors visually without requiring Minecraft:

- **IC2 Nuclear Components**: Heat vents, heat exchangers, coolant cells (10K, 60K, 360K), reflectors, and vanilla IC2 fuel rods (Uranium, MOX) extracted from IndustrialCraft 2 (Open Source).
- **GregTech 5 Multi-element Fuels & Hatches**: Thorium rods, Naquadah rods, Naquadria rods, fluid hatches, and coolant loop ports extracted from GregTech 5 Unofficial (LGPL-3.0).
- **ModularNuclear Additions**: Radiovoltaic plates (`RADIOVOLTAIC_HV.png`, `RADIOVOLTAIC_EV.png`) derived directly from the mod's procedural items.

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
python3 tools/textures/generate_all.py  # Standalone master procedural generator
python3 tools/textures/extract_external_assets.py # Verify upstream open-source baselines

# From nh-dev monorepo root:
make sim-textures                 # Runs texture generation across the simulator stack
```

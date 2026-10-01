# Modular Nuclear (MPTR)

**Modular Pressure Tube Reactor (MPTR)** is a multi-block nuclear reactor and thermodynamics simulation mod for Minecraft 1.7.10 (Forge).

Inspired by Modern Industrialization's nuclear mechanics and adapted for advanced GregTech / GTNH environments, the MPTR introduces multi-chamber grid layouts with fast and thermal neutron kinetics, boiling curve thermodynamics, multi-tier pressure containment, and multi-stage power generation.

🎮 **Interactive Browser Simulator**: [https://mgomezch.github.io/modular-nuclear-simulator/](https://mgomezch.github.io/modular-nuclear-simulator/)  
The simulator engine is compiled directly to client-side WebAssembly (WASM) with 100% mathematical parity to in-game simulation logic.

---

## Key Features

- **Chamber Grid Mechanics**:
  - Modular reactor core chambers configurable from 5×5 up to 13×13+.
  - Individual tile physics: temperatures, fuel burnup, neutron emission, and fluid turnover.
  - Multi-tier pipe casings (Electrum, Platinum, Osmium, Quantium, Fluxed Electrum, Black Plutonium) defining maximum operating temperatures and boiling pressure thresholds.

- **Neutron Kinetics & Fuel Transmutation**:
  - Two-group neutron diffusion model (Fast and Thermal neutrons).
  - Directional scattering through moderators (Graphite, Beryllium, Heavy Water).
  - Control rod insertion for reactivity regulation.
  - Nuclear breeding reactions (Thorium, Uranium-235/238, MOX, High-Density Uranium/Plutonium, Excited fuels, Naquadah, Naquadria, Tiberium, Lithium, and Glowstone).
  - Tritium, Deuterium, and direct Radiovoltaic energy harvesting.

- **Advanced Thermodynamics & Fluid Turnover**:
  - Active coolant boiling kinetics for Distilled Water, High-Pressure Distilled Water, Heavy Water, High-Pressure Heavy Water, and IC2 Coolant.
  - Non-linear turnover boiling curves with heat-transfer caps and dryout meltdown conditions.
  - Multi-stage heat exchange (LHE, Thermal Boilers, Extreme Heat Exchangers) and Large / Extreme Large Steam Turbines (LST / XLST) modeling.

- **Integrations**:
  - **Nuclear Control 2**: 15-mode telemetry sensor cards and display panel support.
  - **OpenComputers**: Dedicated `mptr_reactor` component driver exposing real-time core telemetry, hatch levels, thermal flux, and automated safety trips.
  - **Embedded Web Simulator**: Built-in HTTP server and exportable static WASM webapp for visual layout testing and balance tuning.

---

## Building from Source

This project targets Minecraft 1.7.10 using Java 17/21 with Gradle and JVM Downgrader to produce Java 8-compatible mod binaries.

```bash
# Clone the repository
git clone https://github.com/mgomezch/ModularNuclear.git
cd ModularNuclear

# Build mod jar
./gradlew build

# Run client in development environment
./gradlew runClient

# Build the WebAssembly standalone simulator
./gradlew generateWasm exportStaticDist
```

---

## License

GPLv3. See [LICENSE](https://github.com/GTNewHorizons/GT5-Unofficial/blob/master/LICENSE.txt) for details.

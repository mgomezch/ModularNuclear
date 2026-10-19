#!/usr/bin/env python3
"""
Master procedural texture generator for ModularNuclear.
Runs all component texture generators to deterministically regenerate all
procedural/algorithmic art assets for the mod.

Complies strictly with GTNH policy against AI-generated art assets:
Every texture produced here is derived from explicit mathematical equations,
harmonic wave physics, trigonometric noise functions, and geometric coordinate math.
Zero third-party files or baseline templates are used.
"""

import subprocess
import sys
import time
from pathlib import Path


def run_generator(script_path, args=None):
    if args is None:
        args = []
    cmd = [sys.executable, str(script_path)] + args
    print(f"\n▶ Executing: {script_path.name}")
    subprocess.check_call(cmd)


def main():
    t0 = time.time()
    tools_dir = Path(__file__).resolve().parent
    mod_root = tools_dir.parent.parent
    res_dir = mod_root / "src/main/resources/assets/modularnuclear/textures"

    print("================================================================================")
    print("           ModularNuclear — Procedural Texture Generation Suite                 ")
    print("================================================================================")
    print(f"Mod Root:     {mod_root}")
    print(f"Target Path:  {res_dir}")

    # 1. NEI Atlas & Long Arrow
    run_generator(tools_dir / "generate_nei_atlas.py")

    # 2. Reactor Front Overlays (Idle & Animated Active)
    run_generator(tools_dir / "generate_reactor_overlays.py")

    # 3. Molten Corium Fluids (Still & Flowing animations)
    run_generator(tools_dir / "generate_corium.py")

    # 4. Water Fluids (Heavy Water, HP Distilled Water, HP Heavy Water) via harmonic wave math
    run_generator(tools_dir / "generate_water_fluids.py")

    # 5. Fluid Cells (Pure procedural canisters & liquid columns)
    run_generator(tools_dir / "generate_cells.py")

    # 6. Nuclear Machine Casing (Pure procedural lead-shielded casing)
    run_generator(tools_dir / "generate_casing.py")

    # 7. Radiovoltaic Plates (Pure procedural Si-Ge and SiC-Graphene wafers)
    run_generator(tools_dir / "generate_radiovoltaic_plates.py")

    dt = time.time() - t0
    print("\n================================================================================")
    print(f"✓ All procedural textures generated successfully in {dt:.2f}s!")
    print("================================================================================")


if __name__ == "__main__":
    main()

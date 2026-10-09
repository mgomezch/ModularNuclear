#!/usr/bin/env python3
"""
Master procedural texture generator for ModularNuclear.
Runs all component texture generators to deterministically regenerate all
procedural/algorithmic art assets for the mod.

Complies strictly with GTNH policy against AI-generated art assets:
every texture produced here is derived from explicit mathematical equations,
color-space algorithms, trigonometric noise functions, or open-source baseline textures.
"""

import os
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
    baseline_dir = tools_dir / "baseline"

    print("================================================================================")
    print("           ModularNuclear — Procedural Texture Generation Suite                 ")
    print("================================================================================")
    print(f"Mod Root:     {mod_root}")
    print(f"Baseline:     {baseline_dir}")
    print(f"Target Path:  {res_dir}")

    # 1. NEI Atlas & Long Arrow
    run_generator(tools_dir / "generate_nei_atlas.py")

    # 2. Reactor Front Overlays (Idle & Animated Active)
    run_generator(tools_dir / "generate_reactor_overlays.py")

    # 3. Molten Corium Fluids (Still & Flowing animations)
    run_generator(tools_dir / "generate_corium.py")

    # 4. Water Fluids (Heavy Water, HP Distilled Water, HP Heavy Water) via single-file Java
    java_script = tools_dir / "generate_water_fluids.java"
    print(f"\n▶ Executing: {java_script.name}")
    subprocess.check_call(["java", str(java_script), str(baseline_dir), str(res_dir / "blocks/fluids")])

    # 5. Fluid Cells
    run_generator(tools_dir / "generate_cells.py")

    # 6. Nuclear Machine Casing
    run_generator(tools_dir / "generate_casing.py")

    # 7. Radiovoltaic Plates
    run_generator(tools_dir / "generate_radiovoltaic_plates.py")

    dt = time.time() - t0
    print("\n================================================================================")
    print(f"✓ All procedural textures generated successfully in {dt:.2f}s!")
    print("================================================================================")


if __name__ == "__main__":
    main()

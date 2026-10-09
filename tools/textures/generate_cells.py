#!/usr/bin/env python3
"""
Procedural generator for fluid cell item textures:
  - cellHeavyWater.png (tint #2d5e82)
  - cellHighPressureDistilledWater.png (tint #3a69a8)
  - cellHighPressureHeavyWater.png (tint #4b3c7a)

Composites the base fluid cell container with the respective fluid tint
and overlays the metallic rim and glass reflections from cell_OVERLAY.png.
"""

import shutil
import subprocess
from pathlib import Path


def generate_with_imagemagick(cell_base, cell_overlay, out_path, hex_color):
    cmd = [
        "magick" if shutil.which("magick") else "convert",
        str(cell_base),
        "(", "+clone", "-fill", hex_color, "-colorize", "100%", ")",
        "-compose", "Multiply", "-composite",
        str(cell_overlay), "-compose", "Over", "-composite",
        str(out_path)
    ]
    subprocess.check_call(cmd)
    print(f"  [✓] Wrote {out_path.name} (tint {hex_color})")


def main(baseline_dir=None, output_dir=None):
    script_dir = Path(__file__).resolve().parent
    if baseline_dir is None:
        baseline_dir = script_dir / "baseline"
    else:
        baseline_dir = Path(baseline_dir)

    if output_dir is None:
        output_dir = script_dir.parent.parent / "src/main/resources/assets/modularnuclear/textures/items"
    else:
        output_dir = Path(output_dir)

    cell_base = baseline_dir / "cell.png"
    cell_overlay = baseline_dir / "cell_OVERLAY.png"

    if not cell_base.exists() or not cell_overlay.exists():
        raise FileNotFoundError(f"Baseline cell textures not found in {baseline_dir}")

    output_dir.mkdir(parents=True, exist_ok=True)
    print("Generating Fluid Cell Textures:")

    cells = [
        ("cellHeavyWater.png", "#2d5e82"),
        ("cellHighPressureDistilledWater.png", "#3a69a8"),
        ("cellHighPressureHeavyWater.png", "#4b3c7a"),
    ]

    for name, color in cells:
        generate_with_imagemagick(cell_base, cell_overlay, output_dir / name, color)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""
Procedural generator for Nuclear Machine Casing block texture:
  - assets/modularnuclear/textures/blocks/MACHINE_CASING_NUCLEAR.png

Maps the structural reinforced steel base (BLOCK_STEELPREIN.png) through
a reinforced radiation-shielding lead-bismuth cool alloy color transform:
  1. Base luminance mapping: r *= 0.7475, g *= 1.0075, b *= 0.8322
  2. HSV adjustment: Value * 0.80, Saturation * 1.35
"""

import colorsys
import shutil
import subprocess
from pathlib import Path


def generate_casing(steel_path, out_path):
    magick = "magick" if shutil.which("magick") else "convert"
    out = subprocess.check_output([magick, str(steel_path), "txt:-"]).decode("utf-8")
    lines = out.strip().splitlines()[1:]

    new_pixels = []
    for line in lines:
        parts = line.split(":")
        coords = parts[0].strip()
        val = int(parts[1].split()[0].strip("()").split(",")[0])

        # Step 1: Base luminance mapping
        r = min(255, max(0, round(val * 0.7475)))
        g = min(255, max(0, round(val * 1.0075)))
        b = min(255, max(0, round(val * 0.8322)))

        # Step 2: HSV dark alloy modulation
        h, s, v = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
        v_new = v * 0.80
        s_new = min(1.0, s * 1.35)
        r_new, g_new, b_new = colorsys.hsv_to_rgb(h, s_new, v_new)

        rn = int(round(r_new * 255))
        gn = int(round(g_new * 255))
        bn = int(round(b_new * 255))
        new_pixels.append(f"{coords}: ({rn},{gn},{bn})")

    header = ["# ImageMagick pixel enumeration: 16,16,0,255,srgb"]
    content = "\n".join(header + new_pixels) + "\n"

    tmp_txt = out_path.with_suffix(".txt")
    tmp_txt.write_text(content)
    subprocess.check_call([magick, f"txt:{tmp_txt}", f"PNG32:{out_path}"])
    tmp_txt.unlink(missing_ok=True)

    print(f"  [✓] Wrote {out_path.name}")


def main(baseline_dir=None, output_dir=None):
    script_dir = Path(__file__).resolve().parent
    if baseline_dir is None:
        baseline_dir = script_dir / "baseline"
    else:
        baseline_dir = Path(baseline_dir)

    if output_dir is None:
        output_dir = script_dir.parent.parent / "src/main/resources/assets/modularnuclear/textures/blocks"
    else:
        output_dir = Path(output_dir)

    steel_path = baseline_dir / "BLOCK_STEELPREIN.png"
    if not steel_path.exists():
        raise FileNotFoundError(f"Baseline BLOCK_STEELPREIN.png not found in {baseline_dir}")

    output_dir.mkdir(parents=True, exist_ok=True)
    print("Generating Nuclear Casing Texture:")
    generate_casing(steel_path, output_dir / "MACHINE_CASING_NUCLEAR.png")


if __name__ == "__main__":
    main()

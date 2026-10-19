#!/usr/bin/env python3
"""
100% Pure Procedural Generator for Nuclear Machine Casing block texture:
  - assets/modularnuclear/textures/blocks/MACHINE_CASING_NUCLEAR.png

Complies strictly with GTNH policy against AI-generated art assets:
Synthesizes the reinforced nuclear lead-shielded casing from scratch using pure
geometric coordinate math, bevel shading, and procedural metallic surface grit.
Zero third-party files or baseline textures are used.
"""

import struct
import zlib
from pathlib import Path


def write_png_rgb(width, height, rgb_pixels, out_path):
    raw = bytearray()
    for y in range(height):
        raw.append(0)  # Filter None
        for x in range(width):
            r, g, b = rgb_pixels[y * width + x]
            raw.extend((max(0, min(255, r)), max(0, min(255, g)), max(0, min(255, b))))

    compressed = zlib.compress(bytes(raw), 9)
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    ihdr_crc = zlib.crc32(b"IHDR" + ihdr) & 0xFFFFFFFF
    idat_crc = zlib.crc32(b"IDAT" + compressed) & 0xFFFFFFFF
    iend_crc = zlib.crc32(b"IEND") & 0xFFFFFFFF

    data = (
        b"\x89PNG\r\n\x1a\n"
        + struct.pack(">I", len(ihdr)) + b"IHDR" + ihdr + struct.pack(">I", ihdr_crc)
        + struct.pack(">I", len(compressed)) + b"IDAT" + compressed + struct.pack(">I", idat_crc)
        + struct.pack(">I", 0) + b"IEND" + struct.pack(">I", iend_crc)
    )

    with open(out_path, "wb") as f:
        f.write(data)


def generate_casing(out_path):
    width, height = 16, 16
    pixels = []

    # Lead-shielded bismuth steel base tone: cool greenish-slate (#566864)
    base_r, base_g, base_b = 86, 104, 100

    for y in range(height):
        for x in range(width):
            # Deterministic pseudo-random surface grit noise
            noise = ((x * 17 + y * 29 + 11) % 13) - 6
            r = base_r + noise
            g = base_g + noise
            b = base_b + noise

            # Outer border bevel (1px outer chamfer)
            if x == 0 or y == 0:
                r += 38; g += 42; b += 40  # Top/Left highlight
            elif x == 15 or y == 15:
                r -= 42; g -= 48; b -= 45  # Bottom/Right shadow
            elif x == 1 or y == 1:
                r += 18; g += 20; b += 19
            elif x == 14 or y == 14:
                r -= 22; g -= 26; b -= 24

            # Central recessed reinforced panel rim (3..12)
            if x >= 3 and x <= 12 and y >= 3 and y <= 12:
                if x == 3 or y == 3:
                    r -= 25; g -= 28; b -= 26
                elif x == 12 or y == 12:
                    r += 20; g += 24; b += 22
                else:
                    # Inset panel plate
                    r += 2; g += 4; b += 3

            pixels.append((r, g, b))

    # Heavy corner hex bolts at (2,2), (2,13), (13,2), (13,13)
    bolts = [(2, 2), (13, 2), (2, 13), (13, 13)]
    for bx, by in bolts:
        # Rivet highlight
        pixels[by * 16 + bx] = (168, 185, 178)
        pixels[by * 16 + (bx + 1)] = (130, 145, 138)
        pixels[(by + 1) * 16 + bx] = (130, 145, 138)
        # Rivet drop shadow
        pixels[(by + 1) * 16 + (bx + 1)] = (36, 44, 42)

    write_png_rgb(width, height, pixels, out_path)
    print(f"  [✓] Wrote {out_path.name} (pure procedural lead-shielded casing)")


def main(output_dir=None):
    script_dir = Path(__file__).resolve().parent
    if output_dir is None:
        output_dir = script_dir.parent.parent / "src/main/resources/assets/modularnuclear/textures/blocks"
    else:
        output_dir = Path(output_dir)

    output_dir.mkdir(parents=True, exist_ok=True)
    print("Generating Nuclear Casing Texture:")
    generate_casing(output_dir / "MACHINE_CASING_NUCLEAR.png")


if __name__ == "__main__":
    main()

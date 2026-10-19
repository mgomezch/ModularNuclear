#!/usr/bin/env python3
"""
100% Pure Procedural Generator for fluid cell item textures:
  - assets/modularnuclear/textures/items/cellHeavyWater.png (tint #2d5e82)
  - assets/modularnuclear/textures/items/cellHighPressureDistilledWater.png (tint #3a69a8)
  - assets/modularnuclear/textures/items/cellHighPressureHeavyWater.png (tint #4b3c7a)

Complies strictly with GTNH policy against AI-generated art assets:
Synthesizes the complete fluid cell canister geometry, metallic pressure seals,
sight-gauge viewing window, and fluid column directly from coordinate math.
Zero third-party files or baseline textures are used.
"""

import struct
import zlib
from pathlib import Path


def write_png_rgba(width, height, rgba_pixels, out_path):
    raw = bytearray()
    for y in range(height):
        raw.append(0)  # Filter None
        for x in range(width):
            r, g, b, a = rgba_pixels[y * width + x]
            raw.extend((max(0, min(255, r)), max(0, min(255, g)), max(0, min(255, b)), max(0, min(255, a))))

    compressed = zlib.compress(bytes(raw), 9)
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)  # Type 6: RGBA
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


def generate_cell(out_path, fluid_base_rgb, fluid_highlight_rgb):
    width, height = 16, 16
    pixels = []

    fb_r, fb_g, fb_b = fluid_base_rgb
    fh_r, fh_g, fh_b = fluid_highlight_rgb

    # Canister mask definition (16x16)
    # Outside is transparent (a=0)
    for y in range(height):
        for x in range(width):
            # 1. Background transparency outside canister silhouette
            # Top cap at rows 1-2, cols 6-9
            is_cap = (y in (1, 2)) and (x in (6, 7, 8, 9))
            # Shoulder at row 3, cols 5-10
            is_shoulder = (y == 3) and (x >= 5 and x <= 10)
            # Main cylindrical body at rows 4-13, cols 4-11
            is_body = (y >= 4 and y <= 13) and (x >= 4 and x <= 11)
            # Bottom base at row 14, cols 5-10
            is_base = (y == 14) and (x >= 5 and x <= 10)

            if not (is_cap or is_shoulder or is_body or is_base):
                pixels.append((0, 0, 0, 0))
                continue

            # 2. Outer dark border outline
            is_outline = (
                (y == 1 and x in (6, 9))
                or (y == 2 and x in (6, 9))
                or (y == 3 and x in (5, 10))
                or (y >= 4 and y <= 13 and (x == 4 or x == 11))
                or (y == 14 and (x == 5 or x == 10))
                or (y == 1 and (x in (7, 8))) # top edge
                or (y == 14 and (x >= 6 and x <= 9)) # bottom edge
            )

            if is_outline:
                # Dark outline with subtle metal rim lighting
                if x == 4 or (y == 1 and x == 7) or (y == 3 and x == 5):
                    pixels.append((120, 128, 136, 255)) # Rim highlight
                else:
                    pixels.append((42, 46, 52, 255))    # Dark outline
                continue

            # 3. Metallic canister casing & reinforcement bands
            # Casing collar (row 2-3), reinforcement ribs (row 5 and row 12), and outer cylinder sides (cols 5, 10)
            is_rib = (y == 5 or y == 12) and (x >= 5 and x <= 10)
            is_metal_casing = (y == 2 or y == 3 or y == 13 or x == 5 or x == 10 or is_rib)

            if is_metal_casing:
                # Brushed steel gradient across x
                noise = ((x * 11 + y * 17) % 7) - 3
                if x <= 6:
                    c = 190 + noise # Highlight left side
                elif x <= 8:
                    c = 145 + noise # Mid-tone
                else:
                    c = 95 + noise  # Shadow right side
                pixels.append((c - 3, c, c + 6, 255))
                continue

            # 4. Center viewing window & fluid column (rows 6..11, cols 6..9)
            # Fluid column
            f_noise = ((x * 19 + y * 23) % 7) - 3
            fr = max(0, min(255, fb_r + f_noise))
            fg = max(0, min(255, fb_g + f_noise))
            fb = max(0, min(255, fb_b + f_noise))

            # Fluid meniscus / surface meniscus at row 6
            if y == 6:
                fr = min(255, fr + 20)
                fg = min(255, fg + 20)
                fb = min(255, fb + 20)

            # Diagonal glass specular reflection glint: (6,7), (7,8), (8,9)
            if (x == 6 and y in (7, 8)) or (x == 7 and y in (8, 9)) or (x == 8 and y in (9, 10)):
                fr = (fr + fh_r) // 2
                fg = (fg + fh_g) // 2
                fb = (fb + fh_b) // 2

            pixels.append((fr, fg, fb, 255))

    write_png_rgba(width, height, pixels, out_path)


def main(output_dir=None):
    script_dir = Path(__file__).resolve().parent
    if output_dir is None:
        output_dir = script_dir.parent.parent / "src/main/resources/assets/modularnuclear/textures/items"
    else:
        output_dir = Path(output_dir)

    output_dir.mkdir(parents=True, exist_ok=True)
    print("Generating Fluid Cell Textures:")

    cells = [
        ("cellHeavyWater.png", (45, 94, 130), (120, 180, 230), "Heavy Water"),
        ("cellHighPressureDistilledWater.png", (58, 105, 168), (140, 200, 255), "HP Distilled Water"),
        ("cellHighPressureHeavyWater.png", (75, 60, 122), (160, 140, 230), "HP Heavy Water"),
    ]

    for filename, base_rgb, highlight_rgb, desc in cells:
        dest = output_dir / filename
        generate_cell(dest, base_rgb, highlight_rgb)
        print(f"  [✓] Wrote {filename} (pure procedural {desc})")


if __name__ == "__main__":
    main()

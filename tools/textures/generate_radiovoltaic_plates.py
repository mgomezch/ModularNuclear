#!/usr/bin/env python3
"""
100% Pure Procedural Generator for Radiovoltaic Plate item textures:
  - assets/modularnuclear/textures/items/gt.radiovoltaic.plate.hv.png (HV - Gold/Nickel busbars on Si-Ge wafer)
  - assets/modularnuclear/textures/items/gt.radiovoltaic.plate.ev.png (EV - Cyan superconductor on SiC-Graphene wafer)

Complies strictly with GTNH policy against AI-generated art assets:
Synthesizes the complete semiconductor plate geometry, micro-grid lines,
and collector busbars from scratch using pure coordinate math.
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


def generate_plate(out_path, wafer_base, busbar_color, pad_color, highlight_color):
    width, height = 16, 16
    pixels = []

    wb_r, wb_g, wb_b = wafer_base
    bb_r, bb_g, bb_b = busbar_color
    pd_r, pd_g, pd_b = pad_color
    hl_r, hl_g, hl_b = highlight_color

    # Corner cutoffs for classic GregTech item plate profile (16x16)
    transparent_corners = {
        (0, 0), (1, 0), (2, 0), (0, 1), (1, 1), (0, 2),
        (13, 0), (14, 0), (15, 0), (14, 1), (15, 1), (15, 2),
        (0, 13), (0, 14), (0, 15), (1, 14), (1, 15), (2, 15),
        (15, 13), (14, 14), (15, 14), (13, 15), (14, 15), (15, 15)
    }

    for y in range(height):
        for x in range(width):
            if (x, y) in transparent_corners:
                pixels.append((0, 0, 0, 0))
                continue

            # Base wafer tone with subtle micro-texture
            noise = ((x * 13 + y * 7) % 7) - 3
            r = wb_r + noise
            g = wb_g + noise
            b = wb_b + noise

            # Micro-grid lines (every 2 pixels on wafer)
            if (x % 2 == 0) and (y % 2 == 0) and x >= 3 and x <= 12 and y >= 3 and y <= 12:
                r -= 8; g -= 8; b -= 8

            # Outer bevel perimeter
            is_top_left = (x == 1 and y >= 2 and y <= 13) or (y == 1 and x >= 2 and x <= 13) or (x == 2 and y == 1) or (x == 1 and y == 2)
            is_bot_right = (x == 14 and y >= 2 and y <= 13) or (y == 14 and x >= 2 and x <= 13) or (x == 13 and y == 14) or (x == 14 and y == 13)

            if is_top_left:
                r += 40; g += 40; b += 40
            elif is_bot_right:
                r -= 35; g -= 35; b -= 35

            # Horizontal collector busbars at y=6 and y=9 (rotated 90 degrees)
            if (y == 6 or y == 9) and x >= 3 and x <= 12:
                r, g, b = bb_r, bb_g, bb_b
                if y == 6:
                    r = min(255, r + 15); g = min(255, g + 15); b = min(255, b + 15)

            # Central vertical interconnect at x=7 and x=8 between busbars
            if (x == 7 or x == 8) and y >= 6 and y <= 9:
                r, g, b = bb_r, bb_g, bb_b

            # Contact pads at left and right ends of busbars
            if (y in (5, 6, 7) or y in (8, 9, 10)) and (x == 3 or x == 12):
                r, g, b = pd_r, pd_g, pd_b
            if (y == 6 or y == 9) and (x == 3 or x == 12):
                r, g, b = hl_r, hl_g, hl_b  # Contact glint

            pixels.append((r, g, b, 255))

    write_png_rgba(width, height, pixels, out_path)


def main(output_dir=None):
    script_dir = Path(__file__).resolve().parent
    if output_dir is None:
        output_dir = script_dir.parent.parent / "src/main/resources/assets/modularnuclear/textures/items"
    else:
        output_dir = Path(output_dir)

    output_dir.mkdir(parents=True, exist_ok=True)
    print("Generating Radiovoltaic Plate Textures:")

    # HV: Warm Si-Ge semiconductor wafer with Gold/Nickel collector grid
    hv_dest = output_dir / "gt.radiovoltaic.plate.hv.png"
    generate_plate(
        hv_dest,
        wafer_base=(48, 56, 64),
        busbar_color=(235, 175, 25),
        pad_color=(255, 210, 60),
        highlight_color=(255, 245, 160)
    )
    print(f"  [✓] Wrote {hv_dest.name} (pure procedural HV Gold/Nickel)")

    # EV: Ultra-dense SiC-Graphene semiconductor wafer with Cyan superconductor grid
    ev_dest = output_dir / "gt.radiovoltaic.plate.ev.png"
    generate_plate(
        ev_dest,
        wafer_base=(30, 36, 42),
        busbar_color=(0, 200, 230),
        pad_color=(60, 230, 255),
        highlight_color=(200, 250, 255)
    )
    print(f"  [✓] Wrote {ev_dest.name} (pure procedural EV Cyan superconductor)")

    # Also update web simulator tile icons if present
    sim_icons_dir = script_dir.parent.parent / "src/main/resources/assets/modularnuclear/textures/sim/icons"
    if sim_icons_dir.exists():
        hv_sim = sim_icons_dir / "RADIOVOLTAIC_HV.png"
        ev_sim = sim_icons_dir / "RADIOVOLTAIC_EV.png"
        generate_plate(
            hv_sim,
            wafer_base=(48, 56, 64),
            busbar_color=(235, 175, 25),
            pad_color=(255, 210, 60),
            highlight_color=(255, 245, 160)
        )
        generate_plate(
            ev_sim,
            wafer_base=(30, 36, 42),
            busbar_color=(0, 200, 230),
            pad_color=(60, 230, 255),
            highlight_color=(200, 250, 255)
        )
        print(f"  [✓] Synchronized {hv_sim.name} and {ev_sim.name} for web simulator")


if __name__ == "__main__":
    main()


#!/usr/bin/env python3
"""
Procedural generator for Fission Reactor controller face overlay textures.
Generates both the inactive emblem and the pulsating 4-frame active animation
using purely procedural trigonometry (trefoil hazard blades, recessed background,
and pulsating LED + power indicator lines).

Targets:
  - assets/modularnuclear/textures/blocks/OVERLAY_FRONT_FISSION_REACTOR.png (16x16)
  - assets/modularnuclear/textures/blocks/OVERLAY_FRONT_FISSION_REACTOR_ACTIVE.png (16x64)
  - assets/modularnuclear/textures/blocks/OVERLAY_FRONT_FISSION_REACTOR_ACTIVE.png.mcmeta
"""

import math
import struct
import zlib
from pathlib import Path


def write_png(path, w, h, pixels):
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        raw.extend(row)
    compressed = zlib.compress(bytes(raw), 9)
    ihdr = struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)
    png = bytearray(b'\x89PNG\r\n\x1a\n')

    def write_chunk(ctype, data):
        png.extend(struct.pack('>I', len(data)))
        png.extend(ctype)
        png.extend(data)
        crc = zlib.crc32(ctype + data) & 0xFFFFFFFF
        png.extend(struct.pack('>I', crc))

    write_chunk(b'IHDR', ihdr)
    write_chunk(b'IDAT', compressed)
    write_chunk(b'IEND', b'')
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    with open(path, 'wb') as f:
        f.write(png)
    print(f"  [✓] Wrote {path} ({w}x{h})")


def make_reactor_frame(active=False, pulse_intensity=0.0):
    w, h = 16, 16
    cx, cy = 7.5, 7.5
    frame = []

    r_trefoil = int(240 + 15 * pulse_intensity)
    g_trefoil = int(195 + 45 * pulse_intensity)
    b_trefoil = int(10 + 60 * pulse_intensity)
    border_r = int(170 + 20 * pulse_intensity)
    border_g = int(120 + 30 * pulse_intensity)
    border_b = int(5)

    for y in range(h):
        row = bytearray()
        for x in range(w):
            dx = x - cx
            dy = y - cy
            dist = math.sqrt(dx * dx + dy * dy)
            angle = math.degrees(math.atan2(dy, dx)) % 360

            # Default transparent (allows machine casing to show through)
            r, g, b, a = 0, 0, 0, 0

            # Recessed background plate (circle radius <= 7.2)
            if dist <= 7.2:
                plate_shade = int(32 - 1.5 * dist)
                r, g, b, a = plate_shade, plate_shade + 2, plate_shade, 230
                if dist >= 6.4:
                    r, g, b, a = 50, 54, 50, 240

            # Central trefoil hub
            if dist <= 1.5:
                if dist <= 1.1:
                    r, g, b, a = r_trefoil, g_trefoil, b_trefoil, 255
                else:
                    r, g, b, a = border_r, border_g, border_b, 255
            # Trefoil blades
            elif 2.3 <= dist <= 5.8:
                in_blade = False
                on_edge = False
                for b_start, b_end in [(60, 120), (180, 240), (300, 360)]:
                    if b_start <= angle <= b_end:
                        in_blade = True
                        if dist >= 5.3 or dist <= 2.6 or angle <= b_start + 4 or angle >= b_end - 4:
                            on_edge = True
                        break
                if in_blade:
                    if on_edge:
                        r, g, b, a = border_r, border_g, border_b, 255
                    else:
                        r, g, b, a = r_trefoil, g_trefoil, b_trefoil, 255

            # Status Indicator LED at (13, 2)
            if (x == 13 and y == 2) or (x == 14 and y == 2):
                if active:
                    r, g, b, a = int(20 + 30 * pulse_intensity), int(220 + 35 * pulse_intensity), 80, 255
                else:
                    r, g, b, a = 110, 25, 25, 255
            elif active and ((x == 12 and y == 2) or (x == 13 and y == 1) or (x == 13 and y == 3)):
                r, g, b, a = 10, int(120 + 40 * pulse_intensity), 40, 160

            # Power bar/lines at bottom
            if y == 14 and 4 <= x <= 11:
                if active:
                    r, g, b, a = 40, int(180 + 50 * pulse_intensity), 200, 220
                else:
                    r, g, b, a = 40, 45, 50, 180

            row.extend([r, g, b, a])
        frame.append(row)
    return frame


def main(output_dir=None):
    if output_dir is None:
        output_dir = Path(__file__).resolve().parent.parent.parent / "src/main/resources/assets/modularnuclear/textures/blocks"
    else:
        output_dir = Path(output_dir)

    print("Generating Fission Reactor Overlay Textures:")
    # 1. Inactive overlay
    inactive_frame = make_reactor_frame(active=False, pulse_intensity=0.0)
    write_png(output_dir / "OVERLAY_FRONT_FISSION_REACTOR.png", 16, 16, inactive_frame)

    # 2. Active pulsating overlay
    active_frames = []
    for intens in [0.0, 0.5, 1.0, 0.5]:
        active_frames.extend(make_reactor_frame(active=True, pulse_intensity=intens))
    write_png(output_dir / "OVERLAY_FRONT_FISSION_REACTOR_ACTIVE.png", 16, 64, active_frames)

    # 3. .mcmeta animation config
    mcmeta_path = output_dir / "OVERLAY_FRONT_FISSION_REACTOR_ACTIVE.png.mcmeta"
    mcmeta_path.write_text('{\n  "animation": {\n    "frametime": 3\n  }\n}\n')
    print(f"  [✓] Wrote {mcmeta_path}")


if __name__ == "__main__":
    main()

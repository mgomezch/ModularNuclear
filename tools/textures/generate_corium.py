#!/usr/bin/env python3
"""
Procedural generator for Molten Corium animated fluid textures.
Uses pure Python standard library (math, struct, zlib) with multi-frequency
trigonometric wave synthesis and amber-to-white superheated plasma color mapping.

Targets:
  - assets/modularnuclear/textures/blocks/fluids/corium_still.png (16x512, 32 frames)
  - assets/modularnuclear/textures/blocks/fluids/corium_still.png.mcmeta
  - assets/modularnuclear/textures/blocks/fluids/corium_flow.png (32x1024, 32 frames)
  - assets/modularnuclear/textures/blocks/fluids/corium_flow.png.mcmeta
"""

import math
import struct
import zlib
from pathlib import Path


def write_png(path, w, h, pixels):
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for r, g, b, a in row:
            raw.extend((r, g, b, a))
    compressed = zlib.compress(bytes(raw), 9)
    ihdr = struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)
    png = bytearray(b'\x89PNG\r\n\x1a\n')

    def chunk(ctype, data):
        png.extend(struct.pack('>I', len(data)))
        png.extend(ctype)
        png.extend(data)
        crc = zlib.crc32(ctype + data) & 0xFFFFFFFF
        png.extend(struct.pack('>I', crc))

    chunk(b'IHDR', ihdr)
    chunk(b'IDAT', compressed)
    chunk(b'IEND', b'')
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    with open(path, 'wb') as f:
        f.write(png)
    print(f"  [✓] Wrote {path} ({w}x{h})")


def generate_noise(x, y, t, freq=0.2):
    val = math.sin(x * freq + t) * math.cos(y * freq + t * 0.7)
    val += math.sin((x + y) * freq * 0.5 - t * 1.3) * 0.5
    val += math.cos((x - y) * freq * 0.8 + t * 0.9) * 0.25
    return (val + 1.75) / 3.5  # Normalized 0..1


def get_corium_color(val):
    # Vibrant bright-yellow glowing molten corium
    # val: 0.0 (darker amber-gold crust) to 1.0 (white-hot blazing yellow)
    if val < 0.25:
        # Amber-gold: #FF9E00 -> #FFC107
        t = val / 0.25
        r = 255
        g = int(158 + (193 - 158) * t)
        b = int(0 + (7 - 0) * t)
    elif val < 0.65:
        # Golden Yellow -> Intense Canary Yellow: #FFC107 -> #FFEE58
        t = (val - 0.25) / 0.40
        r = 255
        g = int(193 + (238 - 193) * t)
        b = int(7 + (88 - 7) * t)
    elif val < 0.90:
        # Bright Canary -> Pale Superheated Yellow: #FFEE58 -> #FFF9C4
        t = (val - 0.65) / 0.25
        r = 255
        g = int(238 + (249 - 238) * t)
        b = int(88 + (196 - 88) * t)
    else:
        # Pale Yellow -> White-Hot Core: #FFF9C4 -> #FFFFFF
        t = (val - 0.90) / 0.10
        r = 255
        g = int(249 + (255 - 249) * t)
        b = int(196 + (255 - 196) * t)
    return (r, g, b, 255)


def main(output_dir=None):
    if output_dir is None:
        output_dir = Path(__file__).resolve().parent.parent.parent / "src/main/resources/assets/modularnuclear/textures/blocks/fluids"
    else:
        output_dir = Path(output_dir)

    print("Generating Molten Corium Procedural Textures:")
    num_frames = 32

    # 1. Still: 16x512 (32 frames of 16x16)
    frame_w = 16
    frame_h = 16
    grid_still = []
    for f in range(num_frames):
        t = (f / num_frames) * 2.0 * math.pi
        for y in range(frame_h):
            row = []
            for x in range(frame_w):
                val = generate_noise(x, y, t, freq=0.35)
                row.append(get_corium_color(val))
            grid_still.append(row)

    write_png(output_dir / "corium_still.png", frame_w, frame_h * num_frames, grid_still)

    # 2. Flow: 32x1024 (32 frames of 32x32, animated with downward flow drift)
    frame_w_flow = 32
    frame_h_flow = 32
    grid_flow = []
    for f in range(num_frames):
        t = (f / num_frames) * 2.0 * math.pi
        y_drift = (f / num_frames) * frame_h_flow
        for y in range(frame_h_flow):
            row = []
            for x in range(frame_w_flow):
                eff_y = (y + y_drift) % frame_h_flow
                val = generate_noise(x, eff_y, t, freq=0.25)
                row.append(get_corium_color(val))
            grid_flow.append(row)

    write_png(output_dir / "corium_flow.png", frame_w_flow, frame_h_flow * num_frames, grid_flow)

    # 3. .mcmeta files
    mcmeta_content = '{\n  "animation": {}\n}\n'
    (output_dir / "corium_still.png.mcmeta").write_text(mcmeta_content)
    (output_dir / "corium_flow.png.mcmeta").write_text(mcmeta_content)
    print(f"  [✓] Wrote mcmeta configs")


if __name__ == "__main__":
    main()

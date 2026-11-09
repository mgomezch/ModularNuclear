#!/usr/bin/env python3
"""
Procedural generator for the NEI long animated arrow texture.
Uses pure Python standard library (math, struct, zlib) with zero external dependencies.

Targets:
  - assets/modularnuclear/textures/gui/nei/long_arrow.png (40x40)
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


def generate_long_arrow():
    w, h = 40, 40
    grid = [[(0, 0, 0, 0) for _ in range(w)] for _ in range(h)]

    def in_arrow(x, y):
        if 7 <= y <= 12 and 3 <= x <= 24:
            return True
        if 25 <= x <= 36:
            dx = x - 25
            max_dy = 6 - (dx * 6) / 11.0
            if abs(y - 9.5) <= max_dy:
                return True
        return False

    def is_border(x, y):
        if not in_arrow(x, y):
            return False
        for nx, ny in [(x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)]:
            if not in_arrow(nx, ny):
                return True
        return False

    # Top half: empty track arrow
    for y in range(20):
        for x in range(w):
            if is_border(x, y):
                grid[y][x] = (0x30, 0x30, 0x30, 255)
            elif in_arrow(x, y):
                val = 0x8A + (y - 7) * 4
                grid[y][x] = (val, val, val, 255)

    # Bottom half: filled active progress arrow
    for y in range(20):
        for x in range(w):
            by = y + 20
            if is_border(x, y):
                grid[by][x] = (0x10, 0x30, 0x40, 255)
            elif in_arrow(x, y):
                t = x / 36.0
                r = int(0x00 * (1 - t) + 0x55 * t)
                g = int(0xD0 * (1 - t) + 0xFF * t)
                b = int(0xFF * (1 - t) + 0x80 * t)
                if abs(y - 9.5) < 1.5:
                    r = min(255, r + 60)
                    g = min(255, g + 60)
                    b = min(255, b + 60)
                grid[by][x] = (r, g, b, 255)

    return w, h, grid


def main(output_dir=None):
    if output_dir is None:
        output_dir = Path(__file__).resolve().parent.parent.parent / "src/main/resources/assets/modularnuclear/textures/gui/nei"
    else:
        output_dir = Path(output_dir)

    print("Generating NEI Procedural Textures:")
    w, h, grid = generate_long_arrow()
    write_png(output_dir / "long_arrow.png", w, h, grid)


if __name__ == "__main__":
    main()

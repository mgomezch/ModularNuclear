#!/usr/bin/env python3
"""
Procedural generator for NEI neutron interaction atlas and long animated arrow textures.
Uses pure Python standard library (math, struct, zlib) with zero external dependencies.

Targets:
  - assets/modularnuclear/textures/gui/nei/neutron_interaction_atlas.png (256x256)
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


def generate_atlas():
    w, h = 256, 256
    grid = [[(0, 0, 0, 0) for _ in range(w)] for _ in range(h)]

    def draw_pixel(x, y, color):
        if 0 <= x < w and 0 <= y < h:
            grid[y][x] = color

    def draw_arrow_head_right(tip_x, tip_y, length, color, color_inner=None):
        for i in range(length):
            draw_pixel(tip_x - i, tip_y - i, color)
            draw_pixel(tip_x - i, tip_y + i, color)
            if color_inner:
                for dy in range(-i + 1, i):
                    draw_pixel(tip_x - i, tip_y + dy, color_inner)

    def draw_arrow_head_left(tip_x, tip_y, length, color, color_inner=None):
        for i in range(length):
            draw_pixel(tip_x + i, tip_y - i, color)
            draw_pixel(tip_x + i, tip_y + i, color)
            if color_inner:
                for dy in range(-i + 1, i):
                    draw_pixel(tip_x + i, tip_y + dy, color_inner)

    def draw_arrow_head_down(tip_x, tip_y, length, color, color_inner=None):
        for i in range(length):
            draw_pixel(tip_x - i, tip_y - i, color)
            draw_pixel(tip_x + i, tip_y - i, color)
            if color_inner:
                for dx in range(-i + 1, i):
                    draw_pixel(tip_x + dx, tip_y - i, color_inner)

    # 1. Fast Diagram (u=0, v=0, w=88, h=54)
    FAST_RED = (0xD3, 0x2F, 0x2F, 255)
    STEEL_BORDER = (0x37, 0x47, 0x4F, 255)
    STEEL_FILL = (0x78, 0x90, 0x9C, 255)
    GREEN_ABS = (0x2E, 0x7D, 0x32, 255)
    GREEN_GLOW = (0x81, 0xC7, 0x84, 255)
    THERM_BLUE = (0x19, 0x76, 0xD2, 255)
    THERM_GLOW = (0x90, 0xCA, 0xF9, 255)

    for x in range(6, 48):
        draw_pixel(x, 26, STEEL_BORDER)
        draw_pixel(x, 27, FAST_RED)
        draw_pixel(x, 28, STEEL_BORDER)
    draw_arrow_head_right(50, 27, 4, STEEL_BORDER, FAST_RED)

    for x, y in [(51, 21), (50, 20), (48, 18), (46, 16), (43, 14), (40, 13), (37, 12), (33, 12), (28, 12)]:
        draw_pixel(x, y - 1, STEEL_BORDER)
        draw_pixel(x, y, STEEL_FILL)
        draw_pixel(x, y + 1, STEEL_BORDER)
    draw_arrow_head_left(26, 12, 3, STEEL_BORDER, STEEL_FILL)

    for x, y in [(70, 21), (71, 20), (73, 18), (75, 16), (78, 14), (81, 13)]:
        draw_pixel(x, y - 1, STEEL_BORDER)
        draw_pixel(x, y, GREEN_GLOW)
        draw_pixel(x, y + 1, STEEL_BORDER)
    draw_arrow_head_right(84, 13, 3, STEEL_BORDER, GREEN_ABS)

    for x in range(60, 62):
        for y in range(36, 47):
            draw_pixel(x - 1, y, STEEL_BORDER)
            draw_pixel(x, y, THERM_BLUE)
            draw_pixel(x + 1, y, STEEL_BORDER)
    draw_arrow_head_down(60, 49, 3, STEEL_BORDER, THERM_GLOW)

    # 2. Thermal Diagram (u=0, v=60, w=88, h=54)
    t_off = 60
    for x in range(6, 48):
        draw_pixel(x, t_off + 26, STEEL_BORDER)
        draw_pixel(x, t_off + 27, THERM_BLUE)
        draw_pixel(x, t_off + 28, STEEL_BORDER)
    draw_arrow_head_right(50, t_off + 27, 4, STEEL_BORDER, THERM_BLUE)

    for x, y in [(51, 21), (50, 20), (48, 18), (46, 16), (43, 14), (40, 13), (37, 12), (33, 12), (28, 12)]:
        draw_pixel(x, t_off + y - 1, STEEL_BORDER)
        draw_pixel(x, t_off + y, STEEL_FILL)
        draw_pixel(x, t_off + y + 1, STEEL_BORDER)
    draw_arrow_head_left(26, t_off + 12, 3, STEEL_BORDER, STEEL_FILL)

    for x, y in [(70, 21), (71, 20), (73, 18), (75, 16), (78, 14), (81, 13)]:
        draw_pixel(x, t_off + y - 1, STEEL_BORDER)
        draw_pixel(x, t_off + y, GREEN_GLOW)
        draw_pixel(x, t_off + y + 1, STEEL_BORDER)
    draw_arrow_head_right(84, t_off + 13, 3, STEEL_BORDER, GREEN_ABS)

    for x in range(60, 62):
        for y in range(36, 47):
            draw_pixel(x - 1, t_off + y, STEEL_BORDER)
            draw_pixel(x, t_off + y, GREEN_ABS)
            draw_pixel(x + 1, t_off + y, STEEL_BORDER)
    draw_arrow_head_down(60, t_off + 49, 3, STEEL_BORDER, GREEN_GLOW)

    # 3. Breeder / Conversion Diagram (u=0, v=120, w=88, h=54)
    c_off = 120
    GOLD_HOT = (0xFF, 0xC1, 0x07, 255)
    GOLD_FILL = (0xFF, 0xEC, 0xB3, 255)
    GOLD_BORDER = (0xE6, 0x51, 0x00, 255)

    for x in range(6, 48):
        draw_pixel(x, c_off + 26, STEEL_BORDER)
        draw_pixel(x, c_off + 27, FAST_RED if x % 4 < 2 else THERM_BLUE)
        draw_pixel(x, c_off + 28, STEEL_BORDER)
    draw_arrow_head_right(50, c_off + 27, 4, STEEL_BORDER, GOLD_HOT)

    for x, y in [(70, 26), (73, 25), (76, 25), (79, 24), (82, 24)]:
        draw_pixel(x, c_off + y - 1, GOLD_BORDER)
        draw_pixel(x, c_off + y, GOLD_HOT)
        draw_pixel(x, c_off + y + 1, GOLD_BORDER)
    draw_arrow_head_right(84, c_off + 24, 3, GOLD_BORDER, GOLD_HOT)

    for dy in [-1, 0, 1]:
        draw_pixel(50, c_off + 15 + dy, GOLD_BORDER)
        draw_pixel(51, c_off + 15 + dy, GOLD_HOT)
        draw_pixel(52, c_off + 15 + dy, GOLD_FILL)
        draw_pixel(53, c_off + 15 + dy, GOLD_BORDER)

    # 4. Pie Charts (u=index*16, v=240, w=16, h=16) for index=0..10
    PIE_BORDER = (0x21, 0x21, 0x21, 255)
    PIE_FAST = (0xD3, 0x2F, 0x2F, 255)
    PIE_THERM = (0x19, 0x76, 0xD2, 255)

    r_outer = 6.2
    for idx in range(11):
        ox = idx * 16
        oy = 240
        cx = ox + 7.5
        cy = oy + 7.5
        fraction_therm = idx / 10.0

        for py in range(16):
            for px in range(16):
                gx = ox + px
                gy = oy + py
                dx = (gx - cx)
                dy = (gy - cy)
                dist = math.hypot(dx, dy)
                if dist <= r_outer:
                    if dist > r_outer - 1.1:
                        draw_pixel(gx, gy, PIE_BORDER)
                    else:
                        angle = math.atan2(dy, dx)
                        norm_angle = angle - (-math.pi / 2.0)
                        while norm_angle < 0:
                            norm_angle += 2.0 * math.pi
                        while norm_angle >= 2.0 * math.pi:
                            norm_angle -= 2.0 * math.pi

                        therm_limit = fraction_therm * 2.0 * math.pi
                        if norm_angle < therm_limit and fraction_therm > 0.0:
                            draw_pixel(gx, gy, PIE_THERM)
                        else:
                            draw_pixel(gx, gy, PIE_FAST)

    return w, h, grid


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
    w, h, grid = generate_atlas()
    write_png(output_dir / "neutron_interaction_atlas.png", w, h, grid)

    w, h, grid = generate_long_arrow()
    write_png(output_dir / "long_arrow.png", w, h, grid)


if __name__ == "__main__":
    main()

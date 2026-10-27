#!/usr/bin/env python3
"""
Procedural generator for Molten Corium animated fluid textures in ModularNuclear.
Generates 100% seamlessly tileable, low-contrast isotropic grit textures with
periodic thermal simmer percolation and downward flow scrolling.

Approved Configuration:
{
  "size": 16,
  "seed": 2041,
  "colorBase": "#FFC000",
  "colorDark": "#F09418",
  "colorLight": "#FFE068",
  "contrast": 38,
  "darkDensity": 10,
  "lightDensity": 29,
  "clustering": 0,
  "drift": 0,
  "frametime": 2
}

Outputs:
  - assets/modularnuclear/textures/blocks/fluids/corium_still.png (16x512, 32 frames)
  - assets/modularnuclear/textures/blocks/fluids/corium_still.png.mcmeta (frametime: 2)
  - assets/modularnuclear/textures/blocks/fluids/corium_flow.png (32x1024, 32 frames)
  - assets/modularnuclear/textures/blocks/fluids/corium_flow.png.mcmeta (frametime: 2)

Complies strictly with GTNH policy against AI-generated art assets:
Derived exclusively from pure mathematical toroidal noise, harmonic looping,
and color-space interpolation. Uses standard library only (zlib, struct, math).
"""

import math
import struct
import zlib
from pathlib import Path


def write_png(path, w, h, pixels):
    """Encodes and writes an uncompressed/deflate PNG file using pure standard library."""
    raw = bytearray()
    for row in pixels:
        raw.append(0)  # Filter type 0 (None)
        for r, g, b, a in row:
            raw.extend((r, g, b, a))
    compressed = zlib.compress(bytes(raw), 9)
    ihdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)  # 8-bit RGBA
    png = bytearray(b"\x89PNG\r\n\x1a\n")

    def chunk(ctype, data):
        png.extend(struct.pack(">I", len(data)))
        png.extend(ctype)
        png.extend(data)
        crc = zlib.crc32(ctype + data) & 0xFFFFFFFF
        png.extend(struct.pack(">I", crc))

    chunk(b"IHDR", ihdr)
    chunk(b"IDAT", compressed)
    chunk(b"IEND", b"")
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)
    print(f"  [✓] Wrote {path} ({w}x{h})")


def hex_to_rgb(hex_str):
    h = hex_str.lstrip("#")
    return tuple(int(h[i : i + 2], 16) for i in (0, 2, 4))


def lerp_rgb(c1, c2, t):
    t = max(0.0, min(1.0, t))
    return (
        int(round(c1[0] + (c2[0] - c1[0]) * t)),
        int(round(c1[1] + (c2[1] - c1[1]) * t)),
        int(round(c1[2] + (c2[2] - c1[2]) * t)),
    )


def mulberry32(seed):
    """Deterministic PRNG bit-exact with client generator."""
    state = seed & 0xFFFFFFFF

    def rng():
        nonlocal state
        state = (state + 0x6D2B79F5) & 0xFFFFFFFF
        t = state
        t = ((t ^ (t >> 15)) * (t | 1)) & 0xFFFFFFFF
        t = (t ^ (t + (((t ^ (t >> 7)) * (t | 61)) & 0xFFFFFFFF))) & 0xFFFFFFFF
        return ((t ^ (t >> 14)) & 0xFFFFFFFF) / 4294967296.0

    return rng


# Palette Configuration
C_BASE_RAW = hex_to_rgb("#FFC000")  # (255, 192, 0)
C_DARK_RAW = hex_to_rgb("#F09418")  # (240, 148, 24)
C_LIGHT_RAW = hex_to_rgb("#FFE068")  # (255, 224, 104)

CONTRAST_FACTOR = 0.38
C_BASE = (*C_BASE_RAW, 255)
C_DARK = (*lerp_rgb(C_BASE_RAW, C_DARK_RAW, CONTRAST_FACTOR), 255)  # (249, 175, 9, 255)
C_LIGHT = (*lerp_rgb(C_BASE_RAW, C_LIGHT_RAW, CONTRAST_FACTOR), 255)  # (255, 204, 40, 255)

P_DARK_RATIO = 0.10  # 10% dark amber specks
P_LIGHT_RATIO = 0.29  # 29% pale cream embers
BASE_SEED = 2041
TOTAL_FRAMES = 32


def generate_still_frames(w=16, h=16, frames_count=32, seed=2041):
    """
    Generates 32 frames of 16x16 still fluid animation using quantile ranking
    over a harmonic space-time field. Guarantees:
      1. Exactly 10% dark specks and 29% light embers in every frame.
      2. 100% periodic seamless loop (frame 31 -> frame 0).
      3. 100% toroidal boundary continuity across adjacent blocks.
    """
    rng = mulberry32(seed)
    phases = []
    harmonics = []
    for y in range(h):
        p_row = []
        h_row = []
        for x in range(w):
            p_row.append(rng() * 2.0 * math.pi)
            h_row.append(int(rng() * 3) + 1)  # 1, 2, or 3 full cycles per 32 frames
        phases.append(p_row)
        harmonics.append(h_row)

    total_pixels = w * h
    dark_count = int(round(total_pixels * P_DARK_RATIO))  # 26 pixels
    light_count = int(round(total_pixels * P_LIGHT_RATIO))  # 74 pixels

    frames = []
    for f in range(frames_count):
        theta = 2.0 * math.pi * f / frames_count
        potentials = []
        for y in range(h):
            for x in range(w):
                phi = phases[y][x]
                k = harmonics[y][x]
                # Integer harmonic guarantees f=32 matches f=0 exactly
                val = math.sin(theta * k + phi) + 0.3 * math.cos(theta * (4 - k) + phi * 1.7)
                potentials.append(((x, y), val))

        potentials.sort(key=lambda item: item[1])
        frame = [[C_BASE for _ in range(w)] for _ in range(h)]
        for i, ((x, y), _) in enumerate(potentials):
            if i < dark_count:
                frame[y][x] = C_DARK
            elif i >= total_pixels - light_count:
                frame[y][x] = C_LIGHT
            else:
                frame[y][x] = C_BASE
        frames.append(frame)
    return frames


def generate_flow_frames(still_frames, w=32, h=32, frames_count=32):
    """
    Generates 32 frames of 32x32 flowing fluid animation by tiling 2x2
    and scrolling downward along the Y axis by 1 pixel per frame.
    """
    flow_frames = []
    for f in range(frames_count):
        y_shift = f % 16
        frame32 = [[C_BASE for _ in range(w)] for _ in range(h)]
        sf = still_frames[f]
        for y in range(h):
            src_y = (y - y_shift + 16) % 16
            for x in range(w):
                src_x = x % 16
                frame32[y][x] = sf[src_y][src_x]
        flow_frames.append(frame32)
    return flow_frames


def generate_solid_corium(textures_dir):
    """
    Generates 16x16 solid Corium block texture using the exact parameters from
    the Corium Texture Studio:
    {
      "size": 16,
      "seed": 2001,
      "colorBase": "#000000",
      "colorDark": "#BC00FD",
      "colorLight": "#5E30EB",
      "contrast": 56,
      "darkDensity": 15,
      "lightDensity": 46,
      "clustering": 0,
      "drift": 0
    }
    """
    w = 16
    h = 16
    rng = mulberry32(2001)

    # Permutation table (consumes 255 rng steps)
    perm = list(range(256))
    for i in range(255, 0, -1):
        j = int(rng() * (i + 1))
        perm[i], perm[j] = perm[j], perm[i]

    c_base = hex_to_rgb("#000000")
    c_dark_raw = hex_to_rgb("#BC00FD")
    c_light_raw = hex_to_rgb("#5E30EB")

    contrast_factor = 56 / 100.0
    c_dark = lerp_rgb(c_base, c_dark_raw, contrast_factor)
    c_light = lerp_rgb(c_base, c_light_raw, contrast_factor)

    grid = [[c_base for _ in range(w)] for _ in range(h)]

    p_dark = 15 / 100.0
    p_light = 46 / 100.0

    for y in range(h):
        for x in range(w):
            r = rng()
            if r < p_dark:
                grid[y][x] = c_dark
            elif r < p_dark + p_light:
                grid[y][x] = c_light
            else:
                if rng() < 0.25:
                    tint = c_dark if rng() < 0.5 else c_light
                    grid[y][x] = lerp_rgb(c_base, tint, 0.15 * contrast_factor)

    pixels = []
    for y in range(h):
        row = []
        for x in range(w):
            r, g, b = grid[y][x]
            row.append((r, g, b, 255))
        pixels.append(row)

    out_file = textures_dir / "blocks" / "corium_solid.png"
    write_png(out_file, w, h, pixels)


def generate_corium_dust_and_rod(textures_dir):
    """
    Generates Corium Dust and Corium Rod textures using the standard GregTech
    nuclear material icon templates tinted with the Corium color palette (#BC00FD).
    """
    import shutil
    import subprocess

    gt_icons = (
        textures_dir.parents[6]
        / "GT5-Unofficial/src/main/resources/assets/gregtech/textures/items/materialicons"
    )
    if not gt_icons.exists():
        return

    magick_bin = shutil.which("magick") or shutil.which("convert")
    if not magick_bin:
        return

    dust_out = textures_dir / "items" / "corium_dust.png"
    rod_out = textures_dir / "items" / "corium_rod.png"
    dust_out.parent.mkdir(parents=True, exist_ok=True)

    # 1. Corium Dust: NUCLEAR/dust.png tinted with #BC00FD + dust_OVERLAY.png
    cmd_dust = [
        magick_bin,
        str(gt_icons / "NUCLEAR/dust.png"),
        "-colorspace",
        "RGB",
        "(",
        "+clone",
        "-fill",
        "#BC00FD",
        "-colorize",
        "100%",
        ")",
        "-compose",
        "Multiply",
        "-composite",
        str(gt_icons / "NUCLEAR/dust_OVERLAY.png"),
        "-compose",
        "Over",
        "-composite",
        # No date chunks, so a rebuild writes the same bytes
        "-define",
        "png:exclude-chunks=date,time",
        str(dust_out),
    ]
    subprocess.run(cmd_dust, check=True)
    print(f"  [✓] Wrote {dust_out} (16x16)")

    # 2. Corium Rod: NUCLEAR/stick.png tinted with #BC00FD + stick_OVERLAY.png
    cmd_rod = [
        magick_bin,
        str(gt_icons / "NUCLEAR/stick.png"),
        "-colorspace",
        "RGB",
        "(",
        "+clone",
        "-fill",
        "#BC00FD",
        "-colorize",
        "100%",
        ")",
        "-compose",
        "Multiply",
        "-composite",
        str(gt_icons / "NUCLEAR/stick_OVERLAY.png"),
        "-compose",
        "Over",
        "-composite",
        # No date chunks, so a rebuild writes the same bytes
        "-define",
        "png:exclude-chunks=date,time",
        str(rod_out),
    ]
    subprocess.run(cmd_rod, check=True)
    print(f"  [✓] Wrote {rod_out} (16x16)")


def main(output_dir=None):
    if output_dir is None:
        textures_dir = (
            Path(__file__).resolve().parent.parent.parent
            / "src/main/resources/assets/modularnuclear/textures"
        )
        fluids_dir = textures_dir / "blocks/fluids"
    else:
        fluids_dir = Path(output_dir)
        textures_dir = fluids_dir.parent.parent

    print("Generating Molten Corium Procedural Textures (Low-Contrast Grit Palette):")

    # 1. Still: 16x512 (32 frames of 16x16)
    still_frames = generate_still_frames(w=16, h=16, frames_count=TOTAL_FRAMES, seed=BASE_SEED)
    grid_still = []
    for f in range(TOTAL_FRAMES):
        for y in range(16):
            grid_still.append(still_frames[f][y])

    write_png(fluids_dir / "corium_still.png", 16, 16 * TOTAL_FRAMES, grid_still)

    # 2. Flow: 32x1024 (32 frames of 32x32 with downward flow shift)
    flow_frames = generate_flow_frames(still_frames, w=32, h=32, frames_count=TOTAL_FRAMES)
    grid_flow = []
    for f in range(TOTAL_FRAMES):
        for y in range(32):
            grid_flow.append(flow_frames[f][y])

    write_png(fluids_dir / "corium_flow.png", 32, 32 * TOTAL_FRAMES, grid_flow)

    # 3. .mcmeta files with frametime 2 (10 FPS / 100ms per frame)
    mcmeta_content = '{\n  "animation": {\n    "frametime": 2\n  }\n}\n'
    (fluids_dir / "corium_still.png.mcmeta").write_text(mcmeta_content)
    (fluids_dir / "corium_flow.png.mcmeta").write_text(mcmeta_content)
    print("  [✓] Wrote mcmeta configs (frametime: 2)")

    # 4. Solid Corium Block Texture
    generate_solid_corium(textures_dir)

    # 5. Corium Dust & Corium Rod Item Textures
    generate_corium_dust_and_rod(textures_dir)


if __name__ == "__main__":
    main()

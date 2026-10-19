#!/usr/bin/env python3
"""
100% Pure Procedural Generator for Nuclear Water Fluid Textures:
  - heavywater_still.png / flow.png

Complies strictly with GTNH policy against AI-generated art assets:
Synthesizes all fluid animation strips from scratch using harmonic 2D wave equations,
periodic spatial wrapping (seamlessly tileable), and temporal phase wrapping (seamless loop).
Zero third-party files or baseline textures are used.
"""

import json
import math
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
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
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


def eval_wave(u, v, tau, is_flow=False):
    """
    Spatially periodic on [0, 1) x [0, 1) and temporally periodic on tau in [0, 2*pi).
    Guarantees 100% seamless tileability and seamless animation loop.
    """
    pi2 = 2.0 * math.pi
    if is_flow:
        # Downward advection
        v_eff = (v - tau / pi2) % 1.0
        w1 = math.sin(pi2 * (u + 2.0 * v_eff) - tau)
        w2 = math.cos(pi2 * (2.0 * u - v_eff) + tau)
        w3 = math.sin(pi2 * (3.0 * u + v_eff) - 2.0 * tau)
        w4 = math.cos(pi2 * (u - 3.0 * v_eff) + 2.0 * tau)
    else:
        # Radial / harmonic surface ripples
        w1 = math.sin(pi2 * (2.0 * u + v) - tau)
        w2 = math.sin(pi2 * (u - 2.0 * v) + tau)
        w3 = math.sin(pi2 * (3.0 * u - v) - 2.0 * tau)
        w4 = math.cos(pi2 * (u + 3.0 * v) + 2.0 * tau)

    return (w1 + 0.7 * w2 + 0.5 * w3 + 0.3 * w4) / 2.5  # in [-1, 1]


def generate_fluid_strip(width, frame_height, num_frames, deep_rgb, crest_rgb, alpha, is_flow=False):
    total_height = frame_height * num_frames
    pixels = []

    dr, dg, db = deep_rgb
    cr, cg, cb = crest_rgb

    for frame in range(num_frames):
        tau = 2.0 * math.pi * (frame / float(num_frames))
        for fy in range(frame_height):
            v = fy / float(frame_height)
            for fx in range(width):
                u = fx / float(width)

                w = eval_wave(u, v, tau, is_flow)  # in [-1, 1]
                t = (w + 1.0) * 0.5  # in [0, 1]

                # Fine spatial grain noise for authentic Minecraft pixel texture
                noise = (((fx * 7 + fy * 13 + frame * 3) % 7) - 3) * 2

                # Interpolate between deep base and bright crest
                r = int(dr + t * (cr - dr)) + noise
                g = int(dg + t * (cg - dg)) + noise
                b = int(db + t * (cb - db)) + noise

                # Highlight caustic peaks
                if t > 0.82:
                    glint = int((t - 0.82) * 5.5 * 35)
                    r += glint; g += glint; b += glint

                pixels.append((r, g, b, alpha))

    return pixels


def generate_fluid_set(out_dir, fluid_name, deep_rgb, crest_rgb, alpha):
    num_frames = 32
    # 1. Still: 16x512
    still_pixels = generate_fluid_strip(16, 16, num_frames, deep_rgb, crest_rgb, alpha, is_flow=False)
    still_path = out_dir / f"{fluid_name}_still.png"
    write_png_rgba(16, 16 * num_frames, still_pixels, still_path)

    # 2. Flow: 32x1024
    flow_pixels = generate_fluid_strip(32, 32, num_frames, deep_rgb, crest_rgb, alpha, is_flow=True)
    flow_path = out_dir / f"{fluid_name}_flow.png"
    write_png_rgba(32, 32 * num_frames, flow_pixels, flow_path)

    # 3. .mcmeta files
    mcmeta_content = json.dumps({"animation": {"frametime": 2}}, indent=2) + "\n"
    (out_dir / f"{fluid_name}_still.png.mcmeta").write_text(mcmeta_content)
    (out_dir / f"{fluid_name}_flow.png.mcmeta").write_text(mcmeta_content)

    print(f"  [✓] Wrote {fluid_name}_still.png (16x512, 32 frames) + flow.png (32x1024)")


def main(output_dir=None):
    script_dir = Path(__file__).resolve().parent
    if output_dir is None:
        output_dir = script_dir.parent.parent / "src/main/resources/assets/modularnuclear/textures/blocks/fluids"
    else:
        output_dir = Path(output_dir)

    output_dir.mkdir(parents=True, exist_ok=True)
    print("Generating Water Fluid Textures (Harmonic Wave Synthesis):")

    fluids = [
        # Heavy Water: Deep royal cobalt/indigo with bright surface ripples
        ("heavywater", (20, 48, 96), (65, 125, 205), 215),
    ]

    for name, deep_rgb, crest_rgb, alpha in fluids:
        generate_fluid_set(output_dir, name, deep_rgb, crest_rgb, alpha)


if __name__ == "__main__":
    main()

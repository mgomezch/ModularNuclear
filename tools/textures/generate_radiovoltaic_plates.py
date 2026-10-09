#!/usr/bin/env python3
"""
Procedural generator for Radiovoltaic Plate item textures:
  - assets/modularnuclear/textures/items/gt.radiovoltaic.plate.hv.png (HV - Nickel/Gold tint)
  - assets/modularnuclear/textures/items/gt.radiovoltaic.plate.ev.png (EV - Graphite/Cyan tint)

Transforms the base GregTech neutron reflector plate palette via deterministic
channel multipliers on the indexed PNG palette (PLTE).
"""

import struct
import zlib
from pathlib import Path


def tint_indexed_png(src, dst, r_mul, g_mul, b_mul):
    with open(src, "rb") as f:
        data = f.read()

    pos = 8
    chunks = []
    while pos < len(data):
        length = struct.unpack(">I", data[pos : pos + 4])[0]
        ctype = data[pos + 4 : pos + 8]
        cdata = data[pos + 8 : pos + 8 + length]
        pos += 12 + length
        if ctype == b"PLTE":
            plte = bytearray(cdata)
            for i in range(0, len(plte), 3):
                plte[i] = min(255, int(plte[i] * r_mul))
                plte[i + 1] = min(255, int(plte[i + 1] * g_mul))
                plte[i + 2] = min(255, int(plte[i + 2] * b_mul))
            cdata = bytes(plte)
        chunks.append((ctype, cdata))

    out = b"\x89PNG\r\n\x1a\n"
    for ctype, cdata in chunks:
        crc = zlib.crc32(ctype + cdata) & 0xFFFFFFFF
        out += struct.pack(">I", len(cdata)) + ctype + cdata + struct.pack(">I", crc)

    with open(dst, "wb") as f:
        f.write(out)


def main(baseline_dir=None, output_dir=None):
    script_dir = Path(__file__).resolve().parent
    if baseline_dir is None:
        baseline_dir = script_dir / "baseline"
    else:
        baseline_dir = Path(baseline_dir)

    if output_dir is None:
        output_dir = script_dir.parent.parent / "src/main/resources/assets/modularnuclear/textures/items"
    else:
        output_dir = Path(output_dir)

    reflector_src = baseline_dir / "gt.neutronreflector.png"
    if not reflector_src.exists():
        raise FileNotFoundError(f"Baseline gt.neutronreflector.png not found in {baseline_dir}")

    output_dir.mkdir(parents=True, exist_ok=True)
    print("Generating Radiovoltaic Plate Textures:")

    # HV: Nickel / warm golden-amber tint (1.4, 1.2, 0.5)
    hv_dest = output_dir / "gt.radiovoltaic.plate.hv.png"
    tint_indexed_png(reflector_src, hv_dest, 1.4, 1.2, 0.5)
    print(f"  [✓] Wrote {hv_dest.name} (HV Nickel tint)")

    # EV: Graphite / deep cyan tint (0.4, 0.9, 1.4)
    ev_dest = output_dir / "gt.radiovoltaic.plate.ev.png"
    tint_indexed_png(reflector_src, ev_dest, 0.4, 0.9, 1.4)
    print(f"  [✓] Wrote {ev_dest.name} (EV Graphite tint)")


if __name__ == "__main__":
    main()

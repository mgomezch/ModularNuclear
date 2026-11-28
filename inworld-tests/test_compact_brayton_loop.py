#!/usr/bin/env python3
"""
In-world test of a compact helium reverse-Brayton loop at MV (Note [Compact loop machines], Note [The compact heat
exchanger]):

    compact pump (an Iron Tank of helium) -> compressor -> radiator -> recuperator 1, first pass
        -> recuperator 2, first pass -> turbo-expander -> liquefier (nitrogen gas in)
        -> recuperator 2, second pass -> recuperator 1, second pass -> pump

all one-block machines on medium steel coolant pipe.  The recuperators are Compact Heat Exchangers with the loop
through them twice: across their front going out, and from bottom to top coming back.  Test phase: the loop forms
with both passes of both recuperators, charges, runs in two pressure zones; the expander puts out EU; the
recuperators exchange heat; and the expander's outlet goes far below ambient.  It reports any liquid nitrogen made.

    python3 inworld-tests/test_compact_brayton_loop.py --port 25571
"""
import argparse
import json
import re
import sys
import time

from nhdev import NH_DEV_ROOT as REPO_ROOT  # noqa: F401 - puts the workspace on sys.path

from tools.agentharness import HarnessClient
from tools.bot import compact_loop_builder as C
from tools.bot import gas_loop_builder as G
from tools.inworld_test_driver import InWorldTestDriver

TAG = "compact_brayton_loop"
Y = 8
TIER = "MV"
FLUID = "helium"
PUMP, COMP, RAD, X1, X2, EXP, LIQ = (21, Y, 0), (18, Y, 0), (15, Y, 0), (12, Y, 0), (9, Y, 0), (6, Y, 0), (3, Y, 0)
OUTPUT = REPO_ROOT / "output" / "compact_brayton_loop.json"


def build(h: HarnessClient):
    h.clear_area(-12, Y - 4, -12, 34, Y + 10, 12)
    h.fill_area(-10, Y - 4, -10, 32, Y - 4, 10, "stone", 0)
    pump = C.place_pump(h, TIER, PUMP, "iron", 2, FLUID, 60_000)
    comp, rad = C.place(h, "compressor", TIER, COMP), C.place(h, "radiator", TIER, RAD)
    x1, x2 = C.place(h, "exchanger", TIER, X1, powered=False), C.place(h, "exchanger", TIER, X2, powered=False)
    exp, liq = C.place(h, "expander", TIER, EXP, powered=False), C.place(h, "exchanger", TIER, LIQ, powered=False)
    below = lambda m: (m["pos"][0], Y - 1, m["pos"][2])  # noqa: E731
    above = lambda m: (m["pos"][0], Y + 1, m["pos"][2])  # noqa: E731
    legs = [
        (pump["outlet"], comp["inlet"], "EAST", "WEST"),
        (comp["outlet"], rad["inlet"], "EAST", "WEST"),
        (rad["outlet"], x1["inlet"], "EAST", "WEST"),
        (x1["outlet"], x2["inlet"], "EAST", "WEST"),
        (x2["outlet"], exp["inlet"], "EAST", "WEST"),
        (exp["outlet"], liq["inlet"], "EAST", "WEST"),
        # Back through the recuperators' second passes, bottom to top, the colder one first
        (liq["outlet"], below(x2), "EAST", "UP"),
        (above(x2), below(x1), "DOWN", "UP"),
        (above(x1), pump["inlet"], "DOWN", "WEST"),
    ]
    boxes = [m["box"] for m in (pump, comp, rad, x1, x2, exp, liq)]
    G.route_legs(h, C.pipe("STEEL", "NORMAL"), legs, boxes, Y)
    print("[+] Built compact pump, compressor, radiator, two recuperators, expander and liquefier")


def zone_line(status: str):
    m = re.search(r"(\d+) pressure zones, ([\d.]+) to ([\d.]+) bar, (-?[\d.]+) to (-?[\d.]+)", status or "")
    return tuple(float(g) for g in m.groups()) if m else None


def test(h: HarnessClient):
    h.stand(12, Y + 6, -11, 0.0, 30.0)
    time.sleep(2.0)
    print("    pump:", C.readout(h, PUMP))
    # A GregTech machine takes fluid only once it ticks, so not while the world is built
    print("    nitrogen:", h.fill_fluid(*LIQ, "nitrogen", 8000))
    report = {"samples": []}
    t0 = time.time()
    ran_at = None
    zones = None
    coldest = 1000.0
    peak_out = 0
    exchanged = False
    while time.time() - t0 < 420:
        time.sleep(5.0)
        st = h.coolant_status(*PUMP)
        sample = {"t": round(time.time() - t0), "state": st.get("loop_state"), "status": st.get("loop_status")}
        for name, pos in (("compressor", COMP), ("recuperator1", X1), ("recuperator2", X2), ("expander", EXP),
                          ("liquefier", LIQ)):
            sample[name] = C.readout(h, pos)
        report["samples"].append(sample)
        print(f"[{sample['t']:4d}s] {sample['state']} {sample['status']}")
        for name in ("compressor", "recuperator1", "recuperator2", "expander", "liquefier"):
            print(f"        {name:12s} {sample[name]}")
        assert st.get("loop_state") not in ("FAILED", None), f"Loop failed (or its pump is gone): {st}"
        z = zone_line(st.get("loop_status"))
        if z:
            zones = z
            ran_at = ran_at or time.time()
            coldest = min(coldest, z[3])
        m = re.search(r"(\d+) EU/t out", " ".join(sample["expander"]))
        peak_out = max(peak_out, int(m.group(1)) if m else 0)
        exchanged = exchanged or "Exchanging between its two passes" in " ".join(sample["recuperator1"])
        assert ran_at or time.time() - t0 < 120, f"The loop did not start within two minutes: {st}"
        if ran_at and time.time() - ran_at > 240:
            break

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(report, indent=2))
    assert zones, f"The loop never ran: {h.coolant_status(*PUMP)}"
    count, low, high, tmin, tmax = zones
    made = " ".join(C.readout(h, LIQ))
    print(f"[*] {count:.0f} zones, {low:.2f} / {high:.2f} bar, coldest {coldest:.1f} °C, expander peak {peak_out} "
          f"EU/t out; liquefier: {made}")
    assert count == 2, f"Expected two pressure zones: {zones}"
    assert exchanged, "The first recuperator never exchanged between its passes"
    assert peak_out > 0, "The expander put out no EU"
    assert coldest < -100.0, f"Expected the expander's outlet far below ambient, got {coldest} °C"
    h.screenshot(f"screenshots/{TAG}.png")
    print("[✓] Compact Brayton loop ran cold through its recuperators")


def run_tests(port: int = 25571):
    driver = InWorldTestDriver(tag=TAG, port=port, no_mobs=True, base_template="superflat_empty")
    return driver.run(build_fn=build, test_fn=test)


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--port", type=int, default=25571)
    a = ap.parse_args()
    sys.exit(0 if run_tests(port=a.port) else 1)

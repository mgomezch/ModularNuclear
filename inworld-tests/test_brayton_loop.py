#!/usr/bin/env python3
"""
In-world test of a helium reverse-Brayton loop that liquefies nitrogen (Note [Gas loop pressure zones], Note [PHE
gas modes]):

    pump -> gas compressor -> radiator (aftercooler) -> PHE 1 primary (recuperator, hot side) -> turbo-expander
         -> PHE 2 primary (liquefier: nitrogen gas in, liquid nitrogen out) -> PHE 1 second pair (recuperator,
         cold side) -> pump

all on medium steel coolant pipe.  PHE 1 runs in dual-loop mode with both of its hatch pairs on this one loop.
Test phase: the loop forms, charges with helium, runs, the expander's outlet goes below nitrogen's boiling point
less the PHE's approach, the expander puts out EU, and PHE 2 makes liquid nitrogen.

    python3 inworld-tests/test_brayton_loop.py --port 25571
"""
import argparse
import json
import re
import sys
import time

from nhdev import NH_DEV_ROOT as REPO_ROOT  # noqa: F401 - puts the workspace on sys.path

from tools.agentharness import HarnessClient
from tools.bot import build_modular_nuclear_showcase_world as S
from tools.bot import gas_loop_builder as G
from tools.bot import mptr_loop_builder as LB
from tools.inworld_test_driver import InWorldTestDriver

TAG = "brayton_loop"
Y = 5
PIPE = LB.pipe_id("STEEL", "NORMAL")
Z0 = 0  # the demo world builds it further south
PUMP = (0, Y, Z0)
COMP_CX, RAD_CX, PHE1_X, EXP_CX, PHE2_X = 8, 14, 20, 28, 34
PHE1_LEN, PHE2_LEN = 11, 5
FLUID = "helium"
ID_OUTPUT_HATCH_HV = 63
OUTPUT = REPO_ROOT / "output" / "brayton_loop.json"


def build_phe(h, x: int, sheetmetal: str, length: int, energy_tier: str = "EV"):
    """A 3-high PHE at (x, Y, Z0) along +z, switched on, with an energy hatch; returns its centre layer and back z."""
    h.build_multiblock("phe", x, Y, Z0, sheetmetal, 3, length)
    cy, back = Y + 1, Z0 + length - 1
    h.set_gt_machine(x + 1, Y, back, 40 + LB.TIERS.index(energy_tier), 3)
    h.set_cover(x + 1, Y, back, 3, "Cover_Wireless_Energy_Debug")
    h.charge_energy(x + 1, Y, back, 10_000_000_000)
    h.fix_maintenance(x + 1, cy, Z0)
    return cy, back


def build(h: HarnessClient):
    h.clear_area(-10, Y - 1, Z0 - 10, 45, Y + 12, Z0 + 20)
    h.fill_area(-8, Y - 1, Z0 - 8, 44, Y - 1, Z0 + 18, "stone", 0)
    pump = LB.build_pump(h, *PUMP, "STEEL", 512, FLUID, 200_000)
    comp = G.build_compressor(h, COMP_CX, Y, Z0, 3)
    rad = G.build_radiator(h, RAD_CX, Y, Z0, 5)
    exp = G.build_expander(h, EXP_CX, Y, Z0, 3, dynamo_tier="IV")
    # PHE 1: the recuperator, its second pair of high-pressure hatches in place of its secondary hatches
    cy, back1 = build_phe(h, PHE1_X, "blackplutonium", PHE1_LEN)
    S.set_fresh_gt_machine(h, PHE1_X + 2, cy, Z0, S.ID_PRESSURIZED_INPUT, 5)
    S.set_fresh_gt_machine(h, PHE1_X + 2, cy, back1, S.ID_PRESSURIZED_OUTPUT, 3)
    # PHE 2: the liquefier, nitrogen gas from a debug tank, its liquid into an output hatch
    cy, back2 = build_phe(h, PHE2_X, "osmium", PHE2_LEN)
    h.set_gt_machine(PHE2_X + 3, cy, Z0, S.ID_DEBUG_TANK, 4)
    h.configure_debug_tank(PHE2_X + 3, cy, Z0, "nitrogen", True)
    S.set_fresh_gt_machine(h, PHE2_X + 2, cy, back2, ID_OUTPUT_HATCH_HV, 5)
    boxes = [[0, Y, Z0, 2, Y + 6, Z0 + 2], comp["box"], rad["box"], exp["box"],
             [PHE1_X, Y, Z0, PHE1_X + 2, Y + 2, back1], [PHE2_X, Y, Z0, PHE2_X + 3, Y + 2, back2]]
    legs = [
        (pump["outlet"], comp["inlet"], "WEST", "SOUTH"),
        (comp["outlet"], rad["inlet"], "NORTH", "SOUTH"),
        (rad["outlet"], (PHE1_X - 1, cy, Z0), "NORTH", "EAST"),
        ((PHE1_X, cy, back1 + 1), exp["inlet"], "NORTH", "SOUTH"),
        (exp["outlet"], (PHE2_X - 1, cy, Z0), "NORTH", "EAST"),
        ((PHE2_X, cy, back2 + 1), (PHE1_X + 3, cy, Z0), "NORTH", "WEST"),
        ((PHE1_X + 2, cy, back1 + 1), pump["inlet"], "NORTH", "EAST"),
    ]
    G.route_legs(h, PIPE, legs, boxes, Y + 1)
    print("[+] Built pump, compressor, radiator, recuperator, expander, liquefier")


def zone_line(status: str):
    m = re.search(r"(\d+) pressure zones, ([\d.]+) to ([\d.]+) bar, (-?[\d.]+) to (-?[\d.]+)", status or "")
    return tuple(float(g) for g in m.groups()) if m else None


def test(h: HarnessClient):
    ctrl = (PUMP[0] + 1, PUMP[1], PUMP[2])
    comp_c, rad_c, exp_c = (COMP_CX, Y, Z0), (RAD_CX, Y, Z0), (EXP_CX, Y, Z0)
    phe1_c, phe2_c = (PHE1_X + 1, Y + 1, Z0), (PHE2_X + 1, Y + 1, Z0)
    out_hatch = (PHE2_X + 2, Y + 1, Z0 + PHE2_LEN - 1)
    h.stand(18, Y + 10, Z0 - 14, 0.0, 35.0)
    time.sleep(1.0)
    for c in (comp_c, rad_c, exp_c, phe1_c, phe2_c, ctrl):
        h.recheck(*c)
    for c in (phe1_c, phe2_c):
        LB.set_working(h, c, True)
    time.sleep(1.0)
    for name, c in (("compressor", comp_c), ("radiator", rad_c), ("expander", exp_c), ("recuperator", phe1_c),
                    ("liquefier", phe2_c)):
        info = G.info_lines(h, c)
        print(f"    {name}: formed={info.get('formed')} {info.get('info')}")
        assert info.get("formed"), f"{name} did not form: {info}"
    print("    crawl:", h.crawl_loop(*ctrl))

    report = {"samples": []}
    t0 = time.time()
    running_since = None
    liquid = 0
    while time.time() - t0 < 900:
        time.sleep(5.0)
        st = h.coolant_status(*ctrl)
        z = zone_line(st.get("loop_status"))
        sample = {"t": round(time.time() - t0), "state": st.get("loop_state"), "status": st.get("loop_status")}
        for name, c in (("compressor", comp_c), ("expander", exp_c), ("recuperator", phe1_c),
                        ("liquefier", phe2_c), ("output", out_hatch)):
            sample[name] = G.info_lines(h, c).get("info")
        report["samples"].append(sample)
        print(f"[{sample['t']:4d}s] {sample['state']} {sample['status']}")
        for name in ("compressor", "expander", "recuperator", "liquefier", "output"):
            print(f"        {name:11s} {sample[name]}")
        assert st.get("loop_state") not in ("FAILED", None), f"Loop failed (or its pump is gone): {st}"
        text = " ".join(sample["output"] or [])
        m = re.search(r"([\d,]+) L", text)
        liquid = int(m.group(1).replace(",", "")) if m and "itrogen" in text else liquid
        if z and running_since is None:
            running_since = time.time()
        if running_since and (liquid > 0 and time.time() - running_since > 120 or time.time() - running_since > 480):
            break

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(report, indent=2))
    st = h.coolant_status(*ctrl)
    z = zone_line(st.get("loop_status"))
    assert z, f"The loop never ran: {st}"
    zones, low, high, tmin, tmax = z
    print(f"[*] {zones:.0f} zones, {low:.2f} / {high:.2f} bar, {tmin:.1f} to {tmax:.1f} °C, liquid nitrogen {liquid} L")
    assert zones == 2, f"Expected two pressure zones: {st}"
    assert tmin < -201.0, f"Expected the expander's outlet below nitrogen's boiling point less the approach: {st}"
    assert liquid > 0, "The liquefier made no liquid nitrogen"
    h.screenshot(f"screenshots/{TAG}.png")
    print("[✓] Brayton loop liquefying nitrogen")


def run_tests(port: int = 25571):
    driver = InWorldTestDriver(tag=TAG, port=port, no_mobs=True, base_template="superflat_empty")
    return driver.run(build_fn=build, test_fn=test)


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--port", type=int, default=25571)
    a = ap.parse_args()
    sys.exit(0 if run_tests(port=a.port) else 1)

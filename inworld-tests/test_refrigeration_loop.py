#!/usr/bin/env python3
"""
In-world test of an R-134a refrigeration loop (Note [Gas loop pressure zones], Note [Gas loop machines]):

    pump (receiver) -> expansion valve -> coolant bath (evaporator) -> gas compressor -> radiator (condenser) -> pump

all on medium steel coolant pipe.  The bath is fed hot tungstensteel ingots.  Build phase (ticks frozen): the pump
with a steel tank of R-134a, the four machines, the pipes.  Test phase: the loop forms, charges, runs, settles into
two pressure zones (the low side evaporating below 0 °C, the high side condensing above ambient), the compressor
draws EU and the radiator rejects heat, and the bath turns hot ingots into cold ones.

    python3 inworld-tests/test_refrigeration_loop.py --port 25571
"""
import argparse
import json
import re
import sys
import time

from nhdev import NH_DEV_ROOT as REPO_ROOT  # noqa: F401 - puts the workspace on sys.path

from tools.agentharness import HarnessClient
from tools.bot import gas_loop_builder as G
from tools.bot import mptr_loop_builder as LB
from tools.bot.build_coolant_bath_testcase import build_bath, fill_bus
from tools.inworld_test_driver import InWorldTestDriver

TAG = "refrigeration_loop"
Y = 5
PIPE = LB.pipe_id("STEEL", "NORMAL")
PUMP = (0, Y, 0)
VALVE_CX, BATH_X0, COMP_CX, RAD_CX = 8, 13, 22, 28
COMP_LEN, RAD_LEN = 5, 9
FLUID = "r134a"
INGOTS = [("ore:ingotHotTungstenSteel", 1, 0) for _ in range(6)]
OUTPUT = REPO_ROOT / "output" / "refrigeration_loop.json"


def build(h: HarnessClient):
    h.clear_area(-10, Y - 1, -10, 45, Y + 12, 20)
    h.fill_area(-8, Y - 1, -8, 44, Y - 1, 16, "stone", 0)
    pump = LB.build_pump(h, *PUMP, "STEEL", 512, FLUID, 200_000)
    valve = G.build_valve(h, VALVE_CX, Y, 0, 5, "titanium")
    bath = build_bath(h, BATH_X0, 0, "5x7", "EV")
    comp = G.build_compressor(h, COMP_CX, Y, 0, COMP_LEN)
    rad = G.build_radiator(h, RAD_CX, Y, 0, RAD_LEN)
    avoid = [[0, Y, 0, 2, Y + 6, 2], valve["box"], bath["box"], comp["box"], rad["box"]]
    legs = [
        (pump["outlet"], valve["inlet"], "WEST", "SOUTH"),
        (valve["outlet"], bath["inlet"], "NORTH", "SOUTH"),
        (bath["outlet"], comp["inlet"], "NORTH", "SOUTH"),
        (comp["outlet"], rad["inlet"], "NORTH", "SOUTH"),
        (rad["outlet"], pump["inlet"], "NORTH", "EAST"),
    ]
    G.route_legs(h, PIPE, legs, avoid, Y + 1)
    print("[+] Built pump, valve, bath, compressor, radiator")


def pump_status(h):
    return h.coolant_status(PUMP[0] + 1, PUMP[1], PUMP[2])


def zone_pressures(status: str):
    """(zones, low bar, high bar, min °C, max °C) from the pump's running status line, or None."""
    m = re.search(r"(\d+) pressure zones, ([\d.]+) to ([\d.]+) bar, (-?[\d.]+) to (-?[\d.]+)", status or "")
    return tuple(float(g) for g in m.groups()) if m else None


def test(h: HarnessClient):
    ctrl = (PUMP[0] + 1, PUMP[1], PUMP[2])
    valve_c, comp_c, rad_c = (VALVE_CX, Y, 0), (COMP_CX, Y, 0), (RAD_CX, Y, 0)
    bath_c = (BATH_X0 + 2, Y, 0)
    h.stand(14, Y + 8, -12, 0.0, 30.0)
    time.sleep(1.0)
    for c in (valve_c, bath_c, comp_c, rad_c, ctrl):
        h.recheck(*c)
    time.sleep(1.0)
    for name, c in (("valve", valve_c), ("compressor", comp_c), ("radiator", rad_c)):
        info = G.info_lines(h, c)
        print(f"    {name}: formed={info.get('formed')} {info.get('info')}")
        assert info.get("formed"), f"{name} did not form: {info}"
    fill_bus(h, (BATH_X0 + 1, Y, 0), INGOTS)
    print("    crawl:", h.crawl_loop(*ctrl))

    report = {"samples": []}
    t0 = time.time()
    running_since = None
    while time.time() - t0 < 600:
        time.sleep(5.0)
        st = pump_status(h)
        z = zone_pressures(st.get("loop_status"))
        comp = G.info_lines(h, comp_c)
        rad = G.info_lines(h, rad_c)
        valve = G.info_lines(h, valve_c)
        slots = h.bath_slots(*bath_c)
        sample = {"t": round(time.time() - t0), "state": st.get("loop_state"), "status": st.get("loop_status"),
                  "fill": st.get("fill_liters"), "required": st.get("required_fill_liters"),
                  "compressor": comp.get("info"), "radiator": rad.get("info"), "valve": valve.get("info"),
                  "bath_slots": slots.get("slots") if isinstance(slots, dict) else slots}
        report["samples"].append(sample)
        print(f"[{sample['t']:4d}s] {sample['state']} {sample['status']}")
        print(f"        compressor {comp.get('info')}")
        print(f"        radiator   {rad.get('info')}")
        print(f"        valve      {valve.get('info')}")
        assert st.get("loop_state") not in ("FAILED", None), f"Loop failed (or its pump is gone): {st}"
        if z and running_since is None:
            running_since = time.time()
        if running_since and time.time() - running_since > 180:
            break

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(report, indent=2))
    st = pump_status(h)
    z = zone_pressures(st.get("loop_status"))
    assert z, f"The loop never ran: {st}"
    zones, low, high, tmin, tmax = z
    print(f"[*] {zones:.0f} zones, {low:.2f} / {high:.2f} bar, {tmin:.1f} to {tmax:.1f} °C")
    assert zones == 2, f"Expected two pressure zones: {st}"
    assert high > 2.0 * low, f"Expected a compressor lift: {st}"
    assert tmin < 10.0, f"Expected the low side cold: {st}"
    out = h.bath_slots(*bath_c)
    print("[*] bath:", json.dumps(out)[:2000])
    h.screenshot(f"screenshots/{TAG}.png")
    print("[✓] Refrigeration loop running")


def run_tests(port: int = 25571):
    driver = InWorldTestDriver(tag=TAG, port=port, no_mobs=True, base_template="superflat_empty")
    return driver.run(build_fn=build, test_fn=test)


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--port", type=int, default=25571)
    a = ap.parse_args()
    sys.exit(0 if run_tests(port=a.port) else 1)

#!/usr/bin/env python3
"""
In-world test of a compact R-134a refrigeration loop at MV (Note [Compact loop machines]):

    compact pump (three Iron Tanks of R-134a) -> compact gas compressor -> compact radiator
        -> compact expansion valve -> compact coolant bath -> pump

all on medium steel coolant pipe, every machine one block.  Test phase: the pump finds its tanks and its loop,
charges it and runs it in two pressure zones; the compressor draws EU within its tier; the bath turns hot Kanthal
ingots into Kanthal ingots; and the pump, stopped, takes the charge back into its tanks.

    python3 inworld-tests/test_compact_refrigeration_loop.py --port 25571
"""
import argparse
import json
import re
import sys
import time

from nhdev import NH_DEV_ROOT as REPO_ROOT  # noqa: F401 - puts the workspace on sys.path

from tools.agentharness import HarnessClient
from tools.bot import compact_loop_builder as C
from tools.inworld_test_driver import InWorldTestDriver

TAG = "compact_refrigeration_loop"
Y = 5
TIER = "MV"
FLUID = "r134a"
CHARGE_L = 90_000
TANK = ("iron", 3)
INGOT = "ore:ingotHotKanthal"
PUMP, COMP, RAD, VALVE, BATH = (12, Y, 0), (9, Y, 0), (6, Y, 0), (3, Y, 0), (0, Y, 0)
INGOTS = 8
OUTPUT = REPO_ROOT / "output" / "compact_refrigeration_loop.json"


def build(h: HarnessClient):
    h.clear_area(-12, Y - 1, -12, 24, Y + 10, 12)
    h.fill_area(-10, Y - 1, -10, 22, Y - 1, 10, "stone", 0)
    pump = C.place_pump(h, TIER, PUMP, *TANK, FLUID, CHARGE_L)
    row = [pump, C.place(h, "compressor", TIER, COMP), C.place(h, "radiator", TIER, RAD),
           C.place(h, "valve", TIER, VALVE, powered=False), C.place(h, "bath", TIER, BATH, powered=False)]
    C.connect_row(h, C.pipe("STEEL", "NORMAL"), row, Y)
    h.set_container_slot(*BATH, 0, INGOT, INGOTS, 0)
    print("[+] Built compact pump, compressor, radiator, valve and bath")


def zone_line(status: str):
    m = re.search(r"(\d+) pressure zones, ([\d.]+) to ([\d.]+) bar, (-?[\d.]+) to (-?[\d.]+)", status or "")
    return tuple(float(g) for g in m.groups()) if m else None


def bath_counts(h):
    """(waiting, ready) from the bath's readout."""
    for line in C.readout(h, BATH):
        m = re.search(r"Waiting: (\d+), cooled and ready: (\d+)", line)
        if m:
            return int(m.group(1)), int(m.group(2))
    return None


def tank_liters(h):
    for line in C.readout(h, PUMP):
        m = re.search(r"Tanks: (\d+) x .*?, ([\d,]+) / ([\d,]+) L", line)
        if m:
            return int(m.group(2).replace(",", ""))
    return None


def test(h: HarnessClient):
    h.stand(6, Y + 6, -9, 0.0, 30.0)
    time.sleep(2.0)
    print("    pump:", C.readout(h, PUMP))
    report = {"samples": []}
    t0 = time.time()
    ran_at = None
    peak_eu = 0
    zones = None
    ready = 0
    while time.time() - t0 < 600:
        time.sleep(5.0)
        st = h.coolant_status(*PUMP)
        sample = {"t": round(time.time() - t0), "state": st.get("loop_state"), "status": st.get("loop_status")}
        for name, pos in (("pump", PUMP), ("compressor", COMP), ("radiator", RAD), ("valve", VALVE), ("bath", BATH)):
            sample[name] = C.readout(h, pos)
        report["samples"].append(sample)
        print(f"[{sample['t']:4d}s] {sample['state']} {sample['status']}")
        for name in ("compressor", "radiator", "valve", "bath"):
            print(f"        {name:10s} {sample[name]}")
        assert st.get("loop_state") not in ("FAILED", None), f"Loop failed (or its pump is gone): {st}"
        z = zone_line(st.get("loop_status"))
        if z:
            zones = z
            ran_at = ran_at or time.time()
        m = re.search(r"(\d+) EU/t \(max", " ".join(sample["compressor"]))
        peak_eu = max(peak_eu, int(m.group(1)) if m else 0)
        counts = bath_counts(h)
        ready = counts[1] if counts else ready
        if ready >= INGOTS:
            break
        assert ran_at or time.time() - t0 < 120, f"The loop did not start within two minutes: {st}"

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(report, indent=2))
    assert zones, f"The loop never ran: {h.coolant_status(*PUMP)}"
    count, low, high, tmin, tmax = zones
    print(f"[*] {count:.0f} zones, {low:.2f} / {high:.2f} bar, {tmin:.1f} to {tmax:.1f} °C, "
          f"compressor peak {peak_eu} EU/t, {ready} ingots cooled in {time.time() - (ran_at or t0):.0f} s")
    assert count == 2, f"Expected two pressure zones: {zones}"
    assert high <= 16.0, f"The high side is over the MV machines' rating: {zones}"
    assert 0 < peak_eu <= 128, f"The MV compressor should draw up to its tier's EU: {peak_eu}"
    assert ready >= INGOTS, f"Expected {INGOTS} cooled ingots, got {ready}"
    out = h.container_info(*BATH)
    print("    bath inventory:", out)
    assert "Kanthal" in json.dumps(out), f"Expected Kanthal ingots in the bath's output: {out}"

    # Stopped, the pump takes its charge back
    before = tank_liters(h)
    h.soft_hammer(*PUMP)
    deadline = time.time() + 180
    state = None
    while time.time() < deadline:
        time.sleep(5.0)
        st = h.coolant_status(*PUMP)
        state = st.get("loop_state")
        print(f"    stopping: {state} {st.get('loop_status')}, tanks {tank_liters(h)} L")
        if state == "EMPTY":
            break
    after = tank_liters(h)
    assert state == "EMPTY", f"The stopped pump did not take its charge back: {h.coolant_status(*PUMP)}"
    assert after is not None and before is not None and after > before, f"Tanks {before} -> {after} L"
    assert after >= CHARGE_L - 10, f"The tanks should hold the whole charge again: {after} of {CHARGE_L} L"
    h.screenshot(f"screenshots/{TAG}.png")
    print("[✓] Compact refrigeration loop cooled its ingots and gave its charge back")


def run_tests(port: int = 25571):
    driver = InWorldTestDriver(tag=TAG, port=port, no_mobs=True, base_template="superflat_empty")
    return driver.run(build_fn=build, test_fn=test)


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--port", type=int, default=25571)
    a = ap.parse_args()
    sys.exit(0 if run_tests(port=a.port) else 1)

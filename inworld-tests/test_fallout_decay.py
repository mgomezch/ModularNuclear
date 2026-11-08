#!/usr/bin/env python3
"""
In-world test: how meltdown fallout's contamination goes down, with GregTech pollution on or off.

Run it against stock GregTech pollution, in a game whose GregTech pollution setting
(config/GregTech/Pollution.cfg, "Activate Pollution") matches --pollution; the test checks that first.

--pollution off: contamination falls on a timer, 10,000 over 12,000 ticks, whatever happens to pollution,
                 and the fallout vanishes when it reaches zero.
--pollution on:  contamination falls only when pollution in the fallout's area does:
                 1. Natural: GregTech's pollution cycle (once a minute) decays the fallout's own pollution
                    spike and spreads it to the neighbouring chunks. Contamination steps down then, and
                    stays put between cycles.
                 2. Removed: taking pollution out of the fallout's chunk, or a chunk next to it, lowers
                    contamination by 62.5 per 30,000 removed.
                 3. Air filter: an Electric Air Filter near the fallout cleans its fallout-polluted chunks
                    every second, and contamination falls with it, down to zero.
"""

import sys
import time
from pathlib import Path

from nhdev import NH_DEV_ROOT as REPO_ROOT

from tools.agentharness import HarnessClient
from tools.inworld_test_driver import InWorldTestDriver

INITIAL_CONTAMINATION = 10000.0
TOTAL_LIFETIME_TICKS = 12000
REDUCTION_PER_POLLUTION = 62.5 / 30000.0
POLLUTION_CYCLE_SECONDS = 60

FX, FY, FZ = 904, 5, 904  # the fallout, in the middle of chunk (56, 56)

# Electric Air Filter T3, two chunks east of the fallout: controller at (EX, 5, EZ) facing north
EX, EZ = 936, 904
EAF_T3_ID = 12022
AIR_FILTER_CASING_META = 5
AIR_FILTER_VENT_META = 6
AUTO_MAINTENANCE_HATCH_ID = 111
ENERGY_HATCH_UHV_ID = 49
MUFFLER_HATCH_UHV_ID = 99
STEEL_TURBINE = ("gregtech:gt.metatool.01", 172)


def fallout(client: HarnessClient) -> dict:
    info = client.fallout_info()
    assert len(info) == 1, f"Expected one fallout entity, got: {info}"
    return info[0]


def sample(client: HarnessClient, seconds: int) -> list:
    """Contamination once a second for this many seconds."""
    values = []
    for _ in range(seconds):
        values.append(fallout(client)["contamination"])
        time.sleep(1.0)
    return values


def steps(values: list) -> int:
    return sum(1 for a, b in zip(values, values[1:]) if b != a)


def summon_fallout(client: HarnessClient) -> dict:
    assert client.fallout_info() == [], "Expected no fallout before summoning one"
    client.teleport(FX, FY + 2, FZ - 20)
    client.command(f"summon modularnuclear.MeltdownFallout {FX} {FY} {FZ}")
    client.wait(10)
    return fallout(client)


def build_air_filter(client: HarnessClient):
    """A 3x4x3 Electric Air Filter T3 on y = 5..8, without its turbine, so it stays idle."""
    client.fill_area(EX - 1, 5, EZ, EX + 1, 8, EZ + 2, "gregtech:gt.blockcasingsNH", AIR_FILTER_CASING_META)
    client.fill_area(EX, 6, EZ + 1, EX, 7, EZ + 1, "minecraft:air", 0)
    for y in (6, 7):
        for x in (EX - 1, EX + 1):
            for z in (EZ, EZ + 2):
                client.set_block(x, y, z, "gregtech:gt.blockcasingsNH", AIR_FILTER_VENT_META)
    client.set_gt_machine(EX, 5, EZ, EAF_T3_ID, 2)
    client.set_gt_machine(EX - 1, 5, EZ, AUTO_MAINTENANCE_HATCH_ID, 2)
    client.set_gt_machine(EX, 5, EZ + 2, ENERGY_HATCH_UHV_ID, 3)
    client.set_cover(EX, 5, EZ + 2, 3, "Cover_Wireless_Energy_Debug")
    client.set_gt_machine(EX, 6, EZ + 2, MUFFLER_HATCH_UHV_ID, 3)


def build(client: HarnessClient):
    client.set_time(6000)
    client.set_weather("clear")
    client.teleport(EX, FY + 2, EZ - 6)
    client.wait(20)
    build_air_filter(client)
    client.wait(10)


def test_pollution_off(client: HarnessClient):
    print("[*] Pollution off: contamination falls on a timer")
    start = summon_fallout(client)
    print(f"    Summoned: {start}")
    assert start["pollution_enabled"] is False, "GregTech pollution is on; run with --pollution on"
    rate = INITIAL_CONTAMINATION / TOTAL_LIFETIME_TICKS

    expected = INITIAL_CONTAMINATION - start["lifetime_ticks"] * rate
    assert abs(start["contamination"] - expected) < 1e-6, f"Expected {expected:.3f} after {start['lifetime_ticks']} ticks, got {start}"

    values = sample(client, 10)
    assert steps(values) == len(values) - 1, f"Expected contamination to fall every second, got {values}"

    # Removing pollution changes nothing: there is none
    before = fallout(client)
    client.chat("/gt pollution -240000")
    client.wait(20)
    after = fallout(client)
    expected = before["contamination"] - (after["lifetime_ticks"] - before["lifetime_ticks"]) * rate
    print(f"    Timer only: {before['contamination']:.3f} -> {after['contamination']:.3f} (expected {expected:.3f})")
    assert abs(after["contamination"] - expected) < 1e-6, f"Expected only the timer to lower contamination, got {after}"

    # Near the end, it runs out and the fallout vanishes
    client.fallout_set_contamination(20.0)
    client.wait(40)
    remaining = client.fallout_info()
    assert remaining == [], f"Expected the fallout to vanish when its contamination ran out, got {remaining}"
    print("[✓] Contamination fell on the timer and the fallout vanished at zero")


def wait_for_pollution_cycle(client: HarnessClient) -> tuple:
    """Waits until the pollution cycle lowers contamination; returns the contamination before and after."""
    values = [fallout(client)["contamination"]]
    deadline = time.time() + POLLUTION_CYCLE_SECONDS + 15
    while time.time() < deadline:
        time.sleep(1.0)
        values.append(fallout(client)["contamination"])
        if values[-1] != values[-2]:
            assert all(v == values[0] for v in values[:-1]), f"Contamination moved between pollution cycles: {values}"
            # The cycle works through the polluted chunks a few per tick: let it finish
            time.sleep(2.0)
            return values[-2], fallout(client)["contamination"]
    raise AssertionError(f"No pollution cycle lowered contamination in {POLLUTION_CYCLE_SECONDS + 15} s: {values}")


def remove_pollution(client: HarnessClient, x: int, z: int, amount: int) -> float:
    client.teleport(x, FY + 2, z)
    client.wait(5)
    before = fallout(client)["contamination"]
    client.chat(f"/gt pollution -{amount}")
    client.wait(5)
    after = fallout(client)["contamination"]
    return before - after


def test_pollution_on(client: HarnessClient):
    print("[*] Pollution on: contamination falls only when pollution does")
    start = summon_fallout(client)
    print(f"    Summoned: {start}")
    assert start["pollution_enabled"] is True, "GregTech pollution is off; run with --pollution off"

    # 1. Natural: the pollution cycle decays and spreads the fallout's pollution spike
    before, after = wait_for_pollution_cycle(client)
    print(f"    Pollution cycle: {before:.3f} -> {after:.3f}")
    assert after < before, "Expected the pollution cycle to lower contamination"
    quiet = sample(client, 15)
    assert steps(quiet) == 0, f"Expected contamination to stay put between pollution cycles, got {quiet}"
    print("[✓] Natural decay and spread lowered contamination; nothing else did")

    # 2. Removed: in the fallout's chunk and in the chunk east of it, well before the next cycle
    for x in (FX, FX + 16):
        drop = remove_pollution(client, x, FZ, 240000)
        print(f"    Removing 240,000 pollution at x={x}: contamination -{drop:.3f}")
        assert abs(drop - 240000 * REDUCTION_PER_POLLUTION) < 1e-6, f"Expected -500, got -{drop}"
    print("[✓] Removing pollution lowered contamination by 62.5 per 30,000")

    # 3. Air filter: give it its turbine
    client.teleport(EX, FY + 2, EZ - 6)
    client.wait(20)
    client.set_container_slot(EX, 5, EZ, 1, STEEL_TURBINE[0], 1, STEEL_TURBINE[1])
    # Built with ticks frozen, it has not checked its structure yet
    client.recheck(EX, 5, EZ)
    client.fix_maintenance(EX, 5, EZ)
    status = {}
    for _ in range(20):
        status = client.coolant_status(x=EX, y=5, z=EZ)
        if status.get("machine_formed") and status.get("is_active"):
            break
        time.sleep(1.0)
    print(f"    Air filter: {status}")
    assert status.get("machine_formed") and status.get("is_active"), f"Expected the air filter to form and run, got {status}"

    values = sample(client, 20)
    print(f"    Contamination with the air filter running: {values[0]:.1f} -> {values[-1]:.1f}")
    assert steps(values) >= 10, f"Expected the air filter to lower contamination every second, got {values}"

    # Near the end, the air filter cleans the rest and the fallout vanishes. The fallout then covers only its
    # own chunk and the ones around it, which the air filter reaches once in a while as it goes round its 7x7
    client.fallout_set_contamination(100.0)
    remaining = client.fallout_info()
    deadline = time.time() + 60
    while remaining and time.time() < deadline:
        time.sleep(1.0)
        remaining = client.fallout_info()
    assert remaining == [], f"Expected the air filter to clean up the fallout, got {remaining}"
    print("[✓] The air filter lowered contamination to zero and the fallout vanished")


def main(pollution: str, port: int) -> bool:
    driver = InWorldTestDriver(tag=f"fallout_decay_pollution_{pollution}", port=port)
    test_fn = test_pollution_on if pollution == "on" else test_pollution_off
    return driver.run(build_fn=build, test_fn=test_fn)


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Meltdown fallout decay, with GregTech pollution on or off")
    parser.add_argument("--pollution", choices=("on", "off"), required=True,
                        help="Whether the game has GregTech pollution on; the test checks it")
    parser.add_argument("--port", type=int, default=25571, help="AgentHarness port (default: 25571)")
    args = parser.parse_args()
    sys.exit(0 if main(args.pollution, args.port) else 1)

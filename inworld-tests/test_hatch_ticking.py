#!/usr/bin/env python3
"""
In-world test: reactor hatches accept fluid from the moment they load, and stop ticking unless they have a cover.

A stock GregTech input hatch beside them shows what the state under test does to a normal tile: it refuses fluid
until its tick counter passes 5.  See Note [Hatches that work without ticking].

With ticks frozen: a just-placed core hatch takes fluid with its tick counter at 0, and the stock hatch refuses it.
Running:           the core hatch switches its ticking off, and its facing and colour still reach the client.
Validated again:   validate() leaves a non-ticking hatch's counter alone, and it still takes fluid and opens its GUI.
Covers:            a cover makes the hatch tick, and removing the cover stops it again.
Reload:            after saving and reloading with ticks frozen, its counter is inside GregTech's limit and it takes
                   fluid.
"""

import sys
import time

from nhdev import NH_DEV_ROOT as REPO_ROOT  # noqa: F401 (puts the workspace on sys.path)

from tools.agentharness import HarnessClient
from tools.inworld_test_driver import InWorldTestDriver

Y = 5
CORE_HATCH_ID = 32103
CORE_BUS_ID = 32100
STOCK_HATCH_ID = 51

CORE = (900, Y, 900)
STOCK = (902, Y, 900)
BUS = (904, Y, 900)

FLUID = "ic2coolant"
STANDING_ON = (900, Y + 3, 896)


def filled(result: str) -> int:
    """The amount in a fill_fluid result, 'FILLED: <n> mb of ...'."""
    return int(result.split()[1])


def fill(client: HarnessClient, pos: tuple, amount: int = 1000) -> int:
    return filled(client.fill_fluid(*pos, FLUID, amount))


def state(client: HarnessClient, pos: tuple) -> dict:
    return client.tile_tick_state(*pos)


def wait_until_not_ticking(client: HarnessClient, pos: tuple, seconds: float = 10.0) -> dict:
    deadline = time.time() + seconds
    st = state(client, pos)
    while st["ticking"] and time.time() < deadline:
        time.sleep(0.25)
        st = state(client, pos)
    return st


def build(client: HarnessClient):
    client.teleport(*STANDING_ON)
    client.fill_area(895, Y - 1, 895, 910, Y - 1, 905, "minecraft:stone")


def check_frozen_fill(client: HarnessClient):
    print("[*] Ticks frozen: a just-placed hatch, tick counter 0")
    client.freeze_ticks(True)
    client.set_gt_machine(*CORE, CORE_HATCH_ID, 2)
    client.set_gt_machine(*STOCK, STOCK_HATCH_ID, 2)
    client.set_gt_machine(*BUS, CORE_BUS_ID, 2)
    core = state(client, CORE)
    assert core["timer"] == 0, f"Expected a core hatch that hasn't ticked, got {core}"
    assert state(client, STOCK)["timer"] == 0
    assert fill(client, STOCK) == 0, "Expected the stock hatch to refuse fluid at tick 0; the test shows nothing else"
    took = fill(client, CORE)
    assert took == 1000, f"Expected the core hatch to take 1000 L at tick 0, took {took}"
    print("[✓] The core hatch took fluid at tick 0, and the stock hatch refused it")
    client.freeze_ticks(False)


def check_stops_ticking(client: HarnessClient):
    print("[*] Running: the core hatch switches its ticking off")
    core = wait_until_not_ticking(client, CORE)
    assert not core["ticking"] and core["tick_disabled"], f"Expected the core hatch to stop ticking, got {core}"
    assert state(client, STOCK)["ticking"], "Expected the stock hatch to keep ticking"
    assert fill(client, STOCK) > 0, "Expected the stock hatch to take fluid once it has ticked"
    took = fill(client, CORE)
    assert took == 1000, f"Expected the non-ticking core hatch to take 1000 L, took {took}"
    print(f"[✓] The core hatch stopped ticking at counter {core['timer']} and still takes fluid")

    print("[*] Facing and colour of a non-ticking hatch reach the client")
    client.tile_poke(*CORE, facing="SOUTH", color=3)
    time.sleep(1.0)
    core = state(client, CORE)
    print(f"    {core}")
    assert core["facing"] == "SOUTH" and core["client_facing"] == "SOUTH", f"Facing didn't reach the client: {core}"
    assert core["color"] == 3 and core["client_color"] == 3, f"Colour didn't reach the client: {core}"
    print("[✓] Both reached the client")


def check_validated_again(client: HarnessClient):
    print("[*] Validated again: validate() on a non-ticking hatch")
    bus = wait_until_not_ticking(client, BUS)
    assert not bus["ticking"], f"Expected the bus to stop ticking, got {bus}"
    client.tile_poke(*CORE, validate=True)
    client.tile_poke(*BUS, validate=True)
    core = state(client, CORE)
    assert core["timer"] >= 20 and not core["ticking"], f"Expected the counter kept and no ticking, got {core}"
    took = fill(client, CORE)
    assert took == 1000, f"Expected the reset hatch to take 1000 L, took {took}"
    print("[✓] A non-ticking hatch keeps its counter when validated again and still takes fluid")

    client.teleport(BUS[0] + 0.5, Y, BUS[2] - 1.5)
    client.look_at(BUS[0] + 0.5, Y + 0.5, BUS[2] + 0.5)
    time.sleep(0.5)
    client.right_click()
    time.sleep(1.5)
    print(f"    After clicking the bus: {client.assert_gui('GuiContainerWrapper')}")
    client.close_gui()
    print("[✓] A non-ticking bus validated again opens its GUI")


def check_covers(client: HarnessClient):
    print("[*] Covers: a cover makes the hatch tick")
    client.set_cover(*CORE, 3, "Cover_Wireless_Energy_Debug")
    time.sleep(2.0)
    core = state(client, CORE)
    assert core["ticking"] and not core["tick_disabled"], f"Expected a covered hatch to tick, got {core}"
    print("[✓] The covered hatch ticks, past tick 20")
    client.set_cover(*CORE, 3, "none")
    core = wait_until_not_ticking(client, CORE)
    assert not core["ticking"], f"Expected the hatch to stop ticking with its cover gone, got {core}"
    print("[✓] It stopped ticking once the cover was gone")


def check_reload(client: HarnessClient, driver: InWorldTestDriver, world: str):
    print("[*] Reload: save, reload with ticks frozen")
    client.save_world()
    client.exitworld()
    time.sleep(2.0)
    client.freeze_ticks(True)
    assert driver.wait_for_in_world(world), f"Failed to reload {world}"
    try:
        client.freeze_ticks(True)
        core = state(client, CORE)
        stock = state(client, STOCK)
        print(f"    Core hatch: {core}\n    Stock hatch: {stock}")
        assert core["timer"] <= 5, f"Expected the counter still inside GregTech's limit, got {core}"
        took = fill(client, CORE)
        assert took == 1000, f"Expected the reloaded hatch to take 1000 L at counter {core['timer']}, took {took}"
        if stock["timer"] <= 5:
            assert fill(client, STOCK) == 0, "Expected the reloaded stock hatch to refuse fluid inside the limit"
        print(f"[✓] The reloaded hatch took fluid at counter {core['timer']}")
    finally:
        client.freeze_ticks(False)


def main(port: int) -> bool:
    driver = InWorldTestDriver(tag="hatch_ticking", port=port)

    def test(client: HarnessClient):
        client.teleport(*STANDING_ON)
        check_frozen_fill(client)
        check_stops_ticking(client)
        check_validated_again(client)
        check_covers(client)
        check_reload(client, driver, "test_hatch_ticking")

    return driver.run(build_fn=build, test_fn=test)


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Reactor hatches accept fluid at once and stop ticking")
    parser.add_argument("--port", type=int, default=25571, help="AgentHarness port (default: 25571)")
    args = parser.parse_args()
    sys.exit(0 if main(args.port) else 1)

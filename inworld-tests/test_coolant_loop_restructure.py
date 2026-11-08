#!/usr/bin/env python3
"""
In-world test: a coolant loop stops fully when its pump is off, and can be changed while filled and stopped.

See Note [Changing a stopped coolant loop].

1. Stopping: with the pump off, the flow reaches exactly zero.
2. Broken at rest: removing pipes from the stopped, filled loop explodes nothing and spills nothing, and the pump
   refuses to start until it is closed again.
   It stays that way across a save and reload.
3. Larger: a longer route draws the difference from the reservoir before the flow starts.
4. Smaller: a shorter route returns the difference to the reservoir before the flow starts.
5. No room: with the reservoir full, a loop that got smaller refuses to start.
"""

import sys
import time

from nhdev import NH_DEV_ROOT as REPO_ROOT  # noqa: F401 (puts the workspace on sys.path)

from test_coolant_loops import PIPE_ID, build_coolant_loops
from tools.agentharness import HarnessClient
from tools.inworld_test_driver import InWorldTestDriver

OX, OY, OZ = 500, 5, 20
OY_LOOP = OY + 1
PUMP = (OX + 1, OY, OZ)
PHE = (OX + 11, OY + 1, OZ)
LEG_START = (OX + 5, OY_LOOP, OZ + 8)
LEG_END = (OX - 1, OY_LOOP, OZ + 1)
EXTRA_PIPES = 8
# Longer than the pump's checks for a broken loop (5 s) and its crawl (10 s) while running
SETTLE_SECONDS = 12

cold_leg = {}


def build(client: HarnessClient):
    cold_leg["path"] = build_coolant_loops(client)["path"]


def status(client: HarnessClient) -> dict:
    return client.coolant_status(*PUMP)


def show(st: dict) -> str:
    return (f"{st.get('loop_state')} fill {st.get('fill_liters')}/{st.get('required_fill_liters')} L, "
            f"flow {st.get('flow_rate_l_s')} L/s, reservoir {st.get('total_coolant_liters')} L: {st.get('loop_status')}")


def set_pump(client: HarnessClient, on: bool):
    if status(client)["is_allowed_to_work"] != on:
        client.soft_hammer(*PUMP)


def wait_for(client: HarnessClient, what: str, condition, seconds: float = 120.0) -> dict:
    deadline = time.time() + seconds
    st = status(client)
    while not condition(st):
        assert time.time() < deadline, f"Timed out waiting for {what}: {show(st)}"
        time.sleep(0.5)
        st = status(client)
    return st


def start_running(client: HarnessClient) -> dict:
    return wait_for(
        client, "the loop to circulate",
        lambda st: st.get("loop_state") == "CIRCULATING" and st.get("flow_rate_l_s", 0.0) > 1.0)


def stop(client: HarnessClient) -> dict:
    set_pump(client, False)
    start = time.time()
    st = wait_for(client, "the flow to stop", lambda st: st.get("flow_rate_l_s", 1.0) == 0.0)
    print(f"    Flow reached exactly zero after {time.time() - start:.1f} s: {show(st)}")
    return st


def remove_leg(client: HarnessClient):
    for x, y, z in cold_leg["path"]:
        client.set_block(x, y, z, "minecraft:air", 0)


def lay_leg(client: HarnessClient, length=None):
    pump = client.multiblock_info("coolant_pump", OX, OY, OZ)
    route = client.route_pipe(
        pipe_id=PIPE_ID, start=LEG_START, end=LEG_END, start_conn="EAST", end_conn="EAST",
        avoid=[pump.bounds], preferred_y=OY_LOOP, length=length)
    cold_leg["path"] = route["path"]
    print(f"    Laid {route['length']} pipes")
    return route["length"]


def set_up(client: HarnessClient):
    client.stand(OX + 7, OY + 3, OZ - 6)
    client.wait(20)
    client.fill_fluid(PUMP[0], OY + 2, OZ + 1, "ic2distilledwater", 100000)
    client.fill_fluid(OX, OY + 2, OZ, "ic2distilledwater", 100000)
    client.set_container_slot(*PUMP, 1, "gregtech:gt.metatool.01", 1, 172)
    client.recheck(*PHE)
    client.recheck(*PUMP)
    client.wait(10)
    client.set_cover(PUMP[0], OY, OZ + 2, 3, "Cover_Wireless_Energy_Debug")
    client.set_cover(OX + 11, OY, OZ + 4, 3, "Cover_Wireless_Energy_Debug")
    client.wait(5)
    client.crawl_loop(*PUMP)
    set_pump(client, True)
    print(f"[*] Running: {show(start_running(client))}")


def check_broken_at_rest(client: HarnessClient, rest: dict):
    print("[*] Broken at rest: removing the cold leg from the stopped loop")
    remove_leg(client)
    time.sleep(SETTLE_SECONDS)
    st = status(client)
    print(f"    {show(st)}")
    assert st["machine_formed"], f"Expected the pump intact: {show(st)}"
    assert st["fill_liters"] == rest["fill_liters"], f"Expected the coolant still in the loop: {show(st)}"
    assert st["total_coolant_liters"] == rest["total_coolant_liters"], f"Expected the reservoir untouched: {show(st)}"
    assert st["loop_state"] != "FAILED", f"Expected no failure: {show(st)}"
    print("[✓] No explosion or spill, and the coolant is still accounted for")

    print("[*] Starting the pump with the loop still open")
    set_pump(client, True)
    time.sleep(SETTLE_SECONDS)
    st = status(client)
    print(f"    {show(st)}")
    assert st["flow_rate_l_s"] == 0.0, f"Expected no flow: {show(st)}"
    assert st["fill_liters"] == rest["fill_liters"], f"Expected the coolant still in the loop: {show(st)}"
    assert st["machine_formed"] and st["loop_state"] != "FAILED", f"Expected no failure: {show(st)}"
    assert not st["loop_formed"], f"Expected the pump to find the loop open: {show(st)}"
    print("[✓] The pump refuses to start on an open loop")
    set_pump(client, False)


def check_reload_at_rest(client: HarnessClient, driver: InWorldTestDriver, rest: dict):
    print("[*] Reloading the world with the stopped loop still open")
    client.save_world()
    client.exitworld()
    time.sleep(2.0)
    assert driver.wait_for_in_world("test_coolant_loop_restructure"), "Failed to reload the world"
    client.wait(20)
    time.sleep(SETTLE_SECONDS)
    st = status(client)
    print(f"    {show(st)}")
    assert not st["is_allowed_to_work"], f"Expected the pump still off: {show(st)}"
    assert st["machine_formed"] and st["loop_state"] != "FAILED", f"Expected no failure: {show(st)}"
    assert st["flow_rate_l_s"] == 0.0, f"Expected no flow: {show(st)}"
    assert st["fill_liters"] == rest["fill_liters"], f"Expected the coolant still in the loop: {show(st)}"
    assert st["total_coolant_liters"] == rest["total_coolant_liters"], f"Expected the reservoir untouched: {show(st)}"
    print("[✓] The reload changed nothing")


def check_larger(client: HarnessClient, rest: dict) -> dict:
    print("[*] Larger: a longer route")
    length = lay_leg(client, len(cold_leg["path"]) + EXTRA_PIPES)
    print(f"    Route of {length} pipes")
    set_pump(client, True)
    st = start_running(client)
    print(f"    {show(st)}")
    grew = st["required_fill_liters"] - rest["required_fill_liters"]
    assert grew > 0, f"Expected the loop to hold more: {show(st)}"
    assert st["fill_liters"] == st["required_fill_liters"], f"Expected the loop full: {show(st)}"
    assert rest["total_coolant_liters"] - st["total_coolant_liters"] == grew, \
        f"Expected the reservoir to give {grew} L: {show(st)}"
    print(f"[✓] The pump drew {grew} L from the reservoir and started")
    return st


def check_smaller(client: HarnessClient, base_length: int) -> dict:
    print("[*] Smaller: back to the shorter route")
    rest = stop(client)
    remove_leg(client)
    lay_leg(client, base_length)
    set_pump(client, True)
    st = start_running(client)
    print(f"    {show(st)}")
    shrank = rest["required_fill_liters"] - st["required_fill_liters"]
    assert shrank > 0, f"Expected the loop to hold less: {show(st)}"
    assert st["fill_liters"] == st["required_fill_liters"], f"Expected the loop full: {show(st)}"
    assert st["total_coolant_liters"] - rest["total_coolant_liters"] == shrank, \
        f"Expected the reservoir to take {shrank} L: {show(st)}"
    print(f"[✓] The pump returned {shrank} L to the reservoir and started")
    return st


def check_no_room(client: HarnessClient, base_length: int):
    print("[*] No room: a smaller loop with a full reservoir")
    stop(client)
    remove_leg(client)
    lay_leg(client, base_length + EXTRA_PIPES)
    set_pump(client, True)
    start_running(client)
    stop(client)
    remove_leg(client)
    lay_leg(client, base_length)
    print(f"    Filling the reservoir: {client.fill_fluid(PUMP[0], OY + 2, OZ + 1, 'ic2distilledwater', 10000000)}")
    before = status(client)
    set_pump(client, True)
    st = wait_for(client, "the pump to refuse", lambda s: "no room" in (s.get("loop_status") or ""), 30.0)
    print(f"    {show(st)}")
    time.sleep(3.0)
    st = status(client)
    assert st["flow_rate_l_s"] == 0.0, f"Expected no flow: {show(st)}"
    assert st["fill_liters"] > st["required_fill_liters"], f"Expected the excess still in the loop: {show(st)}"
    assert st["total_coolant_liters"] >= before["total_coolant_liters"], f"Expected the reservoir kept: {show(st)}"
    print("[✓] The pump refuses to start with no room for the excess")


def test(client: HarnessClient, driver: InWorldTestDriver):
    base_length = len(cold_leg["path"])
    set_up(client)
    print("[*] Stopping: pump off")
    rest = stop(client)
    assert rest["fill_liters"] > 0, f"Expected a filled loop: {show(rest)}"
    print("[✓] The flow stopped completely")

    check_broken_at_rest(client, rest)
    check_reload_at_rest(client, driver, rest)
    check_larger(client, rest)
    check_smaller(client, base_length)
    check_no_room(client, base_length)


def main(port: int) -> bool:
    driver = InWorldTestDriver(tag="coolant_loop_restructure", port=port, no_mobs=True,
                               base_template="superflat_empty")
    return driver.run(build_fn=build, test_fn=lambda client: test(client, driver))


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="A coolant loop stops fully and can be changed while stopped")
    parser.add_argument("--port", type=int, default=25571, help="AgentHarness port (default: 25571)")
    args = parser.parse_args()
    sys.exit(0 if main(args.port) else 1)

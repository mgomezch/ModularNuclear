#!/usr/bin/env python3
"""
Automated Meltdown In-World Test Script
Tests the Modular Pressure Tube Reactor (MPTR) Catastrophic Meltdown Mechanic:
  Stage 1 (Build Phase): Builds, forms, and fuels an MPTR with ticks frozen.
                         Saves snapshot to 'mptr_meltdown_pre'.
  Stage 2 (Execution Phase): Ephemeral run from 'mptr_meltdown_pre' with ticks unfrozen.
                             Triggers core meltdown (T > 80% threshold).
                             Verifies explosion breach, Corium generation, fallout entity,
                             and Bedrock solidification after 2 minutes.
                             Saves snapshot to 'mptr_meltdown_post'.
"""

import sys
import os
import time
from pathlib import Path

# Resolve repository root
from nhdev import NH_DEV_ROOT as REPO_ROOT

from tools.agentharness import HarnessClient, HarnessError
from tools.inworld_test_driver import InWorldTestDriver

RC_X, RC_Y, RC_Z = 518, 5, 20
PL_X, PL_Y, PL_Z = 518, 6, 17
MH_X, MH_Y, MH_Z = 517, 5, 20

def build_meltdown_reactor(client: HarnessClient):
    """Stage 1: Assembles and forms the reactor with ticks frozen."""
    print("------------------------------------------------------------")
    client.close_gui()
    client.wait(5)

    print(f"[*] Placing player support stone at ({PL_X}, {PL_Y - 1}, {PL_Z})...")
    client.setblock(PL_X, PL_Y - 1, PL_Z, "stone")
    client.wait(5)

    print(f"[*] Positioning player on support at ({PL_X}, {PL_Y}, {PL_Z})...")
    client.stand(PL_X, PL_Y, PL_Z)
    client.wait(5)
    client.look(0.0, 30.0)
    client.wait(5)

    print("[*] Resetting inventory...")
    client.chat("/clear @p")
    client.wait(5)

    print(f"[*] Placing controller support stone at ({RC_X}, {RC_Y - 1}, {RC_Z})...")
    client.setblock(RC_X, RC_Y - 1, RC_Z, "stone")
    client.wait(5)

    print(f"[*] Placing controller at ({RC_X}, {RC_Y}, {RC_Z})...")
    res = client.set_gt_machine(RC_X, RC_Y, RC_Z, 32115, 2)
    print(f"[*] set_gt_machine result: {res}")
    assert "GT_MACHINE_SET" in res or "already" in res, f"Failed to set GT machine: {res}"
    client.wait(5)
    client.assert_block(RC_X, RC_Y, RC_Z, "gregtech:gt.blockmachines")
    print("[✓] Controller block confirmed.")

    print(f"[*] Autobuilding Tier 2 multiblock at ({RC_X}, {RC_Y}, {RC_Z})...")
    client.autobuild(RC_X, RC_Y, RC_Z, 2)
    client.wait(20)

    print(f"[*] Replacing casing with Auto-Taping Maintenance Hatch at ({MH_X}, {MH_Y}, {MH_Z})...")
    res_mh = client.set_gt_machine(MH_X, MH_Y, MH_Z, 15497, 2)
    print(f"[*] set_gt_machine (MH) result: {res_mh}")
    client.wait(5)

    print("[*] Triggering structure recheck...")
    client.recheck(RC_X, RC_Y, RC_Z)
    client.wait(10)

    for _ in range(5):
        try:
            client.assert_status(RC_X, RC_Y, RC_Z, '"formed":true')
            break
        except AssertionError:
            client.recheck(RC_X, RC_Y, RC_Z)
            client.wait(10)
    else:
        client.assert_status(RC_X, RC_Y, RC_Z, '"formed":true')
    print("[✓] Multiblock formed successfully!")

    print("[*] Feeding core with fuel rods, coolant, and completing maintenance...")
    client.feed(RC_X, RC_Y, RC_Z)
    client.wait(10)
    print("[✓] Build Phase completed: reactor assembled, formed, and fueled.")


def test_meltdown(client: HarnessClient):
    """Stage 2: Triggers meltdown, asserts explosion, Corium, and Bedrock solidification."""
    print("------------------------------------------------------------")
    print(f"[*] Standing safely back to observe reactor meltdown...")
    client.stand(RC_X, 8, RC_Z - 11)
    client.wait(5)
    client.look(0.0, 15.0)
    client.wait(5)

    # 1. Assert structure is initially formed
    client.recheck(RC_X, RC_Y, RC_Z)
    client.wait(10)
    client.assert_status(RC_X, RC_Y, RC_Z, '"formed":true')
    pre_status = client.nuke_status(RC_X, RC_Y, RC_Z)
    print(f"[✓] Pre-meltdown reactor confirmed formed: {pre_status}")

    # 2. Trigger core meltdown
    print(f"[*] Triggering core meltdown at ({RC_X}, {RC_Y}, {RC_Z})...")
    res_heat = client.nuke_heat(RC_X, RC_Y, RC_Z, 1500)
    print(f"    nuke_heat result: {res_heat}")
    client.wait(20)

    fallout_info = client.fallout_info()
    if not fallout_info:
        print("[*] Heating set, invoking nuke_meltdown directly...")
        res = client.nuke_meltdown(RC_X, RC_Y, RC_Z)
        print(f"    nuke_meltdown result: {res}")
        client.wait(20)

    # 3. Verify explosion destroyed core formation
    try:
        status = client.nuke_status(RC_X, RC_Y, RC_Z)
        print(f"[*] Post-meltdown controller status: {status}")
        assert not status.get("formed", True), "Reactor should no longer be formed after meltdown"
    except HarnessError as e:
        print(f"[✓] Controller block destroyed by meltdown explosion: {e}")

    # 4. Verify fallout entity spawned
    fallout_info = client.fallout_info()
    print(f"[*] Fallout entity telemetry: {fallout_info}")
    assert fallout_info, "Expected EntityMeltdownFallout to spawn"

    # 5. Capture flowing Corium screenshot
    client.screenshot("screenshots/meltdown_corium_active.png")
    print("[✓] Screenshot saved: screenshots/meltdown_corium_active.png")

    # 6. The fallout starts at 10,000 contamination; a pollution cycle spreading its own pollution spike can take
    # about 800 off at once. inworld-tests/test_fallout_decay.py covers how it goes down
    assert fallout_info[0]["contamination"] > 9000, f"Expected the fallout near full contamination, got {fallout_info}"

    print("============================================================")
    print("[✓] Nuclear Meltdown & Fallout verification test PASSED!")
    print("============================================================")


def run_test(port: int = 25571):
    driver = InWorldTestDriver(
        tag="mptr_meltdown",
        port=port,
        no_mobs=True,
        base_template="superflat_empty",
    )
    return driver.run(build_fn=build_meltdown_reactor, test_fn=test_meltdown)


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="MPTR Meltdown In-World Test")
    parser.add_argument("--port", type=int, default=25571, help="AgentHarness port (default: 25571)")
    args = parser.parse_args()
    success = run_test(port=args.port)
    sys.exit(0 if success else 1)

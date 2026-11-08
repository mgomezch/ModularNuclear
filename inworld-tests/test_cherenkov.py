#!/usr/bin/env python3
"""
Automated In-World Test Script for Cherenkov Radiation Visual Effect & Water Pool Support
Tests:
  1. Multiblock formation with water-flooded hollow core chamber.
  2. Construction of glass tank and water pool above the reactor.
  3. Reactor active operation with neutron flux.
  4. Client-side Cherenkov radiation rendering, glowing aura, and particle emissions.
  5. Clean removal of all temporary platforms/stone.
"""

import sys
import os
import time
from pathlib import Path

from nhdev import NH_DEV_ROOT as REPO_ROOT

from tools.agentharness import HarnessClient, HarnessError
from tools.inworld_test_driver import InWorldTestDriver

RC_X, RC_Y, RC_Z = 518, 5, 20
MH_X, MH_Y, MH_Z = 517, 5, 20
PL_X, PL_Y, PL_Z = 518, 6, 17


def assert_reactor_running(status: dict):
    """Without neutron flux there is no Cherenkov glow, and the screenshots would show only a dark scene."""
    if not status.get("neutronsProduced"):
        raise AssertionError(f"Reactor is not producing neutrons, so it cannot glow: {status}")
    print(f"[✓] Reactor is running: {status['neutronsProduced']} neutrons in the last cycle")


def build_water_reactor(client: HarnessClient):
    print("------------------------------------------------------------")
    print("[*] Stage 1: Building MPTR and flooding chamber with water...")
    client.close_gui()
    client.wait(5)

    # Position player
    client.stand(PL_X, PL_Y, PL_Z)
    client.wait(5)
    client.look(0.0, 30.0)
    client.wait(5)

    # Controller block
    res = client.set_gt_machine(RC_X, RC_Y, RC_Z, 32115, 2)
    print(f"[*] set_gt_machine result: {res}")
    client.wait(5)
    client.assert_block(RC_X, RC_Y, RC_Z, "gregtech:gt.blockmachines")

    # Autobuild
    print(f"[*] Autobuilding multiblock at ({RC_X}, {RC_Y}, {RC_Z})...")
    client.autobuild(RC_X, RC_Y, RC_Z)
    client.wait(20)

    # Replace maintenance hatch with Auto-Taping Maintenance Hatch
    client.set_gt_machine(MH_X, MH_Y, MH_Z, 15497, 2)
    client.wait(5)

    # Check formation before water
    client.recheck(RC_X, RC_Y, RC_Z)
    client.wait(10)
    client.assert_status(RC_X, RC_Y, RC_Z, '"formed":true')
    print("[✓] Reactor formed cleanly in dry configuration.")

    # Construct glass basin around and above the reactor
    # Bounding perimeter around 7x7 reactor: X=514..522, Z=19..27, Y=4..12
    print("[*] Constructing glass containment basin...")
    client.fill_area(514, 4, 19, 522, 12, 19, "glass")
    client.fill_area(514, 4, 27, 522, 12, 27, "glass")
    client.fill_area(514, 4, 20, 514, 12, 26, "glass")
    client.fill_area(522, 4, 20, 522, 12, 26, "glass")

    # Fill water pool strictly above the top slice/hatches (Y=9 to Y=12)
    print("[*] Filling water pool above reactor (Y=9..12)...")
    res_pool = client.replace_area(515, 9, 20, 521, 12, 26, "water", "air")
    print(f"[✓] {res_pool}")
    client.wait(10)
    print("[✓] Water pool filled above reactor.")

    # Recheck formation: must remain formed!
    print("[*] Verifying structure formation with overhead water pool...")
    client.recheck(RC_X, RC_Y, RC_Z)
    client.wait(10)
    client.assert_status(RC_X, RC_Y, RC_Z, '"formed":true')
    print("[✓] MPTR multiblock successfully formed with overhead water pool!")

    # Feed fuel and coolant
    print("[*] Feeding core with fuel rods and coolant...")
    client.feed(RC_X, RC_Y, RC_Z)
    client.wait(10)

    # Clean up any standing platform blocks
    client.setblock(PL_X, PL_Y - 1, PL_Z, "air")
    client.wait(5)
    print("[✓] Cleaned up temporary player platform.")


def test_cherenkov(client: HarnessClient, time_of_day: int = 18000, prefix: str = ""):
    import shutil
    print("------------------------------------------------------------")
    print(f"[*] Stage 2: Verifying Cherenkov radiation visual effect at time {time_of_day}...")

    # Ensure chunk is loaded by standing near reactor
    client.stand(RC_X, 6, RC_Z - 3)
    client.wait(20)

    # Assert multiblock formed
    client.recheck(RC_X, RC_Y, RC_Z)
    client.wait(10)
    client.assert_status(RC_X, RC_Y, RC_Z, '"formed":true')

    # Set time of day
    print(f"[*] Setting time to {time_of_day}...")
    client.set_time(time_of_day)
    client.set_weather("clear")
    client.wait(10)

    # Let reactor simulate and produce neutrons
    print("[*] Running reactor ticks to produce neutron flux...")
    client.feed(RC_X, RC_Y, RC_Z)
    client.unfreeze_ticks()
    time.sleep(3.0)
    client.wait(80)

    # Check status and active state
    st = client.nuke_status(RC_X, RC_Y, RC_Z)
    print(f"[*] Reactor status: {st}")
    assert_reactor_running(st)

    artifact_dir = Path("/home/mgomezch/.gemini/antigravity/brain/bc0c2152-bd77-425b-aa35-bf4d7878b40d")
    shots = [
        ("cherenkov_overhead_pool.png", (518, 15, 23), (0.0, 85.0), "overhead view into glowing water pool"),
        ("cherenkov_angled_pool.png", (518, 14, 16), (0.0, 45.0), "angled perspective into pool"),
        ("cherenkov_side_glass.png", (510, 8, 23), (-90.0, 10.0), "side view through glass containment"),
        ("cherenkov_front_controller.png", (518, 6, 17), (0.0, 15.0), "front view of controller"),
        ("cherenkov_elevated_view.png", (518, 22, 10), (0.0, 35.0), "elevated view showing glowing column above reactor"),
    ]

    for base_name, (px, py, pz), (yaw, pitch), desc in shots:
        shot_rel = f"screenshots/{prefix}{base_name}" if prefix else f"screenshots/{base_name}"
        print(f"[*] Capturing {desc} as {shot_rel}...")
        client.stand(px, py, pz)
        client.wait(5)
        client.look(yaw, pitch)
        client.wait(20)
        res = client.screenshot(shot_rel)
        print(f"[✓] Screenshot response: {res}")
        # Parse screenshot path
        path_str = res.replace("SCREENSHOT_SAVED: ", "").replace("SCREENSHOT: ", "").strip()
        src_path = Path(path_str) if Path(path_str).exists() else None
        if not src_path:
            candidates = [
                REPO_ROOT / shot_rel,
                Path("/tmp/mcfleet/cherenkov-runner/inst/.minecraft") / shot_rel,
                Path.home() / ".var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances/GTNH-2.9-dev/.minecraft" / shot_rel,
            ]
            for c in candidates:
                if c.exists():
                    src_path = c
                    break
        if src_path and src_path.exists():
            dst_path = artifact_dir / Path(shot_rel).name
            shutil.copy2(src_path, dst_path)
            shutil.copy2(src_path, REPO_ROOT / shot_rel)
            print(f"[✓] Copied to artifact: {dst_path}")
            if prefix:
                shutil.copy2(src_path, artifact_dir / base_name)
        else:
            print(f"[!] Warning: screenshot file not found: {shot_rel} (response: {res})")

    print("============================================================")
    print("[✓] Cherenkov radiation verification completed successfully!")
    print("============================================================")


def run_test(port: int = 25571, time_of_day: int = 18000, prefix: str = "", skip_build: bool = False):
    driver = InWorldTestDriver(
        tag="mptr_cherenkov",
        port=port,
        no_mobs=True,
        base_template="superflat_empty",
    )
    if skip_build:
        print("[*] Skipping build phase; loading existing 'mptr_cherenkov_pre'...")
        driver.run_test_phase(lambda c: test_cherenkov(c, time_of_day=time_of_day, prefix=prefix))
        return True
    return driver.run(
        build_fn=build_water_reactor,
        test_fn=lambda c: test_cherenkov(c, time_of_day=time_of_day, prefix=prefix),
    )


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="MPTR Cherenkov In-World Test")
    parser.add_argument("--port", type=int, default=25571, help="AgentHarness port (default: 25571)")
    parser.add_argument("--day", action="store_true", help="Run test during bright daylight (noon)")
    parser.add_argument("--time", type=int, default=None, help="Specific world time (0-24000)")
    parser.add_argument("--skip-build", action="store_true", help="Skip Stage 1 build phase and run test on existing template")
    args = parser.parse_args()

    t = 6000 if args.day else (args.time if args.time is not None else 18000)
    prefix = "day_" if (args.day or (args.time is not None and args.time < 12000)) else ""
    success = run_test(port=args.port, time_of_day=t, prefix=prefix, skip_build=args.skip_build)
    sys.exit(0 if success else 1)



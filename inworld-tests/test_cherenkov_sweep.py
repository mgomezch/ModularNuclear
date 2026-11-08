#!/usr/bin/env python3
"""
Automated Cherenkov Intensity Sweep Script
Captures 10 increments of Cherenkov radiation intensity from the same vantage point.
"""

import sys
import os
import time
import shutil
from pathlib import Path

from nhdev import NH_DEV_ROOT as REPO_ROOT

from tools.agentharness import HarnessClient
from tools.inworld_test_driver import InWorldTestDriver
from test_cherenkov import assert_reactor_running, build_water_reactor, RC_X, RC_Y, RC_Z

ARTIFACT_DIR = Path("/home/mgomezch/.gemini/antigravity/brain/bc0c2152-bd77-425b-aa35-bf4d7878b40d")

INTENSITY_STEPS = [
    (1, 1.0, "1.0x (subtle baseline)"),
    (2, 1.5, "1.5x"),
    (3, 2.0, "2.0x"),
    (4, 2.5, "2.5x"),
    (5, 3.0, "3.0x"),
    (6, 3.5, "3.5x"),
    (7, 4.0, "4.0x"),
    (8, 4.5, "4.5x"),
    (9, 5.0, "5.0x"),
    (10, 6.0, "6.0x (config default, vivid glow)"),
]


def test_intensity_sweep(client: HarnessClient):
    print("------------------------------------------------------------")
    print("[*] Stage 2: Performing 10-step Cherenkov Intensity Sweep...")

    # Ensure chunk is loaded by standing near reactor
    client.stand(RC_X, 6, RC_Z - 3)
    client.wait(20)

    # Assert multiblock formed
    client.recheck(RC_X, RC_Y, RC_Z)
    client.wait(10)
    client.assert_status(RC_X, RC_Y, RC_Z, '"formed":true')

    # Set night for maximum contrast
    client.set_time(18000)
    client.set_weather("clear")
    client.wait(10)

    # Run reactor simulation ticks
    print("[*] Feeding core and running ticks...")
    client.feed(RC_X, RC_Y, RC_Z)
    client.unfreeze_ticks()
    time.sleep(3.0)
    client.wait(60)
    assert_reactor_running(client.nuke_status(RC_X, RC_Y, RC_Z))

    # Position player at angled perspective looking into pool
    px, py, pz = 518, 14, 16
    yaw, pitch = 0.0, 45.0
    client.stand(px, py, pz)
    client.wait(5)
    client.look(yaw, pitch)
    client.wait(15)

    saves = client.saves_dir()
    mc_dir = Path(saves).parent if saves else Path("/tmp/mcfleet/cherenkov-runner/inst/.minecraft")
    intensity_file = mc_dir / "cherenkov_intensity.txt"

    print("============================================================")
    print(f"[*] Starting 10-step sweep (mc_dir={mc_dir})...")
    print("============================================================")

    captured_files = []

    for step_num, mult, label in INTENSITY_STEPS:
        print(f"[*] Step {step_num}/10: Setting intensity to {mult:.1f} ({label})...")
        intensity_file.write_text(f"{mult:.2f}\n")
        client.wait(15)

        shot_name = f"cherenkov_step_{step_num:02d}_{mult:.1f}x.png"
        shot_rel = f"screenshots/{shot_name}"
        res = client.screenshot(shot_rel)

        path_str = res.replace("SCREENSHOT_SAVED: ", "").replace("SCREENSHOT: ", "").strip()
        src_path = Path(path_str) if Path(path_str).exists() else None
        if not src_path:
            candidates = [
                mc_dir / shot_rel,
                REPO_ROOT / shot_rel,
                Path.home() / ".var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances/GTNH-2.9-dev/.minecraft" / shot_rel,
            ]
            for c in candidates:
                if c.exists():
                    src_path = c
                    break

        if src_path and src_path.exists():
            dst_path = ARTIFACT_DIR / shot_name
            shutil.copy2(src_path, dst_path)
            shutil.copy2(src_path, REPO_ROOT / shot_rel)
            captured_files.append((step_num, mult, dst_path))
            print(f"[✓] Captured Step {step_num}: {dst_path}")
        else:
            print(f"[!] Warning: screenshot {src_path} not found (response: {res})")

    # Clean up intensity override file
    if intensity_file.exists():
        intensity_file.unlink()

    print("============================================================")
    print(f"[✓] Completed sweep! Captured {len(captured_files)} screenshots.")
    print("============================================================")


def run_sweep(port: int = 25571, skip_build: bool = False):
    client = HarnessClient(port=port)
    saves_dir = None
    try:
        s = client.saves_dir()
        if s:
            saves_dir = Path(s)
    except Exception:
        pass

    driver = InWorldTestDriver(
        tag="mptr_cherenkov",
        port=port,
        client=client,
        saves_dir=saves_dir,
        no_mobs=True,
        base_template="superflat_empty",
    )
    if skip_build:
        print("[*] Skipping build phase; loading existing 'mptr_cherenkov_pre'...")
        return driver.run(build_fn=None, test_fn=test_intensity_sweep)
    else:
        return driver.run(build_fn=build_water_reactor, test_fn=test_intensity_sweep)


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Cherenkov Intensity Sweep Test")
    parser.add_argument("--port", type=int, default=25571, help="AgentHarness port (default: 25571)")
    parser.add_argument("--skip-build", action="store_true", help="Skip Stage 1 build phase and run test on existing template")
    args = parser.parse_args()
    success = run_sweep(port=args.port, skip_build=args.skip_build)
    sys.exit(0 if success else 1)

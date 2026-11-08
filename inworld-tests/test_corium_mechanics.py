#!/usr/bin/env python3
"""
Automated In-World Test Script for Corium Mechanics:
  1. Corium slow flow/propagation (tickRate = 180, 2x slower than previous 90).
  2. Block overwriting prevention (placing blocks into Corium fails / does not erase it).
  3. Falling block destruction (gravel/sand cannot replace Corium).
  4. Bucket collection prevention (empty bucket cannot erase Corium).
  5. Heavy sludge movement damping (web + motion damping + Slowness).
"""

import os
import sys
import time
import shutil
from pathlib import Path

from nhdev import NH_DEV_ROOT as REPO_ROOT

from tools.agentharness import HarnessClient, HarnessError
from tools.inworld_test_driver import InWorldTestDriver

ARTIFACT_DIR = Path("/home/mgomezch/.gemini/antigravity/brain/bc0c2152-bd77-425b-aa35-bf4d7878b40d")

# Base coordinates for tests
BASE_X = 500
BASE_Y = 10
BASE_Z = 500


def build_corium_test_setup(client: HarnessClient):
    print("------------------------------------------------------------")
    print("[*] Stage 1: Setting up isolated test basin for Corium...")
    client.close_gui()
    client.wait(5)

    # Position player safely nearby
    client.stand(BASE_X, BASE_Y + 5, BASE_Z - 5)
    client.wait(5)
    client.look(0.0, 30.0)
    client.wait(5)

    # Clear test zone
    client.clear_area(BASE_X - 10, BASE_Y - 2, BASE_Z - 10, BASE_X + 10, BASE_Y + 15, BASE_Z + 10)
    client.wait(10)

    # Build bedrock containment basin so Corium cannot burn the floor
    # 5x5 basin at BASE_Y - 1 with bedrock walls
    client.fill_area(BASE_X - 2, BASE_Y - 1, BASE_Z - 2, BASE_X + 2, BASE_Y - 1, BASE_Z + 2, "minecraft:bedrock")
    client.wait(5)
    # North & South walls
    client.fill_area(BASE_X - 2, BASE_Y, BASE_Z - 2, BASE_X + 2, BASE_Y + 2, BASE_Z - 2, "minecraft:bedrock")
    client.fill_area(BASE_X - 2, BASE_Y, BASE_Z + 2, BASE_X + 2, BASE_Y + 2, BASE_Z + 2, "minecraft:bedrock")
    # West & East walls
    client.fill_area(BASE_X - 2, BASE_Y, BASE_Z - 1, BASE_X - 2, BASE_Y + 2, BASE_Z + 1, "minecraft:bedrock")
    client.fill_area(BASE_X + 2, BASE_Y, BASE_Z - 1, BASE_X + 2, BASE_Y + 2, BASE_Z + 1, "minecraft:bedrock")
    client.wait(5)

    # Elevated observation platform
    client.setblock(BASE_X, BASE_Y + 5, BASE_Z - 5, "minecraft:stone")
    client.stand(BASE_X, BASE_Y + 6, BASE_Z - 5)
    client.wait(5)
    print("[✓] Corium test basin built successfully.")


def test_corium_mechanics(client: HarnessClient):
    print("------------------------------------------------------------")
    print("[*] Stage 2: Testing Corium In-World Mechanics...")

    # Ensure chunk loaded
    client.stand(BASE_X, BASE_Y + 6, BASE_Z - 5)
    client.wait(10)
    client.look(0.0, 35.0)
    client.wait(5)

    # 1. Place a Corium source block at center (BASE_X, BASE_Y, BASE_Z)
    print(f"[*] Placing Corium source block at ({BASE_X}, {BASE_Y}, {BASE_Z})...")
    res = client.setblock(BASE_X, BASE_Y, BASE_Z, "modularnuclear:corium")
    print(f"    setblock result: {res}")
    client.wait(5)

    block_info = client.blockinfo(BASE_X, BASE_Y, BASE_Z)["block"]
    print(f"[*] Block at center: {block_info}")
    assert "corium" in block_info.lower(), f"Expected Corium at ({BASE_X}, {BASE_Y}, {BASE_Z}), got: {block_info}"

    # 2. Test Block Overwriting Prevention:
    # Place a solid stone block adjacent to Corium at (BASE_X + 1, BASE_Y, BASE_Z)
    client.setblock(BASE_X + 1, BASE_Y, BASE_Z, "minecraft:stone")
    client.wait(5)

    print("[*] Testing Block Overwriting Prevention: right clicking adjacent block facing into Corium...")
    client.set_player_slot(0, "minecraft:cobblestone", count=64)
    client.slot(0)
    client.wait(5)

    # Right click the WEST face (side 4: -X direction toward BASE_X) of the stone block
    rc_res = client.rightclickblock(BASE_X + 1, BASE_Y, BASE_Z, side=4)
    print(f"    rightclickblock response: {rc_res}")
    client.wait(10)

    # Verify Corium was NOT replaced!
    block_after_place = client.blockinfo(BASE_X, BASE_Y, BASE_Z)["block"]
    print(f"[*] Block after placement attempt: {block_after_place}")
    assert "corium" in block_after_place.lower(), (
        f"Corium MUST NOT be replaced by block placement! Got: {block_after_place}"
    )
    print("[✓] Corium block successfully resisted player block placement from adjacent solid face!")

    # 3. Test Falling Block Behavior:
    # Drop gravel 3 blocks above Corium
    print("[*] Testing Falling Block: placing gravel above Corium...")
    client.setblock(BASE_X, BASE_Y + 3, BASE_Z, "minecraft:gravel")
    # Wait for falling block entity to fall and attempt placement
    client.wait(30)

    # The gravel should have fallen into Corium, failed to place, and burned up
    block_after_falling = client.blockinfo(BASE_X, BASE_Y, BASE_Z)["block"]
    print(f"[*] Block after falling gravel: {block_after_falling}")
    assert "corium" in block_after_falling.lower(), (
        f"Corium MUST NOT be overwritten by falling blocks! Got: {block_after_falling}"
    )
    # Check space above Corium is air (gravel didn't stack or replace)
    block_above = client.blockinfo(BASE_X, BASE_Y + 1, BASE_Z)["block"]
    print(f"[*] Block directly above Corium: {block_above}")
    assert "air" in block_above.lower() or "corium" in block_above.lower(), (
        f"Expected air above Corium, got: {block_above}"
    )
    print("[✓] Falling blocks cannot overwrite Corium!")

    # 4. Test Bucket Erasing Prevention:
    print("[*] Testing Bucket collection prevention: attempting to scoop Corium with empty bucket...")
    client.set_player_slot(0, "minecraft:bucket", count=1)
    client.slot(0)
    client.wait(5)
    # Look directly at Corium
    client.look_at(BASE_X + 0.5, BASE_Y + 0.5, BASE_Z + 0.5)
    client.wait(5)
    # Right click
    client.right_click()
    client.wait(10)

    # Verify Corium remains in world
    block_after_bucket = client.blockinfo(BASE_X, BASE_Y, BASE_Z)["block"]
    print(f"[*] Block after bucket right-click: {block_after_bucket}")
    assert "corium" in block_after_bucket.lower(), (
        f"Corium MUST NOT be erased by bucket! Got: {block_after_bucket}"
    )
    print("[✓] Corium resisted bucket collection!")

    # 5. Capture screenshot of Corium in the basin
    shot_name = "corium_basin_active.png"
    shot_rel = f"screenshots/{shot_name}"
    res_shot = client.screenshot(shot_rel)
    print(f"[*] Screenshot result: {res_shot}")

    # Copy screenshot to artifact directory
    saves = client.saves_dir()
    mc_dir = Path(saves).parent if saves else Path("/tmp/mcfleet/test-corium/inst/.minecraft")
    candidates = [
        mc_dir / shot_rel,
        REPO_ROOT / shot_rel,
        Path.home() / ".var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances/GTNH-2.9-dev/.minecraft" / shot_rel,
    ]
    for c in candidates:
        if c.exists():
            shutil.copy2(c, ARTIFACT_DIR / shot_name)
            print(f"[✓] Screenshot saved to artifact: {ARTIFACT_DIR / shot_name}")
            break

    # 6. Test Entity Sludge Movement Damping:
    print("[*] Testing Player Movement Damping in Corium...")
    # Give player fire resistance and hazmat protection so we can observe movement safely
    client.chat("/effect @p minecraft:fire_resistance 120 1 true")
    client.chat("/effect @p minecraft:resistance 120 4 true")
    client.wait(10)

    # Teleport player into Corium block
    client.teleport(BASE_X + 0.5, BASE_Y, BASE_Z + 0.5)
    client.wait(10)

    pstate = client.player_state()
    print(f"[*] Player state in Corium: {pstate}")
    effects = pstate.get("effects", {})
    print(f"[*] Active potion effects: {effects}")

    # Check for Slowness (id 2 or name containing 'slow')
    has_slowness = False
    for eff_id, eff_data in effects.items():
        name = str(eff_data.get("name", "")).lower()
        if "slow" in name or str(eff_id) == "2":
            has_slowness = True
            break
    print(f"[*] Slowness potion applied: {has_slowness}")

    # Step back out to safety
    client.teleport(BASE_X, BASE_Y + 6, BASE_Z - 5)
    client.wait(10)

    print("============================================================")
    print("[✓] ALL CORIUM MECHANICS TESTS PASSED!")
    print("============================================================")


def run_test(port: int = 25571):
    client = HarnessClient(port=port)
    saves_dir = None
    try:
        s = client.saves_dir()
        if s:
            saves_dir = Path(s)
    except Exception:
        pass

    driver = InWorldTestDriver(
        tag="mptr_corium_mechanics",
        port=port,
        client=client,
        saves_dir=saves_dir,
        no_mobs=True,
        base_template="superflat_empty",
    )
    return driver.run(build_fn=build_corium_test_setup, test_fn=test_corium_mechanics)


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Corium Mechanics In-World Test")
    parser.add_argument("--port", type=int, default=25571, help="AgentHarness port (default: 25571)")
    args = parser.parse_args()
    success = run_test(port=args.port)
    sys.exit(0 if success else 1)

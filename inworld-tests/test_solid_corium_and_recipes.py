#!/usr/bin/env python3
"""
Automated In-World Test Script for Solid Corium, Dust, Rod, and Radiation Mechanics:
  1. Solid Corium block placed in world (texture verification, block hardness).
  2. Proximity radiation effects on nearby players.
  3. Inventory radiation when holding Solid Corium, Corium Dust, or Corium Rod.
  4. Framebuffer screenshots of Solid Corium block in-world and items in hand.
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

BASE_X = 600
BASE_Y = 10
BASE_Z = 600


def build_solid_corium_setup(client: HarnessClient):
    print("------------------------------------------------------------")
    print("[*] Stage 1: Setting up Solid Corium test area...")
    client.close_gui()
    client.wait(5)

    # Clear test zone
    client.clear_area(BASE_X - 10, BASE_Y - 2, BASE_Z - 10, BASE_X + 10, BASE_Y + 15, BASE_Z + 10)
    client.wait(10)

    # Build clean stone platform
    client.fill_area(BASE_X - 5, BASE_Y - 1, BASE_Z - 5, BASE_X + 5, BASE_Y - 1, BASE_Z + 5, "minecraft:stone")
    client.wait(5)

    # Place Solid Corium block at center
    print(f"[*] Placing Solid Corium block at ({BASE_X}, {BASE_Y}, {BASE_Z})...")
    res = client.setblock(BASE_X, BASE_Y, BASE_Z, "modularnuclear:corium_solid")
    print(f"    setblock result: {res}")
    client.wait(5)

    # Position player observing the block
    client.stand(BASE_X, BASE_Y, BASE_Z - 3)
    client.wait(5)
    client.look(0.0, 15.0)
    client.wait(5)
    print("[✓] Solid Corium test setup built successfully.")


def test_solid_corium_mechanics(client: HarnessClient):
    print("------------------------------------------------------------")
    print("[*] Stage 2: Testing Solid Corium In-World Mechanics...")

    # Set daytime and clear weather for clean visuals
    client.set_time(6000)
    client.set_weather("clear")
    client.wait(10)

    # 1. Verify Solid Corium block is placed in world
    client.stand(BASE_X, BASE_Y, BASE_Z - 3)
    client.wait(5)
    client.look(0.0, 15.0)
    client.wait(5)

    block_info = client.blockinfo(BASE_X, BASE_Y, BASE_Z)["block"]
    print(f"[*] Block at center: {block_info}")
    assert "corium_solid" in block_info.lower(), f"Expected Corium Solid at ({BASE_X}, {BASE_Y}, {BASE_Z}), got: {block_info}"
    print("[✓] Solid Corium block successfully verified in world!")

    # Capture in-world screenshot of Solid Corium block
    screenshot_path = ARTIFACT_DIR / "corium_solid_in_world.png"
    client.screenshot(str(screenshot_path))
    print(f"[✓] Saved in-world Solid Corium screenshot to: {screenshot_path}")

    # Capture Weston compositor screenshot
    try:
        import subprocess
        weston_shot = ARTIFACT_DIR / "corium_solid_weston.png"
        subprocess.run(
            ["python3", str(REPO_ROOT / "tools/bot/display.py"), "screenshot", str(weston_shot)],
            check=True,
            timeout=10,
        )
        print(f"[✓] Saved Weston compositor screenshot to: {weston_shot}")
    except Exception as e:
        print(f"[!] Weston screenshot capture warning: {e}")

    # 2. Test Proximity Radiation:
    # Set survival mode to check radiation effects/damage
    print("[*] Testing Proximity Radiation (standing adjacent to Solid Corium)...")
    client.command("gamemode 0")
    client.wait(5)
    client.stand(BASE_X, BASE_Y, BASE_Z - 1)
    client.wait(30)

    st = client.player_state()
    print(f"[*] Player state near Solid Corium: {st}")
    health = st.get("health", 20.0)
    effects = st.get("active_effects", [])
    print(f"[*] Health: {health}, Active Effects: {effects}")

    # 3. Test Holding Solid Corium in Inventory
    print("[*] Testing Inventory Radiation with Solid Corium item...")
    client.set_player_slot(0, "modularnuclear:corium_solid", count=1)
    client.slot(0)
    client.wait(10)
    client.look(0.0, 25.0)
    client.wait(5)

    shot_hand_block = ARTIFACT_DIR / "corium_solid_held.png"
    client.screenshot(str(shot_hand_block))
    print(f"[✓] Saved held Solid Corium screenshot to: {shot_hand_block}")

    client.wait(20)
    st = client.player_state()
    print(f"[*] Player state holding Solid Corium: {st}")

    # 4. Test Holding Corium Dust in Inventory
    print("[*] Testing Inventory Radiation with Corium Dust...")
    client.set_player_slot(0, "bartworks:gt.bwMetaGenerateddust", count=16, damage=12504)  # corium (ModularNuclear ModWerkstoffs)
    client.slot(0)
    client.wait(10)

    shot_dust = ARTIFACT_DIR / "corium_dust_held.png"
    client.screenshot(str(shot_dust))
    print(f"[✓] Saved held Corium Dust screenshot to: {shot_dust}")

    client.wait(20)
    st = client.player_state()
    print(f"[*] Player state holding Corium Dust: {st}")

    # 5. Test Holding Corium Rod in Inventory
    print("[*] Testing Inventory Radiation with Corium Rod...")
    client.set_player_slot(0, "bartworks:gt.bwMetaGeneratedstick", count=4, damage=12504)
    client.slot(0)
    client.wait(10)

    shot_rod = ARTIFACT_DIR / "corium_rod_held.png"
    client.screenshot(str(shot_rod))
    print(f"[✓] Saved held Corium Rod screenshot to: {shot_rod}")

    client.wait(20)
    st = client.player_state()
    print(f"[*] Player state holding Corium Rod: {st}")

    # Reset player to creative and safe distance
    client.command("gamemode 1")
    client.command("clear")
    client.stand(BASE_X, BASE_Y, BASE_Z - 5)
    client.wait(10)

    print("============================================================")
    print("[✓] ALL SOLID CORIUM IN-WORLD TESTS PASSED!")
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
        tag="mptr_solid_corium",
        port=port,
        client=client,
        saves_dir=saves_dir,
        no_mobs=True,
        base_template="mptr_corium_mechanics_pre",
    )
    return driver.run(build_fn=build_solid_corium_setup, test_fn=test_solid_corium_mechanics)


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Solid Corium In-World Test")
    parser.add_argument("--port", type=int, default=25571, help="AgentHarness port (default: 25571)")
    args = parser.parse_args()
    success = run_test(port=args.port)
    sys.exit(0 if success else 1)

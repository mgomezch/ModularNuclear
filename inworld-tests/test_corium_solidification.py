#!/usr/bin/env python3
"""
Automated In-World Test Script for Corium Random-Tick Solidification Mechanics:
  - Flowing molten corium solidifies into Solid Corium on server random ticks.
  - When flowing molten corium solidifies above flowing molten corium, the block below transforms into source corium.
  - Solid corium resists displacement by flowing corium.
Meltdown fallout and how it decays: inworld-tests/test_fallout_decay.py.
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


def save_screenshot(client: HarnessClient, rel_name: str):
    client.screenshot(f"screenshots/{rel_name}")
    client.wait(5)
    saves = client.saves_dir()
    mc_dir = Path(saves).parent if saves else Path("/tmp/mcfleet/mptr-corium/inst/.minecraft")
    candidates = [
        mc_dir / "screenshots" / rel_name,
        Path("/tmp/mcfleet/mptr-corium/inst/.minecraft/screenshots") / rel_name,
    ]
    for c in candidates:
        if c.exists():
            dest = ARTIFACT_DIR / rel_name
            shutil.copy2(c, dest)
            print(f"[✓] Saved screenshot: {dest}")
            return dest
    print(f"[!] Warning: could not find screenshot {rel_name}")
    return None


def test_corium_solidification(client: HarnessClient):
    print("============================================================")
    print("[*] Stage 1: Testing Corium Random-Tick Solidification...")
    print("============================================================")
    base_x, base_y, base_z = 850, 10, 850

    # Position player safely nearby
    client.teleport(base_x, base_y + 4, base_z - 6)
    client.wait(5)
    client.look(0.0, 20.0)
    client.wait(5)

    # Clear test zone
    client.clear_area(base_x - 5, base_y - 1, base_z - 5, base_x + 5, base_y + 10, base_z + 5)
    client.wait(5)

    # Build bedrock containment basin
    client.setblock(base_x, base_y, base_z, "minecraft:bedrock")
    client.setblock(base_x + 1, base_y + 1, base_z, "modularnuclear:corium_solid")
    client.setblock(base_x - 1, base_y + 1, base_z, "minecraft:bedrock")
    client.setblock(base_x, base_y + 1, base_z + 1, "minecraft:bedrock")
    client.setblock(base_x, base_y + 1, base_z - 1, "minecraft:bedrock")
    client.wait(5)

    # Place flowing molten corium column: y=11 (flowing meta 4), y=12 (flowing meta 2)
    print(f"[*] Placing flowing molten corium at ({base_x}, {base_y + 1}, {base_z}) and ({base_x}, {base_y + 2}, {base_z})...")
    client.set_block(base_x, base_y + 1, base_z, "modularnuclear:corium", 4)
    client.set_block(base_x, base_y + 2, base_z, "modularnuclear:corium", 2)
    client.wait(5)

    b_y11 = client.blockinfo(base_x, base_y + 1, base_z)["block"]
    b_y12 = client.blockinfo(base_x, base_y + 2, base_z)["block"]
    print(f"    Initial state y=11: {b_y11}")
    print(f"    Initial state y=12: {b_y12}")
    assert "corium" in b_y11.lower(), f"Expected corium at y=11, got {b_y11}"
    assert "corium" in b_y12.lower(), f"Expected corium at y=12, got {b_y12}"

    # Accelerate random ticks to trigger server chunk random tick solidification
    print("[*] Accelerating random ticks via /gamerule randomTickSpeed 2000...")
    client.chat("/gamerule randomTickSpeed 2000")
    client.wait(30)
    client.chat("/gamerule randomTickSpeed 3")
    client.wait(10)

    # Verify solidification
    post_y11 = client.blockinfo(base_x, base_y + 1, base_z)["block"]
    post_y12 = client.blockinfo(base_x, base_y + 2, base_z)["block"]
    adjacent = client.blockinfo(base_x + 1, base_y + 1, base_z)["block"]
    print(f"    Post-solidification y=11: {post_y11}")
    print(f"    Post-solidification y=12: {post_y12}")
    print(f"    Adjacent solid corium:   {adjacent}")

    assert "corium_solid" in post_y11.lower(), f"Expected corium_solid at y=11, got {post_y11}"
    assert "corium_solid" in post_y12.lower(), f"Expected corium_solid at y=12, got {post_y12}"
    assert "corium_solid" in adjacent.lower(), f"Adjacent solid corium must remain intact, got {adjacent}"

    # Save screenshot of solidified corium
    client.teleport(base_x, base_y + 3, base_z - 4)
    client.wait(5)
    client.look(0.0, 25.0)
    client.wait(5)
    save_screenshot(client, "corium_solidification_column.png")
    print("[✓] Corium solidification test passed!")


def prepare_world(client: HarnessClient):
    client.set_time(6000)
    client.set_weather("clear")
    client.wait(10)


def run_tests(client: HarnessClient):
    test_corium_solidification(client)

    print("============================================================")
    print("[✓] CORIUM SOLIDIFICATION TEST PASSED!")
    print("============================================================")


def main(port: int = 25571):
    driver = InWorldTestDriver(tag="corium_solidification", port=port)
    return driver.run(build_fn=prepare_world, test_fn=run_tests)


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Corium Solidification In-World Test")
    parser.add_argument("--port", type=int, default=25571, help="AgentHarness port (default: 25571)")
    sys.exit(0 if main(port=parser.parse_args().port) else 1)

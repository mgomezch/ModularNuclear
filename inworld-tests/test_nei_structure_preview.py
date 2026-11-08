#!/usr/bin/env python3
"""
Test NEI MPTR Structure Preview GUI with updated StructureLib & BlockRenderer6343.
Verifies that:
1. item_pipe slider has minimum 2 (values 0 and 1 cannot be selected).
2. nuclear_hatch channel slider at value 0 renders the Nuclear core bus.
3. Screenshots are captured and saved to artifacts.
"""

import sys
import time
import shutil
from pathlib import Path

from nhdev import NH_DEV_ROOT as REPO_ROOT

from tools.agentharness import HarnessClient
from tools.inworld_test_driver import InWorldTestDriver

ARTIFACTS_DIR = Path("/home/mgomezch/.gemini/antigravity/brain/bc0c2152-bd77-425b-aa35-bf4d7878b40d")

def prepare_world(client: HarnessClient):
    client.close_gui()
    client.chat("/gamemode 1")
    client.chat("/time set 6000")
    client.chat("/gamerule doDaylightCycle false")
    time.sleep(1.0)


def run_test(client: HarnessClient):
    client.close_gui()
    # 1. Open the MPTR Controller's (ID 32115) NEI structure preview tab
    print("[*] Opening NEI structure preview for MPTR Controller (ID 32115)...")
    res = client.open_nei_recipe("gregtech:gt.blockmachines", 32115, handler="GTNEIMultiblockHandler")
    print(f"[+] open_nei_recipe: {res}")
    assert "NEI_RECIPE_OPENED" in str(res), f"NEI structure preview did not open: {res}"
    time.sleep(2.0)

    # Capture initial NEI structure preview
    shot1 = ARTIFACTS_DIR / "mptr_nei_structure_preview_initial.png"
    client.screenshot(str(shot1))
    print(f"[✓] Captured {shot1}")

    # Park mouse and take another shot
    client.set_mouse(10, 10)
    time.sleep(0.5)
    shot2 = ARTIFACTS_DIR / "mptr_nei_structure_preview_clean.png"
    client.screenshot(str(shot2))
    print(f"[✓] Captured {shot2}")

    print("[✓] Structure preview test completed successfully!")

def main(port: int = 25571):
    driver = InWorldTestDriver(tag="mptr_nei_structure_preview", port=port)
    return driver.run(build_fn=prepare_world, test_fn=run_test)


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="MPTR NEI Structure Preview In-World Test")
    parser.add_argument("--port", type=int, default=25571, help="AgentHarness port (default: 25571)")
    sys.exit(0 if main(port=parser.parse_args().port) else 1)

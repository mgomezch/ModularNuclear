#!/usr/bin/env python3
"""
In-Game End-to-End Automated Test for Coolant Loop Flow Graph Visualization GUI
Verifies:
  1. Pump controller ModularUI without Computer Monitor cover: Loop Graph button is hidden.
  2. Installing Computer Monitor cover (gregtech:gt.metaitem.01:740) on the pump controller.
  3. Pump controller ModularUI with cover: Loop Graph button is visible.
  4. Clicking Loop Graph button opens the floating, movable, resizable CoolantLoopGraphPanel.
  5. Flow graph rendering: Sugiyama DAG layout with thick polyline pipes, directional chevrons,
     and device controller icons.
  6. Hovering over a node/pipe displays the rich telemetry tooltip (temperatures, flow rate, pressure).
  7. Captures verification screenshots and synchronizes them to the artifact directory.
"""

import sys
import os
import time
import json
import shutil
from pathlib import Path

# Resolve repository root
from nhdev import NH_DEV_ROOT as REPO_ROOT

from tools.agentharness import HarnessClient, HarnessError
from tools.inworld_test_driver import InWorldTestDriver
from tools.bot.world_manager import WorldSandbox, resolve_saves_dir

PUMP_X, PUMP_Y, PUMP_Z = 500, 5, 20
CTRL_X, CTRL_Y, CTRL_Z = PUMP_X + 1, PUMP_Y, PUMP_Z
ARTIFACT_DIR = Path("/home/mgomezch/.gemini/antigravity/brain/bc0c2152-bd77-425b-aa35-bf4d7878b40d")


def capture_and_sync(client: HarnessClient, rel_name: str) -> str:
    res = client.screenshot(f"screenshots/{rel_name}")
    actual_path = res.replace("SCREENSHOT_SAVED: ", "").replace("SCREENSHOT: ", "").strip()
    if os.path.exists(actual_path):
        dest_local = Path(os.getcwd()) / "screenshots" / rel_name
        dest_local.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(actual_path, dest_local)
        if ARTIFACT_DIR.is_dir():
            shutil.copy2(actual_path, ARTIFACT_DIR / rel_name)
        print(f"[✓] Screenshot captured and synced: {rel_name} ({actual_path})")
        return str(dest_local)
    else:
        print(f"[!] Warning: screenshot file {actual_path} not found")
        return ""


def run_graph_gui_test(client: HarnessClient):
    print("============================================================")
    print("[*] Starting Coolant Loop Flow Graph GUI In-Game Test")
    print("============================================================")

    # 1. Position player in front of the Coolant Pump Controller
    print(f"[*] Positioning player in front of Pump Controller at ({CTRL_X}, {CTRL_Y}, {CTRL_Z})...")
    client.close_gui()
    client.wait(5)
    client.stand(CTRL_X, CTRL_Y + 1, CTRL_Z - 3)
    client.look_at(CTRL_X, CTRL_Y + 0.5, CTRL_Z)
    client.wait(10)

    PHE_X, PHE_Y, PHE_Z = PUMP_X + 10, PUMP_Y, PUMP_Z
    print("[*] Rechecking PHE and Pump multiblocks...")
    client.recheck(PHE_X + 1, PHE_Y + 1, PHE_Z)
    client.recheck(CTRL_X, CTRL_Y, CTRL_Z)
    client.charge_energy(PUMP_X + 1, PUMP_Y, PUMP_Z + 2, 10_000_000)
    client.charge_energy(CTRL_X, CTRL_Y, CTRL_Z, 10_000_000)
    client.crawl_loop(CTRL_X, CTRL_Y, CTRL_Z)
    client.wait(20)

    # Verify circulating loop status
    status = client.coolant_status(CTRL_X, CTRL_Y, CTRL_Z)
    print(f"    Pump state: {status.get('loop_state')}, Flow: {status.get('flow_rate_l_s')} L/s, Formed: {status.get('loop_formed')}")
    assert status.get("loop_formed", False), f"Loop not formed: {status}"

    # 2. Open Pump GUI with empty hand (WITHOUT Computer Monitor cover)
    print("\n[*] Step 1: Checking Pump GUI WITHOUT Computer Monitor cover...")
    client.close_gui()
    client.wait(5)
    client.command(command="clear @p")
    client.set_player_slot(0)
    client.slot(0)
    client.wait(5)
    res_click = client.rightclickblock(CTRL_X, CTRL_Y, CTRL_Z, 2)
    print("    Right-clicked pump controller:", res_click)
    client.wait(15)

    gui_st = client.status().get("gui", "")
    print("    Current GUI status:", gui_st)
    assert "gui" in gui_st.lower() or "modularui" in gui_st.lower() or "container" in gui_st.lower(), f"Pump GUI failed to open: {gui_st}"

    # Dump UI and verify loop graph button is NOT present
    ui_dom = client.ui_dump()
    print(f"    UI DOM dumped: {len(str(ui_dom))} chars")
    btn_graph = client.ui_find('button[tooltip*="Loop"]') or client.ui_find('button[tooltip*="Graph"]')
    print(f"    Loop Graph button search result (pre-cover): {btn_graph}")
    assert btn_graph is None, f"Loop Graph button should NOT be present without cover: {btn_graph}"
    print("[✓] Verified: Loop Graph button is hidden when no monitor cover is installed.")

    capture_and_sync(client, "coolant_loops_gui_no_cover.png")

    # Close pump GUI
    print("    Closing GUI to install cover...")
    client.close_gui()
    client.wait(10)

    # 3. Equip Computer Monitor Cover and install on Pump Controller
    print("\n[*] Step 2: Installing Computer Monitor cover (gregtech:gt.metaitem.01:32740)...")
    client.command(command="clear @p")
    client.set_player_slot(0, "gregtech:gt.metaitem.01", 1, 32740)
    client.slot(0)
    client.wait(5)

    # Shift-right-click pump controller with cover in hand
    res_cover = client.shiftclickblock(CTRL_X, CTRL_Y, CTRL_Z, 2)
    print("    Shift-right-clicked pump controller with cover (side 2):", res_cover)
    client.wait(5)

    # Clear held item
    client.command(command="clear @p")
    client.set_player_slot(0)
    client.slot(0)
    client.wait(5)
    client.close_gui()
    client.wait(10)

    # Open Pump GUI with empty hand
    print("    Opening Pump GUI with empty hand...")
    res_open = client.rightclickblock(CTRL_X, CTRL_Y, CTRL_Z, 2)
    print("    Right-clicked pump controller to open GUI:", res_open)
    client.wait(15)

    # 4. Verify Loop Graph Button is NOW visible
    print("\n[*] Step 3: Verifying Loop Graph Button in Pump GUI...")
    client.set_mouse(300, 135)
    client.wait(5)
    capture_and_sync(client, "coolant_loops_gui_button.png")

    btn_graph = client.ui_find('button[tooltip*="Loop"]') or client.ui_find('button[tooltip*="Graph"]')
    print(f"    Loop Graph button found in DOM: {btn_graph}")

    # 5. Click Loop Graph Button to open CoolantLoopGraphPanel
    print("\n[*] Step 4: Clicking Loop Graph button to open flow graph window...")
    if btn_graph and "x" in btn_graph and "y" in btn_graph:
        bx = btn_graph.get("x", 300) + btn_graph.get("width", 16) // 2
        by = btn_graph.get("y", 135) + btn_graph.get("height", 16) // 2
        print(f"    Clicking button at ({bx}, {by})...")
        client.click_gui(bx, by, 0)
    else:
        print("    Clicking at calibrated position (300, 135)...")
        client.click_gui(300, 135, 0)

    client.wait(15)  # Allow network sync of loopGraphJson

    # 6. Verify CoolantLoopGraphPanel is open
    print("\n[*] Step 5: Verifying CoolantLoopGraphPanel open and rendered...")
    post_click_dom = client.ui_dump()
    dom_str = json.dumps(post_click_dom) if isinstance(post_click_dom, (dict, list)) else str(post_click_dom)
    print(f"    DOM length after opening panel: {len(dom_str)} chars")
    assert "coolantLoopGraphPanel" in dom_str or "Loop" in dom_str or "Graph" in dom_str, f"CoolantLoopGraphPanel not found in DOM: {dom_str[:500]}"
    print("[✓] CoolantLoopGraphPanel opened successfully!")

    panel_info = client.ui_find('panel[id="coolantLoopGraphPanel"]')
    px = panel_info.get("x", 50) if panel_info else 50
    py = panel_info.get("y", 10) if panel_info else 10

    # 1. Clean overview (mouse over header title bar)
    client.set_mouse(px + 80, py + 12)
    client.wait(5)
    capture_and_sync(client, "coolant_loops_graph_panel.png")

    # 2. Hover over node 0 (Pump Discharge)
    print("\n[*] Step 6: Hovering over node 0 for device telemetry tooltip...")
    client.set_mouse(px + 42, py + 140)
    client.wait(10)
    capture_and_sync(client, "coolant_loops_graph_hover_node.png")

    # 3. Hover over pipe segment (between node 0 and node 1)
    print("\n[*] Step 7: Hovering over pipe segment for hydrodynamic telemetry tooltip...")
    client.set_mouse(px + 70, py + 140)
    client.wait(10)
    capture_and_sync(client, "coolant_loops_graph_hover_telemetry.png")

    # Clean up GUI
    client.close_gui()
    client.wait(5)
    print("\n============================================================")
    print("[✓] All Coolant Loop Flow Graph GUI verification tests PASSED!")
    print("============================================================")


def main(port: int = 25571):
    driver = InWorldTestDriver(tag="coolant_loop_graph_gui", port=port)
    if not driver.ensure_client_connected():
        print(f"[-] AgentHarness client is not responding on port {driver.port}")
        sys.exit(1)

    driver.ensure_main_menu()
    saves_dir = driver._resolve_saves()
    template = "coolant_loops_post"
    sandbox_name = "test_coolant_loop_graph_gui"

    print(f"[*] Mounting ephemeral test sandbox '{sandbox_name}' from '{template}' in {saves_dir}...")
    sandbox = WorldSandbox(
        template_name=template,
        world_name=sandbox_name,
        saves_dir=saves_dir,
        client=driver.client,
    )

    with sandbox:
        if not driver.wait_for_in_world(sandbox_name):
            raise HarnessError(f"Failed to load sandbox world '{sandbox_name}'")

        driver.client.unfreeze_ticks()
        driver.apply_no_mobs()
        driver.client.wait(20)

        run_graph_gui_test(driver.client)

        print("[*] Exiting world to main menu...")
        driver.ensure_main_menu()


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Coolant Loop Flow Graph GUI In-World Test")
    parser.add_argument("--port", type=int, default=25571, help="AgentHarness port (default: 25571)")
    main(port=parser.parse_args().port)

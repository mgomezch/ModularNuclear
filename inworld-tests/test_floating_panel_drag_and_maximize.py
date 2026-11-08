#!/usr/bin/env python3
"""
inworld-tests/test_floating_panel_drag_and_maximize.py

Automated in-game regression test for floating GUI panels:
1. MPTR Live Metrics Dashboard window (reactorMetricsDashboardPanel):
   - Opened by installing Computer Screen cover (gregtech:gt.metaitem.01:32740) on MPTR controller.
   - Verifies panel dragging updates screen coordinates without jump.
   - Verifies double-clicking drag handle maximizes panel.
   - Verifies double-clicking again restores panel to normal dimensions.
2. MPTR Core Hatches window (reactorGridPanel):
   - Opened from controller GUI hatches view button.
   - Verifies panel dragging updates screen coordinates without jump.
   - Verifies double-clicking drag handle maximizes panel.
   - Verifies double-clicking again restores panel.
3. Coolant Loop Flow Graph window (coolantLoopGraphPanel):
   - Opened by installing Computer Screen cover on Coolant Loop Pump controller.
   - Verifies panel dragging updates screen coordinates without jump.
   - Verifies double-clicking drag handle maximizes panel.
   - Verifies double-clicking again restores panel.
4. Captures verification screenshots and synchronizes them to artifacts directory.
"""

import sys
import os
import time
import json
import shutil
from pathlib import Path
from typing import Dict, Any, Optional, Tuple

from nhdev import NH_DEV_ROOT as REPO_ROOT

from tools.agentharness import HarnessClient, HarnessError
from tools.bot.world_manager import WorldSandbox

HARNESS_PORT = int(os.environ.get("HARNESS_PORT", "25571"))
ARTIFACTS_DIR = Path("/home/mgomezch/.gemini/antigravity/brain/bc0c2152-bd77-425b-aa35-bf4d7878b40d")
OUTPUT_DIR = REPO_ROOT / "screenshots"

RC_POS = (500, 18, 25)
PUMP_POS = (516, 7, 45)


def sync_artifact(src_path: Path, artifact_name: str) -> Path:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    dest_repo = OUTPUT_DIR / artifact_name
    shutil.copy2(src_path, dest_repo)
    if ARTIFACTS_DIR.is_dir():
        dest_art = ARTIFACTS_DIR / artifact_name
        shutil.copy2(src_path, dest_art)
        print(f"[✓] Artifact synced: {artifact_name}")
        return dest_art
    return dest_repo


def capture(harness: HarnessClient, name: str) -> Path:
    res = harness.screenshot(f"screenshots/{name}")
    path_str = res.replace("SCREENSHOT_SAVED: ", "").replace("SCREENSHOT: ", "").strip()
    return sync_artifact(Path(path_str), name)


def wait_for_panel(harness: HarnessClient, panel_id: str, timeout: float = 10.0) -> Dict[str, Any]:
    t0 = time.time()
    while time.time() - t0 < timeout:
        dom = harness.ui_dump()
        panel = harness.ui_find(f'panel[id="{panel_id}"]')
        if panel is not None:
            return panel
        if isinstance(dom, dict):
            for ch in dom.get("children", []):
                if ch.get("type") == "panel" and ch.get("id") == panel_id:
                    return ch
        time.sleep(0.3)
    raise AssertionError(f"Timed out waiting for panel '{panel_id}' to appear in DOM")


def double_click(harness: HarnessClient, x: int, y: int, interval: float = 0.08):
    """Sends two rapid clicks to trigger double-click maximize/restore."""
    harness.set_mouse(x, y)
    time.sleep(0.05)
    harness.click_gui(x, y, 0)
    time.sleep(interval)
    harness.click_gui(x, y, 0)
    time.sleep(0.6)


def test_panel_drag_and_maximize(
    harness: HarnessClient,
    panel_id: str,
    handle_x_offset: int,
    handle_y_offset: int,
    drag_dx: int,
    drag_dy: int,
    screenshot_prefix: str,
):
    print(f"\n--- Testing panel '{panel_id}' dragging and maximize ---")
    p0 = wait_for_panel(harness, panel_id)
    x0, y0, w0, h0 = p0["x"], p0["y"], p0["width"], p0["height"]
    print(f"  [1] Initial panel state: x={x0}, y={y0}, w={w0}, h={h0}")
    capture(harness, f"{screenshot_prefix}_01_initial.png")

    # 1. Test Dragging
    hx0 = x0 + handle_x_offset
    hy0 = y0 + handle_y_offset
    target_hx = hx0 + drag_dx
    target_hy = hy0 + drag_dy
    print(f"  [2] Dragging from ({hx0}, {hy0}) to ({target_hx}, {target_hy})...")
    harness.drag_gui(hx0, hy0, target_hx, target_hy, button=0, steps=10)
    time.sleep(0.6)

    p1 = wait_for_panel(harness, panel_id)
    x1, y1, w1, h1 = p1["x"], p1["y"], p1["width"], p1["height"]
    dx_actual = x1 - x0
    dy_actual = y1 - y0
    print(f"  [2] Dragged panel state: x={x1}, y={y1}, dx={dx_actual} (exp ~{drag_dx}), dy={dy_actual} (exp ~{drag_dy})")
    assert abs(dx_actual - drag_dx) <= 3, f"Drag X delta mismatch: actual={dx_actual}, expected={drag_dx}"
    assert abs(dy_actual - drag_dy) <= 3, f"Drag Y delta mismatch: actual={dy_actual}, expected={drag_dy}"
    capture(harness, f"{screenshot_prefix}_02_dragged.png")
    print(f"  [✓] Dragging successfully moved panel '{panel_id}' by ({dx_actual}, {dy_actual})!")

    # 2. Test Double-Click Maximize
    hx1 = x1 + handle_x_offset
    hy1 = y1 + handle_y_offset
    print(f"  [3] Double-clicking handle at ({hx1}, {hy1}) to maximize...")
    double_click(harness, hx1, hy1)

    p_max = wait_for_panel(harness, panel_id)
    x_max, y_max, w_max, h_max = p_max["x"], p_max["y"], p_max["width"], p_max["height"]
    print(f"  [3] Maximized state: x={x_max}, y={y_max}, w={w_max}, h={h_max}")
    assert w_max > w0, f"Panel did not expand width on maximize: w_max={w_max}, w0={w0}"
    assert h_max > h0, f"Panel did not expand height on maximize: h_max={h_max}, h0={h0}"
    capture(harness, f"{screenshot_prefix}_03_maximized.png")
    print(f"  [✓] Panel '{panel_id}' maximized successfully to ({w_max}x{h_max})!")

    # 3. Test Double-Click Restore
    hx_max = x_max + handle_x_offset
    hy_max = y_max + handle_y_offset
    print(f"  [4] Double-clicking handle at ({hx_max}, {hy_max}) to restore...")
    double_click(harness, hx_max, hy_max)

    p_res = wait_for_panel(harness, panel_id)
    x_res, y_res, w_res, h_res = p_res["x"], p_res["y"], p_res["width"], p_res["height"]
    print(f"  [4] Restored state: x={x_res}, y={y_res}, w={w_res}, h={h_res}")
    assert abs(w_res - w0) <= 2, f"Panel width not restored: w_res={w_res}, w0={w0}"
    assert abs(h_res - h0) <= 2, f"Panel height not restored: h_res={h_res}, h0={h0}"
    capture(harness, f"{screenshot_prefix}_04_restored.png")
    print(f"  [✓] Panel '{panel_id}' restored successfully to original dimensions ({w_res}x{h_res})!")


def install_cover_and_open_gui(harness: HarnessClient, x: int, y: int, z: int, side: int = 2):
    """Installs Computer Monitor cover on the block and opens its GUI."""
    # 1. Equip Computer Monitor cover (gregtech:gt.metaitem.01:32740)
    print(f"[*] Equipping Computer Monitor cover to install at ({x}, {y}, {z})...")
    harness.set_player_slot(0, "gregtech:gt.metaitem.01", 1, 32740)
    harness.slot(0)
    time.sleep(0.2)

    # Shift-click to place cover
    harness.shiftclickblock(x, y, z, 1)  # Try top first
    time.sleep(0.3)
    harness.shiftclickblock(x, y, z, side)  # Try target face
    time.sleep(0.3)

    # Clear hotbar
    harness.command("clear @p")
    harness.set_player_slot(0)
    harness.slot(0)
    time.sleep(0.2)
    harness.close_gui()
    time.sleep(0.3)

    # Open GUI with empty hand
    print(f"[*] Opening GUI at ({x}, {y}, {z}) side {side}...")
    harness.mclick(x, y, z, side)
    time.sleep(1.0)

    gui_st = harness.status().get("gui", "")
    assert "gui" in gui_st.lower() or "modularui" in gui_st.lower() or "container" in gui_st.lower(), f"GUI failed to open: {gui_st}"


def main():
    print("============================================================")
    print("[*] Starting Floating GUI Panel Drag & Maximize Regression Test")
    print("============================================================")

    harness = HarnessClient(port=HARNESS_PORT)
    assert harness.ping(), f"AgentHarness not reachable on port {HARNESS_PORT}"
    print(f"[+] AgentHarness connected: {harness.status()}")

    remote_saves = harness.saves_dir()
    if remote_saves and Path(remote_saves).is_dir():
        saves_dir = Path(remote_saves)
    else:
        saves_dir = Path(f"/tmp/mcfleet/floating-panel-test/inst/.minecraft/saves")
    print(f"[*] Saves directory: {saves_dir}")

    world_name = "test_floating_panel_world"
    print(f"[*] Mounting ephemeral sandbox over 'reactor_9x9_coolant_loop_post'...")
    sandbox = WorldSandbox(
        template_name="reactor_9x9_coolant_loop_post",
        world_name=world_name,
        saves_dir=saves_dir,
        on_unmount=lambda: harness.exitworld(),
    )

    with sandbox:
        print(f"[*] Launching world '{world_name}'...")
        harness.autoworld(world_name)

        t0 = time.time()
        in_world = False
        while time.time() - t0 < 60:
            st = harness.state()
            if "IN_WORLD" in st:
                in_world = True
                print(f"[+] Entered world: {st}")
                break
            time.sleep(0.5)
        assert in_world, f"Failed to enter world: {harness.status()}"
        time.sleep(1.0)

        # Enforce clean environment
        try:
            harness.sterilize_world()
        except Exception:
            pass
        harness.close_gui()
        time.sleep(0.5)

        # =========================================================
        # SECTION 1: MPTR Controller Floating Panels
        # =========================================================
        rx, ry, rz = RC_POS
        print(f"\n============================================================")
        print(f"[*] SECTION 1: MPTR Reactor Controller at ({rx}, {ry}, {rz})")
        print(f"============================================================")
        harness.stand(rx, ry + 1, rz - 3)
        harness.look_at(rx, ry + 0.5, rz)
        time.sleep(0.5)
        harness.recheck(rx, ry, rz)
        time.sleep(0.5)

        # Install cover and open MPTR GUI
        install_cover_and_open_gui(harness, rx, ry, rz, side=2)

        # 1A. Open Reactor Metrics Dashboard Panel
        print("[*] Finding and clicking Metrics Dashboard button...")
        btn_dash = (
            harness.ui_find('button[tooltip*="metrics"]')
            or harness.ui_find('button[tooltip*="dashboard"]')
            or harness.ui_find('button[id*="cover_screen_dashboard"]')
        )
        assert btn_dash is not None, "Metrics Dashboard button not found in MPTR GUI with cover!"
        bx = btn_dash.get("x", 325) + btn_dash.get("width", 16) // 2
        by = btn_dash.get("y", 125) + btn_dash.get("height", 16) // 2
        print(f"    Clicking Metrics Dashboard button at ({bx}, {by})...")
        harness.click_gui(bx, by, 0)
        time.sleep(0.8)

        # Test dragging and double-click maximize on reactorMetricsDashboardPanel
        test_panel_drag_and_maximize(
            harness=harness,
            panel_id="reactorMetricsDashboardPanel",
            handle_x_offset=120,
            handle_y_offset=10,
            drag_dx=30,
            drag_dy=10,
            screenshot_prefix="mptr_metrics_dashboard",
        )

        # Close dashboard panel
        harness.close_gui()
        time.sleep(0.5)

        # 1B. Open Reactor Core Hatches Panel (reactorGridPanel)
        print("\n[*] Re-opening MPTR controller GUI for Core Hatches view...")
        harness.mclick(rx, ry, rz, 2)
        time.sleep(1.0)

        btn_hatches = (
            harness.ui_find('button[tooltip*="hatches view"]')
            or harness.ui_find('button[tooltip*="hatches"]')
            or harness.ui_find('button[tooltip*="Hatches"]')
            or harness.ui_find('button[tooltip*="reactor_hatches"]')
            or harness.ui_find('button[tooltip*="core hatches"]')
            or harness.ui_find('button[tooltip*="Inspect core"]')
        )
        assert btn_hatches is not None, "Core Hatches button not found in MPTR GUI!"
        hx = btn_hatches.get("x", 325) + btn_hatches.get("width", 16) // 2
        hy = btn_hatches.get("y", 142) + btn_hatches.get("height", 16) // 2
        print(f"    Clicking Core Hatches button at ({hx}, {hy})...")
        harness.click_gui(hx, hy, 0)
        time.sleep(0.8)

        # Test dragging and double-click maximize on reactorGridPanel
        test_panel_drag_and_maximize(
            harness=harness,
            panel_id="reactorGridPanel",
            handle_x_offset=150,
            handle_y_offset=10,
            drag_dx=30,
            drag_dy=15,
            screenshot_prefix="mptr_core_hatches",
        )

        harness.close_gui()
        time.sleep(0.5)

        # =========================================================
        # SECTION 2: Coolant Loop Pump Controller Floating Panel
        # =========================================================
        px, py, pz = PUMP_POS
        print(f"\n============================================================")
        print(f"[*] SECTION 2: Coolant Loop Pump Controller at ({px}, {py}, {pz})")
        print(f"============================================================")
        harness.stand(px, py + 1, pz - 3)
        harness.look_at(px, py + 0.5, pz)
        time.sleep(0.5)
        harness.recheck(px, py, pz)
        time.sleep(0.5)

        # Install cover and open Pump GUI
        install_cover_and_open_gui(harness, px, py, pz, side=2)

        # 2A. Open Coolant Loop Graph Panel (coolantLoopGraphPanel)
        print("[*] Finding and clicking Coolant Loop Graph button...")
        btn_graph = (
            harness.ui_find('button[tooltip*="Loop"]')
            or harness.ui_find('button[tooltip*="Graph"]')
            or harness.ui_find('button[tooltip*="graph"]')
        )
        assert btn_graph is not None, "Coolant Loop Graph button not found in Pump GUI with cover!"
        gx = btn_graph.get("x", 300) + btn_graph.get("width", 16) // 2
        gy = btn_graph.get("y", 135) + btn_graph.get("height", 16) // 2
        print(f"    Clicking Coolant Loop Graph button at ({gx}, {gy})...")
        harness.click_gui(gx, gy, 0)
        time.sleep(1.0)

        # Test dragging and double-click maximize on coolantLoopGraphPanel
        test_panel_drag_and_maximize(
            harness=harness,
            panel_id="coolantLoopGraphPanel",
            handle_x_offset=100,
            handle_y_offset=10,
            drag_dx=35,
            drag_dy=10,
            screenshot_prefix="coolant_loop_graph",
        )

        harness.close_gui()
        time.sleep(0.5)

        print("\n============================================================")
        print("[✓] ALL FLOATING PANEL DRAG & MAXIMIZE TESTS PASSED SUCCESSFULLY!")
        print("============================================================")


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=HARNESS_PORT, help="AgentHarness port (default: 25571)")
    HARNESS_PORT = parser.parse_args().port
    main()

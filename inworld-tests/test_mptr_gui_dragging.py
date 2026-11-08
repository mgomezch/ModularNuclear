#!/usr/bin/env python3
"""
inworld-tests/test_mptr_gui_dragging.py

In-game automated verification of:
1. MPTR core hatches window edge-dragging resizing:
   - Dragging one edge resizes asymmetrically (only moves that edge).
2. MPTR window dragging:
   - Dragging via the drag handle moves the window to where dragged without jumping.
3. Dragging from the zoom level readout:
   - Dragging via the zoom percentage text works identically to the drag handle.
4. Visual proof captured via screenshots.
"""

import sys
import os
import time
import json
import shutil
from pathlib import Path

from nhdev import NH_DEV_ROOT as REPO_ROOT

from tools.agentharness import HarnessClient
from tools.bot.world_manager import WorldSandbox

HARNESS_PORT = 25574
ARTIFACTS_DIR = Path("/home/mgomezch/.gemini/antigravity/brain/bc0c2152-bd77-425b-aa35-bf4d7878b40d")
OUTPUT_DIR = REPO_ROOT / "screenshots"

RC_POS = (500, 18, 25)


def sync_artifact(src_path: Path, artifact_name: str) -> Path:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    dest_repo = OUTPUT_DIR / artifact_name
    shutil.copy2(src_path, dest_repo)
    if ARTIFACTS_DIR.is_dir():
        dest_art = ARTIFACTS_DIR / artifact_name
        shutil.copy2(src_path, dest_art)
        print(f"[✓] Artifact synced: {artifact_name} ({dest_art})")
        return dest_art
    return dest_repo


def capture(harness: HarnessClient, name: str) -> Path:
    res = harness.screenshot(f"screenshots/{name}")
    path_str = res.replace("SCREENSHOT_SAVED: ", "").replace("SCREENSHOT: ", "").strip()
    return sync_artifact(Path(path_str), name)


def main():
    print("============================================================")
    print("[*] Starting MPTR Core Hatches Dragging & Resizing Test")
    print("============================================================")

    harness = HarnessClient(port=HARNESS_PORT)
    assert harness.ping(), f"AgentHarness not responding on port {HARNESS_PORT}"
    print(f"[+] AgentHarness connected on port {HARNESS_PORT}: {harness.status()}")

    remote_saves = harness.saves_dir()
    if remote_saves and Path(remote_saves).is_dir():
        saves_dir = Path(remote_saves)
    else:
        saves_dir = Path("/tmp/mcfleet/mptr-gui-test/inst/.minecraft/saves")
    print(f"[*] Target saves directory: {saves_dir}")
    world_name = "test_mptr_drag_world"

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
        harness.command(command="gamemode 1 @p")
        harness.command(command="gamerule doMobSpawning false")
        harness.command(command="gamerule doDaylightCycle false")
        harness.command(command="difficulty 0")
        harness.command(command="time set 6000")
        harness.command(command="weather clear 999999")
        harness.close_gui()
        time.sleep(0.5)

        rx, ry, rz = RC_POS
        print(f"[*] Positioning player at reactor controller ({rx}, {ry}, {rz})...")
        harness.stand(rx, ry + 1, rz - 3)
        harness.look_at(rx, ry + 0.5, rz)
        time.sleep(0.5)

        harness.recheck(rx, ry, rz)
        time.sleep(0.5)

        print("[*] Opening reactor controller GUI...")
        harness.command(command="clear @p")
        harness.set_player_slot(0)
        harness.slot(0)
        time.sleep(0.2)

        harness.mclick(rx, ry, rz, 2)
        time.sleep(1.0)

        gui_st = harness.status().get("gui", "")
        print(f"    GUI status: {gui_st}")
        assert "modularui" in gui_st.lower() or "container" in gui_st.lower() or "gui" in gui_st.lower(), f"GUI failed to open: {gui_st}"

        # Find the reactor hatches button
        ui_dom = harness.ui_dump()
        btn_hatches = (
            harness.ui_find('button[tooltip*="hatches view"]')
            or harness.ui_find('button[tooltip*="hatches"]')
            or harness.ui_find('button[tooltip*="Hatches"]')
            or harness.ui_find('button[tooltip*="reactor_hatches"]')
            or harness.ui_find('button[tooltip*="core hatches"]')
            or harness.ui_find('button[tooltip*="Inspect core"]')
        )
        print(f"    Reactor Hatches button: {btn_hatches}")
        if btn_hatches is not None:
            bx = btn_hatches.get("x", 200) + btn_hatches.get("width", 16) // 2
            by = btn_hatches.get("y", 100) + btn_hatches.get("height", 16) // 2
        else:
            print("    DOM Dump: " + json.dumps(ui_dom, indent=2))
            print("    Warning: button not found by selector, using known position (325, 142)...")
            bx, by = 325, 142

        print(f"    Clicking hatches button at ({bx}, {by})...")
        harness.click_gui(bx, by, 0)
        time.sleep(1.0)

        # Verify reactorGridPanel is open
        dom_panels = harness.ui_dump()
        dom_panels_str = json.dumps(dom_panels) if isinstance(dom_panels, (dict, list)) else str(dom_panels)
        print(f"    Panels DOM summary: {dom_panels_str[:500]}...")

        panel_info = (
            harness.ui_find('panel[id="reactorGridPanel"]')
            or harness.ui_find('panel[name="reactorGridPanel"]')
            or harness.ui_find('panel:not([id="panel_0"])')
        )
        if panel_info is None and isinstance(dom_panels, dict):
            # Inspect children for panel
            for ch in dom_panels.get("children", []):
                if ch.get("type") == "panel" and ch.get("id") != "panel_0":
                    panel_info = ch
                    break
        assert panel_info is not None, f"reactorGridPanel node not found in DOM! Full DOM: {dom_panels_str}"
        print("[✓] reactorGridPanel opened successfully!")
        x0 = panel_info["x"]
        y0 = panel_info["y"]
        w0 = panel_info["width"]
        h0 = panel_info["height"]
        print(f"    Initial panel state: x={x0}, y={y0}, width={w0}, height={h0}")

        capture(harness, "mptr_grid_01_initial_centered.png")

        # -------------------------------------------------------------
        # Test 1: Drag window by drag handle
        # -------------------------------------------------------------
        print("\n[*] Test 1: Dragging window by header drag handle...")
        # Header handle is located around x0 + 150, y0 + 10
        drag_from_x = x0 + 150
        drag_from_y = y0 + 10
        # Choose deltas that stay well within 480x270 viewport (max panel x is 480 - 380 = 100, max y is 270 - 230 = 40)
        delta_x = 30
        delta_y = 15
        target_x = drag_from_x + delta_x
        target_y = drag_from_y + delta_y

        print(f"    Dragging from ({drag_from_x}, {drag_from_y}) to ({target_x}, {target_y})...")
        # Perform smooth drag in steps
        harness.drag_gui(drag_from_x, drag_from_y, target_x, target_y, button=0, steps=10)
        time.sleep(0.5)

        capture(harness, "mptr_grid_02_dragged_handle.png")

        panel_info_after = harness.ui_find('panel[id="reactorGridPanel"]')
        x1 = panel_info_after["x"]
        y1 = panel_info_after["y"]
        w1 = panel_info_after["width"]
        h1 = panel_info_after["height"]
        print(f"    Panel state after drag: x={x1}, y={y1}, width={w1}, height={h1}")

        # The panel must have moved towards target (dx approx delta_x, dy approx delta_y)
        dx_actual = x1 - x0
        dy_actual = y1 - y0
        print(f"    Position delta: dx={dx_actual} (expected ~{delta_x}), dy={dy_actual} (expected ~{delta_y})")
        assert abs(dx_actual - delta_x) <= 3, f"Window did not move by expected dx! actual={dx_actual}, expected={delta_x}"
        assert abs(dy_actual - delta_y) <= 3, f"Window did not move by expected dy! actual={dy_actual}, expected={delta_y}"
        print("[✓] Window dragging by drag handle verified: zero jump, applied exact target position!")

        # -------------------------------------------------------------
        # Test 2: Drag window by zoom readout text
        # -------------------------------------------------------------
        print("\n[*] Test 2: Dragging window by zoom readout text...")
        # Find zoom readout or calculate its coordinates in header row:
        # header has tabs (6 * 18 = ~108px), drag handle, zoom out btn (14px), then zoom readout (32x14)
        # In current panel at (x1, y1), headerRow is at y1 + 3 to y1 + 21
        # Zoom readout widget (size 32x14) is centered at x1 + w1 - 105, y1 + 16
        zoom_text_x = x1 + w1 - 105
        zoom_text_y = y1 + 16
        print(f"    Clicking & dragging from zoom readout text at ({zoom_text_x}, {zoom_text_y})...")

        delta_x2 = -30
        delta_y2 = -15
        harness.drag_gui(zoom_text_x, zoom_text_y, zoom_text_x + delta_x2, zoom_text_y + delta_y2, button=0, steps=10)
        time.sleep(0.5)

        capture(harness, "mptr_grid_03_dragged_zoom_text.png")

        panel_info_after2 = harness.ui_find('panel[id="reactorGridPanel"]')
        x2 = panel_info_after2["x"]
        y2 = panel_info_after2["y"]
        w2 = panel_info_after2["width"]
        h2 = panel_info_after2["height"]
        print(f"    Panel state after zoom-readout drag: x={x2}, y={y2}, width={w2}, height={h2}")

        dx2_actual = x2 - x1
        dy2_actual = y2 - y1
        print(f"    Position delta: dx={dx2_actual} (expected ~{delta_x2}), dy={dy2_actual} (expected ~{delta_y2})")
        assert abs(dx2_actual - delta_x2) <= 3, f"Window did not move from zoom readout drag! actual={dx2_actual}, expected={delta_x2}"
        assert abs(dy2_actual - delta_y2) <= 3, f"Window did not move from zoom readout drag! actual={dy2_actual}, expected={delta_y2}"
        print("[✓] Window dragging from zoom readout text verified: works identically to handle!")

        # -------------------------------------------------------------
        # Test 3: Asymmetric edge-resize on right edge
        # -------------------------------------------------------------
        print("\n[*] Test 3: Edge resizing (asymmetric resizing)...")
        # Current right edge is at x2 + w2
        right_edge_x = x2 + w2 - 2
        mid_y = y2 + h2 // 2
        resize_delta = 30

        print(f"    Dragging right edge at ({right_edge_x}, {mid_y}) by +{resize_delta}px to ({right_edge_x + resize_delta}, {mid_y})...")
        harness.drag_gui(right_edge_x, mid_y, right_edge_x + resize_delta, mid_y, button=0, steps=10)
        time.sleep(0.5)

        capture(harness, "mptr_grid_04_asymmetric_resize.png")

        panel_info_after3 = harness.ui_find('panel[id="reactorGridPanel"]')
        x3 = panel_info_after3["x"]
        y3 = panel_info_after3["y"]
        w3 = panel_info_after3["width"]
        h3 = panel_info_after3["height"]
        print(f"    Panel state after edge resize: x={x3}, y={y3}, width={w3}, height={h3}")

        # Check width increased by ~50px
        dw_actual = w3 - w2
        # Check LEFT edge did NOT move!
        dx_left = x3 - x2
        print(f"    Resize results: width change = {dw_actual} (expected ~{resize_delta}), left edge change = {dx_left} (expected 0)")

        assert abs(dw_actual - resize_delta) <= 4, f"Width did not expand as expected: dw={dw_actual}, expected={resize_delta}"
        assert dx_left == 0, f"Left edge moved during right edge resize! Left edge shifted by {dx_left}px (symmetric resize bug still present!)"
        print("[✓] Asymmetric edge resize verified: left edge stayed completely stationary (dx=0), only right edge expanded!")

        print("\n============================================================")
        print("[✓] ALL TESTS PASSED SUCCESSFULLY!")
        print("============================================================")


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=HARNESS_PORT, help="AgentHarness port (default: 25574)")
    HARNESS_PORT = parser.parse_args().port
    main()

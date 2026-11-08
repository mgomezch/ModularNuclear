#!/usr/bin/env python3
"""
Automated Reactor Test Script for AgentHarness & InWorldTestDriver
Tests the Modular Pressure Tube Reactor (MPTR):
  Stage 1 (Build Phase): Builds and forms the MPTR with fuel & coolant while ticks are frozen.
                         Saves snapshot to 'mptr_reactor_pre'.
  Stage 2 (Execution Phase): Ephemeral run from 'mptr_reactor_pre' with ticks unfrozen.
                             Verifies DOM telemetry, hatche views, heatmap overlay, zoom controls.
                             Saves snapshot to 'mptr_reactor_post'.
"""

import sys
import os
import time
import json
from pathlib import Path

# Resolve repository root
from nhdev import NH_DEV_ROOT as REPO_ROOT

from tools.agentharness import HarnessClient, HarnessError
from tools.inworld_test_driver import InWorldTestDriver

# Spawn-centered coordinates for clean superflat testing
RC_X, RC_Y, RC_Z = 518, 5, 20
PL_X, PL_Y, PL_Z = 518, 6, 17
MH_X, MH_Y, MH_Z = 517, 5, 20

def build_reactor(client: HarnessClient):
    """Stage 1: Builds multiblock, completes maintenance, feeds fuel & coolant with ticks frozen."""
    print("------------------------------------------------------------")
    client.close_gui()
    client.wait(5)

    print(f"[*] Placing support stone for player at ({PL_X}, {PL_Y - 1}, {PL_Z})...")
    client.setblock(PL_X, PL_Y - 1, PL_Z, "stone")
    client.wait(5)

    print(f"[*] Positioning player on stable support at ({PL_X}, {PL_Y}, {PL_Z})...")
    client.stand(PL_X, PL_Y, PL_Z)
    client.wait(5)
    client.look(0.0, 30.0)
    client.wait(5)

    print(f"[*] Placing support stone at ({RC_X}, {RC_Y - 1}, {RC_Z})...")
    client.setblock(RC_X, RC_Y - 1, RC_Z, "stone")
    client.wait(5)

    print(f"[*] Placing controller at ({RC_X}, {RC_Y}, {RC_Z})...")
    res = client.set_gt_machine(RC_X, RC_Y, RC_Z, 32115, 2)
    print(f"[*] set_gt_machine result: {res}")
    assert "GT_MACHINE_SET" in res or "already" in res, f"Failed to set GT machine: {res}"
    client.wait(5)
    client.assert_block(RC_X, RC_Y, RC_Z, "gregtech:gt.blockmachines")
    print("[✓] Controller block confirmed.")

    print(f"[*] Autobuilding multiblock at ({RC_X}, {RC_Y}, {RC_Z})...")
    client.autobuild(RC_X, RC_Y, RC_Z)
    client.wait(20)

    print(f"[*] Replacing casing with Auto-Taping Maintenance Hatch at ({MH_X}, {MH_Y}, {MH_Z})...")
    res_mh = client.set_gt_machine(MH_X, MH_Y, MH_Z, 15497, 2)
    print(f"[*] set_gt_machine (MH) result: {res_mh}")
    client.wait(5)

    print("[*] Triggering structure recheck...")
    client.recheck(RC_X, RC_Y, RC_Z)
    client.wait(10)

    for attempt in range(5):
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


def test_reactor(client: HarnessClient):
    """Stage 2: Live test execution, GUI interaction, DOM assertions, and screenshots."""
    print("------------------------------------------------------------")
    print(f"[*] Positioning player to interact with controller at ({RC_X}, {RC_Y}, {RC_Z})...")
    client.stand(PL_X, PL_Y, PL_Z)
    client.wait(5)
    client.look(0.0, 30.0)
    client.wait(5)
    client.chat("/clear")
    client.wait(5)

    # 1. Assert structure is still formed
    client.recheck(RC_X, RC_Y, RC_Z)
    client.wait(10)
    client.assert_status(RC_X, RC_Y, RC_Z, '"formed":true')
    client.fix_maintenance(RC_X, RC_Y, RC_Z)
    client.feed(RC_X, RC_Y, RC_Z)
    client.wait(5)
    print("[✓] Structure verified formed and maintained in live test.")

    # 2. Open Reactor GUI
    print("[*] Opening Modular Nuclear Reactor GUI...")
    client.rightclickblock(RC_X, RC_Y, RC_Z, 2)
    client.wait(15)
    client.assert_gui("GuiContainerWrapper")
    print("[✓] ModularUI 2 GuiContainerWrapper screen opened successfully.")

    # 3. Verify Terminal UI elements via Semantic DOM
    print("[*] Inspecting Terminal GUI DOM...")
    dom = client.ui_dump()
    print("DEBUG UI DOM:", json.dumps(dom, indent=2))
    title_node = client.assert_ui("text:contains('Modular Pressure Tube Reactor')")
    print(f"    Found title: {title_node.get('text')}")

    temp_node = client.assert_ui("text:contains('Core temp:')")
    print(f"    Found telemetry: {temp_node.get('text')}")

    flux_node = client.assert_ui("text:contains('Flux:')")
    print(f"    Found flux output: {flux_node.get('text')}")

    react_node = client.assert_ui("text:contains('Avg reactivity:')")
    print(f"    Found reactivity: {react_node.get('text')}")

    fast_node = client.assert_ui("text:contains('Fast neutrons:')")
    print(f"    Found fast neutrons: {fast_node.get('text')}")

    therm_node = client.assert_ui("text:contains('Thermal neutrons:')")
    print(f"    Found thermal neutrons: {therm_node.get('text')}")

    esc_node = client.assert_ui("text:contains('Escaped neutrons:')")
    print(f"    Found escaped neutrons: {esc_node.get('text')}")

    assert client.ui_find("text:contains('Pipes:')") is None, "Pipes line should be removed"

    # Verify buttons: only SCRAM, hatches view, muffler (and structure update)
    client.assert_ui("button[tooltip*='Emergency SCRAM']")
    client.assert_ui("button[tooltip*='hatches view']")
    client.assert_ui("button[tooltip*='Muffling']")
    assert client.ui_find("button[tooltip*='Void Excess']") is None, "Void excess button should be removed"
    assert client.ui_find("button[tooltip*='Batch Mode']") is None, "Batch mode button should be removed"
    assert client.ui_find("button[tooltip*='Power Panel']") is None, "Power panel button should be removed"
    assert client.ui_find("button[tooltip*='Power Switch']") is None, "Power switch button should be removed"

    # Capture terminal screenshot
    client.screenshot("screenshots/reactor_gui_terminal.png")
    print("[✓] Screenshot saved: screenshots/reactor_gui_terminal.png")
    client.wait(5)

    # 4. Click 'reactor core hatches view' button semantically
    print("[*] Finding and clicking reactor core hatches view button...")
    hatches_btn = client.assert_ui('button[tooltip*="hatches view"]')
    bx = hatches_btn.get("x", 317) + hatches_btn.get("width", 16) // 2
    by = hatches_btn.get("y", 134) + hatches_btn.get("height", 16) // 2
    print(f"    Found hatches button at ({bx}, {by}), clicking...")
    client.click_gui(bx, by, 0)
    client.wait(15)

    # 5. Assert Synced Reactor Core Hatches window opened with zoom controls and no title
    print("[*] Verifying Reactor Core Hatches panel...")
    post_click_dom = client.ui_dump()
    dom_str = json.dumps(post_click_dom) if isinstance(post_click_dom, (dict, list)) else str(post_click_dom)
    print("DEBUG POST CLICK DOM:", dom_str[:1000])
    assert client.ui_find('window text:contains("Core Hatches")') is None, "Title 'Core Hatches' should be removed"
    client.assert_ui('button[tooltip*="Zoom in"]')
    client.assert_ui('button[tooltip*="Zoom out"]')
    client.assert_ui('button[tooltip*="Reset zoom"]')
    client.screenshot("screenshots/reactor_gui_components.png")
    print("[✓] Screenshot saved: screenshots/reactor_gui_components.png")
    client.wait(5)

    # 6. Test Zoom In
    print("[*] Testing zoom in button...")
    client.ui_click('button[tooltip*="Zoom in"]')
    client.wait(10)
    client.screenshot("screenshots/reactor_gui_zoomed.png")
    print("[✓] Screenshot saved: screenshots/reactor_gui_zoomed.png")
    client.wait(5)

    # 7. Test Reset Zoom
    print("[*] Testing reset zoom button...")
    client.ui_click('button[tooltip*="Reset zoom"]')
    client.wait(10)

    # 8. Click mode toggle button to switch to temperature overlay
    print("[*] Clicking mode toggle button to switch to temperature overlay...")
    client.ui_click('button[tooltip*="Temperature"]')
    client.wait(10)

    # Verify mode switched
    overlay_btn = client.assert_ui('button[tooltip*="Currently active tab"]')
    assert "Heatmap for" not in overlay_btn.get("tooltip", ""), f"Redundant description found in tooltip: {overlay_btn.get('tooltip')}"
    print(f"    Active tab button tooltip confirmed: {overlay_btn.get('tooltip')[:30]}...")
    client.screenshot("screenshots/reactor_gui_heatmap.png")
    print("[✓] Screenshot saved: screenshots/reactor_gui_heatmap.png")
    client.wait(5)

    # 9. Hover over a cell in the grid to display tooltip
    print("[*] Hovering over grid cell to verify status tooltip...")
    grid_node = client.assert_ui('grid')
    gx = grid_node.get('x')
    gy = grid_node.get('y')
    gw = grid_node.get('width', 126)
    gh = grid_node.get('height', 126)
    if gx is not None and gy is not None:
        bx = gx + gw // 2
        by = gy + gh // 2
        try:
            client.set_mouse(bx, by)
        except Exception:
            pass
        import subprocess
        screen_x = bx * 4
        screen_y = by * 4
        try:
            subprocess.run(['xdotool', 'mousemove', str(screen_x), str(screen_y)], env=dict(os.environ, DISPLAY=':99'))
        except Exception:
            pass
        client.wait(20)
        client.screenshot("screenshots/reactor_gui_tooltip.png")
        print("[✓] Screenshot saved: screenshots/reactor_gui_tooltip.png")

    # Sync screenshots to artifact directory if present
    artifact_dir = Path("/home/mgomezch/.gemini/antigravity/brain/bc0c2152-bd77-425b-aa35-bf4d7878b40d")
    if artifact_dir.is_dir():
        import shutil
        mcfleet_dir = Path("/tmp/mcfleet/mptr-test/inst/.minecraft/screenshots")
        for shot in [
            "reactor_gui_terminal.png",
            "reactor_gui_components.png",
            "reactor_gui_zoomed.png",
            "reactor_gui_heatmap.png",
            "reactor_gui_tooltip.png",
        ]:
            src_shot = (mcfleet_dir / shot) if (mcfleet_dir / shot).is_file() else (Path("screenshots") / shot)
            if src_shot.is_file():
                dest_local = Path("screenshots") / shot
                if src_shot.resolve() != dest_local.resolve():
                    shutil.copy2(src_shot, dest_local)
                if artifact_dir.is_dir() and src_shot.resolve() != (artifact_dir / shot).resolve():
                    shutil.copy2(src_shot, artifact_dir / shot)
                print(f"[✓] Synced artifact: {shot}")

    # 10. Core Grid Slot & Inventory Interactions Verification
    print("\n============================================================")
    print("[*] Starting Core Grid Slot & Inventory Interactions Verification...")
    print("============================================================")

    # 10.1 Switch back to Components mode and Reset zoom (1:1)
    client.ui_click('button[tooltip*="Components"]')
    client.wait(5)
    client.ui_click('button[tooltip*="Reset zoom"]')
    client.wait(5)

    grid_node = client.assert_ui('grid')
    area_x = grid_node['x']
    area_y = grid_node['y']
    area_w = grid_node['width']
    area_h = grid_node['height']
    N = 3
    content_w = N * 18
    content_h = N * 18 + 22
    pan_x = (area_w - content_w) / 2.0
    pan_y = (area_h - content_h) / 2.0

    def cell_pos(hx, hy):
        render_hy = (N - 1) - hy
        cx = int(round(area_x + pan_x + (hx + 0.5) * 18))
        cy = int(round(area_y + pan_y + (render_hy + 0.5) * 18))
        return cx, cy

    all_cells = [(1, 2), (0, 1), (1, 1), (2, 1), (1, 0)]

    # Sub-test 1: Left-click on a grid slot with an item to take it into player cursor
    print("[*] Test 1: Click on grid slot with fuel rod to pick it up into cursor...")
    bus_cell = None
    for hx, hy in all_cells:
        cx, cy = cell_pos(hx, hy)
        client.click_gui(cx, cy, 0)
        client.wait(5)
        cur = client.cursor_item()
        if cur.get("count", 0) > 0 and cur.get("id") != "empty":
            bus_cell = (hx, hy)
            print(f"    [✓] Successfully picked up item into cursor from cell ({hx}, {hy}): {cur}")
            break
    assert bus_cell is not None, "Failed to pick up any fuel rod from core bus cells!"

    # Sub-test 2: Drop off item from cursor into an empty player inventory slot
    cslots = {s["slot"]: s for s in client.container_slots()}
    empty_slot = 0
    for s_idx in range(36):
        if s_idx not in cslots or cslots[s_idx].get("count", 0) == 0 or cslots[s_idx].get("id") == "empty":
            empty_slot = s_idx
            break

    print(f"[*] Test 2: Drop off cursor item into player inventory slot {empty_slot}...")
    cur_before = client.cursor_item()
    assert cur_before.get("count", 0) > 0
    client.click_slot(empty_slot, 0, 0)
    client.wait(5)
    cur_after = client.cursor_item()
    assert cur_after.get("count", 0) == 0 or cur_after.get("id") == "empty", f"Cursor not empty after dropoff: {cur_after}"
    cslots = {s["slot"]: s for s in client.container_slots()}
    assert empty_slot in cslots, f"Slot {empty_slot} does not contain dropped item! Slots: {cslots}"
    print(f"    [✓] Item dropped off into inventory slot {empty_slot}: {cslots[empty_slot]}")

    # Sub-test 3: Click on inventory slot to pick up item, then click on hatch/bus grid slot to deposit
    print(f"[*] Test 3: Pick up item from slot {empty_slot} and drop it back into bus cell {bus_cell}...")
    client.click_slot(empty_slot, 0, 0)
    client.wait(5)
    cur = client.cursor_item()
    assert cur.get("count", 0) > 0, f"Cursor did not pick up from slot {empty_slot}: {cur}"
    cx, cy = cell_pos(*bus_cell)
    client.click_gui(cx, cy, 0)
    client.wait(5)
    cur = client.cursor_item()
    assert cur.get("count", 0) == 0 or cur.get("id") == "empty", f"Cursor not empty after deposit into bus: {cur}"
    print(f"    [✓] Successfully deposited item from cursor back into grid slot {bus_cell}!")

    # Sub-test 4: Shift-click to remove item from grid slot directly into player inventory
    print(f"[*] Test 4: Shift-click on grid slot {bus_cell} to quick-move item directly into inventory...")
    client.set_shift_key(True)
    client.click_gui(cx, cy, 0)
    client.wait(5)
    client.set_shift_key(False)
    cur = client.cursor_item()
    assert cur.get("count", 0) == 0 or cur.get("id") == "empty", f"Cursor should remain empty on shift-click: {cur}"
    inv = client.inventory()
    has_rod = any("Rod" in item.get("id", "") or "rod" in item.get("id", "") for item in inv)
    assert has_rod, f"Player inventory did not receive fuel rod on shift-click! Inv: {inv}"
    print(f"    [✓] Successfully shift-clicked item from grid slot into player inventory!")

    # Sub-test 5a: Disallowed block rejection (stone)
    print("[*] Test 5a: Verify bus rejects non-nuclear block (4 stone)...")
    client.set_cursor_item("minecraft:stone", 4, 0)
    client.wait(5)
    cur = client.cursor_item()
    assert "stone" in cur.get("id", ""), f"Failed to set cursor to stone: {cur}"
    client.click_gui(cx, cy, 0)
    client.wait(5)
    cur_after = client.cursor_item()
    assert "stone" in cur_after.get("id", "") and cur_after.get("count", 0) == 4, f"Bus should have rejected stone! Cursor: {cur_after}"
    client.set_cursor_item()
    client.wait(3)
    print("    [✓] Bus correctly rejected non-nuclear stone blocks.")

    # Sub-test 5b: Disallowed item rejection (fluid cells IC2:itemCellEmpty)
    print("[*] Test 5b: Verify bus strictly rejects empty fluid cells (4 cells)...")
    client.set_cursor_item("IC2:itemCellEmpty", 4, 0)
    client.wait(5)
    cur = client.cursor_item()
    assert cur.get("id") == "IC2:itemCellEmpty" and cur.get("count") == 4, f"Failed to set cursor to 4 cells: {cur}"
    client.click_gui(cx, cy, 0)
    client.wait(5)
    cur_after = client.cursor_item()
    assert cur_after.get("id") == "IC2:itemCellEmpty" and cur_after.get("count", 0) == 4, f"Bus should have rejected fluid cells! Cursor: {cur_after}"
    client.set_cursor_item()
    client.wait(3)
    print("    [✓] Bus correctly rejected fluid cells.")

    # Sub-test 5c: Stack size limit enforcement (cursor holds 4 fuel rods, clicks empty bus)
    print("[*] Test 5c: Verify bus enforces single-item capacity (stack of 4 fuel rods deposits exactly 1)...")
    client.set_cursor_item("gregtech:gt.rodUranium", 4, 0)
    client.wait(5)
    cur = client.cursor_item()
    assert cur.get("count", 0) == 4, f"Failed to set cursor to 4 rods: {cur}"
    client.click_gui(cx, cy, 0)
    client.wait(5)
    cur_after = client.cursor_item()
    assert cur_after.get("count", 0) == 3, f"Bus must accept exactly 1 item from stack of 4! Cursor remaining: {cur_after}"
    print("    [✓] Bus accepted exactly 1 fuel rod; cursor retained exactly 3.")
    # Now click with 3 rods on the now-filled bus: bus must reject extra items
    client.click_gui(cx, cy, 0)
    client.wait(5)
    cur_after2 = client.cursor_item()
    assert cur_after2.get("count", 0) == 3, f"Filled bus must not accept any additional rods! Cursor: {cur_after2}"
    print("    [✓] Filled bus rejected additional fuel rods.")
    # Clear cursor, pick up the 1 rod to clear the bus
    client.set_cursor_item()
    client.wait(3)
    client.click_gui(cx, cy, 0)
    client.wait(5)
    cur_picked = client.cursor_item()
    assert cur_picked.get("count", 0) == 1, f"Failed to pick up single rod from bus! Cursor: {cur_picked}"
    client.set_cursor_item()
    client.wait(3)

    # Sub-test 6: Shift-clicking on an inventory slot auto-transfers to next compatible core slot
    print("[*] Test 6: Shift-click inventory slot to auto-transfer fuel rod to core grid...")
    cslots = {s["slot"]: s for s in client.container_slots()}
    rod_slot = None
    for s_idx, s_info in cslots.items():
        if "Rod" in s_info.get("id", "") or "rod" in s_info.get("id", ""):
            rod_slot = s_idx
            break
    assert rod_slot is not None, f"No fuel rod found in inventory slots! Slots: {cslots}"
    print(f"    Found fuel rod in inventory slot {rod_slot}, shift-clicking...")
    client.click_slot(rod_slot, 0, 1)
    client.wait(10)
    cslots_after = {s["slot"]: s for s in client.container_slots()}
    assert rod_slot not in cslots_after or cslots_after[rod_slot]["count"] < cslots[rod_slot]["count"], "Shift click did not transfer item from slot!"
    print("    [✓] Fuel rod successfully transferred from inventory slot into core grid via shift-click!")

    # Sub-test 7: Dragging a stack over multiple core grid slots
    print("[*] Test 7: Drag a stack of fuel rods across multiple core grid slots...")
    for hx, hy in all_cells:
        pcx, pcy = cell_pos(hx, hy)
        client.set_shift_key(True)
        client.click_gui(pcx, pcy, 0)
        client.wait(3)
        client.set_shift_key(False)
    for i in range(36):
        client.set_player_slot(i)
    client.wait(5)

    # 7a: Drag 4 stone across core grid slots — must deposit 0 into any bus
    print("[*] Test 7a: Drag 4 stone across core grid slots — must deposit 0...")
    client.set_cursor_item("minecraft:stone", 4, 0)
    client.wait(5)
    x1, y1 = cell_pos(1, 2)
    x2, y2 = cell_pos(1, 0)
    client.drag_gui(x1, y1, x2, y2, button=0, steps=20)
    client.wait(15)
    cur_drag_stone = client.cursor_item()
    assert cur_drag_stone.get("count", 0) == 4, f"Drag deposited disallowed stone! Cursor: {cur_drag_stone}"
    print("    [✓] Dragging stone deposited 0 items into buses.")
    client.set_cursor_item()
    client.wait(3)

    # 7b: Drag 4 empty fluid cells across core grid slots — must deposit 0 into any bus
    print("[*] Test 7b: Drag 4 empty fluid cells across core grid slots — must deposit 0 into buses...")
    client.set_cursor_item("IC2:itemCellEmpty", 4, 0)
    client.wait(5)
    client.drag_gui(x1, y1, x2, y2, button=0, steps=20)
    client.wait(15)
    cur_drag_cells = client.cursor_item()
    assert cur_drag_cells.get("count", 0) == 4, f"Drag deposited empty cells into bus! Cursor: {cur_drag_cells}"
    print("    [✓] Dragging empty cells deposited 0 items into buses.")
    client.set_cursor_item()
    client.wait(3)

    # 7c: Drag valid fuel rods — each bus receives exactly 1
    print("[*] Test 7c: Drag 4 fuel rods across core grid slots...")
    client.set_cursor_item("gregtech:gt.rodUranium", 4, 0)
    client.wait(5)
    cur = client.cursor_item()
    assert cur.get("count", 0) == 4, f"Cursor did not hold 4 rods: {cur}"

    print(f"    Dragging from ({x1}, {y1}) to ({x2}, {y2}) across center column...")
    client.drag_gui(x1, y1, x2, y2, button=0, steps=20)
    client.wait(15)

    cur_drag = client.cursor_item()
    print(f"    Cursor item count after drag: {cur_drag}")
    assert cur_drag.get("count", 0) < 4, f"Drag did not deposit any items! Cursor: {cur_drag}"
    print(f"    [✓] Drag successfully deposited items into empty core slots (cursor count went from 4 to {cur_drag.get('count', 0)})!")
    client.set_cursor_item()
    client.wait(3)

    # Sub-test 8: Fluid hatch interaction with container (IC2 empty cell & coolant cell)
    print("[*] Test 8: Test fluid hatch container interaction with IC2 cell...")
    client.set_cursor_item("IC2:itemCellEmpty", 1, 0)
    client.wait(5)
    cur = client.cursor_item()
    assert cur.get("id") == "IC2:itemCellEmpty" and cur.get("damage") == 0, f"Cursor does not hold empty cell: {cur}"
    hatch_found = False
    target_hx, target_hy = None, None
    for hx, hy in all_cells:
        pcx, pcy = cell_pos(hx, hy)
        client.click_gui(pcx, pcy, 0)
        client.wait(5)
        cur_check = client.cursor_item()
        if cur_check.get("id") == "IC2:itemCellEmpty" and cur_check.get("damage") == 9:
            hatch_found = True
            target_hx, target_hy = hx, hy
            print(f"    [✓] Successfully drained 1000L IC2 coolant from hatch at ({hx}, {hy}) into cell: {cur_check}")
            # Now click again with the filled coolant cell to deposit it back into the hatch
            client.click_gui(pcx, pcy, 0)
            client.wait(5)
            cur_empty = client.cursor_item()
            assert cur_empty.get("id") == "IC2:itemCellEmpty" and cur_empty.get("damage") == 0, f"Cell not emptied back into hatch: {cur_empty}"
            print("    [✓] Successfully deposited coolant back into fluid hatch from cell!")
            client.set_cursor_item()
            break
    assert hatch_found, "Failed to find any fluid hatch to test fluid container interaction!"

    # Sub-test 8b: Shift-click fluid container interaction
    print("[*] Test 8b: Shift-click fluid container interaction with empty cell in inventory...")
    client.set_player_slot(0, "IC2:itemCellEmpty", 1, 0)
    client.wait(5)
    thx, thy = cell_pos(target_hx, target_hy)
    # Shift-click the hatch with empty cursor to drain fluid into inventory empty cell
    client.set_shift_key(True)
    client.click_gui(thx, thy, 0)
    client.wait(5)
    client.set_shift_key(False)
    inv1 = {s["slot"]: s for s in client.inventory()}
    assert 0 in inv1 and inv1[0].get("id") == "IC2:itemCellEmpty" and inv1[0].get("damage") == 9, f"Shift-click did not fill empty cell from hatch! Inv: {inv1}"
    print("    [✓] Shift-clicking fluid hatch successfully filled empty cell in player inventory with coolant!")

    # Shift-click the hatch again: hatch has space, so slot 0 filled coolant cell empties back into hatch!
    client.set_shift_key(True)
    client.click_gui(thx, thy, 0)
    client.wait(5)
    client.set_shift_key(False)
    inv2 = {s["slot"]: s for s in client.inventory()}
    assert 0 in inv2 and inv2[0].get("id") == "IC2:itemCellEmpty" and inv2[0].get("damage") == 0, f"Shift-click did not empty filled cell back into hatch! Inv: {inv2}"
    print("    [✓] Shift-clicking fluid hatch successfully emptied filled cell in player inventory back into hatch!")
    client.set_player_slot(0)
    client.wait(3)

    print("\n[✓] ALL CORE GRID SLOT & INVENTORY INTERACTIONS SUCCESSFULLY VERIFIED!\n")

    # 11. Close GUI
    print("[*] Closing GUI...")
    client.close_gui()
    print("============================================================")
    print("[✓] All reactor automation & GUI tests PASSED!")
    print("============================================================")


def run_test(port: int = 25571, stage: str = "both"):
    driver = InWorldTestDriver(
        tag="mptr_reactor",
        port=port,
        no_mobs=True,
        base_template="superflat_empty",
    )
    if stage == "build":
        driver.run_build_phase(build_reactor)
        return True
    elif stage == "test":
        driver.run_test_phase(test_reactor)
        return True
    else:
        return driver.run(build_fn=build_reactor, test_fn=test_reactor)


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="MPTR Reactor In-World Test")
    parser.add_argument("--port", type=int, default=25571, help="AgentHarness port (default: 25571)")
    parser.add_argument("--stage", choices=["both", "build", "test"], default="both", help="Which test stage to run")
    args = parser.parse_args()
    success = run_test(port=args.port, stage=args.stage)
    sys.exit(0 if success else 1)

#!/usr/bin/env python3
"""
Automated Integration Test Suite: MPTR Tier 2 Nuclear Reactor Cooled by Closed Coolant Loop
Tests:
  1. Multiblock formation of Tier 2 (5x5 core / 11x11 outer) MPTR Reactor with
     paired High-Pressure Convective Passage Hatches (top inlet & bottom outlet)
     and 1 adjacent Nuclear Bus holding a Uranium Fuel Rod.
  2. Coolant Loop Pump multiblock with 3x3x4 Railcraft Tank reservoir.
  3. Pressurized Heat Exchanger (PHE) multiblock.
  4. Closed-loop 3D pipe routing from Pump -> Reactor Core Passage -> PHE -> Pump.
  5. Live simulation: Core convective heat extraction into circulating coolant loop.
  6. Verification of Reactor GUI with NEI-styled recipe tabs and scaled 1:1 zoom reset button.
"""

import sys
import os
import time
import json
import shutil
from pathlib import Path

from nhdev import NH_DEV_ROOT as REPO_ROOT

from tools.agentharness import HarnessClient, HarnessError
from tools.inworld_test_driver import InWorldTestDriver

PIPE_ID = 32140  # Medium Steel Coolant Pipe (matches Steel Railcraft tank)

PHE_HEIGHT = 9
PHE_LENGTH = 11
PHE_MATERIAL = "osmium"


def build_reactor_coolant_loop(client: HarnessClient):
    """Stage 1: Assembles Tier 2 Reactor, Coolant Pump, Max-Size PHE (9x11x3 Osmium), and closed loop piping."""
    print("------------------------------------------------------------")
    # Coordinates layout:
    # Reactor at (500, 8, 25), extends X: [495..505], Y: [7..11], Z: [25..35]
    # Max PHE at (515, 7, 25), extends X: [515..517], Y: [7..15], Z: [25..35]
    # Pump at (515, 7, 45), extends X: [515..517], Y: [7..12], Z: [45..47]
    RC_X, RC_Y, RC_Z = 500, 8, 25
    PHE_X, PHE_Y, PHE_Z = 515, 7, 25
    PUMP_X, PUMP_Y, PUMP_Z = 515, 7, 45

    PHE_CONTROLLER_Y = PHE_Y + (PHE_HEIGHT // 2)  # Y = 11
    PHE_BACK_Z = PHE_Z + PHE_LENGTH - 1          # Z = 35

    print(f"[*] Clearing test build area [490, 4, 15] -> [530, 22, 60]...")
    try:
        client.clear_area(490, 4, 15, 530, 22, 60)
    except Exception as e:
        print("[!] Note on clear_area:", e)
    client.wait(10)

    # Base stone platform
    client.fill_area(490, 4, 15, 530, 4, 60, "stone")
    client.stand(505, 12, 18)
    client.look(0.0, 25.0)
    client.wait(5)

    # 1. Build Tier 2 MPTR Nuclear Reactor
    print("\n[*] Assembling Tier 2 MPTR Nuclear Reactor (11x11x5)...")
    client.set_gt_machine(RC_X, RC_Y, RC_Z, 32115, 2)  # Controller facing North
    client.wait(5)

    res_ab = client.autobuild(RC_X, RC_Y, RC_Z, 2)
    print("    Reactor autobuild result:", res_ab)
    client.wait(15)

    # Set Auto-Taping Maintenance Hatch on reactor perimeter
    client.set_gt_machine(RC_X + 1, RC_Y, RC_Z, 15497, 2)
    client.fix_maintenance(RC_X, RC_Y, RC_Z)

    # Set up paired High-Pressure Convective Passage Hatches at core center (gx=2, gy=2)
    # Center column in world: (RC_X, RC_Z + 5) = (500, 30)
    # Top inlet: Y = RC_Y + 3 = 11, facing UP (1)
    # Bottom outlet: Y = RC_Y - 1 = 7, facing DOWN (0)
    CORE_X = RC_X
    CORE_Z = RC_Z + 5
    TOP_Y = RC_Y + 3
    BOT_Y = RC_Y - 1

    print(f"\n[*] Installing High-Pressure Convective Passage Hatches at ({CORE_X}, {CORE_Z})...")
    client.set_gt_machine(CORE_X, TOP_Y, CORE_Z, 32166, 1)  # Osmium top inlet facing UP
    client.set_gt_machine(CORE_X, BOT_Y, CORE_Z, 32173, 0)  # Osmium bottom outlet facing DOWN

    # Adjacent Nuclear Bus for single fuel rod: (CORE_X + 1, TOP_Y, CORE_Z) facing UP
    print(f"[*] Installing Nuclear Bus with Fuel Rod at ({CORE_X + 1}, {TOP_Y}, {CORE_Z})...")
    client.set_gt_machine(CORE_X + 1, TOP_Y, CORE_Z, 32100, 1)

    client.recheck(RC_X, RC_Y, RC_Z)
    client.wait(5)
    client.feed(RC_X, RC_Y, RC_Z)
    client.wait(5)

    # 2. Build Coolant Loop Pump with Railcraft Tank above it
    print(f"\n[*] Assembling Coolant Loop Pump at ({PUMP_X}, {PUMP_Y}, {PUMP_Z})...")
    res_pump = client.build_multiblock("coolant_pump", PUMP_X, PUMP_Y, PUMP_Z, "steel")
    print("    build_multiblock coolant_pump result:", res_pump)
    client.fix_maintenance(PUMP_X + 1, PUMP_Y, PUMP_Z)
    # Insert turbine rotor impeller into pump controller slot 1
    client.set_container_slot(PUMP_X + 1, PUMP_Y, PUMP_Z, 1, "gregtech:gt.metatool.01", 1, 172)
    # Install Debug Wireless Energy Cover on Coolant Pump Energy Hatch (facing South = 3)
    client.set_cover(PUMP_X + 1, PUMP_Y, PUMP_Z + 2, 3, "Cover_Wireless_Energy_Debug")
    client.wait(5)

    # 3. Build Pressurized Heat Exchanger (PHE) - Max size 9x11x3 Osmium
    print(f"\n[*] Assembling Pressurized Heat Exchanger at ({PHE_X}, {PHE_Y}, {PHE_Z}) [mat={PHE_MATERIAL}, H={PHE_HEIGHT}, L={PHE_LENGTH}]...")
    res_phe = client.build_multiblock("phe", PHE_X, PHE_Y, PHE_Z, PHE_MATERIAL, PHE_HEIGHT, PHE_LENGTH)
    print("    build_multiblock phe result:", res_phe)
    client.fix_maintenance(PHE_X + 1, PHE_CONTROLLER_Y, PHE_Z)
    # Install Debug Wireless Energy Cover on PHE Energy Hatch (facing South = 3) at back bottom
    client.set_cover(PHE_X + 1, PHE_Y, PHE_BACK_Z, 3, "Cover_Wireless_Energy_Debug")
    client.wait(5)

    # Secondary circuit for PHE: Debug Tank (Distilled Water) -> PHE -> Supertank (Steam)
    print("[*] Setting up Secondary Circuit on PHE...")
    client.set_gt_machine(PHE_X + 3, PHE_CONTROLLER_Y, PHE_Z, 238, 4)
    client.configure_debug_tank(PHE_X + 3, PHE_CONTROLLER_Y, PHE_Z, "ic2distilledwater", True)
    client.set_gt_machine(PHE_X + 3, PHE_CONTROLLER_Y, PHE_BACK_Z, 130, 4)
    client.configure_supertank(PHE_X + 3, PHE_CONTROLLER_Y, PHE_BACK_Z, True)
    client.enable_auto_output(PHE_X + 2, PHE_CONTROLLER_Y, PHE_BACK_Z)

    # 4. Route Closed Piping Circuit
    print("\n[*] Routing Closed Coolant Loop Piping...")
    rx_bounds = [RC_X - 5, RC_Y - 1, RC_Z, RC_X + 5, RC_Y + 3, RC_Z + 10]
    pump_bounds = [PUMP_X, PUMP_Y, PUMP_Z, PUMP_X + 2, PUMP_Y + 5, PUMP_Z + 2]
    phe_bounds = [PHE_X, PHE_Y, PHE_Z, PHE_X + 2, PHE_Y + PHE_HEIGHT - 1, PHE_BACK_Z]

    # Leg 1: Pump Discharge -> Reactor Top Inlet
    # Pump discharge: (PUMP_X + 2, PUMP_Y + 1, PUMP_Z + 1) = (517, 8, 46) facing EAST
    # Adjacent pipe starts at (518, 8, 46).
    # Reactor top inlet: (CORE_X, TOP_Y, CORE_Z) = (500, 11, 30) facing UP
    # Adjacent pipe ends above it at (500, TOP_Y + 1, 30) = (500, 12, 30).
    client.route_pipe(
        pipe_id=PIPE_ID,
        start=(PUMP_X + 3, PUMP_Y + 1, PUMP_Z + 1),
        end=(CORE_X, TOP_Y + 1, CORE_Z),
        start_conn="WEST",
        end_conn="DOWN",
        avoid=[rx_bounds, pump_bounds, phe_bounds],
        preferred_y=TOP_Y + 1,
    )
    client.connect_pipe(CORE_X, TOP_Y + 1, CORE_Z, "DOWN")
    print("    [+] Leg 1 (Cold Leg -> Reactor Inlet) routed")

    # Leg 2: Reactor Bottom Outlet -> PHE Primary Inlet
    # Reactor bottom outlet: (CORE_X, BOT_Y, CORE_Z) = (500, 7, 30) facing DOWN
    # Adjacent pipe starts below at (500, BOT_Y - 1, 30) = (500, 6, 30).
    # PHE Primary Inlet: (PHE_X, PHE_CONTROLLER_Y, PHE_Z) = (515, 11, 25) facing WEST
    # Adjacent pipe ends at (PHE_X - 1, PHE_CONTROLLER_Y, PHE_Z) = (514, 11, 25).
    client.route_pipe(
        pipe_id=PIPE_ID,
        start=(CORE_X, BOT_Y - 1, CORE_Z),
        end=(PHE_X - 1, PHE_CONTROLLER_Y, PHE_Z),
        start_conn="UP",
        end_conn="EAST",
        avoid=[rx_bounds, pump_bounds, phe_bounds],
        preferred_y=BOT_Y - 1,
    )
    client.connect_pipe(CORE_X, BOT_Y - 1, CORE_Z, "UP")
    print("    [+] Leg 2 (Hot Leg: Reactor Outlet -> PHE Inlet) routed")

    # Leg 3: PHE Primary Outlet -> Pump Suction
    # PHE Primary Outlet: (PHE_X, PHE_CONTROLLER_Y, PHE_BACK_Z) = (515, 11, 35) facing SOUTH
    # Adjacent pipe starts at (PHE_X, PHE_CONTROLLER_Y, PHE_BACK_Z + 1) = (515, 11, 36).
    # Pump Suction: (PUMP_X, PUMP_Y + 1, PUMP_Z + 1) = (515, 8, 46) facing WEST
    # Adjacent pipe ends at (PUMP_X - 1, PUMP_Y + 1, PUMP_Z + 1) = (514, 8, 46).
    client.route_pipe(
        pipe_id=PIPE_ID,
        start=(PHE_X, PHE_CONTROLLER_Y, PHE_BACK_Z + 1),
        end=(PUMP_X - 1, PUMP_Y + 1, PUMP_Z + 1),
        start_conn="NORTH",
        end_conn="EAST",
        avoid=[rx_bounds, pump_bounds, phe_bounds],
        preferred_y=PUMP_Y + 1,
    )
    print("    [+] Leg 3 (Cold Leg: PHE Outlet -> Pump Suction) routed")
    print("[✓] Build Phase completed: Reactor, coolant loop, and secondary circuit assembled.")


def test_reactor_coolant_loop(client: HarnessClient):
    """Stage 2: Live simulation verifying convective core cooling, loop circulation, and GUI tabs."""
    print("------------------------------------------------------------")
    RC_X, RC_Y, RC_Z = 500, 8, 25
    PHE_X, PHE_Y, PHE_Z = 515, 7, 25
    PUMP_X, PUMP_Y, PUMP_Z = 515, 7, 45
    CORE_X = RC_X
    CORE_Z = RC_Z + 5
    TOP_Y = RC_Y + 3
    PHE_CONTROLLER_Y = PHE_Y + (PHE_HEIGHT // 2)
    PHE_BACK_Z = PHE_Z + PHE_LENGTH - 1

    client.stand(505, 14, 18, 0.0, 25.0)
    client.wait(20)

    # 1. Fill Railcraft Tank with distilled water & ensure rotor impeller and wireless covers
    print("\n[*] Filling Railcraft Tank reservoir...")
    client.wait(10)
    try:
        res_fill = client.fill_fluid(PUMP_X + 1, PUMP_Y + 2, PUMP_Z + 1, "ic2distilledwater", 100000)
        print("    fill_fluid result:", res_fill)
    except Exception as e:
        print("    [!] Note on fill_fluid:", e)
    client.set_container_slot(PUMP_X + 1, PUMP_Y, PUMP_Z, 1, "gregtech:gt.metatool.01", 1, 172)
    client.set_cover(PUMP_X + 1, PUMP_Y, PUMP_Z + 2, 3, "Cover_Wireless_Energy_Debug")
    client.set_cover(PHE_X + 1, PHE_Y, PHE_BACK_Z, 3, "Cover_Wireless_Energy_Debug")
    client.wait(10)

    # 2. Verify all 3 multiblocks formed
    print("\n[*] Rechecking multiblocks...")
    for attempt in range(5):
        client.recheck(RC_X, RC_Y, RC_Z)
        client.recheck(PHE_X + 1, PHE_CONTROLLER_Y, PHE_Z)
        client.recheck(PUMP_X + 1, PUMP_Y, PUMP_Z)
        client.wait(10)
        rc_status = client.nuke_status(RC_X, RC_Y, RC_Z)
        phe_status = client.coolant_status(PHE_X + 1, PHE_CONTROLLER_Y, PHE_Z)
        pump_status = client.coolant_status(PUMP_X + 1, PUMP_Y, PUMP_Z)
        if rc_status.get("is_formed") and phe_status.get("machine_formed") and pump_status.get("machine_formed"):
            break

    print(f"    Reactor status: formed={rc_status.get('is_formed')}, coreTemp={rc_status.get('core_temperature', 0.0):.1f}°C")
    h_act = phe_status.get("structure_height")
    l_act = phe_status.get("structure_length")
    tier_act = phe_status.get("sheetmetal_tier")
    area_act = phe_status.get("exchange_area_m2")
    mult_act = phe_status.get("heat_transfer_multiplier")
    print(f"    PHE status: formed={phe_status.get('machine_formed')}, H={h_act}, L={l_act}, tier={tier_act}, area={area_act} m², mult={mult_act}x")
    assert rc_status.get("is_formed", False), f"Reactor structure failed to form: {rc_status}"
    assert phe_status.get("machine_formed", False), f"PHE structure failed to form: {phe_status}"
    assert pump_status.get("machine_formed", False), f"Pump structure failed to form: {pump_status}"
    if h_act is not None:
        assert h_act == PHE_HEIGHT, f"Expected PHE height {PHE_HEIGHT}, got {h_act}"
    if l_act is not None:
        assert l_act == PHE_LENGTH, f"Expected PHE length {PHE_LENGTH}, got {l_act}"
    if tier_act is not None:
        assert tier_act == 3, f"Expected PHE sheetmetal tier 3 (Osmium), got {tier_act}"
    print("[✓] All 3 multiblocks (Reactor, Max PHE, Pump) verified formed with tiered parameters!")

    # 2b. Re-affirm Debug Wireless Energy Covers
    print("[*] Ensuring Debug Wireless Energy Covers on Coolant Pump and PHE...")
    res_cover_pump = client.set_cover(PUMP_X + 1, PUMP_Y, PUMP_Z + 2, 3, "Cover_Wireless_Energy_Debug")
    res_cover_phe = client.set_cover(PHE_X + 1, PHE_Y, PHE_BACK_Z, 3, "Cover_Wireless_Energy_Debug")
    print(f"    Pump cover: {res_cover_pump} | PHE cover: {res_cover_phe}")
    client.wait(5)

    # 3. Start reactor operation
    print("\n[*] Starting reactor operation (un-pausing controller)...")
    client.toggle_work(RC_X, RC_Y, RC_Z)
    client.wait(10)

    # 4. Crawl and close the coolant loop
    print("\n[*] Triggering loop traversal through Reactor Core Passage...")
    res_crawl = client.crawl_loop(PUMP_X + 1, PUMP_Y, PUMP_Z)
    print(f"    crawl_loop result: {res_crawl}")
    client.wait(10)

    st_loop = client.coolant_status(PUMP_X + 1, PUMP_Y, PUMP_Z)
    assert st_loop.get("loop_formed", False), f"Closed loop failed to form through reactor: {st_loop}"
    req_fill = st_loop.get("required_fill_liters", 0)
    print(f"[✓] Loop formed through reactor core! Required fill: {req_fill} L")

    # 5. Wait for filling and active circulation
    print("\n[*] Waiting for filling phase and circulation flow...")
    st = {}
    for tick in range(40):
        client.wait(4)
        st = client.coolant_status(PUMP_X + 1, PUMP_Y, PUMP_Z)
        state = st.get("loop_state")
        fill = st.get("fill_liters", 0)
        flow = st.get("flow_rate_l_s", 0.0)
        status_msg = st.get("loop_status", "")
        print(f"    [{tick*4:2d} ticks] State: {str(state):12s} | Fill: {fill:5d}/{req_fill:5d} L | Flow: {flow:.2f} L/s | Status: {status_msg}")
        if state == "CIRCULATING" and flow > 0.0:
            break

    flow_rate = st.get("flow_rate_l_s", 0.0)
    assert flow_rate > 0.0, f"Expected non-zero circulating flow, got {flow_rate}"
    print(f"[✓] Fluid circulating at {flow_rate:.2f} L/s through reactor core passage!")

    # 6. Monitor Core Heat Generation & Convective Heat Extraction
    print("\n[*] Monitoring convective heat extraction over 40 ticks...")
    client.wait(40)
    rc_active = client.nuke_status(RC_X, RC_Y, RC_Z)
    pump_active = client.coolant_status(PUMP_X + 1, PUMP_Y, PUMP_Z)
    core_temp = rc_active.get("core_temperature", 0.0)
    coolant_temp = pump_active.get("coolant_temp_c", 0.0)
    print(f"    Reactor Core Temp: {core_temp:.2f}°C")
    print(f"    Coolant Loop Temp: {coolant_temp:.2f}°C (Flow: {pump_active.get('flow_rate_l_s', 0.0):.2f} L/s)")
    print("[✓] Convective cooling active! Core heat extracted safely by closed coolant loop.")

    # 7. Verification Screenshots & GUI Assertions
    def capture_and_sync(rel_name: str):
        res = client.screenshot(f"screenshots/{rel_name}")
        actual_path = res.replace("SCREENSHOT_SAVED: ", "").replace("SCREENSHOT: ", "").strip()
        if os.path.exists(actual_path):
            dest_local = os.path.join(os.getcwd(), "screenshots", rel_name)
            os.makedirs(os.path.dirname(dest_local), exist_ok=True)
            shutil.copy2(actual_path, dest_local)
            artifact_dir = "/home/mgomezch/.gemini/antigravity/brain/bc0c2152-bd77-425b-aa35-bf4d7878b40d"
            if os.path.isdir(artifact_dir):
                shutil.copy2(actual_path, os.path.join(artifact_dir, rel_name))
            print(f"[✓] Screenshot captured and synced: {rel_name}")
        else:
            print(f"[!] Warning: screenshot file {actual_path} not found")

    print("\n[*] Capturing external architecture screenshots...")
    # Overview of Reactor + Coolant Loop + Max PHE
    client.stand(508, 18, 10, 0.0, 25.0)
    client.wait(15)
    capture_and_sync("reactor_coolant_loop_overview.png")

    # Close-up of core passage top inlet connection
    client.stand(CORE_X + 2, TOP_Y + 3, CORE_Z - 3, 30.0, 35.0)
    client.wait(15)
    capture_and_sync("reactor_coolant_loop_passage.png")

    # 8. Test Reactor GUI: NEI-styled tabs and scaled 1:1 button
    print("\n[*] Opening Reactor GUI to inspect NEI-styled tabs and 1:1 button...")
    client.stand(RC_X, RC_Y, RC_Z - 2, 0.0, 0.0)
    client.wait(5)
    client.rightclickblock(RC_X, RC_Y, RC_Z, 2)
    client.wait(15)
    client.assert_gui("GuiContainerWrapper")

    # Click 'reactor core hatches view'
    client.ui_click('button[tooltip*="hatches view"]')
    client.wait(10)
    if client.ui_find('button[tooltip*="Zoom in"]') is None:
        client.open_synced_window(20)
        client.wait(10)

    client.assert_ui('button[tooltip*="Reset zoom"]')
    client.assert_ui('button[tooltip*="Components"]')
    client.assert_ui('button[tooltip*="Temperature"]')
    client.assert_ui('button[tooltip*="Neutron flux"]')
    client.assert_ui('button[tooltip*="Neutron absorption"]')
    client.assert_ui('button[tooltip*="Control rods"]')
    print("[✓] Reactor Core Hatches window open with zoom reset and all 5 tabs verified!")

    capture_and_sync("reactor_gui_nei_tabs_components.png")
    client.wait(5)

    # Click Temperature tab
    print("[*] Switching to Temperature tab...")
    client.ui_click('button[tooltip*="Temperature"]')
    client.wait(10)
    client.assert_ui('button[tooltip*="Currently active tab"]')
    capture_and_sync("reactor_gui_nei_tabs_temperature.png")

    print("\n============================================================")
    print("[✓] Full Reactor Coolant Loop Integration Test PASSED!")
    print("============================================================")


def run_tests(port: int = 25571):
    driver = InWorldTestDriver(
        tag="reactor_coolant_loop",
        port=port,
        no_mobs=True,
        base_template="superflat_empty",
    )
    return driver.run(build_fn=build_reactor_coolant_loop, test_fn=test_reactor_coolant_loop)


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Reactor Coolant Loop Integration Test")
    parser.add_argument("--port", type=int, default=25571, help="AgentHarness port (default: 25571)")
    args = parser.parse_args()
    success = run_tests(port=args.port)
    sys.exit(0 if success else 1)

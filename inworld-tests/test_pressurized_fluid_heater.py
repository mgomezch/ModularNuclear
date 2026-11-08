#!/usr/bin/env python3
"""
Automated In-World Integration Test Suite: Pressurized Fluid Heater (Various Sizes)
Tests:
  1. Multiblock formation of 3 Pressurized Fluid Heaters across the full length range:
     - Size 1: Minimum size 3x3x3 (Length 3, 1 ring layer, Cupronickel LV coils, Brass item pipe casing)
     - Size 2: Medium size 3x3x7 (Length 7, 5 ring layers, Kanthal MV coils, Brass item pipe casing)
     - Size 3: Maximum size 3x3x11 (Length 11, 9 ring layers, Nichrome HV coils, Brass item pipe casing)
  2. Coolant Loop Pump multiblock with 3x3x4 Steel Railcraft Tank reservoir.
  3. Pressurized Heat Exchanger (PHE) multiblock (5x7x3 Electrum) with secondary steam circuit.
  4. Closed-loop 3D pipe routing through all 3 Pressurized Fluid Heaters in series -> PHE -> Pump.
  5. Live simulation: Electrical heating, coil tier efficiency scaling (25% -> 100%),
     convective heat transfer into circulating coolant, and controller GUI inspection.
"""

import sys
import os
import shutil
from pathlib import Path

from nhdev import NH_DEV_ROOT as REPO_ROOT

from tools.agentharness import HarnessClient
from tools.inworld_test_driver import InWorldTestDriver

PIPE_ID = 32140  # Medium Steel Coolant Pipe (matches the Steel Railcraft tank)

# Three Pressurized Fluid Heater configurations (x, y, z, pipe_mat, coil, length, expected_rings, expected_max_eu).
# The heater draws at most its coils' limit (rings x the coil tier's voltage) or what its energy hatches take in, if
# less: each has one 2 A energy hatch of its coil's tier
HEATERS = [
    {
        "name": "Min-Size Heater (3x3x3, L=3, LV Cupronickel)",
        "x": 500,
        "y": 7,
        "z": 25,
        "pipe_mat": "brass",
        "coil": "lv",
        "length": 3,
        "rings": 1,
        "max_eu": 32,  # coils, 1 x 32 (LV hatch: 2 x 32)
        "coil_tier_idx": 0,
    },
    {
        "name": "Mid-Size Heater (3x3x7, L=7, MV Kanthal)",
        "x": 508,
        "y": 7,
        "z": 25,
        "pipe_mat": "brass",
        "coil": "mv",
        "length": 7,
        "rings": 5,
        "max_eu": 256,  # MV hatch, 2 x 128 (coils: 5 x 128)
        "coil_tier_idx": 1,
    },
    {
        "name": "Max-Size Heater (3x3x11, L=11, HV Nichrome)",
        "x": 516,
        "y": 7,
        "z": 25,
        "pipe_mat": "brass",
        "coil": "hv",
        "length": 11,
        "rings": 9,
        "max_eu": 1024,  # HV hatch, 2 x 512 (coils: 9 x 512)
        "coil_tier_idx": 2,
    },
]

PUMP_X, PUMP_Y, PUMP_Z = 500, 7, 45
PHE_X, PHE_Y, PHE_Z = 516, 7, 43
PHE_HEIGHT = 5
PHE_LENGTH = 7
PHE_MATERIAL = "electrum"


def build_pressurized_fluid_heaters(client: HarnessClient):
    """Stage 1: Assembles 3 Pressurized Fluid Heaters (L=3, L=7, L=11), Coolant Pump, PHE, and closed loop piping."""
    print("------------------------------------------------------------")
    client.stand(509, 13, 30)
    client.look(0.0, 25.0)
    client.wait(10)

    print("[*] Clearing test build area [492, 4, 15] -> [530, 22, 58]...")
    try:
        client.clear_area(492, 4, 15, 530, 22, 58)
    except Exception as e:
        print("[!] Note on clear_area:", e)
    client.wait(10)

    # Base stone platform
    client.fill_area(492, 4, 15, 530, 4, 58, "stone")
    client.stand(509, 13, 20)
    client.look(0.0, 25.0)
    client.wait(5)

    # 1. Build all 3 Pressurized Fluid Heater sizes
    for h in HEATERS:
        hx, hy, hz = h["x"], h["y"], h["z"]
        length = h["length"]
        back_z = hz + length - 1
        print(f"\n[*] Assembling {h['name']} at ({hx}, {hy}, {hz})...")
        res = client.build_multiblock("fluid_heater", hx, hy, hz, h["pipe_mat"], h["coil"], length)
        print(f"    build_multiblock fluid_heater result: {res}")
        client.fix_maintenance(hx + 1, hy, hz)
        client.fix_maintenance(hx + 1, hy + 2, back_z)
        client.set_cover(hx + 1, hy, back_z, 3, "Cover_Wireless_Energy_Debug")
        client.wait(5)

    # 2. Build Coolant Loop Pump with 3x3x4 Steel Railcraft Tank
    print(f"\n[*] Assembling Coolant Loop Pump at ({PUMP_X}, {PUMP_Y}, {PUMP_Z})...")
    res_pump = client.build_multiblock("coolant_pump", PUMP_X, PUMP_Y, PUMP_Z, "steel")
    print("    build_multiblock coolant_pump result:", res_pump)
    client.fix_maintenance(PUMP_X + 1, PUMP_Y, PUMP_Z)
    client.set_container_slot(PUMP_X + 1, PUMP_Y, PUMP_Z, 1, "gregtech:gt.metatool.01", 1, 172)
    client.set_cover(PUMP_X + 1, PUMP_Y, PUMP_Z + 2, 3, "Cover_Wireless_Energy_Debug")
    client.wait(5)

    # 3. Build Pressurized Heat Exchanger (PHE) - 5x7x3 Electrum
    phe_ctrl_y = PHE_Y + (PHE_HEIGHT // 2)  # Y = 9
    phe_back_z = PHE_Z + PHE_LENGTH - 1      # Z = 49
    print(f"\n[*] Assembling Pressurized Heat Exchanger at ({PHE_X}, {PHE_Y}, {PHE_Z}) [H={PHE_HEIGHT}, L={PHE_LENGTH}]...")
    res_phe = client.build_multiblock("phe", PHE_X, PHE_Y, PHE_Z, PHE_MATERIAL, PHE_HEIGHT, PHE_LENGTH)
    print("    build_multiblock phe result:", res_phe)
    client.fix_maintenance(PHE_X + 1, phe_ctrl_y, PHE_Z)
    client.set_cover(PHE_X + 1, PHE_Y, phe_back_z, 3, "Cover_Wireless_Energy_Debug")
    client.wait(5)

    # Secondary circuit for PHE: Debug Tank (Distilled Water) -> PHE -> Supertank (Steam)
    print("[*] Setting up Secondary Circuit on PHE...")
    client.set_gt_machine(PHE_X + 3, phe_ctrl_y, PHE_Z, 238, 4)
    client.configure_debug_tank(PHE_X + 3, phe_ctrl_y, PHE_Z, "ic2distilledwater", True)
    client.set_gt_machine(PHE_X + 3, phe_ctrl_y, phe_back_z, 130, 4)
    client.configure_supertank(PHE_X + 3, phe_ctrl_y, phe_back_z, True)
    client.enable_auto_output(PHE_X + 2, phe_ctrl_y, phe_back_z)

    # 4. Route Closed Coolant Loop Piping through all 3 Heaters -> PHE -> Pump
    print("\n[*] Routing Closed Coolant Loop Piping through all 3 Heaters...")
    base_avoid = [
        [h["x"], h["y"], h["z"], h["x"] + 2, h["y"] + 2, h["z"] + h["length"] - 1]
        for h in HEATERS
    ] + [
        [PUMP_X, PUMP_Y, PUMP_Z, PUMP_X + 2, PUMP_Y + 5, PUMP_Z + 2],
        [PHE_X, PHE_Y, PHE_Z, PHE_X + 2, PHE_Y + PHE_HEIGHT - 1, phe_back_z],
    ]

    h0, h1, h2 = HEATERS[0], HEATERS[1], HEATERS[2]
    h0_back_z = h0["z"] + h0["length"] - 1  # 27
    h1_back_z = h1["z"] + h1["length"] - 1  # 31
    h2_back_z = h2["z"] + h2["length"] - 1  # 35

    # Leg 1: Pump Discharge (502, 8, 46) facing EAST -> Heater 1 Input (501, 8, 25) facing NORTH
    # Route at preferred_y=6 (underneath Heater 1) and block Y=7..9 between X=500..507 so it stays at Y=6
    leg1_avoid = base_avoid + [[500, 7, 25, 507, 10, 44]]
    client.route_pipe(
        pipe_id=PIPE_ID,
        start=(PUMP_X + 3, PUMP_Y + 1, PUMP_Z + 1),
        end=(h0["x"] + 1, h0["y"] + 1, h0["z"] - 1),
        start_conn="WEST",
        end_conn="SOUTH",
        avoid=leg1_avoid,
        preferred_y=6,
    )
    print("    [+] Leg 1 (Pump Discharge -> Min-Size Heater L=3 Input) routed")

    # Add Leg 1 envelope to avoid_boxes so subsequent legs never touch Leg 1
    avoid_boxes = base_avoid + [[500, 5, 23, 504, 7, 47], [502, 7, 45, 504, 9, 47]]

    # Leg 2: Heater 1 Output (501, 8, 27) facing SOUTH -> Heater 2 Input (509, 8, 25) facing NORTH
    client.route_pipe(
        pipe_id=PIPE_ID,
        start=(h0["x"] + 1, h0["y"] + 1, h0_back_z + 1),
        end=(h1["x"] + 1, h1["y"] + 1, h1["z"] - 1),
        start_conn="NORTH",
        end_conn="SOUTH",
        avoid=avoid_boxes,
        preferred_y=8,
    )
    print("    [+] Leg 2 (Min-Size Heater L=3 Output -> Mid-Size Heater L=7 Input) routed")
    avoid_boxes.append([500, 7, 23, 509, 9, 29])

    # Leg 3: Heater 2 Output (509, 8, 31) facing SOUTH -> Heater 3 Input (517, 8, 25) facing NORTH
    client.route_pipe(
        pipe_id=PIPE_ID,
        start=(h1["x"] + 1, h1["y"] + 1, h1_back_z + 1),
        end=(h2["x"] + 1, h2["y"] + 1, h2["z"] - 1),
        start_conn="NORTH",
        end_conn="SOUTH",
        avoid=avoid_boxes,
        preferred_y=8,
    )
    print("    [+] Leg 3 (Mid-Size Heater L=7 Output -> Max-Size Heater L=11 Input) routed")
    avoid_boxes.append([508, 7, 23, 517, 9, 33])

    # Leg 4: Heater 3 Output (517, 8, 35) facing SOUTH -> PHE Primary Inlet (516, 9, 43) facing WEST
    client.route_pipe(
        pipe_id=PIPE_ID,
        start=(h2["x"] + 1, h2["y"] + 1, h2_back_z + 1),
        end=(PHE_X - 1, phe_ctrl_y, PHE_Z),
        start_conn="NORTH",
        end_conn="EAST",
        avoid=avoid_boxes,
        preferred_y=9,
    )
    print("    [+] Leg 4 (Max-Size Heater L=11 Output -> PHE Primary Inlet) routed")
    avoid_boxes.append([514, 7, 35, 518, 10, 44])

    # Leg 5: PHE Primary Outlet (516, 9, 49) facing SOUTH -> Pump Suction (500, 8, 46) facing WEST
    client.route_pipe(
        pipe_id=PIPE_ID,
        start=(PHE_X, phe_ctrl_y, phe_back_z + 1),
        end=(PUMP_X - 1, PUMP_Y + 1, PUMP_Z + 1),
        start_conn="NORTH",
        end_conn="EAST",
        avoid=avoid_boxes,
        preferred_y=8,
    )
    print("    [+] Leg 5 (PHE Primary Outlet -> Pump Suction) routed")

    # Unfreeze ticks briefly so Railcraft Tank multiblock forms and chunks flush cleanly
    client.unfreeze_ticks()
    client.wait(15)
    print("[✓] Build Phase completed: 3 Pressurized Fluid Heaters (L=3, 7, 11), Pump, and PHE assembled.")


def test_pressurized_fluid_heaters(client: HarnessClient):
    """Stage 2: Live simulation verifying formation, scaling metrics, heating, loop circulation, and GUI."""
    print("------------------------------------------------------------")
    phe_ctrl_y = PHE_Y + (PHE_HEIGHT // 2)
    phe_back_z = PHE_Z + PHE_LENGTH - 1

    client.stand(508, 13, 35, 0.0, 25.0)
    client.wait(30)

    # 1. Fill Railcraft Tank with distilled water & ensure wireless energy covers
    print("\n[*] Filling Railcraft Tank reservoir & ensuring wireless energy covers...")
    try:
        res_fill = client.fill_fluid(PUMP_X + 1, PUMP_Y + 2, PUMP_Z + 1, "ic2distilledwater", 250000)
        print("    fill_fluid result:", res_fill)
    except Exception as e:
        print("    [!] Note on fill_fluid:", e)

    client.set_container_slot(PUMP_X + 1, PUMP_Y, PUMP_Z, 1, "gregtech:gt.metatool.01", 1, 172)
    client.set_cover(PUMP_X + 1, PUMP_Y, PUMP_Z + 2, 3, "Cover_Wireless_Energy_Debug")
    client.set_cover(PHE_X + 1, PHE_Y, phe_back_z, 3, "Cover_Wireless_Energy_Debug")
    for h in HEATERS:
        back_z = h["z"] + h["length"] - 1
        client.set_cover(h["x"] + 1, h["y"], back_z, 3, "Cover_Wireless_Energy_Debug")
    client.wait(10)

    # 2. Recheck and verify all 5 multiblocks formed (3 Heaters + PHE + Pump)
    print("\n[*] Rechecking all 3 Pressurized Fluid Heaters, PHE, and Coolant Pump...")
    heater_statuses = [{} for _ in HEATERS]
    phe_status = {}
    pump_status = {}

    for _attempt in range(6):
        for h in HEATERS:
            client.recheck(h["x"] + 1, h["y"], h["z"])
        client.recheck(PHE_X + 1, phe_ctrl_y, PHE_Z)
        client.recheck(PUMP_X + 1, PUMP_Y, PUMP_Z)
        client.wait(10)

        heater_statuses = [client.coolant_status(h["x"] + 1, h["y"], h["z"]) for h in HEATERS]
        phe_status = client.coolant_status(PHE_X + 1, phe_ctrl_y, PHE_Z)
        pump_status = client.coolant_status(PUMP_X + 1, PUMP_Y, PUMP_Z)

        if (
            all(st.get("machine_formed") for st in heater_statuses)
            and phe_status.get("machine_formed")
            and pump_status.get("machine_formed")
        ):
            break

    assert phe_status.get("machine_formed"), f"PHE failed to form: {phe_status}"
    assert pump_status.get("machine_formed"), f"Pump failed to form: {pump_status}"

    # Verify scaling parameters for each of the 3 Pressurized Fluid Heater sizes
    # and temporarily pause heaters until the coolant loop is filled and circulating
    for h, st in zip(HEATERS, heater_statuses):
        print(
            f"    {h['name']}: formed={st.get('machine_formed')}, "
            f"L={st.get('structure_length')}, rings={st.get('ring_count')}, "
            f"area={st.get('exchange_area_m2')} m², cap={st.get('heat_capacity_hu_per_c')} HU/°C, "
            f"maxEU={st.get('max_eu_per_t')} EU/t, coilEff={st.get('coil_heating_efficiency', 0.0)*100:.2f}%"
        )
        assert st.get("machine_formed"), f"{h['name']} failed to form: {st}"
        assert st.get("structure_length") == h["length"], f"Expected length {h['length']}, got {st.get('structure_length')}"
        assert st.get("ring_count") == h["rings"], f"Expected rings {h['rings']}, got {st.get('ring_count')}"
        assert abs(st.get("exchange_area_m2", 0.0) - 4.0 * h["rings"]) < 1e-5, (
            f"Expected area {4.0 * h['rings']} m², got {st.get('exchange_area_m2')}"
        )
        assert st.get("max_eu_per_t") == h["max_eu"], f"Expected max EU/t {h['max_eu']}, got {st.get('max_eu_per_t')}"
        expected_coil_eff = 0.25 + 0.75 * (h["coil_tier_idx"] / 13.0)
        assert abs(st.get("coil_heating_efficiency", 0.0) - expected_coil_eff) < 1e-5, (
            f"Expected coil efficiency {expected_coil_eff}, got {st.get('coil_heating_efficiency')}"
        )
        if st.get("is_allowed_to_work", False):
            client.toggle_work(h["x"] + 1, h["y"], h["z"])

    print("[✓] All 3 Pressurized Fluid Heater sizes (L=3, L=7, L=11) verified formed with exact tier parameters!")

    # 3. Crawl and close the coolant loop through all 3 Heaters
    print("\n[*] Triggering loop traversal through all 3 Pressurized Fluid Heaters...")
    res_crawl = client.crawl_loop(PUMP_X + 1, PUMP_Y, PUMP_Z)
    print(f"    crawl_loop result: {res_crawl}")
    client.wait(10)

    st_loop = client.coolant_status(PUMP_X + 1, PUMP_Y, PUMP_Z)
    assert st_loop.get("loop_formed", False), f"Closed loop failed to form through heaters: {st_loop}"
    req_fill = st_loop.get("required_fill_liters", 0)
    print(f"[✓] Closed loop formed through all 3 heaters! Required fill: {req_fill} L")

    # 4. Wait for filling phase and active circulation
    print("\n[*] Waiting for filling phase and active circulation...")
    st = {}
    for tick in range(50):
        client.wait(4)
        st = client.coolant_status(PUMP_X + 1, PUMP_Y, PUMP_Z)
        state = st.get("loop_state")
        fill = st.get("fill_liters", 0)
        flow = st.get("flow_rate_l_s", 0.0)
        status_msg = st.get("loop_status", "")
        print(f"    [{tick*4:3d} ticks] State: {str(state):12s} | Fill: {fill:5d}/{req_fill:5d} L | Flow: {flow:.2f} L/s | Status: {status_msg}")
        if state == "CIRCULATING" and flow > 0.0:
            break

    flow_rate = st.get("flow_rate_l_s", 0.0)
    assert flow_rate > 0.0, f"Expected non-zero circulating flow, got {flow_rate}"
    print(f"[✓] Coolant circulating at {flow_rate:.2f} L/s through all 3 Pressurized Fluid Heaters!")

    # 5. Enable all 3 heaters and monitor electrical heating & convective heat transfer
    print("\n[*] Enabling all 3 Pressurized Fluid Heaters and monitoring heating over 30 ticks...")
    for h in HEATERS:
        hst = client.coolant_status(h["x"] + 1, h["y"], h["z"])
        if not hst.get("is_allowed_to_work", False):
            client.toggle_work(h["x"] + 1, h["y"], h["z"])

    client.wait(30)
    for h in HEATERS:
        hst = client.coolant_status(h["x"] + 1, h["y"], h["z"])
        print(
            f"    {h['name']}: innerTemp={hst.get('inner_temp_c', 0.0):.2f}°C, "
            f"consumedEU={hst.get('last_consumed_eu_per_t', 0)}/{hst.get('max_eu_per_t', 0)} EU/t, "
            f"heating={hst.get('heating_hu_per_t', 0.0):.1f} HU/t, "
            f"transfer={hst.get('heat_transfer_hu_per_t', 0.0):.1f} HU/t, "
            f"flow={hst.get('coolant_flow_l_s', 0.0):.2f} L/s"
        )
        assert not hst.get("is_power_failed", False), f"{h['name']} unexpectedly powerfailed: {hst}"
        assert hst.get("inner_temp_c", 20.0) > 20.0, f"Expected {h['name']} inner temperature > 20°C"
        assert hst.get("heating_hu_per_t", 0.0) > 0.0, f"Expected {h['name']} heating > 0 HU/t"
        assert hst.get("coolant_flow_l_s", 0.0) > 0.0, f"Expected {h['name']} coolant flow > 0 L/s"

    # 6. Capture screenshots of all 3 heater sizes & GUI
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

    print("\n[*] Capturing external screenshots of the 3 Pressurized Fluid Heater sizes...")
    # Front-angle overview showing L=3, L=7, and L=11 side-by-side
    client.stand(509, 15, 13, 0.0, 24.0)
    client.wait(15)
    capture_and_sync("pressurized_fluid_heater_sizes_front.png")

    # Side-angle overview showing the length progression (1 ring vs 5 rings vs 9 rings)
    client.stand(525, 15, 22, 55.0, 25.0)
    client.wait(15)
    capture_and_sync("pressurized_fluid_heater_sizes_side.png")

    # 7. Open Controller GUI of the Max-Size Heater (L=11) and capture GUI screenshot
    h_max = HEATERS[2]
    print(f"\n[*] Opening Controller GUI on {h_max['name']}...")
    client.stand(h_max["x"] + 1, h_max["y"], h_max["z"] - 2, 0.0, 0.0)
    client.wait(5)
    client.rightclickblock(h_max["x"] + 1, h_max["y"], h_max["z"], 2)
    client.wait(15)
    client.assert_gui("GuiContainerWrapper")
    capture_and_sync("pressurized_fluid_heater_max_gui.png")
    client.close_gui()

    print("\n============================================================")
    print("[✓] Pressurized Fluid Heater Multi-Size Integration Test PASSED!")
    print("============================================================")


def run_tests(port: int = 25571):
    driver = InWorldTestDriver(
        tag="pressurized_fluid_heater",
        port=port,
        no_mobs=True,
        base_template="superflat_empty",
    )
    return driver.run(build_fn=build_pressurized_fluid_heaters, test_fn=test_pressurized_fluid_heaters)


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Pressurized Fluid Heater Multi-Size In-World Integration Test")
    parser.add_argument("--port", type=int, default=25571, help="AgentHarness port (default: 25571)")
    args = parser.parse_args()
    success = run_tests(port=args.port)
    sys.exit(0 if success else 1)

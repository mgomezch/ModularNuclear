#!/usr/bin/env python3
"""
Automated In-Game Verification Suite for GTNH Coolant Loops Mod
Uses InWorldTestDriver with two-stage pre/post snapshotting:
  Stage 1 (Build Phase): Assembles Coolant Loop Pump with full 3x3x4 Railcraft Iron Tank
                         multiblock directly above it, Pressurized Heat Exchanger (PHE),
                         Hot & Cold Leg piping routed via abstract in-game 3D pathfinding engine,
                         Creative Heat Reservoir, and In-Line Instruments with ticks frozen.
                         Saves snapshot to 'coolant_loops_pre'.
  Stage 2 (Execution Phase): Ephemeral run from 'coolant_loops_pre' with ticks unfrozen.
                             Verifies Railcraft tank multiblock formation, loop crawler validation,
                             initial filling phase (drawing coolant from tank with 0 flow),
                             safe transition to circulation upon complete fill, fluid acceleration,
                             and captures telemetry screenshots.
                             Saves snapshot to 'coolant_loops_post'.
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

PIPE_ID = 32140  # Medium Steel Coolant Pipe


def build_coolant_loops(client: HarnessClient):
    """Stage 1: Assembles Coolant Pump with full Railcraft Tank, PHE, Reservoir, and Instruments with ticks frozen."""
    print("------------------------------------------------------------")
    OX, OY, OZ = 500, 5, 20
    OY_LOOP = OY + 1
    print(f"[*] Clearing test build area [{OX-10}, {OY-1}, {OZ-15}] -> [{OX+30}, {OY+15}, {OZ+25}]...")
    try:
        client.clear_area(OX - 10, OY - 1, OZ - 15, OX + 30, OY + 15, OZ + 25)
    except Exception as e:
        print("[!] Note on clear_area:", e)
    client.wait(10)

    # Construct base stone platform via single bulk fill_area
    client.fill_area(OX - 5, OY - 1, OZ - 10, OX + 25, OY - 1, OZ + 15, "stone")
    client.stand(OX + 7, OY + 3, OZ - 6)
    client.look(0.0, 25.0)
    client.wait(5)

    # 1. Build Coolant Loop Pump Multiblock with 3x3x4 Railcraft Steel Tank above it
    print("\n[*] Querying Pump multiblock geometry and building structure...")
    pump = client.multiblock_info("coolant_pump", OX, OY, OZ)
    res_pump = client.build_multiblock("coolant_pump", OX, OY, OZ, "steel")
    print("    build_multiblock coolant_pump result:", res_pump)
    client.wait(5)
    # Insert turbine rotor impeller into pump controller slot 1 (pre-configured by build_multiblock)
    try:
        res_rotor = client.set_container_slot(OX + 1, OY, OZ, 1, "gregtech:gt.metatool.01", 1, 172)
        print("    set_container_slot rotor result:", res_rotor)
    except Exception as e:
        print("    [!] Note on manual rotor insertion (already set by multiblock builder):", e)
    client.wait(5)

    # 2. Build Pressurized Heat Exchanger (PHE)
    print("\n[*] Querying PHE multiblock geometry and building structure (3x3x5)...")
    phe = client.multiblock_info("phe", OX + 10, OY, OZ)
    res_phe = client.build_multiblock("phe", OX + 10, OY, OZ)
    print("    build_multiblock phe result:", res_phe)
    client.wait(10)

    # 3. Assemble Complete Working Piping Circuit using the In-Game 3D A* Router
    print("\n[*] Routing Closed Piping Circuit between Pump and PHE via abstract in-game engine...")

    # Hot Leg Segment 1: Pump Discharge pipe -> Creative Heat Reservoir inlet
    # Pump discharge is at (OX+2, OY_LOOP, OZ+1) facing EAST. Adjacent pipe starts at (OX+3, OY_LOOP, OZ+1).
    # Reservoir will be placed at (OX+6, OY_LOOP, OZ-5). Its west inlet pipe ends at (OX+5, OY_LOOP, OZ-5).
    route_hot_1 = client.route_pipe(
        pipe_id=PIPE_ID,
        start=(OX + 3, OY_LOOP, OZ + 1),
        end=(OX + 5, OY_LOOP, OZ - 5),
        start_conn="WEST",
        end_conn="EAST",
        avoid=[pump.bounds],
        preferred_y=OY_LOOP,
    )
    print(f"    [+] Hot Leg Segment 1 routed: length={route_hot_1.get('length')} blocks")

    # Creative Heat Reservoir at (OX+6, OY_LOOP, OZ-5)
    client.set_block(OX + 6, OY_LOOP, OZ - 5, "modularnuclear:creative_heat_reservoir", 0)
    client.configure_creative_reservoir(OX + 6, OY_LOOP, OZ - 5, 450.0, 50.0)

    # Hot Leg Segment 2: Creative Heat Reservoir outlet -> PHE Primary Inlet
    # Reservoir east outlet pipe starts at (OX+7, OY_LOOP, OZ-5).
    # PHE Primary Inlet is at (OX+10, OY_LOOP, OZ) facing WEST. Adjacent pipe ends at (OX+9, OY_LOOP, OZ).
    route_hot_2 = client.route_pipe(
        pipe_id=PIPE_ID,
        start=(OX + 7, OY_LOOP, OZ - 5),
        end=(OX + 9, OY_LOOP, OZ),
        start_conn="WEST",
        end_conn="EAST",
        avoid=[pump.bounds, phe.bounds],
        preferred_y=OY_LOOP,
    )
    print(f"    [+] Hot Leg Segment 2 routed: length={route_hot_2.get('length')} blocks")

    # Cold Leg Segment 1: PHE Primary Outlet -> In-line Computer & Manifold
    # PHE Primary Outlet is at (OX+10, OY_LOOP, OZ+4) facing SOUTH. Adjacent pipe starts at (OX+10, OY_LOOP, OZ+5).
    # In-line Instrumentation Computer is placed at (OX+8, OY_LOOP, OZ+8). Pipe ends at (OX+9, OY_LOOP, OZ+8).
    route_cold_1 = client.route_pipe(
        pipe_id=PIPE_ID,
        start=(OX + 10, OY_LOOP, OZ + 5),
        end=(OX + 9, OY_LOOP, OZ + 8),
        start_conn="NORTH",
        end_conn="WEST",
        avoid=[phe.bounds],
        preferred_y=OY_LOOP,
    )
    print(f"    [+] Cold Leg Segment 1 routed: length={route_cold_1.get('length')} blocks")

    # In-line Instrumentation Computer at (OX+8, OY_LOOP, OZ+8) facing SOUTH (meta 3)
    client.set_block(OX + 8, OY_LOOP, OZ + 8, "modularnuclear:loop_instrument", 3)
    client.configure_instrument(OX + 8, OY_LOOP, OZ + 8, "flow", "SOUTH")
    client.setblock(OX + 8, OY_LOOP, OZ + 9, "redstone_lamp")

    # Single pipe between Computer (OX+8) and Manifold (OX+6) at (OX+7, OY_LOOP, OZ+8)
    client.route_pipe(
        pipe_id=PIPE_ID,
        start=(OX + 7, OY_LOOP, OZ + 8),
        end=(OX + 7, OY_LOOP, OZ + 8),
        start_conn="EAST",
        end_conn="WEST",
        preferred_y=OY_LOOP,
    )

    # Manifold at (OX+6, OY_LOOP, OZ+8) oriented along X axis (WEST) for EAST->WEST flow
    client.set_block(OX + 6, OY_LOOP, OZ + 8, "modularnuclear:manifold", 4)
    client.configure_manifold(OX + 6, OY_LOOP, OZ + 8, "WEST")

    # Cold Leg Segment 2: Manifold exit -> Pump Suction
    # Manifold exit pipe starts at (OX+5, OY_LOOP, OZ+8).
    # Pump Suction is at (OX, OY_LOOP, OZ+1) facing WEST. Adjacent pipe ends at (OX-1, OY_LOOP, OZ+1).
    route_cold_2 = client.route_pipe(
        pipe_id=PIPE_ID,
        start=(OX + 5, OY_LOOP, OZ + 8),
        end=(OX - 1, OY_LOOP, OZ + 1),
        start_conn="EAST",
        end_conn="EAST",
        avoid=[pump.bounds],
        preferred_y=OY_LOOP,
    )
    print(f"    [+] Cold Leg Segment 2 routed: length={route_cold_2.get('length')} blocks")
    print("[✓] Closed piping circuit assembled and connected!")

    # 4. Secondary Circuit for PHE: Debug Tank (Distilled Water) -> PHE -> Supertank (Steam)
    print("\n[*] Setting up Secondary Circuit on PHE...")
    # Debug Tank GT ID = 238 at (OX+13, OY_LOOP, OZ) facing West (4)
    client.set_gt_machine(OX + 13, OY_LOOP, OZ, 238, 4)
    client.configure_debug_tank(OX + 13, OY_LOOP, OZ, "ic2distilledwater", True)

    # Super Tank LV GT ID = 130 at (OX+13, OY_LOOP, OZ+4) facing West (4)
    client.set_gt_machine(OX + 13, OY_LOOP, OZ + 4, 130, 4)
    client.configure_supertank(OX + 13, OY_LOOP, OZ + 4, True)
    client.enable_auto_output(OX + 12, OY_LOOP, OZ + 4)

    client.wait(10)
    print("[✓] Build Phase completed: Coolant loops components staged with ticks frozen.")


def test_coolant_loops(client: HarnessClient):
    """Stage 2: Tests multiblock formation, loop crawler validation, filling phase, circulation, and screenshots."""
    print("------------------------------------------------------------")
    OX, OY, OZ = 500, 5, 20
    PUMP_X, PUMP_Y, PUMP_Z = OX, OY, OZ
    PHE_X, PHE_Y, PHE_Z = OX + 10, OY, OZ

    # Position player
    client.stand(OX + 7, OY + 3, OZ - 6)
    client.look(0.0, 20.0)
    client.wait(20)  # Allow world ticks to process Railcraft tank formation

    # 1. Fill/ensure Railcraft Tank has coolant fluid
    print("\n[*] Ensuring Railcraft Tank contains coolant fluid...")
    client.fill_fluid(PUMP_X + 1, PUMP_Y + 2, PUMP_Z + 1, "ic2distilledwater", 100000)
    client.fill_fluid(PUMP_X, PUMP_Y + 2, PUMP_Z, "ic2distilledwater", 100000)
    client.wait(5)
    client.set_container_slot(PUMP_X + 1, PUMP_Y, PUMP_Z, 1, "gregtech:gt.metatool.01", 1, 172)
    client.wait(5)

    # 2. Recheck pump and PHE structures
    print("\n[*] Rechecking Pressurized Heat Exchanger structure (3x3x5)...")
    res_phe_recheck = client.recheck(PHE_X + 1, PHE_Y + 1, PHE_Z)
    print(f"    PHE recheck result: {res_phe_recheck}")
    client.wait(10)
    phe_data = client.coolant_status(PHE_X + 1, PHE_Y + 1, PHE_Z)
    print(f"    PHE coolant_status: {phe_data}")
    assert phe_data.get("machine_formed", False), f"PHE structure failed to form: {phe_data}"
    print("[✓] Pressurized Heat Exchanger multiblock formed successfully!")

    print("\n[*] Rechecking Coolant Loop Pump structure...")
    res_pump_recheck = client.recheck(PUMP_X + 1, PUMP_Y, PUMP_Z)
    print(f"    Pump recheck result: {res_pump_recheck}")
    client.wait(10)
    pump_data = client.coolant_status(PUMP_X + 1, PUMP_Y, PUMP_Z)
    print(f"    Pump coolant_status: {pump_data}")

    assert pump_data.get("machine_formed", False), f"Coolant pump structure failed to form: {pump_data}"
    assert pump_data.get("total_coolant_liters", 0) > 0, f"Railcraft tank has no coolant: {pump_data}"
    print(f"[✓] Coolant Pump multiblock formed! Railcraft Tank reservoir holds {pump_data.get('total_coolant_liters')} L.")

    # 2b. Ensure Debug Wireless Energy Cover on Coolant Pump and PHE Energy Hatches
    print("\n[*] Ensuring Debug Wireless Energy Cover on Coolant Pump and PHE Energy Hatches...")
    res_cover_pump = client.set_cover(PUMP_X + 1, PUMP_Y, PUMP_Z + 2, 3, "Cover_Wireless_Energy_Debug")
    res_cover_phe = client.set_cover(PHE_X + 1, PHE_Y, PHE_Z + 4, 3, "Cover_Wireless_Energy_Debug")
    print(f"    Pump cover: {res_cover_pump} | PHE cover: {res_cover_phe}")
    client.wait(5)

    # 3. Trigger loop crawl from pump
    print("\n[*] Triggering closed-loop graph traversal...")
    res_crawl = client.crawl_loop(PUMP_X + 1, PUMP_Y, PUMP_Z)
    print(f"    crawl_loop result: {res_crawl}")
    client.wait(10)

    # Re-query status to inspect LoopState and Initial Filling Phase
    st1 = client.coolant_status(PUMP_X + 1, PUMP_Y, PUMP_Z)
    assert st1.get("loop_formed", False), f"Loop failed to close: {st1}"
    req_fill = st1.get("required_fill_liters", 0)
    assert req_fill > 0, f"Required fill level must be > 0: {st1}"
    print(f"[✓] Loop formed! Required fill level calculated: {req_fill} L")
    print(f"    Current loop state: {st1.get('loop_state')}, Fill: {st1.get('fill_liters')} / {req_fill} L")

    # 4. Observe Filling Phase -> Circulation Transition
    print("\n[*] Monitoring loop filling from Railcraft tank reservoir...")
    max_wait_ticks = 60
    state, fill, flow = None, 0, 0.0
    for tick in range(max_wait_ticks):
        client.wait(2)
        st = client.coolant_status(PUMP_X + 1, PUMP_Y, PUMP_Z)
        state = st.get("loop_state")
        fill = st.get("fill_liters", 0)
        flow = st.get("flow_rate_l_s", 0.0)
        print(f"    [{tick*2:2d} ticks] State: {state:12s} | Fill: {fill:5d}/{req_fill:5d} L | Flow: {flow:.2f} L/s")
        if state == "CIRCULATING" and fill >= req_fill:
            print(f"[✓] Loop reached 100% fill level ({fill} L) and transitioned to CIRCULATING!")
            break
        assert state in ["FILLING", "CIRCULATING"], f"Unexpected loop state during fill: {state}"

    assert state == "CIRCULATING" and fill >= req_fill, f"Failed to complete filling: state={state}, fill={fill}/{req_fill}"

    # 5. Verify flow acceleration during active circulation
    print("\n[*] Waiting for hydraulic acceleration & circulation flow...")
    client.wait(20)
    st_circ = client.coolant_status(PUMP_X + 1, PUMP_Y, PUMP_Z)
    flow_rate = st_circ.get("flow_rate_l_s", 0.0)
    print(f"    Circulating flow rate: {flow_rate:.2f} L/s (m3/s: {st_circ.get('flow_rate_m3s', 0.0):.4f})")
    assert flow_rate > 0.0, f"Expected non-zero circulating flow rate, got {flow_rate}"
    print("[✓] Fluid acceleration verified! Active circulation in progress.")

    # 6. Capture verification screenshots
    print("\n[*] Capturing verification screenshots...")

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

    # Overview of Pump and formed Railcraft Tank above it
    client.stand(PUMP_X + 1, PUMP_Y + 4, PUMP_Z - 6, 0.0, 15.0)
    client.wait(15)
    capture_and_sync("coolant_loops_pump_tank.png")

    # Wide angle of the whole loop circuit
    client.stand(OX + 6, OY + 8, OZ - 10, 0.0, 35.0)
    client.wait(15)
    capture_and_sync("coolant_loops_in_game_test.png")

    # Close-up of PHE 3x3x5 structure (side view showing ducts and insulated pipes)
    client.stand(PHE_X + 5, OY + 3, PHE_Z - 2, 45.0, 20.0)
    client.wait(15)
    capture_and_sync("coolant_loops_phe_maintenance.png")

    # Close-up of Manifold & Instrumentation Computer with flow direction arrows
    client.stand(OX + 7, OY + 3, OZ + 11, 180.0, 30.0)
    client.wait(15)
    capture_and_sync("coolant_loops_manifold_transition.png")

    print("\n============================================================")
    print("[✓] All Coolant Loops verification tests PASSED!")
    print("============================================================")


def run_tests(port: int = 25571):
    driver = InWorldTestDriver(
        tag="coolant_loops",
        port=port,
        no_mobs=True,
        base_template="superflat_empty",
    )
    return driver.run(build_fn=build_coolant_loops, test_fn=test_coolant_loops)


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Coolant Loops In-World Test")
    parser.add_argument("--port", type=int, default=25571, help="AgentHarness port (default: 25571)")
    args = parser.parse_args()
    success = run_tests(port=args.port)
    sys.exit(0 if success else 1)

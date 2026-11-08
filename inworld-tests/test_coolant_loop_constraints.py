#!/usr/bin/env python3
"""
Automated In-Game Verification Suite for GTNH Coolant Loops Constraints
Tests the three physical constraints added to coolant loops:
  1. Material Matching:
     - Railcraft multiblock tank above the pump and all pipes in the loop must use the same material.
     - Only materials that have BOTH GregTech fluid pipes and Railcraft tanks are allowed
       (Iron, Steel, Stainless Steel, Titanium, Tungstensteel, Neutronium).
     - Mixed pipe materials or mismatched tank/pipe materials are strictly rejected.
     - Non-coolant pipe materials (e.g., Bronze, Copper, Wood) are rejected.
  2. Pipe Size Consistency Across Segments & Devices:
     - All pipe blocks in a segment between two devices must use the same pipe size.
     - Devices (PHE, Degasser, Passage Hatch, Instruments) CANNOT change pipe size between
       input and output ends; ONLY Manifolds (TileEntityManifold) can transition pipe sizes.
  3. Volumetric Flow Rate Cap:
     - The pump's suction and discharge pipes must be the same size, and the circulation rate is capped at
       their fluid capacity.
     - The cold leg steps down to Small Steel Pipe (1600 L/s) through one manifold and back up to Medium Steel
       Pipe (4800 L/s) through another just before the pump, so the cap is 4800 L/s (4.8 m^3/s).
"""

import sys
import os
import time
import json
from pathlib import Path

from nhdev import NH_DEV_ROOT as REPO_ROOT

from tools.agentharness import HarnessClient, HarnessError
from tools.inworld_test_driver import InWorldTestDriver

# Coolant Pipe IDs
PIPE_STEEL_SMALL = 32133   # Small Steel Coolant Pipe (1600 L/s cap)
PIPE_STEEL_MED = 32140     # Medium Steel Coolant Pipe (4800 L/s cap)
PIPE_IRON_MED = 32139      # Medium Iron Coolant Pipe
PIPE_BRONZE_MED = 5122     # Medium Bronze Pipe (disallowed material / non-coolant pipe)


def build_constrained_coolant_loop(client: HarnessClient):
    """Stage 1: Assembles Coolant Pump with Steel Railcraft Tank, PHE, Reservoir, Instruments, and Manifold."""
    print("------------------------------------------------------------")
    OX, OY, OZ = 500, 5, 20
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

    # 1. Build Coolant Loop Pump Multiblock with 3x3x4 Railcraft STEEL Tank above it
    print("\n[*] Querying Pump geometry and assembling with Railcraft STEEL Tank...")
    pump = client.multiblock_info("coolant_pump", OX, OY, OZ)
    res_pump = client.build_multiblock("coolant_pump", OX, OY, OZ, "steel")
    print("    build_multiblock coolant_pump result:", res_pump)
    client.wait(10)

    # 2. Build Pressurized Heat Exchanger (PHE)
    print("\n[*] Querying PHE geometry and assembling Pressurized Heat Exchanger...")
    phe = client.multiblock_info("phe", OX + 10, OY, OZ)
    res_phe = client.build_multiblock("phe", OX + 10, OY, OZ)
    print("    build_multiblock phe result:", res_phe)
    client.wait(10)

    OY_LOOP = OY + 1  # Piping plane is at Y = 6

    # 3. Assemble Piping Circuit using In-Game 3D A* Router
    print("\n[*] Assembling Hot Leg (Medium Steel Pipes) via in-game pipe router...")
    # Hot Leg Segment 1: Pump Discharge -> Creative Heat Reservoir
    route_hot_1 = client.route_pipe(
        pipe_id=PIPE_STEEL_MED,
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
    client.configure_creative_reservoir(OX + 6, OY_LOOP, OZ - 5, 350.0, 50.0)

    # Hot Leg Segment 2: Reservoir -> PHE Primary Inlet
    route_hot_2 = client.route_pipe(
        pipe_id=PIPE_STEEL_MED,
        start=(OX + 7, OY_LOOP, OZ - 5),
        end=(OX + 9, OY_LOOP, OZ),
        start_conn="WEST",
        end_conn="EAST",
        avoid=[pump.bounds, phe.bounds],
        preferred_y=OY_LOOP,
    )
    print(f"    [+] Hot Leg Segment 2 routed: length={route_hot_2.get('length')} blocks")

    # Cold Leg Segment 1: PHE Primary Outlet -> Medium Steel Pipes -> Instruments
    print("\n[*] Assembling Cold Leg Segment 1 (Medium Steel Pipes up to Manifold)...")
    route_cold_1 = client.route_pipe(
        pipe_id=PIPE_STEEL_MED,
        start=(OX + 10, OY_LOOP, OZ + 5),
        end=(OX + 10, OY_LOOP, OZ + 8),
        start_conn="NORTH",
        end_conn="WEST",
        avoid=[phe.bounds],
        preferred_y=OY_LOOP,
    )
    print(f"    [+] Cold Leg Segment 1 routed: length={route_cold_1.get('length')} blocks")

    # In-line instruments (same pipe size across instruments):
    # Flow Meter at (OX+9, OY_LOOP, OZ+8)
    client.set_block(OX + 9, OY_LOOP, OZ + 8, "modularnuclear:loop_instrument", 0)
    client.setblock(OX + 9, OY_LOOP, OZ + 9, "redstone_lamp")

    # Every loop device discharges into a pipe: one after the flow meter, the thermometer, then one more
    client.set_gt_machine(OX + 8, OY_LOOP, OZ + 8, PIPE_STEEL_MED, 0)
    client.connect_pipe(OX + 8, OY_LOOP, OZ + 8, "EAST")
    client.connect_pipe(OX + 8, OY_LOOP, OZ + 8, "WEST")

    # Thermometer at (OX+7, OY_LOOP, OZ+8)
    client.set_block(OX + 7, OY_LOOP, OZ + 8, "modularnuclear:loop_instrument", 1)
    client.setblock(OX + 7, OY_LOOP, OZ + 9, "redstone_lamp")

    # Medium Steel Pipe at (OX+6, OY_LOOP, OZ+8) between Thermometer and Manifold
    client.set_gt_machine(OX + 6, OY_LOOP, OZ + 8, PIPE_STEEL_MED, 0)
    client.connect_pipe(OX + 6, OY_LOOP, OZ + 8, "EAST")
    client.connect_pipe(OX + 6, OY_LOOP, OZ + 8, "WEST")

    # Manifold at (OX+5, OY_LOOP, OZ+8) - TRANSITIONS from Medium Steel to Small Steel!
    client.set_block(OX + 5, OY_LOOP, OZ + 8, "modularnuclear:manifold", 4)
    client.configure_manifold(OX + 5, OY_LOOP, OZ + 8, "WEST")

    # Second manifold at (OX-2, OY_LOOP, OZ+1), back to Medium Steel for the pump's suction pipe at (OX-1): the
    # pump's suction and discharge pipes must be the same size
    client.set_block(OX - 2, OY_LOOP, OZ + 1, "modularnuclear:manifold", 5)
    client.configure_manifold(OX - 2, OY_LOOP, OZ + 1, "EAST")
    client.set_gt_machine(OX - 1, OY_LOOP, OZ + 1, PIPE_STEEL_MED, 0)
    client.connect_pipe(OX - 1, OY_LOOP, OZ + 1, "WEST")
    client.connect_pipe(OX - 1, OY_LOOP, OZ + 1, "EAST")

    # Cold Leg Segment 2: SMALL Steel Pipes between the two manifolds
    print("\n[*] Assembling Cold Leg Segment 2 (Small Steel Pipes between the manifolds)...")
    route_cold_2 = client.route_pipe(
        pipe_id=PIPE_STEEL_SMALL,
        start=(OX + 4, OY_LOOP, OZ + 8),
        end=(OX - 3, OY_LOOP, OZ + 1),
        start_conn="EAST",
        end_conn="EAST",
        avoid=[pump.bounds],
        preferred_y=OY_LOOP,
    )
    print(f"    [+] Cold Leg Segment 2 routed: length={route_cold_2.get('length')} blocks")
    print("[✓] Closed piping circuit assembled and connected!")

    # Secondary circuit on PHE
    client.set_gt_machine(OX + 13, OY_LOOP, OZ, 238, 4)
    client.configure_debug_tank(OX + 13, OY_LOOP, OZ, "ic2distilledwater", True)
    client.set_gt_machine(OX + 13, OY_LOOP, OZ + 4, 130, 4)
    client.configure_supertank(OX + 13, OY_LOOP, OZ + 4, True)
    client.enable_auto_output(OX + 12, OY_LOOP, OZ + 4)

    client.wait(10)
    print("[✓] Build Phase completed: Constrained coolant loop staged with ticks frozen.")


def test_constraints_suite(client: HarnessClient):
    """Stage 2: Thoroughly tests material matching, manifold size transition, device size consistency, and flow rate capping."""
    print("============================================================")
    print("STARTING IN-GAME COOLANT LOOPS CONSTRAINTS TEST SUITE")
    print("============================================================")
    OX, OY, OZ = 500, 5, 20
    PUMP_X, PUMP_Y, PUMP_Z = OX, OY, OZ

    client.stand(OX + 7, OY + 3, OZ - 6)
    client.look(0.0, 20.0)
    client.wait(20)

    # Fill Railcraft Steel Tank with distilled water
    print("\n[*] Filling Railcraft Steel Tank with distilled water...")
    client.fill_fluid(PUMP_X + 1, PUMP_Y + 2, PUMP_Z + 1, "ic2distilledwater", 100000)
    client.fill_fluid(PUMP_X, PUMP_Y + 2, PUMP_Z, "ic2distilledwater", 100000)
    client.wait(10)

    # Verify Coolant Pump formation & tank material detection
    print("\n[*] Verifying Coolant Pump formation and Railcraft tank material...")
    client.recheck(PUMP_X + 1, PUMP_Y, PUMP_Z)
    client.wait(10)
    st_pump = client.coolant_status(PUMP_X + 1, PUMP_Y, PUMP_Z)
    print(f"    Pump status: {st_pump}")
    assert st_pump.get("machine_formed", False), f"Pump structure not formed: {st_pump}"
    assert st_pump.get("tank_material") == "Steel", f"Expected Steel tank, got: {st_pump.get('tank_material')}"
    print(f"[✓] Coolant Pump formed with Railcraft Tank Material: {st_pump.get('tank_material')}")

    # ============================================================
    # TEST 1: Baseline Valid Loop (Steel Tank + Steel Pipes + Manifold Size Transition)
    # ============================================================
    print("\n--- TEST 1: Valid Material Match & Manifold Pipe Size Transition ---")
    crawl_res = client.crawl_loop(PUMP_X + 1, PUMP_Y, PUMP_Z)
    print(f"    crawl_loop result: {crawl_res}")
    client.wait(10)

    st1 = client.coolant_status(PUMP_X + 1, PUMP_Y, PUMP_Z)
    assert st1.get("loop_formed", False), f"Expected loop to form successfully: {st1}"
    print(f"    Loop status: {st1.get('loop_status')}")

    # Flow rate cap verification: the pump's suction and discharge pipes are Medium Steel (4800 L/s), so the
    # max volumetric flow rate is capped there, not at the Small Steel pipes between the manifolds
    max_flow = st1.get("max_flow_rate_l_s", 0.0)
    print(f"    Detected Max Volumetric Flow Rate Cap: {max_flow:.2f} L/s (expected 4800.0 L/s)")
    assert abs(max_flow - 4800.0) < 1.0, f"Expected 4800.0 L/s cap from the pump's Medium pipes, got {max_flow}"
    print("[✓] TEST 1 PASSED: Valid Steel loop formed! Max flow rate correctly capped at the pump's pipe capacity (4800 L/s)!")

    # Verify filling and circulation
    print("\n[*] Monitoring filling phase and circulation transition...")
    req_fill = st1.get("required_fill_liters", 0)
    for tick in range(40):
        client.wait(2)
        st = client.coolant_status(PUMP_X + 1, PUMP_Y, PUMP_Z)
        if st.get("loop_state") == "CIRCULATING":
            print(f"    [tick {tick*2}] Reached CIRCULATING state with fill {st.get('fill_liters')}/{req_fill} L")
            break

    client.wait(20)
    st_circ = client.coolant_status(PUMP_X + 1, PUMP_Y, PUMP_Z)
    circ_flow = st_circ.get("flow_rate_l_s", 0.0)
    print(f"    Active circulating flow rate: {circ_flow:.2f} L/s")
    assert circ_flow > 0.0, f"Expected non-zero circulating flow rate, got {circ_flow}"
    assert circ_flow <= 4800.0 + 1e-3, f"Circulating flow rate {circ_flow} exceeded pump capacity cap 4800.0!"
    print(f"[✓] Fluid circulation rate ({circ_flow:.2f} L/s) is strictly bounded by the connected pipe capacity limit!")

    OY_LOOP = OY + 1
    # ============================================================
    # TEST 2: Device Input/Output Size Mismatch Without Manifold (Must Fail)
    # ============================================================
    print("\n--- TEST 2: Pipe Size Mismatch Across Device Without Manifold ---")
    print(f"[*] Replacing pipe upstream of Flow Meter (OX+10, {OY_LOOP}, OZ+8) with Small Steel Pipe...")
    # Upstream is now Small (0.375), downstream of Flow Meter at (OX+8) is Medium (0.500)
    # Flow Meter is an instrument (device), NOT a manifold. Devices cannot transition pipe sizes!
    client.set_gt_machine(OX + 10, OY_LOOP, OZ + 8, PIPE_STEEL_SMALL, 0)
    client.connect_pipe(OX + 10, OY_LOOP, OZ + 8, "EAST")
    client.connect_pipe(OX + 10, OY_LOOP, OZ + 8, "WEST")
    client.wait(5)

    crawl_res2 = client.crawl_loop(PUMP_X + 1, PUMP_Y, PUMP_Z)
    print(f"    crawl_loop result: {crawl_res2}")
    client.wait(5)
    st2 = client.coolant_status(PUMP_X + 1, PUMP_Y, PUMP_Z)
    print(f"    Pump status: loop_formed={st2.get('loop_formed')}, status='{st2.get('loop_status')}'")

    assert not st2.get("loop_formed", True), "Loop should NOT form when a device transitions pipe size without a Manifold!"
    assert "Pipe size mismatch" in st2.get("loop_status", ""), f"Expected size mismatch failure reason, got: {st2.get('loop_status')}"
    print("[✓] TEST 2 PASSED: Size mismatch across device correctly rejected! ('" + st2.get("loop_status") + "')")

    # Restore pipe to Medium Steel Pipe
    print(f"[*] Restoring (OX+10, {OY_LOOP}, OZ+8) to Medium Steel Pipe...")
    client.set_gt_machine(OX + 10, OY_LOOP, OZ + 8, PIPE_STEEL_MED, 0)
    client.connect_pipe(OX + 10, OY_LOOP, OZ + 8, "EAST")
    client.connect_pipe(OX + 10, OY_LOOP, OZ + 8, "WEST")
    client.wait(5)

    # ============================================================
    # TEST 3: Pipe Material Mismatch in Loop (Must Fail)
    # ============================================================
    print("\n--- TEST 3: Pipe Material Mismatch (Steel Tank + Iron Pipe) ---")
    print(f"[*] Replacing Hot Leg pipe (OX+3, {OY_LOOP}, OZ) with Medium Iron Pipe...")
    client.set_gt_machine(OX + 3, OY_LOOP, OZ, PIPE_IRON_MED, 0)
    client.connect_pipe(OX + 3, OY_LOOP, OZ, "SOUTH")
    client.connect_pipe(OX + 3, OY_LOOP, OZ, "NORTH")
    client.wait(5)

    crawl_res3 = client.crawl_loop(PUMP_X + 1, PUMP_Y, PUMP_Z)
    print(f"    crawl_loop result: {crawl_res3}")
    client.wait(5)
    st3 = client.coolant_status(PUMP_X + 1, PUMP_Y, PUMP_Z)
    print(f"    Pump status: loop_formed={st3.get('loop_formed')}, status='{st3.get('loop_status')}'")

    assert not st3.get("loop_formed", True), "Loop should NOT form when pipe material mismatches tank material!"
    assert "Pipe material mismatch" in st3.get("loop_status", ""), f"Expected material mismatch failure, got: {st3.get('loop_status')}"
    print("[✓] TEST 3 PASSED: Mixed pipe/tank material correctly rejected! ('" + st3.get("loop_status") + "')")

    # ============================================================
    # TEST 4: Disallowed Pipe Material (Must Fail)
    # ============================================================
    print("\n--- TEST 4: Disallowed Pipe Material (Bronze Pipe 5122) ---")
    print(f"[*] Replacing Hot Leg pipe (OX+3, {OY_LOOP}, OZ) with Medium Bronze Pipe...")
    client.set_gt_machine(OX + 3, OY_LOOP, OZ, PIPE_BRONZE_MED, 0)
    client.connect_pipe(OX + 3, OY_LOOP, OZ, "SOUTH")
    client.connect_pipe(OX + 3, OY_LOOP, OZ, "NORTH")
    client.wait(5)

    crawl_res4 = client.crawl_loop(PUMP_X + 1, PUMP_Y, PUMP_Z)
    print(f"    crawl_loop result: {crawl_res4}")
    client.wait(5)
    st4 = client.coolant_status(PUMP_X + 1, PUMP_Y, PUMP_Z)
    print(f"    Pump status: loop_formed={st4.get('loop_formed')}, status='{st4.get('loop_status')}'")

    assert not st4.get("loop_formed", True), "Loop should NOT form with Bronze pipes!"
    assert "not an insulated coolant pipe" in st4.get("loop_status", "") or "not allowed for coolant loops" in st4.get("loop_status", ""), f"Expected non-coolant pipe / disallowed material failure, got: {st4.get('loop_status')}"
    print("[✓] TEST 4 PASSED: Non-coolant pipe material correctly rejected! ('" + st4.get("loop_status") + "')")

    # ============================================================
    # TEST 5: Loop Restoration & Final Verification
    # ============================================================
    print("\n--- TEST 5: Restoration to Valid State ---")
    print(f"[*] Restoring (OX+3, {OY_LOOP}, OZ) to Medium Steel Pipe...")
    client.set_gt_machine(OX + 3, OY_LOOP, OZ, PIPE_STEEL_MED, 0)
    client.connect_pipe(OX + 3, OY_LOOP, OZ, "SOUTH")
    client.connect_pipe(OX + 3, OY_LOOP, OZ, "NORTH")
    client.wait(5)

    crawl_res5 = client.crawl_loop(PUMP_X + 1, PUMP_Y, PUMP_Z)
    print(f"    crawl_loop result: {crawl_res5}")
    client.wait(10)
    st5 = client.coolant_status(PUMP_X + 1, PUMP_Y, PUMP_Z)
    assert st5.get("loop_formed", False), f"Restored loop failed to form: {st5}"
    print("[✓] Restored loop successfully reformed!")

    # Capture screenshots
    print("\n[*] Capturing verification screenshots...")
    # 1. Pump + Steel Tank overview
    client.stand(PUMP_X + 1, PUMP_Y + 4, PUMP_Z - 6, 0.0, 15.0)
    client.wait(15)
    client.screenshot("screenshots/coolant_loops_steel_pump_tank.png")
    print("[✓] Screenshot saved: screenshots/coolant_loops_steel_pump_tank.png")

    # 2. Manifold & Instruments close-up (showing size transition from Medium to Small)
    client.stand(OX + 7, OY + 2, OZ + 10, 0.0, 20.0)
    client.wait(15)
    client.screenshot("screenshots/coolant_loops_manifold_transition.png")
    print("[✓] Screenshot saved: screenshots/coolant_loops_manifold_transition.png")

    # 3. Full loop overview
    client.stand(OX + 6, OY + 8, OZ - 10, 0.0, 35.0)
    client.wait(15)
    client.screenshot("screenshots/coolant_loops_constraints_overview.png")
    print("[✓] Screenshot saved: screenshots/coolant_loops_constraints_overview.png")

    print("\n============================================================")
    print("ALL 5 COOLANT LOOPS CONSTRAINTS TESTS PASSED!")
    print("============================================================")


def run_tests(port: int = 25571):
    driver = InWorldTestDriver(
        tag="coolant_constraints",
        port=port,
        base_template="superflat_empty",
    )
    success = driver.run(
        build_fn=build_constrained_coolant_loop,
        test_fn=test_constraints_suite,
    )
    if not success:
        print("[!] Constraints test suite failed!")
        sys.exit(1)
    print("[✓] Constraints test lifecycle completed successfully!")


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Coolant Loop Constraints Test")
    parser.add_argument("pos_port", nargs="?", type=int, default=None, help="Port (positional)")
    parser.add_argument("--port", type=int, default=25571, help="AgentHarness port (default: 25571)")
    args = parser.parse_args()
    port = args.pos_port if args.pos_port is not None else args.port
    run_tests(port=port)

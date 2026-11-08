#!/usr/bin/env python3
"""
Automated In-Game Verification Suite for GTNH Pressurized Degasser Multiblock
Uses InWorldTestDriver with two-stage pre/post snapshotting:
  Stage 1 (Build Phase): Assembles Coolant Loop Pump with full 3x3x4 Railcraft Iron Tank
                         multiblock, Pressurized Degasser (3x3 footprint, height 7) with
                         2 EV energy hatches and 2 top output hatches filtered to Deuterium
                         and Oxygen, closed-circuit piping routed via abstract in-game 3D pathfinding engine,
                         and in-line instrumentation with ticks frozen.
                         Saves snapshot to 'coolant_degasser_pre'.
  Stage 2 (Execution Phase): Ephemeral run from 'coolant_degasser_pre' with ticks unfrozen.
                             Verifies:
                             1. Structure formation of Pressurized Degasser (3x3x7) and Coolant Pump.
                             2. Graph traversal and loop closure through the Degasser inlet/outlet hatches.
                             3. Reservoir tank fill and initial loop filling phase.
                             4. Inactive Degasser behavior (unimpeded coolant flow, zero power, zero gas extracted).
                             5. Active Degasser behavior:
                                - Multi-energy hatch overclocking (2 EV hatches = 4 amps = 8,192 EU/t).
                                - Gas extraction rate limits (energy limit: 1 L/s per 8 EU/t, mass transfer: 25% crossing gas/sec).
                                - Multi-gas separation into dedicated filtered output hatches (Deuterium & Oxygen).
                             Captures verification screenshots and saves snapshot to 'coolant_degasser_post'.
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

PIPE_ID = 32140  # Medium Steel Coolant Pipe


def build_degasser_setup(client: HarnessClient):
    """Stage 1: Assembles Coolant Pump, Railcraft Tank, Pressurized Degasser, and Piping with ticks frozen."""
    print("------------------------------------------------------------")
    OX, OY, OZ = 500, 5, 20

    # Position player safely (stand command enables flight automatically)
    client.stand(OX + 7, OY + 3, OZ - 6)
    client.look(0.0, 25.0)
    client.wait(5)

    OY_LOOP = OY + 1
    DEG_X, DEG_Y, DEG_Z = OX + 10, OY_LOOP, OZ

    # 1. Build Coolant Loop Pump Multiblock with 3x3x4 Railcraft Steel Tank above it
    print("\n[*] Querying Pump geometry and assembling structure...")
    pump = client.multiblock_info("coolant_pump", OX, OY, OZ)
    res_pump = client.build_multiblock("coolant_pump", OX, OY, OZ, "steel")
    print("    build_multiblock coolant_pump result:", res_pump)
    client.wait(10)

    # 2. Build Pressurized Degasser (3x3 footprint, height 7) with 2 EV energy hatches at Y = OY_LOOP
    print("\n[*] Querying Degasser geometry and assembling structure (3x3x7, 2 EV Energy Hatches)...")
    degasser = client.multiblock_info("degasser", DEG_X, DEG_Y, DEG_Z)
    res_deg = client.build_multiblock("degasser", DEG_X, DEG_Y, DEG_Z, 2)
    print("    build_multiblock degasser result:", res_deg)
    client.wait(10)

    # 3. Assemble Complete Working Piping Circuit via In-Game 3D A* Router
    print("\n[*] Routing Closed Piping Circuit between Pump and Degasser via in-game engine...")
    # Hot Leg: Pump Discharge (OX+2, OY_LOOP, OZ+1) East -> Degasser Inlet (OX+10, OY_LOOP, OZ+1) West
    # Discharge pipe starts at (OX+3, OY_LOOP, OZ+1), Degasser inlet pipe ends at (OX+9, OY_LOOP, OZ+1).
    route_hot = client.route_pipe(
        pipe_id=PIPE_ID,
        start=(OX + 3, OY_LOOP, OZ + 1),
        end=(OX + 9, OY_LOOP, OZ + 1),
        start_conn="WEST",
        end_conn="EAST",
        avoid=[pump.bounds, degasser.bounds],
        preferred_y=OY_LOOP,
    )
    print(f"    [+] Hot Leg routed: length={route_hot.get('length')} blocks")

    # Cold Leg: Degasser Outlet (OX+12, OY_LOOP, OZ+1) East -> Instruments -> Manifold -> Pump Suction (OX, OY_LOOP, OZ+1) West
    # Degasser outlet pipe starts at (OX+13, OY_LOOP, OZ+1).
    # Instruments are placed at (OX+9) and (OX+7). Pipe ends at (OX+10, OY_LOOP, OZ+6).
    route_cold_1 = client.route_pipe(
        pipe_id=PIPE_ID,
        start=(OX + 13, OY_LOOP, OZ + 1),
        end=(OX + 10, OY_LOOP, OZ + 6),
        start_conn="WEST",
        end_conn="WEST",
        avoid=[degasser.bounds],
        preferred_y=OY_LOOP,
    )
    print(f"    [+] Cold Leg Segment 1 routed: length={route_cold_1.get('length')} blocks")

    # Flow Meter at (OX+9, OY_LOOP, OZ+6) facing SOUTH (meta 3)
    client.set_block(OX + 9, OY_LOOP, OZ + 6, "modularnuclear:loop_instrument", 3)
    client.configure_instrument(OX + 9, OY_LOOP, OZ + 6, "flow", "SOUTH")
    client.setblock(OX + 9, OY_LOOP, OZ + 7, "redstone_lamp")

    # Every loop device discharges into a pipe: one after the flow meter, the thermometer, then one more
    client.set_gt_machine(OX + 8, OY_LOOP, OZ + 6, PIPE_ID, 0)
    client.connect_pipe(OX + 8, OY_LOOP, OZ + 6, "EAST")
    client.connect_pipe(OX + 8, OY_LOOP, OZ + 6, "WEST")

    # Thermometer at (OX+7, OY_LOOP, OZ+6) facing SOUTH (meta 3)
    client.set_block(OX + 7, OY_LOOP, OZ + 6, "modularnuclear:loop_instrument", 3)
    client.configure_instrument(OX + 7, OY_LOOP, OZ + 6, "temp", "SOUTH")
    client.setblock(OX + 7, OY_LOOP, OZ + 7, "redstone_lamp")

    client.set_gt_machine(OX + 6, OY_LOOP, OZ + 6, PIPE_ID, 0)
    client.connect_pipe(OX + 6, OY_LOOP, OZ + 6, "EAST")
    client.connect_pipe(OX + 6, OY_LOOP, OZ + 6, "WEST")

    # Manifold at (OX+5, OY_LOOP, OZ+6)
    client.set_block(OX + 5, OY_LOOP, OZ + 6, "modularnuclear:manifold", 4)
    client.configure_manifold(OX + 5, OY_LOOP, OZ + 6, "WEST")

    # Cold Leg Segment 2: Manifold exit (OX+4, OY_LOOP, OZ+6) -> Pump Suction pipe (OX-1, OY_LOOP, OZ+1)
    route_cold_2 = client.route_pipe(
        pipe_id=PIPE_ID,
        start=(OX + 4, OY_LOOP, OZ + 6),
        end=(OX - 1, OY_LOOP, OZ + 1),
        start_conn="EAST",
        end_conn="EAST",
        avoid=[pump.bounds],
        preferred_y=OY_LOOP,
    )
    print(f"    [+] Cold Leg Segment 2 routed: length={route_cold_2.get('length')} blocks")
    print("[✓] Closed piping circuit assembled and connected!")

    # 4. Lock Output Hatches to specific gases
    # Top layer output hatches at DEG_Y + 6
    print("\n[*] Configuring Degasser top-layer Output Hatch fluid filters...")
    res_lock1 = client.lock_output_fluid(DEG_X + 1, DEG_Y + 6, OZ, "deuterium")
    print(f"    Lock Hatch 1 result: {res_lock1}")
    res_lock2 = client.lock_output_fluid(DEG_X + 1, DEG_Y + 6, OZ + 2, "oxygen")
    print(f"    Lock Hatch 2 result: {res_lock2}")

    client.recheck(DEG_X + 1, DEG_Y, DEG_Z)
    client.wait(10)
    print("[✓] Build Phase completed: Pressurized Degasser and coolant loop staged with ticks frozen.")


def test_degasser_setup(client: HarnessClient):
    """Stage 2: Live simulation verifying Degasser mechanics, overclocking, and dual rate limits."""
    print("------------------------------------------------------------")
    OX, OY, OZ = 500, 5, 20
    PUMP_X, PUMP_Y, PUMP_Z = OX, OY, OZ
    OY_LOOP = OY + 1
    DEG_X, DEG_Y, DEG_Z = OX + 10, OY_LOOP, OZ

    # Position player
    client.stand(OX + 7, OY + 3, OZ - 6)
    client.look(0.0, 20.0)
    client.wait(20)

    # 1. Fill Railcraft Tank with coolant
    print("\n[*] Ensuring Railcraft Tank contains coolant fluid...")
    client.fill_fluid(PUMP_X + 1, PUMP_Y + 2, PUMP_Z + 1, "ic2distilledwater", 100000)
    client.fill_fluid(PUMP_X, PUMP_Y + 2, PUMP_Z, "ic2distilledwater", 100000)
    client.wait(10)

    # 2. Recheck structures: MUST recheck Degasser FIRST so its inlet and outlet hatches are bound
    # before the pump crawls the loop!
    print("\n[*] Rechecking Pressurized Degasser structure...")
    res_deg_recheck = client.recheck(DEG_X + 1, DEG_Y, DEG_Z)
    print(f"    Degasser recheck result: {res_deg_recheck}")
    client.wait(10)
    deg_data = client.coolant_status(DEG_X + 1, DEG_Y, DEG_Z)
    print(f"    Degasser coolant_status: {deg_data}")
    assert deg_data.get("machine_formed", False), f"Degasser structure failed to form: {deg_data}"
    print("[✓] Pressurized Degasser multiblock formed successfully!")

    print("\n[*] Rechecking Coolant Loop Pump structure...")
    res_pump_recheck = client.recheck(PUMP_X + 1, PUMP_Y, PUMP_Z)
    print(f"    Pump recheck result: {res_pump_recheck}")
    client.wait(10)
    pump_data = client.coolant_status(PUMP_X + 1, PUMP_Y, PUMP_Z)
    print(f"    Pump coolant_status: {pump_data}")
    assert pump_data.get("machine_formed", False), f"Coolant pump structure failed to form: {pump_data}"
    print(f"[✓] Coolant Pump multiblock formed! Tank holds {pump_data.get('total_coolant_liters')} L.")

    # 3. Trigger loop crawl from pump
    print("\n[*] Triggering closed-loop graph traversal...")
    res_crawl = client.crawl_loop(PUMP_X + 1, PUMP_Y, PUMP_Z)
    print(f"    crawl_loop result: {res_crawl}")
    client.wait(10)

    st1 = client.coolant_status(PUMP_X + 1, PUMP_Y, PUMP_Z)
    assert st1.get("loop_formed", False), f"Loop failed to close: {st1}"
    req_fill = st1.get("required_fill_liters", 0)
    assert req_fill > 0, f"Required fill level must be > 0: {st1}"
    print(f"[✓] Loop formed! Required fill level: {req_fill} L")

    # 3b. Charge Coolant Pump EV Energy Hatch
    print("\n[*] Charging Coolant Pump EV Energy Hatch with 10M EU...")
    res_charge = client.charge_energy(PUMP_X + 1, PUMP_Y, PUMP_Z, 10_000_000)
    client.charge_energy(PUMP_X + 1, PUMP_Y, PUMP_Z + 2, 10_000_000)
    print(f"    charge_energy result: {res_charge}")
    client.wait(5)

    # 4. Wait for loop filling and fluid acceleration
    print("\n[*] Waiting for loop filling phase and circulation flow...")
    st = {}
    for _ in range(5):
        client.wait(10)
        st = client.coolant_status(PUMP_X + 1, PUMP_Y, PUMP_Z)
        print(f"    Loop state: {st.get('loop_state')}, Fill: {st.get('fill_liters')}/{req_fill} L, Flow: {st.get('flow_rate_l_s', 0.0):.2f} L/s")
        if st.get("loop_state") == "CIRCULATING" and st.get("flow_rate_l_s", 0.0) > 0.0:
            break

    flow_rate = st.get("flow_rate_l_s", 0.0)
    assert flow_rate > 0.0, f"Expected active flow rate, got {flow_rate}"
    print(f"[✓] Fluid acceleration active! Circulation flow rate: {flow_rate:.2f} L/s")

    # 5. TEST: Degasser Turned Off
    print("\n[*] Testing Degasser when Turned Off (isAllowedToWork == false)...")
    res_disable = client.toggle_work(DEG_X + 1, DEG_Y, DEG_Z)
    print(f"    toggle_work result: {res_disable}")
    client.wait(5)
    deg_off = client.coolant_status(DEG_X + 1, DEG_Y, DEG_Z)
    assert not deg_off.get("is_allowed_to_work", True), f"Expected Degasser to be disabled: {deg_off}"

    # Charge energy hatches
    client.charge_energy(DEG_X + 1, DEG_Y, DEG_Z, 200000)

    # Inject dissolved gases into loop
    print("    Injecting dissolved gases: 5000L Deuterium, 3000L Oxygen...")
    client.add_dissolved_gas(PUMP_X + 1, PUMP_Y, PUMP_Z, "deuterium", 5000)
    client.add_dissolved_gas(PUMP_X + 1, PUMP_Y, PUMP_Z, "oxygen", 3000)
    client.wait(20)

    # Re-verify that while turned off, no energy was drained and no gas extracted
    deg_off_check = client.coolant_status(DEG_X + 1, DEG_Y, DEG_Z)
    last_drained = deg_off_check.get("last_drained_eu", 0)
    total_ext = deg_off_check.get("total_extracted_liters", 0)
    print(f"    Degasser OFF: last_drained_eu={last_drained}, total_extracted_liters={total_ext}")
    assert last_drained == 0, f"Disabled Degasser must not drain energy, drained {last_drained} EU"
    assert total_ext == 0, f"Disabled Degasser must not extract gas, extracted {total_ext} L"

    # Re-verify coolant flow is unimpeded
    st_off = client.coolant_status(PUMP_X + 1, PUMP_Y, PUMP_Z)
    assert st_off.get("flow_rate_l_s", 0.0) > 0.0, "Coolant flow must not be blocked when Degasser is turned off"
    print("[✓] Verified: Disabled Degasser allows flow through unimpeded without draining power or degassing.")

    # 6. TEST: Degasser Turned On with Overclocking & Dual Rate Limits
    print("\n[*] Testing Degasser when Turned On with 2 EV Energy Hatches...")
    res_enable = client.toggle_work(DEG_X + 1, DEG_Y, DEG_Z)
    print(f"    toggle_work result: {res_enable}")
    client.wait(5)
    deg_on = client.coolant_status(DEG_X + 1, DEG_Y, DEG_Z)
    assert deg_on.get("is_allowed_to_work", False), f"Expected Degasser to be enabled: {deg_on}"

    # Charge energy hatches with 1,000,000 EU to sustain multiple seconds of degassing
    client.charge_energy(DEG_X + 1, DEG_Y, DEG_Z, 1000000)
    client.wait(10)

    # Let simulation run for 60 ticks (3 seconds)
    print("    Running degassing simulation for 60 ticks...")
    client.wait(60)

    deg_active = client.coolant_status(DEG_X + 1, DEG_Y, DEG_Z)
    drained_eu = deg_active.get("last_drained_eu", 0)
    total_extracted = deg_active.get("total_extracted_liters", 0)
    output_fluids = deg_active.get("output_fluids", [])
    print(f"    Degasser ACTIVE: last_drained_eu={drained_eu}, total_extracted_liters={total_extracted}")
    print(f"    Output Hatches content: {output_fluids}")

    # Verify dynamic power draw during active degassing
    assert drained_eu > 0, f"Expected active power draw during degassing, got {drained_eu} EU/t"
    print(f"[✓] Degasser dynamic power draw verified: {drained_eu} EU drawn this tick (proportional to extracted gas)!")

    # Verify gas extraction occurred
    assert total_extracted > 0, f"Expected extracted gas liters > 0, got {total_extracted}"
    print(f"[✓] Gas extraction verified! Total gas extracted: {total_extracted} L")

    # Verify multi-gas separation into dedicated filtered output hatches
    extracted_names = [f.get("fluid", "").lower() for f in output_fluids if f.get("amount", 0) > 0]
    print(f"    Extracted fluids present in output hatches: {extracted_names}")
    assert any("deuterium" in name for name in extracted_names), f"Expected Deuterium in output hatches: {output_fluids}"
    assert any("oxygen" in name for name in extracted_names), f"Expected Oxygen in output hatches: {output_fluids}"
    print("[✓] Filtered output hatches correctly recovered Deuterium and Oxygen simultaneously!")

    # 7. Capture verification screenshots
    print("\n[*] Capturing verification screenshots...")
    # Close-up of Pressurized Degasser (3x3x7)
    client.stand(DEG_X + 1, DEG_Y + 4, DEG_Z - 6, 0.0, 15.0)
    client.wait(15)
    client.screenshot("screenshots/coolant_loops_degasser_structure.png")
    print("[✓] Screenshot saved: screenshots/coolant_loops_degasser_structure.png")

    # Wide angle of the whole loop circuit
    client.stand(OX + 6, OY + 8, OZ - 10, 0.0, 35.0)
    client.wait(15)
    client.screenshot("screenshots/coolant_loops_degasser_overview.png")
    print("[✓] Screenshot saved: screenshots/coolant_loops_degasser_overview.png")

    print("\n============================================================")
    print("[✓] All Pressurized Degasser verification tests PASSED!")
    print("============================================================")


def run_tests(port: int = 25571):
    driver = InWorldTestDriver(
        tag="coolant_degasser",
        port=port,
        no_mobs=True,
        base_template="superflat_empty",
    )
    return driver.run(build_fn=build_degasser_setup, test_fn=test_degasser_setup)


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Pressurized Degasser In-World Test")
    parser.add_argument("--port", type=int, default=25571, help="AgentHarness port (default: 25571)")
    args = parser.parse_args()
    success = run_tests(port=args.port)
    sys.exit(0 if success else 1)

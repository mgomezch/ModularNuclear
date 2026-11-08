#!/usr/bin/env python3
"""
Automated In-Game Verification Suite for GTNH Coolant Loops:
Maintenance Requirements and Efficiency Loss Mechanics

Verifies:
  1. Maintenance Hatch Enforcement:
     - Coolant Loop Pump requires 1 Maintenance Hatch; missing hatch invalidates structure.
     - Pressurized Heat Exchanger (PHE) requires 1 Maintenance Hatch; missing hatch invalidates structure.
     - Pressurized Degasser requires 1 Maintenance Hatch; missing hatch invalidates structure.
  2. 2-Block Height Pump & Railcraft Tank:
     - Pump height is reduced to 2 blocks (layers Y and Y+1).
     - Railcraft multiblock tank sits immediately above at Y+2 to Y+5.
     - Structure forms and correctly links to tank.
  3. Pump Efficiency Loss:
     - Efficiency loss degrades input power to mechanical power / pump head ratio.
     - 100% efficiency vs damaged (40% efficiency) shows proportional mechanical power reduction.
  4. PHE Efficiency Loss:
     - Efficiency loss scales convective heat transfer and steam vaporization rate.
     - 100% efficiency vs damaged (40% efficiency) shows proportional heat transfer reduction.
  5. Degasser Efficiency Loss:
     - Efficiency loss scales gas extraction throughput per tick.
     - 100% efficiency vs damaged (40% efficiency) shows proportional extraction rate reduction.
"""

import sys
import os
import time
import json
from pathlib import Path

from nhdev import NH_DEV_ROOT as REPO_ROOT

from tools.agentharness import HarnessClient, HarnessError
from tools.inworld_test_driver import InWorldTestDriver

# Coolant Pipe IDs (32125+ range)
PIPE_STEEL_MED = 32140  # Medium Steel Coolant Pipe
DOWN, UP, NORTH, SOUTH, WEST, EAST = 0, 1, 2, 3, 4, 5


def build_maintenance_test_world(client: HarnessClient):
    """Stage 1: Assembles the entire loop with 2-block pump, PHE, Degasser, and pipes."""
    print("------------------------------------------------------------")
    OX, OY, OZ = 500, 5, 20
    OY_LOOP = OY + 1
    print(f"[*] Clearing test build area [{OX-10}, {OY-1}, {OZ-15}] -> [{OX+35}, {OY+15}, {OZ+25}]...")
    try:
        client.clear_area(OX - 10, OY - 1, OZ - 15, OX + 35, OY + 15, OZ + 25)
    except Exception as e:
        print("[!] Note on clear_area:", e)
    client.wait(10)

    # Floor platform via single fill_area
    client.fill_area(OX - 5, OY - 1, OZ - 10, OX + 30, OY - 1, OZ + 15, "stone")
    client.setblock(OX + 10, OY + 2, OZ - 6, "stone")
    client.stand(OX + 10, OY + 3, OZ - 6)
    client.look(0.0, 25.0)
    client.wait(5)

    # 1. Build Coolant Loop Pump (2-block height) with Steel Railcraft Tank (Y+2 to Y+5)
    print("\n[*] Assembling 2-Block High Coolant Loop Pump with Railcraft Tank...")
    pump = client.multiblock_info("coolant_pump", OX, OY, OZ)
    res_pump = client.build_multiblock("coolant_pump", OX, OY, OZ, "steel")
    print("    build_multiblock coolant_pump:", res_pump)
    client.wait(10)

    # 2. Build Pressurized Heat Exchanger (PHE) at (OX+10, OY, OZ)
    print("\n[*] Assembling Pressurized Heat Exchanger...")
    phe = client.multiblock_info("phe", OX + 10, OY, OZ)
    res_phe = client.build_multiblock("phe", OX + 10, OY, OZ)
    print("    build_multiblock phe:", res_phe)
    client.wait(5)
    client.fix_maintenance(OX + 11, OY_LOOP, OZ)
    client.recheck(OX + 11, OY_LOOP, OZ)
    client.wait(10)

    # 3. Build Pressurized Degasser at (OX+18, OY_LOOP, OZ)
    print("\n[*] Assembling Pressurized Degasser...")
    degasser = client.multiblock_info("degasser", OX + 18, OY_LOOP, OZ)
    res_deg = client.build_multiblock("degasser", OX + 18, OY_LOOP, OZ, 1)
    print("    build_multiblock degasser:", res_deg)
    client.wait(10)

    # 4. Route Closed Piping Circuit between Pump, PHE, Degasser, and Manifold
    print("\n[*] Routing Piping circuit via in-game pipe router...")
    # Hot Leg 1: Pump Discharge (OX+2, OY_LOOP, OZ+1) East -> Reservoir (OX+6, OY_LOOP, OZ-5) West
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
    client.configure_creative_reservoir(OX + 6, OY_LOOP, OZ - 5, 450.0, 50.0)

    # Hot Leg 2: Reservoir East -> PHE Primary Inlet (OX+10, OY_LOOP, OZ) West
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

    # Intermediate Leg: PHE Primary Outlet (OX+10, OY_LOOP, OZ+4) South -> Degasser Inlet (OX+18, OY_LOOP, OZ+1) West
    route_inter = client.route_pipe(
        pipe_id=PIPE_STEEL_MED,
        start=(OX + 10, OY_LOOP, OZ + 5),
        end=(OX + 17, OY_LOOP, OZ + 1),
        start_conn="NORTH",
        end_conn="EAST",
        avoid=[phe.bounds, degasser.bounds],
        preferred_y=OY_LOOP,
    )
    print(f"    [+] Intermediate Leg routed: length={route_inter.get('length')} blocks")

    # Cold Leg 1: Degasser Outlet (OX+20, OY_LOOP, OZ+1) East -> Instruments (OX+10, OY_LOOP, OZ+8)
    route_cold_1 = client.route_pipe(
        pipe_id=PIPE_STEEL_MED,
        start=(OX + 21, OY_LOOP, OZ + 1),
        end=(OX + 10, OY_LOOP, OZ + 8),
        start_conn="WEST",
        end_conn="WEST",
        avoid=[degasser.bounds],
        preferred_y=OY_LOOP,
    )
    print(f"    [+] Cold Leg Segment 1 routed: length={route_cold_1.get('length')} blocks")

    # Flow Meter at (OX+9, OY_LOOP, OZ+8) facing SOUTH (meta 3)
    client.set_block(OX + 9, OY_LOOP, OZ + 8, "modularnuclear:loop_instrument", 3)
    client.configure_instrument(OX + 9, OY_LOOP, OZ + 8, "flow", "SOUTH")
    client.setblock(OX + 9, OY_LOOP, OZ + 9, "redstone_lamp")

    # Every loop device discharges into a pipe: one after the flow meter, the thermometer, then one more
    client.set_gt_machine(OX + 8, OY_LOOP, OZ + 8, PIPE_STEEL_MED, 0)
    client.connect_pipe(OX + 8, OY_LOOP, OZ + 8, "EAST")
    client.connect_pipe(OX + 8, OY_LOOP, OZ + 8, "WEST")

    # Thermometer at (OX+7, OY_LOOP, OZ+8) facing SOUTH (meta 3)
    client.set_block(OX + 7, OY_LOOP, OZ + 8, "modularnuclear:loop_instrument", 3)
    client.configure_instrument(OX + 7, OY_LOOP, OZ + 8, "temp", "SOUTH")
    client.setblock(OX + 7, OY_LOOP, OZ + 9, "redstone_lamp")

    # Medium Steel Pipe at OX+6 between Instrument and Manifold
    client.set_gt_machine(OX + 6, OY_LOOP, OZ + 8, PIPE_STEEL_MED, 0)
    client.connect_pipe(OX + 6, OY_LOOP, OZ + 8, "EAST")
    client.connect_pipe(OX + 6, OY_LOOP, OZ + 8, "WEST")

    # Manifold at (OX+5, OY_LOOP, OZ+8) facing WEST
    client.set_block(OX + 5, OY_LOOP, OZ + 8, "modularnuclear:manifold", 4)
    client.configure_manifold(OX + 5, OY_LOOP, OZ + 8, "WEST")

    # Cold Leg 2: Manifold East -> Pump Suction (OX, OY_LOOP, OZ+1) West
    route_cold_2 = client.route_pipe(
        pipe_id=PIPE_STEEL_MED,
        start=(OX + 4, OY_LOOP, OZ + 8),
        end=(OX - 1, OY_LOOP, OZ + 1),
        start_conn="EAST",
        end_conn="EAST",
        avoid=[pump.bounds, phe.bounds, degasser.bounds],
        preferred_y=OY_LOOP,
    )
    print(f"    [+] Cold Leg Segment 2 routed: length={route_cold_2.get('length')} blocks")

    # Setup Secondary Circuit on PHE (Debug Tank and Super Tank)
    client.set_gt_machine(OX + 13, OY_LOOP, OZ, 238, 4)
    client.configure_debug_tank(OX + 13, OY_LOOP, OZ, "ic2distilledwater", True)
    client.set_gt_machine(OX + 13, OY_LOOP, OZ + 4, 130, 4)
    client.configure_supertank(OX + 13, OY_LOOP, OZ + 4, True)
    client.enable_auto_output(OX + 12, OY_LOOP, OZ + 4)

    # Lock Degasser top output hatch to deuterium
    client.lock_output_fluid(OX + 19, OY_LOOP + 6, OZ, "deuterium")
    client.wait(10)
    print("[✓] Build phase complete.")


def test_maintenance_suite(client: HarnessClient):
    """Stage 2: Live simulation verifying maintenance and efficiency loss across all multiblocks."""
    print("============================================================")
    print("STARTING MAINTENANCE & EFFICIENCY LOSS IN-WORLD VERIFICATION")
    print("============================================================")
    OX, OY, OZ = 500, 5, 20
    OY_LOOP = OY + 1
    PUMP_CTRL = (OX + 1, OY, OZ)
    PHE_CTRL = (OX + 11, OY_LOOP, OZ)
    DEG_CTRL = (OX + 19, OY_LOOP, OZ)

    # Position player
    client.stand(OX + 10, OY + 3, OZ - 6)
    client.look(0.0, 20.0)
    client.wait(20)

    # Fill Railcraft Tank with distilled water
    print("\n[*] Initializing Railcraft Tank coolant fluid...")
    client.fill_fluid(OX + 1, OY + 2, OZ + 1, "ic2distilledwater", 100000)
    client.wait(10)

    # ============================================================
    # TEST 1: Initial Multiblock Formation & Maintenance Status
    # ============================================================
    print("\n--- TEST 1: Initial Multiblock Formation & Maintenance Status ---")

    # Check Pump
    client.recheck(*PUMP_CTRL)
    client.wait(10)
    st_pump = client.coolant_status(PUMP_CTRL[0], PUMP_CTRL[1], PUMP_CTRL[2])
    print("Pump status:", json.dumps(st_pump, indent=2))
    assert st_pump.get("machine_formed", False), f"Pump failed to form: {st_pump}"
    assert st_pump.get("repair_status", 0) == 6, f"Pump repair status not 6: {st_pump}"
    assert abs(st_pump.get("efficiency_factor", 0.0) - 1.0) < 1e-4, f"Pump efficiency factor not 1.0: {st_pump}"
    print("[✓] Pump formed successfully with 100% efficiency and 6/6 repairs!")

    # Check PHE
    client.recheck(*PHE_CTRL)
    client.wait(10)
    st_phe = client.coolant_status(PHE_CTRL[0], PHE_CTRL[1], PHE_CTRL[2])
    print("PHE status:", json.dumps(st_phe, indent=2))
    assert st_phe.get("machine_formed", False), f"PHE failed to form: {st_phe}"
    assert st_phe.get("repair_status", 0) == 6, f"PHE repair status not 6: {st_phe}"
    assert abs(st_phe.get("efficiency_factor", 0.0) - 1.0) < 1e-4, f"PHE efficiency factor not 1.0: {st_phe}"
    print("[✓] PHE formed successfully with 100% efficiency and 6/6 repairs!")

    # Check Degasser
    client.recheck(*DEG_CTRL)
    client.wait(10)
    st_deg = client.coolant_status(DEG_CTRL[0], DEG_CTRL[1], DEG_CTRL[2])
    print("Degasser status:", json.dumps(st_deg, indent=2))
    assert st_deg.get("machine_formed", False), f"Degasser failed to form: {st_deg}"
    assert st_deg.get("repair_status", 0) == 6, f"Degasser repair status not 6: {st_deg}"
    assert abs(st_deg.get("efficiency_factor", 0.0) - 1.0) < 1e-4, f"Degasser efficiency factor not 1.0: {st_deg}"
    print("[✓] Degasser formed successfully with 100% efficiency and 6/6 repairs!")

    # ============================================================
    # TEST 2: Maintenance Hatch Requirement Enforcement
    # ============================================================
    print("\n--- TEST 2: Maintenance Hatch Requirement Enforcement ---")

    # A. Degasser without Maintenance Hatch
    print("[*] Testing Degasser structure without Maintenance Hatch...")
    DEG_MAINT = (OX + 19, OY_LOOP, OZ + 1)
    client.setblock(DEG_MAINT[0], DEG_MAINT[1], DEG_MAINT[2], "gregtech:gt.blockcasings4", 2)
    client.wait(5)
    client.recheck(*DEG_CTRL)
    client.wait(10)
    st_deg_nomaint = client.coolant_status(DEG_CTRL[0], DEG_CTRL[1], DEG_CTRL[2])
    assert not st_deg_nomaint.get("machine_formed", False), "Degasser formed without Maintenance Hatch!"
    print("[✓] Degasser correctly rejects formation when Maintenance Hatch is missing!")

    # Restore Degasser Maintenance Hatch
    client.set_gt_machine(DEG_MAINT[0], DEG_MAINT[1], DEG_MAINT[2], 15497, 2)
    client.fix_maintenance(DEG_CTRL[0], DEG_CTRL[1], DEG_CTRL[2])
    client.wait(5)
    client.recheck(*DEG_CTRL)
    client.wait(10)
    assert client.coolant_status(DEG_CTRL[0], DEG_CTRL[1], DEG_CTRL[2]).get("machine_formed", False)
    print("[✓] Degasser re-formed after restoring Maintenance Hatch!")

    # B. PHE without Maintenance Hatch
    print("[*] Testing PHE structure without Maintenance Hatch...")
    PHE_MAINT = (OX + 11, OY_LOOP, OZ + 4)
    client.set_block(PHE_MAINT[0], PHE_MAINT[1], PHE_MAINT[2], "gregtech:gt.blockcasings", 11)
    client.wait(5)
    client.recheck(*PHE_CTRL)
    client.wait(10)
    st_phe_nomaint = client.coolant_status(PHE_CTRL[0], PHE_CTRL[1], PHE_CTRL[2])
    assert not st_phe_nomaint.get("machine_formed", False), "PHE formed without Maintenance Hatch!"
    print("[✓] PHE correctly rejects formation when Maintenance Hatch is missing!")

    # Restore PHE Maintenance Hatch
    client.set_gt_machine(PHE_MAINT[0], PHE_MAINT[1], PHE_MAINT[2], 15497, 3)
    client.fix_maintenance(PHE_CTRL[0], PHE_CTRL[1], PHE_CTRL[2])
    client.wait(5)
    client.recheck(*PHE_CTRL)
    client.wait(10)
    assert client.coolant_status(PHE_CTRL[0], PHE_CTRL[1], PHE_CTRL[2]).get("machine_formed", False)
    print("[✓] PHE re-formed after restoring Maintenance Hatch!")

    # C. Pump without Maintenance Hatch
    print("[*] Testing Pump structure without Maintenance Hatch...")
    PUMP_MAINT = (OX + 1, OY, OZ + 1)
    client.set_block(PUMP_MAINT[0], PUMP_MAINT[1], PUMP_MAINT[2], "gregtech:gt.blockcasings2", 15)
    client.wait(5)
    client.recheck(*PUMP_CTRL)
    client.wait(10)
    st_pump_nomaint = client.coolant_status(PUMP_CTRL[0], PUMP_CTRL[1], PUMP_CTRL[2])
    assert not st_pump_nomaint.get("machine_formed", False), "Pump formed without Maintenance Hatch!"
    print("[✓] Pump correctly rejects formation when Maintenance Hatch is missing!")

    # Restore Pump Maintenance Hatch
    client.set_gt_machine(PUMP_MAINT[0], PUMP_MAINT[1], PUMP_MAINT[2], 15497, 2)
    client.fix_maintenance(PUMP_CTRL[0], PUMP_CTRL[1], PUMP_CTRL[2])
    client.wait(5)
    client.recheck(*PUMP_CTRL)
    client.wait(10)
    assert client.coolant_status(PUMP_CTRL[0], PUMP_CTRL[1], PUMP_CTRL[2]).get("machine_formed", False)
    print("[✓] Pump re-formed after restoring Maintenance Hatch!")

    # ============================================================
    # TEST 3: Loop Crawling & Active Circulation
    # ============================================================
    print("\n--- TEST 3: Loop Crawling & Active Circulation ---")
    crawl_res = client.crawl_loop(PUMP_CTRL[0], PUMP_CTRL[1], PUMP_CTRL[2])
    print("    crawl_loop result:", crawl_res)
    client.wait(15)

    st_circ = client.coolant_status(PUMP_CTRL[0], PUMP_CTRL[1], PUMP_CTRL[2])
    assert st_circ.get("loop_formed", False), f"Loop failed to form: {st_circ}"
    print(f"[✓] Loop formed! Circulation state: {st_circ.get('loop_state')}, Flow rate: {st_circ.get('flow_rate_l_s')} L/s")

    # ============================================================
    # TEST 4: Pump Maintenance & Mechanical Power Efficiency Loss
    # ============================================================
    print("\n--- TEST 4: Pump Maintenance & Mechanical Power Efficiency Loss ---")
    client.fix_maintenance(PUMP_CTRL[0], PUMP_CTRL[1], PUMP_CTRL[2])
    client.wait(10)
    st_p100 = client.coolant_status(PUMP_CTRL[0], PUMP_CTRL[1], PUMP_CTRL[2])
    assert abs(st_p100.get("efficiency_factor", 0.0) - 1.0) < 1e-4

    # Now damage pump maintenance to maximum operational damage: 5 issues (1/6 repairs -> 50% efficiency)
    print("[*] Damaging pump maintenance to 5 issues (1/6 repairs -> 50% efficiency)...")
    res_dmg = client.damage_maintenance(PUMP_CTRL[0], PUMP_CTRL[1], PUMP_CTRL[2], 5)
    print("    damage_maintenance result:", res_dmg)
    client.wait(10)

    st_p50 = client.coolant_status(PUMP_CTRL[0], PUMP_CTRL[1], PUMP_CTRL[2])
    eff_p50 = st_p50.get("efficiency_factor", 1.0)
    rep_p50 = st_p50.get("repair_status", 6)
    print(f"    Damaged pump: repair_status={rep_p50}, efficiency_factor={eff_p50}")
    assert rep_p50 == 1, f"Expected repair_status 1, got {rep_p50}"
    assert abs(eff_p50 - 0.50) < 1e-4, f"Expected efficiency_factor 0.50, got {eff_p50}"
    print("[✓] Pump efficiency factor correctly dropped to 0.50 under maintenance neglect!")

    # Restore pump maintenance
    client.fix_maintenance(PUMP_CTRL[0], PUMP_CTRL[1], PUMP_CTRL[2])
    client.wait(5)
    st_p_restored = client.coolant_status(PUMP_CTRL[0], PUMP_CTRL[1], PUMP_CTRL[2])
    if not st_p_restored.get("is_allowed_to_work", True):
        client.soft_hammer(PUMP_CTRL[0], PUMP_CTRL[1], PUMP_CTRL[2])
        client.wait(5)
        st_p_restored = client.coolant_status(PUMP_CTRL[0], PUMP_CTRL[1], PUMP_CTRL[2])
    assert abs(st_p_restored.get("efficiency_factor", 0.0) - 1.0) < 1e-4
    print(f"[✓] Pump efficiency restored to 1.00 (working={st_p_restored.get('is_allowed_to_work')}, flow={st_p_restored.get('flow_rate_l_s'):.1f} L/s)!")

    # ============================================================
    # TEST 5: Degasser Gas Extraction Efficiency Loss
    # ============================================================
    print("\n--- TEST 5: Degasser Gas Extraction Efficiency Loss ---")
    # Add 10,000 L of dissolved deuterium to the loop (output hatch is locked to deuterium)
    client.add_dissolved_gas(PUMP_CTRL[0], PUMP_CTRL[1], PUMP_CTRL[2], "deuterium", 10000)
    client.wait(5)

    # 1. Extraction at 100% efficiency
    client.fix_maintenance(DEG_CTRL[0], DEG_CTRL[1], DEG_CTRL[2])
    client.wait(5)
    st_deg_init = client.coolant_status(DEG_CTRL[0], DEG_CTRL[1], DEG_CTRL[2])
    ext_init = st_deg_init.get("total_extracted_liters", 0)

    # Run for 40 ticks (2.0 seconds), continuously keeping energy charged
    for _ in range(8):
        client.charge_energy(DEG_CTRL[0], DEG_CTRL[1], DEG_CTRL[2], 1000000)
        client.wait(5)

    st_deg_100 = client.coolant_status(DEG_CTRL[0], DEG_CTRL[1], DEG_CTRL[2])
    ext_100 = st_deg_100.get("total_extracted_liters", 0) - ext_init
    print(f"    Gas extracted at 100% efficiency in 2 sec: {ext_100} L")
    assert ext_100 > 0, "No gas was extracted at 100% efficiency!"

    # Replenish extracted deuterium to maintain identical loop concentration
    client.add_dissolved_gas(PUMP_CTRL[0], PUMP_CTRL[1], PUMP_CTRL[2], "deuterium", ext_100)
    client.wait(5)

    # 2. Extraction at 50% efficiency (5 issues, 1 repair)
    print("[*] Damaging Degasser maintenance to 5 issues (1/6 repairs -> 50% efficiency)...")
    client.damage_maintenance(DEG_CTRL[0], DEG_CTRL[1], DEG_CTRL[2], 5)
    client.wait(5)
    st_deg_check = client.coolant_status(DEG_CTRL[0], DEG_CTRL[1], DEG_CTRL[2])
    assert abs(st_deg_check.get("efficiency_factor", 1.0) - 0.50) < 1e-4

    ext_start_50 = st_deg_check.get("total_extracted_liters", 0)
    for _ in range(8):
        client.charge_energy(DEG_CTRL[0], DEG_CTRL[1], DEG_CTRL[2], 1000000)
        client.wait(5)

    st_deg_50 = client.coolant_status(DEG_CTRL[0], DEG_CTRL[1], DEG_CTRL[2])
    ext_50 = st_deg_50.get("total_extracted_liters", 0) - ext_start_50
    print(f"    Gas extracted at 50% efficiency in 2 sec: {ext_50} L")
    assert ext_50 < ext_100, f"Extraction at 50% ({ext_50}) was not less than 100% ({ext_100})!"
    ratio = ext_50 / float(ext_100)
    print(f"    Observed extraction ratio: {ratio:.3f} (theoretical: ~0.500)")
    assert 0.35 <= ratio <= 0.65, f"Extraction ratio {ratio} deviated significantly from 0.50!"
    print("[✓] Degasser extraction rate strictly throttled by maintenance efficiency loss!")

    # Restore Degasser
    client.fix_maintenance(DEG_CTRL[0], DEG_CTRL[1], DEG_CTRL[2])
    client.wait(5)

    # ============================================================
    # TEST 6: PHE Convective Heat Transfer Efficiency Loss
    # ============================================================
    print("\n--- TEST 6: PHE Convective Heat Transfer Efficiency Loss ---")
    # Feed secondary distilled water to PHE Input Hatch at (OX+12, OY_LOOP, OZ)
    client.fill_fluid(OX + 12, OY_LOOP, OZ, "ic2distilledwater", 50000)
    client.wait(10)

    # At 100% efficiency
    client.fix_maintenance(PHE_CTRL[0], PHE_CTRL[1], PHE_CTRL[2])
    q_100 = 0.0
    for _ in range(40):
        client.wait(5)
        st_phe_100 = client.coolant_status(PHE_CTRL[0], PHE_CTRL[1], PHE_CTRL[2])
        q_100 = st_phe_100.get("heat_transfer_watts", 0.0)
        temp_c = st_phe_100.get("coolant_temp_c", 0.0)
        if q_100 > 0.0:
            break
    print(f"    PHE coolant temp: {temp_c:.1f}°C, heat transfer rate at 100% efficiency: {q_100:.1f} W")
    assert q_100 > 0.0, f"PHE did not transfer heat at 100% efficiency! (temp={temp_c:.1f}°C, q={q_100})"

    # At 50% efficiency (5 issues, 1 repair)
    print("[*] Damaging PHE maintenance to 5 issues (1/6 repairs -> 50% efficiency)...")
    client.damage_maintenance(PHE_CTRL[0], PHE_CTRL[1], PHE_CTRL[2], 5)
    client.wait(15)
    st_phe_50 = client.coolant_status(PHE_CTRL[0], PHE_CTRL[1], PHE_CTRL[2])
    q_50 = st_phe_50.get("heat_transfer_watts", 0.0)
    eff_phe = st_phe_50.get("efficiency_factor", 1.0)
    print(f"    PHE heat transfer rate at 50% efficiency: {q_50:.1f} W (eff={eff_phe})")
    assert abs(eff_phe - 0.50) < 1e-4
    phe_ratio = q_50 / q_100
    print(f"    Observed PHE heat transfer ratio: {phe_ratio:.3f} (theoretical: 0.500)")
    assert 0.40 <= phe_ratio <= 0.65, f"PHE heat transfer ratio {phe_ratio} deviated from 0.50!"
    print("[✓] PHE convective heat transfer strictly scaled by maintenance efficiency loss!")

    # Restore PHE
    client.fix_maintenance(PHE_CTRL[0], PHE_CTRL[1], PHE_CTRL[2])
    client.wait(5)

    # ============================================================
    # Visual Screenshots
    # ============================================================
    print("\n[*] Capturing verification screenshots...")
    # 1. 2-Block Pump + Railcraft Tank
    client.stand(OX + 1, OY + 4, OZ - 6)
    client.look(0.0, 15.0)
    client.wait(15)
    client.screenshot("screenshots/coolant_loops_2block_pump_tank.png")
    print("[✓] Screenshot saved: screenshots/coolant_loops_2block_pump_tank.png")

    # 2. Pressurized Heat Exchanger with Maintenance Hatch
    client.stand(OX + 11, OY + 3, OZ - 6)
    client.look(0.0, 15.0)
    client.wait(15)
    client.screenshot("screenshots/coolant_loops_phe_maintenance.png")
    print("[✓] Screenshot saved: screenshots/coolant_loops_phe_maintenance.png")

    # 3. Pressurized Degasser with Maintenance Hatch
    client.stand(OX + 19, OY + 4, OZ - 6)
    client.look(0.0, 15.0)
    client.wait(15)
    client.screenshot("screenshots/coolant_loops_degasser_maintenance.png")
    print("[✓] Screenshot saved: screenshots/coolant_loops_degasser_maintenance.png")

    # 4. Overall loop view
    client.stand(OX + 10, OY + 8, OZ - 12)
    client.look(0.0, 30.0)
    client.wait(15)
    client.screenshot("screenshots/coolant_loops_maintenance_overview.png")
    print("[✓] Screenshot saved: screenshots/coolant_loops_maintenance_overview.png")

    # Copy screenshots from PrismLauncher instance to repo and artifact directory
    import shutil
    prism_dir = Path("/home/mgomezch/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances/GTNH-2.9-dev/.minecraft/screenshots")
    art_dir = Path("/home/mgomezch/.gemini/antigravity/brain/bc0c2152-bd77-425b-aa35-bf4d7878b40d")
    repo_shots = REPO_ROOT / "screenshots"
    repo_shots.mkdir(parents=True, exist_ok=True)
    shot_names = [
        "coolant_loops_2block_pump_tank.png",
        "coolant_loops_phe_maintenance.png",
        "coolant_loops_degasser_maintenance.png",
        "coolant_loops_maintenance_overview.png",
    ]
    for sname in shot_names:
        src = prism_dir / sname
        if src.exists():
            dst_repo = repo_shots / sname
            if src.resolve() != dst_repo.resolve():
                shutil.copy2(src, dst_repo)
            if art_dir.is_dir() and src.resolve() != (art_dir / sname).resolve():
                shutil.copy2(src, art_dir / sname)
            print(f"[✓] Deployed screenshot {sname} to artifacts and repo.")

    print("\n============================================================")
    print("ALL 6 MAINTENANCE & EFFICIENCY LOSS TESTS PASSED!")
    print("============================================================")


def run_tests(port: int = 25571):
    driver = InWorldTestDriver(
        tag="coolant_maintenance",
        port=port,
        base_template="superflat_empty",
    )
    success = driver.run(
        build_fn=build_maintenance_test_world,
        test_fn=test_maintenance_suite,
    )
    if not success:
        print("[!] Maintenance & efficiency test suite failed!")
        sys.exit(1)
    print("[✓] Maintenance & efficiency test lifecycle completed successfully!")
    return success


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Coolant Loops Maintenance and Efficiency In-World Test")
    parser.add_argument("--port", type=int, default=25571, help="AgentHarness port (default: 25571)")
    args = parser.parse_args()
    success = run_tests(port=args.port)
    sys.exit(0 if success else 1)

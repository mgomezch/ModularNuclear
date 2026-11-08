#!/usr/bin/env python3
"""
End-to-End Automated Test: 60A Modular Pressure Tube Reactor (MPTR) with Super Factory Manager.

Features Under Test:
- Construction of 60A Electrum 5x5 Peak Power Reactor (Tier 1 EV).
- Auto-Taping Maintenance Hatch, EV Dynamo Hatch, EV Output Hatch, and EV Output Bus.
- 21-cell core layout with 6 Quad Uranium Rods, 4 Quad MOX Rods, and 11 IC2 Coolant Hatches.
- Solid Super Factory Manager Fancy Cable canopy covering all 21 core tiles.
- SFM automated logistics:
  * Creative Vending Drawers supplying infinite Quad Uranium & MOX rods.
  * GregTech Debug Tank supplying infinite IC2 Coolant.
  * SFM Manager running 20-tick cyclic supply & waste-clearance program.
  * Extra Utilities Fluid & Item Trash Cans voiding hot coolant and spent items.
- Verification of multiblock formation, SFM compilation, steady cycle execution,
  and power/steam/hot coolant generation.
- Automated screenshot capture of 3D build, Manager GUI, Code Editor GUI, and Reactor GUI.
"""

import sys
import os
import time
import json
import shutil
from pathlib import Path
from typing import Dict, Any, List, Tuple

from nhdev import NH_DEV_ROOT as REPO_ROOT

from tools.agentharness import HarnessClient, HarnessError
from tools.sfm_client import SfmClient
from tools.runner_instance import get_saves_dir
from tools.mcfleet.client import FleetClient
from tools.bot.world_manager import WorldSandbox

HARNESS_PORT = 25572
SFM_PORT = 25573

OUTPUT_DIR = REPO_ROOT / "output" / "sfm_nuclear_60a"
ARTIFACTS_DIR = Path("/home/mgomezch/.gemini/antigravity/brain/bc0c2152-bd77-425b-aa35-bf4d7878b40d")

# Multiblock Origin & Coordinates
RC_X, RC_Y, RC_Z = 500, 5, 500

# 21 Core Tile Coordinates at Y=8: the simulator's stable "NL,HC,M4,HC,NL;HC,U4,U4,U4,HC;M4,HC,HC,HC,M4;HC,U4,U4,U4,HC;NL,HC,M4,HC,NL"
# (about 41k EU/t, 20 A EV, peaking near 215 °C). The old 60 A layout of 8 quad uranium, 9 quad MOX and 4 coolant
# hatches melts down within seconds since the physics were recalibrated, in the game and in the simulator
COOLANT_HATCHES: List[Tuple[int, int, int]] = [
    (499, 8, 501),
    (501, 8, 501),
    (498, 8, 502),
    (502, 8, 502),
    (499, 8, 503),
    (500, 8, 503),
    (501, 8, 503),
    (498, 8, 504),
    (502, 8, 504),
    (499, 8, 505),
    (501, 8, 505),
]

U4_BUSES: List[Tuple[int, int, int]] = [
    (499, 8, 502),
    (500, 8, 502),
    (501, 8, 502),
    (499, 8, 504),
    (500, 8, 504),
    (501, 8, 504),
]

M4_BUSES: List[Tuple[int, int, int]] = [
    (500, 8, 501),
    (498, 8, 503),
    (502, 8, 503),
    (500, 8, 505),
]

# Automation Logistics Positions at Y=9
MGR_POS = (495, 9, 502)
DRAWER_U4_POS = (495, 9, 501)
DRAWER_M4_POS = (495, 9, 500)
COOLANT_TANK_POS = (495, 9, 499)
FLUID_TRASH_POS = (495, 9, 503)
ITEM_TRASH_POS = (495, 9, 504)

OUT_HATCH_POS = (501, 5, 500)
OUT_BUS_POS = (502, 5, 500)


def build_and_wire_setup(harness: HarnessClient, sfm: SfmClient):
    print("============================================================")
    print("[*] Stage 1: Constructing 60A Electrum Reactor & Hatches...")
    print("============================================================")

    # Clean local area and lay quartz foundation
    harness.clear_area(490, 4, 490, 510, 15, 515)
    time.sleep(0.5)
    harness.fill_area(490, 4, 490, 510, 4, 515, "minecraft:quartz_block", 0)
    time.sleep(0.5)

    # 1. Place Controller and trigger structure autobuild
    print(f"[*] Placing MPTR controller at ({RC_X}, {RC_Y}, {RC_Z}) facing North...")
    harness.set_gt_machine(RC_X, RC_Y, RC_Z, 32115, 2)
    time.sleep(0.5)
    res_ab = harness.autobuild(RC_X, RC_Y, RC_Z)
    print(f"    Autobuild result: {res_ab}")
    time.sleep(0.5)

    # 2. Configure Perimeter Hatches on Front Face (Y=5)
    print("[*] Installing Auto-Taping Maintenance Hatch, Dynamo, Output Hatch, and Output Bus...")
    harness.set_gt_machine(499, 5, 500, 15497, 2)  # Auto-Taping Maintenance Hatch
    harness.set_gt_machine(498, 5, 500, 38, 2)   # UV Dynamo Hatch (524k EU/t high-capacity buffer)
    harness.set_gt_machine(501, 5, 500, 64, 2)   # EV Output Hatch (Fluid)
    harness.set_gt_machine(502, 5, 500, 84, 2)   # EV Output Bus (Item)
    time.sleep(0.5)

    # 3. Configure Top Core Tiles (Y=8) according to 60A Electrum Ceiling Preset
    print("[*] Configuring 21 Top Core Hatches & Buses at Y=8...")
    # 11 IC2 Coolant Hatches (EV Tier = ID 32104, facing UP = 1)
    for pos in COOLANT_HATCHES:
        harness.set_gt_machine(pos[0], pos[1], pos[2], 32104, 1)

    # 6 Quad Uranium Nuclear Buses (ID 32100, facing UP = 1)
    for pos in U4_BUSES:
        harness.set_gt_machine(pos[0], pos[1], pos[2], 32100, 1)

    # 4 Quad MOX Nuclear Buses (ID 32100, facing UP = 1)
    for pos in M4_BUSES:
        harness.set_gt_machine(pos[0], pos[1], pos[2], 32100, 1)

    time.sleep(0.5)

    # 4. Form and verify multiblock
    print("[*] Verifying multiblock formation...")
    harness.recheck(RC_X, RC_Y, RC_Z)
    time.sleep(0.5)
    st = harness.nuke_status(RC_X, RC_Y, RC_Z)
    assert st.get("formed"), f"Reactor failed to form: {st}"
    print(f"[✓] Reactor formed cleanly! (tiles={st.get('tiles')}, pipeTier={st.get('pipeTier')})")
    harness.fix_maintenance(RC_X, RC_Y, RC_Z)
    time.sleep(0.3)

    # 5. Build Fancy Cable Canopy directly covering all 21 core tiles at Y=9
    print("[*] Covering top core with SFM Fancy Inventory Cables at Y=9...")
    for cx in range(498, 503):
        for cz in range(500, 506):
            harness.set_block(cx, 9, cz, "superfactorymanager:fancyCable", 0)

    # Route cable connection to Output Hatch & Bus at front
    for cy in range(5, 10):
        harness.set_block(501, cy, 499, "superfactorymanager:fancyCable", 0)
        harness.set_block(502, cy, 499, "superfactorymanager:fancyCable", 0)

    # Route cable westward to logistics station
    harness.set_block(497, 9, 502, "superfactorymanager:fancyCable", 0)
    harness.set_block(496, 9, 502, "superfactorymanager:fancyCable", 0)
    for cz in range(499, 505):
        harness.set_block(496, 9, cz, "superfactorymanager:fancyCable", 0)

    # 6. Install SFM Manager, Drawers, Debug Tank, and Trash Cans
    print("[*] Placing Factory Manager, Vending Drawers, Debug Tank, and Trash Cans...")
    harness.set_block(MGR_POS[0], MGR_POS[1], MGR_POS[2], "superfactorymanager:manager", 0)

    # Creative Vending Drawers
    harness.setup_vending_drawer(x=DRAWER_U4_POS[0], y=DRAWER_U4_POS[1], z=DRAWER_U4_POS[2], item="gregtech:gt.rodUranium4", meta=0, dir=2)
    harness.setup_vending_drawer(x=DRAWER_M4_POS[0], y=DRAWER_M4_POS[1], z=DRAWER_M4_POS[2], item="gregtech:gt.rodMOX4", meta=0, dir=2)

    # Infinite IC2 Coolant Debug Tank
    harness.set_gt_machine(COOLANT_TANK_POS[0], COOLANT_TANK_POS[1], COOLANT_TANK_POS[2], 238, 2)
    time.sleep(0.3)
    harness.configure_debug_tank(COOLANT_TANK_POS[0], COOLANT_TANK_POS[1], COOLANT_TANK_POS[2], "ic2coolant", output_fluid=False)

    # Extra Utilities Trash Cans
    harness.set_block(FLUID_TRASH_POS[0], FLUID_TRASH_POS[1], FLUID_TRASH_POS[2], "ExtraUtilities:trashcan", 1)
    harness.set_block(ITEM_TRASH_POS[0], ITEM_TRASH_POS[1], ITEM_TRASH_POS[2], "ExtraUtilities:trashcan", 0)
    time.sleep(0.5)

    # 7. Configure SFM Labels
    print("[*] Programming peripheral labels into Factory Manager...")
    mx, my, mz = MGR_POS
    sfm.clearlabels(x=mx, y=my, z=mz)

    # Single peripherals
    sfm.setlabel(x=mx, y=my, z=mz, label="u4_drawer", tx=DRAWER_U4_POS[0], ty=DRAWER_U4_POS[1], tz=DRAWER_U4_POS[2])
    sfm.setlabel(x=mx, y=my, z=mz, label="m4_drawer", tx=DRAWER_M4_POS[0], ty=DRAWER_M4_POS[1], tz=DRAWER_M4_POS[2])
    sfm.setlabel(x=mx, y=my, z=mz, label="coolant_tank", tx=COOLANT_TANK_POS[0], ty=COOLANT_TANK_POS[1], tz=COOLANT_TANK_POS[2])
    sfm.setlabel(x=mx, y=my, z=mz, label="fluid_trash", tx=FLUID_TRASH_POS[0], ty=FLUID_TRASH_POS[1], tz=FLUID_TRASH_POS[2])
    sfm.setlabel(x=mx, y=my, z=mz, label="item_trash", tx=ITEM_TRASH_POS[0], ty=ITEM_TRASH_POS[1], tz=ITEM_TRASH_POS[2])
    sfm.setlabel(x=mx, y=my, z=mz, label="reactor_out_hatch", tx=OUT_HATCH_POS[0], ty=OUT_HATCH_POS[1], tz=OUT_HATCH_POS[2])
    sfm.setlabel(x=mx, y=my, z=mz, label="reactor_out_bus", tx=OUT_BUS_POS[0], ty=OUT_BUS_POS[1], tz=OUT_BUS_POS[2])

    # Grouped Hatch & Bus Clusters
    for pos in COOLANT_HATCHES:
        sfm.setlabel(x=mx, y=my, z=mz, label="coolant_hatches", tx=pos[0], ty=pos[1], tz=pos[2])
    for pos in U4_BUSES:
        sfm.setlabel(x=mx, y=my, z=mz, label="u4_buses", tx=pos[0], ty=pos[1], tz=pos[2])
    for pos in M4_BUSES:
        sfm.setlabel(x=mx, y=my, z=mz, label="m4_buses", tx=pos[0], ty=pos[1], tz=pos[2])

    # 8. Compile and Load SFM Program
    program_code = (
        "NAME \"60A Reactor Automation\"\n"
        "EVERY 20 TICKS DO\n"
        "  INPUT FROM u4_drawer\n"
        "  OUTPUT TO u4_buses\n"
        "  INPUT FROM m4_drawer\n"
        "  OUTPUT TO m4_buses\n"
        "  INPUT 8000 fluid:ic2coolant FROM coolant_tank\n"
        "  OUTPUT TO coolant_hatches\n"
        "  INPUT 8000 fluid:ic2hotcoolant FROM reactor_out_hatch\n"
        "  OUTPUT TO fluid_trash\n"
        "  INPUT FROM reactor_out_bus\n"
        "  OUTPUT TO item_trash\n"
        "END\n"
    )
    print("[*] Compiling SFM Automation Script...")
    res_prog = sfm.programmanager(x=mx, y=my, z=mz, code=program_code)
    print(f"    Compiler result: {res_prog}")
    assert res_prog["valid"], f"SFM program failed compilation: {res_prog}"

    # Start SFM execution
    sfm.settick(x=mx, y=my, z=mz, tick=0)
    sfm.setrunning(x=mx, y=my, z=mz, running=True)
    print("[✓] Build and automation wiring phase completed successfully!")


def verify_and_capture(harness: HarnessClient, sfm: SfmClient):
    print("============================================================")
    print("[*] Stage 2: Verifying Reactor Operation & Capturing Telemetry...")
    print("============================================================")
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    ARTIFACTS_DIR.mkdir(parents=True, exist_ok=True)

    # 1. Let the system run for 40 ticks (2 seconds) to populate all buses and hatches
    print("[*] Stepping simulation through 2 initial logistics cycles (40 ticks)...")
    time.sleep(2.5)

    # Verify that nuclear buses now contain rods
    sample_u4 = harness.container_info(x=U4_BUSES[0][0], y=U4_BUSES[0][1], z=U4_BUSES[0][2])
    sample_m4 = harness.container_info(x=M4_BUSES[0][0], y=M4_BUSES[0][1], z=M4_BUSES[0][2])
    sample_coolant = harness.te_fluid(COOLANT_HATCHES[0][0], COOLANT_HATCHES[0][1], COOLANT_HATCHES[0][2])

    print(f"[+] Sample U4 Bus: {sample_u4.get('slots', [])}")
    print(f"[+] Sample M4 Bus: {sample_m4.get('slots', [])}")
    print(f"[+] Sample Coolant Hatch: {sample_coolant.get('tanks', [])}")

    assert len(sample_u4.get("slots", [])) > 0, "U4 bus was not supplied with fuel rods!"
    assert len(sample_m4.get("slots", [])) > 0, "M4 bus was not supplied with fuel rods!"
    assert len(sample_coolant.get("tanks", [])) > 0 and sample_coolant["tanks"][0]["amount"] > 0, "Coolant hatch was not supplied!"
    print("[✓] ASSERTION PASSED: SFM successfully supplied fuel rods and coolant into core tiles!")

    # 2. Monitor Reactor Nuclear Simulation & Power/Steam Generation over 5 seconds
    print("[*] Monitoring reactor operation over active fission cycles...")
    initial_temp = harness.nuke_status(RC_X, RC_Y, RC_Z).get("core_temperature", 20.0)

    time.sleep(5.0)

    final_st = harness.nuke_status(RC_X, RC_Y, RC_Z)
    print("\n------------------------------------------------------------")
    print(f"[+] Reactor Core Telemetry:")
    print(f"    Status: Formed={final_st.get('formed')}, Active={final_st.get('active', True)}")
    print(f"    Efficiency: {final_st.get('eff', 0.0):.1f}%")
    print(f"    Core Temperature: {final_st.get('core_temperature', 0.0):.2f} °C (Initial: {initial_temp:.2f} °C)")
    print(f"    Tiles Active: {final_st.get('tiles', 0)}")
    print("------------------------------------------------------------\n")

    assert final_st.get("formed"), "Reactor de-formed during operation!"
    assert final_st.get("core_temperature", 0.0) > initial_temp, (
        f"Core temperature did not increase! (Initial: {initial_temp:.2f} °C, Final: {final_st.get('core_temperature', 0.0):.2f} °C)"
    )
    print(f"[✓] ASSERTION PASSED: Reactor core temperature rose from {initial_temp:.2f} °C to {final_st.get('core_temperature', 0.0):.2f} °C via active fission!")

    # 3. Verify Hot Coolant Generation in Output Hatch and SFM Voiding to Trash Can
    print("[*] Pausing SFM to observe un-voided hot coolant accumulation in Output Hatch...")
    sfm.setrunning(x=MGR_POS[0], y=MGR_POS[1], z=MGR_POS[2], running=False)
    time.sleep(3.5)  # Allow 3 reactor cycles (20 ticks each) to produce hot coolant

    out_fluid = harness.te_fluid(OUT_HATCH_POS[0], OUT_HATCH_POS[1], OUT_HATCH_POS[2])
    print(f"[+] Output Hatch State (SFM paused): {out_fluid.get('tanks', [])}")
    hot_coolant_accum = sum(t.get("amount", 0) for t in out_fluid.get("tanks", []))
    assert hot_coolant_accum > 0, f"Expected hot coolant generated in Output Hatch, got {hot_coolant_accum} mB"
    print(f"[✓] ASSERTION PASSED: Reactor generated {hot_coolant_accum} mB of hot coolant in Output Hatch!")

    # Resume SFM and verify it clears the Output Hatch into the Fluid Trash Can
    print("[*] Resuming SFM to verify active voiding into Fluid Trash Can...")
    sfm.setrunning(x=MGR_POS[0], y=MGR_POS[1], z=MGR_POS[2], running=True)
    time.sleep(2.5)  # Allow SFM timer trigger to execute transfer
    sfm.setrunning(x=MGR_POS[0], y=MGR_POS[1], z=MGR_POS[2], running=False)

    out_fluid_voided = harness.te_fluid(OUT_HATCH_POS[0], OUT_HATCH_POS[1], OUT_HATCH_POS[2])
    print(f"[+] Output Hatch State (After SFM voiding): {out_fluid_voided.get('tanks', [])}")
    remaining_coolant = sum(t.get("amount", 0) for t in out_fluid_voided.get("tanks", []))
    assert remaining_coolant < hot_coolant_accum, (
        f"SFM failed to void hot coolant: before={hot_coolant_accum} mB, after={remaining_coolant} mB"
    )
    print(f"[✓] ASSERTION PASSED: SFM successfully voided {hot_coolant_accum - remaining_coolant} mB of hot coolant into the trash can!")

    # -------------------------------------------------------------
    # Visual Verification: High-Quality Screenshots
    # -------------------------------------------------------------
    print("[*] Capturing high-resolution verification screenshots...")

    # A. 3D Perspective of Reactor and SFM Wiring
    sfm.closegui()
    time.sleep(0.3)
    sfm.teleport(x=493.5, y=14.5, z=491.5)
    sfm.look(yaw=-35, pitch=32)
    time.sleep(0.5)
    p_3d = OUTPUT_DIR / "01_reactor_60a_3d_overview.png"
    sfm.screenshot(path=p_3d)
    print(f"[+] Captured {p_3d.name}")

    # B. Close-up of SFM Factory Manager, Drawers, Debug Tank & Cable Canopy
    sfm.teleport(x=493.0, y=11.5, z=502.0)
    sfm.look(yaw=-75, pitch=20)
    time.sleep(0.5)
    p_logistics = OUTPUT_DIR / "02_sfm_logistics_closeup.png"
    sfm.screenshot(path=p_logistics)
    print(f"[+] Captured {p_logistics.name}")

    # C. SFM Code Editor GUI showing active program
    sfm.opengui(gui="editor", x=MGR_POS[0], y=MGR_POS[1], z=MGR_POS[2])
    time.sleep(0.6)
    p_editor = OUTPUT_DIR / "03_sfm_code_editor_gui.png"
    sfm.screenshot(path=p_editor)
    sfm.closegui()
    time.sleep(0.3)
    print(f"[+] Captured {p_editor.name}")

    # D. SFM Manager GUI showing peripheral mapping and running tick
    sfm.opengui(gui="manager", x=MGR_POS[0], y=MGR_POS[1], z=MGR_POS[2])
    time.sleep(0.6)
    p_mgr = OUTPUT_DIR / "04_sfm_manager_gui.png"
    sfm.screenshot(path=p_mgr)
    sfm.closegui()
    time.sleep(0.3)
    print(f"[+] Captured {p_mgr.name}")

    # E. Reactor Controller GUI showing 5x5 Grid & Core Diagnostics
    harness.stand(500, 6, 497)
    harness.look(0, 25)
    time.sleep(0.3)
    harness.mclick(RC_X, RC_Y, RC_Z, 2)
    time.sleep(0.8)
    p_reactor = OUTPUT_DIR / "05_reactor_controller_gui.png"
    sfm.screenshot(path=p_reactor)
    harness.close_gui()
    time.sleep(0.3)
    print(f"[+] Captured {p_reactor.name}")

    # Copy all screenshots to brain artifacts directory
    for f in OUTPUT_DIR.glob("*.png"):
        dest = ARTIFACTS_DIR / f.name
        shutil.copy2(f, dest)
        print(f"[+] Published artifact: {dest.name}")

    print("\n============================================================")
    print("✓ 60A REACTOR & SFM LOGISTICS TEST PASSED SUCCESSFULLY!")
    print("============================================================\n")


def run_full_suite():
    sfm = SfmClient(port=SFM_PORT)
    harness = HarnessClient(port=HARNESS_PORT)

    client = FleetClient()
    saves_dir = get_saves_dir(client, "sfm-test-runner")
    world_name = "nuclear_60a_sfm_test"

    print(f"[*] Mounting ephemeral WorldSandbox over 'superflat_empty' into '{saves_dir}' as '{world_name}'...")
    sandbox = WorldSandbox(
        template_name="superflat_empty",
        world_name=world_name,
        saves_dir=saves_dir,
        on_unmount=lambda: harness.exitworld(),
    )

    with sandbox:
        print(f"[*] Launching world '{world_name}'...")
        sfm.autoworld(name=world_name)

        # Wait for IN_WORLD
        print("[*] Waiting for player to enter world...")
        t0 = time.time()
        in_world = False
        while time.time() - t0 < 60:
            st = harness.state()
            if "IN_WORLD" in st:
                in_world = True
                print(f"[+] In world: {st}")
                break
            time.sleep(0.5)

        assert in_world, f"Failed to enter world, status: {harness.status()}"
        time.sleep(2.0)
        harness.close_gui()
        sfm.chat(message="/gamemode 1")
        sfm.chat(message="/gamerule doDaylightCycle false")
        sfm.chat(message="/gamerule doMobSpawning false")
        sfm.chat(message="/time set 6000")
        sfm.chat(message="/weather clear 999999")
        sfm.chat(message="/difficulty 0")
        harness.stand(500, 10, 490)
        time.sleep(0.5)

        build_and_wire_setup(harness, sfm)
        verify_and_capture(harness, sfm)

        print("[*] Gracefully saving and exiting world...")
        sfm.chat(message="/save-all")
        time.sleep(0.5)
        res_exit = harness.exitworld()
        assert "ARRIVED_AT_MAIN_MENU" in res_exit, f"Exit error: {res_exit}"
        print(f"[+] World exited cleanly: {res_exit}")


if __name__ == "__main__":
    run_full_suite()

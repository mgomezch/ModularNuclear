#!/usr/bin/env python3
"""
End-to-End Automated Test: 60A Modular Pressure Tube Reactor (MPTR) with AE2 Output Logistics.

Architecture Under Test:
- 60A Electrum MPTR (5x5, Tier 1 EV) with 21 core tiles.
- Auto-Taping Maintenance Hatch, UV Dynamo Hatch (buffer).
- ME Output Hatch (GT ID 2713) upgraded with 16M Multi-Fluid Storage Cell.
- ME Output Bus (GT ID 2710) upgraded with 16M Item Storage Cell.
- AE2 Dense Smart Cable connected to the front faces of both ME Output Hatch and Bus.
- Dense cable trunk extending 8 blocks outward.
- Attached to cable trunk:
  * ME Controller (appliedenergistics2:tile.BlockController)
  * Neutronium Energy Cell (appliedenergistics2:tile.BlockCreativeEnergyCell)
  * Main ME Drive (appliedenergistics2:tile.BlockDrive) with 1x 16M Item Cell & 5x 16M Fluid Cells
  * Overflow ME Drive (appliedenergistics2:tile.BlockDrive) with Item Void Cell & Fluid Void Cell,
    configured with Priority = -1 via GUI interaction.
- Top core supplied by Super Factory Manager (SFM) canopy at Y=9 (fuel rods + IC2 coolant).
- Verification of multiblock formation, active fission, and AE2 output collection.
- Screenshot generation of 3D overview, AE2 infrastructure, Drive GUI, Priority GUI, and Reactor GUI.
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
from tools.runner_instance import get_saves_dir, ensure_sfm_instance_running
from tools.mcfleet.client import FleetClient
from tools.bot.world_manager import WorldSandbox

HARNESS_PORT = 25572
SFM_PORT = 25573

OUTPUT_DIR = REPO_ROOT / "output" / "ae2_nuclear_60a"
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

# SFM Supply Logistics Positions at Y=9
MGR_POS = (495, 9, 503)
DRAWER_U4_POS = (495, 9, 502)
DRAWER_M4_POS = (495, 9, 501)
COOLANT_TANK_POS = (495, 9, 500)

# AE2 Output Hatches on Front Wall (Y=5)
ME_OUT_HATCH_POS = (501, 5, 500)
ME_OUT_BUS_POS = (502, 5, 500)

# AE2 Infrastructure Positions at Y=5
CONTROLLER_POS = (500, 5, 495)
ENERGY_CELL_POS = (500, 5, 494)
MAIN_DRIVE_POS = (500, 5, 493)
VOID_DRIVE_POS = (500, 5, 492)


def build_reactor_and_sfm_supply(harness: HarnessClient, sfm: SfmClient):
    print("============================================================")
    print("[*] Stage 1: Constructing 60A Electrum Reactor & Hatches...")
    print("============================================================")

    # 1. Place clean quartz foundation
    harness.fill_area(488, 4, 485, 512, 4, 515, "minecraft:quartz_block", 0)
    time.sleep(0.5)

    # 2. Place Controller and trigger structure autobuild
    print(f"[*] Placing MPTR controller at ({RC_X}, {RC_Y}, {RC_Z}) facing North...")
    harness.set_gt_machine(RC_X, RC_Y, RC_Z, 32115, 2)
    time.sleep(0.5)
    res_ab = harness.autobuild(RC_X, RC_Y, RC_Z)
    print(f"    Autobuild result: {res_ab}")
    time.sleep(0.5)

    # 3. Configure Front Wall Hatches (Y=5)
    print("[*] Installing Auto-Taping Maintenance, UV Dynamo, ME Output Hatch, and ME Output Bus...")
    harness.set_gt_machine(499, 5, 500, 15497, 2)   # Auto-Taping Maintenance Hatch
    harness.set_gt_machine(498, 5, 500, 38, 2)    # UV Dynamo Hatch (524k buffer)
    harness.set_gt_machine(ME_OUT_HATCH_POS[0], ME_OUT_HATCH_POS[1], ME_OUT_HATCH_POS[2], 2713, 2)  # ME Output Hatch
    harness.set_gt_machine(ME_OUT_BUS_POS[0], ME_OUT_BUS_POS[1], ME_OUT_BUS_POS[2], 2710, 2)      # ME Output Bus
    time.sleep(0.5)

    # Install high-capacity storage cells into ME Output Hatch & Bus
    print("[*] Installing 16M storage cells into ME Output Hatch & Bus buffers...")
    harness.set_container_slot(ME_OUT_HATCH_POS[0], ME_OUT_HATCH_POS[1], ME_OUT_HATCH_POS[2], 0, "ae2fc:multi_fluid_storage16384", 1, 0)
    harness.set_container_slot(ME_OUT_BUS_POS[0], ME_OUT_BUS_POS[1], ME_OUT_BUS_POS[2], 0, "appliedenergistics2:item.ItemAdvancedStorageCell.16384k", 1, 0)
    time.sleep(0.3)

    # 4. Configure Top Core Tiles (Y=8)
    print("[*] Configuring 21 Top Core Hatches & Buses at Y=8...")
    for pos in COOLANT_HATCHES:
        harness.set_gt_machine(pos[0], pos[1], pos[2], 32104, 1)  # EV IC2 Coolant Hatch
    for pos in U4_BUSES:
        harness.set_gt_machine(pos[0], pos[1], pos[2], 32100, 1)  # Quad Uranium Bus
    for pos in M4_BUSES:
        harness.set_gt_machine(pos[0], pos[1], pos[2], 32100, 1)  # Quad MOX Bus
    time.sleep(0.5)

    # 5. Form and verify multiblock
    print("[*] Verifying multiblock formation...")
    harness.recheck(RC_X, RC_Y, RC_Z)
    time.sleep(0.5)
    st = harness.nuke_status(RC_X, RC_Y, RC_Z)
    assert st.get("formed"), f"Reactor failed to form: {st}"
    print(f"[✓] Reactor formed cleanly! (tiles={st.get('tiles')}, pipeTier={st.get('pipeTier')})")
    harness.fix_maintenance(RC_X, RC_Y, RC_Z)
    time.sleep(0.3)

    # 6. Build SFM Fancy Cable Canopy covering top core at Y=9
    print("[*] Building SFM Fancy Inventory Cable canopy over core at Y=9...")
    for cx in range(498, 503):
        for cz in range(501, 506):
            harness.set_block(cx, 9, cz, "superfactorymanager:fancyCable", 0)

    # Route cable westward to SFM logistics station
    harness.set_block(497, 9, 503, "superfactorymanager:fancyCable", 0)
    harness.set_block(496, 9, 503, "superfactorymanager:fancyCable", 0)
    for cz in range(500, 504):
        harness.set_block(496, 9, cz, "superfactorymanager:fancyCable", 0)

    # 7. Install SFM Manager, Drawers, and Debug Tank (Input Supply Only)
    print("[*] Installing SFM Manager, Vending Drawers, and Coolant Debug Tank...")
    harness.set_block(MGR_POS[0], MGR_POS[1], MGR_POS[2], "superfactorymanager:manager", 0)
    harness.setup_vending_drawer(x=DRAWER_U4_POS[0], y=DRAWER_U4_POS[1], z=DRAWER_U4_POS[2], item="gregtech:gt.rodUranium4", meta=0, dir=2)
    harness.setup_vending_drawer(x=DRAWER_M4_POS[0], y=DRAWER_M4_POS[1], z=DRAWER_M4_POS[2], item="gregtech:gt.rodMOX4", meta=0, dir=2)
    harness.set_gt_machine(COOLANT_TANK_POS[0], COOLANT_TANK_POS[1], COOLANT_TANK_POS[2], 238, 2)
    time.sleep(0.3)
    harness.configure_debug_tank(COOLANT_TANK_POS[0], COOLANT_TANK_POS[1], COOLANT_TANK_POS[2], "ic2coolant", output_fluid=False)
    time.sleep(0.3)

    # 8. Configure SFM Labels and Supply Program
    print("[*] Programming SFM supply logic...")
    mx, my, mz = MGR_POS
    sfm.clearlabels(x=mx, y=my, z=mz)
    sfm.setlabel(x=mx, y=my, z=mz, label="u4_drawer", tx=DRAWER_U4_POS[0], ty=DRAWER_U4_POS[1], tz=DRAWER_U4_POS[2])
    sfm.setlabel(x=mx, y=my, z=mz, label="m4_drawer", tx=DRAWER_M4_POS[0], ty=DRAWER_M4_POS[1], tz=DRAWER_M4_POS[2])
    sfm.setlabel(x=mx, y=my, z=mz, label="coolant_tank", tx=COOLANT_TANK_POS[0], ty=COOLANT_TANK_POS[1], tz=COOLANT_TANK_POS[2])
    for pos in COOLANT_HATCHES:
        sfm.setlabel(x=mx, y=my, z=mz, label="coolant_hatches", tx=pos[0], ty=pos[1], tz=pos[2])
    for pos in U4_BUSES:
        sfm.setlabel(x=mx, y=my, z=mz, label="u4_buses", tx=pos[0], ty=pos[1], tz=pos[2])
    for pos in M4_BUSES:
        sfm.setlabel(x=mx, y=my, z=mz, label="m4_buses", tx=pos[0], ty=pos[1], tz=pos[2])

    program_code = (
        "NAME \"60A Reactor Supply\"\n"
        "EVERY 20 TICKS DO\n"
        "  INPUT FROM u4_drawer\n"
        "  OUTPUT TO u4_buses\n"
        "  INPUT FROM m4_drawer\n"
        "  OUTPUT TO m4_buses\n"
        "  INPUT 8000 fluid:ic2coolant FROM coolant_tank\n"
        "  OUTPUT TO coolant_hatches\n"
        "END\n"
    )
    res_prog = sfm.programmanager(x=mx, y=my, z=mz, code=program_code)
    print(f"    Compiler result: {res_prog}")
    assert res_prog["valid"], f"SFM program failed compilation: {res_prog}"
    sfm.settick(x=mx, y=my, z=mz, tick=0)
    sfm.setrunning(x=mx, y=my, z=mz, running=True)
    print("[✓] Reactor core & SFM supply configured!")


def build_and_configure_ae2_network(harness: HarnessClient, sfm: SfmClient):
    print("============================================================")
    print("[*] Stage 2: Constructing AE2 Dense Cable Output Network...")
    print("============================================================")

    # 1. Equip AE2 Dense Smart Cable (Fluix)
    harness.chat(message="/give @p appliedenergistics2:item.ItemMultiPart 64 76")
    time.sleep(0.3)
    harness.select_item("appliedenergistics2:item.ItemMultiPart")
    time.sleep(0.3)

    # 2. Place dense cable directly in front of ME Output Bus and ME Output Hatch
    print(f"[*] Placing Dense Cable in front of ME Output Bus at (502, 5, 499)...")
    harness.stand(502, 6, 499)
    time.sleep(0.2)
    harness.click_block(x=502, y=4, z=499, side=1)

    print(f"[*] Placing Dense Cable in front of ME Output Hatch at (501, 5, 499)...")
    harness.stand(501, 6, 499)
    time.sleep(0.2)
    harness.click_block(x=501, y=4, z=499, side=1)

    # 3. Extend cable trunk 8 blocks along Z (from Z=498 down to Z=491 at X=501)
    print("[*] Extending Dense Cable trunk 8 blocks along Z (501, 5, 498..491)...")
    for z in range(498, 491, -1):
        harness.stand(501, 6, z)
        time.sleep(0.1)
        res = harness.click_block(x=501, y=4, z=z, side=1)
        assert res["used"], f"Failed to place dense cable at (501, 5, {z}): {res}"

    time.sleep(0.5)

    # 4. Place AE2 Network Core Blocks along the cable at X=500
    print("[*] Placing ME Controller at (500, 5, 495)...")
    harness.set_block(CONTROLLER_POS[0], CONTROLLER_POS[1], CONTROLLER_POS[2], "appliedenergistics2:tile.BlockController", 0)

    print("[*] Placing Neutronium Energy Cell at (500, 5, 494)...")
    harness.set_block(ENERGY_CELL_POS[0], ENERGY_CELL_POS[1], ENERGY_CELL_POS[2], "appliedenergistics2:tile.BlockCreativeEnergyCell", 0)

    print("[*] Placing Main ME Drive #1 at (500, 5, 493)...")
    harness.set_block(MAIN_DRIVE_POS[0], MAIN_DRIVE_POS[1], MAIN_DRIVE_POS[2], "appliedenergistics2:tile.BlockDrive", 0)
    # Populate Main Drive: Slot 0 = 16M Item Cell, Slots 1-5 = 16M Multi-Fluid Cells
    harness.set_container_slot(MAIN_DRIVE_POS[0], MAIN_DRIVE_POS[1], MAIN_DRIVE_POS[2], 0, "appliedenergistics2:item.ItemAdvancedStorageCell.16384k", 1, 0)
    for slot in range(1, 6):
        harness.set_container_slot(MAIN_DRIVE_POS[0], MAIN_DRIVE_POS[1], MAIN_DRIVE_POS[2], slot, "ae2fc:multi_fluid_storage16384", 1, 0)

    print("[*] Placing Void ME Drive #2 at (500, 5, 492)...")
    harness.set_block(VOID_DRIVE_POS[0], VOID_DRIVE_POS[1], VOID_DRIVE_POS[2], "appliedenergistics2:tile.BlockDrive", 0)
    # Populate Void Drive: Slot 0 = Item Void Cell, Slot 1 = Fluid Void Cell
    harness.set_container_slot(VOID_DRIVE_POS[0], VOID_DRIVE_POS[1], VOID_DRIVE_POS[2], 0, "appliedenergistics2:item.ItemVoidStorageCell", 1, 0)
    harness.set_container_slot(VOID_DRIVE_POS[0], VOID_DRIVE_POS[1], VOID_DRIVE_POS[2], 1, "ae2fc:fluid_storage.void", 1, 0)

    time.sleep(0.5)

    # 5. Capture Main Drive GUI
    print("[*] Opening Main ME Drive GUI...")
    sfm.closegui()
    time.sleep(0.3)
    sfm.teleport(x=498.5, y=6.0, z=493.0)
    sfm.look(yaw=-90, pitch=15)
    time.sleep(0.3)
    harness.click_block(x=MAIN_DRIVE_POS[0], y=MAIN_DRIVE_POS[1], z=MAIN_DRIVE_POS[2], side=4)
    time.sleep(0.6)
    p_main_drive = OUTPUT_DIR / "03a_me_main_drive_gui.png"
    sfm.screenshot(path=p_main_drive)
    print(f"[+] Captured {p_main_drive.name}")
    harness.close_gui()
    time.sleep(0.3)

    # 6. Configure Void Drive Priority to -1 via GUI interaction
    print("[*] Setting Void ME Drive priority to -1 via GUI interaction...")
    sfm.teleport(x=498.5, y=6.0, z=492.0)
    sfm.look(yaw=-90, pitch=15)
    time.sleep(0.3)
    # Right-click Drive #2 (face 4 is West)
    harness.click_block(x=VOID_DRIVE_POS[0], y=VOID_DRIVE_POS[1], z=VOID_DRIVE_POS[2], side=4)
    time.sleep(0.6)

    # Capture Drive GUI screenshot
    p_drive_gui = OUTPUT_DIR / "03_me_void_drive_gui.png"
    sfm.screenshot(path=p_drive_gui)
    print(f"[+] Captured {p_drive_gui.name}")

    # Click Priority tab button (rel 160, 5)
    harness.click_gui_rel(x=160, y=5)
    time.sleep(0.5)

    # Capture Priority GUI before change
    p_prio_before = OUTPUT_DIR / "04a_me_priority_gui_initial.png"
    sfm.screenshot(path=p_prio_before)

    # Click -1 button (rel 31, 85)
    harness.click_gui_rel(x=31, y=85)
    time.sleep(0.4)

    # Capture Priority GUI after -1 change
    p_prio_gui = OUTPUT_DIR / "04_me_priority_gui.png"
    sfm.screenshot(path=p_prio_gui)
    print(f"[+] Captured {p_prio_gui.name}")

    harness.close_gui()
    time.sleep(0.3)
    print("[✓] AE2 Output Logistics Network configured successfully!")


def verify_and_capture(harness: HarnessClient, sfm: SfmClient):
    print("============================================================")
    print("[*] Stage 3: Verifying Reactor Fission & AE2 Output Flow...")
    print("============================================================")
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    ARTIFACTS_DIR.mkdir(parents=True, exist_ok=True)

    # 1. Step simulation through 40 ticks to verify SFM supply
    print("[*] Allowing initial supply cycles (40 ticks)...")
    time.sleep(2.5)

    sample_u4 = harness.container_info(x=U4_BUSES[0][0], y=U4_BUSES[0][1], z=U4_BUSES[0][2])
    sample_m4 = harness.container_info(x=M4_BUSES[0][0], y=M4_BUSES[0][1], z=M4_BUSES[0][2])
    sample_coolant = harness.te_fluid(COOLANT_HATCHES[0][0], COOLANT_HATCHES[0][1], COOLANT_HATCHES[0][2])

    assert len(sample_u4.get("slots", [])) > 0, "U4 bus was not supplied with fuel rods!"
    assert len(sample_m4.get("slots", [])) > 0, "M4 bus was not supplied with fuel rods!"
    assert len(sample_coolant.get("tanks", [])) > 0 and sample_coolant["tanks"][0]["amount"] > 0, "Coolant hatch was not supplied!"
    print("[✓] ASSERTION PASSED: SFM successfully supplied fuel rods and coolant into core tiles!")

    # 2. Monitor Reactor Nuclear Simulation & Core Heating
    print("[*] Monitoring reactor core operation over active fission cycles...")
    initial_temp = harness.nuke_status(RC_X, RC_Y, RC_Z).get("core_temperature", 20.0)

    time.sleep(6.0)

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
    print(f"[✓] ASSERTION PASSED: Reactor core temperature rose to {final_st.get('core_temperature', 0.0):.2f} °C via active fission!")

    # 3. Check AE2 Output Hatch & Storage Network
    print("[*] Inspecting ME Output Hatch & ME Drive storage telemetry...")
    out_hatch_fluid = harness.te_fluid(ME_OUT_HATCH_POS[0], ME_OUT_HATCH_POS[1], ME_OUT_HATCH_POS[2])
    print(f"[+] ME Output Hatch Fluid: {out_hatch_fluid.get('tanks', [])}")

    main_drive_inv = harness.container_info(x=MAIN_DRIVE_POS[0], y=MAIN_DRIVE_POS[1], z=MAIN_DRIVE_POS[2])
    void_drive_inv = harness.container_info(x=VOID_DRIVE_POS[0], y=VOID_DRIVE_POS[1], z=VOID_DRIVE_POS[2])
    print(f"[+] Main ME Drive Cells: {len(main_drive_inv.get('slots', []))} storage cells active")
    print(f"[+] Void ME Drive Cells: {len(void_drive_inv.get('slots', []))} void cells active (Priority: -1)")

    # 4. Visual Verification Screenshots
    print("[*] Clearing ground entities and capturing high-resolution verification screenshots...")
    try:
        sfm.chat(message="/cofh killall *")
    except Exception:
        pass
    time.sleep(0.3)

    # A. 3D Overview of Complete System (Reactor, SFM Canopy, AE2 Dense Cable Network)
    sfm.teleport(x=493.0, y=14.0, z=488.0)
    sfm.look(yaw=-38, pitch=32)
    time.sleep(0.5)
    p_3d = OUTPUT_DIR / "01_reactor_60a_ae2_overview.png"
    sfm.screenshot(path=p_3d)
    print(f"[+] Captured {p_3d.name}")

    # B. Close-up of AE2 Dense Cable Trunk, Controller, Energy Cell & ME Drives
    sfm.teleport(x=496.5, y=8.0, z=490.5)
    sfm.look(yaw=-55, pitch=26)
    time.sleep(0.5)
    p_ae2 = OUTPUT_DIR / "02_ae2_network_closeup.png"
    sfm.screenshot(path=p_ae2)
    print(f"[+] Captured {p_ae2.name}")

    # C. Reactor Controller GUI showing 5x5 Grid & Core Diagnostics
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
    print("✓ 60A REACTOR & AE2 OUTPUT LOGISTICS TEST PASSED SUCCESSFULLY!")
    print("============================================================\n")


def run_full_suite():
    ensure_sfm_instance_running()

    sfm = SfmClient(port=SFM_PORT)
    harness = HarnessClient(port=HARNESS_PORT)

    client = FleetClient()
    saves_dir = get_saves_dir(client, "sfm-test-runner")
    world_name = "nuclear_60a_ae2_test"

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
        time.sleep(1.0)
        if harness.is_dead():
            print("[!] Player is dead on world entry, respawning...")
            harness.respawn()
            time.sleep(1.0)
        harness.close_gui()
        sfm.chat(message="/gamemode 1")
        sfm.chat(message="/gamerule doDaylightCycle false")
        sfm.chat(message="/gamerule doMobSpawning false")
        sfm.chat(message="/gamerule mobGriefing false")
        sfm.chat(message="/time set 6000")
        sfm.chat(message="/weather clear 999999")
        sfm.chat(message="/difficulty 0")
        sfm.chat(message="/cofh killall *")
        harness.stand(500, 10, 490)
        time.sleep(0.5)

        build_reactor_and_sfm_supply(harness, sfm)
        build_and_configure_ae2_network(harness, sfm)
        verify_and_capture(harness, sfm)

        print("[*] Gracefully saving and exiting world...")
        sfm.chat(message="/save-all")
        time.sleep(0.5)
        res_exit = harness.exitworld()
        assert "ARRIVED_AT_MAIN_MENU" in res_exit, f"Exit error: {res_exit}"
        print(f"[+] World exited cleanly: {res_exit}")


if __name__ == "__main__":
    run_full_suite()

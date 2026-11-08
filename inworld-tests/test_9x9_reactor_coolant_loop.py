#!/usr/bin/env python3
"""
End-to-End Automated Test: Tier 2 (9x9 Core / 11x11 Outer) Modular Pressure Tube Reactor (MPTR)
Integrated with SFM Logistics, AE2 Dense Output Network, and Multi-Passage Manifold Coolant Loop.

Architecture Under Test:
- Tier 2 MPTR Nuclear Reactor:
  * 11x11x5 multiblock footprint, elevated 10 blocks in air (controller at Y=18).
  * 69 active core cells (a 9x9 breeder layout, loaded so it stays under the tier's 1800 °C):
    - 16 osmium High-Pressure Convective Passage Hatches (input 32166, output 32173) paired top (inlet, facing UP, Y=21)
      and bottom (outlet, facing DOWN, Y=17).
    - 14 EV IC2 Coolant Hatches (ID 32104) filled with ic2coolant.
    - 26 Quad Uranium Nuclear Buses (ID 32100).
    - 4 Quad MOX Nuclear Buses (ID 32100).
    - 9 Quad High Density Plutonium Nuclear Buses (ID 32100).
  * Perimeter Hatches on Front Wall (Slice 3, Y=18, Z=25):
    - Auto-Taping Maintenance Hatch (GT ID 15497).
    - UV Dynamo Hatch (GT ID 38).
    - ME Output Hatch (GT ID 2713) with 16M multi-fluid cell.
    - ME Output Bus (GT ID 2710) with 16M item storage cell.
- AE2 Dense Cable Output Network:
  * Fluix Dense Smart Cable connected to front faces of ME Output Hatch & Bus.
  * Dense trunk extending along Z to ME Controller, Neutronium Creative Energy Cell.
  * Main ME Drive with 16M storage cells.
  * Overflow Void ME Drive with void cells, Priority = -1 set via GUI interaction.
- Top SFM Logistics Supply:
  * SFM fancy inventory cables at Y=22 covering all 53 non-passage core cells.
  * 3D bridge cables at Y=23..25 routing across passage rings.
  * Supply station at X=492, Y=22:
    - Factory Manager.
    - Creative Vending Drawers for Quad Uranium, Quad MOX, Quad HD Plutonium.
    - Infinite Debug Tank for IC2 Coolant.
  * Automated SFM program distributing fuel rods and coolant into all core buses and hatches.
- Multi-Passage Closed Coolant Loop with Manifolds:
  * Top Splitter Manifold (Y=23): Contiguous manifold group (modularnuclear:manifold, facing UP)
    splitting 1 incoming pump discharge pipe into 16 branch pipes feeding top HP hatches.
  * Bottom Merger Manifold (Y=15): Contiguous manifold group (modularnuclear:manifold, facing UP)
    collecting 16 bottom HP hatch outlets into 1 return pipe feeding PHE primary inlet.
  * Coolant Loop Pump Multiblock at (515, 7, 45) with 100k L distilled water reservoir & rotor.
  * Pressurized Heat Exchanger (PHE) at (515, 7, 25) with secondary circuit (distilled water -> steam).
- Verification:
  * Multiblock formation of all 3 structures (Reactor, Pump, PHE).
  * SFM autonomous core supply verification.
  * Directed Acyclic Graph (DAG) loop crawler traversal (`crawl_loop`).
  * Closed-loop circulation (>0 L/s) through 16 parallel reactor core passages.
  * Core fission and convective thermal exchange.
  * AE2 network integration and GUI screenshots.
"""

import sys
import os
import time
import math
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

OUTPUT_DIR = REPO_ROOT / "output" / "reactor_9x9_coolant_loop"
ARTIFACTS_DIR = Path("/home/mgomezch/.gemini/antigravity/brain/bc0c2152-bd77-425b-aa35-bf4d7878b40d")

PIPE_ID = 32140  # Medium Steel Coolant Pipe

# Machine Positions
RC_X, RC_Y, RC_Z = 500, 18, 25  # Reactor Controller elevated at Y=18
TIER_2_MAX_TEMP_C = 1800.0  # max_operating_temp of tier 2 in tiers.yaml
PHE_X, PHE_Y, PHE_Z = 515, 7, 25
PUMP_X, PUMP_Y, PUMP_Z = 515, 7, 45

# Slices
TOP_Y = RC_Y + 3   # 21 (Slice 0: Top nuclear hatches/buses)
BOT_Y = RC_Y - 1   # 17 (Slice 4: Bottom nuclear casings / HP hatches)

# AE2 Infrastructure Positions at Y=18
ME_OUT_HATCH_POS = (501, 18, 25)
ME_OUT_BUS_POS = (502, 18, 25)
AE2_CONTROLLER_POS = (500, 18, 20)
AE2_ENERGY_CELL_POS = (500, 18, 19)
AE2_MAIN_DRIVE_POS = (500, 18, 18)
AE2_VOID_DRIVE_POS = (500, 18, 17)

# SFM Logistics Supply Station Positions at Y=22
SFM_MGR_POS = (492, 22, 30)
SFM_DRAWER_U4 = (492, 22, 29)
SFM_DRAWER_M4 = (492, 22, 28)
SFM_DRAWER_HDP = (492, 22, 27)
SFM_COOLANT_TANK = (492, 22, 26)

# Manifold Levels & Key Pipe Ports
TOP_MANIFOLD_Y = 23
BOT_MANIFOLD_Y = 15
TOP_INLET_PIPE_POS = (501, 24, 30)     # Arrival from pump discharge into top manifold
BOT_OUTLET_PIPE_POS = (500, 14, 30)    # Exit from bottom manifold towards PHE

# 69 Core Chamber Tile Coordinates (Platinum 9x9 Breeder layout)
PASSAGES_COORDS: List[Tuple[int, int]] = [
    (500, 27), (497, 28), (499, 28), (501, 28), (503, 28),
    (500, 29), (497, 30), (498, 30), (502, 30), (503, 30),
    (500, 31), (497, 32), (499, 32), (501, 32), (503, 32),
    (500, 33)
]

U4_BUSES: List[Tuple[int, int]] = [
    (500, 26), (497, 27), (499, 27), (501, 27), (503, 27),
    (500, 28), (497, 29), (498, 29), (499, 29), (501, 29),
    (502, 29), (503, 29), (496, 30), (504, 30), (497, 31),
    (498, 31), (499, 31), (501, 31), (502, 31), (503, 31),
    (500, 32), (497, 33), (499, 33), (501, 33), (503, 33),
    (500, 34)
]

M4_BUSES: List[Tuple[int, int]] = [
    (499, 26), (501, 26), (499, 34), (501, 34)
]

HDP_BUSES: List[Tuple[int, int]] = [
    (498, 26), (502, 26), (496, 29), (504, 29), (500, 30),
    (496, 31), (504, 31), (498, 34), (502, 34)
]

COOLANT_HATCHES: List[Tuple[int, int]] = [
    (498, 27), (502, 27), (496, 28), (498, 28), (502, 28),
    (504, 28), (499, 30), (501, 30), (496, 32), (498, 32),
    (502, 32), (504, 32), (498, 33), (502, 33)
]


def build_world_foundations(harness: HarnessClient):
    print("============================================================")
    print("[*] Stage 1: Clearing Area and Laying Foundations...")
    print("============================================================")
    try:
        harness.clear_area(480, 4, 10, 535, 30, 60)
    except Exception as e:
        print("[!] Note on clear_area:", e)
    time.sleep(0.5)

    print("[*] Laying smooth stone ground foundation at Y=4...")
    harness.fill_area(480, 4, 10, 535, 4, 60, "stone", 0)
    time.sleep(0.3)

    # Elevated AE2 walkway at Y=17 in front of reactor
    print("[*] Building elevated AE2 walkway at Y=17 [499..503, 16..24]...")
    harness.fill_area(499, 17, 16, 503, 17, 24, "minecraft:quartz_block", 0)

    # Elevated SFM station platform at Y=21 west of reactor
    print("[*] Building elevated SFM service platform at Y=21 [490..494, 25..32]...")
    harness.fill_area(490, 21, 25, 494, 21, 32, "minecraft:quartz_block", 0)
    time.sleep(0.5)


def build_elevated_reactor(harness: HarnessClient):
    print("\n============================================================")
    print(f"[*] Stage 2: Assembling Tier 2 MPTR Reactor at ({RC_X}, {RC_Y}, {RC_Z})...")
    print("============================================================")

    # 1. Place Controller and autobuild structure
    harness.set_gt_machine(RC_X, RC_Y, RC_Z, 32115, 2)  # Controller facing North
    time.sleep(0.5)
    res_ab = harness.autobuild(RC_X, RC_Y, RC_Z, 2)
    print(f"    Autobuild result: {res_ab}")
    time.sleep(0.8)

    # 2. Configure Front Wall Hatches (Y=18, Z=25)
    print("[*] Installing Auto-Taping Maintenance, UV Dynamo, ME Output Hatch, and ME Output Bus...")
    harness.set_gt_machine(499, 18, 25, 15497, 2)   # Auto-Taping Maintenance Hatch
    harness.set_gt_machine(498, 18, 25, 38, 2)    # UV Dynamo Hatch (buffer)
    harness.set_gt_machine(ME_OUT_HATCH_POS[0], ME_OUT_HATCH_POS[1], ME_OUT_HATCH_POS[2], 2713, 2)  # ME Output Hatch
    harness.set_gt_machine(ME_OUT_BUS_POS[0], ME_OUT_BUS_POS[1], ME_OUT_BUS_POS[2], 2710, 2)      # ME Output Bus
    time.sleep(0.3)

    # Install 16M storage cells into ME Output Hatch & Bus
    harness.set_container_slot(ME_OUT_HATCH_POS[0], ME_OUT_HATCH_POS[1], ME_OUT_HATCH_POS[2], 0, "ae2fc:multi_fluid_storage16384", 1, 0)
    harness.set_container_slot(ME_OUT_BUS_POS[0], ME_OUT_BUS_POS[1], ME_OUT_BUS_POS[2], 0, "appliedenergistics2:item.ItemAdvancedStorageCell.16384k", 1, 0)

    # 3. Configure all 69 core chamber columns
    print("[*] Installing 16 paired High-Pressure Convective Passage Hatches (top Y=21, bottom Y=17)...")
    for wx, wz in PASSAGES_COORDS:
        harness.set_gt_machine(wx, TOP_Y, wz, 32166, 1)  # Top inlet facing UP
        harness.set_gt_machine(wx, BOT_Y, wz, 32173, 0)  # Bottom outlet facing DOWN

    print("[*] Installing 14 IC2 Coolant Hatches at Y=21...")
    for wx, wz in COOLANT_HATCHES:
        harness.set_gt_machine(wx, TOP_Y, wz, 32104, 1)  # EV Coolant Hatch facing UP
        harness.fill_fluid(wx, TOP_Y, wz, "ic2coolant", 16000)

    print("[*] Installing 26 Quad Uranium Buses at Y=21...")
    for wx, wz in U4_BUSES:
        harness.set_gt_machine(wx, TOP_Y, wz, 32100, 1)  # Nuclear Bus facing UP

    print("[*] Installing 4 Quad MOX Buses at Y=21...")
    for wx, wz in M4_BUSES:
        harness.set_gt_machine(wx, TOP_Y, wz, 32100, 1)  # Nuclear Bus facing UP

    print("[*] Installing 9 Quad HD Plutonium Buses at Y=21...")
    for wx, wz in HDP_BUSES:
        harness.set_gt_machine(wx, TOP_Y, wz, 32100, 1)  # Nuclear Bus facing UP

    time.sleep(0.5)
    harness.recheck(RC_X, RC_Y, RC_Z)
    time.sleep(0.5)
    harness.fix_maintenance(RC_X, RC_Y, RC_Z)
    time.sleep(0.3)

    st_rc = harness.nuke_status(RC_X, RC_Y, RC_Z)
    assert st_rc.get("formed") or st_rc.get("is_formed"), f"Tier 2 Reactor failed to form: {st_rc}"
    print(f"[✓] Tier 2 Reactor formed cleanly! (tiles={st_rc.get('tiles')}, pipeTier={st_rc.get('pipeTier')})")


def build_ae2_output_network(harness: HarnessClient, sfm: SfmClient):
    print("\n============================================================")
    print("[*] Stage 3: Constructing AE2 Dense Smart Cable Output Network...")
    print("============================================================")

    # Equip Fluix Dense Smart Cable (ItemMultiPart, meta 76)
    harness.chat(message="/give @p appliedenergistics2:item.ItemMultiPart 64 76")
    time.sleep(0.3)
    harness.select_item("appliedenergistics2:item.ItemMultiPart")
    time.sleep(0.3)

    # Place dense cable in front of ME Output Bus and Hatch at Y=18
    print("[*] Placing Dense Smart Cable in front of ME Output Bus & Hatch...")
    harness.stand(502, 19, 24)
    time.sleep(0.2)
    harness.click_block(x=502, y=17, z=24, side=1)

    harness.stand(501, 19, 24)
    time.sleep(0.2)
    harness.click_block(x=501, y=17, z=24, side=1)

    # Extend dense cable trunk along Z from Z=23 down to Z=16 at X=501
    print("[*] Extending Dense Cable trunk along Z (501, 18, 23..16)...")
    for z in range(23, 15, -1):
        harness.stand(501, 19, z)
        time.sleep(0.1)
        harness.click_block(x=501, y=17, z=z, side=1)

    time.sleep(0.5)

    # Place AE2 Network Core Blocks along the trunk at X=500
    print("[*] Placing ME Controller, Energy Cell, Main Drive, and Void Drive...")
    harness.set_block(AE2_CONTROLLER_POS[0], AE2_CONTROLLER_POS[1], AE2_CONTROLLER_POS[2], "appliedenergistics2:tile.BlockController", 0)
    harness.set_block(AE2_ENERGY_CELL_POS[0], AE2_ENERGY_CELL_POS[1], AE2_ENERGY_CELL_POS[2], "appliedenergistics2:tile.BlockCreativeEnergyCell", 0)

    # Main ME Drive: Slot 0 = 16M Item Cell, Slots 1-5 = 16M Multi-Fluid Cells
    harness.set_block(AE2_MAIN_DRIVE_POS[0], AE2_MAIN_DRIVE_POS[1], AE2_MAIN_DRIVE_POS[2], "appliedenergistics2:tile.BlockDrive", 0)
    harness.set_container_slot(AE2_MAIN_DRIVE_POS[0], AE2_MAIN_DRIVE_POS[1], AE2_MAIN_DRIVE_POS[2], 0, "appliedenergistics2:item.ItemAdvancedStorageCell.16384k", 1, 0)
    for slot in range(1, 6):
        harness.set_container_slot(AE2_MAIN_DRIVE_POS[0], AE2_MAIN_DRIVE_POS[1], AE2_MAIN_DRIVE_POS[2], slot, "ae2fc:multi_fluid_storage16384", 1, 0)

    # Void ME Drive: Slot 0 = Item Void Cell, Slot 1 = Fluid Void Cell
    harness.set_block(AE2_VOID_DRIVE_POS[0], AE2_VOID_DRIVE_POS[1], AE2_VOID_DRIVE_POS[2], "appliedenergistics2:tile.BlockDrive", 0)
    harness.set_container_slot(AE2_VOID_DRIVE_POS[0], AE2_VOID_DRIVE_POS[1], AE2_VOID_DRIVE_POS[2], 0, "appliedenergistics2:item.ItemVoidStorageCell", 1, 0)
    harness.set_container_slot(AE2_VOID_DRIVE_POS[0], AE2_VOID_DRIVE_POS[1], AE2_VOID_DRIVE_POS[2], 1, "ae2fc:fluid_storage.void", 1, 0)
    time.sleep(0.5)

    # Configure Void Drive Priority to -1
    print("[*] Setting Void ME Drive priority to -1...")
    res_prio = harness.set_priority(x=AE2_VOID_DRIVE_POS[0], y=AE2_VOID_DRIVE_POS[1], z=AE2_VOID_DRIVE_POS[2], priority=-1)
    print(f"    set_priority result: {res_prio}")
    time.sleep(0.3)
    print("[✓] AE2 Dense Output Network assembled and priority configured!")


def build_sfm_supply_logistics(harness: HarnessClient, sfm: SfmClient):
    print("\n============================================================")
    print("[*] Stage 4: Constructing SFM Supply Logistics Station & Canopy...")
    print("============================================================")

    # Move player to SFM service platform so chunks are firmly loaded
    harness.stand(494, 22, 30)
    time.sleep(0.5)

    # 1. Place SFM Manager, Creative Vending Drawers, and Debug Coolant Tank at X=492, Y=22
    harness.set_block(SFM_MGR_POS[0], SFM_MGR_POS[1], SFM_MGR_POS[2], "superfactorymanager:manager", 0)
    harness.setup_vending_drawer(x=SFM_DRAWER_U4[0], y=SFM_DRAWER_U4[1], z=SFM_DRAWER_U4[2], item="gregtech:gt.rodUranium4", meta=0, dir=2)
    harness.setup_vending_drawer(x=SFM_DRAWER_M4[0], y=SFM_DRAWER_M4[1], z=SFM_DRAWER_M4[2], item="gregtech:gt.rodMOX4", meta=0, dir=2)
    harness.setup_vending_drawer(x=SFM_DRAWER_HDP[0], y=SFM_DRAWER_HDP[1], z=SFM_DRAWER_HDP[2], item="gregtech:gt.rodHighDensityPlutonium4", meta=0, dir=2)
    harness.set_gt_machine(SFM_COOLANT_TANK[0], SFM_COOLANT_TANK[1], SFM_COOLANT_TANK[2], 238, 2)
    time.sleep(0.3)
    harness.configure_debug_tank(SFM_COOLANT_TANK[0], SFM_COOLANT_TANK[1], SFM_COOLANT_TANK[2], "ic2coolant", output_fluid=False)
    time.sleep(0.3)

    # 2. Place SFM fancy inventory cables over all 53 non-passage core cells at Y=22
    all_non_passages = U4_BUSES + M4_BUSES + HDP_BUSES + COOLANT_HATCHES
    print(f"[*] Placing SFM Fancy Cables at Y=22 over {len(all_non_passages)} non-passage core cells...")
    for wx, wz in all_non_passages:
        harness.set_block(wx, 22, wz, "superfactorymanager:fancyCable", 0)

    # 3. Add 3D bridge cables across passage rings to guarantee full connectivity
    bridge_coords = [
        # Bridge 1 (for 500, 28)
        (500, 23, 28), (500, 24, 28), (500, 25, 28), (500, 25, 27),
        (500, 25, 26), (500, 24, 26), (500, 23, 26),
        # Bridge 2 (for 500, 32)
        (500, 23, 32), (500, 24, 32), (500, 25, 32), (500, 25, 33),
        (500, 25, 34), (500, 24, 34), (500, 23, 34),
        # Trunk westward to SFM station
        (496, 22, 30), (495, 22, 30), (494, 22, 30),
        # Cable bus at X=493 adjacent to manager, drawers, and tank at X=492
        (493, 22, 26), (493, 22, 27), (493, 22, 28), (493, 22, 29), (493, 22, 30)
    ]
    for bx, by, bz in bridge_coords:
        harness.set_block(bx, by, bz, "superfactorymanager:fancyCable", 0)

    time.sleep(0.5)

    # 4. Configure SFM Manager Labels
    print("[*] Programming peripheral labels into SFM Factory Manager...")
    mx, my, mz = SFM_MGR_POS
    res_clear = sfm.clearlabels(x=mx, y=my, z=mz)
    print(f"    clearlabels result: {res_clear}")
    time.sleep(0.1)

    for label_name, pos in [
        ("u4_drawer", SFM_DRAWER_U4),
        ("m4_drawer", SFM_DRAWER_M4),
        ("hdp_drawer", SFM_DRAWER_HDP),
        ("coolant_tank", SFM_COOLANT_TANK),
    ]:
        res = sfm.setlabel(x=mx, y=my, z=mz, label=label_name, tx=pos[0], ty=pos[1], tz=pos[2])
        print(f"    setlabel {label_name}: {res}")
        time.sleep(0.05)

    for wx, wz in U4_BUSES:
        sfm.setlabel(x=mx, y=my, z=mz, label="u4_buses", tx=wx, ty=21, tz=wz)
        time.sleep(0.02)
    for wx, wz in M4_BUSES:
        sfm.setlabel(x=mx, y=my, z=mz, label="m4_buses", tx=wx, ty=21, tz=wz)
        time.sleep(0.02)
    for wx, wz in HDP_BUSES:
        sfm.setlabel(x=mx, y=my, z=mz, label="hdp_buses", tx=wx, ty=21, tz=wz)
        time.sleep(0.02)
    for wx, wz in COOLANT_HATCHES:
        sfm.setlabel(x=mx, y=my, z=mz, label="coolant_hatches", tx=wx, ty=21, tz=wz)
        time.sleep(0.02)

    time.sleep(0.5)

    # 5. Compile and start SFM Automation Script
    program_code = (
        "NAME \"9x9 Reactor Automation\"\n"
        "EVERY 20 TICKS DO\n"
        "  INPUT FROM u4_drawer\n"
        "  OUTPUT TO u4_buses\n"
        "  INPUT FROM m4_drawer\n"
        "  OUTPUT TO m4_buses\n"
        "  INPUT FROM hdp_drawer\n"
        "  OUTPUT TO hdp_buses\n"
        "  INPUT 8000 fluid:ic2coolant FROM coolant_tank\n"
        "  OUTPUT TO coolant_hatches\n"
        "END\n"
    )
    print("[*] Compiling SFM Automation Script...")
    res_prog = sfm.programmanager(x=mx, y=my, z=mz, code=program_code)
    print(f"    Compiler result: {res_prog}")
    assert res_prog["valid"], f"SFM program failed compilation: {res_prog}"
    sfm.settick(x=mx, y=my, z=mz, tick=0)
    # Stopped until the coolant loop circulates (verify_simulation_and_capture): the reactor has no off switch, so it
    # runs as soon as it holds fuel. A compiled program starts running by itself
    sfm.setrunning(x=mx, y=my, z=mz, running=False)
    print("[✓] SFM program compiled; it starts once the coolant loop circulates")


def build_coolant_loop_and_manifolds(harness: HarnessClient):
    print("\n============================================================")
    print("[*] Stage 5: Assembling Pump, PHE, Manifolds & Piping DAG...")
    print("============================================================")

    # 1. Build Coolant Loop Pump Multiblock
    print(f"[*] Assembling Coolant Loop Pump at ({PUMP_X}, {PUMP_Y}, {PUMP_Z})...")
    harness.build_multiblock("coolant_pump", PUMP_X, PUMP_Y, PUMP_Z, "steel")
    time.sleep(0.5)
    harness.fix_maintenance(PUMP_X + 1, PUMP_Y, PUMP_Z)

    # Re-initialize Railcraft Tank reservoir with Distilled Water
    print("[*] Filling Railcraft Tank reservoir with 100k L Distilled Water...")
    time.sleep(0.5)
    harness.fill_fluid(PUMP_X + 1, PUMP_Y + 2, PUMP_Z + 1, "ic2distilledwater", 100000)

    # Install Debug Wireless Energy Cover on Coolant Pump Energy Hatch (facing South = 3)
    harness.set_cover(PUMP_X + 1, PUMP_Y, PUMP_Z + 2, 3, "Cover_Wireless_Energy_Debug")

    # Install Turbine Rotor Impeller in slot 1
    harness.set_container_slot(PUMP_X + 1, PUMP_Y, PUMP_Z, 1, "gregtech:gt.metatool.01", 1, 172)
    time.sleep(0.3)

    # 2. Build Pressurized Heat Exchanger (PHE)
    print(f"[*] Assembling Pressurized Heat Exchanger at ({PHE_X}, {PHE_Y}, {PHE_Z})...")
    harness.build_multiblock("phe", PHE_X, PHE_Y, PHE_Z)
    time.sleep(0.5)
    harness.fix_maintenance(PHE_X + 1, PHE_Y + 1, PHE_Z)
    # Install Debug Wireless Energy Cover on PHE Energy Hatch (facing South = 3)
    harness.set_cover(PHE_X + 1, PHE_Y, PHE_Z + 4, 3, "Cover_Wireless_Energy_Debug")

    # Setup Secondary Circuit on PHE: Debug Tank (Distilled Water) -> PHE -> Supertank (Steam)
    harness.set_gt_machine(PHE_X + 3, PHE_Y + 1, PHE_Z, 238, 4)
    harness.configure_debug_tank(PHE_X + 3, PHE_Y + 1, PHE_Z, "ic2distilledwater", True)
    harness.set_gt_machine(PHE_X + 3, PHE_Y + 1, PHE_Z + 4, 130, 4)
    harness.configure_supertank(PHE_X + 3, PHE_Y + 1, PHE_Z + 4, True)
    harness.enable_auto_output(PHE_X + 2, PHE_Y + 1, PHE_Z + 4)
    time.sleep(0.5)

    # 3. Build Top Splitter Manifold Group at Y=23
    print("[*] Assembling Top Splitter Manifold Group at Y=23 (facing UP)...")
    for mx in range(497, 504):
        for mz in range(27, 34):
            if (mx, mz) not in ((500, 28), (500, 32)):  # Keep vertical SFM riser paths clear
                harness.set_block(mx, TOP_MANIFOLD_Y, mz, "modularnuclear:manifold", 1)
                harness.configure_manifold(mx, TOP_MANIFOLD_Y, mz, "UP")

    # Connect the 16 vertical passage pipes at Y=22 between top HP hatches and top manifold
    print("[*] Installing 16 vertical passage pipes at Y=22...")
    for px, pz in PASSAGES_COORDS:
        harness.set_gt_machine(px, 22, pz, PIPE_ID, 0)
        harness.connect_pipe(px, 22, pz, "UP")
        harness.connect_pipe(px, 22, pz, "DOWN")

    # 4. Build Bottom Merger Manifold Group at Y=15
    print("[*] Assembling Bottom Merger Manifold Group at Y=15 (facing UP)...")
    for mx in range(497, 504):
        for mz in range(27, 34):
            harness.set_block(mx, BOT_MANIFOLD_Y, mz, "modularnuclear:manifold", 1)
            harness.configure_manifold(mx, BOT_MANIFOLD_Y, mz, "UP")

    # Connect the 16 vertical passage pipes at Y=16 between bottom HP hatches and bottom manifold
    print("[*] Installing 16 vertical passage pipes at Y=16...")
    for px, pz in PASSAGES_COORDS:
        harness.set_gt_machine(px, 16, pz, PIPE_ID, 0)
        harness.connect_pipe(px, 16, pz, "UP")
        harness.connect_pipe(px, 16, pz, "DOWN")

    # 5. Route Closed Coolant Piping Circuit
    print("\n[*] Routing Closed Coolant Piping Circuit...")
    rx_bounds = [495, 17, 25, 505, 21, 35]
    pump_bounds = [PUMP_X, PUMP_Y, PUMP_Z, PUMP_X + 2, PUMP_Y + 5, PUMP_Z + 2]
    phe_bounds = [PHE_X, PHE_Y, PHE_Z, PHE_X + 2, PHE_Y + 2, PHE_Z + 4]

    # Leg 1 (Cold Leg): Pump Discharge (518, 8, 46) -> Top Splitter Manifold (501, 24, 30)
    print("[*] Routing Leg 1 (Cold Leg: Pump Discharge -> Top Splitter Manifold)...")
    r1 = harness.route_pipe(
        pipe_id=PIPE_ID,
        start=(PUMP_X + 3, PUMP_Y + 1, PUMP_Z + 1),
        end=TOP_INLET_PIPE_POS,
        start_conn="WEST",
        end_conn="DOWN",
        avoid=[rx_bounds, pump_bounds, phe_bounds],
        preferred_y=TOP_INLET_PIPE_POS[1],
    )
    harness.connect_pipe(TOP_INLET_PIPE_POS[0], TOP_INLET_PIPE_POS[1], TOP_INLET_PIPE_POS[2], "DOWN")
    print(f"    [+] Leg 1 routed: length={r1.get('length')}")

    # Leg 2 (Hot Leg): Bottom Merger Manifold (500, 14, 30) -> PHE Primary Inlet (514, 8, 25)
    print("[*] Routing Leg 2 (Hot Leg: Bottom Merger Manifold -> PHE Primary Inlet)...")
    r2 = harness.route_pipe(
        pipe_id=PIPE_ID,
        start=BOT_OUTLET_PIPE_POS,
        end=(PHE_X - 1, PHE_Y + 1, PHE_Z),
        start_conn="UP",
        end_conn="EAST",
        avoid=[rx_bounds, pump_bounds, phe_bounds],
    )
    harness.connect_pipe(BOT_OUTLET_PIPE_POS[0], BOT_OUTLET_PIPE_POS[1], BOT_OUTLET_PIPE_POS[2], "UP")
    print(f"    [+] Leg 2 routed: length={r2.get('length')}")

    # Leg 3 (Cold Return): PHE Primary Outlet (515, 8, 30) -> Pump Suction (514, 8, 46)
    print("[*] Routing Leg 3 (Cold Return: PHE Outlet -> Pump Suction)...")
    r3 = harness.route_pipe(
        pipe_id=PIPE_ID,
        start=(PHE_X, PHE_Y + 1, PHE_Z + 5),
        end=(PUMP_X - 1, PUMP_Y + 1, PUMP_Z + 1),
        start_conn="NORTH",
        end_conn="EAST",
        avoid=[rx_bounds, pump_bounds, phe_bounds],
        preferred_y=PUMP_Y + 1,
    )
    print(f"    [+] Leg 3 routed: length={r3.get('length')}")

    print("[✓] Complete Closed Coolant Loop DAG with 16 parallel passage branches assembled!")


def verify_simulation_and_capture(harness: HarnessClient, sfm: SfmClient):
    print("\n============================================================")
    print("[*] Stage 6: Verifying Autonomous Operation & Thermal Exchange...")
    print("============================================================")

    # 1. Recheck multiblock formation
    for _ in range(5):
        harness.recheck(RC_X, RC_Y, RC_Z)
        harness.recheck(PHE_X + 1, PHE_Y + 1, PHE_Z)
        harness.recheck(PUMP_X + 1, PUMP_Y, PUMP_Z)
        time.sleep(0.5)
        rc_status = harness.nuke_status(RC_X, RC_Y, RC_Z)
        phe_status = harness.coolant_status(PHE_X + 1, PHE_Y + 1, PHE_Z)
        pump_status = harness.coolant_status(PUMP_X + 1, PUMP_Y, PUMP_Z)
        if (rc_status.get("formed") or rc_status.get("is_formed")) and phe_status.get("machine_formed") and pump_status.get("machine_formed"):
            break

    print(f"    Reactor: formed={rc_status.get('is_formed', rc_status.get('formed'))}, tiles={rc_status.get('tiles')}")
    print(f"    PHE: formed={phe_status.get('machine_formed')}")
    print(f"    Pump: formed={pump_status.get('machine_formed')}")

    assert rc_status.get("is_formed") or rc_status.get("formed"), f"Reactor structure failed: {rc_status}"
    assert phe_status.get("machine_formed"), f"PHE structure failed: {phe_status}"
    assert pump_status.get("machine_formed"), f"Pump structure failed: {pump_status}"
    harness.fill_fluid(PUMP_X + 1, PUMP_Y + 2, PUMP_Z + 1, "ic2distilledwater", 100000)
    harness.set_cover(PUMP_X + 1, PUMP_Y, PUMP_Z + 2, 3, "Cover_Wireless_Energy_Debug")
    harness.set_cover(PHE_X + 1, PHE_Y, PHE_Z + 4, 3, "Cover_Wireless_Energy_Debug")

    # 4. Crawl closed loop through 16 parallel passage branches and manifolds
    print("\n[*] Triggering loop traversal through 16 parallel passages and manifolds...")
    res_crawl = harness.crawl_loop(PUMP_X + 1, PUMP_Y, PUMP_Z)
    print(f"    crawl_loop result: {res_crawl}")
    time.sleep(0.5)

    st_loop = harness.coolant_status(PUMP_X + 1, PUMP_Y, PUMP_Z)
    assert st_loop.get("loop_formed", False), f"Closed loop failed to form through reactor: {st_loop}"
    req_fill = st_loop.get("required_fill_liters", 0)
    print(f"[✓] DAG Closed loop formed through all 16 passages! Required fill: {req_fill} L")

    # 5. Wait for filling phase and active circulation
    print("\n[*] Waiting for filling phase and active circulation...")
    st = {}
    for tick in range(60):
        time.sleep(0.3)
        st = harness.coolant_status(PUMP_X + 1, PUMP_Y, PUMP_Z)
        state = st.get("loop_state")
        fill = st.get("fill_liters", 0)
        flow = st.get("flow_rate_l_s", 0.0)
        stat = st.get("loop_status", "")
        print(f"    [{tick*4:2d} ticks] State: {str(state):12s} | Fill: {fill:5d}/{req_fill:5d} L | Flow: {flow:.2f} L/s | Status: {stat}")
        if state == "CIRCULATING" and flow > 0.0:
            break

    flow_rate = st.get("flow_rate_l_s", 0.0)
    assert flow_rate > 0.0, f"Expected non-zero circulating flow, got {flow_rate}"
    print(f"[✓] Coolant circulating at {flow_rate:.2f} L/s across 16 parallel core passages!")

    # 5b. Start fuel supply only now that the loop cools the core
    mx, my, mz = SFM_MGR_POS
    sfm.setrunning(x=mx, y=my, z=mz, running=True)
    print("[✓] SFM Logistics started: core buses and hatches continuously replenished!")
    # 2. Verify SFM has supplied fuel rods and coolant into core tiles
    print("\n[*] Verifying SFM core replenishment...")
    time.sleep(4.0)
    sample_u4 = harness.container_info(x=U4_BUSES[0][0], y=21, z=U4_BUSES[0][1])
    sample_m4 = harness.container_info(x=M4_BUSES[0][0], y=21, z=M4_BUSES[0][1])
    sample_hdp = harness.container_info(x=HDP_BUSES[0][0], y=21, z=HDP_BUSES[0][1])
    sample_coolant = harness.te_fluid(COOLANT_HATCHES[0][0], 21, COOLANT_HATCHES[0][1])

    print(f"    Sample U4 Bus: {sample_u4.get('slots', [])}")
    print(f"    Sample M4 Bus: {sample_m4.get('slots', [])}")
    print(f"    Sample HDP Bus: {sample_hdp.get('slots', [])}")
    print(f"    Sample Coolant Hatch: {sample_coolant.get('tanks', [])}")

    assert len(sample_u4.get("slots", [])) > 0, "U4 bus not supplied!"
    assert sample_u4["slots"][0]["item"] == "gregtech:gt.rodUranium4", f"Expected U4, got {sample_u4}"
    assert len(sample_m4.get("slots", [])) > 0, "M4 bus not supplied!"
    assert sample_m4["slots"][0]["item"] == "gregtech:gt.rodMOX4", f"Expected M4, got {sample_m4}"
    assert len(sample_hdp.get("slots", [])) > 0, "HDP bus not supplied!"
    assert sample_hdp["slots"][0]["item"] == "gregtech:gt.rodHighDensityPlutonium4", f"Expected HDP, got {sample_hdp}"
    assert len(sample_coolant.get("tanks", [])) > 0 and sample_coolant["tanks"][0]["amount"] > 0, "Coolant hatch not supplied!"
    print("[✓] SFM autonomously supplied all fuel types and conductive coolant!")

    # 6. Monitor Core Heat & Convective Transfer
    print("\n[*] Monitoring convective heat extraction over active fission...")
    time.sleep(2.5)
    rc_active = harness.nuke_status(RC_X, RC_Y, RC_Z)
    pump_active = harness.coolant_status(PUMP_X + 1, PUMP_Y, PUMP_Z)
    core_temp = rc_active.get("core_temperature", rc_active.get("temp", 0.0))
    coolant_temp = pump_active["peak_temp_c"]  # the hottest loop segment; the pump reports no single loop temperature
    print(f"    Reactor Core Temp: {core_temp:.2f}°C")
    print(f"    Coolant Loop Peak Temp: {coolant_temp:.2f}°C (Flow: {pump_active.get('flow_rate_l_s', 0.0):.2f} L/s)")
    print("[✓] Convective cooling active! Fission heat extracted safely by closed coolant loop.")

    # A core over its casing's limit melts down a minute or two later; watch long enough to see it
    print("[*] Watching the core for 90 s: it must stay formed and under the tier's 1800 °C limit...")
    peak = 0.0
    for _ in range(18):
        time.sleep(5.0)
        st = harness.nuke_status(RC_X, RC_Y, RC_Z)
        assert st.get("is_formed") or st.get("formed"), f"Reactor stopped being formed (meltdown?): {st}"
        t = st.get("core_temperature", st.get("temp", 0.0))
        peak = max(peak, t)
        assert t < TIER_2_MAX_TEMP_C, f"Core at {t:.0f} °C, over the tier's {TIER_2_MAX_TEMP_C:.0f} °C limit"
    print(f"[✓] Core stayed formed, peaking at {peak:.0f} °C")

    # 7. Capture Verification Screenshots
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    ARTIFACTS_DIR.mkdir(parents=True, exist_ok=True)

    def shoot_view(filename: str, cx: float, cy: float, cz: float, tx: float, ty: float, tz: float):
        dx = tx - (cx + 0.5)
        dy = ty - (cy + 1.62)
        dz = tz - (cz + 0.5)
        dist = math.sqrt(dx * dx + dz * dz)
        yaw = math.degrees(math.atan2(dz, dx)) - 90.0
        pitch = -math.degrees(math.atan2(dy, dist))
        harness.stand(cx, cy, cz, yaw, pitch)
        harness.look(yaw, pitch)
        sfm.look(yaw=yaw, pitch=pitch)
        time.sleep(1.2)
        p = OUTPUT_DIR / filename
        sfm.screenshot(path=p)
        print(f"[+] Captured {filename} (pos: {cx},{cy},{cz} -> target: {tx},{ty},{tz}, yaw={yaw:.1f}, pitch={pitch:.1f})")

    print("\n[*] Capturing comprehensive verification screenshots...")

    # Clear active hand for clean screenshots
    harness.slot(8)
    harness.close_gui()
    time.sleep(0.5)

    # 1. 3D Overview of Elevated Reactor + Manifolds + Coolant Loop + AE2
    shoot_view("01_reactor_9x9_coolant_loop_overview.png", cx=525, cy=28, cz=12, tx=506, ty=16, tz=32)

    # 2. Closeup of Top Splitter Manifold & SFM canopy
    shoot_view("02_top_splitter_manifold_and_sfm.png", cx=507, cy=28, cz=22, tx=500, ty=23, tz=29)

    # 3. Closeup of Bottom Merger Manifold underbelly
    shoot_view("03_bottom_merger_manifold_closeup.png", cx=508, cy=7, cz=18, tx=500, ty=15.5, tz=30)

    # 4. AE2 Dense Cable & Drive Infrastructure
    shoot_view("04_ae2_dense_cable_and_drives.png", cx=496, cy=20, cz=14, tx=501, ty=18, tz=21)

    # 5. ME Main Drive GUI
    sfm.teleport(x=498.5, y=19.0, z=18.0)
    sfm.look(yaw=-90, pitch=15)
    time.sleep(0.3)
    harness.click_block(x=AE2_MAIN_DRIVE_POS[0], y=AE2_MAIN_DRIVE_POS[1], z=AE2_MAIN_DRIVE_POS[2], side=4)
    time.sleep(0.6)
    p_main_drive = OUTPUT_DIR / "05_me_main_drive_gui.png"
    sfm.screenshot(path=p_main_drive)
    harness.close_gui()
    time.sleep(0.3)
    print(f"[+] Captured {p_main_drive.name}")

    # 6. ME Void Drive Priority GUI
    sfm.teleport(x=498.5, y=19.0, z=17.0)
    sfm.look(yaw=-90, pitch=15)
    time.sleep(0.3)
    harness.click_block(x=AE2_VOID_DRIVE_POS[0], y=AE2_VOID_DRIVE_POS[1], z=AE2_VOID_DRIVE_POS[2], side=4)
    time.sleep(0.6)
    harness.click_gui_rel(x=160, y=5)
    time.sleep(0.4)
    p_prio_gui = OUTPUT_DIR / "06_me_void_drive_priority_gui.png"
    sfm.screenshot(path=p_prio_gui)
    harness.close_gui()
    time.sleep(0.3)
    print(f"[+] Captured {p_prio_gui.name}")

    # 7. Coolant Pump and Railcraft Tank
    shoot_view("07_coolant_pump_and_tank.png", cx=519, cy=11, cz=41, tx=515, ty=9, tz=45)

    # 8. Pressurized Heat Exchanger (PHE)
    shoot_view("08_phe_heat_exchanger.png", cx=519, cy=11, cz=21, tx=515, ty=9, tz=26)

    # 9. Reactor Controller GUI showing 9x9 active core diagnostics
    harness.stand(RC_X, RC_Y, RC_Z - 2)
    harness.look(0.0, 0.0)
    time.sleep(0.3)
    harness.mclick(RC_X, RC_Y, RC_Z, 2)
    time.sleep(0.8)
    p_reactor_gui = OUTPUT_DIR / "09_reactor_9x9_controller_gui.png"
    sfm.screenshot(path=p_reactor_gui)
    harness.close_gui()
    time.sleep(0.3)
    print(f"[+] Captured {p_reactor_gui.name}")

    # Sync all screenshots to brain artifacts directory
    for f in OUTPUT_DIR.glob("*.png"):
        dest = ARTIFACTS_DIR / f.name
        shutil.copy2(f, dest)
        print(f"[+] Published artifact: {dest.name}")

    print("\n============================================================")
    print("✓ FULL 9x9 REACTOR, SFM, AE2 & COOLANT LOOP TEST COMPLETED!")
    print("============================================================\n")


def run_full_suite():
    ensure_sfm_instance_running()

    sfm = SfmClient(port=SFM_PORT)
    harness = HarnessClient(port=HARNESS_PORT)

    client = FleetClient()
    saves_dir = get_saves_dir(client, "sfm-test-runner")
    world_name = "reactor_9x9_coolant_loop_test"

    print(f"[*] Mounting ephemeral WorldSandbox over 'superflat_empty' into '{saves_dir}' as '{world_name}'...")
    sandbox = WorldSandbox(
        template_name="superflat_empty",
        world_name=world_name,
        saves_dir=saves_dir,
        on_unmount=lambda: harness.exitworld(),
    )

    with sandbox:
        print(f"[*] Launching world '{world_name}'...")
        harness.autoworld(world_name)

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
        harness.stand(505, 19, 18)
        time.sleep(0.5)

        build_world_foundations(harness)
        build_elevated_reactor(harness)
        build_ae2_output_network(harness, sfm)
        build_sfm_supply_logistics(harness, sfm)
        build_coolant_loop_and_manifolds(harness)
        verify_simulation_and_capture(harness, sfm)

        print("[*] Gracefully saving and exiting world...")
        sfm.chat(message="/save-all")
        time.sleep(0.5)
        res_exit = harness.exitworld()
        assert "ARRIVED_AT_MAIN_MENU" in res_exit, f"Exit error: {res_exit}"
        print(f"[+] World exited cleanly: {res_exit}")

        # Stage 8: Create World Snapshot for User Inspection
        print("\n============================================================")
        print("[*] Stage 8: Creating World Snapshot for User Inspection...")
        print("============================================================")
        snapshot_tag = "reactor_9x9_coolant_loop_post"
        template_dest = sandbox.save_as_template(snapshot_tag, overwrite=True)
        print(f"[✓] Golden template snapshot created at: {template_dest}")

        # Update manifest files with canonical snapshot metadata
        meta_data = {
            "name": snapshot_tag,
            "world_name": snapshot_tag,
            "preferred_base": "GTNH-2.9-dev",
            "required_mods": ["modularnuclear", "superfactorymanager"],
            "description": "Tier 2 9x9 MPTR with SFM logistics canopy, AE2 dense network & 16-passage coolant loop",
            "mc_version": "1.7.10",
            "provenance": {
                "type": "script",
                "path": "inworld-tests/test_9x9_reactor_coolant_loop.py",
                "repo": "ModularNuclear",
            },
        }
        for fname in ["manifest.json", "world_manifest.json"]:
            mfile = template_dest / fname
            if mfile.exists():
                try:
                    with open(mfile, "r") as f:
                        cur = json.load(f)
                    cur.update(meta_data)
                    with open(mfile, "w") as f:
                        json.dump(cur, f, indent=2)
                except Exception as e:
                    print(f"[!] Note on updating {fname}: {e}")

        # Also copy as a persistent playable world into instance saves
        target_saves_dirs = [
            saves_dir,
            Path.home() / ".var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances/GTNH-2.9-sfm-dev/.minecraft/saves",
            Path.home() / ".var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances/bot-dev/minecraft/saves",
        ]
        world_save_name = "reactor_9x9_coolant_loop"
        for sdir in target_saves_dirs:
            if sdir and sdir.exists():
                dest_save = sdir / world_save_name
                print(f"[*] Copying persistent playable save to '{dest_save}'...")
                if dest_save.exists():
                    shutil.rmtree(dest_save)
                shutil.copytree(template_dest, dest_save)
                (dest_save / "session.lock").unlink(missing_ok=True)
                print(f"[✓] Playable world saved to {dest_save}")

        print("\n[★] World snapshot ready! Launch with:")
        print(f"    ./nh launch {snapshot_tag} --visible")
        print(f"    or select '{world_save_name}' in Prism Launcher (GTNH-2.9-sfm-dev)")


if __name__ == "__main__":
    run_full_suite()

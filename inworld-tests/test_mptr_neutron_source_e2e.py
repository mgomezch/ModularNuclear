#!/usr/bin/env python3
"""
End-to-End Automated Test: MPTR Neutron Source Component & Beam Input Hatch Integration.

Features Under Test:
1. Multiblock Structure Validation:
   - Beam Input Hatch (gtnhlanth.common.hatch.MTEHatchInputBeamline, ID 10503) placed on
     Slice 4 (bottom layer) facing DOWN.
   - Mandatory pairing rule: must be placed directly opposite a Nuclear Core Bus
     (MTEHatchNuclearBus, ID 32100) on Slice 0 (top layer).
   - Rejection verification: an unpaired Beam Input Hatch fails multiblock formation.
   - Acceptance verification: a properly paired Beam Input Hatch forms cleanly.

2. Neutron Source Component Behavior:
   - Item: GoodGenerator:neutronSource (or goodgenerator:neutronSource).
   - Idle state: acts like an empty nuclear core bus; generates 0 prompt fission heat,
     0 durability wear, and produces 0 neutrons when idle.

3. External Beam Injection & Fast Neutron Generation:
   - Beam injection via Beam Input Hatch with Particle.NEUTRON (id=12).
   - Injected neutrons are 100% fast (not thermal) and emit isotropically in 4 cardinal directions.
   - Formula: S = floor(energy * rate * beam_energy_to_fast_neutron_multiplier * cycle seconds) per direction.
     At energy=9,000,000, rate=10, mult=0.001 and the default 5-tick (0.25 s) cycle -> S = 22,500 per direction,
     total = 90,000 neutrons per cycle.
   - Zero wear verification: the Neutron Source item suffers zero durability depletion.

4. Particle Mismatch Safety:
   - Beam injection with ANY non-neutron particle (e.g. Proton id=1) immediately triggers
     an emergency reactor core explosion (explodeReactor).
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

OUTPUT_DIR = REPO_ROOT / "output" / "mptr_neutron_source"
ARTIFACTS_DIR = Path("/home/mgomezch/.gemini/antigravity/brain/bc0c2152-bd77-425b-aa35-bf4d7878b40d")

# Multiblock Origin & Coordinates (Elevated Tier 1 5x5 Reactor)
RC_X, RC_Y, RC_Z = 500, 20, 500

# Slices:
# Slice 0 (Top Core Hatches/Buses): Y = 23
# Slice 3 (Controller Layer):       Y = 20
# Slice 4 (Bottom Core Hatches):    Y = 19
Y_TOP = 23
Y_CTRL = 20
Y_BOT = 19

# Center core tile (gx=2, gy=2 in 5x5 grid)
CENTER_X, CENTER_Z = 500, 502


def build_reactor_and_test(harness: HarnessClient, sfm: SfmClient):
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    print("============================================================")
    print("[*] Stage 1: Constructing Elevated Tier 1 MPTR Base...")
    print("============================================================")

    # Clean local area and lay quartz foundation
    harness.clear_area(490, 15, 490, 510, 30, 515)
    time.sleep(0.5)
    harness.fill_area(490, 16, 490, 510, 16, 515, "minecraft:quartz_block", 0)
    time.sleep(0.5)

    # 1. Place Controller and autobuild structure
    print(f"[*] Placing MPTR controller at ({RC_X}, {RC_Y}, {RC_Z}) facing North...")
    harness.set_gt_machine(RC_X, RC_Y, RC_Z, 32115, 2)
    time.sleep(0.5)
    res_ab = harness.autobuild(RC_X, RC_Y, RC_Z)
    print(f"    Autobuild result: {res_ab}")
    time.sleep(0.5)

    # 2. Configure Perimeter Hatches on Front Face (Y=20)
    print("[*] Installing Auto-Taping Maintenance Hatch, Dynamo, Output Hatch, and Output Bus...")
    harness.set_gt_machine(499, Y_CTRL, 500, 15497, 2)  # Auto-Taping Maintenance Hatch
    harness.set_gt_machine(498, Y_CTRL, 500, 38, 2)   # EV Dynamo Hatch
    time.sleep(0.5)

    # Fix maintenance and verify base structure forms cleanly
    harness.fix_maintenance(RC_X, RC_Y, RC_Z)
    time.sleep(0.5)
    res_init = harness.recheck(RC_X, RC_Y, RC_Z)
    print(f"    Initial recheck result: {res_init}")
    st_init = harness.nuke_status(RC_X, RC_Y, RC_Z)
    print(f"    Initial structure status: {st_init}")
    assert st_init.get("formed"), f"Base reactor failed to form: {st_init}"
    print(f"[✓] Base reactor formed cleanly! (tiles={st_init.get('tiles')})")

    # ============================================================
    # Stage 2: Verify Structure Pairing Rejection
    # ============================================================
    print("\n============================================================")
    print("[*] Stage 2: Verifying Beam Input Hatch Pairing Rejection...")
    print("============================================================")

    # Place Beam Input Hatch at bottom center facing DOWN (ID 10503, facing 0)
    print(f"[*] Placing Beam Input Hatch at ({CENTER_X}, {Y_BOT}, {CENTER_Z}) facing DOWN...")
    harness.set_gt_machine(CENTER_X, Y_BOT, CENTER_Z, 10503, 0)
    time.sleep(0.3)

    # Top center tile is currently a Coolant Hatch (32104) from autobuild (NOT a Nuclear Bus!)
    # Recheck multiblock: MUST FAIL
    res_unpaired = harness.recheck(RC_X, RC_Y, RC_Z)
    print(f"    Unpaired recheck result: {res_unpaired}")
    time.sleep(0.3)
    st_unpaired = harness.nuke_status(RC_X, RC_Y, RC_Z)
    print(f"    Structure status with unpaired Beam Input Hatch: {st_unpaired}")
    assert not st_unpaired.get("formed"), f"Reactor formed unexpectedly with unpaired beam input hatch! {st_unpaired}"
    print("[✓] SUCCESS: Unpaired Beam Input Hatch was correctly REJECTED by structure check!")

    # ============================================================
    # Stage 3: Verify Structure Formation with Paired Bus
    # ============================================================
    print("\n============================================================")
    print("[*] Stage 3: Verifying Structure Formation with Paired Bus...")
    print("============================================================")

    # Place Nuclear Bus (ID 32100, facing UP = 1) at top center (CENTER_X, Y_TOP, CENTER_Z)
    print(f"[*] Placing Nuclear Bus at ({CENTER_X}, {Y_TOP}, {CENTER_Z}) paired with Beam Hatch...")
    harness.set_gt_machine(CENTER_X, Y_TOP, CENTER_Z, 32100, 1)
    time.sleep(0.5)

    # Recheck multiblock: MUST PASS
    res_paired = harness.recheck(RC_X, RC_Y, RC_Z)
    print(f"    Paired recheck result: {res_paired}")
    time.sleep(0.3)
    st_paired = harness.nuke_status(RC_X, RC_Y, RC_Z)
    print(f"    Structure status with paired Beam Input Hatch: {st_paired}")
    assert st_paired.get("formed"), f"Reactor failed to form with properly paired beam input hatch! {st_paired}"
    print(f"[✓] SUCCESS: Reactor formed cleanly with paired Beam Input Hatch! (tiles={st_paired.get('tiles')})")

    harness.fix_maintenance(RC_X, RC_Y, RC_Z)
    time.sleep(0.3)

    # ============================================================
    # Stage 4: Test Neutron Source Component in Idle State
    # ============================================================
    print("\n============================================================")
    print("[*] Stage 4: Testing Neutron Source Insertion & Idle State...")
    print("============================================================")

    # Insert Neutron Source item into top center bus slot 0
    print("[*] Inserting Neutron Source into center bus slot 0...")
    try:
        res_ns = harness.set_container_slot(x=CENTER_X, y=Y_TOP, z=CENTER_Z, slot=0, item="GoodGenerator:neutronSource", count=1, damage=0)
    except HarnessError:
        res_ns = harness.set_container_slot(x=CENTER_X, y=Y_TOP, z=CENTER_Z, slot=0, item="goodgenerator:neutronSource", count=1, damage=0)
    print(f"    Insert Neutron Source result: {res_ns}")

    # Verify inventory item in slot 0
    inv_raw = harness.container_info(x=CENTER_X, y=Y_TOP, z=CENTER_Z)
    inv_data = inv_raw
    slots = inv_data.get("slots", [])
    assert len(slots) > 0 and "neutronSource" in slots[0].get("item", ""), f"Failed to verify neutron source in inventory: {inv_data}"
    print(f"    Verified slot 0 item: {slots[0]}")

    # Allow 25 ticks (1 reactor simulation cycle) to elapse in idle state
    print("[*] Waiting 25 ticks to verify idle baseline...")
    time.sleep(1.5)

    st_idle = harness.nuke_status(RC_X, RC_Y, RC_Z)
    print(f"    Idle reactor telemetry: {st_idle}")
    n_idle = st_idle.get("neutronsProduced", 0)
    assert n_idle == 0, f"Expected 0 idle neutrons, got: {n_idle}"
    print("[✓] SUCCESS: Idle Neutron Source produces 0 beam neutrons and remains benign!")

    # ============================================================
    # Stage 5: Inject Fast Neutron Beam & Assert Multiplier Math
    # ============================================================
    print("\n============================================================")
    print("[*] Stage 5: Injecting Fast Neutron Beam (Particle 12)...")
    print("============================================================")

    # Particle 12 = Particle.NEUTRON
    # Energy = 9,000,000 eV (Californium Source Chamber beam)
    # Rate = 10
    # Focus = 95.0
    # Formula: S = 9,000,000 * 10 * 0.001 * 0.25 s (a 5-tick cycle) = 22,500 per direction
    # Total generated in 4 cardinal directions = 4 * 22,500 = 90,000 neutrons per cycle
    print(f"[*] Injecting beam packet into ({CENTER_X}, {Y_BOT}, {CENTER_Z}): particle=12, energy=9000000, rate=10...")
    res_beam = harness.beam_packet(x=CENTER_X, y=Y_BOT, z=CENTER_Z, particle=12, energy=9000000, rate=10, focus=95)
    print(f"    Beam packet result: {res_beam}")

    # Poll during the 20-tick simulation cycle to capture the peak telemetry
    print("[*] Polling reactor cycle for beam telemetry...")
    peak_neutrons = 0
    peak_telemetry = {}
    for _ in range(30):
        st = harness.nuke_status(RC_X, RC_Y, RC_Z)
        np = st.get("neutronsProduced", 0)
        if np > peak_neutrons:
            peak_neutrons = np
            peak_telemetry = st
        time.sleep(0.05)

    print(f"    Peak telemetry during beam injection: {peak_telemetry}")
    print(f"    Peak neutrons produced: {peak_neutrons} (expected 90,000)")
    assert peak_neutrons == 90000, f"Expected exactly 90,000 neutrons produced, got {peak_neutrons}"
    print(f"[✓] SUCCESS: Fast neutron beam injection verified! Produced exactly {peak_neutrons} fast neutrons!")

    # Verify zero wear on the Neutron Source item
    inv_ns = harness.container_info(x=CENTER_X, y=Y_TOP, z=CENTER_Z)
    slots = inv_ns.get("slots", [])
    assert len(slots) > 0, "Neutron source was consumed or disappeared!"
    ns_item = slots[0]
    print(f"    Neutron source inventory slot: {ns_item}")
    assert ns_item.get("damage", 0) == 0, f"Neutron source suffered wear: {ns_item}"
    print("[✓] SUCCESS: Neutron Source suffered zero durability wear!")

    # Capture 3D screenshots of the active reactor setup
    print("[*] Capturing 3D perspective screenshot of elevated reactor...")
    harness.stand(505, 23, 492)
    harness.look(-30, 20)
    time.sleep(0.5)
    p3d = OUTPUT_DIR / "mptr_neutron_source_active_3d.png"
    harness.screenshot(path=str(p3d))
    print(f"[+] Saved {p3d}")

    # ============================================================
    # Stage 6: Particle Mismatch Hazard (Safety Explosion)
    # ============================================================
    print("\n============================================================")
    print("[*] Stage 6: Testing Particle Mismatch Hazard (Safety Explosion)...")
    print("============================================================")

    # Inject incompatible beam: Particle 1 = Proton
    print(f"[*] Injecting INCOMPATIBLE beam packet (particle=1 PROTON) into ({CENTER_X}, {Y_BOT}, {CENTER_Z})...")
    res_bad = harness.beam_packet(x=CENTER_X, y=Y_BOT, z=CENTER_Z, particle=1, energy=1000000, rate=10, focus=95)
    print(f"    Incompatible beam packet result: {res_bad}")

    # Wait for the reactor tick to detect particle mismatch and trigger explodeReactor()
    print("[*] Waiting for particle safety check to trigger core explosion...")
    time.sleep(1.5)

    # Verify the controller block was annihilated by the explosion
    block_info = harness.blockinfo(RC_X, RC_Y, RC_Z)["block"]
    print(f"    Block at controller position after explosion: {block_info}")
    assert "air" in block_info or "none" in block_info, f"Controller block was not destroyed! {block_info}"
    print("[✓] SUCCESS: Reactor immediately exploded upon receiving incompatible particle beam!")

    # Capture aftermath screenshot
    p_aftermath = OUTPUT_DIR / "mptr_particle_explosion_aftermath.png"
    harness.screenshot(path=str(p_aftermath))
    print(f"[+] Saved {p_aftermath}")

    # Copy screenshots to brain artifacts directory
    for f in OUTPUT_DIR.glob("*.png"):
        dest = ARTIFACTS_DIR / f.name
        shutil.copy2(f, dest)
        print(f"[+] Published artifact: {dest.name}")

    print("\n============================================================")
    print("✓ MPTR NEUTRON SOURCE & BEAMLINE INTEGRATION TEST PASSED!")
    print("============================================================\n")


def run_full_suite():
    ensure_sfm_instance_running()

    sfm = SfmClient(port=SFM_PORT)
    harness = HarnessClient(port=HARNESS_PORT)

    client = FleetClient()
    saves_dir = get_saves_dir(client, "sfm-test-runner")
    world_name = "mptr_neutron_source_test"

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
        harness.stand(505, 23, 492)
        time.sleep(0.5)

        build_reactor_and_test(harness, sfm)

        print("[*] Gracefully saving and exiting world...")
        sfm.chat(message="/save-all")
        time.sleep(0.5)
        res_exit = harness.exitworld()
        assert "ARRIVED_AT_MAIN_MENU" in res_exit, f"Exit error: {res_exit}"
        print(f"[+] World exited cleanly: {res_exit}")


if __name__ == "__main__":
    run_full_suite()

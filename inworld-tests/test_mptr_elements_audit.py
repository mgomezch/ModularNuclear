#!/usr/bin/env python3
"""
inworld-tests/test_mptr_elements_audit.py: Automated in-game fidelity audit for MPTR simulation with elements.
Constructs an in-game 5x5 reactor:
  - 1 Single Uranium Fuel Rod (IC2:reactorUraniumSimple) at (518, 8, 23) in Nuclear Core Bus (32100)
  - 1 Beryllium Moderator Long Rod (ore:stickLongBeryllium) at (518, 8, 22) in Nuclear Core Bus (32100)
  - 1 EV Nuclear Core Hatch (32104) at (517, 8, 22) with IC2 Coolant, replenished via SFM
  - 3 ME Output Hatches (2713) with Digital Singularity Fluid Storage Cells & Creative Energy Controllers
Samples telemetry every 5 seconds over 3 minutes of operation (36 samples),
reads ME Output Hatch NBT caches to measure fluid output accumulation,
runs a matching standalone simulation for 3600 ticks, and compares metrics.
"""

import sys
import os
import time
import json
import subprocess
from pathlib import Path
from typing import Dict, Any, List, Optional, Tuple

from nhdev import NH_DEV_ROOT as REPO_ROOT

from tools.agentharness import HarnessClient, HarnessError
from tools.sfm_client import SfmClient
from tools.runner_instance import get_saves_dir
from tools.bot.world_manager import WorldSandbox
from tools.mcfleet.client import FleetClient
from tools.mcfleet.schema import InstanceSpec, DisplayMode

HARNESS_PORT = 25572
SFM_PORT = 25573
INSTANCE_NAME = "mptr-sfm-audit"

RC_X, RC_Y, RC_Z = 518, 5, 20
MH_X, MH_Y, MH_Z = 517, 5, 20
DY_X, DY_Y, DY_Z = 519, 5, 20
PL_X, PL_Y, PL_Z = 518, 6, 17

# 3 ME Output Hatches and their adjacent Creative ME Controllers
ME_HATCHES = [
    {"hatch": (516, 5, 20), "facing": 2, "ctrl": (516, 5, 19), "desc": "ME Output 1 (North-West)"},
    {"hatch": (520, 5, 20), "facing": 2, "ctrl": (520, 5, 19), "desc": "ME Output 2 (North-East)"},
    {"hatch": (515, 5, 22), "facing": 4, "ctrl": (514, 5, 22), "desc": "ME Output 3 (West)"},
]

# Core coordinates (Y=8)
FUEL_POS = (518, 8, 23)
MOD_POS = (518, 8, 22)
COOLANT_POS = (517, 8, 22)

# SFM logistics positions (Y=9)
MGR_POS = (513, 9, 22)
TANK_POS = (513, 9, 21)


def ensure_instance():
    fleet = FleetClient()
    harness = HarnessClient(port=HARNESS_PORT)
    sfm = SfmClient(port=SFM_PORT)

    if harness.ping() and sfm.ping():
        print(f"[+] Instance already running and responsive on ports {HARNESS_PORT} and {SFM_PORT}.")
        try:
            harness.exitworld()
            time.sleep(2.0)
        except Exception:
            pass
        return harness, sfm

    print(f"[*] Launching ephemeral test instance '{INSTANCE_NAME}' via FleetClient...")
    mod_libs = list((REPO_ROOT / "mods" / "ModularNuclear" / "build" / "libs").glob("modularnuclear-*.jar"))
    clean_jars = [
        str(p) for p in mod_libs
        if not any(x in p.name for x in ["-sources", "-dev", "-predowngrade", "-preshadow"])
    ]
    clean_jars.sort(key=lambda p: os.path.getmtime(p), reverse=True)
    overrides = [clean_jars[0]] if clean_jars else []
    print(f"[*] Using mod_overrides: {overrides}")

    spec = InstanceSpec(
        name=INSTANCE_NAME,
        template="GTNH-2.9-sfm-dev",
        base="GTNH-2.9-sfm-dev",
        ephemeral=True,
        display_mode=DisplayMode.HEADLESS,
        display_socket="wayland-99",
        port=HARNESS_PORT,
        memory_min="6144m",
        memory_max="6144m",
        mod_overrides=overrides,
        jvm_args=[
            "-Dsfm.testHarness=true",
            f"-Dsfm.harness.port={SFM_PORT}",
        ],
    )
    inst = fleet.launch(spec, auto_start=True)
    print(f"[+] Started instance '{spec.name}' (PID {inst.status.pid if inst.status else 'unknown'}).")

    start_t = time.time()
    while time.time() - start_t < 240:
        if harness.ping() and sfm.ping():
            try:
                st = harness.state()
                if "GuiCustom" in st or "GuiMainMenu" in st or "MAIN_MENU" in st or "IN_WORLD" in st:
                    print(f"[+] Both controllers ready after {time.time() - start_t:.1f}s ({st})!")
                    return harness, sfm
            except Exception:
                pass
        time.sleep(3)
        print(f"[*] Waiting for game initialization... ({int(time.time() - start_t)}s elapsed)")

    raise RuntimeError("Timed out waiting for test instance controllers to become ready")


def enter_world(harness: HarnessClient, sfm: SfmClient, world_name: str):
    st = harness.state()
    if "IN_WORLD" not in st:
        print(f"[*] Loading world '{world_name}'...")
        res = sfm.autoworld(name=world_name)
        print(f"    autoworld attempt result: {res}")
        start_w = time.time()
        last_send = time.time()
        while time.time() - start_w < 120:
            st = harness.state()
            if "IN_WORLD" in st:
                print(f"[+] Successfully entered world: {st}")
                break
            if time.time() - last_send >= 5.0:
                res = sfm.autoworld(name=world_name)
                print(f"    autoworld retry: {res}")
                last_send = time.time()
            time.sleep(1.0)
        if harness.state() != "IN_WORLD":
            raise RuntimeError(f"Failed to enter world, status: {harness.status()}")

    time.sleep(2.0)
    harness.close_gui()
    time.sleep(0.5)

    # Standardize baseline environment
    print("[*] Setting world baseline parameters...")
    sfm.chat(message="/gamemode 1")
    sfm.chat(message="/gamerule doDaylightCycle false")
    sfm.chat(message="/gamerule doMobSpawning false")
    sfm.chat(message="/time set 6000")
    sfm.chat(message="/weather clear 99999")
    time.sleep(0.5)


def build_audit_reactor(harness: HarnessClient, sfm: SfmClient):
    print("============================================================")
    print("[*] Stage 1: Constructing 5x5 MPTR reactor with test elements...")
    print("    Elements: 1 U1 fuel rod, 1 Beryllium moderator rod,")
    print("              1 EV IC2 coolant hatch, 3 ME output hatches")
    print("============================================================")

    # Position player
    harness.stand(PL_X, PL_Y, PL_Z)
    time.sleep(0.5)
    harness.look(0.0, 30.0)
    time.sleep(0.5)

    # Clear construction envelope
    print("[*] Clearing construction envelope...")
    harness.fill_area(510, 4, 15, 526, 15, 30, "air")
    harness.fill_area(510, 4, 15, 526, 4, 30, "stone")
    time.sleep(1.0)

    # 1. Controller at (518, 5, 20) facing 2 (NORTH)
    print(f"[*] Placing reactor controller at ({RC_X}, {RC_Y}, {RC_Z})...")
    harness.set_gt_machine(RC_X, RC_Y, RC_Z, 32115, 2)
    time.sleep(0.5)

    # 2. Autobuild multiblock structure
    print("[*] Autobuilding 5x5 multiblock structure...")
    res_ab = harness.autobuild(RC_X, RC_Y, RC_Z)
    print(f"    autobuild result: {res_ab}")
    time.sleep(1.0)

    # 3. Install Auto-Taping Maintenance Hatch (15497) and EV Dynamo (38)
    print(f"[*] Installing Auto-Taping Maintenance Hatch at ({MH_X}, {MH_Y}, {MH_Z})...")
    harness.set_gt_machine(MH_X, MH_Y, MH_Z, 15497, 2)
    print(f"[*] Installing EV Dynamo Hatch at ({DY_X}, {DY_Y}, {DY_Z})...")
    harness.set_gt_machine(DY_X, DY_Y, DY_Z, 38, 2)
    time.sleep(0.5)

    # 4. Install 3 ME Output Hatches (ID 2713) with Singularity Cells & Creative ME Controllers
    for entry in ME_HATCHES:
        hx, hy, hz = entry["hatch"]
        facing = entry["facing"]
        cx, cy, cz = entry["ctrl"]
        desc = entry["desc"]
        print(f"[*] Installing {desc} at ({hx}, {hy}, {hz}) facing {facing}...")
        harness.set_gt_machine(hx, hy, hz, 2713, facing)
        time.sleep(0.3)

        # Place Neutronium-Infused ME Controller adjacent to ME Output Hatch
        print(f"    Placing Neutronium-Infused ME Controller at ({cx}, {cy}, {cz})...")
        harness.set_block(cx, cy, cz, "appliedenergistics2:tile.BlockCreativeEnergyController", 0)
        time.sleep(0.3)

        # Insert Digital Singularity Fluid Storage Cell into slot 0
        print(f"    Inserting Digital Singularity Fluid Storage Cell into slot 0...")
        res_cell = harness.set_container_slot(hx, hy, hz, 0, "ae2fc:fluid_storage.singularity", 1, 0)
        print(f"    Insert result: {res_cell}")
        time.sleep(0.3)

    # 5. Configure the 21 core top cells (Y=8)
    print("[*] Configuring 21 core tiles at Y=8...")
    core_coords = []
    for x in range(516, 521):
        for z in range(21, 26):
            if (x, z) in [(516, 21), (520, 21), (516, 25), (520, 25)]:
                continue
            core_coords.append((x, z))

    print(f"    Identified {len(core_coords)} active core grid positions.")
    for x, z in core_coords:
        # All initially configured as Nuclear Bus ID 32100, facing UP (1)
        harness.set_gt_machine(x, 8, z, 32100, 1)
    time.sleep(0.5)

    # Note: Fuel rod insertion is deferred to start of Stage 3 to ensure cold baseline at t=0.
    # Adjacent cell is (518, 8, 22): Beryllium Moderator Long Rod
    print(f"[*] Inserting Beryllium Moderator Long Rod at {MOD_POS}...")
    res_mod = harness.set_container_slot(MOD_POS[0], MOD_POS[1], MOD_POS[2], 0, "ore:stickLongBeryllium", 1, 0)
    print(f"    Moderator insert: {res_mod}")

    # Adjacent cell is (517, 8, 22): EV Nuclear Core Hatch (32104, facing 1)
    print(f"[*] Installing EV Nuclear Core Hatch at {COOLANT_POS}...")
    harness.set_gt_machine(COOLANT_POS[0], COOLANT_POS[1], COOLANT_POS[2], 32104, 1)
    time.sleep(0.5)

    # 6. Form and fix reactor multiblock
    print("[*] Rechecking and validating multiblock formation...")
    harness.recheck(RC_X, RC_Y, RC_Z)
    time.sleep(1.0)
    st = harness.nuke_status(RC_X, RC_Y, RC_Z)
    print(f"    Structure formation status: formed={st.get('formed')} tiles={st.get('tiles')}")
    assert st.get("formed") or st.get("is_formed"), f"Reactor failed to form: {st}"

    harness.fix_maintenance(RC_X, RC_Y, RC_Z)
    time.sleep(1.0)

    # 7. Initial fluid fill of EV coolant hatch (128,000 L) after multiblock formation & tick ramp-up
    print(f"[*] Filling EV Nuclear Core Hatch with 128,000 L IC2 Coolant...")
    filled_ok = False
    for attempt in range(10):
        res_fill = harness.fill_fluid(COOLANT_POS[0], COOLANT_POS[1], COOLANT_POS[2], "ic2coolant", 128000)
        te_fl = harness.te_fluid(COOLANT_POS[0], COOLANT_POS[1], COOLANT_POS[2])
        print(f"    Attempt {attempt+1}: {res_fill} -> {te_fl}")
        if te_fl.get("tanks") and te_fl["tanks"][0]["amount"] > 0:
            filled_ok = True
            print(f"[✓] Coolant hatch filled with {te_fl['tanks'][0]['amount']} L {te_fl['tanks'][0]['fluid']}")
            break
        time.sleep(0.5)

    assert filled_ok, f"Failed to fill EV Nuclear Core Hatch: {harness.te_fluid(COOLANT_POS[0], COOLANT_POS[1], COOLANT_POS[2])}"
    print("[✓] Reactor constructed and formed successfully!")


def setup_sfm_logistics(harness: HarnessClient, sfm: SfmClient):
    print("\n============================================================")
    print("[*] Stage 2: Setting up Super Factory Manager Coolant Logistics...")
    print("============================================================")

    # 1. Place Fancy Inventory Cables from coolant hatch (517, 8, 22) at Y=9 to Manager at (513, 9, 22)
    print("[*] Laying SFM Fancy Inventory Cables at Y=9...")
    for x in range(514, 518):
        harness.set_block(x, 9, 22, "superfactorymanager:fancyCable", 0)
    time.sleep(0.5)

    # 2. Place Manager and Infinite Debug Tank
    print(f"[*] Placing Factory Manager at {MGR_POS}...")
    harness.set_block(MGR_POS[0], MGR_POS[1], MGR_POS[2], "air", 0)
    res_mgr = harness.set_block(MGR_POS[0], MGR_POS[1], MGR_POS[2], "superfactorymanager:manager", 0)
    print(f"    Set manager result: {res_mgr}")
    binfo = harness.blockinfo(x=MGR_POS[0], y=MGR_POS[1], z=MGR_POS[2])["block"]
    print(f"    Manager blockinfo: {binfo}")

    print(f"[*] Placing Debug Tank at {TANK_POS}...")
    harness.set_block(TANK_POS[0], TANK_POS[1], TANK_POS[2], "air", 0)
    harness.set_gt_machine(TANK_POS[0], TANK_POS[1], TANK_POS[2], 238, 2)
    time.sleep(0.3)
    harness.configure_debug_tank(TANK_POS[0], TANK_POS[1], TANK_POS[2], "ic2coolant", output_fluid=False)
    time.sleep(0.5)

    # 3. Program SFM labels
    print("[*] Assigning peripheral labels in Factory Manager...")
    mx, my, mz = MGR_POS
    sfm.clearlabels(x=mx, y=my, z=mz)
    sfm.setlabel(x=mx, y=my, z=mz, label="coolant_tank", tx=TANK_POS[0], ty=TANK_POS[1], tz=TANK_POS[2])
    sfm.setlabel(x=mx, y=my, z=mz, label="coolant_hatch", tx=COOLANT_POS[0], ty=COOLANT_POS[1], tz=COOLANT_POS[2])

    # 4. Load, compile, and run SFM program
    prog = (
        "NAME \"Coolant Feed\"\n"
        "EVERY 20 TICKS DO\n"
        "  INPUT 8000 fluid:ic2coolant FROM coolant_tank\n"
        "  OUTPUT TO coolant_hatch\n"
        "END\n"
    )
    print("[*] Compiling SFM program via SFM controller...")
    res_prog = sfm.programmanager(x=mx, y=my, z=mz, code=prog)
    print(f"    SFM compiler result: {res_prog}")
    assert res_prog["valid"], f"SFM program failed compilation: {res_prog}"

    print("[*] Activating SFM Factory Manager execution...")
    sfm.settick(x=mx, y=my, z=mz, tick=0)
    sfm.setrunning(x=mx, y=my, z=mz, running=True)

    time.sleep(1.0)
    print("[✓] SFM Coolant Logistics active and running!")


def collect_telemetry_and_audit(harness: HarnessClient):
    print("\n============================================================")
    print("[*] Stage 3: Collecting In-Game Telemetry (every 5s for 3m)...")
    print("============================================================")

    # Insert Single Uranium Fuel Rod right now to begin cold baseline at t=0
    print(f"[*] Inserting Single Uranium Fuel Rod at {FUEL_POS}...")
    res_fuel = harness.set_container_slot(FUEL_POS[0], FUEL_POS[1], FUEL_POS[2], 0, "IC2:reactorUraniumSimple", 1, 0)
    print(f"    Fuel insert result: {res_fuel}")
    print(f"    Coolant hatch te_fluid at t=0: {harness.te_fluid(COOLANT_POS[0], COOLANT_POS[1], COOLANT_POS[2])}")

    harness.unfreeze_ticks()
    time.sleep(1.0)

    samples = []
    duration_sec = 180
    interval_sec = 5
    total_steps = duration_sec // interval_sec
    last_tiles = []

    for step in range(total_steps):
        time.sleep(interval_sec)
        t_elapsed = (step + 1) * interval_sec

        metrics = harness.reactor_metrics(RC_X, RC_Y, RC_Z)
        last_tiles = metrics.get("tiles", [])

        # Inspect ME Hatches
        me_info_list = []
        total_hot_coolant = 0
        total_steam = 0
        fluids_seen = {}

        for entry in ME_HATCHES:
            hx, hy, hz = entry["hatch"]
            try:
                info = harness.me_hatch_info(hx, hy, hz)
            except Exception as e:
                info = {"error": str(e)}
            me_info_list.append(info)
            for c in info.get("cache", []):
                fn = c.get("fluid", "")
                fa = max(c.get("amount", 0), c.get("size", 0))
                if fn and fa > 0:
                    fluids_seen[fn] = fluids_seen.get(fn, 0) + fa
                    if "coolant" in fn.lower():
                        total_hot_coolant += fa
                    if "steam" in fn.lower():
                        total_steam += fa
            mf = info.get("mFluid")
            if isinstance(mf, dict):
                mfn = mf.get("fluid", "")
                mfa = mf.get("amount", 0)
                if mfn and mfa > 0:
                    fluids_seen[mfn] = fluids_seen.get(mfn, 0) + mfa
                    if "coolant" in mfn.lower():
                        total_hot_coolant += mfa
                    if "steam" in mfn.lower():
                        total_steam += mfa

        # Core tiles by grid coordinates (gx, gy)
        fuel_tile = None
        mod_tile = None
        coolant_tile = None
        for t in last_tiles:
            gx = t.get("gx")
            gy = t.get("gy")
            if gx == 2 and gy == 2:
                fuel_tile = t
            elif gx == 2 and gy == 1:
                mod_tile = t
            elif gx == 1 and gy == 1:
                coolant_tile = t

        record = {
            "elapsed_sec": t_elapsed,
            "coreTemp": metrics.get("coreTemp", 0.0),
            "avgTemp": metrics.get("avgTemp", 0.0),
            "directPowerEUt": metrics.get("directPowerEUt", 0),
            "neutronsProduced": metrics.get("neutronsProduced", 0),
            "fastAbsorbed": metrics.get("fastAbsorbed", 0),
            "thermalAbsorbed": metrics.get("thermalAbsorbed", 0),
            "escaped": metrics.get("escaped", 0),
            "fuel_temp": fuel_tile.get("temp", 0.0) if fuel_tile else 0.0,
            "fuel_damage": fuel_tile.get("itemDamage", 0) if fuel_tile else 0,
            "mod_temp": mod_tile.get("temp", 0.0) if mod_tile else 0.0,
            "mod_damage": mod_tile.get("itemDamage", 0) if mod_tile else 0,
            "coolant_temp": coolant_tile.get("temp", 0.0) if coolant_tile else 0.0,
            "coolant_fluid": coolant_tile.get("fluidName") if coolant_tile else None,
            "coolant_amount": coolant_tile.get("fluidAmount", 0) if coolant_tile else 0,
            "total_hot_coolant_produced": total_hot_coolant,
            "total_steam_produced": total_steam,
            "fluids_in_me": fluids_seen,
            "me_info": me_info_list,
        }
        samples.append(record)

        print(f"[{t_elapsed:3d}s] CoreMax={record['coreTemp']:5.1f}°C | "
              f"Fuel={record['fuel_temp']:5.1f}°C | "
              f"Mod={record['mod_temp']:5.1f}°C | "
              f"Coolant={record['coolant_temp']:5.1f}°C ({record['coolant_fluid']}:{record['coolant_amount']}L) | "
              f"Neutrons={record['neutronsProduced']} (F_abs={record['fastAbsorbed']}, T_abs={record['thermalAbsorbed']}) | "
              f"HotCoolantOut={total_hot_coolant} L")

    # Save in-game telemetry
    out_path = REPO_ROOT / "tools" / "mptr_elements_audit_telemetry.json"
    out_path.write_text(json.dumps(samples, indent=2))
    print(f"\n[✓] In-game telemetry saved to {out_path}")

    # Steady-state analysis (second half: 90s .. 180s)
    ss = [s for s in samples if s["elapsed_sec"] >= 90]
    avg_fuel_temp = sum(s["fuel_temp"] for s in ss) / len(ss)
    avg_mod_temp = sum(s["mod_temp"] for s in ss) / len(ss)
    avg_coolant_temp = sum(s["coolant_temp"] for s in ss) / len(ss)
    avg_core_max = sum(s["coreTemp"] for s in ss) / len(ss)
    avg_core_mean = sum(s["avgTemp"] for s in ss) / len(ss)
    avg_neutrons = sum(s["neutronsProduced"] for s in ss) / len(ss)
    avg_fast_abs = sum(s["fastAbsorbed"] for s in ss) / len(ss)
    avg_therm_abs = sum(s["thermalAbsorbed"] for s in ss) / len(ss)

    delta_hot_coolant = ss[-1]["total_hot_coolant_produced"] - ss[0]["total_hot_coolant_produced"]
    delta_time = ss[-1]["elapsed_sec"] - ss[0]["elapsed_sec"]
    hot_coolant_rate_ls = delta_hot_coolant / max(1.0, delta_time)

    print("\n============================================================")
    print("   IN-GAME STEADY-STATE METRICS (t = 90s .. 180s)")
    print("============================================================")
    print(f"  Fuel Tile Temp:            {avg_fuel_temp:.1f} °C")
    print(f"  Moderator Tile Temp:       {avg_mod_temp:.1f} °C")
    print(f"  Coolant Hatch Temp:        {avg_coolant_temp:.1f} °C")
    print(f"  Core Max Temp:             {avg_core_max:.1f} °C")
    print(f"  Core Mean Temp:            {avg_core_mean:.1f} °C")
    print(f"  Neutrons Produced / cycle: {avg_neutrons:.1f}")
    print(f"  Fast Neutrons Absorbed:    {avg_fast_abs:.1f}")
    print(f"  Thermal Neutrons Absorbed: {avg_therm_abs:.1f}")
    print(f"  Hot Coolant Production:    {hot_coolant_rate_ls:.2f} L/s ({hot_coolant_rate_ls/20.0:.3f} L/t)")
    print(f"  Total Hot Coolant in ME:   {samples[-1]['total_hot_coolant_produced']} L")

    # Render 5x5 heatmap from last in-game tiles
    grid_map = [["  --  " for _ in range(5)] for _ in range(5)]
    for t in last_tiles:
        gx = t.get("gx", 0)
        gy = t.get("gy", 0)
        temp = t.get("temp", 0.0)
        if 0 <= gx < 5 and 0 <= gy < 5:
            grid_map[gy][gx] = f"{temp:5.1f} "
    print("\nIn-Game Core Final Heat Map (°C):")
    for row in grid_map:
        print("  " + " ".join(row))

    # Run matching standalone simulation
    print("\n============================================================")
    print("[*] Stage 4: Running Matching Standalone Simulation...")
    print("============================================================")
    sim_bin = REPO_ROOT / "tools" / "gtnh-nuclear-sim"
    sim_layout = "NL,EM,EM,EM,NL;EM,HC,MB,EM,EM;EM,EM,U1,EM,EM;EM,EM,EM,EM,EM;NL,EM,EM,EM,NL"
    cmd = [
        str(sim_bin),
        "--size", "5",
        "--tier", "electrum",
        "--layout", sim_layout,
        "--ticks", "720",
        "--ambient-temp", "26.7",
        "--json",
    ]
    res_sim = subprocess.run(cmd, capture_output=True, text=True, check=True)
    sim_data = json.loads(res_sim.stdout.strip())

    sim_out_path = REPO_ROOT / "tools" / "mptr_elements_simulation_result.json"
    sim_out_path.write_text(json.dumps(sim_data, indent=2))
    print(f"[✓] Simulation result saved to {sim_out_path}")

    sim_core_max = sim_data.get("coreMaxTemp", 0.0)
    sim_core_avg = sim_data.get("coreAvgTemp", 0.0)
    sim_power_eu = sim_data.get("powerEUt", 0.0)
    sim_flow_hot_coolant = sim_data.get("flowHotCoolant", 0.0)
    sim_hot_coolant_rate_ls = sim_flow_hot_coolant * 20.0
    sim_total_hot_coolant = sim_data.get("totalHotCoolant", 0)

    print("\n============================================================")
    print("   FIDELITY COMPARISON: IN-GAME VS STANDALONE SIMULATION")
    print("============================================================")
    print(f"  {'Metric':<30} | {'In-Game':<15} | {'Simulation':<15} | {'Ratio / Status'}")
    print("-" * 75)
    print(f"  {'Core Max Temp (°C)':<30} | {avg_core_max:<15.1f} | {sim_core_max:<15.1f} | {sim_core_max/max(1, avg_core_max):.2f}x")
    print(f"  {'Core Avg Temp (°C)':<30} | {avg_core_mean:<15.1f} | {sim_core_avg:<15.1f} | {sim_core_avg/max(1, avg_core_mean):.2f}x")
    coolant_ratio = f"{sim_hot_coolant_rate_ls/max(0.001, hot_coolant_rate_ls):.2f}x" if hot_coolant_rate_ls > 0 else "-"
    print(f"  {'Hot Coolant Rate (L/s)':<30} | {hot_coolant_rate_ls:<15.2f} | {sim_hot_coolant_rate_ls:<15.2f} | {coolant_ratio}")
    print(f"  {'Total Hot Coolant (L)':<30} | {samples[-1]['total_hot_coolant_produced']:<15d} | {sim_total_hot_coolant:<15d} | -")
    print("============================================================")


def main():
    harness, sfm = ensure_instance()
    fleet = FleetClient()
    saves_dir = get_saves_dir(fleet, INSTANCE_NAME)
    saves_dir.mkdir(parents=True, exist_ok=True)
    world_name = "mptr_elements_audit"
    print(f"[*] Mounting ephemeral WorldSandbox over 'superflat_empty' into '{saves_dir}' as '{world_name}'...")
    sandbox = WorldSandbox(
        template_name="superflat_empty",
        world_name=world_name,
        saves_dir=saves_dir,
        on_unmount=lambda: harness.exitworld(),
    )
    try:
        with sandbox:
            enter_world(harness, sfm, world_name)
            build_audit_reactor(harness, sfm)
            setup_sfm_logistics(harness, sfm)
            collect_telemetry_and_audit(harness)
    finally:
        print("\n[*] Tearing down ephemeral test instance...")
        try:
            harness.exitworld()
            time.sleep(1.0)
        except Exception:
            pass
        try:
            fleet = FleetClient()
            fleet.stop(INSTANCE_NAME)
            time.sleep(1.0)
            fleet.delete(INSTANCE_NAME)
            print(f"[✓] Stopped and deleted instance '{INSTANCE_NAME}' cleanly.")
        except Exception as e:
            print(f"[-] Note during teardown: {e}")


if __name__ == "__main__":
    main()

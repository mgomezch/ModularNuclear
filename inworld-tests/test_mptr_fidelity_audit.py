#!/usr/bin/env python3
"""
inworld-tests/test_mptr_fidelity_audit.py: Automated in-game fidelity audit for MPTR simulation.
Constructs a simple in-game 5x5 reactor (1 fuel rod, 1 radiovoltaic, no cooling),
samples telemetry every 5 seconds over 3 minutes of operation (36 samples),
runs a matching standalone simulation for 3600 ticks (180s),
and performs a comprehensive steady-state comparison and mathematical audit.
"""

import sys
import os
import time
import json
import subprocess
from pathlib import Path
from typing import Dict, Any, List, Optional

from nhdev import NH_DEV_ROOT as REPO_ROOT

from tools.agentharness import HarnessClient, HarnessError

RC_X, RC_Y, RC_Z = 518, 5, 20
MH_X, MH_Y, MH_Z = 517, 5, 20
DY_X, DY_Y, DY_Z = 519, 5, 20
PL_X, PL_Y, PL_Z = 518, 6, 17


def build_simple_audit_reactor(client: HarnessClient):
    print("============================================================")
    print("[*] Stage 1: Constructing simple 5x5 MPTR reactor...")
    print("    Configuration: 1 fuel rod, 1 radiovoltaic cell, no cooling")
    print("============================================================")
    client.close_gui()
    client.wait(5)

    # Position player
    client.stand(PL_X, PL_Y, PL_Z)
    client.wait(5)
    client.look(0.0, 30.0)
    client.wait(5)

    # Clear surrounding area to ensure pristine environment
    print("[*] Clearing build envelope...")
    client.fill_area(514, 4, 19, 522, 12, 27, "air")
    client.fill_area(514, 4, 19, 522, 4, 27, "stone")
    client.wait(10)

    # Set controller at (518, 5, 20) facing 2 (NORTH)
    print(f"[*] Placing reactor controller at ({RC_X}, {RC_Y}, {RC_Z})...")
    res = client.set_gt_machine(RC_X, RC_Y, RC_Z, 32115, 2)
    print(f"    set_gt_machine result: {res}")
    client.wait(5)

    # Autobuild 5x5 electrum reactor
    print(f"[*] Autobuilding 5x5 multiblock structure...")
    res_ab = client.autobuild(RC_X, RC_Y, RC_Z)
    print(f"    autobuild result: {res_ab}")
    client.wait(15)

    # Replace maintenance hatch with Auto-Taping Maintenance Hatch (15497)
    print(f"[*] Installing auto-taping maintenance hatch at ({MH_X}, {MH_Y}, {MH_Z})...")
    client.set_gt_machine(MH_X, MH_Y, MH_Z, 15497, 2)
    client.wait(5)

    # Replace casing with EV Dynamo Hatch (38) at (519, 5, 20) to accept direct power
    print(f"[*] Installing EV Dynamo Hatch at ({DY_X}, {DY_Y}, {DY_Z})...")
    client.set_gt_machine(DY_X, DY_Y, DY_Z, 38, 2)
    client.wait(5)

    # Recheck formation before configuring core tiles
    client.recheck(RC_X, RC_Y, RC_Z)
    client.wait(10)
    st = client.nuke_status(RC_X, RC_Y, RC_Z)
    print(f"[*] Structure formation status: formed={st.get('formed')} grid={st.get('grid')}")
    assert st.get("formed") or st.get("is_formed"), f"Reactor failed to form: {st}"

    # Configure the 21 core top cells (Y=8)
    # 5x5 bounding box: X=516..520, Z=21..25, cut corners: (516, 21), (520, 21), (516, 25), (520, 25)
    print("[*] Configuring 21 core tiles: converting to Nuclear Buses (32100)...")
    core_coords = []
    for x in range(516, 521):
        for z in range(21, 26):
            if (x, z) in [(516, 21), (520, 21), (516, 25), (520, 25)]:
                continue
            core_coords.append((x, z))

    print(f"    Identified {len(core_coords)} active core grid positions.")
    for x, z in core_coords:
        # Nuclear Bus ID 32100, facing UP (1)
        client.set_gt_machine(x, 8, z, 32100, 1)
    client.wait(10)

    # Center cell is (518, 8, 23): Single Uranium Fuel Rod
    # Adjacent cell is (518, 8, 24): HV Radiovoltaic Plate
    fuel_pos = (518, 8, 23)
    rv_pos = (518, 8, 24)
    print(f"[*] Inserting Single Uranium Fuel Rod at {fuel_pos}...")
    res_fuel = client.set_container_slot(fuel_pos[0], fuel_pos[1], fuel_pos[2], 0, "IC2:reactorUraniumSimple", 1, 0)
    print(f"    Fuel insert: {res_fuel}")

    print(f"[*] Inserting Radiovoltaic Plate (HV) at {rv_pos}...")
    res_rv = client.set_container_slot(rv_pos[0], rv_pos[1], rv_pos[2], 0, "modularnuclear:radiovoltaic_plate", 1, 0)
    print(f"    Radiovoltaic insert: {res_rv}")
    client.wait(10)

    # Recheck structure formation
    client.recheck(RC_X, RC_Y, RC_Z)
    client.wait(10)
    st = client.nuke_status(RC_X, RC_Y, RC_Z)
    print(f"[✓] MPTR configured and formed: {st}")
    return fuel_pos, rv_pos


def run_audit():
    client = HarnessClient(port=25572)
    fuel_pos, rv_pos = build_simple_audit_reactor(client)

    print("\n============================================================")
    print("[*] Stage 2: Collecting in-game telemetry (every 5s for 3m)...")
    print("============================================================")

    # Unfreeze ticks to start simulation
    client.unfreeze_ticks()
    time.sleep(2.0)

    samples = []
    duration_sec = 180
    interval_sec = 5
    total_steps = duration_sec // interval_sec

    for step in range(total_steps):
        time.sleep(interval_sec)
        metrics = client.reactor_metrics(RC_X, RC_Y, RC_Z)
        t_elapsed = (step + 1) * interval_sec

        # Extract fuel tile and radiovoltaic tile from metrics
        fuel_tile = None
        rv_tile = None
        for t in metrics.get("tiles", []):
            if t.get("isFuel"):
                fuel_tile = t
            if t.get("isRadiovoltaic") or t.get("directEU", 0) > 0:
                rv_tile = t

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
            "rv_temp": rv_tile.get("temp", 0.0) if rv_tile else 0.0,
            "rv_directEU": rv_tile.get("directEU", 0) if rv_tile else 0,
        }
        samples.append(record)
        print(f"[{t_elapsed:3d}s] CoreTemp={record['coreTemp']:5.1f}°C | "
              f"FuelTemp={record['fuel_temp']:5.1f}°C | "
              f"RVTemp={record['rv_temp']:5.1f}°C | "
              f"Power={record['directPowerEUt']} EU/t | "
              f"Neutrons={record['neutronsProduced']} | "
              f"FastAbs={record['fastAbsorbed']}")

    # Save empirical in-game telemetry
    out_path = REPO_ROOT / "tools" / "mptr_ingame_telemetry.json"
    out_path.write_text(json.dumps(samples, indent=2))
    print(f"\n[✓] In-game telemetry saved to {out_path}")

    # Compute in-game steady-state statistics (last half of run: 90s to 180s)
    ss_samples = [s for s in samples if s["elapsed_sec"] >= 90]
    avg_ingame_power = sum(s["directPowerEUt"] for s in ss_samples) / len(ss_samples)
    avg_ingame_core_temp = sum(s["coreTemp"] for s in ss_samples) / len(ss_samples)
    avg_ingame_fuel_temp = sum(s["fuel_temp"] for s in ss_samples) / len(ss_samples)
    avg_ingame_rv_temp = sum(s["rv_temp"] for s in ss_samples) / len(ss_samples)

    print("\n============================================================")
    print("   IN-GAME STEADY-STATE RESULTS (t = 90s .. 180s)")
    print("============================================================")
    print(f"  Average Power Output:      {avg_ingame_power:.1f} EU/t")
    print(f"  Average Core Max Temp:     {avg_ingame_core_temp:.1f} °C")
    print(f"  Average Fuel Tile Temp:    {avg_ingame_fuel_temp:.1f} °C")
    print(f"  Average Radiovoltaic Temp: {avg_ingame_rv_temp:.1f} °C")

    # Run matching standalone simulation
    print("\n============================================================")
    print("[*] Stage 3: Running matching standalone simulation...")
    print("============================================================")
    sim_bin = REPO_ROOT / "tools" / "gtnh-nuclear-sim"
    sim_layout = "NL,EM,EM,EM,NL;EM,EM,EM,EM,EM;EM,EM,U1,RH,EM;EM,EM,EM,EM,EM;NL,EM,EM,EM,NL"
    cmd = [
        str(sim_bin),
        "--size", "5",
        "--tier", "electrum",
        "--layout", sim_layout,
        "--ticks", "3600",
        "--json",
    ]
    res_sim = subprocess.run(cmd, capture_output=True, text=True, check=True)
    sim_data = json.loads(res_sim.stdout.strip())

    sim_out_path = REPO_ROOT / "tools" / "mptr_simulation_result.json"
    sim_out_path.write_text(json.dumps(sim_data, indent=2))
    print(f"[✓] Simulation result saved to {sim_out_path}")

    sim_core_max_temp = sim_data.get("coreMaxTemp", 0.0)
    sim_core_avg_temp = sim_data.get("coreAvgTemp", 0.0)
    sim_total_energy = sim_data.get("powerEUt", 0.0)
    sim_direct_power = sim_data.get("directPowerEUt", 0.0)

    # Let's also run multiple 100-tick runs to get the empirical simulation power average
    sim_power_samples = []
    for _ in range(20):
        r = subprocess.run([str(sim_bin), "--size", "5", "--tier", "electrum", "--layout", sim_layout, "--ticks", "180", "--json"],
                           capture_output=True, text=True, check=True)
        d = json.loads(r.stdout.strip())
        # Check power
        sim_power_samples.append(d.get("directPowerEUt", 0.0))

    avg_sim_sampled_power = sum(sim_power_samples) / len(sim_power_samples) if sim_power_samples else 0.0

    print("\n============================================================")
    print("   COMPARISON: IN-GAME VS SIMULATION")
    print("============================================================")
    print(f"  {'Metric':<25} | {'In-Game':<15} | {'Simulation':<15} | {'Ratio / Status'}")
    print("-" * 70)
    print(f"  {'Core Max Temp (°C)':<25} | {avg_ingame_core_temp:<15.1f} | {sim_core_max_temp:<15.1f} | {sim_core_max_temp/max(1, avg_ingame_core_temp):.2f}x")
    print(f"  {'Direct Power (EU/t)':<25} | {avg_ingame_power:<15.1f} | {avg_sim_sampled_power:<15.1f} | {avg_sim_sampled_power/max(0.1, avg_ingame_power):.2f}x")
    print("============================================================")


if __name__ == "__main__":
    run_audit()

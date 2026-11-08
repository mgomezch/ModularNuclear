#!/usr/bin/env python3
"""
In-world test: a running reactor gets through a save and reload without damage or a jump in temperature.

It builds a calibration preset, runs it until it's warm with its hatches kept full, reloads the world, and keeps
feeding the hatches from the first moment it can.  See Note [Hatches that work without ticking].

Needs the sfm-test-runner instance with the build under test.
"""

import argparse
import sys
import time

from nhdev import NH_DEV_ROOT as REPO_ROOT  # noqa: F401 (puts the workspace on sys.path)

from tools.agentharness import HarnessError
from tools.bot import build_all_ceiling_reactors_world as W
from tools.bot import build_modular_nuclear_showcase_world as S
from tools.bot.reactor_parity import load_preset
from tools.bot.world_manager import WorldSandbox
from tools.mcfleet.client import FleetClient
from tools.runner_instance import get_saves_dir

WORLD = "reactor_reload"
WARM_UP_SECONDS = 120
WATCH_SECONDS = 60  # how long it watches the core, both before the reload as a baseline and after it
SAMPLE_SECONDS = 2.0
# How far the hottest and average samples after the reload may go past those of the baseline before it
MAX_RISE_AFTER_RELOAD_C = 50.0


def fill_hatches(harness, pinfo, xc: int, zc: int) -> int:
    """Tops up every coolant hatch; returns how many took any fluid."""
    took = 0
    for rel_x, rel_z, _code, fluid in pinfo["coolants"]:
        try:
            if int(harness.fill_fluid(xc + rel_x, W.Y_TOP, zc + rel_z, fluid, 128000).split()[1]) > 0:
                took += 1
        except HarnessError:  # blown up with the reactor
            pass
    return took


def sample(harness, ctrl: tuple) -> dict:
    st = harness.nuke_status(*ctrl, cells=True)
    temps = [c["temp"] for c in st.get("cells", [])]
    return {"formed": st.get("formed"), "damage": st.get("damage"),
            "avg": sum(temps) / len(temps) if temps else None, "peak": max(temps) if temps else None}


def watch(harness, ctrl: tuple, pinfo, xc: int, zc: int, seconds: int) -> list:
    """Samples the core while keeping its hatches full; asserts it stays formed."""
    samples = []
    start = time.time()
    while time.time() - start < seconds:
        st = sample(harness, ctrl)
        print(f"    t={time.time() - start:5.1f} s {st}")
        assert st["formed"], f"Expected the reactor to stay formed, got {st}"
        samples.append(st)
        fill_hatches(harness, pinfo, xc, zc)
        time.sleep(SAMPLE_SECONDS)
    return samples


def wait_in_world(harness, timeout: float = 180.0):
    t0 = time.time()
    while harness.state() != "IN_WORLD":
        assert time.time() - t0 < timeout, harness.status()
        time.sleep(0.1)


def main(preset_name: str) -> bool:
    preset = load_preset(preset_name)
    pinfo = W.parse_preset(preset)
    S.ensure_showcase_instance_running()
    harness = S.HarnessClient(port=S.HARNESS_PORT)
    sfm = S.SfmClient(port=S.SFM_PORT)
    sandbox = WorldSandbox(template_name="superflat_empty", world_name=WORLD,
                           saves_dir=get_saves_dir(FleetClient(), "sfm-test-runner"),
                           on_unmount=lambda: harness.exitworld())
    with sandbox:
        sfm.autoworld(name=WORLD)
        wait_in_world(harness)
        time.sleep(1.0)
        harness.close_gui()
        for cmd in ("/gamemode 1", "/gamerule doDaylightCycle false", "/gamerule doMobSpawning false",
                    "/time set 6000", "/weather clear 999999", "/difficulty 0"):
            sfm.chat(message=cmd)
        _, _, xc, zc = W.get_cell_origin(0)
        harness.stand(xc, W.Y_TOP + 4, zc - pinfo["radius"] - 3)
        time.sleep(1.0)
        W.build_cell_reactor(harness, pinfo, xc, zc)
        ctrl = (xc, W.Y_CTRL, zc - pinfo["radius"])
        W.load_cell_fuels(harness, pinfo, xc, zc)  # the reactor starts here

        print(f"[*] Warming up for {WARM_UP_SECONDS} s")
        start = time.time()
        while time.time() - start < WARM_UP_SECONDS:
            fill_hatches(harness, pinfo, xc, zc)
            time.sleep(SAMPLE_SECONDS)
        print(f"[*] Baseline: {WATCH_SECONDS} s before the reload")
        baseline = watch(harness, ctrl, pinfo, xc, zc, WATCH_SECONDS)

        print("[*] Saving and reloading")
        harness.save_world()
        harness.exitworld()
        time.sleep(2.0)
        sfm.autoworld(name=WORLD)
        wait_in_world(harness)
        first_took = fill_hatches(harness, pinfo, xc, zc)
        print(f"    First top-up after the reload: {first_took} of {len(pinfo['coolants'])} hatches took fluid")

        print(f"[*] {WATCH_SECONDS} s after the reload")
        after = watch(harness, ctrl, pinfo, xc, zc, WATCH_SECONDS)

        damage = max(st["damage"] for st in after)
        assert damage <= baseline[-1]["damage"] + 1e-9, f"The reload damaged the reactor: {damage}"
        for key in ("avg", "peak"):
            was, now = max(st[key] for st in baseline), max(st[key] for st in after)
            print(f"    Highest {key}: {was:.0f} °C before the reload, {now:.0f} °C after it")
            assert now - was <= MAX_RISE_AFTER_RELOAD_C, f"Highest {key} rose {now - was:.0f} °C after the reload"
        print("[✓] No damage, and no more heat after the reload than before it")
    return True


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--preset", default="Electrum 5x5 Power (EV)")
    args = ap.parse_args()
    sys.exit(0 if main(args.preset) else 1)

#!/usr/bin/env python3
"""
In-world test: a Source Chamber beams neutrons into an MPTR through a beamline, as a player would build it.

The chamber (gtnhlanth's MTESourceChamber) stands upright with its controller facing down, so its beam output hatch
is on top and fires straight up through beamline pipes into the beam input hatch under the reactor's centre column.
That column holds a neutron source in its nuclear bus, which the beam input hatch pairs with.

Pairing:     a beam input hatch with no nuclear bus opposite it keeps the reactor from forming; with the bus, it forms.
Idle:        with no dust in the chamber there is no beam and the neutron source emits nothing.
Californium: from an EV energy hatch the chamber draws 1,920 EU/t and sends a 9,000 keV neutron beam at rate 10 up
             the beamline, burning one dust a second.  The reactor turns it into energy * rate * multiplier * cycle
             seconds packets of 4 fast neutrons: with beam_energy_to_fast_neutron_multiplier 0.001 and 5-tick cycles,
             22.5 packets a cycle, so 88 and 92 neutrons on alternate cycles, 360 a second.
Shutdown:    once the dust runs out, the beam and the neutrons stop within a few seconds.
Electrons:   a Tungsten rod, from an IV energy hatch, makes an electron beam; the reactor explodes when it arrives.

These are the numbers the simulator's Source Chamber model has to match.
"""

import statistics
import sys
import time

from nhdev import NH_DEV_ROOT as REPO_ROOT  # noqa: F401 (puts the workspace on sys.path)

from tools.agentharness import HarnessClient
from tools.inworld_test_driver import InWorldTestDriver

SOURCE_CHAMBER_ID = 10506
BEAMLINE_PIPE_ID = 10502
BEAM_INPUT_HATCH_ID = 10503
BEAM_OUTPUT_HATCH_ID = 10504
INPUT_BUS_EV_ID = 74
OUTPUT_BUS_EV_ID = 84
ENERGY_HATCH_EV_ID = 44
ENERGY_HATCH_IV_ID = 45
AUTO_MAINTENANCE_ID = 111
AUTO_TAPING_MAINTENANCE_ID = 15497
DYNAMO_EV_ID = 38
REACTOR_ID = 32115
NUCLEAR_BUS_ID = 32100

DOWN, UP, NORTH = 0, 1, 2
NEUTRON, ELECTRON = 12, 0

# The chamber's controller, facing down: the chamber stands on it, from Y0 to Y0 + 5, over z in Z0 - 4 .. Z0
X0, Y0, Z0 = 1000, 4, 1000
CHAMBER = (X0, Y0, Z0)
CHAMBER_INPUT_BUS = (X0, Y0, Z0 - 2)
CHAMBER_OUTPUT_BUS = (X0, Y0 + 1, Z0)
CHAMBER_ENERGY = (X0 - 2, Y0, Z0)
CHAMBER_MAINTENANCE = (X0 - 1, Y0, Z0)
BEAM_OUT = (X0, Y0 + 5, Z0 - 2)

# The reactor (5x5, facing north) over the chamber, its centre column right above the beam output hatch
REACTOR_Y = Y0 + 11
REACTOR = (X0, REACTOR_Y, Z0 - 4)
BEAM_IN = (X0, REACTOR_Y - 1, Z0 - 2)
CENTRE_BUS = (X0, REACTOR_Y + 3, Z0 - 2)
PIPES = [(X0, y, Z0 - 2) for y in range(BEAM_OUT[1] + 1, BEAM_IN[1])]

STANDING_ON = (X0 + 0.5, REACTOR_Y + 6, Z0 - 12)

CALIFORNIUM = "ore:dustCalifornium"
TUNGSTEN_ROD = "ore:stickTungsten"

# What the chamber makes of Californium dust from an EV hatch (GT5-Unofficial's BeamlineRecipeLoader)
CALIFORNIUM_EUT = 1920
CALIFORNIUM_KEV = 9000.0
CALIFORNIUM_RATE = 10
# Neutrons a cycle from that beam, alternating as the reactor carries the half packet over
CALIFORNIUM_NEUTRONS = {88, 92}


def gt(client: HarnessClient, pos: tuple) -> dict:
    return client.gt_info(*pos)


def reactor(client: HarnessClient) -> dict:
    return client.nuke_status(*REACTOR)


def neutron_samples(client: HarnessClient, seconds: float = 4.0) -> list:
    """neutronsProduced of successive cycles, sampled every 50 ms (each cycle is 250 ms)."""
    values, deadline = [], time.time() + seconds
    while time.time() < deadline:
        values.append(reactor(client).get("neutronsProduced", 0))
        time.sleep(0.05)
    return values


def dust_count(client: HarnessClient) -> int:
    slots = client.container_info(*CHAMBER_INPUT_BUS).get("slots", [])
    return sum(s["count"] for s in slots)


def build(client: HarnessClient):
    client.teleport(*STANDING_ON)
    client.fill_area(X0 - 8, Y0 - 1, Z0 - 12, X0 + 8, Y0 - 1, Z0 + 4, "minecraft:stone")

    client.set_gt_machine(*CHAMBER, SOURCE_CHAMBER_ID, DOWN)
    print(f"    Chamber: {client.autobuild(*CHAMBER)}")
    client.set_gt_machine(*CHAMBER_INPUT_BUS, INPUT_BUS_EV_ID, DOWN)
    client.set_gt_machine(*CHAMBER_OUTPUT_BUS, OUTPUT_BUS_EV_ID, NORTH)
    client.set_gt_machine(*CHAMBER_ENERGY, ENERGY_HATCH_EV_ID, DOWN)
    client.set_gt_machine(*CHAMBER_MAINTENANCE, AUTO_MAINTENANCE_ID, DOWN)
    client.set_gt_machine(*BEAM_OUT, BEAM_OUTPUT_HATCH_ID, UP)
    client.set_cover(*CHAMBER_ENERGY, DOWN, "Cover_Wireless_Energy_Debug")

    for pos in PIPES:
        client.set_gt_machine(*pos, BEAMLINE_PIPE_ID, UP)

    client.set_gt_machine(*REACTOR, REACTOR_ID, NORTH)
    print(f"    Reactor: {client.autobuild(*REACTOR)}")
    client.set_gt_machine(REACTOR[0] - 1, REACTOR_Y, REACTOR[2], AUTO_TAPING_MAINTENANCE_ID, NORTH)
    client.set_gt_machine(REACTOR[0] - 2, REACTOR_Y, REACTOR[2], DYNAMO_EV_ID, NORTH)
    client.set_gt_machine(*BEAM_IN, BEAM_INPUT_HATCH_ID, DOWN)


def check_pairing(client: HarnessClient):
    print("[*] Pairing: a beam input hatch needs a nuclear bus opposite it")
    client.recheck(*REACTOR)
    st = reactor(client)
    assert not st.get("formed"), f"Expected no reactor with an unpaired beam input hatch, got {st}"
    client.set_gt_machine(*CENTRE_BUS, NUCLEAR_BUS_ID, UP)
    client.fix_maintenance(*REACTOR)
    client.recheck(*REACTOR)
    st = reactor(client)
    assert st.get("formed"), f"Expected the reactor to form with the bus in place, got {st}"
    client.set_container_slot(*CENTRE_BUS, slot=0, item="GoodGenerator:neutronSource", count=1, damage=0)
    client.recheck(*CHAMBER)
    assert gt(client, CHAMBER).get("formed"), f"Expected the Source Chamber to form, got {gt(client, CHAMBER)}"
    print(f"[✓] Unpaired, the reactor didn't form; paired, it did ({st.get('tiles')} tiles), and so did the chamber")


def check_idle(client: HarnessClient):
    print("[*] Idle: no dust, no beam, no neutrons")
    time.sleep(2.0)
    chamber = gt(client, CHAMBER)
    assert not chamber["active"] and chamber["eut"] == 0, f"Expected an idle chamber, got {chamber}"
    assert gt(client, BEAM_IN)["beam"] is None, "Expected no beam at the reactor"
    samples = neutron_samples(client, 1.5)
    assert max(samples) == 0, f"Expected no neutrons from an unlit source, got {samples}"
    print("[✓] The idle chamber draws nothing and the source emits nothing")


def check_californium(client: HarnessClient):
    print("[*] Californium: an EV chamber's neutron beam drives the reactor's source")
    client.set_container_slot(*CHAMBER_INPUT_BUS, slot=0, item=CALIFORNIUM, count=64, damage=0)
    time.sleep(3.0)

    chamber = gt(client, CHAMBER)
    print(f"    Chamber: eut={chamber['eut']}, {chamber['info']}")
    assert chamber["active"], f"Expected the chamber to run, got {chamber}"
    assert chamber["eut"] == -CALIFORNIUM_EUT, f"Expected {CALIFORNIUM_EUT} EU/t, got {chamber['eut']}"

    beam = gt(client, BEAM_IN)["beam"]
    print(f"    Beam at the reactor: {beam}")
    assert beam is not None, "Expected the beam to reach the reactor's beam input hatch"
    assert beam["particleid"] == NEUTRON, f"Expected neutrons, got {beam}"
    assert beam["energy"] == CALIFORNIUM_KEV and beam["rate"] == CALIFORNIUM_RATE, f"Unexpected beam {beam}"

    samples = neutron_samples(client)
    print(f"    Neutrons a cycle: {samples}")
    assert set(samples) <= CALIFORNIUM_NEUTRONS, f"Expected only {CALIFORNIUM_NEUTRONS} neutrons a cycle"
    assert abs(statistics.mean(samples) - 90.0) <= 2.0, f"Expected 90 a cycle on average, got {samples}"

    before, t0 = dust_count(client), time.time()
    time.sleep(10.0)
    used, seconds = before - dust_count(client), time.time() - t0
    print(f"    Dust used: {used} in {seconds:.1f} s")
    assert 9 <= used <= 11, f"Expected about one dust a second, used {used} in {seconds:.1f} s"
    print("[✓] 1,920 EU/t and a dust a second make a 9,000 keV beam at rate 10: 90 fast neutrons a cycle")


def check_shutdown(client: HarnessClient):
    print("[*] Shutdown: the beam stops with the dust")
    client.clear_container(*CHAMBER_INPUT_BUS)
    t0 = time.time()
    while time.time() - t0 < 6.0:
        if max(neutron_samples(client, 0.5)) == 0:
            break
    stopped = time.time() - t0
    samples = neutron_samples(client, 1.5)
    assert max(samples) == 0, f"Expected the neutrons to stop with the beam, got {samples}"
    assert gt(client, BEAM_IN)["beam"] is None, "Expected the beam input hatch to be empty"
    print(f"[✓] The neutrons stopped about {stopped:.1f} s after the dust ran out")


def check_electrons(client: HarnessClient):
    print("[*] Electrons: a Tungsten rod beam explodes the reactor")
    client.set_gt_machine(*CHAMBER_ENERGY, ENERGY_HATCH_IV_ID, DOWN)
    client.set_cover(*CHAMBER_ENERGY, DOWN, "Cover_Wireless_Energy_Debug")
    client.recheck(*CHAMBER)
    client.set_container_slot(*CHAMBER_INPUT_BUS, slot=0, item=TUNGSTEN_ROD, count=4, damage=0)
    deadline, block = time.time() + 8.0, None
    while time.time() < deadline:
        block = client.blockinfo(*REACTOR)
        if block.get("mte") != "MTENuclearReactor":
            break
        time.sleep(0.25)
    print(f"    Reactor controller block now: {block}")
    assert block.get("mte") != "MTENuclearReactor", "Expected the reactor to explode on an electron beam"
    print("[✓] The reactor exploded when the electron beam arrived")


def main(port: int) -> bool:
    driver = InWorldTestDriver(tag="beamline_source_chamber", port=port)

    def test(client: HarnessClient):
        client.teleport(*STANDING_ON)
        time.sleep(2.0)
        check_pairing(client)
        check_idle(client)
        check_californium(client)
        check_shutdown(client)
        check_electrons(client)

    return driver.run(build_fn=build, test_fn=test)


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="A Source Chamber beams neutrons into an MPTR")
    parser.add_argument("--port", type=int, default=25571, help="AgentHarness port (default: 25571)")
    args = parser.parse_args()
    sys.exit(0 if main(args.port) else 1)

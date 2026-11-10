#!/usr/bin/env python3
"""
In-world test: an advanced control rod hatch's graphite moderator follower slides in as its boron rod comes out.

A 5x5 reactor has a water hatch in one inner column and four single uranium rods around it.  Under that column a
control rod hatch faces down, with control rod housings in a line below it.  Each housing lets the rod come 20 % out
of the core, and no redstone reaches the hatch, so the rod sits as far out as its housings allow: 5 housings, fully
out (insertion 0); 2 housings, 60 % in; none, fully in.  The test reads the rod's cell from the reactor
(nuke_status cells): its probabilities, which come from TileTick's rod and follower math, and the rod hatch's state.

Plain hatch:    a boron rod, out and in: the baseline.
Advanced hatch: the same boron rod and a long graphite rod as its follower, which is in at 1 − insertion:
                rod out    the follower adds about its graphite's moderation
                60 % in    the follower is 40 % in: moderation between the two
                rod in     the follower is out: moderation and absorption exactly the plain hatch's
The advanced hatch has a slot for the follower that the plain hatch lacks.
"""

import sys
import time

from nhdev import NH_DEV_ROOT as REPO_ROOT  # noqa: F401 (puts the workspace on sys.path)

from tools.agentharness import HarnessClient, HarnessError
from tools.inworld_test_driver import InWorldTestDriver

REACTOR_ID = 32115
NUCLEAR_BUS_ID = 32100
CONTROL_ROD_ID = 32111
ADVANCED_CONTROL_ROD_ID = 32117
AUTO_TAPING_MAINTENANCE_ID = 15497
DYNAMO_EV_ID = 38
HOUSING = "modularnuclear:control_rod_housing"
HOUSING_ALONG_Y = 0  # BlockControlRodHousing.AXIS_Y

DOWN, UP, NORTH = 0, 1, 2

BORON_ROD = "ore:stickLongBoron"
GRAPHITE_ROD = "ore:stickLongGraphite"
URANIUM = "gregtech:gt.rodUranium"  # single rods: quads melt a water-cooled 5x5 within a minute
WATER = "ic2distilledwater"

# The reactor's controller, facing north: its 5x5 core runs x X0 - 2 .. X0 + 2, z Z0 .. Z0 + 4
X0, Y0, Z0 = 1200, 12, 1200
REACTOR = (X0, Y0, Z0)
Y_TOP, Y_BOTTOM = Y0 + 3, Y0 - 1
CENTRE = (X0, Z0 + 2)
ROD = (X0, Y_BOTTOM, CENTRE[1])
FUEL = [(X0 - 1, CENTRE[1]), (X0 + 1, CENTRE[1]), (X0, CENTRE[1] - 1), (X0, CENTRE[1] + 1)]
HOUSINGS = [(X0, Y_BOTTOM - i, CENTRE[1]) for i in range(1, 6)]

STANDING_ON = (X0 + 0.5, Y_TOP + 4, Z0 - 8)


def build(client: HarnessClient):
    client.teleport(*STANDING_ON)
    client.set_gt_machine(*REACTOR, REACTOR_ID, NORTH)
    print(f"    Reactor: {client.autobuild(*REACTOR)}")
    client.set_gt_machine(X0 - 1, Y0, Z0, AUTO_TAPING_MAINTENANCE_ID, NORTH)
    client.set_gt_machine(X0 - 2, Y0, Z0, DYNAMO_EV_ID, NORTH)
    for x, z in FUEL:
        client.set_gt_machine(x, Y_TOP, z, NUCLEAR_BUS_ID, UP)
        client.set_container_slot(x, Y_TOP, z, slot=0, item=URANIUM, count=1, damage=0)


def fill_water(client: HarnessClient):
    for x in range(X0 - 2, X0 + 3):
        for z in range(Z0, Z0 + 5):
            if (x, z) in FUEL:
                continue
            try:
                client.fill_fluid(x, Y_TOP, z, WATER, 64000)
            except HarnessError:
                pass  # not a hatch: a corner


def set_housings(client: HarnessClient, count: int):
    """The rod comes 20 % out per housing in an unbroken line below the hatch."""
    for i, pos in enumerate(HOUSINGS):
        if i < count:
            client.set_block(*pos, HOUSING, HOUSING_ALONG_Y)
        else:
            client.set_block(*pos, "minecraft:air", 0)
    client.recheck(*REACTOR)


def install_hatch(client: HarnessClient, hatch_id: int, follower: bool):
    client.set_gt_machine(*ROD, hatch_id, DOWN)
    client.set_container_slot(*ROD, slot=0, item=BORON_ROD, count=1, damage=0)
    if follower:
        client.set_container_slot(*ROD, slot=1, item=GRAPHITE_ROD, count=1, damage=0)
    client.fix_maintenance(*REACTOR)
    client.recheck(*REACTOR)
    st = client.nuke_status(*REACTOR)
    assert st.get("formed"), f"Expected the reactor to form, got {st}"


def centre(client: HarnessClient, seconds: float = 4.0) -> dict:
    """The rod's cell: its probabilities, the rod hatch's state (nuke_status cells), and the reactor's neutrons a
    cycle averaged over a few seconds of running."""
    fill_water(client)
    time.sleep(1.5)
    samples, deadline = [], time.time() + seconds
    while time.time() < deadline:
        st = client.nuke_status(*REACTOR, cells=True)
        cell = next(c for c in st["cells"] if "rod" in c)
        assert st.get("formed"), f"Expected the reactor to stay formed, got {st}"
        samples.append((cell, st.get("neutronsProduced", 0), st.get("temp", 0.0)))
        time.sleep(0.25)
    last = samples[-1][0]
    temp = samples[-1][2]
    return {
        "p_moderation": last["p_moderation"],
        "p_abs_fast": last["p_abs_fast"],
        "p_abs_thermal": last["p_abs_thermal"],
        "rod": last["rod"],
        "neutrons": sum(n for _, n, _ in samples) / len(samples),
        "temp": temp,
    }


def show(label: str, c: dict):
    rod = c["rod"]
    print(f"    {label:22} rod {rod['insertion']:3d}% in, follower {rod.get('follower_insertion', 0):3d}% in | "
          f"moderation {c['p_moderation']:.4f}  fast absorption {c['p_abs_fast']:.4f}  "
          f"thermal absorption {c['p_abs_thermal']:.4f} | {c['neutrons']:.0f} neutrons a cycle, {c['temp']:.0f} °C")


def expect_rod(c: dict, insertion: int, follower: int):
    rod = c["rod"]
    assert rod["insertion"] == insertion and rod.get("follower_insertion", 0) == follower and not rod["scram"], \
        f"Expected the rod {insertion}% in and its follower {follower}% in, got {rod}"


def check_plain(client: HarnessClient) -> dict:
    print("[*] Plain hatch: a boron rod, out and in")
    install_hatch(client, CONTROL_ROD_ID, follower=False)
    size = client.container_info(*ROD)["size"]
    set_housings(client, 5)
    out = centre(client)
    set_housings(client, 0)
    inside = centre(client)
    show("rod out", out)
    show("rod in", inside)
    expect_rod(out, 0, 0)
    expect_rod(inside, 100, 0)
    assert inside["p_abs_thermal"] > out["p_abs_thermal"], "Expected the inserted rod to absorb more"
    print(f"[✓] The plain hatch ({size} slot) absorbs more with its rod in")
    return {"out": out, "in": inside, "size": size}


def check_follower(client: HarnessClient, plain: dict):
    print("[*] Advanced hatch: the same boron rod, and a graphite follower")
    install_hatch(client, ADVANCED_CONTROL_ROD_ID, follower=True)
    size = client.container_info(*ROD)["size"]
    slots = {s["slot"]: s for s in client.container_info(*ROD)["slots"]}
    assert size > plain["size"] and 1 in slots, f"Expected a follower slot holding graphite, got {slots}"

    set_housings(client, 5)
    out = centre(client)
    set_housings(client, 2)
    mid = centre(client)
    set_housings(client, 0)
    inside = centre(client)
    show("rod out, follower in", out)
    show("rod 60 % in", mid)
    show("rod in, follower out", inside)
    expect_rod(out, 0, 100)
    expect_rod(mid, 60, 40)
    expect_rod(inside, 100, 0)

    assert out["p_moderation"] > plain["out"]["p_moderation"] + 0.2, \
        f"Expected the follower to add moderation with the rod out: {out} vs plain {plain['out']}"
    assert plain["in"]["p_moderation"] < mid["p_moderation"] < out["p_moderation"], \
        f"Expected moderation between, part-way in: {mid}"
    for k in ("p_moderation", "p_abs_fast", "p_abs_thermal"):
        assert abs(inside[k] - plain["in"][k]) < 1e-6, \
            f"Expected the advanced hatch with its rod in to act as the plain one: {k} {inside[k]} vs {plain['in'][k]}"
    # The neutron counts are shown, not checked: the core keeps warming through the test (about 90 to 300 °C), and
    # its count follows the temperature more than one follower in a lightly fuelled 5x5.  ControlRodFollowerSimTest
    # checks the follower's effect on neutrons, under steady conditions
    print("[✓] The follower moderates as the rod comes out, partly part-way, and not at all with the rod in")


def main(port: int) -> bool:
    driver = InWorldTestDriver(tag="control_rod_follower", port=port)

    def test(client: HarnessClient):
        client.teleport(*STANDING_ON)
        time.sleep(2.0)
        plain = check_plain(client)
        check_follower(client, plain)

    return driver.run(build_fn=build, test_fn=test)


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Control rod moderator followers")
    parser.add_argument("--port", type=int, default=25571, help="AgentHarness port (default: 25571)")
    args = parser.parse_args()
    sys.exit(0 if main(args.port) else 1)

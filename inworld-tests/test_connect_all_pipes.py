#!/usr/bin/env python3
import sys
import time

import nhdev  # noqa: F401 (nh-dev tools on sys.path)
from tools.agentharness import HarnessClient

c = HarnessClient()

# Ensure peaceful, no mob spawning, and remove all mobs
print("[*] Setting game environment to peaceful, disabling mobs, killing entities...")
c.chat('/difficulty 0')
c.chat('/gamerule doMobSpawning false')
c.chat('/cofh killall *')
c.chat('/time set 6000')
c.chat('/gamerule doDaylightCycle false')

# Directions:
# 0: DOWN (-Y), 1: UP (+Y), 2: NORTH (-Z), 3: SOUTH (+Z), 4: WEST (-X), 5: EAST (+X)
DOWN, UP, NORTH, SOUTH, WEST, EAST = 0, 1, 2, 3, 4, 5

pipe_connections = [
    # Hot Leg
    # (12, 5, 419..416) going North
    (12, 5, 419, SOUTH, NORTH),
    (12, 5, 418, SOUTH, NORTH),
    (12, 5, 417, SOUTH, NORTH),
    (12, 5, 416, SOUTH, NORTH),
    (12, 5, 415, SOUTH, EAST),
    # Going East to Creative Heat Reservoir
    (13, 5, 415, WEST, EAST),
    (14, 5, 415, WEST, EAST),
    (15, 5, 415, WEST, EAST),
    # Creative Heat Reservoir at (16, 5, 415)
    (17, 5, 415, WEST, EAST),
    (18, 5, 415, WEST, EAST),
    (19, 5, 415, WEST, SOUTH),
    # Going South along X=19
    (19, 5, 416, NORTH, SOUTH),
    (19, 5, 417, NORTH, SOUTH),
    (19, 5, 418, NORTH, SOUTH),
    (19, 5, 419, NORTH, SOUTH),
    (19, 5, 420, NORTH, SOUTH),
    (19, 5, 421, NORTH, EAST),

    # Cold Leg
    # Exiting PHE Primary Outlet (22, 5, 421) East
    (23, 5, 421, WEST, EAST),
    (24, 5, 421, WEST, SOUTH),
    # Going South along X=24
    (24, 5, 422, NORTH, SOUTH),
    (24, 5, 423, NORTH, SOUTH),
    (24, 5, 424, NORTH, SOUTH),
    (24, 5, 425, NORTH, SOUTH),
    (24, 5, 426, NORTH, WEST),
    # Going West along Z=426
    (23, 5, 426, EAST, WEST),
    (22, 5, 426, EAST, WEST),
    (21, 5, 426, EAST, WEST),
    (20, 5, 426, EAST, WEST),
    (19, 5, 426, EAST, WEST),
    # In-line devices: (18, 5, 426)=Flow Meter, (17, 5, 426)=Thermometer
    (16, 5, 426, EAST, WEST),
    # Manifold at (15, 5, 426)
    (14, 5, 426, EAST, WEST),
    (13, 5, 426, EAST, WEST),
    (12, 5, 426, EAST, WEST),
    (11, 5, 426, EAST, WEST),
    (10, 5, 426, EAST, WEST),
    (9, 5, 426, EAST, WEST),
    (8, 5, 426, EAST, NORTH),
    # Going North along X=8
    (8, 5, 425, SOUTH, NORTH),
    (8, 5, 424, SOUTH, NORTH),
    (8, 5, 423, SOUTH, NORTH),
    (8, 5, 422, SOUTH, NORTH),
    (8, 5, 421, SOUTH, NORTH),
    (8, 5, 420, SOUTH, NORTH),
    (8, 5, 419, SOUTH, EAST),
    # Going East along Z=419 to Suction Hatch (10, 5, 420)
    (9, 5, 419, WEST, EAST),
    (10, 5, 419, WEST, SOUTH),
]

print(f"[*] Connecting {len(pipe_connections)} pipe blocks directionally (both backward and forward)...")
for x, y, z, back_dir, fwd_dir in pipe_connections:
    r1 = c.connect_pipe(x=x, y=y, z=z, side=back_dir)
    r2 = c.connect_pipe(x=x, y=y, z=z, side=fwd_dir)

print("[✓] All pipe blocks connected!")

# Now test crawler!
print("\n[*] Testing Loop Crawler...")
crawl_res = c.crawl_loop(x=11, y=5, z=420)
print("    crawl_loop result:", crawl_res)

# Test pump recheck
print("\n[*] Testing Pump Recheck...")
recheck_res = c.recheck(11, 5, 420)
print("    recheck result:", recheck_res)

status = c.coolant_status(x=11, y=5, z=420)
print("    coolant_status:", status)

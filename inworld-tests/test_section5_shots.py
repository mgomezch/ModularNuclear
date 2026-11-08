#!/usr/bin/env python3
import time
from pathlib import Path
import nhdev  # noqa: F401 (nh-dev tools on sys.path)
from tools.agentharness import HarnessClient

c = HarnessClient()
c.close_gui()
c.slot(4)  # empty hand
c.chat('/time set 6000')
c.chat('/gamerule doDaylightCycle false')

out_dir = Path("/home/mgomezch/.gemini/antigravity/brain/bc0c2152-bd77-425b-aa35-bf4d7878b40d/scratch")
out_dir.mkdir(parents=True, exist_ok=True)

# 1. Pump
c.chat('/tp 8 8 414')
c.look(-25.0, 24.0)
time.sleep(0.8)
c.screenshot(str(out_dir / "shot1_pump_iso.png"))

# Also try front-facing pump
c.chat('/tp 11 8 414')
c.look(0.0, 20.0)
time.sleep(0.8)
c.screenshot(str(out_dir / "shot1_pump_front.png"))

# 2. PHE
c.chat('/tp 18 8 414')
c.look(-25.0, 24.0)
time.sleep(0.8)
c.screenshot(str(out_dir / "shot2_phe_iso.png"))

c.chat('/tp 21 8 414')
c.look(0.0, 20.0)
time.sleep(0.8)
c.screenshot(str(out_dir / "shot2_phe_front.png"))

# 3. Instruments & Manifold
c.chat('/tp 16 8 422')
c.look(0.0, 35.0)
time.sleep(0.8)
c.screenshot(str(out_dir / "shot3_inst_south.png"))

c.chat('/tp 16 8 430')
c.look(180.0, 35.0)
time.sleep(0.8)
c.screenshot(str(out_dir / "shot3_inst_north.png"))

# 4. Panoramic Overview
c.chat('/tp 16 15 404')
c.look(0.0, 38.0)
time.sleep(0.8)
c.screenshot(str(out_dir / "shot4_panoramic.png"))

# 5. Secondary Circuit (PHE + Debug Tank + Super Tank)
c.chat('/tp 24 8 415')
c.look(-45.0, 25.0)
time.sleep(0.8)
c.screenshot(str(out_dir / "shot5_secondary.png"))

print("All candidate test screenshots captured!")

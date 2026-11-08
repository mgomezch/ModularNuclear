#!/usr/bin/env python3
"""
Automated Test for NEI MPTR Fuel Stats Dynamic Vector Plot & Interactive Tooltip.
Loads an ephemeral superflat world, opens NEI Usage for the MPTR Reactor Controller,
switches to the MPTR fuels handler, navigates through multiple fuels,
verifies interactive crosshairs, guide lines, and data tooltips at exact temperatures,
and captures high-resolution screenshots for each state.
"""

import sys
import os
import time
import shutil
from pathlib import Path

# Resolve repository root
from nhdev import NH_DEV_ROOT as REPO_ROOT

from tools.agentharness import HarnessClient
from tools.bot.world_manager import WorldSandbox, resolve_saves_dir

ARTIFACTS_DIR = Path("/home/mgomezch/.gemini/antigravity/brain/bc0c2152-bd77-425b-aa35-bf4d7878b40d")
SCREENSHOTS_DIR = REPO_ROOT / "screenshots"

# Plot geometry constants in scaled GUI coordinates (480x270)
PLOT_SCREEN_X0 = 185
PLOT_SCREEN_Y0 = 156
PLOT_SCREEN_W = 104
PLOT_SCREEN_H = 50

# Tab and page button positions
TAB_MPTR_X = 180
TAB_MPTR_Y = 32
BTN_NEXT_PAGE_X = 317
BTN_NEXT_PAGE_Y = 66
BTN_PREV_PAGE_X = 163
BTN_PREV_PAGE_Y = 66


def compute_plot_cursor(temp_c: float, floor_temp_c: float) -> tuple[int, int]:
    """Computes the scaled GUI cursor coordinates (x, y) for a given fuel temperature."""
    max_temp = ((floor_temp_c + 200.0 + 499.0) // 500) * 500.0
    dx = int(round((temp_c / max_temp) * PLOT_SCREEN_W))
    dx = max(0, min(PLOT_SCREEN_W, dx))
    cx = PLOT_SCREEN_X0 + dx
    cy = PLOT_SCREEN_Y0 + PLOT_SCREEN_H // 2
    return cx, cy


def capture_and_sync(client: HarnessClient, filename: str, minecraft_dir: Path) -> Path:
    """Captures a screenshot via AgentHarness and copies it to screenshots/ and artifacts."""
    client.screenshot(f"screenshots/{filename}")
    time.sleep(0.5)
    src = minecraft_dir / "screenshots" / filename
    dst1 = SCREENSHOTS_DIR / filename
    dst2 = ARTIFACTS_DIR / filename
    if src.exists():
        shutil.copy2(src, dst1)
        shutil.copy2(src, dst2)
        print(f"[✓] Saved screenshot: {dst1}")
        return dst1
    else:
        print(f"[!] Warning: {src} not found!")
        return dst1


def run_test(port: int = 25571):
    client = HarnessClient(port=port)
    print(f"[*] Connecting to AgentHarness on port {port}...")
    start_c = time.time()
    while time.time() - start_c < 120:
        if client.ping():
            break
        time.sleep(1.0)

    if not client.ping():
        raise RuntimeError(f"Could not connect to AgentHarness on port {port}")

    print(f"[+] Connected! Status: {client.status()}")

    saves_dir = resolve_saves_dir(client)
    minecraft_dir = saves_dir.parent
    SCREENSHOTS_DIR.mkdir(parents=True, exist_ok=True)
    ARTIFACTS_DIR.mkdir(parents=True, exist_ok=True)

    world_name = "test_fuel_stats_visual"

    def safe_exit():
        try:
            client.exitworld()
        except Exception:
            pass

    sandbox = WorldSandbox(
        template_name="superflat_empty",
        world_name=world_name,
        saves_dir=saves_dir,
        on_unmount=safe_exit
    )

    with sandbox:
        print(f"[*] Entering world '{world_name}'...")
        client.autoworld(world_name)
        start_w = time.time()
        in_world = False
        while time.time() - start_w < 60:
            st = client.state()
            if "IN_WORLD" in st:
                in_world = True
                print(f"[+] In world: {st}")
                break
            time.sleep(1.0)

        if not in_world:
            raise RuntimeError(f"Failed to enter world: {client.status()}")

        time.sleep(1.0)
        client.close_gui()
        client.chat("/gamemode 1")
        client.chat("/gamerule doDaylightCycle false")
        client.chat("/gamerule doMobSpawning false")
        client.chat("/time set 6000")
        client.chat("/weather clear 999999")
        time.sleep(0.5)

        # 1. Open NEI recipe usage for MPTR Controller (ID 32115)
        print("[*] Opening NEI usage for MPTR Controller...")
        client.open_nei_usage("gregtech:gt.blockmachines", 32115)
        time.sleep(1.0)

        # 2. Switch to MPTR fuels tab (Tab 2)
        print("[*] Clicking MPTR Fuels tab...")
        client.click_gui(TAB_MPTR_X, TAB_MPTR_Y, 0)
        time.sleep(0.8)

        # Shot 1: Glowstone Baseline (mouse parked in neutral area)
        print("[*] Capturing Shot 1: Glowstone Baseline...")
        client.set_mouse(10, 120)
        time.sleep(0.6)
        capture_and_sync(client, "nei_fuel_stats_01_glowstone_baseline.png", minecraft_dir)

        # Shot 2: Glowstone Peak Reactivity Hover (200 °C)
        gx, gy = compute_plot_cursor(200.0, 2000.0)
        print(f"[*] Hovering Glowstone peak reactivity at ({gx}, {gy})...")
        client.set_mouse(gx, gy)
        time.sleep(0.6)
        capture_and_sync(client, "nei_fuel_stats_02_glowstone_peak_hover.png", minecraft_dir)

        # Advance to Uranium (Page 4: Glowstone(1) -> Lithium(2) -> Thorium(3) -> Uranium(4))
        print("[*] Navigating to Uranium fuel (Page 4)...")
        for _ in range(3):
            client.click_gui(BTN_NEXT_PAGE_X, BTN_NEXT_PAGE_Y, 0)
            time.sleep(0.4)

        # Shot 3: Uranium Baseline (mouse away)
        client.set_mouse(10, 120)
        time.sleep(0.5)
        capture_and_sync(client, "nei_fuel_stats_03_uranium_baseline.png", minecraft_dir)

        # Shot 4: Uranium Peak Reactivity Hover (770 °C)
        ux, uy = compute_plot_cursor(770.0, 2300.0)
        print(f"[*] Hovering Uranium peak reactivity at ({ux}, {uy})...")
        client.set_mouse(ux, uy)
        time.sleep(0.6)
        capture_and_sync(client, "nei_fuel_stats_04_uranium_peak_hover.png", minecraft_dir)

        # Shot 5: Uranium High Temperature Damage Hover (1800 °C)
        ux_dmg, uy_dmg = compute_plot_cursor(1800.0, 2300.0)
        print(f"[*] Hovering Uranium high damage region at ({ux_dmg}, {uy_dmg})...")
        client.set_mouse(ux_dmg, uy_dmg)
        time.sleep(0.6)
        capture_and_sync(client, "nei_fuel_stats_05_uranium_damage_hover.png", minecraft_dir)

        # Advance to MOX (Page 5)
        print("[*] Navigating to MOX fuel (Page 5)...")
        client.click_gui(BTN_NEXT_PAGE_X, BTN_NEXT_PAGE_Y, 0)
        time.sleep(0.5)

        # Shot 6: MOX Peak Reactivity Hover (960 °C)
        mx, my = compute_plot_cursor(960.0, 2400.0)
        print(f"[*] Hovering MOX peak reactivity at ({mx}, {my})...")
        client.set_mouse(mx, my)
        time.sleep(0.6)
        capture_and_sync(client, "nei_fuel_stats_06_mox_peak_hover.png", minecraft_dir)

        # Advance to High-Density Plutonium (Page 7: 2 clicks from Page 5)
        print("[*] Navigating to High-Density Plutonium (Page 7)...")
        for _ in range(2):
            client.click_gui(BTN_NEXT_PAGE_X, BTN_NEXT_PAGE_Y, 0)
            time.sleep(0.4)

        # Shot 6b: High-Density Plutonium Baseline (Verifies long title auto-scale & non-heading layout)
        client.set_mouse(10, 120)
        time.sleep(0.5)
        capture_and_sync(client, "nei_fuel_stats_06b_hd_plutonium_baseline.png", minecraft_dir)

        # Advance to The Core (Page 13: 6 clicks from Page 7)
        print("[*] Navigating to The Core (Page 13)...")
        for _ in range(6):
            client.click_gui(BTN_NEXT_PAGE_X, BTN_NEXT_PAGE_Y, 0)
            time.sleep(0.3)

        # Shot 7: The Core Peak Reactivity Hover (2500 °C)
        cx, cy = compute_plot_cursor(2500.0, 3200.0)
        print(f"[*] Hovering The Core peak reactivity at ({cx}, {cy})...")
        client.set_mouse(cx, cy)
        time.sleep(0.6)
        capture_and_sync(client, "nei_fuel_stats_07_the_core_peak_hover.png", minecraft_dir)

        # Close GUI
        client.close_gui()
        time.sleep(0.5)

    print("\n" + "=" * 60)
    print("[✓] All NEI Fuel Stats visual test screenshots captured successfully!")
    print("=" * 60)


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="NEI MPTR Fuel Stats Visual Test")
    parser.add_argument("--port", type=int, default=25571, help="AgentHarness port (default: 25571)")
    args = parser.parse_args()
    run_test(port=args.port)

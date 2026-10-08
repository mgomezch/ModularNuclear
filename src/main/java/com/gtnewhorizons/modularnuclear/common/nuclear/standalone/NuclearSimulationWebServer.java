package com.gtnewhorizons.modularnuclear.common.nuclear.standalone;

import java.nio.charset.StandardCharsets;

import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;

/**
 * Standalone web export utility providing static HTML generation
 * for visual nuclear reactor design and client-side WebAssembly simulation.
 */
public class NuclearSimulationWebServer {

    private static StandaloneNuclearGrid grid;

    public static void main(String[] args) {
        if (args.length >= 2 && "--export-html".equals(args[0])) {
            try {
                java.io.File file = new java.io.File(args[1]);
                file.getParentFile()
                    .mkdirs();
                NuclearSimulationEngine.setGlobalThermalFissionMultiplier(0.351);
                NuclearSimulationEngine.fissionHeatPerNeutron = 80.1;
                grid = new StandaloneNuclearGrid(9, 9, NuclearSimulationEngine.PIPE_TIER_PLATINUM);
                grid.loadPreset("BEST_PLATINUM_9X9");
                String presetsJson = loadCeilingPresetsJson(args);
                String html = getIndexHtml()
                    .replace("/*__INITIAL_STATE__*/", "window.__INITIAL_STATE__ = " + getStateJson() + ";")
                    .replace("/*__CEILING_PRESETS__*/", "window.__CEILING_PRESETS__ = " + presetsJson + ";");
                java.nio.file.Files.write(file.toPath(), html.getBytes(StandardCharsets.UTF_8));
                System.out.println("Exported static simulator HTML to " + file.getAbsolutePath());
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
            return;
        }
        System.out.println("The Java server version of the simulator webapp has been retired.");
        System.out.println("The simulator is now an entirely client-side WebAssembly application.");
        System.out.println("Usage: --export-html <path/to/index.html> [--presets <path/to/nuclear_ceiling_best.json>]");
    }

    private static String loadCeilingPresetsJson(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--presets".equals(args[i])) {
                java.io.File f = new java.io.File(args[i + 1]);
                if (f.exists() && f.isFile()) {
                    try {
                        return new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
                    } catch (Exception ignored) {}
                }
            }
        }
        String[] candidatePaths = new String[] { "../../tools/nuclear_ceiling_best.json",
            "../tools/nuclear_ceiling_best.json", "tools/nuclear_ceiling_best.json",
            "/home/mgomezch/stuff/dev/nh-dev/tools/nuclear_ceiling_best.json" };
        for (String p : candidatePaths) {
            java.io.File f = new java.io.File(p);
            if (f.exists() && f.isFile()) {
                try {
                    return new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
                } catch (Exception ignored) {}
            }
        }
        return "[]";
    }

    public static String getStateJson() {
        return NuclearSimWasmBridge.buildStateJson(grid, false);
    }

    private static String getIndexHtml() {
        return getHtmlHead() + getHtmlBody() + getHtmlScriptsPart1() + getHtmlScriptsPart2() + getHtmlScriptsPart3();
    }

    private static String getHtmlHead() {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Modular Pressure Tube Reactor (MPTR) Simulator</title>
            <style>
              :root {
                --bg-dark: #0f141c;
                --panel-bg: #18202c;
                --border-color: #2b394e;
                --accent: #00d2ff;
                --accent-glow: rgba(0, 210, 255, 0.3);
                --danger: #ff4757;
                --warning: #ffa502;
                --success: #2ed573;
                --text-primary: #e1e7f0;
                --text-muted: #8899aa;
              }
              * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, monospace; }
              body { background: var(--bg-dark); color: var(--text-primary); min-height: 100vh; display: flex; flex-direction: column; }
              header { background: #131a24; border-bottom: 1px solid var(--border-color); padding: 12px 24px; display: flex; align-items: center; justify-content: space-between; }
              header h1 { font-size: 1.3rem; font-weight: 700; color: #fff; letter-spacing: 0.5px; display: flex; align-items: center; gap: 10px; }
              .badge { background: #1e293b; border: 1px solid var(--border-color); color: var(--accent); padding: 4px 10px; border-radius: 4px; font-size: 0.8rem; }
              .container { display: flex; flex: 1; padding: 16px; gap: 16px; }
              .panel { background: var(--panel-bg); border: 1px solid var(--border-color); border-radius: 8px; padding: 16px; display: flex; flex-direction: column; }
              .grid-panel { flex: 2; align-items: center; }
              .side-panel { flex: 1; min-width: 320px; gap: 16px; }
              .telemetry-panel { flex: 1; min-width: 320px; gap: 12px; }

              /* Touch & Interaction */
              button, select, input, .cell, .palette-btn {
                touch-action: manipulation;
                -webkit-tap-highlight-color: transparent;
              }

              /* Toolbar */
              .toolbar { display: flex; gap: 8px; margin-bottom: 16px; flex-wrap: wrap; width: 100%; align-items: center; }
              button { background: #233044; border: 1px solid var(--border-color); color: #fff; padding: 8px 14px; border-radius: 6px; cursor: pointer; font-weight: 600; font-size: 0.85rem; min-height: 38px; transition: all 0.15s; }
              button:hover { background: #2d3e58; border-color: var(--accent); }
              button.primary { background: #0077b6; border-color: #0096c7; }
              button.primary:hover { background: #0096c7; }
              button.danger { background: #b91c1c; border-color: #dc2626; }
              select { background: #233044; border: 1px solid var(--border-color); color: #fff; padding: 8px; border-radius: 6px; font-size: 0.85rem; min-height: 38px; cursor: pointer; }
              /* Sim Options Bar */
              .sim-options-bar {
                display: flex;
                gap: 16px;
                align-items: center;
                flex-wrap: wrap;
                background: #0f172a;
                border: 1px solid #1e293b;
                border-radius: 6px;
                padding: 6px 12px;
                margin-bottom: 12px;
                font-size: 0.8rem;
              }
              .sim-options-label {
                color: #94a3b8;
                font-weight: 700;
                text-transform: uppercase;
                font-size: 0.7rem;
                letter-spacing: 0.5px;
              }
              .sim-toggle-label {
                display: inline-flex;
                align-items: center;
                gap: 6px;
                cursor: pointer;
                color: var(--text-primary);
                user-select: none;
                transition: color 0.15s;
              }
              .sim-toggle-label:hover {
                color: #fff;
              }

              /* Grid Toolbar & Controls */
              .grid-toolbar {
                display: flex;
                justify-content: space-between;
                align-items: center;
                width: 100%;
                margin: 4px 0 8px 0;
                flex-wrap: wrap;
                gap: 8px;
              }
              .grid-toolbar-title {
                font-size: 0.85rem;
                font-weight: 700;
                color: #94a3b8;
                display: flex;
                align-items: center;
                gap: 8px;
              }
              .display-mode-selector {
                display: flex;
                align-items: center;
                gap: 6px;
                background: #131a24;
                padding: 4px 8px;
                border-radius: 6px;
                border: 1px solid var(--border-color);
              }
              .mode-label {
                font-size: 0.75rem;
                color: var(--text-muted);
                font-weight: 600;
                margin-right: 2px;
                user-select: none;
              }
              .segmented-control {
                display: inline-flex;
                background: #0d121a;
                border: 1px solid #373737;
                border-radius: 4px;
                padding: 2px;
                gap: 2px;
              }
              .seg-btn {
                background: transparent;
                border: none;
                color: #94a3b8;
                padding: 4px 8px;
                border-radius: 3px;
                font-size: 0.75rem;
                font-weight: 700;
                cursor: pointer;
                transition: all 0.15s ease;
                display: inline-flex;
                align-items: center;
                gap: 4px;
                user-select: none;
              }
              .seg-btn:hover {
                color: #fff;
                background: rgba(255, 255, 255, 0.08);
              }
              .seg-btn.active {
                background: #2563eb;
                color: #ffffff;
                box-shadow: 0 1px 3px rgba(0, 0, 0, 0.4);
              }
              .zoom-controls {
                display: flex;
                align-items: center;
                gap: 5px;
                background: #131a24;
                padding: 4px 8px;
                border-radius: 6px;
                border: 1px solid var(--border-color);
              }
              .zoom-label {
                font-size: 0.75rem;
                color: var(--text-muted);
                font-weight: 600;
                margin-right: 2px;
                user-select: none;
              }
              .zoom-btn {
                background: #1e293b;
                border: 1px solid var(--border-color);
                color: #fff;
                padding: 4px 10px;
                border-radius: 4px;
                font-size: 0.75rem;
                font-weight: 700;
                cursor: pointer;
                min-height: 28px;
                display: inline-flex;
                align-items: center;
                justify-content: center;
                transition: all 0.15s;
                user-select: none;
              }
              .zoom-btn:hover {
                background: #2d3e58;
                border-color: var(--accent);
              }
              .zoom-btn.active {
                background: var(--accent);
                border-color: #38bdf8;
                color: #0b1320;
              }
              #zoom-level-btn {
                min-width: 48px;
                text-align: center;
              }
              .grid-container-wrapper {
                width: 100%;
                overflow-x: auto;
                overflow-y: hidden;
                display: flex;
                justify-content: center;
                padding: 4px 0;
                -webkit-overflow-scrolling: touch;
              }

              /* Core Grid - In-Game Chamber Style */
              #reactor-grid {
                --cell-size: 56px;
                --cell-gap: 2px;
                --cell-code-size: 0.8rem;
                --cell-temp-size: 0.65rem;
                display: grid;
                gap: var(--cell-gap);
                background: #14171d;
                padding: 4px;
                border-radius: 2px;
                border: 2px solid #373737;
                box-shadow: inset 1px 1px 0px #0a0c10, inset -1px -1px 0px #2a2e38, 0 4px 16px rgba(0,0,0,0.5);
                user-select: none;
                transition: gap 0.12s ease;
                margin: 0 auto;
              }
              .cell {
                width: var(--cell-size);
                height: var(--cell-size);
                border-radius: 0px;
                display: flex;
                flex-direction: column;
                align-items: center;
                justify-content: center;
                font-weight: bold;
                cursor: pointer;
                background-color: #1e1e1e;
                border: 1px solid #373737;
                box-shadow: inset 1px 1px 0px rgba(0,0,0,0.8), inset -1px -1px 0px rgba(255,255,255,0.06);
                position: relative;
                user-select: none;
                overflow: hidden;
                box-sizing: border-box;
                touch-action: none;
              }
              @media (hover: hover) {
                .cell:hover::after {
                  content: '';
                  position: absolute;
                  top: 0; left: 0; right: 0; bottom: 0;
                  background: rgba(255, 255, 255, 0.22);
                  pointer-events: none;
                  z-index: 5;
                }
              }
              .cell.selected {
                outline: 2px solid #58a6ff;
                outline-offset: -1px;
                box-shadow: 0 0 8px rgba(88, 166, 255, 0.6);
                z-index: 6;
              }
              .cell.loop-attached {
                outline: 2px solid #00d2ff !important;
                outline-offset: -1px;
                box-shadow: inset 0 0 8px rgba(0, 210, 255, 0.45), 0 0 6px rgba(0, 210, 255, 0.6) !important;
                z-index: 5;
              }
              .cell.loop-attached .loop-badge {
                display: block !important;
              }
              .cell .loop-badge {
                display: none;
                position: absolute;
                top: 1px;
                right: 2px;
                font-size: 10px;
                color: #00d2ff;
                font-weight: 900;
                line-height: 1;
                text-shadow: 0 1px 2px #000;
                pointer-events: none;
                z-index: 4;
              }
              .cell.has-rod .rod-badge {
                display: block !important;
              }
              .cell .rod-badge {
                display: none;
                position: absolute;
                bottom: 1px;
                right: 2px;
                font-size: 10px;
                color: #00e5ff;
                font-weight: 900;
                line-height: 1;
                text-shadow: 0 1px 2px #000;
                pointer-events: none;
                z-index: 4;
              }
              .cell.rod-mode-active {
                outline: 2px solid #00e5ff !important;
                outline-offset: -1px;
                box-shadow: inset 0 0 6px rgba(0, 229, 255, 0.45) !important;
              }
              .cell.rod-mode-empty {
                outline: 1px dashed #64748b !important;
                outline-offset: -1px;
              }
              .cell.rod-mode-dimmed {
                opacity: 0.35;
              }
              .cell.wall-cell {
                visibility: hidden !important;
                background: transparent !important;
                border: none !important;
                box-shadow: none !important;
                pointer-events: none !important;
              }
              .cell .cell-overlay {
                position: absolute;
                top: 0; left: 0; right: 0; bottom: 0;
                pointer-events: none;
                z-index: 2;
                transition: background-color 0.1s ease;
              }
              .cell .cell-icon {
                width: calc(var(--cell-size) * 0.72);
                height: calc(var(--cell-size) * 0.72);
                image-rendering: pixelated;
                image-rendering: -moz-crisp-edges;
                image-rendering: crisp-edges;
                pointer-events: none;
                z-index: 1;
                filter: drop-shadow(0 1px 2px rgba(0,0,0,0.6));
              }
              .cell .cell-temp, .cell .cell-metric {
                position: absolute;
                bottom: 2px;
                font-size: var(--cell-temp-size);
                opacity: 0.95;
                line-height: 1;
                z-index: 3;
                text-shadow: 0 1px 2px #000, 0 0 2px #000;
                font-weight: 800;
                letter-spacing: -0.3px;
                pointer-events: none;
                color: #ffffff;
                text-align: center;
                width: 100%;
              }
              .cell .cell-code { font-size: var(--cell-code-size); line-height: 1.1; z-index: 3; }
              .cell .cell-durability {
                position: absolute;
                bottom: 1px;
                left: 2px;
                right: 2px;
                height: 3px;
                background: rgba(0,0,0,0.8);
                border-radius: 1px;
                z-index: 4;
                overflow: hidden;
              }
              .cell .cell-durability-fill {
                height: 100%;
                border-radius: 1px;
                transition: width 0.15s ease;
              }

              /* Palette */
              .palette { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; }
              .palette-btn { padding: 6px 8px; text-align: left; font-size: 0.75rem; border-radius: 4px; display: flex; align-items: center; gap: 8px; min-height: 38px; }
              .palette-btn.active { border-color: var(--accent); background: #29384f; box-shadow: inset 0 0 5px var(--accent-glow); }
              .palette-icon {
                width: 22px;
                height: 22px;
                image-rendering: pixelated;
                image-rendering: -moz-crisp-edges;
                image-rendering: crisp-edges;
                flex-shrink: 0;
              }
              .palette-empty-icon {
                width: 22px;
                height: 22px;
                border: 1px dashed rgba(255,255,255,0.25);
                border-radius: 3px;
              }
              .inspector-icon {
                width: 32px;
                height: 32px;
                image-rendering: pixelated;
                image-rendering: -moz-crisp-edges;
                image-rendering: crisp-edges;
                background: rgba(0,0,0,0.3);
                border-radius: 4px;
                padding: 2px;
                border: 1px solid var(--border-color);
              }

              /* Stats display */
              .stat-row { display: flex; justify-content: space-between; padding: 6px 0; border-bottom: 1px solid rgba(255,255,255,0.05); font-size: 0.85rem; }
              .stat-val { font-weight: 700; color: #fff; }
              .stat-val.safe { color: var(--success); }
              .stat-val.warn { color: var(--warning); }
              .stat-val.danger { color: var(--danger); }

              /* Alert banner & Incident warnings */
              @keyframes alert-pulse {
                0% { box-shadow: 0 0 10px rgba(255, 71, 87, 0.6); }
                50% { box-shadow: 0 0 22px rgba(255, 165, 2, 0.8); }
                100% { box-shadow: 0 0 10px rgba(255, 71, 87, 0.6); }
              }
              @keyframes warning-flash {
                0% { background-color: rgba(255, 71, 87, 0.5); transform: scale(1.008); }
                100% { background-color: rgba(255, 165, 2, 0.15); transform: scale(1); }
              }
              #alert-box {
                display: none;
                padding: 12px 16px;
                border-radius: 6px;
                margin-bottom: 12px;
                width: 100%;
                font-size: 0.85rem;
                transition: all 0.3s ease;
              }
              #alert-box.alert-danger {
                background: rgba(255, 71, 87, 0.25);
                border: 1px solid var(--danger);
                color: #ffb8b8;
                animation: alert-pulse 1s infinite;
              }
              #alert-box.alert-warning {
                background: rgba(255, 165, 2, 0.15);
                border: 1px solid var(--warning);
                color: #fed7aa;
                animation: alert-pulse 1.8s infinite;
              }
              #alert-box.flash {
                animation: warning-flash 0.4s ease-out;
              }

              /* Incident Log Card & Entries */
              .incident-log-container {
                width: 100%;
                background: #131a24;
                border: 1px solid var(--border-color);
                border-radius: 6px;
                padding: 10px 14px;
                margin-top: 12px;
              }
              .incident-entry {
                display: flex;
                align-items: baseline;
                gap: 8px;
                padding: 4px 6px;
                border-radius: 4px;
                background: #0f172a;
                border-left: 3px solid #64748b;
                font-size: 0.76rem;
                line-height: 1.3;
              }
              .incident-entry.thermal-shock {
                border-left-color: #f59e0b;
                background: rgba(245, 158, 11, 0.08);
              }
              .incident-entry.casing-overheat {
                border-left-color: #ef4444;
                background: rgba(239, 68, 68, 0.08);
              }
              .incident-entry.fuel-burned {
                border-left-color: #dc2626;
                background: rgba(220, 38, 38, 0.1);
              }
              .incident-entry.hatch-voided {
                border-left-color: #38bdf8;
                background: rgba(56, 189, 248, 0.08);
              }
              .incident-entry.meltdown {
                border-left-color: #b91c1c;
                background: rgba(185, 28, 28, 0.15);
                color: #fca5a5;
                font-weight: 600;
              }
              .incident-entry.repair {
                border-left-color: #10b981;
                background: rgba(16, 185, 129, 0.08);
                color: #a7f3d0;
              }
              .incident-tick {
                color: #94a3b8;
                font-family: monospace;
                font-weight: 700;
                min-width: 65px;
              }
              .incident-badge-type {
                font-size: 0.68rem;
                padding: 1px 6px;
                border-radius: 3px;
                font-weight: 700;
                letter-spacing: 0.3px;
                text-transform: uppercase;
              }

              /* Canvas Chart */
              canvas#chartCanvas { width: 100%; height: 160px; background: #0a0d13; border-radius: 6px; border: 1px solid var(--border-color); }

              /* Mobile & Responsive Layout */
              @media (max-width: 1024px) {
                header {
                  padding: 12px 16px;
                  flex-direction: column;
                  align-items: flex-start;
                  gap: 10px;
                }
                .container {
                  flex-direction: column;
                  padding: 10px;
                  gap: 14px;
                }
                .grid-panel, .side-panel, .telemetry-panel {
                  min-width: 0;
                  width: 100%;
                }
                .grid-panel { order: 1; }
                .side-panel { order: 2; }
                .telemetry-panel { order: 3; }
                .toolbar {
                  gap: 6px;
                }
                .toolbar button, .toolbar select {
                  flex: 1 1 calc(50% - 6px);
                  font-size: 0.8rem;
                  padding: 8px 10px;
                }
                .grid-toolbar {
                  flex-direction: column;
                  align-items: stretch;
                  gap: 6px;
                }
                .zoom-controls {
                  width: 100%;
                  justify-content: space-between;
                }
                .zoom-controls .zoom-btn {
                  flex: 1;
                  min-height: 36px;
                  font-size: 0.85rem;
                }
                #reactor-grid {
                  max-width: 100%;
                  margin: 6px auto;
                }
              }
            </style>
            <script src="nuclear-sim-bridge.js"></script>
            </head>
            """;
    }

    private static String getHtmlBody() {
        return """
            <body>
            <header>
              <h1><span>⚛</span> Modular Pressure Tube Reactor (MPTR) Standalone Simulator</h1>
              <div style="display:flex; gap:10px; align-items:center;">
                <span class="badge" id="engine-badge" style="background:#065f46; border-color:#059669; color:#34d399;" title="Simulation running client-side via WebAssembly">⚡ Client WASM (0ms)</span>
                <span class="badge" id="casing-badge">Electrum Casing (1000°C Max)</span>
                <span class="badge" id="damage-badge" style="display:none; color:var(--danger); border-color:var(--danger);" title="Cumulative Reactor Structural Damage">Damage: 0.0%</span>
                <span class="badge" id="maint-badge" style="color:var(--success);" title="Maintenance Health (Efficiency Penalty)">Maint: 100%</span>
                <button id="btn-quick-repair" onclick="quickRepair()" style="display:none; padding:2px 8px; font-size:0.75rem; background:#1e293b; border-color:var(--warning); color:var(--warning);" title="Repair structural damage and clear maintenance issues">🔧 Repair</button>
                <span class="badge" id="status-badge" style="color:var(--success);">STATUS: STANDBY</span>
              </div>
            </header>

            <div class="container">
              <!-- Reactor Grid View -->
              <div class="panel grid-panel">
                <div id="alert-box"></div>
                <div class="toolbar">
                  <button class="primary" id="btn-play" onclick="togglePlay()">▶ Start Simulation</button>
                  <button onclick="stepSim(1)">Step +1</button>
                  <button onclick="stepSim(10)">Step +10</button>
                  <button onclick="stepSim(100)">Step +100</button>
                  <button class="danger" onclick="quickScram()" title="Emergency SCRAM: Insert all control rods to 100% [Key: s]">🚨 SCRAM</button>
                  <button class="danger" onclick="resetSim()">↺ Reset</button>
                  <select id="preset-select" onchange="loadPreset(this.value)">
                    <optgroup label="⚡ Calibrated 60A Baseline Designs">
                      <option value="60A_ELECTRUM_5X5">EV 60A: Electrum 5x5 (123k EU/t · 60.0A)</option>
                      <option value="60A_PLATINUM_9X9">IV 60A: Platinum 9x9 Breeder (492k EU/t · 60.0A)</option>
                      <option value="60A_OSMIUM_9X9">LuV 60A: Osmium 9x9 Superheated (1.97M EU/t · 60.0A)</option>
                      <option value="60A_QUANTIUM_13X13">ZPM 60A: Quantium 13x13 CANDU (7.86M EU/t · 60.0A)</option>
                      <option value="60A_FLUXED_13X13">UV 60A: Fluxed 13x13 Supercritical (31.5M EU/t · 60.0A)</option>
                      <option value="60A_PLUTONIUM_13X13">UHV 60A: Black Plutonium 13x13 Peak (126M EU/t · 60.0A)</option>
                    </optgroup>
                    <optgroup id="ceiling-presets-optgroup" label="🏆 Maxxed-Out Optimum Ceilings (nuclear_ceiling_best.json)">
                      <option value="BEST_ELECTRUM_5X5">EV Peak: Electrum 5x5 (565k EU/t · 276A)</option>
                      <option value="BEST_PLATINUM_9X9" selected>IV Peak: Platinum 9x9 (5.08M EU/t · 620A)</option>
                      <option value="BEST_OSMIUM_9X9">LuV Peak: Osmium 9x9 (9.46M EU/t · 289A)</option>
                      <option value="BEST_QUANTIUM_13X13">ZPM Peak: Quantium 13x13 (51.9M EU/t · 396A)</option>
                      <option value="BEST_FLUXED_13X13">UV Peak: Fluxed 13x13 (112M EU/t · 213A)</option>
                      <option value="BEST_PLUTONIUM_13X13">UHV Peak: Black Plutonium 13x13 (141M EU/t · 67A)</option>
                    </optgroup>
                  </select>
                  <select id="tier-select" onchange="changeTier(this.value)" title="Reactor Pipe & Casing Tier">
                    <option value="0">Electrum (1200°C)</option>
                    <option value="1" selected>Platinum (1400°C)</option>
                    <option value="2">Osmium (1800°C)</option>
                    <option value="3">Quantium (2200°C)</option>
                    <option value="4">Fluxed Electrum (2600°C)</option>
                    <option value="5">Black Plutonium (3200°C)</option>
                  </select>
                  <div style="display:inline-flex; align-items:center; gap:4px;">
                    <label for="voltage-tier-select" style="font-size:0.75rem; color:var(--text-muted); font-weight:600;">Voltage:</label>
                    <select id="voltage-tier-select" onchange="onVoltageTierChange(this.value)" title="Voltage tier for live amperage conversion">
                      <option value="ULV">ULV (8 V)</option>
                      <option value="LV">LV (32 V)</option>
                      <option value="MV">MV (128 V)</option>
                      <option value="HV">HV (512 V)</option>
                      <option value="EV">EV (2,048 V)</option>
                      <option value="IV" selected>IV (8,192 V)</option>
                      <option value="LuV">LuV (32,768 V)</option>
                      <option value="ZPM">ZPM (131,072 V)</option>
                      <option value="UV">UV (524,288 V)</option>
                      <option value="UHV">UHV (2,097,152 V)</option>
                      <option value="UEV">UEV (8,388,608 V)</option>
                      <option value="UIV">UIV (33,554,432 V)</option>
                      <option value="UMV">UMV (134,217,728 V)</option>
                      <option value="UXV">UXV (536,870,912 V)</option>
                      <option value="MAX">MAX (2,147,483,647 V)</option>
                    </select>
                  </div>
                </div>

                <div class="sim-options-bar">
                  <span class="sim-options-label">Simulation Options:</span>
                  <label class="sim-toggle-label" title="Automatically pause simulation whenever an incident occurs (overheated cell, dry thermal shock, or reactor damage)">
                    <input type="checkbox" id="chk-stop-incidents" onchange="toggleStopOnIncidents(this.checked)" style="cursor:pointer; accent-color:var(--warning);">
                    <span>🛑 Stop on Incidents</span>
                    <span id="lbl-stop-incidents" style="font-size:0.7rem; color:var(--text-muted); font-weight:normal;">(Off)</span>
                  </label>
                  <label class="sim-toggle-label" title="Continuously supplies fresh 100%-durability fuel rods when spent or burned, and auto-replenishes liquid fuel hatches to maximum">
                    <input type="checkbox" id="chk-auto-supply-fuel" checked onchange="toggleAutoSupplyFuel(this.checked)" style="cursor:pointer; accent-color:#34d399;">
                    <span>🔄 Auto-Supply Fuel</span>
                    <span id="lbl-auto-supply-fuel" style="font-size:0.7rem; color:#34d399; font-weight:600;">(Enabled)</span>
                  </label>
                </div>

                <div class="grid-toolbar">
                  <div class="grid-toolbar-title">
                    <span id="grid-dim-label">Chamber Grid (9×9)</span>
                  </div>

                  <div class="display-mode-selector">
                    <span class="mode-label">Mode:</span>
                    <div class="segmented-control">
                      <button type="button" class="seg-btn" id="btn-mode-comp" onclick="setGridDisplayMode('COMPONENTS')" title="Show Component Icons [Key: 1]">📦 Components</button>
                      <button type="button" class="seg-btn active" id="btn-mode-temp" onclick="setGridDisplayMode('TEMP')" title="Show Cell Temperature (°C) [Key: 2]">🌡️ Temp</button>
                      <button type="button" class="seg-btn" id="btn-mode-flux" onclick="setGridDisplayMode('TOTAL_FLUX')" title="Show Total Neutron Flux [Key: 3]">⚛️ Flux</button>
                      <button type="button" class="seg-btn" id="btn-mode-absorb" onclick="setGridDisplayMode('ABSORPTION')" title="Show Neutron Absorption [Key: 4]">🛡️ Absorption</button>
                      <button type="button" class="seg-btn" id="btn-mode-rods" onclick="setGridDisplayMode('CONTROL_RODS')" title="Show Control Rod Hatches [Key: 5]">🕹️ Control Rods</button>
                    </div>
                  </div>

                  <div class="zoom-controls">
                    <span class="zoom-label">Zoom:</span>
                    <button type="button" class="zoom-btn" onclick="zoomGrid(-1)" title="Zoom Out (− or Key: -)">🔍−</button>
                    <button type="button" class="zoom-btn" id="zoom-level-btn" onclick="resetZoom()" title="Reset to 100% (Key: 0)">100%</button>
                    <button type="button" class="zoom-btn" onclick="zoomGrid(1)" title="Zoom In (+ or Key: +)">🔍+</button>
                    <button type="button" class="zoom-btn fit-btn" onclick="fitGridToScreen()" title="Auto-Fit Grid to Screen (Key: f)">📐 Fit</button>
                  </div>
                </div>

                <div class="grid-container-wrapper">
                  <div id="reactor-grid"></div>
                </div>

                <!-- Incident & Event Log Panel -->
                <div class="incident-log-container" id="incident-log-container">
                  <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:8px;">
                    <div style="display:flex; align-items:center; gap:8px;">
                      <h3 style="font-size:0.85rem; color:#f59e0b; margin:0;">📋 Incident &amp; Event Log</h3>
                      <span class="badge" id="incident-count-badge" style="font-size:0.7rem; color:#f59e0b; border-color:#f59e0b;">0 events</span>
                    </div>
                    <div style="display:flex; gap:6px;">
                      <button type="button" onclick="quickRepair()" style="padding:2px 8px; font-size:0.72rem; background:#1e293b; border-color:var(--warning); color:var(--warning);" title="Repair structural damage and clear maintenance issues">🔧 Quick Repair</button>
                      <button type="button" onclick="clearIncidentLog()" style="padding:2px 8px; font-size:0.72rem;" title="Clear incident history">Clear Log</button>
                    </div>
                  </div>
                  <div id="incident-log-list" style="max-height:130px; overflow-y:auto; display:flex; flex-direction:column; gap:4px;">
                    <div style="font-style:italic; font-size:0.75rem; color:#64748b; padding:4px 6px;">No incidents recorded. Chamber operating nominally.</div>
                  </div>
                </div>
              </div>

              <!-- Palette & Inspector -->
              <div class="panel side-panel">
                <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:8px;">
                  <h3>Interaction Mode</h3>
                  <span class="badge" id="mode-badge" style="color:var(--accent);">INSPECT ONLY</span>
                </div>
                <div style="display:flex; gap:8px; margin-bottom:12px;">
                  <button id="btn-mode-inspect" class="primary" onclick="setInteractionMode('INSPECT')" style="flex:1;" title="Click cells to view live details without modifying (Shortcut: 'i' or Esc)">🔍 Inspect Only</button>
                  <button id="btn-mode-paint" onclick="setInteractionMode('PAINT')" style="flex:1;" title="Click cells to place selected component (Shortcut: 'p')">🖌️ Paint / Place</button>
                </div>

                <h3>Tile Palette</h3>
                <p id="palette-hint" style="font-size:0.75rem; color:var(--text-muted); margin-bottom:8px;">Select a component below to enter Paint Mode, or use Inspect Mode to safely check hatches.</p>
                <div class="palette" id="palette-container"></div>

                <hr style="border:0; border-top:1px solid var(--border-color); margin:12px 0;">

                <h3>Selected Tile Inspector</h3>
                <div id="tile-inspector" style="font-size:0.85rem; color:var(--text-muted);">
                  Click any grid cell to inspect its live telemetry and coolant levels.
                </div>

                <hr style="border:0; border-top:1px solid var(--border-color); margin:12px 0;">

                <div style="background:#131a24; border:1px solid var(--border-color); border-radius:6px; padding:12px;">
                  <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:8px;">
                    <h3 style="font-size:0.95rem; color:var(--warning);">⚙️ Simulation Tuning</h3>
                    <button onclick="resetSimParams()" style="padding:2px 8px; font-size:0.75rem;" title="Reset parameters to defaults">↺ Reset</button>
                  </div>
                  <div style="margin-bottom:8px;">
                    <label style="font-size:0.75rem; color:var(--text-muted); display:block; margin-bottom:2px;">Tuning Profile:</label>
                    <select id="sim-profile-select" onchange="applyProfilePreset(this.value)" style="width:100%;">
                      <option value="BALANCED" selected>Balanced / Default</option>
                      <option value="DANGER">Meltdown Hazard (High Danger)</option>
                      <option value="FLASH_BOIL">Flash Boiling (Low Capacity / Fast Turnover)</option>
                      <option value="SAFE">Conservative / Low Heat</option>
                    </select>
                  </div>
                  <div style="display:grid; grid-template-columns: 1fr 1fr; gap:6px; margin-bottom:6px;">
                    <div>
                      <label style="font-size:0.7rem; color:var(--text-muted);">EU per Degree (°C):</label>
                      <input id="p-eu-per-degree" type="number" step="1.0" value="32.0" onchange="submitSimParams()" style="width:100%; background:#233044; border:1px solid var(--border-color); color:#fff; padding:6px; border-radius:6px; font-size:0.8rem;">
                    </div>
                    <div>
                      <label style="font-size:0.7rem; color:var(--text-muted);">Base Conductance:</label>
                      <input id="p-base-conductance" type="number" step="0.5" value="2.0" onchange="submitSimParams()" title="Base hatch conductance in EU/(t·°C)" style="width:100%; background:#233044; border:1px solid var(--border-color); color:#fff; padding:6px; border-radius:6px; font-size:0.8rem;">
                    </div>
                  </div>
                  <div style="display:grid; grid-template-columns: 1fr 1fr; gap:6px; margin-bottom:6px;">
                    <div>
                      <label style="font-size:0.7rem; color:var(--text-muted);">Fission Heat (EU/n):</label>
                      <input id="p-fission-heat" type="number" step="0.1" value="77.2" onchange="submitSimParams()" style="width:100%; background:#233044; border:1px solid var(--border-color); color:#fff; padding:6px; border-radius:6px; font-size:0.8rem;">
                    </div>
                    <div>
                      <label style="font-size:0.7rem; color:var(--text-muted);">Global Fission Mult (k):</label>
                      <input id="p-fiss-mult" type="number" step="0.005" value="0.348" onchange="submitSimParams()" style="width:100%; background:#233044; border:1px solid var(--border-color); color:#fff; padding:6px; border-radius:6px; font-size:0.8rem;">
                    </div>
                  </div>
                  <div style="display:grid; grid-template-columns: 1fr 1fr; gap:6px; margin-bottom:6px;">
                    <div>
                      <label style="font-size:0.7rem; color:var(--text-muted);">Ambient Temp (°C):</label>
                      <input id="p-ambient-temp" type="number" step="1.0" value="20.0" onchange="submitSimParams()" style="width:100%; background:#233044; border:1px solid var(--border-color); color:#fff; padding:6px; border-radius:6px; font-size:0.8rem;">
                    </div>
                  <div style="display:grid; grid-template-columns: 1fr 1fr; gap:6px; margin-bottom:6px;">
                    <div>
                      <label style="font-size:0.7rem; color:var(--text-muted);">Hatch Capacity (L):</label>
                      <input id="p-hatch-cap" type="number" step="1000" value="8000" onchange="submitSimParams()" style="width:100%; background:#233044; border:1px solid var(--border-color); color:#fff; padding:6px; border-radius:6px; font-size:0.8rem;">
                    </div>
                    <div>
                      <label style="font-size:0.7rem; color:var(--text-muted);">Feed Rate (L/t):</label>
                      <select id="p-feed-rate" onchange="submitSimParams()" style="width:100%;">
                        <option value="500">500 L/t</option>
                        <option value="1000">1,000 L/t</option>
                        <option value="2000">2,000 L/t</option>
                        <option value="4000">4,000 L/t</option>
                        <option value="8000">8,000 L/t</option>
                        <option value="999999" selected>Instant Max</option>
                      </select>
                    </div>
                  </div>
                  <div style="display:grid; grid-template-columns: 1fr 1fr; gap:6px; margin-bottom:6px;">
                    <div>
                      <label style="font-size:0.7rem; color:var(--text-muted);">Turnover Curve:</label>
                      <select id="p-turn-curve" onchange="submitSimParams()" style="width:100%;">
                        <option value="SIGMOID" selected>Sigmoid (S-Curve)</option>
                        <option value="EXPONENTIAL">Exponential</option>
                        <option value="LINEAR">Linear</option>
                        <option value="STEP">Step-Based</option>
                      </select>
                    </div>
                    <div>
                      <label style="font-size:0.7rem; color:var(--text-muted);">Turnover &Delta;T Max:</label>
                      <input id="p-turn-dt" type="number" step="10" value="100" onchange="submitSimParams()" style="width:100%; background:#233044; border:1px solid var(--border-color); color:#fff; padding:6px; border-radius:6px; font-size:0.8rem;">
                    </div>
                  </div>
                  <div style="display:grid; grid-template-columns: 1fr 1fr; gap:6px; margin-bottom:6px;">
                    <div>
                      <label style="font-size:0.7rem; color:var(--text-muted);">Turnover Exponent:</label>
                      <input id="p-turn-exp" type="number" step="0.1" value="1.0" onchange="submitSimParams()" style="width:100%; background:#233044; border:1px solid var(--border-color); color:#fff; padding:6px; border-radius:6px; font-size:0.8rem;">
                    </div>
                    <div>
                      <label style="font-size:0.7rem; color:var(--text-muted);">HP Boil Point (°C):</label>
                      <input id="p-hp-boil" type="number" step="10" value="180" onchange="submitSimParams()" style="width:100%; background:#233044; border:1px solid var(--border-color); color:#fff; padding:6px; border-radius:6px; font-size:0.8rem;">
                    </div>
                  </div>
                  <div style="display:grid; grid-template-columns: 1fr 1fr; gap:6px; margin-top:6px;">
                    <div>
                      <label style="font-size:0.7rem; color:var(--text-muted);">Water Heat (EU/L):</label>
                      <input id="p-cooling-heat" type="number" step="0.5" value="5.0" onchange="submitSimParams()" style="width:100%; background:#233044; border:1px solid var(--border-color); color:#fff; padding:6px; border-radius:6px; font-size:0.8rem;">
                    </div>
                    <div>
                      <label style="font-size:0.7rem; color:var(--text-muted);">IC2 Heat (EU/L):</label>
                      <input id="p-ic2-cooling-heat" type="number" step="1.0" value="20.0" onchange="submitSimParams()" style="width:100%; background:#233044; border:1px solid var(--border-color); color:#fff; padding:6px; border-radius:6px; font-size:0.8rem;">
                    </div>
                  </div>
                </div>
              </div>

              <!-- Telemetry & Live Graphs -->
              <div class="panel telemetry-panel">
                <!-- Coolant Loop (Tier 2+ Convective Subsystem) -->
                <div id="coolant-loop-card" style="background:#131a24; border:1px solid #00d2ff; border-radius:6px; padding:12px; margin-bottom:12px;">
                  <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:4px;">
                    <h3 style="font-size:0.95rem; color:#00d2ff; margin:0;">🔄 Coolant Loop Subsystem</h3>
                    <span class="badge" id="loop-status-badge" style="font-weight:bold; color:var(--success);">NORMAL</span>
                  </div>
                  <div style="font-size:0.75rem; color:var(--text-muted); margin-bottom:8px;">
                    Per-cell convective cooling for <b style="color:#00d2ff;">Coolant Passage</b> tiles or attached cells.
                  </div>
                  <div id="loop-tier-warning" style="display:none; font-size:0.72rem; color:#fbbf24; background:rgba(251,191,36,0.15); border:1px solid #fbbf24; border-radius:4px; padding:6px; margin-bottom:8px;">
                    ⚠️ Convective loops require Tier 2+ (IV Platinum 9x9 or LuV Osmium 9x9+). Neutronium pipes are forbidden in Tier 2!
                  </div>
                  <div style="display:grid; grid-template-columns: 1fr 1fr; gap:6px; margin-bottom:6px;">
                    <div>
                      <label style="font-size:0.7rem; color:var(--text-muted);">Loop Material:</label>
                      <select id="loop-material-select" onchange="onCoolantLoopConfigChange()" style="width:100%;"></select>
                    </div>
                    <div>
                      <label style="font-size:0.7rem; color:var(--text-muted);">Pipe Size:</label>
                      <select id="loop-size-select" onchange="onCoolantLoopConfigChange()" style="width:100%;">
                        <option value="TINY">Tiny (0.25x)</option>
                        <option value="SMALL">Small (0.5x)</option>
                        <option value="NORMAL" selected>Normal (1.0x)</option>
                        <option value="LARGE">Large (2.0x)</option>
                        <option value="HUGE">Huge (4.0x)</option>
                      </select>
                    </div>
                  </div>
                  <div style="display:grid; grid-template-columns: 1fr 1fr; gap:6px; margin-bottom:6px;">
                    <div>
                      <label style="font-size:0.7rem; color:var(--text-muted);">Working Fluid:</label>
                      <select id="loop-fluid-select" onchange="onCoolantLoopConfigChange()" style="width:100%;">
                        <option value="DISTILLED_WATER" selected>Distilled Water</option>
                        <option value="HEAVY_WATER">Heavy Water</option>
                      </select>
                    </div>
                    <div>
                      <label style="font-size:0.7rem; color:var(--text-muted);">Pump Energy Hatch:</label>
                      <select id="loop-hatch-tier-select" onchange="onCoolantLoopConfigChange()" style="width:100%;">
                        <option value="LV">LV (32 EU/t)</option>
                        <option value="MV">MV (128 EU/t)</option>
                        <option value="HV">HV (512 EU/t)</option>
                        <option value="EV" selected>EV (2,048 EU/t)</option>
                        <option value="IV">IV (8,192 EU/t)</option>
                        <option value="LUV">LuV (32,768 EU/t)</option>
                        <option value="ZPM">ZPM (131,072 EU/t)</option>
                        <option value="UV">UV (524,288 EU/t)</option>
                        <option value="UHV">UHV (2,097,152 EU/t)</option>
                      </select>
                    </div>
                  </div>
                  <div style="display:grid; grid-template-columns: 1fr 1fr 1fr; gap:6px; margin-bottom:6px;">
                    <div>
                      <label style="font-size:0.7rem; color:var(--text-muted);">Duty Cycle (%):</label>
                      <input id="loop-duty-cycle-input" type="number" min="0" max="100" step="5" value="100" onchange="onCoolantLoopConfigChange()" style="width:100%; background:#233044; border:1px solid var(--border-color); color:#fff; padding:6px; border-radius:6px; font-size:0.8rem;">
                    </div>
                    <div>
                      <label style="font-size:0.7rem; color:var(--text-muted);">Max Flow (L/s):</label>
                      <input id="loop-max-flow-input" type="number" min="0" step="10" placeholder="No limit" onchange="onCoolantLoopConfigChange()" style="width:100%; background:#233044; border:1px solid var(--border-color); color:#fff; padding:6px; border-radius:6px; font-size:0.8rem;">
                    </div>
                    <div>
                      <label style="font-size:0.7rem; color:var(--text-muted);">Max Press (bar):</label>
                      <input id="loop-max-pressure-input" type="number" min="0" step="5" placeholder="Auto" onchange="onCoolantLoopConfigChange()" style="width:100%; background:#233044; border:1px solid var(--border-color); color:#fff; padding:6px; border-radius:6px; font-size:0.8rem;">
                    </div>
                  </div>
                  <div id="loop-throttled-banner" style="display:none; margin:4px 0 8px 0; font-size:0.74rem; color:#fde047; background:rgba(234,179,8,0.15); border:1px solid #eab308; border-radius:4px; padding:6px 8px; align-items:center; gap:6px;">
                    <span>⚙️</span> <span id="loop-throttled-text">Pump duty cycle limited by pipe pressure constraints</span>
                  </div>
                  <div style="display:flex; gap:6px; margin-bottom:8px;">
                    <button id="btn-loop-attach-mode" onclick="toggleLoopAttachMode()" style="flex:1; font-size:0.75rem; padding:4px 8px; background:#0284c7;">🔗 Attach Points Mode</button>
                    <button onclick="clearLoopAttachments()" style="font-size:0.75rem; padding:4px 8px; background:#475569;">✕ Clear Points</button>
                  </div>
                  <div class="stat-row"><span>Circulation Flow:</span><span class="stat-val" id="loop-flow-val">0 L/s</span></div>
                  <div class="stat-row"><span>Pressure:</span><span class="stat-val" id="loop-pressure-val">1.0 / 70.0 bar</span></div>
                  <div style="width:100%; background:#0f172a; border-radius:3px; height:6px; margin:4px 0 6px 0; overflow:hidden;">
                    <div id="loop-pressure-bar" style="width:5%; height:100%; background:#22c55e; transition:width 0.2s, background 0.2s;"></div>
                  </div>
                  <div class="stat-row"><span>Loop Fluid Temp:</span><span class="stat-val" id="loop-temp-val">20.0 °C</span></div>
                  <div class="stat-row"><span>Convective Heat Extracted:</span><span class="stat-val" id="loop-heat-val" style="color:#38bdf8;">0 EU/t (0.0 MWth)</span></div>
                  <div class="stat-row"><span>Secondary PHE Steam:</span><span class="stat-val" id="loop-steam-val">0 L/t (1:160)</span></div>
                  <div class="stat-row"><span>Pump Power (Parasitic):</span><span class="stat-val" id="loop-pump-val" style="color:#fbbf24;">0 EU/t</span></div>
                  <div class="stat-row"><span>Attached Core Points:</span><span class="stat-val" id="loop-points-val">0 cells</span></div>
                  <div class="stat-row"><span>Byproduct (Transmutation):</span><span class="stat-val" id="loop-byproduct-val" style="color:#a855f7;">0 L</span></div>
                </div>

                <!-- Control Rods Subsystem -->
                <div style="background:#131a24; border:1px solid var(--border-color); border-radius:6px; padding:12px; margin-bottom:12px;">
                  <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:4px;">
                    <h3 style="font-size:0.95rem; color:#00e5ff; margin:0;">🕹️ Control Rods</h3>
                    <span class="badge" id="rods-count-badge" style="font-weight:bold; color:#00e5ff;">0 Hatches</span>
                  </div>
                  <div style="font-size:0.75rem; color:var(--text-muted); margin-bottom:8px;">
                    Non-outer-wall hatches providing vertical neutron absorption shielding.
                  </div>
                  <div style="display:flex; justify-content:space-between; align-items:center; font-size:0.75rem; margin-bottom:4px;">
                    <span>Global Insertion:</span>
                    <span id="lbl-global-rod-ins" style="font-weight:700; color:#00e5ff;">0%</span>
                  </div>
                  <input type="range" id="rng-global-rod-ins" min="0" max="100" value="0"
                    oninput="document.getElementById('lbl-global-rod-ins').innerText = this.value + '%'"
                    onchange="setAllControlRodsInsertion(parseInt(this.value))"
                    style="width:100%; cursor:pointer; accent-color:#00e5ff; margin-bottom:6px;" />
                  <div style="display:flex; gap:6px; margin-bottom:8px;">
                    <button type="button" onclick="setAllControlRodsInsertion(0)" style="flex:1; font-size:0.75rem; padding:4px 6px;">Retract (0%)</button>
                    <button type="button" onclick="setAllControlRodsInsertion(50)" style="flex:1; font-size:0.75rem; padding:4px 6px;">Insert 50%</button>
                    <button type="button" class="danger" onclick="quickScram()" style="flex:1; font-size:0.75rem; padding:4px 6px; background:#b91c1c; border-color:#dc2626;" title="Emergency SCRAM [Key: s]">🚨 SCRAM (100%)</button>
                  </div>
                  <div class="stat-row"><span>Total Rod Absorption:</span><span class="stat-val" id="rods-total-abs-val" style="color:#00e5ff;">0 n/t</span></div>
                </div>

                <div style="background:#131a24; border:1px solid var(--border-color); border-radius:6px; padding:12px; margin-bottom:12px;">
                  <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:8px;">
                    <h3 style="font-size:0.95rem; color:var(--accent);">⚡ Turbine Power (XLST & EHE)</h3>
                    <span class="badge" id="stat-power-tier" style="font-weight:bold; color:var(--success);">0 EU/t (OFF)</span>
                  </div>
                  <div style="display:grid; grid-template-columns: 1fr 1fr; gap:6px; margin-bottom:6px;">
                    <select id="turbine-mat" onchange="onTurbineConfigChange()" title="Turbine Rotor Material"></select>
                    <select id="turbine-size" onchange="onTurbineConfigChange()" title="Turbine Rotor Size">
                      <option value="SMALL">Small (0.33x)</option>
                      <option value="NORMAL">Normal (0.67x)</option>
                      <option value="LARGE" selected>Large (1.00x)</option>
                      <option value="HUGE">Huge (1.33x)</option>
                    </select>
                  </div>
                  <div style="margin-bottom:8px;">
                    <select id="turbine-fitting" onchange="onTurbineConfigChange()" style="width:100%;" title="Housing Fitting Mode">
                      <option value="TIGHT" selected>Fitting: Tight (Optimal)</option>
                      <option value="LOOSE">Fitting: Loose</option>
                    </select>
                  </div>
                  <div class="stat-row"><span>Rotor Efficiency:</span><span class="stat-val" id="turb-eff">100 %</span></div>
                  <div class="stat-row"><span>Opt Flow / Turbine:</span><span class="stat-val" id="turb-opt-flow">0 L/t</span></div>
                  <hr style="border:0; border-top:1px solid var(--border-color); margin:6px 0;">
                  <div class="stat-row"><span>XLST-SC (Supercritical):</span><span class="stat-val" id="turb-sc-pwr">0 EU/t</span></div>
                  <div class="stat-row"><span>XLST-HP (Superheated/HW):</span><span class="stat-val" id="turb-hp-pwr">0 EU/t</span></div>
                  <div class="stat-row"><span>XLST (Regular Steam):</span><span class="stat-val" id="turb-reg-pwr">0 EU/t</span></div>
                  <div class="stat-row" id="ehe-row" style="display:none;"><span>EHE Mode / DW:</span><span class="stat-val" id="turb-ehe-info" style="color:#38bdf8;">None</span></div>
                  <hr style="border:0; border-top:1px solid var(--border-color); margin:6px 0;">
                  <div class="stat-row" style="font-size:0.75rem;"><span>Live Flows (L/t):</span><span class="stat-val" id="turb-flows" style="font-size:0.75rem;">Reg: 0 | SH: 0 | SC: 0</span></div>
                  <!-- Operational Scenarios (Tier Defaults) -->
                  <div style="background:#0f172a; border:1px solid #334155; border-radius:6px; padding:8px; margin-top:8px;">
                    <div style="font-size:0.8rem; font-weight:bold; color:var(--accent); margin-bottom:6px;">⚖️ Operational Scenarios (Tier Defaults)</div>
                    <div style="display:grid; grid-template-columns: 1fr 1fr; gap:6px;">
                      <div style="background:#1e293b; border:1px solid rgba(56,189,248,0.3); border-radius:4px; padding:6px;">
                        <div style="font-size:0.72rem; font-weight:bold; color:#38bdf8;">Scenario A [Tight]</div>
                        <div id="scen-a-desc" style="font-size:0.68rem; color:var(--text-muted); margin-bottom:3px;">-</div>
                        <div style="font-size:0.72rem;"><span style="color:var(--text-muted);">Power: </span><span id="scen-a-pwr" style="font-weight:bold; color:#4ade80;">-</span></div>
                        <div style="font-size:0.72rem;"><span style="color:var(--text-muted);">Turbines: </span><span id="scen-a-turb" style="font-weight:bold;">-</span></div>
                        <div style="font-size:0.72rem;"><span style="color:var(--text-muted);">Coolant: </span><span id="scen-a-mach">-</span></div>
                        <button class="btn btn-sm" onclick="applyScenarioPreset('TIGHT')" style="margin-top:4px; width:100%; font-size:0.68rem; padding:2px 4px; background:#0284c7;">Apply Tight</button>
                      </div>
                      <div style="background:#1e293b; border:1px solid rgba(251,191,36,0.3); border-radius:4px; padding:6px;">
                        <div style="font-size:0.72rem; font-weight:bold; color:#fbbf24;">Scenario B [Loose]</div>
                        <div id="scen-b-desc" style="font-size:0.68rem; color:var(--text-muted); margin-bottom:3px;">-</div>
                        <div style="font-size:0.72rem;"><span style="color:var(--text-muted);">Power: </span><span id="scen-b-pwr" style="font-weight:bold; color:#4ade80;">-</span></div>
                        <div style="font-size:0.72rem;"><span style="color:var(--text-muted);">Turbines: </span><span id="scen-b-turb" style="font-weight:bold;">-</span></div>
                        <div style="font-size:0.72rem;"><span style="color:var(--text-muted);">Coolant: </span><span id="scen-b-mach">-</span></div>
                        <button class="btn btn-sm" onclick="applyScenarioPreset('LOOSE')" style="margin-top:4px; width:100%; font-size:0.68rem; padding:2px 4px; background:#d97706;">Apply Loose</button>
                      </div>
                    </div>
                    <div id="scen-compare-text" style="font-size:0.7rem; color:#94a3b8; margin-top:5px; text-align:center;">-</div>
                  </div>
                </div>

                <!-- Full View of Energy Conversion Processes -->
                <div style="background:#131a24; border:1px solid var(--border-color); border-radius:6px; padding:12px; margin-bottom:12px;">
                  <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:8px;">
                    <h3 style="font-size:0.95rem; color:#38bdf8; margin:0;">🔄 Machine Process Pipeline</h3>
                    <span class="badge" id="proc-tier-badge" style="font-size:0.72rem; font-weight:bold; color:#fbbf24; border:1px solid #475569;">IV Pipeline</span>
                  </div>
                  <div id="proc-tier-desc" style="font-size:0.72rem; color:var(--text-muted); margin-bottom:10px; line-height:1.35; padding:6px 8px; background:#0f172a; border-radius:4px; border:1px solid #1e293b;">
                    Loading tier process description...
                  </div>

                  <!-- Stage 0: Radiovoltaic Direct Power -->
                  <div id="proc-stage-pv" style="margin-bottom:6px; padding:6px 8px; background:#0f172a; border-left:3px solid #f59e0b; border-radius:4px;">
                    <div style="display:flex; justify-content:space-between; font-size:0.75rem; font-weight:600; color:#f59e0b;">
                      <span>☀️ Stage 0: Direct Radiovoltaics</span>
                      <span id="proc-pv-val">0 EU/t</span>
                    </div>
                    <div style="font-size:0.68rem; color:var(--text-muted); margin-top:2px;" id="proc-pv-sub">Chamber cells direct EU output</div>
                  </div>

                  <!-- Stage 1: Hot Coolant Heat Exchanger -->
                  <div id="proc-stage-coolant" style="margin-bottom:6px; padding:6px 8px; background:#0f172a; border-left:3px solid #06b6d4; border-radius:4px;">
                    <div style="display:flex; justify-content:space-between; font-size:0.75rem; font-weight:600; color:#06b6d4;">
                      <span id="proc-coolant-title">🔄 Stage 1: Coolant Exchanger</span>
                      <span id="proc-coolant-count" style="color:#e2e8f0; font-weight:bold;">0x Machine</span>
                    </div>
                    <div style="font-size:0.71rem; color:#94a3b8; margin-top:2px;" id="proc-coolant-detail">Input: 0 L/t Hot Coolant → Output: 0 L/t Steam</div>
                    <div style="font-size:0.67rem; color:var(--text-muted); margin-top:1px;" id="proc-coolant-water">Water: 0 L/t</div>
                  </div>

                  <!-- Stage 2: Supercritical Steam (XLST-SC) -->
                  <div id="proc-stage-sc" style="margin-bottom:6px; padding:6px 8px; background:#0f172a; border-left:3px solid #8b5cf6; border-radius:4px;">
                    <div style="display:flex; justify-content:space-between; font-size:0.75rem; font-weight:600; color:#a78bfa;">
                      <span id="proc-sc-title">⚡ Stage 2: XLST-SC (Supercritical)</span>
                      <span id="proc-sc-pwr">0 EU/t</span>
                    </div>
                    <div style="font-size:0.71rem; color:#94a3b8; margin-top:2px;" id="proc-sc-detail">0 L/t SC steam · 0.00 turbines</div>
                    <div style="font-size:0.67rem; color:#94a3b8; margin-top:1px;" id="proc-sc-cascade">Cascades 1:1 into Superheated Steam</div>
                  </div>

                  <!-- Stage 3: Superheated Steam (XLST-HP) -->
                  <div id="proc-stage-hp" style="margin-bottom:6px; padding:6px 8px; background:#0f172a; border-left:3px solid #ec4899; border-radius:4px;">
                    <div style="display:flex; justify-content:space-between; font-size:0.75rem; font-weight:600; color:#f472b6;">
                      <span id="proc-hp-title">⚡ Stage 3: XLST-HP (Superheated)</span>
                      <span id="proc-hp-pwr">0 EU/t</span>
                    </div>
                    <div style="font-size:0.71rem; color:#94a3b8; margin-top:2px;" id="proc-hp-detail">0 L/t SH steam · 0.00 turbines</div>
                    <div style="font-size:0.67rem; color:#94a3b8; margin-top:1px;" id="proc-hp-cascade">Cascades 1:1 into Regular Steam</div>
                  </div>

                  <!-- Stage 4: Regular Steam Turbine (LST or XLST) -->
                  <div id="proc-stage-reg" style="margin-bottom:6px; padding:6px 8px; background:#0f172a; border-left:3px solid #10b981; border-radius:4px;">
                    <div style="display:flex; justify-content:space-between; font-size:0.75rem; font-weight:600; color:#34d399;">
                      <span id="proc-reg-title">⚡ Stage 4: XLST (Regular Steam)</span>
                      <span id="proc-reg-pwr">0 EU/t</span>
                    </div>
                    <div style="font-size:0.71rem; color:#94a3b8; margin-top:2px;" id="proc-reg-detail">0 L/t steam · 0.00 turbines</div>
                    <div style="font-size:0.67rem; color:#94a3b8; margin-top:1px;" id="proc-reg-eff">Base 0.5 EU/L × Rotor Eff</div>
                  </div>

                  <!-- Process Total Summary -->
                  <div style="border-top:1px solid var(--border-color); padding-top:6px; margin-top:6px; display:flex; justify-content:space-between; align-items:center; font-size:0.78rem;">
                    <span style="font-weight:700; color:var(--text-light);">Net Generation:</span>
                    <span id="proc-net-pwr" style="font-weight:bold; color:var(--success);">0 EU/t (0.00 A)</span>
                  </div>
                </div>

                <h3>Reactor Telemetry</h3>
                <div class="stat-row"><span>Simulation Tick:</span><span class="stat-val" id="stat-tick">0</span></div>
                <div class="stat-row"><span>Core Peak Temp:</span><span class="stat-val" id="stat-max-temp">20.0 °C</span></div>
                <div class="stat-row"><span>Core Average Temp:</span><span class="stat-val" id="stat-avg-temp">20.0 °C</span></div>
                <div class="stat-row"><span>Reactivity Efficiency:</span><span class="stat-val" id="stat-eff">100 %</span></div>
                <div class="stat-row"><span>Neutron Output:</span><span class="stat-val" id="stat-neutrons">0 / tick</span></div>
                <div class="stat-row"><span>Thermal Absorbed:</span><span class="stat-val" id="stat-thermal">0 / tick</span></div>
                <div class="stat-row"><span>Fast Absorbed:</span><span class="stat-val" id="stat-fast">0 / tick</span></div>
                <div class="stat-row"><span>Steam Produced:</span><span class="stat-val" id="stat-steam">0 L</span></div>
                <div class="stat-row"><span>Deuterium Bred:</span><span class="stat-val" id="stat-deuterium" style="color:var(--accent);">0 L</span></div>
                <div class="stat-row"><span>Tritium Bred:</span><span class="stat-val" id="stat-tritium" style="color:#d946ef;">0 L</span></div>
                <div class="stat-row"><span>Power Generation:</span><span class="stat-val safe" id="stat-power">0 EU/t</span></div>

                <!-- Fuel & Coolant Byproducts Subsystem -->
                <div id="fuel-byproducts-card" style="background:#131a24; border:1px solid #10b981; border-radius:6px; padding:12px; margin-top:12px; margin-bottom:12px;">
                  <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:8px;">
                    <div style="display:flex; align-items:center; gap:8px;">
                      <h3 style="font-size:0.95rem; color:#10b981; margin:0;">♻️ Fuel &amp; Byproduct Logistics</h3>
                      <span class="badge" id="byproduct-rate-badge" style="font-size:0.7rem; color:#34d399; border-color:#059669;">0 items / min</span>
                    </div>
                    <div class="segmented-control" style="font-size:0.7rem;">
                      <button type="button" class="seg-btn active" id="btn-byproduct-min" onclick="setByproductUnit('min')" title="Display byproduct rates per minute">/min</button>
                      <button type="button" class="seg-btn" id="btn-byproduct-hour" onclick="setByproductUnit('hour')" title="Display byproduct rates per hour">/h</button>
                    </div>
                  </div>

                  <div style="display:flex; justify-content:space-between; align-items:center; background:#0f172a; border:1px solid #1e293b; border-radius:4px; padding:6px 8px; margin-bottom:8px;">
                    <span style="font-size:0.75rem; color:var(--text-primary); display:flex; align-items:center; gap:4px;">
                      <span>🔄 Auto-Supply Fuel (Continuous Cycling):</span>
                    </span>
                    <label style="display:inline-flex; align-items:center; gap:4px; cursor:pointer; font-size:0.75rem; color:var(--text-muted); user-select:none;">
                      <input type="checkbox" id="chk-auto-refuel" checked onchange="toggleAutoSupplyFuel(this.checked)" style="cursor:pointer;">
                      <span id="lbl-auto-refuel" style="color:#34d399; font-weight:600;">Enabled</span>
                    </label>
                  </div>

                  <div id="byproducts-content" style="font-size:0.75rem;">
                    <div style="margin-bottom:6px;">
                      <div style="font-weight:700; font-size:0.72rem; color:#94a3b8; text-transform:uppercase; margin-bottom:4px; letter-spacing:0.5px; display:flex; justify-content:space-between;">
                        <span>Depleted Fuel Rods</span>
                        <span id="byproduct-solid-total" style="color:#34d399; font-weight:bold;">0 / min</span>
                      </div>
                      <div id="byproduct-solid-list" style="display:flex; flex-direction:column; gap:4px;">
                        <div style="font-style:italic; color:#64748b; padding:2px 0;">No active fuel rods burning.</div>
                      </div>
                    </div>

                    <div id="byproduct-liquid-section" style="margin-bottom:6px; display:none;">
                      <div style="font-weight:700; font-size:0.72rem; color:#94a3b8; text-transform:uppercase; margin-bottom:4px; letter-spacing:0.5px; display:flex; justify-content:space-between;">
                        <span>Depleted Fluid Fuels</span>
                        <span id="byproduct-liquid-total" style="color:#38bdf8; font-weight:bold;">0 L/min</span>
                      </div>
                      <div id="byproduct-liquid-list" style="display:flex; flex-direction:column; gap:4px;"></div>
                    </div>

                    <div id="byproduct-isotope-section" style="margin-bottom:2px; display:none;">
                      <div style="font-weight:700; font-size:0.72rem; color:#94a3b8; text-transform:uppercase; margin-bottom:4px; letter-spacing:0.5px; display:flex; justify-content:space-between;">
                        <span>Transmuted Isotopes</span>
                        <span id="byproduct-isotope-total" style="color:#d946ef; font-weight:bold;">0 L/min</span>
                      </div>
                      <div id="byproduct-isotope-list" style="display:flex; flex-direction:column; gap:4px;"></div>
                    </div>

                    <div id="byproduct-raw-section" style="margin-top:8px; border-top:1px solid #1e293b; padding-top:8px;">
                      <div style="font-weight:700; font-size:0.72rem; color:#94a3b8; text-transform:uppercase; margin-bottom:4px; letter-spacing:0.5px; display:flex; justify-content:space-between;">
                        <span>📦 Raw Materials Net Forecast</span>
                        <span id="byproduct-raw-summary" style="color:#f59e0b; font-weight:bold;">Logistics</span>
                      </div>
                      <div id="byproduct-raw-list" style="display:flex; flex-direction:column; gap:4px;">
                        <div style="font-style:italic; color:#64748b; padding:2px 0;">No active raw material burn forecast.</div>
                      </div>
                    </div>
                  </div>
                </div>

                <h3 style="margin-top:12px;">Temperature Trend</h3>
                <canvas id="chartCanvas" width="300" height="150"></canvas>
              </div>
            </div>
            """;
    }

    private static String getHtmlScriptsPart1() {
        return """
            <script>
            let currentState = null;
            let selectedTilePos = null;
            let activePaletteType = "FUEL_URANIUM_QUAD";
            let interactionMode = "INSPECT";
            let isWasmMode = true;
            let wasmSim = null;
            let byproductUnit = "min";

            function getIconUrl(type) {
              return `icons/${type}.png`;
            }

            async function initEngine() {
              if (typeof NuclearSimWasm !== "undefined") {
                try {
                  wasmSim = new NuclearSimWasm();
                  const wasmPath = "nuclear-sim.wasm";
                  await wasmSim.init(wasmPath);
                  isWasmMode = true;
                  const eb = document.getElementById("engine-badge");
                  if (eb) {
                    eb.innerText = "⚡ Client WASM (0ms)";
                    eb.style.background = "#065f46";
                    eb.style.borderColor = "#059669";
                    eb.style.color = "#34d399";
                  }
                  wasmSim.onStateChange = (state) => {
                    currentState = state;
                    renderUI();
                  };
                  if (window.__INITIAL_STATE__ && window.__INITIAL_STATE__.tiles) {
                    wasmSim.initGrid(window.__INITIAL_STATE__.width, window.__INITIAL_STATE__.height, window.__INITIAL_STATE__.pipeTier);
                    window.__INITIAL_STATE__.tiles.forEach(t => {
                      if (t.type && t.type !== "EMPTY" && t.code !== "NL") {
                        wasmSim.setTile(t.x, t.y, t.type, false);
                      }
                    });
                    if (window.__INITIAL_STATE__.turbineMaterial) {
                      wasmSim.setTurbine(window.__INITIAL_STATE__.turbineMaterial, window.__INITIAL_STATE__.turbineSize, window.__INITIAL_STATE__.turbineFitting, false);
                    }
                  } else {
                    wasmSim.initGrid(9, 9, 1);
                    wasmSim.loadPreset("BEST_PLATINUM_9X9");
                  }
                  currentState = wasmSim.getState();
                  renderUI();
                  console.log("⚡ Modular Nuclear Simulator running via WebAssembly (WASM)!");
                  return true;
                } catch (err) {
                  console.error("WASM initialization error:", err);
                  const eb = document.getElementById("engine-badge");
                  if (eb) {
                    eb.innerText = "❌ WASM Error";
                    eb.style.background = "#7f1d1d";
                    eb.style.borderColor = "#b91c1c";
                    eb.style.color = "#f87171";
                  }
                  return false;
                }
              }
              return false;
            }

            const PALETTE = [
              { type: "EMPTY", name: "Empty Slot", code: ".", color: "#1a2230" },
              { type: "FUEL_GLOWSTONE", name: "Glowstone Rod", code: "G1", color: "#facc15" },
              { type: "FUEL_LITHIUM", name: "Lithium Rod", code: "LI1", color: "#a3e635" },
              { type: "FUEL_THORIUM_QUAD", name: "Thorium Quad Rod", code: "T4", color: "#a855f7" },
              { type: "FUEL_URANIUM_QUAD", name: "Uranium Quad Rod", code: "U4", color: "#22c55e" },
              { type: "FUEL_MOX_QUAD", name: "MOX Quad Rod", code: "M4", color: "#eab308" },
              { type: "FUEL_HD_URANIUM", name: "HD Uranium Quad", code: "HDU", color: "#16a34a" },
              { type: "FUEL_HD_PLUTONIUM", name: "HD Plutonium Quad", code: "HDP", color: "#ea580c" },
              { type: "FUEL_EXCITED_URANIUM", name: "Excited Uranium Quad", code: "EXU", color: "#10b981" },
              { type: "FUEL_EXCITED_PLUTONIUM", name: "Excited Plutonium Quad", code: "EXP", color: "#f43f5e" },
              { type: "FUEL_NAQUADAH", name: "Naquadah Rod", code: "NQ", color: "#ec4899" },
              { type: "FUEL_NAQUADRIA", name: "Naquadria Quad", code: "NQR", color: "#c026d3" },
              { type: "FUEL_TIBERIUM", name: "Tiberium Quad", code: "TIB", color: "#14b8a6" },
              { type: "FUEL_CORE", name: "The Core (NQ32)", code: "NQ32", color: "#dc2626" },
              { type: "HATCH_DISTILLED_WATER", name: "Distilled Water Hatch", code: "HD", color: "#38bdf8" },
              { type: "HATCH_HEAVY_WATER", name: "Heavy Water Hatch", code: "HW", color: "#6366f1" },
              { type: "HATCH_IC2_COOLANT", name: "IC2 Coolant Hatch", code: "HC", color: "#14b8a6" },
              { type: "PASSAGE_CORE", name: "Coolant Loop Passage", code: "CP", color: "#00d2ff" },
              { type: "REFLECTOR_BERYLLIUM", name: "Beryllium Reflector", code: "RB", color: "#94a3b8" },
              { type: "REFLECTOR_CARBON", name: "Carbon Reflector", code: "RC", color: "#475569" },
              { type: "COOLANT_CELL_60K", name: "60k Coolant Cell", code: "C6", color: "#06b6d4" },
              { type: "RADIOVOLTAIC_HV", name: "Radiovoltaic Cell (HV)", code: "RH", color: "#f59e0b" },
              { type: "RADIOVOLTAIC_EV", name: "Radiovoltaic Cell (EV)", code: "RV", color: "#f97316" },
              { type: "VENT_STANDARD", name: "Heat Vent", code: "V1", color: "#38bdf8" },
              { type: "VENT_ADVANCED", name: "Advanced Heat Vent", code: "VA", color: "#0ea5e9" },
              { type: "VENT_OVERCLOCKED", name: "Overclocked Heat Vent", code: "VO", color: "#eab308" },
              { type: "VENT_COMPONENT", name: "Component Heat Vent", code: "VC", color: "#06b6d4" },
              { type: "EXCHANGER_STANDARD", name: "Heat Exchanger", code: "X1", color: "#a855f7" },
              { type: "EXCHANGER_ADVANCED", name: "Advanced Heat Exchanger", code: "XA", color: "#9333ea" },
              { type: "EXCHANGER_COMPONENT", name: "Component Heat Exchanger", code: "XC", color: "#c084fc" }
            ];

            const VOLTAGE_TIERS = {
              ULV: 8,
              LV: 32,
              MV: 128,
              HV: 512,
              EV: 2048,
              IV: 8192,
              LuV: 32768,
              ZPM: 131072,
              UV: 524288,
              UHV: 2097152,
              UEV: 8388608,
              UIV: 33554432,
              UMV: 134217728,
              UXV: 536870912,
              MAX: 2147483647
            };

            let selectedVoltageTier = "IV";

            function onVoltageTierChange(val) {
              selectedVoltageTier = val;
              renderUI();
            }

            function setVoltageTier(val) {
              if (VOLTAGE_TIERS[val]) {
                selectedVoltageTier = val;
                const sel = document.getElementById("voltage-tier-select");
                if (sel && sel.value !== val) sel.value = val;
                renderUI();
              }
            }

            function formatPowerAndAmps(eut, compact = false) {
              if (eut === undefined || eut === null || isNaN(eut)) return "-";
              const voltage = VOLTAGE_TIERS[selectedVoltageTier] || 8192;
              const amps = eut / voltage;
              const euStr = Math.round(eut).toLocaleString() + " EU/t";
              const ampStr = (amps >= 100 ? amps.toFixed(1) : amps.toFixed(2)) + " A";
              if (compact) {
                return `${euStr} (${ampStr} @ ${selectedVoltageTier})`;
              }
              return `${euStr} · ${ampStr} @ ${selectedVoltageTier}`;
            }

            const TURBINE_MATERIALS = [
              { id: "HSS_E", name: "HSS-E (175% / 116%)" },
              { id: "HSS_S", name: "HSS-S (185% / 123%)" },
              { id: "ELVEN_ELEMENTIUM", name: "Elven Elementium (175% / 116%)" },
              { id: "ORINARUKON", name: "Oriharukon (155% / 105%)" },
              { id: "SHADOW_METAL", name: "Shadow Metal (145% / 98%)" },
              { id: "ICHORIUM", name: "Ichorium (225% / 152%)" },
              { id: "DURANIUM", name: "Duranium (215% / 166%)" },
              { id: "GAIA_SPIRIT", name: "Gaia Spirit (195% / 154%)" },
              { id: "ADAMANTIUM", name: "Adamantium (180% / 158%)" },
              { id: "EXT_UNST_NAQUADAH", name: "Ext. Unst. Naquadah (190% / 166%)" },
              { id: "COSMIC_NEUTRONIUM", name: "Cosmic Neutronium (200% / 174%)" },
              { id: "INFINITY", name: "Infinity (212% / 212%)" },
              { id: "TUNGSTENSTEEL", name: "Tungstensteel (130% / 88%)" },
              { id: "TITANIUM", name: "Titanium (120% / 81%)" }
            ];

            function initTurbineSelects() {
              const matSelect = document.getElementById("turbine-mat");
              matSelect.innerHTML = "";
              TURBINE_MATERIALS.forEach(m => {
                const opt = document.createElement("option");
                opt.value = m.id;
                opt.innerText = m.name;
                matSelect.appendChild(opt);
              });
            }

            async function onTurbineConfigChange() {
              const mat = document.getElementById("turbine-mat").value;
              const size = document.getElementById("turbine-size").value;
              const fitting = document.getElementById("turbine-fitting").value;
              if (isWasmMode && wasmSim) {
                wasmSim.setTurbine(mat, size, fitting);
                currentState = wasmSim.getState();
                renderUI();
                return;
              }
              await fetch(`/api/set-turbine?material=${encodeURIComponent(mat)}&size=${encodeURIComponent(size)}&fitting=${encodeURIComponent(fitting)}`);
              await fetchState();
              schedulePoll();
            }

            async function applyScenarioPreset(mode) {
              if (!currentState || !currentState.scenarios) return;
              const cfg = (mode === 'TIGHT') ? currentState.scenarios.tight : currentState.scenarios.loose;
              if (!cfg) return;
              const matSelect = document.getElementById("turbine-mat");
              const opt = Array.from(matSelect.options).find(o => o.value.toLowerCase() === cfg.material.toLowerCase() || o.value.replace(/_/g,"").toLowerCase() === cfg.material.replace(/_/g,"").toLowerCase());
              if (opt) {
                matSelect.value = opt.value;
              } else {
                matSelect.value = cfg.material;
              }
              const sizeSelect = document.getElementById("turbine-size");
              sizeSelect.value = cfg.size.toUpperCase();
              const fitSelect = document.getElementById("turbine-fitting");
              fitSelect.value = cfg.mode.toUpperCase();
              await onTurbineConfigChange();
            }

            let isLoopAttachMode = false;

            async function changeCoolingMode(mode) {
              if (isWasmMode && wasmSim) {
                wasmSim.setCoolingMode(mode);
                currentState = wasmSim.getState();
                renderUI();
                return;
              }
              await fetch(`/api/set-cooling-mode?mode=${encodeURIComponent(mode)}`);
              await fetchState();
              schedulePoll();
            }

            async function onCoolantLoopConfigChange() {
              const matSelect = document.getElementById("loop-material-select");
              const mat = matSelect ? matSelect.value : "TITANIUM";
              const size = document.getElementById("loop-size-select").value;
              const fluid = document.getElementById("loop-fluid-select").value;
              const hatch = document.getElementById("loop-hatch-tier-select").value;
              const duty = parseFloat(document.getElementById("loop-duty-cycle-input").value) || 0;
              const maxFlowVal = document.getElementById("loop-max-flow-input").value;
              const maxFlow = maxFlowVal !== "" ? (parseFloat(maxFlowVal) || 0) : 0;
              const maxPressVal = document.getElementById("loop-max-pressure-input").value;
              const maxPress = maxPressVal !== "" ? (parseFloat(maxPressVal) || 0) : 0;

              if (isWasmMode && wasmSim) {
                wasmSim.setCoolantLoopMaterial(mat, false);
                wasmSim.setCoolantLoopPipeSize(size, false);
                wasmSim.setCoolantLoopFluid(fluid, false);
                wasmSim.setCoolantLoopControl(hatch, duty, maxFlow, maxPress, true);
                currentState = wasmSim.getState();
                renderUI();
                return;
              }
              const url = `/api/set-coolant-loop?material=${encodeURIComponent(mat)}&size=${encodeURIComponent(size)}&fluid=${encodeURIComponent(fluid)}&hatchTier=${encodeURIComponent(hatch)}&dutyCycle=${duty}&maxFlow=${maxFlow}&maxPressure=${maxPress}`;
              await fetch(url);
              await fetchState();
              schedulePoll();
            }

            function toggleLoopAttachMode() {
              isLoopAttachMode = !isLoopAttachMode;
              const btn = document.getElementById("btn-loop-attach-mode");
              if (btn) {
                if (isLoopAttachMode) {
                  btn.innerText = "🛑 Exit Attach Mode";
                  btn.style.background = "#e11d48";
                } else {
                  btn.innerText = "🔗 Attach Points Mode";
                  btn.style.background = "#0284c7";
                }
              }
            }

            async function toggleLoopAttachment(x, y) {
              if (!currentState || !currentState.coolantLoop) return;
              const ptStr = x + "," + y;
              const isAttached = currentState.coolantLoop.attachedPoints && currentState.coolantLoop.attachedPoints.includes(ptStr);
              if (isWasmMode && wasmSim) {
                if (isAttached) {
                  wasmSim.detachCoolantLoopPoint(x, y);
                } else {
                  wasmSim.attachCoolantLoopPoint(x, y);
                }
                currentState = wasmSim.getState();
                renderUI();
                return;
              }
              const param = isAttached ? `detachX=${x}&detachY=${y}` : `attachX=${x}&attachY=${y}`;
              await fetch(`/api/set-coolant-loop?${param}`);
              await fetchState();
              schedulePoll();
            }

            async function clearLoopAttachments() {
              if (isWasmMode && wasmSim) {
                wasmSim.clearCoolantLoopPoints();
                currentState = wasmSim.getState();
                renderUI();
                return;
              }
              await fetch("/api/set-coolant-loop?clearPoints=true");
              await fetchState();
              schedulePoll();
            }

            function getVoltageTier(eut) {
              if (eut <= 0) return "OFF";
              if (eut <= 32) return "LV";
              if (eut <= 128) return "MV";
              if (eut <= 512) return "HV";
              if (eut <= 2048) return "EV";
              if (eut <= 8192) return "IV";
              if (eut <= 32768) return "LuV";
              if (eut <= 131072) return "ZPM";
              if (eut <= 524288) return "UV";
              if (eut <= 2097152) return "UHV";
              if (eut <= 8388608) return "UEV";
              if (eut <= 33554432) return "UIV";
              if (eut <= 134217728) return "UMV";
              return "UXV+";
            }

            function setInteractionMode(mode) {
              interactionMode = mode;
              if (isLoopAttachMode) {
                isLoopAttachMode = false;
                const loopBtn = document.getElementById("btn-loop-attach-mode");
                if (loopBtn) {
                  loopBtn.innerText = "🔗 Attach Points Mode";
                  loopBtn.style.background = "#0284c7";
                }
              }
              const btnInspect = document.getElementById("btn-mode-inspect");
              const btnPaint = document.getElementById("btn-mode-paint");
              const badge = document.getElementById("mode-badge");

              if (mode === "INSPECT") {
                btnInspect.className = "primary";
                btnPaint.className = "";
                badge.innerText = "INSPECT ONLY";
                badge.style.color = "var(--accent)";
                document.querySelectorAll(".palette-btn").forEach(b => b.classList.remove("active"));
              } else {
                btnInspect.className = "";
                btnPaint.className = "primary";
                badge.innerText = "PAINT MODE";
                badge.style.color = "var(--warning)";
                document.querySelectorAll(".palette-btn").forEach(b => {
                  if (b.dataset.type === activePaletteType) {
                    b.classList.add("active");
                  } else {
                    b.classList.remove("active");
                  }
                });
              }
            }

            function initPalette() {
              const container = document.getElementById("palette-container");
              container.innerHTML = "";
              PALETTE.forEach(p => {
                const btn = document.createElement("button");
                btn.className = "palette-btn" + (interactionMode === "PAINT" && p.type === activePaletteType ? " active" : "");
                btn.dataset.type = p.type;
                btn.style.borderLeft = "4px solid " + p.color;
                const iconHtml = p.type !== "EMPTY"
                  ? `<img class="palette-icon" src="${getIconUrl(p.type)}" onerror="this.src='/api/icon/' + p.type" alt="${p.code}" />`
                  : `<div class="palette-icon palette-empty-icon"></div>`;
                btn.innerHTML = `${iconHtml} <span>${p.name}</span>`;
                btn.onclick = (e) => {
                  if (e) e.preventDefault();
                  activePaletteType = p.type;
                  setInteractionMode("PAINT");
                };
                container.appendChild(btn);
              });
            }

            let gridDisplayMode = "TEMP";
            let colorMaps = null;

            async function loadColorMaps() {
              if (isWasmMode && wasmSim) {
                try {
                  colorMaps = wasmSim.getColorMaps();
                  return;
                } catch (e) {
                  console.error("Failed to load colormaps from WASM", e);
                }
              }
              if (location.protocol === "file:" || location.hostname.endsWith("github.io")) {
                return;
              }
              try {
                const res = await fetch("/api/colormaps");
                if (res.ok) {
                  colorMaps = await res.json();
                }
              } catch (e) {
                console.error("Failed to load colormaps", e);
              }
            }

            function setGridDisplayMode(mode) {
              gridDisplayMode = mode;
              document.querySelectorAll(".display-mode-selector .seg-btn").forEach(btn => {
                btn.classList.remove("active");
              });
              const idMap = {
                "COMPONENTS": "btn-mode-comp",
                "TEMP": "btn-mode-temp",
                "TOTAL_FLUX": "btn-mode-flux",
                "FAST_FLUX": "btn-mode-flux",
                "THERMAL_FLUX": "btn-mode-flux",
                "ABSORPTION": "btn-mode-absorb",
                "CONTROL_RODS": "btn-mode-rods"
              };
              const activeBtn = document.getElementById(idMap[mode]);
              if (activeBtn) activeBtn.classList.add("active");
              if (currentState) {
                updateGridDOM();
                if (selectedTilePos) {
                  const idx = selectedTilePos.y * currentState.width + selectedTilePos.x;
                  if (currentState.tiles && currentState.tiles[idx]) {
                    renderInspector(currentState.tiles[idx]);
                  }
                }
              }
            }

            function getControlRodColor(type) {
              switch (type) {
                case "SILVER": return "#94a3b8";
                case "BORON": return "#3b82f6";
                case "CADMIUM": return "#06b6d4";
                case "INDIUM": return "#a855f7";
                case "HAFNIUM": return "#f97316";
                default: return "#64748b";
              }
            }

            function getAbsorptionColor(rate) {
              if (rate <= 0) return "transparent";
              if (rate < 50) return "rgba(120, 53, 15, 0.4)";
              if (rate < 200) return "rgba(180, 83, 9, 0.5)";
              if (rate < 800) return "rgba(217, 119, 6, 0.6)";
              if (rate < 2500) return "rgba(245, 158, 11, 0.7)";
              return "rgba(251, 191, 36, 0.85)";
            }

            function getNeutronColor(rate) {
              if (!colorMaps || !colorMaps.neutron) {
                if (rate <= 0) return "transparent";
                if (rate < 100) return "rgba(68, 1, 84, 0.55)";
                if (rate < 500) return "rgba(59, 82, 139, 0.6)";
                if (rate < 2000) return "rgba(33, 145, 140, 0.65)";
                if (rate < 5000) return "rgba(94, 201, 98, 0.7)";
                return "rgba(253, 231, 36, 0.75)";
              }
              const maxRate = 8192.0;
              const r = Math.min(Math.max(0, rate), maxRate);
              const factor = (r <= 0) ? 0.0 : Math.log1p(10.0 * r) / Math.log1p(10.0 * maxRate);
              let u = Math.round(299.0 * Math.min(1.0, Math.max(0.0, factor)));
              if (u < 0) u = 0;
              if (u > 299) u = 299;
              return colorMaps.neutron[u];
            }

            function getTemperatureColor(temp, maxTemp) {
              if (!colorMaps || !colorMaps.temperature) {
                if (temp < 100) return "rgba(12, 7, 134, 0.55)";
                if (temp < 350) return "rgba(126, 3, 168, 0.6)";
                if (temp < 800) return "rgba(204, 71, 120, 0.65)";
                if (temp < 1500) return "rgba(248, 149, 64, 0.7)";
                return "rgba(240, 249, 33, 0.75)";
              }
              if (!maxTemp || maxTemp <= 0) maxTemp = 3250.0;
              let u = Math.round(299.0 * Math.min(1.0, Math.max(0.0, temp / maxTemp)));
              if (u < 0) u = 0;
              if (u > 299) u = 299;
              return colorMaps.temperature[u];
            }

            function formatFluxValue(val) {
              if (!val || val <= 0) return "0";
              if (val >= 10000) return (val / 1000).toFixed(1) + "k";
              return val.toString();
            }

            let isFetching = false;
            let pollTimer = null;

            async function fetchState() {
              if (isWasmMode && wasmSim) {
                currentState = wasmSim.getState();
                renderUI();
                return;
              }
              if (location.protocol === "file:" || location.hostname.endsWith("github.io")) {
                return;
              }
              if (isFetching) return;
              isFetching = true;
              try {
                const res = await fetch("/api/state");
                if (res.ok) {
                  currentState = await res.json();
                  renderUI();
                }
              } catch (e) {
                console.error("Fetch error", e);
              } finally {
                isFetching = false;
              }
            }

            function schedulePoll() {
              clearTimeout(pollTimer);
              if (isWasmMode || location.protocol === "file:" || location.hostname.endsWith("github.io")) return;
              const delay = (currentState && currentState.isRunning) ? 250 : 1000;
              pollTimer = setTimeout(async () => {
                await fetchState();
                schedulePoll();
              }, delay);
            }

            async function quickRepair() {
              if (currentState && currentState.canRepair === false) {
                const thresh = currentState.repairThreshold || 120.0;
                alert(`Cannot repair reactor: average core temperature (${currentState.coreAvgTemp.toFixed(1)}°C) exceeds safe repair threshold (${thresh.toFixed(1)}°C). Scram or cool the reactor before repairing.`);
                return;
              }
              if (isWasmMode && wasmSim) {
                wasmSim.repair();
                currentState = wasmSim.getState();
                renderUI();
                return;
              }
              await fetch("/api/set-params?repair=true");
              await fetchState();
              schedulePoll();
            }

            async function clearIncidentLog() {
              if (isWasmMode && wasmSim) {
                wasmSim.clearIncidentLog();
                currentState = wasmSim.getState();
                renderUI();
                return;
              }
              if (currentState) {
                currentState.incidentLog = [];
                renderUI();
              }
            }

            function renderUI() {
              if (!currentState) return;

              // Header badges
              document.getElementById("casing-badge").innerText = currentState.pipeTierName + " (Max " + currentState.maxSafeTemp + "°C)";
              const statusBadge = document.getElementById("status-badge");
              const playBtn = document.getElementById("btn-play");
              const alertBox = document.getElementById("alert-box");

              // Damage & Maintenance indicators
              const dmgBadge = document.getElementById("damage-badge");
              const maintBadge = document.getElementById("maint-badge");
              const repairBtn = document.getElementById("btn-quick-repair");
              if (dmgBadge) {
                if (currentState.reactorDamage && currentState.reactorDamage > 0) {
                  dmgBadge.style.display = "inline-block";
                  dmgBadge.innerText = "Damage: " + currentState.reactorDamage.toFixed(1) + "%";
                } else {
                  dmgBadge.style.display = "none";
                }
              }
              if (maintBadge) {
                const effPct = (currentState.maintenanceEfficiency != null ? (currentState.maintenanceEfficiency * 100).toFixed(0) : "100");
                const issues = currentState.maintenanceIssues || 0;
                maintBadge.innerText = `Maint: ${effPct}% (${issues} issues)`;
                maintBadge.style.color = (issues === 0 && (!currentState.reactorDamage || currentState.reactorDamage === 0)) ? "var(--success)" : "var(--warning)";
              }
              if (repairBtn) {
                const hasDmg = (currentState.reactorDamage && currentState.reactorDamage > 0) || (currentState.maintenanceIssues && currentState.maintenanceIssues > 0);
                repairBtn.style.display = hasDmg ? "inline-block" : "none";
                if (hasDmg) {
                  const thresh = currentState.repairThreshold || 120.0;
                  const canRep = (currentState.canRepair !== undefined) ? currentState.canRepair : ((currentState.coreAvgTemp || 0) <= thresh);
                  repairBtn.disabled = !canRep;
                  if (!canRep) {
                    repairBtn.style.opacity = "0.5";
                    repairBtn.style.cursor = "not-allowed";
                    repairBtn.title = `Core too hot to repair (${(currentState.coreAvgTemp||0).toFixed(1)}°C > ${thresh.toFixed(1)}°C safe limit). Cool reactor before repairing.`;
                  } else {
                    repairBtn.style.opacity = "1.0";
                    repairBtn.style.cursor = "pointer";
                    repairBtn.title = `Repair structural damage and clear maintenance issues (Safe limit: ≤${thresh.toFixed(1)}°C)`;
                  }
                }
              }

              // Sync Simulation Options toggles
              const chkStopInc = document.getElementById("chk-stop-incidents");
              const lblStopInc = document.getElementById("lbl-stop-incidents");
              if (chkStopInc && currentState.stopOnIncidents !== undefined) {
                chkStopInc.checked = Boolean(currentState.stopOnIncidents);
                if (lblStopInc) {
                  lblStopInc.innerText = chkStopInc.checked ? "(Enabled)" : "(Off)";
                  lblStopInc.style.color = chkStopInc.checked ? "var(--warning)" : "var(--text-muted)";
                  lblStopInc.style.fontWeight = chkStopInc.checked ? "600" : "normal";
                }
              }

              const chkAutoSupply = document.getElementById("chk-auto-supply-fuel");
              const lblAutoSupply = document.getElementById("lbl-auto-supply-fuel");
              const chkAutoRefuel = document.getElementById("chk-auto-refuel");
              const lblAutoRefuel = document.getElementById("lbl-auto-refuel");
              const autoSupplyVal = Boolean(currentState.autoSupplyFuel !== undefined ? currentState.autoSupplyFuel : currentState.autoReplaceFuel);
              if (chkAutoSupply) {
                chkAutoSupply.checked = autoSupplyVal;
                if (lblAutoSupply) {
                  lblAutoSupply.innerText = autoSupplyVal ? "(Enabled)" : "(Off)";
                  lblAutoSupply.style.color = autoSupplyVal ? "#34d399" : "var(--text-muted)";
                  lblAutoSupply.style.fontWeight = autoSupplyVal ? "600" : "normal";
                }
              }
              if (chkAutoRefuel) {
                chkAutoRefuel.checked = autoSupplyVal;
                if (lblAutoRefuel) {
                  lblAutoRefuel.innerText = autoSupplyVal ? "Enabled" : "Disabled";
                  lblAutoRefuel.style.color = autoSupplyVal ? "#34d399" : "var(--text-muted)";
                }
              }

              // Update Control Rods subsystem card
              let controlRodHatchesCount = (currentState.controlRodCount !== undefined) ? currentState.controlRodCount : 0;
              let totalRodAbsorbed = 0;
              let totalRodInsertion = 0;
              if (currentState.tiles) {
                let counted = 0;
                currentState.tiles.forEach(t => {
                  if (t.hasControlRod) {
                    counted++;
                    totalRodAbsorbed += (t.controlRodFastAbsorbed || 0) + (t.controlRodThermalAbsorbed || 0);
                    totalRodInsertion += (t.controlRodInsertion || 0);
                  }
                });
                if (controlRodHatchesCount === 0) controlRodHatchesCount = counted;
              }
              const avgRodIns = controlRodHatchesCount > 0 ? Math.round(totalRodInsertion / controlRodHatchesCount) : 0;
              const rodsBadge = document.getElementById("rods-count-badge");
              if (rodsBadge) rodsBadge.innerText = `${controlRodHatchesCount} Hatches`;
              const lblGlobalRod = document.getElementById("lbl-global-rod-ins");
              if (lblGlobalRod) lblGlobalRod.innerText = `${avgRodIns}%`;
              const rngGlobalRod = document.getElementById("rng-global-rod-ins");
              if (rngGlobalRod && document.activeElement !== rngGlobalRod) rngGlobalRod.value = avgRodIns;
              const rodsAbsVal = document.getElementById("rods-total-abs-val");
              if (rodsAbsVal) rodsAbsVal.innerText = `${totalRodAbsorbed.toLocaleString()} n/t`;

              if (currentState.exploded) {
                if (isWasmMode && wasmSim && wasmSim.running) {
                  wasmSim.setRunning(false);
                }
                statusBadge.innerText = "MELTDOWN / EXPLODED - STOPPED";
                statusBadge.style.color = "var(--danger)";
                playBtn.innerText = "▶ Start Simulation";
                playBtn.disabled = true;
                alertBox.className = "alert-danger flash";
                alertBox.style.display = "block";
                alertBox.innerHTML = `<strong>💥 MELTDOWN / CATASTROPHIC FAILURE:</strong> ${currentState.explosionReason} <span style="display:block; margin-top:4px; font-size:0.8rem; font-weight:normal;">Simulation automatically halted due to core destruction. Click ↺ Reset to restart.</span>`;
              } else if (currentState.haltedByIncident) {
                if (isWasmMode && wasmSim && wasmSim.running) {
                  wasmSim.setRunning(false);
                }
                statusBadge.innerText = "INCIDENT PAUSE";
                statusBadge.style.color = "var(--warning)";
                playBtn.disabled = false;
                playBtn.innerText = "▶ Resume Simulation";
                alertBox.className = "alert-warning flash";
                alertBox.style.display = "block";
                const reason = currentState.lastHaltIncidentReason || "Bad reactor condition detected";
                alertBox.innerHTML = `<strong>🛑 SIMULATION PAUSED ON INCIDENT:</strong> ${reason} <span style="display:block; margin-top:4px; font-size:0.8rem; font-weight:normal;">Halted by "Stop on Incidents" option. Click <strong>▶ Resume Simulation</strong> to continue or <strong>Step +1</strong> to advance tick-by-tick.</span>`;
              } else if (currentState.reactorDamage && currentState.reactorDamage > 0) {
                // Active structural damage warning: flashes alert banner, but fission continues
                const latestInc = (currentState.incidentLog && currentState.incidentLog.length > 0)
                  ? currentState.incidentLog[currentState.incidentLog.length - 1]
                  : null;
                const incidentMsg = latestInc ? ` [Tick ${latestInc.tick}: ${latestInc.message}]` : "";
                const isNewAlert = (window.__lastAlertTick !== currentState.lastIncidentTick);
                window.__lastAlertTick = currentState.lastIncidentTick;
                alertBox.className = "alert-warning" + (isNewAlert ? " flash" : "");
                alertBox.style.display = "block";
                const effPct = (currentState.maintenanceEfficiency != null ? (currentState.maintenanceEfficiency * 100).toFixed(0) : "100");
                alertBox.innerHTML = `<strong>⚠️ REACTOR DAMAGE DETECTED (${currentState.reactorDamage.toFixed(1)}% Structural Damage, Maint: ${effPct}%):</strong> Operating with maintenance penalties.${incidentMsg} <button type="button" onclick="quickRepair()" style="margin-left:8px; padding:2px 8px; font-size:0.75rem; background:#1e293b; border:1px solid var(--warning); color:var(--warning); cursor:pointer; border-radius:4px;">🔧 Repair Now</button>`;

                playBtn.disabled = false;
                if (currentState.isRunning) {
                  statusBadge.innerText = `DAMAGED (${currentState.reactorDamage.toFixed(1)}%) - RUNNING`;
                  statusBadge.style.color = "var(--warning)";
                  playBtn.innerText = "⏸ Pause Simulation";
                } else {
                  statusBadge.innerText = `DAMAGED (${currentState.reactorDamage.toFixed(1)}%) - PAUSED`;
                  statusBadge.style.color = "var(--warning)";
                  playBtn.innerText = "▶ Resume Simulation";
                }
              } else {
                alertBox.className = "";
                alertBox.style.display = "none";
                playBtn.disabled = false;
                if (currentState.isRunning) {
                  statusBadge.innerText = "SIMULATION RUNNING";
                  statusBadge.style.color = "var(--accent)";
                  playBtn.innerText = "⏸ Pause Simulation";
                } else {
                  statusBadge.innerText = "SIMULATION PAUSED";
                  statusBadge.style.color = "var(--success)";
                  playBtn.innerText = "▶ Resume Simulation";
                }
              }

              // Render Incident & Event Log
              const incidentListEl = document.getElementById("incident-log-list");
              const incidentBadgeEl = document.getElementById("incident-count-badge");
              if (incidentListEl && currentState.incidentLog) {
                const count = currentState.incidentLog.length;
                if (incidentBadgeEl) {
                  incidentBadgeEl.innerText = `${count} event${count === 1 ? "" : "s"}`;
                  incidentBadgeEl.style.color = count > 0 ? "var(--warning)" : "var(--text-muted)";
                  incidentBadgeEl.style.borderColor = count > 0 ? "var(--warning)" : "var(--border-color)";
                }
                if (count === 0) {
                  incidentListEl.innerHTML = `<div style="font-style:italic; font-size:0.75rem; color:#64748b; padding:4px 6px;">No incidents recorded. Chamber operating nominally.</div>`;
                } else {
                  let html = "";
                  for (let i = count - 1; i >= 0; i--) {
                    const ev = currentState.incidentLog[i];
                    let cls = "";
                    let badgeColor = "#94a3b8";
                    let badgeBg = "#1e293b";
                    switch (ev.type) {
                      case "THERMAL_SHOCK":
                        cls = "thermal-shock";
                        badgeColor = "#f59e0b";
                        badgeBg = "rgba(245,158,11,0.2)";
                        break;
                      case "CASING_OVERHEAT":
                        cls = "casing-overheat";
                        badgeColor = "#ef4444";
                        badgeBg = "rgba(239,68,68,0.2)";
                        break;
                      case "FUEL_BURNED":
                        cls = "fuel-burned";
                        badgeColor = "#dc2626";
                        badgeBg = "rgba(220,38,38,0.2)";
                        break;
                      case "HATCH_VOIDED":
                        cls = "hatch-voided";
                        badgeColor = "#38bdf8";
                        badgeBg = "rgba(56,189,248,0.2)";
                        break;
                      case "MELTDOWN":
                        cls = "meltdown";
                        badgeColor = "#f87171";
                        badgeBg = "rgba(185,28,28,0.3)";
                        break;
                      case "REPAIR":
                        cls = "repair";
                        badgeColor = "#34d399";
                        badgeBg = "rgba(16,185,129,0.2)";
                        break;
                      default:
                        cls = "";
                    }
                    html += `<div class="incident-entry ${cls}">
                      <span class="incident-tick">[Tick ${ev.tick}]</span>
                      <span class="incident-badge-type" style="color:${badgeColor}; background:${badgeBg};">${ev.type}</span>
                      <span style="flex:1;">${ev.message}</span>
                      ${ev.damage > 0 ? `<span style="color:#f87171; font-weight:bold;">+${ev.damage.toFixed(1)}% dmg</span>` : ""}
                    </div>`;
                  }
                  incidentListEl.innerHTML = html;
                }
              }

              // Telemetry
              document.getElementById("stat-tick").innerText = currentState.currentTick;
              document.getElementById("stat-max-temp").innerText = currentState.coreMaxTemp + " °C";
              document.getElementById("stat-avg-temp").innerText = currentState.coreAvgTemp + " °C";
              document.getElementById("stat-eff").innerText = (currentState.efficiency * 100).toFixed(1) + " %";
              document.getElementById("stat-neutrons").innerText = currentState.lastNeutrons + " / tick";
              document.getElementById("stat-thermal").innerText = currentState.thermalAbsorbed + " / tick";
              document.getElementById("stat-fast").innerText = currentState.fastAbsorbed + " / tick";
              document.getElementById("stat-steam").innerText = currentState.totalSteam.toLocaleString() + " L";
              document.getElementById("stat-deuterium").innerText = currentState.totalDeuterium.toLocaleString() + " L";
              document.getElementById("stat-tritium").innerText = currentState.totalTritium.toLocaleString() + " L";

              // Turbine config sync
              const activeEl = document.activeElement;
              const turbMat = document.getElementById("turbine-mat");
              if (activeEl !== turbMat && currentState.turbineMaterial) {
                const target = currentState.turbineMaterial;
                const opt = Array.from(turbMat.options).find(o => o.value.toLowerCase() === target.toLowerCase() || o.value.replace(/_/g,"").toLowerCase() === target.replace(/_/g,"").toLowerCase());
                if (opt && turbMat.value !== opt.value) {
                  turbMat.value = opt.value;
                }
              }
              const turbSize = document.getElementById("turbine-size");
              if (activeEl !== turbSize && currentState.turbineSize && turbSize.value !== currentState.turbineSize) {
                turbSize.value = currentState.turbineSize;
              }
              const turbFitting = document.getElementById("turbine-fitting");
              if (activeEl !== turbFitting && currentState.turbineFitting && turbFitting.value !== currentState.turbineFitting) {
                turbFitting.value = currentState.turbineFitting;
              }
              const tierSelect = document.getElementById("tier-select");
              if (activeEl !== tierSelect && currentState.pipeTier !== undefined && parseInt(tierSelect.value) !== currentState.pipeTier) {
                tierSelect.value = currentState.pipeTier;
              }

              // Coolant loop subsystem synchronization
              const loopCard = document.getElementById("coolant-loop-card");
              if (loopCard) {
                loopCard.style.display = "block";
              }
              const tierWarn = document.getElementById("loop-tier-warning");
              if (tierWarn) {
                tierWarn.style.display = currentState.isTier2ConvectiveAllowed ? "none" : "block";
              }

              const cl = currentState.coolantLoop;
              if (cl) {
                const matSelect = document.getElementById("loop-material-select");
                if (matSelect && cl.allowedMaterials) {
                  const matKeys = cl.allowedMaterials.map(m => m.id).join(",");
                  if (matSelect.dataset.lastKeys !== matKeys) {
                    matSelect.dataset.lastKeys = matKeys;
                    matSelect.innerHTML = "";
                    cl.allowedMaterials.forEach(m => {
                      const opt = document.createElement("option");
                      opt.value = m.id;
                      opt.innerText = `${m.name} (${m.tier}, max ${m.maxPressure} bar)`;
                      matSelect.appendChild(opt);
                    });
                  }
                  if (activeEl !== matSelect && cl.material && matSelect.value !== cl.material) {
                    matSelect.value = cl.material;
                  }
                }

                const sizeSel = document.getElementById("loop-size-select");
                if (sizeSel && activeEl !== sizeSel && sizeSel.value !== cl.pipeSize) sizeSel.value = cl.pipeSize;
                const fluidSel = document.getElementById("loop-fluid-select");
                if (fluidSel && activeEl !== fluidSel && fluidSel.value !== cl.fluid) fluidSel.value = cl.fluid;
                const hatchSel = document.getElementById("loop-hatch-tier-select");
                if (hatchSel && activeEl !== hatchSel && cl.hatchTier && hatchSel.value !== cl.hatchTier) {
                  hatchSel.value = cl.hatchTier;
                }
                const dutyIn = document.getElementById("loop-duty-cycle-input");
                if (dutyIn && activeEl !== dutyIn && cl.dutyCyclePercent !== undefined) {
                  if (Math.abs(parseFloat(dutyIn.value || 0) - cl.dutyCyclePercent) > 0.01) {
                    dutyIn.value = Math.round(cl.dutyCyclePercent);
                  }
                }
                const maxFlowIn = document.getElementById("loop-max-flow-input");
                if (maxFlowIn && activeEl !== maxFlowIn) {
                  const currentVal = maxFlowIn.value ? parseFloat(maxFlowIn.value) : 0;
                  const targetVal = cl.maxFlowRateLimit || 0;
                  if (currentVal !== targetVal) {
                    maxFlowIn.value = targetVal > 0 ? targetVal : "";
                  }
                }
                const maxPressIn = document.getElementById("loop-max-pressure-input");
                if (maxPressIn && activeEl !== maxPressIn) {
                  const currentVal = maxPressIn.value ? parseFloat(maxPressIn.value) : 0;
                  const targetVal = cl.maxPressureLimit || 0;
                  if (currentVal !== targetVal) {
                    maxPressIn.value = targetVal > 0 ? targetVal : "";
                  }
                }

                // Throttled / Pressure Constraint Banner
                const throttledBanner = document.getElementById("loop-throttled-banner");
                const throttledText = document.getElementById("loop-throttled-text");
                if (throttledBanner && throttledText) {
                  if (cl.pressureLimited) {
                    throttledBanner.style.display = "flex";
                    throttledText.innerHTML = `<b>Pump Limited by Pressure:</b> ${cl.limitReason}. Duty cycle throttled to <b>${cl.effectiveDutyCyclePercent.toFixed(0)}%</b> (${Math.round(cl.lastPumpPowerEUt)} EU/t).`;
                  } else if (cl.flowLimited) {
                    throttledBanner.style.display = "flex";
                    throttledText.innerHTML = `<b>Pump Limited by Flow:</b> ${cl.limitReason}. Duty cycle throttled to <b>${cl.effectiveDutyCyclePercent.toFixed(0)}%</b> (${Math.round(cl.lastPumpPowerEUt)} EU/t).`;
                  } else {
                    throttledBanner.style.display = "none";
                  }
                }

                // Status Badge
                const statusBadge = document.getElementById("loop-status-badge");
                if (statusBadge) {
                  if (cl.ruptured) {
                    statusBadge.innerText = "💥 RUPTURED";
                    statusBadge.style.color = "var(--danger)";
                  } else if (cl.currentCoolantTempCelsius > cl.maxTempC) {
                    statusBadge.innerText = "🔥 MELTED";
                    statusBadge.style.color = "var(--danger)";
                  } else if (cl.pressureLimited) {
                    statusBadge.innerText = "⚙️ PRESSURE LIMITED";
                    statusBadge.style.color = "#fde047";
                  } else if (cl.flowLimited) {
                    statusBadge.innerText = "⚙️ FLOW LIMITED";
                    statusBadge.style.color = "#38bdf8";
                  } else if (cl.currentPressureBar > cl.maxPressureBar * 0.85) {
                    statusBadge.innerText = "⚠️ HIGH PRESSURE";
                    statusBadge.style.color = "var(--warning)";
                  } else {
                    statusBadge.innerText = "NORMAL";
                    statusBadge.style.color = "var(--success)";
                  }
                }

                const flowEl = document.getElementById("loop-flow-val");
                if (flowEl) {
                  let flowTxt = `${cl.currentFlowRateLPerSec.toFixed(1)} L/s`;
                  if (cl.flowLimited) flowTxt += " [CAPPED]";
                  flowEl.innerText = flowTxt;
                }

                const pressEl = document.getElementById("loop-pressure-val");
                const pressRatio = cl.maxPressureBar > 0 ? (cl.currentPressureBar / cl.maxPressureBar) : 0;
                if (pressEl) pressEl.innerText = `${cl.currentPressureBar.toFixed(1)} / ${cl.maxPressureBar.toFixed(1)} bar (${(pressRatio * 100).toFixed(0)}%)`;

                const pressBar = document.getElementById("loop-pressure-bar");
                if (pressBar) {
                  const pct = Math.min(100, Math.max(0, pressRatio * 100));
                  pressBar.style.width = pct + "%";
                  pressBar.style.background = pct > 90 ? "#ef4444" : (pct > 75 ? "#f59e0b" : "#22c55e");
                }

                const tempEl = document.getElementById("loop-temp-val");
                if (tempEl) tempEl.innerText = `${cl.currentCoolantTempCelsius.toFixed(1)} °C (Max: ${cl.maxTempC.toFixed(0)} °C)`;

                const heatEl = document.getElementById("loop-heat-val");
                if (heatEl) {
                  const mw = (cl.lastHeatExtractedWatts || 0) / 1000000;
                  heatEl.innerText = `${Math.round(cl.lastHeatExtractedEUt).toLocaleString()} EU/t (${mw.toFixed(2)} MWth)`;
                }

                const steamEl = document.getElementById("loop-steam-val");
                if (steamEl) steamEl.innerText = `${Math.round(cl.lastSecondarySteamProducedLt).toLocaleString()} L/t (Water: ${cl.lastSecondaryWaterBoiledLt.toFixed(1)} L/t)`;

                const pumpEl = document.getElementById("loop-pump-val");
                if (pumpEl) {
                  let pText = `-${Math.round(cl.lastPumpPowerEUt).toLocaleString()} EU/t`;
                  if (cl.hatchTier) {
                    pText += ` (${cl.effectiveDutyCyclePercent.toFixed(0)}% duty of ${cl.hatchTier} ${Math.round(cl.hatchVoltage || 0).toLocaleString()} EU/t)`;
                  }
                  pumpEl.innerText = pText;
                }

                const ptsEl = document.getElementById("loop-points-val");
                if (ptsEl) ptsEl.innerText = `${cl.attachedPoints ? cl.attachedPoints.length : 0} cell(s) connected`;

                const bypEl = document.getElementById("loop-byproduct-val");
                if (bypEl) bypEl.innerText = `${cl.totalDeuteriumProduced.toLocaleString()} L D₂ | ${cl.totalTritiumProduced.toLocaleString()} L T₂`;

                const warnEl = document.getElementById("loop-tier-warning");
                if (warnEl) {
                  warnEl.style.display = (!currentState.isTier2ConvectiveAllowed) ? "block" : "none";
                }
              }

              // Turbine telemetry
              if (currentState.powerEstimate) {
                const pe = currentState.powerEstimate;
                let pwrDisplay = formatPowerAndAmps(pe.totalPowerEUt, false);
                if (currentState.pumpPowerEUt && currentState.pumpPowerEUt > 0) {
                  pwrDisplay += ` [Gross: ${Math.round(currentState.grossPowerEUt).toLocaleString()} - Pump: ${Math.round(currentState.pumpPowerEUt).toLocaleString()}]`;
                }
                document.getElementById("stat-power-tier").innerText = formatPowerAndAmps(pe.totalPowerEUt, false);
                document.getElementById("stat-power").innerText = pwrDisplay;
                document.getElementById("turb-eff").innerText = (pe.efficiency * 100).toFixed(1) + " %";
                document.getElementById("turb-opt-flow").innerText = Math.round(pe.optFlowPerTurbine).toLocaleString() + " L/t";
                document.getElementById("turb-sc-pwr").innerText = formatPowerAndAmps(pe.xlstScPowerEUt, true) + " (" + pe.xlstScTurbinesNeeded.toFixed(2) + " units)";
                document.getElementById("turb-hp-pwr").innerText = formatPowerAndAmps(pe.xlstHpPowerEUt, true) + " (" + pe.xlstHpTurbinesNeeded.toFixed(2) + " units)";
                document.getElementById("turb-reg-pwr").innerText = formatPowerAndAmps(pe.xlstPowerEUt, true) + " (" + (pe.isLST ? pe.lstTurbinesNeeded : pe.xlstTurbinesNeeded).toFixed(2) + " units)";

                const eheRow = document.getElementById("ehe-row");
                if (pe.coolantMachine && pe.coolantMachine !== "None" && pe.coolantMachineMode && pe.coolantMachineMode !== "NONE") {
                  eheRow.style.display = "flex";
                  document.getElementById("turb-ehe-info").innerText = pe.coolantMachine + " [" + pe.coolantMachineMode + "] (" + Math.round(pe.coolantSteamProduced).toLocaleString() + " L/t stm, " + Math.round(pe.coolantWaterConsumed).toLocaleString() + " L/t water)";
                } else {
                  eheRow.style.display = "none";
                }

                document.getElementById("turb-flows").innerText =
                  `Reg: ${Math.round(currentState.flowRegularSteam || 0)} | SH: ${Math.round(currentState.flowSuperheatedSteam || 0)} | SC: ${Math.round(currentState.flowSupercriticalSteam || 0)} | HW: ${Math.round(currentState.flowHeavyWaterSteam || 0)}`;
              } else {
                const power = (currentState.history && currentState.history.length > 0)
                  ? currentState.history[currentState.history.length - 1].power
                  : 0;
                document.getElementById("stat-power-tier").innerText = formatPowerAndAmps(power, false);
                document.getElementById("stat-power").innerText = formatPowerAndAmps(power, false);
              }

              // Update Scenario comparison card
              if (currentState.scenarios) {
                const sc = currentState.scenarios;
                if (sc.tight) {
                  document.getElementById("scen-a-desc").innerText = sc.tight.description || ((sc.tight.sizeName || sc.tight.size) + " " + (sc.tight.materialName || sc.tight.material));
                  document.getElementById("scen-a-pwr").innerText = formatPowerAndAmps(sc.tight.powerEUt, true);
                  document.getElementById("scen-a-turb").innerText = sc.tight.totalTurbinesNeeded.toFixed(2) + " units";
                  document.getElementById("scen-a-mach").innerText = sc.tight.coolantMachine !== "None" ? (sc.tight.coolantMachineCount.toFixed(0) + "x " + sc.tight.coolantMachine) : "None";
                }
                if (sc.loose) {
                  document.getElementById("scen-b-desc").innerText = sc.loose.description || ((sc.loose.sizeName || sc.loose.size) + " " + (sc.loose.materialName || sc.loose.material));
                  document.getElementById("scen-b-pwr").innerText = formatPowerAndAmps(sc.loose.powerEUt, true);
                  document.getElementById("scen-b-turb").innerText = sc.loose.totalTurbinesNeeded.toFixed(2) + " units";
                  document.getElementById("scen-b-mach").innerText = sc.loose.coolantMachine !== "None" ? (sc.loose.coolantMachineCount.toFixed(0) + "x " + sc.loose.coolantMachine) : "None";
                }
                if (sc.tight && sc.loose) {
                  const ratio = sc.loose.totalTurbinesNeeded > 0 ? (sc.tight.totalTurbinesNeeded / sc.loose.totalTurbinesNeeded).toFixed(1) : "1.0";
                  const pwrPct = sc.tight.powerEUt > 0 ? ((sc.loose.powerEUt / sc.tight.powerEUt) * 100).toFixed(1) : "100.0";
                  document.getElementById("scen-compare-text").innerText = `Loose mode uses ${ratio}x fewer turbines for ${pwrPct}% of Tight power output.`;
                }
              }

              // Render Machine Process Pipeline
              renderProcessPipeline();

              // Render Fuel & Coolant Byproducts
              renderByproducts();

              // Reconcile and update grid DOM without destroying elements
              updateGridDOM();

              if (selectedTilePos) {
                const tile = currentState.tiles.find(t => t.x === selectedTilePos.x && t.y === selectedTilePos.y);
                if (tile) renderInspector(tile);
              }

              if (currentState.params) {
                syncParamsToUI(currentState.params);
              }

              renderChart();
            }

            let currentZoom = 1.0;
            let isFitMode = false;
            let lastGridWidth = 0;

            const ZOOM_LEVELS = [0.35, 0.45, 0.55, 0.70, 0.85, 1.0, 1.15, 1.30, 1.50];

            function zoomGrid(direction) {
              isFitMode = false;
              if (direction > 0) {
                const next = ZOOM_LEVELS.find(lvl => lvl > currentZoom + 0.03);
                applyZoom(next !== undefined ? next : Math.min(2.0, currentZoom + 0.15));
              } else {
                const reversed = [...ZOOM_LEVELS].reverse();
                const prev = reversed.find(lvl => lvl < currentZoom - 0.03);
                applyZoom(prev !== undefined ? prev : Math.max(0.35, currentZoom - 0.15));
              }
            }

            function resetZoom() {
              isFitMode = false;
              applyZoom(1.0);
            }

            function fitGridToScreen() {
              isFitMode = true;
              recalculateFitZoom();
            }

            function recalculateFitZoom() {
              const gridElem = document.getElementById("reactor-grid");
              const wrapper = gridElem ? gridElem.parentElement : null;
              if (!gridElem || !wrapper || !currentState) return;

              const availableWidth = wrapper.clientWidth - 24;
              const width = currentState.width;
              if (width <= 0 || availableWidth <= 60) return;

              const ratio = width + (width - 1) * (2.0 / 56.0);
              const targetCellSize = Math.max(16, (availableWidth - 12) / ratio);
              const targetScale = Math.min(1.0, targetCellSize / 56.0);
              applyZoom(targetScale);
            }

            function applyZoom(scale) {
              currentZoom = Math.min(2.0, Math.max(0.3, scale));
              const baseSize = 56;
              const cellSize = Math.max(16, Math.round(baseSize * currentZoom));
              const gap = currentZoom < 0.5 ? 1 : 2;
              const codeSize = Math.max(8, Math.round(13 * currentZoom)) + "px";
              const tempSize = Math.max(7, Math.round(10.5 * currentZoom)) + "px";
              const hideTemp = cellSize < 32;

              const grid = document.getElementById("reactor-grid");
              if (grid) {
                grid.style.setProperty("--cell-size", cellSize + "px");
                grid.style.setProperty("--cell-gap", gap + "px");
                grid.style.setProperty("--cell-code-size", codeSize);
                grid.style.setProperty("--cell-temp-size", tempSize);
                if (currentState) {
                  grid.style.gridTemplateColumns = `repeat(${currentState.width}, ${cellSize}px)`;
                }
              }

              const zoomBtn = document.getElementById("zoom-level-btn");
              if (zoomBtn) {
                zoomBtn.textContent = Math.round(currentZoom * 100) + "%";
              }

              const fitBtn = document.querySelector(".zoom-btn.fit-btn");
              if (fitBtn) {
                if (isFitMode) {
                  fitBtn.classList.add("active");
                } else {
                  fitBtn.classList.remove("active");
                }
              }

              const temps = document.querySelectorAll(".cell .cell-temp, .cell .cell-metric");
              for (let i = 0; i < temps.length; i++) {
                temps[i].style.display = hideTemp ? "none" : "";
              }
            }
            """;
    }

    private static String getHtmlScriptsPart2() {
        return """
            function updateGridDOM() {
              const gridElem = document.getElementById("reactor-grid");
              const width = currentState.width;
              const height = currentState.height;
              const totalCells = width * height;

              const dimLabel = document.getElementById("grid-dim-label");
              if (dimLabel) {
                dimLabel.textContent = `Chamber Grid (${width}×${height})`;
              }

              if (lastGridWidth !== width) {
                lastGridWidth = width;
                if (isFitMode) {
                  recalculateFitZoom();
                } else {
                  applyZoom(currentZoom);
                }
              }

              const cellSize = Math.max(16, Math.round(56 * currentZoom));
              const hideTemp = cellSize < 32;

              if (gridElem.children.length !== totalCells) {
                gridElem.innerHTML = "";
                gridElem.style.gridTemplateColumns = `repeat(${width}, ${cellSize}px)`;
                for (let y = 0; y < height; y++) {
                  for (let x = 0; x < width; x++) {
                    const cell = document.createElement("div");
                    cell.className = "cell";
                    cell.dataset.x = x;
                    cell.dataset.y = y;
                    cell.innerHTML = `
                      <img class="cell-icon" style="display:none;" />
                      <span class="cell-code" style="display:none;"></span>
                      <div class="cell-overlay"></div>
                      <span class="cell-metric"${hideTemp ? ' style="display:none;"' : ''}></span>
                      <div class="cell-durability" style="display:none;"><div class="cell-durability-fill"></div></div>
                      <span class="loop-badge">⟳</span>
                      <span class="rod-badge">🕹️</span>
                    `;
                    let lastTrigger = 0;
                    const triggerAction = (e) => {
                      const now = performance.now();
                      if (now - lastTrigger < 50) return;
                      lastTrigger = now;
                      const cx = parseInt(cell.dataset.x);
                      const cy = parseInt(cell.dataset.y);
                      handleCellClick(cx, cy, e);
                    };
                    cell.addEventListener("pointerdown", (e) => {
                      if (e.pointerType === "mouse" && e.button !== 0) return;
                      triggerAction(e);
                    });
                    cell.addEventListener("pointerenter", (e) => {
                      if (interactionMode === "PAINT" && e.buttons === 1) {
                        triggerAction(e);
                      }
                    });
                    cell.addEventListener("click", (e) => {
                      triggerAction(e);
                    });
                    gridElem.appendChild(cell);
                  }
                }
              }

              currentState.tiles.forEach((t, i) => {
                const cell = gridElem.children[i];
                if (!cell) return;
                cell.dataset.x = t.x;
                cell.dataset.y = t.y;

                const isSelected = selectedTilePos && selectedTilePos.x === t.x && selectedTilePos.y === t.y;
                const imgEl = cell.children[0];
                const codeEl = cell.children[1];
                const overlayEl = cell.children[2];
                const metricEl = cell.children[3];
                const durEl = cell.children[4];
                const durFillEl = durEl ? durEl.children[0] : null;

                if (t.code === "NL") {
                  cell.className = "cell wall-cell" + (isSelected ? " selected" : "");
                  if (imgEl) imgEl.style.display = "none";
                  if (codeEl) codeEl.style.display = "none";
                  if (overlayEl) overlayEl.style.backgroundColor = "transparent";
                  if (metricEl) metricEl.style.display = "none";
                  if (durEl) durEl.style.display = "none";
                } else {
                  const isAttached = (currentState.coolantLoop && currentState.coolantLoop.attachedPoints && currentState.coolantLoop.attachedPoints.includes(t.x + "," + t.y)) || t.type === "PASSAGE_CORE";
                  let rodClass = "";
                  if (t.hasControlRod) {
                    rodClass = " has-rod";
                    if (gridDisplayMode === "CONTROL_RODS") {
                      rodClass += (t.controlRodType && t.controlRodType !== "NONE") ? " rod-mode-active" : " rod-mode-empty";
                    }
                  } else if (gridDisplayMode === "CONTROL_RODS") {
                    rodClass = " rod-mode-dimmed";
                  }
                  const desiredClass = "cell" + (isSelected ? " selected" : "") + (isAttached ? " loop-attached" : "") + rodClass;
                  if (cell.className !== desiredClass) {
                    cell.className = desiredClass;
                  }

                  let overlayBg = "transparent";
                  let metricStr = "";
                  let tipText = "";

                  if (gridDisplayMode === "COMPONENTS") {
                    overlayBg = "transparent";
                    metricStr = "";
                    tipText = `${t.name} (${t.x}, ${t.y})` + (t.hasControlRod ? " [Control Rod Hatch]" : "");
                  } else if (gridDisplayMode === "TOTAL_FLUX") {
                    const fl = t.totalFlux || 0;
                    overlayBg = fl > 0 ? getNeutronColor(fl) : "transparent";
                    metricStr = formatFluxValue(fl);
                    tipText = `Total Flux: ${fl.toLocaleString()} n/t (Fast: ${(t.fastFlux||0).toLocaleString()}, Thermal: ${(t.thermalFlux||0).toLocaleString()})`;
                  } else if (gridDisplayMode === "FAST_FLUX") {
                    const fl = t.fastFlux || 0;
                    overlayBg = fl > 0 ? getNeutronColor(fl) : "transparent";
                    metricStr = formatFluxValue(fl);
                    tipText = `Fast Flux: ${fl.toLocaleString()} n/t`;
                  } else if (gridDisplayMode === "THERMAL_FLUX") {
                    const fl = t.thermalFlux || 0;
                    overlayBg = fl > 0 ? getNeutronColor(fl) : "transparent";
                    metricStr = formatFluxValue(fl);
                    tipText = `Thermal Flux: ${fl.toLocaleString()} n/t`;
                  } else if (gridDisplayMode === "ABSORPTION") {
                    const abs = (t.fastAbsorbed || 0) + (t.thermalAbsorbed || 0);
                    const rodAbs = (t.controlRodFastAbsorbed || 0) + (t.controlRodThermalAbsorbed || 0);
                    overlayBg = abs > 0 ? getAbsorptionColor(abs) : "transparent";
                    metricStr = abs > 0 ? formatFluxValue(abs) : "";
                    tipText = `Absorbed: ${abs.toLocaleString()} n/t (Base: ${(abs - rodAbs).toLocaleString()}, Bottom Rod: ${rodAbs.toLocaleString()})`;
                  } else if (gridDisplayMode === "CONTROL_RODS") {
                    if (t.hasControlRod) {
                      if (t.controlRodType && t.controlRodType !== "NONE") {
                        const ins = Math.round(t.controlRodInsertion || 0);
                        const rodCol = getControlRodColor(t.controlRodType);
                        overlayBg = ins > 0 ? `linear-gradient(to top, ${rodCol} ${ins}%, rgba(15, 23, 42, 0.75) ${ins}%)` : "rgba(30, 41, 59, 0.6)";
                        metricStr = ins + "%";
                        tipText = `Control rod: ${t.controlRodTypeName || t.controlRodType} (${ins}% inserted)`;
                      } else {
                        overlayBg = "rgba(30, 41, 59, 0.5)";
                        metricStr = "EMPTY";
                        tipText = "Control rod hatch: empty (no rod inserted)";
                      }
                    } else {
                      overlayBg = "rgba(15, 20, 28, 0.85)";
                      metricStr = "";
                      tipText = "No control rod hatch installed";
                    }
                  } else {
                    overlayBg = getTemperatureColor(t.temp, currentState.maxSafeTemp);
                    metricStr = Math.round(t.temp) + "°C";
                    tipText = `Temperature: ${t.temp.toFixed(1)}°C (Max Safe: ${currentState.maxSafeTemp}°C)`;
                  }

                  if (gridDisplayMode !== "COMPONENTS" && gridDisplayMode !== "CONTROL_RODS" && t.temp > (currentState.maxSafeTemp || 3250) * 0.85) {
                    if (Math.floor(Date.now() / 400) % 2 === 0) {
                      overlayBg = "rgba(255, 0, 0, 0.45)";
                    }
                  }

                  if (overlayEl && overlayEl.style.backgroundColor !== overlayBg) {
                    overlayEl.style.backgroundColor = overlayBg;
                  }

                  if (t.type && t.type !== "EMPTY") {
                    const iconSrc = getIconUrl(t.type);
                    if (imgEl.getAttribute("src") !== iconSrc) {
                      imgEl.src = iconSrc;
                      imgEl.onerror = () => {
                        imgEl.src = '/api/icon/' + t.type;
                        imgEl.onerror = () => {
                          imgEl.style.display = "none";
                          if (codeEl) {
                            codeEl.textContent = t.code;
                            codeEl.style.display = "block";
                          }
                        };
                      };
                    }
                    if (imgEl.style.display !== "block") {
                      imgEl.style.display = "block";
                    }
                    if (codeEl.style.display !== "none") {
                      codeEl.style.display = "none";
                    }
                  } else {
                    if (imgEl.style.display !== "none") {
                      imgEl.style.display = "none";
                    }
                    if (codeEl.style.display !== "none") {
                      codeEl.style.display = "none";
                    }
                  }

                  if (metricEl.textContent !== metricStr) {
                    metricEl.textContent = metricStr;
                  }
                  if (!hideTemp && metricEl.style.display === "none") {
                    metricEl.style.display = "block";
                  } else if (hideTemp && metricEl.style.display !== "none") {
                    metricEl.style.display = "none";
                  }

                  cell.title = `${t.name} (${t.x}, ${t.y})\n${tipText}`;

                  if (t.isFuel && t.durabilityPct < 99.9) {
                    if (durEl) {
                      durEl.style.display = "block";
                      const pct = Math.max(0, Math.min(100, t.durabilityPct));
                      durFillEl.style.width = pct + "%";
                      durFillEl.style.backgroundColor = pct > 50 ? "#22c55e" : (pct > 20 ? "#eab308" : "#ef4444");
                    }
                  } else if (durEl) {
                    durEl.style.display = "none";
                  }
                }
              });
            }

            async function handleCellClick(x, y, e) {
              if (isLoopAttachMode) {
                toggleLoopAttachment(x, y);
                return;
              }
              const tile = currentState && currentState.tiles.find(t => t.x === x && t.y === y);
              if (!tile) return;
              if (tile.code === "NL") {
                selectCell(x, y);
                return;
              }
              if (interactionMode === "INSPECT" || (e && e.shiftKey)) {
                selectCell(x, y);
              } else {
                await setTile(x, y, activePaletteType);
                selectCell(x, y);
              }
            }

            function selectCell(x, y) {
              selectedTilePos = { x, y };
              const tile = currentState && currentState.tiles.find(t => t.x === x && t.y === y);
              if (tile) {
                renderInspector(tile);
              }
              const gridElem = document.getElementById("reactor-grid");
              if (gridElem) {
                for (let i = 0; i < gridElem.children.length; i++) {
                  const c = gridElem.children[i];
                  const isSel = parseInt(c.dataset.x) === x && parseInt(c.dataset.y) === y;
                  const isWall = c.classList.contains("wall-cell");
                  const isAttached = c.classList.contains("loop-attached");
                  const desired = "cell" + (isWall ? " wall-cell" : "") + (isSel ? " selected" : "") + (isAttached ? " loop-attached" : "");
                  if (c.className !== desired) c.className = desired;
                }
              }
            }

            function renderInspector(t) {
              const el = document.getElementById("tile-inspector");
              let extraHtml = "";
              if (t.code === "NL") {
                extraHtml = `
                  <div class="stat-row"><span>Structure:</span><span class="stat-val" style="color:var(--text-muted);">Reflective Casing Wall</span></div>
                  <div class="stat-row"><span>Wall Reflection:</span><span class="stat-val" style="color:#38bdf8;">50% Bounce</span></div>
                  <div class="stat-row"><span>Wall Absorption:</span><span class="stat-val" style="color:#f59e0b;">12 EU/n &rarr; Pool</span></div>
                `;
              } else if (t.type === "PASSAGE_CORE") {
                extraHtml = `
                  <div class="stat-row"><span>Component:</span><span class="stat-val" style="color:#00d2ff;">Pressurized Core Passage</span></div>
                  <div class="stat-row"><span>Heat Transfer (k):</span><span class="stat-val" style="color:#38bdf8;">0.90 (High Convection)</span></div>
                  <div class="stat-row"><span>Moderation / Scatter:</span><span class="stat-val" style="color:#a3e635;">85% / 80%</span></div>
                  <div class="stat-row"><span>Coupling:</span><span class="stat-val" style="color:var(--success);">Direct Convective Loop</span></div>
                `;
              } else if (t.isFuel) {
                const maxDur = (t.durabilityPct > 0) ? Math.round(t.durability / (t.durabilityPct / 100)) : t.durability;
                const burnRate = t.lastDurabilityLoss || 0;
                const durPerMin = burnRate * 1200;
                const minsLeft = (burnRate > 0 && t.durability > 0)
                  ? (t.durability / durPerMin).toFixed(1) + " min"
                  : (t.durability <= 0 ? "0 min" : "Infinity");
                const depBadge = t.isDepleted
                  ? '<span class="badge" style="color:var(--danger); background:rgba(255,71,87,0.2);">⚠️ DEPLETED</span>'
                  : '<span class="badge" style="color:var(--success);">ACTIVE</span>';
                extraHtml = `
                  <div class="stat-row"><span>Durability:</span><span class="stat-val">${t.durability.toLocaleString()} / ${maxDur.toLocaleString()} (${t.durabilityPct}%)</span></div>
                  <div class="stat-row"><span>Rod Status:</span><span class="stat-val">${depBadge}</span></div>
                  <div class="stat-row"><span>Depleted Output:</span><span class="stat-val" style="color:#34d399; font-weight:600;"><span class="badge" style="font-size:0.68rem; padding:1px 4px; color:#34d399; border-color:#059669;">${t.depletedCode || "DEP"}</span> ${t.depletedName || "Depleted Fuel Rod"}</span></div>
                  <div class="stat-row"><span>Burn Rate:</span><span class="stat-val" style="color:#fbbf24;">${burnRate.toFixed(2)} / tick (${Math.round(durPerMin).toLocaleString()} / min)</span></div>
                  <div class="stat-row"><span>Est. Remaining Lifespan:</span><span class="stat-val" style="color:#38bdf8;">${minsLeft}</span></div>
                `;
              } else if (t.fluidName && t.fluidName.length > 0) {
                const cap = t.fluidCapacity || 2000;
                const pct = Math.round((t.fluidAmount / cap) * 100);
                const isCoolant = t.type === "HATCH_IC2_COOLANT";
                const ratio = isCoolant ? 1 : 160;
                const dryBadge = t.wasDry
                  ? `<span class="badge" style="color:var(--danger); background:rgba(255,71,87,0.2);">${isCoolant ? "⚠️ DRAINED DRY" : "⚠️ BOILED DRY"}</span>`
                  : '<span class="badge" style="color:var(--success);">NORMAL</span>';
                const steamFlow = t.lastProduced || 0;
                const coolantTurnover = steamFlow > 0 ? (steamFlow / ratio).toFixed(0) : 0;
                const cumProduced = isCoolant ? (t.outputFluidAmount || 0) : (t.steamAmount || 0);
                const isLiquidFuel = t.type && t.type.startsWith("HATCH_LIQUID_FUEL_");
                let liqFuelHtml = "";
                if (isLiquidFuel) {
                  const burnRateL = t.lastLiquidBurned || 0;
                  liqFuelHtml = `
                    <div class="stat-row"><span>Depleted Output:</span><span class="stat-val" style="color:#38bdf8; font-weight:600;">${t.depletedName || "Depleted Fuel Fluid"}</span></div>
                    <div class="stat-row"><span>Fuel Consumption:</span><span class="stat-val" style="color:#f59e0b;">${burnRateL} L/t (${(burnRateL * 1200).toLocaleString()} L/min)</span></div>
                  `;
                }
                extraHtml = `
                  <div class="stat-row"><span>Coolant:</span><span class="stat-val">${t.fluidName}</span></div>
                  <div class="stat-row"><span>Coolant Volume:</span><span class="stat-val">${t.fluidAmount} / ${cap} L (${pct}%)</span></div>
                  ${liqFuelHtml}
                  <div class="stat-row"><span>Live Turnover:</span><span class="stat-val" style="color:#38bdf8;">${coolantTurnover} L/t coolant &rarr; ${steamFlow} L/t ${isCoolant ? "hot coolant" : "steam"}</span></div>
                  <div class="stat-row"><span>Cumulative ${isCoolant ? "Hot Coolant" : "Steam"}:</span><span class="stat-val">${cumProduced.toLocaleString()} L</span></div>
                  <div class="stat-row"><span>Hatch Status:</span><span class="stat-val">${dryBadge}</span></div>
                `;
              }
              const iconHtml = (t.type && t.type !== "EMPTY" && t.code !== "NL")
                ? `<img src="${getIconUrl(t.type)}" onerror="this.src='/api/icon/' + t.type" class="inspector-icon" alt="${t.code}" />`
                : "";

              let fluxHtml = "";
              if (t.code !== "NL" && ((t.totalFlux && t.totalFlux > 0) || (t.fastFlux && t.fastFlux > 0) || (t.thermalFlux && t.thermalFlux > 0) || (t.fastAbsorbed && t.fastAbsorbed > 0) || (t.thermalAbsorbed && t.thermalAbsorbed > 0) || t.directEU)) {
                fluxHtml = `
                  <div style="background:#10141d; border:1px solid #373737; border-radius:3px; padding:6px 8px; margin-top:8px;">
                    <div style="font-weight:700; font-size:0.75rem; color:#94a3b8; text-transform:uppercase; margin-bottom:4px; letter-spacing:0.5px;">Neutron Kinetics</div>
                    <div class="stat-row" style="margin-bottom:2px;"><span>Total Flux:</span><span class="stat-val" style="color:#38bdf8;">${(t.totalFlux || 0).toLocaleString()} n/t</span></div>
                    <div class="stat-row" style="margin-bottom:2px;"><span>Fast Flux:</span><span class="stat-val" style="color:#fbbf24;">${(t.fastFlux || 0).toLocaleString()} n/t</span></div>
                    <div class="stat-row" style="margin-bottom:2px;"><span>Thermal Flux:</span><span class="stat-val" style="color:#4ade80;">${(t.thermalFlux || 0).toLocaleString()} n/t</span></div>
                    <div class="stat-row" style="margin-bottom:2px;"><span>Fast Absorbed:</span><span class="stat-val" style="color:#cbd5e1;">${(t.fastAbsorbed || 0).toLocaleString()} n/t</span></div>
                    <div class="stat-row" style="margin-bottom:2px;"><span>Thermal Absorbed:</span><span class="stat-val" style="color:#cbd5e1;">${(t.thermalAbsorbed || 0).toLocaleString()} n/t</span></div>
                    ${t.directEU ? `<div class="stat-row" style="margin-top:4px; padding-top:4px; border-top:1px dashed #373737;"><span>Radiovoltaic Output:</span><span class="stat-val" style="color:#38bdf8;">${t.directEU.toLocaleString()} EU/t</span></div>` : ''}
                  </div>
                `;
              }

              let coolingMethodHtml = "";
              const isLoopAttached = (currentState.coolantLoop && currentState.coolantLoop.attachedPoints && currentState.coolantLoop.attachedPoints.includes(t.x + "," + t.y)) || t.type === "PASSAGE_CORE";
              if (isLoopAttached) {
                coolingMethodHtml = `<div class="stat-row"><span>Cell Cooling:</span><span class="stat-val" style="color:#00d2ff; font-weight:600;">🔄 Convective Loop (${t.type === "PASSAGE_CORE" ? "Passage Tile" : "Attached Cell"})</span></div>`;
              } else if (t.fluidName && t.fluidName.length > 0) {
                const methodLabel = t.fluidName.includes("coolant")
                  ? `💧 Conductive Liquid Cooling (${t.fluidName})`
                  : `💧 Conductive Fluid Boiling (${t.fluidName})`;
                coolingMethodHtml = `<div class="stat-row"><span>Cell Cooling:</span><span class="stat-val" style="color:#38bdf8; font-weight:600;">${methodLabel}</span></div>`;
              } else if (t.type && t.type.startsWith("VENT_")) {
                coolingMethodHtml = `<div class="stat-row"><span>Cell Cooling:</span><span class="stat-val" style="color:#a855f7; font-weight:600;">🌬️ IC2 Component Heat Vent</span></div>`;
              } else if (t.type && t.type.startsWith("EXCHANGER_")) {
                coolingMethodHtml = `<div class="stat-row"><span>Cell Cooling:</span><span class="stat-val" style="color:#a855f7; font-weight:600;">🔄 IC2 Heat Exchanger</span></div>`;
              } else if (t.type && t.type.startsWith("COOLANT_CELL_")) {
                coolingMethodHtml = `<div class="stat-row"><span>Cell Cooling:</span><span class="stat-val" style="color:#06b6d4; font-weight:600;">🧊 IC2 Coolant Cell Storage</span></div>`;
              } else if (t.isFuel) {
                coolingMethodHtml = `<div class="stat-row"><span>Cell Cooling:</span><span class="stat-val" style="color:#f43f5e; font-weight:600;">⚠️ Uncooled Fuel (Passive Conduction)</span></div>`;
              } else if (t.code !== "NL" && t.type !== "EMPTY") {
                coolingMethodHtml = `<div class="stat-row"><span>Cell Cooling:</span><span class="stat-val" style="color:var(--text-muted);">None (Passive Conduction)</span></div>`;
              }

              let controlRodHtml = "";
              if (t.code !== "NL") {
                if (isLoopAttached) {
                  controlRodHtml = `
                    <div style="background:#10141d; border:1px solid #373737; border-radius:3px; padding:6px 8px; margin-top:8px;">
                      <div style="font-weight:700; font-size:0.75rem; color:#64748b; text-transform:uppercase; letter-spacing:0.5px;">🕹️ Control Rod Hatch</div>
                      <div style="font-size:0.72rem; color:var(--warning); margin-top:4px;">⚠️ Core face occupied by convective loop passage. Control rod hatches cannot be installed on loop cells.</div>
                    </div>
                  `;
                } else if (!t.hasControlRod) {
                  controlRodHtml = `
                    <div style="background:#10141d; border:1px solid #373737; border-radius:3px; padding:6px 8px; margin-top:8px;">
                      <div style="display:flex; justify-content:space-between; align-items:center;">
                        <span style="font-weight:700; font-size:0.75rem; color:#94a3b8; text-transform:uppercase; letter-spacing:0.5px;">🕹️ Control Rod Hatch</span>
                        <button type="button" onclick="toggleTileControlRod(${t.x}, ${t.y}, true)" style="padding:2px 8px; font-size:0.72rem; background:#1e293b; border-color:#00d2ff; color:#00d2ff;">+ Install Hatch</button>
                      </div>
                      <div style="font-size:0.72rem; color:var(--text-muted); margin-top:4px;">No control rod hatch installed at this coordinate.</div>
                    </div>
                  `;
                } else {
                  const ins = Math.round(t.controlRodInsertion || 0);
                  const rodType = t.controlRodType || "NONE";
                  const fastAbs = t.controlRodFastAbsorbed || 0;
                  const thermAbs = t.controlRodThermalAbsorbed || 0;
                  controlRodHtml = `
                    <div style="background:#10141d; border:1px solid #00e5ff; border-radius:3px; padding:6px 8px; margin-top:8px;">
                      <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:6px;">
                        <span style="font-weight:700; font-size:0.75rem; color:#00e5ff; text-transform:uppercase; letter-spacing:0.5px;">🕹️ Control Rod Hatch</span>
                        <button type="button" onclick="toggleTileControlRod(${t.x}, ${t.y}, false)" style="padding:1px 6px; font-size:0.68rem; background:#29151b; border-color:var(--danger); color:var(--danger);" title="Remove hatch from this cell">Remove</button>
                      </div>
                      <div style="margin-bottom:6px;">
                        <div style="font-size:0.72rem; color:var(--text-muted); margin-bottom:2px;">Rod Material:</div>
                        <select onchange="changeTileControlRodMaterial(${t.x}, ${t.y}, this.value)" style="width:100%; font-size:0.75rem; padding:3px 6px; height:auto; min-height:28px;">
                          <option value="NONE" ${rodType === 'NONE' ? 'selected' : ''}>Empty (No rod inserted)</option>
                          <option value="SILVER" ${rodType === 'SILVER' ? 'selected' : ''}>Silver-Indium-Cadmium (85% F / 80% Th)</option>
                          <option value="BORON" ${rodType === 'BORON' ? 'selected' : ''}>Boron Carbide (70% F / 90% Th)</option>
                          <option value="CADMIUM" ${rodType === 'CADMIUM' ? 'selected' : ''}>Cadmium (50% F / 95% Th)</option>
                          <option value="INDIUM" ${rodType === 'INDIUM' ? 'selected' : ''}>Indium (60% F / 85% Th)</option>
                          <option value="HAFNIUM" ${rodType === 'HAFNIUM' ? 'selected' : ''}>Hafnium (80% F / 75% Th)</option>
                        </select>
                      </div>
                      <div style="margin-bottom:6px;">
                        <div style="display:flex; justify-content:space-between; font-size:0.72rem; margin-bottom:2px;">
                          <span>Insertion:</span>
                          <span id="lbl-rod-ins-${t.x}-${t.y}" style="font-weight:700; color:#00e5ff;">${ins}%</span>
                        </div>
                        <input type="range" min="0" max="100" value="${ins}"
                          oninput="document.getElementById('lbl-rod-ins-${t.x}-${t.y}').innerText = this.value + '%'"
                          onchange="updateTileControlRodInsertion(${t.x}, ${t.y}, parseInt(this.value))"
                          style="width:100%; cursor:pointer; accent-color:#00e5ff;" />
                        <div style="display:flex; gap:4px; margin-top:3px;">
                          <button type="button" onclick="updateTileControlRodInsertion(${t.x}, ${t.y}, 0)" style="flex:1; padding:2px; font-size:0.68rem; min-height:24px;">0% (Retract)</button>
                          <button type="button" onclick="updateTileControlRodInsertion(${t.x}, ${t.y}, 50)" style="flex:1; padding:2px; font-size:0.68rem; min-height:24px;">50%</button>
                          <button type="button" onclick="updateTileControlRodInsertion(${t.x}, ${t.y}, 100)" style="flex:1; padding:2px; font-size:0.68rem; min-height:24px; background:#b91c1c; border-color:#dc2626;">100% (SCRAM)</button>
                        </div>
                      </div>
                      ${(fastAbs > 0 || thermAbs > 0) ? `
                        <div style="border-top:1px dashed #373737; padding-top:4px; margin-top:4px; font-size:0.72rem;">
                          <div class="stat-row" style="margin-bottom:1px;"><span>Rod Fast Absorbed:</span><span class="stat-val" style="color:#fbbf24;">${fastAbs.toLocaleString()} n/t</span></div>
                          <div class="stat-row" style="margin-bottom:1px;"><span>Rod Thermal Absorbed:</span><span class="stat-val" style="color:#4ade80;">${thermAbs.toLocaleString()} n/t</span></div>
                        </div>
                      ` : ''}
                    </div>
                  `;
                }
              }

              el.innerHTML = `
                <div style="display:flex; align-items:center; gap:10px; margin-bottom:10px;">
                  ${iconHtml}
                  <div>
                    <div style="font-weight:bold; font-size:1rem; color:#fff;">${t.name}</div>
                    <div style="font-size:0.75rem; color:var(--text-muted);">Position: (${t.x}, ${t.y})</div>
                  </div>
                </div>
                <div class="stat-row"><span>Temperature:</span><span class="stat-val">${t.temp} °C</span></div>
                ${coolingMethodHtml}
                ${controlRodHtml}
                ${extraHtml}
                ${fluxHtml}
              `;
            }

            function syncParamsToUI(p) {
              if (!p) return;
              const active = document.activeElement;
              if (active && active.id && (active.id.startsWith("p-") || active.id === "sim-profile-select")) return;
              const setVal = (id, val) => {
                const el = document.getElementById(id);
                if (el && active !== el && el.value != val) el.value = val;
              };
              setVal("p-eu-per-degree", p.euPerDegree != null ? p.euPerDegree : 32.0);
              setVal("p-base-conductance", p.baseHatchConductance != null ? p.baseHatchConductance : 2.0);
              setVal("p-fission-heat", p.fissionHeatPerNeutron != null ? p.fissionHeatPerNeutron : 77.2);
              setVal("p-fiss-mult", p.globalThermalFissionMultiplier != null ? p.globalThermalFissionMultiplier : (p.thermalFissionMultiplier != null ? p.thermalFissionMultiplier : 0.348));
              setVal("p-ambient-temp", p.ambientTemp);
              setVal("p-hatch-cap", p.hatchCapacity);
              setVal("p-turn-curve", p.turnoverCurve);
              setVal("p-turn-dt", p.turnoverDeltaTMax);
              setVal("p-turn-exp", p.turnoverExponent);
              setVal("p-feed-rate", p.coolantFeedRate);
              setVal("p-cooling-heat", p.coolingHeatPerLiter);
              setVal("p-ic2-cooling-heat", p.ic2CoolantHeatPerLiter);
              setVal("p-hp-boil", p.hpWaterBoilingPoint);
            }

            async function submitSimParams() {
              const euDeg = document.getElementById("p-eu-per-degree") ? document.getElementById("p-eu-per-degree").value : 32.0;
              const baseCond = document.getElementById("p-base-conductance") ? document.getElementById("p-base-conductance").value : 2.0;
              const fHeat = document.getElementById("p-fission-heat") ? document.getElementById("p-fission-heat").value : 77.2;
              const fMult = document.getElementById("p-fiss-mult") ? document.getElementById("p-fiss-mult").value : 0.348;
              const amb = document.getElementById("p-ambient-temp").value;
              const cap = document.getElementById("p-hatch-cap").value;
              const curve = document.getElementById("p-turn-curve").value;
              const dt = document.getElementById("p-turn-dt").value;
              const exp = document.getElementById("p-turn-exp").value;
              const feed = document.getElementById("p-feed-rate").value;
              const ch = document.getElementById("p-cooling-heat").value;
              const ic2Ch = document.getElementById("p-ic2-cooling-heat").value;
              const hpBoil = document.getElementById("p-hp-boil").value;

              if (isWasmMode && wasmSim) {
                wasmSim.setParam("euPerDegree", euDeg);
                wasmSim.setParam("baseHatchConductance", baseCond);
                wasmSim.setParam("fissionHeat", fHeat);
                wasmSim.setParam("fissionMult", fMult);
                wasmSim.setParam("ambientTemp", amb);
                wasmSim.setParam("hatchCapacity", cap);
                wasmSim.setParam("turnoverCurve", curve);
                wasmSim.setParam("turnoverDeltaTMax", dt);
                wasmSim.setParam("turnoverExponent", exp);
                wasmSim.setParam("coolantFeedRate", feed);
                wasmSim.setParam("coolingHeat", ch);
                wasmSim.setParam("ic2CoolantHeat", ic2Ch);
                wasmSim.setParam("hpBoil", hpBoil);
                currentState = wasmSim.getState();
                renderUI();
                return;
              }

              const url = `/api/set-params?euPerDegree=${euDeg}&baseHatchConductance=${baseCond}&fissionHeat=${fHeat}&fissionMult=${fMult}&ambientTemp=${amb}&hatchCapacity=${cap}&turnoverCurve=${curve}&turnoverDeltaTMax=${dt}&turnoverExponent=${exp}&coolantFeedRate=${feed}&coolingHeat=${ch}&ic2CoolantHeat=${ic2Ch}&hpBoil=${hpBoil}`;
              await fetch(url);
              await fetchState();
              schedulePoll();
            }

            async function resetSimParams() {
              if (isWasmMode && wasmSim) {
                wasmSim.setParam("reset", "true");
                currentState = wasmSim.getState();
                renderUI();
                return;
              }
              await fetch("/api/set-params?reset=true");
              await fetchState();
              schedulePoll();
            }

            function applyProfilePreset(profile) {
              switch (profile) {
                case "BALANCED":
                  if (document.getElementById("p-base-conductance")) document.getElementById("p-base-conductance").value = 2.0;
                  document.getElementById("p-hatch-cap").value = 8000;
                  document.getElementById("p-turn-curve").value = "SIGMOID";
                  document.getElementById("p-turn-dt").value = 100;
                  document.getElementById("p-turn-exp").value = 1.0;
                  document.getElementById("p-feed-rate").value = 999999;
                  document.getElementById("p-cooling-heat").value = 5.0;
                  document.getElementById("p-t-low").value = 800;
                  document.getElementById("p-t-high").value = 3200;
                  document.getElementById("p-react-pow").value = 1.2;
                  document.getElementById("p-fiss-mult").value = 0.348;
                  if (document.getElementById("p-fission-heat")) document.getElementById("p-fission-heat").value = 77.2;
                  document.getElementById("p-hp-boil").value = 180;
                  break;
                case "DANGER":
                  if (document.getElementById("p-base-conductance")) document.getElementById("p-base-conductance").value = 1.5;
                  document.getElementById("p-hatch-cap").value = 1000;
                  document.getElementById("p-turn-curve").value = "EXPONENTIAL";
                  document.getElementById("p-turn-dt").value = 80;
                  document.getElementById("p-turn-exp").value = 1.8;
                  document.getElementById("p-feed-rate").value = 1000;
                  document.getElementById("p-cooling-heat").value = 3.0;
                  document.getElementById("p-t-low").value = 950;
                  document.getElementById("p-t-high").value = 3200;
                  document.getElementById("p-react-pow").value = 1.6;
                  document.getElementById("p-fiss-mult").value = 1.25;
                  document.getElementById("p-hp-boil").value = 200;
                  break;
                case "FLASH_BOIL":
                  if (document.getElementById("p-base-conductance")) document.getElementById("p-base-conductance").value = 2.0;
                  document.getElementById("p-hatch-cap").value = 500;
                  document.getElementById("p-turn-curve").value = "SIGMOID";
                  document.getElementById("p-turn-dt").value = 60;
                  document.getElementById("p-turn-exp").value = 2.0;
                  document.getElementById("p-feed-rate").value = 500;
                  document.getElementById("p-cooling-heat").value = 4.0;
                  document.getElementById("p-t-low").value = 800;
                  document.getElementById("p-t-high").value = 2800;
                  document.getElementById("p-react-pow").value = 1.2;
                  document.getElementById("p-fiss-mult").value = 1.1;
                  document.getElementById("p-hp-boil").value = 200;
                  break;
                case "SAFE":
                  if (document.getElementById("p-base-conductance")) document.getElementById("p-base-conductance").value = 2.5;
                  document.getElementById("p-hatch-cap").value = 8000;
                  document.getElementById("p-turn-curve").value = "LINEAR";
                  document.getElementById("p-turn-dt").value = 150;
                  document.getElementById("p-turn-exp").value = 1.0;
                  document.getElementById("p-feed-rate").value = 8000;
                  document.getElementById("p-cooling-heat").value = 6.0;
                  document.getElementById("p-t-low").value = 650;
                  document.getElementById("p-t-high").value = 2200;
                  document.getElementById("p-react-pow").value = 1.0;
                  document.getElementById("p-fiss-mult").value = 0.95;
                  document.getElementById("p-hp-boil").value = 200;
                  break;
              }
              submitSimParams();
            }

            let lastChartTick = -1;
            function renderChart() {
              const canvas = document.getElementById("chartCanvas");
              if (!canvas) return;
              if (!currentState || !currentState.history || currentState.history.length === 0) return;
              if (currentState.currentTick === lastChartTick) return;
              lastChartTick = currentState.currentTick;

              const ctx = canvas.getContext("2d");
              const w = canvas.width;
              const h = canvas.height;
              ctx.clearRect(0, 0, w, h);

              const hist = currentState.history;
              const maxT = Math.max(100, ...hist.map(p => p.maxT));

              ctx.strokeStyle = "rgba(255,255,255,0.1)";
              ctx.beginPath();
              ctx.moveTo(0, h * 0.25); ctx.lineTo(w, h * 0.25);
              ctx.moveTo(0, h * 0.50); ctx.lineTo(w, h * 0.50);
              ctx.moveTo(0, h * 0.75); ctx.lineTo(w, h * 0.75);
              ctx.stroke();

              // Peak Temp curve (red)
              ctx.strokeStyle = "#ff4757";
              ctx.lineWidth = 2;
              ctx.beginPath();
              hist.forEach((p, idx) => {
                const x = (idx / (hist.length - 1)) * w;
                const y = h - (p.maxT / maxT) * (h - 20) - 10;
                if (idx === 0) ctx.moveTo(x, y);
                else ctx.lineTo(x, y);
              });
              ctx.stroke();

              // Average Temp curve (cyan)
              ctx.strokeStyle = "#00d2ff";
              ctx.lineWidth = 1.5;
              ctx.beginPath();
              hist.forEach((p, idx) => {
                const x = (idx / (hist.length - 1)) * w;
                const y = h - (p.avgT / maxT) * (h - 20) - 10;
                if (idx === 0) ctx.moveTo(x, y);
                else ctx.lineTo(x, y);
              });
              ctx.stroke();
            }

            async function togglePlay() {
              if (currentState && currentState.exploded) {
                return;
              }
              if (isWasmMode && wasmSim) {
                if (currentState && currentState.haltedByIncident) {
                  wasmSim.clearHaltedByIncident();
                }
                wasmSim.togglePlay();
                currentState = wasmSim.getState();
                renderUI();
                return;
              }
              await fetch("/api/play");
              await fetchState();
              schedulePoll();
            }

            async function stepSim(count) {
              if (currentState && currentState.exploded) {
                return;
              }
              if (isWasmMode && wasmSim) {
                if (currentState && currentState.haltedByIncident) {
                  wasmSim.clearHaltedByIncident();
                }
                wasmSim.stepTicks(count);
                currentState = wasmSim.getState();
                renderUI();
                return;
              }
              await fetch(`/api/step?count=${count}`);
              await fetchState();
              schedulePoll();
            }

            async function resetSim() {
              lastChartTick = -1;
              if (isWasmMode && wasmSim) {
                wasmSim.reset();
                currentState = wasmSim.getState();
                renderUI();
                return;
              }
              await fetch("/api/reset");
              await fetchState();
              schedulePoll();
            }
            """;
    }

    private static String getHtmlScriptsPart3() {
        return """
            const PRESET_TO_VOLTAGE = {
              "60A_ELECTRUM_5X5": "EV",
              "60A_PLATINUM_9X9": "IV",
              "60A_OSMIUM_9X9": "LuV",
              "60A_QUANTIUM_13X13": "ZPM",
              "60A_FLUXED_13X13": "UV",
              "60A_PLUTONIUM_13X13": "UHV",
              "BEST_ELECTRUM_5X5": "EV",
              "BEST_PLATINUM_7X7": "IV",
              "BEST_PLATINUM_9X9": "IV",
              "BEST_OSMIUM_9X9": "LuV",
              "BEST_QUANTIUM_9X9": "ZPM",
              "BEST_QUANTIUM_13X13": "ZPM",
              "BEST_FLUXED_9X9": "UV",
              "BEST_FLUXED_13X13": "UV",
              "BEST_PLUTONIUM_9X9": "UHV",
              "BEST_PLUTONIUM_13X13": "UHV"
            };

            const TIER_TO_VOLTAGE = ["EV", "IV", "LuV", "ZPM", "UV", "UHV"];

            function formatEuCompact(eut) {
              if (!eut || isNaN(eut)) return "0 EU/t";
              if (eut >= 1e8) return Math.round(eut / 1e6) + "M EU/t";
              if (eut >= 1e6) {
                const val = (eut / 1e6).toFixed(eut >= 1e7 ? 1 : 2).replace(/\\.0+$/, '');
                return val + "M EU/t";
              }
              if (eut >= 1e3) return Math.round(eut / 1e3) + "k EU/t";
              return Math.round(eut) + " EU/t";
            }

            function loadPresetFromData(p) {
              if (!p || !p.layout) return;
              lastChartTick = -1;
              if (window.innerWidth <= 800) {
                isFitMode = true;
              }
              const tierMap = { "EV": 0, "IV": 1, "LuV": 2, "ZPM": 3, "UV": 4, "UHV": 5 };
              const pipeTier = (p.tier && tierMap[p.tier] !== undefined) ? tierMap[p.tier] : 1;
              if (p.tier) {
                setVoltageTier(p.tier);
              }
              if (isWasmMode && wasmSim) {
                const rows = p.layout.trim().split(";");
                const h = rows.length;
                const w = rows[0].split(",").length;
                if (w > 0 && h > 0) {
                  wasmSim.initGrid(w, h, pipeTier);
                }
                if (wasmSim.loadLayout) {
                  wasmSim.loadLayout(p.layout, false);
                }
                if (p.turbine && p.turbine.length >= 3 && wasmSim.setTurbine) {
                  const mat = p.turbine[0].toUpperCase().replace(/-/g, '_');
                  const sz = p.turbine[1].toUpperCase();
                  const fit = p.turbine[2].toUpperCase();
                  wasmSim.setTurbine(mat, sz, fit, false);
                }
                currentState = wasmSim.getState();
                if (isFitMode) recalculateFitZoom();
                renderUI();
              }
            }

            window.__CEILING_PRESETS_MAP__ = {};
            function initCeilingPresets() {
              const optGroup = document.getElementById("ceiling-presets-optgroup");
              if (!optGroup) return;
              if (!window.__CEILING_PRESETS__ || !Array.isArray(window.__CEILING_PRESETS__) || window.__CEILING_PRESETS__.length === 0) {
                return;
              }
              optGroup.innerHTML = "";
              window.__CEILING_PRESETS__.forEach((p, idx) => {
                const key = "CEILING_" + (p.tier ? p.tier.toUpperCase() : idx);
                window.__CEILING_PRESETS_MAP__[key] = p;
                if (p.tier) {
                  window.__CEILING_PRESETS_MAP__["BEST_" + p.tier.toUpperCase()] = p;
                }
                const opt = document.createElement("option");
                opt.value = key;
                const pwr = formatEuCompact(p.peak_power_eut || p.steam_power_eut);
                const amps = p.peak_amps != null ? (p.peak_amps >= 100 ? Math.round(p.peak_amps) : p.peak_amps.toFixed(1)) + "A" : "";
                const cleanName = (p.name || "").replace(/\\s*\\([A-Z0-9]+\\)$/, '');
                opt.textContent = `${p.tier} Peak: ${cleanName} (${pwr}${amps ? ' · ' + amps : ''})`;
                if (p.tier === "IV") {
                  opt.selected = true;
                }
                optGroup.appendChild(opt);
              });
            }

            async function loadPreset(name) {
              lastChartTick = -1;
              if (window.innerWidth <= 800) {
                isFitMode = true;
              }
              if (window.__CEILING_PRESETS_MAP__ && window.__CEILING_PRESETS_MAP__[name]) {
                loadPresetFromData(window.__CEILING_PRESETS_MAP__[name]);
                return;
              }
              if (window.__CEILING_PRESETS_MAP__) {
                const tierMatch = name.match(/BEST_([A-Z]+)/);
                if (tierMatch) {
                  const tierNameMap = {
                    "ELECTRUM": "EV",
                    "PLATINUM": "IV",
                    "OSMIUM": "LuV",
                    "QUANTIUM": "ZPM",
                    "FLUXED": "UV",
                    "PLUTONIUM": "UHV"
                  };
                  const t = tierNameMap[tierMatch[1]];
                  if (t && window.__CEILING_PRESETS_MAP__["CEILING_" + t]) {
                    loadPresetFromData(window.__CEILING_PRESETS_MAP__["CEILING_" + t]);
                    return;
                  }
                }
              }
              if (PRESET_TO_VOLTAGE[name]) {
                setVoltageTier(PRESET_TO_VOLTAGE[name]);
              }
              if (isWasmMode && wasmSim) {
                wasmSim.loadPreset(name);
                currentState = wasmSim.getState();
                if (isFitMode) recalculateFitZoom();
                renderUI();
                return;
              }
              await fetch(`/api/load-preset?name=${encodeURIComponent(name)}`);
              await fetchState();
              if (isFitMode) recalculateFitZoom();
              schedulePoll();
            }

            async function setTile(x, y, type) {
              if (isWasmMode && wasmSim) {
                wasmSim.setTile(x, y, type);
                currentState = wasmSim.getState();
                renderUI();
                return;
              }
              await fetch(`/api/set-tile?x=${x}&y=${y}&type=${encodeURIComponent(type)}`);
              isFetching = false;
              await fetchState();
              schedulePoll();
            }

            async function changeTier(tier) {
              const tierNum = parseInt(tier);
              if (TIER_TO_VOLTAGE[tierNum]) {
                setVoltageTier(TIER_TO_VOLTAGE[tierNum]);
              }
              if (isWasmMode && wasmSim) {
                wasmSim.setPipeTier(tierNum);
                currentState = wasmSim.getState();
                renderUI();
                return;
              }
              await fetch(`/api/set-tier?tier=${tier}`);
              await fetchState();
              schedulePoll();
            }

            async function toggleTileControlRod(x, y, hasRod) {
              if (isWasmMode && wasmSim) {
                const tile = currentState && currentState.tiles.find(t => t.x === x && t.y === y);
                const curType = (tile && tile.controlRodType && tile.controlRodType !== "NONE") ? tile.controlRodType : "BORON";
                const curIns = (tile && tile.controlRodInsertion != null) ? tile.controlRodInsertion : 0;
                wasmSim.setControlRod(x, y, hasRod, curType, curIns);
                currentState = wasmSim.getState();
                renderUI();
                return;
              }
              await fetch(`/api/set-control-rod?x=${x}&y=${y}&hasRod=${hasRod}`);
              await fetchState();
            }

            async function changeTileControlRodMaterial(x, y, material) {
              if (isWasmMode && wasmSim) {
                const tile = currentState && currentState.tiles.find(t => t.x === x && t.y === y);
                const curIns = (tile && tile.controlRodInsertion != null) ? tile.controlRodInsertion : 0;
                wasmSim.setControlRod(x, y, true, material, curIns);
                currentState = wasmSim.getState();
                renderUI();
                return;
              }
              await fetch(`/api/set-control-rod?x=${x}&y=${y}&hasRod=true&material=${encodeURIComponent(material)}`);
              await fetchState();
            }

            async function updateTileControlRodInsertion(x, y, insertion) {
              if (isWasmMode && wasmSim) {
                const tile = currentState && currentState.tiles.find(t => t.x === x && t.y === y);
                const curType = (tile && tile.controlRodType) ? tile.controlRodType : "BORON";
                wasmSim.setControlRod(x, y, true, curType, insertion);
                currentState = wasmSim.getState();
                renderUI();
                return;
              }
              await fetch(`/api/set-control-rod?x=${x}&y=${y}&hasRod=true&insertion=${insertion}`);
              await fetchState();
            }

            async function quickScram() {
              if (isWasmMode && wasmSim) {
                wasmSim.scram();
                currentState = wasmSim.getState();
                renderUI();
                return;
              }
              await fetch("/api/scram");
              await fetchState();
            }

            async function setAllControlRodsInsertion(ins) {
              if (isWasmMode && wasmSim) {
                wasmSim.setAllControlRodsInsertion(ins);
                currentState = wasmSim.getState();
                renderUI();
                return;
              }
              await fetch(`/api/set-all-control-rods?insertion=${ins}`);
              await fetchState();
            }

            function renderProcessPipeline() {
              if (!currentState || !currentState.powerEstimate) return;
              const pe = currentState.powerEstimate;
              const tier = currentState.pipeTier !== undefined ? currentState.pipeTier : 1;
              const tierBadge = document.getElementById("proc-tier-badge");
              const tierDesc = document.getElementById("proc-tier-desc");

              const stagePv = document.getElementById("proc-stage-pv");
              const stageCoolant = document.getElementById("proc-stage-coolant");
              const stageSc = document.getElementById("proc-stage-sc");
              const stageHp = document.getElementById("proc-stage-hp");
              const stageReg = document.getElementById("proc-stage-reg");

              // Stage 0: Radiovoltaics
              document.getElementById("proc-pv-val").innerText = formatPowerAndAmps(pe.directPowerEUt, true);
              document.getElementById("proc-pv-sub").innerText = pe.directPowerEUt > 0
                ? `${Math.round(pe.directPowerEUt).toLocaleString()} EU/t direct power from radiovoltaic cells`
                : "No radiovoltaic cells generating direct power";

              // Tier Policies
              if (tier <= 0) {
                // EV
                tierBadge.innerText = "EV Pipeline";
                tierBadge.style.color = "#38bdf8";
                tierDesc.innerHTML = "<strong>EV Mode:</strong> Large Heat Exchanger (1L Hot Coolant → 400L Regular Steam) + Large Steam Turbines (LST, 0.5 EU/L) + Direct Radiovoltaics. <em>XLST and Superheated/Supercritical processing unavailable at EV.</em>";

                // Stage 1: LHE
                document.getElementById("proc-coolant-title").innerText = "🔄 Stage 1: Large Heat Exchanger (LHE)";
                const hasCoolant = (currentState.flowHotCoolant || 0) > 0;
                document.getElementById("proc-coolant-count").innerText = hasCoolant ? (pe.coolantMachineCount.toFixed(0) + "x LHE") : "0x LHE";
                document.getElementById("proc-coolant-detail").innerText = hasCoolant
                  ? `Input: ${Math.round(currentState.flowHotCoolant).toLocaleString()} L/t Hot Coolant → Output: ${Math.round(pe.coolantSteamProduced).toLocaleString()} L/t Regular Steam (1:400)`
                  : "Chamber produces no IC2 Hot Coolant";
                document.getElementById("proc-coolant-water").innerText = hasCoolant
                  ? `Distilled Water consumed: ${Math.round(pe.coolantWaterConsumed).toLocaleString()} L/t`
                  : "";

                // Stage 2: Locked
                stageSc.style.opacity = "0.45";
                document.getElementById("proc-sc-title").innerText = "🔒 Stage 2: XLST-SC (Supercritical)";
                document.getElementById("proc-sc-pwr").innerText = "Locked (ZPM+)";
                document.getElementById("proc-sc-detail").innerText = "Requires ZPM or higher. Supercritical steam cannot be turbine-processed at EV.";
                document.getElementById("proc-sc-cascade").innerText = "";

                // Stage 3: Locked
                stageHp.style.opacity = "0.45";
                document.getElementById("proc-hp-title").innerText = "🔒 Stage 3: XLST-HP (Superheated)";
                document.getElementById("proc-hp-pwr").innerText = "Locked (IV+)";
                document.getElementById("proc-hp-detail").innerText = "Requires IV or higher. Superheated steam cannot be turbine-processed at EV.";
                document.getElementById("proc-hp-cascade").innerText = "";

                // Stage 4: LST
                stageReg.style.opacity = "1.0";
                document.getElementById("proc-reg-title").innerText = "⚡ Stage 4: Large Steam Turbine (LST)";
                document.getElementById("proc-reg-pwr").innerText = formatPowerAndAmps(pe.lstPowerEUt, true);
                const regFlow = Math.round((currentState.flowRegularSteam || 0) + pe.coolantSteamProduced);
                document.getElementById("proc-reg-detail").innerText = `${regFlow.toLocaleString()} L/t steam · ${pe.lstTurbinesNeeded.toFixed(2)} LST units (opt ${Math.round(pe.optFlowPerTurbine).toLocaleString()} L/t)`;
                document.getElementById("proc-reg-eff").innerText = `Base 0.5 EU/L × ${(pe.efficiency * 100).toFixed(1)}% Eff (${pe.lstPowerEUt > 0 ? (pe.lstPowerEUt / Math.max(1, regFlow)).toFixed(3) : 0} EU/L)`;

              } else if (tier === 1) {
                // IV
                tierBadge.innerText = "IV Pipeline";
                tierBadge.style.color = "#a3e635";
                tierDesc.innerHTML = "<strong>IV Mode:</strong> Thermal Boiler (1L Hot Coolant → 200L SH Steam) + XLST-HP (1.0 EU/L, cascades 1:1) + XLST (0.5 EU/L). <em>EHE and XLST-SC unavailable at IV.</em>";

                // Stage 1: Thermal Boiler
                document.getElementById("proc-coolant-title").innerText = "🔄 Stage 1: Thermal Boiler";
                const hasCoolant = (currentState.flowHotCoolant || 0) > 0;
                document.getElementById("proc-coolant-count").innerText = hasCoolant ? (pe.coolantMachineCount.toFixed(0) + "x Thermal Boiler") : "0x Thermal Boiler";
                document.getElementById("proc-coolant-detail").innerText = hasCoolant
                  ? `Input: ${Math.round(currentState.flowHotCoolant).toLocaleString()} L/t Hot Coolant → Output: ${Math.round(pe.coolantSteamProduced).toLocaleString()} L/t SH Steam (1:200)`
                  : "Chamber produces no IC2 Hot Coolant";
                document.getElementById("proc-coolant-water").innerText = hasCoolant
                  ? `Water consumed: ${Math.round(pe.coolantWaterConsumed).toLocaleString()} L/t`
                  : "";

                // Stage 2: Locked
                stageSc.style.opacity = "0.45";
                document.getElementById("proc-sc-title").innerText = "🔒 Stage 2: XLST-SC (Supercritical)";
                document.getElementById("proc-sc-pwr").innerText = "Locked (ZPM+)";
                document.getElementById("proc-sc-detail").innerText = "Requires ZPM or higher. Supercritical steam cannot be turbine-processed at IV.";
                document.getElementById("proc-sc-cascade").innerText = "";

                // Stage 3: XLST-HP
                stageHp.style.opacity = "1.0";
                document.getElementById("proc-hp-title").innerText = "⚡ Stage 3: XLST-HP (Superheated Steam)";
                document.getElementById("proc-hp-pwr").innerText = formatPowerAndAmps(pe.xlstHpPowerEUt, true);
                const hpFlow = Math.round((currentState.flowSuperheatedSteam || 0) + (currentState.flowHeavyWaterSteam || 0) + pe.coolantSteamProduced);
                document.getElementById("proc-hp-detail").innerText = `${hpFlow.toLocaleString()} L/t SH steam · ${pe.xlstHpTurbinesNeeded.toFixed(2)} turbines (opt ${Math.round(pe.optFlowPerTurbine).toLocaleString()} L/t)`;
                document.getElementById("proc-hp-cascade").innerText = "Cascades 1:1 into Regular Steam for Stage 4";

                // Stage 4: XLST Regular
                stageReg.style.opacity = "1.0";
                document.getElementById("proc-reg-title").innerText = "⚡ Stage 4: XLST (Regular Steam)";
                document.getElementById("proc-reg-pwr").innerText = formatPowerAndAmps(pe.xlstPowerEUt, true);
                const regFlow = Math.round((currentState.flowRegularSteam || 0) + hpFlow);
                document.getElementById("proc-reg-detail").innerText = `${regFlow.toLocaleString()} L/t steam (including cascade) · ${pe.xlstTurbinesNeeded.toFixed(2)} turbines`;
                document.getElementById("proc-reg-eff").innerText = `Base 0.5 EU/L × ${(pe.efficiency * 100).toFixed(1)}% Eff (${pe.xlstPowerEUt > 0 ? (pe.xlstPowerEUt / Math.max(1, regFlow)).toFixed(3) : 0} EU/L)`;

              } else if (tier === 2) {
                // LuV
                tierBadge.innerText = "LuV Pipeline";
                tierBadge.style.color = "#f59e0b";
                tierDesc.innerHTML = "<strong>LuV Mode:</strong> Extreme Heat Exchanger (1L Hot Coolant → 200L SH Steam) + XLST-HP (1.0 EU/L, cascades 1:1) + XLST (0.5 EU/L). <em>XLST-SC unlocks at ZPM.</em>";

                // Stage 1: EHE
                document.getElementById("proc-coolant-title").innerText = "🔄 Stage 1: Extreme Heat Exchanger (EHE)";
                const hasCoolant = (currentState.flowHotCoolant || 0) > 0;
                document.getElementById("proc-coolant-count").innerText = hasCoolant ? (pe.coolantMachineCount.toFixed(0) + "x EHE") : "0x EHE";
                document.getElementById("proc-coolant-detail").innerText = hasCoolant
                  ? `Input: ${Math.round(currentState.flowHotCoolant).toLocaleString()} L/t Hot Coolant → Output: ${Math.round(pe.coolantSteamProduced).toLocaleString()} L/t SH Steam (1:200)`
                  : "Chamber produces no IC2 Hot Coolant";
                document.getElementById("proc-coolant-water").innerText = hasCoolant
                  ? `Distilled Water consumed: ${Math.round(pe.coolantWaterConsumed).toLocaleString()} L/t`
                  : "";

                // Stage 2: Locked
                stageSc.style.opacity = "0.45";
                document.getElementById("proc-sc-title").innerText = "🔒 Stage 2: XLST-SC (Supercritical)";
                document.getElementById("proc-sc-pwr").innerText = "Locked (ZPM+)";
                document.getElementById("proc-sc-detail").innerText = "Requires ZPM or higher for XLST-SC.";
                document.getElementById("proc-sc-cascade").innerText = "";

                // Stage 3: XLST-HP
                stageHp.style.opacity = "1.0";
                document.getElementById("proc-hp-title").innerText = "⚡ Stage 3: XLST-HP (Superheated Steam)";
                document.getElementById("proc-hp-pwr").innerText = formatPowerAndAmps(pe.xlstHpPowerEUt, true);
                const hpFlow = Math.round((currentState.flowSuperheatedSteam || 0) + (currentState.flowHeavyWaterSteam || 0) + pe.coolantSteamProduced);
                document.getElementById("proc-hp-detail").innerText = `${hpFlow.toLocaleString()} L/t SH steam · ${pe.xlstHpTurbinesNeeded.toFixed(2)} turbines (opt ${Math.round(pe.optFlowPerTurbine).toLocaleString()} L/t)`;
                document.getElementById("proc-hp-cascade").innerText = "Cascades 1:1 into Regular Steam for Stage 4";

                // Stage 4: XLST Regular
                stageReg.style.opacity = "1.0";
                document.getElementById("proc-reg-title").innerText = "⚡ Stage 4: XLST (Regular Steam)";
                document.getElementById("proc-reg-pwr").innerText = formatPowerAndAmps(pe.xlstPowerEUt, true);
                const regFlow = Math.round((currentState.flowRegularSteam || 0) + hpFlow);
                document.getElementById("proc-reg-detail").innerText = `${regFlow.toLocaleString()} L/t steam (including cascade) · ${pe.xlstTurbinesNeeded.toFixed(2)} turbines`;
                document.getElementById("proc-reg-eff").innerText = `Base 0.5 EU/L × ${(pe.efficiency * 100).toFixed(1)}% Eff`;

              } else {
                // ZPM+
                const tierName = (tier === 3) ? "ZPM" : (tier === 4) ? "UV" : "UHV";
                tierBadge.innerText = `${tierName} Full 3-Stage Cascade`;
                tierBadge.style.color = "#ec4899";
                tierDesc.innerHTML = "<strong>ZPM+ Mode:</strong> EHE (Supercritical if ≥8,000 L/s coolant, else SH) + Full 3-Stage Cascade: XLST-SC (1.0 EU/L) → XLST-HP (1.0 EU/L) → XLST (0.5 EU/L).";

                // Stage 1: EHE
                const isSC = pe.coolantMachineMode && pe.coolantMachineMode.includes("Supercritical");
                document.getElementById("proc-coolant-title").innerText = `🔄 Stage 1: Extreme Heat Exchanger (EHE) [${isSC ? "Supercritical" : "Superheated"}]`;
                const hasCoolant = (currentState.flowHotCoolant || 0) > 0;
                document.getElementById("proc-coolant-count").innerText = hasCoolant ? (pe.coolantMachineCount.toFixed(0) + "x EHE") : "0x EHE";
                document.getElementById("proc-coolant-detail").innerText = hasCoolant
                  ? `Input: ${Math.round(currentState.flowHotCoolant).toLocaleString()} L/t (${Math.round(currentState.flowHotCoolant * 20).toLocaleString()} L/s) → Output: ${Math.round(pe.coolantSteamProduced).toLocaleString()} L/t ${isSC ? "SC" : "SH"} Steam`
                  : "Chamber produces no IC2 Hot Coolant";
                document.getElementById("proc-coolant-water").innerText = hasCoolant
                  ? `Distilled Water consumed: ${Math.round(pe.coolantWaterConsumed).toLocaleString()} L/t`
                  : "";

                // Stage 2: XLST-SC
                stageSc.style.opacity = "1.0";
                document.getElementById("proc-sc-title").innerText = "⚡ Stage 2: XLST-SC (Supercritical Steam)";
                document.getElementById("proc-sc-pwr").innerText = formatPowerAndAmps(pe.xlstScPowerEUt, true);
                const scFlow = Math.round((currentState.flowSupercriticalSteam || 0) + (isSC ? pe.coolantSteamProduced : 0));
                document.getElementById("proc-sc-detail").innerText = `${scFlow.toLocaleString()} L/t SC steam · ${pe.xlstScTurbinesNeeded.toFixed(2)} turbines (opt ${Math.round(pe.optFlowPerTurbine).toLocaleString()} L/t)`;
                document.getElementById("proc-sc-cascade").innerText = "Cascades 1:1 into Superheated Steam for Stage 3";

                // Stage 3: XLST-HP
                stageHp.style.opacity = "1.0";
                document.getElementById("proc-hp-title").innerText = "⚡ Stage 3: XLST-HP (Superheated Steam)";
                document.getElementById("proc-hp-pwr").innerText = formatPowerAndAmps(pe.xlstHpPowerEUt, true);
                const hpDirect = Math.round((currentState.flowSuperheatedSteam || 0) + (currentState.flowHeavyWaterSteam || 0) + (!isSC ? pe.coolantSteamProduced : 0));
                const hpTotal = hpDirect + scFlow;
                document.getElementById("proc-hp-detail").innerText = `${hpTotal.toLocaleString()} L/t SH steam (${scFlow.toLocaleString()} from SC cascade) · ${pe.xlstHpTurbinesNeeded.toFixed(2)} turbines`;
                document.getElementById("proc-hp-cascade").innerText = "Cascades 1:1 into Regular Steam for Stage 4";

                // Stage 4: XLST Regular
                stageReg.style.opacity = "1.0";
                document.getElementById("proc-reg-title").innerText = "⚡ Stage 4: XLST (Regular Steam)";
                document.getElementById("proc-reg-pwr").innerText = formatPowerAndAmps(pe.xlstPowerEUt, true);
                const regDirect = Math.round(currentState.flowRegularSteam || 0);
                const regTotal = regDirect + hpTotal;
                document.getElementById("proc-reg-detail").innerText = `${regTotal.toLocaleString()} L/t steam (${hpTotal.toLocaleString()} from HP cascade) · ${pe.xlstTurbinesNeeded.toFixed(2)} turbines`;
                document.getElementById("proc-reg-eff").innerText = `Base 0.5 EU/L × ${(pe.efficiency * 100).toFixed(1)}% Eff`;
              }

              // Net Generation
              document.getElementById("proc-net-pwr").innerText = formatPowerAndAmps(pe.totalPowerEUt, false);
            }

            function setByproductUnit(unit) {
              byproductUnit = unit;
              const minBtn = document.getElementById("btn-byproduct-min");
              const hourBtn = document.getElementById("btn-byproduct-hour");
              if (minBtn) minBtn.classList.toggle("active", unit === "min");
              if (hourBtn) hourBtn.classList.toggle("active", unit === "hour");
              renderByproducts();
            }

            function toggleStopOnIncidents(checked) {
              const lbl = document.getElementById("lbl-stop-incidents");
              if (lbl) {
                lbl.innerText = checked ? "(Enabled)" : "(Off)";
                lbl.style.color = checked ? "var(--warning)" : "var(--text-muted)";
                lbl.style.fontWeight = checked ? "600" : "normal";
              }
              if (isWasmMode && wasmSim) {
                wasmSim.setStopOnIncidents(checked);
                currentState = wasmSim.getState();
                renderUI();
                return;
              }
              fetch(`/api/set-params?stopOnIncidents=${checked}`);
            }

            function toggleAutoSupplyFuel(checked) {
              const lblSupply = document.getElementById("lbl-auto-supply-fuel");
              if (lblSupply) {
                lblSupply.innerText = checked ? "(Enabled)" : "(Off)";
                lblSupply.style.color = checked ? "#34d399" : "var(--text-muted)";
                lblSupply.style.fontWeight = checked ? "600" : "normal";
              }
              const lblRefuel = document.getElementById("lbl-auto-refuel");
              if (lblRefuel) {
                lblRefuel.innerText = checked ? "Enabled" : "Disabled";
                lblRefuel.style.color = checked ? "#34d399" : "var(--text-muted)";
              }
              const chkSupply = document.getElementById("chk-auto-supply-fuel");
              const chkRefuel = document.getElementById("chk-auto-refuel");
              if (chkSupply && chkSupply.checked !== checked) chkSupply.checked = checked;
              if (chkRefuel && chkRefuel.checked !== checked) chkRefuel.checked = checked;

              if (isWasmMode && wasmSim) {
                wasmSim.setAutoSupplyFuel(checked);
                currentState = wasmSim.getState();
                renderUI();
                return;
              }
              fetch(`/api/set-auto-refuel?enabled=${checked}`);
            }

            function toggleAutoReplaceFuel(checked) {
              toggleAutoSupplyFuel(checked);
            }

            function renderByproducts() {
              if (!currentState) return;
              const bp = currentState.byproducts;
              if (!bp) return;

              const isHour = (byproductUnit === "hour");
              const unitLabel = isHour ? "/h" : "/min";
              const fluidUnitLabel = isHour ? "L/h" : "L/min";

              const autoChk = document.getElementById("chk-auto-refuel");
              const autoLbl = document.getElementById("lbl-auto-refuel");
              if (autoChk && bp.autoReplaceFuel !== undefined && autoChk !== document.activeElement) {
                autoChk.checked = bp.autoReplaceFuel;
                if (autoLbl) {
                  autoLbl.innerText = bp.autoReplaceFuel ? "Enabled" : "Disabled";
                  autoLbl.style.color = bp.autoReplaceFuel ? "#34d399" : "var(--text-muted)";
                }
              }

              const rateBadge = document.getElementById("byproduct-rate-badge");
              if (rateBadge) {
                const totalSolidRate = isHour ? bp.totalSolidItemsPerHour : bp.totalSolidItemsPerMin;
                rateBadge.innerText = `${(totalSolidRate || 0).toFixed(isHour ? 1 : 2)} items ${unitLabel}`;
              }

              const solidTotalEl = document.getElementById("byproduct-solid-total");
              const solidListEl = document.getElementById("byproduct-solid-list");
              if (solidTotalEl && solidListEl) {
                const totalSolidRate = isHour ? bp.totalSolidItemsPerHour : bp.totalSolidItemsPerMin;
                solidTotalEl.innerText = `${(totalSolidRate || 0).toFixed(isHour ? 1 : 2)} ${unitLabel}`;

                if (!bp.solidRods || bp.solidRods.length === 0) {
                  solidListEl.innerHTML = `<div style="font-style:italic; color:#64748b; padding:2px 0;">No active fuel rods burning.</div>`;
                } else {
                  let html = "";
                  bp.solidRods.forEach(s => {
                    const rate = isHour ? s.itemsPerHour : s.itemsPerMin;
                    const rateStr = `${rate.toFixed(isHour ? 1 : 2)} ${unitLabel}`;
                    const lifespanStr = (s.avgLifespanMin && s.avgLifespanMin !== "Infinity" && !isNaN(s.avgLifespanMin))
                      ? ` · ~${parseFloat(s.avgLifespanMin).toFixed(1)}m lifespan`
                      : "";
                    html += `
                      <div style="display:flex; justify-content:space-between; align-items:center; background:#0f172a; border-radius:4px; padding:4px 6px; font-size:0.72rem;">
                        <div style="display:flex; align-items:center; gap:6px;">
                          <span class="badge" style="font-size:0.65rem; padding:1px 4px; color:#34d399; border-color:#059669;">${s.depletedCode}</span>
                          <span style="color:#e2e8f0; font-weight:500;">${s.depletedName}</span>
                          <span style="color:var(--text-muted); font-size:0.68rem;">(${s.activeRods} rod${s.activeRods === 1 ? "" : "s"}${lifespanStr})</span>
                        </div>
                        <div style="text-align:right;">
                          <span style="color:#4ade80; font-weight:600;">${rateStr}</span>
                          ${s.totalProduced > 0 ? `<div style="font-size:0.65rem; color:var(--text-muted);">${s.totalProduced.toLocaleString()} produced</div>` : ""}
                        </div>
                      </div>
                    `;
                  });
                  solidListEl.innerHTML = html;
                }
              }

              const liquidSec = document.getElementById("byproduct-liquid-section");
              const liquidTotalEl = document.getElementById("byproduct-liquid-total");
              const liquidListEl = document.getElementById("byproduct-liquid-list");
              if (liquidSec && liquidTotalEl && liquidListEl) {
                if (!bp.liquidFuels || bp.liquidFuels.length === 0) {
                  liquidSec.style.display = "none";
                } else {
                  liquidSec.style.display = "block";
                  const totalLiqRate = isHour ? bp.totalLiquidLitersPerHour : bp.totalLiquidLitersPerMin;
                  liquidTotalEl.innerText = `${Math.round(totalLiqRate || 0).toLocaleString()} ${fluidUnitLabel}`;

                  let html = "";
                  bp.liquidFuels.forEach(l => {
                    const rate = isHour ? l.litersPerHour : l.litersPerMin;
                    const rateStr = `${Math.round(rate).toLocaleString()} ${fluidUnitLabel}`;
                    html += `
                      <div style="display:flex; justify-content:space-between; align-items:center; background:#0f172a; border-radius:4px; padding:4px 6px; font-size:0.72rem;">
                        <div style="display:flex; align-items:center; gap:6px;">
                          <span class="badge" style="font-size:0.65rem; padding:1px 4px; color:#38bdf8; border-color:#0284c7;">FLUID</span>
                          <span style="color:#e2e8f0; font-weight:500;">${l.displayName}</span>
                          <span style="color:var(--text-muted); font-size:0.68rem;">(${l.activeHatches} hatch${l.activeHatches === 1 ? "" : "es"})</span>
                        </div>
                        <div style="text-align:right;">
                          <span style="color:#38bdf8; font-weight:600;">${rateStr}</span>
                          ${l.totalLiters > 0 ? `<div style="font-size:0.65rem; color:var(--text-muted);">${l.totalLiters.toLocaleString()} L total</div>` : ""}
                        </div>
                      </div>
                    `;
                  });
                  liquidListEl.innerHTML = html;
                }
              }

              const isoSec = document.getElementById("byproduct-isotope-section");
              const isoTotalEl = document.getElementById("byproduct-isotope-total");
              const isoListEl = document.getElementById("byproduct-isotope-list");
              if (isoSec && isoTotalEl && isoListEl) {
                const hasIsotopes = bp.isotopes && bp.isotopes.some(iso => iso.totalLiters > 0 || iso.litersPerMin > 0);
                if (!hasIsotopes) {
                  isoSec.style.display = "none";
                } else {
                  isoSec.style.display = "block";
                  let totalIsoRate = 0;
                  let html = "";
                  bp.isotopes.forEach(iso => {
                    const rate = isHour ? iso.litersPerHour : iso.litersPerMin;
                    totalIsoRate += rate;
                    const rateStr = `${rate.toFixed(isHour ? 1 : 2)} ${fluidUnitLabel}`;
                    const color = (iso.code === "T") ? "#d946ef" : "#38bdf8";
                    html += `
                      <div style="display:flex; justify-content:space-between; align-items:center; background:#0f172a; border-radius:4px; padding:4px 6px; font-size:0.72rem;">
                        <div style="display:flex; align-items:center; gap:6px;">
                          <span class="badge" style="font-size:0.65rem; padding:1px 4px; color:${color}; border-color:${color};">${iso.code}</span>
                          <span style="color:#e2e8f0; font-weight:500;">${iso.name}</span>
                        </div>
                        <div style="text-align:right;">
                          <span style="color:${color}; font-weight:600;">${rateStr}</span>
                          ${iso.totalLiters > 0 ? `<div style="font-size:0.65rem; color:var(--text-muted);">${iso.totalLiters.toLocaleString()} L total</div>` : ""}
                        </div>
                      </div>
                    `;
                  });
                  isoTotalEl.innerText = `${totalIsoRate.toFixed(isHour ? 1 : 2)} ${fluidUnitLabel}`;
                  isoListEl.innerHTML = html;
                }
              }

              const rawSec = document.getElementById("byproduct-raw-section");
              const rawListEl = document.getElementById("byproduct-raw-list");
              if (rawSec && rawListEl) {
                if (!bp.rawMaterials || bp.rawMaterials.length === 0) {
                  rawListEl.innerHTML = `<div style="font-style:italic; color:#64748b; padding:2px 0;">No active raw material burn forecast.</div>`;
                } else {
                  let html = "";
                  bp.rawMaterials.forEach(r => {
                    const consumed = isHour ? r.consumedPerHour : r.consumedPerMin;
                    const produced = isHour ? r.producedPerHour : r.producedPerMin;
                    const net = isHour ? r.netPerHour : r.netPerMin;
                    const unitStr = `${r.unit}${unitLabel}`;
                    const isBreeding = net > 0.001;
                    const isDeficit = net < -0.001;
                    const netSign = isBreeding ? "+" : "";
                    const netColor = isBreeding ? "#4ade80" : (isDeficit ? "#f87171" : "#94a3b8");
                    const badgeStyle = isBreeding ? "color:#34d399; border-color:#059669;" : (isDeficit ? "color:#f87171; border-color:#ef4444;" : "color:#94a3b8; border-color:#475569;");
                    const tagText = isBreeding ? "BREEDING" : (isDeficit ? "DEFICIT" : "NEUTRAL");

                    html += `
                      <div style="display:flex; justify-content:space-between; align-items:center; background:#0f172a; border-radius:4px; padding:4px 6px; font-size:0.72rem;">
                        <div style="display:flex; align-items:center; gap:6px;">
                          <span class="badge" style="font-size:0.65rem; padding:1px 4px; ${badgeStyle}">${r.code}</span>
                          <span style="color:#e2e8f0; font-weight:500;">${r.name}</span>
                          <span style="color:var(--text-muted); font-size:0.68rem;">(Req: ${consumed.toFixed(isHour ? 1 : 2)}, Prod: ${produced.toFixed(isHour ? 1 : 2)})</span>
                        </div>
                        <div style="text-align:right;">
                          <span style="color:${netColor}; font-weight:700;">${netSign}${net.toFixed(isHour ? 1 : 2)} ${unitStr}</span>
                          <span class="badge" style="font-size:0.62rem; padding:0 3px; margin-left:4px; ${badgeStyle}">${tagText}</span>
                        </div>
                      </div>
                    `;
                  });
                  rawListEl.innerHTML = html;
                }
              }
            }

            window.addEventListener("resize", () => {
              if (isFitMode) {
                recalculateFitZoom();
              }
            });

            window.addEventListener("keydown", (e) => {
              if (e.target && (e.target.tagName === "INPUT" || e.target.tagName === "SELECT")) return;
              if (e.key === "Escape" || e.key === "i" || e.key === "I") {
                setInteractionMode("INSPECT");
              } else if (e.key === "p" || e.key === "P") {
                setInteractionMode("PAINT");
              } else if (e.key === "+" || e.key === "=") {
                zoomGrid(1);
              } else if (e.key === "-" || e.key === "_") {
                zoomGrid(-1);
              } else if (e.key === "0") {
                resetZoom();
              } else if (e.key === "f" || e.key === "F") {
                fitGridToScreen();
              } else if (e.key === "1") {
                setGridDisplayMode("COMPONENTS");
              } else if (e.key === "2") {
                setGridDisplayMode("TEMP");
              } else if (e.key === "3") {
                setGridDisplayMode("TOTAL_FLUX");
              } else if (e.key === "4") {
                setGridDisplayMode("ABSORPTION");
              } else if (e.key === "5") {
                setGridDisplayMode("CONTROL_RODS");
              } else if (e.key === "s" || e.key === "S") {
                quickScram();
              } else if (e.key === "m" || e.key === "M") {
                const modes = ["COMPONENTS", "TEMP", "TOTAL_FLUX", "ABSORPTION", "CONTROL_RODS"];
                const nextIdx = (modes.indexOf(gridDisplayMode) + 1) % modes.length;
                setGridDisplayMode(modes[nextIdx]);
              }
            });

            initTurbineSelects();
            initPalette();
            setInteractionMode("INSPECT");

            const urlParams = new URLSearchParams(window.location.search);
            const overlayParam = urlParams.get("overlay") || urlParams.get("mode");
            if (overlayParam) {
              setGridDisplayMode(overlayParam.toUpperCase());
            }
            const selX = urlParams.get("x");
            const selY = urlParams.get("y");
            if (selX !== null && selY !== null) {
              selectedTilePos = { x: parseInt(selX), y: parseInt(selY) };
            }

            /*__CEILING_PRESETS__*/
            initCeilingPresets();

            /*__INITIAL_STATE__*/
            if (window.__INITIAL_STATE__) {
              currentState = window.__INITIAL_STATE__;
              renderUI();
            }

            if (window.innerWidth <= 800) {
              isFitMode = true;
            }

            initEngine().then((wasmLoaded) => {
              loadColorMaps();
              if (isFitMode) recalculateFitZoom();
            });
            </script>
            </body>
            </html>
            """;
    }
}

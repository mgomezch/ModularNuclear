package com.gtnewhorizons.modularnuclear.common.nuclear.standalone;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Embedded standalone web server providing an interactive browser UI and REST API
 * for visual nuclear reactor design and real-time simulation.
 */
public class NuclearSimulationWebServer {

    private static StandaloneNuclearGrid grid;
    private static volatile boolean isRunning = false;
    private static ScheduledExecutorService ticker;
    private static int simDelayMs = 100;

    public static void main(String[] args) {
        if (args.length >= 2 && "--export-html".equals(args[0])) {
            try {
                java.io.File file = new java.io.File(args[1]);
                file.getParentFile().mkdirs();
                NuclearSimulationEngine.setGlobalThermalFissionMultiplier(0.348);
                NuclearSimulationEngine.fissionHeatPerNeutron = 77.2;
                grid = new StandaloneNuclearGrid(9, 9, NuclearSimulationEngine.PIPE_TIER_PLATINUM);
                grid.loadPreset("BEST_PLATINUM_9X9");
                String html = getIndexHtml()
                    .replace("/*__INITIAL_STATE__*/", "window.__INITIAL_STATE__ = " + getStateJson() + ";");
                java.nio.file.Files.write(file.toPath(), html.getBytes(StandardCharsets.UTF_8));
                System.out.println("Exported static simulator HTML to " + file.getAbsolutePath());
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
            return;
        }
        int port = 8085;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (Exception ignored) {}
        }
        startServer(port);
    }

    public static void startServer(int port) {
        try {
            NuclearSimulationEngine.setGlobalThermalFissionMultiplier(0.348);
            NuclearSimulationEngine.fissionHeatPerNeutron = 77.2;
            grid = new StandaloneNuclearGrid(9, 9, NuclearSimulationEngine.PIPE_TIER_PLATINUM);
            grid.loadPreset("BEST_PLATINUM_9X9");

            try {
                java.io.File distDir = new java.io.File("build/nuclear-sim-dist");
                if (distDir.exists()) {
                    java.nio.file.Files.write(
                        new java.io.File(distDir, "index.html").toPath(),
                        getIndexHtml().getBytes(StandardCharsets.UTF_8)
                    );
                }
            } catch (Exception ignored) {}

            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

            server.createContext("/", new IndexHandler());
            server.createContext("/api/icon", new IconHandler());
            server.createContext("/icons", new IconHandler());
            server.createContext("/api/state", new StateHandler());
            server.createContext("/api/step", new StepHandler());
            server.createContext("/api/reset", new ResetHandler());
            server.createContext("/api/play", new PlayHandler());
            server.createContext("/api/load-preset", new PresetHandler());
            server.createContext("/api/set-tile", new SetTileHandler());
            server.createContext("/api/set-tier", new SetTierHandler());
            server.createContext("/api/set-turbine", new SetTurbineHandler());
            server.createContext("/api/set-params", new SetParamsHandler());
            server.createContext("/api/colormaps", new ColormapsHandler());
            server.createContext("/nuclear-sim.wasm", new WasmResourceHandler("nuclear-sim.wasm", "application/wasm"));
            server.createContext("/nuclear-sim.wasm-runtime.js", new WasmResourceHandler("nuclear-sim.wasm-runtime.js", "application/javascript"));
            server.createContext("/nuclear-sim-bridge.js", new WasmResourceHandler("nuclear-sim-bridge.js", "application/javascript"));
            server.createContext("/nuclear-sim.js", new WasmResourceHandler("nuclear-sim.js", "application/javascript"));

            server.setExecutor(Executors.newCachedThreadPool());
            server.start();

            // Background ticker loop
            ticker = Executors.newSingleThreadScheduledExecutor();
            ticker.scheduleAtFixedRate(() -> {
                if (isRunning && grid != null && !grid.isExploded()) {
                    grid.step();
                }
            }, 0, 50, TimeUnit.MILLISECONDS);

            System.out.println("\u001B[1;32m============================================================");
            System.out.println("  Modular Nuclear Simulator Web Server ACTIVE");
            System.out.println("  Open in browser: http://localhost:" + port);
            System.out.println("============================================================\u001B[0m");

        } catch (IOException e) {
            System.err.println("Failed to start Web Server on port " + port + ": " + e.getMessage());
        }
    }

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders()
            .set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders()
            .set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                map.put(urlDecode(entry[0]), urlDecode(entry[1]));
            } else if (entry.length == 1) {
                map.put(urlDecode(entry[0]), "");
            }
        }
        return map;
    }

    static class StateHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            sendJsonResponse(exchange, 200, getStateJson());
        }
    }

    public static String getStateJson() {
        return NuclearSimWasmBridge.buildStateJson(grid, isRunning);
    }

    static class StepHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseQueryParams(
                exchange.getRequestURI()
                    .getQuery());
            int count = 1;
            if (params.containsKey("count")) {
                try {
                    count = Integer.parseInt(params.get("count"));
                } catch (NumberFormatException ignored) {}
            }
            for (int i = 0; i < count && !grid.isExploded(); i++) {
                grid.step();
            }
            sendJsonResponse(exchange, 200, "{\"success\":true,\"tick\":" + grid.getCurrentTick() + "}");
        }
    }

    static class PlayHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseQueryParams(
                exchange.getRequestURI()
                    .getQuery());
            if (params.containsKey("running")) {
                isRunning = Boolean.parseBoolean(params.get("running"));
            } else {
                isRunning = !isRunning;
            }
            sendJsonResponse(exchange, 200, "{\"isRunning\":" + isRunning + "}");
        }
    }

    static class ResetHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            isRunning = false;
            grid.resetMetrics();
            sendJsonResponse(exchange, 200, "{\"success\":true}");
        }
    }

    static class PresetHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseQueryParams(
                exchange.getRequestURI()
                    .getQuery());
            String name = params.getOrDefault("name", "BEST_PLATINUM_9X9");
            isRunning = false;
            grid.loadPreset(name);
            sendJsonResponse(exchange, 200, "{\"success\":true,\"preset\":\"" + name + "\"}");
        }
    }

    static class SetTileHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseQueryParams(
                exchange.getRequestURI()
                    .getQuery());
            try {
                int x = Integer.parseInt(params.get("x"));
                int y = Integer.parseInt(params.get("y"));
                String typeStr = params.get("type");
                SimTile.TileType type = SimTile.TileType.fromCode(typeStr);
                grid.setTile(x, y, type);
                sendJsonResponse(exchange, 200, "{\"success\":true}");
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    static class SetTierHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseQueryParams(
                exchange.getRequestURI()
                    .getQuery());
            try {
                int tier = Integer.parseInt(params.get("tier"));
                grid.setPipeTier(tier);
                int tierCap = switch (tier) {
                    case NuclearSimulationEngine.PIPE_TIER_ELECTRUM -> 8000;
                    case NuclearSimulationEngine.PIPE_TIER_PLATINUM -> 16000;
                    case NuclearSimulationEngine.PIPE_TIER_OSMIUM -> 32000;
                    case NuclearSimulationEngine.PIPE_TIER_QUANTIUM -> 64000;
                    case NuclearSimulationEngine.PIPE_TIER_FLUXED_ELECTRUM -> 128000;
                    case NuclearSimulationEngine.PIPE_TIER_BLACK_PLUTONIUM -> 256000;
                    default -> 8000;
                };
                NuclearSimulationEngine.hatchCoolantCapacity = tierCap;
                grid.updateHatchCapacities(tierCap);
                sendJsonResponse(
                    exchange,
                    200,
                    "{\"success\":true,\"tier\":" + tier + ",\"hatchCapacity\":" + tierCap + "}");
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    static class SetTurbineHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseQueryParams(
                exchange.getRequestURI()
                    .getQuery());
            try {
                if (params.containsKey("material")) {
                    grid.setTurbineMaterial(TurbineCalculator.TurbineMaterial.fromString(params.get("material")));
                }
                if (params.containsKey("size")) {
                    grid.setTurbineSize(TurbineCalculator.TurbineSize.fromString(params.get("size")));
                }
                if (params.containsKey("fitting")) {
                    grid.setTurbineFitting(TurbineCalculator.FittingMode.fromString(params.get("fitting")));
                }
                sendJsonResponse(exchange, 200, "{\"success\":true}");
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    static class SetParamsHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseQueryParams(
                exchange.getRequestURI()
                    .getQuery());
            try {
                if ("true".equalsIgnoreCase(params.get("reset"))) {
                    NuclearSimulationEngine.resetDefaultParameters();
                    if (grid != null) {
                        grid.updateHatchCapacities(NuclearSimulationEngine.hatchCoolantCapacity);
                    }
                    sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"Defaults restored\"}");
                    return;
                }

                if (params.containsKey("euPerDegree")) {
                    NuclearSimulationEngine.setEuPerDegree(Double.parseDouble(params.get("euPerDegree")));
                }
                if (params.containsKey("hatchCapacity")) {
                    int cap = Integer.parseInt(params.get("hatchCapacity"));
                    NuclearSimulationEngine.hatchCoolantCapacity = Math.max(100, cap);
                    if (grid != null) {
                        grid.updateHatchCapacities(NuclearSimulationEngine.hatchCoolantCapacity);
                    }
                }
                if (params.containsKey("turnoverCurve")) {
                    NuclearSimulationEngine.turnoverCurve = NuclearSimulationEngine.TurnoverCurve
                        .fromString(params.get("turnoverCurve"));
                }
                if (params.containsKey("turnoverDeltaTMax")) {
                    NuclearSimulationEngine.turnoverDeltaTMax = Double.parseDouble(params.get("turnoverDeltaTMax"));
                }
                if (params.containsKey("turnoverExponent")) {
                    NuclearSimulationEngine.turnoverExponent = Double.parseDouble(params.get("turnoverExponent"));
                }
                if (params.containsKey("coolantFeedRate")) {
                    NuclearSimulationEngine.coolantFeedRate = Integer.parseInt(params.get("coolantFeedRate"));
                }
                if (params.containsKey("coolingHeat")) {
                    NuclearSimulationEngine.coolingHeatPerLiter = Double.parseDouble(params.get("coolingHeat"));
                }
                if (params.containsKey("tempLow")) {
                    NuclearSimulationEngine.tempThresholdLow = Double.parseDouble(params.get("tempLow"));
                }
                if (params.containsKey("tempHigh")) {
                    NuclearSimulationEngine.tempThresholdHigh = Double.parseDouble(params.get("tempHigh"));
                }
                if (params.containsKey("reactivityPow")) {
                    NuclearSimulationEngine.reactivityPower = Double.parseDouble(params.get("reactivityPow"));
                }
                if (params.containsKey("fissionMult") || params.containsKey("globalThermalFissionMultiplier")) {
                    String val = params.containsKey("globalThermalFissionMultiplier")
                        ? params.get("globalThermalFissionMultiplier")
                        : params.get("fissionMult");
                    NuclearSimulationEngine.setGlobalThermalFissionMultiplier(Double.parseDouble(val));
                }
                if (params.containsKey("fissionHeat") || params.containsKey("fissionHeatPerNeutron")) {
                    String val = params.containsKey("fissionHeatPerNeutron") ? params.get("fissionHeatPerNeutron")
                        : params.get("fissionHeat");
                    NuclearSimulationEngine.fissionHeatPerNeutron = Double.parseDouble(val);
                }
                if (params.containsKey("hpBoil")) {
                    NuclearSimulationEngine.hpWaterBoilingPoint = Double.parseDouble(params.get("hpBoil"));
                }
                if (params.containsKey("ambientTemp")) {
                    NuclearSimulationEngine.setAmbientTemperature(Double.parseDouble(params.get("ambientTemp")));
                }
                if (params.containsKey("ic2CoolantHeat")) {
                    NuclearSimulationEngine.ic2CoolantHeatPerLiter = Double.parseDouble(params.get("ic2CoolantHeat"));
                }

                sendJsonResponse(exchange, 200, "{\"success\":true}");
            } catch (Exception e) {
                sendJsonResponse(exchange, 400, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    static class ColormapsHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"temperature\":[");
            for (int i = 0; i
                < com.gtnewhorizons.modularnuclear.common.nuclear.NuclearColorMaps.TEMPERATURE_COLORS.length; i++) {
                if (i > 0) sb.append(",");
                int c = com.gtnewhorizons.modularnuclear.common.nuclear.NuclearColorMaps.TEMPERATURE_COLORS[i];
                int a = (c >> 24) & 0xFF;
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;
                double alpha = Math.round((a / 255.0) * 100.0) / 100.0;
                sb.append("\"rgba(")
                    .append(r)
                    .append(",")
                    .append(g)
                    .append(",")
                    .append(b)
                    .append(",")
                    .append(alpha)
                    .append(")\"");
            }
            sb.append("],\"neutron\":[");
            for (int i = 0; i
                < com.gtnewhorizons.modularnuclear.common.nuclear.NuclearColorMaps.NEUTRON_COLORS.length; i++) {
                if (i > 0) sb.append(",");
                int c = com.gtnewhorizons.modularnuclear.common.nuclear.NuclearColorMaps.NEUTRON_COLORS[i];
                int a = (c >> 24) & 0xFF;
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;
                double alpha = Math.round((a / 255.0) * 100.0) / 100.0;
                sb.append("\"rgba(")
                    .append(r)
                    .append(",")
                    .append(g)
                    .append(",")
                    .append(b)
                    .append(",")
                    .append(alpha)
                    .append(")\"");
            }
            sb.append("]}");
            sendJsonResponse(exchange, 200, sb.toString());
        }
    }

    static class IconHandler implements HttpHandler {

        private static final Map<String, byte[]> ICON_CACHE = new HashMap<>();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI()
                .getPath();
            String name = path.substring(path.lastIndexOf('/') + 1);
            if (name.endsWith(".png")) {
                name = name.substring(0, name.length() - 4);
            }

            byte[] bytes = getIconBytes(name);
            if (bytes == null || bytes.length == 0) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            exchange.getResponseHeaders()
                .set("Content-Type", "image/png");
            exchange.getResponseHeaders()
                .set("Cache-Control", "public, max-age=86400");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private static synchronized byte[] getIconBytes(String name) {
            if (name == null || name.isEmpty()) return null;
            if (ICON_CACHE.containsKey(name)) {
                return ICON_CACHE.get(name);
            }
            byte[] data = loadRawIcon(name);
            if (data == null && !name.equals(name.toUpperCase())) {
                data = loadRawIcon(name.toUpperCase());
            }
            if (data == null) {
                SimTile.TileType mapped = SimTile.TileType.fromCode(name);
                if (mapped != SimTile.TileType.EMPTY && !mapped.name()
                    .equalsIgnoreCase(name)) {
                    data = getIconBytes(mapped.name());
                }
            }
            if (data != null) {
                ICON_CACHE.put(name, data);
            }
            return data;
        }

        private static byte[] loadRawIcon(String iconName) {
            String resPath = "/assets/modularnuclear/textures/sim/icons/" + iconName + ".png";
            try (java.io.InputStream in = NuclearSimulationWebServer.class.getResourceAsStream(resPath)) {
                if (in != null) {
                    return readAllStream(in);
                }
            } catch (Exception ignored) {}

            java.io.File[] searchDirs = { new java.io.File("mods/ModularNuclear/src/main/resources" + resPath),
                new java.io.File("build/resources/main" + resPath), new java.io.File("src/main/resources" + resPath),
                new java.io.File("/home/mgomezch/stuff/dev/nh-dev/mods/ModularNuclear/src/main/resources" + resPath) };
            for (java.io.File f : searchDirs) {
                if (f.exists()) {
                    try {
                        return java.nio.file.Files.readAllBytes(f.toPath());
                    } catch (Exception ignored) {}
                }
            }
            return null;
        }

        static byte[] readAllStream(java.io.InputStream in) throws IOException {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            int n;
            while ((n = in.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return baos.toByteArray();
        }
    }

    static class WasmResourceHandler implements HttpHandler {

        private final String resourceName;
        private final String contentType;

        public WasmResourceHandler(String resourceName, String contentType) {
            this.resourceName = resourceName;
            this.contentType = contentType;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            byte[] data = loadResourceBytes(resourceName);
            if (data == null || data.length == 0) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=3600");
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
        }

        private static byte[] loadResourceBytes(String name) {
            String resPath = "/nuclear-sim/" + name;
            try (java.io.InputStream in = NuclearSimulationWebServer.class.getResourceAsStream(resPath)) {
                if (in != null) return IconHandler.readAllStream(in);
            } catch (Exception ignored) {}

            java.io.File[] searchDirs = {
                new java.io.File("src/main/resources" + resPath),
                new java.io.File("mods/ModularNuclear/src/main/resources" + resPath),
                new java.io.File("build/resources/main" + resPath),
                new java.io.File("build/nuclear-sim-wasm/" + name),
                new java.io.File("/home/mgomezch/stuff/dev/nh-dev/mods/ModularNuclear/src/main/resources" + resPath),
                new java.io.File("/home/mgomezch/stuff/dev/nh-dev/mods/ModularNuclear/build/nuclear-sim-wasm/" + name)
            };
            for (java.io.File f : searchDirs) {
                if (f.exists()) {
                    try {
                        return java.nio.file.Files.readAllBytes(f.toPath());
                    } catch (Exception ignored) {}
                }
            }
            return null;
        }
    }

    static class IndexHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String html = getIndexHtml()
                .replace("/*__INITIAL_STATE__*/", "window.__INITIAL_STATE__ = " + getStateJson() + ";");
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders()
                .set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private static String getIndexHtml() {
        return getHtmlHead() + getHtmlBody() + getHtmlScriptsPart1() + getHtmlScriptsPart2();
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

              /* Alert banner */
              #alert-box { display: none; background: rgba(255, 71, 87, 0.2); border: 1px solid var(--danger); color: #ffb8b8; padding: 12px; border-radius: 6px; margin-bottom: 12px; width: 100%; font-size: 0.85rem; }

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
                <span class="badge" id="engine-badge" style="background:#065f46; border-color:#059669; color:#34d399; cursor:pointer;" onclick="toggleEngineMode()" title="Click to toggle between Client WASM and Server mode">⚡ WASM Client</span>
                <span class="badge" id="casing-badge">Electrum Casing (1000°C Max)</span>
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
                    <optgroup label="🏆 Maxxed-Out Optimum Ceilings (nuclear_ceiling_best.json)">
                      <option value="BEST_ELECTRUM_5X5">EV Peak: Electrum 5x5 (934k EU/t · 456A · +660%)</option>
                      <option value="BEST_PLATINUM_9X9" selected>IV Peak: Platinum 9x9 (5.24M EU/t · 640A · +966%)</option>
                      <option value="BEST_OSMIUM_9X9">LuV Peak: Osmium 9x9 (31.7M EU/t · 967A · +1511%)</option>
                      <option value="BEST_QUANTIUM_13X13">ZPM Peak: Quantium 13x13 (63.6M EU/t · 486A · +709%)</option>
                      <option value="BEST_FLUXED_13X13">UV Peak: Fluxed 13x13 (109M EU/t · 207A · +246%)</option>
                      <option value="BEST_PLUTONIUM_13X13">UHV Peak: Black Plutonium 13x13 (157M EU/t · 75A · +25%)</option>
                    </optgroup>
                  </select>
                  <select id="tier-select" onchange="changeTier(this.value)" title="Reactor Pipe & Casing Tier">
                    <option value="0">Electrum (1000°C)</option>
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

                <div class="grid-toolbar">
                  <div class="grid-toolbar-title">
                    <span id="grid-dim-label">Chamber Grid (9×9)</span>
                  </div>

                  <div class="display-mode-selector">
                    <span class="mode-label">Overlay:</span>
                    <div class="segmented-control">
                      <button type="button" class="seg-btn active" id="btn-mode-temp" onclick="setGridDisplayMode('TEMP')" title="Show Cell Temperature (°C) [Key: 1]">🌡️ Temp</button>
                      <button type="button" class="seg-btn" id="btn-mode-total-flux" onclick="setGridDisplayMode('TOTAL_FLUX')" title="Show Total Neutron Flux (Fast + Thermal) [Key: 2]">⚛️ Total Flux</button>
                      <button type="button" class="seg-btn" id="btn-mode-fast-flux" onclick="setGridDisplayMode('FAST_FLUX')" title="Show Fast Neutron Flux [Key: 3]">⚡ Fast Flux</button>
                      <button type="button" class="seg-btn" id="btn-mode-thermal-flux" onclick="setGridDisplayMode('THERMAL_FLUX')" title="Show Thermal Neutron Flux [Key: 4]">🟢 Thermal Flux</button>
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
                      <label style="font-size:0.7rem; color:var(--text-muted);">Fission Heat (EU/n):</label>
                      <input id="p-fission-heat" type="number" step="0.1" value="77.2" onchange="submitSimParams()" style="width:100%; background:#233044; border:1px solid var(--border-color); color:#fff; padding:6px; border-radius:6px; font-size:0.8rem;">
                    </div>
                  </div>
                  <div style="display:grid; grid-template-columns: 1fr 1fr; gap:6px; margin-bottom:6px;">
                    <div>
                      <label style="font-size:0.7rem; color:var(--text-muted);">Global Fission Mult (k):</label>
                      <input id="p-fiss-mult" type="number" step="0.005" value="0.348" onchange="submitSimParams()" style="width:100%; background:#233044; border:1px solid var(--border-color); color:#fff; padding:6px; border-radius:6px; font-size:0.8rem;">
                    </div>
                    <div>
                      <label style="font-size:0.7rem; color:var(--text-muted);">Ambient Temp (°C):</label>
                      <input id="p-ambient-temp" type="number" step="1.0" value="20.0" onchange="submitSimParams()" style="width:100%; background:#233044; border:1px solid var(--border-color); color:#fff; padding:6px; border-radius:6px; font-size:0.8rem;">
                    </div>
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
            let isWasmMode = false;
            let wasmSim = null;

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
                    wasmSim.initGrid(9, 9, 2);
                    wasmSim.loadPreset("BEST_PLATINUM_9X9");
                  }
                  currentState = wasmSim.getState();
                  renderUI();
                  console.log("⚡ Modular Nuclear Simulator running via WebAssembly (WASM)!");
                  return true;
                } catch (err) {
                  console.warn("WASM initialization fallback to Java server:", err);
                  isWasmMode = false;
                  const eb = document.getElementById("engine-badge");
                  if (eb) {
                    eb.innerText = "🌐 Java Server (HTTP)";
                    eb.style.background = "#1e293b";
                    eb.style.borderColor = "#334155";
                    eb.style.color = "#94a3b8";
                  }
                  return false;
                }
              }
              return false;
            }

            function toggleEngineMode() {
              if (!wasmSim) {
                alert("WebAssembly engine is not available in this environment.");
                return;
              }
              isWasmMode = !isWasmMode;
              const eb = document.getElementById("engine-badge");
              if (isWasmMode) {
                eb.innerText = "⚡ Client WASM (0ms)";
                eb.style.background = "#065f46";
                eb.style.borderColor = "#059669";
                eb.style.color = "#34d399";
                currentState = wasmSim.getState();
                renderUI();
              } else {
                eb.innerText = "🌐 Java Server (HTTP)";
                eb.style.background = "#1e293b";
                eb.style.borderColor = "#334155";
                eb.style.color = "#94a3b8";
                if (wasmSim.running) wasmSim.setRunning(false);
                fetchState();
                schedulePoll();
              }
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
              { type: "HATCH_HP_DISTILLED_WATER", name: "HP Distilled Water Hatch", code: "HP", color: "#0284c7" },
              { type: "HATCH_HEAVY_WATER", name: "Heavy Water Hatch", code: "HW", color: "#6366f1" },
              { type: "HATCH_HP_HEAVY_WATER", name: "HP Heavy Water Hatch", code: "HH", color: "#4338ca" },
              { type: "HATCH_IC2_COOLANT", name: "IC2 Coolant Hatch", code: "HC", color: "#14b8a6" },
              { type: "REFLECTOR_BERYLLIUM", name: "Beryllium Reflector", code: "RB", color: "#94a3b8" },
              { type: "REFLECTOR_CARBON", name: "Carbon Reflector", code: "RC", color: "#475569" },
              { type: "CONTROL_ROD", name: "Boron Control Rod", code: "CR", color: "#b91c1c" },
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
                btn.onclick = () => {
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
                "TEMP": "btn-mode-temp",
                "TOTAL_FLUX": "btn-mode-total-flux",
                "FAST_FLUX": "btn-mode-fast-flux",
                "THERMAL_FLUX": "btn-mode-thermal-flux"
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

            function renderUI() {
              if (!currentState) return;

              // Header badges
              document.getElementById("casing-badge").innerText = currentState.pipeTierName + " (Max " + currentState.maxSafeTemp + "°C)";
              const statusBadge = document.getElementById("status-badge");
              const playBtn = document.getElementById("btn-play");
              const alertBox = document.getElementById("alert-box");

              if (currentState.exploded) {
                statusBadge.innerText = "MELTDOWN / EXPLODED";
                statusBadge.style.color = "var(--danger)";
                playBtn.innerText = "▶ Start Simulation";
                playBtn.disabled = true;
                alertBox.style.display = "block";
                alertBox.innerHTML = `<strong>⚠️ CRITICAL FAILURE:</strong> ${currentState.explosionReason}`;
              } else {
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

              // Turbine telemetry
              if (currentState.powerEstimate) {
                const pe = currentState.powerEstimate;
                document.getElementById("stat-power-tier").innerText = formatPowerAndAmps(pe.totalPowerEUt, false);
                document.getElementById("stat-power").innerText = formatPowerAndAmps(pe.totalPowerEUt, false);
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
                    `;
                    cell.addEventListener("click", (e) => {
                      const cx = parseInt(cell.dataset.x);
                      const cy = parseInt(cell.dataset.y);
                      handleCellClick(cx, cy, e);
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
                  const desiredClass = "cell" + (isSelected ? " selected" : "");
                  if (cell.className !== desiredClass) {
                    cell.className = desiredClass;
                  }

                  let overlayBg = "transparent";
                  let metricStr = "";
                  let tipText = "";

                  if (gridDisplayMode === "TOTAL_FLUX") {
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
                  } else {
                    overlayBg = getTemperatureColor(t.temp, currentState.maxSafeTemp);
                    metricStr = Math.round(t.temp) + "°C";
                    tipText = `Temperature: ${t.temp.toFixed(1)}°C (Max Safe: ${currentState.maxSafeTemp}°C)`;
                  }

                  if (t.temp > (currentState.maxSafeTemp || 3250) * 0.85) {
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
                      imgEl.onerror = () => { imgEl.src = '/api/icon/' + t.type; };
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

            function handleCellClick(x, y, e) {
              const tile = currentState && currentState.tiles.find(t => t.x === x && t.y === y);
              if (!tile) return;
              if (tile.code === "NL") {
                selectCell(tile);
                return;
              }
              if (interactionMode === "INSPECT" || (e && e.shiftKey)) {
                selectCell(tile);
              } else {
                setTile(x, y, activePaletteType);
                selectCell(tile);
              }
            }

            function selectCell(tile) {
              selectedTilePos = { x: tile.x, y: tile.y };
              renderInspector(tile);
              const gridElem = document.getElementById("reactor-grid");
              for (let i = 0; i < gridElem.children.length; i++) {
                const c = gridElem.children[i];
                const isSel = parseInt(c.dataset.x) === tile.x && parseInt(c.dataset.y) === tile.y;
                const isWall = c.classList.contains("wall-cell");
                const desired = "cell" + (isWall ? " wall-cell" : "") + (isSel ? " selected" : "");
                if (c.className !== desired) c.className = desired;
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
              } else if (t.isFuel) {
                extraHtml = `
                  <div class="stat-row"><span>Durability:</span><span class="stat-val">${t.durability} (${t.durabilityPct}%)</span></div>
                `;
              } else if (t.fluidName && t.fluidName.length > 0) {
                const cap = t.fluidCapacity || 2000;
                const pct = Math.round((t.fluidAmount / cap) * 100);
                const dryBadge = t.wasDry
                  ? '<span class="badge" style="color:var(--danger); background:rgba(255,71,87,0.2);">⚠️ BOILED DRY</span>'
                  : '<span class="badge" style="color:var(--success);">NORMAL</span>';
                const steamFlow = t.lastProduced || 0;
                const coolantTurnover = steamFlow > 0 ? (steamFlow / 160).toFixed(0) : 0;
                extraHtml = `
                  <div class="stat-row"><span>Coolant:</span><span class="stat-val">${t.fluidName}</span></div>
                  <div class="stat-row"><span>Coolant Volume:</span><span class="stat-val">${t.fluidAmount} / ${cap} L (${pct}%)</span></div>
                  <div class="stat-row"><span>Live Turnover:</span><span class="stat-val" style="color:#38bdf8;">${coolantTurnover} L/t coolant &rarr; ${steamFlow} L/t steam</span></div>
                  <div class="stat-row"><span>Cumulative Steam:</span><span class="stat-val">${t.steamAmount ? t.steamAmount.toLocaleString() : 0} L</span></div>
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

              el.innerHTML = `
                <div style="display:flex; align-items:center; gap:10px; margin-bottom:10px;">
                  ${iconHtml}
                  <div>
                    <div style="font-weight:bold; font-size:1rem; color:#fff;">${t.name}</div>
                    <div style="font-size:0.75rem; color:var(--text-muted);">Position: (${t.x}, ${t.y})</div>
                  </div>
                </div>
                <div class="stat-row"><span>Temperature:</span><span class="stat-val">${t.temp} °C</span></div>
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

              const url = `/api/set-params?euPerDegree=${euDeg}&fissionHeat=${fHeat}&fissionMult=${fMult}&ambientTemp=${amb}&hatchCapacity=${cap}&turnoverCurve=${curve}&turnoverDeltaTMax=${dt}&turnoverExponent=${exp}&coolantFeedRate=${feed}&coolingHeat=${ch}&ic2CoolantHeat=${ic2Ch}&hpBoil=${hpBoil}`;
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
              if (isWasmMode && wasmSim) {
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
              if (isWasmMode && wasmSim) {
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

            const PRESET_TO_VOLTAGE = {
              "60A_ELECTRUM_5X5": "EV",
              "60A_PLATINUM_9X9": "IV",
              "60A_OSMIUM_9X9": "LuV",
              "60A_QUANTIUM_13X13": "ZPM",
              "60A_FLUXED_13X13": "UV",
              "60A_PLUTONIUM_13X13": "UHV",
              "BEST_ELECTRUM_5X5": "EV",
              "BEST_PLATINUM_9X9": "IV",
              "BEST_OSMIUM_9X9": "LuV",
              "BEST_QUANTIUM_13X13": "ZPM",
              "BEST_FLUXED_13X13": "UV",
              "BEST_PLUTONIUM_13X13": "UHV"
            };

            const TIER_TO_VOLTAGE = ["EV", "IV", "LuV", "ZPM", "UV", "UHV"];

            async function loadPreset(name) {
              lastChartTick = -1;
              if (window.innerWidth <= 800) {
                isFitMode = true;
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
              await fetch(`/api/set-tile?x=${x}&y=${y}&type=${type}`);
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
                const scFlow = Math.round((currentState.flowSupercriticalSteam || 0) + (currentState.flowHPHeavyWaterSteam || 0) + (isSC ? pe.coolantSteamProduced : 0));
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
                setGridDisplayMode("TEMP");
              } else if (e.key === "2") {
                setGridDisplayMode("TOTAL_FLUX");
              } else if (e.key === "3") {
                setGridDisplayMode("FAST_FLUX");
              } else if (e.key === "4") {
                setGridDisplayMode("THERMAL_FLUX");
              } else if (e.key === "m" || e.key === "M") {
                const modes = ["TEMP", "TOTAL_FLUX", "FAST_FLUX", "THERMAL_FLUX"];
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
              if (wasmLoaded) {
                if (isFitMode) recalculateFitZoom();
              } else {
                fetchState().then(() => {
                  if (isFitMode) recalculateFitZoom();
                  schedulePoll();
                });
              }
            });
            </script>
            </body>
            </html>
            """;
    }
}

package com.gtnewhorizons.modularnuclear.common.nuclear.standalone.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid;

/**
 * Closed-loop control rod controllers, one per zone of the core, as a player can build them with OpenComputers: a
 * program reads cell temperatures from the reactor's telemetry, runs a PID controller per zone, and sets that zone's
 * rods through bundled redstone (16 or 256 levels), every few ticks.
 *
 * <p>
 * The {@code rod_pid} option holds the zones, separated by {@code |}; each zone is {@code key:value} pairs separated
 * by {@code /}:
 * <ul>
 * <li>{@code rods}: the cells whose rods the zone drives, as {@code x.y} separated by {@code +}, or {@code all}
 * (every rod no other zone drives)</li>
 * <li>{@code sense}: the cells whose temperature it reads (default: its rods' cells)</li>
 * <li>{@code stat}: {@code max} (default), {@code avg} or {@code min} of those temperatures</li>
 * <li>{@code sp}: the setpoint (°C)</li>
 * <li>{@code kp}, {@code ki}, {@code kd}: gains, in rod insertion (0 to 1) per °C above the setpoint, per °C·s, and
 * per °C/s</li>
 * <li>{@code bias}: insertion at zero error (default 0)</li>
 * <li>{@code period}: ticks between updates (default 20)</li>
 * <li>{@code levels}: insertion steps the signal carries (default 16: vanilla redstone; 256: ProjectRed bundled)</li>
 * <li>{@code min}, {@code max}: insertion limits (default 0 and 1)</li>
 * </ul>
 * For example {@code rod_pid=rods:2.2+6.6/sp:900/kp:0.004/ki:0.0005/kd:0.01|rods:all/sp:1000/kp:0.002}.
 */
public final class RodControllers {

    /** One zone's controller and what it did during the run. */
    public static final class Zone {

        final List<int[]> rods = new ArrayList<>();
        final List<int[]> sense = new ArrayList<>();
        boolean allRods;
        String stat = "max";
        double setpoint = 1000.0, kp, ki, kd, bias, min = 0.0, max = 1.0;
        int period = 20, levels = 16;

        double integral, lastError;
        boolean started;
        int insertion = -1;

        // Run statistics
        int updates, saturatedUpdates;
        double insertionSum, minInsertion = 1.0, maxInsertion = 0.0, maxSensed = -Double.MAX_VALUE, lastSensed;
    }

    private final List<Zone> zones = new ArrayList<>();

    /** Parses a {@code rod_pid} value; an empty or null spec gives no controllers. */
    public static RodControllers parse(String spec) {
        RodControllers c = new RodControllers();
        if (spec == null || spec.trim()
            .isEmpty()) return c;
        for (String zoneSpec : spec.split("\\|")) {
            if (zoneSpec.trim()
                .isEmpty()) continue;
            Zone z = new Zone();
            for (String pair : zoneSpec.split("/")) {
                int colon = pair.indexOf(':');
                if (colon < 0) continue;
                String key = pair.substring(0, colon)
                    .trim()
                    .toLowerCase(Locale.ROOT);
                String value = pair.substring(colon + 1)
                    .trim();
                try {
                    switch (key) {
                        case "rods" -> {
                            if (value.equalsIgnoreCase("all")) z.allRods = true;
                            else z.rods.addAll(parseCells(value));
                        }
                        case "sense" -> z.sense.addAll(parseCells(value));
                        case "stat" -> z.stat = value.toLowerCase(Locale.ROOT);
                        case "sp" -> z.setpoint = Double.parseDouble(value);
                        case "kp" -> z.kp = Double.parseDouble(value);
                        case "ki" -> z.ki = Double.parseDouble(value);
                        case "kd" -> z.kd = Double.parseDouble(value);
                        case "bias" -> z.bias = Double.parseDouble(value);
                        case "min" -> z.min = clamp01(Double.parseDouble(value));
                        case "max" -> z.max = clamp01(Double.parseDouble(value));
                        case "period" -> z.period = Math.max(1, Integer.parseInt(value));
                        case "levels" -> z.levels = Math.max(2, Integer.parseInt(value));
                        default -> {}
                    }
                } catch (NumberFormatException ignored) {}
            }
            c.zones.add(z);
        }
        return c;
    }

    private static List<int[]> parseCells(String value) {
        List<int[]> cells = new ArrayList<>();
        for (String cell : value.split("\\+")) {
            String[] xy = cell.trim()
                .split("\\.");
            if (xy.length == 2) cells.add(new int[] { Integer.parseInt(xy[0]), Integer.parseInt(xy[1]) });
        }
        return cells;
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    public boolean isEmpty() {
        return zones.isEmpty();
    }

    public List<Zone> zones() {
        return zones;
    }

    /** Resolves {@code rods:all} and default sensor cells against the grid; call once, before the run. */
    public void bind(StandaloneNuclearGrid grid) {
        boolean[][] taken = new boolean[grid.getWidth()][grid.getHeight()];
        for (Zone z : zones) {
            z.rods.removeIf(c -> !hasRod(grid, c[0], c[1]));
            for (int[] c : z.rods) taken[c[0]][c[1]] = true;
        }
        for (Zone z : zones) {
            if (!z.allRods) continue;
            for (int x = 0; x < grid.getWidth(); x++) {
                for (int y = 0; y < grid.getHeight(); y++) {
                    if (!taken[x][y] && hasRod(grid, x, y)) {
                        z.rods.add(new int[] { x, y });
                        taken[x][y] = true;
                    }
                }
            }
        }
        for (Zone z : zones) {
            z.sense.removeIf(c -> grid.getTile(c[0], c[1]) == null);
            if (z.sense.isEmpty()) z.sense.addAll(z.rods);
        }
    }

    private static boolean hasRod(StandaloneNuclearGrid grid, int x, int y) {
        SimTile t = grid.getTile(x, y);
        return t != null && t.hasControlRod();
    }

    /** Runs each zone whose update falls on this tick (1-based) and sets its rods. */
    public void update(StandaloneNuclearGrid grid, int tick) {
        for (Zone z : zones) {
            if (z.rods.isEmpty() || (tick - 1) % z.period != 0) continue;
            double sensed = sense(grid, z);
            double error = sensed - z.setpoint;
            double dt = z.period / 20.0;
            double derivative = z.started ? (error - z.lastError) / dt : 0.0;
            // Anti-windup: the integral term alone never asks for more than the full range
            if (z.ki != 0.0) {
                z.integral += error * dt;
                double limit = 1.0 / Math.abs(z.ki);
                z.integral = Math.max(-limit, Math.min(limit, z.integral));
            }
            double u = z.bias + z.kp * error + z.ki * z.integral + z.kd * derivative;
            double clamped = Math.max(z.min, Math.min(z.max, u));
            double level = Math.round(clamped * (z.levels - 1)) / (double) (z.levels - 1);
            int percent = (int) Math.round(level * 100.0);
            for (int[] c : z.rods) {
                SimTile t = grid.getTile(c[0], c[1]);
                if (t != null) t.setControlRodInsertion(percent);
            }
            z.started = true;
            z.lastError = error;
            z.insertion = percent;
            z.lastSensed = sensed;
            z.maxSensed = Math.max(z.maxSensed, sensed);
            z.updates++;
            if (u >= z.max || u <= z.min) z.saturatedUpdates++;
            z.insertionSum += level;
            z.minInsertion = Math.min(z.minInsertion, level);
            z.maxInsertion = Math.max(z.maxInsertion, level);
        }
    }

    private static double sense(StandaloneNuclearGrid grid, Zone z) {
        double sum = 0.0, hi = -Double.MAX_VALUE, lo = Double.MAX_VALUE;
        int n = 0;
        for (int[] c : z.sense) {
            SimTile t = grid.getTile(c[0], c[1]);
            if (t == null) continue;
            double temp = t.getTemperature();
            sum += temp;
            hi = Math.max(hi, temp);
            lo = Math.min(lo, temp);
            n++;
        }
        if (n == 0) return z.setpoint;
        return switch (z.stat) {
            case "avg" -> sum / n;
            case "min" -> lo;
            default -> hi;
        };
    }

    /** Each zone's rods and what its controller did, for the JSON report. */
    void writeJson(JsonWriter j) {
        j.array("rodControllers");
        for (Zone z : zones) {
            j.beginObject()
                .field("rods", z.rods.size())
                .field("sensors", z.sense.size())
                .field("updates", z.updates)
                .field("insertion", z.insertion)
                .fixed("meanInsertion", z.updates > 0 ? z.insertionSum / z.updates : 0.0, 3)
                .fixed("minInsertion", z.updates > 0 ? z.minInsertion : 0.0, 3)
                .fixed("maxInsertion", z.updates > 0 ? z.maxInsertion : 0.0, 3)
                .fixed("saturatedShare", z.updates > 0 ? z.saturatedUpdates / (double) z.updates : 0.0, 3)
                .fixed("lastSensed", z.updates > 0 ? z.lastSensed : 0.0, 1)
                .fixed("maxSensed", z.updates > 0 ? z.maxSensed : 0.0, 1)
                .endObject();
        }
        j.endArray();
    }
}

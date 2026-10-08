package com.gtnewhorizons.modularnuclear.common.nuclear;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Locale;

import javax.imageio.ImageIO;

/**
 * Mathematical solver and programmatic chart renderer for nuclear fuel temperature durability damage curves.
 * <p>
 * Fuel durability damage follows three physical processes:
 * 1. Fast neutron emission
 * 2. Any neutron absorption (fast + thermal)
 * 3. Temperature above ambient
 * <p>
 * The temperature durability damage curve D_temp(T) satisfies:
 * - D_temp(T) = 0 for T <= T_ambient
 * - D_temp(T) = A * (exp(k * (T - T_ambient)) - 1) for T > T_ambient
 * - The point of peak reactivity T_peak produces no more than 25% of the damage at the 10% reactivity point T_10%:
 * D_temp(T_peak) / D_temp(T_10%) <= 0.25
 */
public class FuelDamageCurveSolver {

    public static final double DEFAULT_AMBIENT_TEMP = 20.0;
    public static final double P_10_PERCENT = Math.acos(-17.0 / 19.0) / Math.PI; // ~0.8525547

    public static class FuelCurveStats {

        public final NuclearFuelType fuel;
        public final double tPeak;
        public final double tFloor;
        public final double t10Percent;
        public final double ambientTemp;
        public final double k;
        public final double A;
        public final double damageAtPeak;
        public final double damageAt10Percent;
        public final double damageAtFloor;
        public final double damageRatioPercent;

        public FuelCurveStats(NuclearFuelType fuel, double tPeak, double tFloor, double t10Percent, double ambientTemp,
            double k, double A, double damageAtPeak, double damageAt10Percent, double damageAtFloor,
            double damageRatioPercent) {
            this.fuel = fuel;
            this.tPeak = tPeak;
            this.tFloor = tFloor;
            this.t10Percent = t10Percent;
            this.ambientTemp = ambientTemp;
            this.k = k;
            this.A = A;
            this.damageAtPeak = damageAtPeak;
            this.damageAt10Percent = damageAt10Percent;
            this.damageAtFloor = damageAtFloor;
            this.damageRatioPercent = damageRatioPercent;
        }

        public double calculateDamage(double temp) {
            if (temp <= ambientTemp) return 0.0;
            return A * (Math.exp(k * (temp - ambientTemp)) - 1.0);
        }

        @Override
        public String toString() {
            return String.format(
                Locale.US,
                "%-17s | T_pk=%6.1f°C | T_10=%6.1f°C | k=%9.6f | A=%8.4f | D(T_pk)=%4.2f | D(T_10)=%4.2f | ratio=%5.2f%% | D(T_fl)=%5.2f",
                fuel.name(),
                tPeak,
                t10Percent,
                k,
                A,
                damageAtPeak,
                damageAt10Percent,
                damageRatioPercent,
                damageAtFloor);
        }
    }

    /**
     * Solves the exponential parameters (k, A) for a given fuel type and ambient temperature.
     */
    public static FuelCurveStats solve(NuclearFuelType fuel, double ambientTemp) {
        return solve(fuel, fuel.peakReactivityTemp, fuel.floorTemp, ambientTemp);
    }

    /**
     * Solves the exponential parameters (k, A) given peak and floor temperatures.
     */
    public static FuelCurveStats solve(NuclearFuelType fuel, double tPeak, double tFloor, double ambientTemp) {
        double t10 = tPeak + P_10_PERCENT * (tFloor - tPeak);
        double dtPeak = tPeak - ambientTemp;
        double dt10 = t10 - ambientTemp;
        double linearRatio = (dt10 > 0.0) ? (dtPeak / dt10) : 1.0;

        double k;
        if (linearRatio <= 0.25) {
            // Linear limit is already <= 25%, pick small k for smooth, gentle exponential
            k = 0.0001;
        } else {
            // Solve (exp(k*dtPeak) - 1) / (exp(k*dt10) - 1) = 0.25 via bisection
            double low = 1e-7;
            double high = 0.1;
            for (int iter = 0; iter < 80; iter++) {
                double mid = (low + high) * 0.5;
                double val = (Math.exp(mid * dtPeak) - 1.0) / (Math.exp(mid * dt10) - 1.0);
                if (val > 0.25) {
                    low = mid;
                } else {
                    high = mid;
                }
            }
            k = (low + high) * 0.5;
        }

        // Calibrate A so that damage at peak operating temperature is exactly 1.0
        double denom = Math.exp(k * dtPeak) - 1.0;
        double A = (denom > 0.0) ? (1.0 / denom) : 1.0;

        double dPeak = A * (Math.exp(k * dtPeak) - 1.0);
        double d10 = A * (Math.exp(k * dt10) - 1.0);
        double dFloor = A * (Math.exp(k * (tFloor - ambientTemp)) - 1.0);
        double ratioPct = (d10 > 0.0) ? (dPeak / d10) * 100.0 : 0.0;

        return new FuelCurveStats(fuel, tPeak, tFloor, t10, ambientTemp, k, A, dPeak, d10, dFloor, ratioPct);
    }

    /**
     * Evaluates temperature durability damage for any fuel at a given temperature.
     */
    public static double getTemperatureDamage(NuclearFuelType fuel, double temp, double ambientTemp) {
        if (fuel == null || temp <= ambientTemp) return 0.0;
        return fuel.calculateTemperatureDamage(temp, ambientTemp);
    }

    /**
     * Renders a publication-quality chart of both Reactivity (%) and Temperature Durability Damage vs Temperature.
     */
    public static BufferedImage renderChart(NuclearFuelType fuel, int width, int height) {
        FuelCurveStats stats = solve(fuel, DEFAULT_AMBIENT_TEMP);
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Background
        g.setColor(new Color(0x1B, 0x1E, 0x22));
        g.fillRect(0, 0, width, height);

        // Chart inner area
        int padL = 26;
        int padR = 8;
        int padT = 15;
        int padB = 16;
        int plotW = width - padL - padR;
        int plotH = height - padT - padB;

        g.setColor(new Color(0x23, 0x27, 0x2E));
        g.fillRect(padL, padT, plotW, plotH);
        g.setColor(new Color(0x3E, 0x44, 0x51));
        g.drawRect(padL, padT, plotW, plotH);

        // Domain max: round up to multiple of 500
        double maxTemp = Math.ceil((stats.tFloor + 200.0) / 500.0) * 500.0;
        double maxDamage = Math.max(6.0, Math.ceil(stats.damageAtFloor * 1.1));

        // Grid lines: Horizontal (Reactivity 25%, 50%, 75%, 100%)
        g.setColor(new Color(0x2D, 0x31, 0x39));
        g.setFont(new Font("SansSerif", Font.PLAIN, 8));
        for (int p = 25; p <= 100; p += 25) {
            int y = padT + plotH - (int) Math.round((p / 100.0) * plotH);
            g.drawLine(padL, y, padL + plotW, y);
            g.setColor(new Color(0x6A, 0x73, 0x7D));
            g.drawString(p + "%", 4, y + 3);
            g.setColor(new Color(0x2D, 0x31, 0x39));
        }

        // Grid lines: Vertical (Temperature steps of 500°C)
        for (int t = 500; t < maxTemp; t += 500) {
            int x = padL + (int) Math.round((t / maxTemp) * plotW);
            g.drawLine(x, padT, x, padT + plotH);
            g.setColor(new Color(0x6A, 0x73, 0x7D));
            String tStr = String.valueOf(t);
            int sw = g.getFontMetrics()
                .stringWidth(tStr);
            g.drawString(tStr, x - sw / 2, height - 4);
            g.setColor(new Color(0x2D, 0x31, 0x39));
        }

        // Reactivity curve (Cyan: 0xFF00D4FF)
        int[] rX = new int[plotW];
        int[] rY = new int[plotW];
        for (int i = 0; i < plotW; i++) {
            double temp = (i / (double) plotW) * maxTemp;
            double r = fuel.calculateReactivity(temp);
            rX[i] = padL + i;
            rY[i] = padT + plotH - (int) Math.round(Math.max(0.0, Math.min(1.0, r)) * plotH);
        }
        g.setColor(new Color(0x00, 0xD4, 0xFF));
        g.setStroke(new BasicStroke(1.8f));
        g.drawPolyline(rX, rY, plotW);

        // Durability damage curve (Orange-Red: 0xFFFF5500)
        int[] dX = new int[plotW];
        int[] dY = new int[plotW];
        for (int i = 0; i < plotW; i++) {
            double temp = (i / (double) plotW) * maxTemp;
            double d = stats.calculateDamage(temp);
            dX[i] = padL + i;
            dY[i] = padT + plotH - (int) Math.round(Math.max(0.0, Math.min(1.0, d / maxDamage)) * plotH);
        }
        g.setColor(new Color(0xFF, 0x55, 0x00));
        g.setStroke(new BasicStroke(1.8f));
        g.drawPolyline(dX, dY, plotW);

        // Marker for T_peak
        int peakX = padL + (int) Math.round((stats.tPeak / maxTemp) * plotW);
        int peakYReactivity = padT + plotH - (int) Math.round(fuel.calculateReactivity(stats.tPeak) * plotH);
        int peakYDamage = padT + plotH - (int) Math.round((stats.damageAtPeak / maxDamage) * plotH);

        g.setColor(new Color(0x00, 0xD4, 0xFF));
        g.fillOval(peakX - 3, peakYReactivity - 3, 6, 6);
        g.setColor(new Color(0xFF, 0x55, 0x00));
        g.fillOval(peakX - 3, peakYDamage - 3, 6, 6);

        // Marker for T_10%
        int t10X = padL + (int) Math.round((stats.t10Percent / maxTemp) * plotW);
        int t10YReactivity = padT + plotH - (int) Math.round(fuel.calculateReactivity(stats.t10Percent) * plotH);
        int t10YDamage = padT + plotH - (int) Math.round((stats.damageAt10Percent / maxDamage) * plotH);

        g.setColor(new Color(0x00, 0xD4, 0xFF));
        g.fillOval(t10X - 3, t10YReactivity - 3, 6, 6);
        g.setColor(new Color(0xFF, 0x55, 0x00));
        g.fillOval(t10X - 3, t10YDamage - 3, 6, 6);

        // Top Legend
        g.setFont(new Font("SansSerif", Font.PLAIN, 8));
        // Reactivity legend item
        g.setColor(new Color(0x00, 0xD4, 0xFF));
        g.drawLine(padL + 2, 7, padL + 9, 7);
        g.drawString("Reactivity", padL + 12, 10);

        // Durability damage legend item
        int leg2X = padL + 62;
        g.setColor(new Color(0xFF, 0x55, 0x00));
        g.drawLine(leg2X, 7, leg2X + 7, 7);
        g.drawString("Durability Dmg", leg2X + 10, 10);

        g.dispose();
        return img;
    }

    /**
     * Programmatic batch generation of all fuel charts.
     */
    public static void generateAllChartImages(File outputDir) throws IOException {
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        System.out.println("=========================================================================================");
        System.out.println("                 MPTR NUCLEAR FUEL DAMAGE CURVE SOLVER RESULTS                           ");
        System.out.println("=========================================================================================");
        for (NuclearFuelType fuel : NuclearFuelType.values()) {
            FuelCurveStats stats = solve(fuel, DEFAULT_AMBIENT_TEMP);
            System.out.println(stats);

            BufferedImage chart = renderChart(fuel, 154, 76);
            File outFile = new File(
                outputDir,
                "chart_" + fuel.name()
                    .toLowerCase(Locale.US) + ".png");
            ImageIO.write(chart, "PNG", outFile);
        }
        System.out.println("=========================================================================================");
        System.out.println("Successfully generated 13 fuel stats chart textures in " + outputDir.getAbsolutePath());
    }

    public static void main(String[] args) throws Exception {
        File outDir = (args.length > 0) ? new File(args[0])
            : new File("src/main/resources/assets/modularnuclear/textures/gui/nei/fuelstats");
        generateAllChartImages(outDir);
    }
}

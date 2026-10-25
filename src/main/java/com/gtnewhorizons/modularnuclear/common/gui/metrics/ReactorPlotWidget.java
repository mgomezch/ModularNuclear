package com.gtnewhorizons.modularnuclear.common.gui.metrics;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.EnumChatFormatting;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.Widget;

/**
 * High-performance vector chart widget for MPTR live reactor cycle telemetry.
 * Supports quartile envelope distributions (min, Q1, median, Q3, max) with subtle styling,
 * dynamic X/Y scaling, interactive vertical crosshair, indicator dots, and hover tooltips.
 */
public class ReactorPlotWidget extends Widget<ReactorPlotWidget> implements Interactable {

    public enum Metric {

        CORE_TEMP("Core temp", "°C", true, s -> (float) s.tempMedian, s -> s.tempMin, s -> s.tempQ1, s -> s.tempMedian,
            s -> s.tempQ3, s -> s.tempMax, s -> s.tempAvg, 0xFFFFB020, // Warm amber
            0x993B82F6, // Subtle cool blue for min
            0xCCEF4444 // Subtle red for max
        ),
        REACTIVITY("Reactivity", "%", true, s -> (float) s.reactivityMedian, s -> s.reactivityMin, s -> s.reactivityQ1,
            s -> s.reactivityMedian, s -> s.reactivityQ3, s -> s.reactivityMax, s -> s.reactivityAvg, 0xFF00D4FF, // Cyan
            0x996366F1, // Subtle indigo for min
            0xCC38BDF8 // Subtle sky blue for max
        ),
        FAST_NEUTRONS("Fast neutrons produced", "n/s", false, s -> (float) s.getFastNeutronsPerSec(), null, null, null,
            null, null, null, 0xFFA855F7, // Purple
            0, 0),
        THERMAL_NEUTRONS("Thermal neutrons absorbed", "n/s", false, s -> (float) s.getThermalNeutronsAbsorbedPerSec(),
            null, null, null, null, null, null, 0xFF06B6D4, // Teal / Cyan
            0, 0),
        ESCAPED_NEUTRONS("Escaped neutrons", "n/s", false, s -> (float) s.getEscapedNeutronsPerSec(), null, null, null,
            null, null, null, 0xFFF97316, // Orange
            0, 0),
        DIRECT_EU("Direct EU output", "EU/t", false, s -> (float) s.directEU, null, null, null, null, null, null,
            0xFFEAB308, // Gold / Yellow
            0, 0),
        COOLANT_OUTPUT("Coolant output", "L/s", false, s -> (float) s.getCoolantOutputPerSec(), null, null, null, null,
            null, null, 0xFF3B82F6, // Blue
            0, 0);

        private final String title;
        private final String unit;
        private final boolean quartile;
        private final Function<ReactorCycleSnapshot, Float> scalarVal;
        private final Function<ReactorCycleSnapshot, Float> minVal;
        private final Function<ReactorCycleSnapshot, Float> q1Val;
        private final Function<ReactorCycleSnapshot, Float> medVal;
        private final Function<ReactorCycleSnapshot, Float> q3Val;
        private final Function<ReactorCycleSnapshot, Float> maxVal;
        private final Function<ReactorCycleSnapshot, Float> avgVal;
        private final int mainColor;
        private final int minColor;
        private final int maxColor;

        Metric(String title, String unit, boolean quartile, Function<ReactorCycleSnapshot, Float> scalarVal,
            Function<ReactorCycleSnapshot, Float> minVal, Function<ReactorCycleSnapshot, Float> q1Val,
            Function<ReactorCycleSnapshot, Float> medVal, Function<ReactorCycleSnapshot, Float> q3Val,
            Function<ReactorCycleSnapshot, Float> maxVal, Function<ReactorCycleSnapshot, Float> avgVal, int mainColor,
            int minColor, int maxColor) {
            this.title = title;
            this.unit = unit;
            this.quartile = quartile;
            this.scalarVal = scalarVal;
            this.minVal = minVal;
            this.q1Val = q1Val;
            this.medVal = medVal;
            this.q3Val = q3Val;
            this.maxVal = maxVal;
            this.avgVal = avgVal;
            this.mainColor = mainColor;
            this.minColor = minColor;
            this.maxColor = maxColor;
        }

        public String getTitle() {
            return title;
        }

        public String getUnit() {
            return unit;
        }

        public boolean isQuartile() {
            return quartile;
        }

        public Function<ReactorCycleSnapshot, Float> getScalarVal() {
            return scalarVal;
        }

        public Function<ReactorCycleSnapshot, Float> getMinVal() {
            return minVal;
        }

        public Function<ReactorCycleSnapshot, Float> getMaxVal() {
            return maxVal;
        }

        public Function<ReactorCycleSnapshot, Float> getMedVal() {
            return medVal;
        }
    }

    private final Metric metric;
    private final Supplier<ReactorMetricsHistory> historySupplier;

    private ReactorCycleSnapshot hoveredSnapshot = null;
    private int hoveredIndex = -1;
    private int hoveredTotalCount = 0;

    public ReactorPlotWidget(Metric metric, Supplier<ReactorMetricsHistory> historySupplier) {
        this.metric = metric;
        this.historySupplier = historySupplier;

        fullWidth();
        height(94);
        marginBottom(6);

        tooltipDynamic(this::buildTooltip);
        tooltipAutoUpdate(true);
        tooltipShowUpTimer(0);
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
        int w = getArea().w();
        int h = getArea().h();
        if (w <= 0 || h <= 0) return;

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;

        // Container background
        GuiDraw.drawRect(0, 0, w, h, 0xEE111827);
        GuiDraw.drawRect(0, 0, w, 1, 0xFF1F2937);
        GuiDraw.drawRect(0, h - 1, w, 1, 0xFF1F2937);
        GuiDraw.drawRect(0, 0, 1, h, 0xFF1F2937);
        GuiDraw.drawRect(w - 1, 0, 1, h, 0xFF1F2937);

        // Header: metric title (sentence case)
        font.drawString(metric.getTitle(), 6, 4, 0xFFE5E7EB);

        ReactorMetricsHistory history = historySupplier != null ? historySupplier.get() : null;
        List<ReactorCycleSnapshot> snapshots = history != null ? history.getSnapshots() : null;

        if (snapshots == null || snapshots.isEmpty()) {
            String emptyMsg = "no cycle data recorded yet";
            font.drawString(emptyMsg, w - font.getStringWidth(emptyMsg) - 6, 4, 0xFF6B7280);

            // Empty state placeholder
            String waitMsg = "Awaiting reactor cycle completion with Computer Screen cover...";
            font.drawString(waitMsg, (w - font.getStringWidth(waitMsg)) / 2, (h - 8) / 2, 0xFF4B5563);
            hoveredSnapshot = null;
            return;
        }

        int N = snapshots.size();
        ReactorCycleSnapshot latest = snapshots.get(N - 1);

        // Header info: latest value / summary on top right
        if (metric.isQuartile()) {
            String latestStr = String.format(
                Locale.US,
                "med: %.1f%s (spread: %.1f - %.1f)",
                metric.medVal.apply(latest),
                metric.getUnit(),
                metric.minVal.apply(latest),
                metric.maxVal.apply(latest));
            font.drawString(latestStr, w - font.getStringWidth(latestStr) - 6, 4, 0xFF9CA3AF);
        } else {
            String latestStr = String
                .format(Locale.US, "latest: %,d %s", Math.round(metric.scalarVal.apply(latest)), metric.getUnit());
            font.drawString(latestStr, w - font.getStringWidth(latestStr) - 6, 4, 0xFF9CA3AF);
        }

        // Plot canvas area
        int plotX = 36;
        int plotY = 16;
        int plotW = Math.max(10, w - plotX - 8);
        int plotH = Math.max(10, h - plotY - 14);

        // Dark canvas background
        GuiDraw.drawRect(plotX, plotY, plotW, plotH, 0x44030712);

        // Compute min and max value range across all snapshots
        float[] bounds = calculateBounds(metric, snapshots);
        float minVal = bounds[0];
        float maxVal = bounds[1];
        float range = bounds[2];

        // Draw horizontal grid lines & Y-axis labels
        float[] gridFracs = { 1.0f, 0.5f, 0.0f };
        for (float frac : gridFracs) {
            int lineY = (int) (plotY + plotH - frac * plotH);
            GuiDraw.drawRect(plotX, lineY, plotW, 1, 0x1AFFFFFF);

            float yVal = minVal + frac * range;
            String lbl = formatCompact(yVal);
            int strW = font.getStringWidth(lbl);
            font.drawString(lbl, plotX - strW - 3, lineY - 4, 0xFF6B7280);
        }

        // Draw X-axis relative time labels
        if (N > 1) {
            String leftLbl = "-" + (N - 1) + "s";
            font.drawString(leftLbl, plotX, plotY + plotH + 3, 0xFF6B7280);

            String midLbl = "-" + (N / 2) + "s";
            font.drawString(midLbl, plotX + (plotW - font.getStringWidth(midLbl)) / 2, plotY + plotH + 3, 0xFF6B7280);

            String rightLbl = "now";
            font.drawString(rightLbl, plotX + plotW - font.getStringWidth(rightLbl), plotY + plotH + 3, 0xFF6B7280);
        }

        // GL state setup for smooth vector curve rendering
        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);

        if (metric.isQuartile()) {
            // 1. Min curve (subtle minColor)
            setGlColor(metric.minColor, 0.6f);
            GL11.glLineWidth(1.0f);
            drawCurve(snapshots, metric.minVal, plotX, plotY, plotW, plotH, minVal, range);

            // 2. Q1 curve (subtle mainColor)
            setGlColor(metric.mainColor, 0.4f);
            GL11.glLineWidth(1.0f);
            drawCurve(snapshots, metric.q1Val, plotX, plotY, plotW, plotH, minVal, range);

            // 3. Median curve (bold mainColor)
            setGlColor(metric.mainColor, 1.0f);
            GL11.glLineWidth(2.0f);
            drawCurve(snapshots, metric.medVal, plotX, plotY, plotW, plotH, minVal, range);

            // 4. Q3 curve (subtle mainColor)
            setGlColor(metric.mainColor, 0.4f);
            GL11.glLineWidth(1.0f);
            drawCurve(snapshots, metric.q3Val, plotX, plotY, plotW, plotH, minVal, range);

            // 5. Max curve (subtle maxColor)
            setGlColor(metric.maxColor, 0.75f);
            GL11.glLineWidth(1.0f);
            drawCurve(snapshots, metric.maxVal, plotX, plotY, plotW, plotH, minVal, range);
        } else {
            // Single scalar curve (bold mainColor)
            setGlColor(metric.mainColor, 1.0f);
            GL11.glLineWidth(2.0f);
            drawCurve(snapshots, metric.scalarVal, plotX, plotY, plotW, plotH, minVal, range);
        }

        // Interactive hover check
        int mx = context.getMouseX();
        int my = context.getMouseY();

        if (mx >= plotX && mx <= plotX + plotW && my >= plotY && my <= plotY + plotH) {
            float t = (mx - plotX) / (float) plotW;
            int k = Math.round(t * (N - 1));
            k = Math.max(0, Math.min(N - 1, k));

            hoveredSnapshot = snapshots.get(k);
            hoveredIndex = k;
            hoveredTotalCount = N;

            float guideX = (N == 1) ? (plotX + plotW / 2.0f) : (plotX + (k / (float) (N - 1)) * plotW);

            // Vertical crosshair guide
            GL11.glLineWidth(1.0f);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 0.45f);
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f(guideX, plotY);
            GL11.glVertex2f(guideX, plotY + plotH);
            GL11.glEnd();

            // Indicator dots at hover point
            if (metric.isQuartile()) {
                drawDot(
                    guideX,
                    getY(metric.minVal.apply(hoveredSnapshot), plotY, plotH, minVal, range),
                    metric.minColor);
                drawDot(
                    guideX,
                    getY(metric.q1Val.apply(hoveredSnapshot), plotY, plotH, minVal, range),
                    metric.mainColor);
                drawDot(
                    guideX,
                    getY(metric.medVal.apply(hoveredSnapshot), plotY, plotH, minVal, range),
                    metric.mainColor);
                drawDot(
                    guideX,
                    getY(metric.q3Val.apply(hoveredSnapshot), plotY, plotH, minVal, range),
                    metric.mainColor);
                drawDot(
                    guideX,
                    getY(metric.maxVal.apply(hoveredSnapshot), plotY, plotH, minVal, range),
                    metric.maxColor);
            } else {
                drawDot(
                    guideX,
                    getY(metric.scalarVal.apply(hoveredSnapshot), plotY, plotH, minVal, range),
                    metric.mainColor);
            }
        } else {
            hoveredSnapshot = null;
            hoveredIndex = -1;
        }

        // Restore GL state
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glLineWidth(1.0f);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glPopMatrix();
    }

    public static float[] calculateBounds(Metric metric, List<ReactorCycleSnapshot> snapshots) {
        float minVal = Float.MAX_VALUE;
        float maxVal = -Float.MAX_VALUE;

        if (snapshots != null) {
            for (ReactorCycleSnapshot s : snapshots) {
                if (metric.isQuartile()) {
                    minVal = Math.min(
                        minVal,
                        metric.getMinVal()
                            .apply(s));
                    maxVal = Math.max(
                        maxVal,
                        metric.getMaxVal()
                            .apply(s));
                } else {
                    float v = metric.getScalarVal()
                        .apply(s);
                    minVal = Math.min(minVal, v);
                    maxVal = Math.max(maxVal, v);
                }
            }
        }

        float range;
        if ("%".equals(metric.getUnit())) {
            minVal = 0.0f;
            maxVal = 100.0f;
            range = 100.0f;
        } else if (metric == Metric.CORE_TEMP) {
            minVal = Math.min(minVal, 20f);
            maxVal = Math.max(maxVal, 100f);
            range = (maxVal - minVal) * 1.1f;
        } else {
            minVal = Math.min(minVal, 0f);
            maxVal = Math.max(maxVal, 1f);
            range = (maxVal - minVal) * 1.1f;
        }

        if (range < 0.001f) range = 1.0f;
        return new float[] { minVal, maxVal, range };
    }

    private void drawCurve(List<ReactorCycleSnapshot> snapshots, Function<ReactorCycleSnapshot, Float> valueGetter,
        int plotX, int plotY, int plotW, int plotH, float minVal, float range) {
        int n = snapshots.size();
        if (n <= 0) return;

        if (n == 1) {
            float x = plotX + plotW / 2.0f;
            float y = getY(valueGetter.apply(snapshots.get(0)), plotY, plotH, minVal, range);
            drawDot(x, y, 0xFFFFFFFF);
            return;
        }

        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int i = 0; i < n; i++) {
            float x = plotX + (i / (float) (n - 1)) * plotW;
            float val = valueGetter.apply(snapshots.get(i));
            float y = getY(val, plotY, plotH, minVal, range);
            GL11.glVertex2f(x, y);
        }
        GL11.glEnd();
    }

    private float getY(float val, int plotY, int plotH, float minVal, float range) {
        float normalized = (val - minVal) / range;
        normalized = Math.max(0.0f, Math.min(1.0f, normalized));
        return (plotY + plotH) - normalized * plotH;
    }

    private void drawDot(float cx, float cy, int argb) {
        setGlColor(argb, 1.0f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(cx - 1.5f, cy - 1.5f);
        GL11.glVertex2f(cx + 1.5f, cy - 1.5f);
        GL11.glVertex2f(cx + 1.5f, cy + 1.5f);
        GL11.glVertex2f(cx - 1.5f, cy + 1.5f);
        GL11.glEnd();
    }

    private void setGlColor(int argb, float alphaMultiplier) {
        float a = ((argb >> 24) & 0xFF) / 255.0f * alphaMultiplier;
        float r = ((argb >> 16) & 0xFF) / 255.0f;
        float g = ((argb >> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;
        GL11.glColor4f(r, g, b, a);
    }

    private String formatCompact(float val) {
        if (Math.abs(val) >= 1_000_000f) {
            return String.format(Locale.US, "%.1fM", val / 1_000_000f);
        } else if (Math.abs(val) >= 1_000f) {
            return String.format(Locale.US, "%.0fk", val / 1_000f);
        } else if (Math.abs(val) < 10f && val != (int) val) {
            return String.format(Locale.US, "%.1f", val);
        }
        return String.valueOf(Math.round(val));
    }

    private void buildTooltip(RichTooltip tooltip) {
        if (hoveredSnapshot == null || hoveredIndex < 0) return;

        int cyclesAgo = hoveredTotalCount - 1 - hoveredIndex;
        double secsAgo = (cyclesAgo * (double) hoveredSnapshot.ticksPerCycle) / 20.0;
        String cycleTitle = "Cycle #" + hoveredSnapshot.cycleIndex
            + (cyclesAgo == 0 ? " (current)"
                : String.format(Locale.US, " (%.1fs ago, %d cycles ago)", secsAgo, cyclesAgo));

        tooltip.addLine(EnumChatFormatting.GOLD + "" + EnumChatFormatting.BOLD + cycleTitle);

        if (metric.isQuartile()) {
            tooltip.addLine(
                EnumChatFormatting.RED + "Max: "
                    + EnumChatFormatting.WHITE
                    + String.format(Locale.US, "%.1f %s", metric.maxVal.apply(hoveredSnapshot), metric.getUnit()));
            tooltip.addLine(
                EnumChatFormatting.YELLOW + "Q3 (75%): "
                    + EnumChatFormatting.WHITE
                    + String.format(Locale.US, "%.1f %s", metric.q3Val.apply(hoveredSnapshot), metric.getUnit()));
            tooltip.addLine(
                EnumChatFormatting.GREEN + "Median: "
                    + EnumChatFormatting.WHITE
                    + String.format(Locale.US, "%.1f %s", metric.medVal.apply(hoveredSnapshot), metric.getUnit()));
            tooltip.addLine(
                EnumChatFormatting.AQUA + "Q1 (25%): "
                    + EnumChatFormatting.WHITE
                    + String.format(Locale.US, "%.1f %s", metric.q1Val.apply(hoveredSnapshot), metric.getUnit()));
            tooltip.addLine(
                EnumChatFormatting.BLUE + "Min: "
                    + EnumChatFormatting.WHITE
                    + String.format(Locale.US, "%.1f %s", metric.minVal.apply(hoveredSnapshot), metric.getUnit()));
            tooltip.addLine(
                EnumChatFormatting.GRAY + "Avg: "
                    + EnumChatFormatting.WHITE
                    + String.format(Locale.US, "%.1f %s", metric.avgVal.apply(hoveredSnapshot), metric.getUnit()));
        } else {
            float val = metric.scalarVal.apply(hoveredSnapshot);
            if (metric == Metric.DIRECT_EU) {
                tooltip.addLine(
                    EnumChatFormatting.GRAY + metric.getTitle()
                        + ": "
                        + EnumChatFormatting.WHITE
                        + String.format(Locale.US, "%,d %s", Math.round(val), metric.getUnit()));
            } else {
                long perCycle = 0;
                String cycleUnit = switch (metric) {
                    case FAST_NEUTRONS -> {
                        perCycle = hoveredSnapshot.fastNeutronsProduced;
                        yield "n/cycle";
                    }
                    case THERMAL_NEUTRONS -> {
                        perCycle = hoveredSnapshot.thermalNeutronsAbsorbed;
                        yield "n/cycle";
                    }
                    case ESCAPED_NEUTRONS -> {
                        perCycle = hoveredSnapshot.escapedNeutrons;
                        yield "n/cycle";
                    }
                    case COOLANT_OUTPUT -> {
                        perCycle = hoveredSnapshot.coolantOutput;
                        yield "L/cycle";
                    }
                    default -> "";
                };
                tooltip.addLine(
                    EnumChatFormatting.GRAY + metric.getTitle()
                        + ": "
                        + EnumChatFormatting.WHITE
                        + String.format(
                            Locale.US,
                            "%,d %s (%,d %s)",
                            Math.round(val),
                            metric.getUnit(),
                            perCycle,
                            cycleUnit));
            }
        }
    }

    @Override
    public boolean onMouseScroll(UpOrDown upOrDown, int delta) {
        if (getParent() instanceof Interactable) {
            return ((Interactable) getParent()).onMouseScroll(upOrDown, delta);
        }
        return false;
    }
}

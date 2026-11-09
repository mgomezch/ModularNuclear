package com.gtnewhorizons.modularnuclear.common.nei;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.modularnuclear.common.gui.ColorUtils;

import codechicken.lib.gui.GuiDraw;

/**
 * The colours of the MPTR NEI pages for the loaded resource packs, and the slots and bevels they draw with. See Note
 * [Resource packs restyle our GUIs].
 */
public final class NEIPalette {

    private static final ResourceLocation SLOT_TEXTURE = new ResourceLocation("nei:textures/slot.png");

    private static NEIPalette sCurrent;
    private static boolean sListening;

    /** Only blended into other colours. See wrinkle (W2) of Note [Resource packs restyle our GUIs]. */
    public final int panel;
    /** Lines inside a panel. */
    public final int divider;
    public final int grid;
    public final int cursor;

    public final int inkStrong;
    public final int ink;
    public final int inkDim;
    public final int inkFaint;
    public final int title;
    public final int subtitle;
    public final int label;

    public final int category;
    public final int reflectedText;
    public final int fast;
    public final int fastText;
    public final int absorbed;
    public final int absorbedText;
    public final int scattered;
    public final int scatteredText;
    public final int thermal;
    public final int thermalFaint;
    public final int thermalFainter;
    public final int thermalCornerText;
    public final int lost;
    public final int heatArrow;
    public final int conductanceText;
    public final int removal;
    public final int blocksText;
    public final int burnsText;
    public final int byproductText;
    public final int indestructibleText;
    /** A tile with no neighbours' worth of neutrons to show. */
    public final int spreadEmpty;
    public final int spreadMiddle;

    public final int reactivityCurve;
    public final int reactivityLabel;
    public final int damageCurve;
    public final int powerCurve;
    public final int powerLabel;
    /** The radiovoltaic chart's steam-route reference line. */
    public final int steamRoute;

    public final int peakStat;
    public final int floorStat;
    public final int pointStat;
    public final int goodStat;
    public final int thermalFissionStat;
    public final int yieldStat;

    public final int hotTile;
    public final int coldTile;
    public final int tileSign;
    public final int tileHighlight;
    public final int tileShadow;

    private NEIPalette() {
        this.panel = ColorUtils.neiPanel.getColor();
        this.divider = ColorUtils.neiDivider.getColor();
        this.grid = ColorUtils.neiPlotGrid.getColor();
        this.cursor = ColorUtils.neiPlotCursor.getColor();

        this.inkStrong = ColorUtils.neiInkStrong.getColor();
        this.ink = ColorUtils.neiInk.getColor();
        this.inkDim = ColorUtils.neiInkDim.getColor();
        this.inkFaint = ColorUtils.neiInkFaint.getColor();
        this.title = ColorUtils.neiTitle.getColor();
        this.subtitle = ColorUtils.neiSubtitle.getColor();
        this.label = ColorUtils.neiLabel.getColor();

        this.category = ColorUtils.neiCategory.getColor();
        this.reflectedText = ColorUtils.neiReflectedText.getColor();
        this.fast = ColorUtils.neiFast.getColor();
        this.fastText = ColorUtils.neiFastText.getColor();
        this.absorbed = ColorUtils.neiAbsorbed.getColor();
        this.absorbedText = ColorUtils.neiAbsorbedText.getColor();
        this.scattered = ColorUtils.neiScattered.getColor();
        this.scatteredText = ColorUtils.neiScatteredText.getColor();
        this.thermal = ColorUtils.neiThermal.getColor();
        this.thermalFaint = mix(thermal, panel, 0.25);
        this.thermalFainter = mix(thermal, panel, 0.35);
        this.thermalCornerText = ColorUtils.neiThermalCornerText.getColor();
        this.lost = ColorUtils.neiLost.getColor();
        this.heatArrow = ColorUtils.neiHeatArrow.getColor();
        this.conductanceText = ColorUtils.neiConductanceText.getColor();
        this.removal = ColorUtils.neiRemoval.getColor();
        this.blocksText = ColorUtils.neiBlocksText.getColor();
        this.burnsText = ColorUtils.neiBurnsText.getColor();
        this.byproductText = ColorUtils.neiByproductText.getColor();
        this.indestructibleText = ColorUtils.neiIndestructibleText.getColor();
        this.spreadEmpty = mix(panel, ink, 0.135);
        this.spreadMiddle = mix(panel, ink, 0.66);

        this.reactivityCurve = ColorUtils.neiReactivityCurve.getColor();
        this.reactivityLabel = ColorUtils.neiReactivityLabel.getColor();
        this.damageCurve = ColorUtils.neiDamageCurve.getColor();
        this.powerCurve = ColorUtils.neiPowerCurve.getColor();
        this.powerLabel = ColorUtils.neiPowerLabel.getColor();
        this.steamRoute = ColorUtils.neiSteamRoute.getColor();

        this.peakStat = ColorUtils.neiPeakStat.getColor();
        this.floorStat = ColorUtils.neiFloorStat.getColor();
        this.pointStat = ColorUtils.neiPointStat.getColor();
        this.goodStat = ColorUtils.neiGoodStat.getColor();
        this.thermalFissionStat = ColorUtils.neiThermalFissionStat.getColor();
        this.yieldStat = ColorUtils.neiYieldStat.getColor();

        this.hotTile = ColorUtils.neiHotTile.getColor();
        this.coldTile = ColorUtils.neiColdTile.getColor();
        this.tileSign = ColorUtils.neiTileSign.getColor();
        this.tileHighlight = ColorUtils.neiTileHighlight.getColor();
        this.tileShadow = ColorUtils.neiTileShadow.getColor();
    }

    /** Read once, and again after each resource reload, so that the blends are worked out once per reload. */
    public static NEIPalette current() {
        if (sCurrent != null) return sCurrent;
        Minecraft mc = Minecraft.getMinecraft();
        if (!sListening && mc != null && mc.getResourceManager() instanceof IReloadableResourceManager reloadable) {
            sListening = true;
            reloadable.registerReloadListener((IResourceManagerReloadListener) manager -> sCurrent = null);
        }
        sCurrent = new NEIPalette();
        return sCurrent;
    }

    /** From {@code a} at {@code t} = 0 to {@code b} at {@code t} = 1, opaque. */
    public static int mix(int a, int b, double t) {
        int result = 0xFF000000;
        for (int shift = 0; shift <= 16; shift += 8) {
            int ca = (a >> shift) & 0xFF;
            int cb = (b >> shift) & 0xFF;
            result |= ((int) Math.round(ca + (cb - ca) * t)) << shift;
        }
        return result;
    }

    public static void glColor(int rgb, float alpha) {
        GL11.glColor4f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, alpha);
    }

    /** A sunken card on NEI's window, which shows through it. */
    public void drawPanel(int x, int y, int w, int h) {
        drawBevel(x, y, w, h);
    }

    public void drawSlot(int x, int y) {
        GuiDraw.changeTexture(SLOT_TEXTURE);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        quad(tessellator, x, y, 18, 18, 0.0, 0.0, 1.0, 1.0);
        tessellator.draw();
    }

    /** The background of a plot whose axes run along the given rectangle: a slot stretched around it. */
    public void drawPlotWell(int x, int y, int w, int h) {
        final double e = 1.0 / 18.0;
        final double f = 17.0 / 18.0;
        GuiDraw.changeTexture(SLOT_TEXTURE);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        quad(tessellator, x, y, w, h, e, e, f, f);
        tessellator.draw();
        drawBevel(x - 1, y - 1, w + 2, h + 2);
    }

    /**
     * The border of a slot stretched around a rectangle: the slot texture's corners, and its edges without the corners.
     * The texture's coordinates run from 0 to 1, as a pack's slot texture can have any resolution.
     */
    private static void drawBevel(int x, int y, int w, int h) {
        final double e = 1.0 / 18.0;
        final double f = 17.0 / 18.0;
        GuiDraw.changeTexture(SLOT_TEXTURE);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        quad(tessellator, x, y, 1, 1, 0.0, 0.0, e, e);
        quad(tessellator, x + w - 1, y, 1, 1, f, 0.0, 1.0, e);
        quad(tessellator, x, y + h - 1, 1, 1, 0.0, f, e, 1.0);
        quad(tessellator, x + w - 1, y + h - 1, 1, 1, f, f, 1.0, 1.0);
        quad(tessellator, x + 1, y, w - 2, 1, e, 0.0, f, e);
        quad(tessellator, x + 1, y + h - 1, w - 2, 1, e, f, f, 1.0);
        quad(tessellator, x, y + 1, 1, h - 2, 0.0, e, e, f);
        quad(tessellator, x + w - 1, y + 1, 1, h - 2, f, e, 1.0, f);
        tessellator.draw();
    }

    private static void quad(Tessellator tessellator, int x, int y, int w, int h, double u0, double v0, double u1,
        double v1) {
        tessellator.addVertexWithUV(x, y + h, 0.0, u0, v1);
        tessellator.addVertexWithUV(x + w, y + h, 0.0, u1, v1);
        tessellator.addVertexWithUV(x + w, y, 0.0, u1, v0);
        tessellator.addVertexWithUV(x, y, 0.0, u0, v0);
    }
}

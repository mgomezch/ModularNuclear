package com.gtnewhorizons.modularnuclear.common.nei;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.modularnuclear.common.util.Swallowed;

import codechicken.lib.gui.GuiDraw;

/*-
Note [NEI colours follow the resource pack] (generated)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Topic.  The MPTR NEI pages draw their own panels, slots, plots and text.  A resource pack can restyle NEI's window, so
any colour fixed in the code would stand out, or vanish, under a pack such as Shadow UI.

Design.  Every surface is read from the textures NEI itself draws with, and everything drawn on a surface is worked out
from it.  NEIPalette holds the result, and the handlers ask it for colours by meaning.

    panel                    nei:textures/gui/recipebg.png, inside the window
    slot, its bevel          nei:textures/slot.png; panels use the same bevel
    well (behind a plot)     panel, moved towards the slot's highlight on a light panel and its fill on a dark one
    ink and the meanings     on a light panel, as designed; on a dark one, lightened until readable (readableOn)

Under the default textures this gives exactly the colours the pages were designed in.  A meaning keeps its hue under
any pack, so that red stays "absorbed" and purple stays "thermal".

Moving parts.
    NEIPalette.current       the palette for the loaded packs, read once and again after each resource reload
    NEIPalette.of            works a palette out from the sampled surfaces; NEIPaletteTest covers it
    NEIPalette.drawPanel, drawSunken, drawSlot, drawPlotWell    the surfaces
    NEIFuelStatsHandler, NEINeutronInteractionHandler           draw with these and nothing else

Wrinkles.
(W1) Angelica can recolour text as well.  A pack's dark_mode_utils block in pack.mcmeta makes Angelica replace text
     inside a GuiContainer's layers whose channels are all near black or all near white.  Shadow UI turns those into
     0xBFBFBF with a shadow.  Text that used to be 0x404040 on a fixed light panel would turn light on light.  This
     palette keeps working either way: on a dark panel no text is near black, ink ends up close to what the pack asks
     for, and the coloured text is left alone.  NEIPaletteTest.textStaysReadableUnderShadowUiRecolouring covers it.

(W2) Some colours are data, not theme, and stay fixed: the temperature scale of the heat card's row, and its hot and
     cold tiles, which carry their own white signs.

(W3) Packs with a mid grey panel count as light or dark by isDark, and a light panel keeps the designed colours as they
     are, without checking contrast.  Those colours include the fast card's bright green arrows, which stand out from a
     light panel by hue rather than by lightness, and forcing contrast on them would change the default look.
*/
public final class NEIPalette {

    private static final ResourceLocation WINDOW_TEXTURE = new ResourceLocation("nei:textures/gui/recipebg.png");
    private static final ResourceLocation SLOT_TEXTURE = new ResourceLocation("nei:textures/slot.png");

    // What NEI's own textures hold, for when a texture can't be read
    private static final int DEFAULT_PANEL = 0xFFC6C6C6;
    private static final int DEFAULT_SLOT = 0xFF8B8B8B;
    private static final int DEFAULT_SLOT_SHADOW = 0xFF373737;
    private static final int DEFAULT_SLOT_HIGHLIGHT = 0xFFFFFFFF;

    /** The WCAG contrast ratio that colours on a dark panel are brought up to. */
    static final double MIN_CONTRAST = 4.5;

    private static NEIPalette sCurrent;
    private static boolean sListening;

    public final int panel;
    public final int slot;
    public final int slotShadow;
    public final int slotHighlight;
    public final boolean dark;

    /** Behind a plot. */
    public final int well;
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

    public final int peakStat;
    public final int floorStat;
    public final int pointStat;
    public final int goodStat;
    public final int thermalFissionStat;
    public final int yieldStat;

    private NEIPalette(int panel, int slot, int slotShadow, int slotHighlight) {
        this.panel = panel;
        this.slot = slot;
        this.slotShadow = slotShadow;
        this.slotHighlight = slotHighlight;
        this.dark = isDark(panel);

        // On a dark panel a plot sinks towards the slots' fill, and on a light one it lifts towards their highlight
        this.well = mix(panel, dark ? slot : slotHighlight, 0.56);
        this.divider = slot;

        this.inkStrong = designed(0xFF000000);
        this.ink = designed(0xFF404040);
        this.inkDim = designed(0xFF606060);
        this.inkFaint = designed(0xFF505050);
        this.title = designed(0xFF111111);
        this.subtitle = designed(0xFF333333);
        this.label = designed(0xFF555555);
        this.grid = withAlpha(dark ? ink : 0xFF000000, dark ? 0x18 : 0x20);
        this.cursor = withAlpha(dark ? 0xFFFFFFFF : 0xFF000000, 0xA0);

        this.category = designed(0xFF103080);
        this.reflectedText = designed(0xFF9A6B00);
        this.fast = designed(0xFF0033EE);
        this.fastText = designed(0xFF0033CC);
        this.absorbed = designed(0xFFDD0000);
        this.absorbedText = designed(0xFFCC0000);
        this.scattered = designed(0xFF00CC00);
        this.scatteredText = designed(0xFF00A000);
        this.thermal = designed(0xFF7F00FF);
        this.thermalFaint = mix(thermal, panel, 0.25);
        this.thermalFainter = mix(thermal, panel, 0.35);
        this.thermalCornerText = designed(0xFF7200E6);
        this.lost = designed(0xFF7A7A7A);
        this.heatArrow = designed(0xFFF2913D);
        this.conductanceText = designed(0xFF8A4A00);
        this.removal = designed(0xFF0055AA);
        this.blocksText = designed(0xFF902020);
        this.burnsText = designed(0xFFAA5500);
        this.byproductText = designed(0xFF205090);
        this.indestructibleText = designed(0xFF008000);
        this.spreadEmpty = mix(panel, ink, 0.135);
        this.spreadMiddle = mix(panel, ink, 0.66);

        this.reactivityCurve = designed(0xFFC87800);
        this.reactivityLabel = designed(0xFF8A5F00);
        this.damageCurve = designed(0xFFCC0000);
        this.powerCurve = designed(0xFF0055CC);
        this.powerLabel = designed(0xFF0044AA);

        this.peakStat = designed(0xFF00AAAA);
        this.floorStat = designed(0xFFFF5555);
        this.pointStat = designed(0xFF5555FF);
        this.goodStat = designed(0xFF00AA00);
        this.thermalFissionStat = designed(0xFFAA00AA);
        this.yieldStat = designed(0xFFAA0000);
    }

    /** See Note [NEI colours follow the resource pack]. */
    public static NEIPalette of(int panel, int slot, int slotShadow, int slotHighlight) {
        return new NEIPalette(
            panel | 0xFF000000,
            slot | 0xFF000000,
            slotShadow | 0xFF000000,
            slotHighlight | 0xFF000000);
    }

    /** The palette under the default textures. */
    public static NEIPalette defaults() {
        return of(DEFAULT_PANEL, DEFAULT_SLOT, DEFAULT_SLOT_SHADOW, DEFAULT_SLOT_HIGHLIGHT);
    }

    public static NEIPalette current() {
        if (sCurrent != null) return sCurrent;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.getResourceManager() == null) return defaults();
        if (!sListening && mc.getResourceManager() instanceof IReloadableResourceManager reloadable) {
            sListening = true;
            reloadable.registerReloadListener((IResourceManagerReloadListener) manager -> sCurrent = null);
        }
        sCurrent = load(mc.getResourceManager());
        return sCurrent;
    }

    private static NEIPalette load(IResourceManager resources) {
        int panel = DEFAULT_PANEL;
        int slot = DEFAULT_SLOT;
        int slotShadow = DEFAULT_SLOT_SHADOW;
        int slotHighlight = DEFAULT_SLOT_HIGHLIGHT;
        BufferedImage window = readImage(resources, WINDOW_TEXTURE);
        if (window != null) {
            panel = opaquePixelOr(window, 30.0 / 256.0, 30.0 / 256.0, panel);
        }
        BufferedImage slotImage = readImage(resources, SLOT_TEXTURE);
        if (slotImage != null) {
            slot = opaquePixelOr(slotImage, 9.0 / 18.0, 9.0 / 18.0, slot);
            slotShadow = opaquePixelOr(slotImage, 0.0, 0.0, slotShadow);
            slotHighlight = opaquePixelOr(slotImage, 17.0 / 18.0, 17.0 / 18.0, slotHighlight);
        }
        return of(panel, slot, slotShadow, slotHighlight);
    }

    private static BufferedImage readImage(IResourceManager resources, ResourceLocation location) {
        try (InputStream in = resources.getResource(location)
            .getInputStream()) {
            return ImageIO.read(in);
        } catch (IOException swallowed) {
            Swallowed.note(swallowed);
            return null;
        }
    }

    /** @param u,v the pixel's place in the image from 0 to 1, as a pack's texture can have any resolution */
    private static int opaquePixelOr(BufferedImage image, double u, double v, int fallback) {
        int x = Math.min(image.getWidth() - 1, (int) (u * image.getWidth()));
        int y = Math.min(image.getHeight() - 1, (int) (v * image.getHeight()));
        int argb = image.getRGB(x, y);
        return (argb >>> 24) == 0xFF ? argb : fallback;
    }

    /** A colour designed for a light panel, as it should appear on this one. See wrinkle (W3). */
    private int designed(int color) {
        return dark ? readableOn(panel, color, MIN_CONTRAST) : color;
    }

    public static boolean isDark(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return (0.299 * r + 0.587 * g + 0.114 * b) < 128.0;
    }

    /** {@code color} lightened just enough to reach a contrast ratio against {@code background}, keeping its hue. */
    static int readableOn(int background, int color, double minContrast) {
        for (int step = 0; step <= 32; step++) {
            int candidate = mix(color, 0xFFFFFFFF, step / 32.0);
            if (contrast(background, candidate) >= minContrast) return candidate;
        }
        return 0xFFFFFFFF;
    }

    /** The WCAG contrast ratio between two colours, from 1 to 21. */
    static double contrast(int a, int b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(int color) {
        return 0.2126 * linear((color >> 16) & 0xFF) + 0.7152 * linear((color >> 8) & 0xFF)
            + 0.0722 * linear(color & 0xFF);
    }

    private static double linear(int channel) {
        double c = channel / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
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

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    public static void glColor(int rgb, float alpha) {
        GL11.glColor4f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, alpha);
    }

    /** A sunken card. */
    public void drawPanel(int x, int y, int w, int h) {
        GuiDraw.drawRect(x + 1, y, w - 2, 1, slotShadow);
        GuiDraw.drawRect(x, y + 1, 1, h - 2, slotShadow);
        GuiDraw.drawRect(x + 1, y + h - 1, w - 2, 1, slotHighlight);
        GuiDraw.drawRect(x + w - 1, y + 1, 1, h - 2, slotHighlight);
        GuiDraw.drawRect(x + 1, y + 1, w - 2, h - 2, panel);
    }

    public void drawSunken(int x, int y, int w, int h, int fill) {
        GuiDraw.drawRect(x, y, w, 1, slotShadow);
        GuiDraw.drawRect(x, y, 1, h, slotShadow);
        GuiDraw.drawRect(x + 1, y + h - 1, w - 2, 1, slotHighlight);
        GuiDraw.drawRect(x + w - 1, y + 1, 1, h - 2, slotHighlight);
        GuiDraw.drawRect(x + 1, y + 1, w - 2, h - 2, fill);
    }

    public void drawSlot(int x, int y) {
        GuiDraw.drawRect(x, y, 18, 1, slotShadow);
        GuiDraw.drawRect(x, y, 1, 18, slotShadow);
        GuiDraw.drawRect(x + 17, y, 1, 18, slotHighlight);
        GuiDraw.drawRect(x, y + 17, 18, 1, slotHighlight);
        GuiDraw.drawRect(x + 1, y + 1, 16, 16, slot);
    }

    /** The background of a plot whose axes run along the given rectangle. */
    public void drawPlotWell(int x, int y, int w, int h) {
        drawSunken(x - 1, y - 1, w + 2, h + 2, well);
    }
}

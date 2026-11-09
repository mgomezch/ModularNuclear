package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.nei.NEIPalette;

public class NEIPaletteTest {

    /** What Shadow UI 5.47's recipebg.png and slot.png hold. */
    private static final NEIPalette SHADOW_UI = NEIPalette.of(0x4A4948, 0x2C2C2C, 0x3C3B3A, 0x595857);

    /** Colours drawn on the panel, by field name: every int field but the surfaces and the translucent ones. */
    private static Map<String, Integer> colorsOnPanel(NEIPalette palette) throws IllegalAccessException {
        Map<String, Integer> colors = new LinkedHashMap<>();
        for (Field field : NEIPalette.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType() != int.class) continue;
            switch (field.getName()) {
                case "panel", "slot", "slotShadow", "slotHighlight", "well", "divider", "grid", "cursor":
                case "spreadEmpty", "spreadMiddle", "thermalFaint", "thermalFainter":
                    continue;
                default:
                    colors.put(field.getName(), field.getInt(palette));
            }
        }
        return colors;
    }

    @Test
    public void defaultTexturesGiveTheDesignedColours() {
        NEIPalette palette = NEIPalette.defaults();
        assertFalse(palette.dark);
        assertEquals(0xFFC6C6C6, palette.panel);
        assertEquals(0xFFE6E6E6, palette.well);
        assertEquals(0xFF404040, palette.ink);
        assertEquals(0xFF0033EE, palette.fast);
        assertEquals(0xFFCC0000, palette.absorbedText);
        assertEquals(0xFF00CC00, palette.scattered);
        assertEquals(0xFF7F00FF, palette.thermal);
    }

    @Test
    public void surfacesComeFromThePack() {
        assertTrue(SHADOW_UI.dark);
        assertEquals(0xFF4A4948, SHADOW_UI.panel);
        assertEquals(0xFF2C2C2C, SHADOW_UI.slot);
        assertEquals(0xFF3C3B3A, SHADOW_UI.slotShadow);
        assertEquals(0xFF595857, SHADOW_UI.slotHighlight);
        assertEquals(0xFF2C2C2C, SHADOW_UI.divider);
    }

    @Test
    public void plotWellStandsOffThePanelBetweenTheSlotAndItsHighlight() {
        assertTrue(brightness(NEIPalette.defaults().well) > brightness(NEIPalette.defaults().panel));
        assertTrue(brightness(SHADOW_UI.well) < brightness(SHADOW_UI.panel));
        assertTrue(brightness(SHADOW_UI.well) > brightness(SHADOW_UI.slot));
    }

    @Test
    public void everythingOnADarkPanelIsReadable() throws IllegalAccessException {
        for (int panel : new int[] { 0x4A4948, 0x202020, 0x000000, 0x3A2A55 }) {
            NEIPalette palette = NEIPalette.of(panel, 0x2C2C2C, 0x3C3B3A, 0x595857);
            colorsOnPanel(palette).forEach(
                (name, color) -> assertTrue(
                    contrast(palette.panel, color) >= 4.4,
                    () -> name + " " + Integer.toHexString(color) + " on " + Integer.toHexString(palette.panel)));
        }
    }

    @Test
    public void meaningsStayApartOnADarkPanel() {
        assertTrue(redness(SHADOW_UI.absorbed) > 40, "absorbed should stay red");
        assertTrue(blueness(SHADOW_UI.fast) > 40, "fast should stay blue");
        assertTrue(blueness(SHADOW_UI.thermal) > 20 && redness(SHADOW_UI.thermal) > 20, "thermal should stay purple");
        assertTrue(greenness(SHADOW_UI.scattered) > 40, "scattered should stay green");
    }

    /**
     * What Angelica does to text under Shadow UI's dark_mode_utils block: channels all at or under 0x606666 or all at
     * or over 0xBEBEBE become 0xBFBFBF.
     */
    private static int shadowUiRecolour(int color) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        boolean nearBlack = r <= 0x60 && g <= 0x66 && b <= 0x66;
        boolean nearWhite = r >= 0xBE && g >= 0xBE && b >= 0xBE;
        return nearBlack || nearWhite ? 0xFFBFBFBF : color;
    }

    @Test
    public void textStaysReadableUnderShadowUiRecolouring() throws IllegalAccessException {
        colorsOnPanel(SHADOW_UI).forEach((name, color) -> {
            int shown = shadowUiRecolour(color);
            assertTrue(contrast(SHADOW_UI.panel, shown) >= 4.4, () -> name + " shown as " + Integer.toHexString(shown));
        });
    }

    private static double contrast(int a, int b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(int color) {
        return 0.2126 * linear(color >> 16) + 0.7152 * linear(color >> 8) + 0.0722 * linear(color);
    }

    private static double linear(int channel) {
        double c = (channel & 0xFF) / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static int brightness(int color) {
        return ((color >> 16) & 0xFF) + ((color >> 8) & 0xFF) + (color & 0xFF);
    }

    private static int redness(int color) {
        return ((color >> 16) & 0xFF) - ((color >> 8) & 0xFF);
    }

    private static int greenness(int color) {
        return ((color >> 8) & 0xFF) - (color & 0xFF);
    }

    private static int blueness(int color) {
        return (color & 0xFF) - ((color >> 8) & 0xFF);
    }
}

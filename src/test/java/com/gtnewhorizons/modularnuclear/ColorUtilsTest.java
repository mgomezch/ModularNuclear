package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gtnewhorizon.gtnhlib.color.ColorResource;
import com.gtnewhorizons.modularnuclear.common.gui.ColorUtils;
import com.gtnewhorizons.modularnuclear.common.nei.NEIPalette;

public class ColorUtilsTest {

    /** Pack authors find a colour's lang key from its field, so the two must agree. */
    @Test
    public void everyColourIsKeyedByItsFieldName() throws IllegalAccessException {
        Set<String> keys = new HashSet<>();
        for (Field field : ColorUtils.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != ColorResource.class) continue;
            ColorResource colour = (ColorResource) field.get(null);
            assertEquals("color.resource.modularnuclear." + field.getName(), colour.getLangKey());
            assertTrue(keys.add(colour.getLangKey()), colour::getLangKey);
        }
        assertTrue(keys.size() > 100);
    }

    @Test
    public void defaultsAreTheDesignedColours() {
        assertEquals(0xFF404040, ColorUtils.neiInk.getColor());
        assertEquals(0x28000000, ColorUtils.neiPlotGrid.getColor());
        assertEquals(0xFFFFAA00, ColorUtils.plotCoreTemp.getColor());
    }

    /**
     * The stock web palette's slate and gray darks and its usual accents, the generic dark blue-teal look of a GUI
     * nobody designed for this game: none of our defaults is one. See Note [Resource packs restyle our GUIs]
     */
    @Test
    public void noDefaultIsAStockWebColour() throws IllegalAccessException {
        Set<Integer> stockWeb = Set.of(
            0x0F172A,
            0x1E293B,
            0x334155,
            0x475569, // slate 900 to 600
            0x030712,
            0x111827,
            0x1F2937,
            0x374151,
            0x4B5563,
            0x6B7280,
            0x9CA3AF,
            0xE5E7EB, // gray
            0x3B82F6,
            0x38BDF8,
            0x06B6D4,
            0x6366F1,
            0xA855F7,
            0x10B981,
            0xF97316,
            0xEAB308,
            0xEF4444);
        for (Field field : ColorUtils.class.getFields()) {
            if (field.getType() != ColorResource.class) continue;
            int rgb = ((ColorResource) field.get(null)).getColor() & 0xFFFFFF;
            assertTrue(!stockWeb.contains(rgb), () -> field.getName() + " defaults to " + Integer.toHexString(rgb));
        }
    }

    @Test
    public void mixRunsBetweenTheTwoColours() {
        assertEquals(0xFFC6C6C6, NEIPalette.mix(0xC6C6C6, 0x7F00FF, 0.0));
        assertEquals(0xFF7F00FF, NEIPalette.mix(0xC6C6C6, 0x7F00FF, 1.0));
        assertEquals(0xFFA363E3, NEIPalette.mix(0xC6C6C6, 0x7F00FF, 0.5));
    }
}

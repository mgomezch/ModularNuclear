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
        assertEquals(0xEE12161E, ColorUtils.reactorGridBackground.getColor());
    }

    @Test
    public void mixRunsBetweenTheTwoColours() {
        assertEquals(0xFFC6C6C6, NEIPalette.mix(0xC6C6C6, 0x7F00FF, 0.0));
        assertEquals(0xFF7F00FF, NEIPalette.mix(0xC6C6C6, 0x7F00FF, 1.0));
        assertEquals(0xFFA363E3, NEIPalette.mix(0xC6C6C6, 0x7F00FF, 0.5));
    }
}

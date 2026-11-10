package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.client.texture.HueShiftedSprite;

/** Heavy water's texture: Minecraft's water with its hue turned, everything else kept. */
class HueShiftedSpriteTest {

    private static int shifted(int argb, float turns) {
        BufferedImage in = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        in.setRGB(0, 0, argb);
        return HueShiftedSprite.shiftHue(in, turns)
            .getRGB(0, 0);
    }

    @Test
    void aThirdOfATurnTakesBlueToRedAndKeepsAlpha() {
        assertEquals(0xB3FF0000, shifted(0xB30000FF, 1.0f / 3.0f));
    }

    @Test
    void waterTurnsAlittlePurple() {
        // Vanilla water's average colour, turned 15 degrees: more red, the same brightness
        int out = shifted(0xB32E43F4, 15.0f / 360.0f);
        Color c = new Color(out, true);
        assertTrue(
            c.getRed() > 0x2E && c.getGreen() < 0x43,
            "Expected a purpler blue, got " + Integer.toHexString(out));
        assertEquals(0xF4, c.getBlue(), 1);
        assertEquals(0xB3, c.getAlpha());
    }
}

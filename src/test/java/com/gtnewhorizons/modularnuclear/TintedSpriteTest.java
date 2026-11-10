package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.client.texture.TintedSprite.Tint;

/** Heavy water's texture: Minecraft's water, tinted. */
// See Note [Tinting Minecraft's water]
class TintedSpriteTest {

    /** Heavy water's tint, as ModFluids has it. */
    private static final Tint HEAVY_WATER = new Tint(23.0f / 360.0f, 3.0f, 2.5f, 1.0f, 1.0f, 0x38007C, 0.10f);

    /** A made-up 4x4 frame in water's colours: a blue around (0x2E, 0x43, 0xF4), ripples in red, green and alpha. */
    private static final int[] FRAME = { 0xB32E43F4, 0xA8243AF2, 0xDF5A6EF4, 0xB0283EF4, 0xAB2A40F3, 0xC0405AF4,
        0xB32E43F4, 0xAA2238F4, 0xB8344AF4, 0xAD2C42F4, 0xD05468F4, 0xB12D43F4, 0xA9233BF3, 0xB32E43F4, 0xBE3C52F4,
        0xB0293FF4 };

    /** FRAME through the slider page's JavaScript, at heavy water's tint. */
    private static final int[] FRAME_TINTED = { 0xAE5D22E8, 0x934807E3, 0xFFA998E8, 0xA75111E8, 0x9A5317E6, 0xCF6E52E8,
        0xAE5D22E8, 0x984701E8, 0xBB6532E8, 0x9F571CE8, 0xF79F88E8, 0xA9591FE8, 0x954204E6, 0xAE5D22E8, 0xCA7247E8,
        0xA75214E8 };

    private static BufferedImage image(int w, int h, int... argb) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, w, h, argb, 0, w);
        return image;
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    private static void assertClose(int expected, int actual, String what) {
        for (int shift = 0; shift < 32; shift += 8) {
            assertEquals(
                (expected >>> shift) & 0xFF,
                (actual >>> shift) & 0xFF,
                1,
                what + ": expected " + Integer.toHexString(expected) + ", got " + Integer.toHexString(actual));
        }
    }

    @Test
    void aThirdOfATurnTakesBlueToRedAndKeepsAlpha() {
        Tint hueOnly = new Tint(1.0f / 3.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0, 0.0f);
        assertEquals(0xB3FF0000, pixels(hueOnly.apply(image(1, 1, 0xB30000FF)))[0]);
    }

    @Test
    void heavyWaterMatchesTheSliderPage() {
        int[] out = pixels(HEAVY_WATER.apply(image(4, 4, FRAME)));
        for (int i = 0; i < FRAME.length; i++) {
            assertClose(FRAME_TINTED[i], out[i], "pixel " + i);
        }
    }

    @Test
    void heavyWaterIsPurpleWithStrongerRipples() {
        int[] out = pixels(HEAVY_WATER.apply(image(4, 4, FRAME)));
        // FRAME's lightest and darkest pixels: their gap in red and in alpha grows
        Color light = new Color(out[2], true), dark = new Color(out[7], true);
        assertTrue(light.getRed() - dark.getRed() > 3 * (0x5A - 0x22) / 2, "Expected the ripples to stand out more");
        // (alpha less than 2.5 times: the lightest pixel's clamps at 255)
        assertTrue(light.getAlpha() - dark.getAlpha() > 3 * (0xDF - 0xAA) / 2, "Expected the opacity ripples to grow");
        // The plain water colour: red well above green, blue still the strongest
        Color plain = new Color(out[0], true);
        assertTrue(plain.getRed() > 2 * plain.getGreen() && plain.getBlue() > plain.getRed(), "Expected purple");
    }

    @Test
    void eachFrameUsesItsOwnMean() {
        // Two frames, one uniformly lighter: contrast around each frame's own mean leaves both flat
        BufferedImage strip = image(1, 2, 0xB32E43F4, 0xB35A6EF4);
        Tint contrastOnly = new Tint(0.0f, 3.0f, 2.5f, 1.0f, 1.0f, 0, 0.0f);
        int[] out = pixels(contrastOnly.apply(strip));
        assertClose(0xB32E43F4, out[0], "the first frame");
        assertClose(0xB35A6EF4, out[1], "the second frame");
    }
}

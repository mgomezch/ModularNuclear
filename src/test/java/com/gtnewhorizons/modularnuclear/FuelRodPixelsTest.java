package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.client.texture.FuelRodPixels;
import com.gtnewhorizons.modularnuclear.common.nuclear.CustomFuelMix;
import com.gtnewhorizons.modularnuclear.common.nuclear.CustomFuelMix.ActinideIsotope;

/** See Note [Custom fuel rod textures]. */
public class FuelRodPixelsTest {

    private static final int GREY = 0xFF7C7C7C;
    private static final int GREEN = 0xFF60AE11; // GregTech's uranium rod's window
    private static final int DARK_GREEN = 0xFF4E8715;
    private static final int TEAL = 0xFF63987E; // its depleted rod's window
    private static final int BLACK = 0xFF000000; // thorium's window

    /** A 4x6 image of a rod one pixel wide, at column x, from row top: housing, two window pixels, housing. */
    private static int[] rod(int x, int top, int w1, int w2) {
        int[] p = new int[24];
        p[top * 4 + x] = GREY;
        p[(top + 1) * 4 + x] = w1;
        p[(top + 2) * 4 + x] = w2;
        p[(top + 3) * 4 + x] = GREY;
        return p;
    }

    @Test
    void windowIsTheGreenTintedPixels() {
        assertTrue(FuelRodPixels.isFuelWindow(GREEN));
        assertTrue(FuelRodPixels.isFuelWindow(DARK_GREEN));
        assertTrue(FuelRodPixels.isFuelWindow(TEAL));
        assertFalse(FuelRodPixels.isFuelWindow(GREY));
        assertFalse(FuelRodPixels.isFuelWindow(BLACK));
        assertFalse(FuelRodPixels.isFuelWindow(0x0060AE11)); // transparent
        assertFalse(FuelRodPixels.isFuelWindow(0xFF475192)); // naquadah's blue housing
    }

    @Test
    void baseIsTheRodWithAnEmptyWindow() {
        int[] base = FuelRodPixels.withoutWindow(rod(1, 0, GREEN, DARK_GREEN));
        assertArrayEquals(rod(1, 0, 0, 0), base);
    }

    @Test
    void recolouredWindowKeepsTheShading() {
        int[] out = FuelRodPixels.recolouredWindow(rod(1, 0, GREEN, DARK_GREEN), 0xFF0000);
        assertEquals(GREY, out[1]);
        int light = (out[5] >> 16) & 0xFF, dark = (out[9] >> 16) & 0xFF;
        assertTrue(light > dark, light + " should be lighter than " + dark);
        assertEquals(0, out[5] & 0xFFFF, "only red");
        assertEquals(0xFF, out[5] >>> 24);
    }

    @Test
    void aPureLayerBlendsToItself() {
        int[] thorium = rod(2, 1, BLACK, BLACK);
        int[] out = FuelRodPixels.blend(new int[][] { null, thorium }, new double[] { 0, 1 }, 24);
        assertArrayEquals(thorium, out);
    }

    @Test
    void halfAndHalfIsHalfwayAndTransparentPixelsAddNoColour() {
        int[] a = new int[] { 0xFF000000, 0xFFFF0000, 0x00000000 };
        int[] b = new int[] { 0xFF646464, 0x00FFFFFF, 0x00000000 };
        int[] out = FuelRodPixels.blend(new int[][] { a, b }, new double[] { 0.5, 0.5 }, 3);
        assertEquals(0xFF323232, out[0]);
        assertEquals(0x80FF0000, out[1]); // half as opaque, but all red
        assertEquals(0, out[2]);
    }

    // See wrinkle (W1) of Note [Custom fuel rod textures]
    @Test
    void alignmentFindsWhereARodIsDrawnAndShiftingBackRestoresIt() {
        int[] uranium = rod(1, 0, GREEN, GREEN);
        int[] thorium = rod(1, 1, BLACK, BLACK); // drawn a pixel lower
        int[] offset = FuelRodPixels.alignment(uranium, thorium, 4);
        assertArrayEquals(new int[] { 0, 1 }, offset);
        int[] aligned = FuelRodPixels.shift(thorium, 4, -offset[0], -offset[1]);
        assertArrayEquals(rod(1, 0, BLACK, BLACK), aligned);
        assertArrayEquals(thorium, FuelRodPixels.shift(aligned, 4, offset[0], offset[1]));
        assertArrayEquals(new int[] { 0, 0 }, FuelRodPixels.alignment(uranium, uranium, 4));
    }

    private static NBTTagCompound rodNbt(int thorium, int u235) {
        Map<ActinideIsotope, Integer> units = new LinkedHashMap<>();
        units.put(ActinideIsotope.THORIUM, thorium);
        units.put(ActinideIsotope.URANIUM_235, u235);
        NBTTagCompound nbt = new NBTTagCompound();
        new CustomFuelMix(units).writeToNBT(nbt);
        return nbt;
    }

    @Test
    void rodsStoreTheirCompositionHash() {
        NBTTagCompound nbt = rodNbt(324, 36);
        NBTTagCompound root = nbt.getCompoundTag(CustomFuelMix.NBT_TAG_ROOT);
        long stored = root.getLong(CustomFuelMix.NBT_HASH);
        assertNotEquals(0L, stored);
        assertEquals(stored, CustomFuelMix.compositionHash(root.getCompoundTag("Units")));
        assertEquals(stored, CustomFuelMix.hashOf(nbt));
        assertEquals(stored, CustomFuelMix.hashOf(rodNbt(324, 36)));
        assertNotEquals(stored, CustomFuelMix.hashOf(rodNbt(288, 72)));
        assertEquals(0L, CustomFuelMix.hashOf(new NBTTagCompound()));
        assertEquals(0L, CustomFuelMix.hashOf(null));
    }

    // See wrinkle (W4) of Note [Custom fuel rod textures]
    @Test
    void rodsMadeBeforeTheHashGetItFromTheirUnits() {
        NBTTagCompound nbt = rodNbt(324, 36);
        long stored = CustomFuelMix.hashOf(nbt);
        nbt.getCompoundTag(CustomFuelMix.NBT_TAG_ROOT)
            .removeTag(CustomFuelMix.NBT_HASH);
        assertEquals(stored, CustomFuelMix.hashOf(nbt));
    }
}

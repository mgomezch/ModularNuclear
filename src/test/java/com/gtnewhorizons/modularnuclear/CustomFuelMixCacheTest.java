package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.nuclear.CustomFuelMix;
import com.gtnewhorizons.modularnuclear.common.nuclear.CustomFuelMix.ActinideIsotope;
import com.gtnewhorizons.modularnuclear.common.util.CustomFuelMixCache;

public class CustomFuelMixCacheTest {

    private static final long HOUR = 60L * 60L * 1000L;

    @BeforeEach
    void clearCache() {
        CustomFuelMixCache.clear();
    }

    private static NBTTagCompound rodNbt(int thorium, int u235) {
        Map<ActinideIsotope, Integer> units = new LinkedHashMap<>();
        if (thorium > 0) units.put(ActinideIsotope.THORIUM, thorium);
        if (u235 > 0) units.put(ActinideIsotope.URANIUM_235, u235);
        NBTTagCompound nbt = new NBTTagCompound();
        new CustomFuelMix(units).writeToNBT(nbt);
        return nbt;
    }

    @Test
    void testSameCompositionSharesOneMix() {
        NBTTagCompound a = rodNbt(324, 36);
        NBTTagCompound b = rodNbt(324, 36);
        // Wear and the derived values the NBT stores are not part of the composition
        b.setInteger("advDmg", 1234);
        b.getCompoundTag(CustomFuelMix.NBT_TAG_ROOT)
            .setInteger("MaxDurability", 1);

        CustomFuelMix mixA = CustomFuelMix.readFromNBT(a);
        assertNotNull(mixA);
        assertSame(mixA, CustomFuelMix.readFromNBT(b));
        assertNotSame(mixA, CustomFuelMix.readFromNBT(rodNbt(288, 72)));
        assertEquals(2, CustomFuelMixCache.size());
    }

    @Test
    void testNoUnitsIsNoMix() {
        assertNull(CustomFuelMix.readFromNBT(null));
        assertNull(CustomFuelMix.readFromNBT(new NBTTagCompound()));
        NBTTagCompound zero = new NBTTagCompound();
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound units = new NBTTagCompound();
        units.setInteger(ActinideIsotope.THORIUM.name(), 0);
        root.setTag("Units", units);
        zero.setTag(CustomFuelMix.NBT_TAG_ROOT, root);
        assertNull(CustomFuelMix.readFromNBT(zero));
    }

    @Test
    void testUnusedCompositionsExpire() {
        long now = System.currentTimeMillis();
        CustomFuelMix kept = CustomFuelMixCache.of(rodNbt(324, 36), now);
        CustomFuelMix stale = CustomFuelMixCache.of(rodNbt(288, 72), now);
        assertEquals(2, CustomFuelMixCache.size());

        // One is looked up half an hour in, the other never again
        assertSame(kept, CustomFuelMixCache.of(rodNbt(324, 36), now + HOUR / 2));
        assertSame(kept, CustomFuelMixCache.of(rodNbt(324, 36), now + HOUR + HOUR / 4));
        assertEquals(1, CustomFuelMixCache.size());

        CustomFuelMix rebuilt = CustomFuelMixCache.of(rodNbt(288, 72), now + HOUR + HOUR / 4);
        assertNotSame(stale, rebuilt);
        assertEquals(stale.defaultDurability, rebuilt.defaultDurability);
    }
}

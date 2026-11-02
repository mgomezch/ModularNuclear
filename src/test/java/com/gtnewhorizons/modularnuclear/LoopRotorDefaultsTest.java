package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.CoolantLoopModel;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.CoolantLoopModel.EnergyHatchTier;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.CoolantLoopModel.RotorMaterial;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.CoolantLoopModel.RotorSize;

/** A loop's pump takes its hatch tier's recommended rotor unless one is named. */
public class LoopRotorDefaultsTest {

    @Test
    void pumpRotorFollowsItsHatchTier() {
        CoolantLoopModel loop = new CoolantLoopModel();
        loop.setHatchTier(EnergyHatchTier.EV);
        assertEquals(RotorMaterial.TERRASTEEL, loop.getRotorMaterial());
        assertEquals(RotorSize.NORMAL, loop.getRotorSize());
        loop.setHatchTier(EnergyHatchTier.UHV);
        assertEquals(RotorMaterial.DURANIUM, loop.getRotorMaterial());
        assertEquals(RotorSize.HUGE, loop.getRotorSize());
        // Huge Duranium: 0.5 + (0.5 + 7.5 + 11) * 0.1, the wiki's 240%
        assertEquals(2.4, CoolantLoopModel.rotorEfficiency(loop.getRotorMaterial(), loop.getRotorSize()), 1e-9);

        loop.setRotorMaterial(RotorMaterial.INFINITY);
        assertEquals(RotorMaterial.INFINITY, loop.getRotorMaterial(), "a named rotor stays");
    }
}

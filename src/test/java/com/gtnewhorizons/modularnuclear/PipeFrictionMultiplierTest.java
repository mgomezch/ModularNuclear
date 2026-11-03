package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.config.data.CoolantPipeData;
import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantFluidProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopSegment;

/** A pipe material's friction multiplier (pipes.yaml) scales its blocks' pressure drop. */
public class PipeFrictionMultiplierTest {

    private static LoopSegment pipe() {
        return new LoopSegment("pipe_0_0_0", 1.0, 0.2, 0.000045, 0.05, 12000.0, 10000.0, 20.0);
    }

    @Test
    void multiplierScalesPressureDropAndIsSaved() {
        LoopSegment plain = pipe();
        LoopSegment smooth = pipe();
        smooth.setFrictionMultiplier(0.1);
        double full = plain.computePressureDrop(5.0, CoolantFluidProperty.WATER);
        assertEquals(full * 0.1, smooth.computePressureDrop(5.0, CoolantFluidProperty.WATER), full * 1e-9);

        LoopSegment restored = LoopSegment.fromStateMap(smooth.toStateMap(), new ArrayList<>());
        assertEquals(0.1, restored.getFrictionMultiplier());
    }

    @Test
    void materialsDefaultToFullFrictionAndReadTheConfig() {
        CoolantPipeData defaults = new CoolantPipeData();
        assertEquals(0.05, defaults.getFrictionMultiplier("Infinity"));
        assertEquals(1.0, defaults.getFrictionMultiplier("unobtainium"));

        Map<String, Object> map = new LinkedHashMap<>();
        Map<String, Object> multipliers = new LinkedHashMap<>();
        multipliers.put("Steel", 0.7);
        map.put("materialFrictionMultipliers", multipliers);
        CoolantPipeData parsed = CoolantPipeData.fromMap(map);
        assertEquals(0.7, parsed.getFrictionMultiplier("steel"));
        assertEquals(1.0, parsed.getFrictionMultiplier("infinity"), "a config lists every material it wants lowered");
    }
}

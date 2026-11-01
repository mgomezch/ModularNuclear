package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.config.CoolantLoopConfigData;
import com.gtnewhorizons.modularnuclear.common.config.data.CoolantPipeData;

/** Which Railcraft tank a coolant loop's pump needs for its pipes (pipes.yaml). */
public class CoolantPipeTankTest {

    private CoolantPipeData pipes;

    @BeforeEach
    void load() {
        CoolantLoopConfigData.loadDefaults();
        pipes = CoolantLoopConfigData.getPipeData();
    }

    @Test
    void testPipesTakeTheirOwnMaterialsTank() {
        assertTrue(pipes.takesTank("Neutronium", "Neutronium"));
        assertTrue(pipes.takesTank("Osmium", "Osmium"));
        assertTrue(pipes.takesTank("CastIron", "Iron"));
        assertFalse(pipes.takesTank("Osmium", "Neutronium"));
        assertFalse(pipes.takesTank("Neutronium", "TungstenSteel"));
    }

    @Test
    void testPipesWithNoRailcraftTankTakeNeutroniumTanks() {
        for (String pipe : new String[] { "DraconiumAwakened", "Infinity" }) {
            assertTrue(CoolantLoopConfigData.isAllowedCoolantPipeMaterial(pipe), pipe);
            assertTrue(pipes.takesTank(pipe, "Neutronium"), pipe);
            assertFalse(pipes.takesTank(pipe, "TungstenSteel"), pipe);
            assertFalse(pipes.takesTank(pipe, pipe), pipe);
        }
    }
}

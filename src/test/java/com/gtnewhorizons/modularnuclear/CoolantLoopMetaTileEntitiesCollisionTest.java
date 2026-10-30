package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.metatileentity.CoolantLoopMetaTileEntities;

import gregtech.api.enums.MetaTileEntityIDs;

public class CoolantLoopMetaTileEntitiesCollisionTest {

    @Test
    public void testNoCollisionWithGregTechAndTecTechMetaTileEntityIDs() {
        Set<Integer> gtIds = new HashSet<>();
        for (MetaTileEntityIDs idEnum : MetaTileEntityIDs.values()) {
            gtIds.add(idEnum.ID);
        }

        Set<Integer> coolantLoopIds = new HashSet<>();
        coolantLoopIds.add(CoolantLoopMetaTileEntities.PUMP_ID);
        coolantLoopIds.add(CoolantLoopMetaTileEntities.PRESSURIZED_HATCH_INPUT_ID);
        coolantLoopIds.add(CoolantLoopMetaTileEntities.PRESSURIZED_HATCH_OUTPUT_ID);
        coolantLoopIds.add(CoolantLoopMetaTileEntities.HEAT_EXCHANGER_ID);
        coolantLoopIds.add(CoolantLoopMetaTileEntities.DEGASSER_ID);
        coolantLoopIds.add(CoolantLoopMetaTileEntities.COOLANT_BATH_ID);
        coolantLoopIds.add(CoolantLoopMetaTileEntities.PRESSURIZED_FLUID_HEATER_ID);

        int totalPipes = CoolantLoopMetaTileEntities.COOLANT_PIPE_MATERIALS.length
            * CoolantLoopMetaTileEntities.PIPE_THICKNESSES.length;
        for (int i = 0; i < totalPipes; i++) {
            coolantLoopIds.add(CoolantLoopMetaTileEntities.COOLANT_PIPE_START_ID + i);
        }

        for (int id : coolantLoopIds) {
            assertFalse(
                gtIds.contains(id),
                "CoolantLoops MTE ID " + id + " collides with a core GT/TecTech MetaTileEntityID enum!");
        }

        // Also verify no overlap with ModularNuclear IDs (32100..32115)
        for (int mnId = 32100; mnId <= 32115; mnId++) {
            assertFalse(coolantLoopIds.contains(mnId), "CoolantLoops MTE ID collides with ModularNuclear ID " + mnId);
        }

        assertTrue(coolantLoopIds.size() >= 39, "Expected at least 39 distinct CoolantLoops MTE IDs");
    }
}

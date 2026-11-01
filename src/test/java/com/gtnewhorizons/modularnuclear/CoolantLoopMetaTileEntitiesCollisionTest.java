package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantPipeSpec;
import com.gtnewhorizons.modularnuclear.common.metatileentity.CoolantLoopMetaTileEntities;
import com.gtnewhorizons.modularnuclear.common.metatileentity.ModMetaTileEntities;

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

        coolantLoopIds.addAll(pipeIds());

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

    /** Every coolant pipe's ID, checking none repeats. */
    private static List<Integer> pipeIds() {
        List<Integer> ids = new ArrayList<>();
        for (int m = 0; m < CoolantLoopMetaTileEntities.COOLANT_PIPE_MATERIALS.length; m++) {
            for (int s = 0; s < CoolantLoopMetaTileEntities.PIPE_THICKNESSES.length; s++) {
                ids.add(CoolantLoopMetaTileEntities.getPipeId(m, s));
            }
        }
        return ids;
    }

    @Test
    public void testEveryMetaTileEntityIdIsDistinct() {
        // Single machines and hatches of both halves of the mod, then the per-material pipes and passage hatches
        List<Integer> ids = new ArrayList<>(
            Arrays.asList(
                ModMetaTileEntities.ID_NUCLEAR_BUS,
                ModMetaTileEntities.ID_NUCLEAR_CONTROL,
                ModMetaTileEntities.ID_NUCLEAR_CONTROL_ROD,
                ModMetaTileEntities.ID_NUCLEAR_HIGH_PRESSURE,
                ModMetaTileEntities.ID_NUCLEAR_HIGH_PRESSURE_INPUT,
                ModMetaTileEntities.ID_NUCLEAR_HIGH_PRESSURE_OUTPUT,
                ModMetaTileEntities.ID_NUCLEAR_REACTOR,
                ModMetaTileEntities.ID_ACTINIDE_COMPOUNDER,
                ModMetaTileEntities.ID_NUCLEAR_ADVANCED_CONTROL_ROD,
                CoolantLoopMetaTileEntities.PUMP_ID,
                CoolantLoopMetaTileEntities.PRESSURIZED_HATCH_INPUT_ID,
                CoolantLoopMetaTileEntities.HEAT_EXCHANGER_ID,
                CoolantLoopMetaTileEntities.DEGASSER_ID,
                CoolantLoopMetaTileEntities.COOLANT_BATH_ID,
                CoolantLoopMetaTileEntities.PRESSURIZED_HATCH_OUTPUT_ID,
                CoolantLoopMetaTileEntities.PRESSURIZED_FLUID_HEATER_ID));
        for (int i = 0; i < 9; i++) ids.add(ModMetaTileEntities.ID_NUCLEAR_HATCH_BASE + i);
        ids.addAll(pipeIds());
        for (int m = 0; m < CoolantPipeSpec.MATERIAL_NAMES.length; m++) {
            ids.add(ModMetaTileEntities.highPressureInputId(m));
            ids.add(ModMetaTileEntities.highPressureOutputId(m));
        }
        Set<Integer> seen = new HashSet<>();
        for (int id : ids) {
            assertTrue(seen.add(id), "MTE ID " + id + " is used twice");
        }
    }

    @Test
    public void testExistingPipeAndHatchIdsAreUnchanged() {
        // Worlds store pipes and hatches by ID: materials added later must not move the earlier ones
        assertEquals(32125, CoolantLoopMetaTileEntities.getPipeId(0, 0));
        assertEquals(32159, CoolantLoopMetaTileEntities.getPipeId(6, 4));
        assertEquals(32174, ModMetaTileEntities.highPressureOutputId(6));
        assertEquals(32176, CoolantLoopMetaTileEntities.getPipeId(7, 0));
        assertEquals(32189, ModMetaTileEntities.highPressureOutputId(8));
    }
}

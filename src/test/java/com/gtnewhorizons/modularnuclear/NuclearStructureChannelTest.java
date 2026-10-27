package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor;
import com.gtnewhorizon.structurelib.structure.IChannelDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizons.modularnuclear.common.metatileentity.NuclearStructureChannels;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.structure.NuclearReactorStructure;

import gregtech.common.misc.GTStructureChannels;

public class NuclearStructureChannelTest {

    @BeforeAll
    public static void setUp() {
        TestEnvironmentHelper.init();
        try {
            com.gtnewhorizons.modularnuclear.common.fluid.ModFluids.init();
            if (com.gtnewhorizons.modularnuclear.common.block.ModBlocks.nuclearCasing == null) {
                com.gtnewhorizons.modularnuclear.common.block.ModBlocks.init();
            }
        } catch (Throwable t) {
            if (com.gtnewhorizons.modularnuclear.common.block.ModBlocks.nuclearCasing == null) {
                com.gtnewhorizons.modularnuclear.common.block.ModBlocks.nuclearCasing = org.mockito.Mockito
                    .mock(com.gtnewhorizons.modularnuclear.common.block.BlockNuclearCasing.class);
            }
        }
    }

    @Test
    public void testItemPipeChannelRange() {
        IStructureDefinition<MTENuclearReactor> def = NuclearReactorStructure.getStructureDefinition();
        assertNotNull(def);

        boolean foundItemPipe = false;
        for (IChannelDefinition ch : def.getExplicitChannelDefinitions()) {
            if (GTStructureChannels.ITEM_PIPE_CASING.get()
                .equals(ch.getChannel())) {
                foundItemPipe = true;
                assertEquals(2, ch.getMinimumValue(), "item_pipe minimum channel value must be 2");
                assertEquals(8, ch.getMaximumValue(), "item_pipe maximum channel value must be 8");
            }
        }
        assertTrue(foundItemPipe, "Explicit channel definition for item_pipe must be present");
    }

    @Test
    public void testNuclearHatchChannelValues() {
        // Trigger with null
        assertEquals(0, NuclearStructureChannels.NUCLEAR_HATCH.getValueClamped(null, 0, 10));

        // Trigger with no channel data (unset / position 0)
        ItemStack plainTrigger = new ItemStack(Items.feather, 1, 0);
        assertEquals(0, NuclearStructureChannels.NUCLEAR_HATCH.getValueClamped(plainTrigger, 0, 10));

        // Trigger with channel data explicitly unset (slider value 0)
        ItemStack trigger0 = new ItemStack(Items.feather, 1, 0);
        ChannelDataAccessor.setChannelData(trigger0, NuclearStructureChannels.NUCLEAR_HATCH.get(), 1);
        ChannelDataAccessor.unsetChannelData(trigger0, NuclearStructureChannels.NUCLEAR_HATCH.get());
        assertEquals(0, NuclearStructureChannels.NUCLEAR_HATCH.getValueClamped(trigger0, 0, 10));

        // Trigger with channel data 5 (IV hatch)
        ItemStack trigger5 = new ItemStack(Items.feather, 1, 0);
        ChannelDataAccessor.setChannelData(trigger5, NuclearStructureChannels.NUCLEAR_HATCH.get(), 5);
        assertEquals(5, NuclearStructureChannels.NUCLEAR_HATCH.getValueClamped(trigger5, 0, 10));

        // Trigger with channel data 10 (HP input hatch)
        ItemStack trigger10 = new ItemStack(Items.feather, 1, 0);
        ChannelDataAccessor.setChannelData(trigger10, NuclearStructureChannels.NUCLEAR_HATCH.get(), 10);
        assertEquals(10, NuclearStructureChannels.NUCLEAR_HATCH.getValueClamped(trigger10, 0, 10));

        // Trigger with channel data 15 (clamped to max 10)
        ItemStack trigger15 = new ItemStack(Items.feather, 1, 0);
        ChannelDataAccessor.setChannelData(trigger15, NuclearStructureChannels.NUCLEAR_HATCH.get(), 15);
        assertEquals(10, NuclearStructureChannels.NUCLEAR_HATCH.getValueClamped(trigger15, 0, 10));
    }

    @Test
    public void testRegisterAsIndicatorDoesNotThrow() {
        ItemStack testStack = new ItemStack(Items.feather, 1, 0);
        assertDoesNotThrow(() -> {
            NuclearStructureChannels.NUCLEAR_HATCH.registerAsIndicator(testStack, 0);
            NuclearStructureChannels.NUCLEAR_HATCH.registerAsIndicator(testStack, 1);
            NuclearStructureChannels.NUCLEAR_HATCH.registerAsIndicator(testStack, 10);
        });
    }

    @Test
    public void testNuclearHatchStackMapping() {
        boolean prevPostload = gregtech.api.GregTechAPI.sPostloadStarted;
        boolean prevPreload = gregtech.api.GregTechAPI.sPreloadStarted;
        try {
            gregtech.api.GregTechAPI.sPostloadStarted = false;
            gregtech.api.GregTechAPI.sPreloadStarted = true;
            com.gtnewhorizons.modularnuclear.common.metatileentity.ModMetaTileEntities.init();
        } catch (Throwable ignored) {} finally {
            gregtech.api.GregTechAPI.sPostloadStarted = prevPostload;
            gregtech.api.GregTechAPI.sPreloadStarted = prevPreload;
        }

        if (com.gtnewhorizons.modularnuclear.common.metatileentity.ModMetaTileEntities.nuclearBus != null) {
            ItemStack stack0 = NuclearReactorStructure.getNuclearHatchStack(0);
            assertNotNull(stack0, "Nuclear hatch stack for tier 0 must not be null");

            ItemStack stack1 = NuclearReactorStructure.getNuclearHatchStack(1);
            assertNotNull(stack1, "Nuclear hatch stack for tier 1 must not be null");

            ItemStack stack9 = NuclearReactorStructure.getNuclearHatchStack(9);
            assertNotNull(stack9, "Nuclear hatch stack for tier 9 must not be null");

            // Tier 10 hints the first tiered high-pressure input hatch (registered unless another test already took
            // a legacy hatch's id, which stops registration before the tiered hatches)
            ItemStack firstInput = com.gtnewhorizons.modularnuclear.common.metatileentity.ModMetaTileEntities.nuclearHighPressureInputHatches[0];
            if (firstInput != null) {
                ItemStack stack10 = NuclearReactorStructure.getNuclearHatchStack(10);
                assertNotNull(stack10, "Nuclear hatch stack for tier 10 must not be null");
                assertTrue(
                    ItemStack.areItemStacksEqual(firstInput, stack10),
                    "tier 10 is the first tiered input hatch");
            }

            assertNull(NuclearReactorStructure.getNuclearHatchStack(-1), "Negative tier should return null");
            assertNull(NuclearReactorStructure.getNuclearHatchStack(11), "Tier > 10 should return null");
        }
    }
}

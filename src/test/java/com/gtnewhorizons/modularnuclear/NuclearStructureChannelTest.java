package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor;
import com.gtnewhorizon.structurelib.structure.IChannelDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizons.modularnuclear.common.block.BlockNuclearCasing;
import com.gtnewhorizons.modularnuclear.common.block.ModBlocks;
import com.gtnewhorizons.modularnuclear.common.fluid.ModFluids;
import com.gtnewhorizons.modularnuclear.common.metatileentity.ModMetaTileEntities;
import com.gtnewhorizons.modularnuclear.common.metatileentity.NuclearStructureChannels;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.structure.NuclearReactorStructure;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;

import gregtech.common.misc.GTStructureChannels;

public class NuclearStructureChannelTest {

    @BeforeAll
    public static void setUp() {
        TestEnvironmentHelper.init();
        try {
            ModFluids.init();
            if (ModBlocks.nuclearCasing == null) {
                ModBlocks.init();
            }
        } catch (Throwable t) {
            if (ModBlocks.nuclearCasing == null) {
                ModBlocks.nuclearCasing = org.mockito.Mockito.mock(BlockNuclearCasing.class);
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
            ModMetaTileEntities.init();
        } catch (Throwable ignored) {} finally {
            gregtech.api.GregTechAPI.sPostloadStarted = prevPostload;
            gregtech.api.GregTechAPI.sPreloadStarted = prevPreload;
        }

        if (ModMetaTileEntities.nuclearBus != null) {
            ItemStack stack0 = NuclearReactorStructure.getNuclearHatchStack(0);
            assertNotNull(stack0, "Nuclear hatch stack for tier 0 must not be null");

            ItemStack stack1 = NuclearReactorStructure.getNuclearHatchStack(1);
            assertNotNull(stack1, "Nuclear hatch stack for tier 1 must not be null");

            ItemStack stack9 = NuclearReactorStructure.getNuclearHatchStack(9);
            assertNotNull(stack9, "Nuclear hatch stack for tier 9 must not be null");

            // Tier 10 hints the first tiered high-pressure input hatch (registered unless another test already took
            // a legacy hatch's id, which stops registration before the tiered hatches)
            ItemStack firstInput = ModMetaTileEntities.nuclearHighPressureInputHatches[0];
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

    @Test
    public void testReactorTierPipeCasingRequirements() {
        assertEquals(
            NuclearSimulationEngine.PIPE_TIER_ELECTRUM,
            MTENuclearReactor.getMinRequiredPipeTierForReactorTier(1));
        assertEquals(
            NuclearSimulationEngine.PIPE_TIER_PLATINUM,
            MTENuclearReactor.getMinRequiredPipeTierForReactorTier(2));
        assertEquals(
            NuclearSimulationEngine.PIPE_TIER_OSMIUM,
            MTENuclearReactor.getMinRequiredPipeTierForReactorTier(3));

        MTENuclearReactor reactor = new MTENuclearReactor("test_reactor");
        java.util.List<gregtech.api.structure.error.StructureError> errors = new java.util.ArrayList<>();

        // Tier 1 (gridSize = 5): requires Electrum (0) or higher
        reactor.gridSize = 5;
        reactor.mPipeTier = -1; // Tin or Brass
        errors.clear();
        reactor.validatePipeTierForStructureTier(errors);
        assertEquals(1, errors.size(), "Tier 1 must reject Tin/Brass item pipe casings");

        for (int pipeTier = 0; pipeTier <= 5; pipeTier++) {
            reactor.mPipeTier = pipeTier;
            errors.clear();
            reactor.validatePipeTierForStructureTier(errors);
            assertTrue(errors.isEmpty(), "Tier 1 must accept pipe tier " + pipeTier);
        }

        // Tier 2 (gridSize = 9): requires Platinum (1) or higher
        reactor.gridSize = 9;
        for (int pipeTier = -1; pipeTier <= 0; pipeTier++) {
            reactor.mPipeTier = pipeTier;
            errors.clear();
            reactor.validatePipeTierForStructureTier(errors);
            assertEquals(1, errors.size(), "Tier 2 must reject pipe tier " + pipeTier);
        }
        for (int pipeTier = 1; pipeTier <= 5; pipeTier++) {
            reactor.mPipeTier = pipeTier;
            errors.clear();
            reactor.validatePipeTierForStructureTier(errors);
            assertTrue(errors.isEmpty(), "Tier 2 must accept pipe tier " + pipeTier);
        }

        // Tier 3 (gridSize = 13): requires Osmium (2) or higher
        reactor.gridSize = 13;
        for (int pipeTier = -1; pipeTier <= 1; pipeTier++) {
            reactor.mPipeTier = pipeTier;
            errors.clear();
            reactor.validatePipeTierForStructureTier(errors);
            assertEquals(1, errors.size(), "Tier 3 must reject pipe tier " + pipeTier);
        }
        for (int pipeTier = 2; pipeTier <= 5; pipeTier++) {
            reactor.mPipeTier = pipeTier;
            errors.clear();
            reactor.validatePipeTierForStructureTier(errors);
            assertTrue(errors.isEmpty(), "Tier 3 must accept pipe tier " + pipeTier);
        }
    }

    @Test
    public void testPrepareConstructTriggerClampsMinPipeCasingTier() {
        String channel = GTStructureChannels.ITEM_PIPE_CASING.get();

        // Unset channel defaults to Electrum (3) for Tier 1, Platinum (4) for Tier 2, Osmium (5) for Tier 3
        ItemStack t1 = MTENuclearReactor.prepareConstructTrigger(new ItemStack(Items.feather, 1, 0), 1);
        assertEquals(3, ChannelDataAccessor.getChannelData(t1, channel));

        ItemStack t2 = MTENuclearReactor.prepareConstructTrigger(new ItemStack(Items.feather, 2, 0), 2);
        assertEquals(4, ChannelDataAccessor.getChannelData(t2, channel));

        ItemStack t3 = MTENuclearReactor.prepareConstructTrigger(new ItemStack(Items.feather, 3, 0), 3);
        assertEquals(5, ChannelDataAccessor.getChannelData(t3, channel));

        // Low explicit channel (e.g. Electrum = 3) on Tier 2 or Tier 3 is raised to minimum required
        ItemStack lowForT2 = new ItemStack(Items.feather, 2, 0);
        ChannelDataAccessor.setChannelData(lowForT2, channel, 3);
        assertEquals(
            4,
            ChannelDataAccessor.getChannelData(MTENuclearReactor.prepareConstructTrigger(lowForT2, 2), channel));

        ItemStack lowForT3 = new ItemStack(Items.feather, 3, 0);
        ChannelDataAccessor.setChannelData(lowForT3, channel, 4);
        assertEquals(
            5,
            ChannelDataAccessor.getChannelData(MTENuclearReactor.prepareConstructTrigger(lowForT3, 3), channel));

        // Higher explicit channel (e.g. Quantium = 6) is preserved across all tiers
        for (int reactorTier = 1; reactorTier <= 3; reactorTier++) {
            ItemStack high = new ItemStack(Items.feather, reactorTier, 0);
            ChannelDataAccessor.setChannelData(high, channel, 6);
            assertEquals(
                6,
                ChannelDataAccessor
                    .getChannelData(MTENuclearReactor.prepareConstructTrigger(high, reactorTier), channel));
        }
    }
}

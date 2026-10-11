package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.metatileentity.ModMetaTileEntities;
import com.gtnewhorizons.modularnuclear.common.nei.NEIFuelStatsHandler;

import codechicken.nei.recipe.HandlerInfo;

public class NEIFuelStatsHandlerTest {

    @BeforeAll
    public static void setUp() {
        try {
            Bootstrap.func_151354_b();
        } catch (Throwable ignored) {}

        if (ModMetaTileEntities.reactor == null) {
            ModMetaTileEntities.reactor = new ItemStack(Items.diamond, 1, 0);
        }
    }

    @Test
    public void testControllerStackAndHandlerId() {
        ItemStack controller = NEIFuelStatsHandler.getControllerStack();
        assertNotNull(controller, "Controller stack should not be null");

        NEIFuelStatsHandler handler = new NEIFuelStatsHandler();
        assertEquals(NEIFuelStatsHandler.OVERLAY_ID, handler.getHandlerId());
        assertEquals(NEIFuelStatsHandler.OVERLAY_ID, handler.getOverlayIdentifier());
    }

    @Test
    public void testHandlerInfoBuilderWithControllerStack() {
        ItemStack controller = NEIFuelStatsHandler.getControllerStack();
        assertNotNull(controller);

        HandlerInfo info = new HandlerInfo.Builder(
            NEIFuelStatsHandler.OVERLAY_ID,
            "Modular Nuclear",
            ModularNuclear.MODID).setDisplayStack(controller)
                .setMaxRecipesPerPage(1)
                .build();

        assertNotNull(info);
        assertEquals(NEIFuelStatsHandler.OVERLAY_ID, info.getHandlerName());
        assertNotNull(info.getItemStack(), "Display stack on HandlerInfo must not be null");
        assertEquals(
            controller.getItem(),
            info.getItemStack()
                .getItem());
    }

    @Test
    public void testControllerRecipeLookup() {
        NEIFuelStatsHandler handler = new NEIFuelStatsHandler();
        ItemStack controller = NEIFuelStatsHandler.getControllerStack();

        handler.loadCraftingRecipes(controller);
        assertFalse(handler.arecipes.isEmpty(), "Clicking 'R' on controller should load fuel recipes");

        handler.arecipes.clear();
        handler.loadUsageRecipes(controller);
        assertFalse(handler.arecipes.isEmpty(), "Clicking 'U' on controller should load fuel recipes");
    }
}

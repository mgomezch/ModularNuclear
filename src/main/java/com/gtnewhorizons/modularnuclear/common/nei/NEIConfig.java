package com.gtnewhorizons.modularnuclear.common.nei;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.modularnuclear.ModularNuclear;
import com.gtnewhorizons.modularnuclear.common.gui.FloatingPanelNEIHandler;

import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;
import codechicken.nei.event.NEIRegisterHandlerInfosEvent;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class NEIConfig implements IConfigureNEI {

    @Override
    public void loadConfig() {
        FloatingPanelNEIHandler.register();

        NEINeutronInteractionHandler neutronHandler = new NEINeutronInteractionHandler();
        API.registerRecipeHandler(neutronHandler);
        API.registerUsageHandler(neutronHandler);

        NEICompactLoopHandler compactHandler = new NEICompactLoopHandler();
        API.registerRecipeHandler(compactHandler);
        API.registerUsageHandler(compactHandler);
        ItemStack compactPump = NEICompactLoopHandler.getDisplayStack();
        if (compactPump != null) {
            for (String id : compactHandlerIds()) GuiRecipeTab.handlerMap.put(id, compactHandlerInfo(id, compactPump));
        }

        NEIFuelStatsHandler fuelHandler = new NEIFuelStatsHandler();
        API.registerRecipeHandler(fuelHandler);
        API.registerUsageHandler(fuelHandler);

        ItemStack controller = NEIFuelStatsHandler.getControllerStack();
        if (controller != null) {
            API.addRecipeCatalyst(controller, NEIFuelStatsHandler.OVERLAY_ID);
            API.addRecipeCatalyst(controller, fuelHandler.getRecipeName());
            API.addRecipeCatalyst(
                controller,
                fuelHandler.getClass()
                    .getName());

            HandlerInfo fuelInfo = new HandlerInfo.Builder(
                NEIFuelStatsHandler.OVERLAY_ID,
                "Modular Nuclear",
                ModularNuclear.MODID).setDisplayStack(controller)
                    .setMaxRecipesPerPage(1)
                    .setHeight(166)
                    .setShowOverlayButton(false)
                    .build();
            GuiRecipeTab.handlerMap.put(NEIFuelStatsHandler.OVERLAY_ID, fuelInfo);
            GuiRecipeTab.handlerMap.put(fuelHandler.getRecipeName(), fuelInfo);
            GuiRecipeTab.handlerMap.put(
                fuelHandler.getClass()
                    .getName(),
                fuelInfo);

            API.addRecipeCatalyst(controller, NEINeutronInteractionHandler.OVERLAY_ID);
            API.addRecipeCatalyst(controller, neutronHandler.getRecipeName());
            API.addRecipeCatalyst(
                controller,
                neutronHandler.getClass()
                    .getName());

            HandlerInfo compInfo = new HandlerInfo.Builder(
                NEINeutronInteractionHandler.OVERLAY_ID,
                "Modular Nuclear",
                ModularNuclear.MODID).setDisplayStack(controller)
                    .setMaxRecipesPerPage(1)
                    .setHeight(304)
                    .setShowOverlayButton(false)
                    .build();
            GuiRecipeTab.handlerMap.put(NEINeutronInteractionHandler.OVERLAY_ID, compInfo);
            GuiRecipeTab.handlerMap.put(neutronHandler.getRecipeName(), compInfo);
            GuiRecipeTab.handlerMap.put(
                neutronHandler.getClass()
                    .getName(),
                compInfo);
        }
    }

    /** The names NEI may look the compact machines' handler up by: its id, its class and its tab's name. */
    private static String[] compactHandlerIds() {
        return new String[] { NEICompactLoopHandler.OVERLAY_ID, NEICompactLoopHandler.class.getName(),
            new NEICompactLoopHandler().getRecipeName() };
    }

    private static HandlerInfo compactHandlerInfo(String id, ItemStack display) {
        return new HandlerInfo.Builder(id, "Modular Nuclear", ModularNuclear.MODID).setDisplayStack(display)
            .setMaxRecipesPerPage(1)
            .setHeight(NEICompactLoopHandler.HEIGHT)
            .setShowOverlayButton(false)
            .build();
    }

    @SubscribeEvent
    public void registerHandlerInfo(NEIRegisterHandlerInfosEvent event) {
        ItemStack compactPump = NEICompactLoopHandler.getDisplayStack();
        if (compactPump != null) {
            for (String id : compactHandlerIds()) event.registerHandlerInfo(compactHandlerInfo(id, compactPump));
        }
        ItemStack controller = NEIFuelStatsHandler.getControllerStack();
        if (controller != null) {
            event.registerHandlerInfo(
                new HandlerInfo.Builder(NEIFuelStatsHandler.OVERLAY_ID, "Modular Nuclear", ModularNuclear.MODID)
                    .setDisplayStack(controller)
                    .setMaxRecipesPerPage(1)
                    .setHeight(166)
                    .setShowOverlayButton(false)
                    .build());
            event.registerHandlerInfo(
                new HandlerInfo.Builder(NEIFuelStatsHandler.class.getName(), "Modular Nuclear", ModularNuclear.MODID)
                    .setDisplayStack(controller)
                    .setMaxRecipesPerPage(1)
                    .setHeight(166)
                    .setShowOverlayButton(false)
                    .build());
            event.registerHandlerInfo(
                new HandlerInfo.Builder(
                    new NEIFuelStatsHandler().getRecipeName(),
                    "Modular Nuclear",
                    ModularNuclear.MODID).setDisplayStack(controller)
                        .setMaxRecipesPerPage(1)
                        .setHeight(166)
                        .setShowOverlayButton(false)
                        .build());

            event.registerHandlerInfo(
                new HandlerInfo.Builder(
                    NEINeutronInteractionHandler.OVERLAY_ID,
                    "Modular Nuclear",
                    ModularNuclear.MODID).setDisplayStack(controller)
                        .setMaxRecipesPerPage(1)
                        .setHeight(304)
                        .setShowOverlayButton(false)
                        .build());
            event.registerHandlerInfo(
                new HandlerInfo.Builder(
                    NEINeutronInteractionHandler.class.getName(),
                    "Modular Nuclear",
                    ModularNuclear.MODID).setDisplayStack(controller)
                        .setMaxRecipesPerPage(1)
                        .setHeight(304)
                        .setShowOverlayButton(false)
                        .build());
            event.registerHandlerInfo(
                new HandlerInfo.Builder(
                    new NEINeutronInteractionHandler().getRecipeName(),
                    "Modular Nuclear",
                    ModularNuclear.MODID).setDisplayStack(controller)
                        .setMaxRecipesPerPage(1)
                        .setHeight(304)
                        .setShowOverlayButton(false)
                        .build());
        }
    }

    @Override
    public String getName() {
        return "Modular Nuclear NEI Plugin";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }
}

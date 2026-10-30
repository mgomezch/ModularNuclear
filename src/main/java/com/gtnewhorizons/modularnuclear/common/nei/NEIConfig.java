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

    @SubscribeEvent
    public void registerHandlerInfo(NEIRegisterHandlerInfosEvent event) {
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

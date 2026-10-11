package com.gtnewhorizons.modularnuclear.common.nei;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.modularnuclear.ModularNuclear;

import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;
import codechicken.nei.event.NEIRegisterHandlerInfosEvent;
import codechicken.nei.recipe.GuiRecipeTab;
import codechicken.nei.recipe.HandlerInfo;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class NEIConfig implements IConfigureNEI {

    @Override
    public void loadConfig() {
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

            HandlerInfo info = new HandlerInfo.Builder(
                NEIFuelStatsHandler.OVERLAY_ID,
                "Modular Nuclear",
                ModularNuclear.MODID).setDisplayStack(controller)
                    .setMaxRecipesPerPage(1)
                    .build();
            GuiRecipeTab.handlerMap.put(NEIFuelStatsHandler.OVERLAY_ID, info);
            GuiRecipeTab.handlerMap.put(fuelHandler.getRecipeName(), info);
            GuiRecipeTab.handlerMap.put(
                fuelHandler.getClass()
                    .getName(),
                info);
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
                    .build());
            event.registerHandlerInfo(
                new HandlerInfo.Builder(NEIFuelStatsHandler.class.getName(), "Modular Nuclear", ModularNuclear.MODID)
                    .setDisplayStack(controller)
                    .setMaxRecipesPerPage(1)
                    .build());
            event.registerHandlerInfo(
                new HandlerInfo.Builder(
                    new NEIFuelStatsHandler().getRecipeName(),
                    "Modular Nuclear",
                    ModularNuclear.MODID).setDisplayStack(controller)
                        .setMaxRecipesPerPage(1)
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

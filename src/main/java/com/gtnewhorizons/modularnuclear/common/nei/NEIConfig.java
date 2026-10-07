package com.gtnewhorizons.modularnuclear.common.nei;

import codechicken.nei.api.API;
import codechicken.nei.api.IConfigureNEI;

public class NEIConfig implements IConfigureNEI {

    @Override
    public void loadConfig() {
        NEINeutronInteractionHandler neutronHandler = new NEINeutronInteractionHandler();
        API.registerRecipeHandler(neutronHandler);
        API.registerUsageHandler(neutronHandler);

        NEIFuelStatsHandler fuelHandler = new NEIFuelStatsHandler();
        API.registerRecipeHandler(fuelHandler);
        API.registerUsageHandler(fuelHandler);
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

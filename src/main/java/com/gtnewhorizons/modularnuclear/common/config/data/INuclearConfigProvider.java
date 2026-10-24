package com.gtnewhorizons.modularnuclear.common.config.data;

import java.util.List;

/**
 * Interface decoupling the core nuclear simulation engine and WebAssembly compilation
 * from the Minecraft and Forge configuration registry.
 */
public interface INuclearConfigProvider {

    TierData getTier(int tier);

    FuelData findFuel(String name);

    ComponentData.CoolantFluidData findCoolantFluid(String name);

    boolean matchesFluid(String fluidName, List<String> allowed);
}

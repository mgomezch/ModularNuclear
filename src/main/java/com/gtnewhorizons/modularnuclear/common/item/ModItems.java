package com.gtnewhorizons.modularnuclear.common.item;

import cpw.mods.fml.common.registry.GameRegistry;

public class ModItems {

    public static ItemRadiovoltaicPlate radiovoltaicPlateHV;
    public static ItemRadiovoltaicPlate radiovoltaicPlateEV;

    public static void init() {
        radiovoltaicPlateHV = new ItemRadiovoltaicPlate("modularnuclear.radiovoltaic.plate.hv", 1);
        GameRegistry.registerItem(radiovoltaicPlateHV, "radiovoltaic_plate_hv");

        radiovoltaicPlateEV = new ItemRadiovoltaicPlate("modularnuclear.radiovoltaic.plate.ev", 2);
        GameRegistry.registerItem(radiovoltaicPlateEV, "radiovoltaic_plate_ev");
    }
}

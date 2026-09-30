package com.gtnewhorizons.modularnuclear.common.item;

import cpw.mods.fml.common.registry.GameRegistry;

public class ModItems {

    public static ItemBetavoltaicPlate betavoltaicPlateHV;
    public static ItemBetavoltaicPlate betavoltaicPlateEV;

    public static void init() {
        betavoltaicPlateHV = new ItemBetavoltaicPlate("modularnuclear.betavoltaic.plate.hv", 1);
        GameRegistry.registerItem(betavoltaicPlateHV, "betavoltaic_plate_hv");

        betavoltaicPlateEV = new ItemBetavoltaicPlate("modularnuclear.betavoltaic.plate.ev", 2);
        GameRegistry.registerItem(betavoltaicPlateEV, "betavoltaic_plate_ev");
    }
}

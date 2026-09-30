package com.gtnewhorizons.modularnuclear.common.item;

import cpw.mods.fml.common.registry.GameRegistry;

public class ModItems {

    public static ItemBetavoltaicPlate betavoltaicPlateHV;
    public static ItemBetavoltaicPlate betavoltaicPlateEV;

    public static void init() {
        betavoltaicPlateHV = new ItemBetavoltaicPlate(
            "modularnuclear.betavoltaic.plate.hv",
            "Betavoltaic Plate (HV)",
            1);
        betavoltaicPlateHV.setTextureName("modularnuclear:gt.betavoltaic.plate.hv");
        GameRegistry.registerItem(betavoltaicPlateHV, "betavoltaic_plate_hv");

        betavoltaicPlateEV = new ItemBetavoltaicPlate(
            "modularnuclear.betavoltaic.plate.ev",
            "Betavoltaic Plate (EV)",
            2);
        betavoltaicPlateEV.setTextureName("modularnuclear:gt.betavoltaic.plate.ev");
        GameRegistry.registerItem(betavoltaicPlateEV, "betavoltaic_plate_ev");
    }
}

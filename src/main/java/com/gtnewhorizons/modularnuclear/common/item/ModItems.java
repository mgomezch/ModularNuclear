package com.gtnewhorizons.modularnuclear.common.item;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.gtnewhorizons.modularnuclear.common.fluid.ModFluids;

import cpw.mods.fml.common.registry.GameRegistry;

public class ModItems {

    public static ItemRadiovoltaicPlate radiovoltaicPlateHV;
    public static ItemRadiovoltaicPlate radiovoltaicPlateEV;

    public static ItemNuclearFluidCell cellHeavyWater;
    public static ItemNuclearFluidCell cellHighPressureDistilledWater;
    public static ItemNuclearFluidCell cellHighPressureHeavyWater;

    public static void init() {
        radiovoltaicPlateHV = new ItemRadiovoltaicPlate("modularnuclear.radiovoltaic.plate.hv", 1);
        GameRegistry.registerItem(radiovoltaicPlateHV, "radiovoltaic_plate_hv");

        radiovoltaicPlateEV = new ItemRadiovoltaicPlate("modularnuclear.radiovoltaic.plate.ev", 2);
        GameRegistry.registerItem(radiovoltaicPlateEV, "radiovoltaic_plate_ev");

        cellHeavyWater = new ItemNuclearFluidCell("cellHeavyWater", ModFluids.fluidHeavyWater);
        GameRegistry.registerItem(cellHeavyWater, "cellHeavyWater");
        OreDictionary.registerOre("cellHeavyWater", new ItemStack(cellHeavyWater));

        cellHighPressureDistilledWater = new ItemNuclearFluidCell(
            "cellHighPressureDistilledWater",
            ModFluids.fluidHighPressureDistilledWater);
        GameRegistry.registerItem(cellHighPressureDistilledWater, "cellHighPressureDistilledWater");
        OreDictionary.registerOre("cellHighPressureDistilledWater", new ItemStack(cellHighPressureDistilledWater));

        cellHighPressureHeavyWater = new ItemNuclearFluidCell(
            "cellHighPressureHeavyWater",
            ModFluids.fluidHighPressureHeavyWater);
        GameRegistry.registerItem(cellHighPressureHeavyWater, "cellHighPressureHeavyWater");
        OreDictionary.registerOre("cellHighPressureHeavyWater", new ItemStack(cellHighPressureHeavyWater));
    }
}

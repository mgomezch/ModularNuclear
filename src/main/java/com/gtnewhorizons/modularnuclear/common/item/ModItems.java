package com.gtnewhorizons.modularnuclear.common.item;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.gtnewhorizons.modularnuclear.common.fluid.ModFluids;

import cpw.mods.fml.common.registry.GameRegistry;

public class ModItems {

    public static ItemRadiovoltaicPlate radiovoltaicPlateHV;
    public static ItemRadiovoltaicPlate radiovoltaicPlateEV;

    public static ItemNuclearFluidCell cellHeavyWater;

    public static ItemCustomFuelRod rodCustomFuel4;
    public static ItemCustomDepletedFuelRod rodCustomDepletedFuel4;

    public static void init() {
        radiovoltaicPlateHV = new ItemRadiovoltaicPlate("modularnuclear.radiovoltaic.plate.hv", 1);
        GameRegistry.registerItem(radiovoltaicPlateHV, "radiovoltaic_plate_hv");

        radiovoltaicPlateEV = new ItemRadiovoltaicPlate("modularnuclear.radiovoltaic.plate.ev", 2);
        GameRegistry.registerItem(radiovoltaicPlateEV, "radiovoltaic_plate_ev");

        cellHeavyWater = new ItemNuclearFluidCell("cellHeavyWater", ModFluids.fluidHeavyWater);
        GameRegistry.registerItem(cellHeavyWater, "cellHeavyWater");
        OreDictionary.registerOre("cellHeavyWater", new ItemStack(cellHeavyWater));

        rodCustomFuel4 = new ItemCustomFuelRod();
        OreDictionary.registerOre("fuelRodCustom4", new ItemStack(rodCustomFuel4));

        rodCustomDepletedFuel4 = new ItemCustomDepletedFuelRod();
        OreDictionary.registerOre("depletedFuelRodCustom4", new ItemStack(rodCustomDepletedFuel4));
    }
}

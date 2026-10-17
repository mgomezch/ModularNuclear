package com.gtnewhorizons.modularnuclear.common.item;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.gtnewhorizons.modularnuclear.common.fluid.ModFluids;

import cpw.mods.fml.common.registry.GameRegistry;

public class ModItems {

    public static ItemRadiovoltaicPlate radiovoltaicPlateHV;
    public static ItemRadiovoltaicPlate radiovoltaicPlateEV;

    public static ItemNuclearFluidCell cellHeavyWater;

    public static ItemCustomFuelRod rodCustomFuel1;
    public static ItemCustomDepletedFuelRod rodCustomDepletedFuel1;

    public static ItemCustomFuelRod rodCustomFuel2;
    public static ItemCustomDepletedFuelRod rodCustomDepletedFuel2;

    public static ItemCustomFuelRod rodCustomFuel4;
    public static ItemCustomDepletedFuelRod rodCustomDepletedFuel4;

    private static boolean initialized = false;

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        radiovoltaicPlateHV = new ItemRadiovoltaicPlate("modularnuclear.radiovoltaic.plate.hv", 1);
        GameRegistry.registerItem(radiovoltaicPlateHV, "radiovoltaic_plate_hv");

        radiovoltaicPlateEV = new ItemRadiovoltaicPlate("modularnuclear.radiovoltaic.plate.ev", 2);
        GameRegistry.registerItem(radiovoltaicPlateEV, "radiovoltaic_plate_ev");

        cellHeavyWater = new ItemNuclearFluidCell("cellHeavyWater", ModFluids.fluidHeavyWater);
        GameRegistry.registerItem(cellHeavyWater, "cellHeavyWater");
        OreDictionary.registerOre("cellHeavyWater", new ItemStack(cellHeavyWater));

        rodCustomFuel1 = new ItemCustomFuelRod(1);
        OreDictionary.registerOre("fuelRodCustom1", new ItemStack(rodCustomFuel1));

        rodCustomDepletedFuel1 = new ItemCustomDepletedFuelRod(1);
        OreDictionary.registerOre("depletedFuelRodCustom1", new ItemStack(rodCustomDepletedFuel1));

        rodCustomFuel2 = new ItemCustomFuelRod(2);
        OreDictionary.registerOre("fuelRodCustom2", new ItemStack(rodCustomFuel2));

        rodCustomDepletedFuel2 = new ItemCustomDepletedFuelRod(2);
        OreDictionary.registerOre("depletedFuelRodCustom2", new ItemStack(rodCustomDepletedFuel2));

        rodCustomFuel4 = new ItemCustomFuelRod(4);
        OreDictionary.registerOre("fuelRodCustom4", new ItemStack(rodCustomFuel4));

        rodCustomDepletedFuel4 = new ItemCustomDepletedFuelRod(4);
        OreDictionary.registerOre("depletedFuelRodCustom4", new ItemStack(rodCustomDepletedFuel4));
    }
}

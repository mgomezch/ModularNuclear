package com.gtnewhorizons.modularnuclear.common.recipe;

import static gregtech.api.recipe.RecipeMaps.cannerRecipes;
import static gregtech.api.recipe.RecipeMaps.centrifugeRecipes;
import static gregtech.api.recipe.RecipeMaps.compressorRecipes;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;
import static gregtech.api.util.GTRecipeBuilder.TICKS;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.modularnuclear.common.fluid.ModFluids;
import com.gtnewhorizons.modularnuclear.common.item.ModItems;

import cpw.mods.fml.common.Loader;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.util.GTModHandler;
import ic2.core.item.ItemFluidCell;

public class ModRecipes {

    public static void init() {
        registerCompressorRecipes();
        registerCentrifugeRecipes();
        registerCannerRecipes();
    }

    private static void registerCompressorRecipes() {
        // Distilled Water (1000 L) -> High Pressure Distilled Water (1000 L), EV (1920 EU/t), 30s
        FluidStack distilledWater = GTModHandler.getDistilledWater(1000);
        if (distilledWater != null && ModFluids.fluidHighPressureDistilledWater != null) {
            GTValues.RA.stdBuilder()
                .fluidInputs(distilledWater)
                .fluidOutputs(new FluidStack(ModFluids.fluidHighPressureDistilledWater, 1000))
                .eut(1920)
                .duration(30 * SECONDS)
                .addTo(compressorRecipes);
        }

        // Heavy Water (1000 L) -> High Pressure Heavy Water (1000 L), EV (1920 EU/t), 30s
        if (ModFluids.fluidHeavyWater != null && ModFluids.fluidHighPressureHeavyWater != null) {
            GTValues.RA.stdBuilder()
                .fluidInputs(new FluidStack(ModFluids.fluidHeavyWater, 1000))
                .fluidOutputs(new FluidStack(ModFluids.fluidHighPressureHeavyWater, 1000))
                .eut(1920)
                .duration(30 * SECONDS)
                .addTo(compressorRecipes);
        }
    }

    private static void registerCentrifugeRecipes() {
        // Distilled Water (1000 L) -> Heavy Water (10 L), IV (7680 EU/t), 20s
        FluidStack distilledWater = GTModHandler.getDistilledWater(1000);
        if (distilledWater != null && ModFluids.fluidHeavyWater != null) {
            GTValues.RA.stdBuilder()
                .fluidInputs(distilledWater)
                .fluidOutputs(new FluidStack(ModFluids.fluidHeavyWater, 10))
                .eut(7680)
                .duration(20 * SECONDS)
                .addTo(centrifugeRecipes);
        }
    }

    private static void registerCannerRecipes() {
        registerFluidCanning(ModFluids.fluidHeavyWater, ModItems.cellHeavyWater);
        registerFluidCanning(ModFluids.fluidHighPressureDistilledWater, ModItems.cellHighPressureDistilledWater);
        registerFluidCanning(ModFluids.fluidHighPressureHeavyWater, ModItems.cellHighPressureHeavyWater);
    }

    private static void registerFluidCanning(Fluid fluid, Item cellItem) {
        if (fluid == null) return;

        ItemStack emptyCell = ItemList.Cell_Empty.get(1);
        if (emptyCell != null && cellItem != null) {
            ItemStack fullCell = new ItemStack(cellItem);

            // Filling: Empty Cell + 1000 L Fluid -> Full Cell
            GTValues.RA.stdBuilder()
                .itemInputs(emptyCell.copy())
                .itemOutputs(fullCell.copy())
                .fluidInputs(new FluidStack(fluid, 1000))
                .duration(16 * TICKS)
                .eut(1)
                .addTo(cannerRecipes);

            // Emptying: Full Cell -> Empty Cell + 1000 L Fluid
            GTValues.RA.stdBuilder()
                .itemInputs(fullCell.copy())
                .itemOutputs(emptyCell.copy())
                .fluidOutputs(new FluidStack(fluid, 1000))
                .duration(16 * TICKS)
                .eut(1)
                .addTo(cannerRecipes);
        }

        if (Loader.isModLoaded("IC2")) {
            registerUniversalCellCanning(fluid);
        }
    }

    private static void registerUniversalCellCanning(Fluid fluid) {
        ItemStack emptyUniv = ItemList.Cell_Universal_Fluid.get(1);
        ItemStack filledUniv = ItemFluidCell.getUniversalFluidCell(new FluidStack(fluid, 1000));
        if (emptyUniv != null && filledUniv != null) {
            // Filling: Empty Universal Cell + 1000 L Fluid -> Full Universal Cell
            GTValues.RA.stdBuilder()
                .itemInputs(emptyUniv.copy())
                .itemOutputs(filledUniv.copy())
                .fluidInputs(new FluidStack(fluid, 1000))
                .duration(16 * TICKS)
                .eut(1)
                .addTo(cannerRecipes);

            // Emptying: Full Universal Cell -> Empty Universal Cell + 1000 L Fluid
            GTValues.RA.stdBuilder()
                .itemInputs(filledUniv.copy())
                .itemOutputs(emptyUniv.copy())
                .fluidOutputs(new FluidStack(fluid, 1000))
                .duration(16 * TICKS)
                .eut(1)
                .addTo(cannerRecipes);
        }
    }
}

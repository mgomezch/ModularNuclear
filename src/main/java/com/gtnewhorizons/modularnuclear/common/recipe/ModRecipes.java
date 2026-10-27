package com.gtnewhorizons.modularnuclear.common.recipe;

import static gregtech.api.recipe.RecipeMaps.cannerRecipes;
import static gregtech.api.recipe.RecipeMaps.centrifugeRecipes;
import static gregtech.api.recipe.RecipeMaps.extruderRecipes;
import static gregtech.api.recipe.RecipeMaps.fluidHeaterRecipes;
import static gregtech.api.recipe.RecipeMaps.maceratorRecipes;
import static gregtech.api.recipe.RecipeMaps.rtgFuels;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;
import static gregtech.api.util.GTRecipeBuilder.TICKS;
import static gregtech.api.util.GTRecipeConstants.RTG_DURATION_IN_DAYS;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.modularnuclear.common.block.ModBlocks;
import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantPipeSpec;
import com.gtnewhorizons.modularnuclear.common.fluid.ModFluids;
import com.gtnewhorizons.modularnuclear.common.item.ModItems;
import com.gtnewhorizons.modularnuclear.common.metatileentity.CoolantLoopMetaTileEntities;
import com.gtnewhorizons.modularnuclear.common.metatileentity.ModMetaTileEntities;

import cpw.mods.fml.common.Loader;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.TierEU;
import gregtech.api.enums.ToolDictNames;
import gregtech.api.util.GTModHandler;
import ic2.core.item.ItemFluidCell;

public class ModRecipes {

    private static boolean initialized = false;

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        ModFluids.registerWithGregTech();
        registerCentrifugeRecipes();
        registerCannerRecipes();
        registerFluidHeaterRecipes();
        registerCoriumRecipes();
        registerHighPressureHatchRecipes();
    }

    /**
     * Each material's high-pressure passage hatches: the GregTech input (or output) hatch of the material's voltage
     * tier, a huge coolant pipe of the material and a clean stainless steel casing (GregTech has no reinforced casing
     * per pipe material). A screwdriver turns an input into the output of the same material and back.
     */
    private static void registerHighPressureHatchRecipes() {
        if (!ItemList.Casing_CleanStainlessSteel.hasBeenSet()) return;
        ItemStack casing = ItemList.Casing_CleanStainlessSteel.get(1L);
        for (int m = 0; m < CoolantPipeSpec.MATERIAL_NAMES.length; m++) {
            ItemStack input = ModMetaTileEntities.nuclearHighPressureInputHatches[m];
            ItemStack output = ModMetaTileEntities.nuclearHighPressureOutputHatches[m];
            ItemStack pipe = CoolantLoopMetaTileEntities.coolantPipeItems[m][CoolantPipeSpec.SIZE_NAMES.length - 1];
            String tier = GTValues.VN[CoolantPipeSpec.VOLTAGE_TIERS[m]];
            ItemList inputHatch = ItemList.valueOf("Hatch_Input_" + tier);
            ItemList outputHatch = ItemList.valueOf("Hatch_Output_" + tier);
            if (input == null || output == null
                || pipe == null
                || !inputHatch.hasBeenSet()
                || !outputHatch.hasBeenSet()) {
                continue;
            }
            GTModHandler.addCraftingRecipe(
                input.copy(),
                GTModHandler.RecipeBits.NOT_REMOVABLE | GTModHandler.RecipeBits.REVERSIBLE,
                new Object[] { "P", "H", "C", 'P', pipe, 'H', inputHatch.get(1L), 'C', casing });
            GTModHandler.addCraftingRecipe(
                output.copy(),
                GTModHandler.RecipeBits.NOT_REMOVABLE | GTModHandler.RecipeBits.REVERSIBLE,
                new Object[] { "P", "H", "C", 'P', pipe, 'H', outputHatch.get(1L), 'C', casing });
            GTModHandler.addShapelessCraftingRecipe(
                output.copy(),
                new Object[] { input.copy(), ToolDictNames.craftingToolScrewdriver });
            GTModHandler.addShapelessCraftingRecipe(
                input.copy(),
                new Object[] { output.copy(), ToolDictNames.craftingToolScrewdriver });
        }
    }

    private static void registerCoriumRecipes() {
        if (ModBlocks.blockCoriumSolid != null && ModItems.itemCoriumDust != null) {
            // Macerator: Corium Block -> Corium Dust (120 seconds at 7680 EU/t)
            GTValues.RA.stdBuilder()
                .itemInputs(new ItemStack(ModBlocks.blockCoriumSolid, 1))
                .itemOutputs(new ItemStack(ModItems.itemCoriumDust, 1))
                .duration(120 * SECONDS)
                .eut(7680)
                .addTo(maceratorRecipes);
        }

        if (ModItems.itemCoriumDust != null && ModItems.itemCoriumRod != null) {
            // Extruder: Corium Dust + Shape (Rod) -> Corium Rod
            if (ItemList.Shape_Extruder_Rod.hasBeenSet()) {
                GTValues.RA.stdBuilder()
                    .itemInputs(new ItemStack(ModItems.itemCoriumDust, 1), ItemList.Shape_Extruder_Rod.get(0L))
                    .itemOutputs(new ItemStack(ModItems.itemCoriumRod, 1))
                    .duration(20 * SECONDS)
                    .eut(TierEU.RECIPE_HV)
                    .addTo(extruderRecipes);
            }

            // RTG Fuel: Corium Rod (432 days, twice as long as AM241 at 216 days)
            GTValues.RA.stdBuilder()
                .itemInputs(new ItemStack(ModItems.itemCoriumRod, 1))
                .duration(0)
                .eut(TierEU.RECIPE_LV)
                .metadata(RTG_DURATION_IN_DAYS, 432)
                .addTo(rtgFuels);
        }
    }

    private static void registerFluidHeaterRecipes() {
        if (!ItemList.Circuit_Integrated.hasBeenSet()) return;
        if (ModFluids.fluidHeavyWater != null && ModFluids.fluidHeavyWaterSteam != null) {
            GTValues.RA.stdBuilder()
                .circuit(1)
                .fluidInputs(new FluidStack(ModFluids.fluidHeavyWater, 6))
                .fluidOutputs(new FluidStack(ModFluids.fluidHeavyWaterSteam, 960))
                .duration(30 * TICKS)
                .eut(TierEU.RECIPE_LV)
                .addTo(fluidHeaterRecipes);
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
        if (isModLoaded("IC2") && ModFluids.fluidHeavyWaterSteam != null) {
            registerUniversalCellCanning(ModFluids.fluidHeavyWaterSteam);
        }
    }

    private static void registerFluidCanning(Fluid fluid, Item cellItem) {
        if (fluid == null) return;
        if (!ItemList.Cell_Empty.hasBeenSet()) return;

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

        if (isModLoaded("IC2")) {
            registerUniversalCellCanning(fluid);
        }
    }

    private static boolean isModLoaded(String modname) {
        try {
            return Loader.isModLoaded(modname);
        } catch (Throwable t) {
            return false;
        }
    }

    private static void registerUniversalCellCanning(Fluid fluid) {
        if (!ItemList.Cell_Universal_Fluid.hasBeenSet()) return;
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

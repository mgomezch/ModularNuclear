package com.gtnewhorizons.modularnuclear.common.recipe;

import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.gtnewhorizons.modularnuclear.common.block.CoolantLoopBlocks;
import com.gtnewhorizons.modularnuclear.common.metatileentity.CoolantLoopMetaTileEntities;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.ToolDictNames;
import gregtech.api.util.GTModHandler;
import gregtech.api.util.GTOreDictUnificator;

public class CoolantLoopRecipes {

    public static void init() {
        registerCraftingRecipes();
        registerAssemblerRecipes();
    }

    private static void registerCraftingRecipes() {
        // Manifold: 4 Steel Plates, 4 Steel Normal Pipes, 1 Solid Steel Casing
        ItemStack steelPlate = GTOreDictUnificator.get(OrePrefixes.plate, Materials.Steel, 1L);
        ItemStack steelPipe = GTOreDictUnificator.get(OrePrefixes.pipeMedium, Materials.Steel, 1L);
        ItemStack steelCasing = ItemList.Casing_SolidSteel.get(1L);

        if (CoolantLoopBlocks.manifold != null && steelPlate != null && steelPipe != null && steelCasing != null) {
            GTModHandler.addCraftingRecipe(
                new ItemStack(CoolantLoopBlocks.manifold, 1),
                GTModHandler.RecipeBits.NOT_REMOVABLE | GTModHandler.RecipeBits.REVERSIBLE,
                new Object[] { "PIP", "ICI", "PIP", 'P', steelPlate, 'I', steelPipe, 'C', steelCasing });
        }

        // Loop Instrument: 1 Steel Pipe, 1 Redstone Comparator, 1 Small Steel Gear
        ItemStack smallSteelGear = GTOreDictUnificator.get(OrePrefixes.gearGtSmall, Materials.Steel, 1L);
        ItemStack comparator = new ItemStack(Items.comparator);

        if (CoolantLoopBlocks.loopInstrument != null && steelPipe != null && smallSteelGear != null) {
            GTModHandler.addCraftingRecipe(
                new ItemStack(CoolantLoopBlocks.loopInstrument, 1),
                GTModHandler.RecipeBits.NOT_REMOVABLE | GTModHandler.RecipeBits.REVERSIBLE,
                new Object[] { " G ", " P ", " C ", 'G', smallSteelGear, 'P', steelPipe, 'C', comparator });
        }

        // Pressurized input/output hatch screwdriver conversion
        if (CoolantLoopMetaTileEntities.pressurizedInputHatch != null
            && CoolantLoopMetaTileEntities.pressurizedOutputHatch != null) {
            ItemStack inputHatch = CoolantLoopMetaTileEntities.pressurizedInputHatch.getStackForm(1L);
            ItemStack outputHatch = CoolantLoopMetaTileEntities.pressurizedOutputHatch.getStackForm(1L);

            // Input hatch + Screwdriver -> Output hatch
            GTModHandler.addShapelessCraftingRecipe(
                outputHatch.copy(),
                new Object[] { inputHatch.copy(), ToolDictNames.craftingToolScrewdriver });

            // Output hatch + Screwdriver -> Input hatch
            GTModHandler.addShapelessCraftingRecipe(
                inputHatch.copy(),
                new Object[] { outputHatch.copy(), ToolDictNames.craftingToolScrewdriver });
        }

        registerCoolantPipeCraftingRecipes();
    }

    private static final OrePrefixes[] PIPE_PREFIXES = new OrePrefixes[] { OrePrefixes.pipeTiny, OrePrefixes.pipeSmall,
        OrePrefixes.pipeMedium, OrePrefixes.pipeLarge, OrePrefixes.pipeHuge };

    public static int getMaterialInsulationTier(Materials mat) {
        if (mat == Materials.CastIron || mat == Materials.Steel) return 1;
        if (mat == Materials.StainlessSteel) return 2;
        if (mat == Materials.Titanium) return 3;
        if (mat == Materials.TungstenSteel) return 4;
        if (mat == Materials.Osmium) return 5;
        return 6; // Neutronium / tier > 5 repeats tier 2 (Mica)
    }

    private static ItemStack getNHItem(String name) {
        try {
            Class<?> clazz = Class.forName("com.dreammaster.item.NHItemList");
            for (Object constant : clazz.getEnumConstants()) {
                if (constant.toString()
                    .equalsIgnoreCase(name)) {
                    java.lang.reflect.Method m = clazz.getMethod("get", int.class);
                    return (ItemStack) m.invoke(constant, 1);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static ItemStack findItemSafe(String modId, String name, int amount, int meta) {
        try {
            net.minecraft.item.Item item = cpw.mods.fml.common.registry.GameRegistry.findItem(modId, name);
            if (item != null) {
                return new ItemStack(item, amount, meta);
            }
            ItemStack is = cpw.mods.fml.common.registry.GameRegistry.findItemStack(modId, name, amount);
            if (is != null) {
                if (meta > 0) is.setItemDamage(meta);
                return is;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static ItemStack getInsulationItem(int tier) {
        ItemStack stack = null;
        switch (tier) {
            case 1:
                stack = getNHItem("AluminoSilicateWool");
                if (stack == null) stack = findItemSafe("dreamcraft", "AluminoSilicateWool", 1, 0);
                if (stack == null) stack = GTOreDictUnificator.get("woolAluminoSilicate", 1L);
                if (stack == null) stack = GTOreDictUnificator.get(OrePrefixes.plate, Materials.Asbestos, 1L);
                if (stack == null) stack = new ItemStack(net.minecraft.init.Blocks.wool);
                break;
            case 2:
                stack = getNHItem("MicaInsulatorFoil");
                if (stack == null) stack = findItemSafe("dreamcraft", "MicaInsulatorFoil", 1, 0);
                if (stack == null) stack = GTOreDictUnificator.get(OrePrefixes.foil, Materials.Mica, 1L);
                if (stack == null && ItemList.Naquarite_Universal_Insulator_Foil.hasBeenSet()) {
                    stack = ItemList.Naquarite_Universal_Insulator_Foil.get(1L);
                }
                if (stack == null) stack = new ItemStack(net.minecraft.init.Items.paper);
                break;
            case 3:
                stack = findItemSafe("GalacticraftMars", "itemBasicAsteroids", 1, 7);
                if (stack == null) stack = findItemSafe("GalacticraftMars", "item.itemBasicAsteroids", 1, 7);
                if (stack == null) stack = getInsulationItem(2);
                break;
            case 4:
                stack = findItemSafe("GalaxySpace", "ThermalClothT2", 1, 0);
                if (stack == null) stack = findItemSafe("GalaxySpace", "item.ThermalClothT2", 1, 0);
                if (stack == null) stack = getInsulationItem(2);
                break;
            case 5:
                if (ItemList.Naquarite_Universal_Insulator_Foil.hasBeenSet()) {
                    stack = ItemList.Naquarite_Universal_Insulator_Foil.get(1L);
                }
                if (stack == null) stack = GTOreDictUnificator.get(OrePrefixes.foil, Materials.Naquadah, 1L);
                if (stack == null) stack = getInsulationItem(2);
                break;
            default:
                // Tiers > 5 repeat Mica Insulator Foil
                stack = getInsulationItem(2);
                break;
        }
        return stack;
    }

    private static void registerCoolantPipeCraftingRecipes() {
        for (int m = 0; m < CoolantLoopMetaTileEntities.COOLANT_PIPE_MATERIALS.length; m++) {
            Materials mat = CoolantLoopMetaTileEntities.COOLANT_PIPE_MATERIALS[m];
            int tier = getMaterialInsulationTier(mat);
            ItemStack insulation = getInsulationItem(tier);

            for (int s = 0; s < CoolantLoopMetaTileEntities.PIPE_THICKNESSES.length; s++) {
                OrePrefixes prefix = PIPE_PREFIXES[s];
                ItemStack originalPipe = GTOreDictUnificator.get(prefix, mat, 1L);
                if (originalPipe == null && mat == Materials.CastIron) {
                    originalPipe = GTOreDictUnificator.get(prefix, Materials.Iron, 1L);
                }
                ItemStack coolantPipe = CoolantLoopMetaTileEntities.coolantPipeItems[m][s];

                if (originalPipe != null && insulation != null && coolantPipe != null) {
                    GTModHandler.addCraftingRecipe(
                        gregtech.api.util.GTUtility.copyAmount(3, coolantPipe),
                        GTModHandler.RecipeBits.NOT_REMOVABLE | GTModHandler.RecipeBits.REVERSIBLE,
                        new Object[] { "ZPZ", "ZPZ", "ZPZ", 'Z', insulation, 'P', originalPipe });
                }
            }
        }
    }

    private static void registerAssemblerRecipes() {
        // 1. Coolant Loop Pump (EV)
        if (CoolantLoopMetaTileEntities.coolantPump != null) {
            ItemStack evHull = ItemList.Hull_EV.get(1L);
            ItemStack evMotor = ItemList.Electric_Motor_EV.get(2L);
            ItemStack evPump = ItemList.Electric_Pump_EV.get(2L);
            ItemStack largePipe = GTOreDictUnificator.get(OrePrefixes.pipeLarge, Materials.TungstenSteel, 1L);
            ItemStack circuit = GTOreDictUnificator.get(OrePrefixes.circuit, Materials.EV, 1L);

            if (evHull != null && evMotor != null && evPump != null && largePipe != null && circuit != null) {
                GTValues.RA.stdBuilder()
                    .itemInputs(evHull, evMotor, evPump, largePipe, circuit)
                    .itemOutputs(CoolantLoopMetaTileEntities.coolantPump.getStackForm(1L))
                    .duration(30 * SECONDS)
                    .eut(1920) // EV tier
                    .addTo(assemblerRecipes);
            }
        }

        // 2. Pressurized Input Hatch (EV)
        if (CoolantLoopMetaTileEntities.pressurizedInputHatch != null) {
            ItemStack evHull = ItemList.Hull_EV.get(1L);
            ItemStack evPump = ItemList.Electric_Pump_EV.get(1L);
            ItemStack normalPipe = GTOreDictUnificator.get(OrePrefixes.pipeMedium, Materials.Titanium, 1L);
            ItemStack plate = GTOreDictUnificator.get(OrePrefixes.plate, Materials.Titanium, 1L);

            if (evHull != null && evPump != null && normalPipe != null && plate != null) {
                GTValues.RA.stdBuilder()
                    .itemInputs(evHull, evPump, normalPipe, plate)
                    .itemOutputs(CoolantLoopMetaTileEntities.pressurizedInputHatch.getStackForm(1L))
                    .duration(20 * SECONDS)
                    .eut(1920)
                    .addTo(assemblerRecipes);
            }
        }

        // 3. Pressurized Heat Exchanger Multiblock Controller
        if (CoolantLoopMetaTileEntities.pressurizedHeatExchanger != null) {
            ItemStack evHull = ItemList.Hull_EV.get(1L);
            ItemStack evPump = ItemList.Electric_Pump_EV.get(2L);
            ItemStack tiPlate = GTOreDictUnificator.get(OrePrefixes.plate, Materials.Titanium, 2L);
            ItemStack circuit = GTOreDictUnificator.get(OrePrefixes.circuit, Materials.EV, 1L);

            if (evHull != null && evPump != null && tiPlate != null && circuit != null) {
                GTValues.RA.stdBuilder()
                    .itemInputs(evHull, evPump, tiPlate, circuit)
                    .itemOutputs(CoolantLoopMetaTileEntities.pressurizedHeatExchanger.getStackForm(1L))
                    .duration(30 * SECONDS)
                    .eut(1920)
                    .addTo(assemblerRecipes);
            }
        }

        registerCoolantPipeAssemblerRecipes();
    }

    private static void registerCoolantPipeAssemblerRecipes() {
        if (assemblerRecipes == null || GTValues.RA == null) {
            return;
        }

        for (int m = 0; m < CoolantLoopMetaTileEntities.COOLANT_PIPE_MATERIALS.length; m++) {
            Materials mat = CoolantLoopMetaTileEntities.COOLANT_PIPE_MATERIALS[m];
            int tier = getMaterialInsulationTier(mat);
            ItemStack insulation = getInsulationItem(tier);

            for (int s = 0; s < CoolantLoopMetaTileEntities.PIPE_THICKNESSES.length; s++) {
                OrePrefixes prefix = PIPE_PREFIXES[s];
                ItemStack originalPipe = GTOreDictUnificator.get(prefix, mat, 1L);
                if (originalPipe == null && mat == Materials.CastIron) {
                    originalPipe = GTOreDictUnificator.get(prefix, Materials.Iron, 1L);
                }
                ItemStack coolantPipe = CoolantLoopMetaTileEntities.coolantPipeItems[m][s];

                if (originalPipe != null && insulation != null && coolantPipe != null) {
                    long eut = tier <= 5 ? GTValues.VP[tier] : GTValues.VP[6];
                    GTValues.RA.stdBuilder()
                        .itemInputs(
                            gregtech.api.util.GTUtility.copyAmount(3, originalPipe),
                            gregtech.api.util.GTUtility.copyAmount(6, insulation))
                        .itemOutputs(gregtech.api.util.GTUtility.copyAmount(3, coolantPipe))
                        .duration(15 * SECONDS)
                        .eut(eut)
                        .addTo(assemblerRecipes);
                }
            }
        }
    }
}

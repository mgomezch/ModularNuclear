package com.gtnewhorizons.modularnuclear.common.recipe;

import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;
import static gregtech.api.recipe.RecipeMaps.chemicalPlantRecipes;
import static gregtech.api.recipe.RecipeMaps.multiblockChemicalReactorRecipes;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;
import static gregtech.api.util.GTRecipeConstants.CHEMPLANT_CASING_TIER;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.modularnuclear.common.fluid.ModWerkstoffs;
import com.gtnewhorizons.modularnuclear.common.metatileentity.CoolantLoopMetaTileEntities;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.TierEU;
import gregtech.api.util.GTOreDictUnificator;
import gtPlusPlus.xmod.gregtech.api.enums.GregtechItemList;

/*-
Note [R-134a chemistry] (generated)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
R-134a (1,1,1,2-tetrafluoroethane, CF3CH2F) is made industrially by fluorinating a chlorinated two-carbon
precursor with hydrogen fluoride over catalysts, most often trichloroethylene: first to HCFC-133a (CF3CH2Cl) in the
liquid phase over a Lewis acid (SbCl5), then on to R-134a in the gas phase over a chromia or nickel catalyst.

GTNH has vinyl chloride, chlorine, hydrogen fluoride and hydrochloric acid, but no trichloroethylene, so the chain
starts from vinyl chloride (GregTech's amounts count atoms, so 4000 L of chlorine is two Cl2):

    1. LCR             C2H3Cl + 2 Cl2  ->  C2HCl3 + 2 HCl          vinyl chloride to trichloroethylene
    2. Chemical Plant  C2HCl3 + 3 HF   ->  CF3CH2Cl + 2 HCl        Solid Acid Catalyst, for the Lewis acid
    3. Chemical Plant  CF3CH2Cl + HF   ->  CF3CH2F + HCl           Brown Metal Catalyst (nickel, aluminium)

The catalysts are GT++'s, as the Chemical Plant's other recipes use them, and are not consumed.  The HCl goes
back to chlorine through GregTech's usual electrolysis.  ModWerkstoffs makes the three fluids (trichloroethylene,
chlorotrifluoroethane for HCFC-133a, and R-134a), and GasProperty.R134A knows R-134a as a gas-loop gas.
*/
/** The R-134a chain and the gas loop machines' recipes. See Note [R-134a chemistry] */
public final class RefrigerantRecipes {

    private RefrigerantRecipes() {}

    public static void init() {
        registerChemistry();
        registerMachines();
    }

    private static void registerChemistry() {
        if (ModWerkstoffs.r134a == null) return;
        GTValues.RA.stdBuilder()
            .circuit(4)
            .fluidInputs(Materials.VinylChloride.getGas(1_000), Materials.Chlorine.getGas(4_000))
            .fluidOutputs(
                ModWerkstoffs.trichloroethylene.getFluidOrGas(1_000),
                Materials.HydrochloricAcid.getFluid(2_000))
            .duration(10 * SECONDS)
            .eut(TierEU.RECIPE_MV)
            .addTo(multiblockChemicalReactorRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(GregtechItemList.SolidAcidCatalyst.get(0))
            .circuit(11)
            .fluidInputs(
                ModWerkstoffs.trichloroethylene.getFluidOrGas(1_000),
                Materials.HydrofluoricAcid.getFluid(3_000))
            .fluidOutputs(
                ModWerkstoffs.chlorotrifluoroethane.getFluidOrGas(1_000),
                Materials.HydrochloricAcid.getFluid(2_000))
            .duration(15 * SECONDS)
            .eut(TierEU.RECIPE_HV)
            .metadata(CHEMPLANT_CASING_TIER, 2)
            .addTo(chemicalPlantRecipes);

        GTValues.RA.stdBuilder()
            .itemInputs(GregtechItemList.BrownMetalCatalyst.get(0))
            .circuit(12)
            .fluidInputs(
                ModWerkstoffs.chlorotrifluoroethane.getFluidOrGas(1_000),
                Materials.HydrofluoricAcid.getFluid(1_000))
            .fluidOutputs(ModWerkstoffs.r134a.getFluidOrGas(1_000), Materials.HydrochloricAcid.getFluid(1_000))
            .duration(15 * SECONDS)
            .eut(TierEU.RECIPE_HV)
            .metadata(CHEMPLANT_CASING_TIER, 2)
            .addTo(chemicalPlantRecipes);
    }

    private static ItemStack circuit(Materials tier) {
        return GTOreDictUnificator.get(OrePrefixes.circuit, tier, 1L);
    }

    private static void registerMachines() {
        if (CoolantLoopMetaTileEntities.gasCompressor != null) {
            GTValues.RA.stdBuilder()
                .itemInputs(
                    ItemList.Hull_HV.get(1L),
                    ItemList.Electric_Motor_HV.get(2L),
                    ItemList.Electric_Pump_HV.get(2L),
                    GTOreDictUnificator.get(OrePrefixes.gearGt, Materials.StainlessSteel, 2L),
                    circuit(Materials.HV))
                .itemOutputs(CoolantLoopMetaTileEntities.gasCompressor.getStackForm(1L))
                .duration(30 * SECONDS)
                .eut(TierEU.RECIPE_HV)
                .addTo(assemblerRecipes);
        }
        if (CoolantLoopMetaTileEntities.expansionValve != null) {
            GTValues.RA.stdBuilder()
                .itemInputs(
                    ItemList.Hull_HV.get(1L),
                    ItemList.Electric_Pump_HV.get(1L),
                    GTOreDictUnificator.get(OrePrefixes.pipeMedium, Materials.StainlessSteel, 2L),
                    GTOreDictUnificator.get(OrePrefixes.plate, Materials.Aluminium, 4L),
                    circuit(Materials.HV))
                .itemOutputs(CoolantLoopMetaTileEntities.expansionValve.getStackForm(1L))
                .duration(20 * SECONDS)
                .eut(TierEU.RECIPE_HV)
                .addTo(assemblerRecipes);
        }
        if (CoolantLoopMetaTileEntities.turboExpander != null) {
            GTValues.RA.stdBuilder()
                .itemInputs(
                    ItemList.Hull_HV.get(1L),
                    ItemList.Electric_Motor_HV.get(2L),
                    GTOreDictUnificator.get(OrePrefixes.rotor, Materials.StainlessSteel, 2L),
                    GTOreDictUnificator.get(OrePrefixes.wireGt02, Materials.Gold, 4L),
                    circuit(Materials.HV))
                .itemOutputs(CoolantLoopMetaTileEntities.turboExpander.getStackForm(1L))
                .duration(30 * SECONDS)
                .eut(TierEU.RECIPE_HV)
                .addTo(assemblerRecipes);
        }
        if (CoolantLoopMetaTileEntities.radiator != null) {
            GTValues.RA.stdBuilder()
                .itemInputs(
                    ItemList.Hull_MV.get(1L),
                    ItemList.Electric_Motor_MV.get(4L),
                    GTOreDictUnificator.get(OrePrefixes.plate, Materials.Aluminium, 4L),
                    GTOreDictUnificator.get(OrePrefixes.pipeMedium, Materials.Steel, 2L),
                    circuit(Materials.MV))
                .itemOutputs(CoolantLoopMetaTileEntities.radiator.getStackForm(1L))
                .duration(20 * SECONDS)
                .eut(TierEU.RECIPE_MV)
                .addTo(assemblerRecipes);
        }
    }
}

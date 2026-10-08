package com.gtnewhorizons.modularnuclear.common.util;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearFuelType;

import goodgenerator.items.GGMaterial;
import gregtech.api.enums.ItemList;
import gregtech.api.items.ItemCoolantCell;
import gregtech.api.items.ItemRadioactiveCell;
import gregtech.api.items.ItemRadioactiveCellIC;
import gregtech.api.util.GTUtility;
import ic2.core.Ic2Items;
import ic2.core.item.reactor.ItemReactorHeatSwitch;
import ic2.core.item.reactor.ItemReactorMOX;
import ic2.core.item.reactor.ItemReactorUranium;
import ic2.core.item.reactor.ItemReactorVent;
import ic2.core.item.reactor.ItemReactorVentSpread;

/**
 * Utility class for identifying, classifying, and mapping nuclear reactor fuels, coolants,
 * internal components, and thermal dampeners.
 */
public final class NuclearFuelClassification {

    private NuclearFuelClassification() {}

    public static boolean isItemFuel(ItemStack stack) {
        if (stack == null) return false;
        if (stack.getItem() instanceof ItemRadioactiveCell) return true;
        if (stack.getItem() instanceof ItemReactorUranium) return true;
        if (NuclearFuelType.fromName(stack.getUnlocalizedName()) != null) return true;
        String name = stack.getUnlocalizedName();
        if (name != null) {
            String lower = name.toLowerCase();
            return lower.contains("uranium") || lower.contains("mox")
                || lower.contains("thorium")
                || lower.contains("plutonium")
                || lower.contains("naquadah")
                || lower.contains("naquadria")
                || lower.contains("tiberium")
                || lower.contains("thecore")
                || lower.contains("glowstone")
                || lower.contains("lithium")
                || lower.contains("fuelrod");
        }
        return false;
    }

    public static boolean isFluidFuel(FluidStack fluid) {
        if (fluid == null || fluid.getFluid() == null) return false;
        String name = fluid.getFluid()
            .getName()
            .toLowerCase();
        if (name.contains("naquadah")) return false; // Avoid overlap with Large Naquadah Reactor
        if (name.contains("thoriumbasedliquidfuel") || (name.contains("thorium") && name.contains("liquidfuel")))
            return true;
        if (name.contains("uraniumbasedliquidfuel") || (name.contains("uranium") && name.contains("liquidfuel")))
            return true;
        if (name.contains("plutoniumbasedliquidfuel") || (name.contains("plutonium") && name.contains("liquidfuel")))
            return true;
        if (name.contains("uraniumhexafluoride")) return true;
        try {
            if (fluid.isFluidEqual(GGMaterial.uraniumBasedLiquidFuel.getFluidOrGas(1))
                || fluid.isFluidEqual(GGMaterial.uraniumBasedLiquidFuelExcited.getFluidOrGas(1))
                || fluid.isFluidEqual(GGMaterial.thoriumBasedLiquidFuel.getFluidOrGas(1))
                || fluid.isFluidEqual(GGMaterial.thoriumBasedLiquidFuelExcited.getFluidOrGas(1))
                || fluid.isFluidEqual(GGMaterial.plutoniumBasedLiquidFuel.getFluidOrGas(1))
                || fluid.isFluidEqual(GGMaterial.plutoniumBasedLiquidFuelExcited.getFluidOrGas(1))) {
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static Fluid getSpentFluid(FluidStack fuel) {
        if (fuel == null || fuel.getFluid() == null) return null;
        String name = fuel.getFluid()
            .getName()
            .toLowerCase();
        if (name.contains("thorium")) {
            try {
                return GGMaterial.thoriumBasedLiquidFuelDepleted.getFluidOrGas(1)
                    .getFluid();
            } catch (Throwable ignored) {}
            Fluid f = FluidRegistry.getFluid("thoriumbasedliquidfueldepleted");
            if (f != null) return f;
            return FluidRegistry.getFluid("fluid.thoriumbasedliquidfueldepleted");
        }
        if (name.contains("uraniumbased") || (name.contains("uranium") && name.contains("liquidfuel"))) {
            try {
                return GGMaterial.uraniumBasedLiquidFuelDepleted.getFluidOrGas(1)
                    .getFluid();
            } catch (Throwable ignored) {}
            Fluid f = FluidRegistry.getFluid("uraniumbasedliquidfueldepleted");
            if (f != null) return f;
            return FluidRegistry.getFluid("fluid.uraniumbasedliquidfueldepleted");
        }
        if (name.contains("plutonium")) {
            try {
                return GGMaterial.plutoniumBasedLiquidFuelDepleted.getFluidOrGas(1)
                    .getFluid();
            } catch (Throwable ignored) {}
            Fluid f = FluidRegistry.getFluid("plutoniumbasedliquidfueldepleted");
            if (f != null) return f;
            return FluidRegistry.getFluid("fluid.plutoniumbasedliquidfueldepleted");
        }
        if (name.contains("uraniumhexafluoride")) {
            Fluid tetra = FluidRegistry.getFluid("uraniumtetrafluoride");
            if (tetra != null) return tetra;
            return FluidRegistry.getFluid("fluid.uraniumtetrafluoride");
        }
        return null;
    }

    public static boolean isCoolantFluid(FluidStack fluid) {
        if (fluid == null || fluid.getFluid() == null) return false;
        String name = fluid.getFluid()
            .getName()
            .toLowerCase();
        return name.contains("water") || name.contains("coolant") || name.contains("sodium") || name.contains("lead");
    }

    public static boolean isItemCoolant(ItemStack stack) {
        if (stack == null) return false;
        if (stack.getItem() instanceof ItemCoolantCell) return true;
        String name = stack.getUnlocalizedName()
            .toLowerCase();
        return name.contains("coolant") || name.contains("heatcapacitor");
    }

    public static boolean isItemHeatVent(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        String name = stack.getUnlocalizedName()
            .toLowerCase();
        // Skip reactor heat vent as requested (no reactor hull heat)
        if (name.contains("core")) return false;
        if (stack.getItem() instanceof ItemReactorVent || stack.getItem() instanceof ItemReactorVentSpread) return true;
        return name.contains("reactorvent");
    }

    public static boolean isItemHeatExchanger(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        String name = stack.getUnlocalizedName()
            .toLowerCase();
        // Skip reactor heat exchanger as requested (no reactor hull heat)
        if (name.contains("core")) return false;
        if (stack.getItem() instanceof ItemReactorHeatSwitch) return true;
        return name.contains("heatswitch") || name.contains("heatexchanger");
    }

    public static double getInsulationDampening(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return 0.0;

        // 1. Naquarite Universal Insulator Foil: rejects 100% heat & radiation
        try {
            if (ItemList.Naquarite_Universal_Insulator_Foil.isStackEqual(stack, false, true)) {
                return 1.0;
            }
        } catch (Throwable ignored) {}

        String unlocalizedName = stack.getUnlocalizedName();
        if (unlocalizedName != null) {
            String lower = unlocalizedName.toLowerCase();
            if (lower.contains("naquarite_universal_insulator_foil")
                || lower.contains("naquariteuniversalinsulatorfoil")
                || (lower.contains("naquarite") && lower.contains("insulator"))) {
                return 1.0;
            }
            if (lower.contains("micainsulatorfoil") || lower.contains("mica_insulator_foil")
                || (lower.contains("mica") && lower.contains("foil"))) {
                return 0.60;
            }
            if (lower.contains("thermalclotht2") || lower.contains("thermal_cloth_t2")
                || (lower.contains("thermalcloth") && (lower.contains("t2") || lower.contains("2")))) {
                return 0.40;
            }
            if (lower.contains("itembasicasteroids") && stack.getItemDamage() == 7) {
                return 0.20;
            }
            if (lower.contains("thermalcloth") || lower.contains("thermal_cloth")) {
                return 0.20;
            }
        }

        try {
            ItemStack gcCloth = gregtech.api.util.GTModHandler
                .getModItem("GalacticraftMars", "item.itemBasicAsteroids", 1, 7);
            if (gcCloth != null && GTUtility.areStacksEqual(stack, gcCloth, false)) {
                return 0.20;
            }
            ItemStack gsClothT2 = gregtech.api.util.GTModHandler.getModItem("GalaxySpace", "item.ThermalClothT2", 1);
            if (gsClothT2 != null && GTUtility.areStacksEqual(stack, gsClothT2, true)) {
                return 0.40;
            }
            ItemStack micaFoil = gregtech.api.util.GTModHandler.getModItem("dreamcraft", "MicaInsulatorFoil", 1);
            if (micaFoil != null && GTUtility.areStacksEqual(stack, micaFoil, true)) {
                return 0.60;
            }
        } catch (Throwable ignored) {}

        return 0.0;
    }

    public static ItemStack getItemDepletedForm(ItemStack fuel) {
        if (fuel == null) return null;

        // 1. Check if fuel is ItemRadioactiveCellIC with sDepleted
        if (fuel.getItem() instanceof ItemRadioactiveCellIC icCell && icCell.sDepleted != null) {
            return icCell.sDepleted.copy();
        }

        // 2. Check IC2 native fuel items
        if (fuel.getItem() instanceof ItemReactorUranium ic2Uran) {
            boolean isMox = ic2Uran instanceof ItemReactorMOX;
            int cells = ic2Uran.numberOfCells;
            if (isMox) {
                if (cells >= 4 && Ic2Items.reactorDepletedMOXQuad != null)
                    return Ic2Items.reactorDepletedMOXQuad.copy();
                if (cells >= 2 && Ic2Items.reactorDepletedMOXDual != null)
                    return Ic2Items.reactorDepletedMOXDual.copy();
                if (Ic2Items.reactorDepletedMOXSimple != null) return Ic2Items.reactorDepletedMOXSimple.copy();
                return (cells >= 4) ? ItemList.DepletedRodMOX4.get(1L)
                    : (cells >= 2) ? ItemList.DepletedRodMOX2.get(1L) : ItemList.DepletedRodMOX.get(1L);
            } else {
                if (cells >= 4 && Ic2Items.reactorDepletedUraniumQuad != null)
                    return Ic2Items.reactorDepletedUraniumQuad.copy();
                if (cells >= 2 && Ic2Items.reactorDepletedUraniumDual != null)
                    return Ic2Items.reactorDepletedUraniumDual.copy();
                if (Ic2Items.reactorDepletedUraniumSimple != null) return Ic2Items.reactorDepletedUraniumSimple.copy();
                return (cells >= 4) ? ItemList.DepletedRodUranium4.get(1L)
                    : (cells >= 2) ? ItemList.DepletedRodUranium2.get(1L) : ItemList.DepletedRodUranium.get(1L);
            }
        }

        String name = fuel.getUnlocalizedName();
        if (name == null) return null;
        String lower = name.toLowerCase();

        // 3. The Core (RodNaquadah32)
        if (lower.contains("naquadah32") || lower.contains("thecore")
            || (lower.contains("naquadah") && lower.contains("32"))) {
            return ItemList.DepletedRodNaquadah32.get(1L);
        }

        // 4. Excited variants
        if (lower.contains("exciteduranium") || (lower.contains("excited") && lower.contains("uranium"))) {
            if (lower.contains("quad") || lower.contains("4")) return ItemList.DepletedRodExcitedUranium4.get(1L);
            if (lower.contains("dual") || lower.contains("2")) return ItemList.DepletedRodExcitedUranium2.get(1L);
            return ItemList.DepletedRodExcitedUranium.get(1L);
        }
        if (lower.contains("excitedplutonium") || (lower.contains("excited") && lower.contains("plutonium"))) {
            if (lower.contains("quad") || lower.contains("4")) return ItemList.DepletedRodExcitedPlutonium4.get(1L);
            if (lower.contains("dual") || lower.contains("2")) return ItemList.DepletedRodExcitedPlutonium2.get(1L);
            return ItemList.DepletedRodExcitedPlutonium.get(1L);
        }

        // 5. High density variants
        if (lower.contains("highdensityuranium") || (lower.contains("highdensity") && lower.contains("uranium"))) {
            if (lower.contains("quad") || lower.contains("4")) return ItemList.DepletedRodHighDensityUranium4.get(1L);
            if (lower.contains("dual") || lower.contains("2")) return ItemList.DepletedRodHighDensityUranium2.get(1L);
            return ItemList.DepletedRodHighDensityUranium.get(1L);
        }
        if (lower.contains("highdensityplutonium") || (lower.contains("highdensity") && lower.contains("plutonium"))) {
            if (lower.contains("quad") || lower.contains("4")) return ItemList.DepletedRodHighDensityPlutonium4.get(1L);
            if (lower.contains("dual") || lower.contains("2")) return ItemList.DepletedRodHighDensityPlutonium2.get(1L);
            return ItemList.DepletedRodHighDensityPlutonium.get(1L);
        }

        // 6. Naquadria & Tiberium
        if (lower.contains("naquadria")) {
            if (lower.contains("quad") || lower.contains("4")) return ItemList.DepletedRodNaquadria4.get(1L);
            if (lower.contains("dual") || lower.contains("2")) return ItemList.DepletedRodNaquadria2.get(1L);
            return ItemList.DepletedRodNaquadria.get(1L);
        }
        if (lower.contains("tiberium")) {
            if (lower.contains("quad") || lower.contains("4")) return ItemList.DepletedRodTiberium4.get(1L);
            if (lower.contains("dual") || lower.contains("2")) return ItemList.DepletedRodTiberium2.get(1L);
            return ItemList.DepletedRodTiberium.get(1L);
        }

        // 7. Standard fuels: Naquadah, Thorium, MOX, Uranium
        if (lower.contains("naquadah")) {
            if (lower.contains("quad") || lower.contains("4")) return ItemList.DepletedRodNaquadah4.get(1L);
            if (lower.contains("dual") || lower.contains("2")) return ItemList.DepletedRodNaquadah2.get(1L);
            return ItemList.DepletedRodNaquadah.get(1L);
        } else if (lower.contains("mox")) {
            if (lower.contains("quad") || lower.contains("4")) return ItemList.DepletedRodMOX4.get(1L);
            if (lower.contains("dual") || lower.contains("2")) return ItemList.DepletedRodMOX2.get(1L);
            return ItemList.DepletedRodMOX.get(1L);
        } else if (lower.contains("thorium")) {
            if (lower.contains("quad") || lower.contains("4")) return ItemList.DepletedRodThorium4.get(1L);
            if (lower.contains("dual") || lower.contains("2")) return ItemList.DepletedRodThorium2.get(1L);
            return ItemList.DepletedRodThorium.get(1L);
        } else if (lower.contains("uranium")) {
            if (lower.contains("quad") || lower.contains("4")) return ItemList.DepletedRodUranium4.get(1L);
            if (lower.contains("dual") || lower.contains("2")) return ItemList.DepletedRodUranium2.get(1L);
            return ItemList.DepletedRodUranium.get(1L);
        }
        return null;
    }
}

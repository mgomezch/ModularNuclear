package com.gtnewhorizons.modularnuclear.common.util;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import com.gtnewhorizons.modularnuclear.common.item.ItemCustomDepletedFuelRod;
import com.gtnewhorizons.modularnuclear.common.item.ItemCustomFuelRod;
import com.gtnewhorizons.modularnuclear.common.item.ModItems;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearBus;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod;
import com.gtnewhorizons.modularnuclear.common.nuclear.CustomFuelMix;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearFuelType;

import gregtech.api.enums.ItemList;
import gregtech.api.items.ItemCoolantCell;
import gregtech.api.items.ItemRadioactiveCell;
import gregtech.api.items.ItemRadioactiveCellIC;
import gregtech.api.util.GTUtility;
import ic2.core.Ic2Items;
import ic2.core.item.reactor.ItemReactorHeatStorage;
import ic2.core.item.reactor.ItemReactorMOX;
import ic2.core.item.reactor.ItemReactorUranium;

/**
 * Utility class for identifying, classifying, and mapping nuclear reactor fuels, coolants,
 * internal components, thermal dampeners, and core item allowlists.
 */
public final class NuclearFuelClassification {

    private NuclearFuelClassification() {}

    public static final ItemList[] ACTIVE_FUEL_RODS = new ItemList[] { ItemList.RodLithium, ItemList.RodGlowstone,
        ItemList.RodThorium, ItemList.RodThorium2, ItemList.RodThorium4, ItemList.RodUranium, ItemList.RodUranium2,
        ItemList.RodUranium4, ItemList.RodMOX, ItemList.RodMOX2, ItemList.RodMOX4, ItemList.RodHighDensityUranium,
        ItemList.RodHighDensityUranium2, ItemList.RodHighDensityUranium4, ItemList.RodHighDensityPlutonium,
        ItemList.RodHighDensityPlutonium2, ItemList.RodHighDensityPlutonium4, ItemList.RodExcitedUranium,
        ItemList.RodExcitedUranium2, ItemList.RodExcitedUranium4, ItemList.RodExcitedPlutonium,
        ItemList.RodExcitedPlutonium2, ItemList.RodExcitedPlutonium4, ItemList.RodNaquadah, ItemList.RodNaquadah2,
        ItemList.RodNaquadah4, ItemList.RodNaquadah32, ItemList.RodNaquadria, ItemList.RodNaquadria2,
        ItemList.RodNaquadria4, ItemList.RodTiberium, ItemList.RodTiberium2, ItemList.RodTiberium4 };

    public static final ItemList[] DEPLETED_FUEL_RODS = new ItemList[] { ItemList.DepletedRodLithium,
        ItemList.DepletedRodGlowstone, ItemList.DepletedRodThorium, ItemList.DepletedRodThorium2,
        ItemList.DepletedRodThorium4, ItemList.DepletedRodUranium, ItemList.DepletedRodUranium2,
        ItemList.DepletedRodUranium4, ItemList.DepletedRodMOX, ItemList.DepletedRodMOX2, ItemList.DepletedRodMOX4,
        ItemList.DepletedRodHighDensityUranium, ItemList.DepletedRodHighDensityUranium2,
        ItemList.DepletedRodHighDensityUranium4, ItemList.DepletedRodHighDensityPlutonium,
        ItemList.DepletedRodHighDensityPlutonium2, ItemList.DepletedRodHighDensityPlutonium4,
        ItemList.DepletedRodExcitedUranium, ItemList.DepletedRodExcitedUranium2, ItemList.DepletedRodExcitedUranium4,
        ItemList.DepletedRodExcitedPlutonium, ItemList.DepletedRodExcitedPlutonium2,
        ItemList.DepletedRodExcitedPlutonium4, ItemList.DepletedRodNaquadah, ItemList.DepletedRodNaquadah2,
        ItemList.DepletedRodNaquadah4, ItemList.DepletedRodNaquadah32, ItemList.DepletedRodNaquadria,
        ItemList.DepletedRodNaquadria2, ItemList.DepletedRodNaquadria4, ItemList.DepletedRodTiberium,
        ItemList.DepletedRodTiberium2, ItemList.DepletedRodTiberium4 };

    public static final ItemList[] GT_COOLANT_CELLS = new ItemList[] { ItemList.Reactor_Coolant_He_1,
        ItemList.Reactor_Coolant_He_3, ItemList.Reactor_Coolant_He_6, ItemList.Reactor_Coolant_NaK_1,
        ItemList.Reactor_Coolant_NaK_3, ItemList.Reactor_Coolant_NaK_6, ItemList.Reactor_Coolant_Sp_1,
        ItemList.Reactor_Coolant_Sp_2, ItemList.Reactor_Coolant_Sp_3, ItemList.Reactor_Coolant_Sp_6 };

    private static final java.util.Map<Long, Boolean> BUS_ITEM_ALLOWED_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<Long> ACTIVE_ROD_KEYS = new java.util.HashSet<>();
    private static final java.util.Set<Long> DEPLETED_ROD_KEYS = new java.util.HashSet<>();
    private static final java.util.Set<Long> GT_COOLANT_KEYS = new java.util.HashSet<>();
    private static final java.util.Set<Long> IC2_ACTIVE_KEYS = new java.util.HashSet<>();
    private static final java.util.Set<Long> IC2_DEPLETED_KEYS = new java.util.HashSet<>();
    private static final java.util.Set<Long> HULL_ONLY_KEYS = new java.util.HashSet<>();
    private static volatile boolean keysInitialized = false;
    private static net.minecraft.item.Item cachedNeutronSourceItem = null;
    private static volatile boolean neutronSourceInitialized = false;

    public static synchronized void clearCaches() {
        BUS_ITEM_ALLOWED_CACHE.clear();
        ACTIVE_ROD_KEYS.clear();
        DEPLETED_ROD_KEYS.clear();
        GT_COOLANT_KEYS.clear();
        IC2_ACTIVE_KEYS.clear();
        IC2_DEPLETED_KEYS.clear();
        HULL_ONLY_KEYS.clear();
        keysInitialized = false;
        cachedNeutronSourceItem = null;
        neutronSourceInitialized = false;
    }

    public static synchronized void preIndexKeys() {
        initKeys();
    }

    private static void initKeys() {
        if (keysInitialized) return;
        synchronized (ACTIVE_ROD_KEYS) {
            if (keysInitialized) return;
            for (ItemList item : ACTIVE_FUEL_RODS) {
                if (item != null && item.hasBeenSet()) {
                    ItemStack is = item.get(1L);
                    if (is != null) {
                        long k = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.getItemKey(is);
                        if (k != 0L) ACTIVE_ROD_KEYS.add(k);
                    }
                }
            }
            for (ItemList item : DEPLETED_FUEL_RODS) {
                if (item != null && item.hasBeenSet()) {
                    ItemStack is = item.get(1L);
                    if (is != null) {
                        long k = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.getItemKey(is);
                        if (k != 0L) DEPLETED_ROD_KEYS.add(k);
                    }
                }
            }
            if (ItemList.IC2_Fuel_Rod_Empty != null && ItemList.IC2_Fuel_Rod_Empty.hasBeenSet()) {
                ItemStack is = ItemList.IC2_Fuel_Rod_Empty.get(1L);
                if (is != null) {
                    long k = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.getItemKey(is);
                    if (k != 0L) DEPLETED_ROD_KEYS.add(k);
                }
            }
            for (ItemList item : GT_COOLANT_CELLS) {
                if (item != null && item.hasBeenSet()) {
                    ItemStack is = item.get(1L);
                    if (is != null) {
                        long k = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.getItemKey(is);
                        if (k != 0L) GT_COOLANT_KEYS.add(k);
                    }
                }
            }
            addKeyIfNotNull(IC2_ACTIVE_KEYS, Ic2Items.reactorUraniumSimple);
            addKeyIfNotNull(IC2_ACTIVE_KEYS, Ic2Items.reactorUraniumDual);
            addKeyIfNotNull(IC2_ACTIVE_KEYS, Ic2Items.reactorUraniumQuad);
            addKeyIfNotNull(IC2_ACTIVE_KEYS, Ic2Items.reactorMOXSimple);
            addKeyIfNotNull(IC2_ACTIVE_KEYS, Ic2Items.reactorMOXDual);
            addKeyIfNotNull(IC2_ACTIVE_KEYS, Ic2Items.reactorMOXQuad);
            addKeyIfNotNull(IC2_ACTIVE_KEYS, Ic2Items.reactorLithiumCell);

            addKeyIfNotNull(IC2_DEPLETED_KEYS, Ic2Items.reactorDepletedUraniumSimple);
            addKeyIfNotNull(IC2_DEPLETED_KEYS, Ic2Items.reactorDepletedUraniumDual);
            addKeyIfNotNull(IC2_DEPLETED_KEYS, Ic2Items.reactorDepletedUraniumQuad);
            addKeyIfNotNull(IC2_DEPLETED_KEYS, Ic2Items.reactorDepletedMOXSimple);
            addKeyIfNotNull(IC2_DEPLETED_KEYS, Ic2Items.reactorDepletedMOXDual);
            addKeyIfNotNull(IC2_DEPLETED_KEYS, Ic2Items.reactorDepletedMOXQuad);
            addKeyIfNotNull(DEPLETED_ROD_KEYS, Ic2Items.fuelRod);

            addKeyIfNotNull(HULL_ONLY_KEYS, Ic2Items.reactorVentCore);
            addKeyIfNotNull(HULL_ONLY_KEYS, Ic2Items.reactorHeatSwitchCore);

            if (!ACTIVE_ROD_KEYS.isEmpty()) {
                keysInitialized = true;
            }
        }
    }

    private static void addKeyIfNotNull(java.util.Set<Long> set, ItemStack stack) {
        if (stack != null) {
            long k = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.getItemKey(stack);
            if (k != 0L) set.add(k);
        }
    }

    public static boolean isOreDictMatch(ItemStack stack, String oreName) {
        if (stack == null || oreName == null) return false;
        try {
            int targetId = OreDictionary.getOreID(oreName);
            if (targetId < 0) return false;
            int[] ids = OreDictionary.getOreIDs(stack);
            for (int id : ids) {
                if (id == targetId) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static boolean isIC2ActiveFuelCell(ItemStack stack) {
        if (stack == null) return false;
        initKeys();
        long key = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.getItemKey(stack);
        if (key != 0L && IC2_ACTIVE_KEYS.contains(key)) return true;
        if (Ic2Items.reactorUraniumSimple != null
            && GTUtility.areStacksEqual(stack, Ic2Items.reactorUraniumSimple, true)) return true;
        if (Ic2Items.reactorUraniumDual != null && GTUtility.areStacksEqual(stack, Ic2Items.reactorUraniumDual, true))
            return true;
        if (Ic2Items.reactorUraniumQuad != null && GTUtility.areStacksEqual(stack, Ic2Items.reactorUraniumQuad, true))
            return true;
        if (Ic2Items.reactorMOXSimple != null && GTUtility.areStacksEqual(stack, Ic2Items.reactorMOXSimple, true))
            return true;
        if (Ic2Items.reactorMOXDual != null && GTUtility.areStacksEqual(stack, Ic2Items.reactorMOXDual, true))
            return true;
        if (Ic2Items.reactorMOXQuad != null && GTUtility.areStacksEqual(stack, Ic2Items.reactorMOXQuad, true))
            return true;
        if (Ic2Items.reactorLithiumCell != null && GTUtility.areStacksEqual(stack, Ic2Items.reactorLithiumCell, true))
            return true;
        return false;
    }

    public static boolean isIC2DepletedFuelCell(ItemStack stack) {
        if (stack == null) return false;
        initKeys();
        long key = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.getItemKey(stack);
        if (key != 0L && IC2_DEPLETED_KEYS.contains(key)) return true;
        if (Ic2Items.reactorDepletedUraniumSimple != null
            && GTUtility.areStacksEqual(stack, Ic2Items.reactorDepletedUraniumSimple, true)) return true;
        if (Ic2Items.reactorDepletedUraniumDual != null
            && GTUtility.areStacksEqual(stack, Ic2Items.reactorDepletedUraniumDual, true)) return true;
        if (Ic2Items.reactorDepletedUraniumQuad != null
            && GTUtility.areStacksEqual(stack, Ic2Items.reactorDepletedUraniumQuad, true)) return true;
        if (Ic2Items.reactorDepletedMOXSimple != null
            && GTUtility.areStacksEqual(stack, Ic2Items.reactorDepletedMOXSimple, true)) return true;
        if (Ic2Items.reactorDepletedMOXDual != null
            && GTUtility.areStacksEqual(stack, Ic2Items.reactorDepletedMOXDual, true)) return true;
        if (Ic2Items.reactorDepletedMOXQuad != null
            && GTUtility.areStacksEqual(stack, Ic2Items.reactorDepletedMOXQuad, true)) return true;
        return false;
    }

    public static boolean isDepletedOrEmptyRod(ItemStack stack) {
        if (stack == null) return false;

        initKeys();
        long key = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.getItemKey(stack);
        if (key != 0L && (DEPLETED_ROD_KEYS.contains(key) || IC2_DEPLETED_KEYS.contains(key))) {
            return true;
        }

        if (com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.isItemBlocked(stack)) {
            return true;
        }

        if (stack.getItem() instanceof ItemCustomFuelRod
            || (ModItems.rodCustomFuel1 != null && stack.getItem() == ModItems.rodCustomFuel1)
            || (ModItems.rodCustomFuel2 != null && stack.getItem() == ModItems.rodCustomFuel2)
            || (ModItems.rodCustomFuel4 != null && stack.getItem() == ModItems.rodCustomFuel4)) {
            if (!CustomFuelMix.hasCustomMix(stack)) return true;
        }

        if (stack.getItem() instanceof ItemCustomDepletedFuelRod) return true;
        if (ModItems.rodCustomDepletedFuel1 != null && stack.getItem() == ModItems.rodCustomDepletedFuel1) return true;
        if (ModItems.rodCustomDepletedFuel2 != null && stack.getItem() == ModItems.rodCustomDepletedFuel2) return true;
        if (ModItems.rodCustomDepletedFuel4 != null && stack.getItem() == ModItems.rodCustomDepletedFuel4) return true;

        if (Ic2Items.fuelRod != null && GTUtility.areStacksEqual(stack, Ic2Items.fuelRod, true)) return true;
        if (ItemList.IC2_Fuel_Rod_Empty != null && ItemList.IC2_Fuel_Rod_Empty.hasBeenSet()
            && ItemList.IC2_Fuel_Rod_Empty.isStackEqual(stack, false, true)) return true;

        if (isIC2DepletedFuelCell(stack)) return true;

        for (ItemList item : DEPLETED_FUEL_RODS) {
            if (item != null && item.hasBeenSet() && item.isStackEqual(stack, false, true)) {
                return true;
            }
        }

        if (com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.isSpentProduct(stack)) {
            return true;
        }

        return false;
    }

    public static boolean isFuelAtMaxDamage(ItemStack stack) {
        if (stack == null || stack.getItem() == null || isDepletedOrEmptyRod(stack)) return false;

        if (stack.getItem() instanceof ItemCustomFuelRod customRod) {
            if (!CustomFuelMix.hasCustomMix(stack)) return false;
            int maxDur = customRod.getMaxDamageEx(stack);
            return customRod.getDamageOfStack(stack) >= maxDur;
        }
        if ((ModItems.rodCustomFuel1 != null && stack.getItem() == ModItems.rodCustomFuel1)
            || (ModItems.rodCustomFuel2 != null && stack.getItem() == ModItems.rodCustomFuel2)
            || (ModItems.rodCustomFuel4 != null && stack.getItem() == ModItems.rodCustomFuel4)) {
            if (!CustomFuelMix.hasCustomMix(stack)) return false;
            CustomFuelMix mix = CustomFuelMix.fromStack(stack);
            int maxDur = (mix != null) ? mix.defaultDurability : 160000;
            int curDmg = (stack.stackTagCompound != null && stack.stackTagCompound.hasKey("advDmg"))
                ? stack.stackTagCompound.getInteger("advDmg")
                : stack.getItemDamage();
            return curDmg >= maxDur;
        }

        if (stack.getItem() instanceof ItemRadioactiveCell radCell) {
            return radCell.getDamageOfStack(stack) >= radCell.getMaxDamageEx();
        }

        if (stack.getItem() instanceof ItemReactorUranium) {
            return stack.getItemDamage() >= stack.getMaxDamage();
        }

        com.gtnewhorizons.modularnuclear.common.config.data.FuelData fd = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .findFuel(stack);
        if (fd != null) {
            int maxDur = fd.defaultDurability > 0 ? fd.defaultDurability : stack.getMaxDamage();
            return stack.isItemStackDamageable() && stack.getItemDamage() >= maxDur;
        }

        if (stack.isItemStackDamageable() && stack.getItemDamage() >= stack.getMaxDamage()) {
            for (ItemList item : ACTIVE_FUEL_RODS) {
                if (item != null && item.hasBeenSet() && item.isStackEqual(stack, false, true)) {
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean isModeratorAtMaxDamage(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ModeratorData mod = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .findModerator(stack);
        if (mod != null && stack.stackTagCompound != null && stack.stackTagCompound.hasKey("advDmg")) {
            return stack.stackTagCompound.getInteger("advDmg") >= mod.durability;
        }
        return false;
    }

    public static boolean isReflectorAtMaxDamage(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ReflectorData refl = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .findReflector(stack);
        if (refl != null && refl.durability > 0
            && stack.stackTagCompound != null
            && stack.stackTagCompound.hasKey("advDmg")) {
            return stack.stackTagCompound.getInteger("advDmg") >= refl.durability;
        }
        return false;
    }

    public static ItemStack getModeratorSpentProduct(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ModeratorData mod = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .findModerator(stack);
        if (mod != null && mod.spentProduct != null && !mod.spentProduct.isEmpty()) {
            return com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
                .resolveItemStack(mod.spentProduct);
        }
        return null;
    }

    public static boolean isItemFuel(ItemStack stack) {
        if (stack == null) return false;
        if (isDepletedOrEmptyRod(stack)) return false;

        initKeys();
        long key = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.getItemKey(stack);
        if (key != 0L && (ACTIVE_ROD_KEYS.contains(key) || IC2_ACTIVE_KEYS.contains(key))) {
            return true;
        }

        if (com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.findFuel(stack) != null) {
            return true;
        }
        if (com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.isItemFuel(stack)) {
            return true;
        }

        // Custom fuel rods
        if (stack.getItem() instanceof ItemCustomFuelRod
            || (ModItems.rodCustomFuel1 != null && stack.getItem() == ModItems.rodCustomFuel1)
            || (ModItems.rodCustomFuel2 != null && stack.getItem() == ModItems.rodCustomFuel2)
            || (ModItems.rodCustomFuel4 != null && stack.getItem() == ModItems.rodCustomFuel4)) {
            if (!CustomFuelMix.hasCustomMix(stack)) return false;
            CustomFuelMix mix = CustomFuelMix.fromStack(stack);
            int maxDur = (mix != null) ? mix.defaultDurability : 160000;
            return stack.getItemDamage() < maxDur;
        }

        // GT registered active fuel rods
        for (ItemList item : ACTIVE_FUEL_RODS) {
            if (item != null && item.hasBeenSet() && item.isStackEqual(stack, false, true)) {
                return true;
            }
        }

        // IC2 registered active fuel cells
        if (isIC2ActiveFuelCell(stack)) {
            return true;
        }

        // Radioactive cell check (concrete class in GT)
        if (stack.getItem() instanceof ItemRadioactiveCell radCell) {
            int maxDur = radCell.getMaxDamageEx();
            if (radCell.getDamageOfStack(stack) >= maxDur) return false;
            return NuclearFuelType.fromName(stack.getUnlocalizedName()) != null;
        }

        // IC2 Uranium / MOX item
        if (stack.getItem() instanceof ItemReactorUranium) {
            return stack.getItemDamage() < stack.getMaxDamage();
        }

        return false;
    }

    public static boolean isFluidFuel(FluidStack fluid) {
        if (fluid == null || fluid.getFluid() == null) return false;
        if (com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.isFluidBlocked(fluid)) return false;
        com.gtnewhorizons.modularnuclear.common.config.data.FuelData fd = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .findFuel(fluid);
        if (fd != null && fd.isLiquid) return true;
        if (com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.isFluidFuel(fluid)) return true;
        return false;
    }

    public static Fluid getSpentFluid(FluidStack fuel) {
        if (fuel == null || fuel.getFluid() == null) return null;
        com.gtnewhorizons.modularnuclear.common.config.data.FuelData fd = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .findFuel(fuel);
        if (fd != null && fd.spentProduct != null) {
            Fluid f = FluidRegistry.getFluid(fd.spentProduct);
            if (f == null && !fd.spentProduct.startsWith("fluid.")) {
                f = FluidRegistry.getFluid("fluid." + fd.spentProduct);
            }
            if (f != null) return f;
        }
        return null;
    }

    public static boolean isCoolantFluid(FluidStack fluid) {
        if (fluid == null || fluid.getFluid() == null) return false;
        if (com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.findCoolantFluid(fluid) != null)
            return true;
        if (com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.isCoolantFluid(fluid)) return true;
        return false;
    }

    public static boolean isHullOnlyComponent(ItemStack stack) {
        if (stack == null) return false;
        initKeys();
        long key = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.getItemKey(stack);
        if (key != 0L && HULL_ONLY_KEYS.contains(key)) return true;
        if (Ic2Items.reactorVentCore != null && GTUtility.areStacksEqual(stack, Ic2Items.reactorVentCore, true))
            return true;
        if (Ic2Items.reactorHeatSwitchCore != null
            && GTUtility.areStacksEqual(stack, Ic2Items.reactorHeatSwitchCore, true)) return true;
        String name = stack.getUnlocalizedName();
        if (name != null) {
            return "ic2.reactorVentCore".equalsIgnoreCase(name) || "ic2.reactorHeatSwitchCore".equalsIgnoreCase(name)
                || "reactorVentCore".equalsIgnoreCase(name)
                || "reactorHeatSwitchCore".equalsIgnoreCase(name);
        }
        return false;
    }

    public static boolean isItemCoolant(ItemStack stack) {
        if (stack == null || isHullOnlyComponent(stack)) return false;
        initKeys();
        long key = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.getItemKey(stack);
        if (key != 0L && GT_COOLANT_KEYS.contains(key)) return true;
        if (com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.findCoolantItem(stack) != null)
            return true;
        if (com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.isItemCoolant(stack)) return true;
        if (stack.getItem() instanceof ItemCoolantCell) return true;
        if (stack.getItem() instanceof ItemReactorHeatStorage) return true;
        for (ItemList item : GT_COOLANT_CELLS) {
            if (item != null && item.hasBeenSet() && item.isStackEqual(stack, false, true)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isConfigurationItem(ItemStack stack) {
        if (stack == null) return false;
        return isItemReflector(stack) || isReflectorAtMaxDamage(stack)
            || isItemModerator(stack)
            || isModeratorAtMaxDamage(stack)
            || isItemHeatVent(stack)
            || isItemHeatExchanger(stack)
            || getInsulationDampening(stack) > 0.0
            || isItemRadiovoltaic(stack)
            || isItemNeutronSource(stack);
    }

    public static boolean isVoidedOnStructureBreak(ItemStack stack) {
        if (stack == null) return false;
        if (isConfigurationItem(stack)) {
            return false;
        }
        return isItemFuel(stack) || isFuelAtMaxDamage(stack) || isDepletedOrEmptyRod(stack) || isItemCoolant(stack);
    }

    public static boolean isItemHeatVent(ItemStack stack) {
        if (stack == null || isHullOnlyComponent(stack)) return false;
        if (com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.findHeatVent(stack) != null) return true;
        return com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.isItemHeatVent(stack);
    }

    public static boolean isItemHeatExchanger(ItemStack stack) {
        if (stack == null || isHullOnlyComponent(stack)) return false;
        if (com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.findHeatExchanger(stack) != null)
            return true;
        return com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.isItemHeatExchanger(stack);
    }

    public static boolean isItemReflector(ItemStack stack) {
        if (stack == null || isHullOnlyComponent(stack)) return false;
        if (com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.findReflector(stack) != null)
            return true;
        return com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.isItemReflector(stack);
    }

    public static boolean isItemModerator(ItemStack stack) {
        if (stack == null || isHullOnlyComponent(stack)) return false;
        if (com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.findModerator(stack) != null)
            return true;
        return com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.isItemModerator(stack);
    }

    public static boolean isItemRadiovoltaic(ItemStack stack) {
        if (stack == null || isHullOnlyComponent(stack)) return false;
        if (com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.findRadiovoltaic(stack) != null)
            return true;
        return com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.isItemRadiovoltaic(stack);
    }

    public static double getInsulationDampening(ItemStack stack) {
        if (stack == null || isHullOnlyComponent(stack)) return 0.0;
        com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.InsulatorData ins = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .findInsulator(stack);
        if (ins != null) return ins.dampening;
        return com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.getInsulatorDampening(stack);
    }

    public static boolean isItemAllowedInBus(ItemStack stack) {
        return isItemAllowedInBus(stack, null);
    }

    public static boolean isItemAllowedInBus(ItemStack stack, MTEHatchNuclearBus bus) {
        if (stack == null) return false;

        // Custom fuel rods require valid NBT mix
        if (stack.getItem() != null && (stack.getItem() instanceof ItemCustomFuelRod
            || (ModItems.rodCustomFuel1 != null && stack.getItem() == ModItems.rodCustomFuel1)
            || (ModItems.rodCustomFuel2 != null && stack.getItem() == ModItems.rodCustomFuel2)
            || (ModItems.rodCustomFuel4 != null && stack.getItem() == ModItems.rodCustomFuel4))) {
            return CustomFuelMix.hasCustomMix(stack);
        }

        // Subclasses or buses with cheese extraction recipe
        if (bus != null && bus.findCheeseExtractionRecipe(stack) != null) {
            return true;
        }

        long key = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.getItemKey(stack);
        if (key != 0L) {
            Boolean cached = BUS_ITEM_ALLOWED_CACHE.get(key);
            if (cached != null) {
                return cached.booleanValue();
            }
        }

        boolean allowed = computeIsItemAllowedInBus(stack, bus);
        if (key != 0L) {
            BUS_ITEM_ALLOWED_CACHE.put(key, Boolean.valueOf(allowed));
        }
        return allowed;
    }

    private static boolean computeIsItemAllowedInBus(ItemStack stack, MTEHatchNuclearBus bus) {
        // 1. Dynamic cheese extraction Easter egg inputs:
        if (com.gtnewhorizons.modularnuclear.common.recipe.CheeseRecipeRegistry.getInstance()
            .isCheeseInput(stack)) {
            return true;
        }
        if (bus != null && bus.getClass() != MTEHatchNuclearBus.class
            && bus.findCheeseExtractionRecipe(stack) != null) {
            return true;
        }

        // 2. Control rods can ONLY go into control rod hatches!
        if (MTEHatchNuclearControlRod.isControlRod(stack)) return false;

        // 3. Depleted rods and empty fuel rods are strictly forbidden from reactor core hatches!
        if (isDepletedOrEmptyRod(stack)) return false;

        // 4. Hull-only components (interact with nonexistent reactor hull heat) are strictly forbidden!
        if (isHullOnlyComponent(stack)) return false;

        // 5. Explicitly allowlisted reactor core items:
        if (isItemFuel(stack)) return true;
        if (isFuelAtMaxDamage(stack)) return true;
        if (isItemCoolant(stack)) return true;
        if (isItemHeatVent(stack)) return true;
        if (isItemHeatExchanger(stack)) return true;
        if (isItemReflector(stack)) return true;
        if (isReflectorAtMaxDamage(stack)) return true;
        if (isItemModerator(stack)) return true;
        if (isModeratorAtMaxDamage(stack)) return true;
        if (getInsulationDampening(stack) > 0.0) return true;
        if (isItemRadiovoltaic(stack)) return true;
        if (isItemNeutronSource(stack)) return true;

        return false;
    }

    public static boolean isItemNeutronSource(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        if (!neutronSourceInitialized) {
            try {
                if (goodgenerator.loader.Loaders.neutronSource != null) {
                    cachedNeutronSourceItem = goodgenerator.loader.Loaders.neutronSource;
                    neutronSourceInitialized = true;
                }
            } catch (Throwable ignored) {}
            if (cachedNeutronSourceItem == null) {
                try {
                    cachedNeutronSourceItem = cpw.mods.fml.common.registry.GameRegistry
                        .findItem("GoodGenerator", "neutronSource");
                    if (cachedNeutronSourceItem != null) {
                        neutronSourceInitialized = true;
                    }
                } catch (Throwable ignored) {}
            }
        }
        if (cachedNeutronSourceItem != null) {
            return stack.getItem() == cachedNeutronSourceItem;
        }
        try {
            net.minecraft.item.Item item = stack.getItem();
            cpw.mods.fml.common.registry.GameRegistry.UniqueIdentifier uid = cpw.mods.fml.common.registry.GameRegistry
                .findUniqueIdentifierFor(item);
            if (uid != null && "GoodGenerator".equalsIgnoreCase(uid.modId)
                && "neutronSource".equalsIgnoreCase(uid.name)) {
                cachedNeutronSourceItem = item;
                neutronSourceInitialized = true;
                return true;
            }
            if ("item.neutronSource".equalsIgnoreCase(item.getUnlocalizedName())) {
                cachedNeutronSourceItem = item;
                neutronSourceInitialized = true;
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static ItemStack getItemDepletedForm(ItemStack fuel) {
        if (fuel == null || isDepletedOrEmptyRod(fuel)) return null;

        // 0. Check custom fuel rods
        if (fuel.getItem() instanceof ItemCustomFuelRod || CustomFuelMix.hasCustomMix(fuel)) {
            net.minecraft.item.Item depletedItem = ModItems.rodCustomDepletedFuel4;
            if (fuel.getItem() instanceof ItemCustomFuelRod customRod) {
                if (customRod.rodSize == 1) {
                    depletedItem = ModItems.rodCustomDepletedFuel1;
                } else if (customRod.rodSize == 2) {
                    depletedItem = ModItems.rodCustomDepletedFuel2;
                }
            }
            ItemStack depleted = new ItemStack(depletedItem != null ? depletedItem : ModItems.rodCustomDepletedFuel4);
            if (fuel.getTagCompound() != null) {
                depleted.setTagCompound(
                    (NBTTagCompound) fuel.getTagCompound()
                        .copy());
            }
            return depleted;
        }

        // 1. Check YAML FuelData spentProduct
        com.gtnewhorizons.modularnuclear.common.config.data.FuelData fd = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .findFuel(fuel);
        if (fd != null && fd.spentProduct != null && !fd.spentProduct.isEmpty()) {
            ItemStack spent = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
                .resolveItemStack(fd.spentProduct);
            if (spent != null) {
                return spent;
            }
        }

        // 2. Direct ACTIVE_FUEL_RODS -> DEPLETED_FUEL_RODS mapping (handles Lithium & Glowstone explicitly)
        for (int i = 0; i < ACTIVE_FUEL_RODS.length && i < DEPLETED_FUEL_RODS.length; i++) {
            ItemList active = ACTIVE_FUEL_RODS[i];
            ItemList depleted = DEPLETED_FUEL_RODS[i];
            if (active != null && active.hasBeenSet() && active.isStackEqual(fuel, false, true)) {
                if (depleted != null && depleted.hasBeenSet()) {
                    return depleted.get(1L);
                }
            }
        }

        // 3. Check if fuel is ItemRadioactiveCellIC with sDepleted
        if (fuel.getItem() instanceof ItemRadioactiveCellIC icCell && icCell.sDepleted != null) {
            return icCell.sDepleted.copy();
        }

        // 4. Check IC2 native fuel items
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

        return null;
    }
}

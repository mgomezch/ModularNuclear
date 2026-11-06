package com.gtnewhorizons.modularnuclear.common.util;

import java.util.Locale;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig;
import com.gtnewhorizons.modularnuclear.common.config.ReactorConfigData;
import com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData;
import com.gtnewhorizons.modularnuclear.common.config.data.FuelData;
import com.gtnewhorizons.modularnuclear.common.item.ItemCustomDepletedFuelRod;
import com.gtnewhorizons.modularnuclear.common.item.ItemCustomFuelRod;
import com.gtnewhorizons.modularnuclear.common.item.ModItems;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearBus;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod;
import com.gtnewhorizons.modularnuclear.common.nuclear.CustomFuelMix;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearFuelType;
import com.gtnewhorizons.modularnuclear.common.recipe.CheeseRecipeRegistry;

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

    /** Each item kind's roles (see ItemRoles), until the configuration reloads (see roles). */
    private static final LongKeyMap<ItemRoles> ROLES = new LongKeyMap<>();
    private static volatile long rolesGeneration = -1;
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
        ROLES.clear();
        FluidRoles.clearCache();
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
                        long k = ReactorDataRegistry.getItemKey(is);
                        if (k != 0L) ACTIVE_ROD_KEYS.add(k);
                    }
                }
            }
            for (ItemList item : DEPLETED_FUEL_RODS) {
                if (item != null && item.hasBeenSet()) {
                    ItemStack is = item.get(1L);
                    if (is != null) {
                        long k = ReactorDataRegistry.getItemKey(is);
                        if (k != 0L) DEPLETED_ROD_KEYS.add(k);
                    }
                }
            }
            if (ItemList.IC2_Fuel_Rod_Empty != null && ItemList.IC2_Fuel_Rod_Empty.hasBeenSet()) {
                ItemStack is = ItemList.IC2_Fuel_Rod_Empty.get(1L);
                if (is != null) {
                    long k = ReactorDataRegistry.getItemKey(is);
                    if (k != 0L) DEPLETED_ROD_KEYS.add(k);
                }
            }
            for (ItemList item : GT_COOLANT_CELLS) {
                if (item != null && item.hasBeenSet()) {
                    ItemStack is = item.get(1L);
                    if (is != null) {
                        long k = ReactorDataRegistry.getItemKey(is);
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
            long k = ReactorDataRegistry.getItemKey(stack);
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
        } catch (Throwable swallowed) {
            Swallowed.note(swallowed);
        }
        return false;
    }

    private static boolean scanIsIC2ActiveFuelCell(ItemStack stack) {
        if (stack == null) return false;
        initKeys();
        long key = ReactorDataRegistry.getItemKey(stack);
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

    private static boolean scanIsIC2DepletedFuelCell(ItemStack stack) {
        if (stack == null) return false;
        initKeys();
        long key = ReactorDataRegistry.getItemKey(stack);
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
        return depletedReason(stack) != null;
    }

    /** Why a core bus would not take the item as fuel, for the reactor GUI; null if nothing rules it out. */
    public enum NotFuelReason {

        /** A depleted rod or cell (GregTech, IC2 or custom). */
        DEPLETED,
        /** Listed in the config's blockedItems. */
        BLOCKED_BY_CONFIG,
        /** An empty fuel rod, or a custom rod with no fuel mix. */
        EMPTY_ROD,
        /** Configured as the spent product of a fuel in fuels.yaml. */
        SPENT_PRODUCT,
        /** No fuel definition matches it. */
        NOT_A_FUEL;

        public String langKey() {
            return "modularnuclear.gui.not_fuel." + name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Why an item in a core bus is not burning when a player would expect it to (a depleted or empty rod, a spent
     * product, an item the config blocks), or null for fuel and for components never meant to burn.
     */
    public static NotFuelReason unexpectedNotFuel(ItemStack stack) {
        NotFuelReason reason = whyNotFuel(stack);
        return reason == NotFuelReason.NOT_A_FUEL ? null : reason;
    }

    /** Why the item is not fuel, or null if it is. */
    public static NotFuelReason whyNotFuel(ItemStack stack) {
        if (stack == null) return NotFuelReason.NOT_A_FUEL;
        NotFuelReason depleted = depletedReason(stack);
        if (depleted != null) return depleted;
        return isItemFuel(stack) ? null : NotFuelReason.NOT_A_FUEL;
    }

    private static NotFuelReason depletedReason(ItemStack stack) {
        if (stack == null) return null;
        return notFuel(roles(stack), stack);
    }

    /** Why the stack is not fuel, from its kind's roles plus the one per-stack check (a custom rod's fuel mix). */
    private static NotFuelReason notFuel(ItemRoles r, ItemStack stack) {
        if (r.notFuelBeforeMix != null) return r.notFuelBeforeMix;
        if (r.customRod && !CustomFuelMix.hasCustomMix(stack)) return NotFuelReason.EMPTY_ROD;
        return r.notFuelAfterMix;
    }

    /** The depleted / blocked checks that come before a custom rod's mix check. */
    private static NotFuelReason scanNotFuelBeforeMix(ItemStack stack) {
        initKeys();
        long key = ReactorDataRegistry.getItemKey(stack);
        if (key != 0L && (DEPLETED_ROD_KEYS.contains(key) || IC2_DEPLETED_KEYS.contains(key))) {
            return NotFuelReason.DEPLETED;
        }

        if (ModularNuclearConfig.isItemBlocked(stack)) {
            return NotFuelReason.BLOCKED_BY_CONFIG;
        }
        return null;
    }

    /** The depleted / empty / spent checks that come after a custom rod's mix check. */
    private static NotFuelReason scanNotFuelAfterMix(ItemStack stack) {
        if (stack.getItem() instanceof ItemCustomDepletedFuelRod) return NotFuelReason.DEPLETED;
        if (ModItems.rodCustomDepletedFuel1 != null && stack.getItem() == ModItems.rodCustomDepletedFuel1)
            return NotFuelReason.DEPLETED;
        if (ModItems.rodCustomDepletedFuel2 != null && stack.getItem() == ModItems.rodCustomDepletedFuel2)
            return NotFuelReason.DEPLETED;
        if (ModItems.rodCustomDepletedFuel4 != null && stack.getItem() == ModItems.rodCustomDepletedFuel4)
            return NotFuelReason.DEPLETED;

        if (Ic2Items.fuelRod != null && GTUtility.areStacksEqual(stack, Ic2Items.fuelRod, true))
            return NotFuelReason.EMPTY_ROD;
        if (ReactorDataRegistry.isItemListStack(ItemList.IC2_Fuel_Rod_Empty, stack)) return NotFuelReason.EMPTY_ROD;

        if (scanIsIC2DepletedFuelCell(stack)) return NotFuelReason.DEPLETED;

        for (ItemList item : DEPLETED_FUEL_RODS) {
            if (ReactorDataRegistry.isItemListStack(item, stack)) {
                return NotFuelReason.DEPLETED;
            }
        }

        if (ReactorDataRegistry.isSpentProduct(stack)) {
            return NotFuelReason.SPENT_PRODUCT;
        }

        return null;
    }

    private static boolean isCustomFuelRodItem(Item item) {
        return item instanceof ItemCustomFuelRod || (ModItems.rodCustomFuel1 != null && item == ModItems.rodCustomFuel1)
            || (ModItems.rodCustomFuel2 != null && item == ModItems.rodCustomFuel2)
            || (ModItems.rodCustomFuel4 != null && item == ModItems.rodCustomFuel4);
    }

    // ---- Item roles: worked out once per item kind, read every cycle ----

    /**
     * What the stack's kind of item is to a reactor core (see {@link ItemRoles}). Worked out the first time the kind is
     * seen and kept until the configuration reloads, so this is a table lookup.
     */
    public static ItemRoles roles(ItemStack stack) {
        if (stack == null) return ItemRoles.NONE;
        long gen = configGeneration();
        if (gen != rolesGeneration) {
            ROLES.clear();
            rolesGeneration = gen;
        }
        long key = ReactorDataRegistry.getItemKey(stack);
        // An item with no registry id (never in the game) has no key to cache under
        if (key == 0L) return scanRoles(stack);
        ItemRoles r = ROLES.get(key);
        if (r == null) {
            r = scanRoles(stack);
            ROLES.put(key, r);
        }
        return r;
    }

    /** Changes whenever either configuration does (the YAML files, or the Forge config's item and fluid lists). */
    static long configGeneration() {
        return ((long) ReactorConfigData.generation() << 32) | (ModularNuclearConfig.generation() & 0xFFFFFFFFL);
    }

    /** The slow part: ore dictionary, registry names and the configuration's pattern lists, once per item kind. */
    private static ItemRoles scanRoles(ItemStack stack) {
        initKeys();
        ItemRoles r = new ItemRoles();
        Item item = stack.getItem();
        long key = ReactorDataRegistry.getItemKey(stack);

        r.customRod = isCustomFuelRodItem(item);
        r.notFuelBeforeMix = scanNotFuelBeforeMix(stack);
        r.notFuelAfterMix = scanNotFuelAfterMix(stack);
        r.gtActiveRod = key != 0L && ACTIVE_ROD_KEYS.contains(key);

        // Custom rods build their fuel record from the stack's fuel mix (ReactorDataRegistry.findFuel)
        r.fuelData = r.customRod ? null : ReactorDataRegistry.findFuel(stack);
        if (r.customRod) {
            r.fuelForm = ItemRoles.FuelForm.CUSTOM_ROD;
        } else if (r.gtActiveRod || (key != 0L && IC2_ACTIVE_KEYS.contains(key))
            || r.fuelData != null
            || ModularNuclearConfig.isItemFuel(stack)
            || scanIsIC2ActiveFuelCell(stack)) {
                r.fuelForm = ItemRoles.FuelForm.FUEL;
            } else if (item instanceof ItemRadioactiveCell) {
                r.fuelForm = NuclearFuelType.fromName(stack.getUnlocalizedName()) != null ? ItemRoles.FuelForm.WORN_CELL
                    : ItemRoles.FuelForm.NONE;
            } else if (item instanceof ItemReactorUranium) {
                r.fuelForm = ItemRoles.FuelForm.IC2_URANIUM;
            }
        r.fuelType = r.fuelData != null ? NuclearFuelType.fromFuelData(r.fuelData)
            : NuclearFuelType.fromName(stack.getUnlocalizedName());

        r.hullOnly = scanIsHullOnlyComponent(stack);
        r.coolantItemData = ReactorDataRegistry.findCoolantItem(stack);
        r.coolant = !r.hullOnly && scanIsItemCoolant(stack, key);
        r.heatVentData = ReactorDataRegistry.findHeatVent(stack);
        r.heatVent = !r.hullOnly && (r.heatVentData != null || ModularNuclearConfig.isItemHeatVent(stack));
        r.heatExchangerData = ReactorDataRegistry.findHeatExchanger(stack);
        r.heatExchanger = !r.hullOnly
            && (r.heatExchangerData != null || ModularNuclearConfig.isItemHeatExchanger(stack));
        r.reflectorData = ReactorDataRegistry.findReflector(stack);
        r.reflector = !r.hullOnly && (r.reflectorData != null || ModularNuclearConfig.isItemReflector(stack));
        r.moderatorData = ReactorDataRegistry.findModerator(stack);
        r.moderator = !r.hullOnly && (r.moderatorData != null || ModularNuclearConfig.isItemModerator(stack));
        r.radiovoltaicData = ReactorDataRegistry.findRadiovoltaic(stack);
        r.radiovoltaic = !r.hullOnly && (r.radiovoltaicData != null || ModularNuclearConfig.isItemRadiovoltaic(stack));
        if (!r.hullOnly) {
            ComponentData.InsulatorData ins = ReactorDataRegistry.findInsulator(stack);
            r.insulationDampening = ins != null ? ins.dampening : ModularNuclearConfig.getInsulatorDampening(stack);
        }
        r.neutronSource = scanIsItemNeutronSource(stack);
        r.controlRodData = ReactorDataRegistry.findControlRod(stack);
        r.controlRod = MTEHatchNuclearControlRod.isControlRod(stack);
        r.allowedInBus = !r.customRod && scanIsItemAllowedInBus(r, stack);
        return r;
    }

    public static boolean isFuelAtMaxDamage(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        return isFuelAtMaxDamage(roles(stack), stack);
    }

    private static boolean isFuelAtMaxDamage(ItemRoles r, ItemStack stack) {
        if (notFuel(r, stack) != null) return false;

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

        FuelData fd = r.fuelData;
        if (fd != null) {
            int maxDur = fd.defaultDurability > 0 ? fd.defaultDurability : stack.getMaxDamage();
            return stack.isItemStackDamageable() && stack.getItemDamage() >= maxDur;
        }

        if (stack.isItemStackDamageable() && stack.getItemDamage() >= stack.getMaxDamage()) {
            return r.gtActiveRod;
        }

        return false;
    }

    public static boolean isModeratorAtMaxDamage(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        ComponentData.ModeratorData mod = roles(stack).moderatorData;
        if (mod != null && stack.stackTagCompound != null && stack.stackTagCompound.hasKey("advDmg")) {
            return stack.stackTagCompound.getInteger("advDmg") >= mod.durability;
        }
        return false;
    }

    public static boolean isReflectorAtMaxDamage(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        ComponentData.ReflectorData refl = roles(stack).reflectorData;
        if (refl != null && refl.durability > 0
            && stack.stackTagCompound != null
            && stack.stackTagCompound.hasKey("advDmg")) {
            return stack.stackTagCompound.getInteger("advDmg") >= refl.durability;
        }
        return false;
    }

    public static ItemStack getModeratorSpentProduct(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return null;
        ComponentData.ModeratorData mod = ReactorDataRegistry.findModerator(stack);
        if (mod != null && mod.spentProduct != null && !mod.spentProduct.isEmpty()) {
            return ReactorDataRegistry.resolveItemStack(mod.spentProduct);
        }
        return null;
    }

    public static boolean isItemFuel(ItemStack stack) {
        if (stack == null) return false;
        return isItemFuel(roles(stack), stack);
    }

    private static boolean isItemFuel(ItemRoles r, ItemStack stack) {
        if (notFuel(r, stack) != null) return false;
        switch (r.fuelForm) {
            case FUEL:
                return true;
            case CUSTOM_ROD: {
                // A custom rod with a readable mix is fuel (ReactorDataRegistry.findFuel builds its record); one whose
                // mix cannot be read burns until the default custom rod life
                return CustomFuelMix.fromStack(stack) != null || stack.getItemDamage() < 160000;
            }
            case WORN_CELL: {
                ItemRadioactiveCell radCell = (ItemRadioactiveCell) stack.getItem();
                return radCell.getDamageOfStack(stack) < radCell.getMaxDamageEx();
            }
            case IC2_URANIUM:
                return stack.getItemDamage() < stack.getMaxDamage();
            default:
                return false;
        }
    }

    public static boolean isFluidFuel(FluidStack fluid) {
        if (fluid == null || fluid.getFluid() == null) return false;
        return FluidRoles.of(fluid.getFluid())
            .isFuel();
    }

    public static Fluid getSpentFluid(FluidStack fuel) {
        if (fuel == null || fuel.getFluid() == null) return null;
        return FluidRoles.of(fuel.getFluid())
            .spentFluid();
    }

    public static boolean isCoolantFluid(FluidStack fluid) {
        if (fluid == null || fluid.getFluid() == null) return false;
        return FluidRoles.of(fluid.getFluid())
            .isCoolant();
    }

    public static boolean isHullOnlyComponent(ItemStack stack) {
        return stack != null && roles(stack).hullOnly;
    }

    private static boolean scanIsHullOnlyComponent(ItemStack stack) {
        initKeys();
        long key = ReactorDataRegistry.getItemKey(stack);
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
        return stack != null && roles(stack).coolant;
    }

    private static boolean scanIsItemCoolant(ItemStack stack, long key) {
        if (key != 0L && GT_COOLANT_KEYS.contains(key)) return true;
        if (ReactorDataRegistry.findCoolantItem(stack) != null) return true;
        if (ModularNuclearConfig.isItemCoolant(stack)) return true;
        if (stack.getItem() instanceof ItemCoolantCell) return true;
        if (stack.getItem() instanceof ItemReactorHeatStorage) return true;
        for (ItemList item : GT_COOLANT_CELLS) {
            if (ReactorDataRegistry.isItemListStack(item, stack)) {
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

    public static boolean isItemHeatVent(ItemStack stack) {
        return stack != null && roles(stack).heatVent;
    }

    public static boolean isItemHeatExchanger(ItemStack stack) {
        return stack != null && roles(stack).heatExchanger;
    }

    public static boolean isItemReflector(ItemStack stack) {
        return stack != null && roles(stack).reflector;
    }

    public static boolean isItemModerator(ItemStack stack) {
        return stack != null && roles(stack).moderator;
    }

    public static boolean isItemRadiovoltaic(ItemStack stack) {
        return stack != null && roles(stack).radiovoltaic;
    }

    public static double getInsulationDampening(ItemStack stack) {
        return stack != null ? roles(stack).insulationDampening : 0.0;
    }

    public static boolean isItemAllowedInBus(ItemStack stack) {
        return isItemAllowedInBus(stack, null);
    }

    public static boolean isItemAllowedInBus(ItemStack stack, MTEHatchNuclearBus bus) {
        if (stack == null) return false;
        ItemRoles r = roles(stack);

        // Custom fuel rods require valid NBT mix
        if (r.customRod) return CustomFuelMix.hasCustomMix(stack);

        // Cheese, for the molten cheese extraction
        if (bus != null && CheeseRecipeRegistry.getInstance()
            .findRecipe(stack) != null) {
            return true;
        }
        return r.allowedInBus;
    }

    /** Whether a plain core bus takes the item kind, from the roles worked out so far (see scanRoles). */
    private static boolean scanIsItemAllowedInBus(ItemRoles r, ItemStack stack) {
        // 1. Dynamic cheese extraction Easter egg inputs:
        if (CheeseRecipeRegistry.getInstance()
            .isCheeseInput(stack)) {
            return true;
        }
        // 2. Control rods can ONLY go into control rod hatches!
        if (r.controlRod) return false;

        // 3. Depleted rods and empty fuel rods are strictly forbidden from reactor core hatches!
        if (notFuel(r, stack) != null) return false;

        // 4. Hull-only components (interact with nonexistent reactor hull heat) are strictly forbidden!
        if (r.hullOnly) return false;

        // 5. Explicitly allowlisted reactor core items:
        if (isItemFuel(r, stack)) return true;
        if (isFuelAtMaxDamage(r, stack)) return true;
        if (r.coolant || r.heatVent || r.heatExchanger || r.reflector || r.moderator) return true;
        if (r.insulationDampening > 0.0 || r.radiovoltaic || r.neutronSource) return true;

        return false;
    }

    public static boolean isItemNeutronSource(ItemStack stack) {
        return stack != null && roles(stack).neutronSource;
    }

    private static boolean scanIsItemNeutronSource(ItemStack stack) {
        if (!neutronSourceInitialized) {
            try {
                if (goodgenerator.loader.Loaders.neutronSource != null) {
                    cachedNeutronSourceItem = goodgenerator.loader.Loaders.neutronSource;
                    neutronSourceInitialized = true;
                }
            } catch (Throwable swallowed) {
                Swallowed.note(swallowed);
            }
            if (cachedNeutronSourceItem == null) {
                try {
                    cachedNeutronSourceItem = cpw.mods.fml.common.registry.GameRegistry
                        .findItem("GoodGenerator", "neutronSource");
                    if (cachedNeutronSourceItem != null) {
                        neutronSourceInitialized = true;
                    }
                } catch (Throwable swallowed) {
                    Swallowed.note(swallowed);
                }
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
        } catch (Throwable swallowed) {
            Swallowed.note(swallowed);
        }
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
        FuelData fd = roles(fuel).fuelData;
        if (fd != null && fd.spentProduct != null && !fd.spentProduct.isEmpty()) {
            ItemStack spent = ReactorDataRegistry.resolveItemStack(fd.spentProduct);
            if (spent != null) {
                return spent;
            }
        }

        // 2. Direct ACTIVE_FUEL_RODS -> DEPLETED_FUEL_RODS mapping (handles Lithium & Glowstone explicitly)
        for (int i = 0; i < ACTIVE_FUEL_RODS.length && i < DEPLETED_FUEL_RODS.length; i++) {
            ItemList active = ACTIVE_FUEL_RODS[i];
            ItemList depleted = DEPLETED_FUEL_RODS[i];
            if (ReactorDataRegistry.isItemListStack(active, fuel)) {
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

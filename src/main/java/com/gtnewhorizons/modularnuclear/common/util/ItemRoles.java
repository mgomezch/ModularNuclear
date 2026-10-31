package com.gtnewhorizons.modularnuclear.common.util;

import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData;
import com.gtnewhorizons.modularnuclear.common.config.data.FuelData;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearFuelType;

/**
 * What one kind of item (item and metadata, see {@code ReactorDataRegistry.getItemKey}) is to a reactor core: fuel,
 * coolant, moderator, its configuration records, and so on. Working this out means ore dictionary names, registry
 * names and the configuration's pattern lists, so {@link NuclearFuelClassification} does it once per kind and keeps the
 * answer until the configuration reloads; reactors read it every cycle. What depends on the one stack (a fuel rod's
 * wear, a custom rod's fuel mix) is checked per stack by NuclearFuelClassification.
 * <p>
 * Filled in by {@link NuclearFuelClassification} before it is shared, and never changed after.
 */
public final class ItemRoles {

    /** How a fuel item's stack decides whether it still burns. */
    public enum FuelForm {
        /** Not fuel. */
        NONE,
        /** Always fuel (a configured fuel or a known active rod). */
        FUEL,
        /** A custom fuel rod: fuel while it carries a fuel mix. */
        CUSTOM_ROD,
        /** A GregTech radioactive cell found by name: fuel until worn out. */
        WORN_CELL,
        /** An IC2 uranium or MOX cell: fuel until worn out. */
        IC2_URANIUM
    }

    /** The roles of nothing (a null stack). */
    public static final ItemRoles NONE = new ItemRoles();

    /** Why it is not fuel, from checks that come before a custom rod's mix check (depleted rod lists, config). */
    NuclearFuelClassification.NotFuelReason notFuelBeforeMix;
    /** Why it is not fuel, from the checks after it (custom depleted rods, IC2, spent products). */
    NuclearFuelClassification.NotFuelReason notFuelAfterMix;
    boolean customRod;
    FuelForm fuelForm = FuelForm.NONE;
    /** A GregTech active fuel rod (ItemList). */
    boolean gtActiveRod;

    FuelData fuelData;
    NuclearFuelType fuelType;
    ComponentData.ModeratorData moderatorData;
    ComponentData.ReflectorData reflectorData;
    ComponentData.HeatVentData heatVentData;
    ComponentData.HeatExchangerData heatExchangerData;
    ComponentData.RadiovoltaicData radiovoltaicData;
    ComponentData.CoolantItemData coolantItemData;
    ComponentData.ControlRodData controlRodData;

    boolean hullOnly;
    boolean coolant;
    boolean heatVent;
    boolean heatExchanger;
    boolean reflector;
    boolean moderator;
    boolean radiovoltaic;
    boolean neutronSource;
    boolean controlRod;
    double insulationDampening;
    /** Whether a core bus takes it (for items whose answer does not depend on the stack: not custom rods). */
    boolean allowedInBus;

    ItemRoles() {}

    public FuelForm fuelForm() {
        return fuelForm;
    }

    /** The configured fuel (fuels.yaml), or null; custom rods build theirs from the stack. */
    public FuelData fuelData() {
        return fuelData;
    }

    /** The built-in fuel type, from the configured fuel or the item's name, or null. */
    public NuclearFuelType fuelType() {
        return fuelType;
    }

    public ComponentData.ModeratorData moderatorData() {
        return moderatorData;
    }

    public ComponentData.ReflectorData reflectorData() {
        return reflectorData;
    }

    public ComponentData.HeatVentData heatVentData() {
        return heatVentData;
    }

    public ComponentData.HeatExchangerData heatExchangerData() {
        return heatExchangerData;
    }

    public ComponentData.RadiovoltaicData radiovoltaicData() {
        return radiovoltaicData;
    }

    public ComponentData.CoolantItemData coolantItemData() {
        return coolantItemData;
    }

    public ComponentData.ControlRodData controlRodData() {
        return controlRodData;
    }

    public boolean isCustomRod() {
        return customRod;
    }

    public boolean isHullOnly() {
        return hullOnly;
    }

    public boolean isCoolant() {
        return coolant;
    }

    public boolean isHeatVent() {
        return heatVent;
    }

    public boolean isHeatExchanger() {
        return heatExchanger;
    }

    public boolean isReflector() {
        return reflector;
    }

    public boolean isModerator() {
        return moderator;
    }

    public boolean isRadiovoltaic() {
        return radiovoltaic;
    }

    public boolean isNeutronSource() {
        return neutronSource;
    }

    public boolean isControlRod() {
        return controlRod;
    }

    public double insulationDampening() {
        return insulationDampening;
    }
}

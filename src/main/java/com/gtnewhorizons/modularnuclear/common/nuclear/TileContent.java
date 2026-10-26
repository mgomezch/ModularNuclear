package com.gtnewhorizons.modularnuclear.common.nuclear;

import com.gtnewhorizons.modularnuclear.common.config.ReactorConfigData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.CoolantFluidData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.CoolantItemData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.HeatExchangerData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.HeatVentData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.InsulatorData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ModeratorData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.RadiovoltaicData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ReflectorData;
import com.gtnewhorizons.modularnuclear.common.config.data.FuelData;

/**
 * What occupies a reactor grid cell, resolved to its configuration record. The game resolves item and fluid stacks
 * into a TileContent; the standalone simulator resolves its tile codes into one by config id. Both then compute tile
 * properties through {@link TileProperties}, so the two cannot drift apart.
 */
public final class TileContent {

    public enum Kind {
        /** Nothing in the bus or hatch. */
        EMPTY,
        /** Solid fuel rod (FuelData). */
        FUEL,
        /** Fluid fuel in a hatch (FuelData). */
        LIQUID_FUEL,
        MODERATOR,
        REFLECTOR,
        INSULATOR,
        RADIOVOLTAIC,
        HEAT_VENT,
        HEAT_EXCHANGER,
        /** Coolant cell or condensator item (CoolantItemData). */
        COOLANT_ITEM,
        /** Coolant fluid in a hatch (CoolantFluidData). */
        COOLANT_FLUID,
        NEUTRON_SOURCE,
        /** Nothing in a fluid hatch. */
        EMPTY_HATCH,
        // Items without a configuration entry, recognised by class or ore dictionary (game only).
        GENERIC_FUEL,
        GENERIC_REFLECTOR,
        GENERIC_MODERATOR,
        GENERIC_INSULATOR,
        GENERIC_NAQUARITE,
        /** {@link #data} is the radiovoltaic tier as an Integer. */
        GENERIC_RADIOVOLTAIC,
        GENERIC_COOLANT,
        /** A heat vent or heat exchanger item. */
        GENERIC_VENT,
        /** Any other item. */
        OTHER,
        /** Any other fluid. */
        OTHER_FLUID
    }

    public static final TileContent EMPTY = new TileContent(Kind.EMPTY, null);
    public static final TileContent NEUTRON_SOURCE = new TileContent(Kind.NEUTRON_SOURCE, null);
    public static final TileContent OTHER = new TileContent(Kind.OTHER, null);
    public static final TileContent GENERIC_REFLECTOR = new TileContent(Kind.GENERIC_REFLECTOR, null);
    public static final TileContent GENERIC_MODERATOR = new TileContent(Kind.GENERIC_MODERATOR, null);
    public static final TileContent GENERIC_COOLANT = new TileContent(Kind.GENERIC_COOLANT, null);
    public static final TileContent GENERIC_FUEL = new TileContent(Kind.GENERIC_FUEL, null);
    public static final TileContent GENERIC_INSULATOR = new TileContent(Kind.GENERIC_INSULATOR, null);
    public static final TileContent GENERIC_NAQUARITE = new TileContent(Kind.GENERIC_NAQUARITE, null);
    public static final TileContent GENERIC_VENT = new TileContent(Kind.GENERIC_VENT, null);
    public static final TileContent EMPTY_HATCH = new TileContent(Kind.EMPTY_HATCH, null);
    public static final TileContent OTHER_FLUID = new TileContent(Kind.OTHER_FLUID, null);

    public static TileContent genericRadiovoltaic(int tier) {
        return new TileContent(Kind.GENERIC_RADIOVOLTAIC, tier);
    }

    public int genericTier() {
        return data instanceof Integer i ? i : 0;
    }

    public final Kind kind;
    /** The configuration record for {@link #kind}, or null for the generic kinds. */
    public final Object data;

    private TileContent(Kind kind, Object data) {
        this.kind = kind;
        this.data = data;
    }

    public static TileContent fuel(FuelData fd) {
        return fd == null ? OTHER : new TileContent(fd.isLiquid ? Kind.LIQUID_FUEL : Kind.FUEL, fd);
    }

    public static TileContent of(Kind kind, Object data) {
        return data == null ? OTHER : new TileContent(kind, data);
    }

    public boolean isFuel() {
        return kind == Kind.FUEL || kind == Kind.LIQUID_FUEL;
    }

    public FuelData fuelData() {
        return isFuel() ? (FuelData) data : null;
    }

    public ModeratorData moderator() {
        return kind == Kind.MODERATOR ? (ModeratorData) data : null;
    }

    public ReflectorData reflector() {
        return kind == Kind.REFLECTOR ? (ReflectorData) data : null;
    }

    public InsulatorData insulator() {
        return kind == Kind.INSULATOR ? (InsulatorData) data : null;
    }

    public RadiovoltaicData radiovoltaic() {
        return kind == Kind.RADIOVOLTAIC ? (RadiovoltaicData) data : null;
    }

    public HeatVentData heatVent() {
        return kind == Kind.HEAT_VENT ? (HeatVentData) data : null;
    }

    public HeatExchangerData heatExchanger() {
        return kind == Kind.HEAT_EXCHANGER ? (HeatExchangerData) data : null;
    }

    public CoolantItemData coolantItem() {
        return kind == Kind.COOLANT_ITEM ? (CoolantItemData) data : null;
    }

    public CoolantFluidData coolantFluid() {
        return kind == Kind.COOLANT_FLUID ? (CoolantFluidData) data : null;
    }

    /**
     * Resolves a configuration id from fuels.yaml or components.yaml. Returns {@link #OTHER} when the id is not
     * configured.
     */
    public static TileContent byConfigId(String id) {
        if (id == null) return OTHER;
        FuelData fd = ReactorConfigData.findFuelById(id);
        if (fd != null) return fuel(fd);
        ComponentData c = ReactorConfigData.getComponentData();
        Object d;
        if ((d = ReactorConfigData.findById(c.moderators, x -> x.id, id)) != null) return of(Kind.MODERATOR, d);
        if ((d = ReactorConfigData.findById(c.reflectors, x -> x.id, id)) != null) return of(Kind.REFLECTOR, d);
        if ((d = ReactorConfigData.findById(c.insulators, x -> x.id, id)) != null) return of(Kind.INSULATOR, d);
        if ((d = ReactorConfigData.findById(c.radiovoltaics, x -> x.id, id)) != null) {
            return of(Kind.RADIOVOLTAIC, d);
        }
        if ((d = ReactorConfigData.findById(c.heatVents, x -> x.id, id)) != null) return of(Kind.HEAT_VENT, d);
        if ((d = ReactorConfigData.findById(c.heatExchangers, x -> x.id, id)) != null) {
            return of(Kind.HEAT_EXCHANGER, d);
        }
        if ((d = ReactorConfigData.findById(c.coolantItems, x -> x.id, id)) != null) {
            return of(Kind.COOLANT_ITEM, d);
        }
        if ((d = ReactorConfigData.findById(c.coolantFluids, x -> x.id, id)) != null) {
            return of(Kind.COOLANT_FLUID, d);
        }
        return OTHER;
    }

    @Override
    public String toString() {
        return "TileContent[" + kind + "]";
    }
}

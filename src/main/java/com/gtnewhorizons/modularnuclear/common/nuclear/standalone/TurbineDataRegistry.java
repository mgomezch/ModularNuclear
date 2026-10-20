package com.gtnewhorizons.modularnuclear.common.nuclear.standalone;

import java.util.List;

/**
 * Dynamic registry of turbine rotor materials with flow characteristics and efficiencies.
 */
public final class TurbineDataRegistry {

    public static final TurbineCalculator.TurbineMaterial HSS_E = TurbineCalculator.TurbineMaterial.HSS_E;
    public static final TurbineCalculator.TurbineMaterial HSS_S = TurbineCalculator.TurbineMaterial.HSS_S;
    public static final TurbineCalculator.TurbineMaterial ELVEN_ELEMENTIUM = TurbineCalculator.TurbineMaterial.ELVEN_ELEMENTIUM;
    public static final TurbineCalculator.TurbineMaterial ORINARUKON = TurbineCalculator.TurbineMaterial.ORINARUKON;
    public static final TurbineCalculator.TurbineMaterial SHADOW_METAL = TurbineCalculator.TurbineMaterial.SHADOW_METAL;
    public static final TurbineCalculator.TurbineMaterial ICHORIUM = TurbineCalculator.TurbineMaterial.ICHORIUM;
    public static final TurbineCalculator.TurbineMaterial DURANIUM = TurbineCalculator.TurbineMaterial.DURANIUM;
    public static final TurbineCalculator.TurbineMaterial GAIA_SPIRIT = TurbineCalculator.TurbineMaterial.GAIA_SPIRIT;
    public static final TurbineCalculator.TurbineMaterial ADAMANTIUM = TurbineCalculator.TurbineMaterial.ADAMANTIUM;
    public static final TurbineCalculator.TurbineMaterial EXT_UNST_NAQUADAH = TurbineCalculator.TurbineMaterial.EXT_UNST_NAQUADAH;
    public static final TurbineCalculator.TurbineMaterial COSMIC_NEUTRONIUM = TurbineCalculator.TurbineMaterial.COSMIC_NEUTRONIUM;
    public static final TurbineCalculator.TurbineMaterial INFINITY = TurbineCalculator.TurbineMaterial.INFINITY;
    public static final TurbineCalculator.TurbineMaterial TUNGSTENSTEEL = TurbineCalculator.TurbineMaterial.TUNGSTENSTEEL;
    public static final TurbineCalculator.TurbineMaterial TITANIUM = TurbineCalculator.TurbineMaterial.TITANIUM;

    private TurbineDataRegistry() {}

    public static TurbineCalculator.TurbineMaterial register(TurbineCalculator.TurbineMaterial mat) {
        return TurbineCalculator.TurbineMaterial.register(mat);
    }

    public static TurbineCalculator.TurbineMaterial get(String name) {
        return TurbineCalculator.TurbineMaterial.fromString(name);
    }

    public static List<TurbineCalculator.TurbineMaterial> getAll() {
        return TurbineCalculator.TurbineMaterial.getAll();
    }

    public static TurbineCalculator.TurbineMaterial[] values() {
        return TurbineCalculator.TurbineMaterial.values();
    }

    public static TurbineCalculator.TurbineMaterial fromString(String name) {
        return TurbineCalculator.TurbineMaterial.fromString(name);
    }

    public static TurbineCalculator.TurbineMaterial valueOf(String name) {
        return TurbineCalculator.TurbineMaterial.fromString(name);
    }
}

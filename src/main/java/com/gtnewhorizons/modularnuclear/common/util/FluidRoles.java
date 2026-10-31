package com.gtnewhorizons.modularnuclear.common.util;

import java.util.IdentityHashMap;
import java.util.Locale;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig;
import com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.CoolantFluidData;
import com.gtnewhorizons.modularnuclear.common.config.data.FuelData;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearFuelType;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;

import gregtech.api.enums.Materials;

/**
 * What a fluid is to a reactor core: liquid fuel or coolant, its configuration records, and the fluids it turns into
 * (spent fuel, hot coolant, transmutation products), resolved to {@link Fluid}s. Matching a fluid against the
 * configuration means comparing and normalizing names, so this is worked out once per fluid and kept until the
 * configuration reloads; reactors read it every cycle and every conduction sub-step. Never changed once shared.
 */
public final class FluidRoles {

    private static final FluidRoles NONE = new FluidRoles();

    /** Each fluid's roles (copy on write: reads take no lock), and the configuration generation they belong to. */
    private static volatile IdentityHashMap<Fluid, FluidRoles> cache = new IdentityHashMap<>();
    private static volatile long cacheGeneration = -1;

    private boolean fuel;
    private FuelData fuelData;
    private NuclearFuelType fuelType;
    private boolean coolant;
    private CoolantFluidData coolantData;
    private Fluid spentFluid;
    private Fluid hotFluid;
    private Fluid transmutationFluid;
    private boolean transmutationMakesOxygen;
    private String lowerName = "";
    /** Per casing tier (index tier + 1): 0 not known yet, 1 allowed, 2 not (see allowedAtTier). */
    private final byte[] allowedByTier = new byte[32];

    private FluidRoles() {}

    /** The fluid's roles; a table lookup once the fluid has been seen. */
    public static FluidRoles of(FluidStack stack) {
        return stack == null ? NONE : of(stack.getFluid());
    }

    public static FluidRoles of(Fluid fluid) {
        if (fluid == null) return NONE;
        if (NuclearFuelClassification.configGeneration() != cacheGeneration) clearCache();
        FluidRoles r = cache.get(fluid);
        if (r != null) return r;
        // A fluid missing from the registry (never in the game) is matched by name each time
        if (!FluidRegistry.isFluidRegistered(fluid)) return scan(fluid);
        return add(fluid);
    }

    public static synchronized void clearCache() {
        cache = new IdentityHashMap<>();
        cacheGeneration = NuclearFuelClassification.configGeneration();
    }

    private static synchronized FluidRoles add(Fluid fluid) {
        FluidRoles r = cache.get(fluid);
        if (r != null) return r;
        r = scan(fluid);
        IdentityHashMap<Fluid, FluidRoles> next = new IdentityHashMap<>(cache);
        next.put(fluid, r);
        cache = next;
        return r;
    }

    /** The slow part: the configuration's name patterns, once per fluid. */
    private static FluidRoles scan(Fluid fluid) {
        FluidRoles r = new FluidRoles();
        String name = fluid.getName();
        r.lowerName = name != null ? name.toLowerCase(Locale.ROOT) : "";

        r.fuelData = ReactorDataRegistry.findFuel(fluid);
        r.fuel = !ModularNuclearConfig.isFluidBlocked(fluid)
            && ((r.fuelData != null && r.fuelData.isLiquid) || ModularNuclearConfig.isFluidFuel(fluid));
        r.fuelType = r.fuelData != null ? NuclearFuelType.fromFuelData(r.fuelData) : NuclearFuelType.fromName(name);
        if (r.fuelData != null && r.fuelData.spentProduct != null) {
            r.spentFluid = findFluid(r.fuelData.spentProduct);
        }

        r.coolantData = ReactorDataRegistry.findCoolantFluid(fluid);
        r.coolant = r.coolantData != null || ModularNuclearConfig.isCoolantFluid(fluid);
        if (r.coolantData != null && r.coolantData.hotFluidName != null) {
            Fluid hot = findFluid(r.coolantData.hotFluidName);
            r.hotFluid = hot != null ? hot : FluidRegistry.getFluid("steam");
        }
        if (r.coolantData != null && r.coolantData.transmutationProduct != null) {
            String product = r.coolantData.transmutationProduct;
            r.transmutationFluid = findFluid(product);
            boolean deuterium = "deuterium".equalsIgnoreCase(product);
            boolean tritium = "tritium".equalsIgnoreCase(product);
            if (r.transmutationFluid == null && deuterium && Materials.Deuterium != null) {
                r.transmutationFluid = Materials.Deuterium.mGas != null ? Materials.Deuterium.mGas
                    : Materials.Deuterium.mFluid;
            } else if (r.transmutationFluid == null && tritium && Materials.Tritium != null) {
                r.transmutationFluid = Materials.Tritium.mGas != null ? Materials.Tritium.mGas
                    : Materials.Tritium.mFluid;
            }
            // Radiolysis of (heavy) water splits off oxygen too
            r.transmutationMakesOxygen = deuterium || tritium;
        }
        return r;
    }

    /** A fluid by registry name, with or without GregTech's "fluid." prefix. */
    private static Fluid findFluid(String name) {
        Fluid f = FluidRegistry.getFluid(name);
        if (f == null && name.startsWith("fluid.")) f = FluidRegistry.getFluid(name.substring(6));
        if (f == null && !name.startsWith("fluid.")) f = FluidRegistry.getFluid("fluid." + name);
        return f;
    }

    private static volatile Fluid oxygen;

    /** Oxygen, as radiolysis produces it. */
    public static Fluid oxygen() {
        Fluid ox = oxygen;
        if (ox == null) {
            ox = (Materials.Oxygen != null)
                ? (Materials.Oxygen.mGas != null ? Materials.Oxygen.mGas : Materials.Oxygen.mFluid)
                : null;
            if (ox == null) ox = FluidRegistry.getFluid("oxygen");
            if (ox == null) ox = FluidRegistry.getFluid("fluid.oxygen");
            oxygen = ox;
        }
        return ox;
    }

    /** Liquid fuel a core hatch burns (configured and not blocked). */
    public boolean isFuel() {
        return fuel;
    }

    /** The configured fuel matching the fluid, liquid or not, or null. */
    public FuelData fuelData() {
        return fuelData;
    }

    public NuclearFuelType fuelType() {
        return fuelType;
    }

    /** A coolant fluid (configured, or allowed in the Forge config). */
    public boolean isCoolant() {
        return coolant;
    }

    public CoolantFluidData coolantData() {
        return coolantData;
    }

    /** What the fuel turns into as it burns, or null. */
    public Fluid spentFluid() {
        return spentFluid;
    }

    /** What the coolant turns into as it takes heat (steam if its configured product is missing), or null. */
    public Fluid hotFluid() {
        return hotFluid;
    }

    /** What fast neutrons transmute the coolant into, or null. */
    public Fluid transmutationFluid() {
        return transmutationFluid;
    }

    public boolean transmutationMakesOxygen() {
        return transmutationMakesOxygen;
    }

    /**
     * Whether a core hatch on a casing of this tier takes the fluid ({@link NuclearSimulationEngine#isFluidAllowed}).
     */
    public boolean allowedAtTier(int pipeTier) {
        int i = pipeTier + 1;
        if (this == NONE) return false;
        if (i < 0 || i >= allowedByTier.length) return NuclearSimulationEngine.isFluidAllowed(lowerName, pipeTier);
        byte known = allowedByTier[i];
        if (known == 0) {
            known = NuclearSimulationEngine.isFluidAllowed(lowerName, pipeTier) ? (byte) 1 : (byte) 2;
            allowedByTier[i] = known;
        }
        return known == 1;
    }

    /** The fluid's registry name in lower case (CoreHatchRules works by name). */
    public String lowerName() {
        return lowerName;
    }
}

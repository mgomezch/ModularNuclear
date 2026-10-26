package com.gtnewhorizons.coolantloops.common.config;

import java.util.Map;

import com.gtnewhorizons.coolantloops.common.config.data.CoolantDeviceData;
import com.gtnewhorizons.coolantloops.common.config.data.CoolantFluidData;
import com.gtnewhorizons.coolantloops.common.config.data.CoolantPhysicsData;
import com.gtnewhorizons.coolantloops.common.config.data.CoolantPipeData;
import com.gtnewhorizons.coolantloops.engine.ConvectiveHeatTransferModel;
import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;
import com.gtnewhorizons.modularnuclear.common.config.MiniJson;

/**
 * Coolant loop configuration (hydrodynamic physics, fluids, pipes and devices) without any Minecraft, logging or YAML
 * dependency, shared by the game and the standalone/WebAssembly simulator.
 *
 * <p>
 * Built-in defaults come from {@link CoolantLoopDefaultsJson}, which the build generates from
 * {@code assets/coolantloops/default_config/*.yaml}; those YAML files are also what the game writes out as its
 * initial config files. The game's {@link CoolantLoopDataRegistry} loads the files on disk and hands each document to
 * this class.
 */
public final class CoolantLoopConfigData {

    private static CoolantPhysicsData physicsData = new CoolantPhysicsData();
    private static CoolantFluidData fluidData = new CoolantFluidData();
    private static CoolantPipeData pipeData = new CoolantPipeData();
    private static CoolantDeviceData deviceData = new CoolantDeviceData();
    private static ConvectiveHeatTransferModel convectiveModel = ConvectiveHeatTransferModel.DEFAULT;
    private static volatile boolean loaded = false;

    private CoolantLoopConfigData() {}

    /** Loads the built-in defaults (the shipped default YAML files). */
    public static synchronized void loadDefaults() {
        setPhysics(MiniJson.parse(CoolantLoopDefaultsJson.physics()));
        setFluids(MiniJson.parse(CoolantLoopDefaultsJson.fluids()));
        setPipes(MiniJson.parse(CoolantLoopDefaultsJson.pipes()));
        setDevices(MiniJson.parse(CoolantLoopDefaultsJson.devices()));
        finishLoading();
    }

    /** Loads the built-in defaults if nothing has been loaded yet. */
    public static void ensureLoaded() {
        if (!loaded) loadIfNeeded();
    }

    private static synchronized void loadIfNeeded() {
        if (!loaded) loadDefaults();
    }

    /** Resets every section to the data classes' own field defaults (as the game does without config files). */
    public static synchronized void resetToClassDefaults() {
        physicsData = new CoolantPhysicsData();
        fluidData = new CoolantFluidData();
        pipeData = new CoolantPipeData();
        deviceData = new CoolantDeviceData();
        convectiveModel = ConvectiveHeatTransferModel.DEFAULT;
        CoolantFluidProperty.resetDefaults();
        loaded = true;
    }

    @SuppressWarnings("unchecked")
    public static synchronized void setPhysics(Object doc) {
        if (doc instanceof Map<?, ?>m) physicsData = CoolantPhysicsData.fromMap((Map<String, Object>) m);
    }

    @SuppressWarnings("unchecked")
    public static synchronized void setFluids(Object doc) {
        if (doc instanceof Map<?, ?>m) fluidData = CoolantFluidData.fromMap((Map<String, Object>) m);
    }

    @SuppressWarnings("unchecked")
    public static synchronized void setPipes(Object doc) {
        if (doc instanceof Map<?, ?>m) pipeData = CoolantPipeData.fromMap((Map<String, Object>) m);
    }

    @SuppressWarnings("unchecked")
    public static synchronized void setDevices(Object doc) {
        if (doc instanceof Map<?, ?>m) deviceData = CoolantDeviceData.fromMap((Map<String, Object>) m);
    }

    public static synchronized void setDeviceData(CoolantDeviceData data) {
        deviceData = (data != null) ? data : new CoolantDeviceData();
    }

    /** Builds the derived state (convective model, runtime fluid registry) after the sections are set. */
    public static synchronized void finishLoading() {
        convectiveModel = new ConvectiveHeatTransferModel(
            physicsData.getVTurbulent(),
            physicsData.getVSaturation(),
            physicsData.getHLaminarSlope(),
            physicsData.getHTurbulentFactor(),
            physicsData.getHMax());
        applyFluidRegistry();
        loaded = true;
    }

    /** Applies configured fluid properties into the runtime registry of CoolantFluidProperty. */
    private static void applyFluidRegistry() {
        for (Map.Entry<String, CoolantFluidData.FluidPropertyEntry> entry : fluidData.getFluids()
            .entrySet()) {
            CoolantFluidData.FluidPropertyEntry prop = entry.getValue();
            CoolantFluidProperty canonical = CoolantFluidProperty.getCanonical(prop.getFluidName());
            if (canonical == null) {
                canonical = CoolantFluidProperty.getCanonical(entry.getKey());
            }

            CoolantFluidProperty runtimeProp;
            if (canonical != null && isClose(canonical.getDensity(), prop.getDensity())
                && isClose(canonical.getDynamicViscosity(), prop.getDynamicViscosity())
                && isClose(canonical.getSpecificHeat(), prop.getSpecificHeat())
                && isClose(canonical.getThermalConductivity(), prop.getThermalConductivity())
                && isClose(canonical.getBoilingPointCelsius(), prop.getBoilingPointCelsius())
                && isClose(canonical.getFreezingPointCelsius(), prop.getFreezingPointCelsius())
                && isClose(canonical.getRadiolysisMultiplier(), prop.getRadiolysisMultiplier())
                && canonical.isMolten() == prop.isMolten()
                && canonical.isSpillOnRupture() == prop.isSpillOnRupture()) {
                runtimeProp = canonical;
            } else {
                runtimeProp = new CoolantFluidProperty(
                    prop.getFluidName(),
                    prop.getDensity(),
                    prop.getDynamicViscosity(),
                    prop.getSpecificHeat(),
                    prop.getThermalConductivity(),
                    prop.getBoilingPointCelsius(),
                    prop.getFreezingPointCelsius(),
                    prop.isMolten(),
                    prop.getRadiolysisMultiplier(),
                    prop.isSpillOnRupture());
            }

            CoolantFluidProperty.register(runtimeProp);
            CoolantFluidProperty.registerAlias(lower(entry.getKey()), runtimeProp);
            if (prop.getAliases() != null) {
                for (String alias : prop.getAliases()) {
                    if (alias != null && !alias.isEmpty()) {
                        CoolantFluidProperty.registerAlias(lower(alias), runtimeProp);
                    }
                }
            }
        }
    }

    private static boolean isClose(double a, double b) {
        if (Double.isInfinite(a) || Double.isInfinite(b)) return a == b;
        if (Double.isNaN(a) && Double.isNaN(b)) return true;
        return Math.abs(a - b) < 1e-4;
    }

    private static String lower(String s) {
        return s.toLowerCase(java.util.Locale.ROOT);
    }

    /** Lower case with spaces, underscores and dashes removed (TeaVM-safe; no String.replace). */
    public static String normalizeMaterial(String materialName) {
        StringBuilder sb = new StringBuilder(materialName.length());
        for (int i = 0; i < materialName.length(); i++) {
            char c = materialName.charAt(i);
            if (c != ' ' && c != '_' && c != '-') sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    // Lookups

    public static ConvectiveHeatTransferModel getConvectiveModel() {
        ensureLoaded();
        return convectiveModel;
    }

    public static CoolantPhysicsData getPhysicsData() {
        ensureLoaded();
        return physicsData;
    }

    public static CoolantFluidData getFluidData() {
        ensureLoaded();
        return fluidData;
    }

    public static CoolantPipeData getPipeData() {
        ensureLoaded();
        return pipeData;
    }

    public static CoolantDeviceData getDeviceData() {
        ensureLoaded();
        return deviceData;
    }

    public static double getPipePressureRating(String materialName) {
        if (materialName == null) return 10.0;
        Double rating = getPipeData().getMaterialPressureRatingsBar()
            .get(normalizeMaterial(materialName));
        return rating != null ? rating : 15.0;
    }

    public static double getPipeTemperatureLimit(String materialName) {
        if (materialName == null) return 200.0;
        Double limit = getPipeData().getMaterialTemperatureLimitsCelsius()
            .get(normalizeMaterial(materialName));
        return limit != null ? limit : 200.0;
    }

    public static double getDiameterForThickness(float thickness) {
        Map<String, Double> d = getPipeData().getThicknessDiameters();
        if (thickness <= 0.251f) return d.getOrDefault("tiny", 0.05);
        if (thickness <= 0.376f) return d.getOrDefault("small", 0.075);
        if (thickness <= 0.501f) return d.getOrDefault("normal", 0.10);
        if (thickness <= 0.751f) return d.getOrDefault("large", 0.15);
        return d.getOrDefault("huge", 0.20);
    }

    public static boolean isAllowedCoolantPipeMaterial(String materialName) {
        if (materialName == null) return false;
        return getPipeData().getAllowedMaterials()
            .contains(normalizeMaterial(materialName));
    }
}

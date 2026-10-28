package com.gtnewhorizons.modularnuclear.common.coolantloop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a discrete physical pipe segment or in-line device passage
 * within a closed coolant loop.
 */
public class LoopSegment {

    private final String id;
    private final double length; // Meters (m)
    private final double diameter; // Inner diameter (m)
    private final double area; // Cross-sectional area (m^2)
    private final double roughness; // Absolute pipe wall roughness (m)
    private double minorLossK; // Loss coefficient K (dimensionless)
    private final Double maxPressureBar; // Bursting limit in bar, or NO_LIMIT
    private final Double maxTemperatureCelsius; // Maximum safe operating temperature in deg C, or NO_LIMIT

    private double currentTemperatureCelsius; // Current temperature of coolant in this segment
    private double incomingTemperatureCelsius = 20.0; // Temperature of coolant entering this segment
    private double inletPressureBar = 1.0; // Hydraulic pressure entering this segment
    private double outletPressureBar = 1.0; // Hydraulic pressure exiting this segment
    private double flowFraction = 1.0; // Fraction of loop volumetric flow rate traversing this segment
    private final List<LoopSegment> upstreamSegments = new ArrayList<>();

    public LoopSegment(String id, double length, double diameter, double roughness, double minorLossK,
        Double maxPressureBar, Double maxTemperatureCelsius, double initialTempCelsius) {
        this(
            id,
            Integer.MIN_VALUE,
            Integer.MIN_VALUE,
            Integer.MIN_VALUE,
            length,
            diameter,
            roughness,
            minorLossK,
            maxPressureBar,
            maxTemperatureCelsius,
            initialTempCelsius);
    }

    public LoopSegment(String id, int blockX, int blockY, int blockZ, double length, double diameter, double roughness,
        double minorLossK, Double maxPressureBar, Double maxTemperatureCelsius, double initialTempCelsius) {
        if (length <= 0 || diameter <= 0) {
            throw new IllegalArgumentException("Length and diameter must be positive");
        }
        this.id = id;
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
        this.length = length;
        this.diameter = diameter;
        this.area = Math.PI * Math.pow(diameter / 2.0, 2);
        this.roughness = roughness;
        this.minorLossK = minorLossK;
        this.maxPressureBar = maxPressureBar;
        this.maxTemperatureCelsius = maxTemperatureCelsius;
        this.currentTemperatureCelsius = initialTempCelsius;
        this.incomingTemperatureCelsius = initialTempCelsius;
    }

    public String getId() {
        return id;
    }

    public double getLength() {
        return length;
    }

    public double getDiameter() {
        return diameter;
    }

    public double getArea() {
        return area;
    }

    private double customVolumeM3 = -1.0;

    /**
     * Volume of fluid contained within this segment in m^3.
     */
    public double getVolume() {
        if (customVolumeM3 >= 0.0) {
            return customVolumeM3;
        }
        return area * length;
    }

    public void setCustomVolumeM3(double customVolumeM3) {
        this.customVolumeM3 = customVolumeM3;
    }

    /**
     * Volume in Liters (1 m^3 = 1000 L).
     */
    public double getVolumeLiters() {
        return getVolume() * 1000.0;
    }

    private long capacityLiters = 0L;
    private int blockX = Integer.MIN_VALUE;
    private int blockY = Integer.MIN_VALUE;
    private int blockZ = Integer.MIN_VALUE;

    public void setPosition(int x, int y, int z) {
        this.blockX = x;
        this.blockY = y;
        this.blockZ = z;
    }

    public boolean hasPosition() {
        return blockX != Integer.MIN_VALUE;
    }

    public int getBlockX() {
        if (!hasPosition()) return 0;
        return blockX;
    }

    public int getBlockY() {
        if (!hasPosition()) return 0;
        return blockY;
    }

    public int getBlockZ() {
        if (!hasPosition()) return 0;
        return blockZ;
    }

    public long getCapacityLiters() {
        if (capacityLiters > 0) {
            return capacityLiters;
        }
        return Math.max(1L, (long) Math.ceil(getVolumeLiters()));
    }

    public void setCapacityLiters(long capacityLiters) {
        this.capacityLiters = capacityLiters;
    }

    public double getRoughness() {
        return roughness;
    }

    public double getMinorLossK() {
        return minorLossK;
    }

    public void setMinorLossK(double minorLossK) {
        this.minorLossK = Math.max(0.0, minorLossK);
    }

    /**
     * A limit a segment does not have. Only high-pressure pipes have a pressure limit: a loop's pressure is limited by
     * its pipes, which every loop needs at least at the pump's discharge. Manifolds have no temperature limit either.
     */
    public static final Double NO_LIMIT = null;
    /** How a missing limit is saved. */
    public static final String NO_LIMIT_STATE = "unlimited";

    public boolean hasPressureLimit() {
        return maxPressureBar != NO_LIMIT;
    }

    /** Burst pressure in bar; only for a segment that {@link #hasPressureLimit()}. */
    public double getMaxPressureBar() {
        if (!hasPressureLimit()) throw new IllegalStateException("Segment " + id + " has no pressure limit");
        return maxPressureBar;
    }

    public boolean hasTemperatureLimit() {
        return maxTemperatureCelsius != NO_LIMIT;
    }

    /** Maximum safe temperature in °C; only for a segment that {@link #hasTemperatureLimit()}. */
    public double getMaxTemperatureCelsius() {
        if (!hasTemperatureLimit()) throw new IllegalStateException("Segment " + id + " has no temperature limit");
        return maxTemperatureCelsius;
    }

    private static Object limitState(Double limit) {
        return limit != NO_LIMIT ? (Object) limit : NO_LIMIT_STATE;
    }

    private static Double limitFromState(Object v) {
        if (v == null || NO_LIMIT_STATE.equals(v)) return NO_LIMIT;
        return v instanceof Number n ? n.doubleValue() : Double.parseDouble(v.toString());
    }

    public double getCurrentTemperatureCelsius() {
        return currentTemperatureCelsius;
    }

    public void setCurrentTemperatureCelsius(double temp) {
        this.currentTemperatureCelsius = temp;
    }

    public double getIncomingTemperatureCelsius() {
        return incomingTemperatureCelsius;
    }

    public void setIncomingTemperatureCelsius(double incomingTemperatureCelsius) {
        this.incomingTemperatureCelsius = incomingTemperatureCelsius;
    }

    public double getInletPressureBar() {
        return inletPressureBar;
    }

    public void setInletPressureBar(double inletPressureBar) {
        this.inletPressureBar = inletPressureBar;
    }

    public double getOutletPressureBar() {
        return outletPressureBar;
    }

    public void setOutletPressureBar(double outletPressureBar) {
        this.outletPressureBar = outletPressureBar;
    }

    public double getFlowFraction() {
        return flowFraction;
    }

    public void setFlowFraction(double flowFraction) {
        this.flowFraction = Math.max(0.0, flowFraction);
    }

    private double flowSizeWeight = 1.0;
    private boolean manifold = false;

    public boolean isManifold() {
        return manifold;
    }

    public void setManifold(boolean manifold) {
        this.manifold = manifold;
    }

    public double getFlowSizeWeight() {
        return flowSizeWeight;
    }

    public void setFlowSizeWeight(double weight) {
        this.flowSizeWeight = Math.max(0.001, weight);
    }

    public List<LoopSegment> getUpstreamSegments() {
        return upstreamSegments;
    }

    public void addUpstreamSegment(LoopSegment seg) {
        if (seg != null && !upstreamSegments.contains(seg)) {
            upstreamSegments.add(seg);
        }
    }

    public void clearUpstreamSegments() {
        upstreamSegments.clear();
    }

    private final Map<String, Long> dissolvedGases = new LinkedHashMap<>();
    private double fallbackGasFraction = 0.0;

    public Map<String, Long> getDissolvedGases() {
        return Collections.unmodifiableMap(dissolvedGases);
    }

    public long getDissolvedGasAmount(String gasName) {
        if (gasName == null) return 0L;
        for (Map.Entry<String, Long> entry : dissolvedGases.entrySet()) {
            if (entry.getKey()
                .equalsIgnoreCase(gasName)) {
                return entry.getValue();
            }
        }
        return 0L;
    }

    public long getTotalDissolvedGasLiters() {
        long sum = 0L;
        for (long v : dissolvedGases.values()) {
            sum += v;
        }
        return sum;
    }

    public void addDissolvedGas(String gasName, long liters) {
        if (gasName == null || liters <= 0L) return;
        dissolvedGases.merge(gasName, liters, Long::sum);
    }

    public long extractDissolvedGas(String gasName, long maxLiters) {
        if (gasName == null || maxLiters <= 0L) return 0L;
        String matchedKey = null;
        for (String k : dissolvedGases.keySet()) {
            if (k.equalsIgnoreCase(gasName)) {
                matchedKey = k;
                break;
            }
        }
        if (matchedKey == null) return 0L;
        long current = dissolvedGases.getOrDefault(matchedKey, 0L);
        long toExtract = Math.min(current, maxLiters);
        if (toExtract > 0L) {
            long remaining = current - toExtract;
            if (remaining > 0L) {
                dissolvedGases.put(matchedKey, remaining);
            } else {
                dissolvedGases.remove(matchedKey);
            }
        }
        return toExtract;
    }

    public void setDissolvedGases(Map<String, Long> gases, long totalLoopLiters) {
        if (gases == null || gases.isEmpty() || totalLoopLiters <= 0L) {
            if (!this.dissolvedGases.isEmpty()) {
                this.dissolvedGases.clear();
            }
            return;
        }
        this.dissolvedGases.clear();
        long cap = getCapacityLiters();
        double ratio = (double) cap / (double) totalLoopLiters;
        for (Map.Entry<String, Long> entry : gases.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0L) {
                long segLiters = Math.round(entry.getValue() * ratio);
                if (segLiters > 0L) {
                    this.dissolvedGases.put(entry.getKey(), segLiters);
                }
            }
        }
    }

    public void setDissolvedGasesDirect(Map<String, Long> gases) {
        this.dissolvedGases.clear();
        if (gases != null) {
            for (Map.Entry<String, Long> entry : gases.entrySet()) {
                if (entry.getValue() != null && entry.getValue() > 0L) {
                    this.dissolvedGases.put(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    /**
     * Converts absolute dissolved gas amounts into a volume fraction (0.0 to 1.0)
     * for display purposes and cavitation damage calculations.
     */
    public double getDissolvedGasFraction() {
        long totalGas = getTotalDissolvedGasLiters();
        if (totalGas > 0L) {
            long cap = getCapacityLiters();
            if (cap > 0L) {
                return Math.min(1.0, (double) totalGas / (double) cap);
            }
        }
        return fallbackGasFraction;
    }

    /**
     * Converts volume fraction into percentage (0.0% to 100.0%) for display purposes.
     */
    public double getDissolvedGasPercentage() {
        return getDissolvedGasFraction() * 100.0;
    }

    public void setDissolvedGasFraction(double dissolvedGasFraction) {
        this.fallbackGasFraction = dissolvedGasFraction;
    }

    /**
     * Computes the Darcy friction factor f using the continuous Churchill correlation,
     * which smoothly handles laminar, transition, and fully rough turbulent flow.
     *
     * @param reynolds Reynolds number Re = rho * v * D / mu
     * @return Darcy friction factor f
     */
    public double computeDarcyFrictionFactor(double reynolds) {
        if (reynolds < 1.0) {
            return 64.0; // Clamped creeping flow
        }
        if (reynolds < 2100.0) {
            return 64.0 / reynolds; // Exact Hagen-Poiseuille laminar
        }

        // Churchill correlation for transitional and turbulent regimes:
        double relRoughness = roughness / diameter;
        double a = Math.pow(-2.457 * Math.log(Math.pow(7.0 / reynolds, 0.9) + 0.27 * relRoughness), 16.0);
        double b = Math.pow(37530.0 / reynolds, 16.0);
        return 8.0 * Math.pow(Math.pow(8.0 / reynolds, 12.0) + 1.0 / Math.pow(a + b, 1.5), 1.0 / 12.0);
    }

    /**
     * Computes total head loss delta_P (Pascals) across this segment for a given flow rate Q.
     *
     * @param volumetricFlowRate Volumetric flow rate Q in m^3/s
     * @param fluid              Fluid properties
     * @return Pressure drop delta_P in Pascals
     */
    public double computePressureDrop(double volumetricFlowRate, CoolantFluidProperty fluid) {
        if (Math.abs(volumetricFlowRate) <= 1e-9) {
            return 0.0;
        }
        double velocity = Math.abs(volumetricFlowRate) / area;
        double reynolds = (fluid.getDensity() * velocity * diameter) / fluid.getDynamicViscosity();
        double f = computeDarcyFrictionFactor(reynolds);

        // Major Darcy friction head loss: Delta P_major = f * (L / D) * (rho * v^2 / 2)
        double deltaPMajor = f * (length / diameter) * (fluid.getDensity() * velocity * velocity / 2.0);

        // Minor loss: Delta P_minor = K * (rho * v^2 / 2)
        double deltaPMinor = minorLossK * (fluid.getDensity() * velocity * velocity / 2.0);

        return deltaPMajor + deltaPMinor;
    }

    /**
     * Computes the Reynolds number Re = (rho * v * D) / mu for a given volumetric flow rate.
     *
     * @param volumetricFlowRate Volumetric flow rate Q in m^3/s
     * @param fluid              Fluid properties
     * @return Reynolds number (dimensionless)
     */
    public double computeReynoldsNumber(double volumetricFlowRate, CoolantFluidProperty fluid) {
        if (Math.abs(volumetricFlowRate) <= 1e-9 || fluid == null || area <= 0.0) {
            return 0.0;
        }
        double velocity = Math.abs(volumetricFlowRate) / area;
        return (fluid.getDensity() * velocity * diameter) / fluid.getDynamicViscosity();
    }

    /**
     * Snapshot of this segment's state as plain values (Integer, Long, Double, Boolean, String, List of String), for
     * persistence. Keys match the game's saved NBT.
     */
    public java.util.Map<String, Object> toStateMap() {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", id != null ? id : "");
        m.put("x", blockX);
        m.put("y", blockY);
        m.put("z", blockZ);
        m.put("len", length);
        m.put("diam", diameter);
        m.put("rough", roughness);
        m.put("k", minorLossK);
        m.put("pMax", limitState(maxPressureBar));
        m.put("tMax", limitState(maxTemperatureCelsius));
        m.put("curTemp", currentTemperatureCelsius);
        m.put("inTemp", incomingTemperatureCelsius);
        m.put("inP", inletPressureBar);
        m.put("outP", outletPressureBar);
        m.put("flowFrac", flowFraction);
        m.put("weight", flowSizeWeight);
        m.put("cap", capacityLiters);
        m.put("manifold", manifold);
        if (customVolumeM3 >= 0.0) {
            m.put("customVol", customVolumeM3);
        }
        List<String> ups = new ArrayList<>();
        for (LoopSegment up : upstreamSegments) {
            if (up != null && up.getId() != null) {
                ups.add(up.getId());
            }
        }
        m.put("upstreams", ups);
        return m;
    }

    /** Rebuilds a segment from {@link #toStateMap()}; upstream segment ids are added to outUpstreamIds. */
    public static LoopSegment fromStateMap(java.util.Map<String, Object> m, List<String> outUpstreamIds) {
        String id = StateValues.str(m, "id", "");
        double len = StateValues.dbl(m, "len", 0.0);
        double diam = StateValues.dbl(m, "diam", 0.0);
        if (len <= 0) len = 1.0;
        if (diam <= 0) diam = 0.05;
        LoopSegment seg = new LoopSegment(
            id,
            StateValues.integer(m, "x", 0),
            StateValues.integer(m, "y", 0),
            StateValues.integer(m, "z", 0),
            len,
            diam,
            StateValues.dbl(m, "rough", 0.0),
            StateValues.dbl(m, "k", 0.0),
            limitFromState(m.get("pMax")),
            limitFromState(m.get("tMax")),
            StateValues.dbl(m, "curTemp", 0.0));
        seg.setIncomingTemperatureCelsius(StateValues.dbl(m, "inTemp", 0.0));
        if (m.containsKey("inP")) seg.setInletPressureBar(StateValues.dbl(m, "inP", 0.0));
        if (m.containsKey("outP")) seg.setOutletPressureBar(StateValues.dbl(m, "outP", 0.0));
        if (m.containsKey("flowFrac")) seg.setFlowFraction(StateValues.dbl(m, "flowFrac", 0.0));
        if (m.containsKey("weight")) seg.setFlowSizeWeight(StateValues.dbl(m, "weight", 0.0));
        if (m.containsKey("cap")) seg.setCapacityLiters(StateValues.lng(m, "cap", 0L));
        if (m.containsKey("manifold")) seg.setManifold(StateValues.bool(m, "manifold", false));
        if (m.containsKey("customVol")) seg.setCustomVolumeM3(StateValues.dbl(m, "customVol", 0.0));
        if (outUpstreamIds != null && m.get("upstreams") instanceof List<?>ups) {
            for (Object o : ups) {
                if (o != null) outUpstreamIds.add(o.toString());
            }
        }
        return seg;
    }
}

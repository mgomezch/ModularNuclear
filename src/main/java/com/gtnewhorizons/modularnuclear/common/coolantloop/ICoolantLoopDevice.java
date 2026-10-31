package com.gtnewhorizons.modularnuclear.common.coolantloop;

import java.util.Set;

/**
 * Interface implemented by in-line blocks and MetaTileEntities that interface
 * with a pressurized coolant loop (Pumps, Exchangers, Reservoirs, Manifolds, Instruments).
 */
public interface ICoolantLoopDevice {

    /**
     * Unique identifier or coordinate label of this device.
     */
    String getDeviceId();

    /**
     * Minor loss coefficient K contributed by fluid flowing through this device.
     */
    double getMinorLossK();

    /**
     * Operating temperature of the device in deg C.
     */
    double getDeviceTemperatureCelsius();

    /**
     * Maximum safe operating temperature in deg C before this device or loop breaches/fails.
     */
    default double getMaxOperatingTemperatureCelsius() {
        return 2000.0;
    }

    /**
     * Performs thermal energy exchange between the device and the fluid passing through it.
     *
     * @param coolantFlowRateM3s Volumetric flow rate Q in m^3/s
     * @param dt                 Time step in seconds
     * @param fluid              Fluid thermodynamic properties
     * @param segment            Local loop segment containing fluid entering this device
     */
    void processThermalExchange(double coolantFlowRateM3s, double dt, CoolantFluidProperty fluid, LoopSegment segment);

    /**
     * Required fill volume in liters contributed by this device to the loop.
     */
    default long getFillVolumeLiters() {
        return 1000L;
    }

    /**
     * Associates the managing coolant loop pump with this device.
     */
    default void setLoopPump(ICoolantLoopPump pump) {}

    /**
     * Returns true if this device has entered a disconnected state (e.g. powerfail from over-temperature)
     * and can no longer maintain a closed loop.
     */
    default boolean isDeviceDisconnected() {
        return false;
    }

    /**
     * Human-readable reason why this device disconnected from the loop.
     */
    default String getDisconnectionReason() {
        return null;
    }

    /**
     * Whether this device's structure has been checked and found formed, so the loop can run through it. Right after
     * a load a multiblock reports unformed until its first structure check.
     */
    default boolean isStructureReady() {
        return true;
    }

    /**
     * Adds the chunks this device's whole structure occupies (as {@code ChunkCoordIntPair.chunkXZ2Int} keys), so the
     * pump can keep them loaded while the loop runs through it.
     */
    default void collectStructureChunks(Set<Long> chunks) {}
}

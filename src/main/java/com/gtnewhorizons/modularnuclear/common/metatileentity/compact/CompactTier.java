package com.gtnewhorizons.modularnuclear.common.metatileentity.compact;

import gregtech.api.enums.GTValues;

/**
 * What a compact loop machine of a GregTech tier can do, MV to LuV. See Note [Compact loop machines]
 */
public final class CompactTier {

    public static final int FIRST = 2; // MV
    public static final int LAST = 6; // LuV

    private static final CompactTier[] TIERS = {
        // tier, bar, drive, eta, ratio, radiator W/K per fan EU/t, bath slots, expander eta, fill
        new CompactTier(2, 16.0, 0.09, 0.60, 2.4, 0.14e6, 2, 0.55, 0.10),
        new CompactTier(3, 32.0, 0.15, 0.64, 2.3, 0.16e6, 3, 0.62, 0.15),
        new CompactTier(4, 64.0, 0.19, 0.68, 2.2, 0.18e6, 4, 0.68, 0.20),
        new CompactTier(5, 128.0, 0.21, 0.71, 2.1, 0.20e6, 6, 0.73, 0.30),
        new CompactTier(6, 256.0, 0.23, 0.74, 2.0, 0.22e6, 8, 0.77, 0.40), };

    /** The GregTech voltage tier: 2 is MV. */
    public final int tier;
    /** EU per tick the machine works with: one ampere of its tier's recipe voltage. */
    public final long euPerTick;
    /** The pressure the machine bursts at, bar. */
    public final double maxPressureBar;
    /** The loop's gas the machine holds, m^3: a higher tier's flow needs a larger machine's worth of gas. */
    public final double volumeM3;
    /** Compressor: the share of its EU that reaches its shaft; its small drive loses the rest as heat. */
    public final double driveEfficiency;
    /** Compressor: isentropic efficiency. */
    public final double compressorEfficiency;
    /**
     * Compressor: its built-in pressure ratio. It compresses at least this far whatever the loop's pressures, and
     * the excess is lost as heat.
     */
    public final double builtInRatio;
    /** Compressor: displacement, m^3/s of suction gas at full speed. */
    public final double displacement;
    /** Expansion valve: orifice area at full opening, m^2. */
    public final double valveArea;
    /** Turbo-expander: nozzle area, m^2, and isentropic efficiency. */
    public final double expanderNozzle;
    public final double expanderEfficiency;
    /** Radiator: conductance with its fan running, W/K, and what the fan draws, EU/t. */
    public final double radiatorConductance;
    public final long radiatorFanEu;
    /** Bath: how many items it holds at once. */
    public final int bathSlots;
    /** Heat exchanger: conductance between the loop and its fluid, W/K. */
    public final double exchangerConductance;
    /** Pump: the largest loop it runs, litres of pipe and device volume, and its fill rate against a multiblock's. */
    public final double maxLoopVolumeLiters;
    public final double fillRateFactor;

    private CompactTier(int tier, double maxPressureBar, double driveEfficiency, double compressorEfficiency,
        double builtInRatio, double radiatorWPerKPerEu, int bathSlots, double expanderEfficiency,
        double fillRateFactor) {
        this.tier = tier;
        this.euPerTick = GTValues.VP[tier];
        this.maxPressureBar = maxPressureBar;
        this.driveEfficiency = driveEfficiency;
        this.compressorEfficiency = compressorEfficiency;
        this.builtInRatio = builtInRatio;
        // What the shaft gets grows a little faster than the tier's EU, as the drives improve
        double scale = Math.pow(4.0, tier - FIRST) * driveEfficiency / TIER_ZERO_DRIVE;
        this.volumeM3 = 0.25 * scale;
        this.displacement = 25.0 * scale;
        this.valveArea = 0.01 * scale;
        this.expanderNozzle = 0.004 * scale;
        this.expanderEfficiency = expanderEfficiency;
        this.radiatorFanEu = Math.max(1L, euPerTick / 8L);
        this.radiatorConductance = radiatorWPerKPerEu * radiatorFanEu;
        this.bathSlots = bathSlots;
        this.exchangerConductance = 0.05e6 * scale;
        this.maxLoopVolumeLiters = 8000.0 * volumeM3;
        this.fillRateFactor = fillRateFactor;
    }

    private static final double TIER_ZERO_DRIVE = 0.09;

    /** Joules of shaft work a compressor of this tier gets from one EU. */
    public double shaftJoulesPerEu() {
        return 28_250.0 * driveEfficiency;
    }

    /**
     * The charge settings of a compact pump: the shares of a condensing gas loop's volume that are liquid at ambient.
     * Half a multiblock pump's by default: a compact machine holds a great deal of gas for its tier's flow, and a
     * loop runs on less at a tenth or so less efficiency.
     */
    public static final double[] LIQUID_FRACTIONS = { 0.05, 0.03, 0.1, 0.2, 0.02 };

    /** The loop's temperature above ambient at which a radiator's fan reaches full speed, K. */
    public static final double RADIATOR_FULL_FAN_K = 6.0;
    /** The slowest a radiator's fan turns while the loop flows, as a share of full speed. */
    public static final double RADIATOR_MIN_FAN = 0.1;

    /**
     * The share of full speed a radiator's fan runs at for a loop this warm: in proportion to how far the gas in it
     * is above ambient, so a lightly loaded loop's fan idles. Its conductance and its EU follow the share.
     */
    public static double fanDuty(double gasK, double ambientK) {
        return Math.max(RADIATOR_MIN_FAN, Math.min(1.0, (gasK - ambientK) / RADIATOR_FULL_FAN_K));
    }

    public static CompactTier of(int tier) {
        return TIERS[Math.max(FIRST, Math.min(LAST, tier)) - FIRST];
    }

    public static CompactTier[] all() {
        return TIERS.clone();
    }

    /** The tier's name, as in MV. */
    public String voltageName() {
        return GTValues.VN[tier];
    }
}

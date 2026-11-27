package com.gtnewhorizons.modularnuclear.common.coolantloop.pump;

/** What a pump can take: a multiblock pump has no limits of its own, a singleblock pump's come with its tier. */
public final class PumpLimits {

    public static final PumpLimits UNLIMITED = new PumpLimits(
        Double.MAX_VALUE,
        Double.MAX_VALUE,
        1.0,
        false,
        CoolantPumpGasLoop.LIQUID_FRACTIONS,
        CoolantPumpGasLoop.CHARGE_PRESSURES_BAR);

    /** The largest loop the pump runs, in litres of pipe and device volume. */
    public final double maxLoopVolumeLiters;
    /** The pressure the pump itself bursts at, in bar: it applies to the loop's segments at the pump. */
    public final double maxPressureBar;
    /** The pump's fill and drain rate as a fraction of a multiblock pump's. */
    public final double fillRateFactor;
    /** Whether the pump only runs gas loops. */
    public final boolean gasOnly;
    /**
     * The shares of a condensing gas loop's volume that are liquid at ambient under each charge setting, the first
     * being the default. A screwdriver cycles them together with chargePressuresBar, which has as many.
     */
    public final double[] liquidFractions;
    /** The pressures a permanent gas loop is charged to at ambient under each charge setting, bar. */
    public final double[] chargePressuresBar;

    public PumpLimits(double maxLoopVolumeLiters, double maxPressureBar, double fillRateFactor, boolean gasOnly,
        double[] liquidFractions, double[] chargePressuresBar) {
        this.liquidFractions = liquidFractions;
        this.chargePressuresBar = chargePressuresBar;
        this.maxLoopVolumeLiters = maxLoopVolumeLiters;
        this.maxPressureBar = maxPressureBar;
        this.fillRateFactor = fillRateFactor;
        this.gasOnly = gasOnly;
    }
}

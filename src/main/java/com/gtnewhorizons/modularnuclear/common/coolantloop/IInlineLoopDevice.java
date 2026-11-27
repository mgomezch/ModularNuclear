package com.gtnewhorizons.modularnuclear.common.coolantloop;

/**
 * A loop device the crawler treats generically: the loop enters through its inlet hatch, runs through it as a run of
 * the pipe that feeds it, and leaves through its outlet hatch. The gas loop machines (compressor, expansion valve,
 * turbo-expander, radiator) and a PHE's second hatch pair are these.
 */
public interface IInlineLoopDevice extends ILoopHatchOwner {

    /** Whether the device is formed with both of its loop hatches, so a loop can run through it. */
    boolean isLoopReady();

    /** The device's length along the loop, m: its segment's length. */
    double getLoopLengthMeters();

    /** What the device holds of the loop's fluid, m^3. */
    double getLoopVolumeM3();

    /** The device's own temperature limit in °C, or NaN for none (its pipe's applies). */
    default double getLoopMaxTemperatureCelsius() {
        return Double.NaN;
    }
}

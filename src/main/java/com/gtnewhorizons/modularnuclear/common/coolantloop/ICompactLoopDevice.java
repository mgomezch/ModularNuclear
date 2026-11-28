package com.gtnewhorizons.modularnuclear.common.coolantloop;

import net.minecraftforge.common.util.ForgeDirection;

/**
 * A loop device that is one block: the loop enters through one of its sides and leaves through the opposite one,
 * with a pipe on each. The crawler takes the loop through it as a run of the pipe that feeds it
 * (LoopGraphCrawler.visitCompactDevice). See Note [Compact loop machines]
 */
public interface ICompactLoopDevice extends ICoolantLoopDevice, ILoopPortSides {

    /** The side the loop enters through. */
    ForgeDirection getLoopInletSide();

    /** The side the loop leaves through. */
    default ForgeDirection getLoopOutletSide() {
        return getLoopInletSide().getOpposite();
    }

    @Override
    default boolean isLoopPortSide(ForgeDirection side) {
        return side == getLoopInletSide() || side == getLoopOutletSide();
    }

    /**
     * A second pass of a loop through the same block, entering through another side, or null if the device has
     * none. It is a loop device of its own, with its own id and segment (a compact heat exchanger's, on the same
     * loop as a recuperator or on another).
     */
    default ICompactLoopDevice getSecondPass() {
        return null;
    }

    /** Whether a loop can run through the device now. */
    boolean isLoopReady();

    /** The device's length along the loop, m: its segment's length. */
    default double getLoopLengthMeters() {
        return 1.0;
    }

    /** What the device holds of the loop's fluid, m^3. */
    double getLoopVolumeM3();

    /** The pressure the device bursts at, bar: its segment's rating is the lower of this and its pipe's. */
    double getLoopMaxPressureBar();

    /** The device's own temperature limit in °C, or NaN for none (its pipe's applies). */
    default double getLoopMaxTemperatureCelsius() {
        return Double.NaN;
    }
}

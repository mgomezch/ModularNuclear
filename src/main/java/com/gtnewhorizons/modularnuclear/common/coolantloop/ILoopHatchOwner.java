package com.gtnewhorizons.modularnuclear.common.coolantloop;

import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchPressurizedFluid;

/**
 * A multiblock loop device the loop enters and leaves through two of its high-pressure hatches. It claims them as its
 * structure check finds them ({@link MTEHatchPressurizedFluid#setLoopOwner}), so {@link LoopDevices#at} knows which
 * device a hatch belongs to.
 */
public interface ILoopHatchOwner extends ICoolantLoopDevice {

    /** The hatch the loop enters through, or null while unformed. */
    MTEHatchPressurizedFluid getLoopInlet();

    /** The hatch the loop leaves through, or null while unformed. */
    MTEHatchPressurizedFluid getLoopOutlet();

    default boolean ownsHatch(MTEHatchPressurizedFluid hatch) {
        return hatch != null && (hatch == getLoopInlet() || hatch == getLoopOutlet());
    }

    /**
     * The device a hatch this owner owns belongs to: the owner itself, or for a multiblock with more than one pair
     * of loop hatches (a PHE joining two loops), the side the hatch is on.
     */
    default ICoolantLoopDevice deviceOf(MTEHatchPressurizedFluid hatch) {
        return this;
    }
}

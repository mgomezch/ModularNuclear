package com.gtnewhorizons.modularnuclear.common.coolantloop;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * Interface for multiblock passage hatches (such as Nuclear Core High-Pressure Hatches)
 * that route coolant through a multiblock internal column/channel from an inlet to an outlet.
 */
public interface ICoolantPassageHatch extends ICoolantLoopDevice {

    /**
     * Gets the tile entity of the opposite paired hatch (e.g. bottom outlet for a top inlet).
     */
    IGregTechTileEntity getOppositeHatchTile();

    /**
     * Sets the tile entity of the opposite paired hatch.
     */
    void setOppositeHatch(IGregTechTileEntity opposite);

    /**
     * Returns true if this hatch is the entry/inlet point of the passage, false if it is the exit/outlet.
     */
    boolean isPassageInlet();

    /**
     * Associates the active loop pump with this passage during loop graph crawling.
     */
    void setConnectedPump(ICoolantLoopPump pump);

    /**
     * Whether the passage was in a loop when saved and its pump has not bound it again since loading; the reactor
     * holds its first cycles briefly for that (see MTENuclearReactor).
     */
    default boolean isAwaitingLoopPump() {
        return false;
    }

    default void stopAwaitingLoopPump() {}

    /** A passage's pump is its connected pump, so binding or releasing it as a loop device reaches the passage. */
    @Override
    default void setLoopPump(ICoolantLoopPump pump) {
        setConnectedPump(pump);
    }

    /**
     * Gets the active loop pump associated with this passage, or null if loop is disconnected.
     */
    ICoolantLoopPump getConnectedPump();

    /**
     * Converts an unspecific or legacy passage hatch to a directional variant if supported.
     */
    default void convertToDirectionalVariant(boolean isInput) {}
}

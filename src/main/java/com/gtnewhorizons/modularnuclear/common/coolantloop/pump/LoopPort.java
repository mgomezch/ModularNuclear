package com.gtnewhorizons.modularnuclear.common.coolantloop.pump;

import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** Where a loop leaves or enters a machine: a block and the side of it a pipe connects to. */
public final class LoopPort {

    public final int x, y, z;
    public final ForgeDirection side;

    public LoopPort(int x, int y, int z, ForgeDirection side) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.side = side;
    }

    /** A hatch's block and front. */
    public static LoopPort ofHatch(IGregTechTileEntity hatch) {
        return hatch == null ? null
            : new LoopPort(hatch.getXCoord(), hatch.getYCoord(), hatch.getZCoord(), hatch.getFrontFacing());
    }
}

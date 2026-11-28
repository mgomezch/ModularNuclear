package com.gtnewhorizons.modularnuclear.common.coolantloop;

import net.minecraftforge.common.util.ForgeDirection;

/** A one-block loop machine: coolant pipes connect only to the sides its loop runs through. */
public interface ILoopPortSides {

    boolean isLoopPortSide(ForgeDirection side);
}

package com.gtnewhorizons.modularnuclear.common;

import com.gtnewhorizons.modularnuclear.common.entity.EntityMeltdownFallout;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

public class CommonProxy {

    public void updateCherenkov(MTENuclearReactor reactor, IGregTechTileEntity base) {}

    public void removeReactor(MTENuclearReactor reactor) {}

    public void registerNEIHandlerInfo() {}

    /** Sets up the custom fuel rods' icons, on the client. */
    public void registerItemIcons() {}

    public void registerFallout(EntityMeltdownFallout fallout) {}

    public void unregisterFallout(EntityMeltdownFallout fallout) {}
}

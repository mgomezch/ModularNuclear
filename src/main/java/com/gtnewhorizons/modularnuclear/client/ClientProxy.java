package com.gtnewhorizons.modularnuclear.client;

import com.gtnewhorizons.modularnuclear.common.CommonProxy;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy {

    @Override
    public void updateCherenkov(MTENuclearReactor reactor, IGregTechTileEntity base) {
        CherenkovClientHandler.update(reactor, base);
    }

    @Override
    public void removeReactor(MTENuclearReactor reactor) {
        CherenkovClientHandler.onRemoval(reactor);
    }
}

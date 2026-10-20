package com.gtnewhorizons.modularnuclear.client;

import net.minecraftforge.common.MinecraftForge;

import com.gtnewhorizons.modularnuclear.common.CommonProxy;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;
import com.gtnewhorizons.modularnuclear.common.nei.NEIConfig;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy {

    private static boolean registeredNEIHandlerInfo = false;

    @Override
    public void registerNEIHandlerInfo() {
        if (!registeredNEIHandlerInfo) {
            registeredNEIHandlerInfo = true;
            try {
                MinecraftForge.EVENT_BUS.register(new NEIConfig());
            } catch (Throwable ignored) {}
            try {
                com.gtnewhorizons.modularnuclear.common.gui.FloatingPanelNEIHandler.register();
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void updateCherenkov(MTENuclearReactor reactor, IGregTechTileEntity base) {
        CherenkovClientHandler.update(reactor, base);
    }

    @Override
    public void removeReactor(MTENuclearReactor reactor) {
        CherenkovClientHandler.onRemoval(reactor);
    }

    @Override
    public void registerFallout(com.gtnewhorizons.modularnuclear.common.entity.EntityMeltdownFallout fallout) {
        com.gtnewhorizons.modularnuclear.client.renderer.FalloutVisualEffectRenderer.registerFallout(fallout);
    }

    @Override
    public void unregisterFallout(com.gtnewhorizons.modularnuclear.common.entity.EntityMeltdownFallout fallout) {
        com.gtnewhorizons.modularnuclear.client.renderer.FalloutVisualEffectRenderer.unregisterFallout(fallout);
    }
}

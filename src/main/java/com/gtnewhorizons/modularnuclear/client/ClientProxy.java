package com.gtnewhorizons.modularnuclear.client;

import net.minecraftforge.common.MinecraftForge;

import com.gtnewhorizons.modularnuclear.client.renderer.FalloutVisualEffectRenderer;
import com.gtnewhorizons.modularnuclear.common.CommonProxy;
import com.gtnewhorizons.modularnuclear.common.entity.EntityMeltdownFallout;
import com.gtnewhorizons.modularnuclear.common.gui.FloatingPanelNEIHandler;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;
import com.gtnewhorizons.modularnuclear.common.nei.NEIConfig;
import com.gtnewhorizons.modularnuclear.common.util.Swallowed;

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
            } catch (Throwable swallowed) {
                Swallowed.note(swallowed);
            }
            try {
                FloatingPanelNEIHandler.register();
            } catch (Throwable swallowed) {
                Swallowed.note(swallowed);
            }
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
    public void registerFallout(EntityMeltdownFallout fallout) {
        FalloutVisualEffectRenderer.registerFallout(fallout);
    }

    @Override
    public void unregisterFallout(EntityMeltdownFallout fallout) {
        FalloutVisualEffectRenderer.unregisterFallout(fallout);
    }
}

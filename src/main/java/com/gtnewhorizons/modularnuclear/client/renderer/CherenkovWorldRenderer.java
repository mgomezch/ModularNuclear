package com.gtnewhorizons.modularnuclear.client.renderer;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.gtnewhorizons.modularnuclear.client.CherenkovClientHandler;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class CherenkovWorldRenderer {

    public static final Set<MTENuclearReactor> ACTIVE_REACTORS = Collections.synchronizedSet(new HashSet<>());

    public static void register(MTENuclearReactor reactor) {
        if (reactor != null) {
            ACTIVE_REACTORS.add(reactor);
        }
    }

    public static void unregister(MTENuclearReactor reactor) {
        if (reactor != null) {
            ACTIVE_REACTORS.remove(reactor);
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        ACTIVE_REACTORS.clear();
        CherenkovClientHandler.onWorldUnload();
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        // All Cherenkov beam and pool glow rendering is handled by EntityCherenkovBeamFX
        // in Particle Layer 3 (renderLitParticles).
        // Particle Layer 3 executes after solid blocks (so opaque casings and reactor walls
        // occlude the glow properly) and before translucent blocks (so water and glass properly
        // overlay the glow without depth-culling it).
    }
}

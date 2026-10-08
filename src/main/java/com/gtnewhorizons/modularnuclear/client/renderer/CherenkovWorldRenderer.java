package com.gtnewhorizons.modularnuclear.client.renderer;

import java.util.Collections;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class CherenkovWorldRenderer {

    public static final Set<MTENuclearReactor> ACTIVE_REACTORS = Collections.synchronizedSet(new java.util.HashSet<>());

    public static void register(MTENuclearReactor reactor) {
        ACTIVE_REACTORS.add(reactor);
    }

    public static void unregister(MTENuclearReactor reactor) {
        ACTIVE_REACTORS.remove(reactor);
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        // All rendering is now handled by EntityCherenkovBeamFX in Particle Layer 3 (renderLitParticles).
        // Particle Layer 3 executes after solid blocks (so opaque casings/walls occlude the glow)
        // and before translucent blocks (water/stained glass properly render translucently over the glow).
    }
}

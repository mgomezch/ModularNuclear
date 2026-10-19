package com.gtnewhorizons.modularnuclear.client.renderer;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.world.WorldEvent;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

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
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!ModularNuclearConfig.enableCherenkovRadiation || ACTIVE_REACTORS.isEmpty()) {
            return;
        }

        EntityLivingBase view = Minecraft.getMinecraft().renderViewEntity;
        if (view == null || view.worldObj == null) {
            return;
        }

        float partialTicks = event.partialTicks;
        double camX = view.lastTickPosX + (view.posX - view.lastTickPosX) * partialTicks;
        double camY = view.lastTickPosY + (view.posY - view.lastTickPosY) * partialTicks;
        double camZ = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partialTicks;

        long worldTime = view.worldObj.getTotalWorldTime();

        // Check if any reactor has valid visible glow before modifying GL state
        boolean hasVisibleGlow = false;
        synchronized (ACTIVE_REACTORS) {
            for (MTENuclearReactor reactor : ACTIVE_REACTORS) {
                if (isValidAndVisible(reactor, view.worldObj, camX, camZ)) {
                    hasVisibleGlow = true;
                    break;
                }
            }
        }
        if (!hasVisibleGlow) {
            return;
        }

        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);

        try {
            GL11.glEnable(GL11.GL_BLEND);
            // Additive blending produces the luminous ethereal Cherenkov glow
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE, 1, 0);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glShadeModel(GL11.GL_SMOOTH);

            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);

            Tessellator tess = Tessellator.instance;

            synchronized (ACTIVE_REACTORS) {
                for (MTENuclearReactor reactor : ACTIVE_REACTORS) {
                    if (!isValidAndVisible(reactor, view.worldObj, camX, camZ)) {
                        continue;
                    }

                    float intensity = Math.min(1.0f, reactor.mClientCherenkovIntensity)
                        * ModularNuclearConfig.cherenkovIntensityMultiplier;
                    if (intensity <= 0.001f) continue;

                    double cx = reactor.mClientCenterX - camX;
                    double cz = reactor.mClientCenterZ - camZ;
                    double minY = reactor.mClientWaterMinY - camY;
                    double maxY = reactor.mClientWaterMaxY - camY;

                    renderConcentricBeams(tess, cx, cz, minY, maxY, reactor, worldTime, partialTicks, intensity);
                    renderPoolBaseGlow(tess, cx, cz, minY, reactor, worldTime, partialTicks, intensity);
                }
            }
        } finally {
            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }
    }

    private static boolean isValidAndVisible(MTENuclearReactor reactor, World world, double camX, double camZ) {
        if (reactor == null) return false;
        IGregTechTileEntity base = reactor.getBaseMetaTileEntity();
        if (base == null || base.isDead() || !base.isActive() || base.getWorld() != world) {
            return false;
        }
        if (reactor.mClientWaterBlocks <= 0 || reactor.mClientCherenkovIntensity <= 0.001f) {
            return false;
        }
        double cx = reactor.mClientCenterX - camX;
        double cz = reactor.mClientCenterZ - camZ;
        return (cx * cx + cz * cz) <= 128.0 * 128.0;
    }

    private static void renderConcentricBeams(Tessellator tess, double cx, double cz, double minY, double maxY,
        MTENuclearReactor reactor, long worldTime, float partialTicks, float intensity) {

        int tier = Math.max(1, reactor.mClientTier);
        double R = (tier == 3) ? 6.0 : (tier == 2) ? 4.0 : 2.0;

        double beamHeight = Math.min(3.0, maxY - minY);
        if (beamHeight <= 0.2) return;
        double startY = minY + 0.03;
        double topY = startY + beamHeight;

        tess.startDrawingQuads();
        tess.setBrightness(15728880);

        int sides = 16;
        double angleStep = 2.0 * Math.PI / sides;

        // One large pair of concentric beams covering the core area
        double innerBaseRadius = R * 0.45;
        double innerTopRadius = R * 0.70;

        double outerBaseRadius = R * 0.90;
        double outerTopRadius = R * 1.30;

        float shimmer = (float) Math.sin((worldTime + partialTicks) * 0.08) * 0.03f;

        // Scale intensity dynamically, clamped at 1.0f for valid OpenGL alpha
        float innerBotAlpha = Math.min(1.0f, Math.max(0.10f, (0.65f + shimmer) * intensity));
        float innerTopAlpha = 0.04f * intensity; // Smooth fade with height over ~3 blocks

        float outerBotAlpha = Math.min(1.0f, Math.max(0.05f, (0.38f + shimmer) * intensity));
        float outerTopAlpha = 0.02f * intensity;

        // 1. Inner core cylinder: electric cyan-blue
        if (innerBotAlpha > 0.005f) {
            for (int i = 0; i < sides; i++) {
                double a1 = i * angleStep;
                double a2 = (i + 1) * angleStep;

                double x1Bot = cx + Math.cos(a1) * innerBaseRadius;
                double z1Bot = cz + Math.sin(a1) * innerBaseRadius;
                double x2Bot = cx + Math.cos(a2) * innerBaseRadius;
                double z2Bot = cz + Math.sin(a2) * innerBaseRadius;

                double x1Top = cx + Math.cos(a1) * innerTopRadius;
                double z1Top = cz + Math.sin(a1) * innerTopRadius;
                double x2Top = cx + Math.cos(a2) * innerTopRadius;
                double z2Top = cz + Math.sin(a2) * innerTopRadius;

                tess.setColorRGBA_F(0.18f, 0.85f, 1.0f, innerBotAlpha);
                tess.addVertex(x1Bot, startY, z1Bot);
                tess.addVertex(x2Bot, startY, z2Bot);

                tess.setColorRGBA_F(0.10f, 0.60f, 0.95f, innerTopAlpha);
                tess.addVertex(x2Top, topY, z2Top);
                tess.addVertex(x1Top, topY, z1Top);
            }
        }

        // 2. Outer diffuse sleeve: deep Cherenkov blue
        if (outerBotAlpha > 0.005f) {
            for (int i = 0; i < sides; i++) {
                double a1 = i * angleStep;
                double a2 = (i + 1) * angleStep;

                double x1Bot = cx + Math.cos(a1) * outerBaseRadius;
                double z1Bot = cz + Math.sin(a1) * outerBaseRadius;
                double x2Bot = cx + Math.cos(a2) * outerBaseRadius;
                double z2Bot = cz + Math.sin(a2) * outerBaseRadius;

                double x1Top = cx + Math.cos(a1) * outerTopRadius;
                double z1Top = cz + Math.sin(a1) * outerTopRadius;
                double x2Top = cx + Math.cos(a2) * outerTopRadius;
                double z2Top = cz + Math.sin(a2) * outerTopRadius;

                tess.setColorRGBA_F(0.0f, 0.60f, 1.0f, outerBotAlpha);
                tess.addVertex(x1Bot, startY, z1Bot);
                tess.addVertex(x2Bot, startY, z2Bot);

                tess.setColorRGBA_F(0.0f, 0.35f, 0.90f, outerTopAlpha);
                tess.addVertex(x2Top, topY, z2Top);
                tess.addVertex(x1Top, topY, z1Top);
            }
        }

        // 3. Bottom cap disc at hatch top
        float capAlpha = Math.min(1.0f, Math.max(0.15f, (0.70f + shimmer) * intensity));
        for (int i = 0; i < sides; i++) {
            double a1 = i * angleStep;
            double a2 = (i + 1) * angleStep;

            tess.setColorRGBA_F(0.15f, 0.80f, 1.0f, capAlpha);
            tess.addVertex(cx, startY, cz);
            tess.addVertex(cx + Math.cos(a1) * outerBaseRadius, startY, cz + Math.sin(a1) * outerBaseRadius);
            tess.addVertex(cx + Math.cos(a2) * outerBaseRadius, startY, cz + Math.sin(a2) * outerBaseRadius);
            tess.addVertex(cx, startY, cz);
        }

        tess.draw();
    }

    private static void renderPoolBaseGlow(Tessellator tess, double cx, double cz, double minY,
        MTENuclearReactor reactor, long worldTime, float partialTicks, float intensity) {

        int tier = Math.max(1, reactor.mClientTier);
        double poolRadius = (tier == 3) ? 6.2 : (tier == 2) ? 4.2 : 2.2;
        float pulse = (float) Math.sin((worldTime + partialTicks) * 0.05) * 0.02f;
        float baseAlpha = Math.min(1.0f, Math.max(0.10f, (0.45f + pulse) * intensity));

        tess.startDrawingQuads();
        tess.setBrightness(15728880);
        int sides = 16;
        double angleStep = 2.0 * Math.PI / sides;

        for (int i = 0; i < sides; i++) {
            double a1 = i * angleStep;
            double a2 = (i + 1) * angleStep;

            double x1 = cx + Math.cos(a1) * poolRadius;
            double z1 = cz + Math.sin(a1) * poolRadius;
            double x2 = cx + Math.cos(a2) * poolRadius;
            double z2 = cz + Math.sin(a2) * poolRadius;

            // Planar bottom disc: center (baseAlpha) -> perimeter (0 alpha)
            tess.setColorRGBA_F(0.0f, 0.70f, 1.0f, baseAlpha);
            tess.addVertex(cx, minY + 0.02, cz);
            tess.setColorRGBA_F(0.0f, 0.35f, 0.90f, 0.0f);
            tess.addVertex(x1, minY + 0.02, z1);
            tess.setColorRGBA_F(0.0f, 0.35f, 0.90f, 0.0f);
            tess.addVertex(x2, minY + 0.02, z2);
            tess.setColorRGBA_F(0.0f, 0.70f, 1.0f, baseAlpha);
            tess.addVertex(cx, minY + 0.02, cz);
        }

        tess.draw();
    }
}

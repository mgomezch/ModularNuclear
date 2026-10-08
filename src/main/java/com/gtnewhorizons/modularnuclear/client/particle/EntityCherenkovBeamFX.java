package com.gtnewhorizons.modularnuclear.client.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class EntityCherenkovBeamFX extends EntityFX {

    private final MTENuclearReactor reactor;

    public EntityCherenkovBeamFX(World world, MTENuclearReactor reactor) {
        super(world, reactor.mClientCenterX, reactor.mClientWaterMinY, reactor.mClientCenterZ);
        this.reactor = reactor;
        this.noClip = true;
        this.particleMaxAge = Integer.MAX_VALUE;
    }

    @Override
    public int getFXLayer() {
        // Layer 3 is custom lit particles rendered after solid blocks and before translucent water
        return 3;
    }

    @Override
    public boolean shouldRenderInPass(int pass) {
        return true;
    }

    @Override
    public int getBrightnessForRender(float partialTickTime) {
        return 15728880;
    }

    @Override
    public void onUpdate() {
        if (!ModularNuclearConfig.enableCherenkovRadiation || reactor == null
            || reactor.getBaseMetaTileEntity() == null
            || reactor.getBaseMetaTileEntity()
                .isDead()
            || !reactor.getBaseMetaTileEntity()
                .isActive()
            || reactor.mClientWaterBlocks <= 0
            || reactor.mClientCherenkovIntensity <= 0f) {
            this.setDead();
            return;
        }

        this.posX = reactor.mClientCenterX;
        this.posY = reactor.mClientWaterMinY;
        this.posZ = reactor.mClientCenterZ;
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;

        int tier = Math.max(1, reactor.mClientTier);
        double R = (tier == 3) ? 6.0 : (tier == 2) ? 4.0 : 2.0;
        this.boundingBox.setBounds(
            this.posX - R * 2,
            this.posY - 1,
            this.posZ - R * 2,
            this.posX + R * 2,
            reactor.mClientWaterMaxY + 2,
            this.posZ + R * 2);
    }

    @Override
    public void renderParticle(Tessellator tessellator, float partialTicks, float f1, float f2, float f3, float f4,
        float f5) {
        if (!ModularNuclearConfig.enableCherenkovRadiation || reactor == null
            || reactor.getBaseMetaTileEntity() == null
            || reactor.getBaseMetaTileEntity()
                .isDead()
            || reactor.mClientWaterBlocks <= 0
            || reactor.mClientCherenkovIntensity <= 0f) {
            return;
        }

        EntityLivingBase view = Minecraft.getMinecraft().renderViewEntity;
        if (view == null) return;

        double camX = view.lastTickPosX + (view.posX - view.lastTickPosX) * partialTicks;
        double camY = view.lastTickPosY + (view.posY - view.lastTickPosY) * partialTicks;
        double camZ = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partialTicks;

        double cx = reactor.mClientCenterX - camX;
        double cz = reactor.mClientCenterZ - camZ;
        double minY = reactor.mClientWaterMinY - camY;
        double maxY = reactor.mClientWaterMaxY - camY;

        double distSq = cx * cx + cz * cz;
        if (distSq > 128.0 * 128.0) return;

        float intensity = Math.min(1.0f, reactor.mClientCherenkovIntensity)
            * ModularNuclearConfig.cherenkovIntensityMultiplier;
        if (intensity <= 0.001f) return;

        long worldTime = view.worldObj != null ? view.worldObj.getTotalWorldTime() : 0;

        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);

        GL11.glEnable(GL11.GL_BLEND);
        // Additive blending produces the luminous ethereal Cherenkov glow
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE, 1, 0);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_CULL_FACE);

        // Native depth testing against already-rendered solid blocks (casings, controller).
        // Since particle layer 3 runs before translucent blocks, water and glass have NOT
        // yet written to depth, allowing the beam to be visible inside the pool through glass/water!
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(false);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);

        renderConcentricBeams(tessellator, cx, cz, minY, maxY, reactor, worldTime, partialTicks, intensity);
        renderPoolBaseGlow(tessellator, cx, cz, minY, reactor, worldTime, partialTicks, intensity);

        GL11.glPopAttrib();
        GL11.glPopMatrix();
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

        int sides = 16;
        double angleStep = 2.0 * Math.PI / sides;

        // One large pair of concentric beams covering the core area
        double innerBaseRadius = R * 0.45;
        double innerTopRadius = R * 0.70;

        double outerBaseRadius = R * 0.90;
        double outerTopRadius = R * 1.30;

        float shimmer = (float) Math.sin((worldTime + partialTicks) * 0.08) * 0.02f;

        // Scale intensity dynamically, clamped at 1.0f for valid OpenGL alpha
        float innerBotAlpha = Math.min(1.0f, Math.max(0f, (0.16f + shimmer) * intensity));
        float innerTopAlpha = 0.0f; // Smooth fade with height over ~3 blocks

        float outerBotAlpha = Math.min(1.0f, Math.max(0f, (0.10f + shimmer) * intensity));
        float outerTopAlpha = 0.0f;

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
        float capAlpha = (innerBotAlpha + outerBotAlpha) * 0.40f;
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
        float pulse = (float) Math.sin((worldTime + partialTicks) * 0.05) * 0.012f;
        float baseAlpha = Math.min(1.0f, Math.max(0f, (0.09f + pulse) * intensity));

        tess.startDrawingQuads();
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

package com.gtnewhorizons.modularnuclear.client.particle;

import net.minecraft.client.particle.EntityFX;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class EntityCherenkovFX extends EntityFX {

    private final float baseAlpha;

    public EntityCherenkovFX(World world, double x, double y, double z, double vx, double vy, double vz,
        float intensity) {
        super(world, x, y, z, vx, vy, vz);
        this.motionX = vx;
        this.motionY = vy;
        this.motionZ = vz;

        // Vivid electric cyan / azure blue hue
        this.particleRed = 0.0F;
        this.particleGreen = 0.80F + (float) (Math.random() * 0.20F);
        this.particleBlue = 1.0F;

        this.baseAlpha = Math.min(1.0F, 0.70F + intensity * 0.30F);
        this.particleAlpha = this.baseAlpha;
        this.particleScale = 1.0F + (float) (Math.random() * 0.8F);
        this.particleMaxAge = 40 + (int) (Math.random() * 30);
        this.noClip = true;

        // Particle texture index 65 is the glowing magical sparkle in particles.png
        this.setParticleTextureIndex(65);
    }

    @Override
    public void onUpdate() {
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;

        if (this.particleAge++ >= this.particleMaxAge) {
            this.setDead();
            return;
        }

        this.moveEntity(this.motionX, this.motionY, this.motionZ);

        // Upward drift in water
        this.motionY += 0.0015;
        this.motionX *= 0.96;
        this.motionZ *= 0.96;

        float lifeFraction = (float) this.particleAge / (float) this.particleMaxAge;
        this.particleAlpha = this.baseAlpha * (1.0F - lifeFraction);
    }

    @Override
    public int getBrightnessForRender(float partialTicks) {
        // Full bright unshaded coordinates (240, 240) -> 0x00F000F0 = 15728880
        return 15728880;
    }
}

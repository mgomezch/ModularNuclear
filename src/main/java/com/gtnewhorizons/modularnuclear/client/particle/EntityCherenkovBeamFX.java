package com.gtnewhorizons.modularnuclear.client.particle;

import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.world.World;

import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Legacy particle placeholder. Cherenkov glow and beam rendering is handled by
 * {@link com.gtnewhorizons.modularnuclear.client.renderer.CherenkovWorldRenderer} in RenderWorldLastEvent.
 */
@SideOnly(Side.CLIENT)
public class EntityCherenkovBeamFX extends EntityFX {

    public EntityCherenkovBeamFX(World world, MTENuclearReactor reactor) {
        super(world, 0, 0, 0);
        this.setDead();
    }

    @Override
    public int getFXLayer() {
        return 3;
    }

    @Override
    public void onUpdate() {
        this.setDead();
    }

    @Override
    public void renderParticle(Tessellator tessellator, float partialTicks, float f1, float f2, float f3, float f4,
        float f5) {}
}

package com.gtnewhorizons.modularnuclear.client.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTECoolantBath;
import com.gtnewhorizons.modularnuclear.network.PacketSyncCoolantBath;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * Draws a coolant bath's items, bobbing and turning in their slots under its glass roof. A particle, as GregTech's
 * Large
 * Molecular Assembler shows its crafts: particles draw before translucent blocks, so the glass does not hide what is
 * under it (a RenderWorldLastEvent drawing comes after the glass has filled the depth buffer). One lives per bath for
 * as long as the client knows the bath.
 */
@SideOnly(Side.CLIENT)
public class CoolantBathItemsFX extends EntityFX {

    private final MTECoolantBath bath;
    private final EntityItem entityItem;

    public CoolantBathItemsFX(MTECoolantBath bath, IGregTechTileEntity bte) {
        super(bte.getWorld(), bte.getXCoord(), bte.getYCoord(), bte.getZCoord(), 0, 0, 0);
        this.bath = bath;
        this.entityItem = new EntityItem(this.worldObj, 0, 0, 0);
        this.entityItem.hoverStart = 0;
        this.motionX = 0;
        this.motionY = 0;
        this.motionZ = 0;
        this.particleMaxAge = Integer.MAX_VALUE;
        this.noClip = true;
    }

    /** Whether this still draws a bath the client knows. */
    public boolean isFor(MTECoolantBath b) {
        return b == bath && !isDead;
    }

    @Override
    public void onUpdate() {
        IGregTechTileEntity bte = bath.getBaseMetaTileEntity();
        if (bte == null || bte.isInvalidTileEntity()
            || bte.getWorld() != worldObj
            || !MTECoolantBath.ACTIVE_CLIENT_BATHS.contains(bath)) {
            setDead();
        }
        // Stays where it is: the bath does not move
        this.prevPosX = this.posX;
        this.prevPosY = this.posY;
        this.prevPosZ = this.posZ;
    }

    @Override
    public void renderParticle(Tessellator tess, float partialTicks, float rX, float rY, float rZ, float rYZ,
        float rXY) {
        PacketSyncCoolantBath.SlotSyncData[] slots = bath.clientSlots;
        if (slots == null || slots.length == 0) return;
        tess.draw();
        float ticks = Minecraft.getMinecraft().renderViewEntity.ticksExisted + partialTicks;
        for (PacketSyncCoolantBath.SlotSyncData s : slots) {
            if (s == null || s.stack == null) continue;
            int[] pos = bath.getSlotWorldCoordinates(s.index);
            if (pos == null) continue;
            double x = pos[0] - interpPosX;
            double y = pos[1] - interpPosY;
            double z = pos[2] - interpPosZ;
            if (x * x + y * y + z * z > 4096.0) continue;

            GL11.glPushMatrix();
            float bob = MathHelper.sin((ticks + s.index * 7.0f) / 16.0f) * 0.05f;
            GL11.glTranslated(x + 0.5, y + 0.2 + bob, z + 0.5);
            GL11.glRotatef((ticks * 2.0f + s.index * 23.0f) % 360.0f, 0.0f, 1.0f, 0.0f);
            float scale = s.stack.getItem() instanceof ItemBlock ? 2.4f : 1.8f;
            GL11.glScalef(scale, scale, scale);
            ItemStack shown = s.stack.copy();
            shown.stackSize = 1;
            entityItem.setEntityItemStack(shown);
            RenderManager.instance.renderEntityWithPosYaw(entityItem, 0, 0, 0, 0, 0);
            GL11.glPopMatrix();
        }
        tess.startDrawingQuads();
    }

    @Override
    public int getBrightnessForRender(float partialTicks) {
        return super.getBrightnessForRender((15 << 20) | (15 << 4));
    }

    @Override
    public int getFXLayer() {
        return 2;
    }

    @Override
    public boolean shouldRenderInPass(int pass) {
        return pass == 2;
    }
}

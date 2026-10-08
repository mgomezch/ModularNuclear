package com.gtnewhorizons.modularnuclear.common.nuclearcontrol;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearBus;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControl;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHatch;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import shedar.mods.ic2.nuclearcontrol.items.ItemSensorKitBase;

public class ItemKitModularNuclear extends ItemSensorKitBase {

    public ItemKitModularNuclear() {
        super("kit_modular_nuclear");
        setUnlocalizedName("modularnuclear.kit_modular_nuclear");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister iconRegister) {
        this.itemIcon = iconRegister.registerIcon("modularnuclear:kit_modular_nuclear");
    }

    @Override
    protected ChunkCoordinates getTargetCoordinates(World world, int x, int y, int z, ItemStack stack) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof IGregTechTileEntity gt) {
            IMetaTileEntity mte = gt.getMetaTileEntity();
            if (mte instanceof MTENuclearReactor) {
                return new ChunkCoordinates(x, y, z);
            }
            MTENuclearReactor reactor = null;
            if (mte instanceof MTEHatchNuclearHatch hatch) reactor = hatch.mReactor;
            else if (mte instanceof MTEHatchNuclearBus bus) reactor = bus.mReactor;
            else if (mte instanceof MTEHatchNuclearControl ctrl) reactor = ctrl.mReactor;
            else if (mte instanceof MTEHatchNuclearControlRod rod) reactor = rod.mReactor;

            if (reactor != null && reactor.getBaseMetaTileEntity() != null) {
                IGregTechTileEntity base = reactor.getBaseMetaTileEntity();
                return new ChunkCoordinates(base.getXCoord(), base.getYCoord(), base.getZCoord());
            }
        }
        return null;
    }

    @Override
    protected ItemStack getItemStackByDamage(int damage) {
        return new ItemStack(NuclearControlIntegration.cardModularNuclear);
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        super.addInformation(stack, player, list, advanced);
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("item.modularnuclear.sensorkit.desc"));
    }
}

package com.gtnewhorizons.modularnuclear.common.item;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import gregtech.api.util.GTUtility;

public class ItemCoriumDust extends Item {

    public ItemCoriumDust() {
        setUnlocalizedName("modularnuclear.corium_dust");
        setTextureName("modularnuclear:corium_dust");
        setCreativeTab(CreativeTabs.tabMaterials);
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean isSelected) {
        if (!world.isRemote && entity instanceof EntityLivingBase living && (world.getTotalWorldTime() % 20 == 0)) {
            GTUtility.applyRadioactivity(living, 4, stack.stackSize);
        }
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean adv) {
        list.add(StatCollector.translateToLocal("GTPP.core.GT_Tooltip_Radioactive"));
        list.add(StatCollector.translateToLocal("GTPP.core.GT_Tooltip_HazmatWarning"));
        super.addInformation(stack, player, list, adv);
    }
}

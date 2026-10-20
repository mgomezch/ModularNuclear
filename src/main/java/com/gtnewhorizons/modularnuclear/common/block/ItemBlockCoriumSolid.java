package com.gtnewhorizons.modularnuclear.common.block;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import gregtech.api.util.GTUtility;

public class ItemBlockCoriumSolid extends ItemBlock {

    public ItemBlockCoriumSolid(Block block) {
        super(block);
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

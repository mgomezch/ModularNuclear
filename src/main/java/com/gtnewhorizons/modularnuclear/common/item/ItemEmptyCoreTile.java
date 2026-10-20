package com.gtnewhorizons.modularnuclear.common.item;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.gtnewhorizons.modularnuclear.ModularNuclear;

public class ItemEmptyCoreTile extends Item {

    public ItemEmptyCoreTile() {
        setUnlocalizedName("modularnuclear.empty_core_tile");
        setTextureName(ModularNuclear.MODID + ":empty_core_tile");
        setMaxStackSize(1);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("item.modularnuclear.empty_core_tile.desc"));
    }
}

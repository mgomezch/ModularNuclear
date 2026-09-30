package com.gtnewhorizons.modularnuclear.common.block;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

import gregtech.common.blocks.ItemCasings;

public class ItemNuclearCasing extends ItemCasings {

    public ItemNuclearCasing(Block block) {
        super(block);
        setHasSubtypes(false);
        setMaxDamage(0);
    }

    @Override
    public int getMetadata(int aMeta) {
        return 0;
    }

    @Override
    public String getUnlocalizedName(ItemStack aStack) {
        return this.field_150939_a.getUnlocalizedName();
    }
}

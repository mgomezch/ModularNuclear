package com.gtnewhorizons.modularnuclear.common.item;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;

import gregtech.api.enums.ItemList;

public class ItemNuclearFluidCell extends Item {

    private final Fluid fluid;

    public ItemNuclearFluidCell(String unlocalizedName, Fluid fluid) {
        this.fluid = fluid;
        setUnlocalizedName(unlocalizedName);
        setTextureName("modularnuclear:items/" + unlocalizedName);
        setMaxStackSize(64);
        setCreativeTab(CreativeTabs.tabMaterials);
    }

    public Fluid getFluid() {
        return fluid;
    }

    @Override
    public boolean hasContainerItem(ItemStack stack) {
        return true;
    }

    @Override
    public ItemStack getContainerItem(ItemStack itemStack) {
        ItemStack empty = ItemList.Cell_Empty.get(1);
        return empty != null ? empty.copy() : null;
    }
}

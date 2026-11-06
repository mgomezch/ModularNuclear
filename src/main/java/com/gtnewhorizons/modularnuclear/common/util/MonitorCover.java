package com.gtnewhorizons.modularnuclear.common.util;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * GregTech's computer monitor cover, which turns on the reactor's and the pump's metrics. Its item is resolved once,
 * so the per-cycle check makes no stacks.
 */
public final class MonitorCover {

    private static Item item;
    private static int meta;

    private MonitorCover() {}

    public static boolean is(ItemStack stack) {
        if (stack == null || !resolve()) return false;
        return stack.getItem() == item && stack.getItemDamage() == meta;
    }

    /** Whether any side of the machine carries the cover. */
    public static boolean isOn(IGregTechTileEntity base) {
        if (base == null || !resolve()) return false;
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            if (is(base.getCoverItemAtSide(side))) return true;
        }
        return false;
    }

    private static boolean resolve() {
        if (item != null) return true;
        if (!ItemList.Cover_Screen.hasBeenSet()) return false;
        ItemStack stack = ItemList.Cover_Screen.get(1L);
        if (stack == null) return false;
        meta = stack.getItemDamage();
        item = stack.getItem();
        return true;
    }
}

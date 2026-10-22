package com.gtnewhorizons.modularnuclear.common.item;

import java.util.List;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;
import gregtech.api.util.GTSplit;
import ic2.api.reactor.IReactor;
import ic2.api.reactor.IReactorComponent;

public class ItemRadiovoltaicPlate extends Item implements IReactorComponent {

    @SideOnly(Side.CLIENT)
    private IIcon iconHV;
    @SideOnly(Side.CLIENT)
    private IIcon iconEV;

    public ItemRadiovoltaicPlate(String aUnlocalized) {
        super();
        this.setUnlocalizedName(aUnlocalized);
        this.setCreativeTab(GregTechAPI.TAB_GREGTECH);
        this.setHasSubtypes(true);
        this.setMaxStackSize(64);
        this.setMaxDamage(0);
    }

    public static int getTier(ItemStack stack) {
        if (stack == null) return 1;
        return getTier(stack.getItemDamage());
    }

    public static int getTier(int damage) {
        return damage == 1 ? 2 : 1;
    }

    public int getTier() {
        return 1;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        int damage = stack != null ? stack.getItemDamage() : 0;
        return damage == 1 ? "item.modularnuclear.radiovoltaic.plate.ev" : "item.modularnuclear.radiovoltaic.plate.hv";
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister iconRegister) {
        iconHV = iconRegister.registerIcon("modularnuclear:gt.radiovoltaic.plate.hv");
        iconEV = iconRegister.registerIcon("modularnuclear:gt.radiovoltaic.plate.ev");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int damage) {
        return damage == 1 ? iconEV : iconHV;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubItems(Item item, net.minecraft.creativetab.CreativeTabs tab, List list) {
        list.add(new ItemStack(item, 1, 0));
        list.add(new ItemStack(item, 1, 1));
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack aStack, EntityPlayer aPlayer, List aList, boolean aF3_H) {
        int damage = aStack != null ? aStack.getItemDamage() : 0;
        if (damage == 1) {
            GTSplit.splitLocalizedFormatted(aList, "item.modularnuclear.radiovoltaic.plate.ev.desc");
        } else {
            GTSplit.splitLocalizedFormatted(aList, "item.modularnuclear.radiovoltaic.plate.hv.desc");
        }
        GTSplit.splitLocalizedFormatted(aList, "item.modularnuclear.radiovoltaic.plate.common.desc");
    }

    @Override
    public boolean acceptUraniumPulse(IReactor reactor, ItemStack yourStack, ItemStack pulsingStack, int youX, int youY,
        int pulseX, int pulseY, boolean heatrun) {
        // Absorbs pulse completely without reflecting back
        return false;
    }

    @Override
    public boolean canStoreHeat(IReactor aReactor, ItemStack aStack, int x, int y) {
        return false;
    }

    @Override
    public int getMaxHeat(IReactor aReactor, ItemStack aStack, int x, int y) {
        return 0;
    }

    @Override
    public int getCurrentHeat(IReactor aReactor, ItemStack aStack, int x, int y) {
        return 0;
    }

    @Override
    public float influenceExplosion(IReactor aReactor, ItemStack aStack) {
        return -1.0F;
    }

    @Override
    public int alterHeat(IReactor aReactor, ItemStack aStack, int x, int y, int aHeat) {
        return aHeat;
    }

    @Override
    public void processChamber(IReactor aReactor, ItemStack aStack, int x, int y, boolean aHeatRun) {}
}

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

    private final int tier; // 1 for HV (1024 EU/t), 2 for EV (4096 EU/t)
    @SideOnly(Side.CLIENT)
    private IIcon mIcon;

    public ItemRadiovoltaicPlate(String aUnlocalized, int aTier) {
        super();
        this.tier = aTier;
        this.setUnlocalizedName(aUnlocalized);
        this.setCreativeTab(GregTechAPI.TAB_GREGTECH);
        this.setMaxStackSize(64);
        this.setMaxDamage(0);
    }

    public int getTier() {
        return tier;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister iconRegister) {
        if (tier == 1) {
            mIcon = iconRegister.registerIcon("modularnuclear:gt.radiovoltaic.plate.hv");
        } else {
            mIcon = iconRegister.registerIcon("modularnuclear:gt.radiovoltaic.plate.ev");
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int damage) {
        return mIcon;
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack aStack, EntityPlayer aPlayer, List aList, boolean aF3_H) {
        if (tier == 1) {
            GTSplit.splitLocalizedFormatted(aList, "item.modularnuclear.radiovoltaic.plate.hv.desc");
        } else {
            GTSplit.splitLocalizedFormatted(aList, "item.modularnuclear.radiovoltaic.plate.ev.desc");
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

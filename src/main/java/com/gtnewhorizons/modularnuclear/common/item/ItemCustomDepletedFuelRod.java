package com.gtnewhorizons.modularnuclear.common.item;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import com.gtnewhorizons.modularnuclear.common.nuclear.CustomFuelMix;

import gregtech.api.items.GTGenericItem;

/**
 * Depleted Quad Nuclear Fuel Rod holding residual actinide composition for reprocessing.
 */
public class ItemCustomDepletedFuelRod extends GTGenericItem {

    public ItemCustomDepletedFuelRod() {
        super("modularnuclear.fuelrod.depleted.custom4", "Depleted Quad Fuel Rod (Custom Mix)", null);
        setMaxStackSize(64);
    }

    @Override
    public void addAdditionalToolTips(List<String> aList, ItemStack aStack, EntityPlayer aPlayer) {
        CustomFuelMix mix = CustomFuelMix.fromStack(aStack);
        if (mix != null) {
            aList.add(EnumChatFormatting.DARK_RED + "Depleted Custom Core Fuel");
            aList.add(EnumChatFormatting.GRAY + "Original Initial Composition:");
            for (Map.Entry<CustomFuelMix.ActinideIsotope, Double> entry : mix.getIsotopeFractions()
                .entrySet()) {
                double pct = entry.getValue() * 100.0;
                aList.add(String.format(Locale.US, "  %s: %.1f%%", entry.getKey().symbol, pct));
            }
            aList.add(
                EnumChatFormatting.ITALIC + ""
                    + EnumChatFormatting.DARK_GRAY
                    + "Ready for actinide separation in Thermal Centrifuge");
        } else {
            aList.add(EnumChatFormatting.DARK_RED + "Depleted Custom Core Fuel");
        }
    }
}

package com.gtnewhorizons.modularnuclear.common.item;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import com.gtnewhorizons.modularnuclear.common.nuclear.CustomFuelMix;

import gregtech.api.items.ItemRadioactiveCell;

/**
 * Quad Nuclear Fuel Rod holding a custom compounded actinide mix.
 */
public class ItemCustomFuelRod extends ItemRadioactiveCell {

    public ItemCustomFuelRod() {
        super("modularnuclear.fuelrod.custom4", "Quad Fuel Rod (Custom Mix)", 4);
        this.maxDmg = 40000;
        setMaxStackSize(64);
    }

    @Override
    public int getMaxDamageEx() {
        return 40000;
    }

    @Override
    public void addAdditionalToolTips(List<String> aList, ItemStack aStack, EntityPlayer aPlayer) {
        CustomFuelMix mix = CustomFuelMix.fromStack(aStack);
        if (mix != null) {
            aList.add(EnumChatFormatting.GOLD + "Custom Actinic Composition:");
            for (Map.Entry<CustomFuelMix.ActinideIsotope, Double> entry : mix.getIsotopeFractions()
                .entrySet()) {
                double pct = entry.getValue() * 100.0;
                EnumChatFormatting color = entry.getKey().fissile ? EnumChatFormatting.AQUA : EnumChatFormatting.GREEN;
                aList.add(
                    String.format(
                        Locale.US,
                        "  %s%s: %.1f%% (%s)",
                        color,
                        entry.getKey().symbol,
                        pct,
                        entry.getKey().fissile ? "Fissile" : "Fertile"));
            }
            aList.add(
                String.format(
                    Locale.US,
                    "%sPeak Reactivity: %s%.0f °C",
                    EnumChatFormatting.YELLOW,
                    EnumChatFormatting.WHITE,
                    mix.peakReactivityTemp));
            aList.add(
                String.format(
                    Locale.US,
                    "%sThermal Floor: %s%.0f °C",
                    EnumChatFormatting.DARK_RED,
                    EnumChatFormatting.WHITE,
                    mix.floorTemp));
            aList.add(
                String.format(
                    Locale.US,
                    "%sThermal Mult: %s%.2fx %s| Fast Mult: %s%.2fx",
                    EnumChatFormatting.BLUE,
                    EnumChatFormatting.WHITE,
                    mix.baseThermalFissionMultiplier,
                    EnumChatFormatting.DARK_GRAY,
                    EnumChatFormatting.WHITE,
                    mix.baseFastFissionMultiplier));
            aList.add(
                String.format(
                    Locale.US,
                    "%sBase Neutrons: %s%d n/t",
                    EnumChatFormatting.DARK_PURPLE,
                    EnumChatFormatting.WHITE,
                    mix.baseNeutrons));
        } else {
            aList.add(EnumChatFormatting.GRAY + "Empty / uncalibrated custom fuel rod");
        }
        super.addAdditionalToolTips(aList, aStack, aPlayer);
    }
}

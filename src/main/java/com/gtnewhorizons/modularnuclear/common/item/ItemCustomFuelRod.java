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

    public int getMaxDamageEx(ItemStack stack) {
        if (stack != null && stack.stackTagCompound != null) {
            if (stack.stackTagCompound.hasKey(CustomFuelMix.NBT_TAG_ROOT)) {
                net.minecraft.nbt.NBTTagCompound root = stack.stackTagCompound.getCompoundTag(CustomFuelMix.NBT_TAG_ROOT);
                if (root.hasKey("MaxDurability")) {
                    int max = root.getInteger("MaxDurability");
                    if (max > 0) return max;
                }
            }
            if (stack.stackTagCompound.hasKey("MaxDurability")) {
                int max = stack.stackTagCompound.getInteger("MaxDurability");
                if (max > 0) return max;
            }
            if (CustomFuelMix.hasCustomMix(stack)) {
                CustomFuelMix mix = CustomFuelMix.fromStack(stack);
                if (mix != null && mix.defaultDurability > 0) {
                    return mix.defaultDurability;
                }
            }
        }
        return this.maxDmg;
    }

    public int getMaxNuclearDurability(ItemStack stack) {
        return getMaxDamageEx(stack);
    }

    @Override
    public void setDamageForStack(ItemStack stack, int advDmg) {
        com.gtnewhorizon.gtnhlib.item.ItemStackNBT.setInteger(stack, "advDmg", advDmg);
        int max = getMaxDamageEx(stack);
        if (max > 0) {
            double p = (double) advDmg / (double) max;
            int newDmg = (int) (100.0 * p);
            if (newDmg >= 100) {
                newDmg = 99;
            }
            stack.setItemDamage(newDmg);
        }
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

        int max = getMaxDamageEx(aStack);
        int curDmg = getDamageOfStack(aStack);
        int rDmg = max > 0 ? curDmg * 6 / max : 0;
        EnumChatFormatting color2 = switch (rDmg) {
            case 0, 1 -> EnumChatFormatting.WHITE;
            case 2, 3, 4 -> EnumChatFormatting.GRAY;
            default -> EnumChatFormatting.DARK_GRAY;
        };
        aList.add(
            EnumChatFormatting.WHITE + net.minecraft.util.StatCollector.translateToLocalFormatted(
                "gt.item.desc.durability",
                color2 + com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber(Math.max(0, max - curDmg)) + EnumChatFormatting.WHITE,
                com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber(max)));
    }
}

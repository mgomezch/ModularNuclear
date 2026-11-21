package com.gtnewhorizons.modularnuclear.common.item;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;

import com.gtnewhorizons.modularnuclear.client.texture.FuelRodIcons;
import com.gtnewhorizons.modularnuclear.common.nuclear.CustomFuelMix;
import com.gtnewhorizons.modularnuclear.common.nuclear.EngineParameters;
import com.gtnewhorizons.modularnuclear.common.nuclear.NeutronUnits;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.nuclear.TileProperties;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.items.ItemRadioactiveCell;

/**
 * Nuclear Fuel Rod holding a custom compounded actinide mix (Single, Dual, or Quad).
 */
public class ItemCustomFuelRod extends ItemRadioactiveCell {

    public final int rodSize;

    public ItemCustomFuelRod(int rodSize) {
        super(
            "modularnuclear.fuelrod.custom" + rodSize,
            rodSize == 1 ? "Fuel Rod (Custom Mix)"
                : rodSize == 2 ? "Dual Fuel Rod (Custom Mix)" : "Quad Fuel Rod (Custom Mix)",
            rodSize);
        this.rodSize = rodSize;
        this.maxDmg = 10000 * rodSize;
        setMaxStackSize(64);
    }

    public ItemCustomFuelRod() {
        this(4);
    }

    // The icon is GregTech's uranium rod, its window coloured by the rod's mix; there's no texture file to register
    // See Note [Custom fuel rod textures]
    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {}

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int meta) {
        return FuelRodIcons.base(FuelRodIcons.Kind.of(false, rodSize));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconIndex(ItemStack stack) {
        return FuelRodIcons.icon(FuelRodIcons.Kind.of(false, rodSize), stack);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(ItemStack stack, int pass) {
        return FuelRodIcons.icon(FuelRodIcons.Kind.of(false, rodSize), stack);
    }

    @Override
    public int getMaxDamageEx() {
        return this.maxDmg;
    }

    public int getMaxDamageEx(ItemStack stack) {
        if (stack != null && stack.stackTagCompound != null) {
            if (stack.stackTagCompound.hasKey(CustomFuelMix.NBT_TAG_ROOT)) {
                net.minecraft.nbt.NBTTagCompound root = stack.stackTagCompound
                    .getCompoundTag(CustomFuelMix.NBT_TAG_ROOT);
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
                aList.add(
                    String.format(
                        Locale.US,
                        "  %s%s: %s%.1f%%",
                        EnumChatFormatting.AQUA,
                        entry.getKey().symbol,
                        EnumChatFormatting.WHITE,
                        pct));
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
            // See Note [Players see a fuel's effective values]
            EngineParameters p = NuclearSimulationEngine.params();
            aList.add(
                String.format(
                    Locale.US,
                    "%sThermal Mult: %s%.2fx %s| Fast Mult: %s%.2fx",
                    EnumChatFormatting.BLUE,
                    EnumChatFormatting.WHITE,
                    TileProperties.thermalFissionYield(p, mix.baseThermalFissionMultiplier),
                    EnumChatFormatting.DARK_GRAY,
                    EnumChatFormatting.WHITE,
                    TileProperties.fastFissionYield(p, mix.baseFastFissionMultiplier)));
            aList.add(
                String.format(
                    Locale.US,
                    "%sBase neutrons: %s%s",
                    EnumChatFormatting.DARK_PURPLE,
                    EnumChatFormatting.WHITE,
                    NeutronUnits.rate(mix.getBaseNeutrons(this.rodSize))));
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
                color2
                    + com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil
                        .formatNumber(Math.max(0, max - curDmg))
                    + EnumChatFormatting.WHITE,
                com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber(max)));
    }
}

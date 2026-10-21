package com.gtnewhorizons.modularnuclear.common.item;

import java.util.Locale;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ControlRodData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.CoolantItemData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.HeatExchangerData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.HeatVentData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.InsulatorData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ModeratorData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.RadiovoltaicData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ReflectorData;
import com.gtnewhorizons.modularnuclear.common.config.data.FuelData;
import com.gtnewhorizons.modularnuclear.common.config.data.TierData;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import gregtech.api.items.ItemRadioactiveCell;
import ic2.core.item.reactor.ItemReactorUranium;

/**
 * Appends configured reactor physics and component tooltips to MPTR fuel rods, moderators,
 * reflectors, absorbers, insulators, heat vents, heat exchangers, and radiovoltaic cells.
 */
public class NuclearFuelTooltipHandler {

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        if (event == null || event.itemStack == null) return;

        ItemStack stack = event.itemStack;
        String unlocalizedName = stack.getUnlocalizedName();
        if (unlocalizedName == null) return;

        // 1. Configured nuclear fuel rod tooltips
        FuelData fd = ReactorDataRegistry.findFuel(stack);
        if (fd != null && !fd.isLiquid && !(stack.getItem() instanceof ItemCustomFuelRod)) {
            appendFuelRodTooltips(event, stack, fd);
            return;
        }

        // 2. Moderator tooltips
        ModeratorData mod = ReactorDataRegistry.findModerator(stack);
        if (mod != null) {
            appendModeratorTooltips(event, stack, mod);
            return;
        }

        // 3. Neutron reflector tooltips
        ReflectorData ref = ReactorDataRegistry.findReflector(stack);
        if (ref != null) {
            appendReflectorTooltips(event, stack, ref);
            return;
        }

        // 4. Control rod tooltips
        ControlRodData cr = ReactorDataRegistry.findControlRod(stack);
        if (cr != null) {
            appendControlRodTooltips(event, cr);
            return;
        }

        // 5. Thermal insulator tooltips
        InsulatorData ins = ReactorDataRegistry.findInsulator(stack);
        if (ins != null) {
            appendInsulatorTooltips(event, ins);
            return;
        }

        // 6. Heat vent tooltips
        HeatVentData vent = ReactorDataRegistry.findHeatVent(stack);
        if (vent != null) {
            appendHeatVentTooltips(event, vent);
            return;
        }

        // 7. Heat exchanger tooltips
        HeatExchangerData hex = ReactorDataRegistry.findHeatExchanger(stack);
        if (hex != null) {
            appendHeatExchangerTooltips(event, hex);
            return;
        }

        // 8. Radiovoltaic cell tooltips
        RadiovoltaicData rv = ReactorDataRegistry.findRadiovoltaic(stack);
        if (rv != null) {
            appendRadiovoltaicTooltips(event, rv);
            return;
        }

        // 9. Coolant item tooltips
        CoolantItemData cl = ReactorDataRegistry.findCoolantItem(stack);
        if (cl != null) {
            appendCoolantItemTooltips(event, stack, cl);
            return;
        }
    }

    private void appendFuelRodTooltips(ItemTooltipEvent event, ItemStack stack, FuelData fd) {
        int totalDurability = fd.defaultDurability;
        int remainingDurability = totalDurability;

        if (stack.getItem() instanceof ItemRadioactiveCell radCell) {
            int intrinsicMax = radCell.getMaxDamageEx();
            int curDmg = radCell.getDamageOfStack(stack);
            double remainingFrac = (intrinsicMax > 0) ? Math.max(0.0, 1.0 - ((double) curDmg / (double) intrinsicMax))
                : 1.0;
            remainingDurability = (int) Math.round(remainingFrac * totalDurability);
        } else if (stack.getItem() instanceof ItemReactorUranium) {
            int intrinsicMax = stack.getMaxDamage();
            int curDmg = stack.getItemDamage();
            double remainingFrac = (intrinsicMax > 0) ? Math.max(0.0, 1.0 - ((double) curDmg / (double) intrinsicMax))
                : 1.0;
            remainingDurability = (int) Math.round(remainingFrac * totalDurability);
        } else if (stack.stackTagCompound != null && stack.stackTagCompound.hasKey("advDmg")) {
            int curDmg = stack.stackTagCompound.getInteger("advDmg");
            remainingDurability = Math.max(0, totalDurability - curDmg);
        } else if (stack.isItemStackDamageable()) {
            int curDmg = stack.getItemDamage();
            remainingDurability = Math.max(0, totalDurability - curDmg);
        }

        int rDmg = (totalDurability > 0) ? (totalDurability - remainingDurability) * 6 / totalDurability : 0;
        EnumChatFormatting durColor = switch (rDmg) {
            case 0, 1 -> EnumChatFormatting.GREEN;
            case 2, 3 -> EnumChatFormatting.YELLOW;
            default -> EnumChatFormatting.RED;
        };

        String durabilityLine = EnumChatFormatting.GRAY + "Durability: "
            + durColor
            + String.format(Locale.US, "%,d", remainingDurability)
            + EnumChatFormatting.GRAY
            + " / "
            + EnumChatFormatting.WHITE
            + String.format(Locale.US, "%,d", totalDurability);

        // Replace built-in GT/IC2 durability line if present, otherwise append
        boolean replaced = false;
        String durabilityPrefix = StatCollector.translateToLocal("gt.item.desc.durability");
        int colonIdx = (durabilityPrefix != null) ? durabilityPrefix.indexOf(':') : -1;
        String unlocPrefix = (colonIdx != -1) ? durabilityPrefix.substring(0, colonIdx + 1) : "Durability:";

        for (int i = 0; i < event.toolTip.size(); i++) {
            String stripped = EnumChatFormatting.getTextWithoutFormattingCodes(event.toolTip.get(i));
            if (stripped != null && (stripped.startsWith("Durability:") || stripped.startsWith("Прочность:")
                || stripped.startsWith(unlocPrefix))) {
                event.toolTip.set(i, durabilityLine);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            event.toolTip.add(durabilityLine);
        }

        // Optimal peak & floor temperatures
        event.toolTip.add(
            EnumChatFormatting.AQUA + "Reactivity: "
                + EnumChatFormatting.GOLD
                + String.format(Locale.US, "%,.0f °C (peak)", fd.peakReactivityTemp)
                + EnumChatFormatting.DARK_GRAY
                + " | "
                + EnumChatFormatting.RED
                + String.format(Locale.US, "%,.0f °C (floor)", fd.floorTemp));

        // Base neutrons & Archetype
        event.toolTip.add(
            EnumChatFormatting.GRAY + "Base neutrons: "
                + EnumChatFormatting.WHITE
                + String.format(Locale.US, "%d n/t", fd.baseNeutrons)
                + EnumChatFormatting.DARK_GRAY
                + " | "
                + EnumChatFormatting.GRAY
                + "Archetype: "
                + EnumChatFormatting.YELLOW
                + fd.archetype);

        // Fission multipliers
        event.toolTip.add(
            EnumChatFormatting.GRAY + "Fission: "
                + EnumChatFormatting.LIGHT_PURPLE
                + String.format(Locale.US, "%.2fx (thermal)", fd.thermalFissionMultiplier)
                + EnumChatFormatting.DARK_GRAY
                + " | "
                + EnumChatFormatting.BLUE
                + String.format(Locale.US, "%.2fx (fast)", fd.fastFissionMultiplier));

        // Required casing tier
        if (fd.requiredTier > 0) {
            TierData td = ReactorDataRegistry.getTier(fd.requiredTier);
            String tierName = (td != null) ? td.name : ("Tier " + fd.requiredTier);
            event.toolTip.add(EnumChatFormatting.RED + "Required casing tier: " + EnumChatFormatting.WHITE + tierName);
        }
    }

    private void appendModeratorTooltips(ItemTooltipEvent event, ItemStack stack, ModeratorData mod) {
        event.toolTip.add(EnumChatFormatting.AQUA + "MPTR moderator: " + EnumChatFormatting.WHITE + mod.name);
        event.toolTip.add(
            EnumChatFormatting.GRAY + "Moderation: "
                + EnumChatFormatting.GREEN
                + String.format(Locale.US, "%.0f%%", mod.moderation * 100));

        int curDmg = 0;
        if (stack.stackTagCompound != null && stack.stackTagCompound.hasKey("advDmg")) {
            curDmg = stack.stackTagCompound.getInteger("advDmg");
        }
        int remaining = Math.max(0, mod.durability - curDmg);
        event.toolTip.add(
            EnumChatFormatting.GRAY + "Durability: "
                + EnumChatFormatting.YELLOW
                + String.format(Locale.US, "%,d / %,d", remaining, mod.durability));

        if (mod.wignerEnergyPerFast > 0.0) {
            double stored = 0.0;
            if (stack.stackTagCompound != null && stack.stackTagCompound.hasKey("wignerEnergy")) {
                stored = stack.stackTagCompound.getDouble("wignerEnergy");
            }
            event.toolTip.add(
                EnumChatFormatting.GOLD + "Wigner energy: "
                    + EnumChatFormatting.WHITE
                    + String.format(Locale.US, "%,.0f / %,.0f HU", stored, mod.wignerMaxEnergy));
            event.toolTip.add(
                EnumChatFormatting.GRAY + "Wigner kinetics: "
                    + EnumChatFormatting.WHITE
                    + String.format(
                        Locale.US,
                        "Stores %.0f HU/fast, releases > %.0f °C",
                        mod.wignerEnergyPerFast,
                        mod.wignerReleaseOnsetTemp));
        }
    }

    private void appendReflectorTooltips(ItemTooltipEvent event, ItemStack stack, ReflectorData ref) {
        event.toolTip.add(EnumChatFormatting.AQUA + "MPTR neutron reflector: " + EnumChatFormatting.WHITE + ref.name);
        event.toolTip.add(
            EnumChatFormatting.GRAY + "Reflection: "
                + EnumChatFormatting.GREEN
                + String.format(Locale.US, "%.0f%% (thermal)", ref.scatteringThermal * 100)
                + EnumChatFormatting.DARK_GRAY
                + " | "
                + EnumChatFormatting.BLUE
                + String.format(Locale.US, "%.0f%% (fast)", ref.scatteringFast * 100));

        if (ref.durability > 0) {
            int curDmg = 0;
            if (stack.isItemStackDamageable()) {
                curDmg = stack.getItemDamage();
            } else if (stack.stackTagCompound != null && stack.stackTagCompound.hasKey("advDmg")) {
                curDmg = stack.stackTagCompound.getInteger("advDmg");
            }
            int remaining = Math.max(0, ref.durability - curDmg);
            event.toolTip.add(
                EnumChatFormatting.GRAY + "Durability: "
                    + EnumChatFormatting.YELLOW
                    + String.format(Locale.US, "%,d / %,d", remaining, ref.durability));
        } else {
            event.toolTip.add(EnumChatFormatting.GRAY + "Durability: " + EnumChatFormatting.GREEN + "Infinite");
        }
    }

    private void appendControlRodTooltips(ItemTooltipEvent event, ControlRodData cr) {
        event.toolTip.add(EnumChatFormatting.AQUA + "MPTR control rod: " + EnumChatFormatting.WHITE + cr.name);
        event.toolTip.add(
            EnumChatFormatting.GRAY + "Max absorption: "
                + EnumChatFormatting.RED
                + String.format(Locale.US, "%.0f%% (thermal)", cr.maxThermalAbsorption * 100)
                + EnumChatFormatting.DARK_GRAY
                + " | "
                + EnumChatFormatting.DARK_RED
                + String.format(Locale.US, "%.0f%% (fast)", cr.maxFastAbsorption * 100));
    }

    private void appendInsulatorTooltips(ItemTooltipEvent event, InsulatorData ins) {
        event.toolTip.add(EnumChatFormatting.AQUA + "MPTR thermal insulator: " + EnumChatFormatting.WHITE + ins.name);
        event.toolTip.add(
            EnumChatFormatting.GRAY + "Heat dampening: "
                + EnumChatFormatting.GREEN
                + String.format(Locale.US, "%.0f%%", ins.dampening * 100));
    }

    private void appendHeatVentTooltips(ItemTooltipEvent event, HeatVentData vent) {
        event.toolTip.add(EnumChatFormatting.AQUA + "MPTR heat vent: " + EnumChatFormatting.WHITE + vent.name);
        String maxTempStr = (vent.maxTemperature > 0.0)
            ? (EnumChatFormatting.DARK_GRAY + " | "
                + EnumChatFormatting.RED
                + String.format(Locale.US, "Max temp: %,.0f °C", vent.maxTemperature))
            : "";
        event.toolTip.add(
            EnumChatFormatting.GRAY + "Self-cooling rate: "
                + EnumChatFormatting.AQUA
                + String.format(Locale.US, "%.1f HU/t", vent.selfCoolingRate)
                + maxTempStr);
    }

    private void appendHeatExchangerTooltips(ItemTooltipEvent event, HeatExchangerData hex) {
        event.toolTip.add(EnumChatFormatting.AQUA + "MPTR heat exchanger: " + EnumChatFormatting.WHITE + hex.name);
        String maxTempStr = (hex.maxTemperature > 0.0)
            ? (EnumChatFormatting.DARK_GRAY + " | "
                + EnumChatFormatting.RED
                + String.format(Locale.US, "Max temp: %,.0f °C", hex.maxTemperature))
            : "";
        event.toolTip.add(
            EnumChatFormatting.GRAY + "Transfer rate: "
                + EnumChatFormatting.AQUA
                + String.format(Locale.US, "%.1f HU/t", hex.switchTransferRate)
                + maxTempStr);
    }

    private void appendRadiovoltaicTooltips(ItemTooltipEvent event, RadiovoltaicData rv) {
        long maxEU = (rv.maxEU > 0) ? rv.maxEU : ((rv.tier >= 2) ? 4096 : 1024);
        event.toolTip.add(EnumChatFormatting.AQUA + "MPTR radiovoltaic cell: " + EnumChatFormatting.WHITE + rv.name);
        String effStr = (rv.euConversionEfficiency < 0.999 || rv.euConversionEfficiency > 1.001)
            ? String.format(Locale.US, " (%.0f%% eff)", rv.euConversionEfficiency * 100.0)
            : "";
        event.toolTip.add(
            EnumChatFormatting.GRAY + "Direct generation: "
                + EnumChatFormatting.GREEN
                + String.format(Locale.US, "Tier %d (up to %,d EU/t%s)", rv.tier, maxEU, effStr));
    }

    private void appendCoolantItemTooltips(ItemTooltipEvent event, ItemStack stack, CoolantItemData cl) {
        event.toolTip.add(EnumChatFormatting.AQUA + "MPTR coolant item: " + EnumChatFormatting.WHITE + cl.name);
        double huPerPt = cl.getHuPerDurability();
        if (stack.getItem() instanceof ic2.api.reactor.IReactorComponent comp) {
            int maxHeat = comp.getMaxHeat(null, stack, 0, 0);
            double totalHu = maxHeat * huPerPt;
            event.toolTip.add(
                EnumChatFormatting.GRAY + "Heat capacity: "
                    + EnumChatFormatting.GOLD
                    + String.format(Locale.US, "%,.0f HU", totalHu)
                    + EnumChatFormatting.DARK_GRAY
                    + String.format(Locale.US, " (%.1f HU/pt)", huPerPt));
        } else {
            event.toolTip.add(
                EnumChatFormatting.GRAY + "Heat capacity: "
                    + EnumChatFormatting.GOLD
                    + String.format(Locale.US, "%,.0f HU", cl.heatCapacity)
                    + EnumChatFormatting.DARK_GRAY
                    + String.format(Locale.US, " (%.1f HU/pt)", huPerPt));
        }
    }
}

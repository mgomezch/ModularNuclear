package com.gtnewhorizons.modularnuclear.common.item;

import java.util.Locale;

import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearFuelType;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Appends optimal neutron reactivity temperature tooltip to GTNH nuclear fuel rods.
 */
public class NuclearFuelTooltipHandler {

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        if (event == null || event.itemStack == null) return;

        String unlocalizedName = event.itemStack.getUnlocalizedName();
        if (unlocalizedName == null) return;

        // Skip non-fuel items quickly
        NuclearFuelType fuel = NuclearFuelType.fromName(unlocalizedName);
        if (fuel != null) {
            String lower = unlocalizedName.toLowerCase();
            // Verify it looks like a fuel rod item or cell, rather than generic ore/dust
            if (lower.contains("rod") || lower.contains("cell")
                || lower.contains("fuel")
                || lower.contains("thecore")) {
                event.toolTip.add(
                    EnumChatFormatting.AQUA + "Peak Reactivity: "
                        + EnumChatFormatting.GOLD
                        + String.format(Locale.US, "%,.0f °C", fuel.peakReactivityTemp));
            }
        }

        com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ModeratorData mod =
            com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.findModerator(event.itemStack);
        if (mod != null) {
            event.toolTip.add(EnumChatFormatting.AQUA + "MPTR Moderator: " + EnumChatFormatting.WHITE + mod.name);
            event.toolTip.add(EnumChatFormatting.GRAY + "Moderation: " + EnumChatFormatting.GREEN
                + String.format(Locale.US, "%.0f%%", mod.moderation * 100));

            int curDmg = 0;
            if (event.itemStack.stackTagCompound != null && event.itemStack.stackTagCompound.hasKey("advDmg")) {
                curDmg = event.itemStack.stackTagCompound.getInteger("advDmg");
            }
            int remaining = Math.max(0, mod.durability - curDmg);
            event.toolTip.add(EnumChatFormatting.GRAY + "Durability: " + EnumChatFormatting.YELLOW
                + String.format(Locale.US, "%,d / %,d", remaining, mod.durability));

            if (mod.thermalEmissionMaxPerFast > 0.0) {
                event.toolTip.add(EnumChatFormatting.RED + "Thermal Hazard: " + EnumChatFormatting.WHITE
                    + String.format(Locale.US, "Emits up to %.1f th-n/fast > %.0f °C", mod.thermalEmissionMaxPerFast, mod.thermalEmissionOnsetTemp));
            }
        }
    }
}

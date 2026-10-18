package com.gtnewhorizons.modularnuclear.common.item;

import java.util.Locale;

import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

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
        com.gtnewhorizons.modularnuclear.common.config.data.FuelData fd = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .findFuel(event.itemStack);
        if (fd != null && !fd.isLiquid) {
            event.toolTip.add(
                EnumChatFormatting.AQUA + "Peak Reactivity: "
                    + EnumChatFormatting.GOLD
                    + String.format(Locale.US, "%,.0f °C", fd.peakReactivityTemp));
        }

        com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ModeratorData mod = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .findModerator(event.itemStack);
        if (mod != null) {
            event.toolTip.add(EnumChatFormatting.AQUA + "MPTR Moderator: " + EnumChatFormatting.WHITE + mod.name);
            event.toolTip.add(
                EnumChatFormatting.GRAY + "Moderation: "
                    + EnumChatFormatting.GREEN
                    + String.format(Locale.US, "%.0f%%", mod.moderation * 100));

            int curDmg = 0;
            if (event.itemStack.stackTagCompound != null && event.itemStack.stackTagCompound.hasKey("advDmg")) {
                curDmg = event.itemStack.stackTagCompound.getInteger("advDmg");
            }
            int remaining = Math.max(0, mod.durability - curDmg);
            event.toolTip.add(
                EnumChatFormatting.GRAY + "Durability: "
                    + EnumChatFormatting.YELLOW
                    + String.format(Locale.US, "%,d / %,d", remaining, mod.durability));

            if (mod.wignerEnergyPerFast > 0.0) {
                double stored = 0.0;
                if (event.itemStack.stackTagCompound != null
                    && event.itemStack.stackTagCompound.hasKey("wignerEnergy")) {
                    stored = event.itemStack.stackTagCompound.getDouble("wignerEnergy");
                }
                event.toolTip.add(
                    EnumChatFormatting.GOLD + "Wigner Energy: "
                        + EnumChatFormatting.WHITE
                        + String.format(Locale.US, "%,.0f / %,.0f HU", stored, mod.wignerMaxEnergy));
                event.toolTip.add(
                    EnumChatFormatting.GRAY + "Wigner Kinetics: "
                        + EnumChatFormatting.WHITE
                        + String.format(
                            Locale.US,
                            "Stores %.0f HU/fast, releases > %.0f °C",
                            mod.wignerEnergyPerFast,
                            mod.wignerReleaseOnsetTemp));
            }
        }
    }
}

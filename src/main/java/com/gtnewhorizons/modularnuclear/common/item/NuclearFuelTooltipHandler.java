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
    }
}

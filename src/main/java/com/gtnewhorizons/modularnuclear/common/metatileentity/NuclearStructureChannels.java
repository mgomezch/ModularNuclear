package com.gtnewhorizons.modularnuclear.common.metatileentity;

import net.minecraft.item.ItemStack;

import com.gtnewhorizon.structurelib.StructureLibAPI;

import gregtech.api.structure.IStructureChannels;

public enum NuclearStructureChannels implements IStructureChannels {

    NUCLEAR_HATCH("nuclear_hatch", "Nuclear hatch tier");

    private final String channel;
    private final String defaultTooltip;

    NuclearStructureChannels(String aChannel, String defaultTooltip) {
        this.channel = aChannel;
        this.defaultTooltip = defaultTooltip;
    }

    @Override
    public String get() {
        return channel;
    }

    @Override
    public String getDefaultTooltip() {
        return defaultTooltip;
    }

    @Override
    public void registerAsIndicator(ItemStack indicator, int channelValue) {
        if (indicator == null) return;
        if (channelValue > 0) {
            try {
                StructureLibAPI.registerChannelItem(get(), "modularnuclear", channelValue, indicator);
                return;
            } catch (Throwable ignored) {}
        }
        try {
            if (!StructureLibAPI.isDebugEnabled()) {
                com.gtnewhorizon.structurelib.ChannelDescription.item(get(), channelValue, indicator);
            }
        } catch (Throwable ignored) {}
        try {
            com.gtnewhorizon.gtnhlib.util.AnimatedTooltipHandler.addItemTooltip(
                indicator,
                com.gtnewhorizon.gtnhlib.util.AnimatedTooltipHandler.translatedText("structurelib.tooltip.channelvalue", channelValue, get())
            );
            com.gtnewhorizon.gtnhlib.util.AnimatedTooltipHandler.addItemTooltip(
                indicator,
                com.gtnewhorizon.gtnhlib.util.AnimatedTooltipHandler.translatedText("structurelib.tooltip.indicator_dnd", channelValue, get())
            );
        } catch (Throwable ignored) {}
    }

    @Override
    public int getValueClamped(ItemStack trigger, int min, int max) {
        if (trigger == null) {
            return min;
        }
        if (!com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor.hasSubChannel(trigger, get())) {
            return min;
        }
        int raw = com.gtnewhorizon.structurelib.alignment.constructable.ChannelDataAccessor
            .getChannelData(trigger, get());
        return Math.max(min, Math.min(max, raw));
    }

    public static void register() {
        for (NuclearStructureChannels value : values()) {
            try {
                StructureLibAPI
                    .registerChannelDescription(value.get(), "modularnuclear", "channels.modularnuclear." + value.get());
            } catch (Throwable ignored) {}
        }
    }
}

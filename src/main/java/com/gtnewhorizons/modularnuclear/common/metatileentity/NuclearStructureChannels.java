package com.gtnewhorizons.modularnuclear.common.metatileentity;

import net.minecraft.item.ItemStack;

import com.gtnewhorizon.structurelib.StructureLibAPI;

import gregtech.api.structure.IStructureChannels;

public enum NuclearStructureChannels implements IStructureChannels {

    NUCLEAR_HATCH("nuclear_hatch", "Nuclear Hatch Tier");

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
        StructureLibAPI.registerChannelItem(get(), "modularnuclear", channelValue, indicator);
    }

    public static void register() {
        for (NuclearStructureChannels value : values()) {
            StructureLibAPI
                .registerChannelDescription(value.get(), "modularnuclear", "channels.modularnuclear." + value.get());
        }
    }
}

package com.gtnewhorizons.modularnuclear.common.metatileentity;

import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizon.structurelib.structure.IStructureChannels;

public enum NuclearStructureChannels implements IStructureChannels {

    NUCLEAR_HATCH("nuclear_hatch", "Nuclear Hatch Tier");

    private final String channel;
    private final String defaultTooltip;

    NuclearStructureChannels(String aChannel, String defaultTooltip) {
        this.channel = aChannel;
        this.defaultTooltip = defaultTooltip;
    }

    @Override
    public String getChannel() {
        return channel;
    }

    @Override
    public String getDefaultTooltip() {
        return defaultTooltip;
    }

    public static void register() {
        for (NuclearStructureChannels value : values()) {
            StructureLibAPI.registerChannel(value.getChannel(), value.getDefaultTooltip());
        }
    }
}

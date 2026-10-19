package com.gtnewhorizons.modularnuclear.common.textures;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;

public class ModularNuclearTextures {

    public static IIconContainer OVERLAY_FRONT_FISSION_REACTOR;
    public static IIconContainer OVERLAY_FRONT_FISSION_REACTOR_ACTIVE;
    public static IIconContainer OVERLAY_FRONT_FISSION_REACTOR_MOLTEN;
    public static IIconContainer MACHINE_CASING_NUCLEAR;
    public static IIconContainer OVERLAY_HATCH_NUCLEAR_CONTROL;
    public static IIconContainer OVERLAY_HATCH_NUCLEAR_CONTROL_GLOW;

    public static void init() {
        if (OVERLAY_FRONT_FISSION_REACTOR != null) return;
        OVERLAY_FRONT_FISSION_REACTOR = Textures.BlockIcons.custom("modularnuclear", "OVERLAY_FRONT_FISSION_REACTOR");
        OVERLAY_FRONT_FISSION_REACTOR_ACTIVE = Textures.BlockIcons
            .custom("modularnuclear", "OVERLAY_FRONT_FISSION_REACTOR_ACTIVE");
        OVERLAY_FRONT_FISSION_REACTOR_MOLTEN = Textures.BlockIcons
            .custom("modularnuclear", "OVERLAY_FRONT_FISSION_REACTOR_MOLTEN");
        MACHINE_CASING_NUCLEAR = Textures.BlockIcons.custom("modularnuclear", "MACHINE_CASING_NUCLEAR");
        OVERLAY_HATCH_NUCLEAR_CONTROL = Textures.BlockIcons
            .custom("modularnuclear", "OVERLAY_HATCH_NUCLEAR_CONTROL");
        OVERLAY_HATCH_NUCLEAR_CONTROL_GLOW = Textures.BlockIcons
            .custom("modularnuclear", "OVERLAY_HATCH_NUCLEAR_CONTROL_GLOW");
    }
}

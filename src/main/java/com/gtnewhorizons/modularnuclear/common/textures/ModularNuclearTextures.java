package com.gtnewhorizons.modularnuclear.common.textures;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;

public class ModularNuclearTextures {

    public static IIconContainer OVERLAY_FRONT_FISSION_REACTOR;
    public static IIconContainer OVERLAY_FRONT_FISSION_REACTOR_ACTIVE;
    public static IIconContainer MACHINE_CASING_NUCLEAR;

    public static void init() {
        OVERLAY_FRONT_FISSION_REACTOR = Textures.BlockIcons.custom("modularnuclear", "OVERLAY_FRONT_FISSION_REACTOR");
        OVERLAY_FRONT_FISSION_REACTOR_ACTIVE = Textures.BlockIcons
            .custom("modularnuclear", "OVERLAY_FRONT_FISSION_REACTOR_ACTIVE");
        MACHINE_CASING_NUCLEAR = Textures.BlockIcons.custom("modularnuclear", "MACHINE_CASING_NUCLEAR");
    }
}

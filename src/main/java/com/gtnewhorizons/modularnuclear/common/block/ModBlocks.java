package com.gtnewhorizons.modularnuclear.common.block;

import cpw.mods.fml.common.registry.GameRegistry;

public class ModBlocks {

    public static BlockNuclearCasing nuclearCasing;

    public static void init() {
        nuclearCasing = new BlockNuclearCasing();
        GameRegistry.registerBlock(nuclearCasing, "modularnuclear.casing");
    }
}

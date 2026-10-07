package com.gtnewhorizons.modularnuclear.common.block;

import com.gtnewhorizons.modularnuclear.common.fluid.ModFluids;

import cpw.mods.fml.common.registry.GameRegistry;

public class ModBlocks {

    public static BlockNuclearCasing nuclearCasing;
    public static BlockCorium blockCorium;

    public static void init() {
        nuclearCasing = new BlockNuclearCasing();
        blockCorium = new BlockCorium(ModFluids.fluidCorium);
        GameRegistry.registerBlock(blockCorium, "corium");
        ModFluids.fluidCorium.setBlock(blockCorium);
    }
}

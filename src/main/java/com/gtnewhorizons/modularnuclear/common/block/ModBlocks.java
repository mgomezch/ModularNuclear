package com.gtnewhorizons.modularnuclear.common.block;

import com.gtnewhorizons.modularnuclear.common.fluid.ModFluids;

import cpw.mods.fml.common.registry.GameRegistry;

public class ModBlocks {

    public static BlockNuclearCasing nuclearCasing;
    public static BlockCorium blockCorium;
    public static BlockCoriumSolid blockCoriumSolid;

    private static boolean initialized = false;

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        nuclearCasing = new BlockNuclearCasing();
        blockCorium = new BlockCorium(ModFluids.fluidCorium);
        GameRegistry.registerBlock(blockCorium, "corium");
        ModFluids.fluidCorium.setBlock(blockCorium);

        blockCoriumSolid = new BlockCoriumSolid();
        GameRegistry.registerBlock(blockCoriumSolid, ItemBlockCoriumSolid.class, "corium_solid");
    }
}

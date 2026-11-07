package com.gtnewhorizons.modularnuclear.common.block;

import com.gtnewhorizons.modularnuclear.common.fluid.ModFluids;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;

public class ModBlocks {

    public static BlockNuclearCasing nuclearCasing;
    public static BlockCorium blockCorium;
    public static BlockCoriumSolid blockCoriumSolid;
    public static BlockControlRodHousing controlRodHousing;

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

        controlRodHousing = new BlockControlRodHousing();
        GameRegistry.registerBlock(controlRodHousing, "control_rod_housing");
        GregTechAPI.registerMachineBlock(controlRodHousing, -1);
    }
}

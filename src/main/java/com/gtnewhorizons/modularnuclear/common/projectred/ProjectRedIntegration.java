package com.gtnewhorizons.modularnuclear.common.projectred;

import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.modularnuclear.ModularNuclear;

import cpw.mods.fml.common.Loader;

public class ProjectRedIntegration {

    public static boolean isLoaded = false;

    public static void init() {
        if (!Loader.isModLoaded("ProjRed|Transmission")) {
            return;
        }
        try {
            ProjectRedTransmissionHandler.register();
            isLoaded = true;
            ModularNuclear.LOG.info("Successfully initialized ProjectRed Transmission integration.");
        } catch (Throwable t) {
            ModularNuclear.LOG.warn("Failed to initialize ProjectRed integration", t);
        }
    }

    public static boolean isInterfacing(World world, int x, int y, int z, ForgeDirection facing) {
        if (!isLoaded || world == null) return false;
        try {
            return ProjectRedTransmissionHandler.isInterfacing(world, x, y, z, facing);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static int getBundledInput(World world, int x, int y, int z, int channel) {
        if (!isLoaded || world == null) return -1;
        try {
            return ProjectRedTransmissionHandler.getBundledInputAllSides(world, x, y, z, channel);
        } catch (Throwable ignored) {
            return -1;
        }
    }
}

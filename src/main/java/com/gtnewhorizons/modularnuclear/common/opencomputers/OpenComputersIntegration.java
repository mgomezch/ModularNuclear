package com.gtnewhorizons.modularnuclear.common.opencomputers;

import com.gtnewhorizons.modularnuclear.ModularNuclear;

import cpw.mods.fml.common.Loader;
import li.cil.oc.api.Driver;

public class OpenComputersIntegration {

    public static boolean isLoaded = false;

    public static void init() {
        if (!Loader.isModLoaded("OpenComputers")) {
            return;
        }
        try {
            Driver.add(new DriverNuclearControlHatch());
            isLoaded = true;
        } catch (Throwable t) {
            ModularNuclear.LOG.error("Could not register the OpenComputers driver for the nuclear control hatch", t);
        }
    }
}

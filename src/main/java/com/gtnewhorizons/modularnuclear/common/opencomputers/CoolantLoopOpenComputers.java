package com.gtnewhorizons.modularnuclear.common.opencomputers;

import com.gtnewhorizons.modularnuclear.ModularNuclear;

import cpw.mods.fml.common.Loader;
import li.cil.oc.api.Driver;

public class CoolantLoopOpenComputers {

    public static void register() {
        if (Loader.isModLoaded("OpenComputers")) {
            try {
                Driver.add(new DriverLoopInstrument());
                Driver.add(new DriverPressurizedFluidHeater());
            } catch (Throwable t) {
                ModularNuclear.LOG.error("Could not register the OpenComputers drivers for coolant loop devices", t);
            }
        }
    }
}

package com.gtnewhorizons.modularnuclear.common.opencomputers;

import cpw.mods.fml.common.Loader;
import li.cil.oc.api.Driver;

public class CoolantLoopOpenComputers {

    public static void register() {
        if (Loader.isModLoaded("OpenComputers")) {
            try {
                Driver.add(new DriverLoopInstrument());
                Driver.add(new DriverPressurizedFluidHeater());
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }
}

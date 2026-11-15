package com.gtnewhorizons.modularnuclear.common.gui;

import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.common.gui.modularui.hatch.base.MTEHatchBaseGui;

/** GregTech's hatch GUI without the GregTech logo, which our GUIs don't show. */
public class ModularNuclearHatchGui<T extends MTEHatch> extends MTEHatchBaseGui<T> {

    public ModularNuclearHatchGui(T machine) {
        super(machine);
    }

    @Override
    protected boolean doesAddGregTechLogo() {
        return false;
    }
}

package com.gtnewhorizons.modularnuclear.common.util;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/**
 * GregTech multiblocks first check their structure 100 ticks after loading, and stay unformed until then. Reactors,
 * coolant pumps and loop devices cannot sit idle that long after a restart (a reactor run close to its limits would
 * overheat while its cooling waits), so they check theirs on their first tick instead.
 */
public final class MultiblockStartup {

    private MultiblockStartup() {}

    /**
     * Checks the structure now, on the server, if it is not formed yet. When it forms, GregTech's own delayed startup
     * check is skipped; when it does not (part of it may be in a chunk that is not loaded), that check still runs.
     * Returns whether the machine is formed.
     */
    public static boolean formNow(MTEMultiBlockBase machine) {
        IGregTechTileEntity base = machine.getBaseMetaTileEntity();
        if (base == null || !base.isServerSide() || machine.mMachine) return machine.mMachine;
        if (machine.checkStructure(true, base)) machine.mStartUpCheck = 0;
        return machine.mMachine;
    }
}

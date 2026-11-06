package com.gtnewhorizons.modularnuclear.common.util;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * Per-machine offsets for periodic work. A machine's tick counter starts when its tile loads, so machines loaded
 * together (every machine at server start) count in step, and "every N ticks" would run them all on the same tick.
 * Offsetting each machine's schedule by a phase taken from its position spreads them over the interval, and keeps each
 * machine's own schedule the same across loads.
 */
public final class TickPhase {

    private TickPhase() {}

    /** A phase for the machine at this position, in [0, 2^20). */
    public static int of(int x, int y, int z, int dimension) {
        long h = x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L ^ dimension * 0x27D4EB2FL;
        h ^= h >>> 31;
        h *= 0x7FB5D329728EA185L;
        h ^= h >>> 27;
        return (int) (h & 0xFFFFF);
    }

    public static int of(IGregTechTileEntity base) {
        if (base == null) return 0;
        int dim = base.getWorld() != null && base.getWorld().provider != null ? base.getWorld().provider.dimensionId
            : 0;
        return of(base.getXCoord(), base.getYCoord(), base.getZCoord(), dim);
    }

    /** Whether work done every {@code interval} ticks, offset by {@code phase}, is due on {@code tick}. */
    public static boolean due(long tick, int phase, int interval) {
        return (tick + phase) % interval == 0;
    }
}

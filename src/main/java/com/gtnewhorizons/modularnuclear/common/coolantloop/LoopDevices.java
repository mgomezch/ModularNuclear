package com.gtnewhorizons.modularnuclear.common.coolantloop;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchPressurizedFluid;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTECoolantBath;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTEPressurizedDegasser;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTEPressurizedFluidHeater;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTEPressurizedHeatExchanger;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;

/**
 * Which loop device owns a block: the one lookup the crawler and the pump use. A high-pressure hatch belongs to the
 * multiblock that claimed it ({@link ILoopHatchOwner}); a hatch no device claims is a device of its own (a core
 * passage end).
 * <p>
 * A device's claims are not saved, so until its first structure check after a load a hatch has no known owner. The
 * lookup then searches the hatch's surroundings for the multiblock types registered here, each within its reach,
 * checking an unformed one's structure.
 */
public final class LoopDevices {

    /** A multiblock type whose controller can be up to (dx, dy, dz) blocks from one of its loop hatches. */
    private record OwnerType(Class<? extends ILoopHatchOwner> type, int dx, int dy, int dz) {}

    private static final List<OwnerType> OWNER_TYPES = new ArrayList<>();

    static {
        register(MTEPressurizedFluidHeater.class, 11, 2, 11);
        register(MTEPressurizedHeatExchanger.class, 11, 8, 11);
        register(MTEPressurizedDegasser.class, 2, 0, 2);
        register(MTECoolantBath.class, 5, 2, 12);
    }

    private LoopDevices() {}

    /** Registers a multiblock loop device type and how far its controller can be from its loop hatches. */
    public static void register(Class<? extends ILoopHatchOwner> type, int dx, int dy, int dz) {
        OWNER_TYPES.add(new OwnerType(type, dx, dy, dz));
    }

    /** The loop device that owns the block at (x, y, z), or null if the block is no part of a loop device. */
    public static ICoolantLoopDevice at(World world, int x, int y, int z) {
        if (world == null) return null;
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof ICoolantLoopDevice device) return device;
        IMetaTileEntity mte = te instanceof IGregTechTileEntity gte ? gte.getMetaTileEntity() : null;
        if (mte instanceof MTEHatchPressurizedFluid hatch) {
            ILoopHatchOwner owner = hatch.getLoopOwner();
            if (owner == null || !isLive(owner) || !owner.ownsHatch(hatch)) {
                owner = searchOwner(world, x, y, z, hatch);
                hatch.setLoopOwner(owner);
            }
            return owner != null ? owner : hatch;
        }
        return mte instanceof ICoolantLoopDevice device ? device : null;
    }

    private static boolean isLive(ILoopHatchOwner owner) {
        if (!(owner instanceof IMetaTileEntity mte)) return true;
        IGregTechTileEntity base = mte.getBaseMetaTileEntity();
        return base != null && !base.isInvalidTileEntity();
    }

    private static ILoopHatchOwner searchOwner(World world, int x, int y, int z, MTEHatchPressurizedFluid hatch) {
        for (OwnerType t : OWNER_TYPES) {
            for (int dy = -t.dy; dy <= t.dy; dy++) {
                for (int dx = -t.dx; dx <= t.dx; dx++) {
                    for (int dz = -t.dz; dz <= t.dz; dz++) {
                        TileEntity te = world.getTileEntity(x + dx, y + dy, z + dz);
                        if (!(te instanceof IGregTechTileEntity gte)) continue;
                        IMetaTileEntity mte = gte.getMetaTileEntity();
                        if (!t.type.isInstance(mte)) continue;
                        ILoopHatchOwner owner = t.type.cast(mte);
                        if (!owner.ownsHatch(hatch) && mte instanceof MTEMultiBlockBase mb && !mb.mMachine) {
                            mb.checkMachine(gte, null);
                        }
                        if (owner.ownsHatch(hatch)) return owner;
                    }
                }
            }
        }
        return null;
    }
}

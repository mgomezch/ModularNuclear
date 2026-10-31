package com.gtnewhorizons.modularnuclear.common.util;

import java.util.Set;

import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;

import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.IStructureWalker;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;

/** The chunks a multiblock's structure occupies, as {@link ChunkCoordIntPair#chunkXZ2Int} keys. */
public final class StructureChunks {

    private StructureChunks() {}

    /** Adds the chunks of every block of the given structure piece, checked with the given offsets. */
    public static <T extends MTEEnhancedMultiBlockBase<T>> void collect(T machine, String piece, int horizontalOffset,
        int verticalOffset, int depthOffset, Set<Long> out) {
        IGregTechTileEntity base = machine.getBaseMetaTileEntity();
        if (base == null) return;
        World world = base.getWorld();
        addBlock(base.getXCoord(), base.getZCoord(), out);
        if (piece == null || world == null
            || machine.getStructureDefinition()
                .getStructureFor(piece) == null)
            return;
        IStructureWalker<T> walker = new IStructureWalker<>() {

            @Override
            public boolean visit(IStructureElement<T> element, World w, int x, int y, int z, int a, int b, int c) {
                addBlock(x, z, out);
                return true;
            }

            @Override
            public boolean blockNotLoaded(IStructureElement<T> element, World w, int x, int y, int z, int a, int b,
                int c) {
                addBlock(x, z, out);
                return true;
            }
        };
        machine.getStructureDefinition()
            .iterate(
                piece,
                world,
                machine.getExtendedFacing(),
                base.getXCoord(),
                base.getYCoord(),
                base.getZCoord(),
                horizontalOffset,
                verticalOffset,
                depthOffset,
                walker);
    }

    public static void addBlock(int x, int z, Set<Long> out) {
        out.add(ChunkCoordIntPair.chunkXZ2Int(x >> 4, z >> 4));
    }
}

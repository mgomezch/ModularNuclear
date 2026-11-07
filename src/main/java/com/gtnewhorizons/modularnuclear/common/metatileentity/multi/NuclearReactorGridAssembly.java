package com.gtnewhorizons.modularnuclear.common.metatileentity.multi;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.alignment.enumerable.Rotation;
import com.gtnewhorizons.modularnuclear.common.coolantloop.ICoolantPassageHatch;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearBus;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControl;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHatch;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHighPressure;
import com.gtnewhorizons.modularnuclear.common.nuclear.INuclearTile;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gtnhlanth.common.hatch.MTEHatchInputBeamline;

/**
 * What the reactor structure check does once the structure matched: builds the core grid from the matched hatches,
 * attaches the bottom-layer control rods (counting the housings each extends into) and beam inputs to their cells, and
 * gives every hatch the reactor's casing texture and crafting icon.
 */
final class NuclearReactorGridAssembly {

    private NuclearReactorGridAssembly() {}

    static void assemble(MTENuclearReactor reactor, IGregTechTileEntity base) {
        int size = reactor.gridSize;
        INuclearTile[][] grid = new INuclearTile[size][size];
        reactor.mGrid = grid;
        int cX = base.getXCoord();
        int cY = base.getYCoord();
        int cZ = base.getZCoord();
        ExtendedFacing ef = reactor.getExtendedFacing();
        boolean isUpsideDown = ef.getRotation() == Rotation.UPSIDE_DOWN;
        int hOffset = size / 2;

        for (IGregTechTileEntity te : reactor.mNuclearTiles) {
            int[] g = reactor.calculateGridCoords(te, cX, cY, cZ, ef, isUpsideDown, hOffset);
            if (!inGrid(g, size)) continue;
            IMetaTileEntity mte = te.getMetaTileEntity();
            if (mte instanceof MTEHatchNuclearBus bus) {
                grid[g[0]][g[1]] = new NuclearGridTile(reactor, bus, g[0], g[1]);
            } else if (mte instanceof MTEHatchNuclearHatch hatch) {
                grid[g[0]][g[1]] = new NuclearGridTile(reactor, hatch, g[0], g[1]);
            } else if (mte instanceof MTEHatchNuclearHighPressure hp) {
                grid[g[0]][g[1]] = new NuclearGridTile(reactor, hp, g[0], g[1]);
            }
        }

        for (MTEHatchNuclearControlRod rod : reactor.mBottomControlRodHatches) {
            int[] g = reactor.calculateGridCoords(rod.getBaseMetaTileEntity(), cX, cY, cZ, ef, isUpsideDown, hOffset);
            if (inGrid(g, size) && grid[g[0]][g[1]] instanceof NuclearGridTile gt) {
                gt.setBottomControlRod(rod);
                rod.mReactor = reactor;
                if (reactor.mScram) rod.setScram(true);
                rod.updateHousingCount();
            }
        }

        for (MTEHatchInputBeamline beamHatch : reactor.mBottomBeamInputHatches) {
            int[] g = reactor
                .calculateGridCoords(beamHatch.getBaseMetaTileEntity(), cX, cY, cZ, ef, isUpsideDown, hOffset);
            if (inGrid(g, size) && grid[g[0]][g[1]] instanceof NuclearGridTile gt) {
                gt.setBottomBeamInput(beamHatch);
            }
        }
    }

    private static boolean inGrid(int[] g, int size) {
        return g != null && g[0] >= 0 && g[0] < size && g[1] >= 0 && g[1] < size;
    }

    /** Gives every hatch of the formed structure the reactor's casing texture and crafting icon. */
    static void texture(MTENuclearReactor reactor) {
        for (IGregTechTileEntity te : reactor.mNuclearTiles) {
            if (te != null && te.getMetaTileEntity() instanceof MTEHatchNuclearHatch hatch) {
                hatch.mWasDry = false;
            }
            if (te != null && te.getMetaTileEntity() instanceof MTEHatch hatch) {
                if (hatch.getBaseMetaTileEntity() == null) {
                    hatch.setBaseMetaTileEntity(te);
                }
                texture(reactor, hatch);
            }
        }
        for (MTEHatchNuclearControlRod rod : reactor.mBottomControlRodHatches) texture(reactor, rod);
        for (ICoolantPassageHatch hp : reactor.mTopHighPressureHatches) {
            if (hp instanceof MTEHatch hatch) texture(reactor, hatch);
        }
        for (ICoolantPassageHatch hp : reactor.mBottomHighPressureHatches) {
            if (hp instanceof MTEHatch hatch) texture(reactor, hatch);
        }
        for (MTEHatchInputBeamline beam : reactor.mBottomBeamInputHatches) texture(reactor, beam);
        for (MTEHatchNuclearControl control : reactor.mControlHatches) texture(reactor, control);
    }

    private static void texture(MTENuclearReactor reactor, MTEHatch hatch) {
        if (hatch == null) return;
        if (hatch.getBaseMetaTileEntity() != null) {
            hatch.updateTexture(MTENuclearReactor.CASING_INDEX);
        }
        if (reactor.getBaseMetaTileEntity() != null) {
            hatch.updateCraftingIcon(reactor.getMachineCraftingIcon());
        }
    }
}

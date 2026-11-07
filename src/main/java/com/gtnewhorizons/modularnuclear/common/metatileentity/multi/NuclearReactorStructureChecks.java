package com.gtnewhorizons.modularnuclear.common.metatileentity.multi;

import java.util.List;

import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.alignment.enumerable.Rotation;
import com.gtnewhorizons.modularnuclear.common.coolantloop.ICoolantPassageHatch;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearBus;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControl;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHighPressure;
import com.gtnewhorizons.modularnuclear.common.nuclear.ReactorStructureRules;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gtnhlanth.common.hatch.MTEHatchInputBeamline;

/**
 * The reactor structure check's rules for its hatches: facings, and the pairing of high-pressure and beam hatches with
 * the core.
 */
final class NuclearReactorStructureChecks {

    private NuclearReactorStructureChecks() {}

    static void validateHighPressureHatchPairing(MTENuclearReactor reactor, IGregTechTileEntity aBaseMetaTileEntity,
        List<StructureError> errors) {
        if (!reactor.mTopHighPressureHatches.isEmpty() || !reactor.mBottomHighPressureHatches.isEmpty()) {
            ReactorStructureRules.Violation tier = ReactorStructureRules.passages(reactor.mPipeTier);
            if (tier != null) errors.add(StructureErrors.of(tier.langKey));
            if (reactor.mTopHighPressureHatches.size() != reactor.mBottomHighPressureHatches.size()) {
                errors.add(StructureErrors.of("GT5U.gui.text.structure_error.hp_hatch_unpaired"));
            } else if (reactor.gridSize > 0) {
                int cX = aBaseMetaTileEntity != null ? aBaseMetaTileEntity.getXCoord() : 0;
                int cY = aBaseMetaTileEntity != null ? aBaseMetaTileEntity.getYCoord() : 0;
                int cZ = aBaseMetaTileEntity != null ? aBaseMetaTileEntity.getZCoord() : 0;
                ExtendedFacing ef = reactor.getExtendedFacing();
                boolean isUpsideDown = ef.getRotation() == Rotation.UPSIDE_DOWN;
                int hOffset = reactor.gridSize / 2;

                ICoolantPassageHatch[][] bottomGrid = new ICoolantPassageHatch[reactor.gridSize][reactor.gridSize];
                for (ICoolantPassageHatch bottom : reactor.mBottomHighPressureHatches) {
                    IGregTechTileEntity bTe = bottom instanceof IMetaTileEntity mte ? mte.getBaseMetaTileEntity()
                        : null;
                    if (bTe != null) {
                        int[] gCoords = reactor.calculateGridCoords(bTe, cX, cY, cZ, ef, isUpsideDown, hOffset);
                        if (gCoords != null && gCoords[0] >= 0
                            && gCoords[0] < reactor.gridSize
                            && gCoords[1] >= 0
                            && gCoords[1] < reactor.gridSize) {
                            bottomGrid[gCoords[0]][gCoords[1]] = bottom;
                        }
                    }
                }

                boolean anyUnpaired = false;
                boolean anyMismatched = false;
                for (ICoolantPassageHatch top : reactor.mTopHighPressureHatches) {
                    IGregTechTileEntity tTe = top instanceof IMetaTileEntity mte ? mte.getBaseMetaTileEntity() : null;
                    if (tTe != null) {
                        int[] gCoords = reactor.calculateGridCoords(tTe, cX, cY, cZ, ef, isUpsideDown, hOffset);
                        if (gCoords != null && gCoords[0] >= 0
                            && gCoords[0] < reactor.gridSize
                            && gCoords[1] >= 0
                            && gCoords[1] < reactor.gridSize) {
                            ICoolantPassageHatch bottom = bottomGrid[gCoords[0]][gCoords[1]];
                            if (bottom != null) {
                                if (top instanceof MTEHatchNuclearHighPressure in
                                    && bottom instanceof MTEHatchNuclearHighPressure out
                                    && in.getMaterialIndex() != out.getMaterialIndex()) {
                                    anyMismatched = true;
                                }
                                IGregTechTileEntity bTe = bottom instanceof IMetaTileEntity mte
                                    ? mte.getBaseMetaTileEntity()
                                    : null;
                                top.setOppositeHatch(bTe);
                                if (top instanceof MTEHatchNuclearHighPressure nhp) {
                                    nhp.setIsInlet(true);
                                }
                                bottom.setOppositeHatch(tTe);
                                if (bottom instanceof MTEHatchNuclearHighPressure nhp) {
                                    nhp.setIsInlet(false);
                                }
                                bottomGrid[gCoords[0]][gCoords[1]] = null;
                            } else {
                                anyUnpaired = true;
                            }
                        } else {
                            anyUnpaired = true;
                        }
                    } else {
                        anyUnpaired = true;
                    }
                }

                if (anyUnpaired) {
                    errors.add(StructureErrors.of("GT5U.gui.text.structure_error.hp_hatch_unpaired"));
                }
                if (anyMismatched) {
                    errors.add(StructureErrors.of("GT5U.gui.text.structure_error.hp_hatch_material"));
                }
            }
        }
    }

    static void validateBeamInputHatchPairing(MTENuclearReactor reactor, IGregTechTileEntity aBaseMetaTileEntity,
        List<StructureError> errors) {
        if (!reactor.mBottomBeamInputHatches.isEmpty() && reactor.gridSize > 0) {
            int cX = aBaseMetaTileEntity != null ? aBaseMetaTileEntity.getXCoord() : 0;
            int cY = aBaseMetaTileEntity != null ? aBaseMetaTileEntity.getYCoord() : 0;
            int cZ = aBaseMetaTileEntity != null ? aBaseMetaTileEntity.getZCoord() : 0;
            ExtendedFacing ef = reactor.getExtendedFacing();
            boolean isUpsideDown = ef.getRotation() == Rotation.UPSIDE_DOWN;
            int hOffset = reactor.gridSize / 2;

            boolean[][] busGrid = new boolean[reactor.gridSize][reactor.gridSize];
            for (IGregTechTileEntity te : reactor.mNuclearTiles) {
                if (te != null && te.getMetaTileEntity() instanceof MTEHatchNuclearBus) {
                    int[] gCoords = reactor.calculateGridCoords(te, cX, cY, cZ, ef, isUpsideDown, hOffset);
                    if (gCoords != null && gCoords[0] >= 0
                        && gCoords[0] < reactor.gridSize
                        && gCoords[1] >= 0
                        && gCoords[1] < reactor.gridSize) {
                        busGrid[gCoords[0]][gCoords[1]] = true;
                    }
                }
            }

            boolean anyUnpaired = false;
            for (MTEHatchInputBeamline bottom : reactor.mBottomBeamInputHatches) {
                IGregTechTileEntity bTe = bottom.getBaseMetaTileEntity();
                if (bTe != null) {
                    int[] gCoords = reactor.calculateGridCoords(bTe, cX, cY, cZ, ef, isUpsideDown, hOffset);
                    if (gCoords != null && gCoords[0] >= 0
                        && gCoords[0] < reactor.gridSize
                        && gCoords[1] >= 0
                        && gCoords[1] < reactor.gridSize) {
                        if (!busGrid[gCoords[0]][gCoords[1]]) {
                            anyUnpaired = true;
                            break;
                        }
                    } else {
                        anyUnpaired = true;
                        break;
                    }
                } else {
                    anyUnpaired = true;
                    break;
                }
            }

            if (anyUnpaired) {
                errors.add(StructureErrors.of("GT5U.gui.text.structure_error.beam_hatch_unpaired"));
            }
        }
    }

    static void verifyNuclearHatchFacings(MTENuclearReactor reactor, List<StructureError> errors) {
        reactor.checkNuclearHatchFacings(errors);
    }

    static void checkNuclearControlHatchFacings(MTENuclearReactor reactor, IGregTechTileEntity aBaseMetaTileEntity,
        List<StructureError> errors) {
        if (reactor.mControlHatches.isEmpty() || reactor.gridSize <= 0) return;
        ExtendedFacing ef = reactor.getExtendedFacing();
        if (ef == null) return;
        int cX = aBaseMetaTileEntity != null ? aBaseMetaTileEntity.getXCoord() : 0;
        int cY = aBaseMetaTileEntity != null ? aBaseMetaTileEntity.getYCoord() : 0;
        int cZ = aBaseMetaTileEntity != null ? aBaseMetaTileEntity.getZCoord() : 0;

        ForgeDirection controllerUp = ef.getRelativeUpInWorld();

        for (MTEHatchNuclearControl hatch : reactor.mControlHatches) {
            if (hatch == null) continue;
            IGregTechTileEntity bTe = hatch.getBaseMetaTileEntity();
            if (bTe == null) continue;

            // 1. Must face outward away from the center of the structure
            if (!reactor.isOuterWallFacingValid(bTe, cX, cY, cZ, ef, reactor.gridSize)) {
                errors.add(StructureErrors.of("GT5U.gui.text.structure_error.wrong_control_hatch_facing"));
                return;
            }

            // 2. Must be upright relative to the controller on lateral faces
            ExtendedFacing hatchEF = hatch.getExtendedFacing();
            if (hatchEF == null || hatchEF.getRelativeUpInWorld() != controllerUp) {
                errors.add(StructureErrors.of("GT5U.gui.text.structure_error.wrong_control_hatch_facing"));
                return;
            }
        }
    }
}

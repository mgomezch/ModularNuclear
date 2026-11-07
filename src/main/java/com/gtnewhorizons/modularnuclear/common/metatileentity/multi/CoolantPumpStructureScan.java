package com.gtnewhorizons.modularnuclear.common.metatileentity.multi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantPipingRegistry;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchPressurizedFluid;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Materials;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchMaintenance;
import gregtech.api.structure.error.PositionedStructureError;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.common.blocks.BlockFrameBox;

/**
 * The coolant pump structure check's block scan: the tank-material frame boxes at the corners of both layers, then the
 * footprint's casings and hatches, then which pressurized hatch is the discharge and which the suction.
 */
final class CoolantPumpStructureScan {

    private static final int CASING_INDEX = 16 + 15;

    private CoolantPumpStructureScan() {}

    /**
     * Scans the pump's footprint, collecting its hatches into the pump. Returns the pressurized hatches found, or null
     * when a block is wrong (the error is added and the pump's status set).
     */
    static List<MTEHatchPressurizedFluid> scan(MTECoolantPump pump, IGregTechTileEntity base, World world,
        List<StructureError> aErrors) {
        int x = base.getXCoord();
        int y = base.getYCoord();
        int z = base.getZCoord();
        ForgeDirection front = base.getFrontFacing();
        ForgeDirection back = front != null && front != ForgeDirection.UNKNOWN ? front.getOpposite()
            : ForgeDirection.SOUTH;
        ForgeDirection right = front != null && front != ForgeDirection.UNKNOWN ? front.getRotation(ForgeDirection.UP)
            : ForgeDirection.WEST;
        int R = pump.mWidth / 2;

        // 1. Verify four corners on both layer 0 and layer 1 are Frame Boxes of the tank material
        int[][] cornerOffsets = { { -R, 0 }, { R, 0 }, { -R, pump.mWidth - 1 }, { R, pump.mWidth - 1 } };
        for (int[] corner : cornerOffsets) {
            int cx = x + right.offsetX * corner[0] + back.offsetX * corner[1];
            int cz = z + right.offsetZ * corner[0] + back.offsetZ * corner[1];
            for (int ly = 0; ly < 2; ly++) {
                int cy = y + ly;
                Block block = world.getBlock(cx, cy, cz);
                String matName = pump.mTankMaterial != null ? pump.mTankMaterial.mDefaultLocalName : "Matching";
                if (!(block instanceof BlockFrameBox)) {
                    pump.setLoopStatusKey("coolantloops.pump.status.corner_frame", cx, cy, cz);
                    aErrors.add(
                        new PositionedStructureError(
                            cx,
                            cy,
                            cz,
                            Collections.singletonList("coolantloops.structure.error.pump.corner_frame_tank")));
                    return null;
                }
                int meta = world.getBlockMetadata(cx, cy, cz);
                Materials frameMat = BlockFrameBox.getMaterial(meta);
                if (!CoolantPipingRegistry.areMaterialsEqual(frameMat, pump.mTankMaterial)) {
                    pump.setLoopStatusKey(
                        "coolantloops.pump.status.corner_frame_mat",
                        cx,
                        cy,
                        cz,
                        frameMat != null ? frameMat.mDefaultLocalName : "unknown",
                        matName);
                    aErrors.add(
                        new PositionedStructureError(
                            cx,
                            cy,
                            cz,
                            Collections.singletonList("coolantloops.structure.error.pump.corner_frame_tank")));
                    return null;
                }
            }
        }

        // 2. Scan footprint on both layers for hatches and casings
        List<MTEHatchPressurizedFluid> pressHatches = new ArrayList<>();
        for (int localX = -R; localX <= R; localX++) {
            for (int localZ = 0; localZ < pump.mWidth; localZ++) {
                // Skip corners (already verified)
                if ((localX == -R || localX == R) && (localZ == 0 || localZ == pump.mWidth - 1)) {
                    continue;
                }
                int bx = x + right.offsetX * localX + back.offsetX * localZ;
                int bz = z + right.offsetZ * localX + back.offsetZ * localZ;
                for (int ly = 0; ly < 2; ly++) {
                    // Skip controller position
                    if (localX == 0 && localZ == 0 && ly == 0) {
                        continue;
                    }
                    int by = y + ly;
                    TileEntity te = world.getTileEntity(bx, by, bz);
                    if (te instanceof IGregTechTileEntity) {
                        IGregTechTileEntity gte = (IGregTechTileEntity) te;
                        IMetaTileEntity mte = gte.getMetaTileEntity();
                        if (mte instanceof MTEHatchPressurizedFluid) {
                            MTEHatchPressurizedFluid ph = (MTEHatchPressurizedFluid) mte;
                            if (!pressHatches.contains(ph)) {
                                pressHatches.add(ph);
                            }
                        } else if (mte instanceof MTEHatchMaintenance) {
                            if (!pump.mMaintenanceHatches.contains(mte)) {
                                pump.mMaintenanceHatches.add((MTEHatchMaintenance) mte);
                            }
                        } else if (mte instanceof MTEHatchEnergy) {
                            if (!pump.mEnergyHatches.contains(mte)) {
                                pump.mEnergyHatches.add((MTEHatchEnergy) mte);
                            }
                        } else if (mte instanceof MTEHatchInputBus) {
                            if (!pump.mInputBusses.contains(mte)) {
                                pump.mInputBusses.add((MTEHatchInputBus) mte);
                            }
                        } else {
                            pump.setLoopStatusKey("coolantloops.pump.status.invalid_hatch", bx, by, bz);
                            aErrors.add(
                                new PositionedStructureError(
                                    bx,
                                    by,
                                    bz,
                                    Collections.singletonList("coolantloops.structure.error.pump.invalid_hatch")));
                            return null;
                        }
                    } else if (GregTechAPI.sBlockCasings2 != null) {
                        Block block = world.getBlock(bx, by, bz);
                        int meta = world.getBlockMetadata(bx, by, bz);
                        if (block != GregTechAPI.sBlockCasings2 || meta != 15) {
                            pump.setLoopStatusKey("coolantloops.pump.status.casing_req", bx, by, bz);
                            aErrors.add(
                                new PositionedStructureError(
                                    bx,
                                    by,
                                    bz,
                                    Collections.singletonList("coolantloops.structure.error.pump.casing_req")));
                            return null;
                        }
                    }
                }
            }
        }

        for (MTEHatchPressurizedFluid ph : pressHatches) {
            if (ph.isOutput()) {
                if (pump.mDischargeHatch == null) {
                    pump.mDischargeHatch = ph;
                } else {
                    pump.setLoopStatusKey("coolantloops.pump.status.req_hatches");
                    aErrors.add(StructureErrors.of("coolantloops.structure.error.duplicate_pump_hatches"));
                    return null;
                }
            } else if (ph.isInput()) {
                if (pump.mSuctionHatch == null) {
                    pump.mSuctionHatch = ph;
                } else {
                    pump.setLoopStatusKey("coolantloops.pump.status.req_hatches");
                    aErrors.add(StructureErrors.of("coolantloops.structure.error.duplicate_pump_hatches"));
                    return null;
                }
            }
        }

        return pressHatches;
    }

    /** Gives the pump's hatches its casing texture and crafting icon. */
    static void texture(MTECoolantPump pump, List<MTEHatchPressurizedFluid> pressHatches) {
        ItemStack icon = pump.getBaseMetaTileEntity() != null ? pump.getMachineCraftingIcon() : null;
        for (MTEHatch hatch : pressHatches) texture(hatch, icon);
        for (MTEHatch hatch : pump.mMaintenanceHatches) texture(hatch, icon);
        for (MTEHatch hatch : pump.mEnergyHatches) texture(hatch, icon);
        for (MTEHatch hatch : pump.mInputBusses) texture(hatch, icon);
    }

    private static void texture(MTEHatch hatch, ItemStack icon) {
        hatch.updateTexture(CASING_INDEX);
        if (icon != null) hatch.updateCraftingIcon(icon);
    }
}

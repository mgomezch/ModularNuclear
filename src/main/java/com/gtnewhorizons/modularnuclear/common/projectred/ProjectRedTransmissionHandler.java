package com.gtnewhorizons.modularnuclear.common.projectred;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.modularnuclear.ModularNuclear;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControl;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod;

import codechicken.multipart.TileMultipart;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import mrtjp.projectred.api.IBundledEmitter;
import mrtjp.projectred.api.IBundledTile;
import mrtjp.projectred.api.IBundledTileInteraction;
import mrtjp.projectred.api.ProjectRedAPI;
import mrtjp.projectred.transmission.IBundledCablePart;

public class ProjectRedTransmissionHandler implements IBundledTileInteraction {

    private static final ProjectRedTransmissionHandler INSTANCE = new ProjectRedTransmissionHandler();

    public static void register() {
        if (ProjectRedAPI.transmissionAPI != null) {
            ProjectRedAPI.transmissionAPI.registerBundledTileInteraction(INSTANCE);
            ModularNuclear.LOG.info("Registered ProjectRed bundled tile interaction for ModularNuclear hatches.");
        }
    }

    @Override
    public boolean isValidInteractionFor(World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof IGregTechTileEntity gt) {
            IMetaTileEntity mte = gt.getMetaTileEntity();
            return mte instanceof MTEHatchNuclearControl || mte instanceof MTEHatchNuclearControlRod;
        }
        return false;
    }

    @Override
    public boolean canConnectBundled(World world, int x, int y, int z, int side) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof IGregTechTileEntity gt) {
            IMetaTileEntity mte = gt.getMetaTileEntity();
            if (mte instanceof MTEHatchNuclearControl) {
                int front = gt.getFrontFacing().ordinal();
                int opp = gt.getFrontFacing().getOpposite().ordinal();
                return side == front || side == opp;
            }
            if (mte instanceof MTEHatchNuclearControlRod) {
                return true;
            }
        }
        return false;
    }

    @Override
    public byte[] getBundledSignal(World world, int x, int y, int z, int side) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof IGregTechTileEntity gt) {
            IMetaTileEntity mte = gt.getMetaTileEntity();
            if (mte instanceof MTEHatchNuclearControl hatch) {
                int front = gt.getFrontFacing().ordinal();
                int opp = gt.getFrontFacing().getOpposite().ordinal();
                if (side == front || side == opp) {
                    return hatch.getBundledSignal();
                }
            }
        }
        return null;
    }

    public static boolean isInterfacing(World world, int x, int y, int z, ForgeDirection facing) {
        int tx = x + facing.offsetX;
        int ty = y + facing.offsetY;
        int tz = z + facing.offsetZ;

        TileEntity te = world.getTileEntity(tx, ty, tz);
        if (te instanceof IBundledTile) {
            return true;
        }
        if (te instanceof TileMultipart mp) {
            for (int s = 0; s < 7; s++) {
                Object part = mp.partMap(s);
                if (part instanceof IBundledCablePart || part instanceof IBundledEmitter) {
                    return true;
                }
            }
        }
        if (ProjectRedAPI.transmissionAPI != null) {
            if (ProjectRedAPI.transmissionAPI.containsBundledCable(world, tx, ty, tz, facing.getOpposite().ordinal())) {
                return true;
            }
            if (ProjectRedAPI.transmissionAPI.containsBundledCable(world, tx, ty, tz, 6)) {
                return true;
            }
        }
        return false;
    }

    public static int getBundledInputAllSides(World world, int x, int y, int z, int channel) {
        if (ProjectRedAPI.transmissionAPI == null) return -1;
        int maxSignal = -1;
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            byte[] input = ProjectRedAPI.transmissionAPI.getBundledInput(world, x, y, z, dir.ordinal());
            if (input != null && input.length >= 16) {
                if (channel >= 0 && channel < 16) {
                    int val = input[channel] & 0xFF;
                    if (val > maxSignal) {
                        maxSignal = val;
                    }
                } else {
                    for (int i = 0; i < 16; i++) {
                        int val = input[i] & 0xFF;
                        if (val > maxSignal) {
                            maxSignal = val;
                        }
                    }
                }
            }
        }
        return maxSignal;
    }
}

package com.gtnewhorizons.modularnuclear.common.opencomputers;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControl;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.prefab.DriverSidedTileEntity;

public class DriverNuclearControlHatch extends DriverSidedTileEntity {

    @Override
    public Class<?> getTileEntityClass() {
        return IGregTechTileEntity.class;
    }

    @Override
    public boolean worksWith(World world, int x, int y, int z, ForgeDirection side) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof IGregTechTileEntity gt) {
            return gt.getMetaTileEntity() instanceof MTEHatchNuclearControl;
        }
        return false;
    }

    @Override
    public ManagedEnvironment createEnvironment(World world, int x, int y, int z, ForgeDirection side) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof IGregTechTileEntity gt && gt.getMetaTileEntity() instanceof MTEHatchNuclearControl hatch) {
            return new NuclearControlEnvironment(hatch);
        }
        return null;
    }
}

package com.gtnewhorizons.modularnuclear.common.metatileentity.multi.util;

import java.lang.reflect.Method;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidHandler;

import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantFluidProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantLoopEngine;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopGraphCrawler;
import com.gtnewhorizons.modularnuclear.common.coolantloop.pump.CoolantPumpReservoir;
import com.gtnewhorizons.modularnuclear.common.coolantloop.pump.ReservoirTank;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTECoolantPump;
import com.gtnewhorizons.modularnuclear.common.util.CoolantFluidHelper;

import gregtech.api.enums.Materials;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import mods.railcraft.common.blocks.machine.MultiBlockPattern;
import mods.railcraft.common.blocks.machine.TileMultiBlock;
import mods.railcraft.common.blocks.machine.beta.TileTankBase;
import mods.railcraft.common.fluids.tanks.StandardTank;

/** The multiblock pump's reservoir: the Railcraft tank built on it. */
public class RailcraftPumpReservoir extends CoolantPumpReservoir {

    private final MTECoolantPump pump;

    public RailcraftPumpReservoir(MTECoolantPump pump) {
        super(pump);
        this.pump = pump;
    }

    /**
     * The Railcraft tank over the pump's footprint, through its master block; failing that, any fluid handler there.
     */
    @Override
    protected ReservoirTank findTank() {
        IGregTechTileEntity base = pump.getBaseMetaTileEntity();
        if (base == null || base.getWorld() == null) return null;
        World world = base.getWorld();
        ForgeDirection front = base.getFrontFacing();
        boolean faced = front != null && front != ForgeDirection.UNKNOWN;
        ForgeDirection back = faced ? front.getOpposite() : ForgeDirection.SOUTH;
        ForgeDirection right = faced ? front.getRotation(ForgeDirection.UP) : ForgeDirection.WEST;
        int reach = pump.getWidth() / 2;
        for (int localX = -reach; localX <= reach; localX++) {
            for (int localZ = 0; localZ < pump.getWidth(); localZ++) {
                int x = base.getXCoord() + right.offsetX * localX + back.offsetX * localZ;
                int z = base.getZCoord() + right.offsetZ * localX + back.offsetZ * localZ;
                for (int dy = 2; dy <= 3; dy++) {
                    TileEntity tile = world.getTileEntity(x, base.getYCoord() + dy, z);
                    if (tile instanceof TileTankBase tankTile
                        && tankTile.getMasterBlock() instanceof TileTankBase master
                        && master.getTank() != null) {
                        return ofRailcraft(master.getTank());
                    }
                    if (tile instanceof IFluidHandler handler) return ReservoirTank.of(handler, ForgeDirection.DOWN);
                }
            }
        }
        return null;
    }

    private static ReservoirTank ofRailcraft(StandardTank tank) {
        return new ReservoirTank() {

            @Override
            public FluidStack getFluid() {
                return tank.getFluid();
            }

            @Override
            public long getCapacityLiters() {
                return tank.getCapacity();
            }

            @Override
            public long getAmountLiters() {
                return tank.getFluidAmount();
            }

            @Override
            public int drain(int liters) {
                FluidStack drained = tank.drain(liters, true);
                return drained != null ? drained.amount : 0;
            }

            @Override
            public int fill(FluidStack stack) {
                return tank.fill(stack, true);
            }
        };
    }

    public TileTankBase getMasterTankTile() {
        IGregTechTileEntity baseTE = pump.getBaseMetaTileEntity();
        if (baseTE != null && baseTE.getWorld() != null) {
            World world = baseTE.getWorld();
            int x0 = baseTE.getXCoord();
            int y0 = baseTE.getYCoord();
            int z0 = baseTE.getZCoord();
            ForgeDirection front = baseTE.getFrontFacing();
            ForgeDirection back = front != null && front != ForgeDirection.UNKNOWN ? front.getOpposite()
                : ForgeDirection.SOUTH;
            ForgeDirection right = front != null && front != ForgeDirection.UNKNOWN
                ? front.getRotation(ForgeDirection.UP)
                : ForgeDirection.WEST;
            for (int localX = 0; localX < pump.getWidth(); localX++) {
                for (int localZ = 0; localZ < pump.getWidth(); localZ++) {
                    int bx = x0 + right.offsetX * localX + back.offsetX * localZ;
                    int bz = z0 + right.offsetZ * localX + back.offsetZ * localZ;
                    for (int yOffset = 2; yOffset <= 3; yOffset++) {
                        int y = y0 + yOffset;
                        TileEntity tile = world.getTileEntity(bx, y, bz);
                        if (tile instanceof TileTankBase) {
                            TileTankBase tankTile = (TileTankBase) tile;
                            TileTankBase master = (TileTankBase) tankTile.getMasterBlock();
                            if (master != null) {
                                return master;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    public static Materials getRailcraftTankMaterial(TileEntity te) {
        if (te == null) return null;
        if (te instanceof TileTankBase) {
            TileTankBase tankBase = (TileTankBase) te;
            mods.railcraft.common.blocks.machine.beta.MetalTank tankType = tankBase.getTankType();
            if (tankType instanceof mods.railcraft.common.blocks.machine.beta.SteelTank) {
                return Materials.Steel;
            }
            if (tankType instanceof mods.railcraft.common.blocks.machine.beta.IronTank) {
                return Materials.Iron;
            }
            if (tankType instanceof mods.railcraft.common.blocks.machine.tank.GenericMultiTankBase) {
                String mat = ((mods.railcraft.common.blocks.machine.tank.GenericMultiTankBase) tankType).tankMaterial;
                if (mat != null) {
                    mat = mat.toLowerCase();
                    switch (mat) {
                        case "steel":
                            return Materials.Steel;
                        case "iron":
                            return Materials.Iron;
                        case "stainless":
                            return Materials.StainlessSteel;
                        case "titanium":
                            return Materials.Titanium;
                        case "tungstensteel":
                            return Materials.TungstenSteel;
                        case "neutronium":
                            return Materials.Neutronium;
                        case "aluminium":
                        case "aluminum":
                            return Materials.Aluminium;
                        case "palladium":
                            return Materials.Palladium;
                        case "iridium":
                            return Materials.Iridium;
                        case "osmium":
                            return Materials.Osmium;
                        default:
                            return null;
                    }
                }
            }

        }
        String name = te.getClass()
            .getSimpleName();
        switch (name) {
            case "TileTankSteel":
            case "TileSteelTank":
            case "SteelTank":
                return Materials.Steel;
            case "TileTankIron":
            case "TileIronTank":
            case "IronTank":
                return Materials.Iron;
            case "TileTankStainlessSteel":
            case "TileStainlessSteelTank":
            case "StainlessSteelTank":
                return Materials.StainlessSteel;
            case "TileTankTitanium":
            case "TileTitaniumTank":
            case "TitaniumTank":
                return Materials.Titanium;
            case "TileTankTungstenSteel":
            case "TileTungstenSteelTank":
            case "TungstenSteelTank":
                return Materials.TungstenSteel;
            case "TileTankNeutronium":
            case "TileNeutroniumTank":
            case "NeutroniumTank":
                return Materials.Neutronium;
            case "TileTankAluminium":
            case "TileAluminiumTank":
            case "AluminiumTank":
                return Materials.Aluminium;
            case "TileTankPalladium":
            case "TilePalladiumTank":
            case "PalladiumTank":
                return Materials.Palladium;
            case "TileTankIridium":
            case "TileIridiumTank":
            case "IridiumTank":
                return Materials.Iridium;
            case "TileTankOsmium":
            case "TileOsmiumTank":
            case "OsmiumTank":
                return Materials.Osmium;
            default:
                return null;
        }
    }

    /** Railcraft's private TileMultiBlock.testIfMasterBlock, looked up once. */
    private static final Method TEST_IF_MASTER_BLOCK = findTestIfMasterBlock();

    private static Method findTestIfMasterBlock() {
        try {
            Method m = TileMultiBlock.class.getDeclaredMethod("testIfMasterBlock");
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Railcraft's TileMultiBlock has no testIfMasterBlock", e);
        }
    }

    /** Has a Railcraft multiblock check its structure now instead of on its next update. */
    private static void testIfMasterBlock(TileMultiBlock tmb) {
        try {
            TEST_IF_MASTER_BLOCK.invoke(tmb);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not have a Railcraft tank check its structure", e);
        }
    }

    /**
     * After a load, a Railcraft tank links its blocks to its master block only when that master next updates (which
     * of its blocks is the master is saved), so a pump checking its structure on its first tick would find no tank.
     * Finds the tank's master above the pump and has it check the tank now. Returns whether it found one.
     */
    private static boolean testTankMasterAbove(World world, int x0, int y, int z0, ForgeDirection right,
        ForgeDirection back) {
        // Tanks are up to 8 high and 11 wide (a 9-wide interior and its walls), and sit over the pump's footprint
        for (int dy = 0; dy < 10; dy++) {
            for (int side = -6; side <= 6; side++) {
                for (int depth = -2; depth <= 11; depth++) {
                    int x = x0 + right.offsetX * side + back.offsetX * depth;
                    int z = z0 + right.offsetZ * side + back.offsetZ * depth;
                    if (!world.blockExists(x, y + dy, z)) continue;
                    if (world.getTileEntity(x, y + dy, z) instanceof TileMultiBlock tmb && tmb.isMaster()) {
                        if (!tmb.isStructureValid()) {
                            testIfMasterBlock(tmb);
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean verifyRailcraftTankAbove(IGregTechTileEntity aBaseMetaTileEntity) {
        if (aBaseMetaTileEntity == null) return false;
        World world = aBaseMetaTileEntity.getWorld();
        if (world == null) return false;

        ForgeDirection front = aBaseMetaTileEntity.getFrontFacing();
        ForgeDirection back = front != null && front != ForgeDirection.UNKNOWN ? front.getOpposite()
            : ForgeDirection.SOUTH;
        ForgeDirection right = front != null && front != ForgeDirection.UNKNOWN ? front.getRotation(ForgeDirection.UP)
            : ForgeDirection.WEST;

        int x0 = aBaseMetaTileEntity.getXCoord();
        int y0 = aBaseMetaTileEntity.getYCoord();
        int z0 = aBaseMetaTileEntity.getZCoord();
        int y = y0 + 2;

        TileMultiBlock masterBlock = null;

        int probeX = x0 + back.offsetX;
        int probeZ = z0 + back.offsetZ;
        TileEntity centerTe = world.getTileEntity(probeX, y, probeZ);
        if (centerTe instanceof TileMultiBlock) {
            TileMultiBlock tmbCenter = (TileMultiBlock) centerTe;
            if (!tmbCenter.isStructureValid()) {
                testIfMasterBlock(tmbCenter);
            }
            masterBlock = tmbCenter.getMasterBlock();
        }

        if (masterBlock == null && testTankMasterAbove(world, x0, y, z0, right, back)) {
            TileEntity te = world.getTileEntity(probeX, y, probeZ);
            if (te instanceof TileMultiBlock tmb) masterBlock = tmb.getMasterBlock();
        }

        if (masterBlock == null) {
            for (int dx = -4; dx <= 4; dx++) {
                TileEntity te = world
                    .getTileEntity(x0 + right.offsetX * dx + back.offsetX, y, z0 + right.offsetZ * dx + back.offsetZ);
                if (te instanceof TileMultiBlock) {
                    TileMultiBlock tmb = (TileMultiBlock) te;
                    if (!tmb.isStructureValid()) {
                        testIfMasterBlock(tmb);
                    }
                    masterBlock = tmb.getMasterBlock();
                    if (masterBlock != null) break;
                }
            }
        }

        if (masterBlock == null) {
            pump.setLoopStatusKey("modularnuclear.coolant_loop.pump.status.missing_tank");
            return false;
        }

        Materials tankMat = getRailcraftTankMaterial(masterBlock);
        if (tankMat == null) {
            pump.setLoopStatusKey("modularnuclear.coolant_loop.pump.status.unrecognized_tank");
            return false;
        }
        if (!LoopGraphCrawler.isAllowedCoolantPipeMaterial(tankMat)) {
            pump.setLoopStatusKey("modularnuclear.coolant_loop.pump.status.tank_no_pipe", tankMat.mDefaultLocalName);
            return false;
        }
        pump.setTankMaterial(tankMat);

        MultiBlockPattern pattern = masterBlock.getPattern();
        if (pattern == null) {
            pump.setLoopStatusKey("modularnuclear.coolant_loop.pump.status.invalid_pattern");
            return false;
        }
        int tankWidthX = pattern.getPatternWidthX();
        int tankWidthZ = pattern.getPatternWidthZ();
        int effX = (tankWidthX > 2) ? (tankWidthX - 2) : tankWidthX;
        int effZ = (tankWidthZ > 2) ? (tankWidthZ - 2) : tankWidthZ;

        int detectedWidth = effX;
        if (detectedWidth != 3 && detectedWidth != 5 && detectedWidth != 7 && detectedWidth != 9) {
            pump.setLoopStatusKey("modularnuclear.coolant_loop.pump.status.unsupported_width", detectedWidth);
            return false;
        }
        if (effX != effZ) {
            pump.setLoopStatusKey("modularnuclear.coolant_loop.pump.status.tank_square", effX, effZ);
            return false;
        }

        pump.setWidth(detectedWidth);
        pump.setLength(detectedWidth);
        int R = detectedWidth / 2;

        for (int localX = -R; localX <= R; localX++) {
            for (int localZ = 0; localZ < detectedWidth; localZ++) {
                int bx = x0 + right.offsetX * localX + back.offsetX * localZ;
                int bz = z0 + right.offsetZ * localX + back.offsetZ * localZ;
                TileEntity te = world.getTileEntity(bx, y, bz);
                if (te == null) {
                    pump.setLoopStatusKey("modularnuclear.coolant_loop.pump.status.missing_tank_block", bx, y, bz);
                    return false;
                }
                if (!(te instanceof TileMultiBlock)) {
                    pump.setLoopStatusKey(
                        "modularnuclear.coolant_loop.pump.status.not_tank_block",
                        bx,
                        y,
                        bz,
                        te.getClass()
                            .getSimpleName());
                    return false;
                }
                TileMultiBlock tmb = (TileMultiBlock) te;
                if (!tmb.isStructureValid()) {
                    pump.setLoopStatusKey("modularnuclear.coolant_loop.pump.status.tank_not_valid");
                    return false;
                }
                if (tmb.getMasterBlock() != masterBlock) {
                    pump.setLoopStatusKey("modularnuclear.coolant_loop.pump.status.multiple_tanks");
                    return false;
                }
            }
        }

        FluidStack availableFluid = getReservoirFluid();
        if (availableFluid == null || availableFluid.amount <= 0 || availableFluid.getFluid() == null) {
            // The tank is built: an empty one keeps the pump from starting, not from forming
            pump.setLoopStatusKey("modularnuclear.coolant_loop.pump.status.tank_empty");
            return true;
        }

        if (CoolantFluidHelper.isLiquidNuclearFuel(availableFluid)) {
            pump.failOnLiquidNuclearFuel(
                availableFluid.getFluid()
                    .getName());
            return false;
        }

        if (CoolantFluidHelper.isPlainRegularWater(availableFluid)) {
            pump.setLoopStatusKey("modularnuclear.coolant_loop.pump.status.refuse_water");
            return false;
        }

        // A gas a gas loop runs on. See Note [The pump on a gas loop]
        if (com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasProperty.get(
            availableFluid.getFluid()
                .getName())
            != null) {
            return true;
        }

        if (CoolantFluidHelper.isGaseousFluid(availableFluid)) {
            pump.setLoopStatusKey(
                "modularnuclear.coolant_loop.pump.status.refuse_gas",
                availableFluid.getFluid()
                    .getName());
            return false;
        }

        if (CoolantFluidHelper.isLava(availableFluid)) {
            if (!CoolantFluidHelper.isNetherWorld(world)) {
                pump.setLoopStatusKey("modularnuclear.coolant_loop.pump.status.refuse_lava");
                return false;
            }
        }

        CoolantFluidProperty prop = CoolantFluidProperty.get(
            availableFluid.getFluid()
                .getName());
        if (prop == null) {
            pump.setLoopStatusKey(
                "modularnuclear.coolant_loop.pump.status.refuse_unknown_coolant",
                availableFluid.getFluid()
                    .getName());
            return false;
        }
        if (prop.isPlainWater()) {
            pump.setLoopStatusKey("modularnuclear.coolant_loop.pump.status.refuse_water");
            return false;
        }

        if (CoolantFluidHelper.isMoltenFluid(availableFluid.getFluid(), availableFluid, prop)) {
            if (!(CoolantFluidHelper.isLava(availableFluid) && CoolantFluidHelper.isNetherWorld(world))) {
                double declaredMelting = CoolantFluidHelper
                    .getMeltingPointCelsius(availableFluid.getFluid(), availableFluid, prop);
                double resTemp = getReservoirTemperatureCelsius();
                if (Double.isNaN(resTemp)) {
                    resTemp = CoolantFluidHelper.getBaselineInletTemperatureCelsius(
                        availableFluid.getFluid(),
                        availableFluid,
                        prop,
                        pump.getBiomeTemperatureCelsius());
                    setReservoirTemperatureCelsius(resTemp);
                }
                if (resTemp < declaredMelting - CoolantLoopEngine.FREEZE_TOLERANCE_CELSIUS) {
                    pump.setLoopStatusKey(
                        "modularnuclear.coolant_loop.pump.status.refuse_solidify",
                        resTemp,
                        declaredMelting,
                        prop.getFluidName());
                    return false;
                }
            }
        }
        pump.getEngine()
            .setFluid(prop);
        return true;
    }
}

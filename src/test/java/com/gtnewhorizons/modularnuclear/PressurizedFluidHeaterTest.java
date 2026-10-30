package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Map;

import net.minecraft.init.Bootstrap;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.gtnewhorizon.structurelib.StructureLib;
import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantFluidProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.ICoolantLoopPump;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopGraphCrawler;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopSegment;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchPressurizedFluid;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchPressurizedInput;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchPressurizedOutput;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTEPressurizedFluidHeater;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.structure.HeaterPipeCasingTier;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.structure.PressurizedFluidHeaterStructure;
import com.gtnewhorizons.modularnuclear.common.nuclear.CoolantPassage;
import com.gtnewhorizons.modularnuclear.common.opencomputers.DriverPressurizedFluidHeater;
import com.gtnewhorizons.modularnuclear.common.opencomputers.PressurizedFluidHeaterEnvironment;
import com.gtnewhorizons.modularnuclear.common.projectred.ProjectRedTransmissionHandler;

import cpw.mods.fml.common.ICrashCallable;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.relauncher.FMLInjectionData;
import cpw.mods.fml.relauncher.FMLRelaunchLog;
import cpw.mods.fml.relauncher.Side;
import gregtech.GTMod;
import gregtech.api.enums.HeatingCoilLevel;
import gregtech.api.enums.Materials;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.metatileentity.implementations.MTEHatchMaintenance;
import gregtech.api.util.shutdown.ShutDownReason;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gregtech.common.GTProxy;
import gregtech.common.data.GTPowerfailTracker;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Context;
import tectech.thing.metaTileEntity.hatch.MTEHatchEnergyMulti;
import tectech.thing.metaTileEntity.hatch.MTEHatchEnergyTunnel;

public class PressurizedFluidHeaterTest {

    private GTPowerfailTracker mockTracker;

    @BeforeAll
    static void initEnvironment() {
        Thread.currentThread()
            .setName("Server thread");
        try {
            Loader mockLoader = Mockito.mock(Loader.class);
            Mockito.when(mockLoader.getCallableCrashInformation())
                .thenReturn(Mockito.mock(ICrashCallable.class));
            new File("build/tmp/config").mkdirs();
            Mockito.when(mockLoader.getConfigDir())
                .thenReturn(new File("build/tmp/config"));
            Field f = Loader.class.getDeclaredField("instance");
            f.setAccessible(true);
            if (f.get(null) == null) {
                f.set(null, mockLoader);
            }
        } catch (Throwable ignored) {}

        try {
            Bootstrap.func_151354_b();
        } catch (Throwable ignored) {}

        try {
            Field mcHome = FMLInjectionData.class.getDeclaredField("minecraftHome");
            mcHome.setAccessible(true);
            mcHome.set(null, new File("."));
        } catch (Throwable ignored) {}

        try {
            Field sideField = FMLRelaunchLog.class.getDeclaredField("side");
            sideField.setAccessible(true);
            sideField.set(null, Side.SERVER);
        } catch (Throwable ignored) {}

        registerFluidIfMissing("water");
        registerFluidIfMissing("ic2distilledwater");
        registerFluidIfMissing("distilledwater");

        gregtech.api.GregTechAPI.sBlockCasings1 = net.minecraft.init.Blocks.stone;
        gregtech.api.GregTechAPI.sBlockCasings2 = net.minecraft.init.Blocks.stone;
        gregtech.api.GregTechAPI.sBlockCasings4 = net.minecraft.init.Blocks.stone;
        gregtech.api.GregTechAPI.sBlockCasings5 = net.minecraft.init.Blocks.stone;
        gregtech.api.GregTechAPI.sBlockCasings10 = net.minecraft.init.Blocks.stone;
        gregtech.api.GregTechAPI.sBlockCasings11 = net.minecraft.init.Blocks.stone;
        gregtech.api.GregTechAPI.sBlockSheetmetalGT = net.minecraft.init.Blocks.stone;

        try {
            Field fBlockHint = StructureLib.class.getDeclaredField("blockHint");
            fBlockHint.setAccessible(true);
            if (fBlockHint.get(null) == null) {
                fBlockHint.set(null, net.minecraft.init.Blocks.stone);
            }
        } catch (Throwable ignored) {}
    }

    private static void registerFluidIfMissing(String name) {
        if (FluidRegistry.getFluid(name) == null) {
            Fluid f = new Fluid(name);
            FluidRegistry.registerFluid(f);
        }
    }

    @BeforeEach
    void setupTracker() {
        if (GTMod.proxy == null) {
            GTMod.proxy = Mockito.mock(GTProxy.class);
        }
        mockTracker = Mockito.mock(GTPowerfailTracker.class);
        GTMod.proxy.powerfailTracker = mockTracker;
    }

    private IGregTechTileEntity createMockBaseMetaTileEntity(boolean[] allowedToWorkHolder) {
        IGregTechTileEntity mockBase = Mockito.mock(IGregTechTileEntity.class);
        Mockito.when(mockBase.isAllowedToWork())
            .thenAnswer(inv -> allowedToWorkHolder[0]);
        Mockito.when(mockBase.isServerSide())
            .thenReturn(true);
        Mockito.when(mockBase.getFrontFacing())
            .thenReturn(ForgeDirection.SOUTH);
        Mockito.when(mockBase.getBackFacing())
            .thenReturn(ForgeDirection.NORTH);

        boolean[] shutdownHolder = new boolean[] { false };
        ShutDownReason[] reasonHolder = new ShutDownReason[] { ShutDownReasonRegistry.NONE };

        Mockito.doAnswer(inv -> {
            shutdownHolder[0] = inv.getArgument(0);
            return null;
        })
            .when(mockBase)
            .setShutdownStatus(Mockito.anyBoolean());
        Mockito.when(mockBase.wasShutdown())
            .thenAnswer(inv -> shutdownHolder[0]);

        Mockito.doAnswer(inv -> {
            reasonHolder[0] = inv.getArgument(0);
            return null;
        })
            .when(mockBase)
            .setShutDownReason(Mockito.any());
        Mockito.when(mockBase.getLastShutDownReason())
            .thenAnswer(inv -> reasonHolder[0]);

        Mockito.doAnswer(inv -> {
            allowedToWorkHolder[0] = false;
            return null;
        })
            .when(mockBase)
            .disableWorking();

        Mockito.doAnswer(inv -> {
            allowedToWorkHolder[0] = true;
            shutdownHolder[0] = false;
            reasonHolder[0] = ShutDownReasonRegistry.NONE;
            return null;
        })
            .when(mockBase)
            .enableWorking();

        return mockBase;
    }

    private IGregTechTileEntity createMockHatchBase(MetaTileEntity metaTileEntity) {
        IGregTechTileEntity mockBase = Mockito.mock(IGregTechTileEntity.class);
        Mockito.when(mockBase.getMetaTileEntity())
            .thenReturn(metaTileEntity);
        Mockito.when(mockBase.isServerSide())
            .thenReturn(true);
        metaTileEntity.setBaseMetaTileEntity(mockBase);
        return mockBase;
    }

    private MTEHatchEnergy createMockEnergyHatch(int tier, long storedEU) {
        MTEHatchEnergy hatch = new MTEHatchEnergy("mock_energy_" + tier, tier, new String[0], null);
        IGregTechTileEntity baseTE = Mockito.mock(IGregTechTileEntity.class);
        long[] stored = new long[] { storedEU };
        Mockito.when(baseTE.getMetaTileEntity())
            .thenReturn(hatch);
        Mockito.when(baseTE.isServerSide())
            .thenReturn(true);
        Mockito.when(baseTE.getStoredEU())
            .thenAnswer(inv -> stored[0]);
        Mockito.when(baseTE.decreaseStoredEnergyUnits(Mockito.anyLong(), Mockito.anyBoolean()))
            .thenAnswer(inv -> {
                long toDecrease = inv.getArgument(0);
                if (stored[0] >= toDecrease) {
                    stored[0] -= toDecrease;
                    return true;
                }
                return false;
            });
        hatch.setBaseMetaTileEntity(baseTE);
        return hatch;
    }

    private MTEHatchEnergyMulti createMockMultiAmpEnergyHatch(int tier, int amps, long storedEU) {
        MTEHatchEnergyMulti hatch = new MTEHatchEnergyMulti(
            "mock_multi_energy_" + tier,
            tier,
            amps,
            new String[0],
            null);
        IGregTechTileEntity baseTE = Mockito.mock(IGregTechTileEntity.class);
        long[] stored = new long[] { storedEU };
        Mockito.when(baseTE.getMetaTileEntity())
            .thenReturn(hatch);
        Mockito.when(baseTE.isServerSide())
            .thenReturn(true);
        Mockito.when(baseTE.getStoredEU())
            .thenAnswer(inv -> stored[0]);
        Mockito.when(baseTE.decreaseStoredEnergyUnits(Mockito.anyLong(), Mockito.anyBoolean()))
            .thenAnswer(inv -> {
                long toDecrease = inv.getArgument(0);
                if (stored[0] >= toDecrease) {
                    stored[0] -= toDecrease;
                    return true;
                }
                return false;
            });
        hatch.setBaseMetaTileEntity(baseTE);
        return hatch;
    }

    private MTEPressurizedFluidHeater createConfiguredHeater(int length, HeatingCoilLevel coilLevel,
        HeaterPipeCasingTier pipeTier, boolean[] allowedHolder) {
        MTEPressurizedFluidHeater heater = new MTEPressurizedFluidHeater("test_heater");
        IGregTechTileEntity mockBase = createMockBaseMetaTileEntity(allowedHolder);
        Mockito.when(mockBase.getMetaTileEntity())
            .thenReturn(heater);
        heater.setBaseMetaTileEntity(mockBase);

        heater.setStructureLength(length);
        heater.setCoilLevel(coilLevel);
        heater.setPipeCasingTier(pipeTier.getCasingTier());
        heater.mMachine = true;
        heater.mWrench = true;
        heater.mScrewdriver = true;
        heater.mSoftMallet = true;
        heater.mHardHammer = true;
        heater.mSolderingTool = true;
        heater.mCrowbar = true;

        MTEHatchPressurizedInput inlet = new MTEHatchPressurizedInput("inlet", 4, new String[0], null);
        MTEHatchPressurizedOutput outlet = new MTEHatchPressurizedOutput("outlet", 4, new String[0], null);
        heater.addPressurizedInputToMachineList(createMockHatchBase(inlet), 11);
        heater.addPressurizedOutputToMachineList(createMockHatchBase(outlet), 11);

        return heater;
    }

    @Test
    public void testStructureShapesAndLengths() {
        for (int len = PressurizedFluidHeaterStructure.MIN_LENGTH; len
            <= PressurizedFluidHeaterStructure.MAX_LENGTH; len++) {
            String[][] shape = PressurizedFluidHeaterStructure.makeShape(len);
            assertEquals(len, shape.length, "Shape slice count must equal overall length " + len);

            // Front layer: xxx / xIx / x~x
            assertArrayEquals(new String[] { "xxx", "xIx", "x~x" }, shape[0]);
            // Middle layers (1..len-2): ccc / cpc / ccc
            int rings = len - 2;
            assertTrue(rings >= 1 && rings <= 9, "Ring count must be between 1 and 9");
            for (int z = 1; z < len - 1; z++) {
                assertArrayEquals(new String[] { "ccc", "cpc", "ccc" }, shape[z]);
            }
            // Back layer: xxx / xOx / xxx
            assertArrayEquals(new String[] { "xxx", "xOx", "xxx" }, shape[len - 1]);
        }
    }

    @Test
    public void testCoilTierToMaxEnergyHatchTierAndMaxEuScaling() {
        // LV (1) = Cupronickel -> coilTierIndex = 0 -> max EU/t = 32 * rings * 4^0 = 32 * rings
        assertEquals(0, PressurizedFluidHeaterStructure.getCoilTierIndex(HeatingCoilLevel.LV));
        assertEquals(1, PressurizedFluidHeaterStructure.getMaxAllowedEnergyHatchTier(HeatingCoilLevel.LV));
        assertEquals(32L, PressurizedFluidHeaterStructure.getMaxEuPerTick(1, HeatingCoilLevel.LV));
        assertEquals(288L, PressurizedFluidHeaterStructure.getMaxEuPerTick(9, HeatingCoilLevel.LV));

        // MV (2) = Kanthal -> coilTierIndex = 1 -> max EU/t = 32 * rings * 4^1 = 128 * rings
        assertEquals(1, PressurizedFluidHeaterStructure.getCoilTierIndex(HeatingCoilLevel.MV));
        assertEquals(2, PressurizedFluidHeaterStructure.getMaxAllowedEnergyHatchTier(HeatingCoilLevel.MV));
        assertEquals(128L, PressurizedFluidHeaterStructure.getMaxEuPerTick(1, HeatingCoilLevel.MV));

        // HV (3) = Nichrome -> coilTierIndex = 2 -> max EU/t = 32 * rings * 4^2 = 512 * rings
        assertEquals(2, PressurizedFluidHeaterStructure.getCoilTierIndex(HeatingCoilLevel.HV));
        assertEquals(3, PressurizedFluidHeaterStructure.getMaxAllowedEnergyHatchTier(HeatingCoilLevel.HV));
        assertEquals(512L, PressurizedFluidHeaterStructure.getMaxEuPerTick(1, HeatingCoilLevel.HV));

        // EV (4) = TPV -> coilTierIndex = 3 -> max EU/t = 32 * rings * 4^3 = 2048 * rings
        assertEquals(3, PressurizedFluidHeaterStructure.getCoilTierIndex(HeatingCoilLevel.EV));
        assertEquals(4, PressurizedFluidHeaterStructure.getMaxAllowedEnergyHatchTier(HeatingCoilLevel.EV));
        assertEquals(2048L, PressurizedFluidHeaterStructure.getMaxEuPerTick(1, HeatingCoilLevel.EV));
        assertEquals(18432L, PressurizedFluidHeaterStructure.getMaxEuPerTick(9, HeatingCoilLevel.EV));

        // Heating coil efficiency: 25% at Cupronickel (LV, index 0), linearly reaching 100% at MAX coil tier
        assertEquals(0.25, PressurizedFluidHeaterStructure.getCoilHeatingEfficiency(HeatingCoilLevel.LV), 1e-9);
        assertEquals(1.00, PressurizedFluidHeaterStructure.getCoilHeatingEfficiency(HeatingCoilLevel.MAX), 1e-9);

        int maxTierIdx = PressurizedFluidHeaterStructure.getMaxCoilTierIndex();
        double expectedStep = 0.75 / maxTierIdx;
        for (int t = 0; t <= maxTierIdx; t++) {
            HeatingCoilLevel level = HeatingCoilLevel.getFromTier((byte) t);
            double expectedEff = 0.25 + t * expectedStep;
            assertEquals(
                expectedEff,
                PressurizedFluidHeaterStructure.getCoilHeatingEfficiency(level),
                1e-9,
                "Linear efficiency mismatch at coil tier index " + t + " (" + level + ")");
        }
    }

    @Test
    public void testEnergyHatchValidationSingleMultiAndLaserRejection() {
        boolean[] allowed = new boolean[] { true };
        MTEPressurizedFluidHeater heater = createConfiguredHeater(
            5,
            HeatingCoilLevel.HV,
            HeaterPipeCasingTier.ELECTRUM,
            allowed);

        // Add maintenance hatch
        MTEHatchMaintenance maint = Mockito.mock(MTEHatchMaintenance.class);
        heater.mMaintenanceHatches.add(maint);

        // 1. Single-amp HV energy hatch (tier 3 <= HV coil tier 3) -> valid
        MTEHatchEnergy hvSingle = createMockEnergyHatch(3, 10000L);
        assertTrue(heater.addEnergyOrMultiAmpToMachineList(hvSingle.getBaseMetaTileEntity(), 11));
        assertEquals(1, heater.getTotalEnergyHatchCount());
        assertEquals(3, heater.getInstalledEnergyHatchTier());
        assertTrue(heater.getInstalledEnergyHatchTier() <= heater.getMaxAllowedEnergyHatchTier());

        // 2. Single-amp EV energy hatch (tier 4 > HV coil tier 3) -> tier exceeds max allowed
        heater.mEnergyHatches.clear();
        MTEHatchEnergy evSingle = createMockEnergyHatch(4, 10000L);
        assertTrue(heater.addEnergyOrMultiAmpToMachineList(evSingle.getBaseMetaTileEntity(), 11));
        assertTrue(
            heater.getInstalledEnergyHatchTier() > heater.getMaxAllowedEnergyHatchTier(),
            "EV energy hatch on HV coils must exceed max allowed tier");

        // 3. Multi-amp HV energy hatch (tier 3 <= HV coil tier 3) -> valid
        heater.mEnergyHatches.clear();
        heater.getExoticEnergyHatches()
            .clear();
        MTEHatchEnergyMulti hvMulti = createMockMultiAmpEnergyHatch(3, 4, 50000L);
        assertTrue(heater.addEnergyOrMultiAmpToMachineList(hvMulti.getBaseMetaTileEntity(), 11));
        assertEquals(1, heater.getTotalEnergyHatchCount());
        assertEquals(3, heater.getInstalledEnergyHatchTier());

        // 4. Laser energy hatch (MTEHatchEnergyTunnel) -> rejected
        heater.getExoticEnergyHatches()
            .clear();
        MTEHatchEnergyTunnel laser = new MTEHatchEnergyTunnel("laser", 3, 2, new String[0], null);
        IGregTechTileEntity laserBase = createMockHatchBase(laser);
        assertFalse(
            heater.addEnergyOrMultiAmpToMachineList(laserBase, 11),
            "Laser hatch must be rejected by addEnergyOrMultiAmpToMachineList");
        assertTrue(MTEPressurizedFluidHeater.isLaserEnergyHatch(laser));
    }

    @Test
    public void testHeaterWorksInLoopsOfAnyPipeMaterial() {
        // The heater's casing tier does not need to match the loop pipe material
        World world = Mockito.mock(World.class);
        boolean[] allowed = new boolean[] { true };
        MTEPressurizedFluidHeater heater = createConfiguredHeater(
            3,
            HeatingCoilLevel.EV,
            HeaterPipeCasingTier.OSMIUM,
            allowed);

        // Place heater inlet at (0, 65, 1) and outlet at (0, 65, 3), heater controller at (0, 64, 1)
        IGregTechTileEntity heaterBase = heater.getBaseMetaTileEntity();
        Mockito.when(heaterBase.getWorld())
            .thenReturn(world);
        Mockito.when(heaterBase.getXCoord())
            .thenReturn(0);
        Mockito.when(heaterBase.getYCoord())
            .thenReturn((short) 64);
        Mockito.when(heaterBase.getZCoord())
            .thenReturn(1);

        MTEHatchPressurizedFluid inlet = heater.getPressurizedInputHatch();
        IGregTechTileEntity inletBase = inlet.getBaseMetaTileEntity();
        Mockito.when(inletBase.getWorld())
            .thenReturn(world);
        Mockito.when(inletBase.getXCoord())
            .thenReturn(0);
        Mockito.when(inletBase.getYCoord())
            .thenReturn((short) 65);
        Mockito.when(inletBase.getZCoord())
            .thenReturn(1);
        Mockito.when(inletBase.getFrontFacing())
            .thenReturn(ForgeDirection.NORTH);

        MTEHatchPressurizedFluid outlet = heater.getPressurizedOutputHatch();
        IGregTechTileEntity outletBase = outlet.getBaseMetaTileEntity();
        Mockito.when(outletBase.getWorld())
            .thenReturn(world);
        Mockito.when(outletBase.getXCoord())
            .thenReturn(0);
        Mockito.when(outletBase.getYCoord())
            .thenReturn((short) 65);
        Mockito.when(outletBase.getZCoord())
            .thenReturn(3);
        Mockito.when(outletBase.getFrontFacing())
            .thenReturn(ForgeDirection.SOUTH);

        // Pump discharge at (0, 65, 0) facing SOUTH -> enters heater inlet at (0, 65, 1)
        // Heater outlet at (0, 65, 3) facing SOUTH -> enters pump suction at (0, 65, 4)
        MTEHatchPressurizedOutput pumpDischarge = new MTEHatchPressurizedOutput("pump_dis", 4, new String[0], null);
        IGregTechTileEntity disBase = createMockHatchBase(pumpDischarge);
        Mockito.when(disBase.getWorld())
            .thenReturn(world);
        Mockito.when(disBase.getXCoord())
            .thenReturn(0);
        Mockito.when(disBase.getYCoord())
            .thenReturn((short) 65);
        Mockito.when(disBase.getZCoord())
            .thenReturn(0);
        Mockito.when(disBase.getFrontFacing())
            .thenReturn(ForgeDirection.SOUTH);

        MTEHatchPressurizedInput pumpSuction = new MTEHatchPressurizedInput("pump_suc", 4, new String[0], null);
        IGregTechTileEntity sucBase = createMockHatchBase(pumpSuction);
        Mockito.when(sucBase.getWorld())
            .thenReturn(world);
        Mockito.when(sucBase.getXCoord())
            .thenReturn(0);
        Mockito.when(sucBase.getYCoord())
            .thenReturn((short) 65);
        Mockito.when(sucBase.getZCoord())
            .thenReturn(4);
        Mockito.when(sucBase.getFrontFacing())
            .thenReturn(ForgeDirection.NORTH);

        TileEntity worldHeaterTE = Mockito.mock(
            TileEntity.class,
            Mockito.withSettings()
                .extraInterfaces(IGregTechTileEntity.class));
        Mockito.when(((IGregTechTileEntity) worldHeaterTE).getMetaTileEntity())
            .thenReturn(heater);
        Mockito.when(world.getTileEntity(0, 64, 1))
            .thenReturn(worldHeaterTE);

        TileEntity worldInletTE = Mockito.mock(
            TileEntity.class,
            Mockito.withSettings()
                .extraInterfaces(IGregTechTileEntity.class));
        Mockito.when(((IGregTechTileEntity) worldInletTE).getMetaTileEntity())
            .thenReturn(inlet);
        Mockito.when(world.getTileEntity(0, 65, 1))
            .thenReturn(worldInletTE);

        TileEntity worldOutletTE = Mockito.mock(
            TileEntity.class,
            Mockito.withSettings()
                .extraInterfaces(IGregTechTileEntity.class));
        Mockito.when(((IGregTechTileEntity) worldOutletTE).getMetaTileEntity())
            .thenReturn(outlet);
        Mockito.when(world.getTileEntity(0, 65, 3))
            .thenReturn(worldOutletTE);

        TileEntity worldSuctionTE = Mockito.mock(
            TileEntity.class,
            Mockito.withSettings()
                .extraInterfaces(IGregTechTileEntity.class));
        Mockito.when(((IGregTechTileEntity) worldSuctionTE).getMetaTileEntity())
            .thenReturn(pumpSuction);
        Mockito.when(world.getTileEntity(0, 65, 4))
            .thenReturn(worldSuctionTE);

        LoopGraphCrawler crawler = new LoopGraphCrawler(world);

        // Crawl with matching Osmium tank material -> succeeds
        LoopGraphCrawler.CrawlResult okResult = crawler.crawl(
            0,
            65,
            0,
            ForgeDirection.SOUTH,
            0,
            65,
            4,
            ForgeDirection.SOUTH,
            "pump:0:64:0",
            null,
            Materials.Osmium);
        assertTrue(
            okResult.isSuccess,
            "Crawl should succeed when heater pipe casing matches Osmium loop: " + okResult.failureReason);
        assertEquals(1, okResult.devices.size());
        assertSame(heater, okResult.devices.get(0));

        // An Osmium-cased heater in a Titanium loop crawls the same
        LoopGraphCrawler.CrawlResult titaniumResult = crawler.crawl(
            0,
            65,
            0,
            ForgeDirection.SOUTH,
            0,
            65,
            4,
            ForgeDirection.SOUTH,
            "pump:0:64:0",
            null,
            Materials.Titanium);
        assertTrue(titaniumResult.isSuccess, "Crawl must succeed: " + titaniumResult.failureReason);
    }

    @Test
    public void testElectricalHeatingFullAndPartialEuWithoutPowerfail() {
        boolean[] allowed = new boolean[] { true };
        // Length 5 -> 3 rings, Cupronickel (LV) -> max EU/t = 32 * 3 * 4^0 = 96 EU/t
        MTEPressurizedFluidHeater heater = createConfiguredHeater(
            5,
            HeatingCoilLevel.LV,
            HeaterPipeCasingTier.PLATINUM,
            allowed);
        assertEquals(3, heater.getRingCount());
        assertEquals(96L, heater.getMaxEuPerTick());

        double expectedCapacity = CoolantPassage.heatCapacity(HeaterPipeCasingTier.PLATINUM.getPassageVoltageTier())
            * 3;
        assertEquals(expectedCapacity, heater.getHeatCapacityHuPerC(), 1e-6);
        assertEquals(12.0, heater.getExchangeAreaM2(), 1e-6);

        // Attach energy hatch with 150 EU (enough for 1 full tick of 96 EU, and 1 partial tick of 54 EU)
        MTEHatchEnergy energyHatch = createMockEnergyHatch(1, 150L);
        heater.addEnergyOrMultiAmpToMachineList(energyHatch.getBaseMetaTileEntity(), 11);

        double euPerHu = MTEPressurizedFluidHeater.getEuPerHu(); // 0.25 -> 4 HU per EU at 100% efficiency
        double coilEff = heater.getCoilHeatingEfficiency(); // 0.25 for Cupronickel (LV)
        assertEquals(0.25, coilEff, 1e-9);
        double t0 = heater.getInnerTemperatureCelsius();
        assertEquals(20.0, t0, 1e-6);

        // Tick 1: consumes full 96 EU -> adds (96 / 0.25) * 0.25 = 96 HU
        heater.onPostTick(heater.getBaseMetaTileEntity(), 1L);
        assertEquals(96L, heater.getLastConsumedEuPerTick());
        double expectedHuTick1 = (96.0 / euPerHu) * coilEff;
        assertEquals(expectedHuTick1, heater.getLastHeatingHuPerTick(), 1e-6);
        assertEquals(t0 + expectedHuTick1 / expectedCapacity, heater.getInnerTemperatureCelsius(), 1e-6);
        assertFalse(heater.isPowerFailed(), "Heater must not powerfail on full EU draw");

        // Tick 2: only 54 EU left in hatch (< 96 max EU/t) -> consumes 54 EU without powerfailing!
        double t1 = heater.getInnerTemperatureCelsius();
        heater.onPostTick(heater.getBaseMetaTileEntity(), 2L);
        assertEquals(54L, heater.getLastConsumedEuPerTick());
        double expectedHuTick2 = (54.0 / euPerHu) * coilEff;
        assertEquals(expectedHuTick2, heater.getLastHeatingHuPerTick(), 1e-6);
        assertEquals(t1 + expectedHuTick2 / expectedCapacity, heater.getInnerTemperatureCelsius(), 1e-6);
        assertFalse(heater.isPowerFailed(), "Partial EU draw must NOT cause powerfail");
        assertTrue(
            heater.getBaseMetaTileEntity()
                .isAllowedToWork(),
            "Heater must remain enabled on partial EU");

        // Tick 3: 0 EU left in hatch -> consumes 0 EU without powerfailing!
        double t2 = heater.getInnerTemperatureCelsius();
        heater.onPostTick(heater.getBaseMetaTileEntity(), 3L);
        assertEquals(0L, heater.getLastConsumedEuPerTick());
        assertEquals(0.0, heater.getLastHeatingHuPerTick(), 1e-6);
        assertEquals(t2, heater.getInnerTemperatureCelsius(), 1e-6);
        assertFalse(heater.isPowerFailed(), "Zero EU in energy hatch must NOT cause powerfail");

        // Verify MAX coil tier reaches 100% (1.0) coil heating efficiency
        MTEPressurizedFluidHeater maxCoilHeater = createConfiguredHeater(
            3,
            HeatingCoilLevel.MAX,
            HeaterPipeCasingTier.OSMIUM,
            allowed);
        assertEquals(1.0, maxCoilHeater.getCoilHeatingEfficiency(), 1e-9);
        maxCoilHeater.setSimulatedStoredEU(1000L);
        maxCoilHeater.onPostTick(maxCoilHeater.getBaseMetaTileEntity(), 1L);
        assertEquals(1000L, maxCoilHeater.getLastConsumedEuPerTick());
        assertEquals(1000.0 / euPerHu, maxCoilHeater.getLastHeatingHuPerTick(), 1e-6);
    }

    @Test
    public void testBidirectionalConvectiveHeatTransferOnAndOff() {
        boolean[] allowed = new boolean[] { true };
        MTEPressurizedFluidHeater heater = createConfiguredHeater(
            4, // 2 rings
            HeatingCoilLevel.MV,
            HeaterPipeCasingTier.OSMIUM,
            allowed);

        CoolantFluidProperty fluid = CoolantFluidProperty.get("ic2distilledwater");
        assertNotNull(fluid);
        double flowM3s = 0.05; // 50 L/s
        double dt = 0.05; // 1 tick

        // Case 1: Heater is hot (500 °C), coolant is cold (100 °C) -> heat transfers from heater to coolant
        heater.setInnerTemperatureCelsius(500.0);
        LoopSegment seg = new LoopSegment("heater_seg", 2.0, 0.10, 0.000045, 1.5, 500.0, 2000.0, 100.0);
        double capacityHu = heater.getHeatCapacityHuPerC();

        heater.processThermalExchange(flowM3s, dt, fluid, seg);
        assertTrue(
            heater.getInnerTemperatureCelsius() < 500.0,
            "Heater inner temperature must decrease when heating cold coolant");
        assertTrue(seg.getCurrentTemperatureCelsius() > 100.0, "Coolant temperature must increase when heated");
        assertTrue(
            heater.getLastHeatTransferHuPerTick() > 0.0,
            "Heat transfer rate must be positive (heater -> coolant)");

        // Verify thermal energy conservation: HU lost by heater == HU transferred to coolant
        double huLostByHeater = (500.0 - heater.getInnerTemperatureCelsius()) * capacityHu;
        assertEquals(huLostByHeater, heater.getLastHeatTransferHuPerTick(), 1e-5);

        // Case 2: Powered OFF (allowedToWork = false), coolant is hotter (600 °C) than heater (200 °C)
        // -> no EU consumed, but heat still transfers from hot coolant INTO heater!
        allowed[0] = false;
        heater.setInnerTemperatureCelsius(200.0);
        LoopSegment hotSeg = new LoopSegment("hot_seg", 2.0, 0.10, 0.000045, 1.5, 500.0, 2000.0, 600.0);

        heater.onPostTick(heater.getBaseMetaTileEntity(), 10L);
        assertEquals(0L, heater.getLastConsumedEuPerTick(), "No EU consumed when powered off");

        heater.processThermalExchange(flowM3s, dt, fluid, hotSeg);
        assertTrue(
            heater.getInnerTemperatureCelsius() > 200.0,
            "Heater inner temperature must increase when absorbing heat from hotter coolant even when powered off");
        assertTrue(
            hotSeg.getCurrentTemperatureCelsius() < 600.0,
            "Hot coolant temperature must decrease when transferring heat into colder heater");
        assertTrue(
            heater.getLastHeatTransferHuPerTick() < 0.0,
            "Heat transfer rate must be negative when heat flows from coolant into heater");
    }

    @Test
    public void testMaintenancePenaltyReducesBothHeatingAndHeatTransfer() {
        boolean[] allowedFull = new boolean[] { true };
        MTEPressurizedFluidHeater fullHeater = createConfiguredHeater(
            3,
            HeatingCoilLevel.HV,
            HeaterPipeCasingTier.OSMIUM,
            allowedFull);
        MTEHatchEnergy fullEnergy = createMockEnergyHatch(3, 100_000L);
        fullHeater.addEnergyOrMultiAmpToMachineList(fullEnergy.getBaseMetaTileEntity(), 11);

        boolean[] allowedDegraded = new boolean[] { true };
        MTEPressurizedFluidHeater degradedHeater = createConfiguredHeater(
            3,
            HeatingCoilLevel.HV,
            HeaterPipeCasingTier.OSMIUM,
            allowedDegraded);
        // Simulate 2 maintenance issues (mWrench = false, mScrewdriver = false -> 80% efficiency)
        degradedHeater.mWrench = false;
        degradedHeater.mScrewdriver = false;
        assertEquals(0.8, degradedHeater.getEfficiencyFactor(), 1e-6);
        MTEHatchEnergy degEnergy = createMockEnergyHatch(3, 100_000L);
        degradedHeater.addEnergyOrMultiAmpToMachineList(degEnergy.getBaseMetaTileEntity(), 11);

        // 1. Compare electrical heating on 1 tick
        fullHeater.onPostTick(fullHeater.getBaseMetaTileEntity(), 1L);
        degradedHeater.onPostTick(degradedHeater.getBaseMetaTileEntity(), 1L);

        assertEquals(fullHeater.getLastConsumedEuPerTick(), degradedHeater.getLastConsumedEuPerTick());
        assertEquals(
            fullHeater.getLastHeatingHuPerTick() * 0.8,
            degradedHeater.getLastHeatingHuPerTick(),
            1e-5,
            "Maintenance issues must reduce EU-to-heat conversion efficiency");

        // 2. Compare convective heat transfer from 500 °C heater to 100 °C coolant
        fullHeater.setInnerTemperatureCelsius(500.0);
        degradedHeater.setInnerTemperatureCelsius(500.0);
        CoolantFluidProperty fluid = CoolantFluidProperty.get("ic2distilledwater");
        LoopSegment segFull = new LoopSegment("s1", 2.0, 0.10, 0.000045, 1.5, 500.0, 2000.0, 100.0);
        LoopSegment segDeg = new LoopSegment("s2", 2.0, 0.10, 0.000045, 1.5, 500.0, 2000.0, 100.0);

        fullHeater.processThermalExchange(0.05, 0.05, fluid, segFull);
        degradedHeater.processThermalExchange(0.05, 0.05, fluid, segDeg);

        assertTrue(
            degradedHeater.getLastHeatTransferHuPerTick() < fullHeater.getLastHeatTransferHuPerTick(),
            "Maintenance issues must reduce convective heat transfer rate");
    }

    @Test
    public void testOverTemperaturePowerfailAndLoopDisconnection() {
        boolean[] allowed = new boolean[] { true };
        MTEPressurizedFluidHeater heater = createConfiguredHeater(
            3,
            HeatingCoilLevel.EV,
            HeaterPipeCasingTier.TIN,
            allowed);
        double maxTemp = heater.getMaxOperatingTemperatureCelsius();
        assertEquals(1000.0, maxTemp, 1e-6);

        ICoolantLoopPump mockPump = Mockito.mock(ICoolantLoopPump.class);
        heater.setLoopPump(mockPump);

        // Set inner temperature right below limit (995 °C) and give it EV power to push it over 1000 °C
        heater.setInnerTemperatureCelsius(995.0);
        MTEHatchEnergy energyHatch = createMockEnergyHatch(4, 100_000L);
        heater.addEnergyOrMultiAmpToMachineList(energyHatch.getBaseMetaTileEntity(), 11);

        heater.onPostTick(heater.getBaseMetaTileEntity(), 5L);

        assertTrue(heater.getInnerTemperatureCelsius() > maxTemp);
        assertTrue(
            heater.isPowerFailed(),
            "Heater must enter powerfailed state when inner temperature exceeds casing limit");
        assertTrue(heater.isDeviceDisconnected(), "Powerfailed heater must report isDeviceDisconnected() == true");
        String disconnectReason = heater.getDisconnectionReason();
        assertNotNull(disconnectReason);
        assertFalse(
            disconnectReason.startsWith("Format error"),
            "Disconnection reason must format cleanly without IllegalFormatException: " + disconnectReason);
        assertTrue(
            disconnectReason.contains("1000.0"),
            "Disconnection reason should include max operating temperature: " + disconnectReason);
        assertFalse(allowed[0], "Powerfail must disable working on the controller");
        assertEquals(
            ShutDownReasonRegistry.POWER_LOSS,
            heater.getBaseMetaTileEntity()
                .getLastShutDownReason());
        Mockito.verify(mockPump, Mockito.atLeastOnce())
            .onDeviceDisconnected(Mockito.eq(heater), Mockito.anyString());

        // Verify that a real MTECoolantPump in an active/filled loop ruptures when PFH powerfails
        com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTECoolantPump realPump = new com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTECoolantPump(
            "test_pump");
        realPump.setLoopFormed(true);
        realPump.setLoopState(
            com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTECoolantPump.LoopState.CIRCULATING);
        realPump.setCurrentFillLiters(4000L);
        realPump.setSimulatedTankCapacityLiters(16000L);
        realPump.setSimulatedCoolantLiters(10000L);
        LoopSegment heaterSeg = new LoopSegment(heater.getDeviceId(), 1.0, 0.10, 0.000045, 1.5, 500.0, 1000.0, 995.0);
        realPump.getEngine()
            .addSegment(heaterSeg);
        realPump.addLoopDevice(heater);

        realPump.onDeviceDisconnected(heater, disconnectReason);
        assertFalse(realPump.isLoopFormed(), "Pump must mark loop as unformed on PFH powerfail");
        assertTrue(
            realPump.getEngine()
                .isRuptured(),
            "Active pump loop must rupture when PFH powerfails");
        assertEquals(
            com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTECoolantPump.LoopState.FAILED,
            realPump.getLoopState(),
            "Pump state must transition to FAILED on PFH powerfail rupture");
        assertEquals(
            2000L,
            realPump.getSimulatedCoolantLiters(),
            "Catastrophic loop rupture must drain 80% of reservoir coolant");

        // Cool down below limit and re-enable working -> clears powerfail
        heater.setLoopPump(mockPump);
        heater.setInnerTemperatureCelsius(400.0);
        heater.getBaseMetaTileEntity()
            .enableWorking();
        heater.onEnableWorking();
        heater.onPostTick(heater.getBaseMetaTileEntity(), 6L);
        assertFalse(heater.isPowerFailed(), "Re-enabling working below max temp should clear powerfail");
    }

    @Test
    public void testEnergyDetectorCoverRedstoneAndProjectRedSignals() {
        boolean[] allowed = new boolean[] { true };
        MTEPressurizedFluidHeater heater = createConfiguredHeater(
            3,
            HeatingCoilLevel.HV,
            HeaterPipeCasingTier.PLATINUM,
            allowed);

        // Simulate Energy Detector Cover on ForgeDirection.WEST with threshold = 1020 °C (ambient = 20 °C)
        heater.setSimulatedAmbientTemperatureCelsius(20.0);
        heater.setSimulatedEnergyDetectorCover(ForgeDirection.WEST, 1020L, false);

        // At ambient (20 °C) -> 0 redstone, 0 fine
        heater.setInnerTemperatureCelsius(20.0);
        heater.updateTemperatureRedstoneAndProjectRed();
        assertEquals((byte) 0, heater.getRedstoneOutput());
        assertEquals(0, heater.getFineOutputStrength());

        // Halfway between 20 °C and 1020 °C (520 °C -> 50%) -> redstone 7, fine 128
        heater.setInnerTemperatureCelsius(520.0);
        heater.updateTemperatureRedstoneAndProjectRed();
        assertEquals((byte) 7, heater.getRedstoneOutput());
        assertEquals(128, heater.getFineOutputStrength());

        // At threshold (1020 °C -> 100%) -> redstone 15, fine 255
        heater.setInnerTemperatureCelsius(1020.0);
        heater.updateTemperatureRedstoneAndProjectRed();
        assertEquals((byte) 15, heater.getRedstoneOutput());
        assertEquals(255, heater.getFineOutputStrength());

        // Verify ProjectRed bundled signal array
        heater.setBundledChannelMode(0); // Broadcast all 16 channels
        heater.updateTemperatureRedstoneAndProjectRed();
        byte[] bundled = heater.getBundledSignal(ForgeDirection.WEST.ordinal());
        assertNotNull(bundled);
        assertEquals(16, bundled.length);
        for (int i = 0; i < 16; i++) {
            assertEquals((byte) 255, bundled[i]);
        }

        // Single channel mode (Channel 3 -> index 2)
        heater.setBundledChannelMode(3);
        heater.updateTemperatureRedstoneAndProjectRed();
        byte[] singleChannel = heater.getBundledSignal(ForgeDirection.WEST.ordinal());
        for (int i = 0; i < 16; i++) {
            assertEquals(i == 2 ? (byte) 255 : (byte) 0, singleChannel[i]);
        }

        // Verify ProjectRedTransmissionHandler integration
        TileEntity worldTile = Mockito.mock(
            TileEntity.class,
            Mockito.withSettings()
                .extraInterfaces(IGregTechTileEntity.class));
        Mockito.when(((IGregTechTileEntity) worldTile).getMetaTileEntity())
            .thenReturn(heater);
        World world = Mockito.mock(World.class);
        Mockito.when(world.getTileEntity(10, 70, 10))
            .thenReturn(worldTile);

        ProjectRedTransmissionHandler handler = new ProjectRedTransmissionHandler();
        assertTrue(handler.canConnectBundled(world, 10, 70, 10, ForgeDirection.WEST.ordinal()));
        byte[] prSignal = handler.getBundledSignal(world, 10, 70, 10, ForgeDirection.WEST.ordinal());
        assertNotNull(prSignal);
        assertEquals((byte) 255, prSignal[2]);
    }

    @Test
    public void testOpenComputersDriverAndEnvironment() {
        boolean[] allowed = new boolean[] { true };
        MTEPressurizedFluidHeater heater = createConfiguredHeater(
            6, // 4 rings
            HeatingCoilLevel.EV,
            HeaterPipeCasingTier.OSMIUM,
            allowed);
        heater.setInnerTemperatureCelsius(345.6);

        World world = Mockito.mock(World.class);
        TileEntity te = Mockito.mock(
            TileEntity.class,
            Mockito.withSettings()
                .extraInterfaces(IGregTechTileEntity.class));
        Mockito.when(((IGregTechTileEntity) te).getMetaTileEntity())
            .thenReturn(heater);
        Mockito.when(world.getTileEntity(5, 64, 5))
            .thenReturn(te);

        DriverPressurizedFluidHeater driver = new DriverPressurizedFluidHeater();
        assertTrue(driver.worksWith(world, 5, 64, 5, ForgeDirection.UP));

        PressurizedFluidHeaterEnvironment env = (PressurizedFluidHeaterEnvironment) driver
            .createEnvironment(world, 5, 64, 5, ForgeDirection.UP);
        assertNotNull(env);
        assertEquals("pressurized_fluid_heater", env.preferredName());

        Context ctx = Mockito.mock(Context.class);
        Arguments args = Mockito.mock(Arguments.class);

        Object[] statsArr = env.getHeaterStats(ctx, args);
        assertNotNull(statsArr);
        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) statsArr[0];
        assertEquals(true, stats.get("isFormed"));
        assertEquals(6, stats.get("structureLength"));
        assertEquals(4, stats.get("ringCount"));
        assertEquals("Osmium", stats.get("pipeCasingMaterial"));
        assertEquals(345.6, (Double) stats.get("innerTemperatureCelsius"), 1e-6);
        assertEquals(16.0, (Double) stats.get("exchangeAreaM2"), 1e-6);

        assertEquals(345.6, (Double) env.getInnerTemperature(ctx, args)[0], 1e-6);
        assertEquals(heater.getMaxOperatingTemperatureCelsius(), (Double) env.getMaxTemperature(ctx, args)[0], 1e-6);
        assertEquals(heater.getHeatCapacityHuPerC(), (Double) env.getHeatCapacity(ctx, args)[0], 1e-6);
        assertEquals(16.0, (Double) env.getHeatTransferArea(ctx, args)[0], 1e-6);
        assertEquals(heater.getMaxEuPerTick(), (Long) env.getMaxEU(ctx, args)[0]);
        assertEquals(false, env.isPowerFailed(ctx, args)[0]);
    }
}

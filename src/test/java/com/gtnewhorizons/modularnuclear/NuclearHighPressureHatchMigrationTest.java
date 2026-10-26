package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.gtnewhorizons.coolantloops.common.util.LoopStateNbt;
import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;
import com.gtnewhorizons.coolantloops.engine.CoolantLoopEngine;
import com.gtnewhorizons.coolantloops.engine.LoopSegment;
import com.gtnewhorizons.modularnuclear.common.metatileentity.ModMetaTileEntities;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHighPressure;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHighPressureInput;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHighPressureOutput;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

public class NuclearHighPressureHatchMigrationTest {

    @BeforeAll
    static void initEnvironment() {
        Thread.currentThread()
            .setName("Server thread");
        try {
            cpw.mods.fml.common.Loader mockLoader = Mockito.mock(cpw.mods.fml.common.Loader.class);
            Mockito.when(mockLoader.getCallableCrashInformation())
                .thenReturn(Mockito.mock(cpw.mods.fml.common.ICrashCallable.class));
            java.lang.reflect.Field f = cpw.mods.fml.common.Loader.class.getDeclaredField("instance");
            f.setAccessible(true);
            if (f.get(null) == null) {
                f.set(null, mockLoader);
            }
        } catch (Throwable ignored) {}

        try {
            net.minecraft.init.Bootstrap.func_151354_b();
        } catch (Throwable ignored) {}

        try {
            java.lang.reflect.Field mcHome = cpw.mods.fml.relauncher.FMLInjectionData.class
                .getDeclaredField("minecraftHome");
            mcHome.setAccessible(true);
            mcHome.set(null, new java.io.File("."));
        } catch (Throwable ignored) {}

        try {
            if (gregtech.GTMod.proxy == null) {
                gregtech.GTMod.proxy = Mockito.mock(gregtech.common.GTProxy.class);
            }
        } catch (Throwable ignored) {}

        GregTechAPI.METATILEENTITIES[ModMetaTileEntities.ID_NUCLEAR_HIGH_PRESSURE_INPUT] = new MTEHatchNuclearHighPressureInput(
            "test_in",
            5,
            new String[0],
            null);
        GregTechAPI.METATILEENTITIES[ModMetaTileEntities.ID_NUCLEAR_HIGH_PRESSURE_OUTPUT] = new MTEHatchNuclearHighPressureOutput(
            "test_out",
            5,
            new String[0],
            null);
    }

    @Test
    void testDirectionalHatchInvariants() {
        MTEHatchNuclearHighPressureInput inputHatch = new MTEHatchNuclearHighPressureInput(
            "test_inlet",
            5,
            new String[0],
            new ITexture[0][0][0]);
        assertTrue(inputHatch.isPassageInlet(), "Input hatch must strictly report being a passage inlet");

        MTEHatchNuclearHighPressureOutput outputHatch = new MTEHatchNuclearHighPressureOutput(
            "test_outlet",
            5,
            new String[0],
            new ITexture[0][0][0]);
        assertFalse(outputHatch.isPassageInlet(), "Output hatch must strictly report NOT being a passage inlet");
    }

    @Test
    void testOppositeCoordNBTSerialization() {
        MTEHatchNuclearHighPressure hatch = new MTEHatchNuclearHighPressure(
            "test_hatch",
            5,
            new String[0],
            new ITexture[0][0][0]);
        hatch.mTemperature = 750.5;
        hatch.mCumulativeFluence = 123456L;

        IGregTechTileEntity mockOpp = Mockito.mock(IGregTechTileEntity.class);
        Mockito.when(mockOpp.getXCoord())
            .thenReturn(10);
        Mockito.when(mockOpp.getYCoord())
            .thenReturn((short) 64);
        Mockito.when(mockOpp.getZCoord())
            .thenReturn(-20);
        hatch.setOppositeHatch(mockOpp);

        NBTTagCompound nbt = new NBTTagCompound();
        hatch.saveNBTData(nbt);

        assertTrue(nbt.hasKey("oppositeCoord"));
        int[] savedCoords = nbt.getIntArray("oppositeCoord");
        assertArrayEquals(new int[] { 10, 64, -20 }, savedCoords);

        MTEHatchNuclearHighPressure loadedHatch = new MTEHatchNuclearHighPressure(
            "loaded_hatch",
            5,
            new String[0],
            new ITexture[0][0][0]);
        loadedHatch.loadNBTData(nbt);

        assertEquals(750.5, loadedHatch.getTemperature(), 1e-4);
        assertEquals(123456L, loadedHatch.mCumulativeFluence);
    }

    @Test
    void testInPlaceDirectionalConversion() {
        gregtech.api.metatileentity.BaseMetaTileEntity mockTe = Mockito
            .mock(gregtech.api.metatileentity.BaseMetaTileEntity.class);
        MTEHatchNuclearHighPressure legacyHatch = new MTEHatchNuclearHighPressure(
            "legacy",
            5,
            new String[0],
            new ITexture[0][0][0]);
        legacyHatch.mTemperature = 650.0;
        legacyHatch.mCumulativeFluence = 98765L;

        final Object[] currentMteHolder = new Object[] { legacyHatch };
        Mockito.doAnswer(inv -> currentMteHolder[0])
            .when(mockTe)
            .getMetaTileEntity();
        Mockito.doAnswer(inv -> {
            currentMteHolder[0] = inv.getArgument(0);
            return null;
        })
            .when(mockTe)
            .setMetaTileEntity(Mockito.any());

        // Convert to input
        MTEHatchNuclearHighPressure resultInput = MTEHatchNuclearHighPressure.convertToDirectionalHatch(mockTe, true);
        assertNotNull(resultInput);
        assertTrue(
            resultInput instanceof MTEHatchNuclearHighPressureInput,
            "Must convert to MTEHatchNuclearHighPressureInput");
        assertTrue(resultInput.isPassageInlet());
        assertEquals(650.0, resultInput.getTemperature(), 1e-4);
        assertEquals(98765L, resultInput.mCumulativeFluence);
        Mockito.verify(mockTe)
            .setMetaTileID((short) ModMetaTileEntities.ID_NUCLEAR_HIGH_PRESSURE_INPUT);

        // Convert to output
        MTEHatchNuclearHighPressure resultOutput = MTEHatchNuclearHighPressure.convertToDirectionalHatch(mockTe, false);
        assertNotNull(resultOutput);
        assertTrue(
            resultOutput instanceof MTEHatchNuclearHighPressureOutput,
            "Must convert to MTEHatchNuclearHighPressureOutput");
        assertFalse(resultOutput.isPassageInlet());
        assertEquals(650.0, resultOutput.getTemperature(), 1e-4);
        assertEquals(98765L, resultOutput.mCumulativeFluence);
        Mockito.verify(mockTe)
            .setMetaTileID((short) ModMetaTileEntities.ID_NUCLEAR_HIGH_PRESSURE_OUTPUT);
    }

    @Test
    void testColumnFallbackResolution() {
        World mockWorld = Mockito.mock(World.class);

        gregtech.api.metatileentity.BaseMetaTileEntity topTe = Mockito
            .mock(gregtech.api.metatileentity.BaseMetaTileEntity.class);
        Mockito.when(topTe.getWorld())
            .thenReturn(mockWorld);
        Mockito.when(topTe.getXCoord())
            .thenReturn(10);
        Mockito.when(topTe.getYCoord())
            .thenReturn((short) 68);
        Mockito.when(topTe.getZCoord())
            .thenReturn(20);

        gregtech.api.metatileentity.BaseMetaTileEntity botTe = Mockito
            .mock(gregtech.api.metatileentity.BaseMetaTileEntity.class);
        Mockito.when(botTe.getWorld())
            .thenReturn(mockWorld);
        Mockito.when(botTe.getXCoord())
            .thenReturn(10);
        Mockito.when(botTe.getYCoord())
            .thenReturn((short) 64);
        Mockito.when(botTe.getZCoord())
            .thenReturn(20);

        MTEHatchNuclearHighPressureOutput botHatch = new MTEHatchNuclearHighPressureOutput(
            "bot",
            5,
            new String[0],
            new ITexture[0][0][0]);
        Mockito.when(botTe.getMetaTileEntity())
            .thenReturn(botHatch);

        Mockito.when(mockWorld.getTileEntity(10, 64, 20))
            .thenReturn(botTe);

        MTEHatchNuclearHighPressureInput topHatch = new MTEHatchNuclearHighPressureInput(
            "top",
            5,
            new String[0],
            new ITexture[0][0][0]);
        topHatch.setBaseMetaTileEntity(topTe);
        topHatch.setOppositeHatch(null);

        // Call getOppositeHatchTile, column fallback checks y - 4 = 64
        IGregTechTileEntity resolvedOpp = topHatch.getOppositeHatchTile();
        assertSame(botTe, resolvedOpp, "Column fallback should find and link the bottom hatch at y-4");
    }

    @Test
    void testLoopSegmentAndEngineNBTSerialization() {
        LoopSegment segA = new LoopSegment("seg_a", 0, 60, 0, 10.0, 0.10, 0.000045, 1.5, 350.0, 1200.0, 150.0);
        segA.setInletPressureBar(50.0);
        segA.setOutletPressureBar(48.5);
        segA.setFlowFraction(0.75);
        segA.setFlowSizeWeight(2.0);
        segA.setCapacityLiters(500L);
        segA.setCustomVolumeM3(0.05);

        LoopSegment segB = new LoopSegment("seg_b", 1, 60, 0, 5.0, 0.10, 0.000045, 0.5, 350.0, 1200.0, 145.0);
        segB.addUpstreamSegment(segA);

        NBTTagCompound nbtB = new NBTTagCompound();
        LoopStateNbt.write(segB.toStateMap(), nbtB);

        List<String> upstreams = new ArrayList<>();
        LoopSegment restoredB = LoopSegment.fromStateMap(LoopStateNbt.read(nbtB), upstreams);
        assertEquals("seg_b", restoredB.getId());
        assertEquals(5.0, restoredB.getLength(), 1e-4);
        assertEquals(1, upstreams.size());
        assertEquals("seg_a", upstreams.get(0));

        // Test CoolantLoopEngine save and load
        CoolantLoopEngine engine = new CoolantLoopEngine(CoolantFluidProperty.HEAVY_WATER);
        engine.setVolumetricFlowRate(0.125);
        engine.addDissolvedGas("Tritium", 100L);
        engine.addDissolvedGas("Oxygen", 50L);
        engine.addSegment(segA);

        NBTTagCompound engNBT = new NBTTagCompound();
        LoopStateNbt.write(engine.exportState(), engNBT);

        CoolantLoopEngine restoredEngine = new CoolantLoopEngine();
        restoredEngine.importState(LoopStateNbt.read(engNBT));

        assertEquals(
            "heavywater",
            restoredEngine.getFluid()
                .getFluidName());
        assertEquals(0.125, restoredEngine.getVolumetricFlowRate(), 1e-5);
        assertEquals(100L, restoredEngine.getDissolvedGasAmount("Tritium"));
        assertEquals(50L, restoredEngine.getDissolvedGasAmount("Oxygen"));
        assertEquals(
            1,
            restoredEngine.getSegments()
                .size());
        assertEquals(
            "seg_a",
            restoredEngine.getSegments()
                .get(0)
                .getId());
    }
}

package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.block.BlockNuclearCasing;
import com.gtnewhorizons.modularnuclear.common.block.ModBlocks;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControl;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;
import com.gtnewhorizons.modularnuclear.common.textures.ModularNuclearTextures;

import gregtech.api.enums.Textures;
import gregtech.api.render.TextureFactory;

public class ProjectRedIntegrationTest {

    @BeforeAll
    static void initEnvironment() {
        Thread.currentThread()
            .setName("Server thread");
        try {
            cpw.mods.fml.common.Loader mockLoader = org.mockito.Mockito.mock(cpw.mods.fml.common.Loader.class);
            org.mockito.Mockito.when(mockLoader.getCallableCrashInformation())
                .thenReturn(org.mockito.Mockito.mock(cpw.mods.fml.common.ICrashCallable.class));
            java.lang.reflect.Field f = cpw.mods.fml.common.Loader.class.getDeclaredField("instance");
            f.setAccessible(true);
            if (f.get(null) == null) {
                f.set(null, mockLoader);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }

        try {
            net.minecraft.init.Bootstrap.func_151354_b();
        } catch (Throwable t) {
            t.printStackTrace();
        }

        try {
            java.lang.reflect.Field mcHome = cpw.mods.fml.relauncher.FMLInjectionData.class
                .getDeclaredField("minecraftHome");
            mcHome.setAccessible(true);
            mcHome.set(null, new java.io.File("."));
        } catch (Throwable ignored) {}

        try {
            if (gregtech.GTMod.proxy == null) {
                gregtech.GTMod.proxy = org.mockito.Mockito.mock(gregtech.common.GTProxy.class);
            }
        } catch (Throwable ignored) {}

        try {
            java.lang.reflect.Field sideField = cpw.mods.fml.relauncher.FMLRelaunchLog.class.getDeclaredField("side");
            sideField.setAccessible(true);
            sideField.set(null, cpw.mods.fml.relauncher.Side.SERVER);
        } catch (Throwable t) {
            t.printStackTrace();
        }

        try {
            if (ModBlocks.nuclearCasing == null) {
                ModBlocks.init();
            }
            ModularNuclearTextures.init();
            Textures.BlockIcons.setCasingTexture(
                (byte) BlockNuclearCasing.CASING_PAGE,
                (byte) (BlockNuclearCasing.CASING_ID + 112),
                TextureFactory.of(ModularNuclearTextures.MACHINE_CASING_NUCLEAR));
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Test
    public void testControlHatchBundledSignalMultiMode() {
        MTEHatchNuclearControl hatch = new MTEHatchNuclearControl("test_control_hatch", 1, new String[0], null);

        assertEquals(0, hatch.getOutputStrength());
        assertEquals(0, hatch.getFineOutputStrength());
        assertEquals(MTEHatchNuclearControl.BUNDLED_MODE_MULTI, hatch.getBundledChannelMode());

        byte[] allSignals = new byte[MTEHatchNuclearControl.MODE_COUNT];
        for (int i = 0; i < allSignals.length; i++) {
            allSignals[i] = (byte) ((i + 1) * 10);
        }

        hatch.setOutputs((byte) 8, 128, allSignals);

        assertEquals(8, hatch.getOutputStrength());
        assertEquals(128, hatch.getFineOutputStrength());

        byte[] bundled = hatch.getBundledSignal();
        assertNotNull(bundled);
        assertEquals(16, bundled.length);

        // Channels 0 to 14 should carry each mode's signal
        for (int i = 0; i < 15; i++) {
            assertEquals((byte) ((i + 1) * 10), bundled[i], "Channel " + i + " mismatch");
        }
        // Channel 15 carries the selected mode's fine signal
        assertEquals((byte) 128, bundled[15]);
    }

    @Test
    public void testControlHatchBundledSignalBroadcastMode() {
        MTEHatchNuclearControl hatch = new MTEHatchNuclearControl("test_control_hatch", 1, new String[0], null);
        hatch.setBundledChannelMode(MTEHatchNuclearControl.BUNDLED_MODE_BROADCAST);
        assertEquals(MTEHatchNuclearControl.BUNDLED_MODE_BROADCAST, hatch.getBundledChannelMode());

        byte[] allSignals = new byte[MTEHatchNuclearControl.MODE_COUNT];
        Arrays.fill(allSignals, (byte) 50);

        hatch.setOutputs((byte) 10, 200, allSignals);

        byte[] bundled = hatch.getBundledSignal();
        for (int i = 0; i < 16; i++) {
            assertEquals((byte) 200, bundled[i], "Channel " + i + " should be broadcast value");
        }
    }

    @Test
    public void testControlHatchBundledSignalSingleChannelMode() {
        MTEHatchNuclearControl hatch = new MTEHatchNuclearControl("test_control_hatch", 1, new String[0], null);
        // Mode 2 = Channel 0 (White), Mode 6 = Channel 4 (Yellow)
        hatch.setBundledChannelMode(6);

        hatch.setOutputs((byte) 15, 255, null);

        byte[] bundled = hatch.getBundledSignal();
        for (int i = 0; i < 16; i++) {
            if (i == 4) {
                assertEquals((byte) 255, bundled[i]);
            } else {
                assertEquals((byte) 0, bundled[i]);
            }
        }
    }

    @Test
    public void testControlHatchBundledModeCycling() {
        MTEHatchNuclearControl hatch = new MTEHatchNuclearControl("test_control_hatch", 1, new String[0], null);
        assertEquals(0, hatch.getBundledChannelMode());
        assertEquals("Multi-Channel (All)", MTEHatchNuclearControl.getBundledModeName(0));

        hatch.cycleBundledMode(1);
        assertEquals(1, hatch.getBundledChannelMode());
        assertEquals("Broadcast (All Colors)", MTEHatchNuclearControl.getBundledModeName(1));

        hatch.cycleBundledMode(1);
        assertEquals(2, hatch.getBundledChannelMode());
        assertEquals("Ch 0 (White)", MTEHatchNuclearControl.getBundledModeName(2));

        hatch.cycleBundledMode(15);
        assertEquals(17, hatch.getBundledChannelMode());
        assertEquals("Ch 15 (Black)", MTEHatchNuclearControl.getBundledModeName(17));

        // Wrap around
        hatch.cycleBundledMode(1);
        assertEquals(0, hatch.getBundledChannelMode());

        hatch.cycleBundledMode(-1);
        assertEquals(17, hatch.getBundledChannelMode());
    }

    @Test
    public void testControlRodInputChannelCycling() {
        MTEHatchNuclearControlRod rod = new MTEHatchNuclearControlRod("test_control_rod", 1, new String[0], null);
        assertEquals(MTEHatchNuclearControlRod.CHANNEL_AUTO, rod.mInputChannel);
        assertEquals("Auto (Strongest)", MTEHatchNuclearControlRod.getChannelName(rod.mInputChannel));

        rod.cycleInputChannel(1);
        assertEquals(0, rod.mInputChannel);
        assertEquals("Ch 0 (White)", MTEHatchNuclearControlRod.getChannelName(rod.mInputChannel));

        rod.cycleInputChannel(15);
        assertEquals(15, rod.mInputChannel);
        assertEquals("Ch 15 (Black)", MTEHatchNuclearControlRod.getChannelName(rod.mInputChannel));

        // Wrap around back to auto (-1)
        rod.cycleInputChannel(1);
        assertEquals(-1, rod.mInputChannel);
        assertEquals("Auto (Strongest)", MTEHatchNuclearControlRod.getChannelName(rod.mInputChannel));

        rod.cycleInputChannel(-1);
        assertEquals(15, rod.mInputChannel);
    }

    @Test
    public void testControlRodInsertionRatioWithFineSignal() {
        // Subclass to override getFineRedstoneSignal and getRedstoneSignal for testing
        class TestableControlRod extends MTEHatchNuclearControlRod {

            int fineSignal = -1;
            byte vanillaSignal = 0;

            TestableControlRod() {
                super("test_rod", 1, new String[0], null);
            }

            @Override
            public int getFineRedstoneSignal() {
                return fineSignal;
            }

            @Override
            public byte getRedstoneSignal() {
                return vanillaSignal;
            }
        }

        TestableControlRod rod = new TestableControlRod();

        // 1. Fallback to vanilla redstone when fine signal is -1
        rod.fineSignal = -1;
        rod.vanillaSignal = 0;
        assertEquals(0.0, rod.getInsertionRatio(), 1e-6);
        assertEquals(0, rod.getInsertionPercent());

        rod.vanillaSignal = 15;
        assertEquals(1.0, rod.getInsertionRatio(), 1e-6);
        assertEquals(100, rod.getInsertionPercent());

        rod.vanillaSignal = 6;
        assertEquals(6.0 / 15.0, rod.getInsertionRatio(), 1e-6);
        assertEquals(40, rod.getInsertionPercent());

        // 2. High-precision ProjectRed fine signal (0..255)
        rod.fineSignal = 0;
        assertEquals(0.0, rod.getInsertionRatio(), 1e-6);
        assertEquals(0, rod.getInsertionPercent());

        rod.fineSignal = 128;
        assertEquals(128.0 / 255.0, rod.getInsertionRatio(), 1e-6);
        assertEquals(50, rod.getInsertionPercent());

        rod.fineSignal = 255;
        assertEquals(1.0, rod.getInsertionRatio(), 1e-6);
        assertEquals(100, rod.getInsertionPercent());

        rod.fineSignal = 64;
        assertEquals(64.0 / 255.0, rod.getInsertionRatio(), 1e-6);
        assertEquals(25, rod.getInsertionPercent());

        // 3. SCRAM overrides all signals
        rod.setScram(true);
        assertEquals(1.0, rod.getInsertionRatio(), 1e-6);
        assertEquals(100, rod.getInsertionPercent());
    }

    @Test
    public void testReactorFineSignalCalculations() {
        MTENuclearReactor reactor = new MTENuclearReactor("test_reactor");
        reactor.mPipeTier = 1; // max temp = 1000 C

        // Test fraction and signal conversions
        // When telemetry has not run / empty grid: returns 0
        assertEquals(0.0, reactor.calculateFractionForMode(0));
        assertEquals((byte) 0, reactor.calculateSignalForMode(0));
        assertEquals(0, reactor.calculateFineSignalForMode(0));
    }

    @Test
    public void testTransmissionHandlerInteractionLogic() {
        com.gtnewhorizons.modularnuclear.common.projectred.ProjectRedTransmissionHandler handler =
            new com.gtnewhorizons.modularnuclear.common.projectred.ProjectRedTransmissionHandler();
        net.minecraft.world.World mockWorld = org.mockito.Mockito.mock(net.minecraft.world.World.class);
        gregtech.api.metatileentity.BaseMetaTileEntity mockGt =
            org.mockito.Mockito.mock(gregtech.api.metatileentity.BaseMetaTileEntity.class);

        org.mockito.Mockito.when(mockWorld.getTileEntity(10, 20, 30))
            .thenReturn(mockGt);

        MTEHatchNuclearControl hatch = new MTEHatchNuclearControl("test_hatch", 1, new String[0], null);
        org.mockito.Mockito.when(mockGt.getMetaTileEntity()).thenReturn(hatch);
        org.mockito.Mockito.when(mockGt.getFrontFacing())
            .thenReturn(net.minecraftforge.common.util.ForgeDirection.SOUTH);

        assertTrue(handler.isValidInteractionFor(mockWorld, 10, 20, 30));

        // Front facing is SOUTH (3), opposite is NORTH (2)
        assertTrue(handler.canConnectBundled(mockWorld, 10, 20, 30, net.minecraftforge.common.util.ForgeDirection.SOUTH.ordinal()));
        assertTrue(handler.canConnectBundled(mockWorld, 10, 20, 30, net.minecraftforge.common.util.ForgeDirection.NORTH.ordinal()));
        // Sides UP (1), DOWN (0) should not connect for control hatch
        assertFalse(handler.canConnectBundled(mockWorld, 10, 20, 30, net.minecraftforge.common.util.ForgeDirection.UP.ordinal()));
        assertFalse(handler.canConnectBundled(mockWorld, 10, 20, 30, net.minecraftforge.common.util.ForgeDirection.DOWN.ordinal()));

        // Signal return:
        hatch.setOutputs((byte) 5, 85, null);
        byte[] sig = handler.getBundledSignal(mockWorld, 10, 20, 30, net.minecraftforge.common.util.ForgeDirection.SOUTH.ordinal());
        assertNotNull(sig);
        assertEquals(16, sig.length);

        // Control Rod hatch connects on all sides
        MTEHatchNuclearControlRod rod = new MTEHatchNuclearControlRod("test_rod", 1, new String[0], null);
        org.mockito.Mockito.when(mockGt.getMetaTileEntity()).thenReturn(rod);
        assertTrue(handler.isValidInteractionFor(mockWorld, 10, 20, 30));
        assertTrue(handler.canConnectBundled(mockWorld, 10, 20, 30, net.minecraftforge.common.util.ForgeDirection.UP.ordinal()));
        assertTrue(handler.canConnectBundled(mockWorld, 10, 20, 30, net.minecraftforge.common.util.ForgeDirection.NORTH.ordinal()));
    }
}

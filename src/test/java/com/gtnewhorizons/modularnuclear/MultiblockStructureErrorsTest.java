package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.init.Bootstrap;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.gtnewhorizon.structurelib.StructureLib;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTEActinideCompounder;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTECoolantBath;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTECoolantPump;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTEPressurizedDegasser;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTEPressurizedFluidHeater;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTEPressurizedHeatExchanger;
import com.gtnewhorizons.modularnuclear.common.util.ClientText;

import cpw.mods.fml.common.ICrashCallable;
import cpw.mods.fml.common.Loader;
import gregtech.GTMod;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.shutdown.ShutDownReason;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gregtech.common.GTProxy;
import gregtech.common.data.GTPowerfailTracker;

public class MultiblockStructureErrorsTest {

    @BeforeAll
    static void initEnvironment() {
        Thread.currentThread()
            .setName("Server thread");
        try {
            Loader mockLoader = Mockito.mock(Loader.class);
            Mockito.when(mockLoader.getCallableCrashInformation())
                .thenReturn(Mockito.mock(ICrashCallable.class));
            new java.io.File("build/tmp/config").mkdirs();
            Mockito.when(mockLoader.getConfigDir())
                .thenReturn(new java.io.File("build/tmp/config"));
            Field f = Loader.class.getDeclaredField("instance");
            f.setAccessible(true);
            if (f.get(null) == null) {
                f.set(null, mockLoader);
            }
        } catch (Throwable ignored) {}

        try {
            Field mcHome = cpw.mods.fml.relauncher.FMLInjectionData.class.getDeclaredField("minecraftHome");
            mcHome.setAccessible(true);
            mcHome.set(null, new java.io.File("."));
        } catch (Throwable ignored) {}

        try {
            Bootstrap.func_151354_b();
        } catch (Throwable ignored) {}

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

        try {
            Field fGlassList = gregtech.api.util.GlassTier.class.getDeclaredField("glassList");
            fGlassList.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<org.apache.commons.lang3.tuple.Pair<net.minecraft.block.Block, Integer>> list = (List<org.apache.commons.lang3.tuple.Pair<net.minecraft.block.Block, Integer>>) fGlassList
                .get(null);
            if (list != null && list.isEmpty()) {
                list.add(org.apache.commons.lang3.tuple.Pair.of(net.minecraft.init.Blocks.glass, 0));
            }
        } catch (Throwable ignored) {}

        try {
            if (GTMod.proxy == null) {
                GTMod.proxy = Mockito.mock(GTProxy.class);
            }
            if (GTMod.proxy.powerfailTracker == null) {
                GTMod.proxy.powerfailTracker = Mockito.mock(GTPowerfailTracker.class);
            }
        } catch (Throwable ignored) {}
    }

    @Test
    void testPressurizedHeatExchangerStructureErrors() {
        MTEPressurizedHeatExchanger phe = new MTEPressurizedHeatExchanger("test_phe");
        List<StructureError> errors = new ArrayList<>();
        IGregTechTileEntity mockBase = Mockito.mock(IGregTechTileEntity.class);
        World mockWorld = Mockito.mock(World.class);
        Mockito.when(mockBase.getWorld())
            .thenReturn(mockWorld);
        Mockito.when(mockWorld.blockExists(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(true);
        phe.setBaseMetaTileEntity(mockBase);

        phe.checkMachine(mockBase, null, errors);
        assertFalse(errors.isEmpty(), "PHE should report structure errors when unformed");
    }

    @Test
    void testPressurizedFluidHeaterStructureErrors() {
        MTEPressurizedFluidHeater heater = new MTEPressurizedFluidHeater("test_heater");
        List<StructureError> errors = new ArrayList<>();
        IGregTechTileEntity mockBase = Mockito.mock(IGregTechTileEntity.class);
        World mockWorld = Mockito.mock(World.class);
        Mockito.when(mockBase.getWorld())
            .thenReturn(mockWorld);
        Mockito.when(mockWorld.blockExists(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(true);
        heater.setBaseMetaTileEntity(mockBase);

        heater.checkMachine(mockBase, null, errors);
        assertFalse(errors.isEmpty(), "Pressurized Fluid Heater should report structure errors when unformed");
    }

    @Test
    void testPressurizedDegasserStructureErrors() {
        MTEPressurizedDegasser degasser = new MTEPressurizedDegasser("test_degasser");
        List<StructureError> errors = new ArrayList<>();
        IGregTechTileEntity mockBase = Mockito.mock(IGregTechTileEntity.class);
        World mockWorld = Mockito.mock(World.class);
        Mockito.when(mockBase.getWorld())
            .thenReturn(mockWorld);
        Mockito.when(mockWorld.blockExists(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(true);
        degasser.setBaseMetaTileEntity(mockBase);

        degasser.checkMachine(mockBase, null, errors);
        assertFalse(errors.isEmpty(), "Degasser should report structure errors when unformed");
    }

    @Test
    void testCoolantBathStructureErrors() {
        MTECoolantBath bath = new MTECoolantBath("test_bath");
        List<StructureError> errors = new ArrayList<>();
        IGregTechTileEntity mockBase = Mockito.mock(IGregTechTileEntity.class);
        World mockWorld = Mockito.mock(World.class);
        Mockito.when(mockBase.getWorld())
            .thenReturn(mockWorld);
        Mockito.when(mockWorld.blockExists(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(true);
        bath.setBaseMetaTileEntity(mockBase);

        bath.checkMachine(mockBase, null, errors);
        assertFalse(errors.isEmpty(), "Coolant Bath should report structure errors when unformed");
    }

    @Test
    void testCoolantPumpStructureErrors() {
        MTECoolantPump pump = new MTECoolantPump("test_pump");
        List<StructureError> errors = new ArrayList<>();
        IGregTechTileEntity mockBase = Mockito.mock(IGregTechTileEntity.class);
        World mockWorld = Mockito.mock(World.class);
        Mockito.when(mockBase.getWorld())
            .thenReturn(mockWorld);
        Mockito.when(mockWorld.blockExists(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(true);
        pump.setBaseMetaTileEntity(mockBase);

        pump.checkMachine(mockBase, null, errors);
        assertFalse(errors.isEmpty(), "Coolant Pump should report structure errors when unformed (missing tank)");
        boolean hasTankError = errors.stream()
            .anyMatch(
                e -> e.getDisplayString() != null && (e.getDisplayString()
                    .contains("coolantloops.structure.error.pump.missing_or_mismatched_tank")
                    || e.getDisplayString()
                        .toLowerCase()
                        .contains("railcraft tank")));
        assertTrue(hasTankError, "Pump structure errors should include missing or mismatched tank");
    }

    @Test
    void testStructureErrorLocalizationKeysExist() {
        String[] keys = new String[] { "coolantloops.structure.error.missing_primary_inlet",
            "coolantloops.structure.error.missing_primary_outlet",
            "coolantloops.structure.error.single_amp_energy_required",
            "coolantloops.structure.error.sheetmetal_missing", "coolantloops.structure.error.sheetmetal_mixed",
            "coolantloops.structure.error.pump.missing_or_mismatched_tank",
            "coolantloops.structure.error.pump.corner_frame", "coolantloops.structure.error.pump.casing_req",
            "coolantloops.structure.error.pump.invalid_hatch", "coolantloops.structure.error.missing_suction_hatch",
            "coolantloops.structure.error.missing_discharge_hatch",
            "coolantloops.structure.error.duplicate_pump_hatches", "coolantloops.structure.error.heater_missing_coils",
            "coolantloops.structure.error.heater_missing_pipe_casing",
            "coolantloops.structure.error.heater_mixed_pipe_casing",
            "coolantloops.structure.error.heater_missing_energy", "coolantloops.structure.error.heater_multiple_energy",
            "coolantloops.structure.error.heater_no_laser",
            "coolantloops.structure.error.heater_energy_tier_too_high" };
        for (String key : keys) {
            String val = ClientText.get(key);
            assertNotNull(val, "Key should exist in lang file: " + key);
            assertNotEquals(key, val, "Key should be translated: " + key);
        }
    }

    /**
     * Like any GregTech multiblock (the Electric Blast Furnace, say), a machine shut down by an incomplete structure
     * stays off once the structure is whole again: only the player or a cover turns it back on.
     */
    @Test
    void testFixedStructureLeavesMachineOff() {
        assertStaysOffAfterFix(new MTECoolantPump("test_pump") {

            @Override
            public void checkMachine(IGregTechTileEntity b, ItemStack s, List<StructureError> e) {}
        });
        assertStaysOffAfterFix(new MTEPressurizedFluidHeater("test_heater") {

            @Override
            public void checkMachine(IGregTechTileEntity b, ItemStack s, List<StructureError> e) {}
        });
        assertStaysOffAfterFix(new MTEPressurizedHeatExchanger("test_phe") {

            @Override
            public void checkMachine(IGregTechTileEntity b, ItemStack s, List<StructureError> e) {}
        });
        assertStaysOffAfterFix(new MTEPressurizedDegasser("test_degasser") {

            @Override
            public void checkMachine(IGregTechTileEntity b, ItemStack s, List<StructureError> e) {}
        });
        assertStaysOffAfterFix(new MTECoolantBath("test_bath") {

            @Override
            public void checkMachine(IGregTechTileEntity b, ItemStack s, List<StructureError> e) {}
        });
        assertStaysOffAfterFix(new MTEActinideCompounder("test_compounder") {

            @Override
            public void checkMachine(IGregTechTileEntity b, ItemStack s, List<StructureError> e) {}
        });
    }

    private static void assertStaysOffAfterFix(MTEMultiBlockBase machine) {
        boolean[] works = { true };
        ShutDownReason[] reason = { ShutDownReasonRegistry.NONE };
        IGregTechTileEntity base = Mockito.mock(IGregTechTileEntity.class);
        Mockito.when(base.isServerSide())
            .thenReturn(true);
        Mockito.when(base.getMetaTileEntity())
            .thenReturn(machine);
        Mockito.when(base.isAllowedToWork())
            .thenAnswer(inv -> works[0]);
        Mockito.doAnswer(inv -> works[0] = false)
            .when(base)
            .disableWorking();
        Mockito.doAnswer(inv -> works[0] = true)
            .when(base)
            .enableWorking();
        Mockito.doAnswer(inv -> reason[0] = inv.getArgument(0))
            .when(base)
            .setShutDownReason(Mockito.any());
        machine.setBaseMetaTileEntity(base);

        machine.stopMachine(ShutDownReasonRegistry.STRUCTURE_INCOMPLETE);
        assertFalse(works[0], machine.mName + " must shut down on an incomplete structure");

        assertTrue(machine.checkStructure(true, base), machine.mName + " structure check must pass");
        assertFalse(works[0], machine.mName + " must stay off after its structure is fixed");
        assertEquals(ShutDownReasonRegistry.STRUCTURE_INCOMPLETE, reason[0], machine.mName + " shutdown reason");
    }

    /**
     * The MPTR has no on/off state: GregTech stops (an incomplete structure, maintenance) record no shutdown, and a
     * soft mallet or cover cannot switch it off. Whether it runs is up to its structure alone.
     */
    @Test
    void testReactorHasNoOnOffState() {
        MTENuclearReactor reactor = new MTENuclearReactor("test_reactor");
        boolean[] works = { true };
        boolean[] shutdown = { false };
        IGregTechTileEntity base = Mockito.mock(IGregTechTileEntity.class);
        Mockito.when(base.isServerSide())
            .thenReturn(true);
        Mockito.when(base.getMetaTileEntity())
            .thenReturn(reactor);
        Mockito.when(base.isAllowedToWork())
            .thenAnswer(inv -> works[0]);
        Mockito.doAnswer(inv -> {
            works[0] = false;
            reactor.onDisableWorking();
            return null;
        })
            .when(base)
            .disableWorking();
        Mockito.doAnswer(inv -> works[0] = true)
            .when(base)
            .enableWorking();
        Mockito.doAnswer(inv -> shutdown[0] = inv.getArgument(0))
            .when(base)
            .setShutdownStatus(Mockito.anyBoolean());
        reactor.setBaseMetaTileEntity(base);

        reactor.stopMachine(ShutDownReasonRegistry.STRUCTURE_INCOMPLETE);
        assertTrue(works[0], "An incomplete structure must not switch the reactor off");
        assertFalse(shutdown[0], "An incomplete structure must not record a shutdown");

        base.disableWorking();
        assertTrue(works[0], "A soft mallet or cover must not switch the reactor off");
        assertFalse(reactor.shouldDisplayShutDownReason(), "The reactor GUI shows no shutdown reason");
    }
}

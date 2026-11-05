package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.gtnewhorizons.modularnuclear.common.config.CoolantLoopDataRegistry;
import com.gtnewhorizons.modularnuclear.common.config.data.CoolantDeviceData;
import com.gtnewhorizons.modularnuclear.common.coolantloop.ConvectiveHeatTransferModel;
import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantFluidProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopSegment;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTECoolantBath;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.util.CoolantBathSlot;

/**
 * Test suite verifying:
 * 1. Coolant Bath slot auto-ejection rules for coolant cells and ingots.
 * 2. Convective thermal transfer physics (Newton's cooling & heating law).
 * 3. Flow resistance (minor loss K-factor) dynamic scaling: K = 1.0 + 0.1 * N_active.
 * 4. Structure dimensions and slot counts across glass tiers (EV, IV, LuV).
 * 5. NBT serialization and state restoration.
 */
public class CoolantBathTest {

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

        gregtech.api.GregTechAPI.sBlockCasings1 = net.minecraft.init.Blocks.stone;
        gregtech.api.GregTechAPI.sBlockCasings2 = net.minecraft.init.Blocks.stone;
        gregtech.api.GregTechAPI.sBlockCasings4 = net.minecraft.init.Blocks.stone;
        gregtech.api.GregTechAPI.sBlockCasings10 = net.minecraft.init.Blocks.stone;

        try {
            java.lang.reflect.Field fBlockHint = com.gtnewhorizon.structurelib.StructureLib.class
                .getDeclaredField("blockHint");
            fBlockHint.setAccessible(true);
            if (fBlockHint.get(null) == null) {
                fBlockHint.set(null, net.minecraft.init.Blocks.stone);
            }
        } catch (Throwable ignored) {}
    }

    @Test
    public void testCoolantCellAutoEjectionRules() {
        ItemStack cellItem = new ItemStack(Items.iron_ingot); // Mock item representing cell
        int maxDmg = 10000;

        // Case 1: Fully-cold insertion (initialDamage = 0 or 1).
        // Must NOT eject immediately at insertion.
        // Must ONLY eject when reaching fully-hot (maxDamage).
        cellItem.setItemDamage(0);
        CoolantBathSlot coldInserted = new CoolantBathSlot(
            cellItem.copy(),
            20.0,
            1000.0,
            0,
            maxDmg,
            10000.0,
            0.25,
            true,
            false,
            false,
            null);

        assertFalse(coldInserted.isAutoEjectReady(), "Cold inserted cell must not eject at insertion state");

        // Intermediate damage: should NOT eject
        coldInserted.itemStack.setItemDamage(5000);
        assertFalse(coldInserted.isAutoEjectReady(), "Cold inserted cell must not eject at intermediate damage");

        // Fully hot: MUST eject
        coldInserted.itemStack.setItemDamage(maxDmg);
        assertTrue(coldInserted.isAutoEjectReady(), "Cold inserted cell must eject when reaching maximum heat");

        // Case 2: Fully-hot insertion (initialDamage = 10000).
        // Must NOT eject immediately at insertion.
        // Must ONLY eject when reaching fully-cold (<= 1 damage).
        cellItem.setItemDamage(maxDmg);
        CoolantBathSlot hotInserted = new CoolantBathSlot(
            cellItem.copy(),
            1000.0,
            20.0,
            maxDmg,
            maxDmg,
            10000.0,
            0.25,
            true,
            false,
            true,
            null);

        assertFalse(hotInserted.isAutoEjectReady(), "Hot inserted cell must not eject at insertion state");

        // Intermediate damage: should NOT eject
        hotInserted.itemStack.setItemDamage(4000);
        assertFalse(hotInserted.isAutoEjectReady(), "Hot inserted cell must not eject at intermediate damage");

        // Fully cold: MUST eject
        hotInserted.itemStack.setItemDamage(1);
        assertTrue(hotInserted.isAutoEjectReady(), "Hot inserted cell must eject when reaching fully cold state");

        // Case 3: Intermediate insertion (initialDamage = 5000).
        // Ejects at either extreme (cold or hot), but NOT at initial state.
        cellItem.setItemDamage(5000);
        CoolantBathSlot intermediateInserted = new CoolantBathSlot(
            cellItem.copy(),
            500.0,
            20.0,
            5000,
            maxDmg,
            10000.0,
            0.25,
            true,
            false,
            true,
            null);

        assertFalse(intermediateInserted.isAutoEjectReady(), "Intermediate cell must not eject at insertion state");

        intermediateInserted.itemStack.setItemDamage(maxDmg);
        assertTrue(intermediateInserted.isAutoEjectReady(), "Intermediate cell must eject when fully hot");

        intermediateInserted.itemStack.setItemDamage(1);
        assertTrue(intermediateInserted.isAutoEjectReady(), "Intermediate cell must eject when fully cold");
    }

    @Test
    public void testIngotCoolingPhysicsAndAutoEjection() {
        ItemStack hotIngot = new ItemStack(Items.gold_ingot);
        ItemStack coldIngot = new ItemStack(Items.iron_ingot);

        double initialHotTemp = 1500.0;
        double targetColdTemp = 20.0;
        double heatCapacity = 1000.0; // J/K
        double surfaceArea = 0.15; // m^2

        CoolantBathSlot slot = new CoolantBathSlot(
            hotIngot.copy(),
            initialHotTemp,
            targetColdTemp,
            0,
            1000,
            heatCapacity,
            surfaceArea,
            false,
            true,
            true,
            coldIngot.copy());

        assertEquals(0.0, slot.getProgressFraction(), 1e-4, "Progress should initially be 0.0");
        assertFalse(slot.isAutoEjectReady(), "Should not auto-eject when hot");

        // Simulate 10 seconds of cooling in 20 C coolant at 2 m/s velocity
        double coolantTemp = 20.0;
        double velocity = 2.0;
        ConvectiveHeatTransferModel conv = ConvectiveHeatTransferModel.DEFAULT;

        for (int tick = 0; tick < 200; tick++) {
            double dt = 0.05; // 1 tick = 0.05s
            double qDotWatts = conv.computeHeatTransferRate(velocity, slot.surfaceArea, slot.currentTemp, coolantTemp);
            double deltaEJoules = qDotWatts * dt;
            slot.currentTemp -= deltaEJoules / slot.heatCapacity;
            slot.updateItemDamage();
        }

        assertTrue(slot.currentTemp < initialHotTemp, "Temperature must have decreased");
        assertTrue(slot.getProgressFraction() > 0.0, "Progress must have increased");

        // Just above its target it has not given off all its heat yet; at it, it has
        slot.currentTemp = 21.0;
        slot.updateItemDamage();
        assertFalse(slot.isAutoEjectReady(), "a degree short of its target");
        slot.currentTemp = targetColdTemp;
        slot.updateItemDamage();
        assertTrue(slot.isAutoEjectReady(), "Must be ready to auto-eject once cooled down to target temperature");
        assertEquals(
            coldIngot.getItem(),
            slot.getCompletedOutput()
                .getItem(),
            "Must output the cooled ingot");
    }

    @Test
    public void testIngotHeatingPhysicsAndAutoEjection() {
        ItemStack coldIngot = new ItemStack(Items.iron_ingot);
        ItemStack hotIngot = new ItemStack(Items.gold_ingot);

        double initialColdTemp = 20.0;
        double targetHotTemp = 1200.0;
        double heatCapacity = 800.0; // J/K
        double surfaceArea = 0.15; // m^2

        CoolantBathSlot slot = new CoolantBathSlot(
            coldIngot.copy(),
            initialColdTemp,
            targetHotTemp,
            0,
            1000,
            heatCapacity,
            surfaceArea,
            false,
            true,
            false,
            hotIngot.copy());

        assertEquals(0.0, slot.getProgressFraction(), 1e-4);
        assertFalse(slot.isAutoEjectReady());

        // A degree short of its target it has not taken in all its heat yet; at it, it has
        slot.currentTemp = targetHotTemp - 1.0;
        slot.updateItemDamage();
        assertFalse(slot.isAutoEjectReady(), "a degree short of its target");
        slot.currentTemp = targetHotTemp;
        slot.updateItemDamage();
        assertTrue(slot.isAutoEjectReady(), "Must auto-eject once heated up to target blast furnace temperature");
        assertEquals(
            hotIngot.getItem(),
            slot.getCompletedOutput()
                .getItem(),
            "Must output the heated hot ingot");
    }

    @Test
    public void testSlotNBTSerialization() {
        ItemStack stack = new ItemStack(Items.diamond, 1, 5);
        ItemStack outStack = new ItemStack(Items.emerald, 1, 0);

        CoolantBathSlot original = new CoolantBathSlot(
            stack,
            850.5,
            20.0,
            10,
            1000,
            1250.0,
            0.2,
            false,
            true,
            true,
            outStack);

        NBTTagCompound tag = original.writeToNBT();
        assertNotNull(tag);

        CoolantBathSlot restored = CoolantBathSlot.readFromNBT(tag);
        assertNotNull(restored);
        assertEquals(original.currentTemp, restored.currentTemp, 1e-4);
        assertEquals(original.targetTemp, restored.targetTemp, 1e-4);
        assertEquals(original.startTemp, restored.startTemp, 1e-4);
        assertEquals(original.initialDamage, restored.initialDamage);
        assertEquals(original.maxDamage, restored.maxDamage);
        assertEquals(original.heatCapacity, restored.heatCapacity, 1e-4);
        assertEquals(original.surfaceArea, restored.surfaceArea, 1e-4);
        assertEquals(original.isCoolantCell, restored.isCoolantCell);
        assertEquals(original.isIngot, restored.isIngot);
        assertEquals(original.cooling, restored.cooling);
        assertNotNull(restored.transformedOutput);
        assertEquals(Items.emerald, restored.transformedOutput.getItem());
    }

    @Test
    public void testDynamicMinorLossKFactor() {
        MTECoolantBath bath = new MTECoolantBath("test.bath");
        bath.mSlots = new CoolantBathSlot[15];

        // 0 active items -> base K = 1.0
        assertEquals(1.0, bath.getMinorLossK(), 1e-4, "Base K with 0 active items must be 1.0");

        // Add 3 active items -> K = 1.0 + 0.1 * 3 = 1.3
        bath.mSlots[0] = new CoolantBathSlot(
            new ItemStack(Items.iron_ingot),
            1000,
            20,
            0,
            1000,
            500,
            0.1,
            false,
            true,
            true,
            null);
        bath.mSlots[1] = new CoolantBathSlot(
            new ItemStack(Items.iron_ingot),
            1000,
            20,
            0,
            1000,
            500,
            0.1,
            false,
            true,
            true,
            null);
        bath.mSlots[2] = new CoolantBathSlot(
            new ItemStack(Items.iron_ingot),
            1000,
            20,
            0,
            1000,
            500,
            0.1,
            false,
            true,
            true,
            null);

        assertEquals(3, bath.getActiveItemCount());
        assertEquals(4.0, bath.getMinorLossK(), 1e-4, "K with 3 active items must be 4.0 (1.0 + 1.0 * 3)");

        // Add 12 more active items -> 15 active items -> K = 1.0 + 1.0 * 15 = 16.0
        for (int i = 3; i < 15; i++) {
            bath.mSlots[i] = new CoolantBathSlot(
                new ItemStack(Items.iron_ingot),
                1000,
                20,
                0,
                1000,
                500,
                0.1,
                false,
                true,
                true,
                null);
        }
        assertEquals(15, bath.getActiveItemCount());
        assertEquals(16.0, bath.getMinorLossK(), 1e-4, "K with 15 active items must be 16.0 (1.0 + 1.0 * 15)");
    }

    @Test
    public void testStructureDimensionsAndSlotCountsByTier() {
        MTECoolantBath bath = new MTECoolantBath("test.bath");

        // EV Glass (Tier 4 / structure tier 1): 5x7x3 footprint -> 3x5 interior = 15 slots
        bath.mStructureTier = 1;
        bath.mInteriorWidth = 3;
        bath.mInteriorLength = 5;
        assertEquals(15, bath.getTotalSlotCount(), "EV glass tier must provide 15 virtual slots (3x5)");
        assertEquals(15000L, bath.getFillVolumeLiters(), "15 slots must have 15,000 L fill volume (1000 L/slot)");

        // IV Glass (Tier 5 / structure tier 2): 7x9x3 footprint -> 5x7 interior = 35 slots
        bath.mStructureTier = 2;
        bath.mInteriorWidth = 5;
        bath.mInteriorLength = 7;
        assertEquals(35, bath.getTotalSlotCount(), "IV glass tier must provide 35 virtual slots (5x7)");
        assertEquals(35000L, bath.getFillVolumeLiters(), "35 slots must have 35,000 L fill volume (1000 L/slot)");

        // LuV Glass (Tier 6 / structure tier 3): 9x11x3 footprint -> 7x9 interior = 63 slots
        bath.mStructureTier = 3;
        bath.mInteriorWidth = 7;
        bath.mInteriorLength = 9;
        assertEquals(63, bath.getTotalSlotCount(), "LuV glass tier must provide 63 virtual slots (7x9)");
        assertEquals(63000L, bath.getFillVolumeLiters(), "63 slots must have 63,000 L fill volume (1000 L/slot)");

        // Custom slot array (e.g. 12 slots)
        bath.mSlots = new CoolantBathSlot[12];
        assertEquals(12, bath.getTotalSlotCount(), "Explicit 12-slot array must report 12 slots");
        assertEquals(12000L, bath.getFillVolumeLiters(), "12 slots must have 12,000 L fill volume");
    }

    @Test
    public void testCoolantBathStructureCoordinates() {
        MTECoolantBath bath = new MTECoolantBath("test.bath");
        gregtech.api.interfaces.tileentity.IGregTechTileEntity mockBase = Mockito
            .mock(gregtech.api.interfaces.tileentity.IGregTechTileEntity.class);
        net.minecraft.world.World mockWorld = Mockito.mock(net.minecraft.world.World.class);
        Mockito.when(mockWorld.blockExists(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()))
            .thenReturn(true);
        Mockito.when(mockBase.getWorld())
            .thenReturn(mockWorld);
        Mockito.when(mockBase.getXCoord())
            .thenReturn(100);
        Mockito.when(mockBase.getYCoord())
            .thenReturn((short) 50);
        Mockito.when(mockBase.getZCoord())
            .thenReturn(200);
        Mockito.when(mockBase.getFrontFacing())
            .thenReturn(net.minecraftforge.common.util.ForgeDirection.NORTH);
        bath.setBaseMetaTileEntity(mockBase);

        // Verify tier 1 (5x7x3)
        java.util.concurrent.atomic.AtomicInteger minY = new java.util.concurrent.atomic.AtomicInteger(
            Integer.MAX_VALUE);
        java.util.concurrent.atomic.AtomicInteger maxY = new java.util.concurrent.atomic.AtomicInteger(
            Integer.MIN_VALUE);
        java.util.concurrent.atomic.AtomicInteger minZ = new java.util.concurrent.atomic.AtomicInteger(
            Integer.MAX_VALUE);
        java.util.concurrent.atomic.AtomicInteger maxZ = new java.util.concurrent.atomic.AtomicInteger(
            Integer.MIN_VALUE);
        java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger(0);

        bath.getStructureDefinition()
            .iterate(
                "bath_5x7x3",
                mockWorld,
                bath.getExtendedFacing(),
                100,
                50,
                200,
                2,
                2,
                0,
                (element, world, x, y, z, a, b, c) -> {
                    count.incrementAndGet();
                    minY.updateAndGet(curr -> Math.min(curr, y));
                    maxY.updateAndGet(curr -> Math.max(curr, y));
                    minZ.updateAndGet(curr -> Math.min(curr, z));
                    maxZ.updateAndGet(curr -> Math.max(curr, z));
                    return true;
                });

        assertEquals(50, minY.get(), "Bottom layer must be at controller level y=50");
        assertEquals(52, maxY.get(), "Glass roof must be at top level y=52 (3 blocks tall: 50, 51, 52)");
        assertEquals(200, minZ.get(), "Front narrow end must be at controller level z=200");
        assertEquals(206, maxZ.get(), "Back narrow end must be at z=206 (7 blocks long: 200 to 206)");

        // Verify slot world coordinates for tier 1
        bath.mInteriorWidth = 3;
        bath.mInteriorLength = 5;
        for (int i = 0; i < 15; i++) {
            int[] pos = bath.getSlotWorldCoordinates(i);
            assertNotNull(pos, "Slot coordinate must not be null for slot " + i);
            assertEquals(51, pos[1], "Slot " + i + " must be in the middle layer at y=51");
            assertTrue(pos[0] >= 99 && pos[0] <= 101, "Slot " + i + " X must be within interior width (99..101)");
            assertTrue(pos[2] >= 201 && pos[2] <= 205, "Slot " + i + " Z must be within interior length (201..205)");
        }
    }

    @Test
    public void testLoopSegmentCoupling() {
        MTECoolantBath bath = new MTECoolantBath("test.bath");
        bath.mSlots = new CoolantBathSlot[15];

        LoopSegment segment = new LoopSegment("test_bath_seg", 1.0, 0.05, 10.0, 0.0, 20.0, 100.0, 100.0);
        segment.setCurrentTemperatureCelsius(25.0);

        // Process thermal exchange with empty bath
        bath.processThermalExchange(0.01, 1.0, CoolantFluidProperty.DISTILLED_WATER, segment);
        assertEquals(1.0, segment.getMinorLossK(), 1e-4, "Empty bath must set segment minor loss K to 1.0");

        // Add 10 active items to bath -> K = 1.0 + 1.0 * 10 = 11.0
        for (int i = 0; i < 10; i++) {
            bath.mSlots[i] = new CoolantBathSlot(
                new ItemStack(Items.iron_ingot),
                1000,
                20,
                0,
                1000,
                500,
                0.1,
                false,
                true,
                true,
                null);
        }

        bath.processThermalExchange(0.01, 1.0, CoolantFluidProperty.DISTILLED_WATER, segment);
        assertEquals(11.0, segment.getMinorLossK(), 1e-4, "10 active items must set segment minor loss K to 11.0");
    }

    @Test
    public void testCoolantBathYAMLConfiguration() {
        CoolantDeviceData devData = new CoolantDeviceData();
        devData.getBath().fillVolumePerSlotLiters = 500L;
        devData.getBath().baseMinorLossK = 2.5;
        devData.getBath().itemMinorLossK = 0.5;

        // Verify serialization / deserialization round-trip
        java.util.Map<String, Object> map = devData.toMap();
        CoolantDeviceData loaded = CoolantDeviceData.fromMap(map);
        assertEquals(500L, loaded.getBath().fillVolumePerSlotLiters);
        assertEquals(2.5, loaded.getBath().baseMinorLossK, 1e-4);
        assertEquals(0.5, loaded.getBath().itemMinorLossK, 1e-4);

        // Set as active device data
        CoolantLoopDataRegistry.setDeviceData(loaded);
        try {
            MTECoolantBath bath = new MTECoolantBath("test.bath");
            bath.mSlots = new CoolantBathSlot[12];
            assertEquals(
                12 * 500L,
                bath.getFillVolumeLiters(),
                "12 slots with 500 L/slot must have 6,000 L fill volume");

            // Empty bath
            assertEquals(2.5, bath.getMinorLossK(), 1e-4, "Base K must be 2.5 from config");

            // 3 active items -> 2.5 + 0.5 * 3 = 4.0
            bath.mSlots[0] = new CoolantBathSlot(
                new ItemStack(Items.iron_ingot),
                1000,
                20,
                0,
                1000,
                500,
                0.1,
                false,
                true,
                true,
                null);
            bath.mSlots[1] = new CoolantBathSlot(
                new ItemStack(Items.iron_ingot),
                1000,
                20,
                0,
                1000,
                500,
                0.1,
                false,
                true,
                true,
                null);
            bath.mSlots[2] = new CoolantBathSlot(
                new ItemStack(Items.iron_ingot),
                1000,
                20,
                0,
                1000,
                500,
                0.1,
                false,
                true,
                true,
                null);
            assertEquals(4.0, bath.getMinorLossK(), 1e-4, "3 active items must give K = 2.5 + 0.5 * 3 = 4.0");
        } finally {
            // Restore default
            CoolantLoopDataRegistry.setDeviceData(new CoolantDeviceData());
        }
    }

    @Test
    public void testCryogenicCellLatentHeatCoolingAndAutoEjection() {
        ItemStack liquidNitrogenCell = new ItemStack(Items.apple);
        ItemStack nitrogenGasCell = new ItemStack(Items.baked_potato);

        double boilingPoint = -196.0;
        double latentHeat = 160000.0; // 160 kJ
        double surfaceArea = 0.25;

        CoolantBathSlot cryoSlot = new CoolantBathSlot(
            liquidNitrogenCell.copy(),
            boilingPoint,
            boilingPoint,
            0,
            1000,
            latentHeat,
            surfaceArea,
            false,
            false,
            false,
            nitrogenGasCell.copy(),
            true);

        assertTrue(cryoSlot.isCryoCell);
        assertFalse(cryoSlot.isAutoEjectReady(), "Cryo cell must not be ready to eject initially");
        assertEquals(0.0, cryoSlot.getProgressFraction(), 1e-4);

        // Put into bath
        MTECoolantBath bath = new MTECoolantBath("test.bath");
        bath.mSlots = new CoolantBathSlot[15];
        bath.mSlots[0] = cryoSlot;

        // Loop segment with IC2 coolant at 20 C
        LoopSegment segment = new LoopSegment("cryo_seg", 1.0, 0.05, 0.00005, 0.0, 100.0, 1200.0, 20.0);
        double flowRate = 0.02; // 20 L/s

        // Process thermal exchange for several ticks
        double prevCoolantTemp = segment.getCurrentTemperatureCelsius();
        bath.processThermalExchange(flowRate, 1.0, CoolantFluidProperty.IC2_COOLANT, segment);

        assertTrue(
            segment.getCurrentTemperatureCelsius() < prevCoolantTemp,
            "Coolant temperature must decrease when exchanging heat with -196 C cryo cell");
        assertTrue(cryoSlot.absorbedHeatJoules > 0.0, "Cryo cell must absorb latent heat");
        assertEquals(
            boilingPoint,
            cryoSlot.currentTemp,
            1e-4,
            "Cryo cell temperature remains at boiling point during phase change");

        // Manually absorb full latent heat (160 kJ)
        cryoSlot.absorbedHeatJoules = latentHeat;
        cryoSlot.updateItemDamage();

        assertTrue(
            cryoSlot.isAutoEjectReady(),
            "Cryo cell must be ready to auto-eject once 100% latent heat is absorbed");
        assertEquals(1.0, cryoSlot.getProgressFraction(), 1e-4);
        assertNotNull(cryoSlot.getCompletedOutput());
        assertEquals(
            nitrogenGasCell.getItem(),
            cryoSlot.getCompletedOutput()
                .getItem());
    }

    @Test
    public void testCryoSlotNBTSerialization() {
        ItemStack inCell = new ItemStack(Items.arrow);
        ItemStack outCell = new ItemStack(Items.feather);

        CoolantBathSlot original = new CoolantBathSlot(
            inCell,
            -196.0,
            -196.0,
            0,
            1000,
            160000.0,
            0.25,
            false,
            false,
            false,
            outCell,
            true);
        original.absorbedHeatJoules = 80000.0;
        original.updateItemDamage();

        NBTTagCompound tag = original.writeToNBT();
        assertNotNull(tag);

        CoolantBathSlot restored = CoolantBathSlot.readFromNBT(tag);
        assertNotNull(restored);
        assertTrue(restored.isCryoCell);
        assertEquals(80000.0, restored.absorbedHeatJoules, 1e-4);
        assertEquals(original.currentTemp, restored.currentTemp, 1e-4);
        assertEquals(original.targetTemp, restored.targetTemp, 1e-4);
        assertEquals(original.heatCapacity, restored.heatCapacity, 1e-4);
        assertNotNull(restored.transformedOutput);
        assertEquals(Items.feather, restored.transformedOutput.getItem());
        assertEquals(0.5, restored.getProgressFraction(), 1e-2);
    }

    @Test
    public void testConfiguredConvectiveModel() {
        MTECoolantBath bath = new MTECoolantBath("test.bath.conv");
        assertNotNull(bath.getConvectiveModel(), "Convective model must not be null");

        // Custom convective model with double the heat transfer
        ConvectiveHeatTransferModel customModel = new ConvectiveHeatTransferModel(2.0, 10.0, 1000.0, 2000.0, 50000.0);
        bath.setConvectiveModel(customModel);
        assertSame(customModel, bath.getConvectiveModel(), "Bath must use explicitly configured convective model");

        // Set up single slot with hot item
        bath.mSlots = new CoolantBathSlot[15];
        double initialTemp = 500.0;
        double heatCapacity = 1000.0;
        double surfaceArea = 0.5;
        bath.mSlots[0] = new CoolantBathSlot(
            new ItemStack(Items.iron_ingot),
            initialTemp,
            20.0,
            0,
            1000,
            heatCapacity,
            surfaceArea,
            false,
            true,
            true,
            null);

        LoopSegment seg = new LoopSegment("test_conv_seg", 1.0, 0.05, 10.0, 0.0, 20.0, 100.0, 100.0);
        seg.setCurrentTemperatureCelsius(20.0);

        double flowRateM3s = 0.05; // 1 m/s velocity through 0.05 m^2 segment
        double dt = 1.0;
        bath.processThermalExchange(flowRateM3s, dt, CoolantFluidProperty.WATER, seg);

        // Verify temperature reduction matches custom model exactly
        double velocity = flowRateM3s / seg.getArea();
        double expectedQDotWatts = customModel.computeHeatTransferRate(velocity, surfaceArea, initialTemp, 20.0);
        double expectedDeltaEJoules = expectedQDotWatts * dt;
        double expectedFinalTemp = initialTemp - (expectedDeltaEJoules / heatCapacity);

        assertEquals(
            expectedFinalTemp,
            bath.mSlots[0].currentTemp,
            1e-4,
            "Slot temperature must match heat transfer calculated with configured convective model");
    }

    /** Newton cooling in coolant held at coolantTemp for the given seconds, as the bath steps it. */
    private static void soak(CoolantBathSlot slot, double coolantTemp, double seconds) {
        ConvectiveHeatTransferModel conv = ConvectiveHeatTransferModel.DEFAULT;
        for (int tick = 0; tick < seconds * 20; tick++) {
            double q = conv.computeHeatTransferRate(2.0, slot.surfaceArea, slot.currentTemp, coolantTemp);
            slot.currentTemp -= q * 0.05 / slot.heatCapacity;
            slot.updateItemDamage();
        }
    }

    private static CoolantBathSlot hotIngot(double cooledTemp) {
        return new CoolantBathSlot(
            new ItemStack(Items.gold_ingot),
            1500.0,
            cooledTemp,
            0,
            1000,
            1000.0,
            0.15,
            false,
            true,
            true,
            new ItemStack(Items.iron_ingot));
    }

    @Test
    void testHotIngotCoolsIntoItsIngotOnceSafeToTouch() {
        assertEquals(45.0, CoolantDeviceData.CoolantBathData.TOUCH_SAFE_TEMP_C, 0.0);
        assertEquals(
            CoolantDeviceData.CoolantBathData.TOUCH_SAFE_TEMP_C,
            new CoolantDeviceData.CoolantBathData().ingotCooledTempC,
            0.0);
        // A 30 C loop takes it below 45 C: it leaves as its ingot
        CoolantBathSlot slot = hotIngot(45.0);
        assertFalse(slot.isAutoEjectReady());
        soak(slot, 30.0, 1200);
        assertTrue(slot.currentTemp <= 45.0, "cooled to " + slot.currentTemp);
        assertTrue(slot.isAutoEjectReady());
        ItemStack out = slot.getCompletedOutput();
        assertEquals(Items.iron_ingot, out.getItem());
        assertFalse(out.hasTagCompound(), "no bath tags on what leaves");
    }

    @Test
    void testIngotInATooWarmLoopStaysInTheBath() {
        // A 60 C loop: the ingot settles at 60, never giving off the heat between there and 45, and stays
        CoolantBathSlot slot = hotIngot(45.0);
        soak(slot, 60.0, 2400);
        assertTrue(Math.abs(slot.currentTemp - 60.0) < 0.5, "settled at " + slot.currentTemp);
        assertFalse(slot.isAutoEjectReady(), "settled, but not cooled");
        // Taken out by hand, it is still the hot ingot
        ItemStack out = slot.getCompletedOutput();
        assertEquals(Items.gold_ingot, out.getItem(), "not cooled: still the hot ingot");
        assertFalse(out.hasTagCompound(), "no bath tags on what leaves");
    }

    @Test
    void testHotCellStaysUntilItHasGivenOffAllItsHeat() {
        // A 10k cell at 90% heat (about 900 C): a 40 C loop leaves it holding some heat, so it stays; below 20 C it
        // gives off the rest and leaves empty
        ItemStack cell = new ItemStack(Items.iron_sword);
        int max = cell.getMaxDamage();
        cell.setItemDamage(max * 9 / 10);
        double temp = 20.0 + (double) (cell.getItemDamage() - 1) / (max - 1) * 980.0;
        CoolantBathSlot slot = new CoolantBathSlot(
            cell,
            temp,
            20.0,
            cell.getItemDamage(),
            max,
            max,
            0.25,
            true,
            false,
            true,
            null);
        soak(slot, 40.0, 1200);
        assertFalse(slot.isAutoEjectReady(), "settled at " + slot.currentTemp + " with heat left");
        soak(slot, 0.0, 1200);
        assertTrue(slot.isAutoEjectReady(), "at " + slot.currentTemp);
    }

    @Test
    void testAnEuIsWorthTheHeatItsSteamTook() {
        // 2.26 MJ boils a liter of water into 160 L of steam, 80 EU in a turbine at 100%
        assertEquals(28250.0, new CoolantDeviceData.PHEData().joulesPerEU(), 1e-6);
    }

    @Test
    void testGlassEfficiencyStaysBetweenHalfAndNineTenths() {
        CoolantDeviceData.CoolantBathData bath = new CoolantDeviceData.CoolantBathData();
        assertEquals(0.5, bath.efficiency(4), 1e-9, "EV");
        assertEquals(0.55, bath.efficiency(5), 1e-9, "IV");
        assertEquals(0.6, bath.efficiency(6), 1e-9, "LuV");
        assertEquals(0.9, bath.efficiency(12), 1e-9, "UMV");
        assertEquals(0.9, bath.efficiency(20), 1e-9, "no higher than 90%");
        assertEquals(0.5, bath.efficiency(-1), 1e-9, "no lower than 50%");
        bath.glassEfficiencyBase = 2.0;
        assertEquals(0.9, bath.efficiency(4), 1e-9, "whatever the configuration");
        bath.glassEfficiencyBase = 0.0;
        bath.glassEfficiencyPerTier = 0.0;
        assertEquals(0.5, bath.efficiency(12), 1e-9, "whatever the configuration");
    }

    /** A bath under glass of a tier holding one item, stepped once in a 100 C loop; returns the coolant's heat gain. */
    private static double coolantGain(int glassTier, CoolantBathSlot slot) {
        MTECoolantBath bath = new MTECoolantBath("test.bath.eff" + glassTier);
        bath.mGlassTier = glassTier;
        bath.mSlots = new CoolantBathSlot[15];
        bath.mSlots[0] = slot;
        LoopSegment seg = new LoopSegment("test_eff_seg", 1.0, 0.05, 10.0, 0.0, 20.0, 100.0, 100.0);
        seg.setCurrentTemperatureCelsius(100.0);
        CoolantFluidProperty water = CoolantFluidProperty.WATER;
        bath.processThermalExchange(0.05, 1.0, water, seg);
        return (seg.getCurrentTemperatureCelsius() - 100.0) * seg.getVolume()
            * water.getDensity()
            * water.getSpecificHeat();
    }

    @Test
    void testOnlyTheGlassTiersShareOfAnItemsHeatReachesTheCoolant() {
        for (int tier : new int[] { 4, 8, 12 }) {
            double eff = new CoolantDeviceData.CoolantBathData().efficiency(tier);
            // Cooling: the coolant gains the share of what the item gave off
            CoolantBathSlot hot = hotIngot(45.0);
            double gain = coolantGain(tier, hot);
            double gaveOff = (1500.0 - hot.currentTemp) * hot.heatCapacity;
            assertTrue(gaveOff > 0);
            assertEquals(gaveOff * eff, gain, gaveOff * 1e-6, "cooling under glass tier " + tier);

            // Heating: the coolant loses that much more than the item took up
            CoolantBathSlot cold = new CoolantBathSlot(
                new ItemStack(Items.iron_ingot),
                20.0,
                1500.0,
                0,
                1000,
                1000.0,
                0.15,
                false,
                true,
                false,
                new ItemStack(Items.gold_ingot));
            gain = coolantGain(tier, cold);
            double tookUp = (cold.currentTemp - 20.0) * cold.heatCapacity;
            assertTrue(tookUp > 0);
            assertEquals(-tookUp / eff, gain, tookUp * 1e-6, "heating under glass tier " + tier);

            // A cryogenic cell: the coolant loses the share of the cold the cell spent
            CoolantBathSlot cryo = new CoolantBathSlot(
                new ItemStack(Items.bucket),
                -196.0,
                -196.0,
                0,
                1000,
                1.0e9,
                0.25,
                false,
                false,
                false,
                null,
                true);
            gain = coolantGain(tier, cryo);
            assertTrue(cryo.absorbedHeatJoules > 0);
            assertEquals(-cryo.absorbedHeatJoules * eff, gain, cryo.absorbedHeatJoules * 1e-6, "boiling, tier " + tier);
        }
    }

    @Test
    void testACoolantTheFreezerMakesTakesUpTheFreezersEnergy() {
        CoolantDeviceData.PHEData.SecondaryCoolantEntry e = new CoolantDeviceData.PHEData.SecondaryCoolantEntry(
            "liquidnitrogen",
            -196.0,
            "nitrogen",
            1234.0,
            1,
            false);
        assertEquals(1234.0, e.latentHeat(), 0.0, "as configured where the freezer has no recipe");
        e.recipeLatentHeatJoulePerLiter = 576.0 * 28250.0;
        assertEquals(16272000.0, e.latentHeat(), 0.0, "480 EU/t for a minute per 1000 L");
        // The stock values agree with it outside the game
        assertEquals(
            16272000.0,
            new CoolantDeviceData.PHEData().findSecondaryCoolant("liquidnitrogen")
                .latentHeat(),
            0.0);
    }
}

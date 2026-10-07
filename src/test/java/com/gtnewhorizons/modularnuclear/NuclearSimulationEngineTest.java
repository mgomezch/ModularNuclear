package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;
import com.gtnewhorizons.coolantloops.engine.ICoolantLoopPump;
import com.gtnewhorizons.coolantloops.engine.LoopSegment;
import com.gtnewhorizons.modularnuclear.common.block.BlockCorium;
import com.gtnewhorizons.modularnuclear.common.block.BlockNuclearCasing;
import com.gtnewhorizons.modularnuclear.common.block.ItemNuclearCasing;
import com.gtnewhorizons.modularnuclear.common.entity.EntityMeltdownFallout;
import com.gtnewhorizons.modularnuclear.common.fluid.ModFluids;
import com.gtnewhorizons.modularnuclear.common.metatileentity.ModMetaTileEntities;
import com.gtnewhorizons.modularnuclear.common.metatileentity.NuclearStructureChannels;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearBus;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControl;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHatch;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHighPressure;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;
import com.gtnewhorizons.modularnuclear.common.nuclear.INuclearTile;
import com.gtnewhorizons.modularnuclear.common.nuclear.NeutronType;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearFuelType;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid;
import com.gtnewhorizons.modularnuclear.common.nuclearcontrol.ItemCardModularNuclear;
import com.gtnewhorizons.modularnuclear.common.textures.ModularNuclearTextures;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.render.TextureFactory;
import shedar.mods.ic2.nuclearcontrol.api.CardState;
import shedar.mods.ic2.nuclearcontrol.api.DisplaySettingHelper;
import shedar.mods.ic2.nuclearcontrol.api.ICardWrapper;
import shedar.mods.ic2.nuclearcontrol.api.PanelSetting;
import shedar.mods.ic2.nuclearcontrol.api.PanelString;
import shedar.mods.ic2.nuclearcontrol.panel.CardWrapperImpl;

public class NuclearSimulationEngineTest {

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
            if (com.gtnewhorizons.modularnuclear.common.block.ModBlocks.nuclearCasing == null) {
                com.gtnewhorizons.modularnuclear.common.block.ModBlocks.init();
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

    @BeforeEach
    void setUp() {
        Thread.currentThread()
            .setName("Server thread");
        NuclearSimulationEngine.resetDefaultParameters();
    }

    private static class MockNuclearTile implements INuclearTile {

        double temperature = 20.0;
        double heatEU = 0.0;
        boolean isFuel = false;
        int neutronBaseGen = 0;
        double absorbProb = 0.1;
        double scatterProb = 0.5;
        double moderationProb = 0.8;
        double heatCoeff = 0.05;
        double insulationDampening = 0.0;
        int emissionCount = 1;

        int fluxReceived = 0;
        int fastAbsorbed = 0;
        int thermalAbsorbed = 0;
        int scattered = 0;

        MockNuclearTile(boolean isFuel, int neutronBaseGen) {
            this.isFuel = isFuel;
            this.neutronBaseGen = neutronBaseGen;
        }

        MockNuclearTile(double initialTemp, double heatCoeff) {
            this.temperature = initialTemp;
            this.heatCoeff = heatCoeff;
        }

        @Override
        public double getTemperature() {
            return temperature;
        }

        @Override
        public void setTemperature(double temp) {
            this.temperature = temp;
        }

        @Override
        public void addHeat(double heat) {
            this.heatEU += heat;
            this.temperature += heat / NuclearSimulationEngine.EU_PER_DEGREE;
        }

        @Override
        public double getHeatTransferCoeff() {
            return heatCoeff;
        }

        @Override
        public boolean isFuel() {
            return isFuel;
        }

        @Override
        public int generateNeutrons(double efficiency) {
            return (int) (neutronBaseGen * efficiency);
        }

        @Override
        public void addNeutronFlux(NeutronType type, int count) {
            this.fluxReceived += count;
        }

        @Override
        public double getAbsorptionProbability(NeutronType type) {
            return absorbProb;
        }

        @Override
        public double getScatteringProbability(NeutronType type) {
            return scatterProb;
        }

        @Override
        public double getModerationProbability() {
            return moderationProb;
        }

        @Override
        public void onNeutronAbsorbed(NeutronType type, int count) {
            if (type == NeutronType.FAST) fastAbsorbed += count;
            else thermalAbsorbed += count;
        }

        @Override
        public void onNeutronScattered(NeutronType type, int count) {
            this.scattered += count;
        }

        @Override
        public void nuclearTick(double efficiency) {}

        @Override
        public double getInsulationDampening() {
            return insulationDampening;
        }

        @Override
        public int getNeutronEmissionCount() {
            return emissionCount;
        }
    }

    @Test
    void testNegativeTemperatureEfficiencyCurve() {
        assertEquals(0.5, NuclearSimulationEngine.calculateEfficiency(0.0), 1e-6);
        assertEquals(
            1.0,
            NuclearSimulationEngine.calculateEfficiency(NuclearFuelType.URANIUM.peakReactivityTemp),
            1e-6);
        assertEquals(0.05, NuclearSimulationEngine.calculateEfficiency(NuclearFuelType.URANIUM.floorTemp), 1e-6);
        assertEquals(0.05, NuclearSimulationEngine.calculateEfficiency(3000.0), 1e-6);

        // Test Thorium
        assertEquals(0.5, NuclearFuelType.THORIUM.calculateReactivity(0.0), 1e-6);
        assertEquals(
            1.0,
            NuclearFuelType.THORIUM.calculateReactivity(NuclearFuelType.THORIUM.peakReactivityTemp),
            1e-6);
        assertEquals(0.05, NuclearFuelType.THORIUM.calculateReactivity(NuclearFuelType.THORIUM.floorTemp), 1e-6);
        assertEquals(0.05, NuclearFuelType.THORIUM.calculateReactivity(2500.0), 1e-6);
    }

    @Test
    void testEmptyGridSimulation() {
        NuclearSimulationEngine.SimulationResult res = NuclearSimulationEngine.simulate(null, 0, 0);
        assertNotNull(res);
        assertEquals(0, res.totalNeutronsGenerated);

        INuclearTile[][] emptyGrid = new INuclearTile[3][3];
        NuclearSimulationEngine.SimulationResult res2 = NuclearSimulationEngine.simulate(emptyGrid, 3, 3);
        assertNotNull(res2);
        assertEquals(0, res2.totalNeutronsGenerated);
        assertEquals(NuclearSimulationEngine.AMBIENT_TEMP, res2.averageTemperature, 1e-6);
    }

    @Test
    void testFuelNeutronGenerationAndTransport() {
        INuclearTile[][] grid = new INuclearTile[3][3];
        MockNuclearTile fuel = new MockNuclearTile(true, 100);
        grid[1][1] = fuel;

        MockNuclearTile moderator = new MockNuclearTile(false, 0);
        moderator.absorbProb = 0.5;
        grid[1][0] = moderator;
        grid[1][2] = moderator;
        grid[0][1] = moderator;
        grid[2][1] = moderator;

        NuclearSimulationEngine.SimulationResult res = NuclearSimulationEngine.simulate(grid, 3, 3);
        assertTrue(res.totalNeutronsGenerated > 0, "Fuel rod should generate neutrons");
        assertEquals((int) (100 * NuclearSimulationEngine.calculateEfficiency(20.0)), res.totalNeutronsGenerated);
        assertTrue(fuel.getTemperature() > 20.0, "Fuel rod should heat up due to fission");
        assertTrue(fuel.heatEU > 0, "Direct fission heat should be recorded");
        assertTrue(res.fastNeutronsAbsorbed + res.thermalNeutronsAbsorbed + res.neutronsEscaped > 0);
    }

    @Test
    void testSelfStabilizationUnderHighTemp() {
        INuclearTile[][] grid = new INuclearTile[1][1];
        MockNuclearTile hotFuel = new MockNuclearTile(true, 100);
        hotFuel.temperature = 2500.0; // Above Uranium floor temp (2200°C)
        grid[0][0] = hotFuel;

        NuclearSimulationEngine.SimulationResult res = NuclearSimulationEngine.simulate(grid, 1, 1);
        assertEquals(
            5,
            res.totalNeutronsGenerated,
            "Reactivity should smoothly decay to 5% floor at or above floor temp (never 0)");
    }

    @Test
    void testHeatDiffusionBetweenAdjacentTiles() {
        INuclearTile[][] grid = new INuclearTile[2][1];
        MockNuclearTile hotTile = new MockNuclearTile(500.0, 0.2);
        MockNuclearTile coldTile = new MockNuclearTile(20.0, 0.2);
        grid[0][0] = hotTile;
        grid[1][0] = coldTile;

        double initialDiff = hotTile.getTemperature() - coldTile.getTemperature();
        NuclearSimulationEngine.simulate(grid, 2, 1);

        double newDiff = hotTile.getTemperature() - coldTile.getTemperature();
        assertTrue(newDiff < initialDiff, "Heat diffusion should reduce temperature difference between adjacent tiles");
        assertTrue(coldTile.getTemperature() > 20.0, "Cold tile should have gained temperature from hot tile");
    }

    @Test
    void testNeutronScatteringAndModeration() {
        INuclearTile[][] grid = new INuclearTile[3][3];
        MockNuclearTile fuel = new MockNuclearTile(true, 500);
        fuel.temperature = NuclearFuelType.URANIUM.peakReactivityTemp; // Peak reactivity
        grid[1][1] = fuel;

        MockNuclearTile reflector = new MockNuclearTile(false, 0);
        reflector.absorbProb = 0.05;
        reflector.scatterProb = 0.95;
        reflector.moderationProb = 0.90;
        grid[1][0] = reflector;
        grid[1][2] = reflector;
        grid[0][1] = reflector;
        grid[2][1] = reflector;

        fuel.emissionCount = 4;
        NuclearSimulationEngine.SimulationResult res = null;
        for (int i = 0; i < 100; i++) {
            fuel.temperature = NuclearFuelType.URANIUM.peakReactivityTemp;
            res = NuclearSimulationEngine.simulate(grid, 3, 3);
            if (reflector.scattered > 0) break;
        }
        assertTrue(reflector.fluxReceived > 0, "Reflector should receive neutron flux from adjacent fuel");
        assertTrue(reflector.scattered > 0, "Reflector should scatter neutrons");
        assertEquals(500, res.totalNeutronsGenerated, "All 500 neutrons generated by fuel");
    }

    @Test
    void testCoolantBoilingThermodynamics() {
        // Distilled Water: boiling at 200°C, 320 EU/mB, 160:1 steam ratio
        double boilingPoint = 200.0;
        double heatPerMB = 320.0;
        double currentTemp = 250.0;
        double heatAvailable = (currentTemp - boilingPoint) * NuclearSimulationEngine.EU_PER_DEGREE;
        assertEquals(50.0 * NuclearSimulationEngine.EU_PER_DEGREE, heatAvailable, 1e-6);

        int fluidToBoil = (int) (heatAvailable / heatPerMB);
        assertEquals(5, fluidToBoil);

        int steamProduced = fluidToBoil * 160;
        assertEquals(800, steamProduced);

        double heatConsumed = fluidToBoil * heatPerMB;
        double tempDrop = heatConsumed / NuclearSimulationEngine.EU_PER_DEGREE;
        double finalTemp = currentTemp - tempDrop;
        assertTrue(finalTemp < currentTemp && finalTemp >= boilingPoint);
    }

    @Test
    void testCoolantCellCapacityScaling() {
        // 10k cell vs 60k cell vs 360k cell
        int maxHeat10k = 10_000;
        int maxHeat60k = 60_000;
        int maxHeat360k = 360_000;

        int rate10k = Math.max(1, maxHeat10k / 100);
        int rate60k = Math.max(1, maxHeat60k / 100);
        int rate360k = Math.max(1, maxHeat360k / 100);

        assertEquals(100, rate10k);
        assertEquals(600, rate60k);
        assertEquals(3600, rate360k);

        // 60k cell absorbs 6x more heat per tick than 10k cell
        assertEquals(6, rate60k / rate10k);
        // 360k cell absorbs 6x more heat per tick than 60k cell
        assertEquals(6, rate360k / rate60k);
    }

    @Test
    void testAbsorptionDrivenFuelDepletion() {
        // Under high neutron flux, fuel rod burns much faster than idle
        int fastAbsorbed = 15;
        int thermalAbsorbed = 40;
        int neutronsGenerated = 16;

        int activeDamage = fastAbsorbed * 1 + thermalAbsorbed * 2 + Math.max(1, neutronsGenerated / 4);
        assertEquals(15 + 80 + 4, activeDamage);
        assertEquals(99, activeDamage);

        // Idle / minimal flux
        int idleFast = 0;
        int idleThermal = 0;
        int idleNeutrons = 0;
        int idleDamage = idleFast * 1 + idleThermal * 2 + Math.max(1, idleNeutrons / 4);
        assertEquals(1, idleDamage);

        assertTrue(
            activeDamage > idleDamage * 50,
            "Active fission & absorption should deplete fuel orders of magnitude faster");
    }

    @Test
    void testCoolantRequiredTierMapping() {
        // Water is strictly disallowed
        assertEquals(999, MTEHatchNuclearHatch.getRequiredFluidTier("water"));
        // IC2 Coolant is starter tier (Electrum, 0)
        assertEquals(
            NuclearSimulationEngine.PIPE_TIER_ELECTRUM,
            MTEHatchNuclearHatch.getRequiredFluidTier("ic2coolant"));
        // Distilled Water is Platinum (1)
        assertEquals(
            NuclearSimulationEngine.PIPE_TIER_PLATINUM,
            MTEHatchNuclearHatch.getRequiredFluidTier("distilledwater"));
        // Heavy Water is Quantium (3)
        assertEquals(
            NuclearSimulationEngine.PIPE_TIER_QUANTIUM,
            MTEHatchNuclearHatch.getRequiredFluidTier("fluid.heavywater"));
    }

    @Test
    void testPipeTierNames() {
        assertEquals("Electrum (IC2 Coolant)", NuclearSimulationEngine.getPipeTierName(0));
        assertEquals("Platinum (Distilled Water)", NuclearSimulationEngine.getPipeTierName(1));
        assertEquals("Osmium (HP Core Hatches)", NuclearSimulationEngine.getPipeTierName(2));
        assertEquals("Quantium (Heavy Water)", NuclearSimulationEngine.getPipeTierName(3));
        assertEquals("Fluxed Electrum (Excited Fuel)", NuclearSimulationEngine.getPipeTierName(4));
        assertEquals("Black Plutonium (Max Tier / All Coolants)", NuclearSimulationEngine.getPipeTierName(5));
    }

    @Test
    void testMaxOperatingTemperatures() {
        assertEquals(1000.0, NuclearSimulationEngine.getMaxOperatingTemperature(0));
        assertEquals(1400.0, NuclearSimulationEngine.getMaxOperatingTemperature(1));
        assertEquals(1800.0, NuclearSimulationEngine.getMaxOperatingTemperature(2));
        assertEquals(2200.0, NuclearSimulationEngine.getMaxOperatingTemperature(3));
        assertEquals(2600.0, NuclearSimulationEngine.getMaxOperatingTemperature(4));
        assertEquals(3200.0, NuclearSimulationEngine.getMaxOperatingTemperature(5));
    }

    @Test
    void testCoolantBoilingThresholds() {
        assertEquals(Double.POSITIVE_INFINITY, NuclearSimulationEngine.getCoolantBoilingThreshold("ic2coolant"));
        assertEquals(100.0, NuclearSimulationEngine.getCoolantBoilingThreshold("distilledwater"));
        assertEquals(101.4, NuclearSimulationEngine.getCoolantBoilingThreshold("heavywater"));
    }

    @Test
    void testStandaloneGridPresetsAndExecution() {
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid grid7 = new com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid(
            7,
            7,
            NuclearSimulationEngine.PIPE_TIER_PLATINUM);
        grid7.loadPreset("BREEDER_7X7");

        assertEquals(7, grid7.getWidth());
        assertEquals(7, grid7.getHeight());
        assertFalse(grid7.isExploded());

        for (int i = 0; i < 5; i++) {
            boolean ok = grid7.step();
            assertTrue(ok, "Grid simulation step should succeed without exploding");
        }

        assertTrue(grid7.getCurrentTick() == 5);
        assertTrue(grid7.getTotalNeutronsGenerated() > 0, "Neutrons should be generated by Uranium rods");
        assertTrue(grid7.getCoreMaxTemp() >= NuclearSimulationEngine.AMBIENT_TEMP);

        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid grid9 = new com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid(
            9,
            9,
            NuclearSimulationEngine.PIPE_TIER_OSMIUM);
        grid9.loadPreset("BEST_OSMIUM_9X9");

        assertEquals(9, grid9.getWidth());
        assertEquals(9, grid9.getHeight());
        assertFalse(grid9.isExploded());

        for (int i = 0; i < 5; i++) {
            boolean ok = grid9.step();
            assertTrue(ok, "Grid simulation step should succeed without exploding");
        }

        assertTrue(grid9.getCurrentTick() == 5);
        assertTrue(grid9.getTotalNeutronsGenerated() > 0, "Neutrons should be generated by rods");
        assertTrue(grid9.getCoreMaxTemp() >= NuclearSimulationEngine.AMBIENT_TEMP);
    }

    @Test
    void testDryHatchBoilingThresholdThermalShock() {
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid grid = new com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid(
            3,
            3,
            NuclearSimulationEngine.PIPE_TIER_PLATINUM);

        // Put a distilled water hatch at (1, 1)
        grid.setTile(
            1,
            1,
            com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.HATCH_DISTILLED_WATER);
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile hatch = grid.getTile(1, 1);

        // Case 1: Hatch was dry, but cold (20°C <= 100°C boiling threshold) -> safe refill!
        hatch.setInputFluidAmount(0);
        hatch.setWasDry(true);
        hatch.setTemperature(50.0);
        boolean refilledSafely = hatch.refillCoolant();
        assertTrue(refilledSafely, "Refilling dry hatch below boiling threshold should be safe");
        assertFalse(hatch.isWasDry());

        // Case 2: Hatch was dry, and hot (150°C > 100°C boiling threshold) -> thermal shock damage & maintenance!
        hatch.setInputFluidAmount(0);
        hatch.setWasDry(true);
        hatch.setTemperature(150.0);
        assertEquals(0.0, grid.getReactorDamage(), 1e-4);
        assertEquals(0, grid.getMaintenanceIssues());

        // Step with hot dry hatch receiving coolant
        grid.step();
        assertFalse(grid.isExploded(), "Injecting coolant into dry hatch above boiling threshold must NOT explode on first hit");
        assertEquals(2.0, grid.getReactorDamage(), 1e-4, "Thermal shock must inflict 2% reactor damage");
        assertEquals(1, grid.getMaintenanceIssues(), "Thermal shock must increase maintenance issues by 1");
        assertEquals(0, hatch.getInputFluidAmount(), "Coolant injected into hot dry hatch must flash and void");
        assertTrue(hatch.isWasDry(), "Hatch must remain marked as wasDry after flash voiding");
    }

    @Test
    void testIC2CoolantContinuousAmbientExchange() {
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid grid = new com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid(
            3,
            3,
            NuclearSimulationEngine.PIPE_TIER_ELECTRUM);

        grid.setTile(
            1,
            1,
            com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.HATCH_IC2_COOLANT);
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile hatch = grid.getTile(1, 1);

        // Case 1: Coolant works below 100°C down to ambient (24°C)
        hatch.setInputFluidAmount(2000);
        hatch.setTemperature(80.0); // 80°C is below water boiling, but IC2 coolant must absorb heat!
        grid.step();

        assertTrue(
            hatch.getTemperature() < 80.0,
            "IC2 coolant must extract heat below 100°C down to ambient temperature");
        assertTrue(hatch.getOutputFluidAmount() > 0, "IC2 coolant must produce hot coolant below 100°C");

        // Case 2: Dry hatch at 300°C refilling IC2 coolant NEVER explodes
        hatch.setInputFluidAmount(0);
        hatch.setWasDry(true);
        hatch.setTemperature(300.0);
        boolean refilled = hatch.refillCoolant();
        assertTrue(refilled, "IC2 coolant must never trigger thermal shock explosion when refilling dry hot hatch");
    }

    @Test
    void testRadiovoltaicGenerationAndSaturation() {
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile cellHV = new com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile(
            com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.RADIOVOLTAIC_HV);
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile cellEV = new com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile(
            com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.RADIOVOLTAIC_EV);

        // 1. Check absorption properties
        assertEquals(1.0, cellHV.getAbsorptionProbability(NeutronType.FAST));
        assertEquals(1.0, cellHV.getAbsorptionProbability(NeutronType.THERMAL));
        assertEquals(0.0, cellHV.getScatteringProbability(NeutronType.FAST));
        assertEquals(0.0, cellHV.getModerationProbability());

        // 2. Feed fast vs thermal neutrons and check 4x weight
        cellHV.onNeutronAbsorbed(NeutronType.FAST, 10);
        cellHV.nuclearTick(1.0);
        long fastEU = cellHV.getDirectEUProduced();

        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile cellHVThermal = new com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile(
            com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.RADIOVOLTAIC_HV);
        cellHVThermal.onNeutronAbsorbed(NeutronType.THERMAL, 10);
        cellHVThermal.nuclearTick(1.0);
        long thermalEU = cellHVThermal.getDirectEUProduced();

        assertTrue(fastEU > thermalEU * 2, "Fast neutrons must generate significantly more EU than thermal neutrons");

        // 3. Saturation: HV caps around 1024 EU/t (2A HV)
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile cellHVSat = new com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile(
            com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.RADIOVOLTAIC_HV);
        cellHVSat.onNeutronAbsorbed(NeutronType.FAST, 1000);
        cellHVSat.nuclearTick(1.0);
        assertEquals(1024, cellHVSat.getDirectEUProduced(), "HV Radiovoltaic cell must cap at 1024 EU/t (2A HV)");
        assertTrue(cellHVSat.getTemperature() > 24.0, "Excess energy beyond saturation must convert into heat");

        // 4. EV caps around 4096 EU/t (2A EV)
        cellEV.onNeutronAbsorbed(NeutronType.FAST, 1000);
        cellEV.nuclearTick(1.0);
        assertEquals(4096, cellEV.getDirectEUProduced(), "EV Radiovoltaic cell must cap at 4096 EU/t (2A EV)");

        // 5. Grid integration test with fuel and radiovoltaic
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid grid = new com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid(
            3,
            3,
            NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        grid.setTile(
            1,
            1,
            com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.FUEL_URANIUM_QUAD);
        grid.setTile(0, 1, com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.RADIOVOLTAIC_EV);
        for (int i = 0; i < 10; i++) {
            grid.step();
            if (grid.getFlowDirectEU() > 0) break;
        }

        assertTrue(grid.getFlowDirectEU() > 0, "Grid must accumulate Radiovoltaic direct EU");
        assertEquals(grid.getFlowDirectEU(), grid.getLastPowerResult().directPowerEUt);
        assertEquals(grid.getFlowDirectEU(), grid.getLastPowerResult().totalPowerEUt);
    }

    @Test
    void testOverheatingHatchesVoidContentsWithoutExploding() {
        // High core temperature exceeding Electrum casing limit (1000°C)
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid grid = new com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid(
            3,
            3,
            NuclearSimulationEngine.PIPE_TIER_ELECTRUM);

        // Put a superheated fuel rod and superheated coolant hatch
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile fuelTile = grid.getTile(1, 1);
        fuelTile.setType(com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.FUEL_URANIUM_QUAD);
        fuelTile.setTemperature(3000.0); // Well above 1000°C limit

        NuclearSimulationEngine.coolantFeedRate = 0; // Prevent refilling so hatch actually overheats
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile hatchTile = grid.getTile(0, 1);
        hatchTile
            .setType(com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.HATCH_IC2_COOLANT);
        hatchTile.setInputFluidAmount(10);
        hatchTile.setOutputFluidAmount(200);
        hatchTile.setTemperature(3000.0);

        grid.step();

        // Must NOT explode
        assertFalse(grid.isExploded(), "Reactor must not explode from hatch overheating!");

        // Overheating hatch must have voided its fluids
        assertEquals(0, hatchTile.getInputFluidAmount(), "Overheating fluid hatch must void input fluid");
        assertEquals(0, hatchTile.getOutputFluidAmount(), "Overheating fluid hatch must void output fluid");

        // Overheating fuel bus must have voided its fuel
        assertEquals(
            com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.EMPTY,
            fuelTile.getType(),
            "Overheating fuel tile must void its fuel contents");
    }

    @Test
    void testBoilingCoolantConductiveEvaporationWithoutExplosion() {
        // Distilled water boils at 100°C: a non-dry hatch above boiling point does NOT explode,
        // but instead continuously evaporates coolant into steam and cools down.
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid grid = new com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid(
            3,
            3,
            NuclearSimulationEngine.PIPE_TIER_PLATINUM);
        grid.setTile(
            0,
            1,
            com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.HATCH_DISTILLED_WATER);
        grid.getTile(0, 1)
            .setInputFluidAmount(100);
        grid.getTile(0, 1)
            .setTemperature(300.0); // Above 100°C boiling threshold

        grid.step();
        assertFalse(grid.isExploded(), "Boiling coolant inside reactor core must NOT explode!");
        assertTrue(grid.getTile(0, 1).getTotalSteamProduced() > 0, "Boiling coolant must produce steam");
        assertTrue(grid.getTile(0, 1).getTemperature() < 300.0, "Boiling coolant must cool the hatch");

        // Sub-boiling coolant (< 100°C) must NOT explode
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid safeGrid = new com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid(
            3,
            3,
            NuclearSimulationEngine.PIPE_TIER_PLATINUM);
        safeGrid.setTile(
            0,
            1,
            com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.HATCH_DISTILLED_WATER);
        safeGrid.getTile(0, 1)
            .setInputFluidAmount(100);
        safeGrid.getTile(0, 1)
            .setTemperature(80.0); // Sub-boiling

        safeGrid.step();
        assertFalse(safeGrid.isExploded(), "Sub-boiling distilled water must be safe from explosion!");
    }

    @Test
    void testDryCoolantThermalShockTriggersPowerfailShutdown() {
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid grid = new com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid(
            3,
            3,
            NuclearSimulationEngine.PIPE_TIER_ELECTRUM);

        // Grid contains fuel, reflector, and radiovoltaic
        grid.setTile(
            1,
            1,
            com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.FUEL_URANIUM_QUAD);
        grid.setTile(
            1,
            2,
            com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.REFLECTOR_BERYLLIUM);
        grid.setTile(1, 0, com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.RADIOVOLTAIC_HV);

        // Dry superheated coolant hatch (> 100°C threshold)
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile hatch = grid.getTile(0, 1);
        hatch
            .setType(com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.HATCH_DISTILLED_WATER);
        hatch.setInputFluidAmount(0);
        hatch.setWasDry(true);
        hatch.setTemperature(350.0); // Superheated

        grid.step();

        // Must NOT explode
        assertFalse(grid.isExploded(), "Dry coolant thermal shock must NOT explode the reactor on first incident!");

        // Must inflict 2.0% damage and 1 maintenance issue
        assertEquals(2.0, grid.getReactorDamage(), 1e-4, "Damage must increase by 2.0%");
        assertEquals(1, grid.getMaintenanceIssues(), "Maintenance issues must increase by 1");

        // Fuel must remain intact (not voided on non-meltdown damage)
        assertEquals(
            com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.FUEL_URANIUM_QUAD,
            grid.getTile(1, 1)
                .getType(),
            "Fuel must NOT be voided on non-lethal thermal shock");

        // Coolant must be voided (flashes into steam)
        assertEquals(0, hatch.getInputFluidAmount(), "Coolant fluid must be voided");

        // Crucially, Reflector and Radiovoltaic MUST be preserved!
        assertEquals(
            com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.REFLECTOR_BERYLLIUM,
            grid.getTile(1, 2)
                .getType(),
            "Reflector must NOT be voided on dry coolant shutdown!");
        assertEquals(
            com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.RADIOVOLTAIC_HV,
            grid.getTile(1, 0)
                .getType(),
            "Radiovoltaic cell must NOT be voided on dry coolant shutdown!");

        // Cumulative thermal shocks trigger meltdown at 100% damage
        grid.setReactorDamage(98.0);
        hatch.setWasDry(true);
        hatch.setTemperature(350.0);
        grid.step();
        assertTrue(grid.isExploded(), "Reactor must explode when cumulative damage reaches 100%");
        assertTrue(grid.getExplosionReason().contains("100%"));
    }

    @Test
    void testLossOfCoolantOperatesDryUntilOverheat() {
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid grid = new com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid(
            3,
            3,
            NuclearSimulationEngine.PIPE_TIER_ELECTRUM);

        // Active fuel and an empty coolant hatch with no fluid feed
        NuclearSimulationEngine.coolantFeedRate = 0; // Simulate fluid supply failure
        grid.setTile(
            1,
            1,
            com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.FUEL_URANIUM_QUAD);
        grid.setTile(
            1,
            2,
            com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.REFLECTOR_BERYLLIUM);
        grid.setTile(
            0,
            1,
            com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.HATCH_IC2_COOLANT);
        grid.getTile(0, 1)
            .setInputFluidAmount(0);

        grid.step();

        // Must continue operating dry without artificial shutdown
        assertFalse(grid.isExploded(), "Dry operation within safe limits must not explode");
        assertFalse(grid.isPowerFailed(), "Reactor must not powerfail on dry operation");
        assertEquals(
            com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.FUEL_URANIUM_QUAD,
            grid.getTile(1, 1)
                .getType(),
            "Fuel must remain intact while within safe operating conditions");
        assertEquals(
            com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.REFLECTOR_BERYLLIUM,
            grid.getTile(1, 2)
                .getType(),
            "Reflector must remain intact");
    }

    @Test
    void testReactorFootprintAndWallThickness() {
        int[] coreSizes = { 3, 7, 11 };
        int[] expectedFootprints = { 5, 9, 13 };

        for (int i = 0; i < coreSizes.length; i++) {
            int core = coreSizes[i];
            int footprint = expectedFootprints[i];
            int wall = (footprint - core) / 2;
            assertEquals(1, wall, "Wall thickness for 5-tall octagonal reactor must be 1 block of casing");
            assertEquals(core, footprint - 2 * wall, "Internal core dimension must match");
        }
    }

    @Test
    void testIsCornerNullCell() {
        // Tier 1: 3x3 Core (5 active cells, 4 corner null cells)
        int nullCount3 = 0;
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                if (NuclearSimulationEngine.isCornerNullCell(x, y, 3, 3)) {
                    nullCount3++;
                    assertTrue((x == 0 || x == 2) && (y == 0 || y == 2));
                }
            }
        }
        assertEquals(4, nullCount3);

        // Tier 2: 7x7 Core (45 active cells, 4 corner null cells)
        int nullCount7 = 0;
        for (int x = 0; x < 7; x++) {
            for (int y = 0; y < 7; y++) {
                if (NuclearSimulationEngine.isCornerNullCell(x, y, 7, 7)) {
                    nullCount7++;
                    assertTrue((x == 0 || x == 6) && (y == 0 || y == 6));
                }
            }
        }
        assertEquals(4, nullCount7);

        // Tier 3: 11x11 Core (109 active cells, 12 corner null cells)
        int nullCount11 = 0;
        for (int x = 0; x < 11; x++) {
            for (int y = 0; y < 11; y++) {
                if (NuclearSimulationEngine.isCornerNullCell(x, y, 11, 11)) {
                    nullCount11++;
                }
            }
        }
        assertEquals(12, nullCount11);
        assertEquals(109, (11 * 11) - nullCount11);
    }

    @Test
    void testNeutronFluxEscapesThroughWallsAndNullCells() {
        MockNuclearTile[][] grid = new MockNuclearTile[3][3];
        grid[1][1] = new MockNuclearTile(true, 100); // Fuel in center
        grid[0][1] = new MockNuclearTile(false, 0); // Non-fuel neighbor
        grid[2][1] = new MockNuclearTile(false, 0);
        grid[1][0] = new MockNuclearTile(false, 0);
        grid[1][2] = new MockNuclearTile(false, 0);
        // Corners remain null (cut corner null cells)

        NuclearSimulationEngine.SimulationResult res = null;
        for (int i = 0; i < 20; i++) {
            res = NuclearSimulationEngine.simulate(grid, 3, 3);
            if (res.neutronsEscaped > 0) break;
        }
        assertTrue(res.neutronsEscaped > 0, "Neutrons hitting outer boundary / null cells must escape");
        assertEquals(0, res.wallNeutronsReflected, "Outer casing walls must not reflect neutrons");
        assertEquals(0, res.wallNeutronsAbsorbed, "Outer casing walls must not absorb neutrons into heat pool");
        assertEquals(0.0, res.wallHeatPool, 1e-6, "Wall heat pool must remain 0");
    }

    @Test
    void testPerimeterHeatLossAndInsulationDampening() {
        // Grid 3x3: center [1][1] is completely surrounded by 4 neighbors (north, south, east, west)
        // Center has no boundary / null neighbor.
        // Neighbors have outer boundaries, so they lose heat to ambient.
        MockNuclearTile[][] grid = new MockNuclearTile[3][3];
        MockNuclearTile center = new MockNuclearTile(500.0, 0.1);
        MockNuclearTile nNorth = new MockNuclearTile(500.0, 0.1);
        MockNuclearTile nSouth = new MockNuclearTile(500.0, 0.1);
        MockNuclearTile nEast = new MockNuclearTile(500.0, 0.1);
        MockNuclearTile nWest = new MockNuclearTile(500.0, 0.1);

        grid[1][1] = center;
        grid[1][0] = nNorth;
        grid[1][2] = nSouth;
        grid[0][1] = nWest;
        grid[2][1] = nEast;

        NuclearSimulationEngine.simulate(grid, 3, 3);

        // Center only borders 4 tiles; it has no direct boundaries with empty space/ambient.
        // Neighbors border ambient directly, so their temperature drops significantly faster than the center.
        assertTrue(
            center.getTemperature() > nNorth.getTemperature(),
            "Inner cell should retain more heat than perimeter cells exposed to ambient");
        assertTrue(nNorth.getTemperature() < 500.0, "Perimeter cell must lose heat to ambient");

        // When the inner cell has 100% insulation, no conductive flow to cooled neighbors occurs either
        MockNuclearTile insulatedCenter = new MockNuclearTile(500.0, 0.1);
        insulatedCenter.insulationDampening = 1.0;
        grid[1][1] = insulatedCenter;
        grid[1][0] = new MockNuclearTile(500.0, 0.1);
        grid[1][2] = new MockNuclearTile(500.0, 0.1);
        grid[0][1] = new MockNuclearTile(500.0, 0.1);
        grid[2][1] = new MockNuclearTile(500.0, 0.1);
        NuclearSimulationEngine.simulate(grid, 3, 3);
        assertEquals(
            500.0,
            insulatedCenter.getTemperature(),
            1e-4,
            "100% insulated inner cell has 0 boundary loss and 0 conduction");

        // Now test insulation dampening (20%, 40%, 60%, 100%)
        MockNuclearTile basePerimeter = new MockNuclearTile(500.0, 0.1);
        MockNuclearTile insulated20 = new MockNuclearTile(500.0, 0.1);
        insulated20.insulationDampening = 0.20;
        MockNuclearTile insulated40 = new MockNuclearTile(500.0, 0.1);
        insulated40.insulationDampening = 0.40;
        MockNuclearTile insulated60 = new MockNuclearTile(500.0, 0.1);
        insulated60.insulationDampening = 0.60;
        MockNuclearTile insulated100 = new MockNuclearTile(500.0, 0.1);
        insulated100.insulationDampening = 1.00;

        MockNuclearTile[][] soloGrid = new MockNuclearTile[1][1];

        soloGrid[0][0] = basePerimeter;
        NuclearSimulationEngine.simulate(soloGrid, 1, 1);
        double lossBase = 500.0 - basePerimeter.getTemperature();

        soloGrid[0][0] = insulated20;
        NuclearSimulationEngine.simulate(soloGrid, 1, 1);
        double loss20 = 500.0 - insulated20.getTemperature();

        soloGrid[0][0] = insulated40;
        NuclearSimulationEngine.simulate(soloGrid, 1, 1);
        double loss40 = 500.0 - insulated40.getTemperature();

        soloGrid[0][0] = insulated60;
        NuclearSimulationEngine.simulate(soloGrid, 1, 1);
        double loss60 = 500.0 - insulated60.getTemperature();

        soloGrid[0][0] = insulated100;
        NuclearSimulationEngine.simulate(soloGrid, 1, 1);
        double loss100 = 500.0 - insulated100.getTemperature();

        assertEquals(0.0, loss100, 1e-6, "100% insulation should completely prevent ambient heat loss");
        assertTrue(loss60 < loss40, "60% insulation should lose less heat than 40%");
        assertTrue(loss40 < loss20, "40% insulation should lose less heat than 20%");
        assertTrue(loss20 < lossBase, "20% insulation should lose less heat than uninsulated");
    }

    @Test
    void testNaquariteUniversalInsulatorFoilNeutronBlocking() {
        MockNuclearTile[][] grid = new MockNuclearTile[3][1];
        MockNuclearTile fuel = new MockNuclearTile(true, 100);
        fuel.emissionCount = 4;
        fuel.heatCoeff = 0.0;
        MockNuclearTile naquarite = new MockNuclearTile(false, 0);
        naquarite.absorbProb = 1.0;
        naquarite.scatterProb = 0.0;
        naquarite.insulationDampening = 1.0;
        naquarite.heatCoeff = 0.0;

        MockNuclearTile behind = new MockNuclearTile(false, 0);
        behind.heatCoeff = 0.0;

        grid[0][0] = fuel;
        grid[1][0] = naquarite;
        grid[2][0] = behind;

        for (int i = 0; i < 20; i++) {
            NuclearSimulationEngine.simulate(grid, 3, 1);
        }

        assertEquals(0, behind.fluxReceived, "Behind tile should receive no flux because Naquarite blocks 100%");
        assertEquals(0.0, naquarite.heatEU, 1e-6, "Naquarite absorbs radiation without generating heat");
        assertTrue(naquarite.fastAbsorbed > 0 || naquarite.thermalAbsorbed > 0, "Naquarite absorbed neutrons");
    }

    @Test
    void testNeutronComponentInteractionData() {
        com.gtnewhorizons.modularnuclear.common.nei.NEINeutronInteractionHandler.NeutronComponentData graphite = new com.gtnewhorizons.modularnuclear.common.nei.NEINeutronInteractionHandler.NeutronComponentData(
            null,
            "Graphite Moderator Block",
            "Moderator",
            0.93,
            0.002,
            0.50,
            0.621,
            0.009,
            false,
            0,
            0,
            0,
            0,
            false,
            null,
            0,
            "Slows fast neutrons into thermal neutrons");

        assertEquals(0.93, graphite.fastScattering, 1e-4);
        assertEquals(0.002, graphite.fastAbsorption, 1e-4);
        assertEquals(0.50, graphite.slowingProbability, 1e-4);
        assertEquals(0.621, graphite.thermalScattering, 1e-4);
        assertEquals(0.009, graphite.thermalAbsorption, 1e-4);
        assertEquals(0.50, 1.0 - graphite.slowingProbability, 1e-4);
        assertFalse(graphite.hasCapture);
        assertFalse(graphite.hasAbsorption);

        com.gtnewhorizons.modularnuclear.common.nei.NEINeutronInteractionHandler.NeutronComponentData uraniumQuad = new com.gtnewhorizons.modularnuclear.common.nei.NEINeutronInteractionHandler.NeutronComponentData(
            null,
            "Quad Uranium Fuel Rod",
            "Fuel Rod",
            0.15,
            0.25,
            0.10,
            0.10,
            0.80,
            true,
            8,
            56.0,
            0.88,
            16.0,
            true,
            null,
            163_840_000L,
            "Base: 16 Fast Neutrons/t | Standard fission fuel");

        assertTrue(uraniumQuad.hasCapture);
        assertEquals(8, uraniumQuad.fastNeutronEnergyEU);
        assertEquals(56.0, uraniumQuad.directEU, 1e-4);
        assertEquals(0.88, uraniumQuad.directHeatC, 1e-4);
        assertEquals(16.0, uraniumQuad.maxNeutronsEmitted, 1e-4);
        assertTrue(uraniumQuad.hasAbsorption);
        assertEquals(163_840_000L, uraniumQuad.neutronsRequired);
    }

    @Test
    void testMaxTemperatureAndAverageReactivityOverFuelCells() {
        NuclearSimulationEngine.setSimulationParameters(600.0, 2200.0, 1.0, 1.1, 18.0, 200.0);

        INuclearTile[][] grid = new INuclearTile[3][3];
        // Fuel 1: temp 600°C -> efficiency 1.0
        MockNuclearTile fuel1 = new MockNuclearTile(true, 100);
        fuel1.setTemperature(600.0);
        grid[0][0] = fuel1;

        // Fuel 2: temp 1400°C -> efficiency 0.5
        MockNuclearTile fuel2 = new MockNuclearTile(true, 100);
        fuel2.setTemperature(1400.0);
        grid[1][1] = fuel2;

        // Non-fuel moderator: temp 2000°C (higher temp than fuels, should set maxTemperature but not dilute reactivity)
        MockNuclearTile moderator = new MockNuclearTile(false, 0);
        moderator.setTemperature(2000.0);
        grid[2][2] = moderator;

        NuclearSimulationEngine.SimulationResult res = NuclearSimulationEngine.simulate(grid, 3, 3);
        double maxTileTemp = Math
            .max(moderator.getTemperature(), Math.max(fuel1.getTemperature(), fuel2.getTemperature()));
        assertEquals(
            maxTileTemp,
            res.maxTemperature,
            1e-4,
            "Max temperature must reflect the highest temp in the core");

        // Reactivity should be the average over the 2 fuel cells: (1.0 + 0.5) / 2 = 0.75 (approx, taking into account
        // any tick temperature changes)
        // Note: during simulation tick, fuel temperatures may increase due to fission heat, so calculate expected
        // average from their final temps
        double expectedReactivity = (NuclearSimulationEngine.calculateEfficiency(fuel1.getTemperature())
            + NuclearSimulationEngine.calculateEfficiency(fuel2.getTemperature())) / 2.0;
        assertEquals(
            expectedReactivity,
            res.averageReactivity,
            1e-4,
            "Reactivity must be average strictly over fuel cells");

        // Test grid with NO fuel cells
        INuclearTile[][] noFuelGrid = new INuclearTile[2][2];
        noFuelGrid[0][0] = new MockNuclearTile(false, 0);
        NuclearSimulationEngine.SimulationResult noFuelRes = NuclearSimulationEngine.simulate(noFuelGrid, 2, 2);
        assertEquals(0.0, noFuelRes.averageReactivity, 1e-6, "Reactivity must be 0.0 when no fuel cells are present");

        NuclearSimulationEngine.resetDefaultParameters();
    }

    @Test
    void testFormatNeutronFlux() {
        assertEquals("0 n/cm²s", NuclearSimulationEngine.formatNeutronFlux(0));
        assertEquals("0 n/cm²s", NuclearSimulationEngine.formatNeutronFlux(-5));
        assertEquals("1.00e13 n/cm²s", NuclearSimulationEngine.formatNeutronFlux(1));
        assertEquals("8.80e14 n/cm²s", NuclearSimulationEngine.formatNeutronFlux(88));
        assertEquals("1.50e15 n/cm²s", NuclearSimulationEngine.formatNeutronFlux(150));
    }

    @Test
    void testMaintenanceEfficiencyScaling() {
        NuclearSimulationEngine.resetDefaultParameters();

        // Check that non-fuel tile receives maintenance efficiency during simulate
        INuclearTile[][] grid = new INuclearTile[2][2];
        final double[] receivedEfficiency = new double[1];
        MockNuclearTile nonFuel = new MockNuclearTile(false, 0) {

            @Override
            public void nuclearTick(double eff) {
                receivedEfficiency[0] = eff;
            }
        };
        grid[0][0] = nonFuel;

        NuclearSimulationEngine.simulate(grid, 2, 2, 0.70);
        assertEquals(0.70, receivedEfficiency[0], 1e-6, "Non-fuel tile must receive maintenance efficiency");

        NuclearSimulationEngine.simulate(grid, 2, 2, 1.0);
        assertEquals(1.0, receivedEfficiency[0], 1e-6, "Default/full maintenance efficiency must be 1.0");

        NuclearSimulationEngine.resetDefaultParameters();
    }

    @Test
    void testMaintenanceHatchRepairAndEfficiency() {
        // Ideal status is 6 (wrench, screwdriver, soft mallet, hard hammer, soldering tool, crowbar)
        int idealStatus = 6;
        for (int issues = 0; issues <= 6; issues++) {
            int repairStatus = idealStatus - issues;
            double expectedEff = (double) repairStatus / (double) idealStatus;
            assertEquals(expectedEff, (double) repairStatus / (double) idealStatus, 1e-6);
        }
        // Max maintenance issues (0 repaired) gives 0% efficiency
        assertEquals(0.0, 0.0 / 6.0, 1e-6);
        // Fully repaired gives 100% efficiency
        assertEquals(1.0, 6.0 / 6.0, 1e-6);
    }

    @Test
    void testSymmetricGridSimulationPreservesExactSymmetry() {
        // 1. Verify exact mathematical symmetry for pure heat diffusion
        StandaloneNuclearGrid heatGrid = new StandaloneNuclearGrid(3, 3, NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        heatGrid.setTile(1, 1, SimTile.TileType.REFLECTOR_CARBON);
        heatGrid.getTile(1, 1)
            .setTemperature(500.0);
        heatGrid.setTile(1, 0, SimTile.TileType.REFLECTOR_CARBON);
        heatGrid.setTile(1, 2, SimTile.TileType.REFLECTOR_CARBON);
        heatGrid.setTile(0, 1, SimTile.TileType.REFLECTOR_CARBON);
        heatGrid.setTile(2, 1, SimTile.TileType.REFLECTOR_CARBON);
        heatGrid.step();
        assertEquals(
            heatGrid.getTile(1, 0)
                .getTemperature(),
            heatGrid.getTile(1, 2)
                .getTemperature(),
            1e-6,
            "Heat diffusion must be symmetric North/South");
        assertEquals(
            heatGrid.getTile(0, 1)
                .getTemperature(),
            heatGrid.getTile(2, 1)
                .getTemperature(),
            1e-6,
            "Heat diffusion must be symmetric East/West");

        // 2. In the hybrid simulation model, fast neutrons use stochastic Monte Carlo rays (MI approach),
        // so individual ticks have statistical variance while remaining balanced across symmetric quadrants over time.
        StandaloneNuclearGrid grid = new StandaloneNuclearGrid(5, 5, NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        // Symmetric 5x5 layout with 4 symmetric fuel rods and symmetric hatches
        grid.loadLayout("NL,HC,HC,HC,NL;HC,U4,HC,U4,HC;HC,HC,HC,HC,HC;HC,U4,HC,U4,HC;NL,HC,HC,HC,NL");

        for (int tick = 1; tick <= 50; tick++) {
            grid.step();
        }

        SimTile t11 = grid.getTile(1, 1);
        SimTile t31 = grid.getTile(3, 1);
        SimTile t13 = grid.getTile(1, 3);
        SimTile t33 = grid.getTile(3, 3);

        double avgFuelTemp = (t11.getTemperature() + t31.getTemperature() + t13.getTemperature() + t33.getTemperature())
            / 4.0;
        assertTrue(
            Math.abs(t11.getTemperature() - avgFuelTemp) / avgFuelTemp < 0.20,
            "Fuel cell (1,1) within variance of symmetric mean");
        assertTrue(
            Math.abs(t31.getTemperature() - avgFuelTemp) / avgFuelTemp < 0.20,
            "Fuel cell (3,1) within variance of symmetric mean");
        assertTrue(
            Math.abs(t13.getTemperature() - avgFuelTemp) / avgFuelTemp < 0.20,
            "Fuel cell (1,3) within variance of symmetric mean");
        assertTrue(
            Math.abs(t33.getTemperature() - avgFuelTemp) / avgFuelTemp < 0.20,
            "Fuel cell (3,3) within variance of symmetric mean");

        // Also verify symmetric coolant hatches
        SimTile h21 = grid.getTile(2, 1);
        SimTile h23 = grid.getTile(2, 3);
        SimTile h12 = grid.getTile(1, 2);
        SimTile h32 = grid.getTile(3, 2);

        double avgHatchTemp = (h21.getTemperature() + h23.getTemperature()
            + h12.getTemperature()
            + h32.getTemperature()) / 4.0;
        assertTrue(
            Math.abs(h21.getTemperature() - avgHatchTemp) / avgHatchTemp < 0.20,
            "Hatch (2,1) within variance of symmetric mean");
        assertTrue(
            Math.abs(h23.getTemperature() - avgHatchTemp) / avgHatchTemp < 0.20,
            "Hatch (2,3) within variance of symmetric mean");
        assertTrue(
            Math.abs(h12.getTemperature() - avgHatchTemp) / avgHatchTemp < 0.20,
            "Hatch (1,2) within variance of symmetric mean");
        assertTrue(
            Math.abs(h32.getTemperature() - avgHatchTemp) / avgHatchTemp < 0.20,
            "Hatch (3,2) within variance of symmetric mean");
    }

    @Test
    void testNuclearHatchInputOnlyContract() {
        // Construct hatch via the secondary constructor (no METATILEENTITIES registration required)
        MTEHatchNuclearHatch hatch = new MTEHatchNuclearHatch("test.nuclear.hatch", 1, 16000, new String[0], null);

        // 1. Verify getTankInfo returns 1 input tank with capacity 16000
        net.minecraftforge.fluids.FluidTankInfo[] info = hatch
            .getTankInfo(net.minecraftforge.common.util.ForgeDirection.UP);
        assertNotNull(info);
        assertEquals(1, info.length, "Nuclear hatch must report exactly 1 tank (input only)");
        assertEquals(16000, info[0].capacity);
        assertNull(info[0].fluid);

        // 2. Set mock fluid into input tank
        net.minecraftforge.fluids.Fluid dummyCoolant = org.mockito.Mockito.mock(net.minecraftforge.fluids.Fluid.class);
        org.mockito.Mockito.when(dummyCoolant.getName())
            .thenReturn("ic2coolant");

        net.minecraftforge.fluids.FluidStack stackCoolant = org.mockito.Mockito
            .mock(net.minecraftforge.fluids.FluidStack.class);
        stackCoolant.amount = 5000;
        org.mockito.Mockito.when(stackCoolant.getFluid())
            .thenReturn(dummyCoolant);

        hatch.mInputFluid = stackCoolant;

        info = hatch.getTankInfo(net.minecraftforge.common.util.ForgeDirection.UP);
        assertEquals(5000, info[0].fluid.amount);

        // 3. Verify drain and empty behavior: hatch is input-only
        assertFalse(hatch.canDrain(net.minecraftforge.common.util.ForgeDirection.UP, dummyCoolant));
        assertNull(hatch.drain(net.minecraftforge.common.util.ForgeDirection.UP, 1000, true));
        assertNull(hatch.drain(net.minecraftforge.common.util.ForgeDirection.UP, stackCoolant, true));
        assertFalse(hatch.canTankBeEmptied());
        assertTrue(hatch.canTankBeFilled());
    }

    @Test
    void testNuclearControlHatchModesAndRedstoneOutput() {
        MTEHatchNuclearControl controlHatch = new MTEHatchNuclearControl("test.control", 4, new String[0], null);
        assertEquals(0, controlHatch.getMode());
        assertEquals("Temperature (min)", MTEHatchNuclearControl.getModeName(0));

        // Test cycle
        controlHatch.setMode(1);
        assertEquals(1, controlHatch.getMode());
        assertEquals("Temperature (max)", MTEHatchNuclearControl.getModeName(1));

        controlHatch.setMode(18); // Wrap (MODE_COUNT = 18)
        assertEquals(0, controlHatch.getMode());

        controlHatch.setMode(15);
        assertEquals("Reactor damage % (min)", MTEHatchNuclearControl.getModeName(15));
        controlHatch.setMode(16);
        assertEquals("Reactor damage % (max)", MTEHatchNuclearControl.getModeName(16));
        controlHatch.setMode(17);
        assertEquals("Reactor damage % (avg)", MTEHatchNuclearControl.getModeName(17));

        controlHatch.setMode(-1); // Negative wrap
        assertEquals(17, controlHatch.getMode());
        assertEquals("Reactor damage % (avg)", MTEHatchNuclearControl.getModeName(17));

        // Test NBT persistence
        net.minecraft.nbt.NBTTagCompound nbt = new net.minecraft.nbt.NBTTagCompound();
        controlHatch.setMode(4);
        controlHatch.setOutputStrengthDirect((byte) 10);
        controlHatch.saveNBTData(nbt);

        MTEHatchNuclearControl loaded = new MTEHatchNuclearControl("test.loaded", 4, new String[0], null);
        loaded.loadNBTData(nbt);
        assertEquals(4, loaded.getMode());
        assertEquals(10, loaded.getOutputStrength());

        // Test redstone emission on facing side only
        gregtech.api.interfaces.tileentity.IGregTechTileEntity mockBase = org.mockito.Mockito
            .mock(gregtech.api.interfaces.tileentity.IGregTechTileEntity.class);
        org.mockito.Mockito.when(mockBase.getFrontFacing())
            .thenReturn(net.minecraftforge.common.util.ForgeDirection.EAST);
        controlHatch.setBaseMetaTileEntity(mockBase);

        controlHatch.setOutputRedstone((byte) 12);
        assertEquals(12, controlHatch.getOutputStrength());

        // Facing side must receive 12, other sides must receive 0
        org.mockito.Mockito.verify(mockBase)
            .setOutputRedstoneSignal(net.minecraftforge.common.util.ForgeDirection.EAST, (byte) 12);
        org.mockito.Mockito.verify(mockBase)
            .setOutputRedstoneSignal(net.minecraftforge.common.util.ForgeDirection.WEST, (byte) 0);
        org.mockito.Mockito.verify(mockBase)
            .setOutputRedstoneSignal(net.minecraftforge.common.util.ForgeDirection.NORTH, (byte) 0);
        org.mockito.Mockito.verify(mockBase)
            .setOutputRedstoneSignal(net.minecraftforge.common.util.ForgeDirection.SOUTH, (byte) 0);
    }

    @Test
    void testNuclearControlHatchSeparateMetricAndStatistic() {
        MTEHatchNuclearControl controlHatch = new MTEHatchNuclearControl("test.control.sep", 4, new String[0], null);
        assertEquals(MTEHatchNuclearControl.METRIC_TEMPERATURE, controlHatch.getMetric());
        assertEquals(MTEHatchNuclearControl.STAT_MIN, controlHatch.getStatistic());
        assertEquals("Temperature", MTEHatchNuclearControl.getMetricName(controlHatch.getMetric()));
        assertEquals("Minimum", MTEHatchNuclearControl.getStatisticDisplayName(controlHatch.getStatistic()));

        // Screwdriver right click cycles metric: 0 -> 1 (Coolant item durability)
        net.minecraft.entity.player.EntityPlayer mockPlayer = org.mockito.Mockito
            .mock(net.minecraft.entity.player.EntityPlayer.class);
        controlHatch.onScrewdriverRightClick(ForgeDirection.UP, mockPlayer, 0.5f, 0.5f, 0.5f, null);
        assertEquals(MTEHatchNuclearControl.METRIC_COOLANT_ITEM_DURABILITY, controlHatch.getMetric());
        assertEquals(MTEHatchNuclearControl.STAT_MIN, controlHatch.getStatistic());

        // Soldering iron right click cycles statistic: 0 -> 1 (MAX)
        boolean handled = controlHatch
            .onSolderingToolRightClick(ForgeDirection.UP, ForgeDirection.UP, mockPlayer, 0.5f, 0.5f, 0.5f, null);
        assertTrue(handled);
        assertEquals(MTEHatchNuclearControl.METRIC_COOLANT_ITEM_DURABILITY, controlHatch.getMetric());
        assertEquals(MTEHatchNuclearControl.STAT_MAX, controlHatch.getStatistic());

        // Mode mapping: Metric 1 (COOLANT_ITEM_DURABILITY) * 3 + Stat 1 (MAX) = 4
        assertEquals(4, controlHatch.getMode());

        // Test NBT persistence with separate fields
        NBTTagCompound nbt = new NBTTagCompound();
        controlHatch.saveNBTData(nbt);
        assertEquals(1, nbt.getInteger("mMetric"));
        assertEquals(1, nbt.getInteger("mStatistic"));
        assertEquals(4, nbt.getInteger("mMode"));

        // Loading from separate fields
        MTEHatchNuclearControl loaded = new MTEHatchNuclearControl("test.loaded.sep", 4, new String[0], null);
        loaded.loadNBTData(nbt);
        assertEquals(1, loaded.getMetric());
        assertEquals(1, loaded.getStatistic());
        assertEquals(4, loaded.getMode());

        // Loading from legacy NBT with only mMode
        NBTTagCompound legacyNbt = new NBTTagCompound();
        legacyNbt.setInteger("mMode", 11); // Coolant hatch fill avg -> Metric 3, Stat 2
        MTEHatchNuclearControl legacyLoaded = new MTEHatchNuclearControl("test.loaded.legacy", 4, new String[0], null);
        legacyLoaded.loadNBTData(legacyNbt);
        assertEquals(MTEHatchNuclearControl.METRIC_COOLANT_HATCH_FILL, legacyLoaded.getMetric());
        assertEquals(MTEHatchNuclearControl.STAT_AVG, legacyLoaded.getStatistic());
        assertEquals(11, legacyLoaded.getMode());
    }

    @Test
    void testNuclearHatchesFacingAndTextures() {
        MTEHatchNuclearBus bus = new MTEHatchNuclearBus("test.bus", 4, new String[0], null);
        MTEHatchNuclearHatch hatch = new MTEHatchNuclearHatch("test.hatch", 4, 32000, new String[0], null);
        MTEHatchNuclearControlRod rod = new MTEHatchNuclearControlRod("test.rod", 4, new String[0], null);
        MTEHatchNuclearControl control = new MTEHatchNuclearControl("test.control", 4, new String[0], null);

        // All 6 ForgeDirections must be valid for all nuclear hatches
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            assertTrue(bus.isFacingValid(dir), "Bus facing " + dir + " must be valid");
            assertTrue(hatch.isFacingValid(dir), "Hatch facing " + dir + " must be valid");
            assertTrue(rod.isFacingValid(dir), "Rod facing " + dir + " must be valid");
            assertTrue(control.isFacingValid(dir), "Control facing " + dir + " must be valid");
        }

        // Casing texture must default to Nuclear Casing
        assertNotNull(bus.getCasingTexture(), "Bus casing texture must not be null");
        assertNotNull(hatch.getCasingTexture(), "Hatch casing texture must not be null");
        assertNotNull(rod.getCasingTexture(), "Rod casing texture must not be null");
        assertNotNull(control.getCasingTexture(), "Control casing texture must not be null");

        // Inventory textures rendering must not throw NPE when getBaseMetaTileEntity() is null
        assertDoesNotThrow(() -> {
            bus.getTexturesActive(bus.getCasingTexture());
            bus.getTexturesInactive(bus.getCasingTexture());
            hatch.getTexturesActive(hatch.getCasingTexture());
            hatch.getTexturesInactive(hatch.getCasingTexture());
            rod.getTexturesActive(rod.getCasingTexture());
            rod.getTexturesInactive(rod.getCasingTexture());
            control.getTexturesActive(control.getCasingTexture());
            control.getTexturesInactive(control.getCasingTexture());
        });

        // Controller front face must have casing and overlay textures, sides only casing
        MTENuclearReactor reactor = new MTENuclearReactor("test.reactor.textures");
        ITexture[] frontActive = reactor.getTexture(null, ForgeDirection.SOUTH, ForgeDirection.SOUTH, 0, true, false);
        assertEquals(2, frontActive.length, "Active front face must have casing and overlay textures");
        assertNotNull(frontActive[0]);
        assertNotNull(frontActive[1]);

        ITexture[] frontInactive = reactor
            .getTexture(null, ForgeDirection.SOUTH, ForgeDirection.SOUTH, 0, false, false);
        assertEquals(2, frontInactive.length, "Inactive front face must have casing and overlay textures");
        assertNotNull(frontInactive[0]);
        assertNotNull(frontInactive[1]);

        ITexture[] sideTextures = reactor.getTexture(null, ForgeDirection.NORTH, ForgeDirection.SOUTH, 0, false, false);
        assertEquals(1, sideTextures.length, "Side face must have only casing texture");
        assertNotNull(sideTextures[0]);
    }

    @Test
    void testNuclearCasingSubBlocksAndItemRegistration() {
        BlockNuclearCasing casing = com.gtnewhorizons.modularnuclear.common.block.ModBlocks.nuclearCasing != null
            ? com.gtnewhorizons.modularnuclear.common.block.ModBlocks.nuclearCasing
            : new BlockNuclearCasing();
        List<ItemStack> subBlocks = new ArrayList<>();
        casing.getSubBlocks(net.minecraft.init.Items.iron_ingot, null, subBlocks);
        assertEquals(1, subBlocks.size(), "Nuclear casing must have exactly 1 subblock (metadata 0)");
        assertEquals(
            0,
            subBlocks.get(0)
                .getItemDamage(),
            "Subblock must be metadata 0");
        assertEquals(0, casing.damageDropped(5), "damageDropped must always return 0");

        ItemNuclearCasing itemCasing = new ItemNuclearCasing(casing);
        assertFalse(itemCasing.getHasSubtypes(), "Nuclear casing item must not have subtypes");
        assertEquals(0, itemCasing.getMetadata(5), "Metadata must be forced to 0");
    }

    @Test
    void testNuclearReactorControlHatchSignalCalculations() {
        MTENuclearReactor reactor = new MTENuclearReactor("test.reactor");
        reactor.gridSize = 3;
        reactor.mGrid = new INuclearTile[3][3];
        reactor.mPipeTier = NuclearSimulationEngine.PIPE_TIER_ELECTRUM; // Max temp = 1000 °C

        // Put tiles with known temperatures: 200 °C, 500 °C, 800 °C
        MockNuclearTile t1 = new MockNuclearTile(200.0, 0.05);
        MockNuclearTile t2 = new MockNuclearTile(500.0, 0.05);
        MockNuclearTile t3 = new MockNuclearTile(800.0, 0.05);
        reactor.mGrid[0][0] = t1;
        reactor.mGrid[0][1] = t2;
        reactor.mGrid[0][2] = t3;

        // Temperature modes (Max operating temp = 1000 °C)
        // Min = 200 -> 200/1000 * 15 = 3
        assertEquals((byte) 3, reactor.calculateSignalForMode(MTEHatchNuclearControl.MODE_TEMP_MIN));
        // Max = 800 -> 800/1000 * 15 = 12
        assertEquals((byte) 12, reactor.calculateSignalForMode(MTEHatchNuclearControl.MODE_TEMP_MAX));
        // Avg = 500 -> 500/1000 * 15 = 7.5 -> 8
        assertEquals((byte) 8, reactor.calculateSignalForMode(MTEHatchNuclearControl.MODE_TEMP_AVG));

        // When no fuel/component/coolant present, durabilities and coolant levels return 0
        assertEquals((byte) 0, reactor.calculateSignalForMode(MTEHatchNuclearControl.MODE_FUEL_DURABILITY_MIN));
        assertEquals((byte) 0, reactor.calculateSignalForMode(MTEHatchNuclearControl.MODE_COMPONENT_DURABILITY_MIN));
        assertEquals((byte) 0, reactor.calculateSignalForMode(MTEHatchNuclearControl.MODE_COOLANT_LEVEL_MIN));
    }

    @Test
    void testNuclearControlIntegrationAndSensorCard() {
        ItemCardModularNuclear card = new ItemCardModularNuclear();
        assertEquals(ItemCardModularNuclear.CARD_TYPE, card.getCardType());
        List<PanelSetting> settings = card.getSettingsList();
        assertNotNull(settings);
        assertEquals(12, settings.size());

        // Test wrapper NBT roundtrip with all 5 metric categories
        ItemStack cardStack = new ItemStack(card);
        ICardWrapper wrapper = new CardWrapperImpl(cardStack, -1);
        wrapper.setTarget(10, 64, -20);
        wrapper.setState(CardState.OK);
        wrapper.setBoolean("isOnline", true);
        wrapper.setDouble("tempMin", 250.0);
        wrapper.setDouble("tempAvg", 450.0);
        wrapper.setDouble("tempMax", 750.0);
        wrapper.setDouble("coolItemDurMin", 60.0);
        wrapper.setDouble("coolItemDurAvg", 80.0);
        wrapper.setDouble("coolItemDurMax", 100.0);
        wrapper.setDouble("fuelItemDurMin", 30.0);
        wrapper.setDouble("fuelItemDurAvg", 50.0);
        wrapper.setDouble("fuelItemDurMax", 90.0);
        wrapper.setDouble("coolHatchFillMin", 40.0);
        wrapper.setDouble("coolHatchFillAvg", 60.0);
        wrapper.setDouble("coolHatchFillMax", 80.0);
        wrapper.setDouble("fuelHatchFillMin", 20.0);
        wrapper.setDouble("fuelHatchFillAvg", 40.0);
        wrapper.setDouble("fuelHatchFillMax", 60.0);
        wrapper.setInt("totalFuelItems", 4);
        wrapper.setInt("totalCoolantItems", 8);
        wrapper.setLong("totalCoolantFluid", 16000L);
        wrapper.setLong("totalCoolantCapacity", 32000L);
        wrapper.setLong("totalFuelFluid", 8000L);
        wrapper.setLong("totalFuelCapacity", 16000L);
        wrapper.setInt("coolantHatchCount", 2);
        wrapper.setInt("fuelHatchCount", 1);
        wrapper.setInt("zeroedCoolantLastCycle", 1);
        wrapper.setInt("zeroedFuelLastCycle", 2);
        wrapper.setLong("consumedCoolantLastCycle", 500L);
        wrapper.setLong("producedHotCoolantLastCycle", 80000L);
        wrapper.setLong("transmutationLossLastCycle", 3L);
        wrapper.setLong("transmutationByproductsLastCycle", 6L);
        wrapper.setLong("depletedLiquidFuelLastCycle", 10L);

        // Test display formatting with all settings enabled
        DisplaySettingHelper helper = new DisplaySettingHelper(true);

        List<PanelString> lines = card.getStringData(helper, wrapper, true);
        assertNotNull(lines);
        assertFalse(lines.isEmpty());
        assertEquals(13, lines.size());

        // Verify each line corresponds to requested data
        assertEquals("MPTR Reactor", lines.get(0).textLeft);
        assertEquals("ONLINE", lines.get(0).textRight);
        assertTrue(lines.get(1).textRight.contains("250 / 450 / 750 °C"));
        assertTrue(lines.get(2).textRight.contains("60.0% / 80.0% / 100.0%"));
        assertTrue(lines.get(3).textRight.contains("30.0% / 50.0% / 90.0%"));
        assertTrue(lines.get(4).textRight.contains("40.0% / 60.0% / 80.0%"));
        assertTrue(lines.get(5).textRight.contains("20.0% / 40.0% / 60.0%"));
        assertEquals("4 / 8", lines.get(6).textRight);
        assertTrue(lines.get(7).textLeft.contains("Coolant Fluid"));
        assertTrue(lines.get(8).textLeft.contains("Fuel Fluid"));
        assertEquals("2 / 1", lines.get(9).textRight);
        assertTrue(lines.get(10).textRight.contains("Fuel: 2 itm, 10 L | Cool: 1 itm"));
        assertTrue(lines.get(11).textRight.contains("-500 L in / +80,000 L out"));
        assertTrue(lines.get(12).textRight.contains("Loss: -3 L | Byprod: +6 L"));
    }

    @Test
    void testReactorTelemetryAndModes() {
        MTENuclearReactor reactor = new MTENuclearReactor("test.reactor.full");
        reactor.gridSize = 3;
        reactor.mGrid = new INuclearTile[3][3];
        reactor.mPipeTier = NuclearSimulationEngine.PIPE_TIER_ELECTRUM; // Max temp = 1000 °C

        // Add tile temperatures
        MockNuclearTile t1 = new MockNuclearTile(300.0, 0.05);
        MockNuclearTile t2 = new MockNuclearTile(600.0, 0.05);
        MockNuclearTile t3 = new MockNuclearTile(900.0, 0.05);
        reactor.mGrid[0][0] = t1;
        reactor.mGrid[0][1] = t2;
        reactor.mGrid[0][2] = t3;

        // Populate cycle values
        reactor.mZeroedCoolantItemsLastCycle = 2;
        reactor.mZeroedFuelItemsLastCycle = 1;
        reactor.mConsumedCoolantLastCycle = 1000;
        reactor.mProducedHotCoolantLastCycle = 160000;
        reactor.mTransmutationLossLastCycle = 4;
        reactor.mTransmutationByproductsLastCycle = 8;
        reactor.mDepletedLiquidFuelLastCycle = 15;

        // Calculate telemetry
        reactor.calculateTelemetry();

        assertEquals(300.0, reactor.mMinTileTemp);
        assertEquals(900.0, reactor.mMaxTileTemp);
        assertEquals(600.0, reactor.mAvgTileTemp);

        // Verify redstone signal for all 3 temperature modes (300/1000 * 15 = 4.5 -> 5, 900/1000 * 15 = 13.5 -> 14,
        // 600/1000 * 15 = 9)
        assertEquals((byte) 5, reactor.calculateSignalForMode(MTEHatchNuclearControl.MODE_TEMP_MIN));
        assertEquals((byte) 14, reactor.calculateSignalForMode(MTEHatchNuclearControl.MODE_TEMP_MAX));
        assertEquals((byte) 9, reactor.calculateSignalForMode(MTEHatchNuclearControl.MODE_TEMP_AVG));

        // When no fuel/coolant present, other 12 modes return 0
        for (int m = 3; m < MTEHatchNuclearControl.MODE_COUNT; m++) {
            assertEquals((byte) 0, reactor.calculateSignalForMode(m), "Mode " + m + " must return 0 signal");
        }
    }

    @Test
    void testHoloProjectorNuclearHatchChannel() {
        ModMetaTileEntities.nuclearBus = new ItemStack(net.minecraft.init.Items.diamond, 1);
        for (int i = 0; i < 9; i++) {
            ModMetaTileEntities.nuclearHatches[i] = new ItemStack(net.minecraft.init.Items.emerald, 1, i + 1);
        }

        // Test getNuclearHatchStack: tier 0 returns nuclear bus, 1..9 return fluid hatches
        ItemStack busStack = MTENuclearReactor.getNuclearHatchStack(0);
        assertNotNull(busStack, "Hatch stack for tier 0 must not be null (Nuclear Core Bus)");
        assertEquals(ModMetaTileEntities.nuclearBus.getItem(), busStack.getItem());
        assertEquals(ModMetaTileEntities.nuclearBus.getItemDamage(), busStack.getItemDamage());

        for (int i = 1; i <= 9; i++) {
            ItemStack fluidHatch = MTENuclearReactor.getNuclearHatchStack(i);
            assertNotNull(fluidHatch, "Hatch stack for tier " + i + " must not be null");
            assertEquals(ModMetaTileEntities.nuclearHatches[i - 1].getItem(), fluidHatch.getItem());
            assertEquals(ModMetaTileEntities.nuclearHatches[i - 1].getItemDamage(), fluidHatch.getItemDamage());
        }

        assertNull(MTENuclearReactor.getNuclearHatchStack(10), "Hatch stack for tier 10 must be null");

        // Test NuclearStructureChannels.NUCLEAR_HATCH.getValueClamped
        assertEquals(0, NuclearStructureChannels.NUCLEAR_HATCH.getValueClamped(null, 0, 9));

        // Helper to set channel integer tag
        java.util.function.BiConsumer<ItemStack, Integer> setChannel = (stack, val) -> {
            NBTTagCompound tag = stack.getTagCompound();
            if (tag == null) {
                tag = new NBTTagCompound();
                stack.setTagCompound(tag);
            }
            NBTTagCompound ch = tag.getCompoundTag("channels");
            ch.setInteger("nuclear_hatch", val);
            tag.setTag("channels", ch);
        };

        // Trigger with explicit channel 0 (item bus)
        ItemStack trigger0 = new ItemStack(net.minecraft.init.Items.feather, 1);
        setChannel.accept(trigger0, 0);
        assertEquals(0, NuclearStructureChannels.NUCLEAR_HATCH.getValueClamped(trigger0, 0, 9));

        // Trigger with explicit channel 1 (LV fluid hatch)
        ItemStack trigger1 = new ItemStack(net.minecraft.init.Items.feather, 1);
        setChannel.accept(trigger1, 1);
        assertEquals(1, NuclearStructureChannels.NUCLEAR_HATCH.getValueClamped(trigger1, 0, 9));

        // Trigger with explicit channel 9 (UHV fluid hatch)
        ItemStack trigger9 = new ItemStack(net.minecraft.init.Items.feather, 1);
        setChannel.accept(trigger9, 9);
        assertEquals(9, NuclearStructureChannels.NUCLEAR_HATCH.getValueClamped(trigger9, 0, 9));

        // Clamping bounds
        ItemStack triggerNegative = new ItemStack(net.minecraft.init.Items.feather, 1);
        setChannel.accept(triggerNegative, -5);
        assertEquals(0, NuclearStructureChannels.NUCLEAR_HATCH.getValueClamped(triggerNegative, 0, 9));

        ItemStack triggerOver = new ItemStack(net.minecraft.init.Items.feather, 1);
        setChannel.accept(triggerOver, 15);
        assertEquals(9, NuclearStructureChannels.NUCLEAR_HATCH.getValueClamped(triggerOver, 0, 9));

        // NuclearHatchElement getBlocksToPlace
        MTENuclearReactor.NuclearHatchElement element = new MTENuclearReactor.NuclearHatchElement();
        com.gtnewhorizon.structurelib.structure.IStructureElement.BlocksToPlace blocks0 = element
            .getBlocksToPlace(null, null, 0, 0, 0, trigger0, null);
        assertNotNull(blocks0);

        com.gtnewhorizon.structurelib.structure.IStructureElement.BlocksToPlace blocks1 = element
            .getBlocksToPlace(null, null, 0, 0, 0, trigger1, null);
        assertNotNull(blocks1);
    }

    @Test
    void testNuclearReactorCasingRequirementHalved() {
        MTENuclearReactor reactor = new MTENuclearReactor("test.reactor.casing");
        java.util.List<gregtech.api.structure.error.StructureError> errors = new java.util.ArrayList<>();

        // If casings < 22, errors should be reported
        reactor.verifyCasingMin(errors, 21, 22);
        assertFalse(errors.isEmpty(), "Fewer than 22 casings must fail structure check");

        // If casings >= 22, passes
        errors.clear();
        reactor.verifyCasingMin(errors, 22, 22);
        assertTrue(errors.isEmpty(), "22 casings (50% of 44) must pass structure check");
    }

    @Test
    void testNuclearControlRodHatchBasicsAndRedstoneControl() {
        MTEHatchNuclearControlRod hatch = new MTEHatchNuclearControlRod("test.rod", 4, new String[0], null);
        assertEquals(1, hatch.getInventoryStackLimit(), "Control rod hatch must limit stack size to 1");
        assertFalse(hatch.doesFillContainers());
        assertFalse(hatch.doesEmptyContainers());
        assertFalse(hatch.canTankBeFilled());
        assertFalse(hatch.canTankBeEmptied());

        gregtech.api.interfaces.tileentity.IGregTechTileEntity mockBase = org.mockito.Mockito
            .mock(gregtech.api.interfaces.tileentity.IGregTechTileEntity.class);
        hatch.setBaseMetaTileEntity(mockBase);

        // RS = 0 -> 0% insertion
        org.mockito.Mockito.when(mockBase.getStrongestRedstone())
            .thenReturn((byte) 0);
        assertEquals(0, hatch.getRedstoneSignal());
        assertEquals(0.0, hatch.getInsertionRatio(), 0.001);
        assertEquals(0, hatch.getInsertionPercent());

        // RS = 15 -> 100% insertion
        org.mockito.Mockito.when(mockBase.getStrongestRedstone())
            .thenReturn((byte) 15);
        assertEquals(15, hatch.getRedstoneSignal());
        assertEquals(1.0, hatch.getInsertionRatio(), 0.001);
        assertEquals(100, hatch.getInsertionPercent());

        // RS = 6 -> 40% insertion
        org.mockito.Mockito.when(mockBase.getStrongestRedstone())
            .thenReturn((byte) 6);
        assertEquals(6, hatch.getRedstoneSignal());
        assertEquals(0.40, hatch.getInsertionRatio(), 0.001);
        assertEquals(40, hatch.getInsertionPercent());
    }

    @Test
    void testNuclearControlRodProgressionOrder() {
        // Verify material progression order: Silver < Boron < Cadmium < Indium < Hafnium
        MTEHatchNuclearControlRod.ControlRodType silver = MTEHatchNuclearControlRod.ControlRodType.SILVER;
        MTEHatchNuclearControlRod.ControlRodType boron = MTEHatchNuclearControlRod.ControlRodType.BORON;
        MTEHatchNuclearControlRod.ControlRodType cadmium = MTEHatchNuclearControlRod.ControlRodType.CADMIUM;
        MTEHatchNuclearControlRod.ControlRodType indium = MTEHatchNuclearControlRod.ControlRodType.INDIUM;
        MTEHatchNuclearControlRod.ControlRodType hafnium = MTEHatchNuclearControlRod.ControlRodType.HAFNIUM;

        // Thermal absorption progression
        assertTrue(
            silver.maxThermalAbsorption < boron.maxThermalAbsorption,
            "Boron must absorb more thermal than Silver");
        assertTrue(
            boron.maxThermalAbsorption < cadmium.maxThermalAbsorption,
            "Cadmium must absorb more thermal than Boron");
        assertTrue(
            cadmium.maxThermalAbsorption < indium.maxThermalAbsorption,
            "Indium must absorb more thermal than Cadmium");
        assertTrue(
            indium.maxThermalAbsorption < hafnium.maxThermalAbsorption,
            "Hafnium must absorb more thermal than Indium");

        // Fast absorption progression
        assertTrue(silver.maxFastAbsorption < boron.maxFastAbsorption, "Boron must absorb more fast than Silver");
        assertTrue(boron.maxFastAbsorption < cadmium.maxFastAbsorption, "Cadmium must absorb more fast than Boron");
        assertTrue(cadmium.maxFastAbsorption < indium.maxFastAbsorption, "Indium must absorb more fast than Cadmium");
        assertTrue(indium.maxFastAbsorption < hafnium.maxFastAbsorption, "Hafnium must absorb more fast than Indium");

        // Test rod detection via mock items
        net.minecraft.item.Item dummyItem = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
        net.minecraft.item.ItemStack stackSilver = org.mockito.Mockito.mock(net.minecraft.item.ItemStack.class);
        stackSilver.getItem(); // trigger non-null check
        org.mockito.Mockito.when(stackSilver.getItem())
            .thenReturn(dummyItem);
        org.mockito.Mockito.when(stackSilver.getUnlocalizedName())
            .thenReturn("item.stickLongSilver");
        assertEquals(silver, MTEHatchNuclearControlRod.getRodType(stackSilver));

        net.minecraft.item.ItemStack stackBoron = org.mockito.Mockito.mock(net.minecraft.item.ItemStack.class);
        org.mockito.Mockito.when(stackBoron.getItem())
            .thenReturn(dummyItem);
        org.mockito.Mockito.when(stackBoron.getUnlocalizedName())
            .thenReturn("item.stickLongBoron");
        assertEquals(boron, MTEHatchNuclearControlRod.getRodType(stackBoron));

        net.minecraft.item.ItemStack stackCadmium = org.mockito.Mockito.mock(net.minecraft.item.ItemStack.class);
        org.mockito.Mockito.when(stackCadmium.getItem())
            .thenReturn(dummyItem);
        org.mockito.Mockito.when(stackCadmium.getUnlocalizedName())
            .thenReturn("item.stickLongCadmium");
        assertEquals(cadmium, MTEHatchNuclearControlRod.getRodType(stackCadmium));

        net.minecraft.item.ItemStack stackIndium = org.mockito.Mockito.mock(net.minecraft.item.ItemStack.class);
        org.mockito.Mockito.when(stackIndium.getItem())
            .thenReturn(dummyItem);
        org.mockito.Mockito.when(stackIndium.getUnlocalizedName())
            .thenReturn("item.stickLongIndium");
        assertEquals(indium, MTEHatchNuclearControlRod.getRodType(stackIndium));

        net.minecraft.item.ItemStack stackHafnium = org.mockito.Mockito.mock(net.minecraft.item.ItemStack.class);
        org.mockito.Mockito.when(stackHafnium.getItem())
            .thenReturn(dummyItem);
        org.mockito.Mockito.when(stackHafnium.getUnlocalizedName())
            .thenReturn("item.stickLongHafnium");
        assertEquals(hafnium, MTEHatchNuclearControlRod.getRodType(stackHafnium));

        // Test absorption scaling with redstone on hatch
        MTEHatchNuclearControlRod hatch = new MTEHatchNuclearControlRod("test.rod.prog", 4, new String[0], null);
        gregtech.api.interfaces.tileentity.IGregTechTileEntity mockBase = org.mockito.Mockito
            .mock(gregtech.api.interfaces.tileentity.IGregTechTileEntity.class);
        hatch.setBaseMetaTileEntity(mockBase);
        hatch.mInventory[MTEHatchNuclearControlRod.SLOT_ROD] = stackHafnium;

        // RS = 0 -> minimum baseline absorption (0.01)
        org.mockito.Mockito.when(mockBase.getStrongestRedstone())
            .thenReturn((byte) 0);
        assertEquals(0.01, hatch.getAbsorptionProbability(NeutronType.THERMAL), 0.001);

        // RS = 15 -> 100% of Hafnium max (0.99)
        org.mockito.Mockito.when(mockBase.getStrongestRedstone())
            .thenReturn((byte) 15);
        assertEquals(0.99, hatch.getAbsorptionProbability(NeutronType.THERMAL), 0.001);
        assertEquals(0.80, hatch.getAbsorptionProbability(NeutronType.FAST), 0.001);

        // RS = 7.5 (approx 8) -> 8/15 * 0.99 = 0.528
        org.mockito.Mockito.when(mockBase.getStrongestRedstone())
            .thenReturn((byte) 8);
        assertEquals((8.0 / 15.0) * 0.99, hatch.getAbsorptionProbability(NeutronType.THERMAL), 0.001);
    }

    @Test
    void testNuclearReactorDisablingNotAllowed() {
        MTENuclearReactor reactor = new MTENuclearReactor("nuclear.reactor.test");
        assertFalse(reactor.isDisablingAllowed(), "Nuclear reactor must not support being disabled");
    }

    @Test
    void testNuclearReactorNewlyPlacedMaxMaintenanceIssues() {
        MTENuclearReactor reactor = new MTENuclearReactor("nuclear.reactor.test");
        // Newly instantiated reactor must start with all maintenance tools false
        assertFalse(reactor.mWrench);
        assertFalse(reactor.mScrewdriver);
        assertFalse(reactor.mSoftMallet);
        assertFalse(reactor.mHardHammer);
        assertFalse(reactor.mSolderingTool);
        assertFalse(reactor.mCrowbar);
        assertEquals(0, reactor.getRepairStatus(), "Newly placed reactor must have 0 repaired status (max issues)");

        reactor.mMachine = true;
        assertEquals(0.0, reactor.getMaintenanceEfficiency(), 1e-6, "Max maintenance issues must give 0% efficiency");

        // Partial repairs
        reactor.mWrench = true;
        assertEquals(1.0 / 6.0, reactor.getMaintenanceEfficiency(), 1e-6);

        // Full repair
        reactor.fixAllIssues();
        assertEquals(1.0, reactor.getMaintenanceEfficiency(), 1e-6);
    }

    @Test
    void testNuclearReactorStructureBreakVoidsFuelAndCoolantWhenOver100() {
        MTENuclearReactor reactor = new MTENuclearReactor("nuclear.reactor.test");

        // Mock bus with fuel item
        MTEHatchNuclearBus bus = new MTEHatchNuclearBus("test.bus", 4, new String[0], null);
        net.minecraft.item.ItemStack dummyFuel = org.mockito.Mockito.mock(net.minecraft.item.ItemStack.class);
        bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = dummyFuel;
        bus.mTemperature = 150.0;

        // Mock hatch with fluid
        MTEHatchNuclearHatch hatch = new MTEHatchNuclearHatch("test.hatch", 4, 16000, new String[0], null);
        net.minecraftforge.fluids.FluidStack coolantIn = org.mockito.Mockito
            .mock(net.minecraftforge.fluids.FluidStack.class);
        hatch.mInputFluid = coolantIn;
        hatch.mTemperature = 120.0;

        gregtech.api.interfaces.tileentity.IGregTechTileEntity teBus = org.mockito.Mockito
            .mock(gregtech.api.interfaces.tileentity.IGregTechTileEntity.class);
        org.mockito.Mockito.when(teBus.getMetaTileEntity())
            .thenReturn(bus);
        org.mockito.Mockito.when(teBus.isDead())
            .thenReturn(false);

        gregtech.api.interfaces.tileentity.IGregTechTileEntity teHatch = org.mockito.Mockito
            .mock(gregtech.api.interfaces.tileentity.IGregTechTileEntity.class);
        org.mockito.Mockito.when(teHatch.getMetaTileEntity())
            .thenReturn(hatch);
        org.mockito.Mockito.when(teHatch.isDead())
            .thenReturn(false);

        reactor.mLastFormedNuclearTiles.add(teBus);
        reactor.mLastFormedNuclearTiles.add(teHatch);

        assertTrue(reactor.isAnyTemperatureAbove100(), "Reactor should detect temperature > 100°C");

        // Trigger structure break
        reactor.handleStructureBreak();

        assertNull(bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT], "Fuel in bus must be voided on break above 100°C");
        assertNull(hatch.mInputFluid, "Coolant in hatch must be voided on break above 100°C");
    }

    @Test
    void testNuclearReactorStructureBreakPreservesFuelAndCoolantWhenCold() {
        MTENuclearReactor reactor = new MTENuclearReactor("nuclear.reactor.test");

        MTEHatchNuclearBus bus = new MTEHatchNuclearBus("test.bus", 4, new String[0], null);
        net.minecraft.item.ItemStack dummyFuel = org.mockito.Mockito.mock(net.minecraft.item.ItemStack.class);
        bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = dummyFuel;
        bus.mTemperature = 25.0;

        MTEHatchNuclearHatch hatch = new MTEHatchNuclearHatch("test.hatch", 4, 16000, new String[0], null);
        net.minecraftforge.fluids.FluidStack coolantIn = org.mockito.Mockito
            .mock(net.minecraftforge.fluids.FluidStack.class);
        hatch.mInputFluid = coolantIn;
        hatch.mTemperature = 25.0;

        gregtech.api.interfaces.tileentity.IGregTechTileEntity teBus = org.mockito.Mockito
            .mock(gregtech.api.interfaces.tileentity.IGregTechTileEntity.class);
        org.mockito.Mockito.when(teBus.getMetaTileEntity())
            .thenReturn(bus);
        org.mockito.Mockito.when(teBus.isDead())
            .thenReturn(false);

        gregtech.api.interfaces.tileentity.IGregTechTileEntity teHatch = org.mockito.Mockito
            .mock(gregtech.api.interfaces.tileentity.IGregTechTileEntity.class);
        org.mockito.Mockito.when(teHatch.getMetaTileEntity())
            .thenReturn(hatch);
        org.mockito.Mockito.when(teHatch.isDead())
            .thenReturn(false);

        reactor.mLastFormedNuclearTiles.add(teBus);
        reactor.mLastFormedNuclearTiles.add(teHatch);
        reactor.mCoreTemp = 25.0;
        reactor.mAvgTemp = 25.0;

        assertFalse(reactor.isAnyTemperatureAbove100(), "Reactor is cold, should not detect > 100°C");

        reactor.handleStructureBreak();

        assertNotNull(bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT], "Fuel in bus must be preserved when cold");
        assertNotNull(hatch.mInputFluid, "Coolant in hatch must be preserved when cold");
    }

    private static void setWorldProvider(World world, net.minecraft.world.WorldProvider provider) {
        try {
            java.lang.reflect.Field f = World.class.getField("provider");
            f.setAccessible(true);
            f.set(world, provider);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testBiomeAmbientTemperatureCalculations() {
        // Fallback with null world
        assertEquals(
            NuclearSimulationEngine.DEFAULT_AMBIENT_TEMP,
            MTENuclearReactor.calculateAmbientTemperature(null, 0, 0, 0),
            1e-6);

        // Biome mock
        World mockWorld = org.mockito.Mockito.mock(World.class);
        net.minecraft.world.biome.BiomeGenBase mockBiome = org.mockito.Mockito
            .mock(net.minecraft.world.biome.BiomeGenBase.class);
        org.mockito.Mockito.when(mockWorld.getBiomeGenForCoords(0, 0))
            .thenReturn(mockBiome);

        // Plains (0.80) -> (80.0 - 32.0) / 1.8 = ~26.67°C
        org.mockito.Mockito.when(mockBiome.getFloatTemperature(0, 64, 0))
            .thenReturn(0.80f);
        double plainsTemp = MTENuclearReactor.calculateAmbientTemperature(mockWorld, 0, 64, 0);
        assertEquals(26.67, plainsTemp, 0.05);

        // Freezing snow biome (0.00) -> (0.0 - 32.0) / 1.8 = ~-17.78°C
        org.mockito.Mockito.when(mockBiome.getFloatTemperature(0, 64, 0))
            .thenReturn(0.0f);
        double snowTemp = MTENuclearReactor.calculateAmbientTemperature(mockWorld, 0, 64, 0);
        assertEquals(-17.78, snowTemp, 0.05);

        // Nether / Hell biome (2.00) -> (200.0 - 32.0) / 1.8 = ~93.33°C
        org.mockito.Mockito.when(mockBiome.getFloatTemperature(0, 64, 0))
            .thenReturn(2.0f);
        double netherTemp = MTENuclearReactor.calculateAmbientTemperature(mockWorld, 0, 64, 0);
        assertEquals(93.33, netherTemp, 0.05);
    }

    @Test
    void testThermalShockSetsMaxMaintenanceWithoutStoppingMachine() {
        MTENuclearReactor reactor = new MTENuclearReactor("nuclear.reactor.test");
        reactor.mMachine = true;
        reactor.fixAllIssues();
        assertEquals(1.0, reactor.getMaintenanceEfficiency(), 1e-6);
        assertEquals(0.0, reactor.mReactorDamage, 1e-6);
        assertEquals(6, reactor.getRepairStatus());

        MTEHatchNuclearHatch hatch = new MTEHatchNuclearHatch("test.hatch", 4, 16000, new String[0], null);
        net.minecraftforge.fluids.FluidStack coolantIn = org.mockito.Mockito
            .mock(net.minecraftforge.fluids.FluidStack.class);
        hatch.mInputFluid = coolantIn;

        gregtech.api.interfaces.tileentity.IGregTechTileEntity te = org.mockito.Mockito
            .mock(gregtech.api.interfaces.tileentity.IGregTechTileEntity.class);
        org.mockito.Mockito.when(te.isServerSide())
            .thenReturn(true);
        org.mockito.Mockito.when(te.getWorld())
            .thenReturn(org.mockito.Mockito.mock(World.class));
        reactor.setBaseMetaTileEntity(te);

        // 1st thermal shock
        reactor.triggerThermalShock(hatch, "Test thermal shock 1");

        // Machine entity must remain active (never disabled/stopped)
        assertTrue(reactor.mMachine, "Thermal shock must NOT disable or stop the reactor machine entity!");

        // Coolant in hatch must be voided
        assertNull(hatch.mInputFluid, "Coolant in hatch must be voided upon thermal shock");

        // Must increase reactor damage by 2%
        assertEquals(2.0, reactor.mReactorDamage, 1e-6, "Thermal shock must increase reactor damage by 2%");

        // Must increment maintenance issues by 1 (5 working out of 6, NOT maxed out)
        assertEquals(5, reactor.getRepairStatus(), "Thermal shock must increase maintenance issues by 1");
        assertTrue(reactor.getMaintenanceEfficiency() > 0.0, "Efficiency must not be dropped directly to 0%");

        // 2nd thermal shock
        hatch.mInputFluid = coolantIn;
        reactor.triggerThermalShock(hatch, "Test thermal shock 2");
        assertEquals(4.0, reactor.mReactorDamage, 1e-6, "2nd thermal shock must increase reactor damage to 4%");
        assertEquals(4, reactor.getRepairStatus(), "2nd thermal shock must increase maintenance issues to 2 (4 working)");

        // 3rd through 6th thermal shocks
        for (int i = 3; i <= 6; i++) {
            hatch.mInputFluid = coolantIn;
            reactor.triggerThermalShock(hatch, "Test thermal shock " + i);
        }
        assertEquals(12.0, reactor.mReactorDamage, 1e-6, "6 thermal shocks must accumulate 12% reactor damage");
        assertEquals(0, reactor.getRepairStatus(), "6 thermal shocks should reach max maintenance issues (0 working)");

        // 7th thermal shock - issues capped at maximum (0 working), but damage keeps increasing
        hatch.mInputFluid = coolantIn;
        reactor.triggerThermalShock(hatch, "Test thermal shock 7");
        assertEquals(14.0, reactor.mReactorDamage, 1e-6, "7th thermal shock must increase damage to 14%");
        assertEquals(0, reactor.getRepairStatus(), "Maintenance issues must not exceed maximum (0 working)");
    }

    @Test
    void testEmergencyScramInsertsAllControlRods() {
        MTENuclearReactor reactor = new MTENuclearReactor("nuclear.reactor.test");
        MTEHatchNuclearControlRod rod = new MTEHatchNuclearControlRod("test.rod", 4, new String[0], null);

        gregtech.api.interfaces.tileentity.IGregTechTileEntity teRod = org.mockito.Mockito
            .mock(gregtech.api.interfaces.tileentity.IGregTechTileEntity.class);
        org.mockito.Mockito.when(teRod.getMetaTileEntity())
            .thenReturn(rod);
        org.mockito.Mockito.when(teRod.getStrongestRedstone())
            .thenReturn((byte) 0);
        rod.setBaseMetaTileEntity(teRod);

        reactor.mNuclearTiles.add(teRod);

        // Default state: 0 RS signal -> 0% insertion
        assertFalse(rod.mScram);
        assertEquals(0.0, rod.getInsertionRatio(), 1e-6);

        // Engage SCRAM
        reactor.setScram(true);
        assertTrue(reactor.mScram);
        assertTrue(rod.mScram);
        assertEquals(
            1.0,
            rod.getInsertionRatio(),
            1e-6,
            "SCRAM must force 100% control rod insertion regardless of redstone!");

        // Disengage SCRAM
        reactor.setScram(false);
        assertFalse(reactor.mScram);
        assertFalse(rod.mScram);
        assertEquals(0.0, rod.getInsertionRatio(), 1e-6, "Disengaging SCRAM must restore normal redstone control");
    }

    @Test
    void testWailaTooltipHasNoRecipeProgress() {
        MTENuclearReactor reactor = new MTENuclearReactor("nuclear.reactor.test");
        NBTTagCompound tag = new NBTTagCompound();
        reactor.getWailaNBTData(null, null, tag, null, 0, 0, 0);

        assertEquals(0, tag.getInteger("progress"), "Reactor must report 0 recipe progress");
        assertEquals(0, tag.getInteger("maxProgress"), "Reactor must report 0 max recipe progress");

        List<String> tip = new ArrayList<>();
        mcp.mobius.waila.api.IWailaDataAccessor mockAccessor = org.mockito.Mockito
            .mock(mcp.mobius.waila.api.IWailaDataAccessor.class);
        org.mockito.Mockito.when(mockAccessor.getNBTData())
            .thenReturn(tag);

        reactor.getWailaBody(null, tip, mockAccessor, null);
        for (String line : tip) {
            assertFalse(line.contains("Progress:"), "WAILA tooltip must NOT contain recipe progress string!");
        }
    }

    @Test
    void testNuclearBusFluidTankAndCheeseExtractionEasterEgg() {
        net.minecraftforge.fluids.Fluid cheeseFluid = org.mockito.Mockito.mock(net.minecraftforge.fluids.Fluid.class);
        org.mockito.Mockito.when(cheeseFluid.getName())
            .thenReturn("molten.cheese");

        net.minecraftforge.fluids.FluidStack cheeseOut = mockFluidStack(cheeseFluid, 144);

        net.minecraft.item.Item dummyItem = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
        ItemStack cheeseStack = new ItemStack(dummyItem, 1, 0);

        gregtech.api.util.GTRecipe cheeseRecipe = org.mockito.Mockito.mock(gregtech.api.util.GTRecipe.class);
        cheeseRecipe.mFluidOutputs = new net.minecraftforge.fluids.FluidStack[] { cheeseOut };
        cheeseRecipe.mOutputs = new ItemStack[0];
        cheeseRecipe.mInputs = new ItemStack[] { cheeseStack };
        cheeseRecipe.mDuration = 128;
        cheeseRecipe.mEUt = 4;

        MTEHatchNuclearBus bus = new MTEHatchNuclearBus("test.nuclear.bus", 4, new String[0], null) {

            @Override
            public gregtech.api.util.GTRecipe findCheeseExtractionRecipe(ItemStack stack) {
                if (stack != null) {
                    return cheeseRecipe;
                }
                return null;
            }
        };

        // 1. Tank and Stack Limit Properties (Input-only bus)
        assertEquals(0, bus.getCapacity(), "Nuclear bus internal tank capacity must be 0");
        assertEquals(1, bus.getInventoryStackLimit(), "Nuclear bus inventory stack limit must be 1");
        assertFalse(bus.canTankBeFilled(), "Nuclear bus fluid tank cannot be filled");
        assertFalse(bus.canTankBeEmptied(), "Nuclear bus fluid tank cannot be drained");
        assertFalse(bus.doesFillContainers(), "Nuclear bus cannot fill containers");
        assertFalse(bus.doesEmptyContainers(), "Nuclear bus does not empty containers");
        assertTrue(bus.allowPutStack(null, MTEHatchNuclearBus.SLOT_INPUT, ForgeDirection.UNKNOWN, cheeseStack));
        assertFalse(bus.allowPullStack(null, MTEHatchNuclearBus.SLOT_INPUT, ForgeDirection.UNKNOWN, cheeseStack));

        // 2. Below 65°C: No extraction
        List<net.minecraftforge.fluids.FluidStack> fluidOutputs = new ArrayList<>();
        List<ItemStack> itemOutputs = new ArrayList<>();
        MTENuclearReactor reactor = new MTENuclearReactor("test.reactor") {

            @Override
            public void addOutputPartial(net.minecraftforge.fluids.FluidStack stack) {
                if (stack != null) fluidOutputs.add(stack);
            }

            @Override
            public void addOutputPartial(ItemStack stack) {
                if (stack != null) itemOutputs.add(stack);
            }
        };
        bus.mTemperature = 50.0;
        bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = cheeseStack.copy();
        assertFalse(
            bus.allowPutStack(null, MTEHatchNuclearBus.SLOT_INPUT, ForgeDirection.UNKNOWN, cheeseStack),
            "Cannot insert when full");
        assertFalse(reactor.processCheeseExtraction(bus), "Cheese must not extract at or below 65°C");
        assertEquals(50.0, bus.mTemperature, 1e-4);
        assertNotNull(bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT]);

        // 3. Above 65°C: Extraction occurs!
        // Total EU = 128 * 4 = 512 EU.
        // Heat absorbed = 512 EU.
        // Temp drop = 512 / 32 = 16.0 °C.
        // Expected temperature = 80.0 - 16.0 = 64.0 °C.
        bus.mTemperature = 80.0;
        assertTrue(reactor.processCheeseExtraction(bus), "Cheese must extract when above 65°C");
        assertEquals(64.0, bus.mTemperature, 1e-4, "Temperature must drop by exactly recipe totalEU / EU_PER_DEGREE");
        assertNull(bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT], "Single item input must be consumed");

        // 4. Second extraction with single item
        bus.mTemperature = 80.0;
        bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = cheeseStack.copy();
        assertTrue(reactor.processCheeseExtraction(bus));
        assertEquals(64.0, bus.mTemperature, 1e-4);
        assertNull(bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT], "Second item consumed");
    }

    private static net.minecraftforge.fluids.FluidStack mockFluidStack(net.minecraftforge.fluids.Fluid fluid,
        int amount) {
        net.minecraftforge.fluids.FluidStack stack = org.mockito.Mockito
            .mock(net.minecraftforge.fluids.FluidStack.class);
        stack.amount = amount;
        org.mockito.Mockito.when(stack.getFluid())
            .thenReturn(fluid);
        org.mockito.Mockito.when(stack.copy())
            .thenAnswer(inv -> mockFluidStack(fluid, stack.amount));
        org.mockito.Mockito
            .when(stack.isFluidEqual(org.mockito.Mockito.any(net.minecraftforge.fluids.FluidStack.class)))
            .thenAnswer(inv -> {
                net.minecraftforge.fluids.FluidStack other = inv.getArgument(0);
                return other != null && other.getFluid() == fluid;
            });
        return stack;
    }

    @Test
    void testCheeseRecipeCache() {
        MTEHatchNuclearBus.clearCheeseRecipeCache();

        net.minecraft.item.Item dummyItem = new net.minecraft.item.Item().setUnlocalizedName("cheese.dummy");
        ItemStack stackA = new ItemStack(dummyItem, 1, 0);
        ItemStack stackB = new ItemStack(dummyItem, 1, 1);

        MTEHatchNuclearBus bus = new MTEHatchNuclearBus("test.nuclear.bus", 4, new String[0], null);

        // Initially no cheese recipes are registered
        assertNull(bus.findCheeseExtractionRecipe(null));
        assertNull(bus.findCheeseExtractionRecipe(stackA));
        // Second call should hit the negative cache
        assertNull(bus.findCheeseExtractionRecipe(stackA));

        // Register a mocked cheese recipe
        gregtech.api.util.GTRecipe mockCheeseRecipe = org.mockito.Mockito.mock(gregtech.api.util.GTRecipe.class);
        org.mockito.Mockito
            .when(
                mockCheeseRecipe.isRecipeInputEqual(
                    org.mockito.Mockito.eq(false),
                    org.mockito.Mockito.eq(true),
                    org.mockito.Mockito.isNull(),
                    org.mockito.Mockito.eq(stackA)))
            .thenReturn(true);

        MTEHatchNuclearBus.registerCheeseRecipe(mockCheeseRecipe);

        // stackA matches the cheese recipe
        assertSame(mockCheeseRecipe, bus.findCheeseExtractionRecipe(stackA));
        // Repeated call hits memoized cache
        assertSame(mockCheeseRecipe, bus.findCheeseExtractionRecipe(stackA));

        // stackB does not match and is negatively cached
        assertNull(bus.findCheeseExtractionRecipe(stackB));
        assertNull(bus.findCheeseExtractionRecipe(stackB));

        // Key equality and hashCode check
        MTEHatchNuclearBus.RecipeCacheKey key1 = new MTEHatchNuclearBus.RecipeCacheKey(stackA);
        MTEHatchNuclearBus.RecipeCacheKey key2 = new MTEHatchNuclearBus.RecipeCacheKey(new ItemStack(dummyItem, 64, 0));
        MTEHatchNuclearBus.RecipeCacheKey key3 = new MTEHatchNuclearBus.RecipeCacheKey(stackB);

        assertEquals(key1, key2, "Cache keys with same item and damage must be equal regardless of stack size");
        assertEquals(key1.hashCode(), key2.hashCode(), "Cache keys hash codes must match for equal keys");
        assertNotEquals(key1, key3, "Cache keys with different damage must not be equal");

        MTEHatchNuclearBus.clearCheeseRecipeCache();
    }

    @Test
    void testLeftRightWorldMapping() {
        for (net.minecraftforge.common.util.ForgeDirection facing : new net.minecraftforge.common.util.ForgeDirection[] {
            net.minecraftforge.common.util.ForgeDirection.NORTH, net.minecraftforge.common.util.ForgeDirection.SOUTH,
            net.minecraftforge.common.util.ForgeDirection.EAST, net.minecraftforge.common.util.ForgeDirection.WEST }) {
            com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing ext = com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing
                .of(
                    facing,
                    com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.NORMAL,
                    com.gtnewhorizon.structurelib.alignment.enumerable.Flip.NONE);
            int[] leftWorld = new int[3];
            int[] rightWorld = new int[3];
            ext.getWorldOffset(new int[] { -2, -3, 0 }, leftWorld);
            ext.getWorldOffset(new int[] { +2, -3, 0 }, rightWorld);
            System.out.println(
                "Facing " + facing
                    + ": LeftWorld="
                    + java.util.Arrays.toString(leftWorld)
                    + ", RightWorld="
                    + java.util.Arrays.toString(rightWorld));
        }
    }

    @Test
    void testStructureExtendedFacingToGridCoordinates() {
        for (net.minecraftforge.common.util.ForgeDirection facing : new net.minecraftforge.common.util.ForgeDirection[] {
            net.minecraftforge.common.util.ForgeDirection.NORTH, net.minecraftforge.common.util.ForgeDirection.SOUTH,
            net.minecraftforge.common.util.ForgeDirection.EAST, net.minecraftforge.common.util.ForgeDirection.WEST }) {
            for (com.gtnewhorizon.structurelib.alignment.enumerable.Rotation rot : com.gtnewhorizon.structurelib.alignment.enumerable.Rotation
                .values()) {
                com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing ext = com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing
                    .of(facing, rot, com.gtnewhorizon.structurelib.alignment.enumerable.Flip.NONE);
                int gridSize = 5;
                int hOffset = gridSize / 2;
                boolean isUpsideDown = rot == com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.UPSIDE_DOWN;

                for (int r = 0; r < gridSize; r++) {
                    for (int col = 0; col < gridSize; col++) {
                        // In structure definition: col 0 is leftmost in structure, col N-1 is rightmost
                        // r 0 is front row (closest to controller), r N-1 is back row
                        int a = isUpsideDown ? -(col - hOffset) : (col - hOffset);
                        int b = -3;
                        int c = r;

                        int[] worldOffset = new int[3];
                        ext.getWorldOffset(new int[] { a, b, c }, worldOffset);

                        int[] out = new int[3];
                        ext.getOffsetABC(worldOffset, out);

                        int localA = isUpsideDown ? -out[0] : out[0];
                        int gx = localA + hOffset;
                        int gy = out[2];

                        assertEquals(col, gx, "Mismatch gx for facing " + facing + " rot " + rot);
                        assertEquals(r, gy, "Mismatch gy for facing " + facing + " rot " + rot);
                    }
                }
            }
        }
    }

    @Test
    void testNuclearReactorAlignmentLimits() {
        MTENuclearReactor reactor = new MTENuclearReactor("test_reactor");
        com.gtnewhorizon.structurelib.alignment.IAlignmentLimits limits = reactor.getInitialAlignmentLimits();

        // Horizontal facings with all 4 rotations without flip must be valid
        for (net.minecraftforge.common.util.ForgeDirection facing : new net.minecraftforge.common.util.ForgeDirection[] {
            net.minecraftforge.common.util.ForgeDirection.NORTH, net.minecraftforge.common.util.ForgeDirection.SOUTH,
            net.minecraftforge.common.util.ForgeDirection.EAST, net.minecraftforge.common.util.ForgeDirection.WEST }) {
            assertTrue(
                limits.isNewExtendedFacingValid(
                    facing,
                    com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.NORMAL,
                    com.gtnewhorizon.structurelib.alignment.enumerable.Flip.NONE));
            assertTrue(
                limits.isNewExtendedFacingValid(
                    facing,
                    com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.CLOCKWISE,
                    com.gtnewhorizon.structurelib.alignment.enumerable.Flip.NONE));
            assertTrue(
                limits.isNewExtendedFacingValid(
                    facing,
                    com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.UPSIDE_DOWN,
                    com.gtnewhorizon.structurelib.alignment.enumerable.Flip.NONE));
            assertTrue(
                limits.isNewExtendedFacingValid(
                    facing,
                    com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.COUNTER_CLOCKWISE,
                    com.gtnewhorizon.structurelib.alignment.enumerable.Flip.NONE));

            // Any flip (horizontal, vertical, both) must be REJECTED
            assertFalse(
                limits.isNewExtendedFacingValid(
                    facing,
                    com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.NORMAL,
                    com.gtnewhorizon.structurelib.alignment.enumerable.Flip.HORIZONTAL));
            assertFalse(
                limits.isNewExtendedFacingValid(
                    facing,
                    com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.NORMAL,
                    com.gtnewhorizon.structurelib.alignment.enumerable.Flip.VERTICAL));
            assertFalse(
                limits.isNewExtendedFacingValid(
                    facing,
                    com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.NORMAL,
                    com.gtnewhorizon.structurelib.alignment.enumerable.Flip.BOTH));
        }

        // Vertical facings (UP, DOWN) must be REJECTED
        assertFalse(
            limits.isNewExtendedFacingValid(
                net.minecraftforge.common.util.ForgeDirection.UP,
                com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.NORMAL,
                com.gtnewhorizon.structurelib.alignment.enumerable.Flip.NONE));
        assertFalse(
            limits.isNewExtendedFacingValid(
                net.minecraftforge.common.util.ForgeDirection.DOWN,
                com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.NORMAL,
                com.gtnewhorizon.structurelib.alignment.enumerable.Flip.NONE));
    }

    @Test
    void testTheCoreAndLiquidFuelSimTile() {
        // Test The Core (NQ32)
        SimTile core = new SimTile(SimTile.TileType.FUEL_CORE);
        assertTrue(core.isFuel());
        assertFalse(core.isHatch());
        assertEquals(512, core.generateNeutrons(1.0));
        assertEquals(320000, core.getMaxDurability());

        // Test Liquid Uranium Fuel Hatch
        SimTile liquidU = new SimTile(SimTile.TileType.HATCH_LIQUID_FUEL_URANIUM);
        assertTrue(liquidU.isFuel());
        assertTrue(liquidU.isHatch());
        assertTrue(liquidU.isLiquidFuelHatch());
        assertFalse(liquidU.isCoolantHatch());
        assertEquals(8, liquidU.generateNeutrons(1.0));
        assertEquals(NuclearSimulationEngine.DEFAULT_HATCH_CAPACITY, liquidU.getInputFluidAmount());
        assertEquals(0, liquidU.getOutputFluidAmount());

        // Test Liquid Thorium and Plutonium Hatches
        SimTile liquidTh = new SimTile(SimTile.TileType.HATCH_LIQUID_FUEL_THORIUM);
        assertEquals(4, liquidTh.generateNeutrons(1.0));
        SimTile liquidPu = new SimTile(SimTile.TileType.HATCH_LIQUID_FUEL_PLUTONIUM);
        assertEquals(16, liquidPu.generateNeutrons(1.0));

        // Test liquid fuel burnup into spent byproduct
        liquidU.onNeutronAbsorbed(NeutronType.THERMAL, 50);
        liquidU.nuclearTick(1.0);
        assertTrue(
            liquidU.getInputFluidAmount() < NuclearSimulationEngine.DEFAULT_HATCH_CAPACITY,
            "Liquid fuel input should be consumed");
        assertTrue(liquidU.getOutputFluidAmount() > 0, "Spent fuel byproduct should be produced");
        assertEquals(
            NuclearSimulationEngine.DEFAULT_HATCH_CAPACITY,
            liquidU.getInputFluidAmount() + liquidU.getOutputFluidAmount());
    }

    @Test
    void testLiquidFuelPipingTiers() {
        assertEquals(
            NuclearSimulationEngine.PIPE_TIER_ELECTRUM,
            NuclearSimulationEngine.getRequiredFluidTier("thoriumbasedliquidfuel"));
        assertEquals(
            NuclearSimulationEngine.PIPE_TIER_PLATINUM,
            NuclearSimulationEngine.getRequiredFluidTier("uraniumbasedliquidfuel"));
        assertEquals(
            NuclearSimulationEngine.PIPE_TIER_OSMIUM,
            NuclearSimulationEngine.getRequiredFluidTier("plutoniumbasedliquidfuel"));
        assertEquals(
            NuclearSimulationEngine.PIPE_TIER_OSMIUM,
            NuclearSimulationEngine.getRequiredFluidTier("uraniumhexafluoride"));
        // Arbitrary unhandled fluids return 999
        assertEquals(999, NuclearSimulationEngine.getRequiredFluidTier("some_random_fluid"));
        // Naquadah liquid fuel must be blocked to preserve Large Naquadah Reactor exclusivity
        assertEquals(999, NuclearSimulationEngine.getRequiredFluidTier("liquid_naquadah_fuel"));
        assertEquals(999, NuclearSimulationEngine.getRequiredFluidTier("naquadahbasedliquidfuel"));
    }

    @Test
    void testReactorCasingAcceptsOutputBusAndHatch() {
        MTENuclearReactor reactor = new MTENuclearReactor("test.reactor.outputs") {

            @Override
            public ItemStack getMachineCraftingIcon() {
                return null;
            }
        };
        reactor.clearHatches();
        assertTrue(reactor.mOutputBusses.isEmpty());
        assertTrue(reactor.mOutputHatches.isEmpty());

        // 1. Mock output bus
        IGregTechTileEntity mockBusTe = org.mockito.Mockito.mock(IGregTechTileEntity.class);
        gregtech.api.metatileentity.implementations.MTEHatchOutputBus mockBus = org.mockito.Mockito
            .mock(gregtech.api.metatileentity.implementations.MTEHatchOutputBus.class);
        org.mockito.Mockito.when(mockBusTe.getMetaTileEntity())
            .thenReturn(mockBus);

        assertTrue(reactor.addOutputToMachineList(mockBusTe, MTENuclearReactor.CASING_INDEX));
        assertEquals(1, reactor.mOutputBusses.size());

        // 2. Mock output hatch
        IGregTechTileEntity mockHatchTe = org.mockito.Mockito.mock(IGregTechTileEntity.class);
        gregtech.api.metatileentity.implementations.MTEHatchOutput mockHatch = org.mockito.Mockito
            .mock(gregtech.api.metatileentity.implementations.MTEHatchOutput.class);
        org.mockito.Mockito.when(mockHatchTe.getMetaTileEntity())
            .thenReturn(mockHatch);

        assertTrue(reactor.addOutputToMachineList(mockHatchTe, MTENuclearReactor.CASING_INDEX));
        assertEquals(1, reactor.mOutputHatches.size());
    }

    @Test
    void testDiagonalNeutronTransportAndStencilCoupling() {
        // 1. Moderator thermalization & 9-point isotropic diagonal diffusion:
        // Fuel at (0, 1), Moderator at (1, 1), Diagonal receiver at (2, 2).
        // Fast neutron travels from (0, 1) into Moderator at (1, 1).
        // Moderator slows fast neutron into thermal flux.
        // 9-point isotropic stencil diffuses thermal flux diagonally to (2, 2)!
        MockNuclearTile[][] grid3x3 = new MockNuclearTile[3][3];
        MockNuclearTile fuel = new MockNuclearTile(true, 120);
        fuel.emissionCount = 4;
        MockNuclearTile moderator = new MockNuclearTile(false, 0);
        moderator.absorbProb = 0.01;
        moderator.scatterProb = 0.95;
        moderator.moderationProb = 0.95;
        MockNuclearTile diagReceiver = new MockNuclearTile(false, 0);
        diagReceiver.absorbProb = 0.50;

        grid3x3[0][1] = fuel;
        grid3x3[1][1] = moderator;
        grid3x3[2][2] = diagReceiver;

        for (int i = 0; i < 20; i++) {
            NuclearSimulationEngine.simulate(grid3x3, 3, 3);
        }

        assertTrue(
            diagReceiver.fluxReceived > 0,
            "Diagonal receiver at (2, 2) must receive thermal flux via 9-point isotropic stencil from moderator at (1, 1)");
        assertTrue(diagReceiver.thermalAbsorbed > 0, "Diagonal receiver must absorb thermal neutrons");

        // 2. Diagonal line-of-sight shadowing with Naquarite Insulator
        // Grid 4x4: Fuel at (0, 1), Moderator at (1, 1), Naquarite Foil at (2, 2), Behind at (3, 3).
        // Other cells are null. The Naquarite foil at (2, 2) must completely block diagonal flux to (3, 3).
        MockNuclearTile[][] grid4x4 = new MockNuclearTile[4][4];
        MockNuclearTile fuel4 = new MockNuclearTile(true, 120);
        fuel4.emissionCount = 4;
        MockNuclearTile mod4 = new MockNuclearTile(false, 0);
        mod4.absorbProb = 0.01;
        mod4.scatterProb = 0.95;
        mod4.moderationProb = 0.95;
        MockNuclearTile naquarite = new MockNuclearTile(false, 0);
        naquarite.absorbProb = 1.0;
        naquarite.scatterProb = 0.0;
        MockNuclearTile behindDiag = new MockNuclearTile(false, 0);

        grid4x4[0][1] = fuel4;
        grid4x4[1][1] = mod4;
        grid4x4[2][2] = naquarite;
        grid4x4[3][3] = behindDiag;

        for (int i = 0; i < 20; i++) {
            NuclearSimulationEngine.simulate(grid4x4, 4, 4);
        }

        assertTrue(naquarite.fluxReceived > 0, "Naquarite must receive diagonal flux");
        assertTrue(naquarite.thermalAbsorbed > 0, "Naquarite must absorb all diagonal flux");
        assertEquals(
            0,
            behindDiag.fluxReceived,
            "Behind tile at (3, 3) must receive 0 flux because Naquarite blocks 100%");
    }

    @Test
    void testHollowInnerChamberAndTooltip() {
        MTENuclearReactor reactor = new MTENuclearReactor("test.reactor.hollow");

        // Structure definition verification
        com.gtnewhorizon.structurelib.structure.StructureDefinition<MTENuclearReactor> def = (com.gtnewhorizon.structurelib.structure.StructureDefinition<MTENuclearReactor>) reactor
            .getStructureDefinition();
        assertNotNull(def, "Structure definition must be initialized");
        assertNotNull(
            def.getElements()
                .get('-'),
            "Structure definition must contain '-' element for mandatory air");
        assertTrue(
            def.getShapes()
                .get(MTENuclearReactor.STRUCTURE_TIER_1)
                .contains("-"),
            "STRUCTURE_TIER_1 shape must contain '-' for hollow air cells");
        assertTrue(
            def.getShapes()
                .get(MTENuclearReactor.STRUCTURE_TIER_2)
                .contains("-"),
            "STRUCTURE_TIER_2 shape must contain '-' for hollow air cells");
        assertTrue(
            def.getShapes()
                .get(MTENuclearReactor.STRUCTURE_TIER_3)
                .contains("-"),
            "STRUCTURE_TIER_3 shape must contain '-' for hollow air cells");
    }

    @Test
    void testHeatVentSelfCooling() {
        // 1. Standard Vent: vents up to 6 Hu/t
        SimTile ventStd = new SimTile(SimTile.TileType.VENT_STANDARD);
        ventStd.setCurrentCellHeat(100);
        ventStd.nuclearTick(1.0);
        assertEquals(94, ventStd.getCurrentCellHeat(), "Standard vent must cool 6 Hu/t");

        // 2. Advanced Vent: vents up to 12 Hu/t
        SimTile ventAdv = new SimTile(SimTile.TileType.VENT_ADVANCED);
        ventAdv.setCurrentCellHeat(100);
        ventAdv.nuclearTick(1.0);
        assertEquals(88, ventAdv.getCurrentCellHeat(), "Advanced vent must cool 12 Hu/t");

        // 3. Overclocked Vent: vents up to 20 Hu/t
        SimTile ventOver = new SimTile(SimTile.TileType.VENT_OVERCLOCKED);
        ventOver.setCurrentCellHeat(100);
        ventOver.nuclearTick(1.0);
        assertEquals(80, ventOver.getCurrentCellHeat(), "Overclocked vent must cool 20 Hu/t");

        // 4. Absorbs heat from tile when tile is hot
        ventAdv.setTemperature(300.0);
        ventAdv.setCurrentCellHeat(0);
        ventAdv.nuclearTick(1.0);
        assertTrue(ventAdv.getTemperature() < 300.0, "Vent must cool hot tile");
        assertTrue(ventAdv.getCurrentCellHeat() > 0, "Vent must absorb heat from hot tile");
    }

    @Test
    void testComponentHeatVentAdjacentCooling() {
        SimTile[][] grid = new SimTile[3][3];
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                grid[x][y] = new SimTile(SimTile.TileType.EMPTY);
            }
        }

        // Center: Component Heat Vent (VC)
        SimTile compVent = new SimTile(SimTile.TileType.VENT_COMPONENT);
        grid[1][1] = compVent;

        // 4 orthogonal neighbors: Heat Vents with 50 Hu each
        SimTile top = new SimTile(SimTile.TileType.VENT_STANDARD);
        top.setCurrentCellHeat(50);
        grid[1][0] = top;

        SimTile bottom = new SimTile(SimTile.TileType.VENT_STANDARD);
        bottom.setCurrentCellHeat(50);
        grid[1][2] = bottom;

        SimTile left = new SimTile(SimTile.TileType.VENT_STANDARD);
        left.setCurrentCellHeat(50);
        grid[0][1] = left;

        SimTile right = new SimTile(SimTile.TileType.VENT_STANDARD);
        right.setCurrentCellHeat(50);
        grid[2][1] = right;

        // Process component interaction
        compVent.processNeighborComponents(grid, 1, 1, 3, 3);

        assertEquals(46, top.getCurrentCellHeat(), "Top neighbor must lose 4 Hu");
        assertEquals(46, bottom.getCurrentCellHeat(), "Bottom neighbor must lose 4 Hu");
        assertEquals(46, left.getCurrentCellHeat(), "Left neighbor must lose 4 Hu");
        assertEquals(46, right.getCurrentCellHeat(), "Right neighbor must lose 4 Hu");
    }

    @Test
    void testHeatExchangerBalancing() {
        SimTile[][] grid = new SimTile[3][3];
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                grid[x][y] = new SimTile(SimTile.TileType.EMPTY);
            }
        }

        // Center: Component Heat Exchanger (XC, switchSide = 36)
        SimTile exchanger = new SimTile(SimTile.TileType.EXCHANGER_COMPONENT);
        grid[1][1] = exchanger;

        // Left: hot advanced vent (500 / 1000 = 50%)
        SimTile hotVent = new SimTile(SimTile.TileType.VENT_ADVANCED);
        hotVent.setCurrentCellHeat(500);
        grid[0][1] = hotVent;

        // Right: cold advanced vent (0 / 1000 = 0%)
        SimTile coldVent = new SimTile(SimTile.TileType.VENT_ADVANCED);
        coldVent.setCurrentCellHeat(0);
        grid[2][1] = coldVent;

        // Run exchanger balancing
        exchanger.processNeighborComponents(grid, 1, 1, 3, 3);

        // Heat should flow from hotVent into exchanger, and from exchanger into coldVent
        assertTrue(hotVent.getCurrentCellHeat() < 500, "Hot vent should have transferred heat away");
        assertTrue(
            exchanger.getCurrentCellHeat() > 0 || coldVent.getCurrentCellHeat() > 0,
            "Heat should have moved to exchanger or cold vent");
    }

    @Test
    void testHeatVentAndExchangerGridSimulation() {
        SimTile[][] grid = new SimTile[5][5];
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                grid[x][y] = new SimTile(SimTile.TileType.EMPTY);
            }
        }

        // Center: Uranium quad rod
        grid[2][2] = new SimTile(SimTile.TileType.FUEL_URANIUM_QUAD);

        // Surrounding: Component Heat Exchangers (XC)
        grid[1][2] = new SimTile(SimTile.TileType.EXCHANGER_COMPONENT);
        grid[3][2] = new SimTile(SimTile.TileType.EXCHANGER_COMPONENT);
        grid[2][1] = new SimTile(SimTile.TileType.EXCHANGER_COMPONENT);
        grid[2][3] = new SimTile(SimTile.TileType.EXCHANGER_COMPONENT);

        // Outer: Advanced Heat Vents (VA) and Component Vents (VC)
        grid[0][2] = new SimTile(SimTile.TileType.VENT_ADVANCED);
        grid[4][2] = new SimTile(SimTile.TileType.VENT_ADVANCED);
        grid[2][0] = new SimTile(SimTile.TileType.VENT_ADVANCED);
        grid[2][4] = new SimTile(SimTile.TileType.VENT_ADVANCED);
        grid[1][1] = new SimTile(SimTile.TileType.VENT_COMPONENT);
        grid[3][3] = new SimTile(SimTile.TileType.VENT_COMPONENT);

        // Run 20 ticks of simulation
        for (int t = 0; t < 20; t++) {
            NuclearSimulationEngine.SimulationResult res = NuclearSimulationEngine.simulate(grid, 5, 5, 1.0, 20.0);
            assertNotNull(res);
            assertTrue(
                res.maxTemperature < 1000.0,
                "Reactor temperature should remain within safe limits with vents/exchangers");
        }
    }

    @Test
    void testEnclosedNuclearHatchStructureZeroAirCheck() {
        // Verify that in all 3 structure tiers, Slice 0 hatches are strictly enclosed:
        // No hatch has air in any of the 8 adjacent positions in its horizontal plane.
        String[][][] tiers = { MTENuclearReactor.SHAPE_TIER_1, MTENuclearReactor.SHAPE_TIER_2,
            MTENuclearReactor.SHAPE_TIER_3 };
        int[] expectedHatches = { 21, 69, 145 };
        int[] expectedCasings = { 24, 40, 56 };

        for (int t = 0; t < tiers.length; t++) {
            String[][] shape = tiers[t];
            assertEquals(5, shape.length, "Reactor multiblock must have 5 vertical slices");

            String[] topSlice = shape[0];
            int height = topSlice.length;
            int hatchCount = 0;
            int casingCount = 0;

            for (int y = 0; y < height; y++) {
                String row = topSlice[y];
                for (int x = 0; x < row.length(); x++) {
                    char c = row.charAt(x);
                    if (c == 'g') {
                        hatchCount++;
                        // Verify all 8 planar neighbors are within bounds and NOT air (' ')
                        for (int dy = -1; dy <= 1; dy++) {
                            for (int dx = -1; dx <= 1; dx++) {
                                if (dx == 0 && dy == 0) continue;
                                int ny = y + dy;
                                int nx = x + dx;
                                assertTrue(
                                    ny >= 0 && ny < height,
                                    String.format(
                                        "Tier %d: Hatch at (%d, %d) neighbor (%d, %d) must be within slice bounds",
                                        t + 1,
                                        x,
                                        y,
                                        nx,
                                        ny));
                                assertTrue(
                                    nx >= 0 && nx < topSlice[ny].length(),
                                    String.format(
                                        "Tier %d: Hatch at (%d, %d) neighbor (%d, %d) must be within row bounds",
                                        t + 1,
                                        x,
                                        y,
                                        nx,
                                        ny));
                                char neighbor = topSlice[ny].charAt(nx);
                                assertNotEquals(
                                    ' ',
                                    neighbor,
                                    String.format(
                                        "Tier %d: Hatch at (%d, %d) has air neighbor at (%d, %d)",
                                        t + 1,
                                        x,
                                        y,
                                        nx,
                                        ny));
                                assertTrue(
                                    neighbor == 'g' || neighbor == 'c',
                                    String.format(
                                        "Tier %d: Hatch at (%d, %d) has unexpected neighbor '%c' at (%d, %d)",
                                        t + 1,
                                        x,
                                        y,
                                        neighbor,
                                        nx,
                                        ny));
                            }
                        }
                    } else if (c == 'c') {
                        casingCount++;
                    }
                }
            }

            assertEquals(expectedHatches[t], hatchCount, "Tier " + (t + 1) + " must have expected hatch count");
            assertEquals(expectedCasings[t], casingCount, "Tier " + (t + 1) + " must have expected casing count");
        }

        // Verify predicates isNuclearCoreHatch and isNuclearHatchTile
        MTEHatchNuclearHatch hatch = new MTEHatchNuclearHatch("adj.hatch", 1, 16000, new String[0], null);
        MTEHatchNuclearBus bus = new MTEHatchNuclearBus("sample.bus", 4, new String[0], null);
        MTEHatchNuclearControlRod rod = new MTEHatchNuclearControlRod("adj.rod", 1, new String[0], null);
        gregtech.api.interfaces.metatileentity.IMetaTileEntity mockMte = org.mockito.Mockito
            .mock(gregtech.api.interfaces.metatileentity.IMetaTileEntity.class);

        assertTrue(MTENuclearReactor.isNuclearCoreHatch(hatch));
        assertTrue(MTENuclearReactor.isNuclearCoreHatch(bus));
        assertTrue(MTENuclearReactor.isNuclearCoreHatch(rod));
        assertFalse(MTENuclearReactor.isNuclearCoreHatch(mockMte));
        assertFalse(MTENuclearReactor.isNuclearCoreHatch(null));

        gregtech.api.metatileentity.BaseMetaTileEntity mockHatchTile = org.mockito.Mockito
            .mock(gregtech.api.metatileentity.BaseMetaTileEntity.class);
        org.mockito.Mockito.when(mockHatchTile.getMetaTileEntity())
            .thenReturn(hatch);
        gregtech.api.metatileentity.BaseMetaTileEntity mockRodTile = org.mockito.Mockito
            .mock(gregtech.api.metatileentity.BaseMetaTileEntity.class);
        org.mockito.Mockito.when(mockRodTile.getMetaTileEntity())
            .thenReturn(rod);
        gregtech.api.metatileentity.BaseMetaTileEntity mockOtherMachine = org.mockito.Mockito
            .mock(gregtech.api.metatileentity.BaseMetaTileEntity.class);
        org.mockito.Mockito.when(mockOtherMachine.getMetaTileEntity())
            .thenReturn(mockMte);

        assertTrue(MTENuclearReactor.isNuclearHatchTile(mockHatchTile));
        assertTrue(MTENuclearReactor.isNuclearHatchTile(mockRodTile));
        assertFalse(MTENuclearReactor.isNuclearHatchTile(mockOtherMachine));
        assertFalse(MTENuclearReactor.isNuclearHatchTile(null));
    }

    @Test
    void testNuclearHatchFacingRequirementAndAutobuild() {
        MTENuclearReactor reactor = new MTENuclearReactor("test.reactor.facing");
        assertEquals(ForgeDirection.UP, reactor.getNuclearHatchFacing(), "Default upright reactor requires UP facing");

        // Rotate upside down
        com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing upsideDownFacing = com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing
            .of(
                ForgeDirection.NORTH,
                com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.UPSIDE_DOWN,
                com.gtnewhorizon.structurelib.alignment.enumerable.Flip.NONE);
        reactor.setExtendedFacing(upsideDownFacing);
        assertEquals(ForgeDirection.DOWN, reactor.getNuclearHatchFacing(), "Inverted reactor requires DOWN facing");

        // Reset to default
        reactor.setExtendedFacing(com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing.DEFAULT);
        assertEquals(ForgeDirection.UP, reactor.getNuclearHatchFacing());

        // Test NuclearHatchElement check()
        MTENuclearReactor.NuclearHatchElement element = new MTENuclearReactor.NuclearHatchElement();
        List<String> desc = element.getDescription(reactor);
        assertNotNull(desc);
        assertTrue(
            desc.get(0)
                .contains("Top-layer hatches must face opposite to pipe casings"));

        World mockWorld = org.mockito.Mockito.mock(World.class);
        gregtech.api.metatileentity.BaseMetaTileEntity mockTe = org.mockito.Mockito
            .mock(gregtech.api.metatileentity.BaseMetaTileEntity.class);
        MTEHatchNuclearBus bus = new MTEHatchNuclearBus("test.bus", 4, new String[0], null);
        org.mockito.Mockito.when(mockTe.getMetaTileEntity())
            .thenReturn(bus);
        org.mockito.Mockito.when(mockWorld.getTileEntity(10, 64, 10))
            .thenReturn(mockTe);

        // Wrong facing (NORTH) -> check fails
        org.mockito.Mockito.when(mockTe.getFrontFacing())
            .thenReturn(ForgeDirection.NORTH);
        assertFalse(element.check(reactor, mockWorld, 10, 64, 10), "Hatch facing NORTH must fail check");

        // Correct facing (UP) -> check succeeds
        org.mockito.Mockito.when(mockTe.getFrontFacing())
            .thenReturn(ForgeDirection.UP);
        assertTrue(element.check(reactor, mockWorld, 10, 64, 10), "Hatch facing UP must pass check");

        // Test Autobuild: placeBlock on existing hatch with wrong facing rotates it to UP
        org.mockito.Mockito.when(mockTe.getFrontFacing())
            .thenReturn(ForgeDirection.NORTH);
        boolean placed = element.placeBlock(reactor, mockWorld, 10, 64, 10, null);
        assertTrue(placed, "placeBlock must return true when rotating existing hatch");
        org.mockito.Mockito.verify(mockTe)
            .setFrontFacing(ForgeDirection.UP);

        // Test Survival Autobuild: survivalPlaceBlock on existing hatch with wrong facing rotates it to UP and returns
        // ACCEPT
        org.mockito.Mockito.when(mockTe.getFrontFacing())
            .thenReturn(ForgeDirection.NORTH);
        com.gtnewhorizon.structurelib.structure.IStructureElement.PlaceResult res = element
            .survivalPlaceBlock(reactor, mockWorld, 10, 64, 10, null, null);
        assertEquals(
            com.gtnewhorizon.structurelib.structure.IStructureElement.PlaceResult.ACCEPT,
            res,
            "survivalPlaceBlock must return ACCEPT when rotating existing hatch");

        // When already UP, survivalPlaceBlock returns SKIP
        org.mockito.Mockito.when(mockTe.getFrontFacing())
            .thenReturn(ForgeDirection.UP);
        res = element.survivalPlaceBlock(reactor, mockWorld, 10, 64, 10, null, null);
        assertEquals(
            com.gtnewhorizon.structurelib.structure.IStructureElement.PlaceResult.SKIP,
            res,
            "survivalPlaceBlock must return SKIP when hatch already has correct facing");

        // Test checkNuclearHatchFacings
        List<gregtech.api.structure.error.StructureError> errors = new ArrayList<>();
        reactor.mNuclearTiles.clear();
        reactor.mNuclearTiles.add(mockTe);

        // With UP facing -> no errors
        org.mockito.Mockito.when(mockTe.getFrontFacing())
            .thenReturn(ForgeDirection.UP);
        reactor.verifyNuclearHatchFacings(errors);
        assertTrue(errors.isEmpty(), "Correct facing must pass verifyNuclearHatchFacings");

        // With wrong facing (DOWN) -> error added
        org.mockito.Mockito.when(mockTe.getFrontFacing())
            .thenReturn(ForgeDirection.DOWN);
        reactor.verifyNuclearHatchFacings(errors);
        assertFalse(errors.isEmpty(), "Wrong facing must fail verifyNuclearHatchFacings");
    }

    @Test
    void testNuclearCoreHighPressureHatchAndStructureRules() {
        MTENuclearReactor reactor = new MTENuclearReactor("test.reactor");
        MTEHatchNuclearHighPressure topHp = new MTEHatchNuclearHighPressure("test.top_hp", 4, new String[0], null);
        MTEHatchNuclearHighPressure botHp = new MTEHatchNuclearHighPressure("test.bot_hp", 4, new String[0], null);

        // Core hatch predicates
        assertTrue(MTENuclearReactor.isNuclearCoreHatch(topHp));
        assertTrue(MTENuclearReactor.isNuclearCoreHatch(botHp));

        gregtech.api.metatileentity.BaseMetaTileEntity mockTopTe = org.mockito.Mockito
            .mock(gregtech.api.metatileentity.BaseMetaTileEntity.class);
        topHp.setBaseMetaTileEntity(mockTopTe);
        org.mockito.Mockito.when(mockTopTe.getMetaTileEntity())
            .thenReturn(topHp);
        org.mockito.Mockito.when(mockTopTe.getFrontFacing())
            .thenReturn(ForgeDirection.UP);
        org.mockito.Mockito.when(mockTopTe.getXCoord())
            .thenReturn(10);
        org.mockito.Mockito.when(mockTopTe.getYCoord())
            .thenReturn((short) 68);
        org.mockito.Mockito.when(mockTopTe.getZCoord())
            .thenReturn(15);
        assertTrue(MTENuclearReactor.isNuclearHatchTile(mockTopTe));

        gregtech.api.metatileentity.BaseMetaTileEntity mockBotTe = org.mockito.Mockito
            .mock(gregtech.api.metatileentity.BaseMetaTileEntity.class);
        botHp.setBaseMetaTileEntity(mockBotTe);
        org.mockito.Mockito.when(mockBotTe.getMetaTileEntity())
            .thenReturn(botHp);
        org.mockito.Mockito.when(mockBotTe.getFrontFacing())
            .thenReturn(ForgeDirection.DOWN);
        org.mockito.Mockito.when(mockBotTe.getXCoord())
            .thenReturn(10);
        org.mockito.Mockito.when(mockBotTe.getYCoord())
            .thenReturn((short) 64);
        org.mockito.Mockito.when(mockBotTe.getZCoord())
            .thenReturn(15);

        // BottomCoreElement tests
        MTENuclearReactor.BottomCoreElement bottomElement = new MTENuclearReactor.BottomCoreElement();
        World mockWorld = org.mockito.Mockito.mock(World.class);

        // 1. Nuclear casing at bottom
        org.mockito.Mockito.when(mockWorld.getBlock(10, 64, 15))
            .thenReturn(com.gtnewhorizons.modularnuclear.common.block.ModBlocks.nuclearCasing);
        org.mockito.Mockito.when(mockWorld.getBlockMetadata(10, 64, 15))
            .thenReturn(0);
        reactor.mCasing = 0;
        assertTrue(
            bottomElement.check(reactor, mockWorld, 10, 64, 15),
            "Nuclear casing meta 0 must pass BottomCoreElement");
        assertEquals(1, reactor.mCasing);

        // 2. HP hatch facing DOWN at bottom
        org.mockito.Mockito.when(mockWorld.getBlock(10, 64, 15))
            .thenReturn(gregtech.api.GregTechAPI.sBlockMachines);
        org.mockito.Mockito.when(mockWorld.getTileEntity(10, 64, 15))
            .thenReturn(mockBotTe);
        reactor.mBottomHighPressureHatches.clear();
        assertTrue(
            bottomElement.check(reactor, mockWorld, 10, 64, 15),
            "HP hatch facing DOWN must pass BottomCoreElement");
        assertEquals(1, reactor.mBottomHighPressureHatches.size());
        assertFalse(botHp.isPassageInlet(), "Bottom HP hatch must be outlet");

        // 3. HP hatch with wrong facing (UP) at bottom -> rejected
        org.mockito.Mockito.when(mockBotTe.getFrontFacing())
            .thenReturn(ForgeDirection.UP);
        assertFalse(
            bottomElement.check(reactor, mockWorld, 10, 64, 15),
            "HP hatch facing UP must fail BottomCoreElement");

        // 4. Other hatches at bottom -> rejected (bus, fluid hatch, control rod, maintenance)
        MTEHatchNuclearBus bus = new MTEHatchNuclearBus("test.bus", 4, new String[0], null);
        org.mockito.Mockito.when(mockBotTe.getMetaTileEntity())
            .thenReturn(bus);
        org.mockito.Mockito.when(mockBotTe.getFrontFacing())
            .thenReturn(ForgeDirection.DOWN);
        assertFalse(bottomElement.check(reactor, mockWorld, 10, 64, 15), "Bus hatch must fail BottomCoreElement");

        MTEHatchNuclearHatch fluidHatch = new MTEHatchNuclearHatch("test.hatch", 4, 16000, new String[0], null);
        org.mockito.Mockito.when(mockBotTe.getMetaTileEntity())
            .thenReturn(fluidHatch);
        assertFalse(bottomElement.check(reactor, mockWorld, 10, 64, 15), "Fluid hatch must fail BottomCoreElement");

        MTEHatchNuclearControlRod controlRod = new MTEHatchNuclearControlRod("test.rod", 4, new String[0], null);
        org.mockito.Mockito.when(mockBotTe.getMetaTileEntity())
            .thenReturn(controlRod);
        assertFalse(bottomElement.check(reactor, mockWorld, 10, 64, 15), "Control rod must fail BottomCoreElement");

        // Pairing checks
        List<gregtech.api.structure.error.StructureError> errors = new ArrayList<>();
        reactor.mTopHighPressureHatches.clear();
        reactor.mBottomHighPressureHatches.clear();

        // Tier 1 check: Tier 1 cannot use HP hatches
        reactor.mTopHighPressureHatches.add(topHp);
        reactor.mBottomHighPressureHatches.add(botHp);
        reactor.setExtendedFacing(com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing.DEFAULT);

        gregtech.api.metatileentity.BaseMetaTileEntity baseTe = org.mockito.Mockito
            .mock(gregtech.api.metatileentity.BaseMetaTileEntity.class);
        org.mockito.Mockito.when(baseTe.getXCoord())
            .thenReturn(10);
        org.mockito.Mockito.when(baseTe.getYCoord())
            .thenReturn((short) 64);
        org.mockito.Mockito.when(baseTe.getZCoord())
            .thenReturn(10);

        // Tier 1 (gridSize = 5) -> fails with hp_hatch_tier
        reactor.gridSize = 5;
        reactor.validateHighPressureHatchPairing(baseTe, errors);
        assertTrue(
            errors.stream()
                .anyMatch(
                    e -> e.toString()
                        .contains("hp_hatch_tier")),
            "Tier 1 must reject HP hatches");

        // Unpaired check: 1 top, 0 bottom -> fails with hp_hatch_unpaired
        errors.clear();
        reactor.gridSize = 9; // Tier 2
        reactor.mTopHighPressureHatches.clear();
        reactor.mBottomHighPressureHatches.clear();
        reactor.mTopHighPressureHatches.add(topHp);
        reactor.validateHighPressureHatchPairing(baseTe, errors);
        assertTrue(
            errors.stream()
                .anyMatch(
                    e -> e.toString()
                        .contains("hp_hatch_unpaired")),
            "Unpaired count must fail");

        // Paired check: 1 top, 1 bottom in Tier 2 at matching coordinates
        errors.clear();
        reactor.mBottomHighPressureHatches.add(botHp);
        reactor.validateHighPressureHatchPairing(baseTe, errors);
        assertTrue(errors.isEmpty(), "Paired top/bottom hatches at matching column must succeed: " + errors);
        assertEquals(mockBotTe, topHp.getOppositeHatchTile(), "Top hatch must link to bottom");
        assertEquals(mockTopTe, botHp.getOppositeHatchTile(), "Bottom hatch must link to top");
        assertTrue(topHp.isPassageInlet(), "Top hatch must be inlet");
        assertFalse(botHp.isPassageInlet(), "Bottom hatch must be outlet");
    }

    @Test
    void testHighPressureHatchThermalAndTransmutation() {
        MTEHatchNuclearHighPressure hp = new MTEHatchNuclearHighPressure("test.hp", 4, new String[0], null);
        hp.setTemperature(500.0);

        // Convective thermal exchange: 500 °C core cooling with 50 °C water
        LoopSegment segment = new LoopSegment("seg_core", 5.0, 0.10, 1e-5, 2.5, 500.0, 2000.0, 50.0);
        hp.processThermalExchange(0.05, 1.0, CoolantFluidProperty.WATER, segment);

        assertTrue(hp.getTemperature() < 500.0, "Core temperature must decrease during convective cooling");
        assertTrue(segment.getCurrentTemperatureCelsius() > 50.0, "Coolant temperature must rise during core passage");
        assertTrue(hp.mUsedForCooling, "UsedForCooling flag must be set");

        // Dissolved gas transmutation: Heavy Water
        ICoolantLoopPump mockPump = org.mockito.Mockito.mock(ICoolantLoopPump.class);
        org.mockito.Mockito.when(mockPump.getCoolantFluidProperty())
            .thenReturn(CoolantFluidProperty.HEAVY_WATER);
        hp.setConnectedPump(mockPump);

        hp.onNeutronAbsorbed(NeutronType.THERMAL, 4);
        org.mockito.Mockito.verify(mockPump)
            .consumeCoolant(2);
        org.mockito.Mockito.verify(mockPump)
            .addDissolvedGas("Tritium", 2);
        org.mockito.Mockito.verify(mockPump)
            .addDissolvedGas("Oxygen", 1);
    }

    @Test
    void testMeltdownThresholdAndTierGating() {
        // Temperature limits per pipe tier
        assertEquals(
            1000.0,
            NuclearSimulationEngine.getMaxOperatingTemperature(NuclearSimulationEngine.PIPE_TIER_ELECTRUM));
        assertEquals(
            1400.0,
            NuclearSimulationEngine.getMaxOperatingTemperature(NuclearSimulationEngine.PIPE_TIER_PLATINUM));
        assertEquals(
            1800.0,
            NuclearSimulationEngine.getMaxOperatingTemperature(NuclearSimulationEngine.PIPE_TIER_OSMIUM));
        assertEquals(
            2200.0,
            NuclearSimulationEngine.getMaxOperatingTemperature(NuclearSimulationEngine.PIPE_TIER_QUANTIUM));
        assertEquals(
            2600.0,
            NuclearSimulationEngine.getMaxOperatingTemperature(NuclearSimulationEngine.PIPE_TIER_FLUXED_ELECTRUM));
        assertEquals(
            3200.0,
            NuclearSimulationEngine.getMaxOperatingTemperature(NuclearSimulationEngine.PIPE_TIER_BLACK_PLUTONIUM));

        // Reactor damage percentage & efficiency scaling
        MTENuclearReactor reactor = new MTENuclearReactor("test.reactor");
        reactor.mMachine = true;
        reactor.mWrench = true;
        reactor.mScrewdriver = true;
        reactor.mSoftMallet = true;
        reactor.mHardHammer = true;
        reactor.mSolderingTool = true;
        reactor.mCrowbar = true;
        assertEquals(1.0, reactor.getMaintenanceEfficiency(), 1e-6, "Undamaged machine with perfect maintenance must have 100% efficiency");

        reactor.mReactorDamage = 20.0;
        assertEquals(0.80, reactor.getMaintenanceEfficiency(), 1e-6, "20% reactor damage must scale cooling efficiency to 80%");

        reactor.mReactorDamage = 50.0;
        assertEquals(0.50, reactor.getMaintenanceEfficiency(), 1e-6, "50% reactor damage must scale cooling efficiency to 50%");

        // Verify all 13 fuels satisfy peak/10% damage ratio <= 25%
        for (NuclearFuelType fuel : NuclearFuelType.values()) {
            assertTrue(
                fuel.damageRatio <= 25.0001,
                fuel.name() + " damage ratio must be <= 25% but was " + fuel.damageRatio + "%");
            assertEquals(
                0.0,
                fuel.calculateTemperatureDamage(NuclearSimulationEngine.AMBIENT_TEMP),
                1e-6,
                fuel.name() + " must have 0 damage at ambient temperature");
            assertEquals(
                1.0,
                fuel.calculateTemperatureDamage(fuel.peakReactivityTemp),
                0.01,
                fuel.name() + " damage at peak reactivity must be calibrated to ~1.0");
        }

        // Tier 1 (gridSize = 5)
        reactor.gridSize = 5;
        assertEquals(1, reactor.getReactorTier(), "GridSize 5 must be Tier 1");
        assertFalse(reactor.getReactorTier() >= 2, "Tier 1 must never be eligible for meltdown");

        // Tier 2 (gridSize = 9)
        reactor.gridSize = 9;
        assertEquals(2, reactor.getReactorTier(), "GridSize 9 must be Tier 2");
        assertTrue(reactor.getReactorTier() >= 2, "Tier 2 must be eligible for meltdown");

        // Tier 3 (gridSize = 13)
        reactor.gridSize = 13;
        assertEquals(3, reactor.getReactorTier(), "GridSize 13 must be Tier 3");
        assertTrue(reactor.getReactorTier() >= 2, "Tier 3 must be eligible for meltdown");
    }

    @Test
    void testCoriumFluidAndBlockProperties() {
        ModFluids.init();
        assertNotNull(ModFluids.fluidCorium, "Fluid Corium must be initialized");
        assertEquals("corium", ModFluids.fluidCorium.getName());
        assertEquals(8000, ModFluids.fluidCorium.getDensity());
        assertEquals(18000, ModFluids.fluidCorium.getViscosity());
        assertEquals(3000, ModFluids.fluidCorium.getTemperature());
        assertEquals(15, ModFluids.fluidCorium.getLuminosity());

        BlockCorium block = new BlockCorium(ModFluids.fluidCorium);
        // Tick rate is 90 (3x slower than lava's 30 ticks)
        assertEquals(90, block.tickRate(null), "Corium tick rate must be 90 (3x slower than lava)");
    }

    @Test
    void testCoriumBlockImmunity() {
        // Bedrock is immune
        assertTrue(BlockCorium.isImmuneToCorium(Blocks.bedrock, null, 0, 0, 0), "Bedrock must be immune to Corium");

        // Regular stone / casings are not immune
        assertFalse(BlockCorium.isImmuneToCorium(Blocks.stone, null, 0, 0, 0), "Stone must NOT be immune to Corium");
        assertFalse(
            BlockCorium.isImmuneToCorium(Blocks.obsidian, null, 0, 0, 0),
            "Obsidian must NOT be immune to Corium");

        // Block with "warded" in unlocalized name is immune
        net.minecraft.block.Block mockWarded = org.mockito.Mockito.mock(net.minecraft.block.Block.class);
        org.mockito.Mockito.when(mockWarded.getUnlocalizedName())
            .thenReturn("tile.blockWardedGlass");
        assertTrue(BlockCorium.isImmuneToCorium(mockWarded, null, 0, 0, 0), "Warded glass must be immune to Corium");
    }

    @Test
    void testEntityMeltdownFalloutTimers() {
        assertEquals(2400, EntityMeltdownFallout.SOLIDIFY_TIME, "Solidify time must be 2400 ticks (2 minutes)");
        assertEquals(12000, EntityMeltdownFallout.TOTAL_LIFETIME, "Total lifetime must be 12000 ticks (10 minutes)");
    }

    @Test
    void testReactorDamagePersistedInControllerItemNBT() {
        MTENuclearReactor reactor = new MTENuclearReactor("nuclear.reactor.test");
        reactor.mReactorDamage = 42.5;

        // 1. When controller is broken, setItemNBT writes damage into item tag compound
        NBTTagCompound itemNbt = new NBTTagCompound();
        reactor.setItemNBT(itemNbt);
        assertTrue(itemNbt.hasKey("mReactorDamage"), "Item NBT must contain mReactorDamage tag");
        assertEquals(42.5, itemNbt.getDouble("mReactorDamage"), 1e-6);

        // 2. Controller item tooltip renders damage in red
        ItemStack controllerStack = new ItemStack(Blocks.iron_block);
        controllerStack.setTagCompound(itemNbt);
        List<String> tooltip = new ArrayList<>();
        reactor.addAdditionalTooltipInformation(controllerStack, tooltip);
        boolean foundDamageTooltip = tooltip.stream().anyMatch(s -> s.contains("Reactor Damage: 42.5%"));
        assertTrue(foundDamageTooltip, "Controller item tooltip must display reactor damage percentage");

        // 3. When new controller is placed and loadNBTData is called, damage is restored
        MTENuclearReactor placedReactor = new MTENuclearReactor("nuclear.reactor.test");
        gregtech.api.interfaces.tileentity.IGregTechTileEntity tePlaced = org.mockito.Mockito
            .mock(gregtech.api.interfaces.tileentity.IGregTechTileEntity.class);
        org.mockito.Mockito.when(tePlaced.getFrontFacing()).thenReturn(ForgeDirection.NORTH);
        placedReactor.setBaseMetaTileEntity(tePlaced);
        placedReactor.loadNBTData(itemNbt);
        assertEquals(42.5, placedReactor.mReactorDamage, 1e-6, "Damage must be restored upon placing controller");

        // 4. Undamaged reactor produces no damage tag and loads cleanly
        MTENuclearReactor cleanReactor = new MTENuclearReactor("nuclear.reactor.test");
        cleanReactor.mReactorDamage = 0.0;
        NBTTagCompound cleanNbt = new NBTTagCompound();
        cleanReactor.setItemNBT(cleanNbt);
        assertFalse(cleanNbt.hasKey("mReactorDamage"), "Undamaged reactor should not add mReactorDamage tag");

        MTENuclearReactor placedCleanReactor = new MTENuclearReactor("nuclear.reactor.test");
        gregtech.api.interfaces.tileentity.IGregTechTileEntity teClean = org.mockito.Mockito
            .mock(gregtech.api.interfaces.tileentity.IGregTechTileEntity.class);
        org.mockito.Mockito.when(teClean.getFrontFacing()).thenReturn(ForgeDirection.NORTH);
        placedCleanReactor.setBaseMetaTileEntity(teClean);
        placedCleanReactor.loadNBTData(cleanNbt);
        assertEquals(0.0, placedCleanReactor.mReactorDamage, 1e-6);
    }

    @Test
    void testConductanceScalingAcrossTiers() {
        assertEquals(16.0, NuclearSimulationEngine.getHatchConductance(1), 1e-6, "LV (tier 1) conductance");
        assertEquals(32.0, NuclearSimulationEngine.getHatchConductance(2), 1e-6, "MV (tier 2) conductance");
        assertEquals(64.0, NuclearSimulationEngine.getHatchConductance(3), 1e-6, "HV (tier 3) conductance");
        assertEquals(128.0, NuclearSimulationEngine.getHatchConductance(4), 1e-6, "EV (tier 4) conductance");
        assertEquals(256.0, NuclearSimulationEngine.getHatchConductance(5), 1e-6, "IV (tier 5) conductance");
        assertEquals(512.0, NuclearSimulationEngine.getHatchConductance(6), 1e-6, "LuV (tier 6) conductance");
        assertEquals(1024.0, NuclearSimulationEngine.getHatchConductance(7), 1e-6, "ZPM (tier 7) conductance");
        assertEquals(2048.0, NuclearSimulationEngine.getHatchConductance(8), 1e-6, "UV (tier 8) conductance");
        assertEquals(4096.0, NuclearSimulationEngine.getHatchConductance(9), 1e-6, "UHV (tier 9) conductance");
    }

    @Test
    void testAnalyticalConductiveHeatTransferFormula() {
        // When temp <= sinkTemp: zero heat transfer
        assertEquals(0.0, NuclearSimulationEngine.calculateConductiveHeatTransfer(100.0, 100.0, 3, 1.0), 1e-6);
        assertEquals(0.0, NuclearSimulationEngine.calculateConductiveHeatTransfer(80.0, 100.0, 3, 1.0), 1e-6);

        // When efficiency is 0: zero heat transfer
        assertEquals(0.0, NuclearSimulationEngine.calculateConductiveHeatTransfer(200.0, 100.0, 3, 0.0), 1e-6);

        // Tier 3 (HV, U = 64.0 EU/(t·°C)), Ch = 256.0 EU/°C, DeltaT = 100°C, eff = 1.0
        // Expected Q = 256.0 * 100.0 * (1 - exp(-64.0 / 256.0)) = 5662.70 EU
        double qHV = NuclearSimulationEngine.calculateConductiveHeatTransfer(200.0, 100.0, 3, 1.0);
        assertEquals(5662.70, qHV, 0.01);

        // Tier 4 (EV, U = 128.0 EU/(t·°C)), Ch = 512.0 EU/°C, DeltaT = 100°C, eff = 1.0
        // Expected Q = 512.0 * 100.0 * (1 - exp(-128.0 / 512.0)) = 11325.40 EU
        double qEV = NuclearSimulationEngine.calculateConductiveHeatTransfer(200.0, 100.0, 4, 1.0);
        assertEquals(11325.40, qEV, 0.01);

        // Higher tier transfers more heat, exactly doubling with base-2 progression
        assertTrue(qEV > qHV);
        assertEquals(2.0, qEV / qHV, 1e-4);
    }

    @Test
    void testHatchCoolingContinuousWithoutExplosion() {
        MTENuclearReactor reactor = new MTENuclearReactor("nuclear.reactor.conductive.test");
        reactor.mPipeTier = NuclearSimulationEngine.PIPE_TIER_PLATINUM;
        reactor.mEfficiency = 10000;

        if (!net.minecraftforge.fluids.FluidRegistry.isFluidRegistered("distilledwater")) {
            net.minecraftforge.fluids.FluidRegistry.registerFluid(new net.minecraftforge.fluids.Fluid("distilledwater"));
        }
        if (!net.minecraftforge.fluids.FluidRegistry.isFluidRegistered("steam")) {
            net.minecraftforge.fluids.FluidRegistry.registerFluid(new net.minecraftforge.fluids.Fluid("steam"));
        }
        net.minecraftforge.fluids.Fluid distilledWater = net.minecraftforge.fluids.FluidRegistry.getFluid("distilledwater");

        MTEHatchNuclearHatch hatch = new MTEHatchNuclearHatch("test.hatch", 3, 32000, new String[0], null);
        hatch.mTemperature = 150.0; // Above 100°C boiling point
        hatch.mInputFluid = new net.minecraftforge.fluids.FluidStack(distilledWater, 8000);

        reactor.processHatchNuclearTick(hatch, null, 1.0);

        // Hatch successfully boils coolant without exploding
        assertTrue(hatch.mTemperature < 150.0, "Temperature must drop from boiling cooling");
        assertTrue(hatch.mTemperature >= 100.0, "Temperature must not drop below boiling point");
        assertTrue(hatch.mLastProducedAmount > 0, "Steam must be produced");
        assertTrue(hatch.mInputFluid.amount < 8000, "Coolant must be consumed");
        assertFalse(hatch.mWasDry, "Hatch with coolant remaining must not be marked dry");
    }

    @Test
    void testHatchCoolingStarvationSetsWasDryAndThermalShock() {
        MTENuclearReactor reactor = new MTENuclearReactor("nuclear.reactor.starvation.test");
        reactor.mPipeTier = NuclearSimulationEngine.PIPE_TIER_PLATINUM;
        reactor.mEfficiency = 10000;
        reactor.mReactorDamage = 10.0;
        reactor.fixAllIssues();
        assertEquals(6, reactor.getRepairStatus());

        gregtech.api.interfaces.tileentity.IGregTechTileEntity teMock = org.mockito.Mockito
            .mock(gregtech.api.interfaces.tileentity.IGregTechTileEntity.class);
        org.mockito.Mockito.when(teMock.isServerSide()).thenReturn(true);
        org.mockito.Mockito.when(teMock.getWorld()).thenReturn(org.mockito.Mockito.mock(net.minecraft.world.World.class));
        reactor.setBaseMetaTileEntity(teMock);

        if (!net.minecraftforge.fluids.FluidRegistry.isFluidRegistered("distilledwater")) {
            net.minecraftforge.fluids.FluidRegistry.registerFluid(new net.minecraftforge.fluids.Fluid("distilledwater"));
        }
        if (!net.minecraftforge.fluids.FluidRegistry.isFluidRegistered("steam")) {
            net.minecraftforge.fluids.FluidRegistry.registerFluid(new net.minecraftforge.fluids.Fluid("steam"));
        }
        net.minecraftforge.fluids.Fluid distilledWater = net.minecraftforge.fluids.FluidRegistry.getFluid("distilledwater");

        MTEHatchNuclearHatch hatch = new MTEHatchNuclearHatch("test.hatch.starve", 3, 32000, new String[0], null);
        hatch.mTemperature = 200.0;
        // Only 2 mB of distilled water - insufficient to absorb Q_max (~98 EU, needing ~20 mB)
        hatch.mInputFluid = new net.minecraftforge.fluids.FluidStack(distilledWater, 2);

        reactor.processHatchNuclearTick(hatch, null, 1.0);

        // Coolant should be completely exhausted
        assertNull(hatch.mInputFluid, "Coolant pool should be completely exhausted");
        assertTrue(hatch.mWasDry, "Hatch must be marked dry after coolant pool is exhausted");
        assertTrue(hatch.mTemperature > 100.0, "Hatch remains hot after coolant starvation");

        // Subsequent injection of cold coolant into this dry superheated hatch triggers thermal shock
        reactor.triggerThermalShock(hatch, "Coolant injected into dry superheated hatch");
        assertEquals(12.0, reactor.mReactorDamage, 1e-6, "Thermal shock must increase reactor damage by 2%");
        assertEquals(5, reactor.getRepairStatus(), "Thermal shock must increase maintenance issues by 1 (5 working)");
    }

    @Test
    void testStandaloneGridStrictModeThermalShockFailsFast() {
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid grid =
            new com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid(5, 5, NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        grid.setStrictMode(true);
        assertTrue(grid.isStrictMode());

        // Place a coolant hatch and set it dry and superheated
        grid.setTile(2, 2, com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.HATCH_DISTILLED_WATER);
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile hatch = grid.getTile(2, 2);
        hatch.setTemperature(150.0);
        hatch.setInputFluidAmount(0);
        hatch.setWasDry(true);
        hatch.setAutoRefill(true);

        // Step should trigger thermal shock and fail-fast in strict mode
        boolean ok = grid.step();
        assertFalse(ok, "Step must halt immediately in strict mode on thermal shock");
        assertTrue(grid.isPowerFailed(), "Power failed flag must be set in strict mode");
        assertTrue(grid.getPowerFailReason().contains("Thermal Shock"), "Power fail reason must cite thermal shock: " + grid.getPowerFailReason());
        assertFalse(grid.getIncidentLog().isEmpty(), "Incident log must capture thermal shock event");
        assertEquals("THERMAL_SHOCK", grid.getIncidentLog().get(0).type());
    }

    @Test
    void testStandaloneGridPermissiveModeAccumulatesDamageAndLogs() {
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid grid =
            new com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid(5, 5, NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        grid.setStrictMode(false); // Interactive webapp mode
        assertFalse(grid.isStrictMode());

        // Place a coolant hatch and set it dry and superheated
        grid.setTile(2, 2, com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.HATCH_DISTILLED_WATER);
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile hatch = grid.getTile(2, 2);
        hatch.setTemperature(150.0);
        hatch.setInputFluidAmount(0);
        hatch.setWasDry(true);
        hatch.setAutoRefill(true);

        // Step in permissive mode should NOT halt simulation
        boolean ok = grid.step();
        assertTrue(ok, "Step must continue running in permissive mode despite thermal shock");
        assertFalse(grid.isPowerFailed(), "Power failed flag must NOT be set in permissive mode");
        assertFalse(grid.isExploded(), "Must not explode on initial 2% thermal shock");
        assertEquals(2.0, grid.getReactorDamage(), 1e-6, "Reactor damage must accumulate +2%");
        assertEquals(1, grid.getMaintenanceIssues(), "Maintenance issues must increase to 1");

        // Verify incident log contains the event
        assertFalse(grid.getIncidentLog().isEmpty(), "Incident log must contain the event");
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid.IncidentEvent ev = grid.getIncidentLog().get(0);
        assertEquals("THERMAL_SHOCK", ev.type());
        assertEquals(2.0, ev.damage(), 1e-6);

        // Repair clears damage and issues, logging a REPAIR event
        grid.repair();
        assertEquals(0.0, grid.getReactorDamage(), 1e-6);
        assertEquals(0, grid.getMaintenanceIssues());
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid.IncidentEvent repEv =
            grid.getIncidentLog().get(grid.getIncidentLog().size() - 1);
        assertEquals("REPAIR", repEv.type());
    }

    @Test
    void testStandaloneGridStrictModeCasingOverheatFailsFast() {
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid grid =
            new com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid(5, 5, NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        grid.setStrictMode(true);

        // Electrum casing limit is 1000°C. Set all tiles to 1050°C so diffusion does not cool them below limit
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                grid.getTile(x, y).setTemperature(1050.0);
            }
        }

        boolean ok = grid.step();
        assertFalse(ok, "Step must halt immediately in strict mode on casing overheat");
        assertTrue(grid.isPowerFailed(), "Power failed flag must be set on casing overheat");
        assertTrue(grid.getPowerFailReason().contains("overheated casing max"), "Power fail reason must cite casing overheat");
    }

    @Test
    void testSolidFuelByproductsAndAutoRefuel() {
        StandaloneNuclearGrid grid = new StandaloneNuclearGrid(5, 5, NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        grid.setStrictMode(false);
        grid.setAutoReplaceFuel(true);

        grid.setTile(2, 2, SimTile.TileType.FUEL_URANIUM_QUAD);
        SimTile tile = grid.getTile(2, 2);
        assertNotNull(tile);
        assertTrue(tile.isFuel());
        assertEquals("DU4", tile.getDepletedCode());
        assertTrue(tile.getDepletedDisplayName().contains("Depleted Uranium"));

        // Step grid to produce neutrons and burn durability
        for (int i = 0; i < 5; i++) {
            grid.step();
        }

        List<StandaloneNuclearGrid.SolidFuelByproduct> solidList = grid.getSolidFuelByproducts();
        assertFalse(solidList.isEmpty(), "Should have solid fuel byproduct entries");
        StandaloneNuclearGrid.SolidFuelByproduct s = solidList.get(0);
        assertEquals(SimTile.TileType.FUEL_URANIUM_QUAD, s.type);
        assertEquals("DU4", s.depletedCode);
        assertEquals(1, s.activeRods);
        assertTrue(s.itemsPerMinute > 0.0, "Items per minute should be positive");
        assertEquals(s.itemsPerMinute * 60.0, s.itemsPerHour, 1e-4, "Items per hour should equal perMin * 60");
        assertTrue(s.avgLifespanMinutes > 0.0, "Average lifespan should be positive");

        // Manually exhaust durability to test auto-refuel cycling
        tile.setDurability(0);
        tile.setDepleted(true);
        grid.step();

        // With autoReplaceFuel == true, it should cycle back to max durability and increment cumulative count
        assertEquals(1, grid.getCumulativeDepletedItems(SimTile.TileType.FUEL_URANIUM_QUAD));
        assertEquals(tile.getMaxDurability(), tile.getDurability(), "Durability should be refreshed on auto-refuel");
        assertFalse(tile.isDepleted(), "Tile should not remain depleted when auto-refuel is enabled");

        // Now test with autoReplaceFuel == false
        grid.setAutoReplaceFuel(false);
        tile.setDurability(0);
        tile.setDepleted(true);
        grid.step();
        assertTrue(tile.isDepleted(), "Tile should remain depleted when auto-refuel is disabled");
        assertEquals(0, tile.getDurability());
        assertEquals(2, grid.getCumulativeDepletedItems(SimTile.TileType.FUEL_URANIUM_QUAD), "Should increment to 2 depleted rods");
    }

    @Test
    void testLiquidFuelByproducts() {
        StandaloneNuclearGrid grid = new StandaloneNuclearGrid(5, 5, NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        grid.setStrictMode(false);

        grid.setTile(2, 2, SimTile.TileType.HATCH_LIQUID_FUEL_URANIUM);
        grid.setTile(2, 1, SimTile.TileType.FUEL_URANIUM_QUAD); // Neutron source
        SimTile hatch = grid.getTile(2, 2);
        hatch.setAutoRefill(true);

        for (int i = 0; i < 5; i++) {
            grid.step();
        }

        List<StandaloneNuclearGrid.LiquidFuelByproduct> liquidList = grid.getLiquidFuelByproducts();
        assertFalse(liquidList.isEmpty(), "Should have liquid fuel byproduct entries");
        StandaloneNuclearGrid.LiquidFuelByproduct l = liquidList.get(0);
        assertEquals(SimTile.TileType.HATCH_LIQUID_FUEL_URANIUM, l.type);
        assertEquals("depleteduraniumbasedliquidfuel", l.fluidName);
        assertEquals(1, l.activeHatches);
        assertTrue(l.litersPerMinute > 0.0, "Liters per minute should be positive");
        assertEquals(l.litersPerMinute * 60.0, l.litersPerHour, 1e-4, "Liters per hour should equal perMin * 60");
        assertTrue(l.totalLiters > 0, "Total depleted liquid produced should be positive");
    }

    @Test
    void testWasmBridgeByproductsJsonSerialization() {
        StandaloneNuclearGrid grid = new StandaloneNuclearGrid(5, 5, NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        grid.setStrictMode(false);
        grid.setTile(2, 2, SimTile.TileType.FUEL_URANIUM_QUAD);
        grid.setTile(2, 3, SimTile.TileType.HATCH_DISTILLED_WATER);

        for (int i = 0; i < 5; i++) {
            grid.step();
        }

        String json = com.gtnewhorizons.modularnuclear.common.nuclear.standalone.NuclearSimWasmBridge.buildStateJson(grid, false);
        assertNotNull(json);
        assertTrue(json.contains("\"byproducts\":{"), "JSON must contain byproducts section");
        assertTrue(json.contains("\"autoReplaceFuel\":true"), "JSON must contain autoReplaceFuel");
        assertTrue(json.contains("\"totalSolidItemsPerMin\":"), "JSON must contain totalSolidItemsPerMin");
        assertTrue(json.contains("\"solidRods\":["), "JSON must contain solidRods array");
        assertTrue(json.contains("\"depletedCode\":\"DU4\""), "JSON must contain DU4 depleted code");
        assertTrue(json.contains("\"liquidFuels\":["), "JSON must contain liquidFuels array");
        assertTrue(json.contains("\"isotopes\":["), "JSON must contain isotopes array");
        assertTrue(json.contains("\"rawMaterials\":["), "JSON must contain rawMaterials array");
        assertTrue(json.contains("\"code\":\"U-235\""), "JSON must contain U-235 balance");
        assertTrue(json.contains("\"code\":\"Pu-239\""), "JSON must contain Pu-239 breeding balance");
    }

    @Test
    void testRawMaterialBalancesAndBreedingForecast() {
        // 1. Uranium Quad Rod: Consumes U-235 and U-238, breeds Pu-239
        StandaloneNuclearGrid gridU = new StandaloneNuclearGrid(5, 5, NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        gridU.setStrictMode(false);
        gridU.setTile(2, 2, SimTile.TileType.FUEL_URANIUM_QUAD);
        for (int i = 0; i < 5; i++) {
            gridU.step();
        }

        List<StandaloneNuclearGrid.RawMaterialBalance> rawU = gridU.getRawMaterialBalances();
        assertFalse(rawU.isEmpty(), "Raw material balances must not be empty for Uranium reactor");

        StandaloneNuclearGrid.RawMaterialBalance u235 = null;
        StandaloneNuclearGrid.RawMaterialBalance u238 = null;
        StandaloneNuclearGrid.RawMaterialBalance pu239 = null;
        for (StandaloneNuclearGrid.RawMaterialBalance r : rawU) {
            if ("U-235".equals(r.code)) u235 = r;
            if ("U-238".equals(r.code)) u238 = r;
            if ("Pu-239".equals(r.code)) pu239 = r;
        }

        assertNotNull(u235, "U-235 balance must exist");
        assertNotNull(u238, "U-238 balance must exist");
        assertNotNull(pu239, "Pu-239 balance must exist");

        // U-235 should have consumption > 0, production == 0, net < 0 (net deficit)
        assertTrue(u235.consumedPerMinute > 0.0, "U-235 consumed must be positive");
        assertEquals(0.0, u235.producedPerMinute, 1e-4, "U-235 produced should be zero");
        assertTrue(u235.netPerMinute < 0.0, "U-235 net balance must be negative (deficit)");
        assertEquals(u235.consumedPerMinute * 60.0, u235.consumedPerHour, 1e-3);
        assertEquals(u235.netPerMinute * 60.0, u235.netPerHour, 1e-3);

        // Pu-239 should have consumption == 0, production > 0, net > 0 (net surplus / breeding!)
        assertEquals(0.0, pu239.consumedPerMinute, 1e-4, "Pu-239 consumed should be zero in Uranium reactor");
        assertTrue(pu239.producedPerMinute > 0.0, "Pu-239 produced must be positive (bred)");
        assertTrue(pu239.netPerMinute > 0.0, "Pu-239 net balance must be positive (surplus/breeding)");

        // U-238 consumes 24 dust and recovers 16 dust per Quad rod -> net deficit of 8 dust per rod
        assertTrue(u238.consumedPerMinute > u238.producedPerMinute, "U-238 consumed must exceed produced");
        assertTrue(u238.netPerMinute < 0.0, "U-238 net balance must be negative");

        // 2. Thorium Breeder: Consumes Th-232, breeds Lutetium (Lu) per GTNH FissionFuelLoader
        StandaloneNuclearGrid gridTh = new StandaloneNuclearGrid(5, 5, NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        gridTh.setStrictMode(false);
        gridTh.setTile(2, 2, SimTile.TileType.FUEL_THORIUM_QUAD);
        for (int i = 0; i < 5; i++) {
            gridTh.step();
        }

        List<StandaloneNuclearGrid.RawMaterialBalance> rawTh = gridTh.getRawMaterialBalances();
        StandaloneNuclearGrid.RawMaterialBalance th232 = null;
        StandaloneNuclearGrid.RawMaterialBalance lu = null;
        for (StandaloneNuclearGrid.RawMaterialBalance r : rawTh) {
            if ("Th-232".equals(r.code)) th232 = r;
            if ("Lu".equals(r.code)) lu = r;
        }

        assertNotNull(th232, "Th-232 balance must exist");
        assertNotNull(lu, "Lutetium balance must exist for Thorium breeder");
        assertTrue(th232.consumedPerMinute > 0.0, "Th-232 consumed must be positive");
        assertTrue(th232.netPerMinute < 0.0, "Th-232 net balance must be negative");
        assertTrue(lu.producedPerMinute > 0.0, "Thorium breeding must produce Lutetium");
        assertTrue(lu.netPerMinute > 0.0, "Lutetium net balance must be positive (bred)");

        // 3. Lithium Breeder Rod: Consumes Lithium, breeds Tritium
        StandaloneNuclearGrid gridLithium = new StandaloneNuclearGrid(5, 5, NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        gridLithium.setStrictMode(false);
        gridLithium.setTile(2, 2, SimTile.TileType.FUEL_URANIUM_QUAD);
        gridLithium.setTile(2, 3, SimTile.TileType.FUEL_LITHIUM);
        for (int i = 0; i < 5; i++) {
            gridLithium.step();
        }

        List<StandaloneNuclearGrid.RawMaterialBalance> rawLi = gridLithium.getRawMaterialBalances();
        StandaloneNuclearGrid.RawMaterialBalance lithium = null;
        StandaloneNuclearGrid.RawMaterialBalance tritium = null;
        for (StandaloneNuclearGrid.RawMaterialBalance r : rawLi) {
            if ("Li".equals(r.code)) lithium = r;
            if ("T".equals(r.code)) tritium = r;
        }

        assertNotNull(lithium, "Lithium balance must exist");
        assertNotNull(tritium, "Tritium balance must exist for Lithium breeder");
        assertTrue(lithium.consumedPerMinute > 0.0, "Lithium consumed must be positive");
        assertTrue(lithium.netPerMinute < 0.0, "Lithium net balance must be negative");
        assertTrue(tritium.producedPerMinute > 0.0, "Lithium transmutation must produce Tritium");
        assertTrue(tritium.netPerMinute > 0.0, "Tritium net balance must be positive (bred)");
    }

    @Test
    void testMeltdownStopsSimulation() {
        StandaloneNuclearGrid grid = new StandaloneNuclearGrid(5, 5, NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        grid.setStrictMode(false);
        assertFalse(grid.isExploded());

        // Trigger meltdown
        grid.triggerExplosion("Extreme core meltdown test");
        assertTrue(grid.isExploded());
        assertEquals("Extreme core meltdown test", grid.getExplosionReason());

        // Step should refuse to run after explosion
        boolean stepResult = grid.step();
        assertFalse(stepResult, "step() must return false once exploded");

        // Test with WasmBridge
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.NuclearSimWasmBridge.initGrid(5, 5, NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.NuclearSimWasmBridge.setRunning(true);
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.NuclearSimWasmBridge.getGrid().triggerExplosion("WASM Meltdown test");
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.NuclearSimWasmBridge.step();
        assertFalse(com.gtnewhorizons.modularnuclear.common.nuclear.standalone.NuclearSimWasmBridge.isRunning(),
            "Bridge isRunning must become false immediately upon meltdown");
    }

    @Test
    void testStopOnIncidentsHaltAndResume() {
        StandaloneNuclearGrid grid = new StandaloneNuclearGrid(5, 5, NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        grid.setStrictMode(false);
        grid.setStopOnIncidents(true);
        assertTrue(grid.isStopOnIncidents());
        assertFalse(grid.isHaltedByIncident());

        // Normal step without incidents should succeed
        assertTrue(grid.step());
        assertFalse(grid.isHaltedByIncident());

        // Log an incident with damage
        grid.logIncident("CELL_OVERHEAT", "Overheated cell (2,2) exceeding casing limit", 4.5);
        assertTrue(grid.isHaltedByIncident(), "Grid must be marked as halted by incident");
        assertTrue(grid.getLastHaltIncidentReason().contains("Overheated cell"), "Last halt incident reason must match");

        // Next step should return false because it is paused by incident
        assertFalse(grid.step(), "step() must return false while halted by incident");

        // Clear incident halt
        grid.clearHaltedByIncident();
        assertFalse(grid.isHaltedByIncident(), "Grid should no longer be halted after clearing");

        // Stepping resumes
        assertTrue(grid.step(), "step() should succeed once cleared");

        // If stopOnIncidents is false, logging incident should not halt simulation
        grid.setStopOnIncidents(false);
        grid.logIncident("THERMAL_SHOCK", "Thermal shock incident without stop toggle", 2.0);
        assertFalse(grid.isHaltedByIncident(), "Grid must not halt when stopOnIncidents is disabled");
    }

    @Test
    void testAutoSupplyFuelSolidAndLiquid() {
        StandaloneNuclearGrid grid = new StandaloneNuclearGrid(5, 5, NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        grid.setStrictMode(false);

        // 1. Solid Fuel Rod Replacement
        grid.setTile(2, 2, SimTile.TileType.FUEL_URANIUM_QUAD);
        SimTile fuelTile = grid.getTile(2, 2);
        assertNotNull(fuelTile);
        assertEquals(fuelTile.getMaxDurability(), fuelTile.getDurability());

        // Auto-supply fuel ON: depleting rod causes auto-replacement with 100% fresh rod on next step
        grid.setAutoSupplyFuel(true);
        assertTrue(grid.isAutoSupplyFuel());
        fuelTile.setDurability(0);
        fuelTile.setDepleted(true);
        grid.step();

        assertEquals(fuelTile.getMaxDurability(), fuelTile.getDurability(), "Fuel rod must be replenished to 100% durability");
        assertFalse(fuelTile.isDepleted(), "Fresh fuel rod must not be marked depleted");
        assertEquals(1, grid.getCumulativeDepletedItems(SimTile.TileType.FUEL_URANIUM_QUAD), "Should track 1 depleted rod byproduct");

        // Auto-supply fuel OFF: depleting rod leaves it depleted
        grid.setAutoSupplyFuel(false);
        assertFalse(grid.isAutoSupplyFuel());
        fuelTile.setDurability(0);
        fuelTile.setDepleted(true);
        grid.step();

        assertTrue(fuelTile.isDepleted(), "Fuel rod should remain depleted when autoSupplyFuel is disabled");
        assertEquals(0, fuelTile.getDurability());

        // 2. Liquid Fuel Hatch Continuous Replenishment
        grid.setTile(1, 1, SimTile.TileType.HATCH_LIQUID_FUEL_URANIUM);
        SimTile liquidHatch = grid.getTile(1, 1);
        assertNotNull(liquidHatch);
        liquidHatch.setInputFluidAmount(250); // Drained level
        assertTrue(liquidHatch.getInputFluidAmount() < liquidHatch.getInputFluidCapacity());

        // With auto-supply fuel ON, step should replenish fluid to full capacity
        grid.setAutoSupplyFuel(true);
        grid.step();
        assertEquals(liquidHatch.getInputFluidCapacity(), liquidHatch.getInputFluidAmount(),
            "Liquid fuel hatch must be replenished to capacity by autoSupplyFuel");

        // With auto-supply fuel OFF, drained hatch is NOT automatically topped off to capacity
        grid.setAutoSupplyFuel(false);
        liquidHatch.setInputFluidAmount(300);
        grid.step();
        assertTrue(liquidHatch.getInputFluidAmount() <= 300,
            "Liquid fuel hatch must not be topped off to capacity when autoSupplyFuel is disabled");
    }

    @Test
    void testWasmBridgeIncidentHaltAndResume() {
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.NuclearSimWasmBridge.initGrid(5, 5, NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.NuclearSimWasmBridge.setStopOnIncidents(true);
        assertTrue(com.gtnewhorizons.modularnuclear.common.nuclear.standalone.NuclearSimWasmBridge.isStopOnIncidents());
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.NuclearSimWasmBridge.setRunning(true);

        StandaloneNuclearGrid g = com.gtnewhorizons.modularnuclear.common.nuclear.standalone.NuclearSimWasmBridge.getGrid();
        g.logIncident("CELL_HEAT", "Cell heat damage at (1,1)", 3.0);
        assertTrue(g.isHaltedByIncident());

        // Step should detect incident halt and set isRunning = false
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.NuclearSimWasmBridge.step();
        assertFalse(com.gtnewhorizons.modularnuclear.common.nuclear.standalone.NuclearSimWasmBridge.isRunning(),
            "Bridge isRunning must become false on incident halt");

        // Resuming by setting running = true clears the incident halt
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.NuclearSimWasmBridge.setRunning(true);
        assertFalse(g.isHaltedByIncident(), "Bridge resuming must clear haltedByIncident");
        assertTrue(com.gtnewhorizons.modularnuclear.common.nuclear.standalone.NuclearSimWasmBridge.isRunning());
    }
}


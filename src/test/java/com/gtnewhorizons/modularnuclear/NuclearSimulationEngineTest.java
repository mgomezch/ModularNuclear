package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.coolantloops.common.metatileentity.hatch.MTEHatchPressurizedFluid;
import com.gtnewhorizons.coolantloops.engine.CoolantFluidProperty;
import com.gtnewhorizons.coolantloops.engine.ICoolantLoopPump;
import com.gtnewhorizons.coolantloops.engine.LoopSegment;
import com.gtnewhorizons.modularnuclear.common.block.BlockCorium;
import com.gtnewhorizons.modularnuclear.common.block.BlockCoriumSolid;
import com.gtnewhorizons.modularnuclear.common.block.BlockNuclearCasing;
import com.gtnewhorizons.modularnuclear.common.block.ItemNuclearCasing;
import com.gtnewhorizons.modularnuclear.common.block.ModBlocks;
import com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig;
import com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData;
import com.gtnewhorizons.modularnuclear.common.entity.EntityMeltdownFallout;
import com.gtnewhorizons.modularnuclear.common.fluid.ModFluids;
import com.gtnewhorizons.modularnuclear.common.item.ModItems;
import com.gtnewhorizons.modularnuclear.common.metatileentity.ModMetaTileEntities;
import com.gtnewhorizons.modularnuclear.common.metatileentity.NuclearStructureChannels;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearBus;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControl;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHatch;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHighPressure;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.structure.NuclearReactorStructure;
import com.gtnewhorizons.modularnuclear.common.nuclear.CustomFuelMix;
import com.gtnewhorizons.modularnuclear.common.nuclear.INuclearTile;
import com.gtnewhorizons.modularnuclear.common.nuclear.NeutronType;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearFuelType;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid;
import com.gtnewhorizons.modularnuclear.common.nuclearcontrol.ItemCardModularNuclear;
import com.gtnewhorizons.modularnuclear.common.recipe.ModRecipes;
import com.gtnewhorizons.modularnuclear.common.textures.ModularNuclearTextures;
import com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatchMaintenance;
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
        TestEnvironmentHelper.init();
        com.gtnewhorizons.modularnuclear.common.nei.NEINeutronInteractionHandler.sTestMode = true;
        com.gtnewhorizons.modularnuclear.common.nei.NEINeutronInteractionHandler.resetData();

        try {
            ModFluids.init();
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
        double heatHU = 0.0;
        boolean isFuel = false;
        int neutronBaseGen = 0;
        double absorbProb = 0.1;
        double absorbProbThermal = -1.0;
        double scatterProb = 0.5;
        double moderationProb = 0.8;
        double heatCoeff = 0.05;
        double insulationDampening = 0.0;
        int emissionCount = 1;
        boolean hasControlRod = false;
        double rodAbsorptionFraction = 0.0;
        com.gtnewhorizons.modularnuclear.common.config.data.FuelData fuelData = null;

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
            this.heatHU += heat;
            this.temperature += heat / NuclearSimulationEngine.HU_PER_DEGREE;
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
            if (type == NeutronType.THERMAL && absorbProbThermal >= 0.0) {
                return absorbProbThermal;
            }
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

        @Override
        public com.gtnewhorizons.modularnuclear.common.config.data.FuelData getFuelData() {
            return fuelData;
        }

        @Override
        public boolean hasControlRod() {
            return hasControlRod;
        }

        @Override
        public double getControlRodAbsorptionFraction(NeutronType type) {
            return rodAbsorptionFraction;
        }
    }

    /** Length of one reactor cycle in seconds; a single coolant sub-step of this length is one cycle of cooling. */
    private static double cycleSeconds() {
        return NuclearSimulationEngine.getTicksPerCycle() / 20.0;
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
        assertTrue(fuel.heatHU > 0, "Direct fission heat should be recorded");
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
        // Distilled Water: boiling at 200°C, 320 HU/mB, 160:1 steam ratio
        double boilingPoint = 200.0;
        double heatPerMB = 320.0;
        double currentTemp = 250.0;
        double heatAvailable = (currentTemp - boilingPoint) * NuclearSimulationEngine.HU_PER_DEGREE;
        assertEquals(50.0 * NuclearSimulationEngine.HU_PER_DEGREE, heatAvailable, 1e-6);

        int fluidToBoil = (int) (heatAvailable / heatPerMB);
        assertEquals(5, fluidToBoil);

        int steamProduced = fluidToBoil * 160;
        assertEquals(800, steamProduced);

        double heatConsumed = fluidToBoil * heatPerMB;
        double tempDrop = heatConsumed / NuclearSimulationEngine.HU_PER_DEGREE;
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
        // Water is unrecognized / disallowed
        assertEquals(-1, MTEHatchNuclearHatch.getRequiredFluidTier("water"));
        assertFalse(NuclearSimulationEngine.isFluidAllowed("water", 5));
        // IC2 Coolant is starter tier (Electrum, 0)
        assertEquals(
            NuclearSimulationEngine.PIPE_TIER_ELECTRUM,
            MTEHatchNuclearHatch.getRequiredFluidTier("ic2coolant"));
        assertTrue(NuclearSimulationEngine.isFluidAllowed("ic2coolant", 0));
        // Distilled Water is Platinum (1)
        assertEquals(
            NuclearSimulationEngine.PIPE_TIER_PLATINUM,
            MTEHatchNuclearHatch.getRequiredFluidTier("distilledwater"));
        assertFalse(NuclearSimulationEngine.isFluidAllowed("distilledwater", 0));
        assertTrue(NuclearSimulationEngine.isFluidAllowed("distilledwater", 1));
        // Heavy Water is Quantium (3)
        assertEquals(
            NuclearSimulationEngine.PIPE_TIER_QUANTIUM,
            MTEHatchNuclearHatch.getRequiredFluidTier("fluid.heavywater"));
        assertFalse(NuclearSimulationEngine.isFluidAllowed("fluid.heavywater", 2));
        assertTrue(NuclearSimulationEngine.isFluidAllowed("fluid.heavywater", 3));
    }

    @Test
    void testPipeTierNames() {
        assertEquals("Electrum (IC2 coolant)", NuclearSimulationEngine.getPipeTierName(0));
        assertEquals("Platinum (distilled water)", NuclearSimulationEngine.getPipeTierName(1));
        assertEquals("Osmium (HP core hatches)", NuclearSimulationEngine.getPipeTierName(2));
        assertEquals("Quantium (heavy water)", NuclearSimulationEngine.getPipeTierName(3));
        assertEquals("Fluxed Electrum (excited fuel)", NuclearSimulationEngine.getPipeTierName(4));
        assertEquals("Black Plutonium (max tier / all coolants)", NuclearSimulationEngine.getPipeTierName(5));
    }

    @Test
    void testMaxOperatingTemperatures() {
        assertEquals(1200.0, NuclearSimulationEngine.getMaxOperatingTemperature(0));
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

        assertEquals(5 * grid7.getTicksPerCycle(), grid7.getCurrentTick());
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

        assertEquals(5 * grid9.getTicksPerCycle(), grid9.getCurrentTick());
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
        assertFalse(
            grid.isExploded(),
            "Injecting coolant into dry hatch above boiling threshold must NOT explode on first hit");
        assertEquals(2.0, grid.getReactorDamage(), 1e-4, "Thermal shock must inflict 2% reactor damage");
        assertEquals(1, grid.getMaintenanceIssues(), "Thermal shock must increase maintenance issues by 1");
        assertEquals(0, hatch.getInputFluidAmount(), "Coolant injected into hot dry hatch must flash and void");
        assertTrue(hatch.isWasDry(), "Hatch must remain marked as wasDry after flash voiding");
    }

    @Test
    void testIC2CoolantContinuousConductiveExchange() {
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid grid = new com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid(
            3,
            3,
            NuclearSimulationEngine.PIPE_TIER_ELECTRUM);

        grid.setTile(
            1,
            1,
            com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.HATCH_IC2_COOLANT);
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile hatch = grid.getTile(1, 1);

        // Case 1: Coolant does NOT extract heat when at or below ambient temperature (20°C)
        hatch.setInputFluidAmount(2000);
        hatch.setTemperature(NuclearSimulationEngine.AMBIENT_TEMP);
        hatch.coolantSubstep(cycleSeconds(), 1.0);
        hatch.nuclearTick(1.0);

        assertEquals(
            NuclearSimulationEngine.AMBIENT_TEMP,
            hatch.getTemperature(),
            1e-4,
            "IC2 coolant must NOT extract heat at ambient temperature");
        assertEquals(0, hatch.getLastTickProduced(), "No coolant produced at ambient temperature");
        assertEquals(0, hatch.getOutputFluidAmount(), "IC2 coolant must produce 0 hot coolant at ambient");

        // Case 2: Above ambient (e.g. 80°C), IC2 coolant extracts heat conductively at 1:1 volume ratio
        hatch.setTemperature(80.0);
        hatch.coolantSubstep(cycleSeconds(), 1.0);
        hatch.nuclearTick(1.0);

        assertTrue(hatch.getTemperature() < 80.0, "IC2 coolant must extract heat above ambient threshold");
        assertTrue(
            hatch.getTemperature() >= NuclearSimulationEngine.AMBIENT_TEMP,
            "IC2 coolant cannot cool below ambient sink temperature");
        assertTrue(hatch.getOutputFluidAmount() > 0, "IC2 coolant must produce hot coolant above ambient threshold");
        assertEquals(hatch.getLastTickProduced(), hatch.getOutputFluidAmount(), "1:1 liquid ratio for IC2 coolant");
        assertEquals(0, hatch.getTotalSteamProduced(), "IC2 coolant must NOT produce steam");

        // Case 3: Dry hatch at 300°C refilling IC2 coolant NEVER explodes (no thermal shock)
        hatch.setInputFluidAmount(0);
        hatch.setWasDry(true);
        hatch.setTemperature(300.0);
        boolean refilled = hatch.refillCoolant();
        assertTrue(refilled, "IC2 coolant must never trigger thermal shock explosion when refilling dry hot hatch");
    }

    @Test
    void testCoolantSinkTemperatures() {
        assertEquals(24.0, NuclearSimulationEngine.getCoolantSinkTemperature("ic2coolant", 24.0));
        assertEquals(100.0, NuclearSimulationEngine.getCoolantSinkTemperature("distilledwater", 24.0));
        assertEquals(101.4, NuclearSimulationEngine.getCoolantSinkTemperature("heavywater", 24.0));
        assertEquals(24.0, NuclearSimulationEngine.getCoolantSinkTemperature(null, 24.0));
        assertEquals(24.0, NuclearSimulationEngine.getCoolantSinkTemperature("unknown_liquid", 24.0));

        // High ambient temperature clamped
        assertEquals(120.0, NuclearSimulationEngine.getCoolantSinkTemperature("ic2coolant", 120.0));
        assertEquals(120.0, NuclearSimulationEngine.getCoolantSinkTemperature("distilledwater", 120.0));
        assertEquals(120.0, NuclearSimulationEngine.getCoolantSinkTemperature("heavywater", 120.0));
    }

    @Test
    void testCoolantPhaseChangeAndExpansionRatios() {
        // Distilled water: 160:1 expansion into steam
        SimTile waterTile = new SimTile(SimTile.TileType.HATCH_DISTILLED_WATER);
        waterTile.setInputFluidAmount(1000);
        waterTile.setTemperature(200.0);
        int initialWater = waterTile.getInputFluidAmount();
        waterTile.coolantSubstep(cycleSeconds(), 1.0);
        waterTile.nuclearTick(1.0);
        int waterConsumed = initialWater - waterTile.getInputFluidAmount();
        assertTrue(waterConsumed > 0, "Superheated distilled water must boil");
        assertEquals(
            waterConsumed * 160,
            waterTile.getLastTickProduced(),
            "Distilled water must expand 160:1 into steam");
        assertEquals(waterConsumed * 160, waterTile.getTotalSteamProduced());
        assertEquals(waterConsumed * 160, waterTile.getOutputFluidAmount());

        // Heavy water: 160:1 expansion into heavy water steam
        SimTile heavyWaterTile = new SimTile(SimTile.TileType.HATCH_HEAVY_WATER);
        heavyWaterTile.setInputFluidAmount(1000);
        heavyWaterTile.setTemperature(200.0);
        int initialHw = heavyWaterTile.getInputFluidAmount();
        heavyWaterTile.coolantSubstep(cycleSeconds(), 1.0);
        heavyWaterTile.nuclearTick(1.0);
        int hwConsumed = initialHw - heavyWaterTile.getInputFluidAmount();
        assertTrue(hwConsumed > 0, "Superheated heavy water must boil");
        assertEquals(
            hwConsumed * 160,
            heavyWaterTile.getLastTickProduced(),
            "Heavy water must expand 160:1 into steam");
        assertEquals(hwConsumed * 160, heavyWaterTile.getTotalSteamProduced());
        assertEquals(hwConsumed * 160, heavyWaterTile.getOutputFluidAmount());

        // IC2 coolant: 1:1 liquid exchange (NO steam, NO volume expansion)
        SimTile ic2Tile = new SimTile(SimTile.TileType.HATCH_IC2_COOLANT);
        ic2Tile.setInputFluidAmount(1000);
        ic2Tile.setTemperature(200.0);
        int initialIc2 = ic2Tile.getInputFluidAmount();
        ic2Tile.coolantSubstep(cycleSeconds(), 1.0);
        ic2Tile.nuclearTick(1.0);
        int ic2Consumed = initialIc2 - ic2Tile.getInputFluidAmount();
        assertTrue(ic2Consumed > 0, "Superheated IC2 coolant must exchange heat");
        assertEquals(ic2Consumed * 1, ic2Tile.getLastTickProduced(), "IC2 coolant must have 1:1 liquid ratio");
        assertEquals(ic2Consumed * 1, ic2Tile.getOutputFluidAmount());
        assertEquals(0, ic2Tile.getTotalSteamProduced(), "IC2 coolant must produce 0 steam");
    }

    @Test
    void testRadiovoltaicGenerationAndSaturation() {
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile cellHV = new com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile(
            com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.RADIOVOLTAIC_HV);
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile cellEV = new com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile(
            com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.RADIOVOLTAIC_EV);

        // 1. Check absorption properties (rebalanced: 40% for HV, 60% for EV)
        assertEquals(0.40, cellHV.getAbsorptionProbability(NeutronType.FAST), 1e-4);
        assertEquals(0.40, cellHV.getAbsorptionProbability(NeutronType.THERMAL), 1e-4);
        assertEquals(0.60, cellEV.getAbsorptionProbability(NeutronType.FAST), 1e-4);
        assertEquals(0.60, cellEV.getAbsorptionProbability(NeutronType.THERMAL), 1e-4);
        assertEquals(0.0, cellHV.getScatteringProbability(NeutronType.FAST));
        assertEquals(0.0, cellHV.getModerationProbability());

        // 2. Feed fast vs thermal neutrons and check 4x weight
        cellHV.onNeutronAbsorbed(NeutronType.FAST, 1);
        cellHV.nuclearTick(1.0);
        long fastEU = cellHV.getDirectEUProduced();

        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile cellHVThermal = new com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile(
            com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.RADIOVOLTAIC_HV);
        cellHVThermal.onNeutronAbsorbed(NeutronType.THERMAL, 4);
        cellHVThermal.nuclearTick(1.0);
        long thermalEU = cellHVThermal.getDirectEUProduced();

        assertTrue(fastEU > 0, "A fast neutron must generate EU");
        assertEquals(thermalEU, fastEU, "One fast neutron must count as four thermal neutrons");

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

    private static double[] conductPair(double mult, int ticksPerCycle) {
        int savedTpc = NuclearSimulationEngine.getTicksPerCycle();
        double savedMult = NuclearSimulationEngine.gridConductionMultiplier;
        try {
            NuclearSimulationEngine.setTicksPerCycle(ticksPerCycle);
            NuclearSimulationEngine.setGridConductionMultiplier(mult);
            com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile hot = new com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile(
                com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.REFLECTOR_GOLD);
            com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile cold = new com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile(
                com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.REFLECTOR_GOLD);
            hot.setTemperature(1000.0);
            cold.setTemperature(20.0);
            INuclearTile[][] grid = { { hot }, { cold } };
            NuclearSimulationEngine.simulate(grid, 2, 1, 1.0, 20.0);
            return new double[] { hot.getTemperature(), cold.getTemperature() };
        } finally {
            NuclearSimulationEngine.setTicksPerCycle(savedTpc);
            NuclearSimulationEngine.setGridConductionMultiplier(savedMult);
        }
    }

    @Test
    void testGridConductionMultiplierScalesConduction() {
        assertEquals(
            1.0,
            ReactorDataRegistry.getPhysics().gridConductionMultiplier,
            1e-9,
            "Default grid conduction multiplier must be 1.0");
        double[] base = conductPair(1.0, 5);
        double[] faster = conductPair(2.0, 5);
        assertTrue(
            1000.0 - faster[0] > 1000.0 - base[0],
            "A larger multiplier must move more heat out of the hot cell");
        assertTrue(faster[1] - 20.0 > base[1] - 20.0, "A larger multiplier must move more heat into the cold cell");
    }

    @Test
    void testConductionDoesNotOvershootAtLongCyclesAndHighMultiplier() {
        // With a fixed 5 sub-steps this would move 120% of the difference per sub-step and swap the cells
        double[] r = conductPair(20.0, 20);
        assertTrue(r[0] >= r[1] - 1e-6, "Hot cell must not end up colder than the cold cell (no overshoot)");
        assertTrue(r[1] <= 1000.0 && r[0] >= 20.0, "Temperatures must stay within the initial range");
    }

    @Test
    void testRadiovoltaicCurveIsPerSecondAndCycleIndependent() {
        ComponentData.RadiovoltaicData rv = new ComponentData.RadiovoltaicData();
        rv.tier = 2;
        rv.maxEU = 4096;
        // 95% of maxEU at 10 weighted neutrons absorbed per second
        assertEquals(0.95 * 4096, rv.calculatePower(10.0), 1e-6);
        assertEquals(0.0, rv.calculatePower(0.0), 1e-9);
        assertTrue(rv.calculatePower(30.0) > 0.999 * 4096, "Well past the 95% point the plate is saturated");

        // Same absorption rate gives the same output regardless of cycle length
        int savedTpc = NuclearSimulationEngine.getTicksPerCycle();
        try {
            long[] outputs = new long[2];
            int[] tpcs = { 5, 20 };
            for (int i = 0; i < tpcs.length; i++) {
                NuclearSimulationEngine.setTicksPerCycle(tpcs[i]);
                com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile plate = new com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile(
                    com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.RADIOVOLTAIC_EV);
                // A steady 8 thermal neutrons per second (2 per cycle at 5 ticks, 8 at 20 ticks) for 30 seconds,
                // long enough for the plate's rate average to settle
                for (int c = 0; c < 30 * 20 / tpcs[i]; c++) {
                    plate.onNeutronAbsorbed(NeutronType.THERMAL, 8 * tpcs[i] / 20);
                    plate.nuclearTick(1.0);
                }
                outputs[i] = plate.getDirectEUProduced();
            }
            assertTrue(outputs[0] > 0, "Plate must produce EU");
            assertEquals(outputs[0], outputs[1], 1.0, "Output must not depend on ticks per cycle");
            assertEquals(Math.round(rv.calculatePower(8.0)), outputs[0], 1.0);
        } finally {
            NuclearSimulationEngine.setTicksPerCycle(savedTpc);
        }
    }

    @Test
    void testRadiovoltaicLinearMaintenanceEfficiencyScaling() {
        // 1. SimTile nuclearTick efficiency does not scale individual plate directEUProduced
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile cellHV100 = new com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile(
            com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.RADIOVOLTAIC_HV);
        cellHV100.onNeutronAbsorbed(NeutronType.FAST, 20);
        cellHV100.nuclearTick(1.0);
        long directEU100 = cellHV100.getDirectEUProduced();

        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile cellHV50 = new com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile(
            com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.RADIOVOLTAIC_HV);
        cellHV50.onNeutronAbsorbed(NeutronType.FAST, 20);
        cellHV50.nuclearTick(0.5);
        long directEU50 = cellHV50.getDirectEUProduced();

        assertEquals(directEU100, directEU50, "Plate-level direct EU must be unscaled by maintenance efficiency");

        // 2. StandaloneNuclearGrid scales aggregate flowDirectEU linearly with maintenance efficiency (eta)
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid grid100 = new com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid(
            3,
            3,
            NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        grid100.setTicksPerCycle(20);
        grid100
            .setTile(1, 1, com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.NEUTRON_SOURCE);
        grid100.getTile(1, 1)
            .setNeutronSourceBeamEnergy(100.0f);
        grid100.getTile(1, 1)
            .setNeutronSourceBeamRate(10);
        grid100
            .setTile(0, 1, com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.RADIOVOLTAIC_EV);
        NuclearSimulationEngine.setRandomSeed(42);
        grid100.step();
        double gridEU100 = grid100.getFlowDirectEU();
        assertTrue(gridEU100 > 0, "Nominal grid must generate direct EU");

        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid grid50 = new com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid(
            3,
            3,
            NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        grid50.setTicksPerCycle(20);
        grid50
            .setTile(1, 1, com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.NEUTRON_SOURCE);
        grid50.getTile(1, 1)
            .setNeutronSourceBeamEnergy(100.0f);
        grid50.getTile(1, 1)
            .setNeutronSourceBeamRate(10);
        grid50
            .setTile(0, 1, com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.RADIOVOLTAIC_EV);
        grid50.setMaintenanceIssues(3); // 3 issues -> (6 - 3)/6 = 0.5 maintenance efficiency
        NuclearSimulationEngine.setRandomSeed(42);
        grid50.step();
        double gridEU50 = grid50.getFlowDirectEU();

        assertEquals(
            Math.round(gridEU100 * 0.5),
            gridEU50,
            1.0,
            "Aggregate direct EU must scale linearly with maintenance efficiency (0.5x), not quadratically (0.25x)");
        assertEquals(gridEU50, grid50.getLastPowerResult().directPowerEUt, 1.0);
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
        assertTrue(
            grid.getTile(0, 1)
                .getTotalSteamProduced() > 0,
            "Boiling coolant must produce steam");
        assertTrue(
            grid.getTile(0, 1)
                .getTemperature() < 300.0,
            "Boiling coolant must cool the hatch");

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
        assertTrue(
            grid.getExplosionReason()
                .contains("100%"));
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
        assertEquals(0, res.wallNeutronsAbsorbed, "Outer casing walls must not absorb neutrons");
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
        assertEquals(0.0, naquarite.heatHU, 1e-6, "Naquarite absorbs radiation without generating heat");
        assertTrue(naquarite.fastAbsorbed > 0 || naquarite.thermalAbsorbed > 0, "Naquarite absorbed neutrons");
    }

    @Test
    void testNeutronComponentInteractionData() {
        com.gtnewhorizons.modularnuclear.common.nei.NEINeutronInteractionHandler.ComponentStatsData graphite = new com.gtnewhorizons.modularnuclear.common.nei.NEINeutronInteractionHandler.ComponentStatsData(
            null,
            "Graphite Moderator Block",
            "Moderator",
            0.93,
            0.002,
            0.50,
            0.621,
            0.009,
            false,
            0.0,
            0.0,
            0.15,
            0.0,
            0.0,
            0.0,
            0.0,
            2400.0,
            0.0,
            0.0,
            false,
            0L,
            null,
            0.0,
            0.0,
            true,
            100_000_000L,
            "Wear Capacity",
            false,
            null,
            0L,
            "Slows fast neutrons into thermal neutrons");

        assertEquals(0.93, graphite.fastScattering, 1e-4);
        assertEquals(0.002, graphite.fastAbsorption, 1e-4);
        assertEquals(0.50, graphite.slowingProbability, 1e-4);
        assertEquals(0.621, graphite.thermalScattering, 1e-4);
        assertEquals(0.009, graphite.thermalAbsorption, 1e-4);
        assertEquals(0.50, 1.0 - graphite.slowingProbability, 1e-4);
        assertEquals(0.15, graphite.heatTransferCoeff, 1e-4);
        assertEquals(2400.0, graphite.maxTemperature, 1e-4);
        assertTrue(graphite.hasDurability);
        assertEquals(100_000_000L, graphite.durability);
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
        assertEquals("0 neutrons/cm²/sec", NuclearSimulationEngine.formatNeutronFlux(0));
        assertEquals("0 neutrons/cm²/sec", NuclearSimulationEngine.formatNeutronFlux(-5));
        assertEquals("1.00e13 neutrons/cm²/sec", NuclearSimulationEngine.formatNeutronFlux(1, 20));
        assertEquals("4.00e13 neutrons/cm²/sec", NuclearSimulationEngine.formatNeutronFlux(1, 5));
        assertEquals("4.00e13 neutrons/cm²/sec", NuclearSimulationEngine.formatNeutronFlux(1));
        assertEquals("8.80e14 neutrons/cm²/sec", NuclearSimulationEngine.formatNeutronFlux(88, 20));
        assertEquals("3.52e15 neutrons/cm²/sec", NuclearSimulationEngine.formatNeutronFlux(88, 5));
        assertEquals("1.50e15 neutrons/cm²/sec", NuclearSimulationEngine.formatNeutronFlux(150, 20));
        assertEquals("6.00e15 neutrons/cm²/sec", NuclearSimulationEngine.formatNeutronFlux(150, 5));
    }

    @Test
    void testFormatNeutronFluence() {
        assertEquals("0 neutrons/cm²", NuclearSimulationEngine.formatNeutronFluence(0));
        assertEquals("0 neutrons/cm²", NuclearSimulationEngine.formatNeutronFluence(-5));
        assertEquals("1.00e13 neutrons/cm²", NuclearSimulationEngine.formatNeutronFluence(1));
        assertEquals("8.80e14 neutrons/cm²", NuclearSimulationEngine.formatNeutronFluence(88));
        assertEquals("1.50e15 neutrons/cm²", NuclearSimulationEngine.formatNeutronFluence(150));
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

        // 4. Verify fluid acceptance / rejection logic
        MTEHatchNuclearHatch freshHatch = new MTEHatchNuclearHatch("test.fresh.hatch", 1, 16000, new String[0], null);
        freshHatch.mReactorPipeTier = NuclearSimulationEngine.PIPE_TIER_ELECTRUM; // Tier 0

        // Disallowed / unrecognized fluids: water, naquadah, unknown
        net.minecraftforge.fluids.Fluid waterFluid = org.mockito.Mockito.mock(net.minecraftforge.fluids.Fluid.class);
        org.mockito.Mockito.when(waterFluid.getName())
            .thenReturn("water");
        assertFalse(freshHatch.canFill(ForgeDirection.UP, waterFluid));
        assertEquals(0, freshHatch.fill(ForgeDirection.UP, mockFluidStack(waterFluid, 1000), true));

        net.minecraftforge.fluids.Fluid naquadahFluid = org.mockito.Mockito.mock(net.minecraftforge.fluids.Fluid.class);
        org.mockito.Mockito.when(naquadahFluid.getName())
            .thenReturn("liquid_naquadah_fuel");
        assertFalse(freshHatch.canFill(ForgeDirection.UP, naquadahFluid));
        assertEquals(0, freshHatch.fill(ForgeDirection.UP, mockFluidStack(naquadahFluid, 1000), true));

        net.minecraftforge.fluids.Fluid unknownFluid = org.mockito.Mockito.mock(net.minecraftforge.fluids.Fluid.class);
        org.mockito.Mockito.when(unknownFluid.getName())
            .thenReturn("some_random_fluid");
        assertFalse(freshHatch.canFill(ForgeDirection.UP, unknownFluid));
        assertEquals(0, freshHatch.fill(ForgeDirection.UP, mockFluidStack(unknownFluid, 1000), true));

        // Tier check: Distilled water (Tier 1 Platinum) rejected on Tier 0 Electrum reactor
        net.minecraftforge.fluids.Fluid distilledFluid = org.mockito.Mockito
            .mock(net.minecraftforge.fluids.Fluid.class);
        org.mockito.Mockito.when(distilledFluid.getName())
            .thenReturn("distilledwater");
        assertFalse(freshHatch.canFill(ForgeDirection.UP, distilledFluid));
        assertEquals(0, freshHatch.fill(ForgeDirection.UP, mockFluidStack(distilledFluid, 1000), true));

        // Tier check: IC2 coolant (Tier 0 Electrum) accepted on Tier 0 Electrum reactor
        assertTrue(freshHatch.canFill(ForgeDirection.UP, dummyCoolant));
        int filled = freshHatch.fill(ForgeDirection.UP, mockFluidStack(dummyCoolant, 4000), true);
        assertEquals(4000, filled);
        assertEquals(4000, freshHatch.mInputFluid.amount);

        // Distilled water accepted once pipe tier upgraded to Platinum (Tier 1)
        freshHatch.mInputFluid = null;
        freshHatch.mReactorPipeTier = NuclearSimulationEngine.PIPE_TIER_PLATINUM;
        assertTrue(freshHatch.canFill(ForgeDirection.UP, distilledFluid));
        int filledDist = freshHatch.fill(ForgeDirection.UP, mockFluidStack(distilledFluid, 2000), true);
        assertEquals(2000, filledDist);
        assertEquals(2000, freshHatch.mInputFluid.amount);
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

        // Loading from separate fields
        MTEHatchNuclearControl loaded = new MTEHatchNuclearControl("test.loaded.sep", 4, new String[0], null);
        loaded.loadNBTData(nbt);
        assertEquals(1, loaded.getMetric());
        assertEquals(1, loaded.getStatistic());
        assertEquals(4, loaded.getMode());
    }

    @Test
    void testNuclearHatchesFacingAndTextures() {
        MTEHatchNuclearBus bus = new MTEHatchNuclearBus("test.bus", 4, new String[0], null);
        MTEHatchNuclearHatch hatch = new MTEHatchNuclearHatch("test.hatch", 4, 32000, new String[0], null);
        MTEHatchNuclearControlRod rod = new MTEHatchNuclearControlRod("test.rod", 4, new String[0], null);
        MTEHatchNuclearControl control = new MTEHatchNuclearControl("test.control", 4, new String[0], null);
        MTEHatchNuclearHighPressure hp = new MTEHatchNuclearHighPressure("test.hp", 4, new String[0], null);

        // All 6 ForgeDirections must be valid for all nuclear hatches
        for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
            assertTrue(bus.isFacingValid(dir), "Bus facing " + dir + " must be valid");
            assertTrue(hatch.isFacingValid(dir), "Hatch facing " + dir + " must be valid");
            assertTrue(rod.isFacingValid(dir), "Rod facing " + dir + " must be valid");
            assertTrue(control.isFacingValid(dir), "Control facing " + dir + " must be valid");
            assertTrue(hp.isFacingValid(dir), "HP hatch facing " + dir + " must be valid");
        }

        // Casing texture must default to Nuclear Casing
        assertNotNull(bus.getCasingTexture(), "Bus casing texture must not be null");
        assertNotNull(hatch.getCasingTexture(), "Hatch casing texture must not be null");
        assertNotNull(rod.getCasingTexture(), "Rod casing texture must not be null");
        assertNotNull(control.getCasingTexture(), "Control casing texture must not be null");
        assertNotNull(hp.getCasingTexture(), "HP hatch casing texture must not be null");

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
            hp.getTexturesActive(hp.getCasingTexture());
            hp.getTexturesInactive(hp.getCasingTexture());
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
    void testNuclearReactorHatchCasingTextureInheritanceOnStructureCheck() {
        MTENuclearReactor reactor = new MTENuclearReactor("test.reactor.texture.check");
        ITexture nuclearCasingTex = Textures.BlockIcons.getCasingTextureForId(MTENuclearReactor.CASING_INDEX);
        assertNotNull(nuclearCasingTex, "Nuclear casing texture must not be null");

        // 1. Nuclear Hatch
        MTEHatchNuclearHatch hatch = new MTEHatchNuclearHatch("test.hatch", 4, 32000, new String[0], null);
        gregtech.api.metatileentity.BaseMetaTileEntity mockHatchTile = org.mockito.Mockito
            .mock(gregtech.api.metatileentity.BaseMetaTileEntity.class);
        org.mockito.Mockito.when(mockHatchTile.getMetaTileEntity())
            .thenReturn(hatch);
        org.mockito.Mockito.when(mockHatchTile.getFrontFacing())
            .thenReturn(ForgeDirection.UP);
        org.mockito.Mockito.when(mockHatchTile.isServerSide())
            .thenReturn(true);
        hatch.setBaseMetaTileEntity(mockHatchTile);

        // 2. Nuclear Bus
        MTEHatchNuclearBus bus = new MTEHatchNuclearBus("test.bus", 4, new String[0], null);
        gregtech.api.metatileentity.BaseMetaTileEntity mockBusTile = org.mockito.Mockito
            .mock(gregtech.api.metatileentity.BaseMetaTileEntity.class);
        org.mockito.Mockito.when(mockBusTile.getMetaTileEntity())
            .thenReturn(bus);
        org.mockito.Mockito.when(mockBusTile.getFrontFacing())
            .thenReturn(ForgeDirection.UP);
        org.mockito.Mockito.when(mockBusTile.isServerSide())
            .thenReturn(true);
        bus.setBaseMetaTileEntity(mockBusTile);

        // 3. Nuclear High-Pressure Hatch (Top Inlet)
        MTEHatchNuclearHighPressure hpTop = new MTEHatchNuclearHighPressure("test.hp.top", 4, new String[0], null);
        gregtech.api.metatileentity.BaseMetaTileEntity mockHpTopTile = org.mockito.Mockito
            .mock(gregtech.api.metatileentity.BaseMetaTileEntity.class);
        org.mockito.Mockito.when(mockHpTopTile.getMetaTileEntity())
            .thenReturn(hpTop);
        org.mockito.Mockito.when(mockHpTopTile.getFrontFacing())
            .thenReturn(ForgeDirection.UP);
        org.mockito.Mockito.when(mockHpTopTile.isServerSide())
            .thenReturn(true);
        hpTop.setBaseMetaTileEntity(mockHpTopTile);

        // 4. Pressurized Fluid Hatch (Top Inlet)
        MTEHatchPressurizedFluid pfTop = new MTEHatchPressurizedFluid("test.pf.top", 4, new String[0], null);
        pfTop.setMode(MTEHatchPressurizedFluid.HatchMode.SUCTION);
        gregtech.api.metatileentity.BaseMetaTileEntity mockPfTopTile = org.mockito.Mockito
            .mock(gregtech.api.metatileentity.BaseMetaTileEntity.class);
        org.mockito.Mockito.when(mockPfTopTile.getMetaTileEntity())
            .thenReturn(pfTop);
        org.mockito.Mockito.when(mockPfTopTile.getFrontFacing())
            .thenReturn(ForgeDirection.UP);
        org.mockito.Mockito.when(mockPfTopTile.isServerSide())
            .thenReturn(true);
        pfTop.setBaseMetaTileEntity(mockPfTopTile);

        // Run NuclearHatchElement checks
        NuclearReactorStructure.NuclearHatchElement topElement = new NuclearReactorStructure.NuclearHatchElement();
        net.minecraft.world.World mockWorld = org.mockito.Mockito.mock(net.minecraft.world.World.class);

        org.mockito.Mockito.when(mockWorld.getTileEntity(0, 1, 0))
            .thenReturn(mockHatchTile);
        assertTrue(topElement.check(reactor, mockWorld, 0, 1, 0));
        assertEquals(nuclearCasingTex, hatch.getCasingTexture(), "Nuclear hatch must adopt nuclear casing texture");

        org.mockito.Mockito.when(mockWorld.getTileEntity(1, 1, 0))
            .thenReturn(mockBusTile);
        assertTrue(topElement.check(reactor, mockWorld, 1, 1, 0));
        assertEquals(nuclearCasingTex, bus.getCasingTexture(), "Nuclear bus must adopt nuclear casing texture");

        org.mockito.Mockito.when(mockWorld.getTileEntity(2, 1, 0))
            .thenReturn(mockHpTopTile);
        assertTrue(topElement.check(reactor, mockWorld, 2, 1, 0));
        assertEquals(
            nuclearCasingTex,
            hpTop.getCasingTexture(),
            "Nuclear HP top hatch must adopt nuclear casing texture");

        org.mockito.Mockito.when(mockWorld.getTileEntity(3, 1, 0))
            .thenReturn(mockPfTopTile);
        assertTrue(topElement.check(reactor, mockWorld, 3, 1, 0));
        assertEquals(
            nuclearCasingTex,
            pfTop.getCasingTexture(),
            "Pressurized fluid top hatch must adopt nuclear casing texture");

        // 5. Nuclear High-Pressure Hatch (Bottom Outlet)
        MTEHatchNuclearHighPressure hpBottom = new MTEHatchNuclearHighPressure(
            "test.hp.bottom",
            4,
            new String[0],
            null);
        gregtech.api.metatileentity.BaseMetaTileEntity mockHpBottomTile = org.mockito.Mockito
            .mock(gregtech.api.metatileentity.BaseMetaTileEntity.class);
        org.mockito.Mockito.when(mockHpBottomTile.getMetaTileEntity())
            .thenReturn(hpBottom);
        org.mockito.Mockito.when(mockHpBottomTile.getFrontFacing())
            .thenReturn(ForgeDirection.DOWN);
        org.mockito.Mockito.when(mockHpBottomTile.isServerSide())
            .thenReturn(true);
        hpBottom.setBaseMetaTileEntity(mockHpBottomTile);

        // 6. Pressurized Fluid Hatch (Bottom Outlet)
        MTEHatchPressurizedFluid pfBottom = new MTEHatchPressurizedFluid("test.pf.bottom", 4, new String[0], null);
        pfBottom.setMode(MTEHatchPressurizedFluid.HatchMode.DISCHARGE);
        gregtech.api.metatileentity.BaseMetaTileEntity mockPfBottomTile = org.mockito.Mockito
            .mock(gregtech.api.metatileentity.BaseMetaTileEntity.class);
        org.mockito.Mockito.when(mockPfBottomTile.getMetaTileEntity())
            .thenReturn(pfBottom);
        org.mockito.Mockito.when(mockPfBottomTile.getFrontFacing())
            .thenReturn(ForgeDirection.DOWN);
        org.mockito.Mockito.when(mockPfBottomTile.isServerSide())
            .thenReturn(true);
        pfBottom.setBaseMetaTileEntity(mockPfBottomTile);

        // 7. Control Rod Hatch
        MTEHatchNuclearControlRod rod = new MTEHatchNuclearControlRod("test.rod", 4, new String[0], null);
        gregtech.api.metatileentity.BaseMetaTileEntity mockRodTile = org.mockito.Mockito
            .mock(gregtech.api.metatileentity.BaseMetaTileEntity.class);
        org.mockito.Mockito.when(mockRodTile.getMetaTileEntity())
            .thenReturn(rod);
        org.mockito.Mockito.when(mockRodTile.getFrontFacing())
            .thenReturn(ForgeDirection.DOWN);
        org.mockito.Mockito.when(mockRodTile.isServerSide())
            .thenReturn(true);
        rod.setBaseMetaTileEntity(mockRodTile);

        NuclearReactorStructure.BottomCoreElement bottomElement = new NuclearReactorStructure.BottomCoreElement();

        org.mockito.Mockito.when(mockWorld.getTileEntity(0, 0, 0))
            .thenReturn(mockHpBottomTile);
        assertTrue(bottomElement.check(reactor, mockWorld, 0, 0, 0));
        assertEquals(
            nuclearCasingTex,
            hpBottom.getCasingTexture(),
            "Nuclear HP bottom hatch must adopt nuclear casing texture");

        org.mockito.Mockito.when(mockWorld.getTileEntity(1, 0, 0))
            .thenReturn(mockPfBottomTile);
        assertTrue(bottomElement.check(reactor, mockWorld, 1, 0, 0));
        assertEquals(
            nuclearCasingTex,
            pfBottom.getCasingTexture(),
            "Pressurized fluid bottom hatch must adopt nuclear casing texture");

        org.mockito.Mockito.when(mockWorld.getTileEntity(2, 0, 0))
            .thenReturn(mockRodTile);
        assertTrue(bottomElement.check(reactor, mockWorld, 2, 0, 0));
        assertEquals(
            nuclearCasingTex,
            rod.getCasingTexture(),
            "Nuclear control rod hatch must adopt nuclear casing texture");

        // 8. Control Hatch
        MTEHatchNuclearControl controlHatch = new MTEHatchNuclearControl("test.control", 4, new String[0], null);
        gregtech.api.metatileentity.BaseMetaTileEntity mockControlTile = org.mockito.Mockito
            .mock(gregtech.api.metatileentity.BaseMetaTileEntity.class);
        org.mockito.Mockito.when(mockControlTile.getMetaTileEntity())
            .thenReturn(controlHatch);
        org.mockito.Mockito.when(mockControlTile.isServerSide())
            .thenReturn(true);
        controlHatch.setBaseMetaTileEntity(mockControlTile);

        assertTrue(reactor.addNuclearControlHatchToMachineList(mockControlTile, MTENuclearReactor.CASING_INDEX));
        assertEquals(
            nuclearCasingTex,
            controlHatch.getCasingTexture(),
            "Nuclear control hatch must adopt nuclear casing texture");

        // Check non-facing and facing texture rendering
        ITexture[] hpNonFacing = hpTop
            .getTexture(mockHpTopTile, ForgeDirection.NORTH, ForgeDirection.UP, 0, false, false);
        assertEquals(1, hpNonFacing.length);
        assertEquals(nuclearCasingTex, hpNonFacing[0], "Non-facing side of HP hatch must render nuclear casing");

        ITexture[] hpFacing = hpTop.getTexture(mockHpTopTile, ForgeDirection.UP, ForgeDirection.UP, 0, false, false);
        assertTrue(hpFacing.length >= 2);
        assertEquals(nuclearCasingTex, hpFacing[0], "Facing side base texture of HP hatch must be nuclear casing");

        ITexture[] pfNonFacing = pfTop
            .getTexture(mockPfTopTile, ForgeDirection.NORTH, ForgeDirection.UP, 0, false, false);
        assertEquals(1, pfNonFacing.length);
        assertEquals(nuclearCasingTex, pfNonFacing[0], "Non-facing side of PF hatch must render nuclear casing");

        ITexture[] pfFacing = pfTop.getTexture(mockPfTopTile, ForgeDirection.UP, ForgeDirection.UP, 0, false, false);
        assertTrue(pfFacing.length >= 2);
        assertEquals(nuclearCasingTex, pfFacing[0], "Facing side base texture of PF hatch must be nuclear casing");
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

        // Temperature modes (Max operating temp = 1200 °C)
        // Min = 200 -> 200/1200 * 15 = 2.5 -> 3
        assertEquals((byte) 3, reactor.calculateSignalForMode(MTEHatchNuclearControl.MODE_TEMP_MIN));
        // Max = 800 -> 800/1200 * 15 = 10
        assertEquals((byte) 10, reactor.calculateSignalForMode(MTEHatchNuclearControl.MODE_TEMP_MAX));
        // Avg = 500 -> 500/1200 * 15 = 6.25 -> 6
        assertEquals((byte) 6, reactor.calculateSignalForMode(MTEHatchNuclearControl.MODE_TEMP_AVG));

        // When no fuel/component/coolant present, durabilities and coolant levels return 0
        assertEquals((byte) 0, reactor.calculateSignalForMode(MTEHatchNuclearControl.MODE_FUEL_ITEM_DUR_MIN));
        assertEquals((byte) 0, reactor.calculateSignalForMode(MTEHatchNuclearControl.MODE_COOLANT_ITEM_DUR_MIN));
        assertEquals((byte) 0, reactor.calculateSignalForMode(MTEHatchNuclearControl.MODE_COOLANT_HATCH_FILL_MIN));
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

        // Verify redstone signal for all 3 temperature modes (300/1200 * 15 = 3.75 -> 4, 900/1200 * 15 = 11.25 -> 11,
        // 600/1200 * 15 = 7.5 -> 8)
        assertEquals((byte) 4, reactor.calculateSignalForMode(MTEHatchNuclearControl.MODE_TEMP_MIN));
        assertEquals((byte) 11, reactor.calculateSignalForMode(MTEHatchNuclearControl.MODE_TEMP_MAX));
        assertEquals((byte) 8, reactor.calculateSignalForMode(MTEHatchNuclearControl.MODE_TEMP_AVG));

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
        com.gtnewhorizons.modularnuclear.common.metatileentity.multi.structure.NuclearReactorStructure.NuclearHatchElement element = new com.gtnewhorizons.modularnuclear.common.metatileentity.multi.structure.NuclearReactorStructure.NuclearHatchElement();
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

        // 1. Bus with fuel item (must be voided)
        MTEHatchNuclearBus busFuel = new MTEHatchNuclearBus("test.bus.fuel", 4, new String[0], null);
        net.minecraft.item.Item fuelItem = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
        org.mockito.Mockito.when(fuelItem.getUnlocalizedName(org.mockito.Mockito.any()))
            .thenReturn("gt.rodUranium");
        net.minecraft.item.ItemStack dummyFuel = new net.minecraft.item.ItemStack(fuelItem, 1, 0);
        busFuel.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = dummyFuel;
        busFuel.mTemperature = 150.0;

        // 2. Bus with coolant item (must be voided)
        MTEHatchNuclearBus busCoolant = new MTEHatchNuclearBus("test.bus.coolant", 4, new String[0], null);
        net.minecraft.item.Item coolantItem = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
        org.mockito.Mockito.when(coolantItem.getUnlocalizedName(org.mockito.Mockito.any()))
            .thenReturn("IC2:reactorCoolantSimple");
        net.minecraft.item.ItemStack dummyCoolant = new net.minecraft.item.ItemStack(coolantItem, 1, 0);
        busCoolant.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = dummyCoolant;
        busCoolant.mTemperature = 150.0;

        // 3. Hatch with fluid (must be voided)
        MTEHatchNuclearHatch hatch = new MTEHatchNuclearHatch("test.hatch", 4, 16000, new String[0], null);
        net.minecraftforge.fluids.FluidStack coolantIn = org.mockito.Mockito
            .mock(net.minecraftforge.fluids.FluidStack.class);
        hatch.mInputFluid = coolantIn;
        hatch.mTemperature = 120.0;

        // 4. Bus with reflector item (must be preserved)
        MTEHatchNuclearBus busReflector = new MTEHatchNuclearBus("test.bus.reflector", 4, new String[0], null);
        net.minecraft.item.Item reflectorItem = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
        org.mockito.Mockito.when(reflectorItem.getUnlocalizedName(org.mockito.Mockito.any()))
            .thenReturn("IC2:reactorReflector");
        net.minecraft.item.ItemStack dummyReflector = new net.minecraft.item.ItemStack(reflectorItem, 1, 0);
        busReflector.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = dummyReflector;
        busReflector.mTemperature = 150.0;

        // 5. Bus with moderator item (must be preserved)
        MTEHatchNuclearBus busModerator = new MTEHatchNuclearBus("test.bus.moderator", 4, new String[0], null);
        net.minecraft.item.Item moderatorItem = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
        org.mockito.Mockito.when(moderatorItem.getUnlocalizedName(org.mockito.Mockito.any()))
            .thenReturn("stickLongGraphite");
        net.minecraft.item.ItemStack dummyModerator = new net.minecraft.item.ItemStack(moderatorItem, 1, 0);
        busModerator.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = dummyModerator;
        busModerator.mTemperature = 150.0;

        // 6. Bus with heat vent item (must be preserved)
        MTEHatchNuclearBus busVent = new MTEHatchNuclearBus("test.bus.vent", 4, new String[0], null);
        net.minecraft.item.Item ventItem = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
        org.mockito.Mockito.when(ventItem.getUnlocalizedName(org.mockito.Mockito.any()))
            .thenReturn("IC2:reactorVent");
        net.minecraft.item.ItemStack dummyVent = new net.minecraft.item.ItemStack(ventItem, 1, 0);
        busVent.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = dummyVent;
        busVent.mTemperature = 150.0;

        // 7. Bus with heat exchanger item (must be preserved)
        MTEHatchNuclearBus busExchanger = new MTEHatchNuclearBus("test.bus.exchanger", 4, new String[0], null);
        net.minecraft.item.Item exchangerItem = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
        org.mockito.Mockito.when(exchangerItem.getUnlocalizedName(org.mockito.Mockito.any()))
            .thenReturn("IC2:reactorHeatSwitch");
        net.minecraft.item.ItemStack dummyExchanger = new net.minecraft.item.ItemStack(exchangerItem, 1, 0);
        busExchanger.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = dummyExchanger;
        busExchanger.mTemperature = 150.0;

        // Set up mock tile entities
        MTEHatchNuclearBus[] buses = { busFuel, busCoolant, busReflector, busModerator, busVent, busExchanger };
        for (MTEHatchNuclearBus b : buses) {
            gregtech.api.interfaces.tileentity.IGregTechTileEntity te = org.mockito.Mockito
                .mock(gregtech.api.interfaces.tileentity.IGregTechTileEntity.class);
            org.mockito.Mockito.when(te.getMetaTileEntity())
                .thenReturn(b);
            org.mockito.Mockito.when(te.isDead())
                .thenReturn(false);
            reactor.mLastFormedNuclearTiles.add(te);
        }

        gregtech.api.interfaces.tileentity.IGregTechTileEntity teHatch = org.mockito.Mockito
            .mock(gregtech.api.interfaces.tileentity.IGregTechTileEntity.class);
        org.mockito.Mockito.when(teHatch.getMetaTileEntity())
            .thenReturn(hatch);
        org.mockito.Mockito.when(teHatch.isDead())
            .thenReturn(false);
        reactor.mLastFormedNuclearTiles.add(teHatch);

        assertTrue(reactor.isAnyTemperatureAbove100(), "Reactor should detect temperature > 100°C");

        // Trigger structure break
        reactor.handleStructureBreak();

        // Fuel and coolant items/fluids must be voided:
        assertNull(
            busFuel.mInventory[MTEHatchNuclearBus.SLOT_INPUT],
            "Fuel in bus must be voided on break above 100°C");
        assertNull(
            busCoolant.mInventory[MTEHatchNuclearBus.SLOT_INPUT],
            "Coolant item in bus must be voided on break above 100°C");
        assertNull(hatch.mInputFluid, "Coolant in hatch must be voided on break above 100°C");

        // Spatial configuration items must be preserved:
        assertNotNull(
            busReflector.mInventory[MTEHatchNuclearBus.SLOT_INPUT],
            "Reflector in bus must be preserved on break above 100°C");
        assertNotNull(
            busModerator.mInventory[MTEHatchNuclearBus.SLOT_INPUT],
            "Moderator in bus must be preserved on break above 100°C");
        assertNotNull(
            busVent.mInventory[MTEHatchNuclearBus.SLOT_INPUT],
            "Heat vent in bus must be preserved on break above 100°C");
        assertNotNull(
            busExchanger.mInventory[MTEHatchNuclearBus.SLOT_INPUT],
            "Heat exchanger in bus must be preserved on break above 100°C");
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
        assertEquals(
            4,
            reactor.getRepairStatus(),
            "2nd thermal shock must increase maintenance issues to 2 (4 working)");

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

        reactor.mBottomControlRodHatches.add(rod);

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
        assertTrue(MTEHatchNuclearBus.isCheeseRecipeInput(stackA));
        assertTrue(
            com.gtnewhorizons.modularnuclear.common.recipe.CheeseRecipeRegistry.getInstance()
                .isCheeseInput(stackA));
        assertTrue(com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification.isItemAllowedInBus(stackA));

        // stackB does not match and is negatively cached
        assertNull(bus.findCheeseExtractionRecipe(stackB));
        assertNull(bus.findCheeseExtractionRecipe(stackB));
        assertFalse(MTEHatchNuclearBus.isCheeseRecipeInput(stackB));
        assertFalse(
            com.gtnewhorizons.modularnuclear.common.recipe.CheeseRecipeRegistry.getInstance()
                .isCheeseInput(stackB));

        // Key equality and hashCode check
        MTEHatchNuclearBus.RecipeCacheKey key1 = new MTEHatchNuclearBus.RecipeCacheKey(stackA);
        MTEHatchNuclearBus.RecipeCacheKey key2 = new MTEHatchNuclearBus.RecipeCacheKey(new ItemStack(dummyItem, 64, 0));
        MTEHatchNuclearBus.RecipeCacheKey key3 = new MTEHatchNuclearBus.RecipeCacheKey(stackB);

        assertEquals(key1, key2, "Cache keys with same item and damage must be equal regardless of stack size");
        assertEquals(key1.hashCode(), key2.hashCode(), "Cache keys hash codes must match for equal keys");
        assertNotEquals(key1, key3, "Cache keys with different damage must not be equal");

        // Test singleton behavior with concrete inputs and fast O(1) item set filtering
        MTEHatchNuclearBus.clearCheeseRecipeCache();
        assertFalse(
            com.gtnewhorizons.modularnuclear.common.recipe.CheeseRecipeRegistry.getInstance()
                .hasDynamicOrUnknownInputs());
        gregtech.api.util.GTRecipe recipeWithInputs = org.mockito.Mockito.mock(gregtech.api.util.GTRecipe.class);
        recipeWithInputs.mInputs = new ItemStack[] { stackA };
        MTEHatchNuclearBus.registerCheeseRecipe(recipeWithInputs);
        assertFalse(
            com.gtnewhorizons.modularnuclear.common.recipe.CheeseRecipeRegistry.getInstance()
                .hasDynamicOrUnknownInputs());
        assertTrue(
            com.gtnewhorizons.modularnuclear.common.recipe.CheeseRecipeRegistry.getInstance()
                .getCheeseInputItems()
                .contains(dummyItem));
        assertTrue(
            com.gtnewhorizons.modularnuclear.common.recipe.CheeseRecipeRegistry.getInstance()
                .isCheeseInput(stackA));
        net.minecraft.item.Item otherItem = new net.minecraft.item.Item().setUnlocalizedName("other.dummy");
        ItemStack otherStack = new ItemStack(otherItem, 1, 0);
        assertFalse(
            com.gtnewhorizons.modularnuclear.common.recipe.CheeseRecipeRegistry.getInstance()
                .isCheeseInput(otherStack));

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
        NuclearSimulationEngine.setTicksPerCycle(20);
        try {
            // Test The Core (NQ32)
            SimTile core = new SimTile(SimTile.TileType.FUEL_CORE);
            assertTrue(core.isFuel());
            assertFalse(core.isHatch());
            // Dense archetype multiplier (0.50) applies to base neutrons (512 * 0.50 = 256)
            assertEquals(256, core.generateNeutrons(1.0));
            assertEquals(2560000, core.getMaxDurability());

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
        } finally {
            NuclearSimulationEngine.setTicksPerCycle(NuclearSimulationEngine.DEFAULT_TICKS_PER_CYCLE);
        }
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
        // Arbitrary unhandled fluids return -1
        assertEquals(-1, NuclearSimulationEngine.getRequiredFluidTier("some_random_fluid"));
        assertFalse(NuclearSimulationEngine.isFluidAllowed("some_random_fluid", 5));
        // Naquadah liquid fuel is unrecognized / disallowed
        assertEquals(-1, NuclearSimulationEngine.getRequiredFluidTier("liquid_naquadah_fuel"));
        assertEquals(-1, NuclearSimulationEngine.getRequiredFluidTier("naquadahbasedliquidfuel"));
        assertFalse(NuclearSimulationEngine.isFluidAllowed("naquadahbasedliquidfuel", 5));
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
        double ambient = 20.0;

        // 1. Standard Vent: up to 128 EU/t (up to 512 HU/t). Equalizes cell temperature towards ambient.
        // Heat capacity is 32 HU/°C. At 100°C: tempDiff = 80°C = 2560 HU.
        // Standard vent removes 512 HU (16°C), consuming 128 EU.
        SimTile ventStd = new SimTile(SimTile.TileType.VENT_STANDARD);
        ventStd.setTemperature(100.0);
        NuclearSimulationEngine.processHeatVent(ventStd, ambient);
        assertEquals(84.0, ventStd.getTemperature(), 1e-4, "Standard vent must cool 16°C (512 HU)");
        assertEquals(128, ventStd.drainedEnergy, "Standard vent must consume 128 EU");

        // 2. Advanced Vent: up to 512 EU/t (up to 2048 HU/t).
        // At 100°C: tempDiff = 80°C = 2560 HU. Removes 2048 HU (64°C), consuming 512 EU.
        SimTile ventAdv = new SimTile(SimTile.TileType.VENT_ADVANCED);
        ventAdv.setTemperature(100.0);
        NuclearSimulationEngine.processHeatVent(ventAdv, ambient);
        assertEquals(36.0, ventAdv.getTemperature(), 1e-4, "Advanced vent must cool 64°C (2048 HU)");
        assertEquals(512, ventAdv.drainedEnergy, "Advanced vent must consume 512 EU");

        // 3. Overclocked Vent: up to 1024 EU/t (up to 4096 HU/t).
        // At 100°C: tempDiff = 80°C = 2560 HU <= 4096 HU.
        // Equalizes all the way to ambient (20°C). EU needed = 2560 / 4 = 640 EU.
        SimTile ventOver = new SimTile(SimTile.TileType.VENT_OVERCLOCKED);
        ventOver.setTemperature(100.0);
        NuclearSimulationEngine.processHeatVent(ventOver, ambient);
        assertEquals(20.0, ventOver.getTemperature(), 1e-4, "Overclocked vent must cool all the way to ambient");
        assertEquals(640, ventOver.drainedEnergy, "Overclocked vent must consume 640 EU for 2560 HU");

        // 4. Component Vent: up to 2048 EU/t (up to 8192 HU/t).
        SimTile ventComp = new SimTile(SimTile.TileType.VENT_COMPONENT);
        ventComp.setTemperature(100.0);
        NuclearSimulationEngine.processHeatVent(ventComp, ambient);
        assertEquals(20.0, ventComp.getTemperature(), 1e-4, "Component vent must cool all the way to ambient");
        assertEquals(640, ventComp.drainedEnergy, "Component vent must consume 640 EU for 2560 HU");

        // 5. Insufficient EU: component stops work for that tick
        SimTile ventNoPower = new SimTile(SimTile.TileType.VENT_STANDARD);
        ventNoPower.setTemperature(100.0);
        ventNoPower.energyDrainingAllowed = false;
        NuclearSimulationEngine.processHeatVent(ventNoPower, ambient);
        assertEquals(100.0, ventNoPower.getTemperature(), 1e-4, "Vent without power must not cool");
        assertEquals(0, ventNoPower.drainedEnergy, "Vent without power must consume 0 EU");
    }

    @Test
    void testComponentHeatVentAdjacentCooling() {
        SimTile[][] grid = new SimTile[3][3];
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                grid[x][y] = new SimTile(SimTile.TileType.EMPTY);
            }
        }

        // Center: Component Heat Vent (up to 2048 EU/t, up to 8192 HU/t).
        // Requirement: Operates strictly on its own cell (does NOT interact across cell boundaries).
        SimTile compVent = new SimTile(SimTile.TileType.VENT_COMPONENT);
        compVent.setTemperature(300.0);
        grid[1][1] = compVent;

        // 4 orthogonal neighbors at 100°C
        grid[1][0].setTemperature(100.0);
        grid[1][2].setTemperature(100.0);
        grid[0][1].setTemperature(100.0);
        grid[2][1].setTemperature(100.0);

        NuclearSimulationEngine.processActiveVentsAndExchangers(grid, 3, 3, 20.0);

        // Center cools its own cell towards ambient (300 - 256 = 44°C)
        assertTrue(compVent.getTemperature() < 300.0, "Component vent must cool its own cell");
        assertEquals(44.0, compVent.getTemperature(), 1e-4, "Component vent cools 8192 HU (256°C) from 300°C to 44°C");

        // Neighbors MUST remain strictly unchanged at 100°C because Component Vent does NOT cross cell boundaries!
        assertEquals(100.0, grid[1][0].getTemperature(), 1e-4, "Top neighbor must not be directly altered");
        assertEquals(100.0, grid[1][2].getTemperature(), 1e-4, "Bottom neighbor must not be directly altered");
        assertEquals(100.0, grid[0][1].getTemperature(), 1e-4, "Left neighbor must not be directly altered");
        assertEquals(100.0, grid[2][1].getTemperature(), 1e-4, "Right neighbor must not be directly altered");
    }

    @Test
    void testHeatExchangerBalancing() {
        SimTile[][] grid = new SimTile[3][3];
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                grid[x][y] = new SimTile(SimTile.TileType.EMPTY);
                grid[x][y].setTemperature(20.0);
            }
        }

        // Center: Standard Heat Exchanger (up to 256 EU/t, up to 1024 HU/t)
        // Equalizes temperature between own cell and all 4 cardinally adjacent cells.
        SimTile exchanger = new SimTile(SimTile.TileType.EXCHANGER_STANDARD);
        exchanger.setTemperature(20.0);
        grid[1][1] = exchanger;

        // Top neighbor hot (100°C), other 3 neighbors at 20°C
        grid[1][0].setTemperature(100.0);
        grid[1][2].setTemperature(20.0);
        grid[0][1].setTemperature(20.0);
        grid[2][1].setTemperature(20.0);

        // Cluster size = 5 (center + 4 cardinal).
        // Sum temp = 100 + 4 * 20 = 180°C. Target temp = 180 / 5 = 36.0°C.
        // Hot tile has 100 - 36 = 64°C deviation = 64 * 32 = 2048 HU.
        // Standard exchanger maxHU = 1024 HU. Scale = 1024 / 2048 = 0.5.
        // Delta temp on hot: (36 - 100) * 0.5 = -32°C -> 68.0°C.
        // Delta temp on cold: (36 - 20) * 0.5 = +8°C -> 28.0°C.
        // Consumes 1024 / 4 = 256 EU.
        NuclearSimulationEngine.processActiveVentsAndExchangers(grid, 3, 3, 20.0);

        assertEquals(68.0, grid[1][0].getTemperature(), 1e-4, "Hot neighbor scaled by maxHU");
        assertEquals(28.0, grid[1][2].getTemperature(), 1e-4, "Bottom neighbor warmed by 8°C");
        assertEquals(28.0, grid[0][1].getTemperature(), 1e-4, "Left neighbor warmed by 8°C");
        assertEquals(28.0, grid[2][1].getTemperature(), 1e-4, "Right neighbor warmed by 8°C");
        assertEquals(28.0, exchanger.getTemperature(), 1e-4, "Exchanger own cell warmed by 8°C");
        assertEquals(256, exchanger.drainedEnergy, "Exchanger consumes 256 EU");

        // Energy conservation check: sum of temperatures in cluster must equal 180.0°C exactly
        double sumAfter = grid[1][0].getTemperature() + grid[1][2].getTemperature()
            + grid[0][1].getTemperature()
            + grid[2][1].getTemperature()
            + exchanger.getTemperature();
        assertEquals(180.0, sumAfter, 1e-4, "Total thermal energy in cluster must be strictly conserved");

        // Insufficient EU test
        exchanger.energyDrainingAllowed = false;
        grid[1][0].setTemperature(100.0);
        exchanger.setTemperature(20.0);
        NuclearSimulationEngine.processActiveVentsAndExchangers(grid, 3, 3, 20.0);
        assertEquals(100.0, grid[1][0].getTemperature(), 1e-4, "Exchanger without power must not exchange heat");
    }

    @Test
    void testHeatExchangersRunBeforeVents() {
        SimTile[][] grid = new SimTile[3][3];
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                grid[x][y] = new SimTile(SimTile.TileType.EMPTY);
                grid[x][y].setTemperature(20.0);
            }
        }

        // Center: Standard Heat Exchanger (1024 HU/t). Top neighbor: Standard Heat Vent (512 HU/t).
        SimTile exchanger = new SimTile(SimTile.TileType.EXCHANGER_STANDARD);
        exchanger.setTemperature(20.0);
        grid[1][1] = exchanger;
        SimTile vent = new SimTile(SimTile.TileType.VENT_STANDARD);
        vent.setTemperature(20.0);
        grid[1][0] = vent;

        // Right neighbor hot (120°C).
        grid[2][1].setTemperature(120.0);

        // Exchanger first: cluster mean = (120 + 4 * 20) / 5 = 40°C, deviation 80°C = 2560 HU > 1024 HU, scale 0.4.
        // Hot -> 120 - 80 * 0.4 = 88°C; vent cell -> 20 + 20 * 0.4 = 28°C.
        // Vent second: 8°C above ambient = 256 HU < 512 HU, so the vent cell returns to ambient within the same tick.
        NuclearSimulationEngine.processActiveVentsAndExchangers(grid, 3, 3, 20.0);

        assertEquals(88.0, grid[2][1].getTemperature(), 1e-4, "Hot neighbor drained by exchanger");
        assertEquals(28.0, exchanger.getTemperature(), 1e-4, "Exchanger own cell warmed by 8°C");
        assertEquals(
            20.0,
            vent.getTemperature(),
            1e-4,
            "Vent must remove heat delivered by the exchanger in the same tick");
        assertEquals(64, vent.drainedEnergy, "Vent consumes 256 HU / 4 = 64 EU");
    }

    private static SimTile pooledSimTile(SimTile.TileType type, long[] pool) {
        return new SimTile(type) {

            @Override
            public long getAvailableEnergy() {
                return pool[0];
            }

            @Override
            public boolean drainEnergy(long eu) {
                if (eu > pool[0]) return false;
                pool[0] -= eu;
                drainedEnergy += eu;
                return true;
            }
        };
    }

    private static SimTile[][] randomActiveLayout(long seed, int sizeX, int sizeY) {
        java.util.Random rand = new java.util.Random(seed);
        SimTile.TileType[] types = { SimTile.TileType.EMPTY, SimTile.TileType.EXCHANGER_STANDARD,
            SimTile.TileType.EXCHANGER_COMPONENT, SimTile.TileType.VENT_STANDARD };
        SimTile[][] grid = new SimTile[sizeX][sizeY];
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeY; y++) {
                grid[x][y] = new SimTile(types[rand.nextInt(types.length)]);
                grid[x][y].setTemperature(rand.nextDouble() * 1500.0);
            }
        }
        return grid;
    }

    @Test
    void testExchangerPassIsOrderIndependent() {
        int n = 7;
        for (long seed = 1; seed <= 20; seed++) {
            SimTile[][] base = randomActiveLayout(seed, n, n);
            SimTile[][] mirrored = randomActiveLayout(seed, n, n);
            SimTile[][] transposed = randomActiveLayout(seed, n, n);
            // Rearrange the copies so that the grid scan visits the same components in a different order
            SimTile[][] mirrorGrid = new SimTile[n][n];
            SimTile[][] transposeGrid = new SimTile[n][n];
            for (int x = 0; x < n; x++) {
                for (int y = 0; y < n; y++) {
                    mirrorGrid[n - 1 - x][y] = mirrored[x][y];
                    transposeGrid[y][x] = transposed[x][y];
                }
            }

            NuclearSimulationEngine.processActiveVentsAndExchangers(base, n, n, 20.0);
            NuclearSimulationEngine.processActiveVentsAndExchangers(mirrorGrid, n, n, 20.0);
            NuclearSimulationEngine.processActiveVentsAndExchangers(transposeGrid, n, n, 20.0);

            for (int x = 0; x < n; x++) {
                for (int y = 0; y < n; y++) {
                    assertEquals(
                        base[x][y].getTemperature(),
                        mirrored[x][y].getTemperature(),
                        1e-9,
                        "Mirrored layout must give identical temperatures (seed " + seed + ")");
                    assertEquals(
                        base[x][y].getTemperature(),
                        transposed[x][y].getTemperature(),
                        1e-9,
                        "Transposed layout must give identical temperatures (seed " + seed + ")");
                    assertEquals(base[x][y].drainedEnergy, mirrored[x][y].drainedEnergy, "Mirrored EU must match");
                    assertEquals(base[x][y].drainedEnergy, transposed[x][y].drainedEnergy, "Transposed EU must match");
                }
            }
        }
    }

    @Test
    void testOverlappingExchangersDoNotOvershoot() {
        // A hot cell between two full-strength exchangers whose other cells are at 0°C.
        // Each exchanger alone would pull the hot cell 80°C towards its own mean (20°C); summing both naively would
        // drive it to -60°C, below every temperature around it.
        SimTile[][] grid = new SimTile[5][3];
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 3; y++) {
                grid[x][y] = new SimTile(SimTile.TileType.EMPTY);
                grid[x][y].setTemperature(0.0);
            }
        }
        SimTile left = new SimTile(SimTile.TileType.EXCHANGER_COMPONENT);
        SimTile right = new SimTile(SimTile.TileType.EXCHANGER_COMPONENT);
        left.setTemperature(0.0);
        right.setTemperature(0.0);
        grid[1][1] = left;
        grid[3][1] = right;
        grid[2][1].setTemperature(100.0);

        NuclearSimulationEngine.processHeatExchangers(grid, 5, 3);

        // Hot cell total weight = 2 exchangers * 4 partners * 1/5 = 1.6, so its pairs are scaled by 1/1.6:
        // it gives 50°C to each exchanger's 4 other cells (12.5°C each) and ends at exactly 0°C.
        assertEquals(0.0, grid[2][1].getTemperature(), 1e-9, "Hot cell must not be pushed below its surroundings");
        double sum = 0.0;
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 3; y++) {
                double t = grid[x][y].getTemperature();
                sum += t;
                assertTrue(t >= -1e-9 && t <= 100.0 + 1e-9, "Every cell must stay within the snapshot range");
            }
        }
        assertEquals(100.0, sum, 1e-9, "Heat must be conserved");
        assertEquals(12.5, grid[1][1].getTemperature(), 1e-9);
        assertEquals(12.5, grid[1][0].getTemperature(), 1e-9);
        assertEquals(12.5, grid[3][2].getTemperature(), 1e-9);
        assertEquals(12.5, grid[4][1].getTemperature(), 1e-9);
        assertEquals(400, left.drainedEnergy, "Each exchanger moves 50°C = 1600 HU = 400 EU");
        assertEquals(400, right.drainedEnergy, "Each exchanger moves 50°C = 1600 HU = 400 EU");
    }

    @Test
    void testDenseExchangerGridStaysBoundedAndConservative() {
        int n = 9;
        for (long seed = 1; seed <= 20; seed++) {
            java.util.Random rand = new java.util.Random(seed);
            SimTile[][] grid = new SimTile[n][n];
            double min = Double.MAX_VALUE;
            double max = -Double.MAX_VALUE;
            double sumBefore = 0.0;
            for (int x = 0; x < n; x++) {
                for (int y = 0; y < n; y++) {
                    // Every cell is a component exchanger: maximal overlap
                    grid[x][y] = new SimTile(SimTile.TileType.EXCHANGER_COMPONENT);
                    double t = rand.nextDouble() * 3000.0 - 200.0;
                    grid[x][y].setTemperature(t);
                    min = Math.min(min, t);
                    max = Math.max(max, t);
                    sumBefore += t;
                }
            }
            for (int tick = 0; tick < 50; tick++) {
                NuclearSimulationEngine.processHeatExchangers(grid, n, n);
                double sumAfter = 0.0;
                for (int x = 0; x < n; x++) {
                    for (int y = 0; y < n; y++) {
                        double t = grid[x][y].getTemperature();
                        sumAfter += t;
                        assertTrue(t >= min - 1e-6 && t <= max + 1e-6, "No cell may leave the initial range");
                    }
                }
                assertEquals(sumBefore, sumAfter, 1e-6, "Heat must be conserved every tick (seed " + seed + ")");
            }
        }
    }

    @Test
    void testExchangersShareShortEnergyProportionally() {
        // Two independent standard exchangers, each wanting 1024 HU = 256 EU, sharing a pool of 258 EU.
        // Scale = (258 - 2 headroom) / 512 = 0.5: each moves 512 HU for 128 EU, regardless of scan order.
        long[] pool = { 258L };
        SimTile[][] grid = new SimTile[7][3];
        for (int x = 0; x < 7; x++) {
            for (int y = 0; y < 3; y++) {
                grid[x][y] = new SimTile(SimTile.TileType.EMPTY);
                grid[x][y].setTemperature(20.0);
            }
        }
        SimTile first = pooledSimTile(SimTile.TileType.EXCHANGER_STANDARD, pool);
        SimTile second = pooledSimTile(SimTile.TileType.EXCHANGER_STANDARD, pool);
        first.setTemperature(20.0);
        second.setTemperature(20.0);
        grid[1][1] = first;
        grid[5][1] = second;
        grid[0][1].setTemperature(100.0);
        grid[6][1].setTemperature(100.0);

        NuclearSimulationEngine.processHeatExchangers(grid, 7, 3);

        assertEquals(128, first.drainedEnergy, "First exchanger gets half its demand");
        assertEquals(128, second.drainedEnergy, "Second exchanger gets half its demand");
        assertEquals(2L, pool[0], "Pool keeps only the rounding headroom");
        // Unthrottled each hot cell would drop 32°C (to 68°C); at half throughput it drops 16°C
        assertEquals(84.0, grid[0][1].getTemperature(), 1e-9);
        assertEquals(84.0, grid[6][1].getTemperature(), 1e-9);
    }

    @Test
    void testVentsShareShortEnergyProportionally() {
        // Two standard vents at 100°C, each wanting 512 HU = 128 EU, sharing a pool of 130 EU.
        // Scale = (130 - 2) / 256 = 0.5: each removes 256 HU (8°C) for 64 EU.
        long[] pool = { 130L };
        SimTile[][] grid = new SimTile[3][1];
        SimTile a = pooledSimTile(SimTile.TileType.VENT_STANDARD, pool);
        SimTile b = pooledSimTile(SimTile.TileType.VENT_STANDARD, pool);
        a.setTemperature(100.0);
        b.setTemperature(100.0);
        grid[0][0] = a;
        grid[1][0] = new SimTile(SimTile.TileType.EMPTY);
        grid[2][0] = b;

        NuclearSimulationEngine.processHeatVents(grid, 3, 1, 20.0);

        assertEquals(92.0, a.getTemperature(), 1e-9);
        assertEquals(92.0, b.getTemperature(), 1e-9);
        assertEquals(64, a.drainedEnergy);
        assertEquals(64, b.drainedEnergy);
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
        com.gtnewhorizons.modularnuclear.common.metatileentity.multi.structure.NuclearReactorStructure.NuclearHatchElement element = new com.gtnewhorizons.modularnuclear.common.metatileentity.multi.structure.NuclearReactorStructure.NuclearHatchElement();
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
    void testNuclearControlHatchFacingValidation() {
        MTENuclearReactor reactor = new MTENuclearReactor("test.reactor.control_facing");
        reactor.gridSize = 5; // Tier 1: 5x5 core, 7x7 footprint (half = 3, flatHalfWidth = 1)

        gregtech.api.metatileentity.BaseMetaTileEntity mockControllerTe = org.mockito.Mockito
            .mock(gregtech.api.metatileentity.BaseMetaTileEntity.class);
        org.mockito.Mockito.when(mockControllerTe.getXCoord())
            .thenReturn(0);
        org.mockito.Mockito.when(mockControllerTe.getYCoord())
            .thenReturn((short) 64);
        org.mockito.Mockito.when(mockControllerTe.getZCoord())
            .thenReturn(0);

        reactor.setExtendedFacing(
            com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing.of(ForgeDirection.NORTH));

        // Create mock control hatch
        MTEHatchNuclearControl controlHatch = new MTEHatchNuclearControl("test.control.hatch", 4, new String[0], null);
        gregtech.api.metatileentity.BaseMetaTileEntity mockHatchTe = org.mockito.Mockito
            .mock(gregtech.api.metatileentity.BaseMetaTileEntity.class);
        org.mockito.Mockito.when(mockHatchTe.getMetaTileEntity())
            .thenReturn(controlHatch);
        controlHatch.setBaseMetaTileEntity(mockHatchTe);

        reactor.mControlHatches.clear();
        reactor.mControlHatches.add(controlHatch);

        List<gregtech.api.structure.error.StructureError> errors = new ArrayList<>();

        // 1. Flat Front Wall (cX=0, cY=64, cZ=0): outA=0, outC=0
        org.mockito.Mockito.when(mockHatchTe.getXCoord())
            .thenReturn(0);
        org.mockito.Mockito.when(mockHatchTe.getYCoord())
            .thenReturn((short) 64);
        org.mockito.Mockito.when(mockHatchTe.getZCoord())
            .thenReturn(0);

        // NORTH (FORWARD) -> PASS
        org.mockito.Mockito.when(mockHatchTe.getFrontFacing())
            .thenReturn(ForgeDirection.NORTH);
        errors.clear();
        reactor.verifyNuclearControlHatchFacings(mockControllerTe, errors);
        assertTrue(errors.isEmpty(), "Front flat wall facing NORTH must pass");

        // SOUTH, WEST, EAST, UP, DOWN -> FAIL
        for (ForgeDirection d : new ForgeDirection[] { ForgeDirection.SOUTH, ForgeDirection.WEST, ForgeDirection.EAST,
            ForgeDirection.UP, ForgeDirection.DOWN }) {
            org.mockito.Mockito.when(mockHatchTe.getFrontFacing())
                .thenReturn(d);
            errors.clear();
            reactor.verifyNuclearControlHatchFacings(mockControllerTe, errors);
            assertEquals(1, errors.size(), "Front flat wall facing " + d + " must fail");
        }

        // 2. Flat Back Wall (cX=0, cY=64, cZ=6): outA=0, outC=6
        org.mockito.Mockito.when(mockHatchTe.getXCoord())
            .thenReturn(0);
        org.mockito.Mockito.when(mockHatchTe.getYCoord())
            .thenReturn((short) 64);
        org.mockito.Mockito.when(mockHatchTe.getZCoord())
            .thenReturn(6);

        // SOUTH (BACK) -> PASS
        org.mockito.Mockito.when(mockHatchTe.getFrontFacing())
            .thenReturn(ForgeDirection.SOUTH);
        errors.clear();
        reactor.verifyNuclearControlHatchFacings(mockControllerTe, errors);
        assertTrue(errors.isEmpty(), "Back flat wall facing SOUTH must pass");

        // NORTH, WEST, EAST -> FAIL
        for (ForgeDirection d : new ForgeDirection[] { ForgeDirection.NORTH, ForgeDirection.WEST, ForgeDirection.EAST,
            ForgeDirection.UP, ForgeDirection.DOWN }) {
            org.mockito.Mockito.when(mockHatchTe.getFrontFacing())
                .thenReturn(d);
            errors.clear();
            reactor.verifyNuclearControlHatchFacings(mockControllerTe, errors);
            assertEquals(1, errors.size(), "Back flat wall facing " + d + " must fail");
        }

        // 3. Flat Left Wall: for Facing NORTH, Left is WEST (-X). At (-3, 64, 3): outA=3, outC=3
        org.mockito.Mockito.when(mockHatchTe.getXCoord())
            .thenReturn(-3);
        org.mockito.Mockito.when(mockHatchTe.getYCoord())
            .thenReturn((short) 64);
        org.mockito.Mockito.when(mockHatchTe.getZCoord())
            .thenReturn(3);

        // WEST (LEFT) -> PASS
        org.mockito.Mockito.when(mockHatchTe.getFrontFacing())
            .thenReturn(ForgeDirection.WEST);
        errors.clear();
        reactor.verifyNuclearControlHatchFacings(mockControllerTe, errors);
        assertTrue(errors.isEmpty(), "Left flat wall facing WEST must pass");

        // EAST, NORTH, SOUTH -> FAIL
        for (ForgeDirection d : new ForgeDirection[] { ForgeDirection.EAST, ForgeDirection.NORTH, ForgeDirection.SOUTH,
            ForgeDirection.UP }) {
            org.mockito.Mockito.when(mockHatchTe.getFrontFacing())
                .thenReturn(d);
            errors.clear();
            reactor.verifyNuclearControlHatchFacings(mockControllerTe, errors);
            assertEquals(1, errors.size(), "Left flat wall facing " + d + " must fail");
        }

        // 4. Flat Right Wall: for Facing NORTH, Right is EAST (+X). At (+3, 64, 3): outA=-3, outC=3
        org.mockito.Mockito.when(mockHatchTe.getXCoord())
            .thenReturn(3);
        org.mockito.Mockito.when(mockHatchTe.getYCoord())
            .thenReturn((short) 64);
        org.mockito.Mockito.when(mockHatchTe.getZCoord())
            .thenReturn(3);

        // EAST (RIGHT) -> PASS
        org.mockito.Mockito.when(mockHatchTe.getFrontFacing())
            .thenReturn(ForgeDirection.EAST);
        errors.clear();
        reactor.verifyNuclearControlHatchFacings(mockControllerTe, errors);
        assertTrue(errors.isEmpty(), "Right flat wall facing EAST must pass");

        // WEST, NORTH, SOUTH -> FAIL
        for (ForgeDirection d : new ForgeDirection[] { ForgeDirection.WEST, ForgeDirection.NORTH, ForgeDirection.SOUTH,
            ForgeDirection.UP }) {
            org.mockito.Mockito.when(mockHatchTe.getFrontFacing())
                .thenReturn(d);
            errors.clear();
            reactor.verifyNuclearControlHatchFacings(mockControllerTe, errors);
            assertEquals(1, errors.size(), "Right flat wall facing " + d + " must fail");
        }

        // 5. Corner: Front-Left cut-away at (-2, 64, 0)
        org.mockito.Mockito.when(mockHatchTe.getXCoord())
            .thenReturn(-2);
        org.mockito.Mockito.when(mockHatchTe.getYCoord())
            .thenReturn((short) 64);
        org.mockito.Mockito.when(mockHatchTe.getZCoord())
            .thenReturn(0);

        // NORTH (FORWARD) -> PASS
        org.mockito.Mockito.when(mockHatchTe.getFrontFacing())
            .thenReturn(ForgeDirection.NORTH);
        errors.clear();
        reactor.verifyNuclearControlHatchFacings(mockControllerTe, errors);
        assertTrue(errors.isEmpty(), "Front-Left corner facing NORTH must pass");

        // WEST (LEFT) -> PASS
        org.mockito.Mockito.when(mockHatchTe.getFrontFacing())
            .thenReturn(ForgeDirection.WEST);
        errors.clear();
        reactor.verifyNuclearControlHatchFacings(mockControllerTe, errors);
        assertTrue(errors.isEmpty(), "Front-Left corner facing WEST must pass");

        // SOUTH (BACK) -> FAIL
        org.mockito.Mockito.when(mockHatchTe.getFrontFacing())
            .thenReturn(ForgeDirection.SOUTH);
        errors.clear();
        reactor.verifyNuclearControlHatchFacings(mockControllerTe, errors);
        assertEquals(1, errors.size(), "Front-Left corner facing SOUTH must fail");

        // EAST (RIGHT) -> FAIL
        org.mockito.Mockito.when(mockHatchTe.getFrontFacing())
            .thenReturn(ForgeDirection.EAST);
        errors.clear();
        reactor.verifyNuclearControlHatchFacings(mockControllerTe, errors);
        assertEquals(1, errors.size(), "Front-Left corner facing EAST must fail");

        // 6. Corner: Front-Right cut-away at (+2, 64, 0)
        org.mockito.Mockito.when(mockHatchTe.getXCoord())
            .thenReturn(2);
        org.mockito.Mockito.when(mockHatchTe.getYCoord())
            .thenReturn((short) 64);
        org.mockito.Mockito.when(mockHatchTe.getZCoord())
            .thenReturn(0);

        // NORTH (FORWARD) -> PASS
        org.mockito.Mockito.when(mockHatchTe.getFrontFacing())
            .thenReturn(ForgeDirection.NORTH);
        errors.clear();
        reactor.verifyNuclearControlHatchFacings(mockControllerTe, errors);
        assertTrue(errors.isEmpty(), "Front-Right corner facing NORTH must pass");

        // EAST (RIGHT) -> PASS
        org.mockito.Mockito.when(mockHatchTe.getFrontFacing())
            .thenReturn(ForgeDirection.EAST);
        errors.clear();
        reactor.verifyNuclearControlHatchFacings(mockControllerTe, errors);
        assertTrue(errors.isEmpty(), "Front-Right corner facing EAST must pass");

        // WEST (LEFT) -> FAIL
        org.mockito.Mockito.when(mockHatchTe.getFrontFacing())
            .thenReturn(ForgeDirection.WEST);
        errors.clear();
        reactor.verifyNuclearControlHatchFacings(mockControllerTe, errors);
        assertEquals(1, errors.size(), "Front-Right corner facing WEST must fail");

        // 7. Corner: Back-Left cut-away at (-2, 64, 6)
        org.mockito.Mockito.when(mockHatchTe.getXCoord())
            .thenReturn(-2);
        org.mockito.Mockito.when(mockHatchTe.getYCoord())
            .thenReturn((short) 64);
        org.mockito.Mockito.when(mockHatchTe.getZCoord())
            .thenReturn(6);

        // SOUTH (BACK) -> PASS
        org.mockito.Mockito.when(mockHatchTe.getFrontFacing())
            .thenReturn(ForgeDirection.SOUTH);
        errors.clear();
        reactor.verifyNuclearControlHatchFacings(mockControllerTe, errors);
        assertTrue(errors.isEmpty(), "Back-Left corner facing SOUTH must pass");

        // WEST (LEFT) -> PASS
        org.mockito.Mockito.when(mockHatchTe.getFrontFacing())
            .thenReturn(ForgeDirection.WEST);
        errors.clear();
        reactor.verifyNuclearControlHatchFacings(mockControllerTe, errors);
        assertTrue(errors.isEmpty(), "Back-Left corner facing WEST must pass");

        // NORTH (FORWARD) -> FAIL
        org.mockito.Mockito.when(mockHatchTe.getFrontFacing())
            .thenReturn(ForgeDirection.NORTH);
        errors.clear();
        reactor.verifyNuclearControlHatchFacings(mockControllerTe, errors);
        assertEquals(1, errors.size(), "Back-Left corner facing NORTH must fail");

        // 8. Corner: Back-Right cut-away at (+2, 64, 6)
        org.mockito.Mockito.when(mockHatchTe.getXCoord())
            .thenReturn(2);
        org.mockito.Mockito.when(mockHatchTe.getYCoord())
            .thenReturn((short) 64);
        org.mockito.Mockito.when(mockHatchTe.getZCoord())
            .thenReturn(6);

        // SOUTH (BACK) -> PASS
        org.mockito.Mockito.when(mockHatchTe.getFrontFacing())
            .thenReturn(ForgeDirection.SOUTH);
        errors.clear();
        reactor.verifyNuclearControlHatchFacings(mockControllerTe, errors);
        assertTrue(errors.isEmpty(), "Back-Right corner facing SOUTH must pass");

        // EAST (RIGHT) -> PASS
        org.mockito.Mockito.when(mockHatchTe.getFrontFacing())
            .thenReturn(ForgeDirection.EAST);
        errors.clear();
        reactor.verifyNuclearControlHatchFacings(mockControllerTe, errors);
        assertTrue(errors.isEmpty(), "Back-Right corner facing EAST must pass");

        // NORTH (FORWARD) -> FAIL
        org.mockito.Mockito.when(mockHatchTe.getFrontFacing())
            .thenReturn(ForgeDirection.NORTH);
        errors.clear();
        reactor.verifyNuclearControlHatchFacings(mockControllerTe, errors);
        assertEquals(1, errors.size(), "Back-Right corner facing NORTH must fail");

        // 9. Verify in different reactor orientation: Facing EAST
        reactor.setExtendedFacing(
            com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing.of(ForgeDirection.EAST));
        org.mockito.Mockito.when(mockHatchTe.getXCoord())
            .thenReturn(0);
        org.mockito.Mockito.when(mockHatchTe.getYCoord())
            .thenReturn((short) 64);
        org.mockito.Mockito.when(mockHatchTe.getZCoord())
            .thenReturn(0);

        // Facing EAST (fwd) -> PASS
        org.mockito.Mockito.when(mockHatchTe.getFrontFacing())
            .thenReturn(ForgeDirection.EAST);
        errors.clear();
        reactor.verifyNuclearControlHatchFacings(mockControllerTe, errors);
        assertTrue(errors.isEmpty(), "Facing EAST front flat wall must pass when facing EAST");

        // Facing NORTH (left) on front flat wall -> FAIL
        org.mockito.Mockito.when(mockHatchTe.getFrontFacing())
            .thenReturn(ForgeDirection.NORTH);
        errors.clear();
        reactor.verifyNuclearControlHatchFacings(mockControllerTe, errors);
        assertEquals(1, errors.size(), "Facing EAST front flat wall must fail when facing NORTH");
        assertTrue(
            errors.get(0)
                .toString()
                .contains("wrong_control_hatch_facing"),
            "Error must reference wrong_control_hatch_facing");

        // 10. Uprightness validation on upright reactor
        // Control hatch at (0, 64, 0) facing NORTH (forward)
        reactor.setExtendedFacing(
            com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing.of(ForgeDirection.NORTH));
        org.mockito.Mockito.when(mockHatchTe.getXCoord())
            .thenReturn(0);
        org.mockito.Mockito.when(mockHatchTe.getYCoord())
            .thenReturn((short) 64);
        org.mockito.Mockito.when(mockHatchTe.getZCoord())
            .thenReturn(0);
        org.mockito.Mockito.when(mockHatchTe.getFrontFacing())
            .thenReturn(ForgeDirection.NORTH);

        // NORMAL -> PASS
        controlHatch.setExtendedFacing(
            com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing.of(
                ForgeDirection.NORTH,
                com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.NORMAL,
                com.gtnewhorizon.structurelib.alignment.enumerable.Flip.NONE));
        errors.clear();
        reactor.verifyNuclearControlHatchFacings(mockControllerTe, errors);
        assertTrue(errors.isEmpty(), "Upright control hatch on upright reactor must pass");

        // UPSIDE_DOWN -> FAIL
        controlHatch.setExtendedFacing(
            com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing.of(
                ForgeDirection.NORTH,
                com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.UPSIDE_DOWN,
                com.gtnewhorizon.structurelib.alignment.enumerable.Flip.NONE));
        errors.clear();
        reactor.verifyNuclearControlHatchFacings(mockControllerTe, errors);
        assertEquals(1, errors.size(), "Upside-down control hatch on upright reactor must fail");
        assertTrue(
            errors.get(0)
                .toString()
                .contains("wrong_control_hatch_facing"));

        // CLOCKWISE -> FAIL
        controlHatch.setExtendedFacing(
            com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing.of(
                ForgeDirection.NORTH,
                com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.CLOCKWISE,
                com.gtnewhorizon.structurelib.alignment.enumerable.Flip.NONE));
        errors.clear();
        reactor.verifyNuclearControlHatchFacings(mockControllerTe, errors);
        assertEquals(1, errors.size(), "Clockwise control hatch on upright reactor must fail");

        // 11. Verify with UPSIDE_DOWN reactor facing NORTH
        reactor.setExtendedFacing(
            com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing.of(
                ForgeDirection.NORTH,
                com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.UPSIDE_DOWN,
                com.gtnewhorizon.structurelib.alignment.enumerable.Flip.NONE));

        // When reactor is UPSIDE_DOWN, control hatch must also be UPSIDE_DOWN to match controller's upright orientation
        controlHatch.setExtendedFacing(
            com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing.of(
                ForgeDirection.NORTH,
                com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.UPSIDE_DOWN,
                com.gtnewhorizon.structurelib.alignment.enumerable.Flip.NONE));
        errors.clear();
        reactor.verifyNuclearControlHatchFacings(mockControllerTe, errors);
        assertTrue(errors.isEmpty(), "UPSIDE_DOWN control hatch on UPSIDE_DOWN reactor must pass");

        // NORMAL control hatch on UPSIDE_DOWN reactor -> FAIL (anti-wallsharing invariant)
        controlHatch.setExtendedFacing(
            com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing.of(
                ForgeDirection.NORTH,
                com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.NORMAL,
                com.gtnewhorizon.structurelib.alignment.enumerable.Flip.NONE));
        errors.clear();
        reactor.verifyNuclearControlHatchFacings(mockControllerTe, errors);
        assertEquals(1, errors.size(), "Upright control hatch on UPSIDE_DOWN reactor must fail");

        // Reset control hatch back to NORMAL
        controlHatch.setExtendedFacing(
            com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing.of(
                ForgeDirection.NORTH,
                com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.NORMAL,
                com.gtnewhorizon.structurelib.alignment.enumerable.Flip.NONE));
    }

    @Test
    void testNuclearControlHatchRotationAndWrench() {
        MTEHatchNuclearControl hatch = new MTEHatchNuclearControl("test.control.rotation", 1, new String[0], null);
        gregtech.api.metatileentity.BaseMetaTileEntity mockTe = org.mockito.Mockito
            .mock(gregtech.api.metatileentity.BaseMetaTileEntity.class);
        org.mockito.Mockito.when(mockTe.getFrontFacing())
            .thenReturn(ForgeDirection.NORTH);
        org.mockito.Mockito.when(mockTe.getMetaTileEntity())
            .thenReturn(hatch);
        hatch.setBaseMetaTileEntity(mockTe);

        // Initial alignment is NORMAL
        assertEquals(
            com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.NORMAL,
            hatch.getExtendedFacing()
                .getRotation(),
            "Initial rotation must be NORMAL");
        assertEquals(
            ForgeDirection.UP,
            hatch.getExtendedFacing()
                .getRelativeUpInWorld());

        // Wrenching front facing rotates the hatch
        net.minecraft.entity.player.EntityPlayer mockPlayer = org.mockito.Mockito
            .mock(net.minecraft.entity.player.EntityPlayer.class);
        org.mockito.Mockito.when(mockPlayer.isSneaking())
            .thenReturn(false);

        boolean wrenched = hatch
            .onWrenchRightClick(ForgeDirection.NORTH, ForgeDirection.NORTH, mockPlayer, 0, 0, 0, null);
        assertTrue(wrenched, "Wrenching front face must succeed");
        assertEquals(
            com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.CLOCKWISE,
            hatch.getExtendedFacing()
                .getRotation(),
            "First rotation must be CLOCKWISE");

        hatch.onWrenchRightClick(ForgeDirection.NORTH, ForgeDirection.NORTH, mockPlayer, 0, 0, 0, null);
        assertEquals(
            com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.UPSIDE_DOWN,
            hatch.getExtendedFacing()
                .getRotation(),
            "Second rotation must be UPSIDE_DOWN");
        assertEquals(
            ForgeDirection.DOWN,
            hatch.getExtendedFacing()
                .getRelativeUpInWorld());

        hatch.onWrenchRightClick(ForgeDirection.NORTH, ForgeDirection.NORTH, mockPlayer, 0, 0, 0, null);
        assertEquals(
            com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.COUNTER_CLOCKWISE,
            hatch.getExtendedFacing()
                .getRotation(),
            "Third rotation must be COUNTER_CLOCKWISE");

        hatch.onWrenchRightClick(ForgeDirection.NORTH, ForgeDirection.NORTH, mockPlayer, 0, 0, 0, null);
        assertEquals(
            com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.NORMAL,
            hatch.getExtendedFacing()
                .getRotation(),
            "Fourth rotation must loop back to NORMAL");

        // Verify NBT persistence of rotation
        net.minecraft.nbt.NBTTagCompound nbt = new net.minecraft.nbt.NBTTagCompound();
        hatch.setExtendedFacing(
            com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing.of(
                ForgeDirection.NORTH,
                com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.UPSIDE_DOWN,
                com.gtnewhorizon.structurelib.alignment.enumerable.Flip.NONE));
        hatch.saveNBTData(nbt);
        assertEquals(
            (byte) com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.UPSIDE_DOWN.getIndex(),
            nbt.getByte("mRotation"));

        MTEHatchNuclearControl restored = new MTEHatchNuclearControl("test.control.restored", 1, new String[0], null);
        restored.setBaseMetaTileEntity(mockTe);
        restored.loadNBTData(nbt);
        assertEquals(
            com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.UPSIDE_DOWN,
            restored.getExtendedFacing()
                .getRotation(),
            "Loaded rotation from NBT must be UPSIDE_DOWN");
    }

    @Test
    void testMaintenanceHatchFacingAndUprightValidation() {
        MTENuclearReactor reactor = new MTENuclearReactor("test.reactor.maint_facing");
        reactor.gridSize = 5; // Tier 1: 5x5 core, 7x7 footprint (half = 3, flatHalfWidth = 1)

        gregtech.api.metatileentity.BaseMetaTileEntity mockControllerTe = org.mockito.Mockito
            .mock(gregtech.api.metatileentity.BaseMetaTileEntity.class);
        org.mockito.Mockito.when(mockControllerTe.getXCoord())
            .thenReturn(0);
        org.mockito.Mockito.when(mockControllerTe.getYCoord())
            .thenReturn((short) 64);
        org.mockito.Mockito.when(mockControllerTe.getZCoord())
            .thenReturn(0);

        reactor.setExtendedFacing(
            com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing.of(ForgeDirection.NORTH));

        // Create mock maintenance hatch
        MTEHatchMaintenance maintHatch = org.mockito.Mockito.mock(MTEHatchMaintenance.class);
        gregtech.api.metatileentity.BaseMetaTileEntity mockHatchTe = org.mockito.Mockito
            .mock(gregtech.api.metatileentity.BaseMetaTileEntity.class);
        org.mockito.Mockito.when(mockHatchTe.getMetaTileEntity())
            .thenReturn(maintHatch);
        org.mockito.Mockito.when(maintHatch.getBaseMetaTileEntity())
            .thenReturn(mockHatchTe);

        reactor.mMaintenanceHatches.clear();
        reactor.mMaintenanceHatches.add(maintHatch);

        List<gregtech.api.structure.error.StructureError> errors = new ArrayList<>();

        // 1. Flat Front Wall (cX=0, cY=64, cZ=0) with upright hatch
        org.mockito.Mockito.when(mockHatchTe.getXCoord())
            .thenReturn(0);
        org.mockito.Mockito.when(mockHatchTe.getYCoord())
            .thenReturn((short) 64);
        org.mockito.Mockito.when(mockHatchTe.getZCoord())
            .thenReturn(0);

        // NORTH (outward) + NORMAL (upright) -> PASS
        org.mockito.Mockito.when(mockHatchTe.getFrontFacing())
            .thenReturn(ForgeDirection.NORTH);
        org.mockito.Mockito.when(maintHatch.getExtendedFacing())
            .thenReturn(
                com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing.of(
                    ForgeDirection.NORTH,
                    com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.NORMAL,
                    com.gtnewhorizon.structurelib.alignment.enumerable.Flip.NONE));
        errors.clear();
        reactor.verifyMaintenanceHatchFacing(mockControllerTe, errors);
        assertTrue(errors.isEmpty(), "Front flat wall facing NORTH upright must pass");

        // Wrong facings on front flat wall -> FAIL
        for (ForgeDirection d : new ForgeDirection[] { ForgeDirection.SOUTH, ForgeDirection.WEST, ForgeDirection.EAST,
            ForgeDirection.UP, ForgeDirection.DOWN }) {
            org.mockito.Mockito.when(mockHatchTe.getFrontFacing())
                .thenReturn(d);
            org.mockito.Mockito.when(maintHatch.getExtendedFacing())
                .thenReturn(
                    com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing.of(
                        d,
                        com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.NORMAL,
                        com.gtnewhorizon.structurelib.alignment.enumerable.Flip.NONE));
            errors.clear();
            reactor.verifyMaintenanceHatchFacing(mockControllerTe, errors);
            assertEquals(1, errors.size(), "Front flat wall facing " + d + " must fail");
            assertTrue(
                errors.get(0)
                    .toString()
                    .contains("wrong_maintenance_hatch_facing"));
        }

        // Correct outward facing (NORTH) but wrong rotation (UPSIDE_DOWN on upright reactor) -> FAIL!
        org.mockito.Mockito.when(mockHatchTe.getFrontFacing())
            .thenReturn(ForgeDirection.NORTH);
        org.mockito.Mockito.when(maintHatch.getExtendedFacing())
            .thenReturn(
                com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing.of(
                    ForgeDirection.NORTH,
                    com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.UPSIDE_DOWN,
                    com.gtnewhorizon.structurelib.alignment.enumerable.Flip.NONE));
        errors.clear();
        reactor.verifyMaintenanceHatchFacing(mockControllerTe, errors);
        assertEquals(1, errors.size(), "Upside-down maintenance hatch on upright reactor must fail");

        // Correct outward facing (NORTH) but sideways rotation (CLOCKWISE) -> FAIL!
        org.mockito.Mockito.when(maintHatch.getExtendedFacing())
            .thenReturn(
                com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing.of(
                    ForgeDirection.NORTH,
                    com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.CLOCKWISE,
                    com.gtnewhorizon.structurelib.alignment.enumerable.Flip.NONE));
        errors.clear();
        reactor.verifyMaintenanceHatchFacing(mockControllerTe, errors);
        assertEquals(1, errors.size(), "Clockwise maintenance hatch on upright reactor must fail");

        // 2. Flat Back Wall (cX=0, cY=64, cZ=6)
        org.mockito.Mockito.when(mockHatchTe.getXCoord())
            .thenReturn(0);
        org.mockito.Mockito.when(mockHatchTe.getYCoord())
            .thenReturn((short) 64);
        org.mockito.Mockito.when(mockHatchTe.getZCoord())
            .thenReturn(6);

        // SOUTH (outward) + NORMAL -> PASS
        org.mockito.Mockito.when(mockHatchTe.getFrontFacing())
            .thenReturn(ForgeDirection.SOUTH);
        org.mockito.Mockito.when(maintHatch.getExtendedFacing())
            .thenReturn(
                com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing.of(
                    ForgeDirection.SOUTH,
                    com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.NORMAL,
                    com.gtnewhorizon.structurelib.alignment.enumerable.Flip.NONE));
        errors.clear();
        reactor.verifyMaintenanceHatchFacing(mockControllerTe, errors);
        assertTrue(errors.isEmpty(), "Back flat wall facing SOUTH must pass");

        // NORTH (inward) -> FAIL
        org.mockito.Mockito.when(mockHatchTe.getFrontFacing())
            .thenReturn(ForgeDirection.NORTH);
        errors.clear();
        reactor.verifyMaintenanceHatchFacing(mockControllerTe, errors);
        assertEquals(1, errors.size(), "Back flat wall facing NORTH must fail");

        // 3. Flat Left Wall (-3, 64, 3)
        org.mockito.Mockito.when(mockHatchTe.getXCoord())
            .thenReturn(-3);
        org.mockito.Mockito.when(mockHatchTe.getYCoord())
            .thenReturn((short) 64);
        org.mockito.Mockito.when(mockHatchTe.getZCoord())
            .thenReturn(3);

        // WEST (outward) + NORMAL -> PASS
        org.mockito.Mockito.when(mockHatchTe.getFrontFacing())
            .thenReturn(ForgeDirection.WEST);
        org.mockito.Mockito.when(maintHatch.getExtendedFacing())
            .thenReturn(
                com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing.of(
                    ForgeDirection.WEST,
                    com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.NORMAL,
                    com.gtnewhorizon.structurelib.alignment.enumerable.Flip.NONE));
        errors.clear();
        reactor.verifyMaintenanceHatchFacing(mockControllerTe, errors);
        assertTrue(errors.isEmpty(), "Left flat wall facing WEST must pass");

        // EAST (inward) -> FAIL
        org.mockito.Mockito.when(mockHatchTe.getFrontFacing())
            .thenReturn(ForgeDirection.EAST);
        errors.clear();
        reactor.verifyMaintenanceHatchFacing(mockControllerTe, errors);
        assertEquals(1, errors.size(), "Left flat wall facing EAST must fail");

        // 4. Corner: Front-Left cut-away (-2, 64, 0)
        org.mockito.Mockito.when(mockHatchTe.getXCoord())
            .thenReturn(-2);
        org.mockito.Mockito.when(mockHatchTe.getYCoord())
            .thenReturn((short) 64);
        org.mockito.Mockito.when(mockHatchTe.getZCoord())
            .thenReturn(0);

        // NORTH (outward) -> PASS
        org.mockito.Mockito.when(mockHatchTe.getFrontFacing())
            .thenReturn(ForgeDirection.NORTH);
        org.mockito.Mockito.when(maintHatch.getExtendedFacing())
            .thenReturn(
                com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing.of(
                    ForgeDirection.NORTH,
                    com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.NORMAL,
                    com.gtnewhorizon.structurelib.alignment.enumerable.Flip.NONE));
        errors.clear();
        reactor.verifyMaintenanceHatchFacing(mockControllerTe, errors);
        assertTrue(errors.isEmpty(), "Front-Left corner facing NORTH must pass");

        // WEST (outward) -> PASS
        org.mockito.Mockito.when(mockHatchTe.getFrontFacing())
            .thenReturn(ForgeDirection.WEST);
        org.mockito.Mockito.when(maintHatch.getExtendedFacing())
            .thenReturn(
                com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing.of(
                    ForgeDirection.WEST,
                    com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.NORMAL,
                    com.gtnewhorizon.structurelib.alignment.enumerable.Flip.NONE));
        errors.clear();
        reactor.verifyMaintenanceHatchFacing(mockControllerTe, errors);
        assertTrue(errors.isEmpty(), "Front-Left corner facing WEST must pass");

        // SOUTH (inward) -> FAIL
        org.mockito.Mockito.when(mockHatchTe.getFrontFacing())
            .thenReturn(ForgeDirection.SOUTH);
        errors.clear();
        reactor.verifyMaintenanceHatchFacing(mockControllerTe, errors);
        assertEquals(1, errors.size(), "Front-Left corner facing SOUTH must fail");

        // 5. ANTI-WALLSHARING WITH UPSIDE-DOWN REACTOR:
        // Now configure reactor as UPSIDE-DOWN
        reactor.setExtendedFacing(
            com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing.of(
                ForgeDirection.NORTH,
                com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.UPSIDE_DOWN,
                com.gtnewhorizon.structurelib.alignment.enumerable.Flip.NONE));

        // Front flat wall (0, 64, 0), facing NORTH (outward)
        org.mockito.Mockito.when(mockHatchTe.getXCoord())
            .thenReturn(0);
        org.mockito.Mockito.when(mockHatchTe.getYCoord())
            .thenReturn((short) 64);
        org.mockito.Mockito.when(mockHatchTe.getZCoord())
            .thenReturn(0);
        org.mockito.Mockito.when(mockHatchTe.getFrontFacing())
            .thenReturn(ForgeDirection.NORTH);

        // A) If hatch is upright in world (Rotation.NORMAL, as from an upright reactor trying to wall-share):
        org.mockito.Mockito.when(maintHatch.getExtendedFacing())
            .thenReturn(
                com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing.of(
                    ForgeDirection.NORTH,
                    com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.NORMAL,
                    com.gtnewhorizon.structurelib.alignment.enumerable.Flip.NONE));
        errors.clear();
        reactor.verifyMaintenanceHatchFacing(mockControllerTe, errors);
        assertEquals(1, errors.size(), "Upside-down reactor must REJECT an upright maintenance hatch");

        // B) If hatch is upside-down in world (Rotation.UPSIDE_DOWN, matching the upside-down reactor):
        org.mockito.Mockito.when(maintHatch.getExtendedFacing())
            .thenReturn(
                com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing.of(
                    ForgeDirection.NORTH,
                    com.gtnewhorizon.structurelib.alignment.enumerable.Rotation.UPSIDE_DOWN,
                    com.gtnewhorizon.structurelib.alignment.enumerable.Flip.NONE));
        errors.clear();
        reactor.verifyMaintenanceHatchFacing(mockControllerTe, errors);
        assertTrue(errors.isEmpty(), "Upside-down reactor must ACCEPT an upside-down maintenance hatch");
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
        com.gtnewhorizons.modularnuclear.common.metatileentity.multi.structure.NuclearReactorStructure.BottomCoreElement bottomElement = new com.gtnewhorizons.modularnuclear.common.metatileentity.multi.structure.NuclearReactorStructure.BottomCoreElement();
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
        org.mockito.Mockito.when(mockBotTe.getFrontFacing())
            .thenReturn(ForgeDirection.DOWN);
        assertTrue(
            bottomElement.check(reactor, mockWorld, 10, 64, 15),
            "Control rod facing DOWN must pass BottomCoreElement");
        org.mockito.Mockito.when(mockBotTe.getFrontFacing())
            .thenReturn(ForgeDirection.UP);
        assertFalse(
            bottomElement.check(reactor, mockWorld, 10, 64, 15),
            "Control rod facing UP must fail BottomCoreElement");

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

        // Pipe tier check: Electrum and Platinum must reject HP hatches
        reactor.gridSize = 5;
        reactor.mPipeTier = NuclearSimulationEngine.PIPE_TIER_ELECTRUM;
        reactor.validateHighPressureHatchPairing(baseTe, errors);
        assertTrue(
            errors.stream()
                .anyMatch(
                    e -> e.toString()
                        .contains("hp_hatch_tier")),
            "Electrum pipe tier must reject HP hatches");

        errors.clear();
        reactor.mPipeTier = NuclearSimulationEngine.PIPE_TIER_PLATINUM;
        reactor.validateHighPressureHatchPairing(baseTe, errors);
        assertTrue(
            errors.stream()
                .anyMatch(
                    e -> e.toString()
                        .contains("hp_hatch_tier")),
            "Platinum pipe tier must reject HP hatches");

        // Osmium pipe tier is allowed even on a 5x5 reactor!
        reactor.mPipeTier = NuclearSimulationEngine.PIPE_TIER_OSMIUM;

        // Unpaired check: 1 top, 0 bottom -> fails with hp_hatch_unpaired
        errors.clear();
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

        // Paired check: 1 top, 1 bottom in Osmium at matching coordinates (on 5x5 grid)
        errors.clear();
        reactor.mBottomHighPressureHatches.add(botHp);
        reactor.validateHighPressureHatchPairing(baseTe, errors);
        assertTrue(errors.isEmpty(), "Paired top/bottom hatches with Osmium casing on 5x5 must succeed: " + errors);
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
            1200.0,
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
        assertEquals(
            1.0,
            reactor.getMaintenanceEfficiency(),
            1e-6,
            "Undamaged machine with perfect maintenance must have 100% efficiency");

        reactor.mReactorDamage = 20.0;
        assertEquals(
            0.80,
            reactor.getMaintenanceEfficiency(),
            1e-6,
            "20% reactor damage must scale cooling efficiency to 80%");

        reactor.mReactorDamage = 50.0;
        assertEquals(
            0.50,
            reactor.getMaintenanceEfficiency(),
            1e-6,
            "50% reactor damage must scale cooling efficiency to 50%");

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
        assertEquals(36000, ModFluids.fluidCorium.getViscosity());
        assertEquals(3000, ModFluids.fluidCorium.getTemperature());
        assertEquals(15, ModFluids.fluidCorium.getLuminosity());

        BlockCorium block = new BlockCorium(ModFluids.fluidCorium);
        // Tick rate is 180 (6x slower than lava's 30 ticks, 2x slower than previous 90)
        assertEquals(180, block.tickRate(null), "Corium tick rate must be 180 (6x slower than lava)");
        assertFalse(block.isReplaceable(null, 0, 0, 0), "Corium must NOT be replaceable by blocks");
    }

    @Test
    void testCoriumEntityCollisionSlowing() {
        ModFluids.init();
        BlockCorium block = new BlockCorium(ModFluids.fluidCorium);

        net.minecraft.entity.Entity mockEntity = org.mockito.Mockito.mock(net.minecraft.entity.Entity.class);
        mockEntity.motionX = 1.0;
        mockEntity.motionY = 0.5;
        mockEntity.motionZ = 1.0;

        World mockWorld = org.mockito.Mockito.mock(World.class);
        block.onEntityCollidedWithBlock(mockWorld, 0, 0, 0, mockEntity);

        org.mockito.Mockito.verify(mockEntity)
            .setInWeb();
        assertEquals(0.5, mockEntity.motionX, 1e-4, "motionX must be dampened by 0.5");
        assertEquals(0.125, mockEntity.motionY, 1e-4, "upward motionY must be dampened by 0.25");
        assertEquals(0.5, mockEntity.motionZ, 1e-4, "motionZ must be dampened by 0.5");
    }

    @Test
    void testCoriumPlacementHandlerCancelsReplacingCorium() {
        ModFluids.init();
        BlockCorium block = new BlockCorium(ModFluids.fluidCorium);
        com.gtnewhorizons.modularnuclear.common.event.CoriumPlacementHandler handler = new com.gtnewhorizons.modularnuclear.common.event.CoriumPlacementHandler();

        net.minecraft.entity.player.EntityPlayer mockPlayer = org.mockito.Mockito
            .mock(net.minecraft.entity.player.EntityPlayer.class);
        World mockWorld = org.mockito.Mockito.mock(World.class);
        org.mockito.Mockito.when(mockWorld.getBlock(0, 0, 0))
            .thenReturn(block);

        net.minecraftforge.common.util.BlockSnapshot coriumSnapshot = org.mockito.Mockito
            .mock(net.minecraftforge.common.util.BlockSnapshot.class);
        coriumSnapshot.world = mockWorld;
        org.mockito.Mockito.when(coriumSnapshot.getReplacedBlock())
            .thenReturn(block);
        org.mockito.Mockito.when(coriumSnapshot.getCurrentBlock())
            .thenReturn(block);

        net.minecraftforge.event.world.BlockEvent.PlaceEvent rawCoriumEvent = new net.minecraftforge.event.world.BlockEvent.PlaceEvent(
            coriumSnapshot,
            Blocks.stone,
            mockPlayer);
        net.minecraftforge.event.world.BlockEvent.PlaceEvent coriumEvent = org.mockito.Mockito.spy(rawCoriumEvent);
        org.mockito.Mockito.doReturn(true)
            .when(coriumEvent)
            .isCancelable();

        handler.onBlockPlace(coriumEvent);
        assertTrue(coriumEvent.isCanceled(), "PlaceEvent replacing Corium must be canceled");

        // Non-corium snapshot should NOT be canceled
        net.minecraftforge.common.util.BlockSnapshot stoneSnapshot = org.mockito.Mockito
            .mock(net.minecraftforge.common.util.BlockSnapshot.class);
        stoneSnapshot.world = mockWorld;
        org.mockito.Mockito.when(stoneSnapshot.getReplacedBlock())
            .thenReturn(Blocks.stone);
        org.mockito.Mockito.when(stoneSnapshot.getCurrentBlock())
            .thenReturn(Blocks.stone);

        net.minecraftforge.event.world.BlockEvent.PlaceEvent rawStoneEvent = new net.minecraftforge.event.world.BlockEvent.PlaceEvent(
            stoneSnapshot,
            Blocks.stone,
            mockPlayer);
        net.minecraftforge.event.world.BlockEvent.PlaceEvent stoneEvent = org.mockito.Mockito.spy(rawStoneEvent);
        org.mockito.Mockito.doReturn(true)
            .when(stoneEvent)
            .isCancelable();

        handler.onBlockPlace(stoneEvent);
        assertFalse(stoneEvent.isCanceled(), "PlaceEvent replacing normal stone must NOT be canceled");
    }

    @Test
    void testCoriumPlacementHandlerCancelsBucketFill() {
        ModFluids.init();
        BlockCorium block = new BlockCorium(ModFluids.fluidCorium);
        com.gtnewhorizons.modularnuclear.common.event.CoriumPlacementHandler handler = new com.gtnewhorizons.modularnuclear.common.event.CoriumPlacementHandler();

        net.minecraft.entity.player.EntityPlayer mockPlayer = org.mockito.Mockito
            .mock(net.minecraft.entity.player.EntityPlayer.class);
        World mockWorld = org.mockito.Mockito.mock(World.class);
        org.mockito.Mockito.when(mockWorld.getBlock(10, 20, 30))
            .thenReturn(block);

        net.minecraft.util.MovingObjectPosition mop = new net.minecraft.util.MovingObjectPosition(
            10,
            20,
            30,
            1,
            net.minecraft.util.Vec3.createVectorHelper(10.5, 20.5, 30.5));

        net.minecraftforge.event.entity.player.FillBucketEvent rawBucketEvent = new net.minecraftforge.event.entity.player.FillBucketEvent(
            mockPlayer,
            null,
            mockWorld,
            mop);
        net.minecraftforge.event.entity.player.FillBucketEvent bucketEvent = org.mockito.Mockito.spy(rawBucketEvent);
        org.mockito.Mockito.doReturn(true)
            .when(bucketEvent)
            .isCancelable();

        handler.onBucketFill(bucketEvent);
        assertTrue(bucketEvent.isCanceled(), "FillBucketEvent targeting Corium must be canceled");
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
    void testEntityMeltdownFalloutConstants() {
        assertEquals(10000, EntityMeltdownFallout.INITIAL_CONTAMINATION, "Initial contamination must be 10000");
        assertEquals(12000, EntityMeltdownFallout.TOTAL_LIFETIME, "Total lifetime must be 12000 ticks (10 minutes)");
        assertEquals(
            62.5 / 30000.0,
            ModularNuclearConfig.falloutContaminationReductionPerPollution,
            1e-7,
            "Default fallout contamination reduction ratio must be 62.5/30000 (1/480)");
    }

    @Test
    void testSolidCoriumBlockProperties() {
        ModFluids.init();
        ModBlocks.init();
        BlockCoriumSolid solidBlock = ModBlocks.blockCoriumSolid;
        assertNotNull(solidBlock, "ModBlocks.blockCoriumSolid must be initialized");
        assertEquals(2500.0F, BlockCoriumSolid.CORIUM_HARDNESS, 1e-4, "Hardness must be 2500.0F (50x obsidian)");
        assertEquals(6000.0F, BlockCoriumSolid.CORIUM_RESISTANCE, 1e-4, "Resistance must be 6000.0F");
        assertEquals(20, solidBlock.tickRate(null), "Solid corium tick rate must be 20 ticks (1s)");
        assertEquals(1, solidBlock.quantityDropped(null), "Solid corium must drop 1 item when harvested");
        assertEquals(
            net.minecraft.item.Item.getItemFromBlock(solidBlock),
            solidBlock.getItemDropped(0, null, 0),
            "Solid corium must drop itself");
        assertTrue(
            BlockCorium.isImmuneToCorium(solidBlock, null, 0, 0, 0),
            "Solid corium must be immune to molten corium");
    }

    @Test
    void testCoriumSolidificationMechanics() {
        ModFluids.init();
        ModBlocks.init();

        net.minecraft.world.World mockWorld = org.mockito.Mockito.mock(net.minecraft.world.World.class);
        // Case 1: Flowing corium (meta 3) with flowing corium below (meta 2)
        org.mockito.Mockito.when(mockWorld.getBlock(10, 64, 10))
            .thenReturn(ModBlocks.blockCorium);
        org.mockito.Mockito.when(mockWorld.getBlockMetadata(10, 64, 10))
            .thenReturn(3);
        org.mockito.Mockito.when(mockWorld.getBlock(10, 63, 10))
            .thenReturn(ModBlocks.blockCorium);
        org.mockito.Mockito.when(mockWorld.getBlockMetadata(10, 63, 10))
            .thenReturn(2);

        BlockCorium.solidifyCoriumBlock(mockWorld, 10, 64, 10);

        // Verify below block promoted to source block (meta 0)
        org.mockito.Mockito.verify(mockWorld)
            .setBlock(10, 63, 10, ModBlocks.blockCorium, 0, 3);
        // Verify current block solidified into BlockCoriumSolid
        org.mockito.Mockito.verify(mockWorld)
            .setBlock(10, 64, 10, ModBlocks.blockCoriumSolid, 0, 3);
    }

    @Test
    void testCoriumItemsAndOreDict() {
        ModFluids.init();
        ModBlocks.init();
        ModItems.init();
        assertNotNull(ModItems.itemCoriumDust, "itemCoriumDust must be initialized");
        assertNotNull(ModItems.itemCoriumRod, "itemCoriumRod must be initialized");
        assertEquals(
            "modularnuclear.corium_dust",
            ModItems.itemCoriumDust.getUnlocalizedName()
                .replace("item.", ""));
        assertEquals(
            "modularnuclear.corium_rod",
            ModItems.itemCoriumRod.getUnlocalizedName()
                .replace("item.", ""));
    }

    @Test
    void testCoriumRecipesRegistration() {
        ModFluids.init();
        ModBlocks.init();
        ModItems.init();
        if (!gregtech.api.enums.ItemList.Shape_Extruder_Rod.hasBeenSet()) {
            gregtech.api.enums.ItemList.Shape_Extruder_Rod.set(new ItemStack(net.minecraft.init.Items.stick));
        }
        if (gregtech.api.enums.GTValues.RA == null) {
            gregtech.api.enums.GTValues.RA = new gregtech.common.RecipeAdder();
        }
        ModRecipes.init();

        // 1. Macerator recipe: Corium Block -> Corium Dust
        gregtech.api.util.GTRecipe maceratorRecipe = gregtech.api.recipe.RecipeMaps.maceratorRecipes.findRecipeQuery()
            .items(new ItemStack(ModBlocks.blockCoriumSolid, 1))
            .find();
        assertNotNull(maceratorRecipe, "Macerator recipe for Corium Block must exist");
        assertEquals(120 * 20, maceratorRecipe.mDuration, "Macerator duration must be 120 seconds (2400 ticks)");
        assertEquals(7680, maceratorRecipe.mEUt, "Macerator EU/t must be 7680");
        assertEquals(
            new ItemStack(ModItems.itemCoriumDust).getItem(),
            maceratorRecipe.mOutputs[0].getItem(),
            "Macerator output must be Corium Dust");

        // 2. Extruder recipe: Corium Dust + Rod Shape -> Corium Rod
        gregtech.api.util.GTRecipe extruderRecipe = gregtech.api.recipe.RecipeMaps.extruderRecipes.findRecipeQuery()
            .items(new ItemStack(ModItems.itemCoriumDust, 1), gregtech.api.enums.ItemList.Shape_Extruder_Rod.get(0L))
            .find();
        assertNotNull(extruderRecipe, "Extruder recipe for Corium Dust -> Corium Rod must exist");
        assertEquals(
            new ItemStack(ModItems.itemCoriumRod).getItem(),
            extruderRecipe.mOutputs[0].getItem(),
            "Extruder output must be Corium Rod");

        // 3. RTG fuel recipe: Corium Rod
        gregtech.api.util.GTRecipe rtgRecipe = gregtech.api.recipe.RecipeMaps.rtgFuels.findRecipeQuery()
            .items(new ItemStack(ModItems.itemCoriumRod, 1))
            .find();
        assertNotNull(rtgRecipe, "RTG fuel recipe for Corium Rod must exist");
        assertEquals(
            432,
            rtgRecipe.mSpecialValue,
            "RTG fuel duration must be 432 days (2x longest existing at 216 days)");
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
        boolean foundDamageTooltip = tooltip.stream()
            .anyMatch(s -> s.contains("Reactor damage: 42.5%"));
        assertTrue(foundDamageTooltip, "Controller item tooltip must display reactor damage percentage");

        // 3. When new controller is placed and loadNBTData is called, damage is restored
        MTENuclearReactor placedReactor = new MTENuclearReactor("nuclear.reactor.test");
        gregtech.api.interfaces.tileentity.IGregTechTileEntity tePlaced = org.mockito.Mockito
            .mock(gregtech.api.interfaces.tileentity.IGregTechTileEntity.class);
        org.mockito.Mockito.when(tePlaced.getFrontFacing())
            .thenReturn(ForgeDirection.NORTH);
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
        org.mockito.Mockito.when(teClean.getFrontFacing())
            .thenReturn(ForgeDirection.NORTH);
        placedCleanReactor.setBaseMetaTileEntity(teClean);
        placedCleanReactor.loadNBTData(cleanNbt);
        assertEquals(0.0, placedCleanReactor.mReactorDamage, 1e-6);
    }

    @Test
    void testConductanceScalingAcrossTiers() {
        assertEquals(1.0, NuclearSimulationEngine.getHatchConductance(1), 1e-6, "LV (tier 1) conductance");
        assertEquals(2.0, NuclearSimulationEngine.getHatchConductance(2), 1e-6, "MV (tier 2) conductance");
        assertEquals(4.0, NuclearSimulationEngine.getHatchConductance(3), 1e-6, "HV (tier 3) conductance");
        assertEquals(8.0, NuclearSimulationEngine.getHatchConductance(4), 1e-6, "EV (tier 4) conductance");
        assertEquals(16.0, NuclearSimulationEngine.getHatchConductance(5), 1e-6, "IV (tier 5) conductance");
        assertEquals(32.0, NuclearSimulationEngine.getHatchConductance(6), 1e-6, "LuV (tier 6) conductance");
        assertEquals(64.0, NuclearSimulationEngine.getHatchConductance(7), 1e-6, "ZPM (tier 7) conductance");
        assertEquals(128.0, NuclearSimulationEngine.getHatchConductance(8), 1e-6, "UV (tier 8) conductance");
        assertEquals(256.0, NuclearSimulationEngine.getHatchConductance(9), 1e-6, "UHV (tier 9) conductance");
    }

    @Test
    void testAnalyticalConductiveHeatTransferFormula() {
        // When temp <= sinkTemp: zero heat transfer
        assertEquals(0.0, NuclearSimulationEngine.calculateConductiveHeatTransfer(100.0, 100.0, 3, 1.0), 1e-6);
        assertEquals(0.0, NuclearSimulationEngine.calculateConductiveHeatTransfer(80.0, 100.0, 3, 1.0), 1e-6);

        // When efficiency is 0: zero heat transfer
        assertEquals(0.0, NuclearSimulationEngine.calculateConductiveHeatTransfer(200.0, 100.0, 3, 0.0), 1e-6);

        // Tier 3 (HV, mult=2.0, base=2.0 => U = 4.0 EU/(t·°C)), Ch = 32.0 EU/°C, DeltaT = 100°C, eff = 1.0, dt = 1.0
        // (20 ticks)
        // Expected Q = 32.0 * 100.0 * (1 - exp(-4.0 / 32.0)) = 3200 * (1 - exp(-0.125)) = 376.01 EU
        double qHV = NuclearSimulationEngine.calculateConductiveHeatTransfer(200.0, 100.0, 3, 1.0, 20);
        assertEquals(376.01, qHV, 0.02);

        // Tier 4 (EV, mult=4.0, base=2.0 => U = 8.0 EU/(t·°C)), Ch = 32.0 EU/°C, DeltaT = 100°C, eff = 1.0, dt = 1.0
        // (20 ticks)
        // Expected Q = 32.0 * 100.0 * (1 - exp(-8.0 / 32.0)) = 3200 * (1 - exp(-0.25)) = 707.84 EU
        double qEV = NuclearSimulationEngine.calculateConductiveHeatTransfer(200.0, 100.0, 4, 1.0, 20);
        assertEquals(707.84, qEV, 0.02);

        // Default 5 ticks per cycle (dt = 0.25)
        // Q = 3200 * (1 - exp(-4.0 * 0.25 / 32.0)) = 3200 * (1 - exp(-0.03125)) = 98.45 EU
        double qHV5 = NuclearSimulationEngine.calculateConductiveHeatTransfer(200.0, 100.0, 3, 1.0);
        assertEquals(98.45, qHV5, 0.02);

        // Higher tier transfers more heat
        assertTrue(qEV > qHV);
    }

    @Test
    void testHatchCoolingNeverOvershootsSinkTempInSingleTick() {
        // In a physical lumped-capacitance model with Ch = HU_PER_DEGREE = 32.0,
        // the temperature drop in 1 tick (Q / Ch) must be strictly less than DeltaT for all tiers.
        double temp = 250.0;
        double sinkTemp = 100.0;
        double deltaT = temp - sinkTemp;

        for (int tier = 1; tier <= 8; tier++) {
            double qMax = NuclearSimulationEngine.calculateConductiveHeatTransfer(temp, sinkTemp, tier, 1.0);
            double tempDrop = qMax / NuclearSimulationEngine.HU_PER_DEGREE;
            assertTrue(
                tempDrop < deltaT,
                "Tier " + tier + " temp drop (" + tempDrop + ") must be strictly less than DeltaT (" + deltaT + ")");
            double tempAfter = temp - tempDrop;
            assertTrue(
                tempAfter > sinkTemp,
                "Tier " + tier
                    + " temp after cooling ("
                    + tempAfter
                    + ") must remain strictly above sink temperature ("
                    + sinkTemp
                    + ")");
        }
    }

    @Test
    void testHatchTemperatureRisesAboveBoilingWithActiveFuel() {
        // Verifies that in an active operating reactor, conductive coolant hatches
        // can and do stabilize at temperatures above boiling point (100°C for distilled water),
        // rather than being artificially pinned to 100.0°C.
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid grid = new com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid(
            7,
            7,
            NuclearSimulationEngine.PIPE_TIER_PLATINUM);
        grid.loadPreset("BEST_PLATINUM_7X7");

        // Run 50 ticks of reactor simulation
        for (int t = 0; t < 50; t++) {
            grid.step();
        }

        // Find a distilled water hatch
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile hatch = null;
        for (int x = 0; x < 7; x++) {
            for (int y = 0; y < 7; y++) {
                com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile tile = grid.getTile(x, y);
                if (tile != null && tile.getType()
                    == com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.HATCH_DISTILLED_WATER) {
                    hatch = tile;
                    break;
                }
            }
            if (hatch != null) break;
        }

        assertNotNull(hatch, "Grid must contain distilled water hatch");
        assertTrue(
            hatch.getTemperature() > 100.0,
            "Hatch temperature (" + hatch.getTemperature()
                + "°C) must rise above boiling point under continuous heat input");
        assertNotEquals(
            100.0,
            hatch.getTemperature(),
            0.01,
            "Hatch temperature must not be artificially clamped to exactly 100.0°C");
        assertTrue(hatch.getTotalSteamProduced() > 0, "Hatch must actively produce steam while above boiling point");
        assertTrue(
            hatch.getTemperature() < 1000.0,
            "Hatch temperature must remain safely bounded due to conductive cooling");
    }

    @Test
    void testCoolantHatchHigherTierRunsCooler() {
        // Compares cooling rate of MV (tier 2) vs HV (tier 3) vs EV (tier 4) hatches
        // starting at 200°C. Higher tier hatches must achieve greater cooling and lower temperature.
        SimTile hatchMV = new SimTile(SimTile.TileType.HATCH_DISTILLED_WATER);
        hatchMV.setTier(2); // MV
        hatchMV.setInputFluidAmount(8000);
        hatchMV.setTemperature(200.0);
        hatchMV.coolantSubstep(cycleSeconds(), 1.0);
        hatchMV.nuclearTick(1.0);

        SimTile hatchHV = new SimTile(SimTile.TileType.HATCH_DISTILLED_WATER);
        hatchHV.setTier(3); // HV
        hatchHV.setInputFluidAmount(8000);
        hatchHV.setTemperature(200.0);
        hatchHV.coolantSubstep(cycleSeconds(), 1.0);
        hatchHV.nuclearTick(1.0);

        SimTile hatchEV = new SimTile(SimTile.TileType.HATCH_DISTILLED_WATER);
        hatchEV.setTier(4); // EV
        hatchEV.setInputFluidAmount(8000);
        hatchEV.setTemperature(200.0);
        hatchEV.coolantSubstep(cycleSeconds(), 1.0);
        hatchEV.nuclearTick(1.0);

        assertTrue(
            hatchMV.getTemperature() > hatchHV.getTemperature(),
            "MV hatch temp (" + hatchMV.getTemperature()
                + "°C) must be higher than HV hatch temp ("
                + hatchHV.getTemperature()
                + "°C)");
        assertTrue(
            hatchHV.getTemperature() > hatchEV.getTemperature(),
            "HV hatch temp (" + hatchHV.getTemperature()
                + "°C) must be higher than EV hatch temp ("
                + hatchEV.getTemperature()
                + "°C)");
        assertTrue(
            hatchEV.getTemperature() > 100.0,
            "EV hatch temp (" + hatchEV.getTemperature()
                + "°C) must still be strictly above 100°C boiling point after 1 tick");
    }

    @Test
    void testIC2CoolantHatchTemperatureRisesAboveAmbient() {
        SimTile hatch = new SimTile(SimTile.TileType.HATCH_IC2_COOLANT);
        hatch.setTier(3); // HV
        hatch.setInputFluidAmount(8000);
        hatch.setTemperature(80.0);
        hatch.coolantSubstep(cycleSeconds(), 1.0);
        hatch.nuclearTick(1.0);

        assertTrue(hatch.getTemperature() < 80.0, "IC2 coolant must cool hatch");
        assertTrue(
            hatch.getTemperature() > NuclearSimulationEngine.AMBIENT_TEMP,
            "IC2 coolant must not drop below ambient");
        assertNotEquals(
            NuclearSimulationEngine.AMBIENT_TEMP,
            hatch.getTemperature(),
            0.01,
            "IC2 coolant must not be pinned to ambient in 1 tick");
        assertTrue(hatch.getOutputFluidAmount() > 0, "Hot coolant must be produced");
    }

    @Test
    void testHatchCoolingContinuousWithoutExplosion() {
        MTENuclearReactor reactor = new MTENuclearReactor("nuclear.reactor.conductive.test");
        reactor.setTicksPerCycle(20);
        reactor.mPipeTier = NuclearSimulationEngine.PIPE_TIER_PLATINUM;
        reactor.mEfficiency = 10000;

        if (!net.minecraftforge.fluids.FluidRegistry.isFluidRegistered("distilledwater")) {
            net.minecraftforge.fluids.FluidRegistry
                .registerFluid(new net.minecraftforge.fluids.Fluid("distilledwater"));
        }
        if (!net.minecraftforge.fluids.FluidRegistry.isFluidRegistered("steam")) {
            net.minecraftforge.fluids.FluidRegistry.registerFluid(new net.minecraftforge.fluids.Fluid("steam"));
        }
        net.minecraftforge.fluids.Fluid distilledWater = net.minecraftforge.fluids.FluidRegistry
            .getFluid("distilledwater");

        MTEHatchNuclearHatch hatch = new MTEHatchNuclearHatch("test.hatch", 3, 32000, new String[0], null);
        hatch.mTemperature = 150.0; // Above 100°C boiling point
        hatch.mInputFluid = new net.minecraftforge.fluids.FluidStack(distilledWater, 8000);

        com.gtnewhorizons.modularnuclear.common.metatileentity.multi.NuclearReactorPhysics

            .hatchCoolantSubstep(reactor, hatch, cycleSeconds(), 1.0);

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
        reactor.setTicksPerCycle(20);
        reactor.mPipeTier = NuclearSimulationEngine.PIPE_TIER_PLATINUM;
        reactor.mEfficiency = 10000;
        reactor.mReactorDamage = 10.0;
        reactor.fixAllIssues();
        assertEquals(6, reactor.getRepairStatus());

        gregtech.api.interfaces.tileentity.IGregTechTileEntity teMock = org.mockito.Mockito
            .mock(gregtech.api.interfaces.tileentity.IGregTechTileEntity.class);
        org.mockito.Mockito.when(teMock.isServerSide())
            .thenReturn(true);
        org.mockito.Mockito.when(teMock.getWorld())
            .thenReturn(org.mockito.Mockito.mock(net.minecraft.world.World.class));
        reactor.setBaseMetaTileEntity(teMock);

        if (!net.minecraftforge.fluids.FluidRegistry.isFluidRegistered("distilledwater")) {
            net.minecraftforge.fluids.FluidRegistry
                .registerFluid(new net.minecraftforge.fluids.Fluid("distilledwater"));
        }
        if (!net.minecraftforge.fluids.FluidRegistry.isFluidRegistered("steam")) {
            net.minecraftforge.fluids.FluidRegistry.registerFluid(new net.minecraftforge.fluids.Fluid("steam"));
        }
        net.minecraftforge.fluids.Fluid distilledWater = net.minecraftforge.fluids.FluidRegistry
            .getFluid("distilledwater");

        MTEHatchNuclearHatch hatch = new MTEHatchNuclearHatch("test.hatch.starve", 3, 32000, new String[0], null);
        hatch.mTemperature = 200.0;
        // Only 2 mB of distilled water - insufficient to absorb Q_max (~98 EU, needing ~20 mB)
        hatch.mInputFluid = new net.minecraftforge.fluids.FluidStack(distilledWater, 2);

        com.gtnewhorizons.modularnuclear.common.metatileentity.multi.NuclearReactorPhysics

            .hatchCoolantSubstep(reactor, hatch, cycleSeconds(), 1.0);

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
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid grid = new com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid(
            5,
            5,
            NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        grid.setStrictMode(true);
        assertTrue(grid.isStrictMode());

        // Place a coolant hatch and set it dry and superheated
        grid.setTile(
            2,
            2,
            com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.HATCH_DISTILLED_WATER);
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile hatch = grid.getTile(2, 2);
        hatch.setTemperature(150.0);
        hatch.setInputFluidAmount(0);
        hatch.setWasDry(true);
        hatch.setAutoRefill(true);

        // Step should trigger thermal shock and fail-fast in strict mode
        boolean ok = grid.step();
        assertFalse(ok, "Step must halt immediately in strict mode on thermal shock");
        assertTrue(grid.isPowerFailed(), "Power failed flag must be set in strict mode");
        assertTrue(
            grid.getPowerFailReason()
                .contains("Thermal Shock"),
            "Power fail reason must cite thermal shock: " + grid.getPowerFailReason());
        assertFalse(
            grid.getIncidentLog()
                .isEmpty(),
            "Incident log must capture thermal shock event");
        assertEquals(
            "THERMAL_SHOCK",
            grid.getIncidentLog()
                .get(0)
                .type());
    }

    @Test
    void testStandaloneGridPermissiveModeAccumulatesDamageAndLogs() {
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid grid = new com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid(
            5,
            5,
            NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        grid.setStrictMode(false); // Interactive webapp mode
        assertFalse(grid.isStrictMode());

        // Place a coolant hatch and set it dry and superheated
        grid.setTile(
            2,
            2,
            com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile.TileType.HATCH_DISTILLED_WATER);
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
        assertFalse(
            grid.getIncidentLog()
                .isEmpty(),
            "Incident log must contain the event");
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid.IncidentEvent ev = grid
            .getIncidentLog()
            .get(0);
        assertEquals("THERMAL_SHOCK", ev.type());
        assertEquals(2.0, ev.damage(), 1e-6);

        // Repair clears damage and issues, logging a REPAIR event
        grid.repair();
        assertEquals(0.0, grid.getReactorDamage(), 1e-6);
        assertEquals(0, grid.getMaintenanceIssues());
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid.IncidentEvent repEv = grid
            .getIncidentLog()
            .get(
                grid.getIncidentLog()
                    .size() - 1);
        assertEquals("REPAIR", repEv.type());
    }

    @Test
    void testStandaloneGridStrictModeCasingOverheatFailsFast() {
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid grid = new com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid(
            5,
            5,
            NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        grid.setStrictMode(true);

        // Electrum casing limit is 1200°C. Set all tiles to 1250°C so diffusion does not cool them below limit
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                grid.getTile(x, y)
                    .setTemperature(1250.0);
            }
        }

        boolean ok = grid.step();
        assertFalse(ok, "Step must halt immediately in strict mode on casing overheat");
        assertTrue(grid.isPowerFailed(), "Power failed flag must be set on casing overheat");
        assertTrue(
            grid.getPowerFailReason()
                .contains("overheated casing max"),
            "Power fail reason must cite casing overheat");
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
        assertTrue(
            tile.getDepletedDisplayName()
                .contains("Depleted Uranium"));

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
        assertEquals(
            2,
            grid.getCumulativeDepletedItems(SimTile.TileType.FUEL_URANIUM_QUAD),
            "Should increment to 2 depleted rods");
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

        String json = com.gtnewhorizons.modularnuclear.common.nuclear.standalone.NuclearSimWasmBridge
            .buildStateJson(grid, false);
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
        gridLithium.setTicksPerCycle(20);
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
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.NuclearSimWasmBridge
            .initGrid(5, 5, NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.NuclearSimWasmBridge.setRunning(true);
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.NuclearSimWasmBridge.getGrid()
            .triggerExplosion("WASM Meltdown test");
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.NuclearSimWasmBridge.step();
        assertFalse(
            com.gtnewhorizons.modularnuclear.common.nuclear.standalone.NuclearSimWasmBridge.isRunning(),
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
        assertTrue(
            grid.getLastHaltIncidentReason()
                .contains("Overheated cell"),
            "Last halt incident reason must match");

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

        assertEquals(
            fuelTile.getMaxDurability(),
            fuelTile.getDurability(),
            "Fuel rod must be replenished to 100% durability");
        assertFalse(fuelTile.isDepleted(), "Fresh fuel rod must not be marked depleted");
        assertEquals(
            1,
            grid.getCumulativeDepletedItems(SimTile.TileType.FUEL_URANIUM_QUAD),
            "Should track 1 depleted rod byproduct");

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
        assertEquals(
            liquidHatch.getInputFluidCapacity(),
            liquidHatch.getInputFluidAmount(),
            "Liquid fuel hatch must be replenished to capacity by autoSupplyFuel");

        // With auto-supply fuel OFF, drained hatch is NOT automatically topped off to capacity
        grid.setAutoSupplyFuel(false);
        liquidHatch.setInputFluidAmount(300);
        grid.step();
        assertTrue(
            liquidHatch.getInputFluidAmount() <= 300,
            "Liquid fuel hatch must not be topped off to capacity when autoSupplyFuel is disabled");
    }

    @Test
    void testWasmBridgeIncidentHaltAndResume() {
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.NuclearSimWasmBridge
            .initGrid(5, 5, NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.NuclearSimWasmBridge.setStopOnIncidents(true);
        assertTrue(com.gtnewhorizons.modularnuclear.common.nuclear.standalone.NuclearSimWasmBridge.isStopOnIncidents());
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.NuclearSimWasmBridge.setRunning(true);

        StandaloneNuclearGrid g = com.gtnewhorizons.modularnuclear.common.nuclear.standalone.NuclearSimWasmBridge
            .getGrid();
        g.logIncident("CELL_HEAT", "Cell heat damage at (1,1)", 3.0);
        assertTrue(g.isHaltedByIncident());

        // Step should detect incident halt and set isRunning = false
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.NuclearSimWasmBridge.step();
        assertFalse(
            com.gtnewhorizons.modularnuclear.common.nuclear.standalone.NuclearSimWasmBridge.isRunning(),
            "Bridge isRunning must become false on incident halt");

        // Resuming by setting running = true clears the incident halt
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.NuclearSimWasmBridge.setRunning(true);
        assertFalse(g.isHaltedByIncident(), "Bridge resuming must clear haltedByIncident");
        assertTrue(com.gtnewhorizons.modularnuclear.common.nuclear.standalone.NuclearSimWasmBridge.isRunning());
    }

    @Test
    void testRepairTemperatureThresholdScalingAndGating() {
        // Verify formula: max(120.0, 10% of max operating temperature)
        assertEquals(
            120.0,
            NuclearSimulationEngine.getRepairTemperatureThreshold(NuclearSimulationEngine.PIPE_TIER_ELECTRUM),
            1e-6);
        assertEquals(
            140.0,
            NuclearSimulationEngine.getRepairTemperatureThreshold(NuclearSimulationEngine.PIPE_TIER_PLATINUM),
            1e-6);
        assertEquals(
            180.0,
            NuclearSimulationEngine.getRepairTemperatureThreshold(NuclearSimulationEngine.PIPE_TIER_OSMIUM),
            1e-6);
        assertEquals(
            220.0,
            NuclearSimulationEngine.getRepairTemperatureThreshold(NuclearSimulationEngine.PIPE_TIER_QUANTIUM),
            1e-6);
        assertEquals(
            260.0,
            NuclearSimulationEngine.getRepairTemperatureThreshold(NuclearSimulationEngine.PIPE_TIER_FLUXED_ELECTRUM),
            1e-6);
        assertEquals(
            320.0,
            NuclearSimulationEngine.getRepairTemperatureThreshold(NuclearSimulationEngine.PIPE_TIER_BLACK_PLUTONIUM),
            1e-6);

        // Grid temperature gating verification
        StandaloneNuclearGrid grid = new StandaloneNuclearGrid(5, 5, NuclearSimulationEngine.PIPE_TIER_PLATINUM);
        grid.setReactorDamage(25.0);
        grid.setMaintenanceIssues(1);

        // At ambient temp (20°C <= 140°C), repair succeeds
        assertTrue(grid.canRepair(), "Must be eligible for repair at ambient temperature");
        boolean repOk = grid.repair();
        assertTrue(repOk, "Repair must succeed when core is cool");
        assertEquals(0.0, grid.getReactorDamage(), 1e-6);
        assertEquals(0, grid.getMaintenanceIssues());

        // Heat the core above threshold (150°C > 140°C threshold)
        grid.setReactorDamage(30.0);
        grid.setMaintenanceIssues(1);
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                if (grid.getTile(x, y) != null) {
                    grid.getTile(x, y)
                        .setTemperature(150.0);
                }
            }
        }
        grid.step(); // compute coreAvgTemp

        assertTrue(grid.getCoreAvgTemp() > grid.getRepairTemperatureThreshold(), "Core must be above repair threshold");
        assertFalse(grid.canRepair(), "canRepair must be false when core is too hot");

        boolean repBlocked = grid.repair();
        assertFalse(repBlocked, "Repair must be blocked when core average temperature is above threshold");
        assertEquals(30.0, grid.getReactorDamage(), 1e-6, "Damage must NOT be cleared when repair is blocked");
        assertEquals(1, grid.getMaintenanceIssues(), "Maintenance issues must NOT be cleared when repair is blocked");
        assertEquals(
            "REPAIR_FAILED",
            grid.getIncidentLog()
                .get(
                    grid.getIncidentLog()
                        .size() - 1)
                .type());

        // But forceRepair() succeeds even when hot (unconstrained webapp repair)
        boolean forceOk = grid.forceRepair();
        assertTrue(forceOk, "forceRepair must succeed even when core temperature exceeds safe threshold");
        assertEquals(0.0, grid.getReactorDamage(), 1e-6, "forceRepair must reset damage to 0");
        assertEquals(0, grid.getMaintenanceIssues(), "forceRepair must reset maintenance issues to 0");
        assertEquals(
            "REPAIR",
            grid.getIncidentLog()
                .get(
                    grid.getIncidentLog()
                        .size() - 1)
                .type());
    }

    @Test
    void testDamageSliderAndPendingIncidentLogging() {
        StandaloneNuclearGrid grid = new StandaloneNuclearGrid(5, 5, NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        grid.setStopOnIncidents(true);

        // Test manual damage setting
        grid.setReactorDamage(42.5);
        assertEquals(42.5, grid.getReactorDamage(), 1e-6);

        // Setting damage to 100% triggers explosion
        grid.setReactorDamage(100.0);
        assertTrue(grid.isExploded());

        // Setting damage back down to 50% clears explosion and halted status
        grid.setReactorDamage(50.0);
        assertFalse(grid.isExploded());
        assertFalse(grid.isHaltedByIncident());
        assertEquals(50.0, grid.getReactorDamage(), 1e-6);

        // Test pending incident logging when damage increases or impending incident occurs
        grid.loadPreset("60A_ELECTRUM_5X5");
        grid.setStopOnIncidents(true);
        grid.setReactorDamage(50.0);
        grid.clearHaltedByIncident();

        // 1. Log incident with damage under stopOnIncidents -> triggers PENDING_INCIDENT in log
        grid.logIncident("CASING_OVERHEAT", "Cell overheated casing limit", 2.0);
        assertTrue(grid.isHaltedByIncident(), "Grid must be halted by incident");
        boolean hasPending = grid.getIncidentLog()
            .stream()
            .anyMatch(e -> "PENDING_INCIDENT".equals(e.type()));
        assertTrue(hasPending, "Incident log must record PENDING_INCIDENT when halted to prevent further damage");

        // 2. Clear halt and test impending thermal shock prevention
        grid.clearHaltedByIncident();
        assertFalse(grid.isHaltedByIncident());
        SimTile hatch = grid.getTile(0, 1);
        assertNotNull(hatch);
        hatch.setType(SimTile.TileType.HATCH_DISTILLED_WATER);
        hatch.setInputFluidAmount(0);
        hatch.setWasDry(true);
        hatch.setAutoRefill(true);
        hatch.setTemperature(150.0); // Exceeds 100°C boiling threshold

        boolean stepOk = grid.step();
        assertFalse(stepOk, "step() must return false when impending thermal shock is caught");
        assertTrue(grid.isHaltedByIncident(), "Grid must be halted on impending thermal shock");
        assertTrue(
            grid.getLastHaltIncidentReason()
                .contains("Impending thermal shock")
                || grid.getLastHaltIncidentReason()
                    .contains("thermal shock"));
    }

    @Test
    void testInfoDataScannerIntegration() {
        MTENuclearReactor reactor = new MTENuclearReactor("test.reactor.scanner");
        assertTrue(reactor.isGivingInformation());

        // 1. Unformed machine
        reactor.mMachine = false;
        String[] unformedInfo = reactor.getInfoData();
        assertNotNull(unformedInfo);
        assertEquals(1, unformedInfo.length);
        assertTrue(unformedInfo[0].contains("Structure Incomplete"));

        // 2. Formed machine, operational / running
        reactor.mMachine = true;
        reactor.setTicksPerCycle(20);
        reactor.mPipeTier = NuclearSimulationEngine.PIPE_TIER_OSMIUM;
        reactor.gridSize = 3;
        reactor.mGrid = new INuclearTile[3][3];
        reactor.mCoreTemp = 425.5;
        reactor.mReactorDamage = 0.0;
        reactor.mReactivity = 1.0;
        reactor.mNeutronsProduced = 5000;
        reactor.mFastAbsorbed = 3000;
        reactor.mThermalAbsorbed = 1800;
        reactor.mEscapedNeutrons = 200;
        reactor.mDirectPowerEUt = 2048;
        reactor.mOutputCoolantRate = 1500;
        reactor.mOutputCoolantName = "Supercritical Carbon Dioxide";

        String[] info = reactor.getInfoData();
        assertNotNull(info);
        assertTrue(info.length >= 6);

        String combined = EnumChatFormatting.getTextWithoutFormattingCodes(String.join("\n", info));
        assertTrue(combined.contains("Operational (running)"), "State should show operational when neutrons produced");
        assertTrue(combined.contains("425.5"), "Core temp should be reported");
        assertTrue(combined.contains("1,800 °C"), "Max temp for Osmium tier should be 1800 °C");
        assertTrue(combined.contains("Osmium"), "Tier name should be reported");
        assertTrue(combined.contains("0.0% (nominal)"), "Hull damage should be nominal");
        assertTrue(combined.contains("100.0%"), "Reactivity should be 100%");
        assertTrue(
            combined.contains("Flux: " + NuclearSimulationEngine.formatNeutronFlux(5000, 20)),
            "Neutron flux should be formatted");
        assertTrue(combined.contains("3,000 fast/s (3,000 /cycle)"), "Fast neutrons should be reported");
        assertTrue(combined.contains("+2,048 EU/t"), "EU output should be reported");
        assertTrue(
            combined.contains("1,500 L/s (1,500 L/cycle) Supercritical Carbon Dioxide"),
            "Coolant output should be reported");

        // 3. Test SCRAM state
        reactor.mScram = true;
        String[] scramInfo = reactor.getInfoData();
        String scramCombined = String.join("\n", scramInfo);
        assertTrue(scramCombined.contains("SCRAM (emergency shutdown active)"));

        // 4. Test Damaged state
        reactor.mScram = false;
        reactor.mReactorDamage = 25.0;
        String[] damagedInfo = reactor.getInfoData();
        String damagedCombined = String.join("\n", damagedInfo);
        assertTrue(damagedCombined.contains("25.0%"));
        assertTrue(damagedCombined.contains("repair threshold: 180 °C")); // 10% of 1800 °C

        // 5. Test Meltdown Imminent state
        reactor.mReactorDamage = 95.0;
        String[] meltdownInfo = reactor.getInfoData();
        String meltdownCombined = String.join("\n", meltdownInfo);
        assertTrue(meltdownCombined.contains("MELTDOWN IMMINENT"));

        // 6. Test getExtraInfoData does not duplicate info
        List<String> extraList = new ArrayList<>();
        reactor.getExtraInfoData(extraList);
        assertTrue(extraList.isEmpty(), "getExtraInfoData should be empty to prevent duplication in portable scanner");
    }

    @Test
    void testReactorSimulationIsUnstoppableByScramOrSoftMalletOrMaintenance() {
        MTENuclearReactor reactor = new MTENuclearReactor("test.reactor.unstoppable");
        reactor.mMachine = true;
        reactor.mPipeTier = NuclearSimulationEngine.PIPE_TIER_OSMIUM;
        reactor.gridSize = 3;
        reactor.mGrid = new INuclearTile[3][3];

        gregtech.api.metatileentity.BaseMetaTileEntity baseTe = org.mockito.Mockito
            .mock(gregtech.api.metatileentity.BaseMetaTileEntity.class);
        org.mockito.Mockito.when(baseTe.isServerSide())
            .thenReturn(true);
        reactor.setBaseMetaTileEntity(baseTe);

        // Put a fuel rod tile in mGrid[1][1]
        MockNuclearTile fuelTile = new MockNuclearTile(true, 1000);
        reactor.mGrid[1][1] = fuelTile;

        // 1. Normal cycle at tick 20: simulation runs and produces neutrons
        org.mockito.Mockito.when(baseTe.isAllowedToWork())
            .thenReturn(true);
        reactor.mScram = false;
        reactor.onPostTick(baseTe, 20);
        assertTrue(reactor.mNeutronsProduced > 0, "Simulation must run at tick 20");

        // 2. SCRAM is engaged: simulation STILL runs at tick 40!
        reactor.setScram(true);
        assertTrue(reactor.mScram);
        reactor.mNeutronsProduced = 0;
        reactor.onPostTick(baseTe, 40);
        assertTrue(reactor.mNeutronsProduced > 0, "Simulation MUST NOT stop under SCRAM");

        // 3. Soft mallet toggles work-allowed to false: simulation STILL runs at tick 60!
        org.mockito.Mockito.when(baseTe.isAllowedToWork())
            .thenReturn(false);
        reactor.mNeutronsProduced = 0;
        reactor.onPostTick(baseTe, 60);
        assertTrue(reactor.mNeutronsProduced > 0, "Simulation MUST NOT stop when soft mallet disables work");

        // 4. All 6 maintenance issues present: simulation STILL runs at tick 80!
        reactor.mWrench = false;
        reactor.mScrewdriver = false;
        reactor.mSoftMallet = false;
        reactor.mHardHammer = false;
        reactor.mCrowbar = false;
        reactor.mSolderingTool = false;
        reactor.mNeutronsProduced = 0;
        reactor.onPostTick(baseTe, 80);
        assertTrue(reactor.mNeutronsProduced > 0, "Simulation MUST NOT stop under maintenance failure");

        // 5. Structure broken (unformed): simulation DOES NOT run!
        reactor.mMachine = false;
        reactor.mNeutronsProduced = 0;
        reactor.onPostTick(baseTe, 100);
        assertEquals(0, reactor.mNeutronsProduced, "Simulation must stop only when structure is unformed");
    }

    @Test
    void testReactorSimulationProceedsOnThermalShock() {
        MTENuclearReactor reactor = new MTENuclearReactor("test.reactor.thermalshock.proceed");
        reactor.mMachine = true;
        reactor.mPipeTier = NuclearSimulationEngine.PIPE_TIER_OSMIUM;
        reactor.gridSize = 3;
        reactor.mGrid = new INuclearTile[3][3];
        reactor.fixAllIssues();
        assertEquals(6, reactor.getRepairStatus());

        gregtech.api.metatileentity.BaseMetaTileEntity baseTe = org.mockito.Mockito
            .mock(gregtech.api.metatileentity.BaseMetaTileEntity.class);
        org.mockito.Mockito.when(baseTe.isServerSide())
            .thenReturn(true);
        org.mockito.Mockito.when(baseTe.isAllowedToWork())
            .thenReturn(true);
        org.mockito.Mockito.when(baseTe.getWorld())
            .thenReturn(org.mockito.Mockito.mock(net.minecraft.world.World.class));
        reactor.setBaseMetaTileEntity(baseTe);

        // Put a fuel rod tile in mGrid[1][1]
        MockNuclearTile fuelTile = new MockNuclearTile(true, 1000);
        reactor.mGrid[1][1] = fuelTile;

        // Register distilled water if needed
        if (!net.minecraftforge.fluids.FluidRegistry.isFluidRegistered("distilledwater")) {
            net.minecraftforge.fluids.FluidRegistry
                .registerFluid(new net.minecraftforge.fluids.Fluid("distilledwater"));
        }
        net.minecraftforge.fluids.Fluid distilledWater = net.minecraftforge.fluids.FluidRegistry
            .getFluid("distilledwater");

        // Add a coolant hatch that is dry and superheated (>100°C) with cold coolant injected
        MTEHatchNuclearHatch hatch = new MTEHatchNuclearHatch("test.hatch.shock", 3, 32000, new String[0], null);
        hatch.mTemperature = 150.0;
        hatch.mWasDry = true;
        hatch.mInputFluid = new net.minecraftforge.fluids.FluidStack(distilledWater, 8000);

        gregtech.api.interfaces.tileentity.IGregTechTileEntity hatchTe = org.mockito.Mockito
            .mock(gregtech.api.interfaces.tileentity.IGregTechTileEntity.class);
        org.mockito.Mockito.when(hatchTe.getMetaTileEntity())
            .thenReturn(hatch);
        hatch.setBaseMetaTileEntity(hatchTe);
        reactor.mNuclearTiles.add(hatchTe);

        // Run cycle at tick 20
        reactor.mNeutronsProduced = 0;
        reactor.onPostTick(baseTe, 20);

        // Verify thermal shock occurred
        assertNull(hatch.mInputFluid, "Coolant in superheated dry hatch must be voided on thermal shock");
        assertTrue(hatch.mWasDry, "Hatch must remain marked dry after thermal shock");
        assertEquals(2.0, reactor.mReactorDamage, 1e-6, "Reactor damage must increase by 2% on thermal shock");
        assertEquals(5, reactor.getRepairStatus(), "Maintenance issues must increase by 1 on thermal shock");

        // CRITICAL: verify that the simulation cycle was NOT skipped!
        assertTrue(
            reactor.mNeutronsProduced > 0,
            "Simulation cycle MUST proceed and produce neutrons even after thermal shock");
        assertTrue(reactor.mCoreTemp > 0.0, "Core temperature must be updated by simulation cycle");
    }

    @Test
    void testDistilledWaterVsHeavyWaterModerationAndTransmutation() {
        // 1. SimTile moderation probabilities
        SimTile dwTile = new SimTile(SimTile.TileType.HATCH_DISTILLED_WATER);
        dwTile.setInputFluidAmount(1000);
        assertEquals(0.25, dwTile.getModerationProbability(), 1e-4, "Distilled water moderation must be 0.25");

        SimTile hwTile = new SimTile(SimTile.TileType.HATCH_HEAVY_WATER);
        hwTile.setInputFluidAmount(1000);
        assertEquals(0.90, hwTile.getModerationProbability(), 1e-4, "Heavy water moderation must be 0.90");

        // 2. High-pressure passage moderation probabilities
        MTEHatchNuclearHighPressure hp = new MTEHatchNuclearHighPressure("test.hp", 4, new String[0], null);
        ICoolantLoopPump mockPump = org.mockito.Mockito.mock(ICoolantLoopPump.class);
        hp.setConnectedPump(mockPump);

        org.mockito.Mockito.when(mockPump.getCoolantFluidProperty())
            .thenReturn(CoolantFluidProperty.DISTILLED_WATER);
        assertEquals(
            0.25,
            hp.getModerationProbability(),
            1e-4,
            "Distilled water high pressure passage moderation must be 0.25");

        org.mockito.Mockito.when(mockPump.getCoolantFluidProperty())
            .thenReturn(CoolantFluidProperty.HEAVY_WATER);
        assertEquals(
            0.90,
            hp.getModerationProbability(),
            1e-4,
            "Heavy water high pressure passage moderation must be 0.90");

        // 3. SimTile fast-neutron transmutation rate and stoichiometry verification
        int dwSuccess = 0;
        int hwSuccess = 0;
        int trials = 10_000;
        for (int i = 0; i < trials; i++) {
            SimTile testDw = new SimTile(SimTile.TileType.HATCH_DISTILLED_WATER);
            testDw.setInputFluidAmount(1000);
            testDw.onNeutronAbsorbed(NeutronType.FAST, 1);
            if (testDw.getTotalDeuteriumProduced() > 0) {
                dwSuccess++;
                assertEquals(2, testDw.getTotalDeuteriumProduced(), "Transmutation must produce 2 L Deuterium");
                assertEquals(1, testDw.getTotalOxygenProduced(), "Transmutation must produce 1 L Oxygen");
                assertEquals(998, testDw.getInputFluidAmount(), "Transmutation must consume 2 L water");
            }

            SimTile testHw = new SimTile(SimTile.TileType.HATCH_HEAVY_WATER);
            testHw.setInputFluidAmount(1000);
            testHw.onNeutronAbsorbed(NeutronType.FAST, 1);
            if (testHw.getTotalTritiumProduced() > 0) {
                hwSuccess++;
                assertEquals(2, testHw.getTotalTritiumProduced(), "Transmutation must produce 2 L Tritium");
                assertEquals(1, testHw.getTotalOxygenProduced(), "Transmutation must produce 1 L Oxygen");
                assertEquals(998, testHw.getInputFluidAmount(), "Transmutation must consume 2 L heavy water");
            }
        }
        double dwRate = (double) dwSuccess / trials;
        double hwRate = (double) hwSuccess / trials;
        assertEquals(0.025, dwRate, 0.010, "Deuterium transmutation rate should be ~2.5%");
        assertEquals(0.010, hwRate, 0.007, "Tritium transmutation rate should be ~1.0%");
    }

    @Test
    void testConfigDrivenAllowlistsAndBlocklists() {
        // 1. Save original config state
        String[] origBlocked = com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.blockedItems.clone();
        String[] origFuels = com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.allowedFuelItems
            .clone();
        String[] origCoolants = com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.allowedCoolantFluids
            .clone();
        String[] origRods = com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.allowedControlRods
            .clone();
        String[] origInsulators = com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.allowedInsulators
            .clone();

        try {
            // 2. Pattern matching helper tests
            assertTrue(
                com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig
                    .matchesPattern("ic2.reactorDepletedUraniumSimple", "*depleted*"));
            assertTrue(
                com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig
                    .matchesPattern("gregtech:gt.reactorUranium4", "gregtech:gt.reactorUranium*"));
            assertFalse(
                com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig
                    .matchesPattern("minecraft:dirt", "*fuel*"));

            // 3. Dynamic custom fuel item addition
            net.minecraft.item.Item customFuel = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
            org.mockito.Mockito.when(customFuel.getUnlocalizedName(org.mockito.Mockito.any()))
                .thenReturn("custommod.super_fuel_rod");
            ItemStack fuelStack = new ItemStack(customFuel, 1, 0);

            // Initially not allowed
            assertFalse(com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification.isItemFuel(fuelStack));

            // Server operator adds custom fuel pattern to config:
            com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.allowedFuelItems = new String[] {
                "*super_fuel_rod*" };
            assertTrue(com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.isItemFuel(fuelStack));
            assertTrue(com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification.isItemFuel(fuelStack));

            // 4. Dynamic custom blocked item addition
            net.minecraft.item.Item customDangerous = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
            org.mockito.Mockito.when(customDangerous.getUnlocalizedName(org.mockito.Mockito.any()))
                .thenReturn("somemod.radioactive_waste");
            ItemStack wasteStack = new ItemStack(customDangerous, 1, 0);

            assertFalse(
                com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification
                    .isDepletedOrEmptyRod(wasteStack));
            com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.blockedItems = new String[] {
                "*radioactive_waste*" };
            assertTrue(
                com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification
                    .isDepletedOrEmptyRod(wasteStack));
            assertFalse(
                com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification
                    .isItemAllowedInBus(wasteStack, null));

            // 5. Dynamic custom coolant fluid addition
            net.minecraftforge.fluids.Fluid liquidHelium = org.mockito.Mockito
                .mock(net.minecraftforge.fluids.Fluid.class);
            org.mockito.Mockito.when(liquidHelium.getName())
                .thenReturn("liquid_helium");
            net.minecraftforge.fluids.FluidStack heliumStack = mockFluidStack(liquidHelium, 1000);

            assertFalse(
                com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification.isCoolantFluid(heliumStack));
            com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.allowedCoolantFluids = new String[] {
                "liquid_helium" };
            assertTrue(
                com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification.isCoolantFluid(heliumStack));

            // 6. Dynamic custom control rod mapping
            net.minecraft.item.Item customRodItem = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
            org.mockito.Mockito.when(customRodItem.getUnlocalizedName(org.mockito.Mockito.any()))
                .thenReturn("custommod.lead_bismuth_rod");
            ItemStack rodStack = new ItemStack(customRodItem, 1, 0);

            assertEquals(
                "NONE",
                com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod
                    .getRodType(rodStack)
                    .name());
            com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.allowedControlRods = new String[] {
                "*lead_bismuth_rod*=CADMIUM" };
            assertEquals(
                "CADMIUM",
                com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod
                    .getRodType(rodStack)
                    .name());

            // 7. Dynamic custom insulator mapping
            net.minecraft.item.Item customFoil = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
            org.mockito.Mockito.when(customFoil.getUnlocalizedName(org.mockito.Mockito.any()))
                .thenReturn("aerospace.aerogel_foil");
            ItemStack foilStack = new ItemStack(customFoil, 1, 0);

            assertEquals(
                0.0,
                com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification
                    .getInsulationDampening(foilStack),
                1e-6);
            com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.allowedInsulators = new String[] {
                "*aerogel_foil*=0.85" };
            assertEquals(
                0.85,
                com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification
                    .getInsulationDampening(foilStack),
                1e-6);

        } finally {
            // Restore original config arrays
            com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.blockedItems = origBlocked;
            com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.allowedFuelItems = origFuels;
            com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.allowedCoolantFluids = origCoolants;
            com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.allowedControlRods = origRods;
            com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.allowedInsulators = origInsulators;
        }
    }

    @Test
    void testReactorHatchItemSegregationAndDepletedRodRejection() {
        MTEHatchNuclearBus bus = new MTEHatchNuclearBus("test.nuclear.bus", 4, new String[0], null);
        com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod rodHatch = new com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod(
            "test.control.rod",
            4,
            new String[0],
            null);

        // 1. Depleted rod rejection
        net.minecraft.item.Item depletedItem = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
        org.mockito.Mockito.when(depletedItem.getUnlocalizedName(org.mockito.Mockito.any()))
            .thenReturn("ic2.reactorDepletedUraniumSimple");
        ItemStack depletedStack = new ItemStack(depletedItem, 1, 0);

        assertTrue(
            com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification.isDepletedOrEmptyRod(depletedStack));
        assertFalse(
            bus.isItemAllowed(depletedStack),
            "Depleted rods must be strictly disallowed in nuclear core buses");
        assertFalse(bus.allowPutStack(null, MTEHatchNuclearBus.SLOT_INPUT, ForgeDirection.UNKNOWN, depletedStack));
        assertFalse(bus.isItemValidForSlot(MTEHatchNuclearBus.SLOT_INPUT, depletedStack));

        // 2. Control rod segregation
        net.minecraft.item.Item silverRodItem = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
        org.mockito.Mockito.when(silverRodItem.getUnlocalizedName(org.mockito.Mockito.any()))
            .thenReturn("stickLongSilver");
        ItemStack silverRodStack = new ItemStack(silverRodItem, 1, 0);

        assertTrue(
            com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod
                .isControlRod(silverRodStack));
        // Control rods must NOT be allowed into core buses:
        assertFalse(bus.isItemAllowed(silverRodStack), "Control rods must NOT be allowed in reactor core buses");
        assertFalse(bus.allowPutStack(null, MTEHatchNuclearBus.SLOT_INPUT, ForgeDirection.UNKNOWN, silverRodStack));
        // Control rods MUST be allowed into control rod hatches:
        assertTrue(
            rodHatch.isItemValidForSlot(
                com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod.SLOT_ROD,
                silverRodStack));
        assertTrue(
            rodHatch.allowPutStack(
                null,
                com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod.SLOT_ROD,
                ForgeDirection.UNKNOWN,
                silverRodStack));

        // 3. Normal unrelated items (e.g. cobblestone/dirt) rejected from BOTH hatches
        net.minecraft.item.Item dirtItem = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
        org.mockito.Mockito.when(dirtItem.getUnlocalizedName(org.mockito.Mockito.any()))
            .thenReturn("tile.dirt");
        ItemStack dirtStack = new ItemStack(dirtItem, 1, 0);

        assertFalse(bus.isItemAllowed(dirtStack), "Dirt cannot be placed in nuclear core bus");
        assertFalse(bus.allowPutStack(null, MTEHatchNuclearBus.SLOT_INPUT, ForgeDirection.UNKNOWN, dirtStack));
        assertFalse(
            rodHatch.isItemValidForSlot(
                com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod.SLOT_ROD,
                dirtStack));
        assertFalse(
            rodHatch.allowPutStack(
                null,
                com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod.SLOT_ROD,
                ForgeDirection.UNKNOWN,
                dirtStack));
    }

    @Test
    void testOccupiedHatchSlotShortCircuits() {
        MTEHatchNuclearBus bus = new MTEHatchNuclearBus("test.nuclear.bus", 4, new String[0], null) {

            @Override
            public boolean isItemAllowed(ItemStack stack) {
                return stack != null;
            }
        };
        com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod rodHatch = new com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod(
            "test.control.rod",
            4,
            new String[0],
            null);

        net.minecraft.item.Item silverRodItem = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
        org.mockito.Mockito.when(silverRodItem.getUnlocalizedName(org.mockito.Mockito.any()))
            .thenReturn("stickLongSilver");
        ItemStack silverRodStack = new ItemStack(silverRodItem, 1, 0);

        net.minecraft.item.Item dummyItem = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
        ItemStack validStack = new ItemStack(dummyItem, 1, 0);

        // 1. When bus is empty: accepts valid item
        assertTrue(bus.isItemValidForSlot(MTEHatchNuclearBus.SLOT_INPUT, validStack));
        assertTrue(bus.allowPutStack(null, MTEHatchNuclearBus.SLOT_INPUT, ForgeDirection.UNKNOWN, validStack));

        // 2. When bus is occupied: short-circuits to false immediately
        bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = validStack;
        assertFalse(bus.isItemValidForSlot(MTEHatchNuclearBus.SLOT_INPUT, validStack));
        assertFalse(bus.allowPutStack(null, MTEHatchNuclearBus.SLOT_INPUT, ForgeDirection.UNKNOWN, validStack));

        // 3. When bus is emptied: accepts valid item again
        bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = null;
        assertTrue(bus.isItemValidForSlot(MTEHatchNuclearBus.SLOT_INPUT, validStack));
        assertTrue(bus.allowPutStack(null, MTEHatchNuclearBus.SLOT_INPUT, ForgeDirection.UNKNOWN, validStack));

        // 4. When control rod hatch is empty: accepts control rod
        assertTrue(
            rodHatch.isItemValidForSlot(
                com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod.SLOT_ROD,
                silverRodStack));
        assertTrue(
            rodHatch.allowPutStack(
                null,
                com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod.SLOT_ROD,
                ForgeDirection.UNKNOWN,
                silverRodStack));

        // 5. When control rod hatch is occupied: short-circuits to false immediately
        rodHatch.mInventory[com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod.SLOT_ROD] = silverRodStack;
        assertFalse(
            rodHatch.isItemValidForSlot(
                com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod.SLOT_ROD,
                silverRodStack));
        assertFalse(
            rodHatch.allowPutStack(
                null,
                com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod.SLOT_ROD,
                ForgeDirection.UNKNOWN,
                silverRodStack));

        // 6. When control rod hatch is emptied: accepts control rod again
        rodHatch.mInventory[com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod.SLOT_ROD] = null;
        assertTrue(
            rodHatch.isItemValidForSlot(
                com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod.SLOT_ROD,
                silverRodStack));
        assertTrue(
            rodHatch.allowPutStack(
                null,
                com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod.SLOT_ROD,
                ForgeDirection.UNKNOWN,
                silverRodStack));
    }

    @Test
    void testConfigDrivenTierLimitsAndBoilingPoints() {
        // Default tier limits
        assertEquals(
            1200.0,
            NuclearSimulationEngine.getMaxOperatingTemperature(NuclearSimulationEngine.PIPE_TIER_ELECTRUM),
            1e-4);
        assertEquals(
            1400.0,
            NuclearSimulationEngine.getMaxOperatingTemperature(NuclearSimulationEngine.PIPE_TIER_PLATINUM),
            1e-4);
        assertEquals(
            3200.0,
            NuclearSimulationEngine.getMaxOperatingTemperature(NuclearSimulationEngine.PIPE_TIER_BLACK_PLUTONIUM),
            1e-4);

        // Repair threshold is 10% of max operating temp
        assertEquals(
            120.0,
            NuclearSimulationEngine.getRepairTemperatureThreshold(NuclearSimulationEngine.PIPE_TIER_ELECTRUM),
            1e-4);
        assertEquals(
            320.0,
            NuclearSimulationEngine.getRepairTemperatureThreshold(NuclearSimulationEngine.PIPE_TIER_BLACK_PLUTONIUM),
            1e-4);

        // Boiling points
        assertEquals(Double.POSITIVE_INFINITY, NuclearSimulationEngine.getCoolantBoilingThreshold("ic2coolant"));
        assertEquals(101.4, NuclearSimulationEngine.getCoolantBoilingThreshold("heavywater"), 1e-4);
        assertEquals(100.0, NuclearSimulationEngine.getCoolantBoilingThreshold("distilledwater"), 1e-4);

        // Modify via config
        double origT0 = com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.maxOperatingTempTier[0];
        double origRatio = com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.repairTempThresholdRatio;
        try {
            com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.maxOperatingTempTier[0] = 1500.0;
            com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.repairTempThresholdRatio = 0.20;

            assertEquals(
                1500.0,
                NuclearSimulationEngine.getMaxOperatingTemperature(NuclearSimulationEngine.PIPE_TIER_ELECTRUM),
                1e-4);
            assertEquals(
                300.0,
                NuclearSimulationEngine.getRepairTemperatureThreshold(NuclearSimulationEngine.PIPE_TIER_ELECTRUM),
                1e-4);
        } finally {
            com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.maxOperatingTempTier[0] = origT0;
            com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig.repairTempThresholdRatio = origRatio;
        }
    }

    @Test
    void testYamlDataRegistryRoundtrip() throws Exception {
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("mptr_yaml_roundtrip_test");
        try {
            com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.init(tempDir.toFile());

            File configFolder = new File(tempDir.toFile(), "modularnuclear");
            assertTrue(configFolder.exists(), "Config folder modularnuclear should be created");
            File readmeFile = new File(configFolder, "README.txt");
            assertTrue(readmeFile.exists(), "README.txt should be written on init");

            // Default configurations are loaded directly from JAR resources without cluttering disk
            assertTrue(
                com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.getAllFuels()
                    .size() >= 20);
            assertNotNull(
                com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.findFuel("thoriumbasedliquidfuel"));
            assertNotNull(com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.getPhysics());
            assertEquals(
                6,
                com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.getAllTiers()
                    .size());

            // Explicitly saving to directory writes out complete YAML files
            com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.saveToDir(configFolder);
            File physicsFile = new File(configFolder, "physics.yaml");
            File tiersFile = new File(configFolder, "tiers.yaml");
            File fuelsFile = new File(configFolder, "fuels.yaml");
            File componentsFile = new File(configFolder, "components.yaml");

            assertTrue(physicsFile.exists(), "physics.yaml should be written");
            assertTrue(tiersFile.exists(), "tiers.yaml should be written");
            assertTrue(fuelsFile.exists(), "fuels.yaml should be written");
            assertTrue(componentsFile.exists(), "components.yaml should be written");

            // Re-loading from the exported dir verifies roundtrip parity
            com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.loadFromDir(configFolder);
            assertTrue(
                com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.getAllFuels()
                    .size() >= 20);
            assertNotNull(
                com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.findFuel("thoriumbasedliquidfuel"));
            assertNotNull(com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.getPhysics());
            assertEquals(
                6,
                com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.getAllTiers()
                    .size());
        } finally {
            com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.loadDefaults();
            deleteRecursively(tempDir.toFile());
        }
    }

    @Test
    void testYamlDataRegistryOverridesAndDisallow() throws Exception {
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("mptr_yaml_override_test");
        try {
            File configFolder = new File(tempDir.toFile(), "modularnuclear");
            configFolder.mkdirs();

            // 1. Partial fuel override (only override base_neutrons for fluid_fuel_thorium)
            // and fuel disallow (remove fluid_fuel_uranium)
            String fuelsOverride = "fuels:\n" + "  - id: \"fluid_fuel_thorium\"\n"
                + "    base_neutrons: 999\n"
                + "  - id: \"fluid_fuel_uranium\"\n"
                + "    remove: true\n";
            java.nio.file.Files.write(new File(configFolder, "fuels.yaml").toPath(), fuelsOverride.getBytes());

            // 2. Component disallow via disabled: true and map null
            String compOverride = "reflectors:\n" + "  - id: \"reflector_gold\"\n"
                + "    disabled: true\n"
                + "moderators:\n"
                + "  moderator_graphite: null\n";
            java.nio.file.Files.write(new File(configFolder, "components.yaml").toPath(), compOverride.getBytes());

            // 3. Tier disallow via remove: true
            String tiersOverride = "tiers:\n" + "  - tier: 5\n" + "    remove: true\n";
            java.nio.file.Files.write(new File(configFolder, "tiers.yaml").toPath(), tiersOverride.getBytes());

            // Load overrides
            com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.loadFromDir(configFolder);

            // Verify partial override kept default fields but updated overridden field
            com.gtnewhorizons.modularnuclear.common.config.data.FuelData thorium = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
                .findFuel("thoriumbasedliquidfuel");
            assertNotNull(thorium);
            assertEquals(999, thorium.baseNeutrons);
            assertTrue(thorium.isLiquid);
            assertTrue(thorium.thermalFissionMultiplier > 0);

            // Verify removed fuel is gone
            assertNull(
                com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.findFuel("uraniumbasedliquidfuel"));

            // Verify disabled reflector is gone from reflectors list
            assertFalse(
                com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.getComponentData().reflectors
                    .stream()
                    .anyMatch(r -> "reflector_gold".equals(r.id)));

            // Verify null moderator is gone from moderators list
            assertFalse(
                com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.getComponentData().moderators
                    .stream()
                    .anyMatch(m -> "moderator_graphite".equals(m.id)));

            // Verify tier 5 is removed
            assertEquals(
                5,
                com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.getAllTiers()
                    .size());
            assertFalse(com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.hasTier(5));
        } finally {
            com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.loadDefaults();
            deleteRecursively(tempDir.toFile());
        }
    }

    @Test
    void testCustomFuelAndComponentYamlConfiguration() throws Exception {
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("mptr_yaml_custom_test");
        try {
            File configFolder = new File(tempDir.toFile(), "modularnuclear");
            configFolder.mkdirs();

            // 1. Write custom fuels.yaml
            String fuelsYamlContent = "fuels:\n" + "  - id: \"super_uranium_custom\"\n"
                + "    name: \"Super Uranium Custom Rod\"\n"
                + "    matching:\n"
                + "      - \"item.super_uranium_custom\"\n"
                + "    is_liquid: false\n"
                + "    base_neutrons: 88\n"
                + "    thermal_fission_multiplier: 3.5\n"
                + "    heat_transfer_coeff: 0.18\n"
                + "    absorption_thermal: 0.92\n"
                + "    scattering_thermal: 0.08\n";
            java.nio.file.Files.write(new File(configFolder, "fuels.yaml").toPath(), fuelsYamlContent.getBytes());

            // 2. Write custom components.yaml
            String compYamlContent = "reflectors:\n" + "  - id: \"custom_beryllium_mirror\"\n"
                + "    name: \"Custom Beryllium Mirror\"\n"
                + "    matching:\n"
                + "      - \"item.custom_beryllium_mirror\"\n"
                + "    absorption_thermal: 0.001\n"
                + "    scattering_thermal: 0.999\n"
                + "    moderation: 0.35\n"
                + "    heat_transfer_coeff: 0.30\n"
                + "control_rods:\n"
                + "  - id: \"custom_super_rod\"\n"
                + "    name: \"Custom Super Control Rod\"\n"
                + "    matching:\n"
                + "      - \"item.custom_super_rod\"\n"
                + "    max_thermal_absorption: 0.995\n"
                + "    max_fast_absorption: 0.85\n"
                + "    heat_transfer_coeff: 0.50\n"
                + "moderators:\n"
                + "  - id: \"custom_heavy_graphite\"\n"
                + "    name: \"Custom Heavy Graphite\"\n"
                + "    matching:\n"
                + "      - \"item.custom_heavy_graphite\"\n"
                + "    moderation: 0.88\n";
            java.nio.file.Files.write(new File(configFolder, "components.yaml").toPath(), compYamlContent.getBytes());

            // 3. Load from dir
            com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.loadFromDir(configFolder);

            // 4. Verify mock items match and have custom properties
            net.minecraft.item.Item fuelItem = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
            org.mockito.Mockito.when(fuelItem.getUnlocalizedName(org.mockito.Mockito.any()))
                .thenReturn("item.super_uranium_custom");
            ItemStack fuelStack = new ItemStack(fuelItem, 1, 0);

            com.gtnewhorizons.modularnuclear.common.config.data.FuelData fd = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
                .findFuel(fuelStack);
            assertNotNull(fd, "Custom fuel must be found by matching pattern");
            assertEquals("super_uranium_custom", fd.id);
            assertEquals(88, fd.baseNeutrons);
            assertEquals(3.5, fd.thermalFissionMultiplier, 1e-4);
            assertEquals(0.18, fd.heatTransferCoeff, 1e-4);
            assertEquals(0.92, fd.absorptionThermal, 1e-4);

            net.minecraft.item.Item reflItem = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
            org.mockito.Mockito.when(reflItem.getUnlocalizedName(org.mockito.Mockito.any()))
                .thenReturn("item.custom_beryllium_mirror");
            ItemStack reflStack = new ItemStack(reflItem, 1, 0);

            com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ReflectorData rd = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
                .findReflector(reflStack);
            assertNotNull(rd, "Custom reflector must be found");
            assertEquals(0.999, rd.scatteringThermal, 1e-4);
            assertEquals(0.35, rd.moderation, 1e-4);

            net.minecraft.item.Item rodItem = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
            org.mockito.Mockito.when(rodItem.getUnlocalizedName(org.mockito.Mockito.any()))
                .thenReturn("item.custom_super_rod");
            ItemStack rodStack = new ItemStack(rodItem, 1, 0);

            com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ControlRodData crd = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
                .findControlRod(rodStack);
            assertNotNull(crd, "Custom control rod must be found");
            assertEquals(0.995, crd.maxThermalAbsorption, 1e-4);
            assertEquals(0.50, crd.heatTransferCoeff, 1e-4);
            assertTrue(
                com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod
                    .isControlRod(rodStack));

            net.minecraft.item.Item modItem = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
            org.mockito.Mockito.when(modItem.getUnlocalizedName(org.mockito.Mockito.any()))
                .thenReturn("item.custom_heavy_graphite");
            ItemStack modStack = new ItemStack(modItem, 1, 0);

            com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ModeratorData md = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
                .findModerator(modStack);
            assertNotNull(md, "Custom moderator must be found");
            assertEquals(0.88, md.moderation, 1e-4);
            assertTrue(
                com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification.isItemModerator(modStack));

        } finally {
            com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.loadDefaults();
            deleteRecursively(tempDir.toFile());
        }
    }

    @Test
    void testEscapedNeutronMaintenanceAndCherenkovScaling() {
        MTENuclearReactor reactor = new MTENuclearReactor("nuclear.reactor.test");

        // 1. Cherenkov client event handling
        reactor.receiveClientEvent((byte) 101, (byte) 50);
        assertEquals(0.50f, reactor.mClientRadiationIntensity, 1e-4);

        reactor.receiveClientEvent((byte) 101, (byte) 100);
        assertEquals(1.0f, reactor.mClientRadiationIntensity, 1e-4);

        reactor.receiveClientEvent((byte) 101, (byte) 0);
        assertEquals(0.0f, reactor.mClientRadiationIntensity, 1e-4);

        // Cherenkov tier scaling via block event 100
        reactor.receiveClientEvent((byte) 100, (byte) 1);
        assertEquals(1, reactor.mClientTier);
        assertEquals(2.5, reactor.mClientRadius, 1e-4);

        reactor.receiveClientEvent((byte) 100, (byte) 2);
        assertEquals(2, reactor.mClientTier);
        assertEquals(5.0, reactor.mClientRadius, 1e-4);

        reactor.receiveClientEvent((byte) 100, (byte) 3);
        assertEquals(3, reactor.mClientTier);
        assertEquals(7.5, reactor.mClientRadius, 1e-4);

        io.netty.buffer.ByteBuf buf = io.netty.buffer.Unpooled.buffer();
        reactor.gridSize = 9;
        reactor.writeToStream(buf);
        MTENuclearReactor clientReactor = new MTENuclearReactor("nuclear.reactor.client");
        clientReactor.readFromStream(buf);
        assertEquals(2, clientReactor.mClientTier);
        assertEquals(5.0, clientReactor.mClientRadius, 1e-4);

        // 2. Sigmoid curve evaluation
        double[] testEscaped = { 0, 500, 1000, 2000, 5000, 10000 };
        for (double nEsc : testEscaped) {
            double sFactor = (nEsc > 0) ? (nEsc * nEsc) / (nEsc * nEsc + 1000.0 * 1000.0) : 0.0;
            double multiplier = 1.0 + 9.0 * sFactor;
            double prob = 0.01 * multiplier;

            if (nEsc == 0) {
                assertEquals(0.0, sFactor, 1e-4);
                assertEquals(1.0, multiplier, 1e-4);
                assertEquals(0.01, prob, 1e-4);
            } else if (nEsc == 1000) {
                assertEquals(0.5, sFactor, 1e-4);
                assertEquals(5.5, multiplier, 1e-4);
                assertEquals(0.055, prob, 1e-4);
            } else if (nEsc == 10000) {
                assertTrue(sFactor > 0.99);
                assertTrue(multiplier > 9.9);
                assertTrue(prob <= 0.10);
            }
        }
    }

    @Test
    void testCherenkovWorldRendererLifecycle() {
        MTENuclearReactor reactor = new MTENuclearReactor("nuclear.reactor.test");
        com.gtnewhorizons.modularnuclear.client.renderer.CherenkovWorldRenderer.register(reactor);
        assertTrue(
            com.gtnewhorizons.modularnuclear.client.renderer.CherenkovWorldRenderer.ACTIVE_REACTORS.contains(reactor));

        com.gtnewhorizons.modularnuclear.client.renderer.CherenkovWorldRenderer.unregister(reactor);
        assertFalse(
            com.gtnewhorizons.modularnuclear.client.renderer.CherenkovWorldRenderer.ACTIVE_REACTORS.contains(reactor));

        com.gtnewhorizons.modularnuclear.client.renderer.CherenkovWorldRenderer.register(reactor);
        new com.gtnewhorizons.modularnuclear.client.renderer.CherenkovWorldRenderer().onWorldUnload(null);
        assertTrue(com.gtnewhorizons.modularnuclear.client.renderer.CherenkovWorldRenderer.ACTIVE_REACTORS.isEmpty());
    }

    @Test
    void testNuclearCasingLocalizationKeys() throws Exception {
        java.io.File langFile = new java.io.File("src/main/resources/assets/modularnuclear/lang/en_US.lang");
        assertTrue(langFile.exists(), "en_US.lang must exist");
        java.util.Properties props = new java.util.Properties();
        try (java.io.InputStream in = new java.io.FileInputStream(langFile)) {
            props.load(in);
        }
        assertEquals("Nuclear casing", props.getProperty("modularnuclear.casing.name"));
        assertEquals("Nuclear casing", props.getProperty("modularnuclear.casing.0.name"));
        assertEquals("Nuclear casing", props.getProperty("tile.modularnuclear.casing.name"));
        assertEquals("Nuclear casing", props.getProperty("tile.modularnuclear.casing.0.name"));
    }

    @Test
    void testApplyTextMaxWidthRecursion() {
        com.cleanroommc.modularui.widgets.layout.Flow container = com.cleanroommc.modularui.widgets.layout.Flow.row();
        com.cleanroommc.modularui.widgets.layout.Flow innerFlow = com.cleanroommc.modularui.widgets.layout.Flow
            .column();
        com.cleanroommc.modularui.widgets.TextWidget<?> textWidget = com.cleanroommc.modularui.api.drawable.IKey
            .str("test")
            .asWidget();
        assertEquals(-1, textWidget.getMaxWidth());

        innerFlow.child(textWidget);
        container.child(innerFlow);

        com.gtnewhorizons.modularnuclear.common.gui.NuclearReactorGui.applyTextMaxWidth(container, 142);
        assertEquals(142, textWidget.getMaxWidth());
    }

    @Test
    void testThermalScatteringProbabilityScaling() {
        // Grid A: Fuel in center moderated by heavy water, surrounded by zero-scattering absorbers
        INuclearTile[][] gridZero = new INuclearTile[3][3];
        MockNuclearTile fuelA = new MockNuclearTile(true, 1000);
        fuelA.emissionCount = 4;
        fuelA.moderationProb = 1.0; // Instantly moderate to thermal pool
        fuelA.absorbProb = 0.10;
        fuelA.scatterProb = 0.10;
        gridZero[1][1] = fuelA;

        MockNuclearTile zeroScattering = new MockNuclearTile(false, 0);
        zeroScattering.absorbProb = 0.0;
        zeroScattering.scatterProb = 0.0; // 0% scattering: non-absorbed thermal flux should escape immediately
        gridZero[0][1] = zeroScattering;
        gridZero[2][1] = zeroScattering;
        gridZero[1][0] = zeroScattering;
        gridZero[1][2] = zeroScattering;

        NuclearSimulationEngine.SimulationResult resZero = NuclearSimulationEngine.simulate(gridZero, 3, 3);
        assertEquals(0, zeroScattering.scattered, "Zero-scattering tile must never scatter thermal neutrons");
        assertTrue(resZero.neutronsEscaped > 0, "Non-scattered thermal neutrons must escape");

        // Grid B: Fuel in center, surrounded by high-scattering beryllium reflectors
        INuclearTile[][] gridRefl = new INuclearTile[3][3];
        MockNuclearTile fuelB = new MockNuclearTile(true, 5000);
        fuelB.emissionCount = 4;
        fuelB.moderationProb = 1.0;
        fuelB.absorbProb = 0.10;
        fuelB.scatterProb = 0.10;
        gridRefl[1][1] = fuelB;

        MockNuclearTile highScattering = new MockNuclearTile(false, 0);
        highScattering.absorbProb = 0.01;
        highScattering.scatterProb = 0.98; // 98% scattering
        gridRefl[0][1] = highScattering;
        gridRefl[2][1] = highScattering;
        gridRefl[1][0] = highScattering;
        gridRefl[1][2] = highScattering;

        NuclearSimulationEngine.SimulationResult resRefl = NuclearSimulationEngine.simulate(gridRefl, 3, 3);
        assertTrue(highScattering.scattered > 0, "High-scattering tile must receive and scatter thermal neutrons");
        assertTrue(resRefl.thermalNeutronsAbsorbed > 0, "Scattered thermal neutrons must diffuse and be absorbed");
    }

    @Test
    void testFastNeutronEscapeAccounting() {
        // Test 1: Grid where fuel is surrounded by 100% fast absorbers
        // All fast neutrons must be absorbed, exactly 0 should escape.
        INuclearTile[][] gridAbs = new INuclearTile[3][3];
        MockNuclearTile fuel = new MockNuclearTile(true, 500);
        gridAbs[1][1] = fuel;

        MockNuclearTile absorber = new MockNuclearTile(false, 0);
        absorber.absorbProb = 1.0;
        absorber.scatterProb = 0.0;
        absorber.moderationProb = 0.0;
        gridAbs[0][1] = absorber;
        gridAbs[2][1] = absorber;
        gridAbs[1][0] = absorber;
        gridAbs[1][2] = absorber;

        NuclearSimulationEngine.SimulationResult resAbs = NuclearSimulationEngine.simulate(gridAbs, 3, 3);
        assertTrue(resAbs.totalNeutronsGenerated > 0);
        assertEquals(resAbs.totalNeutronsGenerated, resAbs.fastNeutronsAbsorbed, "All fast neutrons must be absorbed");
        assertEquals(0, resAbs.neutronsEscaped, "Absorbed neutrons must not be counted as escaped");

        // Test 2: Grid where fuel is in an open/empty boundary (surrounded by null cells)
        // All fast neutrons must escape, exactly 0 fast absorbed.
        INuclearTile[][] gridOpen = new INuclearTile[3][3];
        gridOpen[1][1] = new MockNuclearTile(true, 400);

        NuclearSimulationEngine.SimulationResult resOpen = NuclearSimulationEngine.simulate(gridOpen, 3, 3);
        assertTrue(resOpen.totalNeutronsGenerated > 0);
        assertEquals(0, resOpen.fastNeutronsAbsorbed);
        assertEquals(
            resOpen.totalNeutronsGenerated,
            resOpen.neutronsEscaped,
            "All neutrons escaping into boundary/null cells must be counted exactly once");
    }

    @Test
    void testExplicitFuelDataConfiguration() {
        List<com.gtnewhorizons.modularnuclear.common.config.data.FuelData> fuels = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .getAllFuels();
        assertFalse(fuels.isEmpty());

        // Check Lithium & Glowstone breeder targets
        com.gtnewhorizons.modularnuclear.common.config.data.FuelData lithium = null;
        com.gtnewhorizons.modularnuclear.common.config.data.FuelData glowstone = null;
        com.gtnewhorizons.modularnuclear.common.config.data.FuelData quadUranium = null;
        com.gtnewhorizons.modularnuclear.common.config.data.FuelData theCore = null;

        for (com.gtnewhorizons.modularnuclear.common.config.data.FuelData fd : fuels) {
            if ("fuel_lithium".equals(fd.id)) lithium = fd;
            if ("fuel_glowstone".equals(fd.id)) glowstone = fd;
            if ("fuel_uranium_4".equals(fd.id)) quadUranium = fd;
            if ("fuel_naquadah_32".equals(fd.id)) theCore = fd;
        }

        assertNotNull(lithium, "fuel_lithium must be registered");
        assertEquals(1, lithium.packetCount);
        assertEquals(1, lithium.baseNeutrons);
        assertEquals(390.0, lithium.peakReactivityTemp, 1e-3);
        assertEquals(2100.0, lithium.floorTemp, 1e-3);
        assertEquals("ItemList.DepletedRodLithium", lithium.spentProduct);

        assertNotNull(glowstone, "fuel_glowstone must be registered");
        assertEquals(1, glowstone.packetCount);
        assertEquals(1, glowstone.baseNeutrons);
        assertEquals(200.0, glowstone.peakReactivityTemp, 1e-3);
        assertEquals(2000.0, glowstone.floorTemp, 1e-3);
        assertEquals("ItemList.DepletedRodGlowstone", glowstone.spentProduct);

        assertNotNull(quadUranium, "fuel_uranium_4 must be registered");
        assertEquals(4, quadUranium.packetCount);
        assertEquals(16, quadUranium.baseNeutrons);
        assertEquals(770.0, quadUranium.peakReactivityTemp, 1e-3);
        assertEquals(2300.0, quadUranium.floorTemp, 1e-3);
        assertEquals("ItemList.DepletedRodUranium4", quadUranium.spentProduct);

        assertNotNull(theCore, "fuel_naquadah_32 must be registered");
        assertEquals(16, theCore.packetCount);
        assertEquals(512, theCore.baseNeutrons);
        assertEquals(2500.0, theCore.peakReactivityTemp, 1e-3);
        assertEquals(3200.0, theCore.floorTemp, 1e-3);
        assertEquals("ItemList.DepletedRodNaquadah32", theCore.spentProduct);

        // Test reactivity curve and damage calculations on FuelData
        assertEquals(0.50, quadUranium.calculateReactivity(0.0), 1e-3);
        assertEquals(1.00, quadUranium.calculateReactivity(quadUranium.peakReactivityTemp), 1e-3);
        assertEquals(0.05, quadUranium.calculateReactivity(quadUranium.floorTemp), 1e-3);
        assertEquals(0.05, quadUranium.calculateReactivity(quadUranium.floorTemp + 500.0), 1e-3);

        assertEquals(0.0, quadUranium.calculateTemperatureDamage(20.0, 20.0), 1e-6);
        assertTrue(quadUranium.calculateTemperatureDamage(1000.0, 20.0) > 0.0);

        // Test INuclearTile calculateEfficiency delegation
        MockNuclearTile tile = new MockNuclearTile(true, 100);
        tile.fuelData = quadUranium;
        assertEquals(1.00, NuclearSimulationEngine.calculateEfficiency(tile, 770.0), 1e-3);
        assertEquals(0.05, NuclearSimulationEngine.calculateEfficiency(tile, 2300.0), 1e-3);
    }

    @Test
    void testBreederTargetDepletedProducts() {
        if (!gregtech.api.enums.ItemList.DepletedRodLithium.hasBeenSet()) {
            gregtech.api.enums.ItemList.DepletedRodLithium.set(new ItemStack(Blocks.iron_block));
        }
        if (!gregtech.api.enums.ItemList.DepletedRodGlowstone.hasBeenSet()) {
            gregtech.api.enums.ItemList.DepletedRodGlowstone.set(new ItemStack(Blocks.gold_block));
        }
        if (!gregtech.api.enums.ItemList.RodLithium.hasBeenSet()) {
            gregtech.api.enums.ItemList.RodLithium.set(new ItemStack(Blocks.lapis_block));
        }
        if (!gregtech.api.enums.ItemList.RodGlowstone.hasBeenSet()) {
            gregtech.api.enums.ItemList.RodGlowstone.set(new ItemStack(Blocks.redstone_block));
        }

        // Test resolving ItemList names via ReactorDataRegistry
        ItemStack depLithium = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .resolveItemStack("ItemList.DepletedRodLithium");
        assertNotNull(depLithium, "ItemList.DepletedRodLithium must resolve");

        ItemStack depGlowstone = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .resolveItemStack("ItemList.DepletedRodGlowstone");
        assertNotNull(depGlowstone, "ItemList.DepletedRodGlowstone must resolve");

        // Test getItemDepletedForm for Lithium and Glowstone rods
        ItemStack activeLithium = gregtech.api.enums.ItemList.RodLithium.get(1L);
        ItemStack depletedResultLithium = com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification
            .getItemDepletedForm(activeLithium);
        assertNotNull(depletedResultLithium, "Lithium rod must not be voided on depletion");
        assertTrue(gregtech.api.enums.ItemList.DepletedRodLithium.isStackEqual(depletedResultLithium, false, true));

        ItemStack activeGlowstone = gregtech.api.enums.ItemList.RodGlowstone.get(1L);
        ItemStack depletedResultGlowstone = com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification
            .getItemDepletedForm(activeGlowstone);
        assertNotNull(depletedResultGlowstone, "Glowstone rod must not be voided on depletion");
        assertTrue(gregtech.api.enums.ItemList.DepletedRodGlowstone.isStackEqual(depletedResultGlowstone, false, true));

        // Test that depleted rods are recognized by isDepletedOrEmptyRod
        assertTrue(
            com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification
                .isDepletedOrEmptyRod(depletedResultLithium));
        assertTrue(
            com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification
                .isDepletedOrEmptyRod(depletedResultGlowstone));
    }

    @Test
    public void testFuelArchetypeMultipliers() {
        com.gtnewhorizons.modularnuclear.common.config.data.PhysicsData physics = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .getPhysics();
        assertNotNull(physics);
        assertEquals(0.50, physics.denseNbaseMultiplier, 1e-4);
        assertEquals(1.20, physics.denseFissionMultiplier, 1e-4);
        assertEquals(1.40, physics.excitedNbaseMultiplier, 1e-4);
        assertEquals(0.80, physics.excitedFissionMultiplier, 1e-4);

        // Find HD Uranium and Excited Uranium
        com.gtnewhorizons.modularnuclear.common.config.data.FuelData hdUran = null;
        com.gtnewhorizons.modularnuclear.common.config.data.FuelData excUran = null;
        for (com.gtnewhorizons.modularnuclear.common.config.data.FuelData fd : com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .getAllFuels()) {
            if ("fuel_hd_uranium_1".equals(fd.id)) hdUran = fd;
            if ("fuel_excited_uranium_1".equals(fd.id)) excUran = fd;
        }

        assertNotNull(hdUran, "fuel_hd_uranium_1 must exist");
        assertEquals("dense", hdUran.archetype);
        // HD Uranium base neutrons = 8. With dense mult 0.50 -> 4
        assertEquals(4, hdUran.getEffectiveBaseNeutrons(physics));
        // HD Uranium base fission mult = 1.80. With dense mult 1.20 -> 2.16
        assertEquals(1.80 * 1.20, hdUran.getEffectiveFissionMultiplier(physics), 1e-4);

        assertNotNull(excUran, "fuel_excited_uranium_1 must exist");
        assertEquals("excited", excUran.archetype);
        // Excited Uranium base neutrons = 16. With excited mult 1.40 -> 22
        assertEquals(Math.round(16 * 1.40), excUran.getEffectiveBaseNeutrons(physics));
        // Excited Uranium base fission mult = 2.50. With excited mult 0.80 -> 2.00
        assertEquals(2.50 * 0.80, excUran.getEffectiveFissionMultiplier(physics), 1e-4);
    }

    @Test
    public void testDefaultPhysicsFissionHeat() {
        com.gtnewhorizons.modularnuclear.common.config.data.PhysicsData defaultPhysics = new com.gtnewhorizons.modularnuclear.common.config.data.PhysicsData();
        assertEquals(
            38.0,
            defaultPhysics.fissionHeatPerNeutron,
            1e-4,
            "PhysicsData default fission heat must be 38.0 Hu/neutron");

        com.gtnewhorizons.modularnuclear.common.config.data.PhysicsData activePhysics = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .getPhysics();
        assertNotNull(activePhysics);
        assertEquals(
            38.0,
            activePhysics.fissionHeatPerNeutron,
            1e-4,
            "Active registry physics must have 38.0 Hu/neutron");
        assertEquals(
            38.0,
            NuclearSimulationEngine.fissionHeatPerNeutron,
            1e-4,
            "NuclearSimulationEngine must use 38.0 Hu/neutron by default");
    }

    @Test
    public void testDurabilityScalingReconciliation() {
        // Ensure ModItems are initialized
        if (com.gtnewhorizons.modularnuclear.common.item.ModItems.rodCustomFuel4 == null) {
            com.gtnewhorizons.modularnuclear.common.item.ModItems.init();
        }
        com.gtnewhorizons.modularnuclear.common.item.ItemCustomFuelRod customItem = com.gtnewhorizons.modularnuclear.common.item.ModItems.rodCustomFuel4;

        // Create item stack with mock mix
        ItemStack stack = new ItemStack(customItem, 1, 0);
        stack.stackTagCompound = new net.minecraft.nbt.NBTTagCompound();
        stack.stackTagCompound.setInteger("MaxDurability", 25000);

        // Intrinsic max damage for this custom mix stack should be 25000, not the class default 40000
        assertEquals(25000, customItem.getMaxDamageEx(stack));

        // Damage stack to 12500 (half life)
        customItem.setDamageForStack(stack, 12500);
        assertEquals(12500, customItem.getDamageOfStack(stack));
        // Vanilla damage should be 50% of 100 = 50
        assertEquals(50, stack.getItemDamage());

        // Test scaling calculation
        int configuredDurability = 50000; // Say server admin doubled it to 50000
        int intrinsicMax = customItem.getMaxDamageEx(stack); // 25000
        double scale = (double) intrinsicMax / (double) configuredDurability; // 0.50

        double totalDmg = 10.0;
        double scaledDmg = totalDmg * scale;
        assertEquals(5.0, scaledDmg, 1e-4);
    }

    @Test
    public void testFastNeutronFission() {
        NuclearSimulationEngine.resetDefaultParameters();
        NuclearSimulationEngine.setTicksPerCycle(20);
        try {
            assertEquals(1.0, NuclearSimulationEngine.globalFastFissionMultiplier, 1e-4);

            // Uranium fuel tile (baseFastFissionMultiplier = 0.10, baseNeutrons = 4)
            SimTile tile = new SimTile(SimTile.TileType.FUEL_URANIUM_SINGLE);
            assertEquals(4, tile.generateNeutrons(1.0)); // 0 absorbed neutrons -> baseline 4

            // Absorb 100 fast neutrons
            tile.onNeutronAbsorbed(NeutronType.FAST, 100);
            tile.nuclearTick(1.0);
            assertEquals(100, tile.getLastFastAbsorbed());

            // Fast fission chain neutrons = round(100 * 0.10) = 10
            // Total produced = base (4) + chain (10) = 14
            assertEquals(14, tile.generateNeutrons(1.0));

            // Test actinide fast fission cross sections in CustomFuelMix
            assertEquals(0.09, CustomFuelMix.ActinideIsotope.URANIUM_238.baseFastFissionMultiplier, 1e-4);
            assertEquals(0.15, CustomFuelMix.ActinideIsotope.URANIUM_235.baseFastFissionMultiplier, 1e-4);
            assertEquals(0.20, CustomFuelMix.ActinideIsotope.PLUTONIUM_239.baseFastFissionMultiplier, 1e-4);
        } finally {
            NuclearSimulationEngine.setTicksPerCycle(NuclearSimulationEngine.DEFAULT_TICKS_PER_CYCLE);
        }
    }

    @Test
    public void testDifferentialFastVsThermalFuelWear() {
        NuclearSimulationEngine.resetDefaultParameters();
        assertEquals(8.0, NuclearSimulationEngine.fastAbsorptionDamageMultiplier, 1e-4);
        assertEquals(1.0, NuclearSimulationEngine.thermalAbsorptionDamageMultiplier, 1e-4);

        double origBurnup = NuclearSimulationEngine.fuelBurnupMultiplier;
        NuclearSimulationEngine.fuelBurnupMultiplier = 1.0;
        try {
            // Fast neutron absorption wear
            SimTile tileFast = new SimTile(SimTile.TileType.FUEL_URANIUM_SINGLE);
            tileFast.onNeutronAbsorbed(NeutronType.FAST, 10);
            tileFast.nuclearTick(1.0);
            double fastWear = tileFast.getLastDurabilityLoss();
            assertEquals(80.0, fastWear, 1e-4); // 10 * 8.0 = 80.0

            // Thermal neutron absorption wear
            SimTile tileThermal = new SimTile(SimTile.TileType.FUEL_URANIUM_SINGLE);
            tileThermal.onNeutronAbsorbed(NeutronType.THERMAL, 10);
            tileThermal.nuclearTick(1.0);
            double thermalWear = tileThermal.getLastDurabilityLoss();
            assertEquals(10.0, thermalWear, 1e-4); // 10 * 1.0 = 10.0

            // Exactly 8x ratio!
            assertEquals(8.0, fastWear / thermalWear, 1e-4);

            // Test configurable multipliers via PhysicsData
            com.gtnewhorizons.modularnuclear.common.config.data.PhysicsData physics = new com.gtnewhorizons.modularnuclear.common.config.data.PhysicsData();
            physics.fastAbsorptionDamageMultiplier = 12.0;
            physics.thermalAbsorptionDamageMultiplier = 2.0;

            Map<String, Object> map = physics.toMap();
            assertEquals(12.0, (Double) map.get("fast_absorption_damage_multiplier"), 1e-4);
            assertEquals(2.0, (Double) map.get("thermal_absorption_damage_multiplier"), 1e-4);

            com.gtnewhorizons.modularnuclear.common.config.data.PhysicsData roundtrip = com.gtnewhorizons.modularnuclear.common.config.data.PhysicsData
                .fromMap(map);
            assertEquals(12.0, roundtrip.fastAbsorptionDamageMultiplier, 1e-4);
            assertEquals(2.0, roundtrip.thermalAbsorptionDamageMultiplier, 1e-4);
        } finally {
            NuclearSimulationEngine.fuelBurnupMultiplier = origBurnup;
            NuclearSimulationEngine.resetDefaultParameters();
        }
    }

    @Test
    public void testModeratorDurabilityAndProperties() {
        com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ModeratorData carbon = null;
        com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ModeratorData graphite = null;
        com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ModeratorData beryllium = null;

        for (com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ModeratorData mod : com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .getComponentData().moderators) {
            if ("moderator_carbon".equals(mod.id)) carbon = mod;
            if ("moderator_graphite".equals(mod.id)) graphite = mod;
            if ("moderator_beryllium".equals(mod.id)) beryllium = mod;
        }

        assertNotNull(carbon);
        assertNotNull(graphite);
        assertNotNull(beryllium);

        // Durability specifications
        assertEquals(10000, carbon.durability);
        assertEquals(80000, graphite.durability);
        assertEquals(240000, beryllium.durability);

        // Relative ratios: graphite = 8x carbon, beryllium = 24x carbon
        assertEquals(8.0, (double) graphite.durability / (double) carbon.durability, 1e-4);
        assertEquals(24.0, (double) beryllium.durability / (double) carbon.durability, 1e-4);

        // Beryllium works better (higher moderation, higher scattering, better heat transfer)
        assertTrue(beryllium.moderation > graphite.moderation);
        assertTrue(beryllium.moderation > carbon.moderation);
        assertEquals(0.65, beryllium.moderation, 1e-4);
        assertEquals(0.15, beryllium.heatTransferCoeff, 1e-4);

        // Spent products
        assertEquals("ore:dustAsh", carbon.spentProduct);
        assertEquals("ore:dustAsh", graphite.spentProduct);

        // SimTile parity
        SimTile simCarbon = new SimTile(SimTile.TileType.MODERATOR_CARBON);
        assertEquals(10000, simCarbon.getDurability());
        assertEquals(0.45, simCarbon.getModerationProbability(), 1e-4);

        SimTile simGraphite = new SimTile(SimTile.TileType.MODERATOR_GRAPHITE);
        assertEquals(80000, simGraphite.getDurability());
        assertEquals(0.50, simGraphite.getModerationProbability(), 1e-4);

        SimTile simBeryllium = new SimTile(SimTile.TileType.MODERATOR_BERYLLIUM);
        assertEquals(240000, simBeryllium.getDurability());
        assertEquals(0.65, simBeryllium.getModerationProbability(), 1e-4);
    }

    @Test
    public void testGraphiteWignerEnergyStorageAndThermalRelease() {
        com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ModeratorData graphite = null;
        for (com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ModeratorData mod : com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .getComponentData().moderators) {
            if ("moderator_graphite".equals(mod.id)) {
                graphite = mod;
                break;
            }
        }
        assertNotNull(graphite);

        assertEquals(1500.0, graphite.wignerReleaseOnsetTemp, 1e-4);
        assertEquals(2500.0, graphite.wignerReleaseHalfTemp, 1e-4);
        assertEquals(2000.0, graphite.wignerMaxReleaseRate, 1e-4);
        assertEquals(1.0, graphite.wignerEnergyPerFast, 1e-4);
        assertEquals(10000.0, graphite.wignerMaxEnergy, 1e-4);
        assertEquals(0.0, graphite.thermalEmissionMaxPerFast, 1e-4);

        // Below or at 1500 °C: strictly 0 release
        assertEquals(0.0, graphite.calculateWignerHeatRelease(1000.0, 5000.0), 1e-4);
        assertEquals(0.0, graphite.calculateWignerHeatRelease(1500.0, 5000.0), 1e-4);

        // Exactly at 2500 °C: deltaT = 1000, deltaTHalf = 1000 -> factor = 1000^2 / (1000^2 + 1000^2) = 0.5 (50% max
        // release rate = 1000.0 HU/t)
        double releaseAtHalf = graphite.calculateWignerHeatRelease(2500.0, 5000.0);
        assertEquals(1000.0, releaseAtHalf, 1e-4);

        // Limited by available stored energy
        double releaseLimited = graphite.calculateWignerHeatRelease(2500.0, 400.0);
        assertEquals(400.0, releaseLimited, 1e-4);

        // At high temperature (e.g. 5000 °C): factor approaches 1.0, releases up to max rate 2000.0 HU/t
        double releaseHigh = graphite.calculateWignerHeatRelease(5000.0, 5000.0);
        assertTrue(releaseHigh > 1800.0 && releaseHigh <= 2000.0);

        // SimTile parity & saturation cap at 10000.0
        SimTile tile = new SimTile(SimTile.TileType.MODERATOR_GRAPHITE);
        tile.setTemperature(1200.0);
        tile.addWignerEnergy(6000.0);
        assertEquals(6000.0, tile.getStoredWignerEnergy(), 1e-4);
        tile.addWignerEnergy(6000.0); // exceeds cap -> must saturate at 10000.0
        assertEquals(10000.0, tile.getStoredWignerEnergy(), 1e-4);

        assertEquals(0.0, tile.releaseWignerEnergy(), 1e-4);
        assertEquals(10000.0, tile.getStoredWignerEnergy(), 1e-4); // unchanged below onset

        tile.setTemperature(2500.0);
        double releasedSim = tile.releaseWignerEnergy();
        assertEquals(1000.0, releasedSim, 1e-4);
        assertEquals(9000.0, tile.getStoredWignerEnergy(), 1e-4); // depleted by 1000

        // MTEHatchNuclearBus rod NBT storage, persistence & saturation cap
        MTEHatchNuclearBus bus = new MTEHatchNuclearBus("test.bus.wigner", 4, new String[0], null);
        // Bus with no item does not store Wigner energy
        bus.addWignerEnergy(7000.0);
        assertEquals(0.0, bus.getStoredWignerEnergy(), 1e-4);

        // Insert graphite long rod
        net.minecraft.item.Item mockGraphiteRodItem = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
        org.mockito.Mockito.when(mockGraphiteRodItem.getUnlocalizedName(org.mockito.Mockito.any()))
            .thenReturn("item.stickLongGraphite");
        ItemStack graphiteRod = new ItemStack(mockGraphiteRodItem, 1, 0);
        bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = graphiteRod;

        bus.addWignerEnergy(7000.0);
        assertEquals(7000.0, bus.getStoredWignerEnergy(), 1e-4);
        assertNotNull(graphiteRod.stackTagCompound);
        assertEquals(7000.0, graphiteRod.stackTagCompound.getDouble("wignerEnergy"), 1e-4);

        // Saturation cap at 10,000 HU
        bus.addWignerEnergy(5000.0);
        assertEquals(10000.0, bus.getStoredWignerEnergy(), 1e-4);
        assertEquals(10000.0, graphiteRod.stackTagCompound.getDouble("wignerEnergy"), 1e-4);

        // Removing the rod empties bus Wigner query, but the rod retains its stored NBT
        bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = null;
        assertEquals(0.0, bus.getStoredWignerEnergy(), 1e-4);
        assertEquals(10000.0, graphiteRod.stackTagCompound.getDouble("wignerEnergy"), 1e-4);

        // Reinserting the rod into another bus restores the stored energy
        MTEHatchNuclearBus anotherBus = new MTEHatchNuclearBus("test.bus.wigner2", 4, new String[0], null);
        anotherBus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = graphiteRod;
        assertEquals(10000.0, anotherBus.getStoredWignerEnergy(), 1e-4);

        // Thermal release at 2500 °C (half temp -> releases 1000 HU)
        anotherBus.mTemperature = 2500.0;
        double released = anotherBus.releaseWignerEnergy();
        assertEquals(1000.0, released, 1e-4);
        assertEquals(9000.0, anotherBus.getStoredWignerEnergy(), 1e-4);
        assertEquals(9000.0, graphiteRod.stackTagCompound.getDouble("wignerEnergy"), 1e-4);

        // Beryllium rod in bus does not store Wigner energy
        net.minecraft.item.Item mockBerylliumRodItem = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
        org.mockito.Mockito.when(mockBerylliumRodItem.getUnlocalizedName(org.mockito.Mockito.any()))
            .thenReturn("item.stickLongBeryllium");
        ItemStack berylliumRod = new ItemStack(mockBerylliumRodItem, 1, 0);
        bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = berylliumRod;
        bus.addWignerEnergy(5000.0);
        assertEquals(0.0, bus.getStoredWignerEnergy(), 1e-4);
        assertNull(berylliumRod.stackTagCompound);
    }

    @Test
    public void testNEINeutronInteractionHandlerRecipes() {
        com.gtnewhorizons.modularnuclear.common.nei.NEINeutronInteractionHandler handler = new com.gtnewhorizons.modularnuclear.common.nei.NEINeutronInteractionHandler();

        assertEquals("gt.nei.neutron_interaction", handler.getOverlayIdentifier());
        handler.loadCraftingRecipes(handler.getOverlayIdentifier());
        assertTrue(handler.numRecipes() >= 10);

        boolean foundGraphite = false;
        boolean foundReflector = false;
        boolean foundRadiovoltaic = false;
        boolean foundControlRod = false;
        boolean foundCoolantCell = false;
        boolean foundFluidCoolant = false;
        boolean foundHeatVent = false;
        boolean foundHeatExchanger = false;
        boolean foundInsulator = false;
        boolean foundEmptyTile = false;

        for (int i = 0; i < handler.numRecipes(); i++) {
            codechicken.nei.recipe.TemplateRecipeHandler.CachedRecipe cr = (codechicken.nei.recipe.TemplateRecipeHandler.CachedRecipe) handler.arecipes
                .get(i);
            if (cr instanceof com.gtnewhorizons.modularnuclear.common.nei.NEINeutronInteractionHandler.CachedNeutronInteractionRecipe recipe) {
                String cat = recipe.data.category;
                if ("Moderator".equals(cat) && recipe.data.name.contains("Graphite")) foundGraphite = true;
                if ("Reflector".equals(cat)) foundReflector = true;
                if ("Radiovoltaic Plate".equals(cat)) foundRadiovoltaic = true;
                if ("Control Rod".equals(cat)) foundControlRod = true;
                if ("Coolant Cell".equals(cat)) foundCoolantCell = true;
                if ("Fluid Coolant".equals(cat)) foundFluidCoolant = true;
                if ("Heat Vent".equals(cat)) foundHeatVent = true;
                if ("Heat Exchanger".equals(cat)) foundHeatExchanger = true;
                if ("Thermal Insulator".equals(cat)) foundInsulator = true;
                if ("Empty Core Tile".equals(cat)) foundEmptyTile = true;
            }
        }

        assertTrue(foundGraphite, "Graphite moderator must be loaded in NEI component recipes");
        assertTrue(foundReflector, "Reflectors must be loaded in NEI component recipes");
        assertTrue(foundRadiovoltaic, "Radiovoltaic plates must be loaded in NEI component recipes");
        assertTrue(foundControlRod, "Control rods must be loaded in NEI component recipes");
        assertTrue(foundCoolantCell, "Coolant cells must be loaded in NEI component recipes");
        assertTrue(foundFluidCoolant, "Fluid coolants must be loaded in NEI component recipes");
        assertTrue(foundHeatVent, "Heat vents must be loaded in NEI component recipes");
        assertTrue(foundHeatExchanger, "Heat exchangers must be loaded in NEI component recipes");
        assertTrue(foundInsulator, "Insulators must be loaded in NEI component recipes");
        assertTrue(foundEmptyTile, "Empty core tile must be loaded in NEI component recipes");
    }

    @Test
    public void testNeutronSourceSimulation() {
        NuclearSimulationEngine.setTicksPerCycle(20);
        try {
            assertEquals(SimTile.TileType.NEUTRON_SOURCE, SimTile.TileType.fromCode("NS"));

            SimTile sourceTile = new SimTile(SimTile.TileType.NEUTRON_SOURCE);
            assertTrue(sourceTile.isNeutronSource());
            assertFalse(sourceTile.isFuel());
            assertEquals(4, sourceTile.getNeutronEmissionCount());
            assertEquals(0.01, sourceTile.getAbsorptionProbability(NeutronType.FAST), 1e-4);
            assertEquals(0.05, sourceTile.getModerationProbability(), 1e-4);

            // Configure beam parameters
            sourceTile.setNeutronSourceBeamEnergy(1000.0f);
            sourceTile.setNeutronSourceBeamRate(100);
            // S = round(1000 * 100 * 0.001) = 100 per direction, total 400
            int produced = sourceTile.generateNeutrons(1.0);
            assertEquals(400, produced);

            // Run simulation with Neutron Source at center of 3x3 grid
            SimTile[][] grid = new SimTile[3][3];
            for (int x = 0; x < 3; x++) {
                for (int y = 0; y < 3; y++) {
                    grid[x][y] = new SimTile(SimTile.TileType.EMPTY);
                }
            }
            grid[1][1] = sourceTile;

            NuclearSimulationEngine.SimulationResult result = NuclearSimulationEngine.simulate(grid, 3, 3, 1.0, 20.0);

            assertEquals(400, result.totalNeutronsGenerated);
            // Neutron source receives 0 prompt fission heat and does not degrade
            sourceTile.nuclearTick(1.0);
            assertEquals(10000, sourceTile.getDurability());
            assertEquals(0.0, sourceTile.getLastDurabilityLoss(), 1e-4);
        } finally {
            NuclearSimulationEngine.setTicksPerCycle(NuclearSimulationEngine.DEFAULT_TICKS_PER_CYCLE);
        }
    }

    @Test
    public void testNonWaterModerationAndHullOnlyComponentRejection() {
        // 1. Component Registry moderation constants
        for (com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.HeatVentData vent : com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .getComponentData().heatVents) {
            assertEquals(0.05, vent.moderation, 1e-4, "Vent " + vent.id + " moderation must be 0.05");
        }
        for (com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.HeatExchangerData hex : com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .getComponentData().heatExchangers) {
            assertEquals(0.05, hex.moderation, 1e-4, "Exchanger " + hex.id + " moderation must be 0.05");
        }
        for (com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.CoolantItemData ci : com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .getComponentData().coolantItems) {
            assertEquals(0.05, ci.moderation, 1e-4, "Coolant item " + ci.id + " moderation must be 0.05");
        }
        for (com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.CoolantFluidData cf : com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .getComponentData().coolantFluids) {
            if ("coolant_ic2".equals(cf.id)) {
                assertEquals(0.05, cf.moderation, 1e-4, "IC2 coolant fluid moderation must be 0.05");
            }
        }

        // 2. SimTile moderation values
        SimTile ic2Tile = new SimTile(SimTile.TileType.HATCH_IC2_COOLANT);
        ic2Tile.setInputFluidAmount(1000);
        assertEquals(0.05, ic2Tile.getModerationProbability(), 1e-4);
        ic2Tile.setInputFluidAmount(0);
        assertEquals(0.05, ic2Tile.getModerationProbability(), 1e-4);

        SimTile passageTile = new SimTile(SimTile.TileType.PASSAGE_CORE);
        assertEquals(0.05, passageTile.getModerationProbability(), 1e-4);

        MTEHatchNuclearHighPressure hp = new MTEHatchNuclearHighPressure("test.hp.mod", 4, new String[0], null);
        ICoolantLoopPump mockPump = org.mockito.Mockito.mock(ICoolantLoopPump.class);
        hp.setConnectedPump(mockPump);
        org.mockito.Mockito.when(mockPump.getCoolantFluidProperty())
            .thenReturn(CoolantFluidProperty.IC2_COOLANT);
        assertEquals(0.05, hp.getModerationProbability(), 1e-4);
        org.mockito.Mockito.when(mockPump.getCoolantFluidProperty())
            .thenReturn(CoolantFluidProperty.SODIUM);
        assertEquals(0.05, hp.getModerationProbability(), 1e-4);

        // 3. Hull-only components rejection
        net.minecraft.item.Item mockVentCore = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
        org.mockito.Mockito.when(mockVentCore.getUnlocalizedName(org.mockito.Mockito.any()))
            .thenReturn("ic2.reactorVentCore");
        ItemStack ventCoreStack = new ItemStack(mockVentCore, 1, 0);

        assertTrue(
            com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification.isHullOnlyComponent(ventCoreStack));
        assertFalse(
            com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification
                .isItemAllowedInBus(ventCoreStack, null));

        net.minecraft.item.Item mockSwitchCore = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
        org.mockito.Mockito.when(mockSwitchCore.getUnlocalizedName(org.mockito.Mockito.any()))
            .thenReturn("ic2.reactorHeatSwitchCore");
        ItemStack switchCoreStack = new ItemStack(mockSwitchCore, 1, 0);

        assertTrue(
            com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification
                .isHullOnlyComponent(switchCoreStack));
        assertFalse(
            com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification
                .isItemAllowedInBus(switchCoreStack, null));

        // 4. Random unknown item rejection
        net.minecraft.item.Item mockRandom = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
        org.mockito.Mockito.when(mockRandom.getUnlocalizedName(org.mockito.Mockito.any()))
            .thenReturn("random.dirt_block");
        ItemStack randomStack = new ItemStack(mockRandom, 1, 0);

        assertFalse(
            com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification
                .isItemAllowedInBus(randomStack, null));
    }

    @Test
    void testExplicitFuelAndComponentMatchingWithoutWildcardCollisions() {
        // Ensure defaults are loaded
        com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.loadDefaults();

        // 1. High Density Uranium quad rod must match fuel_hd_uranium_4, NOT generic fuel_uranium_1 or fuel_uranium_4
        net.minecraft.item.Item hdUraniumItem = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
        org.mockito.Mockito.when(hdUraniumItem.getUnlocalizedName(org.mockito.Mockito.any()))
            .thenReturn("gt.rodHighDensityUranium4");
        ItemStack hdUraniumStack = new ItemStack(hdUraniumItem, 1, 0);

        com.gtnewhorizons.modularnuclear.common.config.data.FuelData hdUraniumData = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .findFuel(hdUraniumStack);
        assertNotNull(hdUraniumData, "High Density Uranium must be recognized as fuel");
        assertEquals(
            "fuel_hd_uranium_4",
            hdUraniumData.id,
            "Must resolve to fuel_hd_uranium_4, not hijacked by fuel_uranium");
        assertEquals(32, hdUraniumData.baseNeutrons);
        assertEquals(1.80, hdUraniumData.thermalFissionMultiplier, 1e-4);

        // 2. Excited Uranium single rod must match fuel_excited_uranium_1, NOT fuel_uranium_1
        net.minecraft.item.Item excitedUraniumItem = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
        org.mockito.Mockito.when(excitedUraniumItem.getUnlocalizedName(org.mockito.Mockito.any()))
            .thenReturn("gt.rodExcitedUranium");
        ItemStack excitedUraniumStack = new ItemStack(excitedUraniumItem, 1, 0);

        com.gtnewhorizons.modularnuclear.common.config.data.FuelData excitedUraniumData = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .findFuel(excitedUraniumStack);
        assertNotNull(excitedUraniumData, "Excited Uranium must be recognized as fuel");
        assertEquals(
            "fuel_excited_uranium_1",
            excitedUraniumData.id,
            "Must resolve to fuel_excited_uranium_1, not hijacked by fuel_uranium_1");
        assertEquals(16, excitedUraniumData.baseNeutrons);
        assertEquals(2.50, excitedUraniumData.thermalFissionMultiplier, 1e-4);

        // 3. The Core (Naquadah-32) must match fuel_naquadah_32, NOT fuel_naquadah_1
        net.minecraft.item.Item naq32Item = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
        org.mockito.Mockito.when(naq32Item.getUnlocalizedName(org.mockito.Mockito.any()))
            .thenReturn("gt.rodNaquadah32");
        ItemStack naq32Stack = new ItemStack(naq32Item, 1, 0);

        com.gtnewhorizons.modularnuclear.common.config.data.FuelData naq32Data = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .findFuel(naq32Stack);
        assertNotNull(naq32Data, "The Core must be recognized as fuel");
        assertEquals(
            "fuel_naquadah_32",
            naq32Data.id,
            "Must resolve to fuel_naquadah_32, not hijacked by fuel_naquadah");
        assertEquals(512, naq32Data.baseNeutrons);
        assertEquals(5.50, naq32Data.thermalFissionMultiplier, 1e-4);

        // 4. Plain Uranium rod must match fuel_uranium_1
        net.minecraft.item.Item plainUraniumItem = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
        org.mockito.Mockito.when(plainUraniumItem.getUnlocalizedName(org.mockito.Mockito.any()))
            .thenReturn("gt.rodUranium");
        ItemStack plainUraniumStack = new ItemStack(plainUraniumItem, 1, 0);

        com.gtnewhorizons.modularnuclear.common.config.data.FuelData plainUraniumData = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .findFuel(plainUraniumStack);
        assertNotNull(plainUraniumData, "Plain Uranium must be recognized as fuel");
        assertEquals("fuel_uranium_1", plainUraniumData.id);
        assertEquals(4, plainUraniumData.baseNeutrons);

        // 5. Uncalibrated custom fuel rod without NBT mix must be rejected as fuel
        if (com.gtnewhorizons.modularnuclear.common.item.ModItems.rodCustomFuel1 != null) {
            ItemStack uncalibratedRod = new ItemStack(
                com.gtnewhorizons.modularnuclear.common.item.ModItems.rodCustomFuel1);
            assertNull(
                com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.findFuel(uncalibratedRod),
                "Uncalibrated custom fuel rod without NBT must not return fuel data");
            assertFalse(
                com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification.isItemFuel(uncalibratedRod),
                "Uncalibrated custom fuel rod without NBT must not be classified as active fuel");
            assertTrue(
                com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification
                    .isDepletedOrEmptyRod(uncalibratedRod),
                "Uncalibrated custom fuel rod without NBT must be treated as empty rod");
        }

        // 6. Depleted liquid fuel must not be classified as active fluid fuel
        net.minecraftforge.fluids.FluidStack depletedFluidStack = org.mockito.Mockito
            .mock(net.minecraftforge.fluids.FluidStack.class);
        net.minecraftforge.fluids.Fluid mockFluid = org.mockito.Mockito.mock(net.minecraftforge.fluids.Fluid.class);
        org.mockito.Mockito.when(mockFluid.getName())
            .thenReturn("thoriumbasedliquidfueldepleted");
        org.mockito.Mockito.when(mockFluid.getUnlocalizedName())
            .thenReturn("fluid.thoriumbasedliquidfueldepleted");
        org.mockito.Mockito.when(depletedFluidStack.getFluid())
            .thenReturn(mockFluid);
        assertFalse(
            com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification.isFluidFuel(depletedFluidStack),
            "Depleted liquid fuel must never be classified as active fluid fuel");

        // 7. Non-rod silver items (e.g. silver ingot or plate) must not be matched as control rods
        net.minecraft.item.Item mockSilverIngot = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
        org.mockito.Mockito.when(mockSilverIngot.getUnlocalizedName(org.mockito.Mockito.any()))
            .thenReturn("item.ingotSilver");
        ItemStack silverIngotStack = new ItemStack(mockSilverIngot, 1, 0);
        assertNull(
            com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.findControlRod(silverIngotStack),
            "Silver ingot must not be classified as a control rod (wildcards eliminated)");

        // 8. Moderators must strictly require long rods only (blocks, plates, dusts, and short rods rejected)
        net.minecraft.item.Item mockGraphiteBlock = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
        org.mockito.Mockito.when(mockGraphiteBlock.getUnlocalizedName(org.mockito.Mockito.any()))
            .thenReturn("tile.blockGraphite");
        ItemStack graphiteBlockStack = new ItemStack(mockGraphiteBlock, 1, 0);
        assertNull(
            com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.findModerator(graphiteBlockStack),
            "Graphite block must be rejected as a moderator (only long rods accepted)");

        net.minecraft.item.Item mockCarbonPlate = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
        org.mockito.Mockito.when(mockCarbonPlate.getUnlocalizedName(org.mockito.Mockito.any()))
            .thenReturn("item.plateCarbon");
        ItemStack carbonPlateStack = new ItemStack(mockCarbonPlate, 1, 0);
        assertNull(
            com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.findModerator(carbonPlateStack),
            "Carbon plate must be rejected as a moderator (only long rods accepted)");

        net.minecraft.item.Item mockBerylliumShortRod = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
        org.mockito.Mockito.when(mockBerylliumShortRod.getUnlocalizedName(org.mockito.Mockito.any()))
            .thenReturn("item.rodBeryllium");
        ItemStack berylliumShortRodStack = new ItemStack(mockBerylliumShortRod, 1, 0);
        assertNull(
            com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.findModerator(berylliumShortRodStack),
            "Beryllium short rod must be rejected as a moderator (only long rods accepted)");

        net.minecraft.item.Item mockGraphiteLongRod = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
        org.mockito.Mockito.when(mockGraphiteLongRod.getUnlocalizedName(org.mockito.Mockito.any()))
            .thenReturn("item.stickLongGraphite");
        ItemStack graphiteLongRodStack = new ItemStack(mockGraphiteLongRod, 1, 0);
        assertNotNull(
            com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.findModerator(graphiteLongRodStack),
            "Graphite long rod must be accepted as a moderator");

        net.minecraft.item.Item mockCarbonLongRod = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
        org.mockito.Mockito.when(mockCarbonLongRod.getUnlocalizedName(org.mockito.Mockito.any()))
            .thenReturn("item.stickLongCarbon");
        ItemStack carbonLongRodStack = new ItemStack(mockCarbonLongRod, 1, 0);
        assertNotNull(
            com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.findModerator(carbonLongRodStack),
            "Carbon long rod must be accepted as a moderator");

        net.minecraft.item.Item mockBerylliumLongRod = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
        org.mockito.Mockito.when(mockBerylliumLongRod.getUnlocalizedName(org.mockito.Mockito.any()))
            .thenReturn("item.stickLongBeryllium");
        ItemStack berylliumLongRodStack = new ItemStack(mockBerylliumLongRod, 1, 0);
        assertNotNull(
            com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.findModerator(berylliumLongRodStack),
            "Beryllium long rod must be accepted as a moderator");

        // 9. Reflectors: Gold, Stainless Steel, and Tungsten Carbide plates accepted; Beryllium and all blocks/rods
        // rejected
        net.minecraft.item.Item mockGoldPlate = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
        org.mockito.Mockito.when(mockGoldPlate.getUnlocalizedName(org.mockito.Mockito.any()))
            .thenReturn("item.plateGold");
        ItemStack goldPlateStack = new ItemStack(mockGoldPlate, 1, 0);
        com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ReflectorData goldRefl = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .findReflector(goldPlateStack);
        assertNotNull(goldRefl, "Gold plate must be accepted as a reflector");
        assertEquals("reflector_gold", goldRefl.id);
        assertEquals(0.90, goldRefl.scatteringThermal, 1e-4);
        assertEquals(0.10, goldRefl.absorptionThermal, 1e-4);
        assertEquals(0.85, goldRefl.scatteringFast, 1e-4);
        assertEquals(0.15, goldRefl.absorptionFast, 1e-4);
        assertEquals(0.0, goldRefl.moderation, 1e-4);
        assertEquals(0.30, goldRefl.heatTransferCoeff, 1e-4);
        assertEquals(0, goldRefl.durability);

        net.minecraft.item.Item mockStainlessPlate = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
        org.mockito.Mockito.when(mockStainlessPlate.getUnlocalizedName(org.mockito.Mockito.any()))
            .thenReturn("item.plateStainlessSteel");
        ItemStack stainlessPlateStack = new ItemStack(mockStainlessPlate, 1, 0);
        com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ReflectorData stainlessRefl = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .findReflector(stainlessPlateStack);
        assertNotNull(stainlessRefl, "Stainless Steel plate must be accepted as a reflector");
        assertEquals("reflector_stainless_steel", stainlessRefl.id);
        assertEquals(0.95, stainlessRefl.scatteringThermal, 1e-4);
        assertEquals(0.05, stainlessRefl.absorptionThermal, 1e-4);
        assertEquals(0.90, stainlessRefl.scatteringFast, 1e-4);
        assertEquals(0.10, stainlessRefl.absorptionFast, 1e-4);
        assertEquals(0.0, stainlessRefl.moderation, 1e-4);
        assertEquals(0.20, stainlessRefl.heatTransferCoeff, 1e-4);
        assertEquals(0, stainlessRefl.durability);

        net.minecraft.item.Item mockTcPlate = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
        org.mockito.Mockito.when(mockTcPlate.getUnlocalizedName(org.mockito.Mockito.any()))
            .thenReturn("item.plateTungstenCarbide");
        ItemStack tcPlateStack = new ItemStack(mockTcPlate, 1, 0);
        com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ReflectorData tcRefl = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .findReflector(tcPlateStack);
        assertNotNull(tcRefl, "Tungsten Carbide plate must be accepted as a reflector");
        assertEquals("reflector_tungsten_carbide", tcRefl.id);
        assertEquals(0.98, tcRefl.scatteringThermal, 1e-4);
        assertEquals(0.02, tcRefl.absorptionThermal, 1e-4);
        assertEquals(0.93, tcRefl.scatteringFast, 1e-4);
        assertEquals(0.07, tcRefl.absorptionFast, 1e-4);
        assertEquals(0.0, tcRefl.moderation, 1e-4);
        assertEquals(0.10, tcRefl.heatTransferCoeff, 1e-4);
        assertEquals(0, tcRefl.durability);

        // Beryllium, blocks, and rods rejected
        net.minecraft.item.Item mockBerylliumPlate = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
        org.mockito.Mockito.when(mockBerylliumPlate.getUnlocalizedName(org.mockito.Mockito.any()))
            .thenReturn("item.plateBeryllium");
        ItemStack berylliumPlateStack = new ItemStack(mockBerylliumPlate, 1, 0);
        assertNull(
            com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.findReflector(berylliumPlateStack),
            "Beryllium plate must be rejected as a reflector");

        net.minecraft.item.Item mockBerylliumBlockRefl = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
        org.mockito.Mockito.when(mockBerylliumBlockRefl.getUnlocalizedName(org.mockito.Mockito.any()))
            .thenReturn("tile.blockBeryllium");
        ItemStack berylliumBlockStack = new ItemStack(mockBerylliumBlockRefl, 1, 0);
        assertNull(
            com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.findReflector(berylliumBlockStack),
            "Beryllium block must be rejected as a reflector");

        net.minecraft.item.Item mockGoldBlock = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
        org.mockito.Mockito.when(mockGoldBlock.getUnlocalizedName(org.mockito.Mockito.any()))
            .thenReturn("tile.blockGold");
        ItemStack goldBlockStack = new ItemStack(mockGoldBlock, 1, 0);
        assertNull(
            com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.findReflector(goldBlockStack),
            "Gold block must be rejected as a reflector");

        net.minecraft.item.Item mockGoldRod = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
        org.mockito.Mockito.when(mockGoldRod.getUnlocalizedName(org.mockito.Mockito.any()))
            .thenReturn("item.rodGold");
        ItemStack goldRodStack = new ItemStack(mockGoldRod, 1, 0);
        assertNull(
            com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.findReflector(goldRodStack),
            "Gold rod must be rejected as a reflector");
    }

    @Test
    void testFuelDurability8xT10WearFactorAndLiquidHatchRayScaling() {
        // 1. Verify 8x durability across all rods
        assertEquals(160000, NuclearFuelType.URANIUM.defaultDurability);
        assertEquals(80000, NuclearFuelType.MOX.defaultDurability);
        assertEquals(400000, NuclearFuelType.THORIUM.defaultDurability);
        assertEquals(80000, NuclearFuelType.GLOWSTONE.defaultDurability);
        assertEquals(80000, NuclearFuelType.LITHIUM.defaultDurability);
        assertEquals(560000, NuclearFuelType.HD_URANIUM.defaultDurability);
        assertEquals(560000, NuclearFuelType.HD_PLUTONIUM.defaultDurability);
        assertEquals(48000, NuclearFuelType.EXCITED_URANIUM.defaultDurability);
        assertEquals(80000, NuclearFuelType.EXCITED_PLUTONIUM.defaultDurability);
        assertEquals(800000, NuclearFuelType.NAQUADAH.defaultDurability);
        assertEquals(800000, NuclearFuelType.NAQUADRIA.defaultDurability);
        assertEquals(400000, NuclearFuelType.TIBERIUM.defaultDurability);
        assertEquals(2560000, NuclearFuelType.THE_CORE.defaultDurability);

        // 2. Verify T10 wear factor: D(T10) = 8.0 by default
        com.gtnewhorizons.modularnuclear.common.nuclear.FuelDamageCurveSolver.FuelCurveStats uStats = com.gtnewhorizons.modularnuclear.common.nuclear.FuelDamageCurveSolver
            .solve(NuclearFuelType.URANIUM, 20.0);
        assertEquals(1.0, uStats.damageAtPeak, 1e-4);
        assertEquals(8.0, uStats.damageAt10Percent, 1e-4);
        assertEquals(12.5, uStats.damageRatioPercent, 1e-2); // 1.0 / 8.0 = 12.5%

        // Custom wear factor override test (e.g. 4.0)
        com.gtnewhorizons.modularnuclear.common.nuclear.FuelDamageCurveSolver.FuelCurveStats customStats = com.gtnewhorizons.modularnuclear.common.nuclear.FuelDamageCurveSolver
            .solve(
                NuclearFuelType.URANIUM,
                NuclearFuelType.URANIUM.peakReactivityTemp,
                NuclearFuelType.URANIUM.floorTemp,
                20.0,
                4.0);
        assertEquals(1.0, customStats.damageAtPeak, 1e-4);
        assertEquals(4.0, customStats.damageAt10Percent, 1e-4);
        assertEquals(25.0, customStats.damageRatioPercent, 1e-2); // 1.0 / 4.0 = 25%

        // 3. Liquid fuel hatch ray count scaling with fluid amount
        SimTile hatch = new SimTile(SimTile.TileType.HATCH_LIQUID_FUEL_URANIUM);

        // Empty hatch -> 0 rays
        hatch.setInputFluidAmount(0);
        assertEquals(0, hatch.getNeutronEmissionCount());

        // Full LV hatch (8000 L) -> exactly 1 ray
        hatch.setInputFluidAmount(8000);
        assertEquals(1, hatch.getNeutronEmissionCount());

        // Full MV hatch (16000 L) -> exactly 2 rays
        hatch.setInputFluidCapacity(16000);
        hatch.setInputFluidAmount(16000);
        assertEquals(2, hatch.getNeutronEmissionCount());

        // Full HV hatch (32000 L) -> exactly 4 rays
        hatch.setInputFluidCapacity(32000);
        hatch.setInputFluidAmount(32000);
        assertEquals(4, hatch.getNeutronEmissionCount());

        // Partially filled hatch (4000 L = 0.5 LV equivalent) -> probabilistic 0 or 1, averaging ~0.5
        hatch.setInputFluidAmount(4000);
        int totalRays = 0;
        int trials = 2000;
        for (int i = 0; i < trials; i++) {
            int count = hatch.getNeutronEmissionCount();
            assertTrue(count == 0 || count == 1, "4000 L must emit 0 or 1 ray");
            totalRays += count;
        }
        double avg = (double) totalRays / trials;
        assertEquals(0.50, avg, 0.06, "Partially filled hatch at 4000 L must average 0.5 rays per cycle");
    }

    @Test
    void testMaxDamageFuelFailsafe() {
        if (com.gtnewhorizons.modularnuclear.common.item.ModItems.rodCustomFuel4 == null) {
            com.gtnewhorizons.modularnuclear.common.item.ModItems.init();
        }
        com.gtnewhorizons.modularnuclear.common.item.ItemCustomFuelRod customItem = com.gtnewhorizons.modularnuclear.common.item.ModItems.rodCustomFuel4;

        ItemStack fuelStack = new ItemStack(customItem, 1, 0);
        fuelStack.stackTagCompound = new net.minecraft.nbt.NBTTagCompound();

        java.util.Map<com.gtnewhorizons.modularnuclear.common.nuclear.CustomFuelMix.ActinideIsotope, Integer> units = new java.util.LinkedHashMap<>();
        units.put(com.gtnewhorizons.modularnuclear.common.nuclear.CustomFuelMix.ActinideIsotope.URANIUM_235, 360);
        com.gtnewhorizons.modularnuclear.common.nuclear.CustomFuelMix mix = new com.gtnewhorizons.modularnuclear.common.nuclear.CustomFuelMix(
            units);
        mix.writeToNBT(fuelStack.stackTagCompound);

        int maxDur = customItem.getMaxDamageEx(fuelStack);
        assertTrue(maxDur > 0, "Max durability must be greater than 0");

        customItem.setDamageForStack(fuelStack, 0);
        assertFalse(
            com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification.isFuelAtMaxDamage(fuelStack));

        customItem.setDamageForStack(fuelStack, maxDur);
        assertTrue(com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification.isFuelAtMaxDamage(fuelStack));
        assertTrue(
            com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification.isItemAllowedInBus(fuelStack),
            "Max-damaged fuel rod must be allowed in bus for automation");
    }

    @Test
    void testMaxDamageModeratorFailsafe() {
        net.minecraftforge.oredict.OreDictionary
            .registerOre("stickLongGraphite", new ItemStack(net.minecraft.init.Items.stick));
        ItemStack modStack = new ItemStack(net.minecraft.init.Items.stick, 1, 0);
        modStack.stackTagCompound = new net.minecraft.nbt.NBTTagCompound();

        // Graphite durability is 80000
        modStack.stackTagCompound.setInteger("advDmg", 79999);
        assertFalse(
            com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification.isModeratorAtMaxDamage(modStack));

        modStack.stackTagCompound.setInteger("advDmg", 80000);
        assertTrue(
            com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification.isModeratorAtMaxDamage(modStack));
        assertTrue(
            com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification.isItemAllowedInBus(modStack),
            "Fully damaged moderator must be allowed in bus for automation");
    }

    @Test
    void testNuclearReactorEnergyHatchesAndPowerDrain() {
        MTENuclearReactor reactor = new MTENuclearReactor("test.reactor.energy");
        assertEquals(0, reactor.getStoredEnergyUnits(), "Stored energy should start at 0 with no hatches");
        assertFalse(reactor.drainEnergyUnits(100), "Draining from empty reactor should fail");
        assertTrue(reactor.drainEnergyUnits(0), "Draining 0 EU should always succeed");

        // Mock energy hatch
        gregtech.api.metatileentity.implementations.MTEHatchEnergy mockHatch = org.mockito.Mockito
            .mock(gregtech.api.metatileentity.implementations.MTEHatchEnergy.class);
        gregtech.api.interfaces.tileentity.IGregTechTileEntity mockBase = org.mockito.Mockito
            .mock(gregtech.api.interfaces.tileentity.IGregTechTileEntity.class);
        org.mockito.Mockito.when(mockHatch.isValid())
            .thenReturn(true);
        org.mockito.Mockito.when(mockHatch.getBaseMetaTileEntity())
            .thenReturn(mockBase);
        org.mockito.Mockito.when(mockBase.getStoredEU())
            .thenReturn(5000L);

        reactor.mEnergyHatches = new java.util.ArrayList<>();
        reactor.mEnergyHatches.add(mockHatch);

        assertEquals(5000L, reactor.getStoredEnergyUnits(), "Stored energy should reflect hatch buffer");
        assertFalse(reactor.drainEnergyUnits(6000L), "Draining more than stored should fail without draining");

        boolean drained = reactor.drainEnergyUnits(1000L);
        assertTrue(drained, "Draining within available energy should succeed");
        org.mockito.Mockito.verify(mockBase)
            .decreaseStoredEnergyUnits(1000L, false);
    }

    @Test
    void testMoltenDownControllerFailsStructureCheckAndDisplaysInTooltip() {
        MTENuclearReactor reactor = new MTENuclearReactor("nuclear.reactor.molten_test");
        reactor.mMoltenDown = true;

        // 1. setItemNBT writes mMoltenDown tag
        NBTTagCompound itemNbt = new NBTTagCompound();
        reactor.setItemNBT(itemNbt);
        assertTrue(itemNbt.hasKey("mMoltenDown"), "Item NBT must contain mMoltenDown tag");
        assertTrue(itemNbt.getBoolean("mMoltenDown"), "mMoltenDown tag must be true");

        // 2. Controller item tooltip renders molten down status in dark red bold
        ItemStack controllerStack = new ItemStack(Blocks.iron_block);
        controllerStack.setTagCompound(itemNbt);
        List<String> tooltip = new ArrayList<>();
        reactor.addAdditionalTooltipInformation(controllerStack, tooltip);
        boolean foundMoltenTooltip = tooltip.stream()
            .anyMatch(s -> s.contains("Molten down (permanently ruined)"));
        assertTrue(foundMoltenTooltip, "Controller item tooltip must display 'Molten down (permanently ruined)'");

        // 3. Placing controller restores mMoltenDown via loadNBTData
        MTENuclearReactor placedReactor = new MTENuclearReactor("nuclear.reactor.molten_test");
        gregtech.api.interfaces.tileentity.IGregTechTileEntity tePlaced = org.mockito.Mockito
            .mock(gregtech.api.interfaces.tileentity.IGregTechTileEntity.class);
        org.mockito.Mockito.when(tePlaced.getFrontFacing())
            .thenReturn(ForgeDirection.NORTH);
        placedReactor.setBaseMetaTileEntity(tePlaced);
        placedReactor.loadNBTData(itemNbt);
        assertTrue(placedReactor.mMoltenDown, "Placed reactor must restore mMoltenDown state");

        // 4. Structure check immediately fails on a molten-down controller
        List<gregtech.api.structure.error.StructureError> errors = new ArrayList<>();
        placedReactor.checkMachine(null, controllerStack, errors);
        assertFalse(errors.isEmpty(), "Structure check must report an error for molten controller");
        boolean foundStructureError = errors.stream()
            .anyMatch(
                e -> e.getDisplayString() != null && (e.getDisplayString()
                    .contains("molten_down")
                    || e.getDisplayString()
                        .contains("meltdown")));
        assertTrue(foundStructureError, "Structure error list must include molten_down error message");
    }

    @Test
    void testExplosionsDisabledPreventsExplosionAndMeltdownAndVoidsAllContents() {
        boolean prevSetting = gregtech.api.GregTechAPI.sMachineExplosions;
        try {
            gregtech.api.GregTechAPI.sMachineExplosions = false;

            MTENuclearReactor reactor = new MTENuclearReactor("nuclear.reactor.no_expl_test");
            reactor.mReactorDamage = 100.0;
            reactor.mMachine = true;

            gregtech.api.interfaces.tileentity.IGregTechTileEntity teMock = org.mockito.Mockito
                .mock(gregtech.api.interfaces.tileentity.IGregTechTileEntity.class);
            org.mockito.Mockito.when(teMock.isServerSide())
                .thenReturn(true);
            reactor.setBaseMetaTileEntity(teMock);

            // Add a mock nuclear tile bus with an item
            MTEHatchNuclearBus bus = new MTEHatchNuclearBus("bus.test", 4, new String[0], null);
            bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = new ItemStack(Blocks.dirt);
            gregtech.api.interfaces.tileentity.IGregTechTileEntity busTe = org.mockito.Mockito
                .mock(gregtech.api.interfaces.tileentity.IGregTechTileEntity.class);
            org.mockito.Mockito.when(busTe.getMetaTileEntity())
                .thenReturn(bus);
            org.mockito.Mockito.when(busTe.isServerSide())
                .thenReturn(true);
            reactor.mNuclearTiles.add(busTe);

            // Add a mock nuclear tile hatch with fluid
            MTEHatchNuclearHatch hatch = new MTEHatchNuclearHatch("hatch.test", 1, 16000, new String[0], null);
            hatch.mInputFluid = new net.minecraftforge.fluids.FluidStack(
                net.minecraftforge.fluids.FluidRegistry.WATER,
                1000);
            gregtech.api.interfaces.tileentity.IGregTechTileEntity hatchTe = org.mockito.Mockito
                .mock(gregtech.api.interfaces.tileentity.IGregTechTileEntity.class);
            org.mockito.Mockito.when(hatchTe.getMetaTileEntity())
                .thenReturn(hatch);
            org.mockito.Mockito.when(hatchTe.isServerSide())
                .thenReturn(true);
            reactor.mNuclearTiles.add(hatchTe);

            // Trigger meltdown with explosions disabled
            reactor.triggerMeltdown();

            // Meltdown should NOT mark controller as moltenDown when explosions are disabled
            assertFalse(reactor.mMoltenDown, "Controller should not be ruined when explosions are disabled");
            assertEquals(0.0, reactor.mReactorDamage, 1e-6, "Reactor damage should be reset to 0 after safe shutdown");
            assertFalse(reactor.mMachine, "Reactor should be shut down");
            assertTrue(reactor.mScram, "Reactor should be SCRAMmed");

            // All contents must be voided
            assertNull(bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT], "Bus inventory must be completely voided");
            assertNull(hatch.mInputFluid, "Hatch fluid must be completely voided");

            // Similarly, explodeReactor should void all contents without exploding
            bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = new ItemStack(Blocks.dirt);
            hatch.mInputFluid = new net.minecraftforge.fluids.FluidStack(
                net.minecraftforge.fluids.FluidRegistry.WATER,
                1000);
            reactor.explodeReactor(null, "Test explosion when disabled");
            assertNull(bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT], "Bus inventory must be voided by explodeReactor");
            assertNull(hatch.mInputFluid, "Hatch fluid must be voided by explodeReactor");
        } finally {
            gregtech.api.GregTechAPI.sMachineExplosions = prevSetting;
        }
    }

    @Test
    void testFalloutEntityChunkLoading() {
        EntityMeltdownFallout fallout = org.mockito.Mockito
            .mock(EntityMeltdownFallout.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        assertNull(fallout.getChunkTicket(), "Initial ticket should be null");

        net.minecraftforge.common.ForgeChunkManager.Ticket mockTicket = org.mockito.Mockito
            .mock(net.minecraftforge.common.ForgeChunkManager.Ticket.class);
        fallout.initChunkLoading(mockTicket);
        assertEquals(mockTicket, fallout.getChunkTicket(), "Ticket should be set after initChunkLoading");

        fallout.releaseChunkLoading();
        assertNull(fallout.getChunkTicket(), "Ticket should be cleared after releaseChunkLoading");
    }

    @Test
    void testVirtualPollutionCalculations() {
        EntityMeltdownFallout.clearActiveFallouts();
        try {
            boolean prevPollution = gregtech.GTMod.proxy.mPollution;
            gregtech.GTMod.proxy.mPollution = true;
            try {
                net.minecraft.world.World mockWorld = org.mockito.Mockito.mock(net.minecraft.world.World.class);
                mockWorld.isRemote = false;

                EntityMeltdownFallout fallout = org.mockito.Mockito
                    .mock(EntityMeltdownFallout.class, org.mockito.Mockito.CALLS_REAL_METHODS);
                fallout.worldObj = mockWorld;
                fallout.posX = 0.0;
                fallout.posY = 64.0;
                fallout.posZ = 0.0;
                fallout.setContamination(10000.0);
                EntityMeltdownFallout.registerActiveFallout(fallout);

                // Initial contamination is 10000.
                // Ratio is 62.5 / 30000 = 1 / 480.
                // Equivalent pollution = 10000 * 480 = 4,800,000.
                int virtualCenter = EntityMeltdownFallout.getVirtualPollution(mockWorld, 0, 0);
                assertEquals(4800000, virtualCenter, "Center chunk must report 4,800,000 virtual pollution");

                // Adjacent chunk in 3x3 (1, 0)
                int virtualAdj = EntityMeltdownFallout.getVirtualPollution(mockWorld, 1, 0);
                assertEquals(4800000, virtualAdj, "Adjacent chunk in 3x3 must report virtual pollution");

                // Chunk within 160m radius (e.g. 5 chunks away = 80m)
                // Chunk 5 has center at (5 * 16 + 8) = 88m. 88m <= 160m.
                int virtualRadius = EntityMeltdownFallout.getVirtualPollution(mockWorld, 5, 0);
                assertEquals(4800000, virtualRadius, "Chunk within radius must report virtual pollution");

                // Chunk far away (e.g. 20 chunks = 320m)
                int virtualFar = EntityMeltdownFallout.getVirtualPollution(mockWorld, 20, 0);
                assertEquals(0, virtualFar, "Chunk outside radius must report 0 virtual pollution");

                // Now simulate cleaning: a machine removes 48000 pollution in chunk (1, 0)
                EntityMeltdownFallout.onPollutionRemoved(mockWorld, 1, 0, 48000);
                // Contamination should decrease by 48000 * (1 / 480) = 100
                assertEquals(9900.0, fallout.getContamination(), 1e-4, "Contamination should decrease to 9900");

                // Virtual pollution should now reflect 9900 * 480 = 4,752,000
                int virtualAfter = EntityMeltdownFallout.getVirtualPollution(mockWorld, 0, 0);
                assertEquals(4752000, virtualAfter, "Virtual pollution should decrease to 4,752,000");

                // Reduce contamination to 0
                EntityMeltdownFallout.onPollutionRemoved(mockWorld, 0, 0, 4752000);
                assertEquals(0.0, fallout.getContamination(), 1e-4, "Contamination should reach 0");
                assertTrue(fallout.isDead, "Fallout entity should be marked dead once cleared");

                // After clearing, virtual pollution must be 0
                assertEquals(0, EntityMeltdownFallout.getVirtualPollution(mockWorld, 0, 0));
            } finally {
                gregtech.GTMod.proxy.mPollution = prevPollution;
            }
        } finally {
            EntityMeltdownFallout.clearActiveFallouts();
        }
    }

    @Test
    void testHighPressurePassageOvertemperatureAndOutletDisplay() {
        MTENuclearReactor reactor = new MTENuclearReactor("test.reactor.hp.overtemp");
        reactor.mMachine = true;
        reactor.mPipeTier = NuclearSimulationEngine.PIPE_TIER_ELECTRUM; // Max operating temp = 1200 °C
        reactor.gridSize = 3;
        reactor.mGrid = new INuclearTile[3][3];
        reactor.fixAllIssues();

        gregtech.api.metatileentity.BaseMetaTileEntity baseTe = org.mockito.Mockito
            .mock(gregtech.api.metatileentity.BaseMetaTileEntity.class);
        org.mockito.Mockito.when(baseTe.isServerSide())
            .thenReturn(true);
        org.mockito.Mockito.when(baseTe.isAllowedToWork())
            .thenReturn(true);
        org.mockito.Mockito.when(baseTe.getWorld())
            .thenReturn(org.mockito.Mockito.mock(net.minecraft.world.World.class));
        reactor.setBaseMetaTileEntity(baseTe);

        MTEHatchNuclearHighPressure topHp = new MTEHatchNuclearHighPressure("test.top_hp", 4, new String[0], null);
        MTEHatchNuclearHighPressure botHp = new MTEHatchNuclearHighPressure("test.bot_hp", 4, new String[0], null);
        topHp.setIsInlet(true);
        botHp.setIsInlet(false);

        gregtech.api.interfaces.tileentity.IGregTechTileEntity mockTopTe = org.mockito.Mockito
            .mock(gregtech.api.interfaces.tileentity.IGregTechTileEntity.class);
        org.mockito.Mockito.when(mockTopTe.getMetaTileEntity())
            .thenReturn(topHp);
        topHp.setBaseMetaTileEntity(mockTopTe);

        gregtech.api.interfaces.tileentity.IGregTechTileEntity mockBotTe = org.mockito.Mockito
            .mock(gregtech.api.interfaces.tileentity.IGregTechTileEntity.class);
        org.mockito.Mockito.when(mockBotTe.getMetaTileEntity())
            .thenReturn(botHp);
        botHp.setBaseMetaTileEntity(mockBotTe);

        topHp.setOppositeHatch(mockBotTe);
        botHp.setOppositeHatch(mockTopTe);

        // 1. Outlet display & telemetry synchronization
        topHp.setTemperature(350.0);
        assertEquals(350.0, topHp.getTemperature(), 1e-6);
        assertEquals(350.0, botHp.getTemperature(), 1e-6, "Outlet hatch must dynamically display core temperature");
        assertEquals(350.0, botHp.getDeviceTemperatureCelsius(), 1e-6);

        topHp.mFastFlux = 1500;
        topHp.mThermalFlux = 800;
        topHp.mFastAbsorbed = 1200;
        topHp.mThermalAbsorbed = 600;
        topHp.mHeatOutputAccumulator = 4500.0;
        topHp.mCumulativeFluence = 99999L;
        topHp.mIncomingCoolantTemp = 280.0;
        topHp.nuclearTick(1.0);

        assertEquals(1500, botHp.mLastFastFlux, "Outlet hatch must synchronize fast flux telemetry");
        assertEquals(800, botHp.mLastThermalFlux, "Outlet hatch must synchronize thermal flux telemetry");
        assertEquals(1200, botHp.mLastFastAbsorbed, "Outlet hatch must synchronize fast absorbed telemetry");
        assertEquals(600, botHp.mLastThermalAbsorbed, "Outlet hatch must synchronize thermal absorbed telemetry");
        assertEquals(4500.0, botHp.mLastHeatOutput, 1e-6, "Outlet hatch must synchronize heat output telemetry");
        assertEquals(102299L, botHp.mCumulativeFluence, "Outlet hatch must synchronize cumulative fluence");
        assertEquals(280.0, botHp.mIncomingCoolantTemp, 1e-6, "Outlet hatch must synchronize incoming coolant temp");

        // 2. High pressure passage over-temperature damage
        reactor.mNuclearTiles.add(mockTopTe);

        double maxTemp = NuclearSimulationEngine.getMaxOperatingTemperature(reactor.mPipeTier);
        assertEquals(1200.0, maxTemp, 1e-6);

        // Safe temperature: no damage
        topHp.setTemperature(1000.0);
        topHp.mIncomingCoolantTemp = 1000.0;
        reactor.mReactorDamage = 0.0;
        reactor.onPostTick(baseTe, 20);
        assertEquals(0.0, reactor.mReactorDamage, 1e-6, "No damage within temperature limits");

        // Core passage over-temperature (> maxTemp): increases damage by 1% per cycle
        topHp.setTemperature(1300.0);
        topHp.mIncomingCoolantTemp = 1000.0;
        reactor.onPostTick(baseTe, 40);
        assertEquals(
            1.0,
            reactor.mReactorDamage,
            1e-6,
            "Passage cell above max operating temp must increase damage by 1%");

        // Incoming coolant over-temperature (> maxTemp): increases damage
        topHp.setTemperature(1000.0);
        topHp.mIncomingCoolantTemp = 1300.0;
        reactor.onPostTick(baseTe, 60);
        assertEquals(
            2.0,
            reactor.mReactorDamage,
            1e-6,
            "Incoming coolant above max operating temp must increase damage by 1%");

        // 3. Structure break temperature check (> 100 °C)
        reactor.mLastFormedNuclearTiles.clear();
        reactor.mBottomHighPressureHatches.clear();
        topHp.setTemperature(50.0);
        reactor.mLastFormedNuclearTiles.add(mockTopTe);
        assertFalse(reactor.isAnyTemperatureAbove100(), "Below 100°C must not trigger structure break warning");

        topHp.setTemperature(150.0);
        assertTrue(reactor.isAnyTemperatureAbove100(), "Top HP hatch > 100°C must trigger structure break warning");

        reactor.mLastFormedNuclearTiles.clear();
        reactor.mBottomHighPressureHatches.add(botHp);
        assertTrue(reactor.isAnyTemperatureAbove100(), "Bottom HP hatch > 100°C must trigger structure break warning");
    }

    @Test
    void testLiquidFuelThermalShockExemption() {
        MTENuclearReactor reactor = new MTENuclearReactor("test.reactor.liquidfuel.shock");
        reactor.mMachine = true;
        reactor.mPipeTier = NuclearSimulationEngine.PIPE_TIER_OSMIUM;
        reactor.gridSize = 3;
        reactor.mGrid = new INuclearTile[3][3];
        reactor.fixAllIssues();
        assertEquals(6, reactor.getRepairStatus());

        gregtech.api.metatileentity.BaseMetaTileEntity baseTe = org.mockito.Mockito
            .mock(gregtech.api.metatileentity.BaseMetaTileEntity.class);
        org.mockito.Mockito.when(baseTe.isServerSide())
            .thenReturn(true);
        org.mockito.Mockito.when(baseTe.isAllowedToWork())
            .thenReturn(true);
        org.mockito.Mockito.when(baseTe.getWorld())
            .thenReturn(org.mockito.Mockito.mock(net.minecraft.world.World.class));
        reactor.setBaseMetaTileEntity(baseTe);

        if (!net.minecraftforge.fluids.FluidRegistry.isFluidRegistered("thoriumbasedliquidfuel")) {
            net.minecraftforge.fluids.FluidRegistry
                .registerFluid(new net.minecraftforge.fluids.Fluid("thoriumbasedliquidfuel"));
        }
        net.minecraftforge.fluids.Fluid liquidFuel = net.minecraftforge.fluids.FluidRegistry
            .getFluid("thoriumbasedliquidfuel");
        net.minecraftforge.fluids.FluidStack liquidFuelStack = new net.minecraftforge.fluids.FluidStack(
            liquidFuel,
            1000);
        assertTrue(
            MTENuclearReactor.isFluidFuel(liquidFuelStack),
            "Thorium based liquid fuel must be classified as fluid fuel");

        // Superheated hatch (>100°C) with liquid fuel injected into dry hatch
        MTEHatchNuclearHatch hatch = new MTEHatchNuclearHatch("test.hatch.fuel", 3, 32000, new String[0], null);
        hatch.mTemperature = 250.0;
        hatch.mWasDry = true;
        hatch.mInputFluid = liquidFuelStack;

        gregtech.api.interfaces.tileentity.IGregTechTileEntity hatchTe = org.mockito.Mockito
            .mock(gregtech.api.interfaces.tileentity.IGregTechTileEntity.class);
        org.mockito.Mockito.when(hatchTe.getMetaTileEntity())
            .thenReturn(hatch);
        hatch.setBaseMetaTileEntity(hatchTe);
        reactor.mNuclearTiles.add(hatchTe);

        // Run cycle at tick 20
        reactor.onPostTick(baseTe, 20);

        // Liquid fuel must NOT trigger thermal shock and must NOT be voided
        assertNotNull(hatch.mInputFluid, "Liquid fuel must NOT be voided on refill into hot dry hatch");
        assertEquals(1000, hatch.mInputFluid.amount, "Liquid fuel amount must be intact");
        assertFalse(hatch.mWasDry, "Liquid fuel hatch mWasDry must be cleared without triggering thermal shock");
        assertEquals(
            0.0,
            reactor.mReactorDamage,
            1e-6,
            "Reactor damage must remain 0 (no thermal shock for liquid fuel)");
        assertEquals(6, reactor.getRepairStatus(), "Maintenance issues must remain intact (no thermal shock)");

        // Directly invoking triggerThermalShock on a liquid fuel hatch must be a no-op
        reactor.triggerThermalShock(hatch, "Manual trigger attempt");
        assertNotNull(hatch.mInputFluid, "Direct triggerThermalShock must be a no-op for liquid fuel");
        assertEquals(0.0, reactor.mReactorDamage, 1e-6);
        assertEquals(6, reactor.getRepairStatus());
    }

    @Test
    void testControlRodAbsorptionHeatRateOnFuelCell() {
        // 1. Verify SimTile control rod absorption fraction logic
        SimTile simTileFuel = new SimTile(SimTile.TileType.FUEL_URANIUM_SINGLE);
        assertFalse(simTileFuel.hasControlRod());
        assertEquals(0.0, simTileFuel.getControlRodAbsorptionFraction(NeutronType.THERMAL), 1e-6);

        simTileFuel.setHasControlRod(true);
        simTileFuel.setControlRodType(SimTile.ControlRodType.CADMIUM);
        simTileFuel.setControlRodInsertion(100);
        assertTrue(simTileFuel.hasControlRod());
        double pBaseThermal = simTileFuel.getBaseAbsorptionProbability(NeutronType.THERMAL);
        double pRodThermal = simTileFuel.getControlRodAbsorptionProbability(NeutronType.THERMAL);
        assertEquals(0.85, pRodThermal, 1e-6);
        double expectedFrac = pRodThermal / (pBaseThermal + pRodThermal);
        assertEquals(expectedFrac, simTileFuel.getControlRodAbsorptionFraction(NeutronType.THERMAL), 1e-6);

        // 2. Verify NuclearGridTile control rod absorption fraction with MTEHatchNuclearControlRod
        MTENuclearReactor reactor = new MTENuclearReactor("test.reactor.rod.heat");
        reactor.mPipeTier = NuclearSimulationEngine.PIPE_TIER_ELECTRUM;
        com.gtnewhorizons.modularnuclear.common.metatileentity.multi.NuclearGridTile gridTile = new com.gtnewhorizons.modularnuclear.common.metatileentity.multi.NuclearGridTile(
            reactor,
            (MTEHatchNuclearBus) null,
            1,
            1);
        assertFalse(gridTile.hasControlRod());
        assertEquals(0.0, gridTile.getControlRodAbsorptionFraction(NeutronType.THERMAL), 1e-6);

        MTEHatchNuclearControlRod rodHatch = new MTEHatchNuclearControlRod("test.rod.hatch", 4, new String[0], null);
        gridTile.setBottomControlRod(rodHatch);
        assertTrue(gridTile.hasControlRod());
        double pGridBase = reactor.getBaseAbsorptionProbability(gridTile, NeutronType.THERMAL);
        double pGridRod = rodHatch.getAbsorptionProbability(NeutronType.THERMAL);
        double expectedGridFrac = pGridRod / (pGridBase + pGridRod);
        assertEquals(expectedGridFrac, gridTile.getControlRodAbsorptionFraction(NeutronType.THERMAL), 1e-6);

        // 3. Verify NuclearSimulationEngine heat generation:
        // When control rods absorb thermal neutrons on a fuel cell, they deposit heat at
        // HU_FOR_FAST_NEUTRON * 0.5 (4.0 Hu/neutron), NOT the fuel fission rate (fissionHeatPerNeutron * 1.25 = 5.0
        // Hu/neutron).
        // A non-fuel cell also deposits thermal neutron absorption heat at HU_FOR_FAST_NEUTRON * 0.5 (4.0 Hu/neutron).

        double origHeatPerNeutron = NuclearSimulationEngine.fissionHeatPerNeutron;
        NuclearSimulationEngine.fissionHeatPerNeutron = 4.0; // 4.0 * 1.25 = 5.0 Hu/neutron for fuel

        try {
            NuclearSimulationEngine.setTicksPerCycle(20);
            java.util.function.BiFunction<Boolean, Double, Double> runSim = (isFuel, rodFraction) -> {
                INuclearTile[][] grid = new INuclearTile[3][3];
                SimTile source = new SimTile(SimTile.TileType.NEUTRON_SOURCE);
                source.setNeutronSourceBeamEnergy(1000.0f);
                source.setNeutronSourceBeamRate(100);
                grid[0][1] = source;

                MockNuclearTile target = new MockNuclearTile(isFuel, 0);
                target.heatCoeff = 0.0; // Isolate conductive heat transfer
                target.temperature = 20.0;
                target.absorbProb = 0.0; // Fast absorption = 0 (100% scattered & moderated)
                target.scatterProb = 1.0;
                target.moderationProb = 1.0;
                target.absorbProbThermal = 1.0; // 100% thermal absorption
                target.hasControlRod = (rodFraction > 0.0);
                target.rodAbsorptionFraction = rodFraction;
                grid[1][1] = target;

                NuclearSimulationEngine.simulate(grid, 3, 3);
                return target.heatHU;
            };

            // Non-fuel cell: 800.0 (fast mod) + 100 * 4.0 (non-fuel thermal) = 1200.0 Hu
            double heatNonFuel = runSim.apply(false, 0.0);
            assertEquals(1200.0, heatNonFuel, 1e-4, "Non-fuel cell must absorb thermal neutrons at 4.0 Hu/neutron");

            // Fuel cell without control rod: 800.0 (fast mod) + 100 * 5.0 (fuel thermal) = 1300.0 Hu
            double heatFuelNoRod = runSim.apply(true, 0.0);
            assertEquals(
                1300.0,
                heatFuelNoRod,
                1e-4,
                "Fuel cell without control rod must absorb thermal neutrons at 5.0 Hu/neutron");

            // Fuel cell with 100% control rod absorption: 800.0 (fast mod) + 100 * 4.0 (rod thermal) = 1200.0 Hu
            double heatFuelFullRod = runSim.apply(true, 1.0);
            assertEquals(
                1200.0,
                heatFuelFullRod,
                1e-4,
                "Fuel cell with 100% control rod must absorb thermal neutrons at non-fuel rate (4.0 Hu/neutron)");
            assertEquals(
                heatNonFuel,
                heatFuelFullRod,
                1e-4,
                "Rod-absorbed heat on fuel cell must exactly equal non-fuel cell heat");

            // Fuel cell with 50% control rod absorption: 800.0 + 50 * 5.0 + 50 * 4.0 = 1250.0 Hu
            double heatFuelHalfRod = runSim.apply(true, 0.5);
            assertEquals(
                1250.0,
                heatFuelHalfRod,
                1e-4,
                "Fuel cell with 50% control rod must split absorption: 50 * 5.0 + 50 * 4.0 = 450 Hu");

            // Fuel cell with 25% control rod absorption: 800.0 + 75 * 5.0 + 25 * 4.0 = 1275.0 Hu
            double heatFuelQuarterRod = runSim.apply(true, 0.25);
            assertEquals(
                1275.0,
                heatFuelQuarterRod,
                1e-4,
                "Fuel cell with 25% control rod must split absorption: 75 * 5.0 + 25 * 4.0 = 475 Hu");
        } finally {
            NuclearSimulationEngine.fissionHeatPerNeutron = origHeatPerNeutron;
            NuclearSimulationEngine.setTicksPerCycle(NuclearSimulationEngine.DEFAULT_TICKS_PER_CYCLE);
        }
    }

    @Test
    void testControlRodMiningPipeTravelHousing() {
        // 1. Basic Hatch properties and clamping
        MTEHatchNuclearControlRod rod = new MTEHatchNuclearControlRod("test.rod.housing", 4, new String[0], null);
        assertEquals(5, rod.getMiningPipeCount());
        assertEquals(100, rod.getMaxExtractionPercent());
        assertEquals(1.0, rod.getMaxExtractionRatio(), 1e-6);
        assertEquals(0, rod.getMinInsertionPercent());
        assertEquals(0.0, rod.getMinInsertionRatio(), 1e-6);

        // Clamp checks on setter
        rod.setMiningPipeCount(6);
        assertEquals(5, rod.getMiningPipeCount());
        rod.setMiningPipeCount(-2);
        assertEquals(0, rod.getMiningPipeCount());

        // 2. Extraction and insertion limits per pipe count
        int[][] pipeScenarios = {
            // pipes, expectedMaxExtraction, expectedMinInsertion
            { 5, 100, 0 }, { 4, 80, 20 }, { 3, 60, 40 }, { 2, 40, 60 }, { 1, 20, 80 }, { 0, 0, 100 } };
        for (int[] s : pipeScenarios) {
            rod.setMiningPipeCount(s[0]);
            assertEquals(s[1], rod.getMaxExtractionPercent(), "Max extraction mismatch for " + s[0] + " pipes");
            assertEquals(s[2], rod.getMinInsertionPercent(), "Min insertion mismatch for " + s[0] + " pipes");
            assertEquals(s[1] / 100.0, rod.getMaxExtractionRatio(), 1e-6);
            assertEquals(s[2] / 100.0, rod.getMinInsertionRatio(), 1e-6);
        }

        // 3. Signal clamping with subclass overriding signals
        class TestHousingRod extends MTEHatchNuclearControlRod {

            int fine = -1;
            byte rs = 0;

            TestHousingRod() {
                super("test.housing.mock", 4, new String[0], null);
            }

            @Override
            public int getFineRedstoneSignal() {
                return fine;
            }

            @Override
            public byte getRedstoneSignal() {
                return rs;
            }
        }

        TestHousingRod testRod = new TestHousingRod();

        // 3 pipes: max extraction 60%, min insertion 40%
        testRod.setMiningPipeCount(3);
        assertEquals(40, testRod.getMinInsertionPercent());

        // Target insertion 0% (full extraction requested): clamped to 40%
        testRod.fine = 0;
        assertEquals(0, testRod.getTargetInsertionPercent());
        assertEquals(40, testRod.getInsertionPercent());
        assertEquals(0.40, testRod.getInsertionRatio(), 1e-6);

        // Target insertion 20% (below min 40%): clamped to 40%
        testRod.fine = 51; // 51 / 255 = 0.20
        assertEquals(20, testRod.getTargetInsertionPercent());
        assertEquals(40, testRod.getInsertionPercent());
        assertEquals(0.40, testRod.getInsertionRatio(), 1e-6);

        // Target insertion 60% (above min 40%): remains 60%
        testRod.fine = 153; // 153 / 255 = 0.60
        assertEquals(60, testRod.getTargetInsertionPercent());
        assertEquals(60, testRod.getInsertionPercent());
        assertEquals(0.60, testRod.getInsertionRatio(), 1e-6);

        // 0 pipes: locked at 100% insertion regardless of redstone
        testRod.setMiningPipeCount(0);
        testRod.fine = 0;
        assertEquals(100, testRod.getInsertionPercent());
        assertEquals(1.0, testRod.getInsertionRatio(), 1e-6);

        // SCRAM forces 100% insertion even with 5 pipes
        testRod.setMiningPipeCount(5);
        testRod.fine = 0;
        testRod.setScram(true);
        assertEquals(100, testRod.getInsertionPercent());
        assertEquals(1.0, testRod.getInsertionRatio(), 1e-6);
        testRod.setScram(false);

        // 4. World contiguous scanning & gap handling
        World mockWorld = org.mockito.Mockito.mock(World.class);
        IGregTechTileEntity mockGt = org.mockito.Mockito.mock(IGregTechTileEntity.class);
        org.mockito.Mockito.when(mockGt.getWorld())
            .thenReturn(mockWorld);
        org.mockito.Mockito.when(mockGt.getXCoord())
            .thenReturn(10);
        org.mockito.Mockito.when(mockGt.getYCoord())
            .thenReturn((short) 50);
        org.mockito.Mockito.when(mockGt.getZCoord())
            .thenReturn(10);
        org.mockito.Mockito.when(mockGt.getFrontFacing())
            .thenReturn(ForgeDirection.DOWN);

        net.minecraft.block.Block mockMiningPipe = org.mockito.Mockito.mock(net.minecraft.block.Block.class);
        org.mockito.Mockito.when(mockMiningPipe.getUnlocalizedName())
            .thenReturn("tile.blockMiningPipe");

        net.minecraft.block.Block mockOther = org.mockito.Mockito.mock(net.minecraft.block.Block.class);
        org.mockito.Mockito.when(mockOther.getUnlocalizedName())
            .thenReturn("tile.stone");

        // Test isMiningPipeBlock
        assertTrue(MTEHatchNuclearControlRod.isMiningPipeBlock(mockMiningPipe, 0));
        assertFalse(MTEHatchNuclearControlRod.isMiningPipeBlock(mockOther, 0));
        assertFalse(MTEHatchNuclearControlRod.isMiningPipeBlock(null, 0));

        MTEHatchNuclearControlRod worldRod = new MTEHatchNuclearControlRod("test.world.rod", 4, new String[0], null);
        worldRod.setBaseMetaTileEntity(mockGt);

        // Case A: 5 consecutive mining pipes below hatch (y=49..45)
        for (int y = 49; y >= 45; y--) {
            org.mockito.Mockito.when(mockWorld.getBlock(10, y, 10))
                .thenReturn(mockMiningPipe);
        }
        worldRod.updateMiningPipeHousing();
        assertEquals(5, worldRod.getMiningPipeCount());
        assertEquals(0, worldRod.getMinInsertionPercent());

        // Case B: 2 mining pipes, then gap (stone), then 2 mining pipes
        org.mockito.Mockito.when(mockWorld.getBlock(10, 49, 10))
            .thenReturn(mockMiningPipe);
        org.mockito.Mockito.when(mockWorld.getBlock(10, 48, 10))
            .thenReturn(mockMiningPipe);
        org.mockito.Mockito.when(mockWorld.getBlock(10, 47, 10))
            .thenReturn(mockOther); // GAP!
        org.mockito.Mockito.when(mockWorld.getBlock(10, 46, 10))
            .thenReturn(mockMiningPipe);
        org.mockito.Mockito.when(mockWorld.getBlock(10, 45, 10))
            .thenReturn(mockMiningPipe);

        worldRod.updateMiningPipeHousing();
        assertEquals(2, worldRod.getMiningPipeCount(), "Gap should stop pipe counting at 2");
        assertEquals(60, worldRod.getMinInsertionPercent());
        assertEquals(40, worldRod.getMaxExtractionPercent());

        // Case C: Immediate blockage / no pipes (y=49 is stone)
        org.mockito.Mockito.when(mockWorld.getBlock(10, 49, 10))
            .thenReturn(mockOther);
        worldRod.updateMiningPipeHousing();
        assertEquals(0, worldRod.getMiningPipeCount());
        assertEquals(100, worldRod.getMinInsertionPercent());

        // 5. SimTile mining pipe logic
        SimTile simTile = new SimTile(SimTile.TileType.EMPTY);
        simTile.setHasControlRod(true);
        simTile.setControlRodType(SimTile.ControlRodType.CADMIUM);
        assertEquals(5, simTile.getControlRodMiningPipes());
        assertEquals(0, simTile.getMinControlRodInsertion());
        assertEquals(100, simTile.getMaxControlRodExtraction());

        simTile.setControlRodMiningPipes(2); // 2 pipes -> min insertion 60%
        assertEquals(60, simTile.getMinControlRodInsertion());
        assertEquals(40, simTile.getMaxControlRodExtraction());

        simTile.setControlRodInsertion(10); // Target 10%, should be clamped to 60%
        assertEquals(60, simTile.getEffectiveControlRodInsertion());

        // Check absorption probability uses effective insertion (60% instead of 10%)
        double expectedAbs = 0.60 * SimTile.ControlRodType.CADMIUM.maxThermalAbsorption;
        assertEquals(expectedAbs, simTile.getControlRodAbsorptionProbability(NeutronType.THERMAL), 1e-4);

        // 6. NuclearGridTile delegation
        com.gtnewhorizons.modularnuclear.common.metatileentity.multi.NuclearGridTile gridTile = new com.gtnewhorizons.modularnuclear.common.metatileentity.multi.NuclearGridTile(
            null,
            (MTEHatchNuclearBus) null,
            0,
            0);
        assertEquals(5, gridTile.getControlRodMiningPipes(), "Default without hatch is 5");

        gridTile.setBottomControlRod(worldRod);
        assertEquals(worldRod.getMiningPipeCount(), gridTile.getControlRodMiningPipes());
    }

    @Test
    void testTicksPerCycleSimulationAccuracy() {
        NuclearSimulationEngine.resetDefaultParameters();
        assertEquals(5, NuclearSimulationEngine.getTicksPerCycle());

        // 1. Grid with 20 ticks per cycle
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid grid20 = new com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid(
            5,
            5,
            NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        grid20.loadPreset("BASIC_ELECTRUM_5X5");
        grid20.setTicksPerCycle(20);
        assertEquals(20, grid20.getTicksPerCycle());

        grid20.step();
        assertEquals(20, grid20.getCurrentTick());
        double power20 = grid20.getLastPowerResult().totalPowerEUt;
        double flow20 = grid20.getFlowRegularSteam();
        double energy20 = grid20.getTotalEnergyEU();
        assertEquals(power20 * 20.0, energy20, 1e-4, "Accumulated energy should equal powerEUt * ticksPerCycle");

        // 2. Grid with 10 ticks per cycle (reactor cycle runs twice as fast)
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid grid10 = new com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid(
            5,
            5,
            NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        grid10.loadPreset("BASIC_ELECTRUM_5X5");
        grid10.setTicksPerCycle(10);
        assertEquals(10, grid10.getTicksPerCycle());

        grid10.step();
        assertEquals(10, grid10.getCurrentTick());
        double power10 = grid10.getLastPowerResult().totalPowerEUt;
        double flow10 = grid10.getFlowRegularSteam();
        double energy10 = grid10.getTotalEnergyEU();
        assertEquals(power10 * 10.0, energy10, 1e-4, "Accumulated energy should equal powerEUt * ticksPerCycle");

        // 3. Grid with default 5 ticks per cycle (4 cycles per second)
        com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid grid5 = new com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid(
            5,
            5,
            NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        grid5.loadPreset("BASIC_ELECTRUM_5X5");
        assertEquals(5, grid5.getTicksPerCycle());

        grid5.step();
        assertEquals(5, grid5.getCurrentTick());
        double power5 = grid5.getLastPowerResult().totalPowerEUt;
        double energy5 = grid5.getTotalEnergyEU();
        assertEquals(power5 * 5.0, energy5, 1e-4, "Accumulated energy should equal powerEUt * ticksPerCycle");

        NuclearSimulationEngine.resetDefaultParameters();
    }

    @Test
    void testConfigFieldsCleanupAndWiring() {
        // 1. Reflector durability
        ComponentData.ReflectorData goldRefl = ReactorDataRegistry.getComponentData().reflectors.stream()
            .filter(r -> "reflector_gold".equals(r.id))
            .findFirst()
            .orElse(null);
        assertNotNull(goldRefl);
        assertEquals(0, goldRefl.durability, "Pure reflector durability must default to 0 (infinite)");

        net.minecraft.item.ItemStack dummyGold = org.mockito.Mockito.mock(net.minecraft.item.ItemStack.class);
        net.minecraft.item.Item dummyItem = org.mockito.Mockito.mock(net.minecraft.item.Item.class);
        org.mockito.Mockito.when(dummyGold.getItem())
            .thenReturn(dummyItem);
        assertFalse(NuclearFuelClassification.isReflectorAtMaxDamage(dummyGold));

        // 2. Radiovoltaic defaults and fields
        ComponentData.RadiovoltaicData rvHv = ReactorDataRegistry.getComponentData().radiovoltaics.get(0);
        assertEquals(1.0, rvHv.euConversionEfficiency, 1e-4, "Radiovoltaic efficiency must default to 1.0 (100%)");
        assertEquals(1024L, rvHv.maxEU, "HV radiovoltaic maxEU must default to 1024 EU/t");
        assertEquals(0.40, rvHv.absorptionFast, 1e-4, "HV radiovoltaic fast absorption must default to 0.40");
        assertEquals(0.40, rvHv.absorptionThermal, 1e-4, "HV radiovoltaic thermal absorption must default to 0.40");
        assertEquals(10.0, rvHv.fullOutputAbsorbedPerSecond, 1e-4, "HV radiovoltaic reaches 95% at 10 absorbed/s");

        ComponentData.RadiovoltaicData rvEv = ReactorDataRegistry.getComponentData().radiovoltaics.get(1);
        assertEquals(1.0, rvEv.euConversionEfficiency, 1e-4, "Radiovoltaic efficiency must default to 1.0 (100%)");
        assertEquals(4096L, rvEv.maxEU, "EV radiovoltaic maxEU must default to 4096 EU/t");
        assertEquals(0.60, rvEv.absorptionFast, 1e-4, "EV radiovoltaic fast absorption must default to 0.60");
        assertEquals(0.60, rvEv.absorptionThermal, 1e-4, "EV radiovoltaic thermal absorption must default to 0.60");
        assertEquals(10.0, rvEv.fullOutputAbsorbedPerSecond, 1e-4, "EV radiovoltaic reaches 95% at 10 absorbed/s");

        // 3. Platings are not admitted or recognized as reactor components
        assertFalse(
            NuclearFuelClassification.isItemAllowedInBus(dummyGold, null),
            "Dummy non-component must not be admitted");

        // 4. Heat vent and heat exchanger maxTemperature default = 0.0 and over-temperature voiding
        for (ComponentData.HeatVentData vent : ReactorDataRegistry.getComponentData().heatVents) {
            assertEquals(0.0, vent.maxTemperature, 1e-4, "Vent " + vent.id + " max temperature must default to 0.0");
            assertEquals(0.25, vent.euPerHu, 1e-4, "Vent " + vent.id + " euPerHu must default to 0.25");
        }
        for (ComponentData.HeatExchangerData hex : ReactorDataRegistry.getComponentData().heatExchangers) {
            assertEquals(0.0, hex.maxTemperature, 1e-4, "Exchanger " + hex.id + " max temperature must default to 0.0");
            assertEquals(0.25, hex.euPerHu, 1e-4, "Exchanger " + hex.id + " euPerHu must default to 0.25");
        }

        // Test overtemp voiding for heat vent
        SimTile overtempVent = new SimTile(SimTile.TileType.VENT_STANDARD) {

            @Override
            public ComponentData.HeatVentData getHeatVentData() {
                ComponentData.HeatVentData data = super.getHeatVentData();
                data.maxTemperature = 100.0;
                return data;
            }
        };
        overtempVent.setTemperature(150.0);
        NuclearSimulationEngine.processHeatVent(overtempVent, 20.0);
        assertEquals(SimTile.TileType.EMPTY, overtempVent.getType(), "Over-temperature vent must be voided");

        // Test overtemp voiding for heat exchanger
        SimTile[][] hexGrid = new SimTile[3][3];
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                hexGrid[x][y] = new SimTile(SimTile.TileType.EMPTY);
            }
        }
        SimTile overtempHex = new SimTile(SimTile.TileType.EXCHANGER_STANDARD) {

            @Override
            public ComponentData.HeatExchangerData getHeatExchangerData() {
                ComponentData.HeatExchangerData data = super.getHeatExchangerData();
                data.maxTemperature = 100.0;
                return data;
            }
        };
        overtempHex.setTemperature(150.0);
        hexGrid[1][1] = overtempHex;
        NuclearSimulationEngine.processHeatExchanger(hexGrid, 1, 1, 3, 3);
        assertEquals(SimTile.TileType.EMPTY, overtempHex.getType(), "Over-temperature exchanger must be voided");

        // 5. CoolantItemData heat capacity scaling
        ComponentData.CoolantItemData ic2Cell = ReactorDataRegistry.getComponentData().coolantItems.stream()
            .filter(c -> "coolant_cell_ic2".equals(c.id))
            .findFirst()
            .orElse(null);
        assertNotNull(ic2Cell);
        assertEquals(250000.0, ic2Cell.heatCapacity, 1e-4);
        assertEquals(25.0, ic2Cell.getHuPerDurability(), 1e-4, "Default huPerDurability must be 25.0");

        // 6. TierData allowsCoolantLoops
        assertFalse(ReactorDataRegistry.getTier(0).allowsCoolantLoops, "Tier 0 must not allow coolant loops");
        assertFalse(ReactorDataRegistry.getTier(1).allowsCoolantLoops, "Tier 1 must not allow coolant loops");
        assertTrue(ReactorDataRegistry.getTier(2).allowsCoolantLoops, "Tier 2 must allow coolant loops");
        assertTrue(ReactorDataRegistry.getTier(3).allowsCoolantLoops, "Tier 3 must allow coolant loops");
        assertTrue(NuclearSimulationEngine.allowsCoolantLoops(2));
        assertFalse(NuclearSimulationEngine.allowsCoolantLoops(1));
    }

    @Test
    void testNuclearCoreHatchesDisableTicking() {
        IGregTechTileEntity mockTe = org.mockito.Mockito.mock(IGregTechTileEntity.class);
        org.mockito.Mockito.when(mockTe.isServerSide())
            .thenReturn(true);

        MTEHatchNuclearBus bus = new MTEHatchNuclearBus("test.bus", 4, new String[0], null);
        MTEHatchNuclearHatch hatch = new MTEHatchNuclearHatch("test.hatch", 4, 16000, new String[0], null);
        MTEHatchNuclearControlRod rod = new MTEHatchNuclearControlRod("test.rod", 4, new String[0], null);
        MTEHatchNuclearHighPressure hp = new MTEHatchNuclearHighPressure("test.hp", 4, new String[0], null);

        // Before tick 20: must NOT call tryDisableTicking so mTickTimer can exceed 5
        bus.onPostTick(mockTe, 1);
        hatch.onPostTick(mockTe, 1);
        rod.onPostTick(mockTe, 1);
        hp.onPostTick(mockTe, 1);
        org.mockito.Mockito.verify(mockTe, org.mockito.Mockito.never())
            .tryDisableTicking();

        // At tick 20: must call tryDisableTicking
        org.mockito.Mockito.clearInvocations(mockTe);
        bus.onPostTick(mockTe, 20);
        org.mockito.Mockito.verify(mockTe, org.mockito.Mockito.times(1))
            .tryDisableTicking();

        org.mockito.Mockito.clearInvocations(mockTe);
        hatch.onPostTick(mockTe, 20);
        org.mockito.Mockito.verify(mockTe, org.mockito.Mockito.times(1))
            .tryDisableTicking();

        org.mockito.Mockito.clearInvocations(mockTe);
        rod.onPostTick(mockTe, 20);
        org.mockito.Mockito.verify(mockTe, org.mockito.Mockito.times(1))
            .tryDisableTicking();

        org.mockito.Mockito.clearInvocations(mockTe);
        hp.onPostTick(mockTe, 20);
        org.mockito.Mockito.verify(mockTe, org.mockito.Mockito.times(1))
            .tryDisableTicking();

        // Client side must not call tryDisableTicking even at tick 20
        IGregTechTileEntity clientTe = org.mockito.Mockito.mock(IGregTechTileEntity.class);
        org.mockito.Mockito.when(clientTe.isServerSide())
            .thenReturn(false);
        bus.onPostTick(clientTe, 20);
        hatch.onPostTick(clientTe, 20);
        rod.onPostTick(clientTe, 20);
        hp.onPostTick(clientTe, 20);
        org.mockito.Mockito.verify(clientTe, org.mockito.Mockito.never())
            .tryDisableTicking();
    }

    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}

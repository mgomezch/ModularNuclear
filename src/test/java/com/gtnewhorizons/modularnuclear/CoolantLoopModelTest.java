package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.CoolantLoopModel;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid;

public class CoolantLoopModelTest {

    private StandaloneNuclearGrid grid;
    private CoolantLoopModel loop;

    @BeforeEach
    void setUp() {
        NuclearSimulationEngine.resetDefaultParameters();
        grid = new StandaloneNuclearGrid(7, 7, NuclearSimulationEngine.PIPE_TIER_OSMIUM);
        grid.loadLayout(
            "U4,U4,U4,M4,U4,U4,U4;U4,M4,HD,HD,HD,M4,U4;T4,HD,M4,HD,M4,HD,T4;U4,M2,HD,HD,HD,M2,U4;T4,HD,M4,HD,M4,HD,T4;U4,M4,HD,HD,HD,M4,U4;U4,U4,U4,M4,U4,U4,U4");
        loop = grid.getCoolantLoop();
    }

    @Test
    void testHydrodynamicPolynomialScaling() {
        // Zero flow gives approximately c0
        double pZero = loop.calculatePumpPowerForFlow(0.0);
        assertTrue(pZero >= 0.0);

        // At 50 L/s: mechanical power is ~175 EU/t; electrical power is pMech / eff
        double p50 = loop.calculatePumpPowerForFlow(50.0);
        double eff = loop.getImpellerEfficiency();
        double pMech50 = p50 * eff;
        assertTrue(
            pMech50 > 150.0 && pMech50 < 250.0,
            "Mechanical pump power at 50 L/s should be ~175 EU/t, got " + pMech50);

        // At 100 L/s: mechanical power is ~1390 EU/t
        double p100 = loop.calculatePumpPowerForFlow(100.0);
        double pMech100 = p100 * eff;
        assertTrue(
            pMech100 > 1300.0 && pMech100 < 1500.0,
            "Mechanical pump power at 100 L/s should be ~1390 EU/t, got " + pMech100);

        // Inversion check: flow from pump power
        double qCalculated = loop.calculateFlowFromPumpPower(p50);
        assertEquals(50.0, qCalculated, 0.5, "Inverted flow should match 50 L/s");
    }

    @Test
    void testPumpOverclockAndImpellerEfficiency() {
        assertFalse(loop.isPumpOverclocked(), "Pump should not be overclocked by default");
        assertEquals(
            com.gtnewhorizons.modularnuclear.common.nuclear.standalone.TurbineCalculator.TurbineMaterial.ORINARUKON,
            loop.getImpellerMaterial());

        // Higher efficiency impeller requires less electrical power for the same flow
        loop.setImpellerMaterial("ICHORIUM"); // 2.25 eff vs Oriharukon 1.55 eff
        assertEquals(2.25, loop.getImpellerEfficiency(), 0.001);
        double pIchorium = loop.calculatePumpPowerForFlow(50.0);

        loop.setImpellerMaterial("ORINARUKON");
        assertEquals(1.55, loop.getImpellerEfficiency(), 0.001);
        double pOriharukon = loop.calculatePumpPowerForFlow(50.0);

        assertTrue(pIchorium < pOriharukon, "Higher efficiency impeller must require less electrical EU/t");
        assertEquals(
            pOriharukon * (1.55 / 2.25),
            pIchorium,
            0.1,
            "Electrical power should scale inversely with impeller efficiency");

        // Overclocking toggle increases max pump power draw to 4A
        grid.setCoolingMode(CoolantLoopModel.CoolingMode.CONVECTIVE_LOOP);
        loop.attachPoint(2, 2);
        loop.setHatchTier(CoolantLoopModel.EnergyHatchTier.IV); // 8192 EU/t
        loop.setPumpOverclocked(false);
        loop.setDutyCyclePercent(100.0);
        assertEquals(8192.0, loop.getLastPumpPowerEUt(), 1.0, "Normal pump draws 1A immediately");
        grid.step();
        assertEquals(8192.0, loop.getLastPumpPowerEUt(), 1.0, "Normal pump draws 1A (8192 EU/t at IV)");

        loop.setPumpOverclocked(true);
        assertTrue(loop.isPumpOverclocked());
        assertEquals(32768.0, loop.getLastPumpPowerEUt(), 1.0, "Overclocked pump draws 4A immediately without step");
        grid.step();
        assertEquals(32768.0, loop.getLastPumpPowerEUt(), 1.0, "Overclocked pump draws 4A (32768 EU/t at IV)");
    }

    @Test
    void testReactorMaterialRestrictions() {
        // Electrum (EV) and Platinum (IV) reject all loop materials
        int tierEV = NuclearSimulationEngine.PIPE_TIER_ELECTRUM;
        int tierIV = NuclearSimulationEngine.PIPE_TIER_PLATINUM;
        int tierLuV = NuclearSimulationEngine.PIPE_TIER_OSMIUM;
        int tierZPM = NuclearSimulationEngine.PIPE_TIER_QUANTIUM;

        assertFalse(CoolantLoopModel.LoopMaterial.CAST_IRON.isAllowedInReactorTier(tierEV));
        assertFalse(CoolantLoopModel.LoopMaterial.STEEL.isAllowedInReactorTier(tierEV));
        assertFalse(CoolantLoopModel.LoopMaterial.CAST_IRON.isAllowedInReactorTier(tierIV));
        assertFalse(CoolantLoopModel.LoopMaterial.STEEL.isAllowedInReactorTier(tierIV));
        assertFalse(CoolantLoopModel.LoopMaterial.OSMIUM.isAllowedInReactorTier(tierIV));

        // In Osmium (LuV) and above, loop materials are physically allowed
        assertTrue(CoolantLoopModel.LoopMaterial.CAST_IRON.isAllowedInReactorTier(tierLuV));
        assertTrue(CoolantLoopModel.LoopMaterial.STEEL.isAllowedInReactorTier(tierLuV));
        assertTrue(CoolantLoopModel.LoopMaterial.STAINLESS_STEEL.isAllowedInReactorTier(tierLuV));
        assertTrue(CoolantLoopModel.LoopMaterial.TITANIUM.isAllowedInReactorTier(tierLuV));
        assertTrue(CoolantLoopModel.LoopMaterial.TUNGSTENSTEEL.isAllowedInReactorTier(tierLuV));
        assertTrue(CoolantLoopModel.LoopMaterial.OSMIUM.isAllowedInReactorTier(tierLuV));
        assertTrue(
            CoolantLoopModel.LoopMaterial.NEUTRONIUM.isAllowedInReactorTier(tierLuV),
            "Neutronium is physically allowed on LuV");

        // Progression appropriate check (for automated optimization searches):
        assertFalse(CoolantLoopModel.LoopMaterial.CAST_IRON.isProgressionAppropriate(tierEV));
        assertFalse(CoolantLoopModel.LoopMaterial.STEEL.isProgressionAppropriate(tierEV));
        assertFalse(CoolantLoopModel.LoopMaterial.CAST_IRON.isProgressionAppropriate(tierIV));
        assertFalse(CoolantLoopModel.LoopMaterial.STEEL.isProgressionAppropriate(tierIV));

        // In LuV, Osmium and below are progression-appropriate, but Neutronium is excluded
        assertTrue(CoolantLoopModel.LoopMaterial.CAST_IRON.isProgressionAppropriate(tierLuV));
        assertTrue(CoolantLoopModel.LoopMaterial.STEEL.isProgressionAppropriate(tierLuV));
        assertTrue(CoolantLoopModel.LoopMaterial.STAINLESS_STEEL.isProgressionAppropriate(tierLuV));
        assertTrue(CoolantLoopModel.LoopMaterial.TITANIUM.isProgressionAppropriate(tierLuV));
        assertTrue(CoolantLoopModel.LoopMaterial.TUNGSTENSTEEL.isProgressionAppropriate(tierLuV));
        assertTrue(CoolantLoopModel.LoopMaterial.OSMIUM.isProgressionAppropriate(tierLuV));
        assertFalse(
            CoolantLoopModel.LoopMaterial.NEUTRONIUM.isProgressionAppropriate(tierLuV),
            "Neutronium is not progression-appropriate for LuV optimization searches");

        // In Tier 3+ (ZPM+), Neutronium and Osmium are progression-appropriate
        assertTrue(CoolantLoopModel.LoopMaterial.OSMIUM.isProgressionAppropriate(tierZPM));
        assertTrue(CoolantLoopModel.LoopMaterial.NEUTRONIUM.isProgressionAppropriate(tierZPM));
    }

    @Test
    void testOsmiumAllowedAndOperationalInLuV() {
        StandaloneNuclearGrid luvGrid = new StandaloneNuclearGrid(9, 9, NuclearSimulationEngine.PIPE_TIER_OSMIUM);
        luvGrid.setCoolingMode(CoolantLoopModel.CoolingMode.CONVECTIVE_LOOP);
        CoolantLoopModel luvLoop = luvGrid.getCoolantLoop();
        luvLoop.setMaterial(CoolantLoopModel.LoopMaterial.OSMIUM);
        luvLoop.setPipeSize(CoolantLoopModel.LoopPipeSize.LARGE);
        luvLoop.setFluidType(CoolantLoopModel.CoolantFluidType.DISTILLED_WATER);
        luvLoop.setPumpPowerEUt(32768.0);
        luvLoop.attachPoint(2, 2);
        luvLoop.attachPoint(2, 3);

        for (int t = 1; t <= 20; t++) {
            boolean ok = luvGrid.step();
            assertTrue(ok, "Grid step should succeed with Osmium loop on LuV");
            assertFalse(luvGrid.isExploded(), "Grid should not explode");
            assertFalse(luvLoop.isRuptured(), "Osmium loop should not rupture on LuV");
        }

        assertEquals(CoolantLoopModel.LoopMaterial.OSMIUM, luvLoop.getMaterial());
        assertEquals(600.0, luvLoop.getMaterial().maxPressureBar);
        assertEquals("LuV", luvLoop.getMaterial().tierUnlocked);
    }

    @Test
    void testNeutroniumAllowedAndOperationalInTier2() {
        grid.setCoolingMode(CoolantLoopModel.CoolingMode.CONVECTIVE_LOOP);
        loop.setMaterial(CoolantLoopModel.LoopMaterial.NEUTRONIUM);
        loop.setPipeSize(CoolantLoopModel.LoopPipeSize.NORMAL);
        loop.setFluidType(CoolantLoopModel.CoolantFluidType.DISTILLED_WATER);
        loop.setPumpPowerEUt(500.0);
        loop.attachPoint(2, 2);
        loop.attachPoint(2, 3);
        loop.attachPoint(4, 2);
        loop.attachPoint(4, 3);

        for (int t = 1; t <= 20; t++) {
            boolean ok = grid.step();
            assertTrue(ok, "Grid step should succeed with Neutronium loop on Tier 2");
            assertFalse(grid.isExploded(), "Grid should not explode");
            assertFalse(loop.isRuptured(), "Neutronium loop should not rupture on Tier 2");
        }

        assertEquals(CoolantLoopModel.LoopMaterial.NEUTRONIUM, loop.getMaterial());
        assertTrue(loop.getLastHeatExtractedEUt() > 0, "Neutronium loop should extract heat");
        assertTrue(loop.getMaterial().maxPressureBar >= 3000.0, "Neutronium should have 3000 bar rating");
    }

    @Test
    void testConvectiveCoolingTierGating() {
        StandaloneNuclearGrid electrumGrid = new StandaloneNuclearGrid(5, 5, NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        assertFalse(electrumGrid.isTier2ConvectiveAllowed());
        assertFalse(
            electrumGrid.setCoolingMode(CoolantLoopModel.CoolingMode.CONVECTIVE_LOOP),
            "Convective cooling mode must reject Electrum (EV)");
        assertEquals(CoolantLoopModel.CoolingMode.MODULAR, electrumGrid.getCoolingMode());

        StandaloneNuclearGrid platinumGrid = new StandaloneNuclearGrid(7, 7, NuclearSimulationEngine.PIPE_TIER_PLATINUM);
        assertFalse(platinumGrid.isTier2ConvectiveAllowed());
        assertFalse(
            platinumGrid.setCoolingMode(CoolantLoopModel.CoolingMode.CONVECTIVE_LOOP),
            "Convective cooling mode must reject Platinum (IV)");
        assertEquals(CoolantLoopModel.CoolingMode.MODULAR, platinumGrid.getCoolingMode());

        // Osmium on 5x5 must be allowed!
        StandaloneNuclearGrid osmiumSmallGrid = new StandaloneNuclearGrid(5, 5, NuclearSimulationEngine.PIPE_TIER_OSMIUM);
        assertTrue(osmiumSmallGrid.isTier2ConvectiveAllowed(), "Osmium on 5x5 must allow convective loop");
        assertTrue(
            osmiumSmallGrid.setCoolingMode(CoolantLoopModel.CoolingMode.CONVECTIVE_LOOP),
            "Convective cooling mode must accept Osmium (LuV) even on 5x5");
        assertEquals(CoolantLoopModel.CoolingMode.CONVECTIVE_LOOP, osmiumSmallGrid.getCoolingMode());
    }

    @Test
    void testConvectiveLoopHeatExtractionAndSecondarySteam() {
        grid.setCoolingMode(CoolantLoopModel.CoolingMode.CONVECTIVE_LOOP);
        loop.setMaterial(CoolantLoopModel.LoopMaterial.TITANIUM);
        loop.setPipeSize(CoolantLoopModel.LoopPipeSize.NORMAL);
        loop.setFluidType(CoolantLoopModel.CoolantFluidType.DISTILLED_WATER);
        loop.setPumpPowerEUt(250.0);

        // Attach core passage cells
        loop.clearAttachedPoints();
        loop.attachPoint(2, 2);
        loop.attachPoint(2, 3);
        loop.attachPoint(2, 4);

        // Step 20 ticks
        for (int t = 1; t <= 20; t++) {
            boolean ok = grid.step();
            assertTrue(ok, "Grid step should succeed");
            assertFalse(grid.isExploded(), "Grid should not explode");
            assertFalse(loop.isRuptured(), "Loop should not rupture");
        }

        assertTrue(loop.getLastHeatExtractedEUt() > 0, "Loop should extract heat from attached cells");
        assertTrue(loop.getCurrentCoolantTempCelsius() > 20.0, "Coolant should warm up");
        assertTrue(grid.getGrossPowerEUt() > 0, "Gross power should be positive");
        assertTrue(grid.getPumpPowerEUt() > 0, "Pump power should be subtracted");
        assertEquals(
            grid.getGrossPowerEUt() - grid.getPumpPowerEUt(),
            grid.getLastPowerResult().totalPowerEUt,
            0.01,
            "Net power should equal gross minus pump draw");
    }

    @Test
    void testRadiolyticByproductsHeavyWater() {
        grid.setCoolingMode(CoolantLoopModel.CoolingMode.CONVECTIVE_LOOP);
        loop.setMaterial(CoolantLoopModel.LoopMaterial.TUNGSTENSTEEL);
        loop.setFluidType(CoolantLoopModel.CoolantFluidType.HEAVY_WATER);
        loop.setPumpPowerEUt(200.0);
        loop.attachPoint(2, 2);
        loop.attachPoint(2, 3);
        loop.attachPoint(4, 2);
        loop.attachPoint(4, 3);

        for (int t = 1; t <= 30; t++) {
            grid.step();
        }

        assertTrue(loop.getTotalTritiumProduced() > 0, "Heavy water coolant loop should produce Tritium byproduct");
        assertEquals(0, loop.getTotalDeuteriumProduced(), "Heavy water loop should produce 0 Deuterium");
    }

    @Test
    void testPressureBurstLimit() {
        grid.setCoolingMode(CoolantLoopModel.CoolingMode.CONVECTIVE_LOOP);
        // Steel has 35 bar max pressure
        loop.setMaterial(CoolantLoopModel.LoopMaterial.STEEL);
        loop.setPipeSize(CoolantLoopModel.LoopPipeSize.TINY); // Tiny pipe -> very high fluid velocity and pressure!
        loop.setCirculationFlowLs(150.0); // Extreme flow in tiny pipe

        boolean stepResult = grid.step();
        assertFalse(stepResult, "Extreme flow in tiny pipe should exceed burst limit");
        assertTrue(grid.isExploded());
        assertTrue(loop.isRuptured());
        assertTrue(
            loop.getRuptureReason()
                .contains("Coolant Loop Burst"));
    }

    @Test
    void testPassageCoreTileRecognition() {
        grid.setCoolingMode(CoolantLoopModel.CoolingMode.CONVECTIVE_LOOP);
        loop.clearAttachedPoints();
        assertEquals(
            0,
            loop.getAttachedPoints()
                .size());

        // Place a PASSAGE_CORE cell
        grid.setTile(3, 3, SimTile.TileType.PASSAGE_CORE);
        assertTrue(
            grid.getTile(3, 3)
                .isCoolantPassage());

        grid.step();
        assertTrue(
            loop.getLastHeatExtractedEUt() > 0,
            "PASSAGE_CORE tile should automatically be cooled by convective loop");
    }

    @Test
    void testPerCellModularCooling() {
        // Grid starts in default MODULAR mode without needing global mode switches
        assertEquals(CoolantLoopModel.CoolingMode.MODULAR, grid.getCoolingMode());

        grid.clearGrid();
        // (1, 1): Fuel rod cooled by (1, 2) conductive boiling hatch
        grid.setTile(1, 1, SimTile.TileType.FUEL_URANIUM_QUAD);
        grid.setTile(1, 2, SimTile.TileType.HATCH_DISTILLED_WATER);
        grid.getTile(1, 2)
            .setInputFluidAmount(8000);
        grid.getTile(1, 2)
            .setAutoRefill(true);
        grid.getTile(1, 1)
            .setTemperature(600.0);
        grid.getTile(1, 2)
            .setTemperature(200.0);

        // (3, 3): Fuel rod cooled by convective loop via attached point and (3, 4) Passage Core
        grid.setTile(3, 3, SimTile.TileType.FUEL_URANIUM_QUAD);
        grid.setTile(3, 4, SimTile.TileType.PASSAGE_CORE);
        loop.attachPoint(3, 3);

        // (5, 5): Fuel rod cooled by (5, 4) Advanced Heat Vent (IC2 cooling)
        grid.setTile(5, 5, SimTile.TileType.FUEL_URANIUM_QUAD);
        grid.setTile(5, 4, SimTile.TileType.VENT_ADVANCED);

        // (5, 1): Uncooled Fuel rod
        grid.setTile(5, 1, SimTile.TileType.FUEL_URANIUM_QUAD);

        // Configure loop
        loop.setMaterial(CoolantLoopModel.LoopMaterial.TITANIUM);
        loop.setPipeSize(CoolantLoopModel.LoopPipeSize.NORMAL);
        loop.setFluidType(CoolantLoopModel.CoolantFluidType.DISTILLED_WATER);
        loop.setPumpPowerEUt(250.0);
        loop.setCurrentCoolantTempCelsius(120.0);
        grid.getTile(3, 3)
            .setTemperature(600.0);
        grid.getTile(3, 4)
            .setTemperature(500.0);

        assertTrue(grid.isCoolantLoopActive(), "Grid should recognize PASSAGE_CORE and activate loop");
        assertTrue(grid.shouldStepCoolantLoop(), "Grid should step coolant loop in MODULAR mode");

        // Run 20 ticks
        for (int t = 1; t <= 20; t++) {
            boolean ok = grid.step();
            assertTrue(ok, "Grid step should succeed");
            assertFalse(grid.isExploded(), "Grid should not explode");
        }

        // 1. Conductive boiling hatch boiled water
        assertTrue(
            grid.getTile(1, 2)
                .getTotalSteamProduced() > 0,
            "Boiling hatch should produce conductive steam");

        // 2. Convective loop extracted heat and produced secondary steam
        assertTrue(loop.getTotalHeatExtractedHut() > 0, "Loop should convectively extract heat");
        assertTrue(loop.getTotalSecondarySteamProduced() > 0, "Loop should produce secondary steam");

        // 3. Steam generation accounts for BOTH conductive hatch steam and loop secondary steam
        assertTrue(
            grid.getTotalSteamProduced() > grid.getTile(1, 2)
                .getTotalSteamProduced(),
            "Total steam should include both conductive hatch and convective loop steam");

        // 4. IC2 vent tile actively dissipates heat
        assertTrue(
            grid.getTile(5, 4)
                .getTemperature() > NuclearSimulationEngine.AMBIENT_TEMP,
            "Heat vent should absorb and dissipate heat");

        // 5. Uncooled fuel rod heats up freely via fission
        assertTrue(
            grid.getTile(5, 1)
                .getTemperature() > 100.0,
            "Uncooled fuel rod should heat up without direct cooling");

        // 6. Parasitic power is subtracted from gross output
        assertTrue(grid.getPumpPowerEUt() > 0, "Active loop should consume pump power");
        assertEquals(
            Math.max(0.0, grid.getGrossPowerEUt() - grid.getPumpPowerEUt()),
            grid.getLastPowerResult().totalPowerEUt,
            0.01);
    }

    @Test
    void testMoltenCheeseFluidPropertiesAndNoRadiolysis() {
        loop.setFluidType(CoolantLoopModel.CoolantFluidType.MOLTEN_CHEESE);
        assertEquals("Molten Cheese", loop.getFluidType().displayName);
        assertEquals(1120.0, loop.getFluidType().density, 0.01);
        assertEquals(3000.0, loop.getFluidType().specificHeat, 0.01);
        assertEquals(0.557, loop.getFluidType().dynamicViscosity, 0.001);
        assertEquals(0.481, loop.getFluidType().thermalConductivity, 0.001);
        assertEquals("None", loop.getFluidType().byproductGas);

        assertEquals(
            CoolantLoopModel.CoolantFluidType.MOLTEN_CHEESE,
            CoolantLoopModel.CoolantFluidType.fromString("cheese"));
        assertEquals(
            CoolantLoopModel.CoolantFluidType.MOLTEN_CHEESE,
            CoolantLoopModel.CoolantFluidType.fromString("MOLTEN_CHEESE"));

        // Set hot ambient biome (Nether) for molten cheese circulation
        NuclearSimulationEngine.ambientTemp = 50.0;
        loop.setCurrentCoolantTempCelsius(50.0);
        grid.setTile(3, 4, SimTile.TileType.PASSAGE_CORE);
        grid.setTile(3, 3, SimTile.TileType.FUEL_URANIUM_QUAD);
        loop.attachPoint(2, 2);
        loop.attachPoint(2, 3);
        loop.setPumpPowerEUt(250.0);

        for (int t = 1; t <= 30; t++) {
            grid.step();
        }
        assertEquals(0, loop.getTotalMethaneProduced(), "Molten cheese loop must not produce radiolytic methane");
        assertEquals(0, loop.getTotalDeuteriumProduced(), "Molten cheese loop must not produce deuterium");
        assertEquals(0, loop.getTotalTritiumProduced(), "Molten cheese loop must not produce tritium");

        // Distilled water produces deuterium
        NuclearSimulationEngine.resetDefaultParameters();
        loop.setFluidType(CoolantLoopModel.CoolantFluidType.DISTILLED_WATER);
        for (int t = 1; t <= 30; t++) {
            grid.step();
        }
        assertTrue(loop.getTotalDeuteriumProduced() > 0, "Distilled water loop must produce radiolytic deuterium");

        // Heavy water produces tritium
        loop.setFluidType(CoolantLoopModel.CoolantFluidType.HEAVY_WATER);
        for (int t = 1; t <= 30; t++) {
            grid.step();
        }
        assertTrue(loop.getTotalTritiumProduced() > 0, "Heavy water loop must produce radiolytic tritium");
    }

    @Test
    void testMoltenFluidTemperatureLimitsAndSolidificationRupture() {
        loop.setFluidType(CoolantLoopModel.CoolantFluidType.MOLTEN_CHEESE);
        assertEquals(46.85, loop.getFluidType().meltingPointCelsius, 0.01);
        assertTrue(
            loop.getFluidType()
                .isMolten());

        // 1. In standard biome (ambientTemp 20 °C < 46.85 °C), pump refuses to start
        loop.setPumpPowerEUt(250.0);
        loop.updatePumpState();
        assertEquals(0.0, loop.getCurrentFlowRateLPerSec(), 1e-5, "Pump must refuse to accelerate flow when cold");
        assertTrue(loop.isFlowLimited());
        assertTrue(
            loop.getLimitReason()
                .contains("Pump blocked"));
        assertTrue(
            loop.getLimitReason()
                .contains("Biome ambient temperature"));

        // 2. In hot biome (ambientTemp 50 °C > 46.85 °C) with warm coolant, flow is allowed
        NuclearSimulationEngine.ambientTemp = 50.0;
        loop.setCurrentCoolantTempCelsius(50.0);
        loop.updatePumpState();
        assertTrue(
            loop.getCurrentFlowRateLPerSec() > 0.0,
            "Flow must accelerate when above melting point in hot biome");

        // 3. If circulating and coolant drops below melting point, immediate catastrophic rupture!
        grid.setTile(3, 4, SimTile.TileType.PASSAGE_CORE);
        loop.setCurrentCoolantTempCelsius(30.0); // drops below 46.85 °C while circulating
        assertFalse(grid.step(), "Step should fail on rupture");
        assertTrue(loop.isRuptured(), "Circulating molten coolant below melting point must cause rupture");
        assertTrue(
            loop.getRuptureReason()
                .contains("Solidification"),
            "Rupture reason must mention solidification");
    }

    @Test
    void testCastIronMaterialPropertiesAndParsing() {
        assertEquals("Cast Iron", CoolantLoopModel.LoopMaterial.CAST_IRON.displayName);
        assertEquals(16.0, CoolantLoopModel.LoopMaterial.CAST_IRON.maxPressureBar, 1e-4);
        assertEquals(1538.0, CoolantLoopModel.LoopMaterial.CAST_IRON.maxTemperatureCelsius, 1e-4);
        assertEquals("ULV", CoolantLoopModel.LoopMaterial.CAST_IRON.tierUnlocked);

        assertEquals(CoolantLoopModel.LoopMaterial.CAST_IRON, CoolantLoopModel.LoopMaterial.fromString("CAST_IRON"));
        assertEquals(CoolantLoopModel.LoopMaterial.CAST_IRON, CoolantLoopModel.LoopMaterial.fromString("cast iron"));
        assertEquals(CoolantLoopModel.LoopMaterial.CAST_IRON, CoolantLoopModel.LoopMaterial.fromString("Cast"));
        assertEquals(CoolantLoopModel.LoopMaterial.CAST_IRON, CoolantLoopModel.LoopMaterial.fromString("Iron"));
    }

    @Test
    void testAllCoolantFluidsPropertiesAndParsing() {
        // IC2 Coolant
        assertEquals(CoolantLoopModel.CoolantFluidType.IC2_COOLANT, CoolantLoopModel.CoolantFluidType.fromString("IC2_COOLANT"));
        assertEquals(CoolantLoopModel.CoolantFluidType.IC2_COOLANT, CoolantLoopModel.CoolantFluidType.fromString("coolant"));
        assertEquals("IC2 Coolant", CoolantLoopModel.CoolantFluidType.IC2_COOLANT.displayName);
        assertEquals(1080.0, CoolantLoopModel.CoolantFluidType.IC2_COOLANT.density, 1e-2);
        assertEquals(3800.0, CoolantLoopModel.CoolantFluidType.IC2_COOLANT.specificHeat, 1e-2);
        assertFalse(CoolantLoopModel.CoolantFluidType.IC2_COOLANT.isMolten());

        // Sodium
        assertEquals(CoolantLoopModel.CoolantFluidType.SODIUM, CoolantLoopModel.CoolantFluidType.fromString("SODIUM"));
        assertEquals(CoolantLoopModel.CoolantFluidType.SODIUM, CoolantLoopModel.CoolantFluidType.fromString("sodium"));
        assertEquals("Sodium", CoolantLoopModel.CoolantFluidType.SODIUM.displayName);
        assertEquals(927.0, CoolantLoopModel.CoolantFluidType.SODIUM.density, 1e-2);
        assertEquals(1260.0, CoolantLoopModel.CoolantFluidType.SODIUM.specificHeat, 1e-2);
        assertEquals(86.0, CoolantLoopModel.CoolantFluidType.SODIUM.thermalConductivity, 1e-2);
        assertEquals(97.8, CoolantLoopModel.CoolantFluidType.SODIUM.meltingPointCelsius, 1e-2);
        assertFalse(CoolantLoopModel.CoolantFluidType.SODIUM.isMolten());

        // Lava
        assertEquals(CoolantLoopModel.CoolantFluidType.LAVA, CoolantLoopModel.CoolantFluidType.fromString("LAVA"));
        assertEquals(CoolantLoopModel.CoolantFluidType.LAVA, CoolantLoopModel.CoolantFluidType.fromString("lava"));
        assertEquals("Lava", CoolantLoopModel.CoolantFluidType.LAVA.displayName);
        assertEquals(3000.0, CoolantLoopModel.CoolantFluidType.LAVA.density, 1e-2);
        assertEquals(1250.0, CoolantLoopModel.CoolantFluidType.LAVA.specificHeat, 1e-2);
        assertEquals(1000.0, CoolantLoopModel.CoolantFluidType.LAVA.meltingPointCelsius, 1e-2);
        assertTrue(CoolantLoopModel.CoolantFluidType.LAVA.isMolten());
    }

    @Test
    void testSecondarySteamGenerationLatentHeatEnergyConservation() {
        grid.setCoolingMode(CoolantLoopModel.CoolingMode.CONVECTIVE_LOOP);
        loop.setMaterial(CoolantLoopModel.LoopMaterial.TITANIUM);
        loop.setPipeSize(CoolantLoopModel.LoopPipeSize.NORMAL);
        loop.setFluidType(CoolantLoopModel.CoolantFluidType.DISTILLED_WATER);
        loop.setPumpPowerEUt(500.0);
        loop.attachPoint(2, 2);
        loop.attachPoint(2, 3);
        loop.attachPoint(2, 4);

        // Pre-heat coolant to 150 °C to trigger PHE secondary boiling (> 100 °C)
        loop.setCurrentCoolantTempCelsius(150.0);

        boolean ok = grid.step();
        assertTrue(ok, "Grid step should succeed");

        double waterBoiledL = loop.getLastSecondaryWaterBoiledLt();
        double steamProducedL = loop.getLastSecondarySteamProducedLt();
        assertTrue(waterBoiledL > 0.0, "Water boiled should be positive");
        assertEquals(waterBoiledL * 160.0, steamProducedL, 1e-3, "Steam produced should be exactly 160x water boiled");

        // Verify sensible order of magnitude:
        // Core generates around 1-10k HU/t (128-1280 kJ/t).
        // At 2.26 MJ/L (2,260,000 J/L), 1000 kJ vaporizes ~0.44 L/t of water -> ~70 L/t steam.
        // It must NOT be thousands of L/t of water boiled per tick!
        assertTrue(waterBoiledL < 10.0, "Water boiled per tick should be realistic (< 10 L/t), not 1000x inflated");
        assertTrue(steamProducedL < 1600.0, "Steam produced per tick should be realistic (< 1600 L/t)");
    }
}

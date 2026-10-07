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
        grid = new StandaloneNuclearGrid(7, 7, NuclearSimulationEngine.PIPE_TIER_PLATINUM);
        grid.loadPreset("BEST_PLATINUM_7X7");
        loop = grid.getCoolantLoop();
    }

    @Test
    void testHydrodynamicPolynomialScaling() {
        // Zero flow gives approximately c0
        double pZero = loop.calculatePumpPowerForFlow(0.0);
        assertTrue(pZero >= 0.0);

        // At 50 L/s
        double p50 = loop.calculatePumpPowerForFlow(50.0);
        assertTrue(p50 > 150.0 && p50 < 250.0, "Pump power at 50 L/s should be ~175 EU/t, got " + p50);

        // At 100 L/s
        double p100 = loop.calculatePumpPowerForFlow(100.0);
        assertTrue(p100 > 1300.0 && p100 < 1500.0, "Pump power at 100 L/s should be ~1390 EU/t, got " + p100);

        // Inversion check: flow from pump power
        double qCalculated = loop.calculateFlowFromPumpPower(p50);
        assertEquals(50.0, qCalculated, 0.5, "Inverted flow should match 50 L/s");
    }

    @Test
    void testTier2MaterialRestrictions() {
        // Tier 2 (IV Platinum / LuV Osmium)
        int tierIV = NuclearSimulationEngine.PIPE_TIER_PLATINUM;
        int tierLuV = NuclearSimulationEngine.PIPE_TIER_OSMIUM;
        int tierZPM = NuclearSimulationEngine.PIPE_TIER_QUANTIUM;

        // All materials MUST be physically allowed in Tier 2+
        assertTrue(CoolantLoopModel.LoopMaterial.STEEL.isAllowedInReactorTier(tierIV));
        assertTrue(CoolantLoopModel.LoopMaterial.STAINLESS_STEEL.isAllowedInReactorTier(tierIV));
        assertTrue(CoolantLoopModel.LoopMaterial.TITANIUM.isAllowedInReactorTier(tierIV));
        assertTrue(CoolantLoopModel.LoopMaterial.TUNGSTENSTEEL.isAllowedInReactorTier(tierIV));
        assertTrue(CoolantLoopModel.LoopMaterial.NEUTRONIUM.isAllowedInReactorTier(tierIV),
            "Neutronium is physically allowed on IV (sim app allows it)");

        assertTrue(CoolantLoopModel.LoopMaterial.STEEL.isAllowedInReactorTier(tierLuV));
        assertTrue(CoolantLoopModel.LoopMaterial.STAINLESS_STEEL.isAllowedInReactorTier(tierLuV));
        assertTrue(CoolantLoopModel.LoopMaterial.TITANIUM.isAllowedInReactorTier(tierLuV));
        assertTrue(CoolantLoopModel.LoopMaterial.TUNGSTENSTEEL.isAllowedInReactorTier(tierLuV));
        assertTrue(CoolantLoopModel.LoopMaterial.NEUTRONIUM.isAllowedInReactorTier(tierLuV),
            "Neutronium is physically allowed on LuV");

        // Progression appropriate check (for automated optimization searches):
        // In Tier 2, Neutronium is excluded from optimization searches because player would build ZPM+ reactor instead
        assertTrue(CoolantLoopModel.LoopMaterial.STEEL.isProgressionAppropriate(tierIV));
        assertTrue(CoolantLoopModel.LoopMaterial.STAINLESS_STEEL.isProgressionAppropriate(tierIV));
        assertTrue(CoolantLoopModel.LoopMaterial.TITANIUM.isProgressionAppropriate(tierIV));
        assertTrue(CoolantLoopModel.LoopMaterial.TUNGSTENSTEEL.isProgressionAppropriate(tierIV));
        assertFalse(CoolantLoopModel.LoopMaterial.NEUTRONIUM.isProgressionAppropriate(tierIV),
            "Neutronium is not progression-appropriate for Tier 2 optimization searches");
        assertFalse(CoolantLoopModel.LoopMaterial.NEUTRONIUM.isProgressionAppropriate(tierLuV),
            "Neutronium is not progression-appropriate for Tier 2 optimization searches");

        // In Tier 3+ (ZPM+), Neutronium is progression-appropriate
        assertTrue(CoolantLoopModel.LoopMaterial.NEUTRONIUM.isProgressionAppropriate(tierZPM));
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
    void testTier1ConvectiveCoolingRejection() {
        StandaloneNuclearGrid tier1Grid = new StandaloneNuclearGrid(5, 5, NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        assertFalse(tier1Grid.isTier2ConvectiveAllowed());
        assertFalse(tier1Grid.setCoolingMode(CoolantLoopModel.CoolingMode.CONVECTIVE_LOOP),
            "Convective cooling mode must reject Tier 1 (Electrum / EV)");
        assertEquals(CoolantLoopModel.CoolingMode.MODULAR, tier1Grid.getCoolingMode());
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
        assertEquals(grid.getGrossPowerEUt() - grid.getPumpPowerEUt(),
            grid.getLastPowerResult().totalPowerEUt, 0.01,
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
        assertTrue(loop.getRuptureReason().contains("Coolant Loop Burst"));
    }

    @Test
    void testPassageCoreTileRecognition() {
        grid.setCoolingMode(CoolantLoopModel.CoolingMode.CONVECTIVE_LOOP);
        loop.clearAttachedPoints();
        assertEquals(0, loop.getAttachedPoints().size());

        // Place a PASSAGE_CORE cell
        grid.setTile(3, 3, SimTile.TileType.PASSAGE_CORE);
        assertTrue(grid.getTile(3, 3).isCoolantPassage());

        grid.step();
        assertTrue(loop.getLastHeatExtractedEUt() > 0, "PASSAGE_CORE tile should automatically be cooled by convective loop");
    }

    @Test
    void testPerCellModularCooling() {
        // Grid starts in default MODULAR mode without needing global mode switches
        assertEquals(CoolantLoopModel.CoolingMode.MODULAR, grid.getCoolingMode());

        grid.clearGrid();
        // (1, 1): Fuel rod cooled by (1, 2) conductive boiling hatch
        grid.setTile(1, 1, SimTile.TileType.FUEL_URANIUM_QUAD);
        grid.setTile(1, 2, SimTile.TileType.HATCH_DISTILLED_WATER);
        grid.getTile(1, 2).setInputFluidAmount(8000);
        grid.getTile(1, 2).setAutoRefill(true);
        grid.getTile(1, 1).setTemperature(600.0);
        grid.getTile(1, 2).setTemperature(200.0);

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
        grid.getTile(3, 3).setTemperature(600.0);
        grid.getTile(3, 4).setTemperature(500.0);

        assertTrue(grid.isCoolantLoopActive(), "Grid should recognize PASSAGE_CORE and activate loop");
        assertTrue(grid.shouldStepCoolantLoop(), "Grid should step coolant loop in MODULAR mode");

        // Run 20 ticks
        for (int t = 1; t <= 20; t++) {
            boolean ok = grid.step();
            assertTrue(ok, "Grid step should succeed");
            assertFalse(grid.isExploded(), "Grid should not explode");
        }

        // 1. Conductive boiling hatch boiled water
        assertTrue(grid.getTile(1, 2).getTotalSteamProduced() > 0, "Boiling hatch should produce conductive steam");

        // 2. Convective loop extracted heat and produced secondary steam
        assertTrue(loop.getLastHeatExtractedEUt() > 0, "Loop should convectively extract heat");
        assertTrue(loop.getTotalSecondarySteamProduced() > 0, "Loop should produce secondary steam");

        // 3. Steam generation accounts for BOTH conductive hatch steam and loop secondary steam
        assertTrue(grid.getTotalSteamProduced() > grid.getTile(1, 2).getTotalSteamProduced(),
            "Total steam should include both conductive hatch and convective loop steam");

        // 4. IC2 vent tile actively dissipates heat
        assertTrue(grid.getTile(5, 4).getTemperature() > NuclearSimulationEngine.AMBIENT_TEMP, "Heat vent should absorb and dissipate heat");

        // 5. Uncooled fuel rod heats up freely via fission
        assertTrue(grid.getTile(5, 1).getTemperature() > 100.0, "Uncooled fuel rod should heat up without direct cooling");

        // 6. Parasitic power is subtracted from gross output
        assertTrue(grid.getPumpPowerEUt() > 0, "Active loop should consume pump power");
        assertEquals(Math.max(0.0, grid.getGrossPowerEUt() - grid.getPumpPowerEUt()), grid.getLastPowerResult().totalPowerEUt, 0.01);
    }
}

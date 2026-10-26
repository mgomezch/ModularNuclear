package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.coolantloops.common.config.CoolantLoopConfigData;
import com.gtnewhorizons.coolantloops.engine.CoolantPipeSpec;
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
        grid.setTicksPerCycle(20);
        grid.loadLayout(
            "U4,U4,U4,M4,U4,U4,U4;U4,M4,HD,HD,HD,M4,U4;T4,HD,M4,HD,M4,HD,T4;U4,M2,HD,HD,HD,M2,U4;T4,HD,M4,HD,M4,HD,T4;U4,M4,HD,HD,HD,M4,U4;U4,U4,U4,M4,U4,U4,U4");
        loop = grid.getCoolantLoop();
    }

    /** Runs the grid until it has stepped the given number of cycles, failing on explosion. */
    private void run(int cycles) {
        for (int i = 0; i < cycles; i++) {
            assertTrue(grid.step(), "grid step " + i + " failed: " + loop.getRuptureReason());
        }
    }

    private void attachCenterColumn() {
        grid.setCoolingMode(CoolantLoopModel.CoolingMode.CONVECTIVE_LOOP);
        loop.clearAttachedPoints();
        loop.attachPoint(3, 2);
        loop.attachPoint(3, 3);
        loop.attachPoint(3, 4);
    }

    @Test
    void testPumpDrawFollowsEnergyHatchesAsInGame() {
        loop.setHatchTier(CoolantLoopModel.EnergyHatchTier.EV);
        loop.setPumpHatchCount(1);
        assertEquals(2048.0, loop.pumpPowerEUt(), 1e-9);
        loop.setPumpHatchCount(2);
        assertEquals(4 * 2048.0, loop.pumpPowerEUt(), 1e-9, "two or more hatches draw four times the voltage");
        loop.setPumpHatchCount(0);
        assertEquals(512.0, loop.pumpPowerEUt(), 1e-9, "a pump without hatches draws 512 EU/t");
    }

    @Test
    void testRotorEfficiencyIsGregTechBaseEfficiency() {
        // 0.5 + (0.5 + base damage + tool quality) / 10; a huge rotor has base damage 7.5
        assertEquals(
            1.7,
            CoolantLoopModel
                .rotorEfficiency(CoolantLoopModel.RotorMaterial.TUNGSTENSTEEL, CoolantLoopModel.RotorSize.HUGE),
            1e-9);
        assertEquals(
            0.85,
            CoolantLoopModel.rotorEfficiency(CoolantLoopModel.RotorMaterial.STEEL, CoolantLoopModel.RotorSize.SMALL),
            1e-9);
    }

    @Test
    void testPumpMechanicalPowerUsesConfiguredWattsPerEU() {
        loop.setHatchTier(CoolantLoopModel.EnergyHatchTier.LUV);
        loop.setPumpHatchCount(1);
        double perEu = CoolantLoopConfigData.getPhysicsData()
            .getPumpMechanicalPowerPerEU();
        assertEquals(32768.0 * perEu * loop.getRotorEfficiency(), loop.pumpMechanicalPowerWatts(), 1e-6);
    }

    @Test
    void testMaxFlowIsPipeCapacity() {
        loop.setMaterial(CoolantLoopModel.LoopMaterial.TITANIUM);
        loop.setPipeSize(CoolantLoopModel.LoopPipeSize.NORMAL);
        assertEquals(480 * 20.0, loop.maxFlowLitersPerSecond(), 1e-9);
        loop.setPipeSize(CoolantLoopModel.LoopPipeSize.HUGE);
        assertEquals(CoolantPipeSpec.capacityLitersPerSecond(3, 4), loop.maxFlowLitersPerSecond(), 1e-9);
    }

    @Test
    void testPipeLimitsComeFromCoolantLoopConfig() {
        for (CoolantLoopModel.LoopMaterial m : CoolantLoopModel.LoopMaterial.values()) {
            assertEquals(CoolantLoopConfigData.getPipePressureRating(m.materialName), m.maxPressureBar(), 1e-9);
            assertEquals(
                CoolantLoopConfigData.getPipeTemperatureLimit(m.materialName),
                m.maxTemperatureCelsius(),
                1e-9);
            assertTrue(CoolantPipeSpec.materialIndex(m.materialName) >= 0, m + " is a registered coolant pipe");
        }
    }

    @Test
    void testLoopExtractsHeatAndMakesSteam() {
        attachCenterColumn();
        loop.setMaterial(CoolantLoopModel.LoopMaterial.TITANIUM);
        loop.setPipeSize(CoolantLoopModel.LoopPipeSize.NORMAL);
        loop.setFluidType(CoolantLoopModel.CoolantFluidType.DISTILLED_WATER);
        loop.setHatchTier(CoolantLoopModel.EnergyHatchTier.EV);
        // The loop has to heat up past the secondary's boiling point (plus the PHE approach) before it boils anything
        run(260);

        assertFalse(loop.isRuptured(), loop.getRuptureReason());
        assertTrue(loop.getCurrentFlowRateLPerSec() > 0, "the pump should circulate coolant");
        assertTrue(loop.getLastHeatExtractedHut() > 0, "the loop should take heat from the attached cells");
        assertTrue(loop.getCurrentCoolantTempCelsius() > 20.0, "the coolant should warm up");
        assertTrue(loop.getTotalSecondarySteamProduced() > 0, "the PHE should boil distilled water");
        assertEquals(
            Math.max(0.0, grid.getGrossPowerEUt() - loop.getLastLoopPowerEUt()),
            grid.getLastPowerResult().totalPowerEUt,
            0.01,
            "net power is gross minus the pump and PHE draw");
    }

    @Test
    void testParallelPassagesSplitTheFlow() {
        attachCenterColumn();
        run(2);
        assertEquals(
            3,
            loop.getAttachedPoints()
                .size());
        assertTrue(loop.getCurrentFlowRateLPerSec() > 0);
        // Heat extraction is per passage, at its share of the loop flow (checked through the shared engine state)
        assertTrue(loop.getLastHeatExtractedWatts() > 0);
    }

    @Test
    void testRadiolysisFollowsLoopFluid() {
        attachCenterColumn();
        loop.setFluidType(CoolantLoopModel.CoolantFluidType.HEAVY_WATER);
        run(40);
        assertTrue(loop.getTotalTritiumProduced() > 0, "heavy water breeds tritium in the passages");
        assertEquals(0, loop.getTotalDeuteriumProduced());

        setUp();
        attachCenterColumn();
        loop.setFluidType(CoolantLoopModel.CoolantFluidType.IC2_COOLANT);
        run(40);
        assertEquals(
            0,
            loop.getTotalTritiumProduced() + loop.getTotalDeuteriumProduced(),
            "IC2 coolant has no radiolysis");
    }

    @Test
    void testOverpressureRupturesTheLoop() {
        attachCenterColumn();
        loop.setMaterial(CoolantLoopModel.LoopMaterial.CAST_IRON);
        loop.setPipeSize(CoolantLoopModel.LoopPipeSize.TINY);
        loop.setHatchTier(CoolantLoopModel.EnergyHatchTier.UHV);
        loop.setPumpHatchCount(2);
        boolean exploded = false;
        for (int i = 0; i < 40 && !exploded; i++) {
            exploded = !grid.step();
        }
        assertTrue(exploded && loop.isRuptured(), "a UHV pump on tiny cast iron pipes should burst them");
        assertTrue(
            loop.getRuptureReason()
                .contains("burst"),
            loop.getRuptureReason());
    }

    @Test
    void testSodiumSolidifiesAtAmbient() {
        attachCenterColumn();
        loop.setFluidType(CoolantLoopModel.CoolantFluidType.SODIUM);
        assertFalse(grid.step(), "sodium below its melting point plugs the loop");
        assertTrue(loop.isRuptured());
    }

    @Test
    void testSecondaryCoolantsComeFromPheConfig() {
        assertTrue(
            CoolantLoopModel.secondaryCoolantChoices()
                .stream()
                .anyMatch(e -> e.isSteam));
        loop.setSecondaryCoolant("liquidnitrogen");
        assertEquals("liquidnitrogen", loop.getSecondaryFluidName());
        assertFalse(loop.getSecondaryCoolant().isSteam);
        loop.setSecondaryCoolant("not a fluid");
        assertEquals("liquidnitrogen", loop.getSecondaryFluidName(), "unknown secondary fluids are rejected");
    }

    @Test
    void testPassagesSplitAmongLoops() {
        attachCenterColumn();
        loop.setLoopCount(3);
        run(5);
        assertFalse(loop.isRuptured(), loop.getRuptureReason());
        assertEquals(3 * loop.pumpPowerEUt(), loop.getLastPumpPowerEUt(), 1e-9, "each loop has its own pump");
        assertEquals(3 * loop.phePowerEUt(), loop.getLastPhePowerEUt(), 1e-9, "and its own PHEs");
        assertTrue(loop.getLastHeatExtractedHut() > 0);
    }

    @Test
    void testDutyCycleScalesPumpDraw() {
        attachCenterColumn();
        loop.setDutyCyclePercent(25.0);
        run(20);
        assertEquals(
            0.25,
            loop.getSelectedCircuit()
                .getLastPumpOnFraction(),
            0.051);
        assertEquals(
            loop.pumpPowerEUt() * loop.getSelectedCircuit()
                .getLastPumpOnFraction(),
            loop.getLastPumpPowerEUt(),
            1e-6);
    }

    @Test
    void testFlowCapHoldsFlowNearCap() {
        attachCenterColumn();
        run(20);
        double free = loop.getCurrentFlowRateLPerSec();
        setUp();
        attachCenterColumn();
        loop.setMaxFlowRateLPerSec(free / 3.0);
        run(40);
        assertTrue(loop.getCurrentFlowRateLPerSec() < free / 2.0, "flow cap should hold the flow down");
        assertTrue(
            loop.getSelectedCircuit()
                .getLastPumpOnFraction() < 1.0);
        assertEquals(
            "flow cap",
            loop.getSelectedCircuit()
                .getLimitReason());
    }

    @Test
    void testPressureCapSwitchesPumpOff() {
        attachCenterColumn();
        run(20);
        double free = loop.getCurrentPressureBar();
        setUp();
        attachCenterColumn();
        loop.setMaxPressureBar(1.0 + (free - 1.0) / 4.0);
        run(40);
        assertTrue(
            loop.getSelectedCircuit()
                .getLastPumpOnFraction() < 1.0);
    }

    @Test
    void testTemperatureBandKeepsPumpOffWhileCold() {
        attachCenterColumn();
        loop.setTemperatureBand(5000.0, 6000.0);
        run(10);
        assertEquals(
            0.0,
            loop.getSelectedCircuit()
                .getLastPumpOnFraction(),
            1e-9,
            "a cold core never reaches the band, so the pump stays off");
        assertEquals(0.0, loop.getLastPumpPowerEUt(), 1e-9);
    }

    @Test
    void testCircuitsTakeTheirPassagesAndFluids() {
        grid.setCoolingMode(CoolantLoopModel.CoolingMode.CONVECTIVE_LOOP);
        grid.setTileCode(3, 2, "CP");
        grid.setTileCode(3, 4, "CP2");
        assertEquals(
            "CP2",
            grid.getTile(3, 4)
                .getLayoutCode());
        loop.setFluidType(CoolantLoopModel.CoolantFluidType.HEAVY_WATER);
        loop.selectCircuit(1)
            .setFluidType(CoolantLoopModel.CoolantFluidType.IC2_COOLANT);
        loop.selectCircuit(0);
        run(3);
        assertEquals(
            1,
            loop.getCircuits()
                .get(0)
                .getPassageCount());
        assertEquals(
            1,
            loop.getCircuits()
                .get(1)
                .getPassageCount());
        assertEquals(
            "heavywater",
            grid.getTile(3, 2)
                .getPassageLoopFluid());
        assertEquals(
            "ic2coolant",
            grid.getTile(3, 4)
                .getPassageLoopFluid());
        assertTrue(
            grid.toLayoutString()
                .contains("CP2"),
            "layouts keep the passage's circuit");
    }

    @Test
    void testPassageModeratesAsItsLoopFluid() {
        grid.setCoolingMode(CoolantLoopModel.CoolingMode.CONVECTIVE_LOOP);
        grid.setTileCode(3, 3, "CP");
        loop.setFluidType(CoolantLoopModel.CoolantFluidType.HEAVY_WATER);
        run(1);
        double heavy = grid.getTile(3, 3)
            .getModerationProbability();
        assertEquals(
            com.gtnewhorizons.modularnuclear.common.nuclear.CoolantPassage.moderation("heavywater"),
            heavy,
            1e-9);
        loop.selectCircuit(0)
            .setFluidType(CoolantLoopModel.CoolantFluidType.MOLTEN_LEAD);
        loop.setTemperatureBand(5000.0, 6000.0);
        run(1);
        assertTrue(
            grid.getTile(3, 3)
                .getModerationProbability() < heavy,
            "lead moderates less than heavy water");
    }

    @Test
    void testMoltenLeadFillsAtItsMeltingPoint() {
        loop.setFluidType(CoolantLoopModel.CoolantFluidType.MOLTEN_LEAD);
        assertEquals(
            327.5,
            loop.getSelectedCircuit()
                .fillTemperatureCelsius(),
            1e-9);
    }

    @Test
    void testPhePowerDraw() {
        loop.setPheCount(3);
        loop.setPheHatchTier(CoolantLoopModel.EnergyHatchTier.IV);
        loop.setPheHatchCount(1);
        assertEquals(3 * (15.0 * 8192 / 16.0), loop.phePowerEUt(), 1e-9);
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
    void testConvectiveCoolingTierGating() {
        StandaloneNuclearGrid electrumGrid = new StandaloneNuclearGrid(
            5,
            5,
            NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        assertFalse(electrumGrid.isTier2ConvectiveAllowed());
        assertFalse(
            electrumGrid.setCoolingMode(CoolantLoopModel.CoolingMode.CONVECTIVE_LOOP),
            "Convective cooling mode must reject Electrum (EV)");
        assertEquals(CoolantLoopModel.CoolingMode.MODULAR, electrumGrid.getCoolingMode());

        StandaloneNuclearGrid platinumGrid = new StandaloneNuclearGrid(
            7,
            7,
            NuclearSimulationEngine.PIPE_TIER_PLATINUM);
        assertFalse(platinumGrid.isTier2ConvectiveAllowed());
        assertFalse(
            platinumGrid.setCoolingMode(CoolantLoopModel.CoolingMode.CONVECTIVE_LOOP),
            "Convective cooling mode must reject Platinum (IV)");
        assertEquals(CoolantLoopModel.CoolingMode.MODULAR, platinumGrid.getCoolingMode());

        // Osmium on 5x5 must be allowed!
        StandaloneNuclearGrid osmiumSmallGrid = new StandaloneNuclearGrid(
            5,
            5,
            NuclearSimulationEngine.PIPE_TIER_OSMIUM);
        assertTrue(osmiumSmallGrid.isTier2ConvectiveAllowed(), "Osmium on 5x5 must allow convective loop");
        assertTrue(
            osmiumSmallGrid.setCoolingMode(CoolantLoopModel.CoolingMode.CONVECTIVE_LOOP),
            "Convective cooling mode must accept Osmium (LuV) even on 5x5");
        assertEquals(CoolantLoopModel.CoolingMode.CONVECTIVE_LOOP, osmiumSmallGrid.getCoolingMode());
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
            loop.getLastHeatExtractedHut() > 0,
            "PASSAGE_CORE tile should automatically be cooled by convective loop");
    }
}

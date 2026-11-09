package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.nuclear.SourceChamber;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid;

/**
 * The Source Chamber model against what the game does, as the in-world suite test_beamline_source_chamber measures it.
 * See Note [Source Chamber beams].
 */
class SourceChamberTest {

    private static final int EV = 4, IV = 5, LUV = 6;

    @Test
    void californiumFromAnEvHatchIsWhatTheGameMakes() {
        SourceChamber.Recipe cf = SourceChamber.Recipe.CALIFORNIUM;
        assertEquals(EV, cf.minTier());
        assertEquals(1920, SourceChamber.practicalVoltage(EV), "The chamber draws VP of its hatch");
        assertEquals(9000.0, cf.beamEnergyKeV(EV), 1e-9);
        assertEquals(10, cf.rate);
    }

    @Test
    void berylliumNeedsLuvAndIsCappedAtTheNeutronMaximum() {
        SourceChamber.Recipe be = SourceChamber.Recipe.BERYLLIUM;
        assertEquals(LUV, be.minTier());
        assertEquals(30720, SourceChamber.practicalVoltage(LUV));
        assertEquals(SourceChamber.NEUTRON_MAX_KEV, be.beamEnergyKeV(LUV), 1e-9);
    }

    @Test
    void aHigherHatchCostsMoreForTheSameBeam() {
        SourceChamber.Recipe cf = SourceChamber.Recipe.CALIFORNIUM;
        assertEquals(cf.beamEnergyKeV(EV), cf.beamEnergyKeV(IV), 1e-9);
        assertEquals(7680, SourceChamber.practicalVoltage(IV));
        assertEquals(0.0, cf.beamEnergyKeV(EV - 1), "Below its tier the recipe doesn't run");
    }

    @Test
    void theSimulatorEmitsWhatTheReactorDoesInGame() {
        // In game, a Californium beam gives 88 and 92 fast neutrons on alternate 5-tick cycles
        int saved = NuclearSimulationEngine.getTicksPerCycle();
        try {
            StandaloneNuclearGrid grid = new StandaloneNuclearGrid(5, 5, NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
            grid.setTicksPerCycle(5);
            grid.loadLayout(".,.,.,.,.;.,.,.,.,.;.,.,NS:CF,.,.;.,.,.,.,.;.,.,.,.,.");
            SimTile source = grid.getTile(2, 2);
            assertEquals(SourceChamber.Recipe.CALIFORNIUM, source.getSourceChamberRecipe());
            List<Integer> perCycle = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                grid.step();
                perCycle.add(source.getLastNeutronsGenerated());
            }
            assertTrue(
                perCycle.stream()
                    .allMatch(n -> n == 88 || n == 92),
                "Expected 88 and 92 a cycle, got " + perCycle);
            assertEquals(
                90.0,
                perCycle.stream()
                    .mapToInt(Integer::intValue)
                    .average()
                    .orElse(0),
                2.0);
            assertEquals(1920, grid.getBeamEuPerTick());
        } finally {
            NuclearSimulationEngine.params()
                .setTicksPerCycle(saved);
        }
    }

    @Test
    void layoutCodesRoundTrip() {
        StandaloneNuclearGrid grid = new StandaloneNuclearGrid(3, 1, NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        grid.loadLayout("NS:CF,NS:BE:UV,NS");
        assertEquals("NS:CF,NS:BE:UV,NS", grid.toLayoutString());
        assertEquals(1920 + SourceChamber.practicalVoltage(8), grid.getBeamEuPerTick());
        assertNull(
            grid.getTile(2, 0)
                .getSourceChamberRecipe(),
            "A source with no chamber gets no beam");
    }
}

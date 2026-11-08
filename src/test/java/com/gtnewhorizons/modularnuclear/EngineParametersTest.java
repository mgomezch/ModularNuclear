package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.nuclear.EngineParameters;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile;

class EngineParametersTest {

    /** An engine runs with its own set: another engine's set, or the installed one, does not change its results. */
    @Test
    void anEngineRunsWithItsOwnParameters() {
        EngineParameters cold = NuclearSimulationEngine.params()
            .copy();
        EngineParameters hot = NuclearSimulationEngine.params()
            .copy();
        hot.fissionHeatPerNeutron *= 4.0;

        double coldAlone = run(cold, 7);
        double hotAlone = run(hot, 7);
        assertNotEquals(coldAlone, hotAlone);

        NuclearSimulationEngine.params().fissionHeatPerNeutron *= 10.0;
        assertEquals(coldAlone, run(cold, 7), 1e-9, "the installed set does not reach an engine built with its own");
    }

    /** A 3x3 core of uranium fuel run for 20 cycles with a seeded random source; returns its hottest cell. */
    private static double run(EngineParameters parameters, long seed) {
        parameters.setRandomSeed(seed);
        SimTile[][] grid = new SimTile[3][3];
        for (int x = 0; x < 3; x++)
            for (int y = 0; y < 3; y++) grid[x][y] = new SimTile(parameters, SimTile.TileType.FUEL_URANIUM_QUAD);
        NuclearSimulationEngine engine = new NuclearSimulationEngine(parameters);
        double max = 0;
        for (int i = 0; i < 20; i++) max = engine.simulate(grid, 3, 3).maxTemperature;
        return max;
    }

    @Test
    void eachTestRunsWithItsOwnParameters() {
        NuclearSimulationEngine.params()
            .setTicksPerCycle(17);
        assertEquals(17, NuclearSimulationEngine.params().ticksPerCycle);
    }

    @Test
    void eachTestRunsWithItsOwnParametersToo() {
        assertNotEquals(17, NuclearSimulationEngine.params().ticksPerCycle, "another test's change is gone");
    }
}

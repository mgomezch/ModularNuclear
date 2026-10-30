package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.nuclear.EngineParameters;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid;

class EngineParametersTest {

    @AfterEach
    void reset() {
        NuclearSimulationEngine.resetDefaultParameters();
    }

    @Test
    void scopedParametersLeaveTheSharedSetAlone() {
        double shared = NuclearSimulationEngine.params().fissionHeatPerNeutron;
        EngineParameters mine = NuclearSimulationEngine.params()
            .copy();
        mine.fissionHeatPerNeutron = shared * 3;
        double seen = NuclearSimulationEngine
            .withParameters(mine, () -> NuclearSimulationEngine.params().fissionHeatPerNeutron);
        assertEquals(shared * 3, seen, 1e-9);
        assertEquals(shared, NuclearSimulationEngine.params().fissionHeatPerNeutron, 1e-9);
    }

    /** Two parameter sets run side by side on two threads, each giving the result it gives alone. */
    @Test
    void twoParameterSetsRunInParallel() throws Exception {
        EngineParameters cold = NuclearSimulationEngine.params()
            .copy();
        EngineParameters hot = NuclearSimulationEngine.params()
            .copy();
        double coldAlone = run(cold, 1.0, 7);
        double hotAlone = run(hot, 4.0, 7);
        assertNotEquals(coldAlone, hotAlone);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Double> c = pool.submit(() -> run(cold, 1.0, 7));
            Future<Double> h = pool.submit(() -> run(hot, 4.0, 7));
            assertEquals(coldAlone, c.get(), 1e-9);
            assertEquals(hotAlone, h.get(), 1e-9);
        } finally {
            pool.shutdown();
        }
    }

    /** Runs the EV preset with its fission heat scaled (after setup, which loads the configured physics). */
    private static double run(EngineParameters parameters, double heatScale, long seed) {
        return NuclearSimulationEngine.withParameters(parameters, () -> {
            StandaloneNuclearGrid grid = new StandaloneNuclearGrid(5, 5, NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
            grid.loadPreset("BEST_ELECTRUM_5X5");
            NuclearSimulationEngine.params().fissionHeatPerNeutron *= heatScale;
            NuclearSimulationEngine.setRandomSeed(seed);
            for (int i = 0; i < 40; i++) grid.step();
            return grid.getCoreMaxTemp();
        });
    }
}

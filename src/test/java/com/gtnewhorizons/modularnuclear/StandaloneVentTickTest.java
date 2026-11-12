package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid;

// See Note [Active vents and exchangers run every tick]
/** The simulator runs heat vents once per tick, as the game does. */
class StandaloneVentTickTest {

    @BeforeEach
    void defaults() {
        NuclearSimulationEngine.resetDefaultParameters();
    }

    /**
     * Electricity the centre vent of an evenly hot core of standard vents draws over one cycle of the given length.
     * Its neighbours are as hot as it is, so it keeps its own heat: 280 K above ambient is 8960 HU, more than the
     * 512 HU a tick it can move over 10 ticks, so it runs at its cap on every tick.
     */
    private static long centreVentDrawPerCycle(int ticksPerCycle) {
        NuclearSimulationEngine.params()
            .setTicksPerCycle(ticksPerCycle);
        StandaloneNuclearGrid g = new StandaloneNuclearGrid(5, 5, NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        String row = "V1,V1,V1,V1,V1";
        g.loadLayout(String.join(";", row, row, row, row, row));
        g.setTicksPerCycle(ticksPerCycle);
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                g.getTile(x, y)
                    .setTemperature(300.0);
            }
        }
        SimTile vent = g.getTile(2, 2);
        assertTrue(g.step(), "The cycle runs");
        return vent.drainedEnergy;
    }

    @Test
    void aVentDrawsItsPerTickCapOnEveryTickOfTheCycle() {
        // A standard vent moves at most 512 HU a tick at 0.25 EU per HU: 128 EU a tick
        assertEquals(128L, centreVentDrawPerCycle(1));
        assertEquals(5 * 128L, centreVentDrawPerCycle(5));
        assertEquals(10 * 128L, centreVentDrawPerCycle(10));
    }
}

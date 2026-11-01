package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.nuclear.NeutronType;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid;

/** Every effect of a control rod is proportional to its insertion: a withdrawn rod is the same as no rod. */
class ProportionalControlRodTest {

    private static final String[] CELLS = { "U4", "HC", "MG", "RG", "." };

    private static SimTile tile(String cell) {
        StandaloneNuclearGrid g = new StandaloneNuclearGrid(5, 5, NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        g.loadLayout("NL,HC,HC,HC,NL;HC,HC,HC,HC,HC;HC,HC," + cell + ",HC,HC;HC,HC,HC,HC,HC;NL,HC,HC,HC,NL");
        return g.getTile(2, 2);
    }

    private static double[] properties(SimTile t) {
        return new double[] { t.getAbsorptionProbability(NeutronType.FAST),
            t.getAbsorptionProbability(NeutronType.THERMAL), t.getScatteringProbability(NeutronType.FAST),
            t.getScatteringProbability(NeutronType.THERMAL), t.getModerationProbability() };
    }

    @Test
    void withdrawnRodIsTheSameAsNoRod() {
        for (String rod : new String[] { "HAFNIUM", "SILVER", "BORON" }) {
            for (String cell : CELLS) {
                double[] bare = properties(tile(cell));
                double[] withdrawn = properties(tile(cell + "+CR:" + rod + ":0"));
                assertArrayEquals(bare, withdrawn, 1e-12, cell + " with a withdrawn " + rod + " rod");
            }
        }
    }

    @Test
    void rodEffectsScaleLinearlyWithInsertion() {
        for (String cell : CELLS) {
            double[] p0 = properties(tile(cell + "+CR:HAFNIUM:0"));
            double[] p50 = properties(tile(cell + "+CR:HAFNIUM:50"));
            double[] p100 = properties(tile(cell + "+CR:HAFNIUM:100"));
            for (int i = 0; i < p0.length; i++) {
                // Combined column properties are quadratic in insertion at most; no step anywhere
                double mid = (p0[i] + p100[i]) / 2.0;
                assertEquals(mid, p50[i], 0.26 * Math.abs(p100[i] - p0[i]) + 1e-12, cell + " property " + i);
            }
            double[] p1 = properties(tile(cell + "+CR:HAFNIUM:1"));
            for (int i = 0; i < p0.length; i++) {
                assertEquals(
                    p0[i],
                    p1[i],
                    0.02 * Math.max(1e-3, Math.abs(p100[i] - p0[i])) + 1e-12,
                    cell + ": 1% insertion is next to no change, not a step (property " + i + ")");
            }
        }
    }

    @Test
    void emptyRodHousingDoesNothing() {
        SimTile t = tile("U4");
        double[] bare = properties(t);
        t.setHasControlRod(true);
        t.setControlRodType(SimTile.ControlRodType.NONE);
        t.setControlRodInsertion(100);
        assertArrayEquals(bare, properties(t), 1e-12);
    }
}

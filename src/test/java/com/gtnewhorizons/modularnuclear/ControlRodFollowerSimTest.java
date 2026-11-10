package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.nuclear.TileTick;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid;

/** The simulator's advanced control rod hatches: a moderator follower slides in as the rod comes out, as in game. */
class ControlRodFollowerSimTest {

    @BeforeEach
    void defaults() {
        NuclearSimulationEngine.resetDefaultParameters();
    }

    private static StandaloneNuclearGrid grid(String layout) {
        StandaloneNuclearGrid g = new StandaloneNuclearGrid(5, 5, NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        g.loadLayout(layout);
        return g;
    }

    private static ComponentData.ModeratorData graphite() {
        return SimTile.contentOf(SimTile.TileType.MODERATOR_GRAPHITE)
            .moderator();
    }

    @Test
    void theFollowerModeratesAsTheRodWithdraws() {
        StandaloneNuclearGrid g = grid(".,.,.,.,.;.,.,.,.,.;.,.,CR:BORON:0:MG,.,.;.,.,.,.,.;.,.,.,.,.");
        SimTile t = g.getTile(2, 2);
        assertEquals(SimTile.TileType.MODERATOR_GRAPHITE, t.getControlRodFollower());
        assertEquals(TileTick.followerModeration(graphite(), 0.0), t.getControlRodFollowerModeration(), 1e-12);
        assertTrue(
            t.getModerationProbability()
                > grid(".,.,.,.,.;.,.,.,.,.;.,.,CR:BORON:0,.,.;.,.,.,.,.;.,.,.,.,.").getTile(2, 2)
                    .getModerationProbability(),
            "Withdrawn, the follower adds moderation");

        g.setControlRod(2, 2, true, SimTile.ControlRodType.BORON, 100);
        assertEquals(0.0, t.getControlRodFollowerModeration(), 1e-12, "Fully inserted, the follower is out");
        g.setControlRod(2, 2, true, SimTile.ControlRodType.BORON, 25);
        assertEquals(TileTick.followerModeration(graphite(), 0.25), t.getControlRodFollowerModeration(), 1e-12);
    }

    @Test
    void layoutsKeepTheFollower() {
        StandaloneNuclearGrid g = grid(".,.,.,.,.;.,.,.,.,.;.,.,U4+CR:BORON:40:MG,.,.;.,.,.,.,.;.,.,.,.,.");
        assertTrue(
            java.util.Arrays.asList(
                g.toLayoutString()
                    .split("[;,]"))
                .contains("U4+CR:BORON:40:MG"),
            g.toLayoutString());
        g.setControlRodFollower(2, 2, "");
        assertNull(
            g.getTile(2, 2)
                .getControlRodFollower(),
            "An empty code makes it a plain hatch");
        g.setControlRod(2, 2, false, SimTile.ControlRodType.NONE, 0);
        g.setControlRodFollower(2, 2, "MG");
        assertNull(
            g.getTile(2, 2)
                .getControlRodFollower(),
            "A cell without a rod hatch takes no follower");
    }

    @Test
    void withTheRodOutTheFollowerThermalizesItsCell() {
        // Uranium on the diagonals of a coolant-cooled 5x5, a rod hatch under the centre coolant column: with the rod
        // out, its graphite follower moderates fast neutrons in that cell, so far more of the cell's flux is thermal;
        // with the rod in, the follower is out and the cell is exactly as with a plain hatch.
        String core = "NL,HC,HC,HC,NL;HC,U4,HC,U4,HC;HC,HC,%s,HC,HC;HC,U4,HC,U4,HC;NL,HC,HC,HC,NL";
        double plainOut = thermalShare(grid(String.format(core, "HC+CR:BORON:0")));
        double followerOut = thermalShare(grid(String.format(core, "HC+CR:BORON:0:MG")));
        assertTrue(
            followerOut > 2 * plainOut && plainOut > 0,
            "Expected a much more thermal cell with the follower in: " + followerOut + " vs " + plainOut);
        assertEquals(
            thermalShare(grid(String.format(core, "HC+CR:BORON:100"))),
            thermalShare(grid(String.format(core, "HC+CR:BORON:100:MG"))),
            1e-12,
            "With the rod in, the follower is out");
    }

    /** The centre cell's thermal share of its flux, over 40 cycles after 20 to settle. */
    private static double thermalShare(StandaloneNuclearGrid g) {
        NuclearSimulationEngine.params()
            .setRandomSeed(7);
        double thermal = 0, total = 0;
        for (int i = 0; i < 60; i++) {
            g.step();
            if (i < 20) continue;
            SimTile t = g.getTile(2, 2);
            thermal += t.getLastThermalFlux();
            total += t.getLastTotalFlux();
        }
        return total > 0 ? thermal / total : 0;
    }
}

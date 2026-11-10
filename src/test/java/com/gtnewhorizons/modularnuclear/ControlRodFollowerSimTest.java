package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.nuclear.TileTick;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid;

/** The simulator's advanced control rod hatches: a moderator follower slides in as the rod comes out, as in game. */
class ControlRodFollowerSimTest {

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
    void pullingARodWithAFollowerMakesMoreNeutrons() {
        // Uranium around a rod hatch: withdrawn, the graphite follower thermalizes neutrons the fuel then fissions more
        String around = "U4,U4,U4,U4,U4;U4,U4,U4,U4,U4;U4,U4,%s,U4,U4;U4,U4,U4,U4,U4;U4,U4,U4,U4,U4";
        double plain = neutrons(grid(String.format(around, "CR:BORON:0")));
        double follower = neutrons(grid(String.format(around, "CR:BORON:0:MG")));
        assertTrue(follower > plain, "Expected more neutrons with a follower: " + follower + " vs " + plain);
    }

    private static double neutrons(StandaloneNuclearGrid g) {
        NuclearSimulationEngine.params()
            .setRandomSeed(7);
        double total = 0;
        for (int i = 0; i < 60; i++) {
            g.step();
            if (i >= 20) total += g.getTotalNeutronsGenerated();
        }
        return total;
    }
}

package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.cli.RodControllers;

class RodControllersTest {

    // Rods on the two outer fuel cells of the middle row and on the centre hatch
    private static final String LAYOUT = "NL,HC,HC,HC,NL;HC,U4,HC,U4,HC;HC,U4+CR:HAFNIUM:0,HC+CR:HAFNIUM:0,"
        + "U4+CR:HAFNIUM:0,HC;HC,U4,HC,U4,HC;NL,HC,HC,HC,NL";

    private static StandaloneNuclearGrid grid() {
        StandaloneNuclearGrid g = new StandaloneNuclearGrid(5, 5, NuclearSimulationEngine.PIPE_TIER_ELECTRUM);
        g.loadLayout(LAYOUT);
        return g;
    }

    private static int insertion(StandaloneNuclearGrid g, int x, int y) {
        return g.getTile(x, y)
            .getControlRodInsertion();
    }

    @Test
    void proportionalZoneSetsItsRodsQuantizedToTheSignalLevels() {
        StandaloneNuclearGrid g = grid();
        RodControllers c = RodControllers.parse("rods:1.2/sp:500/kp:0.01/levels:16");
        c.bind(g);
        g.getTile(1, 2)
            .setTemperature(530.0);
        c.update(g, 1);
        // 30 °C over at 0.01 per °C asks for 0.3; 16 levels give 5/15
        assertEquals((int) Math.round(5.0 / 15.0 * 100.0), insertion(g, 1, 2));
        assertEquals(0, insertion(g, 3, 2), "a rod no zone drives stays put");
    }

    @Test
    void rodsAllTakesOnlyTheRodsNoOtherZoneDrives() {
        StandaloneNuclearGrid g = grid();
        RodControllers c = RodControllers.parse("rods:1.2/sp:2000/kp:1|rods:all/sp:0/kp:1");
        c.bind(g);
        for (int x = 1; x <= 3; x++) g.getTile(x, 2)
            .setTemperature(400.0);
        c.update(g, 1);
        assertEquals(0, insertion(g, 1, 2), "the first zone is below its setpoint");
        assertEquals(100, insertion(g, 2, 2));
        assertEquals(100, insertion(g, 3, 2));
        assertEquals(
            2,
            c.zones()
                .size());
    }

    @Test
    void zoneReadsItsOwnSensorsAndUpdatesOnlyEveryPeriod() {
        StandaloneNuclearGrid g = grid();
        RodControllers c = RodControllers.parse("rods:2.2/sense:1.1+3.3/stat:avg/sp:500/kp:0.01/period:20/levels:256");
        c.bind(g);
        g.getTile(1, 1)
            .setTemperature(600.0);
        g.getTile(3, 3)
            .setTemperature(500.0);
        g.getTile(2, 2)
            .setTemperature(5000.0);
        c.update(g, 1);
        int first = insertion(g, 2, 2);
        assertEquals(50, first, "average of the sensors, 50 °C over, at 0.01 per °C");
        g.getTile(1, 1)
            .setTemperature(900.0);
        c.update(g, 2);
        assertEquals(first, insertion(g, 2, 2), "no update between periods");
        c.update(g, 21);
        assertEquals(100, insertion(g, 2, 2));
    }

    @Test
    void integralTermIsBoundedSoItCanUnwind() {
        StandaloneNuclearGrid g = grid();
        RodControllers c = RodControllers.parse("rods:1.2/sp:500/ki:0.01/period:20/levels:256");
        c.bind(g);
        g.getTile(1, 2)
            .setTemperature(1500.0);
        for (int i = 0; i < 50; i++) c.update(g, 1 + 20 * i);
        assertEquals(100, insertion(g, 1, 2));
        // A long time far over the setpoint must not keep the rod in once the core is below it
        g.getTile(1, 2)
            .setTemperature(300.0);
        int tick = 1 + 20 * 50;
        for (int i = 0; i < 3; i++) c.update(g, tick + 20 * i);
        assertTrue(insertion(g, 1, 2) < 100, "the rod starts to come out within a few updates");
    }
}

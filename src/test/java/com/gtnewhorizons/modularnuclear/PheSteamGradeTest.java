package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.config.CoolantLoopConfigData;
import com.gtnewhorizons.modularnuclear.common.config.data.CoolantDeviceData.PHEData;
import com.gtnewhorizons.modularnuclear.common.config.data.CoolantDeviceData.PHEData.SecondaryCoolantEntry;
import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantFluidProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopSegment;
import com.gtnewhorizons.modularnuclear.common.coolantloop.PheExchange;

/** The steam a PHE makes takes the grade of the coolant entering it, however far the PHE cools it. */
public class PheSteamGradeTest {

    private static final SecondaryCoolantEntry WATER = new SecondaryCoolantEntry(
        "distilledwater",
        100.0,
        "steam",
        2260000.0,
        160,
        true);

    private PHEData cfg;

    @BeforeEach
    void setUp() {
        CoolantLoopConfigData.loadDefaults();
        cfg = CoolantLoopConfigData.getDeviceData()
            .getPhe();
    }

    /** Grade of one tick's steam from coolant entering at the given temperature, across the given exchange area. */
    private String grade(double coolantTemp, double exchangeArea) {
        LoopSegment segment = new LoopSegment("phe", 5.0, 0.10, 0.000045, 1.2, 500.0, 3200.0, coolantTemp);
        PheExchange.Batch batch = new PheExchange.Batch();
        PheExchange.tick(
            batch,
            cfg,
            WATER,
            Double.MAX_VALUE,
            0.05,
            segment,
            CoolantFluidProperty.WATER,
            coolantTemp,
            exchangeArea,
            CoolantLoopConfigData.getConvectiveModel(),
            1.0,
            Double.MAX_VALUE);
        assertTrue(batch.heatJoules > 0.0, "no heat moved");
        return PheExchange.steamGrade(batch.effectiveTemperature(coolantTemp), cfg);
    }

    @Test
    void testAStrongPhesSteamFollowsTheLoopTemperature() {
        // An exchange area large enough to cool the coolant to the approach temperature in one tick
        assertEquals("steam", grade(250.0, 1e6));
        assertEquals("ic2superheatedsteam", grade(500.0, 1e6));
        assertEquals("supercriticalsteam", grade(2000.0, 1e6));
    }

    @Test
    void testAStrongerPheMakesTheSameGrade() {
        // Only the coolant's temperature sets the grade, not how much heat the PHE takes from it
        for (double temp : new double[] { 250.0, 400.0, 800.0, 1500.0, 2000.0 }) {
            assertEquals(grade(temp, 1.0), grade(temp, 1e6), "at " + temp + " °C");
        }
    }
}

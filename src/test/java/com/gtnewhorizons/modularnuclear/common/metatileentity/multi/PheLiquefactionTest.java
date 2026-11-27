package com.gtnewhorizons.modularnuclear.common.metatileentity.multi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** See Note [PHE gas modes] */
public class PheLiquefactionTest {

    private static final double LITRE = 16_272_000.0; // liquid nitrogen

    @Test
    public void heatShortOfALitreCarriesToTheNextBatch() {
        // A 500 kW liquefier takes 25 kJ a tick, far short of a litre: none of it is lost
        double heat = 0.0;
        for (int tick = 0; tick < 1000; tick++) {
            heat += 25_000.0;
            int litres = (int) Math.floor(heat / LITRE);
            heat = PheGasModes.carriedHeat(heat, litres, LITRE);
        }
        assertEquals(25e6 - LITRE, heat, 1e-3);
    }

    @Test
    public void gasRunningOutCarriesNoMoreThanALitre() {
        assertEquals(LITRE, PheGasModes.carriedHeat(5 * LITRE, 1, LITRE), 1e-6);
        assertEquals(0.0, PheGasModes.carriedHeat(2 * LITRE, 2, LITRE), 1e-6);
    }
}

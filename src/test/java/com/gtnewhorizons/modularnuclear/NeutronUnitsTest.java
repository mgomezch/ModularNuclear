package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.nuclear.NeutronUnits;

// See Note [Neutrons are shown in physical units]
class NeutronUnitsTest {

    @Test
    void numbersHaveThreeSignificantDigits() {
        assertEquals("1.00e0", NeutronUnits.number(1));
        assertEquals("1.23e14", NeutronUnits.number(1.234e14));
        assertEquals("1.24e14", NeutronUnits.number(1.235001e14));
        assertEquals("9.99e18", NeutronUnits.number(9.99e18));
        assertEquals("1.00e19", NeutronUnits.number(9.996e18));
        assertEquals("1.00e3", NeutronUnits.number(1000));
        assertEquals("3.00e-1", NeutronUnits.number(0.3));
        assertEquals("-2.50e5", NeutronUnits.number(-2.5e5));
    }

    @Test
    void zeroAndNonNumbersAreZero() {
        assertEquals("0", NeutronUnits.number(0));
        assertEquals("0", NeutronUnits.number(Double.NaN));
        assertEquals("0", NeutronUnits.number(Double.POSITIVE_INFINITY));
        assertEquals("0 n/s", NeutronUnits.rate(0));
    }

    @Test
    void numbersTakeOtherDigitCounts() {
        assertEquals("1.2e14", NeutronUnits.number(1.234e14, 2));
        assertEquals("1.0e15", NeutronUnits.number(9.99e14, 2));
        assertEquals("4e13", NeutronUnits.number(4e13, 1));
    }

    @Test
    void scalesCountsRatesAndFluxes() {
        assertEquals("4.00e14 n", NeutronUnits.count(4));
        assertEquals("2.00e19 n/s", NeutronUnits.rate(200_000));
        // A tile crossed by 10⁴ neutrons a second, through its 1 m² face
        assertEquals("1.00e14 n/cm²/s", NeutronUnits.flux(10_000));
        assertEquals("1.00e14", NeutronUnits.fluxValue(10_000));
        assertEquals("2.00e19", NeutronUnits.rateValue(200_000));
    }
}

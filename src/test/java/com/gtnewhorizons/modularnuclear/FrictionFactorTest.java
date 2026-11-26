package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopSegment;

/**
 * LoopSegment.computeDarcyFrictionFactor computes the Churchill correlation with multiplications and roots in place
 * of Math.pow. It must agree with the formula as written, across the turbulent range and the pipe roughnesses.
 */
public class FrictionFactorTest {

    static double churchill(double re, double relRoughness) {
        double a = Math.pow(-2.457 * Math.log(Math.pow(7.0 / re, 0.9) + 0.27 * relRoughness), 16.0);
        double b = Math.pow(37530.0 / re, 16.0);
        return 8.0 * Math.pow(Math.pow(8.0 / re, 12.0) + 1.0 / Math.pow(a + b, 1.5), 1.0 / 12.0);
    }

    @Test
    void testMatchesTheChurchillFormula() {
        double worst = 0.0;
        for (double diameter : new double[] { 0.02, 0.1, 0.2, 0.5 }) {
            for (double roughness : new double[] { 0.0, 1.5e-6, 4.5e-5, 5e-5, 1e-3 }) {
                LoopSegment s = new LoopSegment("p", 1.0, diameter, roughness, 0.05, null, null, 20.0);
                for (double logRe = Math.log10(2100.0); logRe <= 9.0; logRe += 0.01) {
                    double re = Math.max(2100.0, Math.pow(10.0, logRe));
                    double want = churchill(re, roughness / diameter);
                    double got = s.computeDarcyFrictionFactor(re);
                    worst = Math.max(worst, Math.abs(got - want) / want);
                    assertEquals(want, got, 1e-12 * want, "Re " + re + ", e/D " + roughness / diameter);
                }
            }
        }
        System.out.printf("friction factor: worst relative difference %.2e%n", worst);
    }

    @Test
    void testLaminarAndCreepingFlowAreUnchanged() {
        LoopSegment s = new LoopSegment("p", 1.0, 0.1, 4.5e-5, 0.05, null, null, 20.0);
        assertEquals(64.0, s.computeDarcyFrictionFactor(0.5));
        assertEquals(64.0 / 1000.0, s.computeDarcyFrictionFactor(1000.0));
    }
}

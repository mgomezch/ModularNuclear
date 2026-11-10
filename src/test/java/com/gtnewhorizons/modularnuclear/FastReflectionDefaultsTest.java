package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.config.ReactorConfigData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData;
import com.gtnewhorizons.modularnuclear.common.config.data.FuelData;

/**
 * The default components reflect fast neutrons as their names say. See Note [Fast neutrons are reflected or scattered]
 */
public class FastReflectionDefaultsTest {

    private static ComponentData components;

    @BeforeAll
    static void load() {
        ReactorConfigData.loadDefaults();
        components = ReactorConfigData.getComponentData();
    }

    private record Named(String id, double reflection, double absorption, double scattering) {}

    private static List<Named> others() {
        List<Named> all = new ArrayList<>();
        for (var d : components.moderators)
            all.add(new Named(d.id, d.reflectionFast, d.absorptionFast, d.scatteringFast));
        for (var d : components.insulators)
            all.add(new Named(d.id, d.reflectionFast, d.absorptionFast, d.scatteringFast));
        for (var d : components.radiovoltaics)
            all.add(new Named(d.id, d.reflectionFast, d.absorptionFast, d.scatteringFast));
        for (var d : components.coolantFluids)
            all.add(new Named(d.id, d.reflectionFast, d.absorptionFast, d.scatteringFast));
        for (var d : components.coolantItems)
            all.add(new Named(d.id, d.reflectionFast, d.absorptionFast, d.scatteringFast));
        for (FuelData d : ReactorConfigData.getAllFuels())
            all.add(new Named(d.id, d.reflectionFast, d.absorptionFast, d.scatteringFast));
        return all;
    }

    @Test
    void reflectorsReflectTheMost() {
        double weakest = components.reflectors.stream()
            .mapToDouble(d -> d.reflectionFast)
            .min()
            .orElseThrow();
        for (Named n : others()) {
            assertTrue(
                n.reflection() < weakest,
                () -> n.id() + " reflects " + n.reflection() + ", as much as a reflector");
        }
    }

    @Test
    void reflectorsReflectMoreThanTheyScatter() {
        for (var d : components.reflectors) {
            assertTrue(d.reflectionFast > d.scatteringFast, () -> d.id + " scatters more than it reflects");
        }
    }

    @Test
    void noComponentMeetsAFastNeutronMoreThanOnce() {
        List<Named> all = others();
        for (var d : components.reflectors)
            all.add(new Named(d.id, d.reflectionFast, d.absorptionFast, d.scatteringFast));
        for (Named n : all) {
            double total = n.reflection() + n.absorption() + n.scattering();
            assertTrue(total <= 1.0 + 1e-9, () -> n.id() + " absorbs, reflects and scatters " + total + " in all");
        }
    }
}

package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.config.ReactorConfigData;
import com.gtnewhorizons.modularnuclear.common.config.data.PhysicsData;
import com.gtnewhorizons.modularnuclear.common.nuclear.CoolantPassage;
import com.gtnewhorizons.modularnuclear.common.nuclear.NeutronType;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;

public class CoolantPassageConfigTest {

    @AfterEach
    void restore() {
        ReactorConfigData.loadDefaults();
        NuclearSimulationEngine.resetDefaultParameters();
    }

    @Test
    void testShippedDefaults() {
        ReactorConfigData.loadDefaults();
        assertEquals(0.90, CoolantPassage.conduction(), 1e-12);
        assertEquals(0.80, CoolantPassage.scattering(), 1e-12);
        assertEquals(0.02, CoolantPassage.absorption(NeutronType.FAST), 1e-12);
        assertEquals(0.05, CoolantPassage.absorption(NeutronType.THERMAL), 1e-12);
        assertEquals(0.05, CoolantPassage.defaultModeration(), 1e-12);
        assertEquals(4, CoolantPassage.neutronsPerRadiolysis());
        assertEquals(2.5, CoolantPassage.minorLossK(), 1e-12);
        assertEquals(8.12, NuclearSimulationEngine.baseHatchConductance, 1e-12);
        assertEquals(2.65, NuclearSimulationEngine.hatchConductanceTierRatio, 1e-12);
    }

    @Test
    void testConfigKeysAreRead() {
        Map<String, Object> m = new HashMap<>();
        m.put("coolant_passage_conduction", 0.5);
        m.put("coolant_passage_scattering", 0.6);
        m.put("coolant_passage_fast_absorption", 0.03);
        m.put("coolant_passage_thermal_absorption", 0.07);
        m.put("coolant_passage_default_moderation", 0.1);
        m.put("coolant_passage_neutrons_per_radiolysis", 8);
        m.put("coolant_passage_minor_loss_k", 3.0);
        m.put("base_hatch_conductance", 16.0);
        m.put("hatch_conductance_tier_ratio", 3.0);
        PhysicsData d = PhysicsData.fromMap(m);
        assertEquals(0.5, d.coolantPassageConduction, 1e-12);
        assertEquals(0.6, d.coolantPassageScattering, 1e-12);
        assertEquals(0.03, d.coolantPassageFastAbsorption, 1e-12);
        assertEquals(0.07, d.coolantPassageThermalAbsorption, 1e-12);
        assertEquals(0.1, d.coolantPassageDefaultModeration, 1e-12);
        assertEquals(8, d.coolantPassageNeutronsPerRadiolysis);
        assertEquals(3.0, d.coolantPassageMinorLossK, 1e-12);
        assertEquals(16.0, d.baseHatchConductance, 1e-12);
        assertEquals(3.0, d.hatchConductanceTierRatio, 1e-12);
        assertEquals(
            m.get("coolant_passage_scattering"),
            d.toMap()
                .get("coolant_passage_scattering"));
    }

    @Test
    void testPassageFollowsTheLoadedConfig() {
        ReactorConfigData.getPhysics().coolantPassageScattering = 0.42;
        assertEquals(0.42, CoolantPassage.scattering(), 1e-12);
    }
}

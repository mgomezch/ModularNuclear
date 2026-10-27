package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.config.ReactorConfigData;
import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantFluidProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantPipeSpec;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopSegment;
import com.gtnewhorizons.modularnuclear.common.nuclear.CoolantPassage;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.CoolantLoopModel;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile;

/** High-pressure passages are tiered by their pipe material, like core hatches by their tier. */
public class PassageTierTest {

    @BeforeEach
    void setUp() {
        ReactorConfigData.loadDefaults();
        NuclearSimulationEngine.resetDefaultParameters();
    }

    @AfterEach
    void tearDown() {
        NuclearSimulationEngine.resetDefaultParameters();
    }

    /** Heat (HU) per second per °C a passage of the given tier moves into water flowing at the given rate. */
    private static double passageConductance(int tier, double flowM3s) {
        // A large segment, so the coolant barely warms and the exchange is not limited by it
        LoopSegment segment = new LoopSegment("passage", 50.0, 0.10, 0.000045, 2.5, 500.0, 3200.0, 200.0);
        double hu = CoolantPassage.exchange(
            210.0,
            CoolantPassage.heatCapacity(tier),
            tier,
            flowM3s,
            0.05,
            CoolantFluidProperty.WATER,
            segment);
        return hu / (10.0 * 0.05);
    }

    @Test
    void testHeatCapacityDoublesPerTier() {
        for (int tier = 0; tier <= 7; tier++) {
            assertEquals(16.0 * Math.pow(2.0, tier), CoolantPassage.heatCapacity(tier), 1e-9, "tier " + tier);
        }
    }

    @Test
    void testPassageOutCoolsACoreHatchOfItsTier() {
        // 75 L/s of water: about three times what a core hatch of the same tier conducts into its own coolant
        for (int m = 0; m < CoolantPipeSpec.MATERIAL_NAMES.length; m++) {
            int tier = CoolantPipeSpec.VOLTAGE_TIERS[m];
            double ratio = passageConductance(tier, 0.075) / NuclearSimulationEngine.getHatchConductance(tier);
            System.out.printf("%s (tier %d): passage / hatch = %.2f%n", CoolantPipeSpec.MATERIAL_NAMES[m], tier, ratio);
            assertTrue(ratio > 2.5 && ratio < 3.5, CoolantPipeSpec.MATERIAL_NAMES[m] + ": " + ratio);
        }
        // and more with more flow
        assertTrue(passageConductance(6, 0.3) > 2.5 * passageConductance(6, 0.075));
    }

    @Test
    void testMaterialTiersRiseWithTheMaterial() {
        for (int m = 1; m < CoolantPipeSpec.VOLTAGE_TIERS.length; m++) {
            assertTrue(CoolantPipeSpec.VOLTAGE_TIERS[m] > CoolantPipeSpec.VOLTAGE_TIERS[m - 1]);
        }
        assertEquals(4, CoolantLoopModel.LoopMaterial.TITANIUM.voltageTier());
        assertEquals(7, CoolantLoopModel.LoopMaterial.NEUTRONIUM.voltageTier());
    }

    @Test
    void testSimulatorPassageHoldsItsTiersHeat() {
        SimTile passage = new SimTile(SimTile.TileType.PASSAGE_CORE);
        assertEquals(CoolantPassage.heatCapacity(SimTile.DEFAULT_PASSAGE_TIER), passage.getHeatCapacity(), 1e-9);
        passage.setPassageTier(CoolantLoopModel.LoopMaterial.OSMIUM.voltageTier());
        assertEquals(CoolantPassage.heatCapacity(6), passage.getHeatCapacity(), 1e-9);
        passage.setTemperature(100.0);
        passage.addHeat(CoolantPassage.heatCapacity(6) * 5.0);
        assertEquals(105.0, passage.getTemperature(), 1e-9);
    }
}

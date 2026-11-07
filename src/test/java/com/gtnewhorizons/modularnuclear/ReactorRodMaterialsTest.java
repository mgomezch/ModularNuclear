package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.item.ReactorRodMaterials;

import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;

class ReactorRodMaterialsTest {

    /**
     * GregTech's other conditions (a material's item id, its parent mod) are set by its own init, which tests do not
     * run; the in-game check covers them.
     */
    @Test
    void gregTechMakesTheReactorRods() {
        ReactorRodMaterials.enableRods();
        for (Materials material : new Materials[] { Materials.Graphite, Materials.Boron, Materials.Cadmium }) {
            for (OrePrefixes prefix : new OrePrefixes[] { OrePrefixes.stick, OrePrefixes.stickLong }) {
                assertTrue(prefix.mGeneratedItems.contains(material), prefix + " " + material.mName);
                assertFalse(prefix.mDisabledItems.contains(material), prefix + " " + material.mName);
                assertFalse(prefix.mNotGeneratedItems.contains(material), prefix + " " + material.mName);
            }
        }
        // Only those: other dust-only materials still get no rods
        assertFalse(OrePrefixes.stickLong.mGeneratedItems.contains(Materials.Sodium));
    }
}

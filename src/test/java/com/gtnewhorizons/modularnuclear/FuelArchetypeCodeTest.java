package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.config.data.FuelData;
import com.gtnewhorizons.modularnuclear.common.config.data.PhysicsData;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearFuelType;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;

/** See Note [Fuel archetypes are resolved once]. */
public class FuelArchetypeCodeTest {

    @Test
    public void namesAndAliasesResolveToTheirFamily() {
        assertEquals(NuclearSimulationEngine.ARCHETYPE_STANDARD, NuclearSimulationEngine.archetypeOf(null));
        assertEquals(NuclearSimulationEngine.ARCHETYPE_STANDARD, NuclearSimulationEngine.archetypeOf("unheard-of"));
        assertEquals(NuclearSimulationEngine.ARCHETYPE_DENSE, NuclearSimulationEngine.archetypeOf("Naquadah"));
        assertEquals(NuclearSimulationEngine.ARCHETYPE_DENSE, NuclearSimulationEngine.archetypeOf("subcritical"));
        assertEquals(NuclearSimulationEngine.ARCHETYPE_EXCITED, NuclearSimulationEngine.archetypeOf("DRIVER"));
        assertEquals(NuclearSimulationEngine.ARCHETYPE_BREEDER, NuclearSimulationEngine.archetypeOf("breeder"));
    }

    @Test
    public void fuelDataResolvesAgainAfterTheFieldChanges() {
        PhysicsData physics = new PhysicsData();
        physics.denseFissionMultiplier = 1.5;
        physics.excitedFissionMultiplier = 0.25;
        FuelData fd = new FuelData();
        fd.fastFissionMultiplier = 2.0;
        fd.archetype = "dense";
        assertEquals(3.0, fd.getEffectiveFastFissionMultiplier(physics), 1e-12);
        assertEquals(3.0, fd.getEffectiveFastFissionMultiplier(physics), 1e-12, "the cached code");
        fd.archetype = "excited";
        assertEquals(0.5, fd.getEffectiveFastFissionMultiplier(physics), 1e-12);
        // The multipliers aren't cached: a config change applies at once
        physics.excitedFissionMultiplier = 1.0;
        assertEquals(2.0, fd.getEffectiveFastFissionMultiplier(physics), 1e-12);
    }

    @Test
    public void codeAndNameLookupsAgree() {
        PhysicsData physics = new PhysicsData();
        physics.neutronYieldMultiplier = 1.3;
        for (NuclearFuelType t : NuclearFuelType.values()) {
            assertEquals(
                physics.getArchetypeFissionMultiplier(t.archetype),
                physics.getArchetypeFissionMultiplier(t.archetypeCode),
                t.name());
            assertEquals(
                physics.getArchetypeNbaseMultiplier(t.archetype),
                physics.getArchetypeNbaseMultiplier(t.archetypeCode),
                t.name());
        }
    }
}

package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry;
import com.gtnewhorizons.modularnuclear.common.config.data.FuelData;
import com.gtnewhorizons.modularnuclear.common.nuclear.NeutronType;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearFuelType;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile;

/**
 * The standalone simulator (compiled to WebAssembly) cannot read the YAML config, so SimTile carries its own copy of
 * the fuel defaults. This test keeps that copy identical to the game's fuels.yaml defaults.
 */
public class SimulatorFuelParityTest {

    private static String fuelId(SimTile.TileType type) {
        String n = type.name();
        switch (n) {
            case "FUEL_CORE":
                return "fuel_naquadah_32";
            case "FUEL_GLOWSTONE":
                return "fuel_glowstone";
            case "FUEL_LITHIUM":
                return "fuel_lithium";
            case "HATCH_LIQUID_FUEL_THORIUM":
                return "fluid_fuel_thorium";
            case "HATCH_LIQUID_FUEL_URANIUM":
                return "fluid_fuel_uranium";
            case "HATCH_LIQUID_FUEL_PLUTONIUM":
                return "fluid_fuel_plutonium";
            default:
                break;
        }
        if (!n.startsWith("FUEL_")) return null;
        String size = n.endsWith("_SINGLE") ? "1" : n.endsWith("_DUAL") ? "2" : n.endsWith("_QUAD") ? "4" : null;
        if (size == null) return null;
        String family = n.substring(5, n.lastIndexOf('_'))
            .toLowerCase(Locale.ROOT);
        return "fuel_" + family + "_" + size;
    }

    private static void check(List<String> diffs, String id, String field, double sim, double cfg) {
        if (Math.abs(sim - cfg) > 1e-6) {
            diffs.add(String.format(Locale.US, "%s %s: simulator %s, fuels.yaml %s", id, field, sim, cfg));
        }
    }

    @Test
    void testSimulatorFuelsMatchConfigDefaults() {
        ReactorDataRegistry.loadDefaults();
        List<String> diffs = new ArrayList<>();
        int checked = 0;
        for (SimTile.TileType type : SimTile.TileType.values()) {
            String id = fuelId(type);
            if (id == null) continue;
            FuelData fd = ReactorDataRegistry.getAllFuels()
                .stream()
                .filter(f -> id.equals(f.id))
                .findFirst()
                .orElse(null);
            if (fd == null) {
                diffs.add(type + ": no fuels.yaml entry " + id);
                continue;
            }
            checked++;
            SimTile tile = new SimTile(type);
            if (tile.isLiquidFuelHatch()) {
                tile.setInputFluidAmount(8000);
            }
            check(diffs, id, "base_neutrons", tile.getRawBaseNeutrons(), fd.baseNeutrons);
            if (!tile.isLiquidFuelHatch()) {
                check(diffs, id, "packet_count", tile.getNeutronEmissionCount(), fd.packetCount);
            }
            if (!fd.archetype.equalsIgnoreCase(tile.getArchetype())) {
                diffs.add(id + " archetype: simulator " + tile.getArchetype() + ", fuels.yaml " + fd.archetype);
            }
            check(
                diffs,
                id,
                "absorption_thermal",
                tile.getBaseAbsorptionProbability(NeutronType.THERMAL),
                fd.absorptionThermal);
            check(diffs, id, "absorption_fast", tile.getBaseAbsorptionProbability(NeutronType.FAST), fd.absorptionFast);
            check(diffs, id, "heat_transfer_coeff", tile.getHeatTransferCoeff(), fd.heatTransferCoeff);
            NuclearFuelType fuel = tile.getFuelType();
            if (fuel == null) {
                diffs.add(id + ": simulator has no fuel type");
                continue;
            }
            check(
                diffs,
                id,
                "thermal_fission_multiplier",
                tile.getThermalFissionMultiplier(),
                fd.thermalFissionMultiplier);
            check(diffs, id, "fast_fission_multiplier", fuel.baseFastFissionMultiplier, fd.fastFissionMultiplier);
            check(diffs, id, "peak_reactivity_temp", fuel.peakReactivityTemp, fd.peakReactivityTemp);
            check(diffs, id, "floor_temp", fuel.floorTemp, fd.floorTemp);
        }
        assertTrue(checked > 30, "Expected to compare every simulator fuel, compared " + checked);
        assertTrue(diffs.isEmpty(), diffs.size() + " simulator/config fuel mismatches:\n" + String.join("\n", diffs));
        assertNotNull(ReactorDataRegistry.getPhysics());
    }
}

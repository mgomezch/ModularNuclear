package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry;
import com.gtnewhorizons.modularnuclear.common.config.data.FuelData;
import com.gtnewhorizons.modularnuclear.common.nuclear.NeutronType;
import com.gtnewhorizons.modularnuclear.common.nuclear.TileContent;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile;

/**
 * The standalone simulator (compiled to WebAssembly) reads fuel and component properties from the same embedded config
 * as the game, through each tile type's config id. These tests check every id resolves and the values come through.
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
            case "HATCH_LIQUID_FUEL_URANIUM_EXCITED":
            case "HATCH_LIQUID_FUEL_THORIUM_EXCITED":
            case "HATCH_LIQUID_FUEL_PLUTONIUM_EXCITED":
                return SimTile.TileType.valueOf(n).configId;
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
    void testEveryTileConfigIdResolves() {
        ReactorDataRegistry.loadDefaults();
        List<String> missing = new ArrayList<>();
        for (SimTile.TileType type : SimTile.TileType.values()) {
            if (type.configId == null) continue;
            if (SimTile.contentOf(type).kind == TileContent.Kind.OTHER) missing.add(type + " -> " + type.configId);
        }
        assertTrue(missing.isEmpty(), "Tile config ids without a config entry: " + missing);
    }

    @Test
    void testControlRodTypesMatchConfig() {
        ReactorDataRegistry.loadDefaults();
        for (SimTile.ControlRodType rod : SimTile.ControlRodType.values()) {
            if (rod == SimTile.ControlRodType.NONE) continue;
            SimTile tile = new SimTile(SimTile.TileType.EMPTY);
            tile.setControlRodType(rod);
            var data = tile.getControlRodData();
            assertNotNull(data, rod + " has no components.yaml entry " + rod.configId);
            assertEquals(data.maxThermalAbsorption, rod.maxThermalAbsorption, 1e-9, rod + " thermal");
            assertEquals(data.maxFastAbsorption, rod.maxFastAbsorption, 1e-9, rod + " fast");
            assertEquals(data.heatTransferCoeff, rod.heatTransferCoeff, 1e-9, rod + " conduction");
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
            FuelData used = tile.getFuelData();
            if (used == null || !id.equals(used.id)) {
                diffs.add(id + ": simulator uses fuel record " + (used == null ? "none" : used.id));
                continue;
            }
            check(
                diffs,
                id,
                "thermal_fission_multiplier",
                tile.getThermalFissionMultiplier(),
                fd.thermalFissionMultiplier);
            check(diffs, id, "fast_fission_multiplier", used.fastFissionMultiplier, fd.fastFissionMultiplier);
            check(diffs, id, "peak_reactivity_temp", used.peakReactivityTemp, fd.peakReactivityTemp);
            check(diffs, id, "floor_temp", used.floorTemp, fd.floorTemp);
        }
        assertTrue(checked > 30, "Expected to compare every simulator fuel, compared " + checked);
        assertTrue(diffs.isEmpty(), diffs.size() + " simulator/config fuel mismatches:\n" + String.join("\n", diffs));
        assertNotNull(ReactorDataRegistry.getPhysics());
    }
}

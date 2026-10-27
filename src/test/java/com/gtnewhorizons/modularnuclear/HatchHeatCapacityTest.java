package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.config.data.PhysicsData;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile;

/**
 * A nuclear core hatch's heat capacity belongs to its structure: it doubles per tier, as its tank does, whatever fluid
 * it holds. Every other core tile holds hu_per_degree.
 */
public class HatchHeatCapacityTest {

    @BeforeEach
    @AfterEach
    void reset() {
        NuclearSimulationEngine.resetDefaultParameters();
    }

    private static SimTile hatch(SimTile.TileType type, int tier) {
        SimTile t = new SimTile(type);
        t.setTier(tier);
        return t;
    }

    @Test
    void testCapacityDoublesPerTierLikeTheTank() {
        assertEquals(32.0, NuclearSimulationEngine.getCoreHatchHeatCapacity(1), 1e-9, "LV");
        assertEquals(256.0, NuclearSimulationEngine.getCoreHatchHeatCapacity(4), 1e-9, "EV");
        assertEquals(4096.0, NuclearSimulationEngine.getCoreHatchHeatCapacity(8), 1e-9, "UV");
        for (int tier = 1; tier < 9; tier++) {
            assertEquals(
                2.0,
                NuclearSimulationEngine.getCoreHatchHeatCapacity(tier + 1)
                    / NuclearSimulationEngine.getCoreHatchHeatCapacity(tier),
                1e-12);
        }
    }

    @Test
    void testCapacityDependsOnTheHatchNotItsFluid() {
        for (SimTile.TileType type : new SimTile.TileType[] { SimTile.TileType.HATCH_DISTILLED_WATER,
            SimTile.TileType.HATCH_IC2_COOLANT, SimTile.TileType.HATCH_LIQUID_FUEL_URANIUM }) {
            assertEquals(1024.0, hatch(type, 6).getHeatCapacity(), 1e-9, type.toString());
        }
        assertEquals(
            NuclearSimulationEngine.HU_PER_DEGREE,
            new SimTile(SimTile.TileType.FUEL_URANIUM_QUAD).getHeatCapacity(),
            1e-9,
            "a bus holds hu_per_degree");
    }

    @Test
    void testCapacityIsConfigurable() {
        Map<String, Object> map = new HashMap<>();
        map.put("core_hatch_heat_capacity", 4.0);
        PhysicsData data = PhysicsData.fromMap(map);
        assertEquals(4.0, data.coreHatchHeatCapacity, 1e-9);
        assertEquals(
            4.0,
            data.toMap()
                .get("core_hatch_heat_capacity"));
        NuclearSimulationEngine.setCoreHatchHeatCapacity(data.coreHatchHeatCapacity);
        assertEquals(4.0 * 16, hatch(SimTile.TileType.HATCH_DISTILLED_WATER, 4).getHeatCapacity(), 1e-9);
    }

    @Test
    void testDepositedHeatWarmsAHatchByItsOwnCapacity() {
        SimTile h = hatch(SimTile.TileType.HATCH_DISTILLED_WATER, 8);
        h.setTemperature(20.0);
        h.addHeat(4096.0);
        assertEquals(21.0, h.getTemperature(), 1e-9);
    }

    @Test
    void testCoolingRemovesHeatAtTheHatchesOwnCapacity() {
        // Twice the capacity and a larger share of the excess per tier: more than twice the heat
        double previous = 0.0;
        for (int tier = 2; tier <= 9; tier++) {
            SimTile h = hatch(SimTile.TileType.HATCH_DISTILLED_WATER, tier);
            h.setInputFluidAmount(Integer.MAX_VALUE); // a full tank, which at 120 °C never limits the transfer
            h.setTemperature(120.0);
            double q = h.coolantSubstep(0.25, 1.0);
            assertEquals(q, (120.0 - h.getTemperature()) * h.getHeatCapacity(), 1e-6, "heat leaves at its capacity");
            if (previous > 0.0) assertTrue(q > 2.0 * previous, "tier " + tier);
            previous = q;
        }
    }

    @Test
    void testExchangerConservesHeatBetweenUnequalCells() {
        SimTile[][] grid = new SimTile[3][3];
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                grid[x][y] = new SimTile(SimTile.TileType.EMPTY);
                grid[x][y].setTemperature(20.0);
            }
        }
        grid[1][1] = new SimTile(SimTile.TileType.EXCHANGER_COMPONENT);
        grid[1][1].setTemperature(600.0);
        SimTile cold = hatch(SimTile.TileType.HATCH_DISTILLED_WATER, 8);
        cold.setTemperature(100.0);
        grid[1][2] = cold;
        double before = 0.0;
        for (SimTile[] row : grid) for (SimTile t : row) before += t.getHeatCapacity() * t.getTemperature();

        NuclearSimulationEngine.processHeatExchangers(grid, 3, 3);

        double after = 0.0;
        for (SimTile[] row : grid) {
            for (SimTile t : row) {
                after += t.getHeatCapacity() * t.getTemperature();
                assertTrue(t.getTemperature() >= 20.0 - 1e-9 && t.getTemperature() <= 600.0 + 1e-9);
            }
        }
        assertEquals(before, after, 1e-9 * before, "an exchanger only moves heat");
        assertTrue(cold.getTemperature() > 100.0, "the hatch took heat");
        assertTrue(cold.getTemperature() - 100.0 < 600.0 - grid[1][1].getTemperature(), "and warmed more slowly");
    }
}

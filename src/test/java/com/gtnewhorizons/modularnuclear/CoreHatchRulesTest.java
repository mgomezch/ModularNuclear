package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.nuclear.CoreHatchRules;
import com.gtnewhorizons.modularnuclear.common.nuclear.CoreHatchRules.CoolantFeed;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.SimTile;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.StandaloneNuclearGrid;

class CoreHatchRulesTest {

    @AfterEach
    void reset() {
        NuclearSimulationEngine.resetDefaultParameters();
    }

    @Test
    void coolantFeedFollowsBoilingPointAndDryness() {
        assertEquals(CoolantFeed.SAFE, CoreHatchRules.checkCoolantFeed("distilledwater", false, 500.0, 20.0));
        assertEquals(CoolantFeed.SAFE, CoreHatchRules.checkCoolantFeed("distilledwater", true, 90.0, 20.0));
        assertEquals(
            CoolantFeed.INTO_DRY_HOT_HATCH,
            CoreHatchRules.checkCoolantFeed("distilledwater", true, 150.0, 20.0));
        assertEquals(
            CoolantFeed.BOILS_AT_AMBIENT,
            CoreHatchRules.checkCoolantFeed("distilledwater", false, 20.0, 120.0));
        // IC2 coolant never boils
        assertEquals(CoolantFeed.SAFE, CoreHatchRules.checkCoolantFeed("ic2coolant", true, 3000.0, 500.0));
    }

    @Test
    void simulatorShocksWaterThatBoilsAtAmbientAsTheGameDoes() {
        StandaloneNuclearGrid grid = new StandaloneNuclearGrid(5, 5, NuclearSimulationEngine.PIPE_TIER_PLATINUM);
        grid.setTile(2, 2, SimTile.TileType.HATCH_DISTILLED_WATER);
        grid.getTile(2, 2)
            .setInputFluidAmount(0);
        grid.getTile(2, 2)
            .setAutoRefill(true);
        NuclearSimulationEngine.params().ambientTemp = 120.0;
        grid.step();
        assertEquals(CoreHatchRules.THERMAL_SHOCK_DAMAGE_PERCENT, grid.getReactorDamage(), 1e-9);
    }
}

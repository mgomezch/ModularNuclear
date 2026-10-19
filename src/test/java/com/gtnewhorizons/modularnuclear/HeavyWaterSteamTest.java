package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry;
import com.gtnewhorizons.modularnuclear.common.fluid.ModFluids;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.TurbineCalculator;
import com.gtnewhorizons.modularnuclear.common.steam.HeavyWaterSteamTurbineHelper;

public class HeavyWaterSteamTest {

    @BeforeAll
    static void initFluids() {
        try {
            net.minecraft.init.Bootstrap.func_151354_b();
        } catch (Throwable ignored) {}

        if (FluidRegistry.getFluid("heavywater") == null) {
            Fluid hw = new Fluid("heavywater");
            hw.setDensity(1100).setViscosity(1100).setTemperature(300).setUnlocalizedName("fluid.heavywater");
            FluidRegistry.registerFluid(hw);
        }
        if (FluidRegistry.getFluid("heavywatersteam") == null) {
            Fluid hws = new Fluid("heavywatersteam");
            hws.setDensity(-100).setViscosity(100).setTemperature(375).setGaseous(true).setUnlocalizedName("fluid.heavywatersteam");
            FluidRegistry.registerFluid(hws);
        }
        if (FluidRegistry.getFluid("ic2distilledwater") == null) {
            Fluid dw = new Fluid("ic2distilledwater");
            FluidRegistry.registerFluid(dw);
        }
        if (FluidRegistry.getFluid("steam") == null) {
            Fluid st = new Fluid("steam");
            FluidRegistry.registerFluid(st);
        }
        ModFluids.fluidHeavyWater = FluidRegistry.getFluid("heavywater");
        ModFluids.fluidHeavyWaterSteam = FluidRegistry.getFluid("heavywatersteam");
    }

    @Test
    void testIsHeavyWaterSteam() {
        assertTrue(HeavyWaterSteamTurbineHelper.isHeavyWaterSteam(ModFluids.fluidHeavyWaterSteam));
        assertTrue(HeavyWaterSteamTurbineHelper.isHeavyWaterSteam(new FluidStack(ModFluids.fluidHeavyWaterSteam, 1000)));

        Fluid regularSteam = FluidRegistry.getFluid("steam");
        assertFalse(HeavyWaterSteamTurbineHelper.isHeavyWaterSteam(regularSteam));
        assertFalse(HeavyWaterSteamTurbineHelper.isHeavyWaterSteam(new FluidStack(regularSteam, 1000)));
        assertFalse(HeavyWaterSteamTurbineHelper.isHeavyWaterSteam((Fluid) null));
        assertFalse(HeavyWaterSteamTurbineHelper.isHeavyWaterSteam((FluidStack) null));
    }

    @Test
    void testDistributeCondensedWater() {
        List<FluidStack> outputs = new ArrayList<>();

        // 1. All heavy water steam consumed -> only heavy water output
        outputs.clear();
        HeavyWaterSteamTurbineHelper.distributeCondensedWater(outputs::add, 1000, 160000, 0);
        assertEquals(1, outputs.size());
        assertEquals("heavywater", outputs.get(0).getFluid().getName());
        assertEquals(1000, outputs.get(0).amount);

        // 2. All regular steam consumed -> only distilled water output
        outputs.clear();
        HeavyWaterSteamTurbineHelper.distributeCondensedWater(outputs::add, 1000, 0, 160000);
        assertEquals(1, outputs.size());
        assertEquals(1000, outputs.get(0).amount);

        // 3. 50/50 mixture
        outputs.clear();
        HeavyWaterSteamTurbineHelper.distributeCondensedWater(outputs::add, 1000, 80000, 80000);
        assertEquals(2, outputs.size());
        int hwAmount = 0;
        int dwAmount = 0;
        for (FluidStack stack : outputs) {
            if ("heavywater".equals(stack.getFluid().getName())) {
                hwAmount += stack.amount;
            } else {
                dwAmount += stack.amount;
            }
        }
        assertEquals(500, hwAmount);
        assertEquals(500, dwAmount);

        // 4. Zero water edge case
        outputs.clear();
        HeavyWaterSteamTurbineHelper.distributeCondensedWater(outputs::add, 0, 100, 100);
        assertTrue(outputs.isEmpty());
    }

    @Test
    void testTurbinePowerMatchesRegularSteam() {
        // In EV tier (Large Steam Turbine):
        // 1000 L/t regular steam
        TurbineCalculator.PowerEstimationResult regEV = TurbineCalculator.calculatePower(
            NuclearSimulationEngine.PIPE_TIER_ELECTRUM,
            1000.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
            TurbineCalculator.TurbineMaterial.ORINARUKON,
            TurbineCalculator.TurbineSize.NORMAL,
            TurbineCalculator.FittingMode.TIGHT);

        // 1000 L/t heavy-water steam
        TurbineCalculator.PowerEstimationResult hwEV = TurbineCalculator.calculatePower(
            NuclearSimulationEngine.PIPE_TIER_ELECTRUM,
            0.0, 0.0, 0.0, 1000.0, 0.0, 0.0, 0.0,
            TurbineCalculator.TurbineMaterial.ORINARUKON,
            TurbineCalculator.TurbineSize.NORMAL,
            TurbineCalculator.FittingMode.TIGHT);

        assertEquals(regEV.totalPowerEUt, hwEV.totalPowerEUt, 1e-4);
        assertEquals(regEV.lstPowerEUt, hwEV.lstPowerEUt, 1e-4);
        assertEquals(regEV.totalTurbinesNeeded, hwEV.totalTurbinesNeeded, 1e-4);

        // In IV tier (XL Turbo Steam Turbine):
        TurbineCalculator.PowerEstimationResult regIV = TurbineCalculator.calculatePower(
            NuclearSimulationEngine.PIPE_TIER_PLATINUM,
            1000.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
            TurbineCalculator.TurbineMaterial.ICHORIUM,
            TurbineCalculator.TurbineSize.LARGE,
            TurbineCalculator.FittingMode.TIGHT);

        TurbineCalculator.PowerEstimationResult hwIV = TurbineCalculator.calculatePower(
            NuclearSimulationEngine.PIPE_TIER_PLATINUM,
            0.0, 0.0, 0.0, 1000.0, 0.0, 0.0, 0.0,
            TurbineCalculator.TurbineMaterial.ICHORIUM,
            TurbineCalculator.TurbineSize.LARGE,
            TurbineCalculator.FittingMode.TIGHT);

        assertEquals(regIV.totalPowerEUt, hwIV.totalPowerEUt, 1e-4);
        assertEquals(regIV.xlstPowerEUt, hwIV.xlstPowerEUt, 1e-4);
        assertEquals(regIV.totalTurbinesNeeded, hwIV.totalTurbinesNeeded, 1e-4);
    }

    @Test
    void testCoolantHeavyWaterHotFluidName() {
        com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.CoolantFluidData data = ReactorDataRegistry.findCoolantFluid("heavywater");
        assertNotNull(data);
        assertEquals("heavywatersteam", data.hotFluidName);
    }
}

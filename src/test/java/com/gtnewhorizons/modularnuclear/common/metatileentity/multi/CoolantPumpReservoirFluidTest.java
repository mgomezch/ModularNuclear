package com.gtnewhorizons.modularnuclear.common.metatileentity.multi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.TestCoolantPump;
import com.gtnewhorizons.modularnuclear.TestEnvironmentHelper;

/** The pump starts only on a reservoir whose fluid it can tell is a loop coolant. */
class CoolantPumpReservoirFluidTest {

    @BeforeAll
    static void initEnvironment() {
        TestEnvironmentHelper.init();
    }

    @Test
    void aTankThatDoesNotReportItsFluidKeepsThePumpStopped() {
        // The in-memory tank holds 100000 L, and the tank reports no fluid
        TestCoolantPump pump = new TestCoolantPump("unknown_fluid_pump");
        assertEquals(MTECoolantPump.Step.DONE, CoolantPumpFluidSafety.refuseUnsuitableFluid(pump, null));
        assertEquals(MTECoolantPump.LoopState.STOPPED, pump.mLoopState);
        assertEquals(
            "modularnuclear.coolant_loop.pump.status.reservoir_unknown_fluid",
            pump.getLoopStatusMessage().key);
    }

    @Test
    void aFluidThatIsNotALoopCoolantKeepsThePumpStopped() {
        Fluid notACoolant = FluidRegistry.getFluid("reservoir_test_not_a_coolant");
        if (notACoolant == null) {
            notACoolant = new Fluid("reservoir_test_not_a_coolant");
            FluidRegistry.registerFluid(notACoolant);
        }
        TestCoolantPump pump = new TestCoolantPump("not_a_coolant_pump");
        assertEquals(
            MTECoolantPump.Step.DONE,
            CoolantPumpFluidSafety.refuseUnsuitableFluid(pump, new FluidStack(notACoolant, 1000)));
        assertEquals(MTECoolantPump.LoopState.STOPPED, pump.mLoopState);
        assertEquals("modularnuclear.coolant_loop.pump.status.refuse_unknown_coolant", pump.getLoopStatusMessage().key);
    }
}

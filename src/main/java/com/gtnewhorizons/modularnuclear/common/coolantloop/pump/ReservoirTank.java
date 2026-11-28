package com.gtnewhorizons.modularnuclear.common.coolantloop.pump;

import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

/** The tank a pump's reservoir draws from and returns to. */
public interface ReservoirTank {

    /** The tank's fluid with all there is of it, or null when the tank is empty. */
    FluidStack getFluid();

    long getCapacityLiters();

    default long getAmountLiters() {
        FluidStack fluid = getFluid();
        return fluid != null ? fluid.amount : 0L;
    }

    /** Drains up to {@code liters}; returns how much it drained. */
    int drain(int liters);

    /** Fills up to the stack's amount; returns how much the tank took. */
    int fill(FluidStack stack);

    /** A Forge fluid handler's first tank, reached from one side. */
    static ReservoirTank of(IFluidHandler handler, ForgeDirection side) {
        return new ReservoirTank() {

            private FluidTankInfo info() {
                FluidTankInfo[] infos = handler.getTankInfo(side);
                return infos != null && infos.length > 0 ? infos[0] : null;
            }

            @Override
            public FluidStack getFluid() {
                FluidTankInfo info = info();
                return info != null ? info.fluid : null;
            }

            @Override
            public long getCapacityLiters() {
                FluidTankInfo info = info();
                return info != null ? info.capacity : 0L;
            }

            @Override
            public int drain(int liters) {
                FluidStack drained = handler.drain(side, liters, true);
                return drained != null ? drained.amount : 0;
            }

            @Override
            public int fill(FluidStack stack) {
                return handler.fill(side, stack, true);
            }
        };
    }
}

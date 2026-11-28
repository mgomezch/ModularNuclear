package com.gtnewhorizons.modularnuclear.common.coolantloop.pump;

import java.util.Collections;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantFluidProperty;
import com.gtnewhorizons.modularnuclear.common.util.CoolantFluidHelper;

/**
 * A pump's reservoir: the coolant in the tank on the pump, its temperature, and the gases dissolved in it. Which
 * tank that is the pump's kind says ({@link #findTank}): a Railcraft tank on a multiblock pump, a stack of Iron
 * Tanks on a singleblock pump.
 */
public abstract class CoolantPumpReservoir {

    protected final ICoolantPumpHost host;

    protected CoolantPumpReservoir(ICoolantPumpHost host) {
        this.host = host;
    }

    /** The tank on the pump now, or null when there is none. */
    protected abstract ReservoirTank findTank();

    public long getTotalTankCapacityLiters() {
        ReservoirTank tank = findTank();
        return tank != null ? tank.getCapacityLiters() : 0L;
    }

    /** The tank's fluid and how much of it there is, or null for an empty or missing tank. */
    public FluidStack getReservoirFluid() {
        ReservoirTank tank = findTank();
        FluidStack fluid = tank != null ? tank.getFluid() : null;
        return fluid != null && fluid.amount > 0 && fluid.getFluid() != null ? fluid : null;
    }

    /** Drains up to {@code liters} from the tank on the pump; returns how much it drained. */
    protected int drainFromReservoirInternal(int liters) {
        ReservoirTank tank = findTank();
        return tank != null && liters > 0 ? tank.drain(liters) : 0;
    }

    /** Fills up to {@code liters} of the loop's coolant into the tank on the pump; returns how much it took. */
    protected int fillIntoReservoirInternal(int liters) {
        ReservoirTank tank = findTank();
        if (tank == null || liters <= 0) return 0;
        CoolantFluidProperty loopFluid = host.getPumpCore()
            .getEngine()
            .getFluid();
        Fluid fluid = FluidRegistry.getFluid(loopFluid != null ? loopFluid.getFluidName() : "ic2distilledwater");
        return fluid != null ? tank.fill(new FluidStack(fluid, liters)) : 0;
    }

    /** Whether the tank takes no more; a missing tank takes nothing. */
    public boolean isReservoirFull() {
        ReservoirTank tank = findTank();
        return tank == null || tank.getAmountLiters() >= tank.getCapacityLiters();
    }

    public long getTotalCoolantLiters() {
        ReservoirTank tank = findTank();
        return tank != null ? tank.getAmountLiters() : 0L;
    }

    private final Map<String, Long> dissolvedGases = new java.util.LinkedHashMap<>();
    private final Map<String, Long> unmodifiableDissolvedGases = Collections.unmodifiableMap(dissolvedGases);
    private long dissolvedGasesVersion = 0L;

    private double reservoirTemperatureCelsius = Double.NaN;
    private long lastObservedTankLiters = 0L;

    public double getReservoirTemperatureCelsius() {
        if (Double.isNaN(reservoirTemperatureCelsius)) {
            FluidStack f = getReservoirFluid();
            if (f != null && f.amount > 0 && f.getFluid() != null) {
                CoolantFluidProperty prop = reservoirFluidProperty(f);
                reservoirTemperatureCelsius = CoolantFluidHelper
                    .getBaselineInletTemperatureCelsius(f.getFluid(), f, prop, host.getBiomeTemperatureCelsius());
                return reservoirTemperatureCelsius;
            }
        }
        return reservoirTemperatureCelsius;
    }

    public void setReservoirTemperatureCelsius(double temp) {
        this.reservoirTemperatureCelsius = temp;
    }

    public void updateReservoirState() {
        long currentLiters = getTotalCoolantLiters();
        if (currentLiters <= 0) {
            lastObservedTankLiters = 0L;
            if (!Double.isNaN(reservoirTemperatureCelsius)) {
                setReservoirTemperatureCelsius(Double.NaN);
            }
            return;
        }

        FluidStack f = getReservoirFluid();
        CoolantFluidProperty prop = reservoirFluidProperty(f);

        if (lastObservedTankLiters <= 0 || Double.isNaN(reservoirTemperatureCelsius)) {
            double baseline = CoolantFluidHelper.getBaselineInletTemperatureCelsius(
                f != null ? f.getFluid() : null,
                f,
                prop,
                host.getBiomeTemperatureCelsius());
            setReservoirTemperatureCelsius(baseline);
            lastObservedTankLiters = currentLiters;
            return;
        }

        if (currentLiters > lastObservedTankLiters) {
            long deltaLiters = currentLiters - lastObservedTankLiters;
            double inletTemp = CoolantFluidHelper.getBaselineInletTemperatureCelsius(
                f != null ? f.getFluid() : null,
                f,
                prop,
                host.getBiomeTemperatureCelsius());
            double newTemp = ((lastObservedTankLiters * reservoirTemperatureCelsius) + (deltaLiters * inletTemp))
                / currentLiters;
            setReservoirTemperatureCelsius(newTemp);
            lastObservedTankLiters = currentLiters;
        } else if (currentLiters < lastObservedTankLiters) {
            lastObservedTankLiters = currentLiters;
        }
    }

    /**
     * The coolant in the tank: its own fluid's properties (a sodium reservoir is molten before the loop has ever
     * filled), or null for a tank whose fluid is not a loop coolant or that does not report its fluid.
     */
    private CoolantFluidProperty reservoirFluidProperty(FluidStack f) {
        if (f == null || f.getFluid() == null) return null;
        return CoolantFluidProperty.get(
            f.getFluid()
                .getName());
    }

    public int drainFromReservoir(int liters) {
        int drained = drainFromReservoirInternal(liters);
        if (drained > 0) {
            lastObservedTankLiters = getTotalCoolantLiters();
            if (lastObservedTankLiters <= 0) {
                setReservoirTemperatureCelsius(Double.NaN);
            }
        }
        return drained;
    }

    public int fillIntoReservoir(int liters) {
        double loopTemp = host.getPumpCore()
            .getEngine() != null ? host.getPumpCore()
                .getEngine()
                .getAvgLoopTempCelsius() : Double.NaN;
        return fillIntoReservoir(liters, loopTemp);
    }

    public int fillIntoReservoir(int liters, double returningFluidTempCelsius) {
        long prevLiters = lastObservedTankLiters > 0 ? lastObservedTankLiters : getTotalCoolantLiters();
        int filled = fillIntoReservoirInternal(liters);
        if (filled > 0) {
            long newLiters = getTotalCoolantLiters();
            if (!Double.isNaN(returningFluidTempCelsius)) {
                if (prevLiters <= 0 || Double.isNaN(reservoirTemperatureCelsius)) {
                    setReservoirTemperatureCelsius(returningFluidTempCelsius);
                } else {
                    double newTemp = ((prevLiters * reservoirTemperatureCelsius) + (filled * returningFluidTempCelsius))
                        / newLiters;
                    setReservoirTemperatureCelsius(newTemp);
                }
            }
            lastObservedTankLiters = newLiters;
        }
        return filled;
    }

    public void addDissolvedGas(String gasName, long liters) {
        if (gasName == null || liters <= 0) return;
        dissolvedGases.merge(gasName, liters, Long::sum);
        dissolvedGasesVersion++;
    }

    public long getTotalDissolvedGasLiters() {
        long total = 0L;
        for (long val : dissolvedGases.values()) {
            total += val;
        }
        return total;
    }

    public double getDissolvedGasFraction() {
        long totalGas = getTotalDissolvedGasLiters();
        if (totalGas <= 0L) return 0.0;
        long totalLiters = getTotalCoolantLiters();
        if (totalLiters <= 0L) return 0.0;
        double frac = (double) totalGas / (double) totalLiters;
        return Math.min(1.0, Math.max(0.0, frac));
    }

    public double getDissolvedGasPercentage() {
        return getDissolvedGasFraction() * 100.0;
    }

    public long getDissolvedGasAmount(String gasName) {
        if (gasName == null) return 0L;
        for (Map.Entry<String, Long> entry : dissolvedGases.entrySet()) {
            if (entry.getKey()
                .equalsIgnoreCase(gasName)) {
                return entry.getValue();
            }
        }
        return 0L;
    }

    public Map<String, Long> getDissolvedGases() {
        return unmodifiableDissolvedGases;
    }

    public long getDissolvedGasesVersion() {
        return dissolvedGasesVersion;
    }

    public synchronized long extractDissolvedGas(String gasName, long maxLiters) {
        if (gasName == null || maxLiters <= 0) return 0L;
        String matchedKey = null;
        for (String k : dissolvedGases.keySet()) {
            if (k.equalsIgnoreCase(gasName)) {
                matchedKey = k;
                break;
            }
        }
        if (matchedKey == null) return 0L;
        long current = dissolvedGases.getOrDefault(matchedKey, 0L);
        long toExtract = Math.min(current, maxLiters);
        if (toExtract > 0) {
            long remaining = current - toExtract;
            if (remaining > 0) {
                dissolvedGases.put(matchedKey, remaining);
            } else {
                dissolvedGases.remove(matchedKey);
            }
            dissolvedGasesVersion++;
        }
        return toExtract;
    }

    public void saveNBTData(NBTTagCompound aNBT) {
        NBTTagCompound gasesTag = new NBTTagCompound();
        for (Map.Entry<String, Long> entry : dissolvedGases.entrySet()) {
            gasesTag.setLong(entry.getKey(), entry.getValue());
        }
        aNBT.setTag("mDissolvedGases", gasesTag);
        if (!Double.isNaN(reservoirTemperatureCelsius)) {
            aNBT.setDouble("mReservoirTemperatureCelsius", reservoirTemperatureCelsius);
        }
        aNBT.setLong("mLastObservedTankLiters", lastObservedTankLiters);
    }

    public void loadNBTData(NBTTagCompound aNBT) {
        dissolvedGases.clear();
        dissolvedGasesVersion++;
        if (aNBT.hasKey("mDissolvedGases")) {
            NBTTagCompound gasesTag = aNBT.getCompoundTag("mDissolvedGases");
            for (Object keyObj : gasesTag.func_150296_c()) {
                String key = (String) keyObj;
                dissolvedGases.put(key, gasesTag.getLong(key));
            }
        }
        if (aNBT.hasKey("mReservoirTemperatureCelsius")) {
            reservoirTemperatureCelsius = aNBT.getDouble("mReservoirTemperatureCelsius");
        }
        if (aNBT.hasKey("mLastObservedTankLiters")) {
            lastObservedTankLiters = aNBT.getLong("mLastObservedTankLiters");
        }
    }
}

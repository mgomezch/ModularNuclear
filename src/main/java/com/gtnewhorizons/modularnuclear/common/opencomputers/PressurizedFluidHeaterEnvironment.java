package com.gtnewhorizons.modularnuclear.common.opencomputers;

import java.util.LinkedHashMap;
import java.util.Map;

import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTEPressurizedFluidHeater;
import com.gtnewhorizons.modularnuclear.common.tileentity.TileEntityLoopInstrument;
import com.gtnewhorizons.modularnuclear.common.util.Swallowed;

import gregtech.api.enums.GTValues;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.NamedBlock;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.ManagedEnvironment;

/**
 * OpenComputers component environment for {@link MTEPressurizedFluidHeater}.
 */
public class PressurizedFluidHeaterEnvironment extends ManagedEnvironment implements NamedBlock {

    private final MTEPressurizedFluidHeater heater;

    public PressurizedFluidHeaterEnvironment(MTEPressurizedFluidHeater heater) {
        this.heater = heater;
        try {
            var builder = Network.newNode(this, Visibility.Network);
            if (builder != null) {
                setNode(
                    builder.withComponent("pressurized_fluid_heater")
                        .create());
            }
        } catch (Throwable swallowed) {
            Swallowed.note(swallowed);
        }
    }

    @Override
    public String preferredName() {
        return "pressurized_fluid_heater";
    }

    @Override
    public int priority() {
        return 10;
    }

    @Callback(
        doc = "function():table -- Returns full telemetry and operating parameters of the pressurized fluid heater.",
        direct = true)
    public Object[] getHeaterStats(Context context, Arguments args) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (heater == null) {
            data.put("isFormed", false);
            data.put("isActive", false);
            return new Object[] { data };
        }

        boolean formed = heater.mMachine;
        boolean active = heater.getBaseMetaTileEntity() != null && heater.getBaseMetaTileEntity()
            .isActive();
        boolean allowedToWork = heater.isAllowedToWork();

        data.put("isFormed", formed);
        data.put("isActive", active);
        data.put("isAllowedToWork", allowedToWork);
        data.put("powerFailed", heater.isPowerFailed());
        data.put("disconnectionReason", heater.getDisconnectionReason() != null ? heater.getDisconnectionReason() : "");
        data.put("structureLength", heater.getStructureLength());
        data.put("ringCount", heater.getRingCount());
        data.put(
            "coilName",
            heater.getCoilLevel() != null ? heater.getCoilLevel()
                .getName() : "");
        data.put("coilTier", heater.getMaxAllowedEnergyHatchTier());
        data.put(
            "maxEnergyHatchTierName",
            heater.getMaxAllowedEnergyHatchTier() >= 0 && heater.getMaxAllowedEnergyHatchTier() < GTValues.VN.length
                ? GTValues.VN[heater.getMaxAllowedEnergyHatchTier()]
                : "");
        data.put(
            "pipeCasingMaterial",
            heater.getHeaterPipeCasingTier() != null ? heater.getHeaterPipeCasingTier()
                .getDisplayName() : "");
        data.put("innerTemperatureCelsius", heater.getInnerTemperatureCelsius());
        data.put("maxOperatingTemperatureCelsius", heater.getMaxOperatingTemperatureCelsius());
        data.put("maxOperatingPressureBar", heater.getMaxOperatingPressureBar());
        data.put("heatCapacityHuPerC", heater.getHeatCapacityHuPerC());
        data.put("exchangeAreaM2", heater.getExchangeAreaM2());
        data.put("coolantFlowRateLPerSec", heater.getLastCoolantFlowLitersPerSecond());
        data.put("heatTransferRateHuPerTick", heater.getLastHeatTransferHuPerTick());
        data.put("heatTransferRateHuPerSec", heater.getLastHeatTransferHuPerTick() * 20.0);
        data.put("heatTransferRateWatts", heater.getLastHeatTransferWatts());
        data.put("consumedEuPerTick", heater.getLastConsumedEuPerTick());
        data.put("maxEuPerTick", heater.getMaxEuPerTick());
        data.put("heatAddedHuPerTick", heater.getLastHeatingHuPerTick());
        data.put("coilHeatingEfficiency", heater.getCoilHeatingEfficiency());
        data.put("efficiencyFactor", heater.getEfficiencyFactor());
        data.put("redstoneOutput", (int) heater.getRedstoneOutput());
        data.put("fineOutputStrength", heater.getFineOutputStrength());
        data.put("bundledMode", heater.getBundledChannelMode());
        data.put("bundledModeName", TileEntityLoopInstrument.getBundledModeName(heater.getBundledChannelMode()));
        return new Object[] { data };
    }

    @Callback(doc = "function():table -- Alias for getHeaterStats.", direct = true)
    public Object[] getTelemetry(Context context, Arguments args) {
        return getHeaterStats(context, args);
    }

    @Callback(doc = "function():number -- Returns current inner temperature in Celsius.", direct = true)
    public Object[] getInnerTemperature(Context context, Arguments args) {
        return new Object[] { heater.getInnerTemperatureCelsius() };
    }

    @Callback(doc = "function():number -- Returns max operating temperature in Celsius.", direct = true)
    public Object[] getMaxTemperature(Context context, Arguments args) {
        return new Object[] { heater.getMaxOperatingTemperatureCelsius() };
    }

    @Callback(doc = "function():number -- Returns max operating pressure in bar.", direct = true)
    public Object[] getMaxPressure(Context context, Arguments args) {
        return new Object[] { heater.getMaxOperatingPressureBar() };
    }

    @Callback(doc = "function():number -- Returns total thermal heat capacity in HU/°C.", direct = true)
    public Object[] getHeatCapacity(Context context, Arguments args) {
        return new Object[] { heater.getHeatCapacityHuPerC() };
    }

    @Callback(doc = "function():number -- Returns convective heat transfer area in m².", direct = true)
    public Object[] getHeatTransferArea(Context context, Arguments args) {
        return new Object[] { heater.getExchangeAreaM2() };
    }

    @Callback(doc = "function():number -- Returns current coolant flow rate in L/s.", direct = true)
    public Object[] getCoolantFlowRate(Context context, Arguments args) {
        return new Object[] { heater.getLastCoolantFlowLitersPerSecond() };
    }

    @Callback(
        doc = "function():number -- Returns current convective heat transfer rate in HU/t (positive: heater -> coolant, negative: coolant -> heater).",
        direct = true)
    public Object[] getHeatTransferRate(Context context, Arguments args) {
        return new Object[] { heater.getLastHeatTransferHuPerTick() };
    }

    @Callback(doc = "function():number -- Returns EU consumed on the last tick.", direct = true)
    public Object[] getConsumedEU(Context context, Arguments args) {
        return new Object[] { heater.getLastConsumedEuPerTick() };
    }

    @Callback(doc = "function():number -- Returns maximum EU/t capacity based on rings and coil tier.", direct = true)
    public Object[] getMaxEU(Context context, Arguments args) {
        return new Object[] { heater.getMaxEuPerTick() };
    }

    @Callback(
        doc = "function():number -- Returns heating coil efficiency multiplier (0.25 at Cupronickel up to 1.0 at MAX coil tier).",
        direct = true)
    public Object[] getCoilHeatingEfficiency(Context context, Arguments args) {
        return new Object[] { heater.getCoilHeatingEfficiency() };
    }

    @Callback(doc = "function():boolean -- Returns true if the heater is in a powerfailed state.", direct = true)
    public Object[] isPowerFailed(Context context, Arguments args) {
        return new Object[] { heater.isPowerFailed() };
    }

    @Callback(
        doc = "function():number -- Returns vanilla redstone output level (0 to 15) from energy detector cover.",
        direct = true)
    public Object[] getRedstoneOutput(Context context, Arguments args) {
        return new Object[] { (int) heater.getRedstoneOutput() };
    }

    @Callback(
        doc = "function():number -- Returns fine-grained 8-bit signal strength (0 to 255) from energy detector cover.",
        direct = true)
    public Object[] getFineOutputStrength(Context context, Arguments args) {
        return new Object[] { heater.getFineOutputStrength() };
    }

    @Callback(
        doc = "function():number, string -- Returns ProjectRed bundled channel mode index and name.",
        direct = true)
    public Object[] getBundledMode(Context context, Arguments args) {
        return new Object[] { heater.getBundledChannelMode(),
            TileEntityLoopInstrument.getBundledModeName(heater.getBundledChannelMode()) };
    }

    @Callback(
        doc = "function(mode:number):boolean -- Sets ProjectRed bundled channel mode (0: All channels, 1-16: Color 0-15).",
        direct = true)
    public Object[] setBundledMode(Context context, Arguments args) {
        int mode = args.checkInteger(0);
        if (mode >= 0 && mode < TileEntityLoopInstrument.BUNDLED_MODE_COUNT) {
            heater.setBundledChannelMode(mode);
            return new Object[] { true };
        }
        return new Object[] { false, "Bundled mode out of bounds (0 to 16)" };
    }
}

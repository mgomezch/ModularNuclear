package com.gtnewhorizons.modularnuclear.common.coolantloop.pump;

import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantFluidProperty;
import com.gtnewhorizons.modularnuclear.common.coolantloop.CoolantLoopEngine;
import com.gtnewhorizons.modularnuclear.common.coolantloop.ICoolantLoopDevice;
import com.gtnewhorizons.modularnuclear.common.coolantloop.ICoolantLoopPump;
import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopGraphCrawler;
import com.gtnewhorizons.modularnuclear.common.util.LocalizedText;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.shutdown.ShutDownReason;

/**
 * A machine that owns a coolant loop through a {@link CoolantPumpCore}. See Note [Pump cores and their hosts]
 */
public interface ICoolantPumpHost extends ICoolantLoopPump {

    CoolantPumpCore getPumpCore();

    /** The reservoir of a new core; called once, as the host is constructed. */
    CoolantPumpReservoir createReservoir();

    IGregTechTileEntity getBaseMetaTileEntity();

    default IMetaTileEntity asMetaTileEntity() {
        return (IMetaTileEntity) this;
    }

    boolean isAllowedToWork();

    /** Whether the machine's own structure stands. */
    boolean isPumpStructureFormed();

    void stopMachine(ShutDownReason reason);

    boolean drainEnergy(long eu);

    /** EU per tick the pump draws while it fills, drains or drives its loop. */
    long getRequiredPowerDrawEU();

    /** The ambient temperature around the pump. */
    double getBiomeTemperatureCelsius();

    /** Where the loop leaves the pump, or null while there is no such place. */
    LoopPort getDischargePort();

    /** Where the loop returns to the pump, or null while there is no such place. */
    LoopPort getSuctionPort();

    /**
     * After a load: finds the pump's ports again. Null while their chunks are not loaded yet, else whether both are
     * there.
     */
    Boolean restoreLoopPorts(World world);

    /**
     * Adds the chunks the pump and its tank occupy. Before the structure's first check after a load its size is not
     * known, and this covers the largest it can be.
     */
    void collectPumpChunks(Set<Long> chunks, boolean beforeStructureCheck);

    /** The impeller that drives a liquid loop, or null for a pump that only charges gas loops. */
    PumpImpeller getImpeller();

    /** What the pump can take. */
    default PumpLimits getPumpLimits() {
        return PumpLimits.UNLIMITED;
    }

    // What follows passes the core's public methods through

    default long getLastCrawlStartTime() {
        return getPumpCore().getLastCrawlStartTime();
    }

    default long getLastCrawlCompleteTime() {
        return getPumpCore().getLastCrawlCompleteTime();
    }

    default LoopGraphCrawler.CrawlSession getActiveCrawl() {
        return getPumpCore().getActiveCrawl();
    }

    default LoopGraphCrawler.CrawlResult getLastCrawlResult() {
        return getPumpCore().getLastCrawlResult();
    }

    default String getLoopGraphDataJson() {
        return getPumpCore().getLoopGraphDataJson();
    }

    default boolean isPowerFailed() {
        return getPumpCore().isPowerFailed();
    }

    default void setLoopStatus(LocalizedText loopStatus) {
        getPumpCore().setLoopStatus(loopStatus);
    }

    default CoolantPumpReservoir getReservoir() {
        return getPumpCore().getReservoir();
    }

    default void addLoopDevice(ICoolantLoopDevice dev) {
        getPumpCore().addLoopDevice(dev);
    }

    default void rebuildDeviceBindings() {
        getPumpCore().rebuildDeviceBindings();
    }

    default List<CoolantPumpCore.DeviceSegmentBinding> getDeviceBindings() {
        return getPumpCore().getDeviceBindings();
    }

    default List<ICoolantLoopDevice> getLoopDevices() {
        return getPumpCore().getLoopDevices();
    }

    default double getCoolantTemperatureCelsius() {
        return getPumpCore().getCoolantTemperatureCelsius();
    }

    default FluidStack getReservoirFluid() {
        return getPumpCore().getReservoirFluid();
    }

    default boolean crawlLoopGraph() {
        return getPumpCore().crawlLoopGraph();
    }

    default void stepActiveCrawl() {
        getPumpCore().stepActiveCrawl();
    }

    default boolean applyCrawlResult(LoopGraphCrawler.CrawlResult result) {
        return getPumpCore().applyCrawlResult(result);
    }

    default boolean triggerPowerfail() {
        return getPumpCore().triggerPowerfail();
    }

    default boolean isLoopAtRest() {
        return getPumpCore().isLoopAtRest();
    }

    default void processCoastingThermalExchange() {
        getPumpCore().processCoastingThermalExchange();
    }

    default LocalizedText findLoopBreak(World world) {
        return getPumpCore().findLoopBreak(world);
    }

    @Override
    default void onDeviceDisconnected(ICoolantLoopDevice device, LocalizedText reason) {
        getPumpCore().onDeviceDisconnected(device, reason);
    }

    default boolean stepCoolantLoop() {
        return getPumpCore().stepCoolantLoop();
    }

    @Override
    default double getPumpFluidTemperatureCelsius() {
        return getPumpCore().getPumpFluidTemperatureCelsius();
    }

    default void failOnLiquidNuclearFuel(String fluidName) {
        getPumpCore().failOnLiquidNuclearFuel(fluidName);
    }

    default CoolantLoopEngine getEngine() {
        return getPumpCore().getEngine();
    }

    default boolean isLoopFormed() {
        return getPumpCore().isLoopFormed();
    }

    default void setLoopFormed(boolean formed) {
        getPumpCore().setLoopFormed(formed);
    }

    @Override
    default LocalizedText getLoopStatusMessage() {
        return getPumpCore().getLoopStatusMessage();
    }

    default void setLoopStatusKey(String key, Object... args) {
        getPumpCore().setLoopStatusKey(key, args);
    }

    @Override
    default boolean isCoolantFlowing() {
        return getPumpCore().isCoolantFlowing();
    }

    default CoolantPumpCore.LoopState getLoopState() {
        return getPumpCore().getLoopState();
    }

    default void setLoopState(CoolantPumpCore.LoopState state) {
        getPumpCore().setLoopState(state);
    }

    default long getCurrentFillLiters() {
        return getPumpCore().getCurrentFillLiters();
    }

    default void setCurrentFillLiters(long liters) {
        getPumpCore().setCurrentFillLiters(liters);
    }

    default long getRequiredFillLiters() {
        return getPumpCore().getRequiredFillLiters();
    }

    default void setRequiredFillLiters(long liters) {
        getPumpCore().setRequiredFillLiters(liters);
    }

    default void setFillRateLitersPerTick(long litersPerTick) {
        getPumpCore().setFillRateLitersPerTick(litersPerTick);
    }

    @Override
    default double getFillFraction() {
        return getPumpCore().getFillFraction();
    }

    @Override
    default String getLoopStateName() {
        return getPumpCore().getLoopStateName();
    }

    @Override
    default boolean isFlowing() {
        return getPumpCore().isFlowing();
    }

    @Override
    default double getMaxFlowRateLitersPerSecond() {
        return getPumpCore().getMaxFlowRateLitersPerSecond();
    }

    @Override
    default double getFlowRateLitersPerSecond() {
        return getPumpCore().getFlowRateLitersPerSecond();
    }

    @Override
    default double getPumpMechanicalPressureBar() {
        return getPumpCore().getPumpMechanicalPressureBar();
    }

    @Override
    default double getPeakLoopPressureBar() {
        return getPumpCore().getPeakLoopPressureBar();
    }

    @Override
    default double getPeakLoopTempCelsius() {
        return getPumpCore().getPeakLoopTempCelsius();
    }

    @Override
    default long getPowerConsumptionEU() {
        return getPumpCore().getPowerConsumptionEU();
    }

    @Override
    default double getPumpMechanicalPowerWatts() {
        return getPumpCore().getPumpMechanicalPowerWatts();
    }

    @Override
    default long getReservoirCapacityLiters() {
        return getPumpCore().getReservoirCapacityLiters();
    }

    default int drainFromReservoir(int liters) {
        return getPumpCore().drainFromReservoir(liters);
    }

    default int fillIntoReservoir(int liters) {
        return getPumpCore().fillIntoReservoir(liters);
    }

    default boolean isReservoirFull() {
        return getPumpCore().isReservoirFull();
    }

    @Override
    default void consumeCoolant(long liters) {
        getPumpCore().consumeCoolant(liters);
    }

    @Override
    default void addDissolvedGas(String gasName, long liters) {
        getPumpCore().addDissolvedGas(gasName, liters);
    }

    @Override
    default double getDissolvedGasFraction() {
        return getPumpCore().getDissolvedGasFraction();
    }

    @Override
    default double getDissolvedGasPercentage() {
        return getPumpCore().getDissolvedGasPercentage();
    }

    @Override
    default long getTotalDissolvedGasLiters() {
        return getPumpCore().getTotalDissolvedGasLiters();
    }

    @Override
    default long getDissolvedGasAmount(String gasName) {
        return getPumpCore().getDissolvedGasAmount(gasName);
    }

    @Override
    default Map<String, Long> getDissolvedGases() {
        return getPumpCore().getDissolvedGases();
    }

    @Override
    default long extractDissolvedGas(String gasName, long maxLiters) {
        return getPumpCore().extractDissolvedGas(gasName, maxLiters);
    }

    @Override
    default long getTotalCoolantLiters() {
        return getPumpCore().getTotalCoolantLiters();
    }

    @Override
    default CoolantFluidProperty getCoolantFluidProperty() {
        return getPumpCore().getCoolantFluidProperty();
    }
}

package com.gtnewhorizons.modularnuclear.common.metatileentity.multi;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.modularnuclear.common.coolantloop.ICoolantPassageHatch;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearBus;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHatch;
import com.gtnewhorizons.modularnuclear.common.nuclear.CoreHatchRules;
import com.gtnewhorizons.modularnuclear.common.nuclear.INuclearTile;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.util.FluidRoles;
import com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification;
import com.gtnewhorizons.modularnuclear.common.util.TickPhase;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * The reactor's cycle, run every {@code ticksPerCycle} ticks while it is formed: prepare the cells, check the coolant
 * feeds, simulate the core, collect its outputs, apply overheating and end the cycle. Between cycles only the active
 * vents and exchangers run.
 */
final class NuclearReactorCycle {

    private NuclearReactorCycle() {}

    static void tick(MTENuclearReactor r, IGregTechTileEntity base, long aTick) {
        int tpc = r.getTicksPerCycle();
        boolean cycleDue = aTick % tpc == 0 || r.mCycleDeferred;
        if (r.mGrid != null && !cycleDue) {
            NuclearSimulationEngine
                .processActiveVentsAndExchangers(r.mGrid, r.gridSize, r.gridSize, r.mCachedAmbientTemp);
        }

        if (r.mGrid != null && cycleDue && holdForPassagePumps(r)) {
            r.mCycleDeferred = true;
        } else if (r.mGrid != null && cycleDue) {
            r.mCycleDeferred = false;
            prepareCycle(r);

            double ambient = r.getAmbientTemperature();

            if (!checkCoolantFeeds(r, ambient)) return;

            beginCycleTotals(r);

            double maintEff = r.getMaintenanceEfficiency();
            NuclearSimulationEngine.SimulationResult res = r.engine()
                .simulate(r.mGrid, r.gridSize, r.gridSize, maintEff, ambient);
            r.mCoreTemp = res.maxTemperature;
            r.mAvgTemp = res.averageTemperature;
            r.mNeutronsProduced = res.totalNeutronsGenerated;
            r.mFastAbsorbed = res.fastNeutronsAbsorbed;
            r.mThermalAbsorbed = res.thermalNeutronsAbsorbed;
            r.mEscapedNeutrons = res.neutronsEscaped;
            r.mReactivity = res.averageReactivity;
            updateIdleReason(r);

            syncCherenkovMetric(r, res, base, aTick);

            accumulateWallWear(r, res, tpc, base);

            collectCycleOutputs(r, maintEff);

            applyOverheating(r);

            endCycle(r, res, maintEff);

            // Meltdown: strictly when reactor damage reaches 100%
            if (r.mReactorDamage >= 100.0) {
                r.triggerMeltdown();
            }
        }
    }

    /**
     * Start of a cycle: bus damage failsafes and the casing tier the core cells see.
     */
    private static void prepareCycle(MTENuclearReactor r) {
        for (IGregTechTileEntity te : r.mNuclearTiles) {
            if (te != null && te.getMetaTileEntity() instanceof MTEHatchNuclearBus bus) {
                bus.processMaxDamageFailsafe();
            }
        }

        r.updateNuclearTilesPipeTier();
    }

    /** Applies CoreHatchRules to every coolant hatch; false when a thermal shock melted the core down or broke it. */
    private static boolean checkCoolantFeeds(MTENuclearReactor r, double ambient) {
        // Check coolant boiling against ambient or dry hatch injection (thermal shock)
        for (IGregTechTileEntity te : r.mNuclearTiles) {
            if (te != null && te.getMetaTileEntity() instanceof MTEHatchNuclearHatch hatch) {
                // Liquid fuel does not boil/evaporate and is exempt from thermal shock
                FluidRoles roles = FluidRoles.of(hatch.mInputFluid);
                if (roles.isFuel()) {
                    hatch.mWasDry = false;
                    continue;
                }
                if (hatch.mInputFluid != null && hatch.mInputFluid.amount > 0) {
                    String name = roles.lowerName();
                    CoreHatchRules.CoolantFeed feed = CoreHatchRules
                        .checkCoolantFeed(name, hatch.mWasDry, hatch.mTemperature, ambient);
                    if (feed != CoreHatchRules.CoolantFeed.SAFE) {
                        r.triggerThermalShock(hatch, CoreHatchRules.describe(feed, name, hatch.mTemperature, ambient));
                        if (r.mReactorDamage >= 100.0 || !r.mMachine) return false;
                        continue;
                    }
                    hatch.mWasDry = false;
                } else {
                    // Hatch has zero coolant: controller remembers that
                    hatch.mWasDry = true;
                }
            }
        }
        return true;
    }

    /** Clears this cycle's counters and has every core cell reread its contents. */
    private static void beginCycleTotals(MTENuclearReactor r) {
        r.mCycleZeroedCoolantItems = 0;
        r.mCycleZeroedFuelItems = 0;
        r.mCycleConsumedCoolant = 0;
        r.mCycleProducedHotCoolant = 0;
        r.mCycleTransmutationLoss = 0;
        r.mCycleTransmutationByproducts = 0;
        r.mCycleDepletedLiquidFuel = 0;

        if (r.mGrid != null) {
            for (int x = 0; x < r.gridSize; x++) {
                for (int y = 0; y < r.gridSize; y++) {
                    if (r.mGrid[x][y] instanceof NuclearGridTile gt) {
                        gt.refreshProperties();
                    }
                }
            }
        }
    }

    /**
     * Sends clients the Cherenkov glow for this cycle's flux when it changes, and every five seconds while it glows.
     */
    private static void syncCherenkovMetric(MTENuclearReactor r, NuclearSimulationEngine.SimulationResult res,
        IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        // Dynamic Cherenkov radiation intensity metric: active baseline + scaling with flux
        double flux = Math.max(res.neutronsEscaped, res.totalNeutronsGenerated * 0.15);
        float cherenkovMetric = (flux > 0) ? (float) Math.min(1.0, 0.40 + 0.60 * (flux / (flux + 400.0))) : 0.0f;
        byte metricByte = (byte) Math.round(cherenkovMetric * 100.0f);
        int tier = r.getReactorTier();
        if (metricByte != r.mLastSentCherenkovMetricByte) {
            r.mLastSentCherenkovMetricByte = metricByte;
            if (tier > 0) {
                aBaseMetaTileEntity.sendBlockEvent((byte) 100, (byte) tier);
            }
            aBaseMetaTileEntity.sendBlockEvent((byte) 101, metricByte);
        } else if (TickPhase.due(aTick, r.schedulePhase(), 100) && cherenkovMetric > 0) {
            if (tier > 0) {
                aBaseMetaTileEntity.sendBlockEvent((byte) 100, (byte) tier);
            }
            aBaseMetaTileEntity.sendBlockEvent((byte) 101, metricByte);
        }
    }

    /**
     * Neutrons escaping into the vessel wall wear it: once a minute, a chance of a maintenance issue rising with the
     * flux.
     */
    private static void accumulateWallWear(MTENuclearReactor r, NuclearSimulationEngine.SimulationResult res, int tpc,
        IGregTechTileEntity aBaseMetaTileEntity) {
        // Accumulate escaped neutrons for vessel maintenance wear mechanic
        r.mWallNeutronAccumulator += res.neutronsEscaped;
        r.mWallMaintenanceTimer += tpc;

        // Once per minute (1200 ticks = 60s), check maintenance issue probability:
        // S(N) = N^2 / (N^2 + 1000^2), scaling maintenance wear from 1.0x to 10.0x base rate (0.01 per minute)
        if (r.mWallMaintenanceTimer >= 1200) {
            r.mWallMaintenanceTimer = 0;
            double avgEscaped = (double) r.mWallNeutronAccumulator / 60.0;
            r.mWallNeutronAccumulator = 0;
            double sFactor = (avgEscaped > 0.0)
                ? (avgEscaped * avgEscaped) / (avgEscaped * avgEscaped + 1000.0 * 1000.0)
                : 0.0;
            double multiplier = 1.0 + 9.0 * sFactor;
            double baseProb = 0.01;
            double prob = baseProb * multiplier;
            if ((r.getRandomNumber(1000000) / 1000000.0) < prob) {
                r.causeNewMaintenanceIssue();
            }
        }
    }

    /** Direct power from radiovoltaics and the hot coolant the hatches produced this cycle. */
    private static void collectCycleOutputs(MTENuclearReactor r, double maintEff) {
        // Sum direct EU from radiovoltaic cells across the grid
        long directEU = 0;
        for (int x = 0; x < r.gridSize; x++) {
            for (int y = 0; y < r.gridSize; y++) {
                INuclearTile tile = r.mGrid[x][y];
                if (tile instanceof NuclearGridTile gt && gt.isBus()) {
                    directEU += gt.getBus().mDirectEUProduced;
                }
            }
        }
        r.mDirectPowerEUt = (long) Math.round(directEU * maintEff);

        // Sum output coolant production across all coolant hatches
        int totalCoolantProduced = 0;
        String coolantName = "";
        for (IGregTechTileEntity te : r.mNuclearTiles) {
            if (te != null && te.getMetaTileEntity() instanceof MTEHatchNuclearHatch hatch) {
                if (hatch.mLastProducedAmount > 0) {
                    totalCoolantProduced += hatch.mLastProducedAmount;
                    if (coolantName.isEmpty() && hatch.mLastProducedFluidName != null
                        && !hatch.mLastProducedFluidName.isEmpty()) {
                        coolantName = hatch.mLastProducedFluidName;
                    }
                }
            }
        }
        r.mOutputCoolantRate = totalCoolantProduced;
        r.mOutputCoolantName = coolantName;
        r.mEfficiency = (int) Math.round(maintEff * 10000);
    }

    /** Columns past the casing's operating temperature lose their non-fuel contents and add damage. */
    private static void applyOverheating(MTENuclearReactor r) {
        NuclearReactorMeltdown.applyOverheating(r);
    }

    /**
     * Right after a load, holds the cycle while a passage that was in a loop when saved has not been bound to its pump
     * again, so the core never heats without its cooling. Only for a few ticks: a reactor that stops unexpectedly can
     * starve whatever it powers.
     */
    private static boolean holdForPassagePumps(MTENuclearReactor r) {
        boolean awaiting = false;
        for (ICoolantPassageHatch hp : r.mBottomHighPressureHatches) {
            if (hp != null && hp.isAwaitingLoopPump()) awaiting = true;
        }
        if (!awaiting) return false;
        if (++r.mPassagePumpWaitTicks <= r.PASSAGE_PUMP_WAIT_TICKS) return true;
        for (ICoolantPassageHatch hp : r.mBottomHighPressureHatches) {
            if (hp != null) hp.stopAwaitingLoopPump();
        }
        return false;
    }

    /**
     * When the core produced no neutrons, works out why for the GUI: no fuel at all, or items that look like fuel
     * (depleted or empty rods, spent products, items the config blocks) that the core refuses.
     */
    private static void updateIdleReason(MTENuclearReactor r) {
        r.mIdleReasonKey = "";
        r.mIdleCount = 0;
        r.mIdleDetailKey = "";
        if (r.mNeutronsProduced > 0 || r.mGrid == null) return;
        int burning = 0, refused = 0;
        NuclearFuelClassification.NotFuelReason firstReason = null;
        for (int x = 0; x < r.gridSize; x++) {
            for (int y = 0; y < r.gridSize; y++) {
                if (!(r.mGrid[x][y] instanceof NuclearGridTile gt)) continue;
                if (gt.isFuel() || gt.isNeutronSource()) {
                    burning++;
                } else if (gt.isBus()) {
                    ItemStack in = gt.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT];
                    NuclearFuelClassification.NotFuelReason reason = (in != null)
                        ? NuclearFuelClassification.unexpectedNotFuel(in)
                        : null;
                    if (reason != null) {
                        refused++;
                        if (firstReason == null) firstReason = reason;
                    }
                }
            }
        }
        if (burning > 0) return;
        if (refused > 0) {
            r.mIdleReasonKey = "modularnuclear.gui.idle.refused";
            r.mIdleCount = refused;
            r.mIdleDetailKey = firstReason.langKey();
        } else {
            r.mIdleReasonKey = "modularnuclear.gui.idle.no_fuel";
        }
    }

    /** Closes the cycle: last-cycle totals, telemetry, coolant tracking, control hatches and metrics history. */
    private static void endCycle(MTENuclearReactor r, NuclearSimulationEngine.SimulationResult res, double maintEff) {
        // 5. Update last cycle totals, calculate telemetry, coolant tracking, and nuclear control hatches
        r.mZeroedCoolantItemsLastCycle = r.mCycleZeroedCoolantItems;
        r.mZeroedFuelItemsLastCycle = r.mCycleZeroedFuelItems;
        r.mConsumedCoolantLastCycle = r.mCycleConsumedCoolant;
        r.mProducedHotCoolantLastCycle = r.mCycleProducedHotCoolant;
        r.mTransmutationLossLastCycle = r.mCycleTransmutationLoss;
        r.mTransmutationByproductsLastCycle = r.mCycleTransmutationByproducts;
        r.mDepletedLiquidFuelLastCycle = r.mCycleDepletedLiquidFuel;

        r.updateCoolantTracking();
        r.calculateTelemetry();
        r.updateControlHatches();

        r.mTotalCycles++;
        if (r.hasComputerMonitorCover()) {
            r.recordMetricsCycleSnapshot(res, maintEff);
        } else if (!r.mMetricsHistory.isEmpty()) {
            r.mMetricsHistory.clear();
        }
    }
}

package com.gtnewhorizons.modularnuclear.common.metatileentity.multi;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.modularnuclear.common.gui.metrics.ReactorCycleSnapshot;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearAdvancedControlRod;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearBus;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHatch;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHighPressure;
import com.gtnewhorizons.modularnuclear.common.nuclear.INuclearTile;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.nuclear.ReactorGridSyncData;
import com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification;

/** What the reactor GUI shows of the grid (its header and cells) and the per-cycle metrics snapshot. */
final class NuclearReactorGridSnapshot {

    private NuclearReactorGridSnapshot() {}

    /** The reactor-wide readings the GUI shows. */
    static void fillGridHeader(MTENuclearReactor reactor, ReactorGridSyncData data) {
        data.gridSize = reactor.gridSize;
        data.coreDimension = reactor.coreDimension;
        data.coreTemp = (float) reactor.mCoreTemp;
        data.avgTemp = (float) reactor.mAvgTemp;
        data.efficiency = reactor.mReactivity;
        data.pipeTier = reactor.mPipeTier;
        data.directPowerEUt = reactor.mDirectPowerEUt;
        data.neutronsProduced = reactor.mNeutronsProduced;
        data.cumulativeNeutrons = reactor.mCumulativeNeutrons;
        data.fastAbsorbed = reactor.mFastAbsorbed;
        data.thermalAbsorbed = reactor.mThermalAbsorbed;
        data.escapedNeutrons = reactor.mEscapedNeutrons;
        data.outputCoolantRate = reactor.mOutputCoolantRate;
        data.outputCoolantName = reactor.mOutputCoolantName;
        data.scram = reactor.mScram;
        data.ambientTemp = (float) reactor.getAmbientTemperature();
        data.reactorDamage = (float) reactor.mReactorDamage;
        data.ticksPerCycle = reactor.getTicksPerCycle();
    }

    /**
     * Fills a cell of the GUI's grid from the core cell at (x, y). Without {@code copyStacks} the cell holds the
     * hatches' own stacks: only for comparing, never to keep.
     */
    static void fillGridCell(MTENuclearReactor reactor, int x, int y, ReactorGridSyncData.ReactorGridCellData cell,
        boolean copyStacks) {
        INuclearTile tile = reactor.mGrid[x][y];
        cell.exists = false;
        cell.isFluid = false;
        cell.isHighPressure = false;
        cell.hasControlRod = false;
        cell.isAdvancedControlRod = false;
        cell.controlRodInsertion = 0;
        cell.controlRodType = 0;
        cell.moderatorInsertion = 0;
        cell.moderatorStack = null;
        cell.itemStack = null;
        cell.fluidStack = null;
        cell.temperature = 20.0f;
        cell.heatOutput = 0.0f;
        cell.fastFlux = 0;
        cell.thermalFlux = 0;
        cell.fastAbsorbed = 0;
        cell.thermalAbsorbed = 0;
        cell.directEU = 0;
        cell.notFuelReason = 0;
        if (!(tile instanceof NuclearGridTile gt)) return;
        cell.exists = true;
        cell.temperature = (float) gt.getTemperature();
        if (gt.isBus()) {
            MTEHatchNuclearBus bus = gt.getBus();
            ItemStack in = bus.getInput();
            cell.itemStack = copyStacks ? ItemStack.copyItemStack(in) : in;
            cell.fastFlux = bus.mLastFastFlux;
            cell.thermalFlux = bus.mLastThermalFlux;
            cell.fastAbsorbed = bus.mLastFastAbsorbed;
            cell.thermalAbsorbed = bus.mLastThermalAbsorbed;
            cell.directEU = bus.mDirectEUProduced;
            cell.heatOutput = (float) bus.mLastHeatOutput;
            NuclearFuelClassification.NotFuelReason refused = (in != null && !gt.isFuel())
                ? NuclearFuelClassification.unexpectedNotFuel(in)
                : null;
            cell.notFuelReason = refused != null ? (byte) (refused.ordinal() + 1) : 0;
        } else if (gt.isHatch()) {
            MTEHatchNuclearHatch hatch = gt.getHatch();
            cell.isFluid = true;
            cell.fluidStack = (copyStacks && hatch.mInputFluid != null) ? hatch.mInputFluid.copy() : hatch.mInputFluid;
            cell.fastFlux = hatch.mLastFastFlux;
            cell.thermalFlux = hatch.mLastThermalFlux;
            cell.fastAbsorbed = hatch.mLastFastAbsorbed;
            cell.thermalAbsorbed = hatch.mLastThermalAbsorbed;
            cell.heatOutput = (float) hatch.mLastHeatOutput;
        } else if (gt.isHighPressureHatch()) {
            MTEHatchNuclearHighPressure hp = gt.getHighPressureHatch();
            cell.isHighPressure = true;
            cell.fastFlux = hp.mLastFastFlux;
            cell.thermalFlux = hp.mLastThermalFlux;
            cell.fastAbsorbed = hp.mLastFastAbsorbed;
            cell.thermalAbsorbed = hp.mLastThermalAbsorbed;
            cell.heatOutput = (float) hp.mLastHeatOutput;
        }

        if (gt.hasControlRod()) {
            MTEHatchNuclearControlRod rod = gt.getBottomControlRod();
            cell.hasControlRod = true;
            cell.isAdvancedControlRod = rod.hasModerator() || (rod instanceof MTEHatchNuclearAdvancedControlRod);
            cell.controlRodInsertion = rod.getInsertionPercent();
            cell.controlRodType = MTEHatchNuclearControlRod
                .getRodType(rod.mInventory[MTEHatchNuclearControlRod.SLOT_ROD])
                .ordinal();
            if (cell.isAdvancedControlRod) {
                cell.moderatorInsertion = rod.getModeratorInsertionPercent();
                ItemStack mod = rod.getModeratorStack();
                cell.moderatorStack = copyStacks ? ItemStack.copyItemStack(mod) : mod;
            }
        }
    }

    /** A full grid snapshot of copies (the client-side fallback before the first sync arrives). */
    static ReactorGridSyncData collectGridSyncData(MTENuclearReactor reactor) {
        ReactorGridSyncData data = new ReactorGridSyncData();
        reactor.fillGridHeader(data);
        if (reactor.mGrid != null && reactor.gridSize > 0) {
            for (int x = 0; x < reactor.gridSize; x++) {
                for (int y = 0; y < reactor.gridSize; y++) {
                    ReactorGridSyncData.ReactorGridCellData cell = new ReactorGridSyncData.ReactorGridCellData();
                    reactor.fillGridCell(x, y, cell, true);
                    data.cells.add(cell);
                }
            }
        }
        return data;
    }

    static void recordMetricsCycleSnapshot(MTENuclearReactor reactor, NuclearSimulationEngine.SimulationResult res,
        double maintEff) {
        if (res == null) return;
        int cells = reactor.gridSize * reactor.gridSize;
        if (reactor.mSnapshotTemps.length < cells) {
            reactor.mSnapshotTemps = new double[cells];
            reactor.mSnapshotReactivities = new double[cells];
        }
        int temps = 0;
        int reactivities = 0;

        if (reactor.mGrid != null) {
            for (int x = 0; x < reactor.gridSize; x++) {
                for (int y = 0; y < reactor.gridSize; y++) {
                    INuclearTile tile = reactor.mGrid[x][y];
                    if (tile != null) {
                        double t = tile.getTemperature();
                        reactor.mSnapshotTemps[temps++] = t;
                        if (tile.isFuel()) {
                            reactor.mSnapshotReactivities[reactivities++] = NuclearSimulationEngine
                                .calculateEfficiency(tile, t) * 100.0;
                        }
                    }
                }
            }
        }

        ReactorCycleSnapshot snapshot = ReactorCycleSnapshot.create(
            reactor.mTotalCycles,
            reactor.mSnapshotTemps,
            temps,
            reactor.mSnapshotReactivities,
            reactivities,
            res.neutronsEscaped,
            res.totalNeutronsGenerated,
            res.thermalNeutronsAbsorbed,
            reactor.mDirectPowerEUt,
            reactor.mProducedHotCoolantLastCycle,
            reactor.getTicksPerCycle());
        reactor.mMetricsHistory.addSnapshot(snapshot);
    }
}

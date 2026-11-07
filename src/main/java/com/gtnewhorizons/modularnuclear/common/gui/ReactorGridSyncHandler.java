package com.gtnewhorizons.modularnuclear.common.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;
import com.gtnewhorizons.modularnuclear.common.nuclear.ReactorGridSyncData;
import com.gtnewhorizons.modularnuclear.common.nuclear.ReactorGridSyncData.ReactorGridCellData;

/**
 * Syncs the reactor grid to the player viewing it, in two parts. A cell's contents (items, fluid, rods) go when they
 * change, cell by cell. The readings (the reactor's, and each cell's temperature, heat, fluxes and insertion) go once
 * per reactor cycle, or when the reactor-wide ones change. Each viewer has its own handler, which keeps what it last
 * sent; the whole grid goes when the panel opens or the grid's size changes.
 */
public class ReactorGridSyncHandler extends SyncHandler<ReactorGridSyncHandler> {

    private static final int FULL = 0;
    private static final int UPDATE = 1;

    private final MTENuclearReactor reactor;
    /** What this viewer has: its header and cells, with copies of the stacks. */
    private final ReactorGridSyncData sent = new ReactorGridSyncData();
    private long sentCycle = -1;
    private final ReactorGridSyncData current = new ReactorGridSyncData();
    private final ReactorGridCellData scratch = new ReactorGridCellData();

    public ReactorGridSyncHandler(MTENuclearReactor reactor) {
        this.reactor = reactor;
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        reactor.fillGridHeader(current);
        int n = reactor.mGrid != null ? current.gridSize * current.gridSize : 0;
        if (init || n != sent.cells.size() || current.gridSize != sent.gridSize) {
            sent.cells.clear();
            for (int i = 0; i < n; i++) {
                ReactorGridCellData cell = new ReactorGridCellData();
                reactor.fillGridCell(i / current.gridSize, i % current.gridSize, cell, true);
                sent.cells.add(cell);
            }
            sent.copyHeaderFrom(current);
            sentCycle = reactor.mTotalCycles;
            syncToClient(FULL, buf -> {
                sent.writeHeader(buf);
                buf.writeVarIntToBuffer(sent.cells.size());
                for (ReactorGridCellData cell : sent.cells) cell.writeContents(buf);
                for (ReactorGridCellData cell : sent.cells) cell.writeReadings(buf);
            });
            return;
        }

        List<Integer> changed = new ArrayList<>();
        boolean newCycle = reactor.mTotalCycles != sentCycle;
        for (int i = 0; i < n; i++) {
            reactor.fillGridCell(i / current.gridSize, i % current.gridSize, scratch, false);
            ReactorGridCellData had = sent.cells.get(i);
            if (!scratch.sameContents(had)) {
                had.copyContentsFrom(scratch);
                changed.add(i);
            }
            if (newCycle) copyReadings(scratch, had);
        }
        boolean readings = newCycle || !current.sameHeader(sent);
        if (changed.isEmpty() && !readings) return;
        sent.copyHeaderFrom(current);
        sentCycle = reactor.mTotalCycles;
        syncToClient(UPDATE, buf -> {
            buf.writeVarIntToBuffer(changed.size());
            for (int i : changed) {
                buf.writeVarIntToBuffer(i);
                sent.cells.get(i)
                    .writeContents(buf);
            }
            buf.writeBoolean(readings);
            if (readings) {
                sent.writeHeader(buf);
                for (ReactorGridCellData cell : sent.cells) cell.writeReadings(buf);
            }
        });
    }

    private static void copyReadings(ReactorGridCellData from, ReactorGridCellData to) {
        to.temperature = from.temperature;
        to.heatOutput = from.heatOutput;
        to.fastFlux = from.fastFlux;
        to.thermalFlux = from.thermalFlux;
        to.fastAbsorbed = from.fastAbsorbed;
        to.thermalAbsorbed = from.thermalAbsorbed;
        to.directEU = from.directEU;
        to.controlRodInsertion = from.controlRodInsertion;
        to.moderatorInsertion = from.moderatorInsertion;
    }

    @Override
    public void readOnClient(int id, PacketBuffer buf) {
        if (id == FULL) {
            ReactorGridSyncData view = new ReactorGridSyncData();
            view.readHeader(buf);
            int n = buf.readVarIntFromBuffer();
            for (int i = 0; i < n; i++) {
                ReactorGridCellData cell = new ReactorGridCellData();
                cell.readContents(buf);
                view.cells.add(cell);
            }
            for (ReactorGridCellData cell : view.cells) cell.readReadings(buf);
            reactor.applyGridSyncData(view);
            return;
        }
        ReactorGridSyncData view = reactor.mClientGridData;
        if (view == null) return;
        int changed = buf.readVarIntFromBuffer();
        for (int k = 0; k < changed; k++) {
            view.cells.get(buf.readVarIntFromBuffer())
                .readContents(buf);
        }
        if (buf.readBoolean()) {
            view.readHeader(buf);
            for (ReactorGridCellData cell : view.cells) cell.readReadings(buf);
        }
        reactor.applyGridSyncData(view);
    }

    @Override
    public void readOnServer(int id, PacketBuffer buf) {}
}

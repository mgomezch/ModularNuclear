package com.gtnewhorizons.modularnuclear.common.gui.metrics;

import java.util.List;

import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.value.sync.SyncHandler;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;

/**
 * Syncs a reactor's metrics history to the player viewing it: the whole window when the panel opens (or when the
 * viewer fell too far behind, or the history was cleared), and after that only the snapshots added since the last
 * send. Each viewer has its own handler, so it tracks what that viewer has.
 */
public class MetricsHistorySyncHandler extends SyncHandler<MetricsHistorySyncHandler> {

    private static final int FULL = 0;
    private static final int APPEND = 1;

    private final MTENuclearReactor reactor;
    private int sentEpoch = -1;
    private long sentAdded;

    public MetricsHistorySyncHandler(MTENuclearReactor reactor) {
        this.reactor = reactor;
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        ReactorMetricsHistory history = reactor.getMetricsHistory();
        int epoch = history.getEpoch();
        long added = history.getAdded();
        if (!init && epoch == sentEpoch && added == sentAdded) return;
        List<ReactorCycleSnapshot> newer = init ? null : history.snapshotsSince(sentEpoch, sentAdded);
        if (newer == null) {
            List<ReactorCycleSnapshot> all = history.getSnapshots();
            syncToClient(FULL, buf -> write(buf, all));
        } else {
            syncToClient(APPEND, buf -> write(buf, newer));
        }
        sentEpoch = epoch;
        sentAdded = added;
    }

    private static void write(PacketBuffer buf, List<ReactorCycleSnapshot> snapshots) {
        buf.writeVarIntToBuffer(snapshots.size());
        for (ReactorCycleSnapshot s : snapshots) s.writeToBuffer(buf);
    }

    @Override
    public void readOnClient(int id, PacketBuffer buf) {
        ReactorMetricsHistory history = id == FULL || reactor.getClientMetricsHistory() == null
            ? new ReactorMetricsHistory()
            : reactor.getClientMetricsHistory();
        int n = buf.readVarIntFromBuffer();
        for (int i = 0; i < n; i++) history.addSnapshot(ReactorCycleSnapshot.readFromBuffer(buf));
        reactor.applyMetricsHistory(history);
    }

    @Override
    public void readOnServer(int id, PacketBuffer buf) {}
}

package com.gtnewhorizons.modularnuclear.common.gui.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.network.PacketBuffer;

/**
 * In-memory sliding window of the last 100 reactor cycle snapshots.
 * Used for network synchronization and live-updating telemetry charts.
 * Not persisted to NBT.
 */
public class ReactorMetricsHistory {

    public static final int MAX_HISTORY_SIZE = 100;

    private final List<ReactorCycleSnapshot> snapshots = new ArrayList<>();
    private long version = 0;

    public ReactorMetricsHistory() {}

    public synchronized void addSnapshot(ReactorCycleSnapshot snapshot) {
        if (snapshot == null) return;
        snapshots.add(snapshot);
        while (snapshots.size() > MAX_HISTORY_SIZE) {
            snapshots.remove(0);
        }
        version++;
    }

    public synchronized List<ReactorCycleSnapshot> getSnapshots() {
        return Collections.unmodifiableList(new ArrayList<>(snapshots));
    }

    public synchronized int size() {
        return snapshots.size();
    }

    public synchronized boolean isEmpty() {
        return snapshots.isEmpty();
    }

    public synchronized void clear() {
        snapshots.clear();
        version++;
    }

    public synchronized long getVersion() {
        return version;
    }

    public synchronized ReactorMetricsHistory copy() {
        ReactorMetricsHistory copy = new ReactorMetricsHistory();
        copy.snapshots.addAll(this.snapshots);
        copy.version = this.version;
        return copy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReactorMetricsHistory that = (ReactorMetricsHistory) o;
        return this.version == that.version && this.snapshots.size() == that.snapshots.size();
    }

    @Override
    public int hashCode() {
        return (int) (version ^ (version >>> 32));
    }

    public static void writeToBuffer(PacketBuffer buf, ReactorMetricsHistory history) {
        if (history == null) {
            buf.writeVarIntToBuffer(0);
            return;
        }
        List<ReactorCycleSnapshot> list = history.getSnapshots();
        buf.writeVarIntToBuffer(list.size());
        for (ReactorCycleSnapshot s : list) {
            s.writeToBuffer(buf);
        }
    }

    public static ReactorMetricsHistory readFromBuffer(PacketBuffer buf) {
        ReactorMetricsHistory history = new ReactorMetricsHistory();
        int count = buf.readVarIntFromBuffer();
        for (int i = 0; i < count; i++) {
            history.snapshots.add(ReactorCycleSnapshot.readFromBuffer(buf));
        }
        return history;
    }
}

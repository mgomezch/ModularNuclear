package com.gtnewhorizons.modularnuclear.common.gui.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.network.PacketBuffer;

/**
 * In-memory sliding window of the last 100 reactor cycle snapshots, in a ring buffer. Used for network synchronization
 * and live-updating telemetry charts. Not persisted to NBT.
 *
 * <p>
 * It counts the snapshots ever added and the times it was cleared, so a viewer that has seen some of them can be sent
 * only the newer ones ({@link #snapshotsSince}).
 */
public class ReactorMetricsHistory {

    public static final int MAX_HISTORY_SIZE = 100;

    private final ReactorCycleSnapshot[] ring = new ReactorCycleSnapshot[MAX_HISTORY_SIZE];
    /** Index of the oldest snapshot. */
    private int head;
    private int size;
    /** Snapshots added since the last clear. */
    private long added;
    /** Times cleared. */
    private int epoch;

    public ReactorMetricsHistory() {}

    public synchronized void addSnapshot(ReactorCycleSnapshot snapshot) {
        if (snapshot == null) return;
        if (size < MAX_HISTORY_SIZE) {
            ring[(head + size++) % MAX_HISTORY_SIZE] = snapshot;
        } else {
            ring[head] = snapshot;
            head = (head + 1) % MAX_HISTORY_SIZE;
        }
        added++;
    }

    /** The snapshots, oldest first. */
    public synchronized List<ReactorCycleSnapshot> getSnapshots() {
        List<ReactorCycleSnapshot> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) out.add(ring[(head + i) % MAX_HISTORY_SIZE]);
        return Collections.unmodifiableList(out);
    }

    /**
     * The snapshots added after the first {@code seen} of this epoch, oldest first; null if some of them have already
     * been dropped from the window (or the epoch changed), so the caller must resend the whole window.
     */
    public synchronized List<ReactorCycleSnapshot> snapshotsSince(int seenEpoch, long seen) {
        if (seenEpoch != epoch || seen > added || added - seen > size) return null;
        int n = (int) (added - seen);
        List<ReactorCycleSnapshot> out = new ArrayList<>(n);
        for (int i = size - n; i < size; i++) out.add(ring[(head + i) % MAX_HISTORY_SIZE]);
        return out;
    }

    public synchronized int size() {
        return size;
    }

    public synchronized boolean isEmpty() {
        return size == 0;
    }

    public synchronized void clear() {
        java.util.Arrays.fill(ring, null);
        head = 0;
        size = 0;
        added = 0;
        epoch++;
    }

    public synchronized long getAdded() {
        return added;
    }

    public synchronized int getEpoch() {
        return epoch;
    }

    /** Changes whenever a snapshot is added or the history is cleared. */
    public synchronized long getVersion() {
        return ((long) epoch << 40) ^ added;
    }

    public synchronized ReactorMetricsHistory copy() {
        ReactorMetricsHistory copy = new ReactorMetricsHistory();
        System.arraycopy(ring, 0, copy.ring, 0, MAX_HISTORY_SIZE);
        copy.head = head;
        copy.size = size;
        copy.added = added;
        copy.epoch = epoch;
        return copy;
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
            history.addSnapshot(ReactorCycleSnapshot.readFromBuffer(buf));
        }
        return history;
    }
}

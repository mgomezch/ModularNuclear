package com.gtnewhorizons.modularnuclear.common.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;

import com.gtnewhorizons.modularnuclear.common.nuclear.CustomFuelMix;
import com.gtnewhorizons.modularnuclear.common.nuclear.CustomFuelMix.ActinideIsotope;

/**
 * Custom fuel mixes by composition, shared by every reactor, hatch and tooltip. A custom rod's physics follow from
 * the isotope units in its NBT, and building a {@link CustomFuelMix} from them solves its damage curve, so each
 * distinct composition is built once and the same immutable instance is handed out for every rod that carries it.
 * Only the composition is the key: the rod's wear ({@code advDmg}) and the derived values the NBT also stores are
 * not looked at.
 * <p>
 * A world holds few compositions. One nothing has looked up for an hour is dropped, so compositions crafted over a
 * long game do not pile up; a reactor cell keeps the instance it holds until its contents change.
 * <p>
 * Reads take no lock and allocate nothing (server and client threads alike); adding and dropping compositions take
 * the class lock.
 */
public final class CustomFuelMixCache {

    private static final ActinideIsotope[] ISOTOPES = ActinideIsotope.values();
    private static final long EXPIRE_MS = 60L * 60L * 1000L;
    private static final long SWEEP_EVERY_MS = 5L * 60L * 1000L;
    private static final int TAG_COMPOUND = 10;

    private static final class Entry {

        final long hash;
        /** Units per isotope, in {@link ActinideIsotope} order. */
        final int[] units;
        final CustomFuelMix mix;
        /** When it was last looked up (racy, only compared against the hour). */
        long lastUsed;

        Entry(long hash, int[] units, CustomFuelMix mix, long now) {
            this.hash = hash;
            this.units = units;
            this.mix = mix;
            this.lastUsed = now;
        }
    }

    /** Entries by composition hash (rarely more than one per hash). Replaced whole when entries are dropped. */
    private static volatile LongKeyMap<Entry[]> byHash = new LongKeyMap<>();
    /** Every entry, for dropping the stale ones. Guarded by the class lock. */
    private static final List<Entry> entries = new ArrayList<>();
    private static volatile long lastSweep = System.currentTimeMillis();

    private CustomFuelMixCache() {}

    /**
     * The mix an item's NBT carries ({@link CustomFuelMix#NBT_TAG_ROOT}), or null when it carries none or no
     * actinide units.
     */
    public static CustomFuelMix of(NBTTagCompound nbt) {
        return of(nbt, System.currentTimeMillis());
    }

    /** {@link #of(NBTTagCompound)} at a given time (milliseconds, as {@link System#currentTimeMillis}). */
    public static CustomFuelMix of(NBTTagCompound nbt, long now) {
        if (nbt == null || !nbt.hasKey(CustomFuelMix.NBT_TAG_ROOT, TAG_COMPOUND)) return null;
        NBTTagCompound root = nbt.getCompoundTag(CustomFuelMix.NBT_TAG_ROOT);
        if (!root.hasKey("Units", TAG_COMPOUND)) return null;
        NBTTagCompound units = root.getCompoundTag("Units");

        long hash = 0xCBF29CE484222325L;
        boolean any = false;
        for (ActinideIsotope iso : ISOTOPES) {
            int u = unitsOf(units, iso);
            if (u > 0) any = true;
            hash = (hash ^ u) * 0x100000001B3L;
        }
        if (!any) return null;
        if (hash == 0L) hash = 1L;

        if (now - lastSweep > SWEEP_EVERY_MS) sweep(now);
        Entry[] chain = byHash.get(hash);
        if (chain != null) {
            for (Entry e : chain) {
                if (matches(e, units)) {
                    e.lastUsed = now;
                    return e.mix;
                }
            }
        }
        return add(hash, units, now);
    }

    /** How many compositions are cached. */
    public static synchronized int size() {
        return entries.size();
    }

    public static synchronized void clear() {
        entries.clear();
        byHash = new LongKeyMap<>();
        lastSweep = System.currentTimeMillis();
    }

    /** An isotope's units; missing or negative counts as none, as {@link CustomFuelMix} reads them. */
    private static int unitsOf(NBTTagCompound units, ActinideIsotope iso) {
        return Math.max(0, units.getInteger(iso.name()));
    }

    private static boolean matches(Entry e, NBTTagCompound units) {
        for (int i = 0; i < ISOTOPES.length; i++) {
            if (e.units[i] != unitsOf(units, ISOTOPES[i])) return false;
        }
        return true;
    }

    private static synchronized CustomFuelMix add(long hash, NBTTagCompound unitsTag, long now) {
        Entry[] chain = byHash.get(hash);
        if (chain != null) {
            for (Entry e : chain) {
                if (matches(e, unitsTag)) return e.mix;
            }
        }
        int[] units = new int[ISOTOPES.length];
        Map<ActinideIsotope, Integer> byIsotope = new LinkedHashMap<>();
        for (int i = 0; i < ISOTOPES.length; i++) {
            units[i] = unitsOf(unitsTag, ISOTOPES[i]);
            if (units[i] > 0) byIsotope.put(ISOTOPES[i], units[i]);
        }
        Entry e = new Entry(hash, units, new CustomFuelMix(byIsotope), now);
        entries.add(e);
        byHash.put(hash, append(chain, e));
        return e.mix;
    }

    private static Entry[] append(Entry[] chain, Entry e) {
        if (chain == null) return new Entry[] { e };
        Entry[] next = new Entry[chain.length + 1];
        System.arraycopy(chain, 0, next, 0, chain.length);
        next[chain.length] = e;
        return next;
    }

    /** Drops the compositions nothing has looked up for an hour. */
    private static synchronized void sweep(long now) {
        if (now - lastSweep <= SWEEP_EVERY_MS) return;
        lastSweep = now;
        boolean dropped = false;
        for (Iterator<Entry> it = entries.iterator(); it.hasNext();) {
            if (now - it.next().lastUsed > EXPIRE_MS) {
                it.remove();
                dropped = true;
            }
        }
        if (!dropped) return;
        LongKeyMap<Entry[]> next = new LongKeyMap<>();
        for (Entry e : entries) next.put(e.hash, append(next.get(e.hash), e));
        byHash = next;
    }
}

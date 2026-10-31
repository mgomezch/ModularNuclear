package com.gtnewhorizons.modularnuclear.common.coolantloop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;

import com.gtnewhorizons.modularnuclear.ModularNuclear;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Keeps the chunks of formed coolant loops loaded: a pump holds tickets for every chunk its pipes and the whole
 * structures of its devices occupy. Each ticket lists its chunks in its saved data, so after a restart the loop comes
 * back loaded together with its pump, which then claims the tickets. Tickets no pump claims are released.
 */
public final class LoopChunkLoader {

    private static final String PUMP_KEY = "loopPump";
    private static final String CHUNKS_KEY = "loopChunks";
    /** How long (ticks) restored tickets wait for their pump to load and claim them. */
    private static final long UNCLAIMED_RELEASE_TICKS = 1200;

    private static final Map<String, Restored> RESTORED = new ConcurrentHashMap<>();
    private static boolean warnedNoTicket;

    private static final class Restored {

        final World world;
        final List<Ticket> tickets = new ArrayList<>();
        final long restoredAt;

        Restored(World world) {
            this.world = world;
            this.restoredAt = world.getTotalWorldTime();
        }
    }

    private LoopChunkLoader() {}

    public static boolean isLoopTicket(Ticket ticket) {
        return ticket != null && ticket.getModData()
            .hasKey(PUMP_KEY);
    }

    /** Re-forces the chunks a saved ticket lists, until its pump claims it. */
    public static void restore(Ticket ticket, World world) {
        NBTTagCompound data = ticket.getModData();
        int[] pump = data.getIntArray(PUMP_KEY);
        int[] chunks = data.getIntArray(CHUNKS_KEY);
        if (pump.length < 3) {
            ForgeChunkManager.releaseTicket(ticket);
            return;
        }
        for (int i = 0; i + 1 < chunks.length; i += 2) {
            ForgeChunkManager.forceChunk(ticket, new ChunkCoordIntPair(chunks[i], chunks[i + 1]));
        }
        RESTORED.computeIfAbsent(key(world, pump[0], pump[1], pump[2]), k -> new Restored(world)).tickets.add(ticket);
    }

    /**
     * Makes the pump at the given position hold exactly the given chunks, reusing the tickets it holds (or that were
     * restored for it) when they already cover them. Returns the tickets now held.
     */
    public static List<Ticket> update(List<Ticket> held, World world, int x, int y, int z, Set<Long> chunks) {
        List<Ticket> current = new ArrayList<>(held);
        Restored restored = RESTORED.remove(key(world, x, y, z));
        if (restored != null) current.addAll(restored.tickets);
        if (heldChunks(current).equals(chunks)) return current;
        release(current);
        List<Ticket> tickets = new ArrayList<>();
        Ticket ticket = null;
        List<Integer> listed = new ArrayList<>();
        for (long chunk : chunks) {
            if (ticket == null || ticket.getChunkList()
                .size() >= ticket.getChunkListDepth()) {
                if (ticket != null) list(ticket, listed);
                ticket = ForgeChunkManager.requestTicket(ModularNuclear.instance, world, ForgeChunkManager.Type.NORMAL);
                if (ticket == null) {
                    if (!warnedNoTicket) {
                        warnedNoTicket = true;
                        ModularNuclear.LOG.warn(
                            "No chunk loading ticket left for the coolant loop at {} {} {}; part of it may unload",
                            x,
                            y,
                            z);
                    }
                    return tickets;
                }
                ticket.getModData()
                    .setIntArray(PUMP_KEY, new int[] { x, y, z });
                listed.clear();
                tickets.add(ticket);
            }
            int cx = (int) chunk, cz = (int) (chunk >>> 32);
            ForgeChunkManager.forceChunk(ticket, new ChunkCoordIntPair(cx, cz));
            listed.add(cx);
            listed.add(cz);
        }
        if (ticket != null) list(ticket, listed);
        return tickets;
    }

    /** Releases the restored tickets of the pump at the given position, if it no longer holds a loop. */
    public static void releaseRestored(World world, int x, int y, int z) {
        Restored restored = RESTORED.remove(key(world, x, y, z));
        if (restored != null) release(restored.tickets);
    }

    public static void release(List<Ticket> tickets) {
        for (Ticket t : tickets) ForgeChunkManager.releaseTicket(t);
    }

    private static Set<Long> heldChunks(List<Ticket> tickets) {
        if (tickets.isEmpty()) return Collections.emptySet();
        Set<Long> out = new HashSet<>();
        for (Ticket t : tickets) {
            for (ChunkCoordIntPair c : t.getChunkList()) {
                out.add(ChunkCoordIntPair.chunkXZ2Int(c.chunkXPos, c.chunkZPos));
            }
        }
        return out;
    }

    private static void list(Ticket ticket, List<Integer> chunks) {
        int[] arr = new int[chunks.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = chunks.get(i);
        ticket.getModData()
            .setIntArray(CHUNKS_KEY, arr);
    }

    private static String key(World world, int x, int y, int z) {
        return world.provider.dimensionId + ":" + x + ":" + y + ":" + z;
    }

    /** Releases restored tickets whose pump never claimed them (it was removed, or no longer forms a loop). */
    public static final class Sweeper {

        @SubscribeEvent
        public void onWorldTick(TickEvent.WorldTickEvent event) {
            if (event.phase != TickEvent.Phase.END || event.world.isRemote || RESTORED.isEmpty()) return;
            long now = event.world.getTotalWorldTime();
            for (Iterator<Restored> it = RESTORED.values()
                .iterator(); it.hasNext();) {
                Restored r = it.next();
                if (r.world == event.world && now - r.restoredAt > UNCLAIMED_RELEASE_TICKS) {
                    it.remove();
                    release(r.tickets);
                }
            }
        }
    }
}

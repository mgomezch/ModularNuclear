package com.gtnewhorizons.modularnuclear.common.gui.graph;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.network.PacketBuffer;

import org.junit.jupiter.api.Test;

import io.netty.buffer.Unpooled;

public class LargeStringSyncValueTest {

    private static String roundTrip(String s) throws Exception {
        LargeStringSyncValue sync = new LargeStringSyncValue(() -> s, v -> {});
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        sync.serialize(buffer, s);
        String back = sync.deserialize(buffer);
        assertEquals(0, buffer.readableBytes(), "nothing left in the packet");
        return back;
    }

    @Test
    void testStringPastAPacketStringsLimitArrivesWhole() throws Exception {
        // A graph's JSON for a loop of hundreds of pipe edges, well past ModularUI's 32 KB string limit
        StringBuilder sb = new StringBuilder("{\"edges\":[");
        for (int i = 0; i < 2000; i++) {
            sb.append("{\"id\":\"edge_")
                .append(i)
                .append("\",\"pipeMaterial\":\"Awakened Draconium\",\"temperatureCelsius\":")
                .append(368.0 + i % 50)
                .append(",\"label\":\"Ω µ °C\"},");
        }
        sb.append("{}]}");
        String json = sb.toString();
        assertTrue(json.length() > 150_000);
        assertEquals(json, roundTrip(json));
    }

    @Test
    void testEmptyAndNullStrings() throws Exception {
        assertEquals("", roundTrip(""));
        assertNull(roundTrip(null));
    }

    @Test
    void testSourceIsReadOnceEachInterval() {
        // A loop whose graph has not changed (the cache's null): the server only builds it every 20 ticks
        int[] reads = { 0 };
        LargeStringSyncValue sync = new LargeStringSyncValue(() -> {
            reads[0]++;
            return null;
        }, v -> {}, 20);
        reads[0] = 0; // the constructor's own read, filling its cache
        for (int tick = 0; tick < 60; tick++) sync.detectAndSendChanges(false);
        assertEquals(3, reads[0]);

        int[] everyTick = { 0 };
        LargeStringSyncValue fast = new LargeStringSyncValue(() -> {
            everyTick[0]++;
            return null;
        }, v -> {});
        everyTick[0] = 0;
        for (int tick = 0; tick < 60; tick++) fast.detectAndSendChanges(false);
        assertEquals(60, everyTick[0]);
    }
}

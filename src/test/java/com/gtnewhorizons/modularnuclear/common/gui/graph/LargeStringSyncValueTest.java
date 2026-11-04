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
}

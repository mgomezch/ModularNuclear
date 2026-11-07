package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.config.ReactorConfigData;
import com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry;
import com.gtnewhorizons.modularnuclear.network.PacketSyncConfig;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** A dedicated server's configuration reaches a client whole, and loads there like the server's own files. */
public class SyncedConfigTest {

    @Test
    void thePacketCarriesFilesAndSettingsIntact() {
        Map<String, String> reactor = new LinkedHashMap<>();
        StringBuilder big = new StringBuilder("physics:\n");
        for (int i = 0; i < 5000; i++) big.append("  # padding line ")
            .append(i)
            .append(" past writeUTF's 64 KB limit\n");
        reactor.put("physics.yaml", big.toString());
        Map<String, String> loops = new LinkedHashMap<>();
        loops.put("fluids.yaml", "fluids: {}\n");
        Map<String, Object> gameplay = new LinkedHashMap<>();
        gameplay.put("allowedFuelFluids", new String[] { "uranium based liquid fuel", "thorium based liquid fuel" });
        gameplay.put("ticksPerCycle", 7);
        gameplay.put("falloutContaminationReductionPerPollution", 0.25);

        ByteBuf buf = Unpooled.buffer();
        new PacketSyncConfig(reactor, loops, gameplay).toBytes(buf);
        PacketSyncConfig read = new PacketSyncConfig();
        read.fromBytes(buf);

        assertEquals(reactor, read.reactorFiles);
        assertEquals(loops, read.coolantLoopFiles);
        assertArrayEquals(
            (String[]) gameplay.get("allowedFuelFluids"),
            (String[]) read.gameplay.get("allowedFuelFluids"));
        assertEquals(7, read.gameplay.get("ticksPerCycle"));
        assertEquals(0.25, read.gameplay.get("falloutContaminationReductionPerPollution"));
    }

    @Test
    void aServersOverridesApplyAsItsFilesWould() {
        Map<String, String> texts = new LinkedHashMap<>();
        texts.put("physics.yaml", "physics:\n  fission_heat_per_neutron: 123.0\n");
        ReactorDataRegistry.loadFromTexts(texts);
        try {
            assertEquals(123.0, ReactorConfigData.getPhysics().fissionHeatPerNeutron, 1e-9);
        } finally {
            ReactorDataRegistry.loadFromTexts(new LinkedHashMap<>());
        }
    }
}

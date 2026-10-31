package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import net.minecraft.network.PacketBuffer;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.nuclear.ReactorGridSyncData;
import com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification.NotFuelReason;

import io.netty.buffer.Unpooled;

/** The reactor GUI's explanation of a core that does not burn: per-cell refusals and their texts. */
public class ReactorIdleReasonTest {

    @Test
    public void testCellRefusalSurvivesSync() {
        ReactorGridSyncData data = new ReactorGridSyncData();
        data.gridSize = 1;
        data.coreDimension = 1;
        ReactorGridSyncData.ReactorGridCellData cell = new ReactorGridSyncData.ReactorGridCellData();
        cell.exists = true;
        cell.notFuelReason = (byte) (NotFuelReason.DEPLETED.ordinal() + 1);
        data.cells.add(cell);

        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        ReactorGridSyncData.writeToBuffer(buf, data);
        ReactorGridSyncData read = ReactorGridSyncData.readFromBuffer(buf);

        assertEquals(1, read.cells.size());
        assertEquals(cell.notFuelReason, read.cells.get(0).notFuelReason);
        assertEquals(cell, read.cells.get(0));
    }

    @Test
    public void testEveryExplanationHasText() throws IOException {
        String lang;
        try (InputStream in = getClass().getResourceAsStream("/assets/modularnuclear/lang/en_US.lang");
            Scanner s = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A")) {
            lang = s.next();
        }
        for (NotFuelReason reason : NotFuelReason.values()) {
            assertTrue(lang.contains("\n" + reason.langKey() + "="), reason.langKey());
        }
        for (String key : new String[] { "modularnuclear.gui.idle.no_fuel", "modularnuclear.gui.idle.refused",
            "modularnuclear.gui.cell.not_burning" }) {
            assertTrue(lang.contains("\n" + key + "="), key);
        }
    }
}

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
        ReactorGridSyncData.ReactorGridCellData cell = new ReactorGridSyncData.ReactorGridCellData();
        cell.exists = true;
        cell.notFuelReason = (byte) (NotFuelReason.DEPLETED.ordinal() + 1);
        cell.temperature = 812.5f;
        cell.fastFlux = 42;

        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        cell.writeContents(buf);
        cell.writeReadings(buf);
        ReactorGridSyncData.ReactorGridCellData read = new ReactorGridSyncData.ReactorGridCellData();
        read.readContents(buf);
        read.readReadings(buf);

        assertEquals(cell.notFuelReason, read.notFuelReason);
        assertTrue(cell.sameContents(read));
        assertEquals(812.5f, read.temperature);
        assertEquals(42, read.fastFlux);
    }

    @Test
    public void testHeaderSurvivesSync() {
        ReactorGridSyncData data = new ReactorGridSyncData();
        data.gridSize = 9;
        data.coreDimension = 7;
        data.coreTemp = 1234.5f;
        data.efficiency = 0.875;
        data.outputCoolantName = "ic2hotcoolant";
        data.scram = true;

        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        data.writeHeader(buf);
        ReactorGridSyncData read = new ReactorGridSyncData();
        read.readHeader(buf);
        assertTrue(data.sameHeader(read), "the header comes back exactly");
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

package com.gtnewhorizons.modularnuclear.common.gui.graph;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.value.sync.StringSyncValue;

/**
 * A string synced whole, however long: ModularUI's StringSyncValue cuts a string at 32 KB (a packet string's limit),
 * which a large loop's graph JSON passes. The string goes deflated, as a length and its bytes.
 */
public class LargeStringSyncValue extends StringSyncValue {

    /** Longest string accepted back, against a corrupt length. */
    private static final int MAX_BYTES = 16 * 1024 * 1024;

    public LargeStringSyncValue(Supplier<String> getter, Consumer<String> setter) {
        super(getter, setter);
    }

    @Override
    protected void serialize(PacketBuffer buffer, String value) throws IOException {
        if (value == null) {
            buffer.writeVarIntToBuffer(-1);
            return;
        }
        byte[] raw = value.getBytes(StandardCharsets.UTF_8);
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, raw.length / 4));
        byte[] chunk = new byte[8192];
        while (!deflater.finished()) out.write(chunk, 0, deflater.deflate(chunk));
        deflater.end();
        buffer.writeVarIntToBuffer(raw.length);
        buffer.writeVarIntToBuffer(out.size());
        buffer.writeBytes(out.toByteArray());
    }

    @Override
    protected String deserialize(PacketBuffer buffer) throws IOException {
        int rawLength = buffer.readVarIntFromBuffer();
        if (rawLength < 0) return null;
        int packedLength = buffer.readVarIntFromBuffer();
        if (rawLength > MAX_BYTES || packedLength < 0 || packedLength > buffer.readableBytes()) {
            throw new IOException("Bad synced string length " + rawLength + "/" + packedLength);
        }
        byte[] packed = new byte[packedLength];
        buffer.readBytes(packed);
        Inflater inflater = new Inflater();
        inflater.setInput(packed);
        byte[] raw = new byte[rawLength];
        try {
            int n = 0;
            while (n < rawLength && !inflater.finished()) n += inflater.inflate(raw, n, rawLength - n);
            if (n != rawLength) throw new IOException("Synced string inflated to " + n + " of " + rawLength + " bytes");
        } catch (DataFormatException e) {
            throw new IOException(e);
        } finally {
            inflater.end();
        }
        return new String(raw, StandardCharsets.UTF_8);
    }
}

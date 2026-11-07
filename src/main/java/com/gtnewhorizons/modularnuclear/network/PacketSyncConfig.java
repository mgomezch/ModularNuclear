package com.gtnewhorizons.modularnuclear.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import net.minecraft.client.Minecraft;

import com.gtnewhorizons.modularnuclear.common.config.SyncedConfig;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

/**
 * A dedicated server's configuration, sent to each player as they join: its reactor and coolant loop config files (as
 * YAML text) and its Forge config's gameplay settings, gzipped.
 */
public class PacketSyncConfig implements IMessage {

    public Map<String, String> reactorFiles = new LinkedHashMap<>();
    public Map<String, String> coolantLoopFiles = new LinkedHashMap<>();
    public Map<String, Object> gameplay = new LinkedHashMap<>();

    public PacketSyncConfig() {}

    public PacketSyncConfig(Map<String, String> reactorFiles, Map<String, String> coolantLoopFiles,
        Map<String, Object> gameplay) {
        this.reactorFiles = reactorFiles;
        this.coolantLoopFiles = coolantLoopFiles;
        this.gameplay = gameplay;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            writeTexts(out, reactorFiles);
            writeTexts(out, coolantLoopFiles);
            out.writeInt(gameplay.size());
            for (Map.Entry<String, Object> e : gameplay.entrySet()) {
                out.writeUTF(e.getKey());
                Object v = e.getValue();
                if (v instanceof String[]list) {
                    out.writeByte(0);
                    out.writeInt(list.length);
                    for (String item : list) out.writeUTF(item);
                } else if (v instanceof Integer i) {
                    out.writeByte(1);
                    out.writeInt(i);
                } else {
                    out.writeByte(2);
                    out.writeDouble((Double) v);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        byte[] data = bytes.toByteArray();
        buf.writeInt(data.length);
        buf.writeBytes(data);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        byte[] data = new byte[buf.readInt()];
        buf.readBytes(data);
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(data)))) {
            reactorFiles = readTexts(in);
            coolantLoopFiles = readTexts(in);
            gameplay = new LinkedHashMap<>();
            int n = in.readInt();
            for (int i = 0; i < n; i++) {
                String key = in.readUTF();
                switch (in.readByte()) {
                    case 0 -> {
                        String[] list = new String[in.readInt()];
                        for (int j = 0; j < list.length; j++) list[j] = in.readUTF();
                        gameplay.put(key, list);
                    }
                    case 1 -> gameplay.put(key, in.readInt());
                    default -> gameplay.put(key, in.readDouble());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Texts are written in chunks: writeUTF takes at most 64 KB. */
    private static void writeTexts(DataOutputStream out, Map<String, String> texts) throws IOException {
        out.writeInt(texts.size());
        for (Map.Entry<String, String> e : texts.entrySet()) {
            out.writeUTF(e.getKey());
            String text = e.getValue();
            int chunks = (text.length() + 16383) / 16384;
            out.writeInt(chunks);
            for (int c = 0; c < chunks; c++) {
                out.writeUTF(text.substring(c * 16384, Math.min(text.length(), (c + 1) * 16384)));
            }
        }
    }

    private static Map<String, String> readTexts(DataInputStream in) throws IOException {
        Map<String, String> texts = new LinkedHashMap<>();
        int n = in.readInt();
        for (int i = 0; i < n; i++) {
            String name = in.readUTF();
            StringBuilder sb = new StringBuilder();
            int chunks = in.readInt();
            for (int c = 0; c < chunks; c++) sb.append(in.readUTF());
            texts.put(name, sb.toString());
        }
        return texts;
    }

    public static class Handler implements IMessageHandler<PacketSyncConfig, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketSyncConfig message, MessageContext ctx) {
            // Packets arrive on the network thread; the configuration is the game thread's
            Minecraft.getMinecraft()
                .func_152344_a(() -> SyncedConfig.useServer(message));
            return null;
        }
    }
}

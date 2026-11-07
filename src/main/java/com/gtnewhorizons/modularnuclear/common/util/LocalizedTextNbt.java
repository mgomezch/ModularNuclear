package com.gtnewhorizons.modularnuclear.common.util;

import java.io.IOException;
import java.io.UncheckedIOException;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.common.util.Constants;

/** Saves and sends a {@link LocalizedText} as its key and arguments, never as text. */
public final class LocalizedTextNbt {

    private LocalizedTextNbt() {}

    public static NBTTagCompound write(LocalizedText text) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("k", text.key);
        NBTTagList args = new NBTTagList();
        for (int i = 0; i < text.argCount(); i++) {
            NBTTagCompound a = new NBTTagCompound();
            a.setTag("v", toNbt(text.arg(i)));
            args.appendTag(a);
        }
        tag.setTag("a", args);
        return tag;
    }

    public static LocalizedText read(NBTTagCompound tag) {
        NBTTagList list = tag.getTagList("a", Constants.NBT.TAG_COMPOUND);
        Object[] args = new Object[list.tagCount()];
        for (int i = 0; i < args.length; i++) {
            args[i] = fromNbt(
                list.getCompoundTagAt(i)
                    .getTag("v"));
        }
        String key = tag.getString("k");
        return LocalizedText.LITERAL_KEY.equals(key) ? LocalizedText.literal(args.length > 0 ? (String) args[0] : "")
            : LocalizedText.of(key, args);
    }

    public static void write(PacketBuffer buf, LocalizedText text) {
        try {
            buf.writeNBTTagCompoundToBuffer(write(text));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static LocalizedText read(PacketBuffer buf) {
        try {
            return read(buf.readNBTTagCompoundFromBuffer());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static NBTBase toNbt(Object v) {
        if (v instanceof LocalizedText t) return write(t);
        if (v instanceof Long l) return new NBTTagLong(l);
        if (v instanceof Double d) return new NBTTagDouble(d);
        if (v instanceof Boolean b) return new NBTTagByte((byte) (b ? 1 : 0));
        return new NBTTagString(String.valueOf(v));
    }

    private static Object fromNbt(NBTBase v) {
        if (v instanceof NBTTagCompound c) return read(c);
        if (v instanceof NBTTagLong l) return l.func_150291_c();
        if (v instanceof NBTTagDouble d) return d.func_150286_g();
        if (v instanceof NBTTagByte b) return b.func_150290_f() != 0;
        if (v instanceof NBTTagString s) return s.func_150285_a_();
        return "";
    }
}

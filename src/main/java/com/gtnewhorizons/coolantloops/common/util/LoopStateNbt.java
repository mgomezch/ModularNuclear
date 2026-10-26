package com.gtnewhorizons.coolantloops.common.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ChunkCoordinates;

import com.gtnewhorizons.coolantloops.engine.LoopSegment;

/**
 * Converts the coolant loop engine's plain state maps (CoolantLoopEngine.exportState, LoopSegment.toStateMap) to and
 * from NBT, and adapts segment positions to Minecraft coordinates. This keeps the engine itself free of Minecraft
 * classes so the standalone simulator can run it.
 */
public final class LoopStateNbt {

    private LoopStateNbt() {}

    /** Writes a state map into an NBT compound (Boolean as byte, Integer as int, and so on). */
    public static void write(Map<String, Object> state, NBTTagCompound tag) {
        for (Map.Entry<String, Object> e : state.entrySet()) {
            NBTBase value = toNbt(e.getValue());
            if (value != null) {
                tag.setTag(e.getKey(), value);
            }
        }
    }

    /** Reads an NBT compound back into a state map. */
    public static Map<String, Object> read(NBTTagCompound tag) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (tag == null) return m;
        for (Object keyObj : tag.func_150296_c()) {
            String key = (String) keyObj;
            Object v = fromNbt(tag.getTag(key));
            if (v != null) m.put(key, v);
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    private static NBTBase toNbt(Object v) {
        if (v instanceof Boolean b) return new NBTTagByte((byte) (b ? 1 : 0));
        if (v instanceof Integer i) return new NBTTagInt(i);
        if (v instanceof Long l) return new NBTTagLong(l);
        if (v instanceof Double d) return new NBTTagDouble(d);
        if (v instanceof Float f) return new NBTTagFloat(f);
        if (v instanceof String s) return new NBTTagString(s);
        if (v instanceof Map<?, ?>map) {
            NBTTagCompound c = new NBTTagCompound();
            write((Map<String, Object>) map, c);
            return c;
        }
        if (v instanceof List<?>list) {
            NBTTagList l = new NBTTagList();
            for (Object o : list) {
                NBTBase n = toNbt(o);
                if (n != null) l.appendTag(n);
            }
            return l;
        }
        return null;
    }

    private static Object fromNbt(NBTBase n) {
        if (n instanceof NBTTagByte b) return b.func_150290_f() != 0;
        if (n instanceof NBTTagShort s) return (int) s.func_150289_e();
        if (n instanceof NBTTagInt i) return i.func_150287_d();
        if (n instanceof NBTTagLong l) return l.func_150291_c();
        if (n instanceof NBTTagDouble d) return d.func_150286_g();
        if (n instanceof NBTTagFloat f) return (double) f.func_150288_h();
        if (n instanceof NBTTagString s) return s.func_150285_a_();
        if (n instanceof NBTTagCompound c) return read(c);
        if (n instanceof NBTTagList l) {
            List<Object> out = new ArrayList<>();
            NBTTagList copy = (NBTTagList) l.copy();
            while (copy.tagCount() > 0) {
                Object o = fromNbt(copy.removeTag(0));
                if (o != null) out.add(o);
            }
            return out;
        }
        return null;
    }

    /** A segment's block position, or null if it has none. */
    public static ChunkCoordinates coordinates(LoopSegment seg) {
        return seg.hasPosition() ? new ChunkCoordinates(seg.getBlockX(), seg.getBlockY(), seg.getBlockZ()) : null;
    }

    /** Sets (or, with null, clears) a segment's block position. */
    public static void setPosition(LoopSegment seg, ChunkCoordinates coords) {
        if (coords != null) {
            seg.setPosition(coords.posX, coords.posY, coords.posZ);
        } else {
            seg.setPosition(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
        }
    }
}

package com.gtnewhorizons.modularnuclear.common.metatileentity.multi.gas;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import net.minecraft.block.Block;

import org.apache.commons.lang3.tuple.Pair;

import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.StructureUtility;

import gregtech.api.GregTechAPI;
import gregtech.common.blocks.BlockCasingsAbstract;

/**
 * The GregTech blocks the gas loop machines are built of, and the numbers that size them. See Note [Gas loop
 * machines]
 */
public final class GasMachineParts {

    private GasMachineParts() {}

    /** Joules of shaft work per EU, for compressors and expanders: the EU a turbine makes of 28,250 J of steam heat. */
    public static final double SHAFT_JOULES_PER_EU = 28_250.0;

    // Gearbox casings (sBlockCasings2): steel, titanium, tungstensteel
    public static final int[] GEARBOX_METAS = { 3, 4, 5 };
    // Pipe casings (sBlockCasings2): bronze, steel, titanium, tungstensteel
    public static final int[] PIPE_CASING_METAS = { 12, 13, 14, 15 };
    // Air filter turbine casings (sBlockCasingsNH), the radiator's fans: T1, T2, T3
    public static final int[] FAN_METAS = { 0, 3, 5 };
    public static final int VENT_META = 1;

    /** Compressor displacement per ring, m^3/s of suction gas, by gearbox tier. */
    public static double compressorDisplacementPerRing(int tier) {
        return 20.0 * Math.pow(4.0, tier - 1);
    }

    public static final double COMPRESSOR_ISENTROPIC_EFFICIENCY = 0.75;
    public static final double COMPRESSOR_CLEARANCE = 0.04;

    /** Expansion valve orifice area at full opening, m^2, by pipe casing tier, per ring. */
    public static double valveAreaPerRing(int tier) {
        return 0.004 * Math.pow(4.0, tier - 1);
    }

    /** Turbo-expander nozzle area per ring, m^2, by gearbox tier. */
    public static double expanderNozzlePerRing(int tier) {
        return 0.003 * Math.pow(4.0, tier - 1);
    }

    public static final double EXPANDER_ISENTROPIC_EFFICIENCY = 0.85;
    public static final double EXPANDER_GENERATOR_EFFICIENCY = 0.9;

    /** Radiator conductance per ring with its fans running, W/K, by fan tier. */
    public static double radiatorConductancePerRing(int tier) {
        return 3.0e6 * Math.pow(4.0, tier - 1);
    }

    /** What the fans of one radiator ring draw, EU/t, by fan tier. */
    public static long radiatorFanEuPerRing(int tier) {
        return 32L << (2 * (tier - 1));
    }

    /** Share of a radiator's conductance it keeps with its fans stopped (natural convection). */
    public static final double RADIATOR_STILL_AIR = 0.05;

    /** Volume per ring of the gas a machine holds, m^3. */
    public static final double VOLUME_PER_RING_M3 = 0.05;

    /** GregTech texture index of a casing block's meta. */
    public static int textureIndex(Block block, int meta) {
        return ((BlockCasingsAbstract) block).getTextureIndex(meta);
    }

    /**
     * Blocks of one kind in tiers: (block, metas[i]) is tier i + 1. The structure takes them all of one tier,
     * recorded with the setter.
     */
    public static <T> IStructureElement<T> tiered(Block block, int[] metas, BiConsumer<T, Integer> setter,
        Function<T, Integer> getter) {
        List<Pair<Block, Integer>> known = new ArrayList<>();
        for (int m : metas) known.add(Pair.of(block, m));
        return StructureUtility.ofBlocksTiered((b, meta) -> {
            if (b != block) return null;
            for (int i = 0; i < metas.length; i++) if (metas[i] == meta) return i + 1;
            return null;
        }, known, -1, setter, getter);
    }

    public static Block casings2() {
        return GregTechAPI.sBlockCasings2;
    }

    public static Block casings4() {
        return GregTechAPI.sBlockCasings4;
    }

    public static Block casingsNH() {
        return GregTechAPI.sBlockCasingsNH;
    }

    /** Si-prefixed number: 1.2k, 3.4M. */
    public static String si(double v) {
        double a = Math.abs(v);
        if (a >= 1e9) return String.format("%.2fG", v / 1e9);
        if (a >= 1e6) return String.format("%.2fM", v / 1e6);
        if (a >= 1e3) return String.format("%.1fk", v / 1e3);
        return String.format("%.1f", v);
    }
}

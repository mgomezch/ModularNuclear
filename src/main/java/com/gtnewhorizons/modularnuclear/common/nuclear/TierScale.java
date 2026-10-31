package com.gtnewhorizons.modularnuclear.common.nuclear;

/**
 * {@code base × ratio^(tier − offset)} for tiers 0..15, kept as a table until base or ratio change. Hatches and
 * passages ask for their tier's value every conduction sub-step, and {@link Math#pow} is slow for that; the table holds
 * the same {@code Math.pow} results, so values are bit-for-bit those of computing them each time.
 */
public final class TierScale {

    private static final int TIERS = 16;

    /** One immutable table, for one base and ratio. */
    private static final class Table {

        final double base;
        final double ratio;
        final double[] byTier = new double[TIERS];

        Table(double base, double ratio, int offset) {
            this.base = base;
            this.ratio = ratio;
            for (int t = 0; t < TIERS; t++) byTier[t] = base * Math.pow(ratio, t - offset);
        }
    }

    private final int offset;
    private volatile Table table;

    public TierScale(int offset) {
        this.offset = offset;
    }

    /** {@code base × ratio^(max(0, tier) − offset)}. */
    public double get(double base, double ratio, int tier) {
        int t = Math.max(0, tier);
        if (t >= TIERS) return base * Math.pow(ratio, t - offset);
        Table tb = table;
        if (tb == null || tb.base != base || tb.ratio != ratio) {
            tb = new Table(base, ratio, offset);
            table = tb;
        }
        return tb.byTier[t];
    }

    /** {@code 2^max(0, tier)}, exactly, without {@link Math#pow}. */
    public static double pow2(int tier) {
        int t = Math.max(0, tier);
        return t < 62 ? (double) (1L << t) : Math.pow(2.0, t);
    }
}

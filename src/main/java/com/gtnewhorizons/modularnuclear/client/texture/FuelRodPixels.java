package com.gtnewhorizons.modularnuclear.client.texture;

import java.awt.Color;

/**
 * The pixel work behind the custom fuel rods' icons, on packed ARGB pixels in rows, as BufferedImage.getRGB and
 * the texture atlas have them. Nothing here touches the game, so tests can run it.
 */
// See Note [Custom fuel rod textures]
public final class FuelRodPixels {

    /** How far a layer may be drawn off the uranium rod's place, in pixels, either way on each axis. */
    private static final int MAX_SHIFT = 2;

    private FuelRodPixels() {}

    /**
     * Whether a pixel of GregTech's uranium rod is fuel seen through the housing's window: opaque and tinted green
     * (hue 60 to 180 degrees, saturation over 0.2). The housing is grey, so this takes in fresh uranium's greens
     * and depleted uranium's teal, and nothing of the housing.
     */
    public static boolean isFuelWindow(int argb) {
        if ((argb >>> 24) == 0) return false;
        float[] hsb = Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null);
        return hsb[1] > 0.2f && hsb[0] >= 60.0f / 360.0f && hsb[0] <= 180.0f / 360.0f;
    }

    /** The uranium rod with its fuel window ({@link #isFuelWindow}) made transparent: the empty housing. */
    public static int[] withoutWindow(int[] uranium) {
        int[] out = uranium.clone();
        for (int i = 0; i < out.length; i++) {
            if (isFuelWindow(out[i])) out[i] = 0;
        }
        return out;
    }

    /**
     * The uranium rod with fuel of colour {@code rgb} in its window: each window pixel takes the colour, as much
     * lighter or darker as the uranium pixel is than the window's mean (by luma), so the window keeps its shading.
     */
    public static int[] recolouredWindow(int[] uranium, int rgb) {
        double sum = 0;
        int n = 0;
        for (int p : uranium) {
            if (isFuelWindow(p)) {
                sum += luma(p);
                n++;
            }
        }
        int[] out = uranium.clone();
        if (n == 0 || sum <= 0) return out;
        double mean = sum / n;
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        for (int i = 0; i < out.length; i++) {
            int p = out[i];
            if (!isFuelWindow(p)) continue;
            double t = luma(p) / mean;
            out[i] = (p & 0xFF000000) | clamp(r * t) << 16 | clamp(g * t) << 8 | clamp(b * t);
        }
        return out;
    }

    /** A colour halfway to the grey of its own luma: how {@link #recolouredWindow} shows a fuel spent. */
    public static int spent(int rgb) {
        double y = luma(rgb);
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return clamp((r + y) / 2) << 16 | clamp((g + y) / 2) << 8 | clamp((b + y) / 2);
    }

    /**
     * The shift (dx, dy) that lines {@code other} up with {@code base}: shifting {@code other} by (-dx, -dy) puts its
     * opaque pixels where the base's are. Of the shifts up to {@link #MAX_SHIFT} each way, the one under which the
     * most pixels agree on being opaque or not wins; ties go to the smaller shift.
     */
    // See wrinkle (W1) of Note [Custom fuel rod textures]
    public static int[] alignment(int[] base, int[] other, int width) {
        int[] best = { 0, 0 };
        int bestScore = -1;
        for (int d = 0; d <= 2 * MAX_SHIFT; d++) {
            for (int dx = -MAX_SHIFT; dx <= MAX_SHIFT; dx++) {
                for (int dy = -MAX_SHIFT; dy <= MAX_SHIFT; dy++) {
                    if (Math.abs(dx) + Math.abs(dy) != d) continue;
                    int[] moved = shift(other, width, -dx, -dy);
                    int score = 0;
                    for (int i = 0; i < base.length; i++) {
                        if (((base[i] >>> 24) != 0) == ((moved[i] >>> 24) != 0)) score++;
                    }
                    if (score > bestScore) {
                        bestScore = score;
                        best = new int[] { dx, dy };
                    }
                }
            }
        }
        return best;
    }

    /** The pixels moved (dx, dy); what moves out is lost and what moves in is transparent. */
    public static int[] shift(int[] pixels, int width, int dx, int dy) {
        int height = pixels.length / width;
        int[] out = new int[pixels.length];
        for (int y = 0; y < height; y++) {
            int sy = y - dy;
            if (sy < 0 || sy >= height) continue;
            for (int x = 0; x < width; x++) {
                int sx = x - dx;
                if (sx < 0 || sx >= width) continue;
                out[y * width + x] = pixels[sy * width + sx];
            }
        }
        return out;
    }

    /**
     * The layers blended pixel by pixel, each by its weight (weights summing to 1; layers of weight 0 are skipped
     * and may be null). Alpha blends as a weighted mean and colour by alpha-weighted means, so a layer's
     * transparent pixels add no colour. A pixel in one layer of weight 1 comes out exactly as it went in.
     */
    public static int[] blend(int[][] layers, double[] weights, int length) {
        int[] out = new int[length];
        for (int i = 0; i < length; i++) {
            double a = 0, r = 0, g = 0, b = 0;
            for (int l = 0; l < layers.length; l++) {
                double w = weights[l];
                if (w <= 0) continue;
                int p = layers[l][i];
                double pa = (p >>> 24) * w;
                a += pa;
                r += ((p >> 16) & 0xFF) * pa;
                g += ((p >> 8) & 0xFF) * pa;
                b += (p & 0xFF) * pa;
            }
            if (a <= 0) continue;
            out[i] = clamp(a) << 24 | clamp(r / a) << 16 | clamp(g / a) << 8 | clamp(b / a);
        }
        return out;
    }

    private static double luma(int rgb) {
        return 0.299 * ((rgb >> 16) & 0xFF) + 0.587 * ((rgb >> 8) & 0xFF) + 0.114 * (rgb & 0xFF);
    }

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }
}

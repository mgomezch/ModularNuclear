package com.gtnewhorizons.modularnuclear.client.texture;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.data.AnimationFrame;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ResourceLocation;

import com.gtnewhorizons.modularnuclear.ModularNuclear;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/*-
Note [Tinting heavy water's textures] (generated)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Heavy water looks like Minecraft's water, turned purple, and its steams like the steams of the same grade, drawn
toward heavy water's purple.  TintedSprite builds these icons when the block atlas is stitched, from the textures
they copy as the loaded resource packs have them, so the mod ships none of that art and follows whatever a resource
pack draws:

  heavywater_still, _flow        minecraft:textures/blocks/water_still.png, water_flow.png
  heavywatersteam                railcraft:textures/blocks/fluids/steam_still.png (steam), or without Railcraft,
                                 gregtech:textures/blocks/fluids/fluid.steam.png played back and forth
  superheatedheavywatersteam     ic2:textures/blocks/fluids/superheatedsteam_still.png (ic2superheatedsteam)
  supercriticalheavywatersteam   goodgenerator:textures/blocks/fluids/supercriticalSteam.still.png and
    _still, _flow                .flowing.png (supercriticalsteam)

ModFluids.ClientFluidTextureHandler sets up the icons, with the tints HEAVY_WATER and HEAVY_WATER_STEAM.

Water's tint needs care.  A hue turn alone looks washed out.  Vanilla water's ripples aren't in its brightness:
every pixel's blue is about 0.96, and the ripples are small swings in red, green and opacity around a frame mean of
about (0.18, 0.26, 0.96, alpha 0.70).  Turned toward purple, those swings read as even fainter, so the ripples all but
vanish.  Tint.apply therefore works on each animation frame in this order:

  1. Ripple contrast: red, green and blue pushed away from the frame's mean by Tint.contrast.  Water's
     ripples are in red and green, so this is what keeps them visible.
  2. Alpha contrast: the same for opacity, by Tint.alphaContrast.  Lighter ripples are also more opaque.
  3. Hue, saturation and brightness: the hue turned by Tint.hueTurn, saturation and brightness scaled,
     through Color.RGBtoHSB and Color.HSBtoRGB.
  4. Blend: a mix toward Tint.blendColour, by Tint.blend.

Means are per frame (frames are square, stacked down the image), so a frame that is lighter on the whole stays
lighter.  Values are clamped to 0..255 after each step.

Wrinkles:

(W1) A resource pack can draw heavy water or its steams itself.  The mod ships no PNG at its icons' paths
     (modularnuclear:textures/blocks/fluids/heavywater_still.png and so on), so when the resource manager finds
     one there, a pack put it there.  TintedSprite.hasCustomLoader then returns false, and TextureMap loads that
     PNG and its .mcmeta animation as it loads any block texture; no tint is applied.  A pack that only changes
     Minecraft's water still gets heavy water tinted from its water.  The check assumes the blocks atlas
     (textures/blocks), the only one TintedSprite is used on.

(W2) Each steam copies the texture the game shows for the light-water steam of its grade.  For steam that is
     Railcraft's: GregTech, IC2 and Railcraft each have a steam texture, and Railcraft registers the fluid steam
     first, so its icon is the one the game draws.  GregTech's fluid.steam.png goes unused, and it doesn't loop:
     its 20 frames boil in place, a frame at a time, but the last frame is far from the first, so it jumps once a
     second.  (Across the wrap a pixel's red, green and blue change by 16 between them, on average, against 1 to 3
     between other frames.)  Railcraft's steam and IC2's play their frames forward and then back, as their .mcmeta
     lists them, and loop without a jump.

     Without Railcraft, as in a development client, which has Railcraft only to compile against, heavy-water
     steam falls back to GregTech's texture and plays it forward and back too (TintedSprite.pingPong, in the
     order pingPongFrames gives): 0, 1, ..., 19, 18, ..., 1, then 0 again.  The steam doesn't drift, so playing it
     backward doesn't show.  A source whose .mcmeta lists its frames keeps its own order.

HEAVY_WATER's parameters were chosen by eye in a local slider page that runs this same pipeline in JavaScript on
vanilla water.  Color.HSBtoRGB rounds to whole values before the blend where the page doesn't, so the two can
differ by 2 in 255; TintedSpriteTest checks the pipeline against pixels taken from that page.

Steam is grey, so a hue turn does nothing to it: HEAVY_WATER_STEAM blends it 30% of the way toward 0x612AE8,
heavy water's average colour (vanilla water_still through HEAVY_WATER, averaged over its pixels and frames).  A
blend pulls every pixel toward the same colour, so it flattens the steam's mottle by as much; a contrast of 1.4
gives the mottle back.
*/
/** An atlas sprite made when textures load, from another texture, tinted. */
@SideOnly(Side.CLIENT)
public class TintedSprite extends TextureAtlasSprite {

    /**
     * A tint, as Tint.apply does it.
     *
     * @param hueTurn       the hue turn, in turns (1.0 is all the way round)
     * @param contrast      how far red, green and blue are pushed from the frame's mean (1 keeps them)
     * @param alphaContrast how far alpha is pushed from the frame's mean (1 keeps it)
     * @param saturation    saturation's multiplier
     * @param brightness    brightness's multiplier
     * @param blendColour   the RGB colour blended toward
     * @param blend         how far toward it, 0 to 1
     */
    public record Tint(float hueTurn, float contrast, float alphaContrast, float saturation, float brightness,
        int blendColour, float blend) {

        /** A copy of the image, each square frame tinted. */
        public BufferedImage apply(BufferedImage image) {
            int w = image.getWidth(), h = image.getHeight();
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            for (int top = 0; top < h; top += w) {
                tintFrame(image, out, top, Math.min(h, top + w));
            }
            return out;
        }

        private void tintFrame(BufferedImage in, BufferedImage out, int top, int bottom) {
            int w = in.getWidth();
            double[] mean = new double[4];
            for (int y = top; y < bottom; y++) {
                for (int x = 0; x < w; x++) {
                    int argb = in.getRGB(x, y);
                    for (int c = 0; c < 4; c++) mean[c] += channel(argb, c);
                }
            }
            int n = w * (bottom - top);
            for (int c = 0; c < 4; c++) mean[c] /= n;

            float[] hsb = new float[3];
            float blendR = (blendColour >> 16) & 0xFF, blendG = (blendColour >> 8) & 0xFF, blendB = blendColour & 0xFF;
            for (int y = top; y < bottom; y++) {
                for (int x = 0; x < w; x++) {
                    int argb = in.getRGB(x, y);
                    int r = clamp(mean[0] + contrast * (channel(argb, 0) - mean[0]));
                    int g = clamp(mean[1] + contrast * (channel(argb, 1) - mean[1]));
                    int b = clamp(mean[2] + contrast * (channel(argb, 2) - mean[2]));
                    int a = clamp(mean[3] + alphaContrast * (channel(argb, 3) - mean[3]));
                    Color.RGBtoHSB(r, g, b, hsb);
                    int rgb = Color.HSBtoRGB(
                        hsb[0] + hueTurn,
                        Math.min(1.0f, hsb[1] * saturation),
                        Math.min(1.0f, hsb[2] * brightness));
                    r = clamp(channel(rgb, 0) + (blendR - channel(rgb, 0)) * blend);
                    g = clamp(channel(rgb, 1) + (blendG - channel(rgb, 1)) * blend);
                    b = clamp(channel(rgb, 2) + (blendB - channel(rgb, 2)) * blend);
                    out.setRGB(x, y, a << 24 | r << 16 | g << 8 | b);
                }
            }
        }

        /** Red, green, blue or alpha (0 to 3) of a packed ARGB colour. */
        private static int channel(int argb, int c) {
            return (argb >> (c == 3 ? 24 : 16 - 8 * c)) & 0xFF;
        }

        private static int clamp(double v) {
            return (int) Math.max(0, Math.min(255, Math.round(v)));
        }
    }

    private final ResourceLocation source;
    private final Tint tint;
    private final boolean pingPong;

    public TintedSprite(String iconName, ResourceLocation source, Tint tint) {
        this(iconName, source, tint, false);
    }

    /** pingPong: play the source's frames forward and then back, unless its .mcmeta lists them (wrinkle (W2)). */
    public TintedSprite(String iconName, ResourceLocation source, Tint tint, boolean pingPong) {
        super(iconName);
        this.source = source;
        this.tint = tint;
        this.pingPong = pingPong;
    }

    /** The frames of an n-frame animation played forward and then back: 0..n-1, then n-2 down to 1. */
    public static List<Integer> pingPongFrames(int n) {
        List<Integer> frames = new ArrayList<>();
        for (int i = 0; i < n; i++) frames.add(i);
        for (int i = n - 2; i > 0; i--) frames.add(i);
        return frames;
    }

    // Tints only when no resource pack draws this texture itself: a pack's own PNG (and its animation) loads as
    // any block texture does. See wrinkle (W1)
    @Override
    public boolean hasCustomLoader(IResourceManager manager, ResourceLocation location) {
        ResourceLocation own = new ResourceLocation(
            location.getResourceDomain(),
            "textures/blocks/" + location.getResourcePath() + ".png");
        try {
            manager.getResource(own);
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    /** Loads the sprite; false means it loaded (TextureMap's convention for custom loaders). */
    @Override
    public boolean load(IResourceManager manager, ResourceLocation location) {
        try {
            IResource resource = manager.getResource(source);
            BufferedImage image;
            try (InputStream in = resource.getInputStream()) {
                image = ImageIO.read(in);
            }
            GameSettings settings = Minecraft.getMinecraft().gameSettings;
            BufferedImage[] mipmaps = new BufferedImage[1 + settings.mipmapLevels];
            mipmaps[0] = tint.apply(image);
            AnimationMetadataSection animation = (AnimationMetadataSection) resource.getMetadata("animation");
            if (pingPong && animation != null && animation.getFrameCount() == 0) {
                animation = pingPong(animation, image.getHeight() / image.getWidth());
            }
            loadSprite(mipmaps, animation, settings.anisotropicFiltering > 1);
            return false;
        } catch (IOException | RuntimeException e) {
            ModularNuclear.LOG.error("Could not make texture " + getIconName() + " from " + source, e);
            return true;
        }
    }

    // See wrinkle (W2)
    private static AnimationMetadataSection pingPong(AnimationMetadataSection animation, int n) {
        List<AnimationFrame> frames = new ArrayList<>();
        for (int i : pingPongFrames(n)) frames.add(new AnimationFrame(i));
        return new AnimationMetadataSection(
            frames,
            animation.getFrameWidth(),
            animation.getFrameHeight(),
            animation.getFrameTime());
    }
}

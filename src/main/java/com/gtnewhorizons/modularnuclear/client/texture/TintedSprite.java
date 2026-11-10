package com.gtnewhorizons.modularnuclear.client.texture;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ResourceLocation;

import com.gtnewhorizons.modularnuclear.ModularNuclear;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/*-
Note [Tinting Minecraft's water] (generated)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Heavy water looks like Minecraft's water, turned purple.  TintedSprite builds its icons when the block atlas is
stitched, from minecraft:textures/blocks/water_still.png and water_flow.png as the loaded resource packs have them,
so the mod ships none of Minecraft's art and follows whatever water a resource pack draws.

A hue turn alone looks washed out.  Vanilla water's ripples aren't in its brightness: every pixel's blue is
about 0.96, and the ripples are small swings in red, green and opacity around a frame mean of about
(0.18, 0.26, 0.96, alpha 0.70).  Turned toward purple, those swings read as even fainter, so the ripples all but
vanish.  Tint.apply therefore works on each animation frame in this order:

  1. Ripple contrast: red, green and blue pushed away from the frame's mean by Tint.contrast.  Water's
     ripples are in red and green, so this is what keeps them visible.
  2. Alpha contrast: the same for opacity, by Tint.alphaContrast.  Lighter ripples are also more opaque.
  3. Hue, saturation and brightness: the hue turned by Tint.hueTurn, saturation and brightness scaled,
     through Color.RGBtoHSB and Color.HSBtoRGB.
  4. Blend: a mix toward Tint.blendColour, by Tint.blend.

Means are per frame (frames are square, stacked down the image), so a frame that is lighter on the whole stays
lighter.  Values are clamped to 0..255 after each step.

ModFluids.ClientFluidTextureHandler.HEAVY_WATER sets the parameters.  They were chosen by eye in a local slider
page that runs this same pipeline in JavaScript on vanilla water.  Color.HSBtoRGB rounds to whole values before
the blend where the page doesn't, so the two can differ by 2 in 255; TintedSpriteTest checks the pipeline against
pixels taken from that page.
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

    public TintedSprite(String iconName, ResourceLocation source, Tint tint) {
        super(iconName);
        this.source = source;
        this.tint = tint;
    }

    @Override
    public boolean hasCustomLoader(IResourceManager manager, ResourceLocation location) {
        return true;
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
            loadSprite(mipmaps, animation, settings.anisotropicFiltering > 1);
            return false;
        } catch (IOException | RuntimeException e) {
            ModularNuclear.LOG.error("Could not make texture " + getIconName() + " from " + source, e);
            return true;
        }
    }
}

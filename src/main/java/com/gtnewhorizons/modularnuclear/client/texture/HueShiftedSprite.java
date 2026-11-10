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

/**
 * An atlas sprite made when textures load from another texture, its hue turned: heavy water is Minecraft's water,
 * turned a little purple. The source is read through the resource manager, so it is whatever water the loaded
 * resource packs have, animation included, and the mod ships none of Minecraft's art.
 */
@SideOnly(Side.CLIENT)
public class HueShiftedSprite extends TextureAtlasSprite {

    private final ResourceLocation source;
    /** The hue turn, in turns (1.0 is all the way round). */
    private final float hueShift;

    public HueShiftedSprite(String iconName, ResourceLocation source, float hueShift) {
        super(iconName);
        this.source = source;
        this.hueShift = hueShift;
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
            mipmaps[0] = shiftHue(image, hueShift);
            AnimationMetadataSection animation = (AnimationMetadataSection) resource.getMetadata("animation");
            loadSprite(mipmaps, animation, settings.anisotropicFiltering > 1);
            return false;
        } catch (IOException | RuntimeException e) {
            ModularNuclear.LOG.error("Could not make texture " + getIconName() + " from " + source, e);
            return true;
        }
    }

    /** A copy of the image with each pixel's hue turned by the given turns; brightness, saturation and alpha kept. */
    public static BufferedImage shiftHue(BufferedImage image, float turns) {
        int w = image.getWidth(), h = image.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        float[] hsb = new float[3];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = image.getRGB(x, y);
                Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, hsb);
                int rgb = Color.HSBtoRGB(hsb[0] + turns, hsb[1], hsb[2]) & 0xFFFFFF;
                out.setRGB(x, y, (argb & 0xFF000000) | rgb);
            }
        }
        return out;
    }
}

package com.gtnewhorizons.modularnuclear.client.texture;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.oredict.OreDictionary;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizons.modularnuclear.ModularNuclear;
import com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig;
import com.gtnewhorizons.modularnuclear.common.nuclear.CustomFuelMix;
import com.gtnewhorizons.modularnuclear.common.nuclear.CustomFuelMix.ActinideIsotope;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/*-
Note [Custom fuel rod textures] (generated)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Topic.  A custom fuel rod (ItemCustomFuelRod, ItemCustomDepletedFuelRod) holds whatever actinide mix the Actinide
Fuel Compounder put in it, so no fixed texture can show it.  Its icon is GregTech's uranium rod of the same size,
fresh or depleted, with the fuel in its window coloured by the mix.  A rod of one pure fuel looks exactly like
GregTech's rod of that fuel, where GregTech has one.  The mod ships none of this art: everything is made from the
textures the loaded resource packs give GregTech's rods.

Design.  When the item atlas is stitched, FuelRodIcons.onStitch reads, for each of the six kinds of rod (Kind: fresh
and depleted, single, dual and quad):

  - gregtech:textures/items/gt.rodUranium.png (gt.rodUranium2.png, gt.depletedRodUranium4.png, ...),
  - the same rod of each fuel in GT_ROD: Thorium for Th-232, Uranium for U-238, Naquadah for Nq.

and registers in the atlas:

  - a base sprite per kind: the uranium rod with its window made transparent (FuelRodPixels.withoutWindow; the
    window is the green-tinted pixels, FuelRodPixels.isFuelWindow).  It is the icon of a rod with no mix, as in NEI:
    an empty rod.
  - fuelRodTextureCacheSize slot sprites (ModularNuclearConfig, client_visuals, 30 by default), uranium-sized.

A rod's icon (FuelRodIcons.icon) is found by its composition hash, which the compounder stores in the rod's NBT
beside the units (CustomFuelMix.NBT_HASH, from CustomFuelMix.compositionHash), with the kind mixed in.  The slots
are a least-recently-used cache by that key: a hit returns the slot's sprite, and a miss draws the mix's pixels
(FuelRodIcons.compose) and uploads them into the slot drawn longest ago, over its old texture.  A hit reads one
long from the NBT and compares at most fuelRodTextureCacheSize longs, so drawing a rod costs nearly nothing; the mix
itself (CustomFuelMixCache) is only looked at on a miss.

The pixels of a mix are a blend, pixel by pixel, of one layer per isotope in it, weighted by its share of the units
(FuelRodPixels.blend):

  - an isotope in GT_ROD: GregTech's rod of that fuel, as it is, housing and all.
  - any other: the uranium rod with its window recoloured to the isotope's colour (FuelRodPixels.recolouredWindow),
    which is the colour of its dust (dustUranium235 and so on, through the dust item's getColorFromItemStack).  A
    depleted rod's window takes that colour halfway to grey (FuelRodPixels.spent).

So a rod of 100% Th-232 is GregTech's thorium rod, pixel for pixel, and one of 100% U-238 is its uranium rod.  A
half-and-half rod of the two has a window halfway between uranium's greens and thorium's black.

Moving parts: FuelRodIcons (stitching, slots, upload), FuelRodPixels (the pixel work, which FuelRodPixelsTest
covers), CustomFuelMix.compositionHash and hashOf, the getIconIndex and getIcon overrides of the two rod items,
and ModularNuclearConfig.fuelRodTextureCacheSize.

Wrinkles:

(W1) GregTech's dual uranium rods are drawn one pixel higher than every other fuel's dual rods; single and quad
     rods are all in the same place.  Blending a dual uranium layer with a dual thorium one as they are would draw
     two rods on top of each other, a pixel apart.  So each GT_ROD layer is first moved to where the uranium rod is
     (FuelRodPixels.alignment finds the shift), blended there, and the result is then moved to where the mix's
     largest fuel draws it.  A pure rod therefore goes back exactly to its own place.

(W2) GregTech's naquadah rods have a blue housing.  Mixes with naquadah blend toward it, so their housing turns
     blue with the naquadah share.  That keeps a pure naquadah rod looking like GregTech's.

(W3) A slot drawn in the current frame is never overwritten in the same frame: an item drawn earlier in the frame
     may not be on screen yet (batched rendering), and would show the new texture.  When more distinct mixes are on
     screen than there are slots, the ones past the cache's size show the base sprite, an empty rod, until slots
     free up.  FuelRodIcons.onRenderTick counts frames.

(W4) Rods made before rods stored the hash carry only the units: CustomFuelMix.hashOf works the hash out from them
     each time, which takes nine NBT reads.  Such rods don't stack with rods made since, whose NBT has the hash.

(W5) Two compositions with the same 64-bit hash would share a texture.  Unlike CustomFuelMixCache, which checks the
     units, the texture cache trusts the hash: a world holds few compositions, and the worst case is a wrong colour.

(W6) Each stitch (a resource reload, F3+T) rebuilds the sprites and empties the slots, so a new cache size or a
     resource pack's rods take effect then.  An upload writes the slot's place in the item atlas's GL texture, in
     the middle of drawing, and binds back whatever texture was bound.  Nothing is uploaded off the client thread:
     there an uncached rod gets the base sprite.
*/
/** The icons of custom fuel rods: GregTech's uranium rod, with the fuel in its window coloured by the rod's mix. */
// See Note [Custom fuel rod textures]
@SideOnly(Side.CLIENT)
public final class FuelRodIcons {

    /** A kind of custom rod, by the GregTech uranium rod it is drawn from. */
    public enum Kind {

        FRESH_1("rod", ""),
        FRESH_2("rod", "2"),
        FRESH_4("rod", "4"),
        DEPLETED_1("depletedRod", ""),
        DEPLETED_2("depletedRod", "2"),
        DEPLETED_4("depletedRod", "4");

        final String gtPrefix;
        final String gtSuffix;

        Kind(String gtPrefix, String gtSuffix) {
            this.gtPrefix = gtPrefix;
            this.gtSuffix = gtSuffix;
        }

        public static Kind of(boolean depleted, int rodSize) {
            int size = rodSize <= 1 ? 0 : rodSize == 2 ? 1 : 2;
            return values()[(depleted ? 3 : 0) + size];
        }

        boolean depleted() {
            return ordinal() >= 3;
        }

        ResourceLocation gtTexture(String fuel) {
            return new ResourceLocation("gregtech", "textures/items/gt." + gtPrefix + fuel + gtSuffix + ".png");
        }
    }

    private static final ActinideIsotope[] ISOTOPES = ActinideIsotope.values();
    private static final Kind[] KINDS = Kind.values();

    /** The GregTech rod a pure rod of each isotope looks like, by its name in gt.rod<name>.png; null for none. */
    private static final String[] GT_ROD = new String[ISOTOPES.length];
    /** The ore name of each isotope's dust, whose colour a window takes when GregTech has no rod of it. */
    private static final String[] DUST = new String[ISOTOPES.length];
    /** Colours for when no dust is found. */
    private static final int[] FALLBACK_COLOUR = new int[ISOTOPES.length];

    static {
        GT_ROD[ActinideIsotope.THORIUM.ordinal()] = "Thorium";
        GT_ROD[ActinideIsotope.URANIUM_238.ordinal()] = "Uranium";
        GT_ROD[ActinideIsotope.NAQUADAH.ordinal()] = "Naquadah";
        dust(ActinideIsotope.THORIUM, "dustThorium", 0x001E00);
        dust(ActinideIsotope.URANIUM_238, "dustUranium", 0x32F032);
        dust(ActinideIsotope.URANIUM_235, "dustUranium235", 0x46FA46);
        dust(ActinideIsotope.PLUTONIUM_239, "dustPlutonium", 0xF03232);
        dust(ActinideIsotope.PLUTONIUM_241, "dustPlutonium241", 0xFA4646);
        dust(ActinideIsotope.URANIUM_233, "dustUranium233", 0x2DBE2D);
        dust(ActinideIsotope.AMERICIUM, "dustAmericium", 0xC8C8C8);
        dust(ActinideIsotope.CALIFORNIUM, "dustCalifornium", 0xFFA5A5);
        dust(ActinideIsotope.NAQUADAH, "dustNaquadah", 0x323232);
    }

    private static void dust(ActinideIsotope iso, String ore, int fallback) {
        DUST[iso.ordinal()] = ore;
        FALLBACK_COLOUR[iso.ordinal()] = fallback;
    }

    /** One kind's textures for this stitch. */
    private static final class KindTextures {

        final int width;
        final int[] uranium;
        /** GregTech's rod of each GT_ROD isotope, as read; null where there is none or it doesn't fit. */
        final int[][] gtRods = new int[ISOTOPES.length][];
        /** Each isotope's layer, moved to the uranium rod's place, and that move; made on first use. */
        final int[][] layers = new int[ISOTOPES.length][];
        final int[][] offsets = new int[ISOTOPES.length][];
        PixelSprite base;

        KindTextures(int width, int[] uranium) {
            this.width = width;
            this.uranium = uranium;
        }
    }

    private static final KindTextures[] kinds = new KindTextures[KINDS.length];
    private static TextureMap atlas;
    private static PixelSprite[] slots = new PixelSprite[0];
    /** Each slot's key (composition hash and kind), 0 when empty. */
    private static long[] slotKeys = new long[0];
    /** The frame each slot was last drawn in. */
    private static long[] slotFrames = new long[0];
    private static int slotWidth;
    private static long frame = 1;
    private static int[] dustColours;

    private FuelRodIcons() {}

    public static void register() {
        Handler handler = new Handler();
        MinecraftForge.EVENT_BUS.register(handler);
        FMLCommonHandler.instance()
            .bus()
            .register(handler);
    }

    public static final class Handler {

        @SubscribeEvent
        public void onTextureStitch(TextureStitchEvent.Pre event) {
            if (event.map.getTextureType() == 1) {
                onStitch(
                    event.map,
                    Minecraft.getMinecraft()
                        .getResourceManager());
            }
        }

        // See wrinkle (W3) of Note [Custom fuel rod textures]
        @SubscribeEvent
        public void onRenderTick(TickEvent.RenderTickEvent event) {
            if (event.phase == TickEvent.Phase.START) frame++;
        }
    }

    // See wrinkle (W6) of Note [Custom fuel rod textures]
    private static void onStitch(TextureMap map, IResourceManager manager) {
        atlas = map;
        for (Kind kind : KINDS) {
            KindTextures k = null;
            BufferedImage uranium = read(manager, kind.gtTexture("Uranium"));
            if (uranium != null) {
                k = new KindTextures(uranium.getWidth(), pixels(uranium));
                for (int i = 0; i < ISOTOPES.length; i++) {
                    if (GT_ROD[i] == null) continue;
                    BufferedImage rod = read(manager, kind.gtTexture(GT_ROD[i]));
                    if (rod != null && rod.getWidth() == uranium.getWidth() && rod.getHeight() == uranium.getHeight()) {
                        k.gtRods[i] = pixels(rod);
                    }
                }
                k.base = sprite(
                    map,
                    "modularnuclear:fuelrod/base_" + kind.name()
                        .toLowerCase(Locale.ROOT),
                    k.width,
                    FuelRodPixels.withoutWindow(k.uranium));
            }
            kinds[kind.ordinal()] = k;
        }
        KindTextures single = kinds[Kind.FRESH_1.ordinal()];
        int n = single == null ? 0 : Math.max(1, ModularNuclearConfig.fuelRodTextureCacheSize);
        slots = new PixelSprite[n];
        slotKeys = new long[n];
        slotFrames = new long[n];
        slotWidth = single == null ? 0 : single.width;
        for (int i = 0; i < n; i++) {
            slots[i] = sprite(map, "modularnuclear:fuelrod/slot_" + i, single.width, single.base.pixels);
        }
    }

    private static PixelSprite sprite(TextureMap map, String name, int width, int[] pixels) {
        PixelSprite sprite = new PixelSprite(name, width, pixels);
        map.setTextureEntry(name, sprite);
        return sprite;
    }

    /** The icon of a rod of this kind with no mix: the empty uranium rod; null when GregTech's rod isn't there. */
    public static IIcon base(Kind kind) {
        KindTextures k = kinds[kind.ordinal()];
        return k == null ? null : k.base;
    }

    /** The icon of a rod of this kind, coloured by the mix in its NBT. */
    public static IIcon icon(Kind kind, ItemStack stack) {
        KindTextures k = kinds[kind.ordinal()];
        if (k == null) return null;
        long hash = stack == null ? 0L : CustomFuelMix.hashOf(stack.getTagCompound());
        if (hash == 0L || k.width != slotWidth) return k.base;
        long key = (hash ^ 0x9E3779B97F4A7C15L * (kind.ordinal() + 1)) | 1L;

        long[] keys = slotKeys;
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] == key) {
                slotFrames[i] = frame;
                return slots[i];
            }
        }
        if (!Minecraft.getMinecraft()
            .func_152345_ab()) return k.base; // not the client thread: no GL
        // An empty slot, else the one drawn longest ago, but none drawn this frame
        int victim = -1;
        for (int i = 0; i < keys.length; i++) {
            if (slotFrames[i] == frame) continue; // See wrinkle (W3) of Note [Custom fuel rod textures]
            if (keys[i] == 0L) {
                victim = i;
                break;
            }
            if (victim < 0 || slotFrames[i] < slotFrames[victim]) victim = i;
        }
        if (victim < 0) return k.base;
        CustomFuelMix mix = CustomFuelMix.fromStack(stack);
        if (mix == null) return k.base;
        try {
            upload(slots[victim], compose(kind, k, mix));
        } catch (RuntimeException e) {
            ModularNuclear.LOG.error("Could not draw the texture of a custom fuel rod", e);
            return k.base;
        }
        keys[victim] = key;
        slotFrames[victim] = frame;
        return slots[victim];
    }

    /** The mix's pixels for this kind of rod. */
    private static int[] compose(Kind kind, KindTextures k, CustomFuelMix mix) {
        int[][] layers = new int[ISOTOPES.length][];
        double[] weights = new double[ISOTOPES.length];
        int dominant = -1;
        for (Map.Entry<ActinideIsotope, Double> e : mix.getIsotopeFractions()
            .entrySet()) {
            int i = e.getKey()
                .ordinal();
            weights[i] = e.getValue();
            layers[i] = layer(kind, k, i);
            if (dominant < 0 || weights[i] > weights[dominant]) dominant = i;
        }
        int[] blended = FuelRodPixels.blend(layers, weights, k.uranium.length);
        // See wrinkle (W1) of Note [Custom fuel rod textures]
        int[] offset = k.offsets[dominant];
        return FuelRodPixels.shift(blended, k.width, offset[0], offset[1]);
    }

    private static int[] layer(Kind kind, KindTextures k, int isotope) {
        if (k.layers[isotope] != null) return k.layers[isotope];
        int[] rod = k.gtRods[isotope];
        if (rod != null) {
            int[] offset = FuelRodPixels.alignment(k.uranium, rod, k.width);
            k.offsets[isotope] = offset;
            k.layers[isotope] = FuelRodPixels.shift(rod, k.width, -offset[0], -offset[1]);
        } else {
            int colour = dustColour(isotope);
            k.offsets[isotope] = new int[] { 0, 0 };
            k.layers[isotope] = FuelRodPixels
                .recolouredWindow(k.uranium, kind.depleted() ? FuelRodPixels.spent(colour) : colour);
        }
        return k.layers[isotope];
    }

    private static int dustColour(int isotope) {
        if (dustColours == null) {
            int[] colours = new int[ISOTOPES.length];
            for (int i = 0; i < ISOTOPES.length; i++) {
                colours[i] = FALLBACK_COLOUR[i];
                for (ItemStack dust : OreDictionary.getOres(DUST[i])) {
                    if (dust == null || dust.getItem() == null) continue;
                    int c = dust.getItem()
                        .getColorFromItemStack(dust, 0) & 0xFFFFFF;
                    if (c != 0xFFFFFF) colours[i] = c;
                    break;
                }
            }
            dustColours = colours;
        }
        return dustColours[isotope];
    }

    private static void upload(PixelSprite slot, int[] pixels) {
        int[][] mips = slot.mipLevels > 0
            ? TextureUtil.generateMipmapData(slot.mipLevels, slot.getIconWidth(), new int[][] { pixels })
            : new int[][] { pixels };
        int bound = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlas.getGlTextureId());
        TextureUtil.uploadTextureMipmap(
            mips,
            slot.getIconWidth(),
            slot.getIconHeight(),
            slot.getOriginX(),
            slot.getOriginY(),
            false,
            false);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, bound);
    }

    private static BufferedImage read(IResourceManager manager, ResourceLocation location) {
        try (InputStream in = manager.getResource(location)
            .getInputStream()) {
            return ImageIO.read(in);
        } catch (IOException e) {
            return null;
        }
    }

    private static int[] pixels(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        return image.getRGB(0, 0, w, h, null, 0, w);
    }

    /** An atlas sprite of given pixels. */
    private static final class PixelSprite extends TextureAtlasSprite {

        final int width;
        final int[] pixels;
        int mipLevels;

        PixelSprite(String name, int width, int[] pixels) {
            super(name);
            this.width = width;
            this.pixels = pixels;
        }

        @Override
        public boolean hasCustomLoader(IResourceManager manager, ResourceLocation location) {
            return true;
        }

        /** Loads the sprite; false means it loaded (TextureMap's convention for custom loaders). */
        @Override
        public boolean load(IResourceManager manager, ResourceLocation location) {
            setIconWidth(width);
            setIconHeight(pixels.length / width);
            List<int[][]> frames = new ArrayList<>();
            frames.add(new int[][] { pixels.clone() });
            setFramesTextureData(frames);
            return false;
        }

        @Override
        public void generateMipmaps(int levels) {
            mipLevels = levels;
            super.generateMipmaps(levels);
        }
    }
}

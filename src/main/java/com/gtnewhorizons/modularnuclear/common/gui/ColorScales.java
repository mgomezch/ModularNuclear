package com.gtnewhorizons.modularnuclear.common.gui;

import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;

import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearColorMaps;
import com.gtnewhorizons.modularnuclear.common.util.Swallowed;

/*-
Note [Heat map scales come from textures] (generated)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
The reactor grid's heat maps and the NEI heat card's row colour their tiles along a scale of 300 steps, each with its
own alpha.  A resource pack replaces a scale with a texture whose top row runs from the low end at the left to the high
end at the right, at any width:

    modularnuclear:textures/gui/scales/temperature.png    core temperature, also the NEI heat row
    modularnuclear:textures/gui/scales/neutrons.png       neutron flux and absorption
    modularnuclear:textures/gui/scales/heat_output.png    heat output

The mod ships no such texture.  Without one, a scale is NuclearColorMaps' array, which the web simulator draws with
too, so the simulator and the game agree by default.  ColorScales reads the textures again after each resource reload.
This is part of Note [Resource packs restyle our GUIs].
*/
public final class ColorScales {

    private ColorScales() {}

    private static final int STEPS = 300;

    private static int[] sTemperature;
    private static int[] sNeutrons;
    private static int[] sHeatOutput;
    private static boolean sListening;

    public static int temperature(double temperature, double maxTemp) {
        load();
        return sTemperature[NuclearColorMaps.temperatureIndex(temperature, maxTemp)];
    }

    public static int neutrons(double neutronRate) {
        load();
        return sNeutrons[NuclearColorMaps.neutronIndex(neutronRate)];
    }

    /** Transparent for no heat. */
    public static int heatOutput(double heatRate) {
        if (heatRate <= 0) return 0;
        load();
        return sHeatOutput[NuclearColorMaps.heatOutputIndex(heatRate)];
    }

    private static void load() {
        if (sTemperature != null) return;
        Minecraft mc = Minecraft.getMinecraft();
        IResourceManager resources = mc != null ? mc.getResourceManager() : null;
        if (!sListening && resources instanceof IReloadableResourceManager reloadable) {
            sListening = true;
            reloadable.registerReloadListener((IResourceManagerReloadListener) manager -> sTemperature = null);
        }
        sNeutrons = read(resources, "neutrons", NuclearColorMaps.NEUTRON_COLORS);
        sHeatOutput = read(resources, "heat_output", NuclearColorMaps.HEAT_OUTPUT_COLORS);
        sTemperature = read(resources, "temperature", NuclearColorMaps.TEMPERATURE_COLORS);
    }

    private static int[] read(IResourceManager resources, String name, int[] builtIn) {
        if (resources == null) return builtIn;
        ResourceLocation location = new ResourceLocation("modularnuclear", "textures/gui/scales/" + name + ".png");
        BufferedImage image;
        try (InputStream in = resources.getResource(location)
            .getInputStream()) {
            image = ImageIO.read(in);
        } catch (FileNotFoundException noPackHasOne) {
            return builtIn;
        } catch (IOException swallowed) {
            Swallowed.note(swallowed);
            return builtIn;
        }
        if (image == null) return builtIn;
        int[] scale = new int[STEPS];
        for (int i = 0; i < STEPS; i++) {
            scale[i] = image.getRGB(Math.min(image.getWidth() - 1, i * image.getWidth() / STEPS), 0);
        }
        return scale;
    }
}

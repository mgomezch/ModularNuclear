import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import javax.imageio.ImageIO;

/**
 * Procedural generator for Heavy Water, High-Pressure Distilled Water, and High-Pressure Heavy Water.
 * Applies HSB color-space modulation, fine ripple preservation, and opacity adjustments
 * to the base open-source distilled water fluid frames.
 *
 * Can be executed directly with Java 11+:
 *   java generate_water_fluids.java [baselineDir] [outputDir]
 */
public class generate_water_fluids {

    public static void main(String[] args) throws Exception {
        Path scriptDir = Paths.get(generate_water_fluids.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
        File baselineDir = (args.length > 0) ? new File(args[0]) : scriptDir.resolve("baseline").toFile();
        File outputDir = (args.length > 1) ? new File(args[1]) : scriptDir.resolve("../../src/main/resources/assets/modularnuclear/textures/blocks/fluids").normalize().toFile();

        File ic2Still = new File(baselineDir, "distilledwater_still.png");
        File ic2Flow = new File(baselineDir, "distilledwater_flow.png");

        if (!ic2Still.exists() || !ic2Flow.exists()) {
            throw new IllegalStateException("Baseline textures not found in: " + baselineDir.getAbsolutePath());
        }

        outputDir.mkdirs();
        System.out.println("Generating Water Fluid Textures:");

        // 1. Heavy Water: deep indigo-blue (264.0° hue, sat=0.90, bri=0.82)
        process(ic2Still, ic2Flow, baselineDir, outputDir, "heavywater", 264.0f, 0.90f, 0.82f, 1.0f);

        // 2. High-Pressure Distilled Water: supercritical bright cerulean-cyan (219.5° hue, sat=0.90, bri=0.65, alpha=1.25)
        process(ic2Still, ic2Flow, baselineDir, outputDir, "highpressuredistilledwater", 219.5f, 0.90f, 0.65f, 1.25f);

        // 3. High-Pressure Heavy Water: supercritical dense midnight violet (263.5° hue, sat=0.92, bri=0.48, alpha=1.25)
        process(ic2Still, ic2Flow, baselineDir, outputDir, "highpressureheavywater", 263.5f, 0.92f, 0.48f, 1.25f);
    }

    static void process(File stillIn, File flowIn, File baselineDir, File outDir, String fluidName,
                        float targetHueDeg, float targetSat, float briMult, float alphaMult) throws Exception {
        BufferedImage still = ImageIO.read(stillIn);
        BufferedImage stillOut = transform(still, targetHueDeg, targetSat, briMult, alphaMult);
        File stillFile = new File(outDir, fluidName + "_still.png");
        ImageIO.write(stillOut, "PNG", stillFile);
        System.out.println("  [✓] Wrote " + stillFile.getName());

        BufferedImage flow = ImageIO.read(flowIn);
        BufferedImage flowOut = transform(flow, targetHueDeg, targetSat, briMult, alphaMult);
        File flowFile = new File(outDir, fluidName + "_flow.png");
        ImageIO.write(flowOut, "PNG", flowFile);
        System.out.println("  [✓] Wrote " + flowFile.getName());

        File mcmetaStill = new File(baselineDir, "distilledwater_still.png.mcmeta");
        File mcmetaFlow = new File(baselineDir, "distilledwater_flow.png.mcmeta");
        if (mcmetaStill.exists()) {
            Files.copy(mcmetaStill.toPath(), new File(outDir, fluidName + "_still.png.mcmeta").toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        if (mcmetaFlow.exists()) {
            Files.copy(mcmetaFlow.toPath(), new File(outDir, fluidName + "_flow.png.mcmeta").toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static BufferedImage transform(BufferedImage src, float targetHueDeg, float targetSat, float briMult, float alphaMult) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        float[] hsb = new float[3];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                if (a == 0) {
                    dst.setRGB(x, y, 0);
                    continue;
                }
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;

                Color.RGBtoHSB(r, g, b, hsb);

                // Preserve fine ripple luminance variations from base water texture
                float bri = Math.min(1.0f, hsb[2] * briMult);
                // Modulate saturation slightly with surface ripple
                float sat = Math.min(1.0f, targetSat * (0.85f + 0.15f * hsb[1]));
                float hue = targetHueDeg / 360.0f;

                int rgb = Color.HSBtoRGB(hue, sat, bri) & 0x00FFFFFF;
                int newAlpha = Math.min(255, Math.max(0, Math.round(a * alphaMult)));
                dst.setRGB(x, y, (newAlpha << 24) | rgb);
            }
        }
        return dst;
    }
}

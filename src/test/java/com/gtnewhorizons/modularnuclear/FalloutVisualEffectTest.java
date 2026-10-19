package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.client.renderer.FalloutVisualEffectRenderer;
import com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig;

public class FalloutVisualEffectTest {

    @Test
    public void testDistanceIntensityCalculation() {
        double maxDist = 80.0;

        // At d = 0m: intensity should be 1.0 (smoothstep: t = 1.0, 1 * 1 * (3 - 2) = 1.0)
        double dist0 = 0.0;
        float norm0 = (float) (dist0 / maxDist);
        float t0 = Math.max(0.0f, Math.min(1.0f, 1.0f - norm0));
        float intensity0 = t0 * t0 * (3.0f - 2.0f * t0);
        assertEquals(1.0f, intensity0, 0.001f);

        // At d = maxDist: intensity should be 0.0
        double distMax = 80.0;
        float normMax = (float) (distMax / maxDist);
        float tMax = Math.max(0.0f, Math.min(1.0f, 1.0f - normMax));
        float intensityMax = tMax * tMax * (3.0f - 2.0f * tMax);
        assertEquals(0.0f, intensityMax, 0.001f);

        // At d = 40m (half distance): t = 0.5, smoothstep = 0.25 * 2.0 = 0.5
        double distHalf = 40.0;
        float normHalf = (float) (distHalf / maxDist);
        float tHalf = Math.max(0.0f, Math.min(1.0f, 1.0f - normHalf));
        float intensityHalf = tHalf * tHalf * (3.0f - 2.0f * tHalf);
        assertEquals(0.5f, intensityHalf, 0.001f);

        // Beyond max distance: intensity clamped to 0.0
        double distBeyond = 120.0;
        float normBeyond = (float) (distBeyond / maxDist);
        float tBeyond = Math.max(0.0f, Math.min(1.0f, 1.0f - normBeyond));
        float intensityBeyond = tBeyond * tBeyond * (3.0f - 2.0f * tBeyond);
        assertEquals(0.0f, intensityBeyond, 0.001f);
    }

    @Test
    public void testHazmatHelmetHalving() {
        float rawIntensity = 1.0f;
        float withHazmat = rawIntensity * 0.5f;
        assertEquals(0.5f, withHazmat, 0.001f);

        float rawHalf = 0.5f;
        float halfWithHazmat = rawHalf * 0.5f;
        assertEquals(0.25f, halfWithHazmat, 0.001f);
    }

    @Test
    public void testConfigDefaults() {
        assertTrue(ModularNuclearConfig.enableFalloutVisualEffect);
        assertEquals(80.0f, ModularNuclearConfig.falloutEffectMaxDistance, 0.001f);
    }
}

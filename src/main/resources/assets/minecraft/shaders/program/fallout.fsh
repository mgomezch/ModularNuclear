#version 120

uniform sampler2D DiffuseSampler;

varying vec2 texCoord;
varying vec2 oneTexel;

uniform vec2 InSize;
uniform vec2 OutSize;
uniform float Time;
uniform float Intensity;
uniform float Seed;

// Fast pseudorandom hash without trigonometric functions
float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    vec4 scene = texture2D(DiffuseSampler, texCoord);
    if (Intensity <= 0.001) {
        gl_FragColor = scene;
        return;
    }

    // 1. Pixelated Black-and-White Static (Old CRT TV snow)
    // 3.0 pixel blocks give an authentic coarse analog television grain
    vec2 block = floor(gl_FragCoord.xy / 3.0);
    float n1 = hash(block + vec2(Seed * 13.37, fract(Time * 41.17) * 97.0));
    float n2 = hash(block * 1.5 + vec2(Seed * 29.71, fract(Time * 19.83) * 53.0));
    float rawNoise = mix(n1, n2, 0.3);
    // Sharp high-contrast static specks blended with softer grain
    float staticVal = (rawNoise > 0.5) ? 1.0 : 0.0;
    staticVal = mix(rawNoise, staticVal, 0.65);

    // 2. De-saturation
    // Standard BT.601 luminance weights
    float luma = dot(scene.rgb, vec3(0.299, 0.587, 0.114));
    float desatAmount = clamp(Intensity * 0.85, 0.0, 0.95);
    vec3 col = mix(scene.rgb, vec3(luma), desatAmount);

    // 3. Blend Static over Viewport
    float staticMix = clamp(Intensity * 0.40, 0.0, 0.70);
    col = mix(col, vec3(staticVal), staticMix);

    // 4. Darkening & Vignette
    // Global exposure drop
    float darkFactor = 1.0 - (Intensity * 0.30);
    // Edge vignette narrowing viewport
    vec2 uv = texCoord - 0.5;
    float distSq = dot(uv, uv);
    float vignette = clamp(1.0 - distSq * (1.0 + 1.5 * Intensity), 0.0, 1.0);
    col *= darkFactor * mix(1.0, vignette, clamp(Intensity * 0.9, 0.0, 1.0));

    gl_FragColor = vec4(clamp(col, 0.0, 1.0), scene.a);
}

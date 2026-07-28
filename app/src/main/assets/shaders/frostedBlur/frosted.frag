#version 300 es
precision highp float;
// hashU needs 32-bit ints; ES 3.00 defaults to mediump (only 16 bits guaranteed).
precision highp int;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTextureSharp;
uniform sampler2D uTextureBlur;

uniform float uAspectRatio;

uniform float uBlurStrength;
uniform float uDimLevel;
uniform float uEnableNoise;
uniform float uNoiseScale;
uniform float uNoiseStrength;

// App-drawer / recents blur, driven by wallpaper visibility. 0 = in view, 1 = hidden.
uniform float uDrawerBlur;

// Bit-mixing hash, replacing fract(sin(dot(...))) -- that idiom collapses to a repeating pattern
// at the coordinate magnitudes this grain grid produces (see #85).
// uint overflow is defined wrapping in GLSL ES.
uint hashU(uvec2 p) {
    uint h = p.x * 73856093u ^ p.y * 19349663u;
    h ^= h >> 13;
    h *= 0x85ebca6bu;
    h ^= h >> 16;
    return h;
}

float random(vec2 co) {
    return float(hashU(uvec2(co)) & 0xFFFFFFu) / float(0x1000000u);
}

void main() {
    float t = clamp(uBlurStrength, 0.0, 1.0);

    vec3 sharp = textureLod(uTextureSharp, vTexCoord, t * 4.0).rgb;

    vec3 frosted = texture(uTextureBlur, vTexCoord).rgb;

    vec3 finalColor = mix(sharp, frosted, t);

    finalColor = mix(finalColor, vec3(0.0), uDimLevel * t);

    if (uEnableNoise > 0.5) {
        vec2 noiseUV = vTexCoord;
        noiseUV.x *= uAspectRatio;
        vec2 grainUV = floor(noiseUV * uNoiseScale);
        float noise = random(grainUV);
        float noiseVisibility = smoothstep(0.4, 1.0, t);
        finalColor += vec3(noise * uNoiseStrength * noiseVisibility);
    }

    // App-drawer / recents: reverse Frosted sets this to 1 when out of view, blending
    // toward the frosted image so the drawer shows a blur. In view -> 0 -> sharp.
    finalColor = mix(finalColor, frosted, clamp(uDrawerBlur, 0.0, 1.0));

    fragColor = vec4(finalColor, 1.0);
}

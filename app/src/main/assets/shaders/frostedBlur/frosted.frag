#version 300 es
precision highp float;

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

float random(vec2 co) {
    return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
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
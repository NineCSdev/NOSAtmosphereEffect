#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

// Canvas transition: a restrained pale sketch on black crossfades with the
// original fitted wallpaper. uReverse swaps the lock and home states.

uniform sampler2D uTextureSharp;
uniform sampler2D uLineTex;

uniform float uBlurStrength;
uniform float uReverse;
uniform float uDimLevel;
uniform float uLineWidth;
uniform float uLineMax;

void main() {
    vec2 uv = vTexCoord;
    vec3 sharp = texture(uTextureSharp, uv).rgb;

    float progress = clamp(uBlurStrength, 0.0, 1.0);
    float imageAmount = mix(progress, 1.0 - progress, uReverse);

    float lineDistance = texture(uLineTex, uv).r * uLineMax;
    float width = mix(uLineWidth, uLineWidth * 0.78, imageAmount);
    float ink = 1.0 - smoothstep(width * 0.45, width * 0.45 + 1.1, lineDistance);

    float luma = dot(sharp, vec3(0.2126, 0.7152, 0.0722));
    vec3 inkColor = mix(vec3(0.76), vec3(0.96), smoothstep(0.12, 0.88, luma));
    vec3 sketch = inkColor * ink;

    float blend = smoothstep(0.02, 0.98, imageAmount);
    vec3 color = mix(sketch, sharp, blend);
    color = mix(color, vec3(0.0), uDimLevel * (1.0 - imageAmount));

    fragColor = vec4(color, 1.0);
}

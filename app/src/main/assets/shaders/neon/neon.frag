#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

// ----------------------------------------------------------------------------
// Canvas sketch transition.
//
// Sketch state: black canvas with thin line art extracted from the wallpaper.
// Image state: the original fitted wallpaper. uReverse swaps which state belongs
// to the lock screen so the same shader handles sketch -> image and image ->
// sketch transitions.
// ----------------------------------------------------------------------------

uniform sampler2D uTextureSharp;
uniform sampler2D uLineTex;       // R = texels to the nearest outline, over uLineMax

uniform float uBlurStrength;      // 0.0 = lock state, 1.0 = home state
uniform float uReverse;
uniform float uDimLevel;

uniform float uLineWidth;         // ink stroke width, in texels
uniform float uLineMax;

void main() {
    vec2 uv = vTexCoord;
    vec3 sharp = texture(uTextureSharp, uv).rgb;

    float p = clamp(uBlurStrength, 0.0, 1.0);
    float t = mix(p, 1.0 - p, uReverse);

    // Distance-based ink keeps every traced contour the same width, independent
    // of source contrast. The stroke narrows slightly as the image settles.
    float ld = texture(uLineTex, uv).r * uLineMax;
    float width = mix(uLineWidth, uLineWidth * 0.72, smoothstep(0.12, 0.7, t));
    float core = 1.0 - smoothstep(width * 0.5, width * 0.5 + 0.85, ld);
    float hairline = 1.0 - smoothstep(0.2, 1.65, ld);
    float ink = max(core, hairline * 0.42);

    // OnePlus Canvas AOD reads as clean pale line art rather than a tinted or
    // luminous edge treatment. Keeping a little source luminance makes faces and
    // fabric contours feel less flat while staying flat and non-luminous.
    float luma = dot(sharp, vec3(0.2126, 0.7152, 0.0722));
    vec3 inkColor = mix(vec3(0.66), vec3(1.0), smoothstep(0.08, 0.82, luma));
    vec3 sketch = inkColor * ink;

    float imageAlpha = smoothstep(0.02, 0.98, t);
    float inkAlpha = 1.0 - smoothstep(0.58, 1.0, t);
    vec3 col = sharp * imageAlpha + sketch * inkAlpha;

    col = mix(col, vec3(0.0), uDimLevel * (1.0 - t));

    fragColor = vec4(col, 1.0);
}

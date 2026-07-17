#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

// ----------------------------------------------------------------------------
// Neon Blueprint - bake pass 4 (once per wallpaper load).
//
// Seeds the 1/8-scale glow field from the full-resolution lines, so the halo and
// the colour bleed travel outward from exactly the outlines that get drawn. They
// used to come from a second, coarser Sobel of their own, which meant the glow
// only approximately agreed with the lines it was supposedly glowing from.
//
// One field texel covers DIV x DIV wallpaper texels; seed it if ANY of them
// carries a line. Sampling the middle of the block instead would miss any line
// that happens to pass between sample points, which is most of them - and at 1/8
// scale the whole block search costs a rounding error.
// ----------------------------------------------------------------------------

const int DIV = 8;            // must match NeonRenderer.FIELD_DIV

uniform sampler2D uLineDist;  // full-res texels-to-nearest-line, over uMaxDist
uniform vec2  uSrcStep;       // one WALLPAPER texel, in uv
uniform float uMaxDist;       // what uLineDist is normalised by, in texels

void main() {
    float m = 1.0;
    for (int y = 0; y < DIV; y++) {
        for (int x = 0; x < DIV; x++) {
            vec2 o = (vec2(float(x), float(y)) - float(DIV - 1) * 0.5) * uSrcStep;
            m = min(m, textureLod(uLineDist, vTexCoord + o, 0.0).r);
        }
    }
    // 0.0 = "an outline lives in this block", 1.0 = "nothing here".
    fragColor = vec4(step(0.5, m * uMaxDist), 0.0, 0.0, 1.0);
}

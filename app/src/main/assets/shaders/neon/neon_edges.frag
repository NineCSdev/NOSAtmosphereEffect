#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

// ----------------------------------------------------------------------------
// Neon Blueprint - bake pass 1 (runs once per wallpaper load, not per frame).
//
// Finds the outlines. Runs at full wallpaper resolution and writes a crest map:
//   1.0 = definitely an outline      0.5 = maybe    0.0 = no
// which neon_hyst.frag then resolves.
//
// Two things a plain Sobel gets wrong, both of which this pass fixes, and both
// of which are only affordable because the wallpaper never changes - the lines
// are baked once and read back with a single tap per frame:
//
// 1. It measures brightness. Any boundary whose two sides happen to share a
//    brightness is invisible to it - red against green, most skies against most
//    foliage - and those are exactly the outlines the eye reads as the subject.
//    The colour structure tensor sees the change in the full RGB vector instead.
//
// 2. It answers "how fast is colour changing here", which is large across the
//    WHOLE width of a transition, not just at its centre. So a soft edge comes
//    out as a fat band and a hard edge as a thin one, and the line width ends up
//    reporting how out-of-focus the subject was. Suppressing everything that is
//    not a ridge crest leaves one clean line down the middle of every
//    transition, however soft it was to begin with.
// ----------------------------------------------------------------------------

uniform sampler2D uTextureSharp;
uniform vec2  uStep;         // one wallpaper texel, in uv
uniform float uLod;          // gentle pre-blur: sensor grain makes crests of its own
uniform float uThreshold;    // magnitude a crest must beat to count as an outline
uniform float uWeakRatio;    // crests above uThreshold * this are "maybe" (see neon_hyst.frag)

// Fractional mip: grain, fabric weave and foliage are all "edges" by any local
// measure, and without a pre-blur they crest just as readily as the silhouette
// does. The sensitivity slider drives this alongside the threshold, so turning it
// down asks for structure rather than merely fewer specks.
vec3 tap(vec2 uv) {
    return textureLod(uTextureSharp, uv, uLod).rgb;
}

// (magnitude, direction.x, direction.y) - direction points across the edge.
vec3 gradientAt(vec2 uv) {
    vec2 s = uStep;
    vec3 tl = tap(uv + vec2(-s.x, -s.y));
    vec3 tm = tap(uv + vec2( 0.0, -s.y));
    vec3 tr = tap(uv + vec2( s.x, -s.y));
    vec3 ml = tap(uv + vec2(-s.x,  0.0));
    vec3 mr = tap(uv + vec2( s.x,  0.0));
    vec3 bl = tap(uv + vec2(-s.x,  s.y));
    vec3 bm = tap(uv + vec2( 0.0,  s.y));
    vec3 br = tap(uv + vec2( s.x,  s.y));

    // Sobel per channel; 0.25 normalises a full step to 1.0 in that channel.
    vec3 gx = ((tr + 2.0 * mr + br) - (tl + 2.0 * ml + bl)) * 0.25;
    vec3 gy = ((bl + 2.0 * bm + br) - (tl + 2.0 * tm + tr)) * 0.25;

    // Di Zenzo structure tensor. Its dominant eigenvalue is the steepest squared
    // change available in any direction, over all three channels at once, and the
    // matching eigenvector is the direction that achieves it.
    float Jxx = dot(gx, gx);
    float Jyy = dot(gy, gy);
    float Jxy = dot(gx, gy);

    float disc = sqrt(max((Jxx - Jyy) * (Jxx - Jyy) + 4.0 * Jxy * Jxy, 0.0));
    float lam  = 0.5 * (Jxx + Jyy + disc);

    // 1/sqrt(3): a grey full-step edge lands on 1.0 exactly as it did under the
    // old luma Sobel, so the sensitivity slider keeps its calibration.
    float mag = sqrt(max(lam, 0.0)) * 0.57735027;

    // Principal axis of a symmetric 2x2. Sign is arbitrary, which is fine - the
    // crest test looks both ways.
    float th = 0.5 * atan(2.0 * Jxy, Jxx - Jyy);
    return vec3(mag, cos(th), sin(th));
}

void main() {
    vec3 g = gradientAt(vTexCoord);
    float m = g.r;

    // Is this the crest of the ridge, or just its flank? Step one texel each way
    // across the edge and check.
    vec2 o = normalize(g.gb + 1e-8) * uStep;
    float ma = gradientAt(vTexCoord + o).r;
    float mb = gradientAt(vTexCoord - o).r;

    // >= on one side and > on the other: where quantisation leaves two texels
    // tied, this keeps exactly one of them instead of both or neither.
    float crest = (m >= ma && m > mb) ? 1.0 : 0.0;
    float v = m * crest;

    float strong = step(uThreshold, v);
    float weak   = step(uThreshold * uWeakRatio, v) * (1.0 - strong);
    fragColor = vec4(strong + weak * 0.5, 0.0, 0.0, 1.0);
}

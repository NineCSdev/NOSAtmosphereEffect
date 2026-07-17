#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

// ----------------------------------------------------------------------------
// Neon Blueprint - edge-detection wake.
//
// Blueprint state: the screen is pitch black (nothing lit = nothing drawn on an
// OLED) except the outlines of whatever is in the wallpaper, drawn as thin white
// tubes with a soft halo around them.
//
// Transition: the tubes pulse, then let go - colour bleeds out of every line at
// once and crawls into the black until the untouched wallpaper is back. Every
// colour on screen is the wallpaper's own, arriving with the bleed; the
// blueprint itself is white, so there is nothing to tint what it becomes.
//
// The bleed front is a threshold sweeping across the pre-baked distance field
// (see neon_edt.frag), so it genuinely travels outward from the lines rather
// than cross-fading in place.
//
// The lines are baked (neon_edges.frag -> neon_hyst.frag -> neon_edt.frag), so
// all this pass does is read how far it is from one. That is a single tap for
// something that cannot change between frames, and it buys line-finding far too
// expensive to run per-frame - see neon_edges.frag.
//
// One shader serves both directions; uReverse decides which end of the
// animation is the lock screen. Run backwards, the pulse lands last: the
// outlines swallow the last of the colour and flare as they finish.
// ----------------------------------------------------------------------------

uniform sampler2D uTextureSharp;  // fitted wallpaper
uniform sampler2D uLineTex;       // R = texels to the nearest outline, over uLineMax
uniform sampler2D uFieldTex;      // R = distance to nearest outline, 0..1 (see neon_edt.frag)
uniform sampler2D uRankTex;       // 256x1 R8: uFieldTex distance -> share of screen nearer than it

uniform float uBlurStrength;      // house convention: 0.0 = lock state, 1.0 = home state
uniform float uReverse;           // 0.0 = Neon Blueprint, 1.0 = Neon Blueprint (Reverse)
uniform float uDimLevel;
uniform float uAspectRatio;

uniform float uLineWidth;         // tube width, in texels
uniform float uLineMax;           // what uLineTex is normalised by, in texels
uniform float uGlowFalloff;       // halo falloff over the distance field; bigger = tighter

// --- value noise (same shape as the Color Fill spilled-paint front) ----------
float hash(vec2 p) {
    p = fract(p * vec2(123.34, 345.45));
    p += dot(p, p + 34.345);
    return fract(p.x * p.y);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 4; i++) {
        v += amp * vnoise(p);
        p = p * 2.03 + 7.1;
        amp *= 0.5;
    }
    return v;
}

void main() {
    vec2 uv = vTexCoord;
    vec2 flippedUv = vec2(uv.x, 1.0 - uv.y);

    // --- the tube -----------------------------------------------------------
    // Distance, not gradient strength: every tube is the width it was asked for,
    // whatever the edge under it looked like, and the falloff antialiases itself
    // and rounds the ends for free.
    float ld = texture(uLineTex, uv).r * uLineMax;
    float hw = uLineWidth * 0.5;
    float line = 1.0 - smoothstep(hw - 0.5, hw + 0.5, ld);

    vec3 sharp = texture(uTextureSharp, uv).rgb;

    // --- animation state: 0.0 = blueprint, 1.0 = untouched wallpaper ---------
    float p = clamp(uBlurStrength, 0.0, 1.0);
    float t = mix(p, 1.0 - p, uReverse);

    float pulse = sin(clamp(t / 0.22, 0.0, 1.0) * 3.14159265);

    // --- blueprint composite ------------------------------------------------
    float d = texture(uFieldTex, flippedUv).r;
//    float glow = exp(-d * uGlowFalloff);
    vec3 blueprint = vec3(line * (1.35 + 1.1 * pulse));
//    vec3 blueprint = vec3(line * (1.35 + 1.1 * pulse) + glow * (0.55 + 0.6 * pulse));

    // --- the bleed ----------------------------------------------------------
    // Sweep the front over each pixel's RANK, not its distance: uRankTex maps a
    // distance to the share of the screen that sits nearer to an outline than it
    // does. Raw distance would let content dictate the pacing - distances pile up
    // near zero, so a front at constant speed does most of its visible work in
    // the first third and then coasts on a screen that already looks finished,
    // and rescaling by the image's own maximum fixes the axis but not that shape.
    // Over ranks, a front at constant speed fills area at a constant rate on any
    // wallpaper, because that is what a rank means.
    // (Sampled at texel centres; the LUT is 256 wide and filtered LINEAR.)
    float dnorm = texture(uRankTex, vec2(d * (255.0 / 256.0) + (0.5 / 256.0), 0.5)).r;

    // Break the front with noise so it stops being a clean offset curve of the
    // outline, and so a big empty sky - where the field flattens out - dissolves
    // rather than popping in one piece. In rank space this is a fixed +/-11% of
    // screen area whatever the image, rather than swamping a busy photo's field
    // and vanishing on a sparse one.
    //
    // Taper it to nothing at both ends of the rank. That keeps dn inside 0..1
    // exactly, so the front only has to clear the ends by its own antialiasing
    // instead of overshooting a range the noise widened by an unknown amount -
    // which used to cost the last sixth of the animation, finished but still
    // playing. It also reads better: colour leaves the tube cleanly and breaks up
    // as it travels, rather than fraying while still attached.
    float n  = fbm(uv * vec2(uAspectRatio, 1.0) * 5.0) - 0.5;
    float w  = 4.0 * dnorm * (1.0 - dnorm);
    float dn = clamp(dnorm + n * 0.22 * w, 0.0, 1.0);

    // Linear: rank space already carries the correction that a curve here used to
    // stand in for. The short hold lets the pulse land before colour moves.
    float bleed = clamp((t - 0.04) / 0.96, 0.0, 1.0);
    float front = bleed * 1.05 - 0.025;
    float aa    = 0.012 + 0.05 * bleed * (1.0 - bleed);
    float fill  = 1.0 - smoothstep(front - aa, front + aa, dn);

    // Over-bright rim riding the advancing front - the colour arrives hot and
    // settles. White, like the tubes it came out of. Zero at both ends, so the
    // resting states stay exact.
    float rim = (1.0 - smoothstep(0.0, aa * 1.5 + 0.02, abs(dn - front)))
              * bleed * (1.0 - bleed) * 4.0;

    vec3 col = mix(blueprint, sharp, fill);
    col += vec3(rim * 0.55);
    col *= 1.0 + 0.22 * pulse;

    // House convention: dimming rides the stylised state, so it fades out with
    // the neon and never touches the wallpaper itself.
    col = mix(col, vec3(0.0), uDimLevel * (1.0 - t));

    fragColor = vec4(col, 1.0);
}

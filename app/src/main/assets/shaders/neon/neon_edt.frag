#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

// ----------------------------------------------------------------------------
// Neon Blueprint - off-screen pass B (runs once per wallpaper load, not per frame).
//
// Turns the seed map from neon_edges.frag into a distance field: how far this
// pixel sits from the nearest outline. neon.frag then drives the whole effect
// off that one number - the glow falls off with it, and the unlock "bleed" is a
// threshold sweeping across it, so colour crawls outward from the lines at a
// constant speed instead of cross-fading in place.
//
// Two separable sweeps give the EXACT Euclidean distance, not an approximation:
//
//   pass 0 (horizontal)  r(x,y) = min |i|  over edges in row y
//   pass 1 (vertical)    d(x,y) = min sqrt( r(x, y+j)^2 + j^2 )  over j
//
// which is exact because, for any fixed row y+j, the edge minimising the
// hypotenuse is the one minimising the horizontal leg - i.e. r(x, y+j) itself.
//
// Both sweeps are windowed to uRadius texels; anything further reads as uMaxDist
// ("out of reach") and simply never wins the min. The field is 1/8 scale, so a
// 128-texel window still reaches ~1024 screen pixels.
// ----------------------------------------------------------------------------

uniform sampler2D uTexture;
uniform vec2  uStep;      // one field texel along the sweep axis, in uv
uniform float uRadius;    // window half-width, in field texels
uniform float uMaxDist;   // normalisation + "no hit" sentinel, in field texels
uniform float uPass;      // 0.0 = horizontal seed sweep, 1.0 = vertical sweep

void main() {
    float best = uMaxDist;

    if (uPass < 0.5) {
        // Nearest edge along this row.
        for (float i = -uRadius; i <= uRadius; i += 1.0) {
            float seed = texture(uTexture, vTexCoord + uStep * i).r;
            float isEdge = step(seed, 0.5);
            best = min(best, mix(uMaxDist, abs(i), isEdge));
        }
    } else {
        // Combine the rows above and below into a true 2D distance.
        for (float i = -uRadius; i <= uRadius; i += 1.0) {
            float rowDist = texture(uTexture, vTexCoord + uStep * i).r * uMaxDist;
            best = min(best, sqrt(rowDist * rowDist + i * i));
        }
    }

    fragColor = vec4(clamp(best / uMaxDist, 0.0, 1.0), 0.0, 0.0, 1.0);
}

#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

// ----------------------------------------------------------------------------
// Canvas sketch - outline cleanup pass.
//
// Decides what the "maybe" crests from neon_edges.frag really are.
//
// A single threshold has to be wrong somewhere: set it high and outlines break
// into dashes wherever the contrast dips for a texel or two, set it low and
// every scrap of sensor noise becomes a line. But real outlines are continuous,
// so the question is not "is this crest strong enough" - it is "does this crest
// join up with one that is". Each pass walks certainty one texel further along
// the contour; a few passes carry it across the dips and leave the strays behind.
// ----------------------------------------------------------------------------

uniform sampler2D uTexture;   // R: 1.0 = outline, 0.5 = maybe, 0.0 = no
uniform vec2  uStep;
uniform float uFinal;         // 1.0 on the last pass: emit distance-pass seed encoding

void main() {
    float c = textureLod(uTexture, vTexCoord, 0.0).r;
    float v = c;

    // Join a one-texel break only when a contour reaches it from opposite
    // directions. This repairs dashed walls and facial outlines without
    // growing isolated texture in every direction.
    if (c <= 0.25) {
        float left = textureLod(uTexture, vTexCoord + vec2(-uStep.x, 0.0), 0.0).r;
        float right = textureLod(uTexture, vTexCoord + vec2(uStep.x, 0.0), 0.0).r;
        float up = textureLod(uTexture, vTexCoord + vec2(0.0, -uStep.y), 0.0).r;
        float down = textureLod(uTexture, vTexCoord + vec2(0.0, uStep.y), 0.0).r;
        float diagonalA = min(
            textureLod(uTexture, vTexCoord - uStep, 0.0).r,
            textureLod(uTexture, vTexCoord + uStep, 0.0).r
        );
        float diagonalB = min(
            textureLod(uTexture, vTexCoord + vec2(-uStep.x, uStep.y), 0.0).r,
            textureLod(uTexture, vTexCoord + vec2(uStep.x, -uStep.y), 0.0).r
        );
        float bridge = max(max(min(left, right), min(up, down)), max(diagonalA, diagonalB));
        if (bridge > 0.75) {
            v = 1.0;
        } else if (bridge > 0.25) {
            v = 0.5;
        }
    }

    if (v > 0.25 && v < 0.75) {
        float m = 0.0;
        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                m = max(m, textureLod(uTexture, vTexCoord + vec2(float(x), float(y)) * uStep, 0.0).r);
            }
        }
        v = (m > 0.75) ? 1.0 : 0.5;
    }

    // Last pass: anything still unproven never reached a real outline, so it was
    // noise - drop it. Then re-encode for neon_edt.frag, which reads
    // 0.0 = "an outline lives here", 1.0 = "nothing here".
    float edge = step(0.75, v);
    fragColor = vec4(mix(v, 1.0 - edge, uFinal), 0.0, 0.0, 1.0);
}

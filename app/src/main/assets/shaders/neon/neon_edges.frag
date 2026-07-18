#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

// Canvas contour bake. The subject mask supplies one clean silhouette. Color
// contours are detected at the original two scales, then their position and
// direction are averaged along the line. Detail stays present, but nearby
// broken or zigzag samples converge on one calmer contour.

uniform sampler2D uTextureSharp;
uniform sampler2D uSubjectMask;
uniform vec2 uStep;
uniform vec2 uMaskStep;
uniform float uHasSubject;
uniform float uLod;
uniform float uThreshold;
uniform float uWeakRatio;

vec3 tap(vec2 uv, float lod) {
    return textureLod(uTextureSharp, clamp(uv, vec2(0.0), vec2(1.0)), lod).rgb;
}

// Returns (magnitude, direction.x, direction.y). The color structure tensor
// sees boundaries that a luma-only Sobel would miss.
vec3 gradientAt(vec2 uv, float lod) {
    vec2 s = uStep;
    vec3 tl = tap(uv + vec2(-s.x, -s.y), lod);
    vec3 tm = tap(uv + vec2( 0.0, -s.y), lod);
    vec3 tr = tap(uv + vec2( s.x, -s.y), lod);
    vec3 ml = tap(uv + vec2(-s.x,  0.0), lod);
    vec3 mr = tap(uv + vec2( s.x,  0.0), lod);
    vec3 bl = tap(uv + vec2(-s.x,  s.y), lod);
    vec3 bm = tap(uv + vec2( 0.0,  s.y), lod);
    vec3 br = tap(uv + vec2( s.x,  s.y), lod);

    vec3 gx = ((tr + 2.0 * mr + br) - (tl + 2.0 * ml + bl)) * 0.25;
    vec3 gy = ((bl + 2.0 * bm + br) - (tl + 2.0 * tm + tr)) * 0.25;

    float jxx = dot(gx, gx);
    float jyy = dot(gy, gy);
    float jxy = dot(gx, gy);
    float disc = sqrt(max((jxx - jyy) * (jxx - jyy) + 4.0 * jxy * jxy, 0.0));
    float lambda = 0.5 * (jxx + jyy + disc);
    float magnitude = sqrt(max(lambda, 0.0)) * 0.83715789;
    float theta = 0.5 * atan(2.0 * jxy, jxx - jyy);
    return vec3(magnitude, cos(theta), sin(theta));
}

float maskAt(vec2 uv) {
    return textureLod(uSubjectMask, clamp(uv, vec2(0.0), vec2(1.0)), 0.0).r;
}

float smoothMaskAt(vec2 uv) {
    vec2 s = uMaskStep * 1.6;
    float value = maskAt(uv) * 4.0;
    value += maskAt(uv + vec2( s.x, 0.0));
    value += maskAt(uv + vec2(-s.x, 0.0));
    value += maskAt(uv + vec2(0.0,  s.y));
    value += maskAt(uv + vec2(0.0, -s.y));
    return value * 0.125;
}

float subjectSilhouette(float center) {
    vec2 s = uMaskStep * 1.8;
    float lo = center;
    float hi = center;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            float value = maskAt(vTexCoord + vec2(float(x), float(y)) * s);
            lo = min(lo, value);
            hi = max(hi, value);
        }
    }

    float crossesBoundary = step(lo, 0.5) * step(0.5, hi);
    float centered = 1.0 - smoothstep(0.06, 0.26, abs(center - 0.5));
    return crossesBoundary * centered;
}

vec2 alignDirection(vec2 candidate, vec2 referenceDirection) {
    return candidate * ((dot(candidate, referenceDirection) < 0.0) ? -1.0 : 1.0);
}

float tangentAverage(vec2 uv, vec2 tangent, float lod) {
    vec2 offset = tangent * uStep * 3.5;
    float before = gradientAt(uv - offset, lod).r;
    float center = gradientAt(uv, lod).r;
    float after = gradientAt(uv + offset, lod).r;
    return (before + center * 2.0 + after) * 0.25;
}

void main() {
    float interior = 1.0;
    float silhouette = 0.0;
    if (uHasSubject > 0.5) {
        float mask = smoothMaskAt(vTexCoord);
        interior = smoothstep(0.60, 0.82, mask);
        silhouette = subjectSilhouette(mask);
    }

    vec3 fine = gradientAt(vTexCoord, uLod);
    vec3 broad = gradientAt(vTexCoord, uLod + 0.85);
    vec2 initialDirection = normalize(broad.gb + vec2(1e-8));
    vec2 initialTangent = vec2(-initialDirection.y, initialDirection.x);
    vec2 guideOffset = initialTangent * uStep * 3.5;
    vec3 guideBefore = gradientAt(vTexCoord - guideOffset, uLod + 0.85);
    vec3 guideAfter = gradientAt(vTexCoord + guideOffset, uLod + 0.85);

    // Average orientation with the neighboring pieces of the same contour.
    // The tensor direction has an arbitrary sign, so align both samples first.
    vec2 beforeDirection = alignDirection(
        normalize(guideBefore.gb + vec2(1e-8)),
        initialDirection
    );
    vec2 afterDirection = alignDirection(
        normalize(guideAfter.gb + vec2(1e-8)),
        initialDirection
    );
    float beforeWeight = smoothstep(uThreshold * 0.20, uThreshold, guideBefore.r);
    float afterWeight = smoothstep(uThreshold * 0.20, uThreshold, guideAfter.r);
    vec2 direction = normalize(
        initialDirection * 2.0 +
        beforeDirection * beforeWeight +
        afterDirection * afterWeight
    );
    vec2 tangent = vec2(-direction.y, direction.x);

    // Average the edge position along that direction. Non-maximum suppression
    // then chooses the center of the averaged ridge, instead of following every
    // one-pixel kink in the source image.
    float fineAverage = max(
        tangentAverage(vTexCoord, tangent, uLod),
        fine.r * 0.68
    );
    float broadAverage = max(
        tangentAverage(vTexCoord, tangent, uLod + 0.85),
        broad.r * 0.68
    );
    float magnitude = min(fineAverage, broadAverage * 1.4);
    vec2 normalOffset = direction * uStep;
    float before = tangentAverage(vTexCoord - normalOffset, tangent, uLod + 0.85);
    float after = tangentAverage(vTexCoord + normalOffset, tangent, uLod + 0.85);
    float crest = (broadAverage >= before && broadAverage > after) ? 1.0 : 0.0;
    float contour = magnitude * crest * interior;

    float strong = max(step(uThreshold, contour), step(0.35, silhouette));
    float weak = step(uThreshold * uWeakRatio, contour) * (1.0 - strong);
    fragColor = vec4(strong + weak * 0.5, 0.0, 0.0, 1.0);
}

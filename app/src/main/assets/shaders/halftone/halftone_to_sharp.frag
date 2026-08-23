#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTextureSharp;
uniform sampler2D uSubjectMask;
uniform float uAspectRatio;
uniform float uBlurStrength;
uniform float uDimLevel;
uniform float uDotSize;
uniform float uGrayscale;
uniform float uBackgroundOnly;
uniform float uHasSubject;

float random(vec2 co) {
    return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
}

mat2 rotate2d(float angle) {
    float s = sin(angle);
    float c = cos(angle);
    return mat2(c, -s, s, c);
}

float halftoneChannel(vec2 uv, float angle, float value, vec2 texSize, float dotSize) {
    vec2 centerUV = uv - 0.5;
    centerUV.x *= uAspectRatio;
    vec2 rotUV = rotate2d(angle) * centerUV;

    vec2 gridUV = rotUV * texSize.y / dotSize;
    vec2 localUV = fract(gridUV) - 0.5;

    float dist = length(localUV);
    float radius = sqrt(value) * 0.75;
    float edge = max(0.05, 1.0 / dotSize);

    return smoothstep(radius + edge, radius - edge, dist);
}

float foregroundProtection(vec2 uv) {
    if (uBackgroundOnly <= 0.5) return 0.0;
    // No subject mask: nothing is known to protect, so don't revert the
    // whole frame back to the untouched image.
    if (uHasSubject <= 0.5) return 0.0;

    vec2 stepSize = 2.0 / vec2(textureSize(uSubjectMask, 0));
    float mask = texture(uSubjectMask, uv).r;
    mask = max(mask, texture(uSubjectMask, clamp(uv + vec2(stepSize.x, 0.0), 0.0, 1.0)).r);
    mask = max(mask, texture(uSubjectMask, clamp(uv - vec2(stepSize.x, 0.0), 0.0, 1.0)).r);
    mask = max(mask, texture(uSubjectMask, clamp(uv + vec2(0.0, stepSize.y), 0.0, 1.0)).r);
    mask = max(mask, texture(uSubjectMask, clamp(uv - vec2(0.0, stepSize.y), 0.0, 1.0)).r);
    return smoothstep(0.30, 0.72, mask);
}

void main() {
    float t = clamp(uBlurStrength, 0.0, 1.0);
    vec3 sharp = texture(uTextureSharp, vTexCoord).rgb;
    vec2 texSize = vec2(textureSize(uTextureSharp, 0));

    vec3 halftoneOutput;

    if (uDotSize == 0.0) {
        if (uGrayscale > 0.5) {
            float luma = dot(sharp, vec3(0.299, 0.587, 0.114));
            halftoneOutput = vec3(luma);
        } else {
            halftoneOutput = sharp;
        }
    } else {
        if (uGrayscale > 0.5) {
            float luma = dot(sharp, vec3(0.299, 0.587, 0.114));
            float kDot = halftoneChannel(vTexCoord, radians(45.0), 1.0 - luma, texSize, uDotSize);
            halftoneOutput = vec3(1.0 - kDot);
        } else {
            vec3 cmy = 1.0 - sharp;
            float cDot = halftoneChannel(vTexCoord, radians(15.0), cmy.r, texSize, uDotSize);
            float mDot = halftoneChannel(vTexCoord, radians(75.0), cmy.g, texSize, uDotSize);
            float yDot = halftoneChannel(vTexCoord, radians(0.0), cmy.b, texSize, uDotSize);
            halftoneOutput = 1.0 - vec3(cDot, mDot, yDot);
        }
    }

    vec3 finalColor = mix(sharp, halftoneOutput, t);
    finalColor = mix(finalColor, vec3(0.0), uDimLevel * t);
    finalColor = mix(finalColor, sharp, foregroundProtection(vTexCoord));
    fragColor = vec4(finalColor, 1.0);
}

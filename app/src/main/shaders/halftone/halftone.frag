#version 450

layout(set = 0, binding = 0) uniform sampler2D wallpaperTexture;
layout(set = 0, binding = 1) uniform sampler2D subjectMask;

layout(location = 0) in vec2 vTexCoord;
layout(location = 0) out vec4 fragColor;

layout(push_constant) uniform HalftoneParams {
    vec4 render;
    vec4 controls;
    vec4 scroll;
} params;

mat2 rotate2d(float angle) {
    float sine = sin(angle);
    float cosine = cos(angle);
    return mat2(cosine, -sine, sine, cosine);
}

float halftoneChannel(
    vec2 uv,
    float angle,
    float value,
    vec2 textureDimensions,
    float dotSize
) {
    vec2 centered = uv - 0.5;
    centered.x *= params.render.z;
    vec2 rotated = rotate2d(angle) * centered;
    vec2 grid = rotated * textureDimensions.y / dotSize;
    vec2 local = fract(grid) - 0.5;

    float distanceFromCenter = length(local);
    float radius = sqrt(value) * 0.75;
    float edge = max(0.05, 1.0 / dotSize);
    return smoothstep(
        radius + edge,
        radius - edge,
        distanceFromCenter
    );
}

float foregroundProtection(vec2 uv) {
    if (params.controls.z <= 0.5) return 0.0;
    if (params.controls.w <= 0.5) return 1.0;

    vec2 stepSize = 2.0 / vec2(textureSize(subjectMask, 0));
    float mask = texture(subjectMask, uv).r;
    mask = max(
        mask,
        texture(subjectMask, clamp(uv + vec2(stepSize.x, 0.0), 0.0, 1.0)).r
    );
    mask = max(
        mask,
        texture(subjectMask, clamp(uv - vec2(stepSize.x, 0.0), 0.0, 1.0)).r
    );
    mask = max(
        mask,
        texture(subjectMask, clamp(uv + vec2(0.0, stepSize.y), 0.0, 1.0)).r
    );
    mask = max(
        mask,
        texture(subjectMask, clamp(uv - vec2(0.0, stepSize.y), 0.0, 1.0)).r
    );
    return smoothstep(0.30, 0.72, mask);
}

void main() {
    bool reverse = params.render.w > 0.5;
    float progress = clamp(params.render.x, 0.0, 1.0);
    float effectStrength = reverse ? 1.0 - progress : progress;
    float dotSize = params.controls.x;
    bool grayscale = params.controls.y > 0.5;

    vec3 sharp = texture(wallpaperTexture, vTexCoord).rgb;
    vec2 textureDimensions = vec2(textureSize(wallpaperTexture, 0));
    vec3 halftoneOutput;

    bool dotsDisabled = reverse ? dotSize == 0.0 : dotSize < 0.1;
    if (dotsDisabled) {
        if (grayscale) {
            float luma = dot(sharp, vec3(0.299, 0.587, 0.114));
            halftoneOutput = vec3(luma);
        } else {
            halftoneOutput = sharp;
        }
    } else if (grayscale) {
        float luma = dot(sharp, vec3(0.299, 0.587, 0.114));
        float black = halftoneChannel(
            vTexCoord,
            radians(45.0),
            1.0 - luma,
            textureDimensions,
            dotSize
        );
        halftoneOutput = vec3(1.0 - black);
    } else {
        vec3 cmy = 1.0 - sharp;
        float cyan = halftoneChannel(
            vTexCoord,
            radians(15.0),
            cmy.r,
            textureDimensions,
            dotSize
        );
        float magenta = halftoneChannel(
            vTexCoord,
            radians(75.0),
            cmy.g,
            textureDimensions,
            dotSize
        );
        float yellow = halftoneChannel(
            vTexCoord,
            radians(0.0),
            cmy.b,
            textureDimensions,
            dotSize
        );
        halftoneOutput = 1.0 - vec3(cyan, magenta, yellow);
    }

    vec3 finalColor = mix(sharp, halftoneOutput, effectStrength);
    finalColor = mix(
        finalColor,
        vec3(0.0),
        params.render.y * effectStrength
    );
    finalColor = mix(
        finalColor,
        sharp,
        foregroundProtection(vTexCoord)
    );
    fragColor = vec4(finalColor, 1.0);
}

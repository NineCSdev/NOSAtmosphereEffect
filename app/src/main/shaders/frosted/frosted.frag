#version 450

layout(set = 0, binding = 0) uniform sampler2D sharpTexture;
layout(set = 0, binding = 1) uniform sampler2D blurredTexture;

layout(location = 0) in vec2 vTexCoord;
layout(location = 0) out vec4 fragColor;

layout(push_constant) uniform FrostedParams {
    vec4 render;
    vec4 noise;
    vec4 scroll;
} params;

float random(vec2 coordinate) {
    return fract(
        sin(dot(coordinate, vec2(12.9898, 78.233))) *
        43758.5453
    );
}

void main() {
    float progress = clamp(params.render.x, 0.0, 1.0);
    vec3 sharp = textureLod(
        sharpTexture,
        vTexCoord,
        progress * 4.0
    ).rgb;
    vec3 frosted = texture(blurredTexture, vTexCoord).rgb;

    vec3 finalColor = mix(sharp, frosted, progress);
    finalColor = mix(
        finalColor,
        vec3(0.0),
        params.render.y * progress
    );

    if (params.noise.x > 0.5) {
        vec2 noiseCoordinate = vTexCoord;
        noiseCoordinate.x *= params.render.z;
        vec2 grain = floor(noiseCoordinate * params.noise.y);
        float noiseValue = random(grain);
        float visibility = smoothstep(0.4, 1.0, progress);
        finalColor += vec3(
            noiseValue *
            params.noise.z *
            visibility
        );
    }

    finalColor = mix(
        finalColor,
        frosted,
        clamp(params.render.w, 0.0, 1.0)
    );
    fragColor = vec4(finalColor, 1.0);
}

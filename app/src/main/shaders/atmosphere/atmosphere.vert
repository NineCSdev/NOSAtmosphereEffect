#version 450

layout(location = 0) out vec2 vTexCoord;
layout(location = 1) out vec2 vEffectCoord;

layout(std140, set = 0, binding = 3) uniform AtmosphereParams {
    vec4 render;
    vec4 noise;
    vec4 glass;
    vec4 viewport;
    vec4 misc;
    ivec4 blobMeta;
    vec4 blobColors[16];
    vec4 blobPositionsAndSizes[16];
} params;

void main() {
    const vec2 positions[3] = vec2[3](
        vec2(-1.0, -1.0),
        vec2(3.0, -1.0),
        vec2(-1.0, 3.0)
    );
    const vec2 coordinates[3] = vec2[3](
        vec2(0.0, 0.0),
        vec2(2.0, 0.0),
        vec2(0.0, 2.0)
    );

    gl_Position = vec4(positions[gl_VertexIndex], 0.0, 1.0);
    vec2 coordinate = coordinates[gl_VertexIndex];
    float windowX = max(params.viewport.y, 0.001);
    coordinate.x =
        params.viewport.x * (1.0 - windowX) +
        coordinate.x * windowX;
    vTexCoord = coordinate;
    vEffectCoord = coordinates[gl_VertexIndex];
}

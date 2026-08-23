#version 450

layout(location = 0) out vec2 vTexCoord;

layout(push_constant) uniform FrostedParams {
    vec4 render;
    vec4 noise;
    vec4 scroll;
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
    float windowWidth = max(params.scroll.y, 0.001);
    vec2 coordinate = coordinates[gl_VertexIndex];
    coordinate.x = params.scroll.x * (1.0 - windowWidth) +
        coordinate.x * windowWidth;
    vTexCoord = coordinate;
}

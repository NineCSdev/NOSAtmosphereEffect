#version 450

layout(set = 0, binding = 0) uniform sampler2D sharpTexture;
layout(set = 0, binding = 1) uniform sampler2D lineTexture;

layout(location = 0) in vec2 vTexCoord;
layout(location = 0) out vec4 fragColor;

layout(push_constant) uniform CanvasParams {
    vec4 render;
    vec4 canvas;
} params;

void main() {
    vec3 sharp = texture(sharpTexture, vTexCoord).rgb;
    float progress = clamp(params.render.x, 0.0, 1.0);
    float reverse = step(0.5, params.render.w);
    float imageAmount = mix(progress, 1.0 - progress, reverse);

    float lineMaximum = max(params.canvas.w, 1.0);
    float lineDistance = texture(lineTexture, vTexCoord).r * lineMaximum;
    float sourceWidth = max(float(textureSize(sharpTexture, 0).x), 1.0);
    float lineScale = float(textureSize(lineTexture, 0).x) / sourceWidth;
    float baseWidth = max(params.canvas.x * lineScale, 0.25);
    float width = mix(baseWidth, baseWidth * 0.78, imageAmount);
    float ink =
        1.0 -
        smoothstep(width * 0.45, width * 0.45 + 1.1, lineDistance);

    float luma = dot(sharp, vec3(0.2126, 0.7152, 0.0722));
    vec3 inkColor = mix(
        vec3(0.76),
        vec3(0.96),
        smoothstep(0.12, 0.88, luma)
    );
    vec3 sketch = inkColor * ink;

    float blend = smoothstep(0.02, 0.98, imageAmount);
    vec3 color = mix(sketch, sharp, blend);
    color = mix(
        color,
        vec3(0.0),
        clamp(params.render.y, 0.0, 1.0) * (1.0 - imageAmount)
    );
    fragColor = vec4(color, 1.0);
}

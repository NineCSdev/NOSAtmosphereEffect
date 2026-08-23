#version 300 es

in vec4 aPosition;
in vec2 aTexCoord;

out vec2 vTexCoord;
out vec2 vEffectCoord;

uniform float uScrollOffsetX;
uniform float uScrollWindowX;

void main() {
    gl_Position = aPosition;

    float windowX = uScrollWindowX <= 0.0 ? 1.0 : uScrollWindowX;
    float textureX = uScrollOffsetX * (1.0 - windowX) + aTexCoord.x * windowX;
    vTexCoord = vec2(textureX, aTexCoord.y);
    vEffectCoord = aTexCoord;
}

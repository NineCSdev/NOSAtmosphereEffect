#version 300 es
in vec4 aPosition;
in vec2 aTexCoord;
out vec2 vTexCoord;
out vec2 vEffectCoord;

// Horizontal wallpaper scrolling (home-screen parallax). The defaults are
// identity (full-width window, zero offset), so any draw that does NOT set
// these uniforms - e.g. the off-screen separable-blur passes - samples the
// texture exactly as before. Only the final on-screen draw sets real values.
uniform float uScrollOffsetX;   // launcher page offset, 0.0 (left) .. 1.0 (right)
uniform float uScrollWindowX;   // visible fraction of texture width, (0.0, 1.0]

void main() {
    gl_Position = aPosition;
    float win = uScrollWindowX <= 0.0 ? 1.0 : uScrollWindowX;
    float u = uScrollOffsetX * (1.0 - win) + aTexCoord.x * win;
    vTexCoord = vec2(u, aTexCoord.y);
    // Keep the glass ribs anchored to the physical screen while the photo pans.
    vEffectCoord = aTexCoord;
}

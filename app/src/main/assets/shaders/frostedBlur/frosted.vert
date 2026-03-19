#version 300 es
in vec4 aPosition;
in vec2 aTexCoord;
out vec2 vTexCoord;

// New uniforms for Smart Cropping & Scrolling
uniform float uOffset;
uniform float uEnableParallax;
uniform float uImageAspect;
uniform float uScreenAspect;

void main() {
    gl_Position = aPosition;
    vec2 tex = aTexCoord;

    if (uImageAspect > uScreenAspect) {
        // IMAGE IS WIDER THAN SCREEN (Horizontal Bleed available)
        // Calculate how much of the texture fits on screen
        float visibleWidthRatio = uScreenAspect / uImageAspect;

        // Base center crop offset (if parallax is off)
        float baseOffset = (1.0 - visibleWidthRatio) * 0.5;

        // Parallax offset (maps launcher 0.0-1.0 swipe to the hidden edges)
        float scrollOffset = (1.0 - visibleWidthRatio) * uOffset;

        float finalXOffset = (uEnableParallax > 0.5) ? scrollOffset : baseOffset;

        // Squeeze the UVs to fit the visible ratio, then shift by offset
        tex.x = (tex.x * visibleWidthRatio) + finalXOffset;

    } else {
        // IMAGE IS TALLER THAN SCREEN (No horizontal bleed, lock X, center Y)
        float visibleHeightRatio = uImageAspect / uScreenAspect;
        float offsetY = (1.0 - visibleHeightRatio) * 0.5;
        tex.y = (tex.y * visibleHeightRatio) + offsetY;
    }

    vTexCoord = tex;
}
#version 300 es
precision highp float;

in vec2 vTexCoord;
in vec2 vEffectCoord;

out vec4 fragColor;

uniform sampler2D uTexture;
uniform sampler2D uSubjectMask;
uniform float uProgress;
uniform float uLineCount;
uniform float uLineThickness;
uniform float uTransitionStyle;
uniform float uDimLevel;
uniform float uScrollWindowX;
uniform float uBackgroundOnly;
uniform float uHasSubject;

const float TWO_PI = 6.28318530718;

vec3 sampleSoftened(vec2 uv, vec2 texel) {
    vec2 radius = vec2(texel.x * 1.15, 0.0);
    return
        texture(uTexture, uv).rgb * 0.58 +
        texture(uTexture, clamp(uv + radius, 0.0, 1.0)).rgb * 0.21 +
        texture(uTexture, clamp(uv - radius, 0.0, 1.0)).rgb * 0.21;
}

float sampleSubject(vec2 uv) {
    vec2 stepSize = 2.0 / vec2(textureSize(uSubjectMask, 0));
    float mask = texture(uSubjectMask, uv).r;
    mask = max(mask, texture(uSubjectMask, clamp(uv + vec2(stepSize.x, 0.0), 0.0, 1.0)).r);
    mask = max(mask, texture(uSubjectMask, clamp(uv - vec2(stepSize.x, 0.0), 0.0, 1.0)).r);
    mask = max(mask, texture(uSubjectMask, clamp(uv + vec2(0.0, stepSize.y), 0.0, 1.0)).r);
    mask = max(mask, texture(uSubjectMask, clamp(uv - vec2(0.0, stepSize.y), 0.0, 1.0)).r);
    return mask;
}

void main() {
    float count = max(1.0, floor(uLineCount + 0.5));
    float screenX = clamp(vEffectCoord.x, 0.0, 0.999999);
    float lanePosition = screenX * count;
    float laneIndex = floor(lanePosition);
    float orderFromRight = count - 1.0 - laneIndex;

    float progress = clamp(uProgress, 0.0, 1.0);
    float sequential = clamp(progress * count - orderFromRight, 0.0, 1.0);
    sequential = sequential * sequential * (3.0 - 2.0 * sequential);
    float fade = progress * progress * (3.0 - 2.0 * progress);
    float transition = mix(sequential, fade, step(0.5, uTransitionStyle));

    float localRib = fract(lanePosition);
    float phase = TWO_PI * localRib;
    float wave = sin(phase);
    float profileExponent = mix(1.80, 0.25, clamp(uLineThickness, 0.0, 1.0));
    float profile = sign(wave) * pow(abs(wave), profileExponent);

    float scrollWindow = uScrollWindowX <= 0.0 ? 1.0 : uScrollWindowX;
    float displacement = profile * (1.08 * scrollWindow / count);

    vec2 glassUv = vec2(
        clamp(vTexCoord.x + displacement, 0.0, 1.0),
        vTexCoord.y
    );
    vec2 texel = 1.0 / vec2(textureSize(uTexture, 0));
    vec3 sharpColor = texture(uTexture, vTexCoord).rgb;
    vec3 refractedColor = texture(uTexture, glassUv).rgb;
    vec3 glassColor = mix(refractedColor, sampleSoftened(glassUv, texel), 0.72);
    float ribFaceLighting = 0.016 * (2.0 * localRib - 1.0);
    glassColor += vec3(ribFaceLighting);

    float rightInnerDistance = 1.0 - localRib;
    float rightInnerGlow = 1.0 - smoothstep(
        0.0,
        0.25,
        rightInnerDistance
    );
    glassColor = clamp(
        glassColor + vec3(0.036 * rightInnerGlow),
        0.0,
        1.0
    );
    glassColor = mix(
        glassColor,
        vec3(0.0),
        clamp(uDimLevel, 0.0, 1.0)
    );

    float backgroundCoverage = 1.0;
    if (uBackgroundOnly > 0.5) {
        if (uHasSubject > 0.5) {
            float subject = max(
                sampleSubject(vTexCoord),
                sampleSubject(glassUv)
            );
            backgroundCoverage = 1.0 - smoothstep(0.30, 0.72, subject);
        } else {
            // No subject mask: nothing is known to protect, so cover the
            // whole frame rather than suppressing the effect everywhere.
            backgroundCoverage = 1.0;
        }
    }

    fragColor = vec4(
        mix(sharpColor, glassColor, transition * backgroundCoverage),
        1.0
    );
}

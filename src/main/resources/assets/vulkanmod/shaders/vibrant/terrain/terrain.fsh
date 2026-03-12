#version 460

layout(binding = 2) uniform sampler2D Sampler0;

layout(binding = 1) uniform UBO {
    vec4 FogColor;
    float FogEnvironmentalStart;
    float FogEnvironmentalEnd;
    float FogRenderDistanceStart;
    float FogRenderDistanceEnd;
    float FogSkyEnd;
    float FogCloudsEnd;
    float AlphaCutout;
};

layout(location = 0) in vec4 inColor;
layout(location = 1) in vec2 inUV0;
layout(location = 2) in vec2 inLightmap;
layout(location = 3) in vec3 inWorldPos;

layout(location = 0) out vec4 fragColor;

// ACES Filmic Tonemapping Curve
vec3 ACESFilm(vec3 x) {
    float a = 2.51;
    float b = 0.03;
    float c = 2.43;
    float d = 0.59;
    float e = 0.14;
    return clamp((x*(a*x+b))/(x*(c*x+d)+e), 0.0, 1.0);
}

void main() {
    vec4 texColor = texture(Sampler0, inUV0);
    if (texColor.a < AlphaCutout) {
        discard;
    }

    // 1. Reconstruct 3D Normals from screen-space derivatives
    vec3 dx = dFdx(inWorldPos);
    vec3 dy = dFdy(inWorldPos);
    vec3 normal = normalize(cross(dx, dy));

    // 2. Lightmap Curves
    float blockLight = pow(inLightmap.x, 2.0); // Make torches falloff naturally
    float skyLight = pow(inLightmap.y, 1.5);

    // 3. Define Beautiful Colors
    vec3 torchColor = vec3(1.0, 0.55, 0.15) * 1.5;
    vec3 skyAmbient = vec3(0.12, 0.20, 0.35) * 0.9;
    vec3 skyDirect  = vec3(1.10, 1.05, 0.95) * 1.5;
    vec3 sunDir     = normalize(vec3(0.7, 0.8, 0.4)); // Fixed beautiful sun angle

    // 4. Lighting Calculation
    float NdotL = max(dot(normal, sunDir), 0.0);

    // Base texture combined with vanilla Ambient Occlusion/Biome colors
    vec3 baseAlbedo = texColor.rgb * inColor.rgb;

    // Combine ambient sky and block lights
    vec3 lighting = mix(skyAmbient, skyDirect, skyLight);
    lighting += blockLight * torchColor;

    // Add directional sunlight (only applies where sky light hits)
    float shadowMask = smoothstep(0.85, 1.0, skyLight);
    lighting += NdotL * skyDirect * shadowMask * inColor.rgb;

    vec3 finalColor = baseAlbedo * lighting;

    // 5. Water Specular Highlight
    // Vanilla water alpha is ~0.8. We check if translucent to apply reflections.
    if (inColor.a < 0.95) {
        vec3 viewDir = normalize(-inWorldPos);
        vec3 halfVector = normalize(sunDir + viewDir);
        float NdotH = max(dot(normal, halfVector), 0.0);
        float specular = pow(NdotH, 64.0) * shadowMask;
        finalColor += vec3(1.0, 0.9, 0.8) * specular * 1.5;
    }

    // 6. Atmospheric Scattering (Exponential Fog)
    float dist = length(inWorldPos);
    float fogDensity = 0.006;
    float fogFactor = 1.0 - exp(-dist * fogDensity);

    // Tint fog towards sun color when looking at the horizon
    vec3 atmosphere = mix(FogColor.rgb, vec3(1.0, 0.8, 0.5), NdotL * 0.4);
    finalColor = mix(finalColor, atmosphere, clamp(fogFactor, 0.0, 1.0));

    // 7. Tonemapping & Saturation Boost
    finalColor = ACESFilm(finalColor);
    float luminance = dot(finalColor, vec3(0.299, 0.587, 0.114));
    finalColor = mix(vec3(luminance), finalColor, 1.35); // 35% Saturation boost

    fragColor = vec4(finalColor, texColor.a * inColor.a);
}
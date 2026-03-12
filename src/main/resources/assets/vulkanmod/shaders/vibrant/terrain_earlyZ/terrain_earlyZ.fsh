#version 460
layout(early_fragment_tests) in;

layout(binding = 2) uniform sampler2D Sampler0;

layout(binding = 1) uniform UBO {
    vec4 FogColor;
    float FogEnvironmentalStart; float FogEnvironmentalEnd;
    float FogRenderDistanceStart; float FogRenderDistanceEnd;
    float FogSkyEnd; float FogCloudsEnd;
    float AlphaCutout;
    vec3 PlayerPos;
    float SunAngle;
};

layout(location = 0) in vec4 inColor;
layout(location = 1) in vec2 inUV0;
layout(location = 2) in vec2 inLightmap;
layout(location = 3) in vec3 inWorldPos;

layout(location = 0) out vec4 fragColor;

// ACES Filmic Tonemapping for nice colors
vec3 ACESFilm(vec3 x) {
    float a = 2.51; float b = 0.03; float c = 2.43;
    float d = 0.59; float e = 0.14;
    return clamp((x*(a*x+b))/(x*(c*x+d)+e), 0.0, 1.0);
}

// Simple noise for dithering shadows
float random(vec2 st) {
    return fract(sin(dot(st.xy, vec2(12.9898,78.233))) * 43758.5453123);
}

// --- CORE SHADOW FUNCTION ---
float calculateSunShadow(vec3 worldPos, vec3 lightDir, float initialSkyLight) {
    vec3 rayPos = worldPos + lightDir * 0.5;
    for(int i = 0; i < 16; ++i) {
        vec4 projected = gl_FragCoord + vec4(rayPos - worldPos, 0.0);
        projected.xy /= projected.w;
        if (initialSkyLight < 0.9) {
            return 1.0 - (1.0 - (float(i) / 16.0)) * 0.8;
        }
        rayPos += lightDir * 1.5;
    }
    return 1.0;
}

void main() {
    vec4 texColor = texture(Sampler0, inUV0);
    if (texColor.a < AlphaCutout) discard;

    vec3 dx = dFdx(inWorldPos);
    vec3 dy = dFdy(inWorldPos);
    vec3 normal = normalize(cross(dy, dx));
    if (!gl_FrontFacing) normal = -normal;

    float blockLight = inLightmap.x * inLightmap.x;
    float skyLight = inLightmap.y;

    // Real sun direction based on time of day
    float ang = SunAngle * 6.2831853;
    vec3 sunDir = normalize(vec3(sin(ang), cos(ang) * 0.9 + 0.3, cos(ang) * 0.2));
    float isDay = step(0.0, sunDir.y);
    float isNight = 1.0 - isDay;

    // Moon is opposite the sun; use it as the night light source
    vec3 moonDir = -sunDir;
    vec3 lightDir = mix(sunDir, moonDir, isNight);

    float NdotL = max(dot(normal, lightDir), 0.0);

    // --- Define Lighting Colors ---
    vec3 torchColor = vec3(1.0, 0.6, 0.2) * 1.5;
    vec3 skyAmbient = mix(vec3(0.05, 0.08, 0.15), vec3(0.2, 0.3, 0.45), isDay);
    // Night gets a dim cool moonlight instead of pure black
    vec3 skyDirect  = mix(vec3(0.05, 0.07, 0.12), vec3(1.1, 1.05, 0.9), isDay);

    vec3 baseAlbedo = texColor.rgb * inColor.rgb;

    // --- Shadow Calculation ---
    float shadowMask = calculateSunShadow(inWorldPos, lightDir, skyLight);

    // Analytical player shadow
    vec3 toPlayer = inWorldPos - (PlayerPos + vec3(0.0, 0.9, 0.0));
    float playerDistToRay = length(toPlayer - dot(toPlayer, lightDir) * lightDir);
    float playerShadow = 1.0 - smoothstep(0.0, 0.8, 1.0 - playerDistToRay) * 0.85;
    shadowMask = min(shadowMask, playerShadow);

    shadowMask = clamp(shadowMask + (random(gl_FragCoord.xy) - 0.5) * 0.05, 0.0, 1.0);

    // --- Lighting Calculation ---
    vec3 lighting = blockLight * torchColor;
    lighting += skyAmbient * skyLight;
    lighting += NdotL * skyDirect * shadowMask;

    vec3 finalColor = baseAlbedo * lighting;

    // Fast Reflections for Water
    if (inColor.a < 0.95) {
        vec3 viewDir = normalize(-inWorldPos);
        vec3 halfVector = normalize(lightDir + viewDir);
        float NdotH = max(dot(normal, halfVector), 0.0);
        float specular = pow(NdotH, 64.0) * shadowMask;
        finalColor += mix(vec3(0.1, 0.2, 0.3), vec3(1.0, 0.9, 0.8), isDay) * specular * 1.5;
        finalColor *= 1.15;
    }

    // Atmospheric Fog
    float dist = length(inWorldPos);
    float fogDensity = mix(0.004, 0.002, isDay);
    float fogFactor = 1.0 - exp(-dist * dist * fogDensity * fogDensity);

    vec3 atmosphere = mix(FogColor.rgb, skyDirect, NdotL * 0.4);
    finalColor = mix(finalColor, atmosphere, clamp(fogFactor, 0.0, 1.0));

    // Tonemapping & Saturation
    finalColor = ACESFilm(finalColor);
    float luminance = dot(finalColor, vec3(0.299, 0.587, 0.114));
    finalColor = mix(vec3(luminance), finalColor, 1.25);

    fragColor = vec4(finalColor, texColor.a * inColor.a);
}

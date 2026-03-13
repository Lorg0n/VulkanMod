#version 460
layout(early_fragment_tests) in;

layout(binding = 2) uniform sampler2D Sampler0;
layout(binding = 3) uniform sampler2D ShadowMap;
layout(binding = 4) uniform sampler3D Sampler4;

layout(binding = 1) uniform UBO {
    vec4 FogColor;
    float FogEnvironmentalStart; float FogEnvironmentalEnd;
    float FogRenderDistanceStart; float FogRenderDistanceEnd;
    float FogSkyEnd; float FogCloudsEnd;
    float AlphaCutout;
    vec3 PlayerPos;
    float SunAngle;
    float GameTime;
};

layout(location = 0) in vec4 inColor;
layout(location = 1) in vec2 inUV0;
layout(location = 2) in vec2 inLightmap;
layout(location = 3) in vec3 inWorldPos;
layout(location = 4) in vec4 inLightSpacePos;

layout(location = 0) out vec4 fragColor;

const float PI = 3.14159265359;

vec3 ACESFilm(vec3 x) {
    float a = 2.51; float b = 0.03; float c = 2.43;
    float d = 0.59; float e = 0.14;
    return clamp((x*(a*x+b))/(x*(c*x+d)+e), 0.0, 1.0);
}

float hash(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453123); }

float noise(vec2 p) {
    vec2 i = floor(p); vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0; float a = 0.5;
    mat2 rot = mat2(0.866, 0.5, -0.5, 0.866);
    for (int i = 0; i < 3; ++i) { v += a * noise(p); p = rot * p * 2.0; a *= 0.5; }
    return v;
}

bool VoxelRayCast(vec3 startPos, vec3 dir, vec3 normal, float maxDist, out bool hitLight) {
    startPos += normal * 0.05 + dir * 0.02;
    vec3 pos = floor(startPos);
    vec3 stepDir = sign(dir);
    vec3 tDelta = abs(1.0 / dir);
    vec3 tMax = (pos - startPos + max(stepDir, 0.0)) * tDelta;

    hitLight = false;
    float dist = 0.0;

    for(int i = 0; i < 128; i++) {
        if (dist > maxDist) break;

        vec3 samplePos = (mod(pos, 512.0) + 0.5) / 512.0;
        float voxel = texture(Sampler4, samplePos).r;

        if (voxel > 0.8) {
            return true;
        } else if (voxel > 0.4) {
            hitLight = true;
            return true;
        }

        if (tMax.x < tMax.y) {
            if (tMax.x < tMax.z) {
                pos.x += stepDir.x;
                dist = tMax.x;
                tMax.x += tDelta.x;
            } else {
                pos.z += stepDir.z;
                dist = tMax.z;
                tMax.z += tDelta.z;
            }
        } else {
            if (tMax.y < tMax.z) {
                pos.y += stepDir.y;
                dist = tMax.y;
                tMax.y += tDelta.y;
            } else {
                pos.z += stepDir.z;
                dist = tMax.z;
                tMax.z += tDelta.z;
            }
        }
    }
    return false;
}

float VoxelSunShadow(vec3 startPos, vec3 lightDir, vec3 normal) {
    vec3 pos = startPos + normal * 0.1;
    float visibility = 1.0;
    float t = 0.2;
    float stepSize = 0.4;

    for(int i = 0; i < 60; i++) {
        if(t > 64.0) break;

        vec3 p = pos + lightDir * t;
        float v = texture(Sampler4, fract(p / 512.0)).r;

        if(v > 0.8) {
            visibility -= 0.6;
            if(visibility <= 0.0) return 0.0;
        }

        t += stepSize;
        stepSize *= 1.03;
    }
    return max(visibility, 0.0);
}

float VoxelAO(vec3 startPos, vec3 normal) {
    float ao = 0.0;

    vec3 t = normalize(cross(normal, vec3(0.0, 1.0, 0.0)));
    if (length(t) < 0.1) t = normalize(cross(normal, vec3(1.0, 0.0, 0.0)));
    vec3 b = cross(normal, t);

    vec3 dirs[5] = vec3[](
        normal,
        normalize(normal + t),
        normalize(normal - t),
        normalize(normal + b),
        normalize(normal - b)
    );

    for(int d = 0; d < 5; d++) {
        vec3 rayDir = dirs[d];
        float rayAO = 0.0;

        for(int i = 1; i <= 3; i++) {
            float dist = float(i) * 0.7;
            vec3 p = startPos + normal * 0.1 + rayDir * dist;
            float v = texture(Sampler4, fract(p / 512.0)).r;
            if(v > 0.8) {
                rayAO += 1.0 / float(i);
            }
        }
        ao += rayAO;
    }
    return clamp(1.0 - ao * 0.15, 0.0, 1.0);
}

float ShadowCalculation(vec4 fragPosLightSpace, vec3 normal, vec3 lightDir) {
    vec3 projCoords = fragPosLightSpace.xyz / fragPosLightSpace.w;
    projCoords.xy = projCoords.xy * 0.5 + 0.5;

    if(projCoords.z > 1.0 || projCoords.z < 0.0 || projCoords.x < 0.0 || projCoords.x > 1.0 || projCoords.y < 0.0 || projCoords.y > 1.0)
        return 1.0;

    float currentDepth = projCoords.z;
    float bias = max(0.005 * (1.0 - dot(normal, lightDir)), 0.002);

    float shadow = 0.0;
    vec2 texelSize = 1.0 / textureSize(ShadowMap, 0);

    for(int x = -1; x <= 1; ++x) {
        for(int y = -1; y <= 1; ++y) {
            float pcfDepth = texture(ShadowMap, projCoords.xy + vec2(x, y) * texelSize).r;
            shadow += currentDepth - bias > pcfDepth  ? 0.0 : 1.0;
        }
    }
    shadow /= 9.0;
    shadow = clamp(shadow + (hash(gl_FragCoord.xy) - 0.5) * 0.1, 0.0, 1.0);

    return shadow;
}

void main() {
    vec4 texColor = texture(Sampler0, inUV0);
    if (texColor.a < AlphaCutout) discard;

    bool isWater = (inColor.a < 0.95 && AlphaCutout < 0.05);
    vec4 albedo = texColor * inColor;

    vec3 dx = dFdx(inWorldPos);
    vec3 dy = dFdy(inWorldPos);
    vec3 normal = normalize(cross(dy, dx));
    if (!gl_FrontFacing) normal = -normal;

    if (isWater) {
        vec2 wavePos = inWorldPos.xz * 2.0 + GameTime * 1.5;
        float w = fbm(wavePos) * 0.5 + fbm(wavePos * 2.0 - GameTime) * 0.25;
        vec3 bump = normalize(vec3(w, 1.0, w));
        normal = normalize(mix(normal, bump, 0.15));
    }

    float blockLight = inLightmap.x * inLightmap.x;
    float skyLight = inLightmap.y;

    float ang = SunAngle;
    vec3 sunDir = normalize(vec3(-sin(ang), cos(ang), 0.1));
    float isDay = smoothstep(-0.05, 0.05, sunDir.y);
    vec3 lightDir = sunDir.y > 0.0 ? sunDir : -sunDir;

    float NdotL = max(dot(normal, lightDir), 0.0);

    vec3 sunColor = mix(vec3(1.0, 0.4, 0.1), vec3(1.0, 0.9, 0.8), smoothstep(0.0, 0.3, sunDir.y));
    // FIXED: Brighter, more visible blue moonlight
    vec3 moonColor = vec3(0.08, 0.15, 0.25);
    vec3 directLightColor = mix(moonColor, sunColor, isDay);

    // FIXED: Brighter night ambient light so shadows aren't pitch black
    vec3 skyAmbient = mix(vec3(0.04, 0.07, 0.12), vec3(0.15, 0.25, 0.4), isDay);
    vec3 torchColor = vec3(1.0, 0.6, 0.2) * 1.5;

    float shadow = ShadowCalculation(inLightSpacePos, normal, lightDir);
    float rtxSunShadow = VoxelSunShadow(inWorldPos + PlayerPos, lightDir, normal);

    // Smoothly blend RTX shadow back to the vanilla shadow map in the distance
    shadow = min(shadow, mix(rtxSunShadow, 1.0, smoothstep(48.0, 64.0, length(inWorldPos))));
    shadow = min(shadow, smoothstep(0.5, 0.95, skyLight));

    // Calculate our high quality deterministic Voxel AO for contact shadows
    float vxAO = VoxelAO(inWorldPos + PlayerPos, normal);

    vec3 lighting = blockLight * torchColor * mix(0.5, 1.0, vxAO);
    // FIXED: Raised minimum skylight factor from 0.05 to 0.1 so ambient is stronger
    lighting += skyAmbient * max(skyLight, 0.1) * vxAO;
    lighting += NdotL * directLightColor * shadow;
    lighting = max(lighting, vec3(0.01));

    vec3 finalColor = albedo.rgb * lighting;

    if (isWater) {
        vec3 viewDir = normalize(-inWorldPos);
        float NdotV = max(dot(normal, viewDir), 0.0);
        float fresnel = mix(0.02, 1.0, pow(1.0 - NdotV, 5.0));

        vec3 halfVector = normalize(lightDir + viewDir);
        float NdotH = max(dot(normal, halfVector), 0.0);
        float specular = pow(NdotH, 64.0) * shadow;

        vec3 reflectionColor = mix(skyAmbient, directLightColor, shadow);
        finalColor = mix(finalColor, reflectionColor, fresnel * 0.8);
        finalColor += directLightColor * specular * 2.0 * isDay;

        finalColor *= vec3(0.3, 0.7, 0.9);
        albedo.a = mix(0.6, 0.9, fresnel);
    }

    float dist = length(inWorldPos);
    float fogDensity = mix(0.003, 0.001, isDay);
    float fogFactor = 1.0 - exp(-dist * dist * fogDensity * fogDensity);

    float VdotL = max(dot(normalize(inWorldPos), lightDir), 0.0);
    float sunGlare = pow(VdotL, 8.0) * isDay * shadow * skyLight;

    vec3 atmosphere = mix(FogColor.rgb, directLightColor, sunGlare * 0.5);
    finalColor = mix(finalColor, atmosphere, clamp(fogFactor, 0.0, 1.0));

    finalColor *= 1.2;
    finalColor = ACESFilm(finalColor);

    float luminance = dot(finalColor, vec3(0.2126, 0.7152, 0.0722));
    finalColor = mix(vec3(luminance), finalColor, 1.2);

    fragColor = vec4(finalColor, albedo.a);
}
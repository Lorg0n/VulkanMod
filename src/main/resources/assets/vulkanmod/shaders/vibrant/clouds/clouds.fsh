#version 450

#include "fog.glsl"

layout(binding = 1) uniform UBO {
    vec4 ColorModulator;
    float FogCloudsEnd;
    float SunAngle;
    float GameTime;
};

layout(location = 0) in vec4 vertexColor;
layout(location = 1) in float vertexDistance;
layout(location = 2) in vec3 worldPos;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 color = vertexColor;

    // FIX: SunAngle is already in Radians!
    float ang = SunAngle;
    vec3 sunDir = normalize(vec3(-sin(ang), cos(ang), 0.1));
    float isDay = smoothstep(-0.05, 0.05, sunDir.y);

    vec3 sunColor = mix(vec3(1.0, 0.5, 0.2), vec3(1.0, 1.0, 1.0), smoothstep(0.0, 0.3, sunDir.y));
    vec3 moonColor = vec3(0.2, 0.3, 0.4);
    vec3 lightColor = mix(moonColor, sunColor, isDay);

    color.rgb *= lightColor * 1.5;

    color.rgb *= mix(0.8, 1.0, fract(worldPos.x * 0.01 + GameTime * 0.05));

    color.a *= 1.0f - linear_fog_value(vertexDistance, 0, FogCloudsEnd);
    color.a *= 0.8;

    fragColor = vec4(color.rgb, color.a);
}
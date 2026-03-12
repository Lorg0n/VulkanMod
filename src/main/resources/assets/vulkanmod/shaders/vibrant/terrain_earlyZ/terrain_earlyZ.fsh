#version 460
layout(early_fragment_tests) in;

layout(binding = 2) uniform sampler2D Sampler0;
layout(binding = 1) uniform UBO {
    vec4 FogColor;
    float FogEnvironmentalStart; float FogEnvironmentalEnd;
    float FogRenderDistanceStart; float FogRenderDistanceEnd;
    float FogSkyEnd; float FogCloudsEnd;
    float AlphaCutout;
};

// Location MUST match outUV0 from terrain.vsh (which is location 1)
layout(location = 1) in vec2 inUV0;

void main() {
    if (texture(Sampler0, inUV0).a < AlphaCutout) discard;
}
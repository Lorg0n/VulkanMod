#version 460

layout (binding = 0) uniform UniformBufferObject {
    mat4 MVP;
    mat4 LightSpaceMat;
};

layout (push_constant) uniform pushConstant {
    vec3 ModelOffset;
};

layout (binding = 1) uniform UBO {
    vec4 FogColor;
    float FogEnvironmentalStart; float FogEnvironmentalEnd;
    float FogRenderDistanceStart; float FogRenderDistanceEnd;
    float FogSkyEnd; float FogCloudsEnd;
    float AlphaCutout;
    vec3 PlayerPos;
    float SunAngle;
    float GameTime;
};

layout (location = 0) in ivec4 Position;
layout (location = 1) in uvec2 UV0;
layout (location = 2) in uint PackedColor;

layout (location = 0) out vec4 outColor;
layout (location = 1) out vec2 outUV0;
layout (location = 2) out vec2 outLightmap;
layout (location = 3) out vec3 outWorldPos;
layout (location = 4) out vec4 outLightSpacePos;

const float UV_INV = 1.0 / 32768.0;
const vec3 POSITION_INV = vec3(1.0 / 2048.0);

vec3 getVertexPosition() {
    const vec3 baseOffset = bitfieldExtract(ivec3(gl_InstanceIndex) >> ivec3(0, 16, 8), 0, 8);
    return fma(Position.xyz, POSITION_INV, ModelOffset + baseOffset);
}

void main() {
    vec3 pos = getVertexPosition();
    vec4 color = unpackUnorm4x8(PackedColor);
    vec2 texUV = UV0 * UV_INV;

    bool isFoliage = (AlphaCutout > 0.4 && AlphaCutout < 0.6);
    bool isWater = (AlphaCutout < 0.05 && color.a < 0.95);

    if (isFoliage || isWater) {
        float wave = sin(pos.x * 2.0 + GameTime * 3.0) * cos(pos.z * 2.0 + GameTime * 2.0);
        float magnitude = isWater ? 0.05 : 0.03;

        float atten = isWater ? 1.0 : max(1.0 - texUV.y, 0.0);

        pos.x += wave * magnitude * atten;
        pos.z += wave * magnitude * atten;
        if (isWater) pos.y += wave * 0.05;
    }

    gl_Position = MVP * vec4(pos, 1.0);
    outLightSpacePos = LightSpaceMat * vec4(pos, 1.0);

    outWorldPos = pos;
    outColor = color;
    outUV0 = texUV;

    uint uv = Position.w;
    outLightmap = vec2(bitfieldExtract(uv, 4, 4), bitfieldExtract(uv, 12, 4)) / 15.0;
}
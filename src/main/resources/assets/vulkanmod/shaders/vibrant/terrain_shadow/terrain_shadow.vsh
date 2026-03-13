#version 460

layout (binding = 0) uniform UniformBufferObject {
    mat4 MVP;
};

layout (push_constant) uniform pushConstant {
    vec3 ModelOffset;
};

layout (location = 0) in ivec4 Position;
layout (location = 1) in uvec2 UV0;
layout (location = 2) in uint PackedColor;

layout (location = 0) out vec2 outUV0;

const float UV_INV = 1.0 / 32768.0;
const vec3 POSITION_INV = vec3(1.0 / 2048.0);

vec3 getVertexPosition() {
    const vec3 baseOffset = bitfieldExtract(ivec3(gl_InstanceIndex) >> ivec3(0, 16, 8), 0, 8);
    return fma(Position.xyz, POSITION_INV, ModelOffset + baseOffset);
}

void main() {
    vec3 pos = getVertexPosition();
    gl_Position = MVP * vec4(pos, 1.0);
    outUV0 = UV0 * UV_INV;
}
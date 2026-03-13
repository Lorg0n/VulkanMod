#version 460

layout(binding = 2) uniform sampler2D Sampler0;

layout(binding = 1) uniform UBO {
    float AlphaCutout;
};

layout(location = 0) in vec2 inUV0;

void main() {
    if (texture(Sampler0, inUV0).a < AlphaCutout) discard;
}
#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

// Paint the sampled shape a flat colour (vertexColor), keeping only the texture's alpha as the mask.
// Fed the item's cached atlas slot, this renders the item's exact silhouette in solid white.
void main() {
    float a = texture(Sampler0, texCoord0).a;
    if (a == 0.0) {
        discard;
    }
    fragColor = vec4(vertexColor.rgb, vertexColor.a * a) * ColorModulator;
}

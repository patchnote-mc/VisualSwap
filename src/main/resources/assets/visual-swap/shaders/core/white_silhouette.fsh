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

// Recolour the sampled item into a gamma-shaded tint (cf. alpha_to_bw.py's "shade" mode). vertexColor.rgb is the
// per-item tint colour; vertexColor.a carries the gamma, encoded as gamma / 8.0 (see ItemFlash.GAMMA_ENCODE_MAX). We
// take the item's own luminance, reshape it with pow(lum, 1/gamma) — a higher gamma lifts darks toward a flat bright
// tint, a lower gamma keeps the item's shading — then paint the tint colour at that brightness. The texture's alpha
// stays the silhouette mask.
void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    if (tex.a == 0.0) {
        discard;
    }
    float gamma = max(vertexColor.a * 8.0, 0.01);
    float lum = dot(tex.rgb, vec3(0.299, 0.587, 0.114));
    float shade = pow(clamp(lum, 0.0, 1.0), 1.0 / gamma);
    fragColor = vec4(vertexColor.rgb * shade, tex.a) * ColorModulator;
}

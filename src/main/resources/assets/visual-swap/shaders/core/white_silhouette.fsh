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

// Recolour the sampled item into a shade-tinted silhouette (cf. alpha_to_bw.py's "shade" mode). vertexColor.rgb is the
// per-item tint colour; vertexColor.a carries the shade exponent (1/gamma) directly, 0..1 (see ItemFlash.packTint). We
// take the item's own luminance and paint the tint colour at pow(lum, exponent): a smaller exponent lifts darks toward
// a flat bright tint, a larger one keeps the item's shading. Exponent 0 is a flat fill — every opaque pixel becomes the
// full tint colour (fully white / whatever the tint is). The texture's alpha stays the silhouette mask.
void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    if (tex.a == 0.0) {
        discard;
    }
    float exponent = vertexColor.a;
    float lum = dot(tex.rgb, vec3(0.299, 0.587, 0.114));
    float shade = exponent <= 0.0 ? 1.0 : pow(clamp(lum, 0.0, 1.0), exponent);
    fragColor = vec4(vertexColor.rgb * shade, tex.a) * ColorModulator;
}

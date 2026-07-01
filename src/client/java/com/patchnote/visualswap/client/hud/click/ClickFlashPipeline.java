package com.patchnote.visualswap.client.hud.click;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.patchnote.visualswap.VisualSwap;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

/// GUI pipeline that draws a texture as a flat white silhouette: it keeps the sampled alpha as a mask but forces the
/// colour to the vertex colour, so re-blitting the item's cached atlas slot paints the item's exact shape solid white.
///
/// Mirrors vanilla's `core/position_tex_color` GUI-textured pipeline (same bind groups + vertex format) with only the
/// fragment output changed. Needs no registration: {@code ShaderManager} scans every namespace's `shaders/` folder and
/// the device compiles unregistered pipelines lazily on first use.
public final class ClickFlashPipeline
{
    private static final Identifier SHADER = Identifier.fromNamespaceAndPath(VisualSwap.MOD_ID, "core/white_silhouette");

    public static final RenderPipeline WHITE_SILHOUETTE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath(VisualSwap.MOD_ID, "pipeline/white_silhouette"))
            .withVertexShader(SHADER)
            .withFragmentShader(SHADER)
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withUsePipelineDrawModeForGui(true)
            .build();

    private ClickFlashPipeline() { }
}

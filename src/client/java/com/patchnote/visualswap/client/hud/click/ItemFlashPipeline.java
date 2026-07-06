package com.patchnote.visualswap.client.hud.click;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.patchnote.visualswap.VisualSwap;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

/// GUI pipeline that recolours a texture into a gamma-shaded tint silhouette (see {@code white_silhouette.fsh})
public final class ItemFlashPipeline
{
    private static final Identifier SHADER = Identifier.fromNamespaceAndPath(
            VisualSwap.MOD_ID,
            "core/white_silhouette"
    );

    public static final RenderPipeline WHITE_SILHOUETTE = //
            RenderPipeline.builder() //
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

    private ItemFlashPipeline() { }
}

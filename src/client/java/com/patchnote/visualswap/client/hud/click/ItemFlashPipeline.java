package com.patchnote.visualswap.client.hud.click;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.patchnote.visualswap.VisualSwap;
import com.patchnote.visualswap.client.utils.HotbarGeometry;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
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
                          .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                          .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                          .withSampler("Sampler0")
                          .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                          .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                          .build();

    private ItemFlashPipeline() { }

    /// A silhouette re-blit of an atlas-rendered GUI item: same texture view, pose, bounds and scissor as the item's
    /// own blit, drawn through {@link #WHITE_SILHOUETTE} with {@code tint} (packed via {@link ItemFlash#packTint}).
    public static BlitRenderState silhouetteBlit(GuiItemRenderState itemState, GuiItemAtlas.SlotView slotView, int tint)
    {
        return new BlitRenderState(
                WHITE_SILHOUETTE,
                TextureSetup.singleTexture(
                        slotView.textureView(), //
                        RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST)
                ),
                itemState.pose(),
                itemState.x(),
                itemState.y(),
                itemState.x() + HotbarGeometry.SLOT_SIZE,
                itemState.y() + HotbarGeometry.SLOT_SIZE,
                slotView.u0(),
                slotView.u1(),
                slotView.v0(),
                slotView.v1(),
                tint,
                itemState.scissorArea(),
                null
        );
    }
}

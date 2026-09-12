package com.patchnote.visualswap.client.mixin;

import com.patchnote.visualswap.client.hud.click.ItemFlashPipeline;
import com.patchnote.visualswap.client.hud.click.ItemFlashPreview;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.state.GuiItemRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// Re-blits any GUI item registered in {@link ItemFlashPreview} as a gamma-shaded tint silhouette — the config screen's
/// flash preview rides the exact same shader path as the in-game hotbar flash ({@code HotbarItemGlowMixin}).
@Mixin(GuiRenderer.class)
public class GuiItemFlashPreviewMixin
{
    @Shadow
    @Final
    private GuiRenderState renderState;

    @Shadow
    private GpuTextureView itemsAtlasView;

    @Inject(
            method = "submitBlitFromItemAtlas(Lnet/minecraft/client/gui/render/state/GuiItemRenderState;FFII)V",
            at = @At("TAIL")
    )
    private void visualswap$flashPreviewItem(GuiItemRenderState itemState, float u, float v,
                                             int itemSize, int atlasSize,
                                             CallbackInfo ci)
    {
        Integer tint = ItemFlashPreview.tintAt(itemState.x(), itemState.y());
        if (tint == null) return;

        this.renderState.submitBlitToCurrentLayer(
                ItemFlashPipeline.silhouetteBlit(itemState, this.itemsAtlasView, u, v, itemSize, atlasSize, tint));
    }
}

package com.patchnote.visualswap.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.patchnote.visualswap.client.hud.click.ClickFlash;
import com.patchnote.visualswap.client.hud.click.ClickFlashPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.joml.Matrix3x2fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// Additively re-blits the clicked hotbar item on top of itself for a single frame, flaring it white on each click.
/// Runs right after vanilla submits the item's own atlas blit, reusing that cached slot texture with an additive
/// GUI pipeline.
@Mixin(GuiRenderer.class)
public class HotbarItemGlowMixin
{
    @Shadow @Final private GuiRenderState renderState;

    @Inject(
            method = "submitBlitFromItemAtlas(Lnet/minecraft/client/renderer/state/gui/GuiItemRenderState;Lnet/minecraft/client/gui/render/GuiItemAtlas$SlotView;)V",
            at = @At("TAIL")
    )
    private void visualswap$glowClickedSlot(GuiItemRenderState itemState, GuiItemAtlas.SlotView slotView,
                                            CallbackInfo ci)
    {
        Minecraft mc = Minecraft.getInstance();
        int guiWidth = mc.getWindow().getGuiScaledWidth();
        int guiHeight = mc.getWindow().getGuiScaledHeight();

        // Match the vanilla hotbar-slot geometry (Hud: x = w/2 - 90 + i*20 + 2, y = h - 16 - 3).
        if (itemState.y() != guiHeight - 19) return;
        int rel = itemState.x() - (guiWidth / 2 - 88);
        if (rel < 0 || rel % 20 != 0) return;
        int slot = rel / 20;
        if (slot < 0 || slot > 8) return;
        if (mc.player == null || !ClickFlash.INSTANCE.isActive(slot, mc.player.tickCount)) return;

        this.renderState.addBlitToCurrentLayer(new BlitRenderState(
                ClickFlashPipeline.WHITE_SILHOUETTE,
                TextureSetup.singleTexture(slotView.textureView(),
                        RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST)),
                itemState.pose(),
                itemState.x(), itemState.y(), itemState.x() + 16, itemState.y() + 16,
                slotView.u0(), slotView.u1(), slotView.v0(), slotView.v1(),
                ClickFlash.GLOW_ARGB,
                itemState.scissorArea(), null
        ));
    }
}

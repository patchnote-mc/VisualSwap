package com.patchnote.visualswap.client.mixin;

import com.patchnote.visualswap.client.config.ModConfig;
import com.patchnote.visualswap.client.hud.click.ItemFlash;
import com.patchnote.visualswap.client.hud.click.ItemFlashPipeline;
import com.patchnote.visualswap.client.utils.Constants;
import com.patchnote.visualswap.client.utils.HotbarGeometry;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.state.GuiItemRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// Re-blits a flashing hotbar item as a gamma-shaded tint silhouette for one frame, right after vanilla submits its
/// atlas blit — reusing that slot's cached texture. The per-item tint colour and intensity (gamma) ride in the tint int
/// from {@link ItemFlash#getTintFor(int)}.
@Mixin(GuiRenderer.class)
public class HotbarItemGlowMixin
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
    private void visualswap$glowClickedSlot(GuiItemRenderState itemState, float u, float v,
                                            int itemSize, int atlasSize,
                                            CallbackInfo ci)
    {
        if (!ModConfig.get().itemFlashActive()) return;

        Minecraft mc = Minecraft.getInstance();
        int guiWidth = mc.getWindow().getGuiScaledWidth();
        int guiHeight = mc.getWindow().getGuiScaledHeight();

        int slot = HotbarGeometry.slotIndexAt(guiWidth, guiHeight, itemState.x(), itemState.y());
        if (slot == Constants.NO_SLOT) return;
        if (mc.player == null || !ItemFlash.INSTANCE.isActive(slot, mc.player.tickCount)) return;

        this.renderState.submitBlitToCurrentLayer(ItemFlashPipeline.silhouetteBlit(
                itemState, this.itemsAtlasView, u, v, itemSize, atlasSize, ItemFlash.INSTANCE.getTintFor(slot)));
    }
}

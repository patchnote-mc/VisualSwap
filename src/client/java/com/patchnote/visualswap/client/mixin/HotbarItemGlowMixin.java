package com.patchnote.visualswap.client.mixin;

import com.patchnote.visualswap.client.config.ModConfig;
import com.patchnote.visualswap.client.hud.click.ItemFlash;
import com.patchnote.visualswap.client.hud.click.ItemFlashPipeline;
import com.patchnote.visualswap.client.utils.Constants;
import com.patchnote.visualswap.client.utils.HotbarGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
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

    @Inject(
            method = "submitBlitFromItemAtlas(Lnet/minecraft/client/renderer/state/gui/GuiItemRenderState;Lnet/minecraft/client/gui/render/GuiItemAtlas$SlotView;)V",
            at = @At("TAIL")
    )
    private void visualswap$glowClickedSlot(GuiItemRenderState itemState, GuiItemAtlas.SlotView slotView,
                                            CallbackInfo ci)
    {
        if (!ModConfig.get().itemFlashActive()) return;

        Minecraft mc = Minecraft.getInstance();
        int guiWidth = mc.getWindow().getGuiScaledWidth();
        int guiHeight = mc.getWindow().getGuiScaledHeight();

        int slot = HotbarGeometry.slotIndexAt(guiWidth, guiHeight, itemState.x(), itemState.y());
        if (slot == Constants.NO_SLOT) return;
        if (mc.player == null || !ItemFlash.INSTANCE.isActive(slot, mc.player.tickCount)) return;

        this.renderState.addBlitToCurrentLayer(
                ItemFlashPipeline.silhouetteBlit(itemState, slotView, ItemFlash.INSTANCE.getTintFor(slot)));
    }
}

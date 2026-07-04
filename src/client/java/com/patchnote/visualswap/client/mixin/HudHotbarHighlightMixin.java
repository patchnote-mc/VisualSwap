package com.patchnote.visualswap.client.mixin;

import com.patchnote.visualswap.client.hud.SwapHotbarHighlight;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// Used to draw highlight after the hotbar, before the item
@Mixin(Hud.class)
public class HudHotbarHighlightMixin
{
    @Inject(
            method = "extractSlot(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/client/DeltaTracker;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;I)V",
            at = @At("HEAD")
    )
    private void visualSwap$highlightSlot (GuiGraphicsExtractor graphics, int x, int y, DeltaTracker deltaTracker, Player player,
                                          ItemStack itemStack, int seed, CallbackInfo ci)
    { SwapHotbarHighlight.INSTANCE.highlightSlot(graphics, x, y); }
}

package com.patchnote.visualswap.client.mixin;

import com.patchnote.visualswap.client.tracker.HotbarSelectSignal;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/// Flags a deliberate hotbar slot switch. The hotbar number keys ({@code Minecraft.handleKeybinds}) and the scroll wheel
/// ({@code MouseHandler.onScroll}) both route through {@link Inventory#setSelectedSlot(int)}, so hooking that single
/// method catches either input — and, unlike comparing the held item across ticks, it also fires when the selection lands
/// on the slot already held (re-pressing the current key) or on a different slot holding an identical item.
@Mixin(Inventory.class)
public class HotbarSelectMixin
{
    @Shadow
    @Final
    public Player player;

    @Inject(method = "setSelectedSlot(I)V", at = @At("HEAD"))
    private void visualswap$markHotbarSelect(int slot, CallbackInfo ci)
    {
        // In single-player this Inventory bytecode also runs on the integrated-server player (a different thread); only
        // the client player's own selection is a user-driven hotbar switch, so gate on identity.
        if (this.player == Minecraft.getInstance().player) HotbarSelectSignal.mark();
    }
}

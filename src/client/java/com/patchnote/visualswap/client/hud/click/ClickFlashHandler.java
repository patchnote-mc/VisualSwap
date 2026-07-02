package com.patchnote.visualswap.client.hud.click;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/// Drives {@link ClickFlash} from its own END_CLIENT_TICK callback, sampling the inputs it needs directly.
/// Kept fully independent of the swap-window glyph / hotbar-highlight logic in {@code VisualSwapClient}.
public final class ClickFlashHandler
{
    private boolean hasPrevious;
    private boolean prevAttackDown;
    private boolean prevUseDown;
    private boolean prevSwinging;
    private int prevSwingTime;

    private ClickFlashHandler() { }

    public static void register()
    {
        ClientTickEvents.END_CLIENT_TICK.register(new ClickFlashHandler()::onEndClientTick);
    }

    private void onEndClientTick(Minecraft client)
    {
        LocalPlayer player = client.player;
        if (player == null)
        {
            ClickFlash.INSTANCE.clear();
            this.hasPrevious = false;
            return;
        }

        boolean attackDown = client.options.keyAttack.isDown();
        boolean useDown = client.options.keyUse.isDown();
        boolean swinging = player.swinging;
        int swingTime = player.swingTime;

        boolean attackStarted = this.hasPrevious && attackDown && !this.prevAttackDown;
        boolean useStarted = this.hasPrevious && useDown && !this.prevUseDown;
        boolean swingStarted = this.hasPrevious && swinging && (!this.prevSwinging || swingTime < this.prevSwingTime);

        // A swing only counts as a fresh press when the attack key isn't held, so held auto-swings (which keep
        // re-swinging) don't re-arm the minimum-flash floor and leave a tail after release.
        boolean attackEdge = attackStarted || (swingStarted && !attackDown);

        ClickFlash.INSTANCE.onTick(
                player.tickCount,
                player.getInventory().getSelectedSlot(),
                player.getMainHandItem(),
                attackDown, useDown, attackEdge, useStarted);

        this.hasPrevious = true;
        this.prevAttackDown = attackDown;
        this.prevUseDown = useDown;
        this.prevSwinging = swinging;
        this.prevSwingTime = swingTime;
    }
}

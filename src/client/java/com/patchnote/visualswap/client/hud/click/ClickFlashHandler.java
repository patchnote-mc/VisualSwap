package com.patchnote.visualswap.client.hud.click;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/// Input sensor for {@link ClickFlash}: its own END_CLIENT_TICK callback, independent of the swap-window HUD.
/// Samples the attack/use keys and the swing animation, turns them into "a press happened this tick", and drives
/// the flash state machine.
public final class ClickFlashHandler
{
    private boolean hasPrev;
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
            this.hasPrev = false;
            return;
        }

        boolean attackDown = client.options.keyAttack.isDown();
        boolean useDown = client.options.keyUse.isDown();
        boolean swinging = player.swinging;
        int swingTime = player.swingTime;

        boolean attackStarted = this.hasPrev && attackDown && !this.prevAttackDown;
        boolean useStarted = this.hasPrev && useDown && !this.prevUseDown;
        boolean swingStarted = this.hasPrev && swinging && (!this.prevSwinging || swingTime < this.prevSwingTime);

        // Spear jabs are fast clicks the once-per-tick key sampling can miss; a jab always raises a fresh swing.
        // Count a swing as a press only while the attack key is up, so a held key's auto-swings don't re-fire.
        boolean attackPressed = attackStarted || (swingStarted && !attackDown);

        ClickFlash.INSTANCE.onTick(
                player.tickCount,
                player.getInventory().getSelectedSlot(),
                player.getMainHandItem(),
                attackDown, useDown, attackPressed, useStarted);

        this.hasPrev = true;
        this.prevAttackDown = attackDown;
        this.prevUseDown = useDown;
        this.prevSwinging = swinging;
        this.prevSwingTime = swingTime;
    }
}

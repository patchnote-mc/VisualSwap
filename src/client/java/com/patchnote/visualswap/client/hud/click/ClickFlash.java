package com.patchnote.visualswap.client.hud.click;

import java.util.Arrays;

/// White-silhouette flash of clicked hotbar items, held for at least one tick, tracked per slot.
///
/// Armed from the client tick whenever any click (attack / use / spear jab) starts; the render mixin paints the item's
/// silhouette white every frame while the slot's flash is active. Each slot has its own timer, so multiple slots can
/// flash at once (e.g. the axe then the mace during a stun slam) — the overlays are independent.
public final class ClickFlash
{
    public static final ClickFlash INSTANCE = new ClickFlash();

    /// Tint of the silhouette (ARGB). White paints the item's shape solid white; lower the alpha to fade it.
    public static final int GLOW_ARGB = 0xFFFFFFFF;

    /// How many client ticks a flash stays lit after a click (minimum 1 = one full tick of frames).
    private static final int FLASH_TICKS = 1;

    private static final int NO_TICK = Integer.MIN_VALUE;
    private static final int HOTBAR_SLOTS = 9;

    private final int[] flashUntilTick = new int[HOTBAR_SLOTS];

    private ClickFlash() { Arrays.fill(this.flashUntilTick, NO_TICK); }

    /// Arm a flash on {@code slot} (the hotbar slot whose item was clicked) at {@code tick}.
    public void onClick(int slot, int tick)
    {
        if (slot < 0 || slot >= HOTBAR_SLOTS) return;
        this.flashUntilTick[slot] = tick + FLASH_TICKS;
    }

    public void clear() { Arrays.fill(this.flashUntilTick, NO_TICK); }

    /// @return whether {@code slot} is flashing at {@code currentTick}.
    public boolean isActive(int slot, int currentTick)
    {
        return slot >= 0 && slot < HOTBAR_SLOTS && currentTick < this.flashUntilTick[slot];
    }
}

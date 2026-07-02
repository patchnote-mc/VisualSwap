package com.patchnote.visualswap.client.hud.click;

import java.util.Arrays;

/// White-silhouette flash of clicked hotbar items
public final class ClickFlash
{
    public static final ClickFlash INSTANCE = new ClickFlash();

    /// Tint of the silhouette
    public static final int GLOW_ARGB = 0xFFFFFFFF;

    /// Number of ticks the flash is visible
    private static final int FLASH_TICKS = 1;

    private static final int NO_TICK = Integer.MIN_VALUE;
    private static final int HOTBAR_SLOTS = 9;

    private final int[] flashUntilTick = new int[HOTBAR_SLOTS];

    private ClickFlash() { Arrays.fill(this.flashUntilTick, NO_TICK); }

    /// Call when item clicked
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

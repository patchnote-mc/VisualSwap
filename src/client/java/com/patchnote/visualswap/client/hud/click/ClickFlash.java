package com.patchnote.visualswap.client.hud.click;

import com.patchnote.visualswap.client.config.ModConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/// White-silhouette flash of a clicked hotbar item
public final class ClickFlash
{
    public static final ClickFlash INSTANCE = new ClickFlash();

    public static final int GLOW_RGB = 0xFFFFFF;
    private static final int FLASH_TICKS = 5;

    private static final int NO_TICK = Integer.MIN_VALUE;
    private static final int NO_SLOT = -1;
    private static final int HOTBAR_SLOTS = 9;
    private static final int DEFAULT_ARGB = 0xFF000000 | GLOW_RGB;

    private final int[] slotsExpirationTick = new int[HOTBAR_SLOTS];
    private final int[] slotsGlow = new int[HOTBAR_SLOTS];

    private int heldSlot = NO_SLOT;

    private ClickFlash()
    {
        Arrays.fill(this.slotsExpirationTick, NO_TICK);
        Arrays.fill(this.slotsGlow, DEFAULT_ARGB);
    }

    /// Call on Client Tick
    public void onTick(int tick, int currentSlot, ItemStack selectedStack, boolean attackDown, boolean useDown,
                       boolean attackPressed, boolean usePressed)
    {
        ModConfig.FlashRule rule = ruleFor(selectedStack);
        boolean validSlot = currentSlot >= 0 && currentSlot < HOTBAR_SLOTS;

        if (rule == null || !validSlot) return;

        boolean attackFlashActive = rule.flashesAt.flashesOnAttack();
        boolean useFlashActive = rule.flashesAt.flashesOnUse();

        boolean pressed = (attackFlashActive && attackPressed) || (useFlashActive && usePressed);
        boolean keyHeld = (attackFlashActive && attackDown) || (useFlashActive && useDown);

        if (pressed)
        {
            this.slotsGlow[currentSlot] = argbOf(rule);
            this.slotsExpirationTick[currentSlot] = tick + FLASH_TICKS;
            this.heldSlot = keyHeld ? currentSlot : NO_SLOT;
        }
        else
        {
            this.heldSlot = NO_SLOT;
        }
    }

    /// Reset all flashes (e.g. when leaving a world).
    public void clear()
    {
        Arrays.fill(this.slotsExpirationTick, NO_TICK);
        Arrays.fill(this.slotsGlow, DEFAULT_ARGB);
        this.heldSlot = NO_SLOT;
    }

    /// @return whether {@code slot} is lit at {@code currentTick} — sustained by a hold, or within its floor.
    public boolean isActive(int slot, int currentTick)
    {
        if (slot < 0 || slot >= HOTBAR_SLOTS) return false;
        return slot == this.heldSlot || currentTick < this.slotsExpirationTick[slot];
    }

    /// @return the silhouette tint (ARGB, opacity in the alpha byte) for {@code slot}.
    public int argbFor(int slot) { return (slot >= 0 && slot < HOTBAR_SLOTS) ? this.slotsGlow[slot] : DEFAULT_ARGB; }

    private static int argbOf(ModConfig.FlashRule rule)
    {
        int alpha = (rule.opacity != null ? rule.opacity : ModConfig.FlashOpacity.HIGH).alpha();
        return (alpha << 24) | GLOW_RGB;
    }

    /// @return the configured rule matching {@code stack}, or {@code null} if none flashes it.
    private static ModConfig.@Nullable FlashRule ruleFor(ItemStack stack)
    {
        if (stack == null || stack.isEmpty()) return null;

        Identifier held = BuiltInRegistries.ITEM.getKey(stack.getItem());

        for (ModConfig.FlashRule rule : ModConfig.get().clickFlashRules)
        {
            if (rule == null || rule.item == null || rule.flashesAt == null) continue;
            if (held.equals(Identifier.tryParse(rule.item))) return rule;
        }
        return null;
    }
}

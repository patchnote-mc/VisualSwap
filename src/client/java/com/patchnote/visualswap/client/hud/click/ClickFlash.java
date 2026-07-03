package com.patchnote.visualswap.client.hud.click;

import java.util.Arrays;

import com.patchnote.visualswap.client.config.ModConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/// White-silhouette flash of a clicked hotbar item — the render-facing state machine. Driven each tick by
/// {@link ClickFlashHandler}; read each frame by the render mixin via {@link #isActive} / {@link #argbFor}.
///
/// Only items listed in {@link ModConfig#clickFlashRules} flash, each on its configured input(s) and opacity.
/// The behaviour is defined by three rules (which together make fast swap/slam combos read correctly — see
/// attribute-swap-mechanic.txt):
///
///   1. START ON PRESS ONLY. A flash begins only on a genuine attack/use press while the item is selected.
///      Merely holding a key and switching items never lights the new item, so a swap shows only the item
///      actually clicked — never the one swapped past nor a leftover from before.
///   2. INDEPENDENT PER SLOT. Each slot keeps its own {@link #FLASH_TICKS} timeline, so two quick presses on two
///      items (a stun-slam) flash independently rather than replacing one another.
///   3. HOLD SUSTAINS ITS OWN SLOT. Holding the key past the minimum keeps the *pressed* slot lit and never
///      migrates to another slot; releasing switches it off at once. A tap still honours the {@link #FLASH_TICKS}
///      minimum.
public final class ClickFlash
{
    public static final ClickFlash INSTANCE = new ClickFlash();

    /// Silhouette colour (white); each flash's alpha (opacity) is packed on top per rule.
    public static final int GLOW_RGB = 0xFFFFFF;

    /// Minimum ticks a press stays lit, however briefly the key was held.
    private static final int FLASH_TICKS = 5;

    private static final int NO_TICK = Integer.MIN_VALUE;
    private static final int NO_SLOT = -1;
    private static final int HOTBAR_SLOTS = 9;
    private static final int DEFAULT_ARGB = 0xFF000000 | GLOW_RGB;

    /// Per slot: tick its minimum-flash floor expires (exclusive) and the flash tint. Independent across slots.
    private final int[] floorEndTick = new int[HOTBAR_SLOTS];
    private final int[] slotArgb = new int[HOTBAR_SLOTS];

    /// The one slot a held key is sustaining past its floor (or {@link #NO_SLOT}). Only a press may set it.
    private int heldSlot = NO_SLOT;

    private ClickFlash()
    {
        Arrays.fill(this.floorEndTick, NO_TICK);
        Arrays.fill(this.slotArgb, DEFAULT_ARGB);
    }

    /// Drive once per client tick with the current selection and this tick's input state.
    ///
    /// @param attackDown/useDown        whether the attack/use key is held this tick.
    /// @param attackPressed/usePressed  whether a genuine attack/use press landed this tick.
    public void onTick(int tick, int selectedSlot, ItemStack selectedStack, boolean attackDown, boolean useDown,
                       boolean attackPressed, boolean usePressed)
    {
        ModConfig.FlashRule rule = ruleFor(selectedStack);
        boolean validSlot = selectedSlot >= 0 && selectedSlot < HOTBAR_SLOTS;
        boolean onAttack = rule != null && rule.flashesAt.flashesOnAttack();
        boolean onUse = rule != null && rule.flashesAt.flashesOnUse();

        boolean pressed = validSlot && ((onAttack && attackPressed) || (onUse && usePressed));
        boolean keyHeld = (onAttack && attackDown) || (onUse && useDown);

        if (pressed)
        {
            // Rule 1 + 2: a press (re)lights this slot's own floor and, if the key is down, begins the hold here.
            this.slotArgb[selectedSlot] = argbOf(rule);
            this.floorEndTick[selectedSlot] = tick + FLASH_TICKS;
            this.heldSlot = keyHeld ? selectedSlot : NO_SLOT;
        }
        else if (validSlot && keyHeld && selectedSlot == this.heldSlot)
        {
            // Rule 3: same pressed slot still selected and key still held — keep sustaining it (floor already set).
        }
        else
        {
            // Key released, or the selection moved off the pressed slot: end the hold. Floor tails decay on their own.
            this.heldSlot = NO_SLOT;
        }
    }

    /// Reset all flashes (e.g. when leaving a world).
    public void clear()
    {
        Arrays.fill(this.floorEndTick, NO_TICK);
        Arrays.fill(this.slotArgb, DEFAULT_ARGB);
        this.heldSlot = NO_SLOT;
    }

    /// @return whether {@code slot} is lit at {@code currentTick} — sustained by a hold, or within its floor.
    public boolean isActive(int slot, int currentTick)
    {
        if (slot < 0 || slot >= HOTBAR_SLOTS) return false;
        return slot == this.heldSlot || currentTick < this.floorEndTick[slot];
    }

    /// @return the silhouette tint (ARGB, opacity in the alpha byte) for {@code slot}.
    public int argbFor(int slot)
    {
        return (slot >= 0 && slot < HOTBAR_SLOTS) ? this.slotArgb[slot] : DEFAULT_ARGB;
    }

    private static int argbOf(ModConfig.FlashRule rule)
    {
        int alpha = (rule.opacity != null ? rule.opacity : ModConfig.FlashOpacity.HIGH).alpha();
        return (alpha << 24) | GLOW_RGB;
    }

    /// @return the configured rule matching {@code stack}, or {@code null} if none flashes it.
    private static ModConfig.FlashRule ruleFor(ItemStack stack)
    {
        if (stack == null || stack.isEmpty()) return null;

        Identifier held = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (held == null) return null;

        for (ModConfig.FlashRule rule : ModConfig.get().clickFlashRules)
        {
            if (rule == null || rule.item == null || rule.flashesAt == null) continue;
            if (held.equals(Identifier.tryParse(rule.item))) return rule;
        }
        return null;
    }
}

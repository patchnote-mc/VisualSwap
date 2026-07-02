package com.patchnote.visualswap.client.hud.click;

import java.util.Arrays;

import com.patchnote.visualswap.client.config.ModConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/// White-silhouette flash of the clicked hotbar item. Only items listed in {@link ModConfig#clickFlashRules} flash,
/// each on its configured input(s).
///
/// Two independent mechanisms, so that fast combos read correctly (see attribute-swap-mechanic.txt):
///
///   - CLICK FLOOR (per slot, independent): an actual attack/use *press* lights that slot for a minimum of
///     {@link #FLASH_TICKS}. Floors are keyed to the slot and never cleared by a later swap, so two consecutive
///     clicks on two different items — a stun-slam — leave two independent flashes on their own timelines.
///     Only a genuine press arms a floor; merely holding the key (or an auto-swing) never does.
///
///   - HELD WHITE (single, follows the selection): while the key is held on the selected configured item, that
///     slot stays lit past the floor. Because it tracks the *current* selection, swapping to another item drops
///     the previous item's held-white immediately — an item you only swapped past (never clicked) never lingers.
///
/// A press therefore flashes for >= {@link #FLASH_TICKS}; holding past that keeps it lit until the key is released.
public final class ClickFlash
{
    public static final ClickFlash INSTANCE = new ClickFlash();

    /// Silhouette colour (white); the per-flash alpha comes from the rule's opacity.
    public static final int GLOW_RGB = 0xFFFFFF;

    /// Minimum ticks a press stays lit, regardless of how briefly the key was held.
    private static final int FLASH_TICKS = 5;

    private static final int NO_TICK = Integer.MIN_VALUE;
    private static final int NO_SLOT = -1;
    private static final int HOTBAR_SLOTS = 9;
    private static final int DEFAULT_ARGB = 0xFF000000 | GLOW_RGB;

    /// Per-slot minimum-flash floors (tick each expires, exclusive) and their tint. Independent across slots.
    private final int[] floorUntilTick = new int[HOTBAR_SLOTS];
    private final int[] floorArgb = new int[HOTBAR_SLOTS];

    /// The single slot whose key is currently held (or {@link #NO_SLOT}); follows the selection.
    private int heldSlot = NO_SLOT;
    private int heldArgb = DEFAULT_ARGB;

    private ClickFlash()
    {
        Arrays.fill(this.floorUntilTick, NO_TICK);
        Arrays.fill(this.floorArgb, DEFAULT_ARGB);
    }

    /// Drive once per client tick.
    ///
    /// @param attackDown/useDown whether the attack/use key is held this tick.
    /// @param attackEdge/useEdge whether a fresh attack/use press began this tick (arms the per-slot floor).
    public void onTick(int tick, int selectedSlot, ItemStack selectedStack, boolean attackDown, boolean useDown,
                       boolean attackEdge, boolean useEdge)
    {
        ModConfig.FlashRule rule = ruleFor(selectedStack);
        boolean heldNow = false;

        if (rule != null && selectedSlot >= 0 && selectedSlot < HOTBAR_SLOTS)
        {
            boolean attackFlashes = rule.flashesAt.flashesOnAttack();
            boolean useFlashes = rule.flashesAt.flashesOnUse();
            heldNow = (attackFlashes && attackDown) || (useFlashes && useDown);
            boolean fired = (attackFlashes && attackEdge) || (useFlashes && useEdge);

            int alpha = (rule.opacity != null ? rule.opacity : ModConfig.FlashOpacity.HIGH).alpha();
            int argb = (alpha << 24) | GLOW_RGB;
            if (fired)
            {
                this.floorUntilTick[selectedSlot] = tick + FLASH_TICKS;
                this.floorArgb[selectedSlot] = argb;
            }
            if (heldNow) this.heldArgb = argb;
        }

        // Held-white tracks the current selection; swapping/scrolling away drops the previous slot's hold at once.
        this.heldSlot = heldNow ? selectedSlot : NO_SLOT;
    }

    public void clear()
    {
        Arrays.fill(this.floorUntilTick, NO_TICK);
        Arrays.fill(this.floorArgb, DEFAULT_ARGB);
        this.heldSlot = NO_SLOT;
        this.heldArgb = DEFAULT_ARGB;
    }

    /// @return whether {@code slot} is flashing at {@code currentTick} (held, or within its own minimum-flash floor).
    public boolean isActive(int slot, int currentTick)
    {
        if (slot < 0 || slot >= HOTBAR_SLOTS) return false;
        return slot == this.heldSlot || currentTick < this.floorUntilTick[slot];
    }

    /// @return the silhouette tint (ARGB) for {@code slot}, carrying that flash's opacity.
    public int argbFor(int slot)
    {
        if (slot == this.heldSlot) return this.heldArgb;
        return (slot >= 0 && slot < HOTBAR_SLOTS) ? this.floorArgb[slot] : DEFAULT_ARGB;
    }

    /// @return the configured rule whose item matches {@code stack}, or {@code null} if none flashes it.
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

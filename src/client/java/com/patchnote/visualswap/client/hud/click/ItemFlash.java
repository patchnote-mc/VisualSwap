package com.patchnote.visualswap.client.hud.click;

import com.patchnote.visualswap.client.config.ModConfig;
import com.patchnote.visualswap.client.config.ModConfig.FlashOpacity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

import static com.patchnote.visualswap.client.utils.Constants.*;

/// White tint flash on a clicked hotbar item
public final class ItemFlash
{
    public static final ItemFlash INSTANCE = new ItemFlash();

    public static final int TINT_RGB = 0xFFFFFF;
    private static final int FLASH_VISIBLE_TICKS = 5;

    private static final int DEFAULT_ARGB = 0xFF000000 | TINT_RGB;

    private final int[] slotsExpirationTick = new int[HOTBAR_SLOTS];
    private final int[] slotsTint = new int[HOTBAR_SLOTS];
    private int heldSlot;

    private ItemFlash() { reset(); }

    /// Call on Client Tick
    public void onTick(int tick, int currentSlot, ItemStack selectedStack, boolean attackDown, boolean useDown,
                       boolean attackPressed, boolean usePressed)
    {
        ModConfig.FlashRule rule = getRuleFor(selectedStack);
        boolean validSlot = currentSlot >= 0 && currentSlot < HOTBAR_SLOTS;

        if (rule == null || !validSlot) return;

        boolean attackFlashActive = rule.flashesAt.flashesOnAttack();
        boolean useFlashActive = rule.flashesAt.flashesOnUse();

        boolean pressed = (attackFlashActive && attackPressed) || (useFlashActive && usePressed);
        boolean keyHeld = (attackFlashActive && attackDown) || (useFlashActive && useDown);

        if (pressed)
        {
            this.slotsTint[currentSlot] = calculateTintFor(rule);
            this.slotsExpirationTick[currentSlot] = tick + FLASH_VISIBLE_TICKS;
            this.heldSlot = keyHeld ? currentSlot : NO_SLOT;
        }
        else
        {
            this.heldSlot = NO_SLOT;
        }
    }

    /* API */

    /// @return whether {@code slot} is tinted at {@code currentTick}
    public boolean isActive(int slot, int currentTick)
    {
        if (slot < 0 || slot >= HOTBAR_SLOTS) return false;
        return slot == this.heldSlot || currentTick < this.slotsExpirationTick[slot];
    }

    /// @return the tint for {@code slot}.
    public int getTintFor(int slot) { return (slot >= 0 && slot < HOTBAR_SLOTS) ? this.slotsTint[slot] : DEFAULT_ARGB; }

    public void reset()
    {
        Arrays.fill(this.slotsExpirationTick, NO_TICK);
        Arrays.fill(this.slotsTint, DEFAULT_ARGB);
        this.heldSlot = NO_SLOT;
    }

    /* HELPERS */

    private static int calculateTintFor(ModConfig.FlashRule rule)
    {
        int alpha = (rule.opacity != null) ? rule.opacity.alpha() : FlashOpacity.HIGH.alpha();
        return (alpha << 24) | TINT_RGB;
    }

    private static ModConfig.@Nullable FlashRule getRuleFor(ItemStack stack)
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

package com.patchnote.visualswap.client.hud.click;

import com.patchnote.visualswap.client.config.ModConfig;
import com.patchnote.visualswap.client.config.models.FlashIntensity;
import com.patchnote.visualswap.client.config.models.FlashRule;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

import static com.patchnote.visualswap.client.utils.Constants.*;

/// Per-item, gamma-shaded tint flash on a clicked hotbar item
public final class ItemFlash
{
    public static final ItemFlash INSTANCE = new ItemFlash();

    public static final int TINT_RGB = 0xFFFFFF;
    private static final int FLASH_VISIBLE_TICKS = 5;

    /// The shade exponent (1/gamma; see {@link FlashIntensity}) rides in the tint int's alpha byte, {@code 0..1}
    /// mapped onto {@code 0..255} — the silhouette shader reads it straight back as the exponent (the output alpha
    /// comes from the texture mask, so that channel is free). Exponent {@code 0} is a flat fill: every opaque pixel
    /// becomes the full tint colour.
    private static final int DEFAULT_ARGB = packTint(TINT_RGB, FlashIntensity.HIGH.getShadeExponent());

    private final int[] slotsExpirationTick = new int[HOTBAR_SLOTS];
    private final int[] slotsTint = new int[HOTBAR_SLOTS];
    private int heldSlot;

    private ItemFlash() { reset(); }

    /// Call on Client Tick
    public void onTick(int tick, int currentSlot, ItemStack selectedStack, boolean attackDown, boolean useDown,
                       boolean attackPressed, boolean usePressed)
    {
        // Resolve attack and use independently so two rules for one item can each cover their own input (e.g. Attack +
        // Use both apply). A config-screen/load-time conflict block keeps two rules from ever claiming the same input.
        FlashRule attackRule = getRuleFor(selectedStack, true);
        FlashRule useRule = getRuleFor(selectedStack, false);
        boolean validSlot = currentSlot >= 0 && currentSlot < HOTBAR_SLOTS;

        boolean attackPressedThisTick = validSlot && attackRule != null && attackPressed;
        boolean usePressedThisTick = validSlot && useRule != null && usePressed;
        boolean keyHeldThisTick = (attackRule != null && attackDown) || (useRule != null && useDown);

        if (attackPressedThisTick || usePressedThisTick)
        {
            // A press (re)lights this slot's own timeline and, if the key is down, begins the hold here.
            FlashRule active = attackPressedThisTick ? attackRule : useRule;
            this.slotsTint[currentSlot] = calculateTintFor(active);
            this.slotsExpirationTick[currentSlot] = tick + FLASH_VISIBLE_TICKS;
            this.heldSlot = keyHeldThisTick ? currentSlot : NO_SLOT;
        }
        else if (validSlot && keyHeldThisTick && currentSlot == this.heldSlot)
        {
            // Hold sustains its own slot: the pressed slot is still selected and its key still held — keep it lit.
        }
        else
        {
            // Key released, or the selection moved off the pressed slot: end the hold. Timeline tails decay on their own.
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

    private static int calculateTintFor(FlashRule rule)
    {
        FlashIntensity intensity = (rule.intensity() != null) ? rule.intensity() : FlashIntensity.HIGH;
        int color = rule.colorFor(ModConfig.get().preset);
        return packTint(color, intensity.getShadeExponent());
    }

    /// Packs a per-item tint: RGB in the low 24 bits, the shade exponent (see {@link FlashIntensity}) encoded into the
    /// alpha byte as {@code exponent * 255}.
    public static int packTint(int color, double shadeExponent)
    {
        int exponentByte = Math.clamp((int) Math.round(shadeExponent * 255.0), 0, 255);
        return (exponentByte << 24) | (color & 0xFFFFFF);
    }

    /// The first rule for {@code stack}'s item that flashes on the given input — attack when {@code forAttack}, else use
    /// — or null when none matches. Resolving each input separately lets two rules for one item cover different inputs.
    private static @Nullable FlashRule getRuleFor(ItemStack stack, boolean forAttack)
    {
        if (stack == null || stack.isEmpty()) return null;

        Identifier held = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (FlashRule rule : ModConfig.get().clickFlashRules)
        {
            if (rule == null || rule.item() == null || rule.flashesAt() == null) continue;
            boolean covers = forAttack ? rule.flashesAt().flashesOnAttack() : rule.flashesAt().flashesOnUse();
            if (covers && held.equals(Identifier.tryParse(rule.item()))) return rule;
        }
        return null;
    }
}

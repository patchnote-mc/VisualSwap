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

    /// The tint gamma (see {@link FlashIntensity}) rides in the tint int's alpha byte as
    /// {@code gamma / GAMMA_ENCODE_MAX} — the silhouette shader derives its output alpha from the texture mask, so that
    /// channel is free. Must match the {@code * 8.0} decode in {@code white_silhouette.fsh}.
    public static final double GAMMA_ENCODE_MAX = 8.0;

    private static final int DEFAULT_ARGB = packTint(TINT_RGB, FlashIntensity.HIGH.getGamma());

    private final int[] slotsExpirationTick = new int[HOTBAR_SLOTS];
    private final int[] slotsTint = new int[HOTBAR_SLOTS];
    private int heldSlot;

    private ItemFlash() { reset(); }

    /// Call on Client Tick
    public void onTick(int tick, int currentSlot, ItemStack selectedStack, boolean attackDown, boolean useDown,
                       boolean attackPressed, boolean usePressed)
    {
        FlashRule rule = getRuleFor(selectedStack);
        boolean validSlot = currentSlot >= 0 && currentSlot < HOTBAR_SLOTS;
        //@formatter:off
        boolean attackFlashActive = rule != null && rule.flashesAt().flashesOnAttack();
        boolean useFlashActive = rule != null && rule.flashesAt().flashesOnUse();
        //@formatter:on

        boolean pressedThisTick = validSlot && ((attackFlashActive && attackPressed) || (useFlashActive && usePressed));
        boolean keyHeldThisTick = (attackFlashActive && attackDown) || (useFlashActive && useDown);

        if (pressedThisTick)
        {
            this.slotsTint[currentSlot] = calculateTintFor(rule);
            this.slotsExpirationTick[currentSlot] = tick + FLASH_VISIBLE_TICKS;
            this.heldSlot = keyHeldThisTick ? currentSlot : NO_SLOT;
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

    private static int calculateTintFor(FlashRule rule)
    {
        FlashIntensity intensity = (rule.intensity() != null) ? rule.intensity() : FlashIntensity.HIGH;
        return packTint(rule.color(), intensity.getGamma());
    }

    /// Packs a per-item tint: RGB in the low 24 bits, the gamma encoded into the alpha byte (see
    /// {@link #GAMMA_ENCODE_MAX}).
    private static int packTint(int color, double gamma)
    {
        int gammaByte = Math.clamp((int) Math.round(gamma / GAMMA_ENCODE_MAX * 255.0), 0, 255);
        return (gammaByte << 24) | (color & 0xFFFFFF);
    }

    private static @Nullable FlashRule getRuleFor(ItemStack stack)
    {
        if (stack == null || stack.isEmpty()) return null;

        Identifier held = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (FlashRule rule : ModConfig.get().clickFlashRules)
        {
            if (rule == null || rule.item() == null || rule.flashesAt() == null) continue;
            if (held.equals(Identifier.tryParse(rule.item()))) return rule;
        }
        return null;
    }
}

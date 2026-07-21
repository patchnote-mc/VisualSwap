package com.patchnote.visualswap.client.config.models;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/// Which input(s) make a {@link FlashRule}'s item flash.
public enum FlashTrigger
{
    ATTACK,
    USE,
    BOTH;

    public boolean flashesOnAttack() { return this != USE; }

    public boolean flashesOnUse() { return this != ATTACK; }

    /* HELPERS */

    public Component getNameComponent()
    {
        return Component.translatable("gui.visual-swap.trigger." + name().toLowerCase(Locale.ROOT));
    }
}

package com.patchnote.visualswap.client.config.models;

import net.minecraft.network.chat.Component;

/// Which input(s) make a {@link FlashRule}'s item flash.
public enum FlashTrigger
{
    ATTACK,
    USE,
    BOTH;

    public boolean flashesOnAttack() { return this != USE; }

    public boolean flashesOnUse() { return this != ATTACK; }

    /* HELPERS */

    public String getName()
    {
        return switch (this)
        {
            case ATTACK -> "Attack";
            case USE -> "Use";
            case BOTH -> "Both";
        };
    }

    public Component getNameComponent()
    {
        return switch (this)
        {
            case ATTACK -> Component.literal("Attack");
            case USE -> Component.literal("Use");
            case BOTH -> Component.literal("Both");
        };
    }
}

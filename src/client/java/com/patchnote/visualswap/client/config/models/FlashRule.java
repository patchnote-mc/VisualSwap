package com.patchnote.visualswap.client.config.models;

import java.util.ArrayList;
import java.util.List;

/// An item identifier plus when/how it flashes, and the colour it flashes.
public final class FlashRule
{
    public static final int DEFAULT_COLOR = 0xFFFFFFFF;

    private String item;
    private FlashTrigger flashesAt = FlashTrigger.BOTH;
    private FlashIntensity intensity = FlashIntensity.LOW;
    private int color = DEFAULT_COLOR;

    public FlashRule(String item, FlashTrigger flashesAt, FlashIntensity intensity)
    {
        this(item, flashesAt, intensity, DEFAULT_COLOR);
    }

    public FlashRule(String item, FlashTrigger flashesAt, FlashIntensity intensity, int color)
    {
        this.item = item;
        this.flashesAt = flashesAt;
        this.intensity = intensity;
        this.color = color;
    }

    /* GETTERS & SETTERS */

    public String item() { return item; }

    public FlashTrigger flashesAt() { return flashesAt; }

    public FlashIntensity intensity() { return intensity; }

    public int color() { return color; }

    public FlashRule setItem(String item)
    {
        this.item = item;
        return this;
    }

    public FlashRule setFlashesAt(FlashTrigger flashesAt)
    {
        this.flashesAt = flashesAt;
        return this;
    }

    public FlashRule setIntensity(FlashIntensity intensity)
    {
        this.intensity = intensity;
        return this;
    }

    public FlashRule setColor(int color)
    {
        this.color = color;
        return this;
    }

    /* HELPERS */

    /// Fills in fields left null/absent when an older config is loaded (Gson bypasses field initializers, so a
    /// pre-intensity/pre-colour schema deserialises with a null {@code intensity} and a 0 {@code color}). A null
    /// {@code intensity} is the tell-tale of such a rule, so its colour is reset to the default too.
    public void normalize()
    {
        if (this.flashesAt == null) this.flashesAt = FlashTrigger.BOTH;
        if (this.intensity == null)
        {
            this.intensity = FlashIntensity.LOW;
            this.color = DEFAULT_COLOR;
        }
    }

    public static List<FlashRule> defaultFlashRules()
    {
        List<FlashRule> rules = new ArrayList<>(25); // (7 * 3) + 4
        String[] materials = {"wooden", "stone", "copper", "golden", "iron", "diamond", "netherite"};

        // swords
        for (String material : materials)
            addRule(rules, "minecraft:" + material + "_sword", FlashTrigger.ATTACK, FlashIntensity.LOW);

        // axes
        for (String material : materials)
            addRule(rules, "minecraft:" + material + "_axe", FlashTrigger.ATTACK, FlashIntensity.LOW);

        // spear
        for (String material : materials)
            addRule(rules, "minecraft:" + material + "_spear", FlashTrigger.BOTH, FlashIntensity.HIGH);

        addRule(rules, "minecraft:mace", FlashTrigger.ATTACK, FlashIntensity.HIGH);
        addRule(rules, "minecraft:trident", FlashTrigger.ATTACK, FlashIntensity.LOW);
        addRule(rules, "minecraft:ender_pearl", FlashTrigger.USE, FlashIntensity.LOW);
        addRule(rules, "minecraft:wind_charge", FlashTrigger.USE, FlashIntensity.LOW);

        return rules;
    }

    private static void addRule(List<FlashRule> list, String item, FlashTrigger flashesAt, FlashIntensity intensity)
    {
        list.add(new FlashRule(item, flashesAt, intensity));
    }
}

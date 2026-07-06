package com.patchnote.visualswap.client.config.models;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/// An item identifier plus when/how it flashes, and the tint colour it flashes under each {@link PresetType}.
public final class FlashRule
{
    private String item;
    private FlashTrigger flashesAt = FlashTrigger.BOTH;
    private FlashIntensity intensity = FlashIntensity.LOW;

    /// One tint colour per preset (packed ARGB; only the RGB is used at render — the alpha byte carries the flash
    /// gamma). Only the Custom slot is user-editable; the others always resolve to {@link PresetType#getFlashTint()}
    /// (see {@link #colorFor}).
    private Map<PresetType, Integer> colors;

    public FlashRule(String item, FlashTrigger flashesAt, FlashIntensity intensity)
    {
        this.item = item;
        this.flashesAt = flashesAt;
        this.intensity = intensity;
        this.colors = defaultColors();
    }

    /// Deep copy — the working copies the config screen edits must not share the colour map with the committed rule.
    public FlashRule(FlashRule other)
    {
        this.item = other.item;
        this.flashesAt = other.flashesAt;
        this.intensity = other.intensity;
        this.colors = copyColors(other.colors);
    }

    /* GETTERS & SETTERS */

    public String item() { return item; }

    public FlashTrigger flashesAt() { return flashesAt; }

    public FlashIntensity intensity() { return intensity; }

    /// The tint colour to use under {@code preset}. Non-editable presets always resolve to their current default, so
    /// changing a default never leaves a stale stored value behind.
    public int colorFor(PresetType preset)
    {
        if (!preset.isColorEditable()) return preset.getFlashTint();
        Integer c = (colors != null) ? colors.get(preset) : null;
        return (c != null) ? c : preset.getFlashTint();
    }

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

    /// Sets the tint for {@code preset}. No-op for non-editable presets (only Custom is user-editable).
    public FlashRule setColorFor(PresetType preset, int color)
    {
        if (!preset.isColorEditable()) return this;
        ensureColors();
        colors.put(preset, color);
        return this;
    }

    /* HELPERS */

    /// Repairs a rule loaded from an older/partial config: Gson bypasses field initializers, so a pre-intensity /
    /// pre-colours schema deserialises with null/absent fields (and any concrete map type).
    public void normalize()
    {
        if (this.flashesAt == null) this.flashesAt = FlashTrigger.BOTH;
        if (this.intensity == null) this.intensity = FlashIntensity.LOW;
        ensureColors();
    }

    private void ensureColors()
    {
        this.colors = copyColors(this.colors);
    }

    /// Rebuilds {@code src} into a complete {@link EnumMap} — preserves any present entries and fills every missing
    /// preset with its default, so callers always get a full, well-typed map (Gson may hand back a plain LinkedHashMap
    /// or null).
    private static Map<PresetType, Integer> copyColors(Map<PresetType, Integer> src)
    {
        EnumMap<PresetType, Integer> map = new EnumMap<>(PresetType.class);
        if (src != null)
        {
            // Skip null keys/values: Gson deserialises an unknown preset name (hand-edited or renamed) to a null key,
            // which EnumMap.putAll would NPE on.
            for (Map.Entry<PresetType, Integer> e : src.entrySet())
                if (e.getKey() != null && e.getValue() != null) map.put(e.getKey(), e.getValue());
        }
        for (PresetType p : PresetType.values()) map.putIfAbsent(p, p.getFlashTint());
        return map;
    }

    private static Map<PresetType, Integer> defaultColors()
    {
        EnumMap<PresetType, Integer> map = new EnumMap<>(PresetType.class);
        for (PresetType p : PresetType.values()) map.put(p, p.getFlashTint());
        return map;
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

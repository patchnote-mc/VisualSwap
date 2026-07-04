package com.patchnote.visualswap.client.config;

import com.patchnote.visualswap.VisualSwap;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry.Gui.EnumHandler;
import me.shedaniel.autoconfig.annotation.ConfigEntry.Gui.EnumHandler.EnumDisplayOption;
import me.shedaniel.autoconfig.annotation.ConfigEntry.Gui.Excluded;
import me.shedaniel.autoconfig.annotation.ConfigEntry.Gui.Tooltip;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Config(name = VisualSwap.MOD_ID)
public final class ModConfig implements ConfigData
{
    @Tooltip
    @EnumHandler(option = EnumDisplayOption.BUTTON)
    public Preset preset = Preset.VANILLA;

    /// Colors for {@link Preset#CUSTOM} (ARGB). {@code from} tints the source slot and failure glyphs, {@code to}
    /// tints the destination slot and success glyphs, and chain gradients interpolate {@code from -> to}. Ignored
    /// unless {@link #preset} is {@link Preset#CUSTOM}.
    public int customColorFrom = 0xFFFF5555;
    public int customColorTo = 0xFF55FF55;

    /// Per-preset size multiplier, keyed by {@link Preset#name()} (a plain String map so toml4j round-trips it
    /// cleanly). The single source of truth — read via {@link #sizeMultiplier(Preset)}, which falls back to the
    /// preset's {@link Preset#defaultSizeMultiplier()} for any absent key, so an untouched preset scales by its
    /// default (1.0 for practice/custom = no scaling). Hidden from the (unused) auto GUI; the custom screen edits it.
    @Excluded public Map<String, Float> sizeByPreset = defaultSizes();

    /// Which held items flash their hotbar slot on click, on which input, and how strongly. GUI is handled by a custom
    /// screen (added later); hidden from the auto-generated one but still persisted.
    @Excluded public List<FlashRule> clickFlashRules = defaultFlashRules();


    /// Which input(s) make a {@link FlashRule}'s item flash.
    public enum FlashOn
    {
        ATTACK,
        USE,
        BOTH;

        public boolean flashesOnAttack() { return this != USE; }

        public boolean flashesOnUse() { return this != ATTACK; }
    }

    /// Flash strength, mapped to the silhouette's alpha (0-255).
    public enum FlashOpacity
    {
        LOW((byte) 0xC0),
        HIGH((byte) 0xFF);

        private final byte alpha;

        FlashOpacity(byte alpha) { this.alpha = alpha; }

        public byte alpha() { return this.alpha; }
    }

    /// One entry of {@link #clickFlashRules}: an item identifier plus when/how it flashes.
    public static final class FlashRule
    {
        public String item;
        public FlashOn flashesAt;
        public FlashOpacity opacity;

        public FlashRule(String item, FlashOn flashesAt, FlashOpacity opacity)
        {
            this.item = item;
            this.flashesAt = flashesAt;
            this.opacity = opacity;
        }
    }

    private static List<FlashRule> defaultFlashRules()
    {
        List<FlashRule> rules = new ArrayList<>();
        String[] materials = {"wooden", "stone", "copper", "golden", "iron", "diamond", "netherite"};

        for (String material : materials) addRule(rules, material + "_sword", FlashOn.ATTACK, FlashOpacity.LOW);
        for (String material : materials) addRule(rules, material + "_axe", FlashOn.ATTACK, FlashOpacity.LOW);
        addRule(rules, "mace", FlashOn.ATTACK, FlashOpacity.HIGH);
        addRule(rules, "trident", FlashOn.ATTACK, FlashOpacity.LOW);

        addRule(rules, "ender_pearl", FlashOn.USE, FlashOpacity.LOW);
        addRule(rules, "wind_charge", FlashOn.USE, FlashOpacity.LOW);

        for (String material : materials) addRule(rules, material + "_spear", FlashOn.BOTH, FlashOpacity.LOW);

        return rules;
    }

    private static void addRule(List<FlashRule> rules, String path, FlashOn flashesAt, FlashOpacity opacity)
    {
        rules.add(new FlashRule("minecraft:" + path, flashesAt, opacity));
    }


    /// A named look for the swap indicator: a color scheme plus a default size multiplier. {@link #VANILLA} and
    /// {@link #PRACTICE} carry fixed color schemes; {@link #CUSTOM} draws its colors from
    /// {@link ModConfig#customColorFrom}/{@link ModConfig#customColorTo}. The enum is immutable — the live, editable
    /// size lives in {@link ModConfig#sizeByPreset}; each constant only supplies the default.
    public enum Preset
    {
        VANILLA(0.8f, "Vanilla"),
        PRACTICE(1.0f, "Practice"),
        CUSTOM(1.0f, "Custom");

        private final float defaultSizeMultiplier;
        private final String displayName;

        Preset(float defaultSizeMultiplier, String displayName)
        {
            this.defaultSizeMultiplier = defaultSizeMultiplier;
            this.displayName = displayName;
        }

        public float defaultSizeMultiplier() { return this.defaultSizeMultiplier; }

        public String displayName() { return this.displayName; }

        public boolean is(Preset other) { return this == other; }

        public boolean isVanilla() { return this == VANILLA; }

        public boolean isPractice() { return this == PRACTICE; }

        public boolean isCustom() { return this == CUSTOM; }
    }

    /// The size multiplier for a given preset, or its default when unset.
    public float sizeMultiplier(Preset preset)
    {
        Float value = this.sizeByPreset.get(preset.name());
        return value != null ? value : preset.defaultSizeMultiplier();
    }

    /// The size multiplier for the currently-active preset.
    public float sizeMultiplier() { return sizeMultiplier(this.preset); }

    public void setSizeMultiplier(Preset preset, float value) { this.sizeByPreset.put(preset.name(), value); }

    private static Map<String, Float> defaultSizes()
    {
        Map<String, Float> sizes = new LinkedHashMap<>();
        for (Preset preset : Preset.values()) sizes.put(preset.name(), preset.defaultSizeMultiplier());
        return sizes;
    }

    public static ModConfig get() { return AutoConfig.getConfigHolder(ModConfig.class).getConfig(); }
}

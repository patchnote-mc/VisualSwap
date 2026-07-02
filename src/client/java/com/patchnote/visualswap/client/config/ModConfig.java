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
import java.util.List;

@Config(name = VisualSwap.MOD_ID)
public final class ModConfig implements ConfigData
{
    @Tooltip
    @EnumHandler(option = EnumDisplayOption.BUTTON)
    public IndicatorType indicatorType = IndicatorType.VANILLA;

    public float vanillaSizeMultiplier = 0.8f;

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
        LOW(0xAA),
        HIGH(0xFF);

        private final int alpha;

        FlashOpacity(int alpha) { this.alpha = alpha; }

        public int alpha() { return this.alpha; }
    }

    /// One entry of {@link #clickFlashRules}: an item identifier plus when/how it flashes.
    public static final class FlashRule
    {
        public String item;
        public FlashOn flashesAt;
        public FlashOpacity opacity;

        public FlashRule() { this("", FlashOn.ATTACK, FlashOpacity.HIGH); }

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

        addRule(rules, "ender_pearl", FlashOn.USE, FlashOpacity.LOW);
        addRule(rules, "wind_charge", FlashOn.USE, FlashOpacity.LOW);

        for (String material : materials) addRule(rules, material + "_spear", FlashOn.BOTH, FlashOpacity.LOW);

        return rules;
    }

    private static void addRule(List<FlashRule> rules, String path, FlashOn flashesAt, FlashOpacity opacity)
    {
        rules.add(new FlashRule("minecraft:" + path, flashesAt, opacity));
    }


    public enum IndicatorType
    {
        VANILLA,
        PRACTICE;

        public boolean is(IndicatorType other)
        {
            return this.equals(other);
        }

        public boolean isVanilla() { return is(VANILLA); }

        public boolean isPractice() { return is(PRACTICE); }
    }

    public static ModConfig get() { return AutoConfig.getConfigHolder(ModConfig.class).getConfig(); }
}

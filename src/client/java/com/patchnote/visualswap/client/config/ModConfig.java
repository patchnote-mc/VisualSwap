package com.patchnote.visualswap.client.config;

import com.patchnote.visualswap.VisualSwap;
import com.patchnote.visualswap.client.config.models.FlashRule;
import com.patchnote.visualswap.client.config.models.Preset;
import com.patchnote.visualswap.client.config.models.PresetType;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Config(name = VisualSwap.MOD_ID)
public final class ModConfig implements ConfigData
{
    /// Inclusive range the clicked-item flash duration is clamped to (whole ticks).
    public static final int MIN_VISIBLE_TICKS = 2;
    public static final int MAX_VISIBLE_TICKS = 10;

    /* CONFIG */

    /// Which preset is active (identity only — serializes as its name).
    public PresetType preset = PresetType.VANILLA;

    /// How many ticks a clicked item's flash tint stays visible.
    public int flashVisibleTicks = 5;

    /// When true the item flash (and its per-rule hotbar effects) only fire when the click lands inside a swap window —
    /// i.e. right after switching to the item (an actual attribute swap). When false the flash fires on every matching
    /// attack/use, regardless of a recent switch. Does not affect the hotbar highlight, which is always swap-driven.
    public boolean flashOnlyOnSwap = true;

    /* MASTER TOGGLES */

    /// Master switch: when false the mod does nothing at all — no swap detection, HUD effects, or particles.
    public boolean modEnabled = true;

    /// Umbrella for the on-screen HUD effects — the swap-hit glyph plus the hotbar-highlight and item-flash finer
    /// switches below. When false none of them draw, regardless of the finer switches.
    public boolean hudEnabled = true;

    /// Whether the hotbar slot highlight is drawn (finer switch, gated by {@link #hudEnabled}).
    public boolean hotbarHighlightEnabled = true;

    /// Whether the clicked-item tint flash is drawn (finer switch, gated by {@link #hudEnabled}).
    public boolean itemFlashEnabled = true;

    /// Whether the swap-hit particle burst is spawned. When false, no swap particles are emitted.
    public boolean particlesEnabled = true;

    /// The Custom preset's editable settings. A real data object (not an enum) so its fields persist; Vanilla/Practice
    /// use their fixed {@link PresetType} defaults instead. Only touched when the active preset is Custom.
    public Preset customPresetData = PresetType.CUSTOM.createDefault();

    public List<FlashRule> clickFlashRules = FlashRule.defaultFlashRules();

    /* HELPERS */

    public static ModConfig get() { return AutoConfig.getConfigHolder(ModConfig.class).getConfig(); }

    /// HUD effects (the glyph) run only when both the master switch and the HUD umbrella are on.
    public boolean hudActive() { return this.modEnabled && this.hudEnabled; }

    /// The hotbar highlight runs only when the HUD is active and its finer switch is on.
    public boolean hotbarHighlightActive() { return hudActive() && this.hotbarHighlightEnabled; }

    /// The item flash runs only when the HUD is active and its finer switch is on.
    public boolean itemFlashActive() { return hudActive() && this.itemFlashEnabled; }

    /// Particles run only when both the master switch and the particle switch are on.
    public boolean particlesActive() { return this.modEnabled && this.particlesEnabled; }

    public int getFromColor() { return preset.isCustom() ? customPresetData.getFromColor() : preset.getFromColor(); }

    public int getToColor() { return preset.isCustom() ? customPresetData.getToColor() : preset.getToColor(); }

    public double getSize() { return preset.isCustom() ? customPresetData.getSizeMultiplier() : preset.getSize(); }

    /* VALIDATION */

    @Override
    public void validatePostLoad()
    {
        if (this.preset == null) this.preset = PresetType.VANILLA;
        if (this.customPresetData == null) this.customPresetData = PresetType.CUSTOM.createDefault();
        this.customPresetData.setSizeMultiplier(Math.clamp(this.customPresetData.getSizeMultiplier(), 0.10, 2.00));
        this.flashVisibleTicks = Math.clamp(this.flashVisibleTicks, MIN_VISIBLE_TICKS, MAX_VISIBLE_TICKS);

        if (this.clickFlashRules == null) this.clickFlashRules = FlashRule.defaultFlashRules();
        this.clickFlashRules.removeIf(Objects::isNull);
        for (FlashRule rule : this.clickFlashRules) rule.normalize();
        // Precedence is an explicit per-rule order (first match wins). Sort by it (stable) then re-stamp a dense
        // 0..n-1 sequence so a legacy or hand-edited file with absent/duplicate orders resolves deterministically.
        this.clickFlashRules.sort(Comparator.comparingInt(FlashRule::order));
        for (int i = 0; i < this.clickFlashRules.size(); i++) this.clickFlashRules.get(i).setOrder(i);
    }
}

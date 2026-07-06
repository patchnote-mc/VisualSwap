package com.patchnote.visualswap.client.config;

import com.patchnote.visualswap.VisualSwap;
import com.patchnote.visualswap.client.config.models.FlashRule;
import com.patchnote.visualswap.client.config.models.Preset;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;

import java.util.List;
import java.util.Objects;

@Config(name = VisualSwap.MOD_ID)
public final class ModConfig implements ConfigData
{
    /* CONFIG */

    public Preset preset = Preset.VANILLA;
    public List<FlashRule> clickFlashRules = FlashRule.defaultFlashRules();

    /* HELPERS */

    public static ModConfig get() { return AutoConfig.getConfigHolder(ModConfig.class).getConfig(); }

    /// Repair configs written by an older schema: Gson can leave the rule list null or its rules with null/absent
    /// fields (e.g. the pre-{@code intensity} / pre-{@code color} format), which would otherwise crash the config GUI.
    @Override
    public void validatePostLoad()
    {
        if (this.clickFlashRules == null)
        {
            this.clickFlashRules = FlashRule.defaultFlashRules();
            return;
        }
        this.clickFlashRules.removeIf(Objects::isNull);
        for (FlashRule rule : this.clickFlashRules) rule.normalize();
    }
}

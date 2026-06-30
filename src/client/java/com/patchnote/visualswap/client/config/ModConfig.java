package com.patchnote.visualswap.client.config;

import com.patchnote.visualswap.VisualSwap;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry.Gui.EnumHandler;
import me.shedaniel.autoconfig.annotation.ConfigEntry.Gui.EnumHandler.EnumDisplayOption;

@Config(name = VisualSwap.MOD_ID)
public final class ModConfig implements ConfigData
{
    @EnumHandler(option = EnumDisplayOption.BUTTON)
    public IndicatorType indicatorType = IndicatorType.VANILLA;

    /// @return the registered config instance (must be registered via {@link AutoConfig#register} first).
    public static ModConfig get()
    {
        return AutoConfig.getConfigHolder(ModConfig.class).getConfig();
    }

    public enum IndicatorType
    {
        VANILLA,
        PRACTICE
    }
}

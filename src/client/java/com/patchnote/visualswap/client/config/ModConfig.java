package com.patchnote.visualswap.client.config;

import com.patchnote.visualswap.VisualSwap;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry.Gui.EnumHandler;
import me.shedaniel.autoconfig.annotation.ConfigEntry.Gui.EnumHandler.EnumDisplayOption;
import me.shedaniel.autoconfig.annotation.ConfigEntry.Gui.Tooltip;

@Config(name = VisualSwap.MOD_ID)
public final class ModConfig implements ConfigData
{
    @Tooltip
    @EnumHandler(option = EnumDisplayOption.BUTTON)
    public IndicatorType indicatorType = IndicatorType.VANILLA;


    public enum IndicatorType
    {
        VANILLA,
        PRACTICE
    }

    public static ModConfig get() { return AutoConfig.getConfigHolder(ModConfig.class).getConfig(); }
}

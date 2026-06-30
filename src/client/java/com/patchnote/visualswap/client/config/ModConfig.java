package com.patchnote.visualswap.client.config;

import com.patchnote.visualswap.VisualSwap;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry.Gui.EnumHandler;
import me.shedaniel.autoconfig.annotation.ConfigEntry.Gui.EnumHandler.EnumDisplayOption;

@Config(name = VisualSwap.MOD_ID)
public final class ModConfig implements ConfigData
{
    @EnumHandler(option = EnumDisplayOption.DROPDOWN)
    public IndicatorType indicatorType = IndicatorType.VANILLA;

    public enum IndicatorType
    {
        VANILLA,
        TRAINING
    }
}

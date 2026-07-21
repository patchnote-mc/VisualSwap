package com.patchnote.visualswap.client.config.screen.widget;

import com.patchnote.visualswap.client.config.ModConfig;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

/// A slider selecting the clicked-item flash's visible duration, in whole ticks
/// ({@link ModConfig#MIN_VISIBLE_TICKS}..{@link ModConfig#MAX_VISIBLE_TICKS}).
public final class TicksSlider extends AbstractSliderButton
{
    private static final int MIN = ModConfig.MIN_VISIBLE_TICKS;
    private static final int MAX = ModConfig.MAX_VISIBLE_TICKS;

    private final IntConsumer onChange;

    public TicksSlider(int x, int y, int width, int height, int initial, IntConsumer onChange)
    {
        super(x, y, width, height, Component.empty(), toFraction(initial));
        this.onChange = onChange;
        updateMessage();
    }

    private static double toFraction(int ticks) { return (double) (Math.clamp(ticks, MIN, MAX) - MIN) / (MAX - MIN); }

    private int ticks() { return MIN + (int) Math.round(this.value * (MAX - MIN)); }

    @Override
    protected void updateMessage()
    {
        int t = ticks();
        setMessage(t == 1
                   ? Component.translatable("gui.visual-swap.slider.flash_duration_one", t)
                   : Component.translatable("gui.visual-swap.slider.flash_duration", t));
    }

    @Override
    protected void applyValue() { this.onChange.accept(ticks()); }
}

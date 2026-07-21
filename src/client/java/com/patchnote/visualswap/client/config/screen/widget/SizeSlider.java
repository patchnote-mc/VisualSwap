package com.patchnote.visualswap.client.config.screen.widget;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;

public final class SizeSlider extends AbstractSliderButton
{
    private static final double MIN = 0.10;
    private static final double MAX = 2.00;
    private static final double STEP = 0.05;

    private Component label;
    private final DoubleConsumer onChange;

    public SizeSlider(int x, int y, int width, int height, Component label, double initial, DoubleConsumer onChange)
    {
        super(x, y, width, height, Component.empty(), toFraction(initial));
        this.label = label;
        this.onChange = onChange;
        updateMessage();
    }

    /// Re-point the slider at a different preset's size without firing {@code onChange} (sets the raw value directly
    /// rather than via applyValue).
    public void update(Component label, double size)
    {
        this.label = label;
        this.value = toFraction(size);
        updateMessage();
    }

    private static double toFraction(double size) { return (Math.clamp(size, MIN, MAX) - MIN) / (MAX - MIN); }

    private double size()
    {
        double raw = MIN + this.value * (MAX - MIN);
        return Math.round(raw / STEP) * STEP;
    }

    @Override
    protected void updateMessage()
    {
        setMessage(Component.translatable("gui.visual-swap.slider.size", this.label, String.format("%.2f", size())));
    }

    @Override
    protected void applyValue() { this.onChange.accept(size()); }
}

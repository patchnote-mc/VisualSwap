package com.patchnote.visualswap.client.config.screen.widget;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;

public final class SizeSlider extends AbstractSliderButton
{
    private static final double MIN = 0.10;
    private static final double MAX = 2.00;
    private static final double STEP = 0.05;

    private final String label;
    private final DoubleConsumer onChange;

    public SizeSlider(int x, int y, int width, int height, String label, double initial, DoubleConsumer onChange)
    {
        super(x, y, width, height, Component.empty(), toFraction(initial));
        this.label = label;
        this.onChange = onChange;
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
        setMessage(Component.literal(String.format("%s size: %.2f×", this.label, size())));
    }

    @Override
    protected void applyValue() { this.onChange.accept(size()); }
}

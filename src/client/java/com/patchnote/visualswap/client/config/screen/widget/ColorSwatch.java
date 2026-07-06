package com.patchnote.visualswap.client.config.screen.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.IntSupplier;

/// A small, non-interactive square that fills its bounds with a live colour (re-read each frame) inside a 1px border.
/// There is no vanilla widget for this; everything else in the config screen reuses stock components.
public final class ColorSwatch extends AbstractWidget
{
    private final IntSupplier color;
    private final int borderColor;

    public ColorSwatch(int size, int borderColor, IntSupplier color)
    {
        super(0, 0, size, size, Component.empty());
        this.color = color;
        this.borderColor = borderColor;
        this.active = false;  // decorative only — no focus, no clicks
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a)
    {
        int x = getX();
        int y = getY();
        int s = this.width;
        g.fill(x - 1, y - 1, x + s + 1, y + s + 1, this.borderColor);
        g.fill(x, y, x + s, y + s, this.color.getAsInt());
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) { }
}

package com.patchnote.visualswap.client.screen.overlay;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.DoubleToIntFunction;

/// A horizontal 0..1 slider whose track is painted per-column from a fraction→ARGB function — one widget covers the hue
/// rainbow, contextual saturation/value ramps, and (with {@code checker}) the alpha ramp over a checkerboard.
final class GradientSlider extends AbstractWidget
{
    private static final int BORDER = 0xFF4A4A52;
    private static final int CHECKER_LIGHT = 0xFF9E9E9E;
    private static final int CHECKER_DARK = 0xFF6B6B6B;
    private static final int CHECKER_CELL = 3;

    private final DoubleSupplier value;
    private final DoubleConsumer onSet;
    private final DoubleToIntFunction trackColor;
    private final boolean checker;

    GradientSlider(int x, int y, int width, int height, DoubleSupplier value, DoubleConsumer onSet,
                   DoubleToIntFunction trackColor, boolean checker)
    {
        super(x, y, width, height, Component.empty());
        this.value = value;
        this.onSet = onSet;
        this.trackColor = trackColor;
        this.checker = checker;
    }

    @Override
    protected void renderWidget(@NonNull GuiGraphics g, int mouseX, int mouseY, float a)
    {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, BORDER);
        if (this.checker) extractChecker(g, x, y, w, h);

        int trackW = w - 1;
        for (int i = 0; i < trackW; i++)
        {
            g.fill(x + i, y, x + i + 1, y + h, this.trackColor.applyAsInt(i / (double) (trackW - 1)));
        }

        // thumb: a white notch with a dark outline, slightly taller than the track
        int thumbX = x + (int) Math.round(this.value.getAsDouble() * (trackW - 1));
        g.fill(thumbX - 2, y - 2, thumbX + 3, y + h + 2, 0xFF202024);
        g.fill(thumbX - 1, y - 2, thumbX + 2, y + h + 2, 0xFFF0F0F0);
    }

    private static void extractChecker(GuiGraphics g, int x, int y, int w, int h)
    {
        for (int cy = 0; cy < h; cy += CHECKER_CELL)
        {
            for (int cx = 0; cx < w; cx += CHECKER_CELL)
            {
                int color = ((cx / CHECKER_CELL + cy / CHECKER_CELL) % 2 == 0) ? CHECKER_LIGHT : CHECKER_DARK;
                g.fill(
                        x + cx, y + cy, Math.min(x + cx + CHECKER_CELL, x + w), Math.min(y + cy + CHECKER_CELL, y + h),
                        color
                );
            }
        }
    }

    /* INPUT */

    @Override
    public void onClick(@NonNull MouseButtonEvent event, boolean doubleClick) { setFromMouse(event.x()); }

    @Override
    protected void onDrag(@NonNull MouseButtonEvent event, double dx, double dy) { setFromMouse(event.x()); }

    private void setFromMouse(double mouseX)
    {
        this.onSet.accept(Math.clamp((mouseX - getX()) / (getWidth() - 2), 0.0, 1.0));
    }

    @Override
    public void playDownSound(@NonNull SoundManager soundManager) { }  // continuous control — no click per grab

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput output) { }
}

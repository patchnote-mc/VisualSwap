package com.patchnote.visualswap.client.config.screen.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.function.IntSupplier;

/// A small square that fills its bounds with a live colour (re-read each frame) inside a 1px border. Decorative by
/// default; give it an {@link #setOnPress onPress} (and {@link #setClickable}) to make it a colour-picker trigger —
/// a clickable swatch brightens its border on hover.
public final class ColorSwatch extends AbstractWidget
{
    private static final int BORDER_HOVERED = 0xFF8A8A94;

    private final IntSupplier color;
    private final int borderColor;
    private @Nullable Runnable onPress;
    private boolean clickable;

    public ColorSwatch(int size, int borderColor, IntSupplier color)
    {
        super(0, 0, size, size, Component.empty());
        this.color = color;
        this.borderColor = borderColor;
        this.active = false;  // decorative until wired to a picker
    }

    /// The click action (a colour-picker open). The swatch stays inert until {@link #setClickable} enables it.
    public void setOnPress(Runnable onPress) { this.onPress = onPress; refreshActive(); }

    /// Toggle interactivity — the config screen enables swatches only while the preset is editable (Custom).
    public void setClickable(boolean clickable) { this.clickable = clickable; refreshActive(); }

    private void refreshActive() { this.active = this.clickable && this.onPress != null; }

    @Override
    public void onClick(@NonNull MouseButtonEvent event, boolean doubleClick)
    {
        if (this.onPress != null) this.onPress.run();
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a)
    {
        int x = getX();
        int y = getY();
        int s = this.width;
        int border = (this.active && isHovered()) ? BORDER_HOVERED : this.borderColor;
        g.fill(x - 1, y - 1, x + s + 1, y + s + 1, border);
        g.fill(x, y, x + s, y + s, this.color.getAsInt());
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) { }
}

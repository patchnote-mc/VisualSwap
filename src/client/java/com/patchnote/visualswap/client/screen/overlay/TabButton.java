package com.patchnote.visualswap.client.screen.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.function.BooleanSupplier;

/// A flat tab: a filled rounded-ish rect (no vanilla button 3-slice, which squishes below 20px), that reads as selected
/// / hovered / idle. Used by {@link ColorPickerOverlay}.
final class TabButton extends AbstractWidget
{
    private static final int BG_SELECTED = 0xFF3A3A48;
    private static final int BG_HOVERED = 0xFF2B2B36;
    private static final int BG_IDLE = 0xFF20202A;
    private static final int BORDER_SELECTED = 0xFF9090A4;
    private static final int BORDER_IDLE = 0xFF43434E;
    private static final int TEXT_SELECTED = 0xFFFFFFFF;
    private static final int TEXT_IDLE = 0xFFB0B0BA;

    private final Font font = Minecraft.getInstance().font;
    private final BooleanSupplier selected;
    private final Runnable onPress;

    TabButton(int x, int y, int w, int h, Component label, BooleanSupplier selected, Runnable onPress)
    {
        super(x, y, w, h, label);
        this.selected = selected;
        this.onPress = onPress;
    }

    @Override
    public void onClick(@NonNull MouseButtonEvent event, boolean doubleClick) { this.onPress.run(); }

    @Override
    protected void renderWidget(@NonNull GuiGraphics g, int mouseX, int mouseY, float a)
    {
        boolean sel = this.selected.getAsBoolean();
        int bg = sel ? BG_SELECTED : isHovered() ? BG_HOVERED : BG_IDLE;
        int border = sel ? BORDER_SELECTED : BORDER_IDLE;

        g.fill(getX(), getY(), getX() + this.width, getY() + this.height, border);
        g.fill(getX() + 1, getY() + 1, getX() + this.width - 1, getY() + this.height - 1, bg);

        int tx = getX() + (this.width - this.font.width(getMessage())) / 2;
        int ty = getY() + (this.height - this.font.lineHeight) / 2 + 1;
        g.drawString(this.font, getMessage(), tx, ty, sel ? TEXT_SELECTED : TEXT_IDLE, false);
    }

    @Override
    public void playDownSound(@NonNull SoundManager soundManager) { }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput output) { }
}

package com.patchnote.visualswap.client.config.screen.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

/// A borderless icon button: it blits a white {@link Icons} texture, tinted per state (normal / hover / disabled), over
/// a faint hover backdrop. The icon is authored white so the tint sets its colour.
public final class IconButton extends AbstractWidget
{
    private static final int HOVER_BG = 0x33FFFFFF;
    private static final int TINT_NORMAL = 0xFFC8C8D0;
    private static final int TINT_HOVER = 0xFFFFFFFF;
    private static final int TINT_DISABLED = 0xFF5E5E66;
    private static final int PADDING = 3;   // inset of the glyph from the button edges

    private final Identifier icon;
    private final Runnable onPress;

    public IconButton(int size, Identifier icon, Component narration, Runnable onPress)
    {
        super(0, 0, size, size, narration);
        this.icon = icon;
        this.onPress = onPress;
        setTooltip(Tooltip.create(narration));
    }

    @Override
    public void onClick(@NonNull MouseButtonEvent event, boolean doubleClick)
    {
        this.onPress.run();   // AbstractWidget only routes clicks here while active
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a)
    {
        boolean hovered = this.active && isHovered();
        if (hovered) g.fill(getX(), getY(), getX() + this.width, getY() + this.height, HOVER_BG);

        int size = Math.min(this.width, this.height) - 2 * PADDING;
        int ix = getX() + (this.width - size) / 2;
        int iy = getY() + (this.height - size) / 2;
        int tint = !this.active ? TINT_DISABLED : (hovered ? TINT_HOVER : TINT_NORMAL);
        Icons.blit(g, this.icon, ix, iy, size, tint);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput output) { }
}

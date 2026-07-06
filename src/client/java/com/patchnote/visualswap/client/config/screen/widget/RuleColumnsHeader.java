package com.patchnote.visualswap.client.config.screen.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import static com.patchnote.visualswap.client.config.screen.widget.FlashRulesList.*;

/// The column captions above the rules table (Item / Flash Type / Intensity / Color). It scrolls with the table and
/// derives every column x from the same right-anchored math as {@link FlashRuleRow}, so the captions always line up
/// with the row widgets below them.
public final class RuleColumnsHeader extends AbstractWidget
{
    private static final int CAPTION_ARGB = 0xFF97979E;
    private static final int CONTENT_PAD = 2;  // matches FlashRuleRow

    private final Font font = Minecraft.getInstance().font;

    public RuleColumnsHeader(int width)
    {
        super(0, 0, width, Minecraft.getInstance().font.lineHeight, Component.empty());
        this.active = false;  // decorative
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a)
    {
        int left = getX() + CONTENT_PAD;
        int right = getX() + getWidth() - CONTENT_PAD;
        int y = getY();

        int deleteX = right - DELETE_WIDTH;
        int colorX = deleteX - GAP - COLOR_WIDTH;
        int intensityX = colorX - GAP - INTENSITY_WIDTH;
        int onX = intensityX - GAP - ON_WIDTH;
        int itemX = left + ICON + GAP;

        g.text(this.font, "Item", itemX, y, CAPTION_ARGB, false);
        g.centeredText(this.font, "Flash Type", onX + ON_WIDTH / 2, y, CAPTION_ARGB);
        g.centeredText(this.font, "Intensity", intensityX + INTENSITY_WIDTH / 2, y, CAPTION_ARGB);
        g.centeredText(this.font, "Color", colorX + COLOR_WIDTH / 2, y, CAPTION_ARGB);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput output) { }
}

package com.patchnote.visualswap.client.screen.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/// A passive, vanilla-looking tooltip that can be spawned at ANY position — hover tooltips today (via
/// {@link OverlayManager#showTooltip}), onboarding callouts later (via {@link OverlayManager#open}). Sizing and line
/// spacing mirror the vanilla tooltip renderer (10px lines, a 2px gap under the title line).
public final class TooltipOverlay extends Overlay
{
    private static final int LINE_HEIGHT = 10;
    private static final int TITLE_GAP = 2;

    private final Font font;
    private final List<FormattedCharSequence> lines;

    public TooltipOverlay(Font font, List<Component> lines)
    {
        super(width(font, lines), height(lines));
        this.font = font;
        this.lines = lines.stream().map(Component::getVisualOrderText).toList();
    }

    /// The vanilla tooltip for {@code stack} — same lines a container slot would show (respects advanced tooltips;
    /// safe with no world/player, e.g. a config screen opened from the title screen).
    public static TooltipOverlay forItem(Font font, ItemStack stack)
    {
        Minecraft mc = Minecraft.getInstance();
        return new TooltipOverlay(font, Screen.getTooltipFromItem(mc, stack));
    }

    /// Vanilla hover placement: right-below the cursor, flipped/clamped at the screen edges.
    public TooltipOverlay positionNear(int mouseX, int mouseY)
    {
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int x = mouseX + 12;
        if (x + getWidth() > screenW - PAD) x = mouseX - 16 - getWidth();
        position(x, mouseY - 12);
        return this;
    }

    @Override
    public boolean isModal() { return false; }

    @Override
    protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a)
    {
        int y = contentY();
        for (int i = 0; i < this.lines.size(); i++)
        {
            g.text(this.font, this.lines.get(i), contentX(), y, 0xFFFFFFFF, true);
            y += LINE_HEIGHT + (i == 0 ? TITLE_GAP : 0);
        }
    }

    /* SIZING */

    private static int width(Font font, List<Component> lines)
    {
        int max = 0;
        for (Component line : lines) max = Math.max(max, font.width(line));
        return max + 2 * PAD;
    }

    private static int height(List<Component> lines)
    {
        int n = Math.max(1, lines.size());
        return n * LINE_HEIGHT + (n > 1 ? TITLE_GAP : -2) + 2 * PAD;
    }
}

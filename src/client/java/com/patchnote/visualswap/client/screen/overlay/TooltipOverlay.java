package com.patchnote.visualswap.client.screen.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/// A passive, vanilla-looking tooltip that can be spawned at ANY position — hover tooltips today (via
/// {@link OverlayManager#showTooltip}), onboarding callouts later (via {@link OverlayManager#open}). Sizing and line
/// spacing mirror the vanilla tooltip renderer (10px lines, a 2px gap under the title line).
public final class TooltipOverlay extends Overlay
{
    private static final int LINE_HEIGHT = 10;
    private static final int TITLE_GAP = 2;

    /// A width cap high enough that {@link Font#split} only breaks on explicit {@code \n}, never soft-wrapping — so a
    /// (translated) value controls its own line breaks with the escape sequence.
    private static final int NO_SOFT_WRAP = Integer.MAX_VALUE;

    private final Font font;
    private final List<FormattedCharSequence> lines;

    /// Build a tooltip from styled component lines. Each component is split on its own {@code \n} breaks (styles
    /// preserved), so a single (translated) value can span several rendered lines. Line 0 is still the title — it gets
    /// the extra gap below it.
    public static TooltipOverlay of(Font font, List<Component> lines)
    {
        return new TooltipOverlay(font, split(font, lines));
    }

    private TooltipOverlay(Font font, List<FormattedCharSequence> lines)
    {
        super(width(font, lines), height(lines));
        this.font = font;
        this.lines = lines;
    }

    /// The vanilla tooltip for {@code stack} — same lines a container slot would show (respects advanced tooltips; safe
    /// with no world/player, e.g. a config screen opened from the title screen).
    public static TooltipOverlay forItem(Font font, ItemStack stack)
    {
        Minecraft mc = Minecraft.getInstance();
        return of(font, Screen.getTooltipFromItem(mc, stack));
    }

    /// Split each component on its {@code \n} breaks, flattening to one entry per rendered line (styles preserved).
    private static List<FormattedCharSequence> split(Font font, List<Component> lines)
    {
        List<FormattedCharSequence> out = new ArrayList<>();
        for (Component line : lines) out.addAll(font.split(line, NO_SOFT_WRAP));
        return out;
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

    private static int width(Font font, List<FormattedCharSequence> lines)
    {
        int max = 0;
        for (FormattedCharSequence line : lines) max = Math.max(max, font.width(line));
        return max + 2 * PAD;
    }

    private static int height(List<FormattedCharSequence> lines)
    {
        int n = Math.max(1, lines.size());
        return n * LINE_HEIGHT + (n > 1 ? TITLE_GAP : -2) + 2 * PAD;
    }
}

package com.patchnote.visualswap.client.screen.modal;

import com.patchnote.visualswap.client.config.models.FlashRule;
import com.patchnote.visualswap.client.config.screen.widget.Icons;
import com.patchnote.visualswap.client.utils.ItemIcons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.List;

/// The regex preview modal's scrollable table of matched items — one row per matching item id: a checkbox (ticked =
/// included, unticked = excluded on the rule), the item icon, and the id with the regex-matched substrings highlighted.
/// Only the visible rows are drawn (scissor-clipped), so it stays cheap even for a pattern that matches every item.
/// Clicking a row toggles that id's exclusion on the shared {@link FlashRule}.
final class MatchedItemsList extends AbstractScrollArea
{
    static final int ROW_H = 20;

    private static final int PAD = 4;
    private static final int GAP = 5;
    private static final int ICON = 16;
    private static final int BOX = 12;
    private static final int SCROLL_RATE = ROW_H * 2;

    private static final int ROW_HOVER = 0x22FFFFFF;
    private static final int BOX_BORDER = 0xFF6A6A72;
    private static final int BOX_BG = 0xFF17171C;
    private static final int CHECK_ARGB = 0xFF6DD37F;
    private static final int SLOT_BORDER = 0xFF4A4A52;
    private static final int SLOT_BG = 0xFF26262B;
    private static final int ID_ARGB = 0xFFDDDDE2;       // the non-matched part of an included id
    private static final int HIT_ARGB = 0xFF63D0A0;      // the matched substring — why the item was included
    private static final int EXCLUDED_ARGB = 0xFF6A6A72; // an unticked (excluded) id reads as dimmed

    private final FlashRule rule;
    private final List<Row> rows;
    private final Font font = Minecraft.getInstance().font;

    MatchedItemsList(int x, int y, int width, int height, FlashRule rule, List<Row> rows)
    {
        super(x, y, width, height, Component.translatable("gui.visual-swap.preview.title"),
              AbstractScrollArea.defaultSettings(SCROLL_RATE));
        this.rule = rule;
        this.rows = rows;
    }

    @Override
    protected int contentHeight() { return this.rows.size() * ROW_H; }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick)
    {
        if (!this.visible) return false;
        boolean over = event.x() >= getX() && event.x() < getRight() && event.y() >= getY() && event.y() < getBottom();
        if (!over) return false;

        if (updateScrolling(event)) return true;        // grabbed the scrollbar → base handles the drag
        if (event.x() >= scrollBarX()) return false;    // in the scrollbar gutter but not draggable

        int idx = rowIndexAt(event.y());
        if (idx < 0 || idx >= this.rows.size()) return false;

        String key = this.rows.get(idx).id().toString();
        if (this.rule.isExcluded(key)) this.rule.removeExcluded(key);
        else this.rule.addExcluded(key);
        return true;
    }

    private int rowIndexAt(double y)
    {
        int rel = (int) (y - getY() + scrollAmount());
        return (rel < 0) ? -1 : rel / ROW_H;
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a)
    {
        g.enableScissor(getX(), getY(), getRight(), getBottom());
        int scroll = (int) scrollAmount();
        int first = Math.max(0, scroll / ROW_H);
        int last = Math.min(this.rows.size(), (scroll + getHeight()) / ROW_H + 1);
        for (int i = first; i < last; i++) drawRow(g, i, getY() - scroll + i * ROW_H, mouseX, mouseY);
        g.disableScissor();

        extractScrollbar(g, mouseX, mouseY);
    }

    private void drawRow(GuiGraphicsExtractor g, int i, int rowY, int mouseX, int mouseY)
    {
        Row row = this.rows.get(i);
        boolean included = !this.rule.isExcluded(row.id().toString());
        int contentRight = scrollBarX();

        boolean hovered = mouseX >= getX() && mouseX < contentRight
                && mouseY >= Math.max(rowY, getY()) && mouseY < Math.min(rowY + ROW_H, getBottom());
        if (hovered) g.fill(getX(), rowY, contentRight, rowY + ROW_H, ROW_HOVER);

        // checkbox
        int cbX = getX() + PAD;
        int cbY = rowY + (ROW_H - BOX) / 2;
        g.fill(cbX, cbY, cbX + BOX, cbY + BOX, BOX_BORDER);
        g.fill(cbX + 1, cbY + 1, cbX + BOX - 1, cbY + BOX - 1, BOX_BG);
        if (included) Icons.blit(g, Icons.CHECK, cbX + 1, cbY + 1, BOX - 2, CHECK_ARGB);

        // item icon
        int iconX = cbX + BOX + GAP;
        int iconY = rowY + (ROW_H - ICON) / 2;
        ItemStack stack = ItemIcons.stackFor(BuiltInRegistries.ITEM.getValue(row.id()));
        if (stack.isEmpty())
        {
            g.fill(iconX, iconY, iconX + ICON, iconY + ICON, SLOT_BORDER);
            g.fill(iconX + 1, iconY + 1, iconX + ICON - 1, iconY + ICON - 1, SLOT_BG);
        }
        else
        {
            g.item(stack, iconX, iconY);
        }

        // id text — matched substrings highlighted, dimmed as a whole when excluded
        int textX = iconX + ICON + GAP;
        int textY = rowY + (ROW_H - this.font.lineHeight) / 2;
        if (included) drawHighlighted(g, row.text(), row.spans(), textX, textY);
        else g.text(this.font, row.text(), textX, textY, EXCLUDED_ARGB, false);
    }

    /// Draw {@code id} run by run, colouring the regex-matched spans with {@link #HIT_ARGB} and the rest with
    /// {@link #ID_ARGB}. {@code spans} is a sorted, non-overlapping {@code [start,end,…]} list.
    private void drawHighlighted(GuiGraphicsExtractor g, String id, int[] spans, int x, int y)
    {
        int cursor = x;
        int i = 0;
        int s = 0;   // index into spans (pairs)
        while (i < id.length())
        {
            boolean inSpan = s < spans.length && i >= spans[s] && i < spans[s + 1];
            int runEnd = inSpan ? spans[s + 1] : (s < spans.length ? spans[s] : id.length());
            String run = id.substring(i, runEnd);
            g.text(this.font, run, cursor, y, inSpan ? HIT_ARGB : ID_ARGB, false);
            cursor += this.font.width(run);
            i = runEnd;
            if (inSpan) s += 2;
        }
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput output) { }

    /// One matched item: its id, the id string, and the non-overlapping match spans to highlight.
    record Row(Identifier id, String text, int[] spans) { }
}

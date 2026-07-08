package com.patchnote.visualswap.client.screen.modal;

import com.patchnote.visualswap.client.config.models.FlashRule;
import com.patchnote.visualswap.client.utils.ItemRegex;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/// Interactive preview of a flash rule's regex selector: a scrollable table of every item id the pattern matches, each
/// with the matched substring highlighted and a checkbox to include/exclude that specific id (writing to the rule's
/// {@link FlashRule#excludedItems() exclusion set}). Opened by clicking a rule row's item icon; the edits land on the
/// shared rule, so returning to the config screen (which re-inits on show) picks them up.
public final class RegexPreviewModal extends Modal
{
    private static final int PAD = 10;
    private static final int GAP = 6;
    private static final int TITLE_LINE = 11;
    private static final int COUNT_LINE = 10;
    private static final int BTN_H = 20;
    private static final int BTN_W = 100;
    private static final int PANEL_MAX_W = 340;

    private static final int PANEL_BG = 0xF00E0E14;
    private static final int PANEL_BORDER = 0xFF45454F;
    private static final int LIST_BG = 0xFF090910;
    private static final int TITLE_ARGB = 0xFFFFFFFF;
    private static final int COUNT_ARGB = 0xFFB9B9C0;
    private static final int EMPTY_ARGB = 0xFF8A8A90;

    private final FlashRule rule;
    private final List<MatchedItemsList.Row> rows;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int listTop;
    private int listH;

    private RegexPreviewModal(FlashRule rule)
    {
        super(Component.literal("Matched items"));
        this.rule = rule;
        this.rows = buildRows(rule);
    }

    /// Open the preview for {@code rule} over the current screen.
    public static void open(FlashRule rule) { new RegexPreviewModal(rule).open(); }

    private static List<MatchedItemsList.Row> buildRows(FlashRule rule)
    {
        Pattern pattern = rule.pattern();
        List<Identifier> ids = ItemRegex.matchingIds(pattern);
        List<MatchedItemsList.Row> out = new ArrayList<>(ids.size());
        for (Identifier id : ids)
        {
            String text = id.toString();
            int[] spans = (pattern != null) ? ItemRegex.spans(pattern, text) : new int[0];
            out.add(new MatchedItemsList.Row(id, text, spans));
        }
        return out;
    }

    @Override
    protected void init()
    {
        this.panelW = Math.min(PANEL_MAX_W, this.width - 40);
        int headerH = PAD + TITLE_LINE + 2 + COUNT_LINE + GAP;
        int footerH = BTN_H + PAD;
        int maxListH = Math.max(MatchedItemsList.ROW_H * 3, (int) (this.height * 0.55));
        int wantList = this.rows.isEmpty()
                       ? MatchedItemsList.ROW_H * 2
                       : Math.min(this.rows.size() * MatchedItemsList.ROW_H, maxListH);
        this.panelH = headerH + wantList + footerH;
        this.panelX = (this.width - this.panelW) / 2;
        this.panelY = (this.height - this.panelH) / 2;
        this.listTop = this.panelY + headerH;
        this.listH = wantList;

        if (!this.rows.isEmpty())
        {
            addRenderableWidget(new MatchedItemsList(
                    this.panelX + PAD, this.listTop, this.panelW - 2 * PAD, this.listH, this.rule, this.rows));
        }

        int btnY = this.panelY + this.panelH - PAD - BTN_H;
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                                    .bounds(this.panelX + (this.panelW - BTN_W) / 2, btnY, BTN_W, BTN_H).build());
    }

    @Override
    protected void extractPanel(@NonNull GuiGraphicsExtractor g)
    {
        int x1 = this.panelX + this.panelW;
        int y1 = this.panelY + this.panelH;
        g.fill(this.panelX, this.panelY, x1, y1, PANEL_BG);
        g.fill(this.panelX, this.panelY, x1, this.panelY + 1, PANEL_BORDER);
        g.fill(this.panelX, y1 - 1, x1, y1, PANEL_BORDER);
        g.fill(this.panelX, this.panelY, this.panelX + 1, y1, PANEL_BORDER);
        g.fill(x1 - 1, this.panelY, x1, y1, PANEL_BORDER);

        // header: the pattern, then an included/total count
        int y = this.panelY + PAD;
        String pattern = (this.rule.item() == null || this.rule.item().isBlank()) ? "(empty pattern)" : this.rule.item();
        g.text(this.font, pattern, this.panelX + PAD, y, TITLE_ARGB, true);
        y += TITLE_LINE + 2;
        String count = this.rows.isEmpty() ? "0 matches" : includedCount() + " of " + this.rows.size() + " included";
        g.text(this.font, count, this.panelX + PAD, y, COUNT_ARGB, false);

        // list backdrop + empty state (the list widget, if any, draws over this)
        g.fill(this.panelX + PAD, this.listTop, this.panelX + this.panelW - PAD, this.listTop + this.listH, LIST_BG);
        if (this.rows.isEmpty())
        {
            g.centeredText(this.font, "No items match this pattern", this.panelX + this.panelW / 2,
                           this.listTop + this.listH / 2 - this.font.lineHeight / 2, EMPTY_ARGB);
        }
    }

    private int includedCount()
    {
        int n = 0;
        for (MatchedItemsList.Row row : this.rows) if (!this.rule.isExcluded(row.id().toString())) n++;
        return n;
    }
}

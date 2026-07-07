package com.patchnote.visualswap.client.config.screen.widget;

import com.patchnote.visualswap.client.config.models.FlashIntensity;
import com.patchnote.visualswap.client.config.models.FlashRule;
import com.patchnote.visualswap.client.config.models.FlashTrigger;
import com.patchnote.visualswap.client.config.models.PresetType;
import com.patchnote.visualswap.client.screen.overlay.OverlayManager;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LinearLayout;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/// A vertical stack of {@link FlashRule} rows — one {@link FlashRuleRow} each. It is a plain, non-scrolling
/// {@link Layout} (backed by a vertical {@link LinearLayout}); the page it lives on owns the scrolling. Rows operate on
/// independent working copies of the rules, so nothing touches {@code ModConfig} until the screen commits.
///
/// Adding or removing a rule mutates the row list and fires {@link #onChanged} — the screen re-lays-out the page (its
/// {@code rebuildWidgets}) so the new row set is re-collected and re-arranged.
public final class FlashRulesList implements Layout
{
    static final int ROW_HEIGHT = 26;
    static final int ROW_SPACING = 0;   // rows sit flush, matching the old 26px list slots
    static final int WIDGET_HEIGHT = 18;
    static final int ICON = 16;
    static final int GAP = 6;

    static final int ON_WIDTH = 58;
    static final int INTENSITY_WIDTH = 46;
    static final int COLOR_SWATCH = 14;  // the colour column is now just this swatch (click opens the picker)
    static final int DUPLICATE_WIDTH = 18;
    static final int DELETE_WIDTH = 18;
    static final int ACTION_GAP = 3;     // tighter gap between the duplicate/delete action buttons

    static final int TEXT_VALID = 0xFFE0E0E0;
    static final int TEXT_INVALID = 0xFFFF5555;
    static final int TEXT_MUTED = 0xFF97979E;  // greyed hex text under a non-editable (non-Custom) preset

    static final int SLOT_BG = 0xFF26262B;
    static final int SLOT_BORDER = 0xFF4A4A52;

    private final int rowWidth;
    private final Runnable onChanged;
    private final Consumer<FlashRule> onColorEdited;
    private final OverlayManager overlays;
    private final List<FlashRuleRow> rows = new ArrayList<>();
    private final LinearLayout layout = LinearLayout.vertical().spacing(ROW_SPACING);

    /// The preset whose colour each row currently shows/edits — mirrors the screen's working preset.
    private PresetType preset;

    /// Case-insensitive item-id substring the table is filtered by; empty shows every row. Filtering is view-only —
    /// hidden rows stay in {@link #rows} (and in {@link #toRules()}), they are just excluded from the layout.
    private String filter = "";

    public FlashRulesList(int rowWidth, PresetType preset, List<FlashRule> rules, Runnable onChanged,
                          Consumer<FlashRule> onColorEdited, OverlayManager overlays)
    {
        this.rowWidth = rowWidth;
        this.preset = preset;
        this.onChanged = onChanged;
        this.onColorEdited = onColorEdited;
        this.overlays = overlays;
        for (FlashRule rule : rules)
        {
            this.rows.add(new FlashRuleRow(this, new FlashRule(rule)));
        }
        rebuildLayout();
    }

    private void rebuildLayout()
    {
        this.layout.removeChildren();
        for (FlashRuleRow row : this.rows) if (matches(row)) this.layout.addChild(row);
    }

    private boolean matches(FlashRuleRow row)
    {
        if (this.filter.isEmpty()) return true;
        String item = row.getRule().item();
        return item != null && item.toLowerCase(Locale.ROOT).contains(this.filter);
    }

    /* API */

    public int getRowWidth() { return this.rowWidth; }

    /// The screen's overlay layer — rows use it for hover tooltips and colour pickers.
    OverlayManager overlays() { return this.overlays; }

    /// The preset each row's colour column currently reflects.
    public PresetType preset() { return this.preset; }

    /// Switch the preset shown/edited by every row's colour column (value + editability + swatch follow).
    public void setPreset(PresetType preset)
    {
        this.preset = preset;
        for (FlashRuleRow row : this.rows) row.refreshColor(preset);
    }

    /// Filter the visible rows to those whose item id contains {@code text} (case-insensitive). View-only — the
    /// underlying rules and {@link #toRules()} are unaffected. Rebuilds the layout; the page re-arranges after.
    public void setFilter(@Nullable String text)
    {
        this.filter = (text == null) ? "" : text.trim().toLowerCase(Locale.ROOT);
        rebuildLayout();
    }

    /// Rows currently shown (after the active filter).
    public int visibleCount()
    {
        int n = 0;
        for (FlashRuleRow row : this.rows) if (matches(row)) n++;
        return n;
    }

    /// Total rules, ignoring the filter.
    public int totalCount() { return this.rows.size(); }

    /// The topmost row (where {@link #addRule} inserts), or null when the table is empty — used to focus a just-added
    /// rule's item box.
    public @Nullable FlashRuleRow firstRow() { return this.rows.isEmpty() ? null : this.rows.get(0); }

    /// How many rows resolve to no real item (blank or unknown id) — surfaced as a warning before saving.
    public int invalidCount()
    {
        int n = 0;
        for (FlashRuleRow row : this.rows) if (!row.isItemValid()) n++;
        return n;
    }

    /// Insert a fresh, blank rule at the TOP and ask the screen to re-lay-out the page. Top insertion keeps the new
    /// row visible: a rebuild recreates the scroll viewport reset to the top, so the newest rule is always on screen.
    public void addRule()
    {
        this.rows.add(0, new FlashRuleRow(this, new FlashRule("minecraft:", FlashTrigger.ATTACK, FlashIntensity.HIGH)));
        if (this.onChanged != null) this.onChanged.run();
    }

    /// Insert a copy of {@code row} directly below it — invoked by the row's own duplicate button.
    void duplicate(FlashRuleRow row)
    {
        int i = this.rows.indexOf(row);
        if (i < 0) return;
        this.rows.add(i + 1, new FlashRuleRow(this, new FlashRule(row.getRule())));
        if (this.onChanged != null) this.onChanged.run();
    }

    /// Remove every rule (used by the toolbar's "Clear all", behind a confirmation).
    public void clear()
    {
        this.rows.clear();
        if (this.onChanged != null) this.onChanged.run();
    }

    /// Remove {@code row} — invoked by the row's own delete button — and ask the screen to re-lay-out the page.
    void removeRule(FlashRuleRow row)
    {
        this.rows.remove(row);
        if (this.onChanged != null) this.onChanged.run();
    }

    /// The current rules, in row order — the value the screen commits on save.
    public ArrayList<FlashRule> toRules()
    {
        ArrayList<FlashRule> out = new ArrayList<>();
        for (FlashRuleRow row : this.rows) out.add(row.getRule());
        return out;
    }

    /// A user edit of {@code rule}'s colour — forwarded to the screen so the swap preview can follow that rule.
    void notifyColorEdited(FlashRule rule)
    {
        if (this.onColorEdited != null) this.onColorEdited.accept(rule);
    }

    /// The rule at row {@code index}, or — when the index is out of range — the first row whose item is
    /// {@code fallbackItem}, else the first row, else null (empty table). Used to retarget the swap preview across
    /// screen rebuilds: the new rows copy the old list's rules in order, so an index into the old {@code toRules()}
    /// identifies the same rule here even when ids are duplicated or mid-edit.
    public @Nullable FlashRule ruleAt(int index, String fallbackItem)
    {
        if (index >= 0 && index < this.rows.size()) return this.rows.get(index).getRule();

        FlashRule first = null;
        for (FlashRuleRow row : this.rows)
        {
            if (first == null) first = row.getRule();
            if (fallbackItem.equals(row.getRule().item())) return row.getRule();
        }
        return first;
    }

    /* LAYOUT (delegates to the backing vertical LinearLayout) */

    @Override
    public void arrangeElements() { this.layout.arrangeElements(); }

    @Override
    public void visitChildren(Consumer<LayoutElement> visitor) { this.layout.visitChildren(visitor); }

    @Override
    public void visitWidgets(Consumer<AbstractWidget> visitor) { this.layout.visitWidgets(visitor); }

    @Override
    public void removeChildren() { this.layout.removeChildren(); }

    @Override
    public void setX(int x) { this.layout.setX(x); }

    @Override
    public void setY(int y) { this.layout.setY(y); }

    @Override
    public int getX() { return this.layout.getX(); }

    @Override
    public int getY() { return this.layout.getY(); }

    @Override
    public int getWidth() { return this.layout.getWidth(); }

    @Override
    public int getHeight() { return this.layout.getHeight(); }
}

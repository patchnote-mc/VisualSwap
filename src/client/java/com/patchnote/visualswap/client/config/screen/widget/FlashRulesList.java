package com.patchnote.visualswap.client.config.screen.widget;

import com.patchnote.visualswap.client.config.models.FlashIntensity;
import com.patchnote.visualswap.client.config.models.FlashRule;
import com.patchnote.visualswap.client.config.models.FlashTrigger;
import com.patchnote.visualswap.client.config.models.PresetType;
import com.patchnote.visualswap.client.screen.overlay.OverlayManager;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LinearLayout;

import org.jspecify.annotations.NonNull;
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
    static final int MOVE_WIDTH = 18;    // up/down reorder buttons
    static final int DUPLICATE_WIDTH = 18;
    static final int DELETE_WIDTH = 18;
    static final int ACTION_GAP = 3;     // tighter gap between the reorder/duplicate/delete action buttons

    static final int TEXT_VALID = 0xFFE0E0E0;
    static final int TEXT_INVALID = 0xFFFF5555;
    static final int TEXT_MUTED = 0xFF97979E;  // greyed hex text under a non-editable (non-Custom) preset

    // Per-row marker in the left gutter.
    static final int NEW_ARGB = 0xFF4FC463;       // green — a rule added this session
    static final int MODIFIED_ARGB = 0xFFF09A3C;  // orange — a saved rule that's been edited

    static final int SLOT_BG = 0xFF26262B;
    static final int SLOT_BORDER = 0xFF4A4A52;

    private final int rowWidth;
    private final Runnable onChanged;
    private final Consumer<FlashRule> onColorEdited;
    private final Consumer<String> onTextChanged;
    private final OverlayManager overlays;
    private final List<FlashRuleRow> rows = new ArrayList<>();
    private final LinearLayout layout = LinearLayout.vertical().spacing(ROW_SPACING);

    /// The preset whose colour each row currently shows/edits — mirrors the screen's working preset.
    private PresetType preset;

    /// Case-insensitive item-id substring the table is filtered by; empty shows every row. Filtering is view-only —
    /// hidden rows stay in {@link #rows} (and in {@link #toRules()}), they are just excluded from the layout.
    private String filter = "";

    /// A freshly duplicated rule the screen should scroll into view after the next rebuild; consumed once.
    private @Nullable FlashRule revealTarget;

    public FlashRulesList(int rowWidth, PresetType preset, List<FlashRule> rules, Runnable onChanged,
                          Consumer<FlashRule> onColorEdited, Consumer<String> onTextChanged, OverlayManager overlays)
    {
        this.rowWidth = rowWidth;
        this.preset = preset;
        this.onChanged = onChanged;
        this.onColorEdited = onColorEdited;
        this.onTextChanged = onTextChanged;
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
        List<FlashRuleRow> visible = visibleRows();
        for (int i = 0; i < visible.size(); i++)
        {
            FlashRuleRow row = visible.get(i);
            row.setCanMoveUp(i > 0);
            row.setCanMoveDown(i < visible.size() - 1);
            this.layout.addChild(row);
        }
    }

    /// The rows currently shown, in order (after the active filter).
    private List<FlashRuleRow> visibleRows()
    {
        List<FlashRuleRow> out = new ArrayList<>();
        for (FlashRuleRow row : this.rows) if (matches(row)) out.add(row);
        return out;
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

    /// The tint every rule shares under the current preset, or the preset's default flash tint when the table is empty
    /// or the rows disagree. Seeds (and is displayed by) the header's bulk "set all colours" swatch.
    public int commonColor()
    {
        int fallback = this.preset.getFlashTint();
        if (this.rows.isEmpty()) return fallback;
        int shared = this.rows.getFirst().getRule().colorFor(this.preset);
        for (FlashRuleRow row : this.rows)
            if (row.getRule().colorFor(this.preset) != shared) return fallback;
        return shared;
    }

    /// Set every rule's tint under the current preset to {@code color} — the header's bulk action. Applies to the whole
    /// table, ignoring the active filter (hidden rows included), and no-ops under a non-editable preset (setColorFor
    /// guards it). Swatches read their colour live, so no rebuild is needed.
    public void setColorForAll(int color)
    {
        for (FlashRuleRow row : this.rows) row.getRule().setColorFor(this.preset, color);
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
    public @Nullable FlashRuleRow firstRow() { return this.rows.isEmpty() ? null : this.rows.getFirst(); }

    /// The row at {@code index} in full (unfiltered) order, or null when out of range — used to scroll a specific row
    /// (a just-duplicated one) into view after a rebuild.
    public @Nullable FlashRuleRow rowAt(int index)
    {
        return (index >= 0 && index < this.rows.size()) ? this.rows.get(index) : null;
    }

    /// The rule flagged by the last {@link #duplicate} for the screen to scroll into view, cleared as it is read.
    public @Nullable FlashRule consumeRevealTarget()
    {
        FlashRule target = this.revealTarget;
        this.revealTarget = null;
        return target;
    }

    public int invalidCount()
    {
        int n = 0;
        for (FlashRuleRow row : this.rows) if (!row.isItemValid()) n++;
        return n;
    }

    /// Grey out (or restore) every row — the screen calls this with {@code false} when the Item Flash effect is switched
    /// off, so the rules can't be edited while their effect is disabled. {@code disabledTip} becomes every cell's
    /// tooltip while disabled, so hovering any row explains which switch turned the table off.
    public void setEnabled(boolean enabled, @Nullable Tooltip disabledTip)
    {
        for (FlashRuleRow row : this.rows) row.setEnabled(enabled, disabledTip);
    }

    /// Insert a fresh, blank rule at the TOP and ask the screen to re-lay-out the page. The screen focuses and scrolls
    /// the new top row into view after the rebuild (see its {@code focusNewRow} handling).
    public void addRule()
    {
        this.rows.addFirst(new FlashRuleRow(
                this, //
                new FlashRule("", FlashTrigger.ATTACK, FlashIntensity.HIGH)
        ));
        if (this.onChanged != null) this.onChanged.run();
    }

    /// Move {@code row} one step up in precedence — swapping it with the previous visible row — then re-lay-out the page.
    void moveUp(FlashRuleRow row)
    {
        List<FlashRuleRow> visible = visibleRows();
        int vi = visible.indexOf(row);
        if (vi <= 0) return;
        swapInRows(row, visible.get(vi - 1));
        if (this.onChanged != null) this.onChanged.run();
    }

    /// Move {@code row} one step down in precedence — swapping it with the next visible row — then re-lay-out the page.
    void moveDown(FlashRuleRow row)
    {
        List<FlashRuleRow> visible = visibleRows();
        int vi = visible.indexOf(row);
        if (vi < 0 || vi >= visible.size() - 1) return;
        swapInRows(row, visible.get(vi + 1));
        if (this.onChanged != null) this.onChanged.run();
    }

    private void swapInRows(FlashRuleRow a, FlashRuleRow b)
    {
        int ia = this.rows.indexOf(a);
        int ib = this.rows.indexOf(b);
        if (ia < 0 || ib < 0) return;
        this.rows.set(ia, b);
        this.rows.set(ib, a);
    }

    /// Insert a copy of {@code row} directly below it — invoked by the row's own duplicate button. The copy is a brand
    /// new rule (no saved origin → shown as newly added) and is flagged to be scrolled into view after the rebuild.
    void duplicate(FlashRuleRow row)
    {
        int i = this.rows.indexOf(row);
        if (i < 0) return;
        FlashRule copy = new FlashRule(row.getRule()).setSavedOrigin(null);
        this.rows.add(i + 1, new FlashRuleRow(this, copy));
        this.revealTarget = copy;
        if (this.onChanged != null) this.onChanged.run();
    }

    /// Remove every rule (used by the toolbar's "Clear all", behind a confirmation).
    public void clear()
    {
        this.rows.clear();
        if (this.onChanged != null) this.onChanged.run();
    }

    /// Remove {@code row} — invoked by the row's own delete button (behind a confirm) — and ask the screen to
    /// re-lay-out the page.
    void removeRule(FlashRuleRow row)
    {
        this.rows.remove(row);
        if (this.onChanged != null) this.onChanged.run();
    }

    /// Revert {@code row}'s rule to the saved value it descends from — invoked by the row's own revert button, which is
    /// shown in place of delete only while the rule is modified. Replaces the row in place (keeping its list position)
    /// with a fresh working copy of its saved origin, then re-lays-out the page. No-op for a newly-added rule (it has
    /// no saved value to revert to, so it never shows the revert button).
    void revertRule(FlashRuleRow row)
    {
        int i = this.rows.indexOf(row);
        if (i < 0) return;
        FlashRule origin = row.getRule().savedOrigin();
        if (origin == null) return;
        this.rows.set(i, new FlashRuleRow(this, new FlashRule(origin)));
        if (this.onChanged != null) this.onChanged.run();
    }

    /// The current rules, in row order — the value the screen commits on save. Stamps each rule's precedence
    /// {@code order} from its row position, so the committed/saved list carries the order explicitly.
    public ArrayList<FlashRule> toRules()
    {
        ArrayList<FlashRule> out = new ArrayList<>();
        for (int i = 0; i < this.rows.size(); i++)
        {
            FlashRule rule = this.rows.get(i).getRule();
            rule.setOrder(i);
            out.add(rule);
        }
        return out;
    }

    /// A user edit of {@code rule}'s colour — forwarded to the screen so the swap preview can follow that rule.
    void notifyColorEdited(FlashRule rule) { if (this.onColorEdited != null) this.onColorEdited.accept(rule); }

    void notifyTextChanged(String text) { if (this.onTextChanged != null) this.onTextChanged.accept(text); }

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
    public void visitChildren(@NonNull Consumer<LayoutElement> visitor) { this.layout.visitChildren(visitor); }

    @Override
    public void visitWidgets(@NonNull Consumer<AbstractWidget> visitor) { this.layout.visitWidgets(visitor); }

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

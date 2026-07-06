package com.patchnote.visualswap.client.config.screen.widget;

import com.patchnote.visualswap.client.config.models.FlashIntensity;
import com.patchnote.visualswap.client.config.models.FlashRule;
import com.patchnote.visualswap.client.config.models.FlashTrigger;
import com.patchnote.visualswap.client.config.models.PresetType;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LinearLayout;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
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
    static final int COLOR_WIDTH = 70;  // swatch + gap + hex box
    static final int COLOR_SWATCH = 12;
    static final int COLOR_BOX_WIDTH = COLOR_WIDTH - COLOR_SWATCH - 4;
    static final int DELETE_WIDTH = 18;

    static final int TEXT_VALID = 0xFFE0E0E0;
    static final int TEXT_INVALID = 0xFFFF5555;
    static final int TEXT_MUTED = 0xFF97979E;  // greyed hex text under a non-editable (non-Custom) preset

    static final int SLOT_BG = 0xFF26262B;
    static final int SLOT_BORDER = 0xFF4A4A52;

    private final int rowWidth;
    private final Runnable onChanged;
    private final Consumer<FlashRule> onColorEdited;
    private final List<FlashRuleRow> rows = new ArrayList<>();
    private final LinearLayout layout = LinearLayout.vertical().spacing(ROW_SPACING);

    /// The preset whose colour each row currently shows/edits — mirrors the screen's working preset.
    private PresetType preset;

    public FlashRulesList(int rowWidth, PresetType preset, List<FlashRule> rules, Runnable onChanged,
                          Consumer<FlashRule> onColorEdited)
    {
        this.rowWidth = rowWidth;
        this.preset = preset;
        this.onChanged = onChanged;
        this.onColorEdited = onColorEdited;
        for (FlashRule rule : rules)
        {
            this.rows.add(new FlashRuleRow(this, new FlashRule(rule)));
        }
        rebuildLayout();
    }

    private void rebuildLayout()
    {
        this.layout.removeChildren();
        for (FlashRuleRow row : this.rows) this.layout.addChild(row);
    }

    /* API */

    public int getRowWidth() { return this.rowWidth; }

    /// The preset each row's colour column currently reflects.
    public PresetType preset() { return this.preset; }

    /// Switch the preset shown/edited by every row's colour column (value + editability + swatch follow).
    public void setPreset(PresetType preset)
    {
        this.preset = preset;
        for (FlashRuleRow row : this.rows) row.refreshColor(preset);
    }

    /// Append a fresh, blank rule and ask the screen to re-lay-out the page.
    public void addRule()
    {
        this.rows.add(new FlashRuleRow(this, new FlashRule("minecraft:", FlashTrigger.ATTACK, FlashIntensity.HIGH)));
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

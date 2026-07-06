package com.patchnote.visualswap.client.config.screen.widget;

import com.patchnote.visualswap.client.config.models.FlashTrigger;
import com.patchnote.visualswap.client.config.models.FlashIntensity;
import com.patchnote.visualswap.client.config.models.FlashRule;
import com.patchnote.visualswap.client.config.models.PresetType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/// Scrollable table of {@link FlashRule}s — one row per rule. Each row edits the item id (with a live icon), cycles its
/// flash input and intensity, and edits its tint colour (hex box + live swatch); a per-row delete button removes it.
/// Operates on independent working copies of the rules so nothing touches {@code ModConfig} until the screen commits.
public final class FlashRulesList extends ContainerObjectSelectionList<FlashRuleEntry>
{
    static final int ROW_HEIGHT = 26;
    static final int WIDGET_HEIGHT = 18;
    static final int ICON = 16;
    static final int GAP = 6;

    static final int ON_WIDTH = 58;
    static final int INTENSITY_WIDTH = 46;
    static final int COLOR_WIDTH = 70;  // swatch + gap + hex box
    static final int COLOR_SWATCH = 12;
    static final int DELETE_WIDTH = 18;

    static final int TEXT_VALID = 0xFFE0E0E0;
    static final int TEXT_INVALID = 0xFFFF5555;
    static final int TEXT_MUTED = 0xFF97979E;  // greyed hex text under a non-editable (non-Custom) preset

    static final int SLOT_BG = 0xFF26262B;
    static final int SLOT_BORDER = 0xFF4A4A52;

    private final int rowWidth;

    /// The preset whose colour each row currently shows/edits — mirrors the screen's working preset.
    private PresetType preset;

    public FlashRulesList(Minecraft minecraft, int width, int height, int y, int rowWidth, PresetType preset,
                          List<FlashRule> rules)
    {
        super(minecraft, width, height, y, ROW_HEIGHT);
        this.rowWidth = rowWidth;
        this.preset = preset;
        for (FlashRule rule : rules)
        {
            addEntry(new FlashRuleEntry(this, new FlashRule(rule)));
        }
    }

    /* PROPERTIES */

    @Override
    public int getRowWidth() { return this.rowWidth; }

    @Override
    protected int scrollBarX() { return getRowRight() + 10; }

    /* API */

    /// The preset each row's colour column currently reflects.
    public PresetType preset() { return this.preset; }

    /// Switch the preset shown/edited by every row's colour column (value + editability + swatch follow).
    public void setPreset(PresetType preset)
    {
        this.preset = preset;
        for (FlashRuleEntry entry : children()) entry.refreshColor(preset);
    }

    /// Append a fresh, blank rule and scroll it into view.
    public void addRule()
    {
        FlashRuleEntry entry = new FlashRuleEntry(this, new FlashRule("minecraft:", FlashTrigger.ATTACK, FlashIntensity.HIGH));
        addEntry(entry);
        scrollToEntry(entry);
        setSelected(entry);
    }

    /// Remove {@code entry}'s row — invoked by the row's own delete button.
    void removeRule(FlashRuleEntry entry)
    {
        removeEntry(entry);
    }

    /// The current rules, in row order — the value the screen commits on save.
    public ArrayList<FlashRule> toRules()
    {
        ArrayList<FlashRule> out = new ArrayList<>();
        for (FlashRuleEntry entry : children()) out.add(entry.getRule());
        return out;
    }
}

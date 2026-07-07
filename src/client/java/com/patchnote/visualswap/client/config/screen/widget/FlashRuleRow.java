package com.patchnote.visualswap.client.config.screen.widget;

import com.patchnote.visualswap.client.config.models.FlashIntensity;
import com.patchnote.visualswap.client.config.models.FlashRule;
import com.patchnote.visualswap.client.config.models.FlashTrigger;
import com.patchnote.visualswap.client.config.models.PresetType;
import com.patchnote.visualswap.client.screen.overlay.ColorPickerOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.NonNull;

import java.util.List;

import static com.patchnote.visualswap.client.config.screen.widget.FlashRulesList.*;

/// One rule's row: item id (with a live icon), flash-input and intensity cyclers, a tint-colour swatch (click to open
/// the picker), and duplicate + delete icon buttons. It is a self-contained container widget — it positions and renders
/// its own child widgets and routes events to them — so it can be stacked by a plain
/// {@link net.minecraft.client.gui.layouts.Layout} (and scrolled by the page) instead of being an entry in a
/// self-scrolling list.
public final class FlashRuleRow extends AbstractContainerWidget
{
    /// Horizontal inset of the row content from its own edges — kept in step with the screen's column headers.
    private static final int CONTENT_PAD = 2;

    private final FlashRulesList list;
    private final FlashRule rule;
    private final List<GuiEventListener> children;

    // widgets
    private final EditBox itemBox;
    private final CycleButton<FlashTrigger> onButton;
    private final CycleButton<FlashIntensity> intensityButton;
    private final ColorSwatch colorSwatch;
    private final IconButton duplicateButton;
    private final IconButton deleteButton;

    // state
    private boolean isValid;
    private boolean conflicting;   // set by the list each frame; drives the red-cross gutter marker
    private ItemStack previewItem;

    FlashRuleRow(FlashRulesList list, FlashRule rule)
    {
        super(0, 0, list.getRowWidth(), ROW_HEIGHT, Component.empty());
        this.list = list;
        rule.normalize();  // repair a rule carried over from an older config schema before any widget reads it
        this.rule = rule;
        Item item = resolveItem(rule.item());
        this.isValid = item != Items.AIR;
        this.previewItem = getItemStack(item);

        // widgets
        this.itemBox = createItemInput(rule);
        this.onButton = createTriggerSelector(rule);
        this.intensityButton = createIntensitySelector(rule);
        this.colorSwatch = new ColorSwatch(
                COLOR_SWATCH,
                () -> 0xFF000000 | (this.rule.colorFor(this.list.preset()) & 0xFFFFFF)
        );
        this.duplicateButton = new IconButton(
                DUPLICATE_WIDTH,
                Icons.DUPLICATE,
                Component.literal("Duplicate this rule"),
                () -> this.list.duplicate(this)
        );
        this.deleteButton = new IconButton(
                DELETE_WIDTH,
                Icons.DELETE,
                Component.literal("Delete this rule"),
                () -> this.list.removeRule(this)
        );

        this.colorSwatch.setOnPress(this::openColorPicker);
        this.colorSwatch.setClickable(list.preset().isColorEditable());

        this.onButton.setTooltip(Tooltip.create(Component.literal("When this item flashes: on attack, on use, or both")));
        this.intensityButton.setTooltip(Tooltip.create(Component.literal("Flash strength (Low = subtle, High = punchy)")));

        refreshItemColor();

        this.children = List.of(
                this.itemBox,
                this.onButton,
                this.intensityButton,
                this.colorSwatch,
                this.duplicateButton,
                this.deleteButton
        );
    }

    /// A rule tint is RGB-only (its alpha byte carries the flash gamma), so the picker hides alpha and writes the
    /// picked RGB straight onto the rule (opaque), then retargets the swap preview — the same effect the old hex box's
    /// responder had.
    private void openColorPicker()
    {
        ColorPickerOverlay picker = new ColorPickerOverlay(
                this.rule.colorFor(this.list.preset()), false, argb -> {
            this.rule.setColorFor(this.list.preset(), 0xFF000000 | (argb & 0xFFFFFF));
            this.list.notifyColorEdited(this.rule);
        }
        );
        picker.position(this.colorSwatch.getX() - 8, this.colorSwatch.getY() + this.colorSwatch.getHeight() + 4);
        this.list.overlays().open(picker);
    }

    /* WIDGETS */

    private @NonNull EditBox createItemInput(FlashRule rule)
    {
        EditBox input = new EditBox(
                Minecraft.getInstance().font, //
                0, 0, 100, WIDGET_HEIGHT, Component.literal("Item identifier")
        );
        input.setMaxLength(256);
        input.setHint(Component.literal("minecraft:item"));
        input.setValue(rule.item() == null ? "" : rule.item());
        input.setResponder(this::onItemEdited);
        input.moveCursorToStart(false);
        return input;
    }

    private @NonNull CycleButton<FlashTrigger> createTriggerSelector(FlashRule rule)
    {
        FlashTrigger initial = rule.flashesAt() != null ? rule.flashesAt() : FlashTrigger.BOTH;
        return CycleButton.builder(FlashTrigger::getNameComponent, initial)
                .withValues(FlashTrigger.values())
                .displayOnlyValue()
                .create(
                        0,
                        0,
                        ON_WIDTH,
                        WIDGET_HEIGHT,
                        Component.empty(),
                        (button, value) -> this.rule.setFlashesAt(value)
                );
    }

    private @NonNull CycleButton<FlashIntensity> createIntensitySelector(FlashRule rule)
    {
        FlashIntensity initial = rule.intensity() != null ? rule.intensity() : FlashIntensity.LOW;
        return CycleButton.builder(FlashIntensity::getNameComponent, initial)
                .withValues(FlashIntensity.values())
                .displayOnlyValue()
                .create(
                        0,
                        0,
                        INTENSITY_WIDTH,
                        WIDGET_HEIGHT,
                        Component.empty(),
                        (button, value) -> this.rule.setIntensity(value)
                );
    }

    /* GETTERS */

    public FlashRule getRule() { return this.rule; }

    /// Whether this row's item id resolves to a real item — a blank or unknown id renders as an empty slot and never
    /// flashes. The screen counts these to warn before saving.
    public boolean isItemValid() { return this.isValid; }

    /// Tag this row as conflicting with another rule (same item + overlapping trigger). Recomputed by the list each
    /// frame (see {@link FlashRulesList#recomputeConflicts}); shows a red cross in the gutter and blocks saving.
    void setConflicting(boolean conflicting) { this.conflicting = conflicting; }

    /// Give keyboard focus to the item id box (used when the screen adds a fresh rule so the user can type at once).
    public void focusItemInput()
    {
        setFocused(this.itemBox);
        this.itemBox.setFocused(true);
        this.itemBox.moveCursorToEnd(false);
    }

    /* OVERRIDES */

    @Override
    public @NonNull List<? extends GuiEventListener> children()
    {
        return this.children;
    }

    @Override
    protected int contentHeight() { return getHeight(); }  // == height, so there is nothing to scroll

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a)
    {
        int left = getX() + CONTENT_PAD;
        int right = getX() + getWidth() - CONTENT_PAD;
        int midY = getY() + getHeight() / 2;
        int widgetY = midY - WIDGET_HEIGHT / 2;

        // item icon
        int iconY = midY - ICON / 2;
        if (this.previewItem.isEmpty())
        {
            g.fill(left, iconY, left + ICON, iconY + ICON, SLOT_BORDER);
            g.fill(left + 1, iconY + 1, left + ICON - 1, iconY + ICON - 1, SLOT_BG);
        }
        else
        {
            g.item(this.previewItem, left, iconY);
        }

        // right-anchored columns: trigger | intensity | colour swatch | duplicate | delete
        int deleteX = right - DELETE_WIDTH;
        int duplicateX = deleteX - ACTION_GAP - DUPLICATE_WIDTH;
        int colorX = duplicateX - GAP - COLOR_SWATCH;
        int intensityX = colorX - GAP - INTENSITY_WIDTH;
        int onX = intensityX - GAP - ON_WIDTH;

        this.deleteButton.setPosition(deleteX, widgetY);
        this.duplicateButton.setPosition(duplicateX, widgetY);
        this.intensityButton.setPosition(intensityX, widgetY);
        this.onButton.setPosition(onX, widgetY);

        // colour swatch (live), vertically centred in the row
        this.colorSwatch.setPosition(colorX, midY - COLOR_SWATCH / 2);

        // item box fills the middle
        int boxX = left + ICON + GAP;
        int boxW = Math.max(20, onX - GAP - boxX);
        this.itemBox.setX(boxX);
        this.itemBox.setY(widgetY);
        this.itemBox.setWidth(boxW);

        this.itemBox.extractRenderState(g, mouseX, mouseY, a);
        this.onButton.extractRenderState(g, mouseX, mouseY, a);
        this.intensityButton.extractRenderState(g, mouseX, mouseY, a);
        this.colorSwatch.extractRenderState(g, mouseX, mouseY, a);
        this.duplicateButton.extractRenderState(g, mouseX, mouseY, a);
        this.deleteButton.extractRenderState(g, mouseX, mouseY, a);

        // left-gutter marker: a red cross when this rule conflicts with another (same item + trigger, blocks saving),
        // else the unsaved-changes dot — green when newly added, orange when an existing rule was edited.
        if (this.conflicting)
        {
            int cross = 7;
            Icons.blit(
                    g,
                    Icons.DELETE,
                    getX() - 2 - cross,
                    midY - cross / 2,
                    cross,
                    CONFLICT_ARGB
            );  // DELETE is a ✕ glyph
        }
        else
        {
            int marker = this.rule.isNew() ? NEW_ARGB : this.rule.isModified() ? MODIFIED_ARGB : 0;
            if (marker != 0)
            {
                int dot = 6;
                Icons.blit(g, Icons.DIRTY, getX() - 2 - dot, midY - dot / 2, dot, marker);
            }
        }
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput output) { }

    /* HELPERS */

    private void onItemEdited(String value)
    {
        Item item = resolveItem(value);
        this.rule.setItem(value);
        this.isValid = item != Items.AIR;
        this.previewItem = getItemStack(item);
        refreshItemColor();
        this.list.notifyTextChanged(value);
    }

    /// Point the colour column at {@code preset}: only allow edits (picker) under Custom. The swatch tracks the list's
    /// preset live, so its colour needs no explicit refresh.
    void refreshColor(PresetType preset)
    {
        this.colorSwatch.setClickable(preset.isColorEditable());
    }

    private void refreshItemColor() { this.itemBox.setTextColor(this.isValid ? TEXT_VALID : TEXT_INVALID); }

    static Item resolveItem(String id)
    {
        Identifier ident = Identifier.tryParse(id == null ? "" : id.trim());
        if (ident == null) return Items.AIR;
        return BuiltInRegistries.ITEM.getValue(ident);
    }

    static ItemStack getItemStack(Item item)
    {
        if (item == Items.AIR) return ItemStack.EMPTY;

        // if item model already loaded
        if (BuiltInRegistries.ITEM.wrapAsHolder(item).areComponentsBound()) return new ItemStack(item);

        // load item model
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        DataComponentMap components = DataComponentMap.builder().set(DataComponents.ITEM_MODEL, id).build();
        return new ItemStack(Holder.direct(item, components));
    }
}

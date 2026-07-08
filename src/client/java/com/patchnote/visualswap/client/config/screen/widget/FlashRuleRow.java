package com.patchnote.visualswap.client.config.screen.widget;

import com.patchnote.visualswap.client.config.models.FlashIntensity;
import com.patchnote.visualswap.client.config.models.FlashRule;
import com.patchnote.visualswap.client.config.models.FlashTrigger;
import com.patchnote.visualswap.client.config.models.PresetType;
import com.patchnote.visualswap.client.screen.modal.RegexPreviewModal;
import com.patchnote.visualswap.client.screen.overlay.ColorPickerOverlay;
import com.patchnote.visualswap.client.utils.ItemIcons;
import com.patchnote.visualswap.client.utils.ItemRegex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.List;

import static com.patchnote.visualswap.client.config.screen.widget.FlashRulesList.*;

/// One rule's row: a clickable item-preview icon (opens the regex preview modal), the regex selector box, flash-input
/// and intensity cyclers, a tint-colour swatch (click to open the picker), reorder up/down buttons, and duplicate +
/// delete buttons. It is a self-contained container widget — it positions and renders its own child widgets and routes
/// events to them — so it can be stacked by a plain {@link net.minecraft.client.gui.layouts.Layout} (and scrolled by the
/// page) instead of being an entry in a self-scrolling list.
public final class FlashRuleRow extends AbstractContainerWidget
{
    /// Horizontal inset of the row content from its own edges — kept in step with the screen's column headers.
    private static final int CONTENT_PAD = 2;

    private final FlashRulesList list;
    private final FlashRule rule;
    private final List<GuiEventListener> children;

    // widgets
    private final ItemPreviewButton previewButton;
    private final EditBox itemBox;
    private final CycleButton<FlashTrigger> onButton;
    private final CycleButton<FlashIntensity> intensityButton;
    private final ColorSwatch colorSwatch;
    private final IconButton moveUpButton;
    private final IconButton moveDownButton;
    private final IconButton duplicateButton;
    private final IconButton deleteButton;

    // state — derived from the rule's regex selector
    private boolean isValid;        // pattern is valid and matches at least one registered item
    private int matchCount;         // how many items the selector matches (raw pattern reach, ignoring exclusions)
    private ItemStack previewItem;  // first matched item's stack (bind-guarded), or EMPTY

    FlashRuleRow(FlashRulesList list, FlashRule rule)
    {
        super(0, 0, list.getRowWidth(), ROW_HEIGHT, Component.empty());
        this.list = list;
        rule.normalize();  // repair a rule carried over from an older config schema before any widget reads it
        this.rule = rule;

        // widgets
        this.previewButton = new ItemPreviewButton(() -> this.previewItem, () -> this.matchCount, this::openPreviewModal);
        this.itemBox = createItemInput(rule);
        this.onButton = createTriggerSelector(rule);
        this.intensityButton = createIntensitySelector(rule);
        this.colorSwatch = new ColorSwatch(
                COLOR_SWATCH,
                () -> 0xFF000000 | (this.rule.colorFor(this.list.preset()) & 0xFFFFFF)
        );
        this.moveUpButton = new IconButton(
                MOVE_WIDTH, Icons.MOVE_UP, Component.literal("Move up (higher priority)"),
                () -> this.list.moveUp(this)
        );
        this.moveDownButton = new IconButton(
                MOVE_WIDTH, Icons.MOVE_DOWN, Component.literal("Move down (lower priority)"),
                () -> this.list.moveDown(this)
        );
        this.duplicateButton = new IconButton(
                DUPLICATE_WIDTH, Icons.DUPLICATE, Component.literal("Duplicate this rule"),
                () -> this.list.duplicate(this)
        );
        this.deleteButton = new IconButton(
                DELETE_WIDTH, Icons.DELETE, Component.literal("Delete this rule"),
                () -> this.list.removeRule(this)
        );

        this.colorSwatch.setOnPress(this::openColorPicker);
        this.colorSwatch.setClickable(list.preset().isColorEditable());

        this.onButton.setTooltip(Tooltip.create(Component.literal("When this item flashes: on attack, on use, or both")));
        this.intensityButton.setTooltip(Tooltip.create(Component.literal("Flash strength (Low = subtle, High = punchy)")));

        refreshMatches();

        this.children = List.of(
                this.previewButton,
                this.itemBox,
                this.onButton,
                this.intensityButton,
                this.colorSwatch,
                this.moveUpButton,
                this.moveDownButton,
                this.duplicateButton,
                this.deleteButton
        );
    }

    private void openPreviewModal() { RegexPreviewModal.open(this.rule); }

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
                0, 0, 100, WIDGET_HEIGHT, Component.literal("Item selector (regex)")
        );
        input.setMaxLength(256);
        input.setHint(Component.literal("filter (e.g \".*\")"));
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

    /// Whether this row's selector is a valid regex that matches at least one registered item — a blank/unparseable
    /// pattern, or one matching nothing, renders an empty slot and never flashes. The screen counts these to block Done.
    public boolean isItemValid() { return this.isValid; }

    /// Enable/disable the reorder buttons — the list calls these each layout pass so the top visible row can't move up
    /// and the bottom can't move down.
    void setCanMoveUp(boolean can) { this.moveUpButton.active = can; }

    void setCanMoveDown(boolean can) { this.moveDownButton.active = can; }

    /// Give keyboard focus to the selector box (used when the screen adds a fresh rule so the user can type at once).
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

        // clickable item preview
        this.previewButton.setPosition(left, midY - ICON / 2);

        // right-anchored columns: trigger | intensity | colour | up | down | duplicate | delete
        int deleteX = right - DELETE_WIDTH;
        int duplicateX = deleteX - ACTION_GAP - DUPLICATE_WIDTH;
        int downX = duplicateX - ACTION_GAP - MOVE_WIDTH;
        int upX = downX - ACTION_GAP - MOVE_WIDTH;
        int colorX = upX - GAP - COLOR_SWATCH;
        int intensityX = colorX - GAP - INTENSITY_WIDTH;
        int onX = intensityX - GAP - ON_WIDTH;

        this.deleteButton.setPosition(deleteX, widgetY);
        this.duplicateButton.setPosition(duplicateX, widgetY);
        this.moveDownButton.setPosition(downX, widgetY);
        this.moveUpButton.setPosition(upX, widgetY);
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

        this.previewButton.extractRenderState(g, mouseX, mouseY, a);
        this.itemBox.extractRenderState(g, mouseX, mouseY, a);
        this.onButton.extractRenderState(g, mouseX, mouseY, a);
        this.intensityButton.extractRenderState(g, mouseX, mouseY, a);
        this.colorSwatch.extractRenderState(g, mouseX, mouseY, a);
        this.moveUpButton.extractRenderState(g, mouseX, mouseY, a);
        this.moveDownButton.extractRenderState(g, mouseX, mouseY, a);
        this.duplicateButton.extractRenderState(g, mouseX, mouseY, a);
        this.deleteButton.extractRenderState(g, mouseX, mouseY, a);

        // left-gutter marker: the unsaved-changes dot — green when newly added, orange when an existing rule was edited.
        int marker = this.rule.isNew() ? NEW_ARGB : this.rule.isModified() ? MODIFIED_ARGB : 0;
        if (marker != 0)
        {
            int dot = 6;
            Icons.blit(g, Icons.DIRTY, getX() - 2 - dot, midY - dot / 2, dot, marker);
        }
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput output) { }

    /* HELPERS */

    private void onItemEdited(String value)
    {
        this.rule.setItem(value);
        refreshMatches();
        this.list.notifyTextChanged(value);
    }

    /// Recompute the selector's reach: validity, match count, and the preview icon (the first matched item). Called on
    /// construction and after every keystroke in the selector box.
    private void refreshMatches()
    {
        ItemRegex.Summary summary = ItemRegex.summarize(this.rule.pattern());
        this.matchCount = summary.count();
        this.isValid = summary.count() >= 1;
        this.previewItem = (summary.first() != null)
                           ? ItemIcons.stackFor(BuiltInRegistries.ITEM.getValue(summary.first()))
                           : ItemStack.EMPTY;
        refreshItemColor();
    }

    /// Point the colour column at {@code preset}: only allow edits (picker) under Custom. The swatch tracks the list's
    /// preset live, so its colour needs no explicit refresh.
    void refreshColor(PresetType preset)
    {
        this.colorSwatch.setClickable(preset.isColorEditable());
    }

    private void refreshItemColor() { this.itemBox.setTextColor(this.isValid ? TEXT_VALID : TEXT_INVALID); }
}

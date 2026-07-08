package com.patchnote.visualswap.client.config.screen;

import com.patchnote.visualswap.client.config.ModConfig;
import com.patchnote.visualswap.client.config.models.FlashRule;
import com.patchnote.visualswap.client.config.models.Preset;
import com.patchnote.visualswap.client.config.models.PresetType;
import com.patchnote.visualswap.client.config.screen.widget.*;
import com.patchnote.visualswap.client.hud.click.ItemFlashPreview;
import com.patchnote.visualswap.client.screen.modal.ConfirmModal;
import com.patchnote.visualswap.client.screen.overlay.ColorPickerOverlay;
import com.patchnote.visualswap.client.screen.overlay.OverlayManager;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/// The mod's config screen: a fixed title header, a fixed rules header (search box + column captions + Add/Clear/Reset
/// icon buttons), a scrollable middle (preset selector, size slider, From/To colour swatches, and the rules table), and
/// a fixed Discard/Cancel/Done footer, laid out with the vanilla `layouts` package and wrapped in a
/// {@link ScrollableLayout}. The scroll viewport gets the vanilla list look — a tiled dark panel with top/bottom
/// separators (drawn in {@link #styleScrollPanel}) plus the scrollbar the ScrollableLayout draws itself.
///
/// Nothing is written to {@link ModConfig} until "Done": the screen edits working copies ({@link #workingType},
/// {@link #workingCustom}, and the {@link FlashRulesList}'s rule copies), compared against the saved snapshot captured
/// at construction to drive the unsaved-changes ("dirty") state. Reset/Discard/Clear route through a
/// {@link ConfirmModal}; colours are edited via the {@link ColorPickerOverlay} opened from each swatch.
public final class VisualSwapConfigScreen extends Screen
{
    private static final int CHIP_H = 20;
    private static final int COL_GAP = 8;       // between the top grid's columns

    // Custom-colour row: a "From"/"To" caption then a colour swatch (click opens the picker).
    private static final int COLOR_LABEL_W = 34;
    private static final int COLOR_SWATCH = 16;
    private static final int COLOR_GAP = 6;     // caption → swatch

    // Vertical rhythm of the scrollable content column.
    private static final int CONTENT_SPACING = 6;
    private static final int COLOR_ROW_GAP = 4; // From ↔ To sit tighter as a pair
    private static final int SECTION_GAP = 8;   // extra breathing room above the rules table
    private static final int SCROLL_MIN_HEIGHT = 10;
    private static final int SCROLLBAR_RESERVE = 10; // ScrollableLayout's per-side reserve (spacing 4 + scrollbar 6)

    // StringWidget colours are RGB (styled via Component#withColor); swatch colours are ARGB.
    private static final int LABEL_RGB = 0xB9B9C0;
    private static final int MUTED_RGB = 0x8A8A90;      // count label + empty-state message
    private static final int DIRTY_ARGB = 0xFFF0B84C;   // amber unsaved-changes dot beside the title
    private static final int CONFLICT_ARGB = 0xFFE0453A; // red cross beside the title while a conflict blocks saving

    // Vanilla list-panel textures (menu_list_background is private in AbstractSelectionList, so re-declared here).
    private static final Identifier MENU_LIST_BACKGROUND = Identifier.withDefaultNamespace(
            "textures/gui/menu_list_background.png");
    private static final Identifier INWORLD_MENU_LIST_BACKGROUND = Identifier.withDefaultNamespace(
            "textures/gui/inworld_menu_list_background.png");

    private final Screen parent;

    /// Which preset is selected, and a working copy of the Custom preset's settings — preserved across preset toggles
    /// so switching away and back doesn't lose in-progress Custom edits. Only committed to ModConfig on "Done".
    private PresetType workingType;
    private Preset workingCustom;

    /// Working copy of the global flash-visible-ticks setting (not preset-scoped); committed to ModConfig on "Done".
    private int workingVisibleTicks;

    /// The saved config as captured on open — the baseline the working state is diffed against to detect unsaved edits,
    /// and the values a "Discard" reverts to.
    private final PresetType savedType;
    private final Preset savedCustom;
    private final int savedVisibleTicks;
    private final List<FlashRule> savedRules;

    /// When set, {@link #init} seeds the rules table from this list (instead of carrying the current rows over) — used
    /// by Reset rules and Discard to replace the whole table. Consumed on the next init.
    private @Nullable List<FlashRule> pendingRules;

    /// Case-insensitive item-id filter for the rules table, plus one-shot flags asking init to re-focus the search box
    /// after a filter rebuild, or the freshly-added top row after an add.
    private String filterText = "";
    private boolean refocusSearch;
    private boolean focusNewRow;

    /// Recomputed each init; drives the header dot, the Discard button, and the confirm-before-leaving guard.
    private boolean isModified;

    /// Number of rules that conflict with another (same item + overlapping trigger). Recomputed live every frame — a
    /// trigger/item edit can create or clear a conflict without a rebuild — and while it is > 0 the title shows a red
    /// cross and {@link #doneButton} is disabled, so a conflicting config can never be saved.
    private int conflictCount;
    private @Nullable Button doneButton;   // held so the live conflict check can toggle whether saving is allowed

    /// One-shot: when set, the next rebuild snaps the rules viewport to the top (a filter/reset/discard replaces the
    /// visible set) instead of preserving the prior scroll position.
    private boolean resetScroll;

    private HeaderAndFooterLayout layout;
    private RuleColumnsHeader tableHeader;
    private ScrollableLayout scrollArea;
    private @Nullable GuiEventListener scrollContainer;   // the ScrollableLayout's inner scroll widget (for row focus)
    private SizeSlider slider;
    private FlashRulesList list;

    /// The screen's floating layer: hover tooltips + the colour-picker popup.
    private final OverlayManager overlays = new OverlayManager();

    /// The rule the swap preview's flashing slot follows — the last rule whose colour the user edited. Across screen
    /// rebuilds (where every rule instance is recreated) the target is re-resolved by its position in the rebuild seed,
    /// which maps 1:1 onto the new rows — item ids can be duplicated or mid-edit, so they are no identity.
    private @Nullable FlashRule previewRule;

    public VisualSwapConfigScreen(Screen parent)
    {
        super(Component.literal("Visual Swap"));
        this.parent = parent;

        ModConfig cfg = ModConfig.get();
        this.workingType = cfg.preset;
        this.workingCustom = new Preset(cfg.customPresetData);
        this.workingVisibleTicks = cfg.flashVisibleTicks;

        this.savedType = cfg.preset;
        this.savedCustom = new Preset(cfg.customPresetData);
        this.savedVisibleTicks = cfg.flashVisibleTicks;
        this.savedRules = cfg.clickFlashRules.stream()
                .map(FlashRule::new).toList();
        // Each saved rule is its own baseline; working copies inherit the link (copy ctor) to drive per-row markers.
        for (FlashRule saved : this.savedRules) saved.setSavedOrigin(saved);
    }

    @Override
    protected void init()
    {
        this.overlays.close();  // rebuild invalidates overlay anchors

        double previousScroll = currentScroll();   // preserved across the rebuild (unless resetScroll snaps to top)

        // Choose the rules seed: an explicit pending seed (reset/discard) wins; otherwise carry the current rows over
        // (preserving in-progress edits across a resize / add / remove); otherwise the saved snapshot (first open).
        List<FlashRule> rules;
        if (this.pendingRules != null)
        {
            rules = this.pendingRules;
            this.pendingRules = null;
        }
        else if (this.list != null) { rules = this.list.toRules(); }
        else { rules = this.savedRules; }

        int previewIdx = (this.previewRule != null) ? rules.indexOf(this.previewRule) : -1;

        this.isModified = isModified(rules);
        boolean resetRulesEnabled = !FlashRule.listsSameValues(rules, FlashRule.defaultFlashRules());
        boolean resetColorsEnabled =
                this.workingType.isCustom() && !this.workingCustom.sameValuesAs(PresetType.CUSTOM.createDefault());

        this.layout = new HeaderAndFooterLayout(this);
        this.layout.addTitleHeader(getTitle(), this.font);

        int rowWidth = Math.min(360, this.width - 60);
        int colW = (rowWidth - HotbarSwapPreview.WIDTH - 2 * COL_GAP) / 2;  // the preset/slider and From/To columns

        // --- scrollable content column ---
        LinearLayout content = LinearLayout.vertical().spacing(CONTENT_SPACING);

        // top controls, a 2x3 grid:  preset | From colour | swap preview
        //                            slider | To colour   | reset-colours button
        GridLayout top = new GridLayout().columnSpacing(COL_GAP).rowSpacing(COLOR_ROW_GAP);
        top.defaultCellSetting().alignVerticallyMiddle();

        CycleButton<PresetType> presetButton = CycleButton.<PresetType>builder(
                PresetType::getNameComponent,
                this.workingType
        ).withValues(PresetType.values()).create(
                0, 0, colW, CHIP_H, //
                Component.literal("Preset"), (button, value) -> setPreset(value)
        );
        top.addChild(presetButton, 0, 0);

        this.slider = new SizeSlider(
                0, 0, colW, CHIP_H, //
                this.workingType.getDisplayName(), effectiveSize(), this.workingCustom::setSizeMultiplier
        );
        this.slider.active = this.workingType.isColorEditable();
        this.slider.setTooltip(Tooltip.create(Component.literal("Scale of the swap highlight overlay.")));
        top.addChild(this.slider, 1, 0);

        // From/To highlight colours — a caption + a swatch that opens the colour picker (Custom only)
        top.addChild(colorRow("From", this::effectiveFrom, this.workingCustom::setFromColor), 0, 1);
        top.addChild(colorRow("To", this::effectiveTo, this.workingCustom::setToColor), 1, 1);

        top.addChild(
                new HotbarSwapPreview(
                        this::effectiveFrom,
                        this::effectiveTo,
                        () -> this.workingType,
                        () -> this.previewRule,
                        this.overlays
                ), 0, 2
        );

        IconButton resetColorsButton = new IconButton(
                CHIP_H, Icons.RESET, //
                Component.literal("Reset colours & size to defaults"), this::confirmResetColors
        );
        resetColorsButton.active = resetColorsEnabled;
        top.addChild(resetColorsButton, 1, 2, LayoutSettings::alignHorizontallyLeft);

        content.addChild(top, LayoutSettings::alignHorizontallyCenter);

        // global flash-duration slider (not preset-scoped, so always editable)
        TicksSlider ticksSlider = new TicksSlider(
                0, 0, rowWidth, CHIP_H, this.workingVisibleTicks, ticks -> this.workingVisibleTicks = ticks
        );
        ticksSlider.setTooltip(
                Tooltip.create(Component.literal("How many ticks a clicked item's flash tint stays visible.")));
        content.addChild(ticksSlider, LayoutSettings::alignHorizontallyCenter);

        // rules table — build first so the count + empty-state can read its filtered size
        this.list = new FlashRulesList(
                rowWidth,
                this.workingType,
                rules,
                this::rebuildWidgets,
                rule -> this.previewRule = rule,
                _ -> applyDoneState(
                        this.list.invalidCount() == 0 ? DoneButtonState.ENABLED : DoneButtonState.INVALID_RULE),
                this.overlays
        );
        this.list.setFilter(this.filterText);
        this.previewRule = this.list.ruleAt(previewIdx, "minecraft:mace");
        this.conflictCount = this.list.recomputeConflicts();
        // --- fixed rules header (search + column captions + Add/Clear/Reset); positioned in arrangeContents ---
        this.tableHeader = new RuleColumnsHeader(
                rowWidth,
                this.filterText,
                this::onSearchEdited,
                this::addRuleClearingFilter,
                this::confirmClear,
                this::confirmResetRules,
                this.list.totalCount() > 0,
                resetRulesEnabled
        );
        content.addChild(this.tableHeader, LayoutSettings::alignVerticallyMiddle);

        if (!this.filterText.isEmpty())
        {
            LinearLayout row = LinearLayout.horizontal();
            row.addChild(SpacerElement.width(rowWidth - this.font.width(countText())));
            row.addChild(new StringWidget(Component.literal(countText()).withColor(MUTED_RGB), this.font));
            content.addChild(row);
        }

        content.addChild(this.list, LayoutSettings::alignHorizontallyCenter);

        if (this.list.visibleCount() == 0)
        {
            String msg = this.list.totalCount() == 0
                         ? "No rules yet — use the + button above to add one"
                         : "No rules match \"" + this.filterText + "\"";
            content.addChild(
                    new StringWidget(Component.literal(msg).withColor(MUTED_RGB), this.font),
                    s -> s.alignHorizontallyCenter().paddingVertical(SECTION_GAP)
            );
        }

        // --- wrap the content in a full-width scroll viewport so the panel + scrollbar reach the screen edges ---
        FrameLayout viewport = new FrameLayout();
        viewport.setMinWidth(this.width - 2 * SCROLLBAR_RESERVE);
        viewport.addChild(content);
        this.scrollArea = new ScrollableLayout(this.minecraft, viewport, this.layout.getContentHeight());
        this.layout.addToContents(this.scrollArea);

        // --- footer: revert / leave / save ---
        LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        Button discardButton = Button.builder(Component.literal("Discard"), b -> confirmDiscard()).width(80).build();
        discardButton.active = this.isModified;
        discardButton.setTooltip(Tooltip.create(Component.literal("Revert all unsaved changes to your saved config.")));
        footer.addChild(discardButton);
        footer.addChild(Button.builder(Component.literal("Cancel"), b -> onClose()).width(80).build());
        this.doneButton = Button.builder(Component.literal("Done"), b -> onDone()).width(80).build();
        applyDoneState(this.conflictCount == 0 ? DoneButtonState.ENABLED : DoneButtonState.CONFLICTS);
        footer.addChild(this.doneButton);

        this.layout.visitWidgets(this::addRenderableWidget);
        this.scrollContainer = null;
        this.scrollArea.visitWidgets(w -> this.scrollContainer = w);   // capture the inner scroll widget for row focus

        arrangeContents();
        restoreScroll(previousScroll);
        applyPendingFocus();
    }

    /// The fixed header, then the scroll viewport below it — dropping HeaderAndFooterLayout's ~30px content margin.
    private void arrangeContents()
    {
        this.layout.arrangeElements();
        int available = this.height - this.layout.getFooterHeight() - this.layout.getHeaderHeight();

        // set scrollArea position and size
        this.scrollArea.setY(this.layout.getHeaderHeight());
        this.scrollArea.setMaxHeight(Math.max(SCROLL_MIN_HEIGHT, available));
    }

    /// Honor a pending focus request from the last action: the search box after a filter edit, or the new top row's
    /// item box after an add. Kept to the end of init so the widgets exist and are positioned.
    private void applyPendingFocus()
    {
        if (this.refocusSearch)
        {
            setFocused(this.tableHeader);
            this.tableHeader.focusSearch();
            this.refocusSearch = false;
        }
        else if (this.focusNewRow)
        {
            FlashRuleRow row = this.list.firstRow();
            if (row != null && this.scrollContainer instanceof ContainerEventHandler container)
            {
                setFocused(this.scrollContainer);
                container.setFocused(row);
                row.focusItemInput();
                ensureRowVisible(row);
            }
            this.focusNewRow = false;
        }
    }

    /// The rules viewport's current scroll offset (0 before the first init, when there is no container yet).
    private double currentScroll()
    {
        return (this.scrollContainer instanceof AbstractScrollArea area) ? area.scrollAmount() : 0.0;
    }

    /// After a rebuild, put the viewport back where it was — so adding, duplicating or deleting a row keeps the user's
    /// place instead of snapping to the top. A pending {@link #resetScroll} (filter/reset/discard) forces the top.
    private void restoreScroll(double previous)
    {
        if (this.scrollContainer instanceof AbstractScrollArea area)
        {
            area.setScrollAmount(this.resetScroll ? 0.0 : previous);   // setScrollAmount clamps to the new max
        }
        this.resetScroll = false;
    }

    /// Scroll the rules viewport just enough to bring {@code row} fully into view (a no-op when it already is).
    private void ensureRowVisible(FlashRuleRow row)
    {
        if (!(this.scrollContainer instanceof AbstractScrollArea area)) return;

        int viewTop = this.scrollArea.getY();
        int viewBottom = viewTop + this.scrollArea.getHeight();
        int rowTop = row.getY();
        int rowBottom = rowTop + row.getHeight();

        double scroll = area.scrollAmount();
        double target = scroll;
        if (rowTop < viewTop) target = scroll - (viewTop - rowTop);
        else if (rowBottom > viewBottom) target = scroll + (rowBottom - viewBottom);
        area.setScrollAmount(Math.clamp(target, 0.0, area.maxScrollAmount()));
    }

    /// One From/To colour row: a caption and a swatch that opens the picker (Custom preset only). The picker writes the
    /// picked ARGB straight to {@code onEdit}, and the swatch reads {@code color} live, so no rebuild is needed.
    private LinearLayout colorRow(String label, IntSupplier color, IntConsumer onEdit)
    {
        LinearLayout row = LinearLayout.horizontal().spacing(COLOR_GAP);
        row.addChild(
                new StringWidget(COLOR_LABEL_W, CHIP_H, Component.literal(label).withColor(LABEL_RGB), this.font),
                LayoutSettings::alignVerticallyMiddle
        );

        ColorSwatch swatch = new ColorSwatch(COLOR_SWATCH, color);
        swatch.setOnPress(() -> openPicker(swatch, color.getAsInt(), onEdit));
        swatch.setClickable(this.workingType.isColorEditable());
        row.addChild(swatch, LayoutSettings::alignVerticallyMiddle);
        return row;
    }

    /// Open the colour picker (with alpha — From/To colours are AARRGGBB) anchored under {@code anchor}.
    private void openPicker(ColorSwatch anchor, int current, IntConsumer apply)
    {
        ColorPickerOverlay picker = new ColorPickerOverlay(current, true, apply);
        picker.position(anchor.getX() - 8, anchor.getY() + anchor.getHeight() + 4);
        this.overlays.open(picker);
    }

    /// Switch the active preset. A full rebuild re-derives colour editability, the greyed controls, the rules table's
    /// preset column, and the dirty/reset button states — the Custom edits survive in {@link #workingCustom}.
    private void setPreset(PresetType type)
    {
        this.workingType = type;
        rebuildWidgets();
    }

    /* EFFECTIVE VALUES — the Custom working copy under Custom, else the preset's fixed default. */

    private double effectiveSize()
    {
        return this.workingType.isCustom() ? this.workingCustom.getSizeMultiplier() : this.workingType.getSize();
    }

    private int effectiveFrom()
    {
        return this.workingType.isCustom() ? this.workingCustom.getFromColor() : this.workingType.getFromColor();
    }

    private int effectiveTo()
    {
        return this.workingType.isCustom() ? this.workingCustom.getToColor() : this.workingType.getToColor();
    }

    /* RULES HEADER / ACTIONS */

    private void onSearchEdited(String text)
    {
        if (text.equals(this.filterText)) return;
        this.filterText = text;
        this.refocusSearch = true;
        this.resetScroll = true;   // a filter change replaces the visible set — show it from the top
        rebuildWidgets();
    }

    /// Add a fresh rule at the top — clearing any active filter first so the new (blank) row isn't hidden by it — and
    /// focus its item box on the rebuild.
    private void addRuleClearingFilter()
    {
        this.filterText = "";
        this.focusNewRow = true;
        this.list.addRule();   // inserts at top and triggers a rebuild
    }

    private void confirmResetRules()
    {
        openConfirm(
                "Reset rules?",
                List.of("Replace the whole table with the default rule set?"),
                "Reset",
                this::doResetRules
        );
    }

    private void confirmResetColors()
    {
        openConfirm(
                "Reset colours?",
                List.of("Reset the highlight colours and size to their defaults?"),
                "Reset",
                this::doResetColors
        );
    }

    private void confirmClear()
    {
        openConfirm(
                "Clear all rules?",
                List.of("Remove every rule from the table?"),
                "Clear all",
                () -> this.list.clear()
        );
    }

    private void confirmDiscard()
    {
        openConfirm(
                "Discard changes?",
                List.of("Revert every unsaved change to your saved config?"),
                "Discard",
                this::doDiscard
        );
    }

    private void doResetRules()
    {
        List<FlashRule> defaults = FlashRule.defaultFlashRules();
        linkToSavedByValue(defaults);   // defaults that match a saved rule read as unchanged; the rest as newly added
        this.pendingRules = defaults;
        this.resetScroll = true;
        rebuildWidgets();
    }

    /// Point each rule in {@code rules} at the first not-yet-claimed saved rule with equal values, so a bulk replace
    /// (reset) shows a green/orange marker only for rows that actually differ from the saved config. Unmatched rules
    /// keep their null origin and read as newly added.
    private void linkToSavedByValue(List<FlashRule> rules)
    {
        boolean[] claimed = new boolean[this.savedRules.size()];
        for (FlashRule rule : rules)
        {
            for (int i = 0; i < this.savedRules.size(); i++)
            {
                if (!claimed[i] && rule.sameValuesAs(this.savedRules.get(i)))
                {
                    rule.setSavedOrigin(this.savedRules.get(i));
                    claimed[i] = true;
                    break;
                }
            }
        }
    }

    private void doResetColors()
    {
        this.workingCustom = PresetType.CUSTOM.createDefault();
        rebuildWidgets();
    }

    private void doDiscard()
    {
        this.workingType = this.savedType;
        this.workingCustom = new Preset(this.savedCustom);
        this.workingVisibleTicks = this.savedVisibleTicks;
        this.filterText = "";
        this.pendingRules = this.savedRules;
        this.list = null;
        this.resetScroll = true;
        rebuildWidgets();
    }

    /// Open a modal confirmation (its own screen) for a destructive action; only Confirm runs {@code action}.
    private void openConfirm(String title, List<String> lines, String confirmLabel, Runnable action)
    {
        ConfirmModal.open(
                Component.literal(title),
                lines.stream()
                        .<Component>map(Component::literal).toList(),
                Component.literal(confirmLabel),
                action
        );
    }

    /// The rule-count line above the table: "N of M shown" while a filter is active.
    private String countText()
    {
        return this.list.visibleCount() + " of " + this.list.totalCount() + " shown";
    }

    /* DIRTY STATE / SAVE / CLOSE */

    private boolean isModified(List<FlashRule> currentRules)
    {
        return this.workingType != this.savedType || this.workingVisibleTicks != this.savedVisibleTicks ||
                !this.workingCustom.sameValuesAs(this.savedCustom) ||
                !FlashRule.listsSameValues(currentRules, this.savedRules);
    }

    /// Recompute the live conflict count (trigger/item edits don't rebuild the page) and toggle whether saving is
    /// allowed. Called each frame before rendering, so the red cross and the disabled Done button stay in step with
    /// edits the user just made.
    private void updateConflictState()
    {
        boolean wasBlocked = this.conflictCount > 0;
        this.conflictCount = (this.list != null) ? this.list.recomputeConflicts() : 0;
        if ((this.conflictCount > 0) != wasBlocked)
        {
            applyDoneState(this.conflictCount == 0 ? DoneButtonState.ENABLED : DoneButtonState.CONFLICTS);
        }
    }

    /// Enable/disable the Done button and set the matching tooltip — a conflicting config can never be saved.
    private void applyDoneState(DoneButtonState newState)
    {
        if (this.doneButton == null) return;
        this.doneButton.active = newState.allowsSaving();
        this.doneButton.setTooltip(Tooltip.create(Component.literal(newState.getMessage())));
    }

    private void onDone()
    {
        int invalid = this.list.invalidCount();
        if (this.conflictCount > 0 || invalid > 0) return;
        saveConfig();
    }

    private void saveConfig()
    {
        ModConfig cfg = ModConfig.get();
        cfg.preset = this.workingType;
        cfg.customPresetData = new Preset(this.workingCustom);
        cfg.flashVisibleTicks = this.workingVisibleTicks;
        cfg.clickFlashRules = this.list.toRules();
        AutoConfig.getConfigHolder(ModConfig.class).save();
        this.minecraft.setScreenAndShow(this.parent);
    }

    @Override
    public void onClose()
    {
        List<FlashRule> current = (this.list != null) ? this.list.toRules() : this.savedRules;
        if (isModified(current))
        {
            openConfirm(
                    "Discard unsaved changes?",
                    List.of("You have unsaved changes. Leave without saving?"),
                    "Discard",
                    () -> this.minecraft.setScreenAndShow(this.parent)
            );
        }
        else
        {
            this.minecraft.setScreenAndShow(this.parent);
        }
    }

    /* GUI */

    /// Drop the preview's flash registrations so a later screen's item at the same coords can't get silhouetted.
    @Override
    public void removed() { ItemFlashPreview.clear(); }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a)
    {
        updateConflictState();
        styleScrollPanel(graphics);
        // move mouse offscreen for screen's widget when modal is opened
        boolean modal = this.overlays.isModalOpen();
        super.extractRenderState(graphics, modal ? -1 : mouseX, modal ? -1 : mouseY, a);
        addStatusMarker(graphics);
        this.overlays.extract(graphics, mouseX, mouseY, a);
    }

    private void styleScrollPanel(GuiGraphicsExtractor graphics)
    {
        if (this.scrollArea == null) return;

        int x = this.scrollArea.getX();
        int y = this.scrollArea.getY();
        int w = this.scrollArea.getWidth();
        int h = this.scrollArea.getHeight();
        boolean inWorld = this.minecraft.level != null;

        Identifier background = inWorld ? INWORLD_MENU_LIST_BACKGROUND : MENU_LIST_BACKGROUND;
        graphics.blit(RenderPipelines.GUI_TEXTURED, background, x, y, (float) (x + w), (float) (y + h), w, h, 32, 32);

        Identifier headerSeparator = inWorld ? INWORLD_HEADER_SEPARATOR : HEADER_SEPARATOR;
        Identifier footerSeparator = inWorld ? INWORLD_FOOTER_SEPARATOR : FOOTER_SEPARATOR;
        graphics.blit(RenderPipelines.GUI_TEXTURED, headerSeparator, x, y - 2, 0.0f, 0.0f, w, 2, 32, 2);
        graphics.blit(RenderPipelines.GUI_TEXTURED, footerSeparator, x, y + h, 0.0f, 0.0f, w, 2, 32, 2);
    }

    private void addStatusMarker(GuiGraphicsExtractor graphics)
    {
        if (this.layout == null) return;
        int size = 8;
        int iconX = this.width / 2 + this.font.width(getTitle()) / 2 + 4;
        int iconY = (this.layout.getHeaderHeight() - size) / 2;
        if (this.conflictCount > 0)
        {
            Icons.blit(graphics, Icons.DELETE, iconX, iconY, size, CONFLICT_ARGB);   // DELETE is a ✕ glyph
        }
        else if (this.isModified)
        {
            Icons.blit(graphics, Icons.DIRTY, iconX, iconY, size, DIRTY_ARGB);
        }
    }

    /* INPUT */

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick)
    {
        return this.overlays.mouseClicked(event, doubleClick) || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event)
    {
        return this.overlays.mouseReleased(event) || super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dx, double dy)
    {
        return this.overlays.mouseDragged(event, dx, dy) || super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY)
    {
        return this.overlays.mouseScrolled(x, y, scrollX, scrollY) || super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public void mouseMoved(double x, double y)
    {
        this.overlays.mouseMoved(x, y);
        super.mouseMoved(x, y);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event)
    {
        return this.overlays.keyPressed(event) || super.keyPressed(event);
    }

    @Override
    public boolean charTyped(@NonNull CharacterEvent event)
    {
        return this.overlays.charTyped(event) || super.charTyped(event);
    }

    /* HELPERS */

    public enum DoneButtonState
    {
        ENABLED("Save your changes and close."),
        CONFLICTS("Resolve the conflicting rules before saving."),
        INVALID_RULE("Make sure all item identifiers are valid");

        private final String message;

        DoneButtonState(String message)
        {
            this.message = message;
        }

        public String getMessage() { return message; }

        public boolean allowsSaving() { return this.equals(ENABLED); }
    }
}

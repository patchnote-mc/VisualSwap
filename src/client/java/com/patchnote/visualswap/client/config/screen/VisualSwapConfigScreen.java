package com.patchnote.visualswap.client.config.screen;

import com.patchnote.visualswap.client.config.ModConfig;
import com.patchnote.visualswap.client.config.models.FlashRule;
import com.patchnote.visualswap.client.config.models.Preset;
import com.patchnote.visualswap.client.config.models.PresetType;
import com.patchnote.visualswap.client.config.screen.widget.*;
import com.patchnote.visualswap.client.hud.click.ItemFlashPreview;
import com.patchnote.visualswap.client.screen.modal.ConfirmModal;
import com.patchnote.visualswap.client.screen.modal.EffectsModal;
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
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
    private static final int EFFECTS_BTN_W = 64; // header "Effects" button that opens the toggles modal
    private static final int EFFECTS_BTN_MARGIN = 8;

    // Custom-colour row: a "From"/"To" caption then a colour swatch (click opens the picker).
    private static final int COLOR_LABEL_W = 34;
    private static final int COLOR_SWATCH_SIZE = 16;
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

    /// Working copies of the master toggles (not preset-scoped); committed to ModConfig on "Done".
    private boolean workingModEnabled;
    private boolean workingHudEnabled;
    private boolean workingHotbarHighlightEnabled;
    private boolean workingItemFlashEnabled;
    private boolean workingFlashOnlyOnSwap;
    private boolean workingClearPreviousFlashOnSwap;
    private boolean workingParticlesEnabled;

    /// The saved config as captured on open — the baseline the working state is diffed against to detect unsaved edits,
    /// and the values a "Discard" reverts to.
    private final PresetType savedType;
    private final Preset savedCustom;
    private final int savedVisibleTicks;
    private final boolean savedModEnabled;
    private final boolean savedHudEnabled;
    private final boolean savedHotbarHighlightEnabled;
    private final boolean savedItemFlashEnabled;
    private final boolean savedFlashOnlyOnSwap;
    private final boolean savedClearPreviousFlashOnSwap;
    private final boolean savedParticlesEnabled;
    private final List<FlashRule> savedRules;

    /// When set, {@link #init} seeds the rules table from this list (instead of carrying the current rows over) — used
    /// by Reset rules and Discard to replace the whole table. Consumed on the next init.
    private @Nullable List<FlashRule> pendingRules;

    /// Case-insensitive item-id filter for the rules table, plus one-shot flags asking init to re-focus the search box
    /// after a filter rebuild, or the freshly-added top row after an add.
    private String filterText = "";
    private boolean refocusSearch;
    private boolean focusNewRow;

    /// One-shot: set when the "set all colours" confirm is accepted, so the next init opens the bulk-tint picker — the
    /// confirm-modal's return re-runs init (clearing overlays), so the picker must be (re)opened after that, not
    /// during.
    private boolean openBulkColorPicker;

    /// Recomputed each init; drives the header dot, the Discard button, and the confirm-before-leaving guard.
    private boolean isModified;

    private @Nullable Button doneButton;   // held so the live invalid-rule check can toggle whether saving is allowed
    private @Nullable Button discardButton;   // held so in-place edits can refresh its dirty-state gate
    private @Nullable IconButton resetColorsButton;   // held so live Custom edits can refresh the reset gate

    private @Nullable Button effectsButton;   // header button opening the EffectsModal; positioned in arrangeContents

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

    /// Preview-only backdrop selection for the glyph tiles; retained across widget rebuilds, never persisted.
    private boolean glyphPreviewSky;

    /// Glyph colour targets in click order. A normal click replaces the set; Shift-click adds/removes targets.
    private final Set<String> selectedGlyphs = new LinkedHashSet<>(List.of("possible"));

    public VisualSwapConfigScreen(Screen parent)
    {
        super(Component.translatable("gui.visual-swap.title"));
        this.parent = parent;

        ModConfig cfg = ModConfig.get();
        this.workingType = cfg.preset;
        this.workingCustom = new Preset(cfg.customPresetData);
        this.workingVisibleTicks = cfg.flashVisibleTicks;
        this.workingModEnabled = cfg.modEnabled;
        this.workingHudEnabled = cfg.hudEnabled;
        this.workingHotbarHighlightEnabled = cfg.hotbarHighlightEnabled;
        this.workingItemFlashEnabled = cfg.itemFlashEnabled;
        this.workingFlashOnlyOnSwap = cfg.flashOnlyOnSwap;
        this.workingClearPreviousFlashOnSwap = cfg.clearPreviousFlashOnSwap;
        this.workingParticlesEnabled = cfg.particlesEnabled;

        this.savedType = cfg.preset;
        this.savedCustom = new Preset(cfg.customPresetData);
        this.savedVisibleTicks = cfg.flashVisibleTicks;
        this.savedModEnabled = cfg.modEnabled;
        this.savedHudEnabled = cfg.hudEnabled;
        this.savedHotbarHighlightEnabled = cfg.hotbarHighlightEnabled;
        this.savedItemFlashEnabled = cfg.itemFlashEnabled;
        this.savedFlashOnlyOnSwap = cfg.flashOnlyOnSwap;
        this.savedClearPreviousFlashOnSwap = cfg.clearPreviousFlashOnSwap;
        this.savedParticlesEnabled = cfg.particlesEnabled;
        this.savedRules = cfg.clickFlashRules.stream().map(FlashRule::new).toList();
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
        content.addChild(new SpacerElement(0, 4));

        // The effect switches now live in the Effects modal (opened from the header button). Their working state drives
        // which config controls below are greyed: a control is disabled whenever the effect it feeds isn't active. The
        // colours + preset feed several effects at once, so they only grey when the whole mod is off.
        boolean modOn = this.workingModEnabled;
        boolean glyphOn = this.workingModEnabled && this.workingHudEnabled;
        boolean itemFlashOn = this.workingModEnabled && this.workingHudEnabled && this.workingItemFlashEnabled;
        boolean durationOn = this.workingModEnabled && this.workingHudEnabled;
        boolean particlesOn = this.workingModEnabled && this.workingParticlesEnabled;

        // top controls, a 2x3 grid:  preset | From colour | swap preview
        //                            slider | To colour   | reset-colours button
        GridLayout top = new GridLayout().columnSpacing(COL_GAP).rowSpacing(COLOR_ROW_GAP);
        top.defaultCellSetting().alignVerticallyMiddle();

        CycleButton<PresetType> presetButton = CycleButton.<PresetType>builder(
                PresetType::getNameComponent,
                this.workingType
        ).withValues(PresetType.values()).create(
                0, 0, colW, CHIP_H, //
                Component.translatable("gui.visual-swap.preset.label"), (button, value) -> setPreset(value)
        );
        presetButton.active = modOn;
        if (!modOn) presetButton.setTooltip(offTooltip(modSwitch()));
        top.addChild(presetButton, 0, 0);

        this.slider = new SizeSlider(
                0, 0, colW, CHIP_H, //
                this.workingType.getNameComponent(), effectiveSize(), size -> {
            this.workingCustom.setSizeMultiplier(size);
            refreshDirtyState();
        }
        );
        this.slider.active = particlesOn && this.workingType.isColorEditable();
        this.slider.setTooltip(particlesOn
                               ? Tooltip.create(Component.translatable("gui.visual-swap.tooltip.size_slider"))
                               : offTooltip(particlesOffSwitch()));
        top.addChild(this.slider, 1, 0);

        // From/To highlight colours — a caption + a swatch that opens the colour picker (Custom only)
        top.addChild(
                colorRow(
                        Component.translatable("gui.visual-swap.color.from"), this::effectiveFrom,
                        this.workingCustom::setFromColor
                ), 0, 1
        );
        top.addChild(
                colorRow(
                        Component.translatable("gui.visual-swap.color.to"), this::effectiveTo,
                        this.workingCustom::setToColor
                ), 1, 1
        );

        top.addChild(
                new HotbarSwapPreview(
                        this::effectiveFrom,
                        this::effectiveTo,
                        () -> this.workingType,
                        () -> this.previewRule,
                        this.overlays
                ), 0, 2
        );

        this.resetColorsButton = new IconButton(
                CHIP_H, Icons.RESET, //
                Component.translatable("gui.visual-swap.tooltip.reset_colors"), this::confirmResetColors
        );
        this.resetColorsButton.active = modOn && resetColorsEnabled;
        if (!modOn) this.resetColorsButton.setTooltip(offTooltip(modSwitch()));
        top.addChild(this.resetColorsButton, 1, 2, LayoutSettings::alignHorizontallyLeft);

        content.addChild(top, LayoutSettings::alignHorizontallyCenter);

        content.addChild(new SpacerElement(0, 8));

        LinearLayout glyphRow = LinearLayout.horizontal().spacing(COLOR_GAP);
        glyphRow.addChild(
                new StringWidget(COLOR_LABEL_W, CHIP_H,
                        Component.translatable("gui.visual-swap.color.glyph").withColor(LABEL_RGB), this.font),
                LayoutSettings::alignVerticallyMiddle
        );
        ColorSwatch glyphSwatch = new ColorSwatch(COLOR_SWATCH_SIZE, this::effectiveGlyph);
        glyphSwatch.setOnPress(() -> openPicker(
                glyphSwatch, effectiveGlyph(), this::setSelectedGlyphColors));
        glyphSwatch.setClickable(glyphOn && this.workingType.isColorEditable());
        if (!glyphOn) glyphSwatch.setTooltip(offTooltip(glyphOffSwitch()));
        else if (this.workingType.isCustom()) glyphSwatch.setTooltip(Tooltip.create(
                Component.translatable("gui.visual-swap.glyph.selection_help")));
        glyphRow.addChild(glyphSwatch, LayoutSettings::alignVerticallyMiddle);
        glyphRow.addChild(
                new GlyphPreview(
                        () -> this.workingType,
                        glyph -> effectiveGlyph(glyph),
                        this.selectedGlyphs::contains,
                        this::selectGlyph,
                        () -> this.glyphPreviewSky,
                        this.overlays
                ),
                LayoutSettings::alignVerticallyMiddle
        );
        glyphRow.addChild(
                new IconButton(
                        CHIP_H,
                        Icons.BACKGROUND,
                        Component.translatable("gui.visual-swap.tooltip.toggle_glyph_background"),
                        () -> this.glyphPreviewSky = !this.glyphPreviewSky
                ),
                LayoutSettings::alignVerticallyMiddle
        );
        content.addChild(glyphRow, LayoutSettings::alignHorizontallyCenter);

        content.addChild(new SpacerElement(0, 8));

        int topWidth = colW + COL_GAP + (COLOR_LABEL_W + COLOR_ROW_GAP + COLOR_SWATCH_SIZE) + COL_GAP +
                HotbarSwapPreview.WIDTH;
        TicksSlider ticksSlider = new TicksSlider(
                0, 0, topWidth, CHIP_H, //
                this.workingVisibleTicks, ticks -> {
            this.workingVisibleTicks = ticks;
            refreshDirtyState();
        }
        );
        ticksSlider.active = durationOn;
        ticksSlider.setTooltip(durationOn
                               ? Tooltip.create(Component.translatable("gui.visual-swap.tooltip.ticks_slider"))
                               : offTooltip(hudSwitch()));
        content.addChild(ticksSlider, LayoutSettings::alignHorizontallyCenter);

        CycleButton<Boolean> clearPreviousButton = CycleButton.onOffBuilder(this.workingClearPreviousFlashOnSwap)
                                                                 .create(
                                                                         0, 0, topWidth, CHIP_H,
                                                                         Component.translatable(
                                                                                 "gui.visual-swap.clear_previous_flash.label"),
                                                                         (button, value) -> {
                                                                             this.workingClearPreviousFlashOnSwap = value;
                                                                             refreshDirtyState();
                                                                         }
                                                                 );
        clearPreviousButton.active = itemFlashOn;
        clearPreviousButton.setTooltip(itemFlashOn
                                       ? Tooltip.create(Component.translatable(
                                               "gui.visual-swap.clear_previous_flash.help"))
                                       : offTooltip(itemFlashOffSwitch()));
        content.addChild(clearPreviousButton, LayoutSettings::alignHorizontallyCenter);

        content.addChild(new SpacerElement(0, 8));

        // rules table — build first so the count + empty-state can read its filtered size
        this.list = new FlashRulesList(
                rowWidth,
                this.workingType,
                rules,
                this::rebuildWidgets,
                rule -> this.previewRule = rule,
                this::onRuleValueEdited,
                this.overlays
        );
        this.list.setFilter(this.filterText);
        this.previewRule = this.list.ruleAt(previewIdx, "minecraft:mace");
        // --- fixed rules header (search + column captions + Add/Clear/Reset); positioned in arrangeContents ---
        this.tableHeader = new RuleColumnsHeader(
                rowWidth,
                this.filterText,
                this::onSearchEdited,
                this::addRuleClearingFilter,
                this::confirmClear,
                this::confirmResetRules,
                this.list.totalCount() > 0,
                resetRulesEnabled,
                this.list::commonColor,
                this.workingType.isColorEditable(),
                this::confirmSetAllColors,
                this.list::setColorForAll,
                this.overlays
        );
        content.addChild(this.tableHeader, LayoutSettings::alignVerticallyMiddle);

        // Item Flash off → the rules are inert: grey the toolbar and every row so they can't be edited.
        if (!itemFlashOn)
        {
            Tooltip off = offTooltip(itemFlashOffSwitch());
            this.tableHeader.disableWith(off);
            this.list.setEnabled(false, off);
        }

        if (!this.filterText.isEmpty())
        {
            LinearLayout row = LinearLayout.horizontal();
            row.addChild(SpacerElement.width(rowWidth - this.font.width(countText())));
            row.addChild(new StringWidget(countText().withColor(MUTED_RGB), this.font));
            content.addChild(row);
        }

        content.addChild(this.list, LayoutSettings::alignHorizontallyCenter);

        if (this.list.visibleCount() == 0)
        {
            MutableComponent msg = this.list.totalCount() == 0
                                   ? Component.translatable("gui.visual-swap.rules.empty")
                                   : Component.translatable("gui.visual-swap.rules.no_match", this.filterText);
            content.addChild(
                    new StringWidget(msg.withColor(MUTED_RGB), this.font),
                    s -> s.alignHorizontallyCenter().paddingVertical(SECTION_GAP)
            );
        }

        content.addChild(new SpacerElement(0, 4));

        // --- wrap the content in a full-width scroll viewport so the panel + scrollbar reach the screen edges ---
        FrameLayout viewport = new FrameLayout();
        viewport.setMinWidth(this.width - 2 * SCROLLBAR_RESERVE);
        viewport.addChild(content);
        this.scrollArea = new ScrollableLayout(this.minecraft, viewport, this.layout.getContentHeight());
        this.layout.addToContents(this.scrollArea);

        // --- footer: revert / leave / save ---
        LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        this.discardButton = Button.builder(
                Component.translatable("gui.visual-swap.button.discard"),
                b -> confirmDiscard()
        ).width(80).build();
        this.discardButton.active = this.isModified;
        this.discardButton.setTooltip(Tooltip.create(Component.translatable("gui.visual-swap.tooltip.discard")));
        footer.addChild(this.discardButton);
        footer.addChild(Button.builder(
                Component.translatable("gui.visual-swap.button.cancel"),
                b -> onClose()
        ).width(80).build());
        this.doneButton = Button.builder(
                Component.translatable("gui.visual-swap.button.done"),
                b -> onDone()
        ).width(80).build();
        applyDoneState(this.list.invalidCount() == 0 ? DoneButtonState.ENABLED : DoneButtonState.INVALID_RULE);
        footer.addChild(this.doneButton);

        this.layout.visitWidgets(this::addRenderableWidget);
        this.scrollContainer = null;
        this.scrollArea.visitWidgets(w -> this.scrollContainer = w);   // capture the inner scroll widget for row focus

        // header button that opens the effect switches — always enabled, so a disabled config can always be re-enabled
        this.effectsButton = Button
                .builder(
                        Component.translatable("gui.visual-swap.effects.title"),
                        b -> EffectsModal.open(effectToggles())
                )
                .width(EFFECTS_BTN_W)
                .build();
        this.effectsButton.setTooltip(Tooltip.create(Component.translatable("gui.visual-swap.tooltip.effects_button")));
        addRenderableWidget(this.effectsButton);

        arrangeContents();
        restoreScroll(previousScroll);
        applyPendingFocus();
        applyPendingColorPicker();
    }

    /// The fixed header, then the scroll viewport below it — dropping HeaderAndFooterLayout's ~30px content margin.
    private void arrangeContents()
    {
        this.layout.arrangeElements();
        int available = this.height - this.layout.getFooterHeight() - this.layout.getHeaderHeight();

        // pin the Effects button to the header's top-right corner (it sits outside the header/footer layout)
        if (this.effectsButton != null)
        {
            int by = Math.max(2, (this.layout.getHeaderHeight() - CHIP_H) / 2);
            this.effectsButton.setPosition(this.width - EFFECTS_BTN_MARGIN - EFFECTS_BTN_W, by);
        }

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

    /// Open the bulk-tint picker if the last "set all colours" confirm asked for it — deferred to here (init's tail, so
    /// the header is laid out and {@link #init}'s {@code overlays.close()} has already run) because the confirm modal's
    /// return re-runs init and would otherwise close a picker opened mid-confirm. One-shot.
    private void applyPendingColorPicker()
    {
        if (!this.openBulkColorPicker) return;
        this.openBulkColorPicker = false;
        this.tableHeader.openColorPicker();
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

    /// The effect switches shown in the {@link EffectsModal}, each bound live to a working field. Sub-switches carry an
    /// {@code enabled} predicate so they grey out under a parent that is off; the modal re-reads these on every
    /// rebuild, and its changes flow straight back into this screen's working state (refreshed on the modal's close →
    /// re-init).
    private List<EffectsModal.Toggle> effectToggles()
    {
        return List.of(
                new EffectsModal.Toggle(
                        modSwitch(),
                        Component.translatable("gui.visual-swap.effects.mod.help"),
                        () -> this.workingModEnabled,
                        v -> this.workingModEnabled = v,
                        () -> true,
                        0
                ), new EffectsModal.Toggle(
                        hudSwitch(),
                        Component.translatable("gui.visual-swap.effects.hud.help"),
                        () -> this.workingHudEnabled,
                        v -> this.workingHudEnabled = v,
                        () -> this.workingModEnabled,
                        0
                ), new EffectsModal.Toggle(
                        hotbarHighlightSwitch(),
                        Component.translatable("gui.visual-swap.effects.hotbar_highlight.help"),
                        () -> this.workingHotbarHighlightEnabled,
                        v -> this.workingHotbarHighlightEnabled = v,
                        () -> this.workingModEnabled && this.workingHudEnabled,
                        1
                ), new EffectsModal.Toggle(
                        itemFlashSwitch(),
                        Component.translatable("gui.visual-swap.effects.item_flash.help"),
                        () -> this.workingItemFlashEnabled,
                        v -> this.workingItemFlashEnabled = v,
                        () -> this.workingModEnabled && this.workingHudEnabled,
                        1
                ), new EffectsModal.Toggle(
                        Component.translatable("gui.visual-swap.effects.only_on_swap.label"),
                        Component.translatable("gui.visual-swap.effects.only_on_swap.help"),
                        () -> this.workingFlashOnlyOnSwap,
                        v -> this.workingFlashOnlyOnSwap = v,
                        () -> this.workingModEnabled && this.workingHudEnabled && this.workingItemFlashEnabled,
                        2
                ), new EffectsModal.Toggle(
                        particlesSwitch(),
                        Component.translatable("gui.visual-swap.effects.particles.help"),
                        () -> this.workingParticlesEnabled,
                        v -> this.workingParticlesEnabled = v,
                        () -> this.workingModEnabled,
                        0
                )
        );
    }

    /// The effect-switch display names — shared by the {@link EffectsModal} toggles and the "…is turned off" tooltips
    /// so both read from one key each and stay in step.
    private static Component modSwitch() { return Component.translatable("gui.visual-swap.effects.mod.label"); }

    private static Component hudSwitch() { return Component.translatable("gui.visual-swap.effects.hud.label"); }

    private static Component hotbarHighlightSwitch()
    {
        return Component.translatable("gui.visual-swap.effects.hotbar_highlight.label");
    }

    private static Component itemFlashSwitch()
    {
        return Component.translatable("gui.visual-swap.effects.item_flash.label");
    }

    private static Component particlesSwitch() { return Component.translatable("gui.visual-swap.effects.particles.label"); }

    /// Tooltip for a control greyed because its effect is off — names the switch to flip and points at the header.
    private Tooltip offTooltip(Component switchName)
    {
        return Tooltip.create(Component.translatable("gui.visual-swap.tooltip.effect_off", switchName));
    }

    /// The first switch that is off along the Item Flash gate chain (mod → HUD → item flash) — the one to re-enable.
    private Component itemFlashOffSwitch()
    {
        if (!this.workingModEnabled) return modSwitch();
        if (!this.workingHudEnabled) return hudSwitch();
        return itemFlashSwitch();
    }

    /// The first switch that is off along the hotbar-highlight gate chain (mod → HUD → hotbar highlight).
    private Component hotbarHighlightOffSwitch()
    {
        if (!this.workingModEnabled) return modSwitch();
        if (!this.workingHudEnabled) return hudSwitch();
        return hotbarHighlightSwitch();
    }

    /// The first switch that is off along the particle gate chain (mod → particles).
    private Component glyphOffSwitch() { return this.workingModEnabled ? hudSwitch() : modSwitch(); }

    private Component particlesOffSwitch() { return this.workingModEnabled ? particlesSwitch() : modSwitch(); }

    /// One From/To colour row: a caption and a swatch that opens the picker (Custom preset only). The picker writes the
    /// picked ARGB straight to {@code onEdit}, and the swatch reads {@code color} live, so no rebuild is needed.
    private LinearLayout colorRow(MutableComponent label, IntSupplier color, IntConsumer onEdit)
    {
        LinearLayout row = LinearLayout.horizontal().spacing(COLOR_GAP);
        row.addChild(
                new StringWidget(COLOR_LABEL_W, CHIP_H, label.withColor(LABEL_RGB), this.font),
                LayoutSettings::alignVerticallyMiddle
        );

        ColorSwatch swatch = new ColorSwatch(COLOR_SWATCH_SIZE, color);
        swatch.setOnPress(() -> openPicker(swatch, color.getAsInt(), onEdit));
        // From/To are the swap-highlight colours, so they follow the Hotbar Highlight effect: greyed while it (or a
        // switch above it) is off, else editable under an editable (Custom) preset.
        boolean highlightOn = this.workingModEnabled && this.workingHudEnabled && this.workingHotbarHighlightEnabled;
        swatch.setClickable(highlightOn && this.workingType.isColorEditable());
        if (!highlightOn) swatch.setTooltip(offTooltip(hotbarHighlightOffSwitch()));
        row.addChild(swatch, LayoutSettings::alignVerticallyMiddle);
        return row;
    }

    /// Open the colour picker (with alpha — From/To colours are AARRGGBB) anchored under {@code anchor}.
    private void openPicker(ColorSwatch anchor, int current, IntConsumer apply)
    {
        ColorPickerOverlay picker = new ColorPickerOverlay(current, true, color -> {
            apply.accept(color);
            refreshDirtyState();
        });
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

    private int effectiveGlyph()
    {
        return effectiveGlyph(this.selectedGlyphs.iterator().next());
    }

    private int effectiveGlyph(String glyph)
    {
        if (this.workingType.isCustom()) return this.workingCustom.getGlyphColor(glyph);
        return this.workingType.getGlyphColor(glyph);
    }

    private void setSelectedGlyphColors(int color)
    {
        for (String glyph : this.selectedGlyphs) this.workingCustom.setGlyphColor(glyph, color);
    }

    private void selectGlyph(String glyph, boolean addToSelection)
    {
        if (!addToSelection)
        {
            this.selectedGlyphs.clear();
            this.selectedGlyphs.add(glyph);
            return;
        }

        if (this.selectedGlyphs.contains(glyph))
        {
            if (this.selectedGlyphs.size() > 1) this.selectedGlyphs.remove(glyph);
        }
        else this.selectedGlyphs.add(glyph);
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
                Component.translatable("gui.visual-swap.confirm.reset_rules.title"),
                List.of(Component.translatable("gui.visual-swap.confirm.reset_rules.body")),
                Component.translatable("gui.visual-swap.button.reset"),
                this::doResetRules
        );
    }

    private void confirmResetColors()
    {
        openConfirm(
                Component.translatable("gui.visual-swap.confirm.reset_colors.title"),
                List.of(Component.translatable("gui.visual-swap.confirm.reset_colors.body")),
                Component.translatable("gui.visual-swap.button.reset"),
                this::doResetColors
        );
    }

    private void confirmClear()
    {
        openConfirm(
                Component.translatable("gui.visual-swap.confirm.clear.title"),
                List.of(Component.translatable("gui.visual-swap.confirm.clear.body")),
                Component.translatable("gui.visual-swap.button.clear_all"),
                () -> this.list.clear()
        );
    }

    /// Gate the header's bulk tint action behind a confirm (it overwrites every rule's colour). On confirm, just flag
    /// the picker to open on the next init — the modal's return re-runs init, which is where the picker is opened so it
    /// isn't torn down by that rebuild. No rebuild is triggered here, so init runs exactly once (from the modal
    /// close).
    private void confirmSetAllColors()
    {
        openConfirm(
                Component.translatable("gui.visual-swap.confirm.set_all_colors.title"),
                List.of(Component.translatable("gui.visual-swap.confirm.set_all_colors.body")),
                Component.translatable("gui.visual-swap.button.set_all"),
                () -> this.openBulkColorPicker = true
        );
    }

    private void confirmDiscard()
    {
        openConfirm(
                Component.translatable("gui.visual-swap.confirm.discard.title"),
                List.of(Component.translatable("gui.visual-swap.confirm.discard.body")),
                Component.translatable("gui.visual-swap.button.discard"),
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
        this.workingModEnabled = this.savedModEnabled;
        this.workingHudEnabled = this.savedHudEnabled;
        this.workingHotbarHighlightEnabled = this.savedHotbarHighlightEnabled;
        this.workingItemFlashEnabled = this.savedItemFlashEnabled;
        this.workingFlashOnlyOnSwap = this.savedFlashOnlyOnSwap;
        this.workingClearPreviousFlashOnSwap = this.savedClearPreviousFlashOnSwap;
        this.workingParticlesEnabled = this.savedParticlesEnabled;
        this.filterText = "";
        this.pendingRules = this.savedRules;
        this.list = null;
        this.resetScroll = true;
        rebuildWidgets();
    }

    /// Open a modal confirmation (its own screen) for a destructive action; only Confirm runs {@code action}.
    private void openConfirm(Component title, List<Component> lines, Component confirmLabel, Runnable action)
    {
        ConfirmModal.open(title, lines, confirmLabel, action);
    }

    /// The rule-count line above the table: "N of M shown" while a filter is active.
    private MutableComponent countText()
    {
        return Component.translatable("gui.visual-swap.rules.count", this.list.visibleCount(), this.list.totalCount());
    }

    /* DIRTY STATE / SAVE / CLOSE */

    private boolean isModified(List<FlashRule> currentRules)
    {
        return this.workingType != this.savedType || this.workingVisibleTicks != this.savedVisibleTicks ||
                this.workingModEnabled != this.savedModEnabled || this.workingHudEnabled != this.savedHudEnabled ||
                this.workingHotbarHighlightEnabled != this.savedHotbarHighlightEnabled ||
                this.workingItemFlashEnabled != this.savedItemFlashEnabled ||
                this.workingFlashOnlyOnSwap != this.savedFlashOnlyOnSwap ||
                this.workingClearPreviousFlashOnSwap != this.savedClearPreviousFlashOnSwap ||
                this.workingParticlesEnabled != this.savedParticlesEnabled ||
                !this.workingCustom.sameValuesAs(this.savedCustom) || !FlashRule.listsSameValues(
                currentRules,
                this.savedRules
        );
    }

    private void onRuleValueEdited()
    {
        applyDoneState(this.list.invalidCount() == 0 ? DoneButtonState.ENABLED : DoneButtonState.INVALID_RULE);
        refreshDirtyState();
    }

    /// Refresh the cached state used while widgets edit their backing values without rebuilding the screen.
    private void refreshDirtyState()
    {
        if (this.list == null) return;
        this.isModified = isModified(this.list.toRules());
        if (this.discardButton != null) this.discardButton.active = this.isModified;
        if (this.resetColorsButton != null)
        {
            this.resetColorsButton.active = this.workingModEnabled && this.workingType.isCustom() &&
                    !this.workingCustom.sameValuesAs(PresetType.CUSTOM.createDefault());
        }
    }

    /// Enable/disable the Done button and set the matching tooltip — an invalid config can never be saved.
    private void applyDoneState(DoneButtonState newState)
    {
        if (this.doneButton == null) return;
        this.doneButton.active = newState.allowsSaving();
        this.doneButton.setTooltip(Tooltip.create(Component.translatable(newState.getMessageKey())));
    }

    private void onDone()
    {
        if (this.list.invalidCount() > 0) return;
        saveConfig();
    }

    private void saveConfig()
    {
        ModConfig cfg = ModConfig.get();
        cfg.preset = this.workingType;
        cfg.customPresetData = new Preset(this.workingCustom);
        cfg.flashVisibleTicks = this.workingVisibleTicks;
        cfg.modEnabled = this.workingModEnabled;
        cfg.hudEnabled = this.workingHudEnabled;
        cfg.hotbarHighlightEnabled = this.workingHotbarHighlightEnabled;
        cfg.itemFlashEnabled = this.workingItemFlashEnabled;
        cfg.flashOnlyOnSwap = this.workingFlashOnlyOnSwap;
        cfg.clearPreviousFlashOnSwap = this.workingClearPreviousFlashOnSwap;
        cfg.particlesEnabled = this.workingParticlesEnabled;
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
                    Component.translatable("gui.visual-swap.confirm.leave.title"),
                    List.of(Component.translatable("gui.visual-swap.confirm.leave.body")),
                    Component.translatable("gui.visual-swap.button.discard"),
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
        if (this.isModified)
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
        ENABLED("gui.visual-swap.tooltip.done_enabled"),
        INVALID_RULE("gui.visual-swap.tooltip.done_invalid");

        private final String messageKey;

        DoneButtonState(String messageKey)
        {
            this.messageKey = messageKey;
        }

        public String getMessageKey() { return messageKey; }

        public boolean allowsSaving() { return this.equals(ENABLED); }
    }
}

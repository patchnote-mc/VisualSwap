package com.patchnote.visualswap.client.config.screen;

import com.patchnote.visualswap.client.config.ModConfig;
import com.patchnote.visualswap.client.config.models.FlashRule;
import com.patchnote.visualswap.client.config.models.Preset;
import com.patchnote.visualswap.client.config.models.PresetType;
import com.patchnote.visualswap.client.config.screen.widget.*;
import com.patchnote.visualswap.client.hud.click.ItemFlashPreview;
import com.patchnote.visualswap.client.utils.ColorHelpers;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.layouts.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/// The mod's config screen: a fixed title header, a fixed Add/Cancel/Done footer, and a scrollable middle (preset
/// selector, size slider, From/To highlight colours, and the rules table) laid out with the vanilla `layouts` package
/// and wrapped in a {@link ScrollableLayout}. The scroll viewport gets the vanilla list look — a tiled dark panel with
/// top/bottom separators (drawn in {@link #extractScrollPanel}) plus the scrollbar the ScrollableLayout draws itself.
public final class VisualSwapConfigScreen extends Screen
{
    private static final int CHIP_H = 20;
    private static final int COL_GAP = 8;       // between the top grid's columns

    // Custom-colour row: a "From"/"To" caption, then a colour swatch, then the hex edit box.
    private static final int COLOR_LABEL_W = 34;
    private static final int COLOR_SWATCH = 16;
    private static final int COLOR_GAP = 6;     // swatch → hex box

    // Vertical rhythm of the scrollable content column.
    private static final int CONTENT_SPACING = 6;
    private static final int COLOR_ROW_GAP = 4; // From ↔ To sit tighter as a pair
    private static final int SECTION_GAP = 8;   // extra breathing room above the rules table
    private static final int SCROLL_MIN_HEIGHT = 40;
    private static final int CONTENT_TOP_GAP = 4;    // gap below the fixed header before the scroll panel
    private static final int SCROLLBAR_RESERVE = 10; // ScrollableLayout's per-side reserve (spacing 4 + scrollbar 6)

    // StringWidget colours are RGB (styled via Component#withColor); EditBox/swatch colours are ARGB.
    private static final int LABEL_RGB = 0xB9B9C0;
    private static final int SWATCH_BORDER = 0xFF4A4A52;
    private static final int TEXT_VALID = 0xFFE0E0E0;
    private static final int TEXT_INVALID = 0xFFFF5555;
    private static final int TEXT_MUTED = 0xFF97979E;

    // Vanilla list-panel textures (menu_list_background is private in AbstractSelectionList, so re-declared here).
    private static final Identifier MENU_LIST_BACKGROUND = Identifier.withDefaultNamespace(
            "textures/gui/menu_list_background.png");
    private static final Identifier INWORLD_MENU_LIST_BACKGROUND = Identifier.withDefaultNamespace(
            "textures/gui/inworld_menu_list_background.png");

    private final Screen parent;

    /// Which preset is selected, and a working copy of the Custom preset's settings — preserved across preset toggles
    /// so switching away and back doesn't lose in-progress Custom edits. Only committed to ModConfig on "Done".
    private PresetType workingType;
    private final Preset workingCustom;
    private final List<FlashRule> ruleSeed;

    private HeaderAndFooterLayout layout;
    private ScrollableLayout scrollArea;
    private SizeSlider slider;
    private EditBox fromColorBox;
    private EditBox toColorBox;
    private FlashRulesList list;

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
        this.ruleSeed = cfg.clickFlashRules;
    }

    @Override
    protected void init()
    {
        // Preserve in-progress rule edits across a rebuild (window resize, or an add/remove that re-inits). The
        // followed preview rule is carried over as its seed position — the new rows copy the seed in order.
        List<FlashRule> seed = (this.list != null) ? this.list.toRules() : this.ruleSeed;
        int previewIdx = (this.previewRule != null) ? seed.indexOf(this.previewRule) : -1;

        this.layout = new HeaderAndFooterLayout(this);
        this.layout.addTitleHeader(getTitle(), this.font);

        int rowWidth = Math.min(360, this.width - 60);
        int colW = (rowWidth - HotbarSwapPreview.WIDTH - 2 * COL_GAP) / 2;  // the preset/slider and From/To columns

        // --- scrollable content column ---
        LinearLayout content = LinearLayout.vertical().spacing(CONTENT_SPACING);

        // top controls, a 2x3 grid:  preset | From colour | swap preview
        //                            slider | To colour   | (empty)
        GridLayout top = new GridLayout().columnSpacing(COL_GAP).rowSpacing(COLOR_ROW_GAP);
        top.defaultCellSetting().alignVerticallyMiddle();  // the preview cell is taller than the 20px controls

        top.addChild(
                CycleButton.<PresetType>builder(PresetType::getNameComponent, this.workingType)
                        .withValues(PresetType.VANILLA, PresetType.PRACTICE, PresetType.CUSTOM)
                        .create(0, 0, colW, CHIP_H, Component.literal("Preset"), (button, value) -> setPreset(value)),
                0,
                0
        );

        // size slider (edits the Custom preset; disabled/greyed for the fixed presets)
        this.slider = new SizeSlider(
                0,
                0,
                colW,
                CHIP_H,
                this.workingType.getDisplayName(),
                effectiveSize(),
                this.workingCustom::setSizeMultiplier
        );
        this.slider.active = this.workingType.isColorEditable();
        top.addChild(this.slider, 1, 0);

        // From/To highlight colours (shown for every preset; the hex box is editable only under Custom)
        top.addChild(
                colorRow(
                        "From",
                        this::effectiveFrom,
                        b -> this.fromColorBox = b,
                        this.workingCustom::setFromColor,
                        effectiveFrom(),
                        colW
                ), 0, 1
        );
        top.addChild(
                colorRow(
                        "To",
                        this::effectiveTo,
                        b -> this.toColorBox = b,
                        this.workingCustom::setToColor,
                        effectiveTo(),
                        colW
                ), 1, 1
        );

        // right column: the swap preview in the top cell; the bottom cell is intentionally left empty
        top.addChild(
                new HotbarSwapPreview(
                        this::effectiveFrom,
                        this::effectiveTo,
                        () -> this.workingType,
                        () -> this.previewRule
                ), 0, 2
        );

        content.addChild(top, LayoutSettings::alignHorizontallyCenter);

        // rules table: column captions + stacked rows
        content.addChild(new RuleColumnsHeader(rowWidth), LayoutSettings::alignHorizontallyCenter);
        this.list = new FlashRulesList(
                rowWidth,
                this.workingType,
                seed,
                this::rebuildWidgets,
                rule -> this.previewRule = rule
        );
        this.previewRule = this.list.ruleAt(previewIdx, "minecraft:mace");
        content.addChild(this.list, LayoutSettings::alignHorizontallyCenter);

        // --- wrap the content in a full-width scroll viewport so the panel + scrollbar reach the screen edges ---
        // A full-width frame centres the (narrower) content column; ScrollableLayout adds its scrollbar reserve on
        // both sides, so the container ends up exactly the screen width.
        FrameLayout viewport = new FrameLayout();
        viewport.setMinWidth(this.width - 2 * SCROLLBAR_RESERVE);
        viewport.addChild(content);
        this.scrollArea = new ScrollableLayout(this.minecraft, viewport, this.layout.getContentHeight());
        this.layout.addToContents(this.scrollArea);

        // --- footer ---
        LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(Component.literal("+ Add rule"), b -> this.list.addRule()).width(96).build());
        footer.addChild(Button.builder(Component.literal("Cancel"), b -> onClose()).width(90).build());
        footer.addChild(Button.builder(Component.literal("Done"), b -> commitAndClose()).width(90).build());

        this.layout.visitWidgets(this::addRenderableWidget);
        arrangeContents();
    }

    /// Arrange the header/footer, then pin the scroll viewport just below the header — dropping HeaderAndFooterLayout's
    /// ~30px content margin — and size it to fill down to the footer.
    private void arrangeContents()
    {
        this.scrollArea.setMaxHeight(SCROLL_MIN_HEIGHT);
        this.layout.arrangeElements();

        int top = this.layout.getHeaderHeight() + CONTENT_TOP_GAP;
        int available = this.height - this.layout.getFooterHeight() - top;
        this.scrollArea.setMaxHeight(Math.max(SCROLL_MIN_HEIGHT, available));
        this.scrollArea.setY(top);
    }

    /// One From/To color row (total width {@code rowW}): a fixed-width caption, a live swatch, and the hex box
    /// (assigned back via {@code assign}).
    private LinearLayout colorRow(String label, IntSupplier color, Consumer<EditBox> assign, IntConsumer onEdit,
                                  int initial, int rowW)
    {
        LinearLayout row = LinearLayout.horizontal();
        row.addChild(
                new StringWidget(COLOR_LABEL_W, CHIP_H, Component.literal(label).withColor(LABEL_RGB), this.font),
                LayoutSettings::alignVerticallyMiddle
        );
        row.addChild(swatch(color), LayoutSettings::alignVerticallyMiddle);
        EditBox box = makeColorBox(rowW - COLOR_LABEL_W - COLOR_SWATCH - COLOR_GAP, initial, onEdit);
        assign.accept(box);
        row.addChild(box, s -> s.alignVerticallyMiddle().paddingLeft(COLOR_GAP));
        return row;
    }

    private void setPreset(PresetType type)
    {
        this.workingType = type;
        boolean editable = type.isColorEditable();

        this.slider.active = editable;
        this.slider.update(type.getDisplayName(), effectiveSize());

        this.fromColorBox.setEditable(editable);
        this.toColorBox.setEditable(editable);
        this.fromColorBox.setValue(ColorHelpers.formatArgbHex(effectiveFrom()));
        this.toColorBox.setValue(ColorHelpers.formatArgbHex(effectiveTo()));

        if (this.list != null) this.list.setPreset(type);
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

    /* WIDGET FACTORIES */

    private ColorSwatch swatch(IntSupplier color)
    {
        return new ColorSwatch(COLOR_SWATCH, SWATCH_BORDER, color);
    }

    /// A hex-color edit box (AARRGGBB, "#"/"0x" and 6-digit RRGGBB accepted). Editable only under the Custom preset;
    /// greyed (uneditable) otherwise. Pushes each valid value to {@code onEdit} and reddens its text while
    /// unparseable.
    private EditBox makeColorBox(int width, int initial, IntConsumer onEdit)
    {
        EditBox box = new EditBox(this.font, 0, 0, width, CHIP_H, Component.literal("Color"));
        box.setMaxLength(10);
        box.setHint(Component.literal("AARRGGBB"));
        box.setTextColorUneditable(TEXT_MUTED);
        box.setValue(ColorHelpers.formatArgbHex(initial));
        box.setEditable(this.workingType.isColorEditable());
        box.setResponder(text -> {
            Integer color = ColorHelpers.parseHexColor(text);
            box.setTextColor(color != null ? TEXT_VALID : TEXT_INVALID);
            if (color != null && this.workingType.isColorEditable()) onEdit.accept(color);
        });
        return box;
    }

    private void commitAndClose()
    {
        ModConfig cfg = ModConfig.get();
        cfg.preset = this.workingType;
        cfg.customPresetData = new Preset(this.workingCustom);
        cfg.clickFlashRules = this.list.toRules();
        AutoConfig.getConfigHolder(ModConfig.class).save();
        this.minecraft.setScreenAndShow(this.parent);
    }

    /* OVERRIDES */

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a)
    {
        extractScrollPanel(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, a);
    }

    /// The vanilla scrollable-list look for the content viewport: a tiled dark panel behind it, capped by the
    /// header/footer separator lines. The scrollbar itself is drawn by the ScrollableLayout.
    private void extractScrollPanel(GuiGraphicsExtractor graphics)
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

    @Override
    public void onClose() { this.minecraft.setScreenAndShow(this.parent); }

    /// Drop the preview's flash registrations so a later screen's item at the same coords can't get silhouetted.
    @Override
    public void removed() { ItemFlashPreview.clear(); }
}

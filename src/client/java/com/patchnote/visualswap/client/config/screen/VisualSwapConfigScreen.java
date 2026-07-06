package com.patchnote.visualswap.client.config.screen;

import com.patchnote.visualswap.client.config.ModConfig;
import com.patchnote.visualswap.client.config.models.FlashRule;
import com.patchnote.visualswap.client.config.models.Preset;
import com.patchnote.visualswap.client.config.models.PresetType;
import com.patchnote.visualswap.client.config.screen.widget.ColorSwatch;
import com.patchnote.visualswap.client.config.screen.widget.FlashRulesList;
import com.patchnote.visualswap.client.config.screen.widget.SizeSlider;
import com.patchnote.visualswap.client.utils.ColorHelpers;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public final class VisualSwapConfigScreen extends Screen
{
    private static final int TITLE_Y = 13;
    private static final int CHIP_H = 20;
    private static final int FOOTER_H = 40;

    // Custom-color row: a "From"/"To" caption, then a color swatch, then the hex edit box.
    private static final int COLOR_LABEL_W = 34;
    private static final int COLOR_SWATCH = 16;

    // Column geometry for the rules-table headers — mirrors FlashRuleEntry's right-anchored layout.
    private static final int ICON = 16;
    private static final int GAP = 6;
    private static final int ON_WIDTH = 58;
    private static final int INTENSITY_WIDTH = 46;
    private static final int COLOR_WIDTH = 70;
    private static final int DELETE_WIDTH = 18;

    // StringWidget colours are RGB (styled via Component#withColor); EditBox/swatch colours are ARGB.
    private static final int TITLE_RGB = 0xFFFFFF;
    private static final int LABEL_RGB = 0xB9B9C0;
    private static final int MUTED_RGB = 0x97979E;
    private static final int SWATCH_BORDER = 0xFF4A4A52;
    private static final int TEXT_VALID = 0xFFE0E0E0;
    private static final int TEXT_INVALID = 0xFFFF5555;
    private static final int TEXT_MUTED = 0xFF97979E;

    private final Screen parent;

    /// Which preset is selected, and a working copy of the Custom preset's settings — preserved across preset toggles
    /// so switching away and back doesn't lose in-progress Custom edits. Only committed to ModConfig on "Done".
    private PresetType workingType;
    private final Preset workingCustom;
    private final List<FlashRule> ruleSeed;

    private SizeSlider slider;
    private EditBox fromColorBox;
    private EditBox toColorBox;
    private FlashRulesList list;

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
        // Preserve in-progress rule edits across a window resize (init runs again).
        List<FlashRule> seed = (this.list != null) ? this.list.toRules() : this.ruleSeed;

        int cx = this.width / 2;

        // --- title ---
        addRenderableWidget(centeredLabel(getTitle().getString(), TITLE_RGB, cx, TITLE_Y));

        // --- preset selector ---
        int groupW = 192;
        int gx = cx - groupW / 2;
        int chipsY = TITLE_Y + 16;

        addRenderableWidget(CycleButton.<PresetType>builder(PresetType::getNameComponent, this.workingType)
                                    .withValues(PresetType.VANILLA, PresetType.PRACTICE, PresetType.CUSTOM)
                                    .create(
                                            gx,
                                            chipsY,
                                            groupW,
                                            CHIP_H,
                                            Component.literal("Preset"),
                                            (button, value) -> setPreset(value)
                                    ));

        // --- size slider (edits the Custom preset; disabled/greyed for the fixed presets) ---
        int sliderY = chipsY + CHIP_H + 6;
        this.slider = new SizeSlider(
                gx,
                                     sliderY,
                                     groupW,
                                     CHIP_H,
                                     this.workingType.getDisplayName(),
                                     effectiveSize(),
                                     v -> this.workingCustom.setSizeMultiplier(v)
        );
        this.slider.active = this.workingType.isColorEditable();
        addRenderableWidget(this.slider);

        // --- From/To highlight colours (shown for every preset; the hex box is editable only under Custom) ---
        int labelX = gx;
        int swatchX = gx + COLOR_LABEL_W;
        int boxX = gx + COLOR_LABEL_W + COLOR_SWATCH + 6;
        int boxW = groupW - (COLOR_LABEL_W + COLOR_SWATCH + 6);
        int captionYOffset = (CHIP_H - this.font.lineHeight) / 2;
        int swatchYOffset = (CHIP_H - COLOR_SWATCH) / 2;

        int fromY = sliderY + CHIP_H + 6;
        addRenderableWidget(leftLabel("From", LABEL_RGB, labelX, fromY + captionYOffset));
        addRenderableWidget(swatch(swatchX, fromY + swatchYOffset, this::effectiveFrom));
        this.fromColorBox = makeColorBox(boxX, fromY, boxW, effectiveFrom(), this.workingCustom::setFromColor);
        addRenderableWidget(this.fromColorBox);

        int toY = fromY + CHIP_H + 4;
        addRenderableWidget(leftLabel("To", LABEL_RGB, labelX, toY + captionYOffset));
        addRenderableWidget(swatch(swatchX, toY + swatchYOffset, this::effectiveTo));
        this.toColorBox = makeColorBox(boxX, toY, boxW, effectiveTo(), this.workingCustom::setToColor);
        addRenderableWidget(this.toColorBox);

        // --- rules table ---
        int rowWidth = Math.min(360, this.width - 60);
        int rowLeft = cx - rowWidth / 2;
        int listTop = toY + CHIP_H + 24;
        int headerY = listTop - 11;
        int listBottom = this.height - FOOTER_H;

        addColumnHeaders(rowLeft, rowWidth, headerY);

        this.list = new FlashRulesList(
                this.minecraft,
                this.width,
                listBottom - listTop,
                listTop,
                rowWidth,
                this.workingType,
                seed
        );
        addRenderableWidget(this.list);

        // --- footer ---
        int footerY = this.height - 28;
        int addW = 96;
        int btnW = 90;
        int btnGap = 8;

        addRenderableWidget(Button.builder(Component.literal("+ Add rule"), b -> this.list.addRule())
                                    .bounds(rowLeft, footerY, addW, CHIP_H)
                                    .build());

        int rightEdge = rowLeft + rowWidth;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> commitAndClose())
                                    .bounds(rightEdge - btnW, footerY, btnW, CHIP_H)
                                    .build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                                    .bounds(rightEdge - btnW * 2 - btnGap, footerY, btnW, CHIP_H)
                                    .build());
    }

    /// Column captions over the rules table, aligned to FlashRuleEntry's right-anchored columns.
    private void addColumnHeaders(int rowLeft, int rowWidth, int headerY)
    {
        int contentX = rowLeft + 2;
        int contentRight = rowLeft + rowWidth - 2;
        int deleteX = contentRight - DELETE_WIDTH;
        int colorX = deleteX - GAP - COLOR_WIDTH;
        int intensityX = colorX - GAP - INTENSITY_WIDTH;
        int onX = intensityX - GAP - ON_WIDTH;
        int itemX = contentX + ICON + GAP;

        addRenderableWidget(leftLabel("Item", MUTED_RGB, itemX, headerY));
        addRenderableWidget(centeredLabel("Flash Type", MUTED_RGB, onX + ON_WIDTH / 2, headerY));
        addRenderableWidget(centeredLabel("Intensity", MUTED_RGB, intensityX + INTENSITY_WIDTH / 2, headerY));
        addRenderableWidget(centeredLabel("Color", MUTED_RGB, colorX + COLOR_WIDTH / 2, headerY));
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

    private double effectiveSize() { return this.workingType.isCustom() ? this.workingCustom.getSizeMultiplier() : this.workingType.getSize(); }

    private int effectiveFrom() { return this.workingType.isCustom() ? this.workingCustom.getFromColor() : this.workingType.getFromColor(); }

    private int effectiveTo() { return this.workingType.isCustom() ? this.workingCustom.getToColor() : this.workingType.getToColor(); }

    /* WIDGET FACTORIES */

    private StringWidget leftLabel(String text, int rgb, int x, int y)
    {
        StringWidget w = new StringWidget(Component.literal(text).withColor(rgb), this.font);
        w.setPosition(x, y);
        return w;
    }

    private StringWidget centeredLabel(String text, int rgb, int centerX, int y)
    {
        StringWidget w = new StringWidget(Component.literal(text).withColor(rgb), this.font);
        w.setPosition(centerX - w.getWidth() / 2, y);
        return w;
    }

    private ColorSwatch swatch(int x, int y, IntSupplier color)
    {
        ColorSwatch w = new ColorSwatch(COLOR_SWATCH, SWATCH_BORDER, color);
        w.setPosition(x, y);
        return w;
    }

    /// A hex-color edit box (AARRGGBB, "#"/"0x" and 6-digit RRGGBB accepted). Editable only under the Custom preset;
    /// greyed (uneditable) otherwise. Pushes each valid value to {@code onEdit} and reddens its text while unparseable.
    private EditBox makeColorBox(int x, int y, int width, int initial, IntConsumer onEdit)
    {
        EditBox box = new EditBox(this.font, x, y, width, CHIP_H, Component.literal("Color"));
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
    public void onClose() { this.minecraft.setScreenAndShow(this.parent); }
}

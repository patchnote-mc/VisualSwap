package com.patchnote.visualswap.client.config.screen;

import com.patchnote.visualswap.client.config.ModConfig;
import com.patchnote.visualswap.client.config.models.FlashRule;
import com.patchnote.visualswap.client.config.models.Preset;
import com.patchnote.visualswap.client.config.screen.widget.FlashRulesList;
import com.patchnote.visualswap.client.config.screen.widget.SizeSlider;
import com.patchnote.visualswap.client.utils.ColorHelpers;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.function.IntConsumer;

public final class VisualSwapConfigScreen extends Screen
{
    private static final int TITLE_Y = 13;
    private static final int CHIP_H = 20;
    private static final int FOOTER_H = 40;

    // Custom-color row: a "From"/"To" caption, then a color swatch, then the hex edit box.
    private static final int COLOR_LABEL_W = 34;
    private static final int COLOR_SWATCH = 16;

    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int LABEL_COLOR = 0xFFB9B9C0;
    private static final int MUTED_COLOR = 0xFF97979E;
    private static final int SWATCH_BORDER = 0xFF4A4A52;
    private static final int TEXT_VALID = 0xFFE0E0E0;
    private static final int TEXT_INVALID = 0xFFFF5555;

    private final Screen parent;

    private Preset workingPreset;
    private double workingSize;
    private int workingFrom;
    private int workingTo;
    private final List<FlashRule> ruleSeed;

    private SizeSlider slider;
    private EditBox fromColorBox;
    private EditBox toColorBox;
    private FlashRulesList list;

    // Geometry shared between the list rows and the drawn column headers, recomputed each init().
    private int rowLeft;
    private int rowWidth;
    private int headerY;

    public VisualSwapConfigScreen(Screen parent)
    {
        super(Component.literal("Visual Swap"));
        this.parent = parent;

        ModConfig cfg = ModConfig.get();
        this.workingPreset = cfg.preset;
        this.workingSize = cfg.preset.getSizeMultiplier();
        this.workingFrom = cfg.preset.getFromColor();
        this.workingTo = cfg.preset.getToColor();
        this.ruleSeed = cfg.clickFlashRules;
    }

    @Override
    protected void init()
    {
        // Preserve in-progress rule edits across a window resize (init runs again).
        List<FlashRule> seed = (this.list != null) ? this.list.toRules() : this.ruleSeed;

        int cx = this.width / 2;

        // --- indicator preset ---
        int groupW = 192;
        int gx = cx - groupW / 2;
        int chipsY = TITLE_Y + 16;

        addRenderableWidget(CycleButton.<Preset>builder(
                        preset -> Component.literal(preset.getDisplayName()),
                        this.workingPreset
                )
                                    .withValues(Preset.VANILLA, Preset.PRACTICE, Preset.CUSTOM)
                                    .create(
                                            gx,
                                            chipsY,
                                            groupW,
                                            CHIP_H,
                                            Component.literal("Preset"),
                                            (button, value) -> setPreset(value)
                                    ));

        // --- size slider (edits the active preset) ---
        int sliderY = chipsY + CHIP_H + 6;
        this.slider = new SizeSlider(
                gx,
                                     sliderY,
                                     groupW,
                                     CHIP_H,
                                     this.workingPreset.getDisplayName(),
                                     workingSize,
                                     v -> workingSize = v
        );
        addRenderableWidget(this.slider);

        // --- custom colors (only under the Custom preset) ---
        int lastTopRowY = sliderY;
        if (this.workingPreset.isCustom())
        {
            int boxX = gx + COLOR_LABEL_W + COLOR_SWATCH + 6;
            int boxW = gx + groupW - boxX;

            int fromY = sliderY + CHIP_H + 6;
            this.fromColorBox = makeColorBox(boxX, fromY, boxW, this.workingFrom, v -> this.workingFrom = v);
            addRenderableWidget(this.fromColorBox);

            int toY = fromY + CHIP_H + 4;
            this.toColorBox = makeColorBox(boxX, toY, boxW, this.workingTo, v -> this.workingTo = v);
            addRenderableWidget(this.toColorBox);

            lastTopRowY = toY;
        }
        else
        {
            this.fromColorBox = null;
            this.toColorBox = null;
        }

        // --- rules table ---
        this.rowWidth = Math.min(360, this.width - 60);
        this.rowLeft = cx - this.rowWidth / 2;
        int listTop = lastTopRowY + CHIP_H + 24;
        this.headerY = listTop - 11;
        int listBottom = this.height - FOOTER_H;
        this.list = new FlashRulesList(this.minecraft, this.width, listBottom - listTop, listTop, this.rowWidth, seed);
        addRenderableWidget(this.list);

        // --- footer ---
        int footerY = this.height - 28;
        int addW = 96;
        int btnW = 90;
        int btnGap = 8;

        addRenderableWidget(Button.builder(Component.literal("+ Add rule"), b -> this.list.addRule())
                                    .bounds(this.rowLeft, footerY, addW, CHIP_H)
                                    .build());

        int rightEdge = this.rowLeft + this.rowWidth;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> commitAndClose())
                                    .bounds(rightEdge - btnW, footerY, btnW, CHIP_H)
                                    .build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                                    .bounds(rightEdge - btnW * 2 - btnGap, footerY, btnW, CHIP_H)
                                    .build());
    }

    private void setPreset(Preset preset)
    {
        this.workingPreset = preset;
        // The Custom preset adds two color rows, so the layout has to be rebuilt when the preset changes.
        rebuildWidgets();
    }

    /// A hex-color edit box (AARRGGBB, "#"/"0x" and 6-digit RRGGBB accepted) that pushes each valid value to
    /// {@code sink} and reddens its text while the entry is unparseable.
    private EditBox makeColorBox(int x, int y, int width, int initial, IntConsumer sink)
    {
        EditBox box = new EditBox(this.font, x, y, width, CHIP_H, Component.literal("Color"));
        box.setMaxLength(10);
        box.setHint(Component.literal("AARRGGBB"));
        box.setValue(ColorHelpers.formatArgbHex(initial));
        box.setResponder(text -> {
            Integer color = ColorHelpers.parseHexColor(text);
            box.setTextColor(color != null ? TEXT_VALID : TEXT_INVALID);
            if (color != null) sink.accept(color);
        });
        return box;
    }

    private void commitAndClose()
    {
        ModConfig cfg = ModConfig.get();
        cfg.preset = this.workingPreset;
        cfg.preset.setSizeMultiplier(this.workingSize);
        cfg.preset.setFromColor(this.workingFrom);
        cfg.preset.setToColor(this.workingTo);
        cfg.clickFlashRules = this.list.toRules();
        AutoConfig.getConfigHolder(ModConfig.class)
                .save();
        this.minecraft.setScreenAndShow(this.parent);
    }

    /* OVERRIDES */

    @Override
    public void onClose() { this.minecraft.setScreenAndShow(this.parent); }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a)
    {
        super.extractRenderState(g, mouseX, mouseY, a);

        g.centeredText(this.font, getTitle(), this.width / 2, TITLE_Y, TITLE_COLOR);

        // Section caption + column headers, aligned to the row columns (must mirror FlashRuleEntry's layout).
        int contentX = this.rowLeft + 2;
        int contentRight = this.rowLeft + this.rowWidth - 2;
        int deleteX = contentRight - 18;
        int colorX = deleteX - 6 - 70;
        int intensityX = colorX - 6 - 46;
        int onX = intensityX - 6 - 58;
        int boxX = contentX + 16 + 6;

        g.text(this.font, Component.literal("Item"), boxX, this.headerY, MUTED_COLOR);
        g.centeredText(this.font, Component.literal("Flash Type"), onX + 29, this.headerY, MUTED_COLOR);
        g.centeredText(this.font, Component.literal("Intensity"), intensityX + 23, this.headerY, MUTED_COLOR);
        g.centeredText(this.font, Component.literal("Color"), colorX + 35, this.headerY, MUTED_COLOR);

        // Custom-color rows: "From" / "To" caption + a live swatch left of each hex box.
        if (this.fromColorBox != null) drawColorRow(g, "From", this.fromColorBox, this.workingFrom);
        if (this.toColorBox != null) drawColorRow(g, "To", this.toColorBox, this.workingTo);
    }

    /* HELPERS */

    private void drawColorRow(GuiGraphicsExtractor g, String label, EditBox box, int color)
    {
        int labelX = box.getX() - COLOR_SWATCH - 6 - COLOR_LABEL_W;
        int textY = box.getY() + (CHIP_H - this.font.lineHeight) / 2 + 1;
        g.text(this.font, Component.literal(label), labelX, textY, LABEL_COLOR);

        int swatchX = box.getX() - COLOR_SWATCH - 6;
        int swatchY = box.getY() + (CHIP_H - COLOR_SWATCH) / 2;
        g.fill(swatchX - 1, swatchY - 1, swatchX + COLOR_SWATCH + 1, swatchY + COLOR_SWATCH + 1, SWATCH_BORDER);
        g.fill(swatchX, swatchY, swatchX + COLOR_SWATCH, swatchY + COLOR_SWATCH, color);
    }
}

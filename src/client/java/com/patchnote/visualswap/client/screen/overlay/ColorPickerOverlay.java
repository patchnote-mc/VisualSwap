package com.patchnote.visualswap.client.screen.overlay;

import com.patchnote.visualswap.client.utils.ColorHelpers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.DoubleToIntFunction;
import java.util.function.IntConsumer;

/// A colour picker in a floating overlay, opened from a colour swatch. Three tabs:
/// - **Wheel** (default) — the HSV pinwheel (hue = angle, saturation = radius) with value/alpha sliders below;
/// - **Sliders** — one gradient slider per HSVA channel;
/// - **Hex** — a {@code #RRGGBB} entry box (RGB only), with an alpha slider below it when alpha is enabled.
///
/// HSV state is the source of truth (so hue survives zero-saturation edits); every change is pushed live through
/// {@code onChange} — the opener wires that to its hex box, whose responder runs the normal edit pipeline. With
/// {@code alphaEnabled} false the alpha channel is pinned to FF and its controls are hidden (rule tints are RGB-only).
public final class ColorPickerOverlay extends Overlay
{
    public enum Mode
    {
        WHEEL,
        SLIDERS,
        HEX
    }

    private static final int PAD = 6;         // inner margin from the panel edge to the content
    private static final int CONTENT_W = 150;
    private static final int TAB_H = 15;
    private static final int TAB_GAP = 2;
    private static final int CHIP = 15;       // live-colour chip, right of the tabs
    private static final int TABS_TO_BODY = 6;
    private static final int SLIDER_H = 11;
    private static final int HEX_BOX_H = 18;   // hex entry box height
    private static final int ROW_STEP = 16;   // vertical pitch between stacked sliders
    private static final int LABEL_W = 11;    // "H"/"S"/"V"/"A" gutter left of a slider
    private static final int WHEEL_GAP = 8;   // wheel → its value/alpha sliders

    private static final int PANEL_BG = 0xF0121218;
    private static final int PANEL_BORDER = 0xFF45454F;
    private static final int DIVIDER = 0xFF303038;
    private static final int LABEL_ARGB = 0xFFB9B9C0;
    private static final int TEXT_VALID = 0xFFE0E0E0;
    private static final int TEXT_INVALID = 0xFFFF5555;

    private final Font font = Minecraft.getInstance().font;
    private final boolean alphaEnabled;
    private final IntConsumer onChange;

    // HSV(A) is the working state; ARGB is derived on push
    private float hue;
    private float sat;
    private float val;
    private int alpha;

    private final List<AbstractWidget> wheelWidgets = new ArrayList<>();
    private final List<AbstractWidget> sliderWidgets = new ArrayList<>();
    private final EditBox hexBox;
    private final GradientSlider hexAlpha;  // alpha slider under the hex box; null when alpha is disabled

    private Mode mode = Mode.WHEEL;
    private boolean syncingHex;

    public ColorPickerOverlay(int initial, boolean alphaEnabled, IntConsumer onChange)
    {
        super(CONTENT_W + 2 * PAD, contentHeight(alphaEnabled) + 2 * PAD);
        this.alphaEnabled = alphaEnabled;
        this.onChange = onChange;

        float[] hsv = ColorHelpers.argbToHsv(initial);
        this.hue = hsv[0];
        this.sat = hsv[1];
        this.val = hsv[2];
        this.alpha = alphaEnabled ? (initial >>> 24) : 0xFF;

        int x = contentX();
        int tabsY = contentY();

        int tabsW = CONTENT_W - CHIP - TAB_GAP;
        int tabW = (tabsW - 2 * TAB_GAP) / 3;
        addChild(new TabButton(
                x,
                               tabsY,
                               tabW,
                               TAB_H,
                               Component.translatable("gui.visual-swap.picker.tab.wheel"),
                               () -> this.mode == Mode.WHEEL,
                               () -> setMode(Mode.WHEEL)
        ));
        addChild(new TabButton(
                x + tabW + TAB_GAP,
                               tabsY,
                               tabW,
                               TAB_H,
                               Component.translatable("gui.visual-swap.picker.tab.sliders"),
                               () -> this.mode == Mode.SLIDERS,
                               () -> setMode(Mode.SLIDERS)
        ));
        addChild(new TabButton(
                x + 2 * (tabW + TAB_GAP),
                               tabsY,
                               tabW,
                               TAB_H,
                               Component.translatable("gui.visual-swap.picker.tab.hex"),
                               () -> this.mode == Mode.HEX,
                               () -> setMode(Mode.HEX)
        ));

        buildWheelTab(x);
        buildSlidersTab(x);
        this.hexBox = addChild(buildHexBox(x));
        this.hexAlpha = this.alphaEnabled ? addChild(alphaSlider(x, hexAlphaY())) : null;

        setMode(Mode.WHEEL);
        syncHexBox();
    }

    @Override
    protected int pad() { return PAD; }

    /// Top of the tab body — derived live from the current origin so it tracks {@link #position} instead of freezing at
    /// the construction-time (0,0) origin (children shift with the overlay; free-form content must too).
    private int bodyY() { return contentY() + TAB_H + TABS_TO_BODY; }

    /// Top of the hex tab's alpha slider — sits below the hex entry box.
    private int hexAlphaY() { return bodyY() + 4 + HEX_BOX_H + WHEEL_GAP; }

    /* TAB CONTENT */

    private void buildWheelTab(int x)
    {
        int wheelX = x + (CONTENT_W - HueSatWheel.SIZE) / 2;
        this.wheelWidgets.add(addChild(new HueSatWheel(
                wheelX, bodyY(), () -> new float[]{this.hue, this.sat, this.val}, (h, s) -> {
            this.hue = h;
            this.sat = s;
            push();
        }
        )));
        int slidersY = bodyY() + HueSatWheel.SIZE + WHEEL_GAP;
        this.wheelWidgets.add(addChild(valueSlider(x, slidersY)));
        if (this.alphaEnabled) this.wheelWidgets.add(addChild(alphaSlider(x, slidersY + ROW_STEP)));
    }

    private void buildSlidersTab(int x)
    {
        // hue is periodic — wrap to [0, 360) so the far-right (360°) collapses to red at the left, not a stuck thumb
        this.sliderWidgets.add(addChild(slider(
                x, bodyY(), () -> this.hue / 360.0, v -> {
                    this.hue = (float) (v * 360.0 % 360.0);
                    push();
                }, f -> ColorHelpers.hsvToArgb((float) (f * 360.0), 1f, 1f, 255), false
        )));
        this.sliderWidgets.add(addChild(slider(
                x, bodyY() + ROW_STEP, () -> this.sat, v -> {
                    this.sat = (float) v;
                    push();
                }, f -> ColorHelpers.hsvToArgb(this.hue, (float) f, this.val, 255), false
        )));
        this.sliderWidgets.add(addChild(valueSlider(x, bodyY() + 2 * ROW_STEP)));
        if (this.alphaEnabled) this.sliderWidgets.add(addChild(alphaSlider(x, bodyY() + 3 * ROW_STEP)));
    }

    private GradientSlider valueSlider(int x, int y)
    {
        return slider(
                x, y, () -> this.val, v -> {
                    this.val = (float) v;
                    push();
                }, f -> ColorHelpers.hsvToArgb(this.hue, this.sat, (float) f, 255), false
        );
    }

    private GradientSlider alphaSlider(int x, int y)
    {
        return slider(
                x, y, () -> this.alpha / 255.0, v -> {
                    this.alpha = (int) Math.round(v * 255.0);
                    push();
                }, f -> ColorHelpers.hsvToArgb(this.hue, this.sat, this.val, (int) Math.round(f * 255.0)), true
        );
    }

    private GradientSlider slider(int x, int y, DoubleSupplier get, DoubleConsumer set, DoubleToIntFunction track,
                                  boolean checker)
    {
        return new GradientSlider(x + LABEL_W, y, CONTENT_W - LABEL_W, SLIDER_H, get, set, track, checker);
    }

    private EditBox buildHexBox(int x)
    {
        EditBox box = new EditBox(this.font, x + 2, bodyY() + 4, CONTENT_W - 4, HEX_BOX_H,
                                  Component.translatable("gui.visual-swap.picker.hex.narration"));
        box.setMaxLength(10);
        box.setHint(Component.translatable("gui.visual-swap.picker.hex.hint"));
        box.setResponder(this::onHexEdited);
        return box;
    }

    /* STATE */

    private void setMode(Mode mode)
    {
        this.mode = mode;
        this.wheelWidgets.forEach(w -> w.visible = mode == Mode.WHEEL);
        this.sliderWidgets.forEach(w -> w.visible = mode == Mode.SLIDERS);
        this.hexBox.visible = mode == Mode.HEX;
        if (this.hexAlpha != null) this.hexAlpha.visible = mode == Mode.HEX;
    }

    private int argb()
    {
        return ColorHelpers.hsvToArgb(this.hue, this.sat, this.val, this.alphaEnabled ? this.alpha : 0xFF);
    }

    /// A wheel/slider edit: mirror it into the hex box and notify the opener.
    private void push()
    {
        syncHexBox();
        this.onChange.accept(argb());
    }

    private void syncHexBox()
    {
        this.syncingHex = true;
        int argb = argb();
        this.hexBox.setValue("#" + ColorHelpers.formatRgbHex(argb));
        this.hexBox.setTextColor(TEXT_VALID);
        this.syncingHex = false;
    }

    /// A hex-tab edit: adopt the parsed RGB as the new HSV state (without rewriting the box mid-typing). Alpha is
    /// owned by the tab's alpha slider, so any alpha byte in the entry is ignored.
    private void onHexEdited(String text)
    {
        if (this.syncingHex) return;
        Integer color = ColorHelpers.parseHexColor(text);
        this.hexBox.setTextColor(color != null ? TEXT_VALID : TEXT_INVALID);
        if (color == null) return;

        float[] hsv = ColorHelpers.argbToHsv(color);
        if (hsv[1] > 1e-4f) this.hue = hsv[0];  // greys carry no hue — keep the current one
        this.sat = hsv[1];
        this.val = hsv[2];
        this.onChange.accept(argb());
    }

    /* RENDER */

    @Override
    protected void extractBackground(GuiGraphicsExtractor g)
    {
        int x0 = getX();
        int y0 = getY();
        int x1 = getX() + getWidth();
        int y1 = getY() + getHeight();
        g.fill(x0, y0, x1, y1, PANEL_BG);
        g.fill(x0, y0, x1, y0 + 1, PANEL_BORDER);
        g.fill(x0, y1 - 1, x1, y1, PANEL_BORDER);
        g.fill(x0, y0, x0 + 1, y1, PANEL_BORDER);
        g.fill(x1 - 1, y0, x1, y1, PANEL_BORDER);
    }

    @Override
    protected void extractContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float a)
    {
        extractColorChip(g);
        g.fill(contentX(), bodyY() - 4, contentX() + CONTENT_W, bodyY() - 3, DIVIDER);  // under the tabs

        if (this.mode == Mode.SLIDERS)
        {
            String[] channels = {"h", "s", "v", "a"};
            int rows = this.alphaEnabled ? 4 : 3;
            for (int i = 0; i < rows; i++) sliderLabel(g, channels[i], bodyY() + i * ROW_STEP);
        }
        else if (this.mode == Mode.WHEEL)
        {
            int slidersY = bodyY() + HueSatWheel.SIZE + WHEEL_GAP;
            sliderLabel(g, "v", slidersY);
            if (this.alphaEnabled) sliderLabel(g, "a", slidersY + ROW_STEP);
        }
        else if (this.mode == Mode.HEX && this.alphaEnabled)
        {
            sliderLabel(g, "a", hexAlphaY());
        }
    }

    private void sliderLabel(GuiGraphicsExtractor g, String channel, int sliderY)
    {
        String label = Component.translatable("gui.visual-swap.picker.channel." + channel).getString();
        g.text(this.font, label, contentX(), sliderY + (SLIDER_H - this.font.lineHeight) / 2 + 1, LABEL_ARGB, false);
    }

    /// The live colour, top-right beside the tabs (over a checker when translucent).
    private void extractColorChip(GuiGraphicsExtractor g)
    {
        int x = contentX() + CONTENT_W - CHIP;
        int y = contentY();
        g.fill(x - 1, y - 1, x + CHIP + 1, y + CHIP + 1, PANEL_BORDER);
        int half = CHIP / 2;
        // checker
        g.fill(x, y, x + half, y + half, 0xFF000000);               // tl
        g.fill(x + half, y, x + CHIP, y + half, 0xFFFFFFFF);        // tr
        g.fill(x, y + half, x + half, y + CHIP, 0xFFFFFFFF);        // bl
        g.fill(x + half, y + half, x + CHIP, y + CHIP, 0xFF000000); // br
        g.fill(x, y, x + CHIP, y + CHIP, argb());
    }

    /// Tallest tab (wheel) drives the panel height: tabs + wheel + value slider (+ alpha slider).
    private static int contentHeight(boolean alphaEnabled)
    {
        return TAB_H + TABS_TO_BODY + HueSatWheel.SIZE + WHEEL_GAP + SLIDER_H + (alphaEnabled ? ROW_STEP : 0) + 2;
    }
}

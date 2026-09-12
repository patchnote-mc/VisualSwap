package com.patchnote.visualswap.client.screen.modal;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/// The mod's effect switches, moved off the config page into a {@link Modal} opened from the config header. Lists the
/// master switch and each effect toggle as an ON/OFF row; a sub-switch greys out while its parent switch is off (the
/// same umbrella relationship the effects gate through). The switches carry no tooltips — hovering a row instead prints
/// its help text in the panel's help area. Every change writes straight to the config screen's working state (via the
/// {@link Toggle} accessors) and rebuilds this modal so the greying tracks live; the config page re-reads that state
/// and re-applies its own greying when this modal closes back to it.
public final class EffectsModal extends Modal
{
    /// One switch: its label, the help shown while hovered, a live get/set of the backing working value, whether it can
    /// currently be toggled (greyed when a parent switch is off), and an indent level that nests sub-switches.
    public record Toggle(Component label, Component help, BooleanSupplier value, Consumer<Boolean> onChange,
                         BooleanSupplier enabled, int indent) { }

    private static final int PANEL_W = 244;
    private static final int PAD = 12;
    private static final int LINE = 10;
    private static final int TITLE_GAP = 6;
    private static final int ROW_H = 20;
    private static final int ROW_GAP = 4;
    private static final int INDENT = 14;
    private static final int HELP_GAP = 8;
    private static final int HELP_LINES = 2;
    private static final int BTN_GAP = 8;
    private static final int BTN_H = 20;

    private static final int PANEL_BG = 0xF00E0E14;
    private static final int PANEL_BORDER = 0xFF45454F;
    private static final int DIVIDER = 0xFF2A2A31;
    private static final int TITLE_ARGB = 0xFFFFFFFF;
    private static final int HELP_ARGB = 0xFF97979E;

    private final List<Toggle> toggles;
    private final List<CycleButton<Boolean>> buttons = new ArrayList<>();

    private int panelX;
    private int panelY;
    private int panelH;
    private int helpY;

    /// Index of the toggle whose row the mouse is over, or -1 — drives the help text. Updated in {@link #mouseMoved}.
    private int hovered = -1;

    private EffectsModal(List<Toggle> toggles)
    {
        super(Component.translatable("gui.visual-swap.effects.title"));
        this.toggles = toggles;
    }

    /// Open the effects modal over the current screen.
    public static void open(List<Toggle> toggles) { new EffectsModal(toggles).open(); }

    @Override
    protected void init()
    {
        this.buttons.clear();

        int rowsH = this.toggles.size() * ROW_H + (this.toggles.size() - 1) * ROW_GAP;
        this.panelH = PAD + LINE + TITLE_GAP + rowsH + HELP_GAP + HELP_LINES * LINE + HELP_GAP + BTN_H + PAD;
        this.panelX = (this.width - PANEL_W) / 2;
        this.panelY = (this.height - this.panelH) / 2;

        int y = this.panelY + PAD + LINE + TITLE_GAP;
        for (Toggle t : this.toggles)
        {
            int x = this.panelX + PAD + t.indent() * INDENT;
            int w = PANEL_W - 2 * PAD - t.indent() * INDENT;
            CycleButton<Boolean> button = CycleButton.onOffBuilder(t.value().getAsBoolean()).create(
                    x, y, w, ROW_H, //
                    t.label(), (b, value) -> {
                        t.onChange().accept(value);
                        rebuildWidgets();
                    }
            );
            button.active = t.enabled().getAsBoolean();
            addRenderableWidget(button);
            this.buttons.add(button);
            y += ROW_H + ROW_GAP;
        }

        this.helpY = y + HELP_GAP - ROW_GAP;

        int btnY = this.panelY + this.panelH - PAD - BTN_H;
        addRenderableWidget(Button.builder(Component.translatable("gui.visual-swap.button.done"), b -> close())
                                  .bounds(this.panelX + PAD, btnY, PANEL_W - 2 * PAD, BTN_H).build());
    }

    @Override
    public void mouseMoved(double mx, double my)
    {
        super.mouseMoved(mx, my);
        this.hovered = -1;
        for (int i = 0; i < this.buttons.size(); i++)
        {
            CycleButton<Boolean> b = this.buttons.get(i);
            if (mx >= b.getX() && mx < b.getX() + b.getWidth() && my >= b.getY() && my < b.getY() + b.getHeight())
            {
                this.hovered = i;
                break;
            }
        }
    }

    @Override
    protected void renderPanel(@NonNull GuiGraphics g)
    {
        int x1 = this.panelX + PANEL_W;
        int y1 = this.panelY + this.panelH;
        g.fill(this.panelX, this.panelY, x1, y1, PANEL_BG);
        g.fill(this.panelX, this.panelY, x1, this.panelY + 1, PANEL_BORDER);
        g.fill(this.panelX, y1 - 1, x1, y1, PANEL_BORDER);
        g.fill(this.panelX, this.panelY, this.panelX + 1, y1, PANEL_BORDER);
        g.fill(x1 - 1, this.panelY, x1, y1, PANEL_BORDER);

        int cx = this.width / 2;
        g.drawString(
                this.font, getTitle().getVisualOrderText(), cx - this.font.width(getTitle()) / 2,
                this.panelY + PAD, TITLE_ARGB, true
        );

        // divider above the help area
        g.fill(this.panelX + PAD, this.helpY - 4, x1 - PAD, this.helpY - 3, DIVIDER);

        Component help = (this.hovered >= 0)
                         ? this.toggles.get(this.hovered).help()
                         : Component.translatable("gui.visual-swap.effects.hint");
        List<FormattedCharSequence> lines = this.font.split(help, PANEL_W - 2 * PAD);
        int hy = this.helpY;
        for (int i = 0; i < Math.min(lines.size(), HELP_LINES); i++)
        {
            g.drawString(this.font, lines.get(i), this.panelX + PAD, hy, HELP_ARGB, false);
            hy += LINE;
        }
    }
}

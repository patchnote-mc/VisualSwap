package com.patchnote.visualswap.client.screen.modal;

import com.patchnote.visualswap.client.config.models.FlashIntensity;
import com.patchnote.visualswap.client.config.models.FlashRule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/// A rule's secondary settings — flash Strength plus independent Glyph and Hotbar Highlight opt-ins — moved off the
/// table row into a {@link Modal} opened from the row's config (gear) button. Mirrors the {@link EffectsModal} look:
/// each setting is a full-width button row, and hovering a row prints its help text in the panel's help area (the rows
/// carry no tooltips). Every change writes straight onto the shared working rule; returning re-inits the config screen,
/// which picks the edits up (same flow as the {@link RegexPreviewModal}).
public final class RuleConfigModal extends Modal
{
    private static final int PANEL_W = 244;
    private static final int PAD = 12;
    private static final int LINE = 10;
    private static final int TITLE_GAP = 6;
    private static final int ROW_H = 20;
    private static final int ROW_GAP = 4;
    private static final int HELP_GAP = 8;
    private static final int HELP_LINES = 2;
    private static final int BTN_H = 20;

    private static final int PANEL_BG = 0xF00E0E14;
    private static final int PANEL_BORDER = 0xFF45454F;
    private static final int DIVIDER = 0xFF2A2A31;
    private static final int TITLE_ARGB = 0xFFFFFFFF;
    private static final int HELP_ARGB = 0xFF97979E;

    private static final List<Component> HELP = List.of(
            Component.translatable("gui.visual-swap.rule_config.strength.help"),
            Component.translatable("gui.visual-swap.rule_config.glyph.help"),
            Component.translatable("gui.visual-swap.rule_config.hotbar_highlight.help")
    );

    private final FlashRule rule;
    private final List<AbstractWidget> buttons = new ArrayList<>();

    private int panelX;
    private int panelY;
    private int panelH;
    private int helpY;

    /// Index of the setting whose row the mouse is over, or -1 — drives the help text. Updated in {@link #mouseMoved}.
    private int hovered = -1;

    private RuleConfigModal(FlashRule rule)
    {
        super(Component.translatable("gui.visual-swap.rule_config.title"));
        this.rule = rule;
    }

    /// Open the rule-config modal over the current screen.
    public static void open(FlashRule rule) { new RuleConfigModal(rule).open(); }

    @Override
    protected void init()
    {
        this.buttons.clear();

        int rowsH = HELP.size() * ROW_H + (HELP.size() - 1) * ROW_GAP;
        this.panelH = PAD + LINE + TITLE_GAP + rowsH + HELP_GAP + HELP_LINES * LINE + HELP_GAP + BTN_H + PAD;
        this.panelX = (this.width - PANEL_W) / 2;
        this.panelY = (this.height - this.panelH) / 2;

        int x = this.panelX + PAD;
        int w = PANEL_W - 2 * PAD;
        int y = this.panelY + PAD + LINE + TITLE_GAP;

        FlashIntensity initial = this.rule.intensity() != null ? this.rule.intensity() : FlashIntensity.LOW;
        CycleButton<FlashIntensity> strength = CycleButton.builder(FlashIntensity::getNameComponent, initial)
                                                          .withValues(FlashIntensity.values())
                                                          .create(
                                                                  x, y, w, ROW_H, //
                                                                  Component.translatable(
                                                                          "gui.visual-swap.rule_config.strength.label"),
                                                                  (b, value) -> this.rule.setIntensity(value)
                                                          );
        addRenderableWidget(strength);
        this.buttons.add(strength);
        y += ROW_H + ROW_GAP;

        CycleButton<Boolean> glyph = CycleButton.onOffBuilder(this.rule.showGlyph()).create(
                x, y, w, ROW_H, //
                Component.translatable("gui.visual-swap.rule_config.glyph.label"),
                (b, value) -> this.rule.setShowGlyph(value)
        );
        addRenderableWidget(glyph);
        this.buttons.add(glyph);
        y += ROW_H + ROW_GAP;

        CycleButton<Boolean> hotbarHighlight = CycleButton.onOffBuilder(this.rule.showHotbarHighlight()).create(
                x, y, w, ROW_H, //
                Component.translatable("gui.visual-swap.rule_config.hotbar_highlight.label"),
                (b, value) -> this.rule.setShowHotbarHighlight(value)
        );
        addRenderableWidget(hotbarHighlight);
        this.buttons.add(hotbarHighlight);
        y += ROW_H + ROW_GAP;

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
            AbstractWidget b = this.buttons.get(i);
            if (mx >= b.getX() && mx < b.getX() + b.getWidth() && my >= b.getY() && my < b.getY() + b.getHeight())
            {
                this.hovered = i;
                break;
            }
        }
    }

    @Override
    protected void extractPanel(@NonNull GuiGraphicsExtractor g)
    {
        int x1 = this.panelX + PANEL_W;
        int y1 = this.panelY + this.panelH;
        g.fill(this.panelX, this.panelY, x1, y1, PANEL_BG);
        g.fill(this.panelX, this.panelY, x1, this.panelY + 1, PANEL_BORDER);
        g.fill(this.panelX, y1 - 1, x1, y1, PANEL_BORDER);
        g.fill(this.panelX, this.panelY, this.panelX + 1, y1, PANEL_BORDER);
        g.fill(x1 - 1, this.panelY, x1, y1, PANEL_BORDER);

        int cx = this.width / 2;
        g.text(
                this.font, getTitle().getVisualOrderText(), cx - this.font.width(getTitle()) / 2,
                this.panelY + PAD, TITLE_ARGB, true
        );

        // divider above the help area
        g.fill(this.panelX + PAD, this.helpY - 4, x1 - PAD, this.helpY - 3, DIVIDER);

        Component help = (this.hovered >= 0)
                         ? HELP.get(this.hovered)
                         : Component.translatable("gui.visual-swap.rule_config.hint");
        List<FormattedCharSequence> lines = this.font.split(help, PANEL_W - 2 * PAD);
        int hy = this.helpY;
        for (int i = 0; i < Math.min(lines.size(), HELP_LINES); i++)
        {
            g.text(this.font, lines.get(i), this.panelX + PAD, hy, HELP_ARGB, false);
            hy += LINE;
        }
    }
}

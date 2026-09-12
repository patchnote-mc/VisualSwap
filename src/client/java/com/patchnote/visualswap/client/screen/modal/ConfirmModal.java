package com.patchnote.visualswap.client.screen.modal;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/// A yes/no confirmation {@link Modal}: a title, wrapped body text, and a Cancel / Confirm button pair. Gate a
/// destructive action behind it with {@link #open(Component, Component, Component, Runnable)}.
///
/// After the confirm action runs, the modal returns to its backdrop **only if the action didn't itself navigate away**
/// (checked via the live screen) — so an action that leaves for another screen (e.g. "discard &amp; leave") isn't
/// yanked back, while an in-place action (reset/clear) returns to the screen it was launched from.
public final class ConfirmModal extends Modal
{
    private static final int PAD = 10;
    private static final int LINE = 10;
    private static final int TITLE_GAP = 4;
    private static final int TEXT_TO_BUTTONS = 12;
    private static final int BTN_H = 20;
    private static final int BTN_GAP = 6;
    private static final int MIN_BTN_W = 92;
    private static final int MAX_W = 300;
    private static final int SCREEN_MARGIN = 20;

    private static final int PANEL_BG = 0xF00E0E14;
    private static final int PANEL_BORDER = 0xFF45454F;
    private static final int TITLE_ARGB = 0xFFFFFFFF;
    private static final int BODY_ARGB = 0xFFB9B9C0;

    private final Component body;
    private final Component confirmLabel;
    private final Runnable onConfirm;

    private List<FormattedCharSequence> titleLines = List.of();
    private List<FormattedCharSequence> bodyLines = List.of();

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;

    private ConfirmModal(Component title, Component body, Component confirmLabel, Runnable onConfirm)
    {
        super(title);
        this.body = body;
        this.confirmLabel = confirmLabel;
        this.onConfirm = onConfirm;
    }

    /// Open a confirmation over the current screen.
    public static void open(Component title, Component body, Component confirmLabel, Runnable onConfirm)
    {
        new ConfirmModal(title, body, confirmLabel, onConfirm).open();
    }

    @Override
    protected void init()
    {
        Font font = this.font;
        int availablePanelW = Math.max(2 * PAD + 1, Math.min(MAX_W, this.width - 2 * SCREEN_MARGIN));
        int availableContentW = availablePanelW - 2 * PAD;
        int buttonRowW = Math.min(2 * MIN_BTN_W + BTN_GAP, availableContentW);
        int textW = Math.max(font.width(getTitle()), font.width(this.body));
        int contentW = Math.min(availableContentW, Math.max(textW, buttonRowW));

        this.titleLines = font.split(getTitle(), contentW);
        this.bodyLines = font.split(this.body, contentW);
        this.panelW = contentW + 2 * PAD;
        int titleH = Math.max(1, this.titleLines.size()) * LINE;
        int bodyH = Math.max(1, this.bodyLines.size()) * LINE;
        this.panelH = PAD + titleH + TITLE_GAP + bodyH + TEXT_TO_BUTTONS + BTN_H + PAD;
        this.panelX = (this.width - this.panelW) / 2;
        this.panelY = (this.height - this.panelH) / 2;

        int btnW = (contentW - BTN_GAP) / 2;
        int btnY = this.panelY + this.panelH - PAD - BTN_H;
        addRenderableWidget(Button.builder(Component.translatable("gui.visual-swap.button.cancel"), b -> close())
                                  .bounds(this.panelX + PAD, btnY, btnW, BTN_H).build());
        addRenderableWidget(Button.builder(this.confirmLabel, b -> confirm())
                                  .bounds(this.panelX + PAD + btnW + BTN_GAP, btnY, btnW, BTN_H).build());
    }

    private void confirm()
    {
        this.onConfirm.run();
        if (Minecraft.getInstance().screen == this) close();
    }

    @Override
    protected void renderPanel(GuiGraphics g)
    {
        int x1 = this.panelX + this.panelW;
        int y1 = this.panelY + this.panelH;
        g.fill(this.panelX, this.panelY, x1, y1, PANEL_BG);
        g.fill(this.panelX, this.panelY, x1, this.panelY + 1, PANEL_BORDER);
        g.fill(this.panelX, y1 - 1, x1, y1, PANEL_BORDER);
        g.fill(this.panelX, this.panelY, this.panelX + 1, y1, PANEL_BORDER);
        g.fill(x1 - 1, this.panelY, x1, y1, PANEL_BORDER);

        int cx = this.width / 2;
        int y = this.panelY + PAD;
        for (FormattedCharSequence line : this.titleLines)
        {
            g.drawString(this.font, line, cx - this.font.width(line) / 2, y, TITLE_ARGB, true);
            y += LINE;
        }
        y += TITLE_GAP;
        for (FormattedCharSequence line : this.bodyLines)
        {
            g.drawString(this.font, line, cx - this.font.width(line) / 2, y, BODY_ARGB, false);
            y += LINE;
        }
    }
}

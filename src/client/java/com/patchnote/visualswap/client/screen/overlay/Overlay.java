package com.patchnote.visualswap.client.screen.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/// A floating panel drawn above a screen's normal content, managed by an {@link OverlayManager}. Subclasses add child
/// widgets via {@link #addChild} (positioned relative to the overlay, then moved with it) and draw free-form content in
/// {@link #renderContent}. The panel background is the vanilla tooltip nine-slice, so overlays read as native chrome.
///
/// Two kinds:
/// - modal ({@link #isModal} true, the default) — captures all screen input while open; clicking outside dismisses it;
/// - passive (false) — pure display, no input capture (tooltips, onboarding callouts).
public abstract class Overlay extends AbstractContainerWidget
{
    /// Default content inset = vanilla tooltip text padding, so a tooltip-sprite background lines up exactly. The
    /// widget bounds are {@code content + 2*pad}; subclasses that want more breathing room override {@link #pad}.
    protected static final int PAD = 3;

    private final List<GuiEventListener> children = new ArrayList<>();

    protected Overlay(int width, int height)
    {
        super(0, 0, width, height, Component.empty());
    }

    /// Inset from the overlay bounds to the content — must match the {@code content + 2*pad} sizing the subclass used.
    protected int pad() { return PAD; }

    /// Whether this overlay captures screen input while open (click-outside dismisses). Passive overlays return false.
    public boolean isModal() { return true; }

    /// Invoked by the manager when the overlay is dismissed — for cleanup/apply hooks.
    protected void onClosed() { }

    /* CONTENT */

    /// Register a child widget positioned relative to the overlay's current origin. Children move with the overlay.
    protected <T extends AbstractWidget> T addChild(T child)
    {
        this.children.add(child);
        return child;
    }

    /// Free-form drawing under the child widgets (labels, swatches, custom tracks).
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float a) { }

    /// The panel chrome behind the content — the vanilla tooltip nine-slice by default; override for a solid panel.
    protected void renderBackground(GuiGraphics g)
    {
        TooltipRenderUtil.renderTooltipBackground(
                g, contentX(), contentY(), getWidth() - 2 * pad(),
                getHeight() - 2 * pad(), null
        );
    }

    protected final int contentX() { return getX() + pad(); }

    protected final int contentY() { return getY() + pad(); }

    /* POSITIONING */

    /// Place the overlay's top-left at ({@code x}, {@code y}), clamped so it stays fully on screen.
    public void position(int x, int y)
    {
        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int m = pad();
        setX(Math.clamp(x, m, Math.max(m, screenW - getWidth() - m)));
        setY(Math.clamp(y, m, Math.max(m, screenH - getHeight() - m)));
    }

    // children carry absolute coordinates, so moving the overlay shifts them by the same delta
    @Override
    public void setX(int x)
    {
        int dx = x - getX();
        super.setX(x);
        for (GuiEventListener child : this.children)
        {
            if (child instanceof AbstractWidget w) w.setX(w.getX() + dx);
        }
    }

    @Override
    public void setY(int y)
    {
        int dy = y - getY();
        super.setY(y);
        for (GuiEventListener child : this.children)
        {
            if (child instanceof AbstractWidget w) w.setY(w.getY() + dy);
        }
    }

    /* OVERRIDES */

    @Override
    public @NonNull List<? extends GuiEventListener> children() { return this.children; }

    @Override
    protected int contentHeight() { return getHeight(); }  // == height: overlays never self-scroll

    @Override
    protected double scrollRate() { return 0.0; }

    @Override
    protected void renderWidget(@NonNull GuiGraphics g, int mouseX, int mouseY, float a)
    {
        renderBackground(g);
        renderContent(g, mouseX, mouseY, a);
        for (GuiEventListener child : this.children)
        {
            if (child instanceof AbstractWidget w) w.render(g, mouseX, mouseY, a);
        }
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput output) { }
}

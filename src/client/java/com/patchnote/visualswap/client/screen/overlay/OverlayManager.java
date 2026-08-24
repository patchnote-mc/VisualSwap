package com.patchnote.visualswap.client.screen.overlay;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.jspecify.annotations.Nullable;

/// Hosts one {@link Overlay} (plus a transient hover tooltip) on top of a screen. The owning screen delegates every
/// input event here FIRST (a consumed event never reaches the page) and calls {@link #render} LAST so overlays render
/// above all normal content, on their own strata.
///
/// Hover tooltips are immediate-mode: a widget calls {@link #showTooltip} every frame it is hovered (from its render
/// pass); the tooltip disappears the first frame it is not re-requested.
public final class OverlayManager
{
    private @Nullable Overlay overlay;
    private @Nullable TooltipOverlay tooltip;
    private boolean tooltipRequested;

    /* API */

    public void open(Overlay overlay)
    {
        close();
        this.overlay = overlay;
    }

    public void close()
    {
        if (this.overlay != null) this.overlay.onClosed();
        this.overlay = null;
    }

    public boolean isOpen() { return this.overlay != null; }

    /// Show {@code tooltip} for this frame only — call every frame while the trigger (hover) holds.
    public void showTooltip(TooltipOverlay tooltip)
    {
        this.tooltip = tooltip;
        this.tooltipRequested = true;
    }

    /* INPUT INTERCEPTION — each returns true when the event was consumed by the overlay layer */

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)
    {
        if (!isModalOpen()) return false;
        if (this.overlay.isMouseOver(event.x(), event.y()))
        {
            this.overlay.mouseClicked(event, doubleClick);
        }
        else
        {
            close();  // click-outside dismisses; the click itself is swallowed
        }
        return true;
    }

    public boolean mouseReleased(MouseButtonEvent event)
    {
        if (!isModalOpen()) return false;
        this.overlay.mouseReleased(event);
        return true;
    }

    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy)
    {
        if (!isModalOpen()) return false;
        this.overlay.mouseDragged(event, dx, dy);
        return true;
    }

    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY)
    {
        if (!isModalOpen()) return false;
        this.overlay.mouseScrolled(x, y, scrollX, scrollY);
        return true;  // the page must not scroll under a modal overlay
    }

    public void mouseMoved(double x, double y)
    {
        if (isModalOpen()) this.overlay.mouseMoved(x, y);
    }

    public boolean keyPressed(KeyEvent event)
    {
        if (!isModalOpen()) return false;
        if (event.isEscape())
        {
            close();
            return true;
        }
        this.overlay.keyPressed(event);
        return true;  // swallow all keys while modal (typing must not trigger screen hotkeys)
    }

    public boolean charTyped(CharacterEvent event)
    {
        if (!isModalOpen()) return false;
        this.overlay.charTyped(event);
        return true;
    }

    /* RENDER */

    /// Draw the overlay layer — call at the end of the screen render so overlays sit above everything.
    public void render(GuiGraphics g, int mouseX, int mouseY, float a)
    {
        if (this.overlay != null)
        {
            g.nextStratum();
            this.overlay.render(g, mouseX, mouseY, a);
        }
        if (this.tooltip != null)
        {
            if (this.tooltipRequested)
            {
                g.nextStratum();
                this.tooltip.render(g, mouseX, mouseY, a);
            }
            else
            {
                this.tooltip = null;  // not re-requested this frame — the hover ended
            }
            this.tooltipRequested = false;
        }
    }

    /// True while a modal overlay is capturing input — the owning screen renders the page beneath it with an off-screen
    /// mouse so covered widgets don't show hover outlines or hijack the cursor through the overlay.
    public boolean isModalOpen() { return this.overlay != null && this.overlay.isModal(); }
}

package com.patchnote.visualswap.client.screen.modal;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/// A modal dialog that is its own {@link Screen} (unlike an in-screen {@code Overlay}). It captures whatever screen was
/// open as its **backdrop**, renders that backdrop dimmed behind a centred panel, and returns to it on close — so it
/// works over any screen and owns all the routing itself. Open one from anywhere with {@link #open()}; a subclass adds
/// its widgets in {@code init()} and draws its panel chrome in {@link #extractPanel}.
public abstract class Modal extends Screen
{
    protected static final int SCRIM = 0xB4000000;

    /// The screen this modal was opened over — drawn behind it and returned to on close (null if opened over nothing).
    protected final @Nullable Screen background;

    protected Modal(Component title)
    {
        super(title);
        this.background = Minecraft.getInstance().screen;
    }

    /// Show this modal over the currently-open screen.
    public final void open() { Minecraft.getInstance().setScreenAndShow(this); }

    /// Dismiss and return to the backdrop screen.
    public final void close() { Minecraft.getInstance().setScreenAndShow(this.background); }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a)
    {
        if (this.background != null)
        {
            g.nextStratum();
            this.background.extractRenderState(g, -1, -1, a);   // backdrop, rendered with an off-screen mouse (no hover)
        }
        g.nextStratum();
        g.fill(0, 0, this.width, this.height, SCRIM);
        extractPanel(g);
        g.nextStratum();
        super.extractRenderState(g, mouseX, mouseY, a);   // this modal's own widgets, on top
    }

    /// Draw the modal's panel chrome (background + text) above the scrim and beneath the widgets.
    protected abstract void extractPanel(GuiGraphicsExtractor g);

    @Override
    public void onClose() { close(); }

    @Override
    public boolean isPauseScreen()
    {
        return this.background == null || this.background.isPauseScreen();
    }
}

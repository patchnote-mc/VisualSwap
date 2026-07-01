package com.patchnote.visualswap.client.hud;

import com.patchnote.visualswap.client.particles.SwapHitMasks;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.NonNull;

public final class SwapHitGlyph implements HudElement
{
    private static final int SCALE = 1;
    private static final int VERTICAL_OFFSET = 20;

    /// Horizontal gap (px) between the glyph and the `xN` chain counter.
    private static final int COUNTER_GAP = 2;

    // state
    private boolean visible;
    private boolean attacked;
    private boolean failed;
    private boolean stunSlam;
    private int chainCount;

    // cache
    private boolean loaded;
    private SwapHitMasks.Mask possibleMask;
    private SwapHitMasks.Mask attackedMask;
    private SwapHitMasks.Mask failedMask;
    private SwapHitMasks.Mask stunSlamMask;

    /// Update Each Tick
    public void updateState(boolean visible, boolean attacked, boolean failed, boolean stunSlam, int chainCount)
    {
        this.visible = visible;
        this.attacked = attacked;
        this.failed = failed;
        this.stunSlam = stunSlam;
        this.chainCount = chainCount;
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, @NonNull DeltaTracker deltaTracker)
    {
        if (!this.visible) { return; }

        ensureLoaded();
        SwapHitMasks.Mask mask;

        if (this.stunSlam) mask = this.stunSlamMask;
        else if (this.failed) mask = this.failedMask;
        else if (this.attacked) mask = this.attackedMask;
        else mask = this.possibleMask;

        if (!mask.canDraw()) return;

        // draw
        int color = mask.color();
        int cols = mask.width();
        int rows = mask.height();
        int left = (graphics.guiWidth() / 2) - (cols * SCALE / 2);
        int top = (graphics.guiHeight() / 2) - (rows * SCALE / 2) + VERTICAL_OFFSET;

        for (int row = 0; row < rows; row++)
        {
            for (int col = 0; col < cols; col++)
            {
                if (!mask.filled(col, row))
                {
                    continue;
                }
                int x = left + col * SCALE;
                int y = top + row * SCALE;
                graphics.fill(x, y, x + SCALE, y + SCALE, color);
            }
        }

        // Chain counter ("x2", "x3", ...) drawn to the right of the glyph, vertically centred on it.
        if (this.chainCount >= 2)
        {
            Font font = Minecraft.getInstance().font;
            int textX = left + cols * SCALE + COUNTER_GAP;
            int textY = top + (rows * SCALE - font.lineHeight) / 2;
            graphics.text(font, "x" + this.chainCount, textX, textY, color);
        }
    }

    /* HELPERS */

    private void ensureLoaded()
    {
        if (this.loaded) return;

        this.loaded = true;
        this.possibleMask = SwapHitMasks.possible();
        this.attackedMask = SwapHitMasks.attacked();
        this.failedMask = SwapHitMasks.failed();
        this.stunSlamMask = SwapHitMasks.stunSlam();
    }
}

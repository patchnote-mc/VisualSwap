package com.patchnote.visualswap.client.hud;

import com.patchnote.visualswap.client.config.ModConfig;
import com.patchnote.visualswap.client.particles.SwapHitMasks;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
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
    private boolean consecutive;
    private int chainCount;

    // cache
    private boolean loaded;
    private SwapHitMasks.Mask possibleMask;
    private SwapHitMasks.Mask attackedMask;
    private SwapHitMasks.Mask failedMask;
    private SwapHitMasks.Mask consecutiveMask;

    /* EVENTS */

    /// Update Each Tick
    public void eventUpdate(boolean visible, boolean attacked, boolean failed, boolean consecutive, int chainCount)
    {
        this.visible = visible;
        this.attacked = attacked;
        this.failed = failed;
        this.consecutive = consecutive;
        this.chainCount = chainCount;
    }

    public void eventReset() { eventUpdate(false, false, false, false, 0); }

    /* OVERRIDES */

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, @NonNull DeltaTracker deltaTracker)
    {
        if (!this.visible) { return; }

        ensureLoaded();
        SwapHitMasks.Mask mask;

        if (this.consecutive) mask = this.consecutiveMask;
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

        boolean vanilla = ModConfig.get().preset.isVanilla();

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
                graphics.fill(
                        vanilla ? RenderPipelines.GUI_INVERT : RenderPipelines.GUI,
                        x,
                        y,
                        x + SCALE,
                        y + SCALE,
                        color
                );
            }
        }

        // chain count
        if (this.chainCount >= 2)
        {
            int markWidth = SCALE;
            int markHeight = rows * SCALE;

            int startX = left + cols * SCALE + COUNTER_GAP;

            for (int i = 0; i < this.chainCount; i++)
            {
                int x = startX + i * (markWidth + SCALE);

                graphics.fill(
                        vanilla ? RenderPipelines.GUI_INVERT : RenderPipelines.GUI,
                        x,
                        top,
                        x + markWidth,
                        top + markHeight,
                        color
                );
            }
        }
    }

    /* HELPERS */

    private void ensureLoaded()
    {
        if (this.loaded) return;

        this.possibleMask = SwapHitMasks.possible();
        this.attackedMask = SwapHitMasks.attacked();
        this.failedMask = SwapHitMasks.failed();
        this.consecutiveMask = SwapHitMasks.consecutive();
        this.loaded = true;
    }
}

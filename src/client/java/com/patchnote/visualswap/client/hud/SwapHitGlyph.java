package com.patchnote.visualswap.client.hud;

import com.patchnote.visualswap.client.particles.SwapHitMasks;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.NonNull;

public final class SwapHitGlyph implements HudElement
{
    private static final int SCALE = 1;
    private static final int VERTICAL_OFFSET = 20;
    
    // state
    private boolean visible;
    private boolean attacked;

    // cache
    private boolean loaded;
    private SwapHitMasks.Mask possibleMask;
    private SwapHitMasks.Mask attackedMask;

    /// Update Each Tick
    public void updateState(boolean visible, boolean attacked)
    {
        this.visible = visible;
        this.attacked = attacked;
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, @NonNull DeltaTracker deltaTracker)
    {
        if (!this.visible) { return; }

        ensureLoaded();
        SwapHitMasks.Mask mask = this.attacked ? this.attackedMask : this.possibleMask;
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
    }

    /* HELPERS */

    private void ensureLoaded()
    {
        if (this.loaded) return;

        this.loaded = true;
        this.possibleMask = SwapHitMasks.possible();
        this.attackedMask = SwapHitMasks.attacked();
    }
}

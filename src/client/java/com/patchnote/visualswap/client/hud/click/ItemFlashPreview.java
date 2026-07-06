package com.patchnote.visualswap.client.hud.click;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/// Frame-scoped registry that lets a screen widget flash arbitrary GUI items through the real
/// {@link ItemFlashPipeline#WHITE_SILHOUETTE} shader path. A widget registers each preview item's exact draw position
/// and packed tint during its extract pass; the {@code GuiItemFlashPreviewMixin} then re-blits any atlas item whose
/// position matches. Position is the only correlation available — {@code GuiItemRenderState} carries no source info.
///
/// Render-thread only. The owning widget clears + re-registers every frame; the owning screen clears on
/// {@code removed()} so stale positions can't tint unrelated items on later screens.
public final class ItemFlashPreview
{
    private static final Map<Long, Integer> TINTS = new HashMap<>();

    private ItemFlashPreview() { }

    public static void clear() { TINTS.clear(); }

    /// Flash the 16px GUI item drawn at exactly ({@code x}, {@code y}) with {@code tint} (packed via
    /// {@link ItemFlash#packTint}).
    public static void register(int x, int y, int tint) { TINTS.put(key(x, y), tint); }

    /// @return the registered tint for the item at ({@code x}, {@code y}), or null if none.
    public static @Nullable Integer tintAt(int x, int y)
    {
        return TINTS.isEmpty() ? null : TINTS.get(key(x, y));
    }

    private static long key(int x, int y) { return ((long) x << 32) | (y & 0xFFFFFFFFL); }
}

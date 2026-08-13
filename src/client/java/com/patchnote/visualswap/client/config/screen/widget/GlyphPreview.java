package com.patchnote.visualswap.client.config.screen.widget;

import com.patchnote.visualswap.VisualSwap;
import com.patchnote.visualswap.client.config.models.PresetType;
import com.patchnote.visualswap.client.particles.SwapHitMasks;
import com.patchnote.visualswap.client.screen.overlay.OverlayManager;
import com.patchnote.visualswap.client.screen.overlay.TooltipOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/// Pixel-accurate preview of every HUD glyph over a switchable in-world background.
public final class GlyphPreview extends AbstractWidget
{
    private static final Identifier GRASS = background("grass");
    private static final Identifier SKY = background("sky");
    private static final int GRASS_TEXTURE_SIZE = 621;
    private static final int SKY_TEXTURE_SIZE = 555;

    private static final int GLYPH_SCALE = 1;
    private static final int TILE_HEIGHT = 16;
    private static final int TILE_WIDTH = 16;
    private static final int CONSECUTIVE_TILE_WIDTH = 28;
    private static final int TILE_GAP = 6;
    private static final int BORDER = 0xFF8A8A94;
    private static final int BORDER_HOVERED = 0xFFFFFFFF;
    private static final int BACKGROUND_ZOOM = 3;
    private static final int COUNTER_GAP = 3;
    private static final int COUNTER_BARS = 2;
    private static final List<Entry> ENTRIES = List.of(
            new Entry(SwapHitMasks.possible(), TILE_WIDTH, "gui.visual-swap.glyph.possible"),
            new Entry(SwapHitMasks.attacked(), TILE_WIDTH, "gui.visual-swap.glyph.attacked"),
            new Entry(SwapHitMasks.consecutive(), CONSECUTIVE_TILE_WIDTH, "gui.visual-swap.glyph.consecutive"),
            new Entry(SwapHitMasks.failed(), TILE_WIDTH, "gui.visual-swap.glyph.failed")
    );

    public static final int WIDTH = ENTRIES.stream().mapToInt(Entry::width).sum() + (ENTRIES.size() - 1) * TILE_GAP;
    public static final int HEIGHT = TILE_HEIGHT;

    private final Supplier<PresetType> preset;
    private final ToIntFunction<String> color;
    private final Predicate<String> selected;
    private final BiConsumer<String, Boolean> onSelect;
    private final BooleanSupplier sky;
    private final OverlayManager overlays;

    public GlyphPreview(Supplier<PresetType> preset, ToIntFunction<String> color, Predicate<String> selected,
                        BiConsumer<String, Boolean> onSelect, BooleanSupplier sky, OverlayManager overlays)
    {
        super(0, 0, WIDTH, HEIGHT, Component.translatable("gui.visual-swap.glyph_preview.narration"));
        this.preset = preset;
        this.color = color;
        this.selected = selected;
        this.onSelect = onSelect;
        this.sky = sky;
        this.overlays = overlays;
        this.active = preset.get().isCustom();
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a)
    {
        int x = getX();
        for (Entry entry : ENTRIES)
        {
            boolean hovered = mouseX >= x && mouseX < x + entry.width()
                    && mouseY >= getY() && mouseY < getY() + TILE_HEIGHT;
            drawTile(graphics, entry, x, getY(), hovered);
            if (hovered)
            {
                this.overlays.showTooltip(TooltipOverlay.of(
                        Minecraft.getInstance().font,
                        List.of(Component.translatable(entry.tooltipKey()))
                ).positionNear(mouseX, mouseY));
            }
            x += entry.width() + TILE_GAP;
        }
    }

    @Override
    public void onClick(@NonNull MouseButtonEvent event, boolean doubleClick)
    {
        if (!this.preset.get().isCustom()) return;
        Entry entry = entryAt(event.x());
        if (entry != null) this.onSelect.accept(entry.mask().name(), event.hasShiftDown());
    }

    private void drawTile(GuiGraphicsExtractor graphics, Entry entry, int x, int y, boolean hovered)
    {
        SwapHitMasks.Mask mask = entry.mask();
        boolean isSelected = this.preset.get().isCustom() && this.selected.test(mask.name());
        int inset = isSelected ? 1 : 0;
        graphics.fill(
                RenderPipelines.GUI,
                x - 1,
                y - 1,
                x + entry.width() + 1,
                y + TILE_HEIGHT + 1,
                this.active && hovered ? BORDER_HOVERED : BORDER
        );

        Identifier background = this.sky.getAsBoolean() ? SKY : GRASS;
        int textureSize = this.sky.getAsBoolean() ? SKY_TEXTURE_SIZE : GRASS_TEXTURE_SIZE;
        int innerWidth = entry.width() - 2 * inset;
        int innerHeight = TILE_HEIGHT - 2 * inset;
        int cropWidth = textureSize / BACKGROUND_ZOOM;
        int cropHeight = Math.max(1, cropWidth * innerHeight / innerWidth);
        if (cropHeight > textureSize / BACKGROUND_ZOOM)
        {
            cropHeight = textureSize / BACKGROUND_ZOOM;
            cropWidth = Math.max(1, cropHeight * innerWidth / innerHeight);
        }
        int cropX = (textureSize - cropWidth) / 2;
        int cropY = (textureSize - cropHeight) / 2;
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                background,
                x + inset,
                y + inset,
                (float) cropX,
                (float) cropY,
                innerWidth,
                innerHeight,
                cropWidth,
                cropHeight,
                textureSize,
                textureSize
        );

        int left = x + (entry.width() - mask.width() * GLYPH_SCALE) / 2;
        int top = y + (TILE_HEIGHT - mask.height() * GLYPH_SCALE) / 2;
        PresetType type = this.preset.get();
        int glyphColor = switch (type)
        {
            case VANILLA, PRACTICE -> type.getGlyphColor(mask.name());
            case CUSTOM -> this.color.applyAsInt(mask.name());
        };
        boolean invert = type.isVanilla() && !"failed".equals(mask.name());

        for (int row = 0; row < mask.height(); row++)
        {
            for (int col = 0; col < mask.width(); col++)
            {
                if (!mask.filled(col, row)) continue;
                int pixelX = left + col * GLYPH_SCALE;
                int pixelY = top + row * GLYPH_SCALE;
                graphics.fill(
                        invert ? RenderPipelines.GUI_INVERT : RenderPipelines.GUI,
                        pixelX,
                        pixelY,
                        pixelX + GLYPH_SCALE,
                        pixelY + GLYPH_SCALE,
                        glyphColor
                );
            }
        }

        if ("consecutive".equals(mask.name())) drawCounterBars(graphics, type, left, top, mask, glyphColor);
    }

    private static void drawCounterBars(GuiGraphicsExtractor graphics, PresetType type, int left, int top,
                                        SwapHitMasks.Mask mask, int color)
    {
        int rightStart = left + mask.width() * GLYPH_SCALE + COUNTER_GAP;
        int leftStart = left - COUNTER_GAP - GLYPH_SCALE;
        for (int i = 0; i < COUNTER_BARS; i++)
        {
            int right = rightStart + i * 2 * GLYPH_SCALE;
            int leftBar = leftStart - i * 2 * GLYPH_SCALE;
            graphics.fill(
                    type.isVanilla() ? RenderPipelines.GUI_INVERT : RenderPipelines.GUI,
                    right,
                    top,
                    right + GLYPH_SCALE,
                    top + mask.height() * GLYPH_SCALE,
                    color
            );
            graphics.fill(
                    type.isVanilla() ? RenderPipelines.GUI_INVERT : RenderPipelines.GUI,
                    leftBar,
                    top,
                    leftBar + GLYPH_SCALE,
                    top + mask.height() * GLYPH_SCALE,
                    color
            );
        }
    }

    private Entry entryAt(double mouseX)
    {
        int x = getX();
        for (Entry entry : ENTRIES)
        {
            if (mouseX >= x && mouseX < x + entry.width()) return entry;
            x += entry.width() + TILE_GAP;
        }
        return null;
    }

    private static Identifier background(String name)
    {
        return Identifier.fromNamespaceAndPath(
                VisualSwap.MOD_ID,
                "textures/gui/backgrounds/" + name + ".png"
        );
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput output) { }

    private record Entry(SwapHitMasks.Mask mask, int width, String tooltipKey) { }
}

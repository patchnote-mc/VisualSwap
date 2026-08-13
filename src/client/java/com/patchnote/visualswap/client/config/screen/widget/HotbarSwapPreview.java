package com.patchnote.visualswap.client.config.screen.widget;

import com.patchnote.visualswap.VisualSwap;
import com.patchnote.visualswap.client.config.models.FlashIntensity;
import com.patchnote.visualswap.client.config.models.FlashRule;
import com.patchnote.visualswap.client.config.models.PresetType;
import com.patchnote.visualswap.client.hud.click.ItemFlash;
import com.patchnote.visualswap.client.hud.click.ItemFlashPreview;
import com.patchnote.visualswap.client.screen.overlay.OverlayManager;
import com.patchnote.visualswap.client.screen.overlay.TooltipOverlay;
import com.patchnote.visualswap.client.utils.ItemIcons;
import com.patchnote.visualswap.client.utils.ItemRegex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/// Live, pixel-accurate preview of a swap+flash: two real hotbar slots (cropped from the vanilla {@code hud/hotbar}
/// sprite) rendered exactly as in-game —
/// - left slot: the swap's origin — From colour filled behind a normal item (as {@code SwapHotbarHighlight} does);
/// - right slot: the swap's destination — vanilla selection frame, To colour fill, and the item flashing through the
/// real {@code WHITE_SILHOUETTE} shader (via {@link ItemFlashPreview} + the {@code GuiItemFlashPreviewMixin} re-blit),
/// tinted with its rule's colour/intensity for the current preset.
///
/// The flashing item follows {@code flashRule} — the screen points it at whichever rule the user last edited (falling
/// back to the mace rule), so its id/colour/intensity are re-read live every frame.
public final class HotbarSwapPreview extends AbstractWidget
{
    // Vanilla hotbar geometry (Hud.extractItemHotbar): 182x22 texture; slot items 16px at u=3+20i, v=3; 4px dividers;
    // 3px outer edges; the 24x23 selection frame overhangs its slot by 1px on every side.
    private static final Identifier HOTBAR_SPRITE = Identifier.withDefaultNamespace("hud/hotbar");
    private static final Identifier HOTBAR_SELECTION_SPRITE = Identifier.withDefaultNamespace("hud/hotbar_selection");
    private static final Identifier GRASS_BACKGROUND = Identifier.fromNamespaceAndPath(
            VisualSwap.MOD_ID,
            "textures/gui/backgrounds/grass.png"
    );
    private static final int GRASS_TEXTURE_SIZE = 621;
    private static final int BACKGROUND_ZOOM = 3;
    private static final int TEX_W = 182;
    private static final int TEX_H = 22;
    private static final int BAR_H = 22;
    private static final int LEFT_CROP_W = 39;    // left edge (3) + item 0 (16) + divider (4) + item 1 (16)
    private static final int RIGHT_EDGE_U = 179;  // the bar's true right-edge columns
    private static final int RIGHT_EDGE_W = 3;
    private static final int BAR_W = LEFT_CROP_W + RIGHT_EDGE_W;  // a seamless two-slot bar
    private static final int PAD = 1;             // room for the selection frame's overhang
    public static final int WIDTH = PAD + BAR_W + PAD;
    private static final int ITEM_INSET = 3;
    private static final int ITEM_SIZE = 16;
    private static final int SLOT_STRIDE = 20;
    private static final int SEL_SIZE_W = 24;
    private static final int SEL_SIZE_H = 23;
    public static final int HEIGHT = PAD + BAR_H;

    private static final String DEFAULT_FLASH_ITEM = "minecraft:mace";

    private static final int TITLE = 0xFFFFFFFF;
    private static final int BODY = 0xFFA8A8B2;
    // Each tooltip is a white title line + a grey body whose value may carry \n line breaks (split by TooltipOverlay).
    private static final List<Component> ORIGIN_TOOLTIP = List.of(
            Component.translatable("gui.visual-swap.preview.origin.title").withColor(TITLE),
            Component.translatable("gui.visual-swap.preview.origin.body").withColor(BODY)
    );
    private static final List<Component> DESTINATION_TOOLTIP = List.of(
            Component.translatable("gui.visual-swap.preview.destination.title").withColor(TITLE),
            Component.translatable("gui.visual-swap.preview.destination.body").withColor(BODY)
    );

    private final IntSupplier fromColor;
    private final IntSupplier toColor;
    private final Supplier<PresetType> preset;
    private final Supplier<@Nullable FlashRule> flashRule;
    private final OverlayManager overlays;

    private final ItemStack fromItem;
    private @Nullable FlashRule resolvedFlashRule;
    private @Nullable String resolvedSelector;
    private ItemStack flashItem = ItemStack.EMPTY;

    public HotbarSwapPreview(IntSupplier fromColor, IntSupplier toColor, Supplier<PresetType> preset,
                             Supplier<@Nullable FlashRule> flashRule, OverlayManager overlays)
    {
        super(0, 0, WIDTH, HEIGHT, Component.empty());
        this.active = false;  // decorative
        this.fromColor = fromColor;
        this.toColor = toColor;
        this.preset = preset;
        this.flashRule = flashRule;
        this.overlays = overlays;
        this.fromItem = ItemIcons.stackFor(Items.NETHERITE_SWORD);
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a)
    {
        int bx = getX() + PAD;
        int by = getY() + PAD;

        // the two-slot bar, stitched from the vanilla sprite so both edges are the real border art
        g.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE, TEX_W, TEX_H, 0, 0, bx, by, LEFT_CROP_W, BAR_H);
        g.blitSprite(
                RenderPipelines.GUI_TEXTURED, HOTBAR_SPRITE, TEX_W, TEX_H, RIGHT_EDGE_U, 0,
                bx + LEFT_CROP_W, by, RIGHT_EDGE_W, BAR_H
        );

        // the swapped-to slot is the selected one
        g.blitSprite(
                RenderPipelines.GUI_TEXTURED, HOTBAR_SELECTION_SPRITE, bx - 1 + SLOT_STRIDE, by - 1,
                SEL_SIZE_W, SEL_SIZE_H
        );

        // From/To fills behind the items — the same call SwapHotbarHighlight.fillSlot makes in-game
        int fromX = bx + ITEM_INSET;
        int toX = fromX + SLOT_STRIDE;
        int itemY = by + ITEM_INSET;
        drawGrassBackground(g, fromX, itemY);
        drawGrassBackground(g, toX, itemY);
        g.fill(RenderPipelines.GUI, fromX, itemY, fromX + ITEM_SIZE, itemY + ITEM_SIZE, this.fromColor.getAsInt());
        g.fill(RenderPipelines.GUI, toX, itemY, toX + ITEM_SIZE, itemY + ITEM_SIZE, this.toColor.getAsInt());

        if (!this.fromItem.isEmpty()) g.item(this.fromItem, fromX, itemY);

        refreshFlashItem();
        ItemFlashPreview.clear();
        if (!this.flashItem.isEmpty())
        {
            g.item(this.flashItem, toX, itemY);
            ItemFlashPreview.register(toX, itemY, flashTint());
        }

        slotTooltip(ORIGIN_TOOLTIP, fromX, itemY, mouseX, mouseY);
        slotTooltip(DESTINATION_TOOLTIP, toX, itemY, mouseX, mouseY);
    }

    private static void drawGrassBackground(GuiGraphicsExtractor graphics, int x, int y)
    {
        int cropSize = GRASS_TEXTURE_SIZE / BACKGROUND_ZOOM;
        int cropOffset = (GRASS_TEXTURE_SIZE - cropSize) / 2;
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                GRASS_BACKGROUND,
                x,
                y,
                (float) cropOffset,
                (float) cropOffset,
                ITEM_SIZE,
                ITEM_SIZE,
                cropSize,
                cropSize,
                GRASS_TEXTURE_SIZE,
                GRASS_TEXTURE_SIZE
        );
    }

    /// Explain what the hovered slot represents (the preview is illustrative, so the tooltip is guidance, not an
    /// item).
    private void slotTooltip(List<Component> lines, int x, int y, int mouseX, int mouseY)
    {
        if (mouseX < x || mouseX >= x + ITEM_SIZE || mouseY < y || mouseY >= y + ITEM_SIZE) return;
        this.overlays.showTooltip(
                TooltipOverlay.of(Minecraft.getInstance().font, lines).positionNear(mouseX, mouseY));
    }

    /// Re-resolve the flashing item when the followed rule (or its selector) changes. The selector is a regex, so use
    /// the same representative first match as its rule row instead of treating the selector text as a literal id. While
    /// the selector is mid-edit and invalid, the last valid item stays on screen.
    private void refreshFlashItem()
    {
        FlashRule rule = this.flashRule.get();
        String selector = (rule != null) ? rule.item() : null;
        if (rule == this.resolvedFlashRule && Objects.equals(selector, this.resolvedSelector)) return;

        this.resolvedFlashRule = rule;
        this.resolvedSelector = selector;

        Identifier firstMatch = (rule != null) ? ItemRegex.summarize(rule.pattern()).first() : null;
        Item item = (firstMatch != null)
                    ? ItemIcons.resolveItem(firstMatch.toString())
                    : Items.AIR;
        if (item != Items.AIR) this.flashItem = ItemIcons.stackFor(item);
        else if (this.flashItem.isEmpty())
            this.flashItem = ItemIcons.stackFor(ItemIcons.resolveItem(DEFAULT_FLASH_ITEM));
    }

    /// The packed tint the in-game flash would use: the rule's colour for the current preset + its intensity's shade
    /// exponent (mirrors {@code ItemFlash.calculateTintFor}).
    private int flashTint()
    {
        PresetType type = this.preset.get();
        FlashRule rule = this.flashRule.get();
        if (rule == null) return ItemFlash.packTint(type.getFlashTint(), FlashIntensity.HIGH.getShadeExponent());

        FlashIntensity intensity = (rule.intensity() != null) ? rule.intensity() : FlashIntensity.HIGH;
        return ItemFlash.packTint(rule.colorFor(type), intensity.getShadeExponent());
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput output) { }
}

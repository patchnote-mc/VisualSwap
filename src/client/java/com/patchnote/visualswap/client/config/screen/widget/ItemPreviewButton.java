package com.patchnote.visualswap.client.config.screen.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

import static com.patchnote.visualswap.client.config.screen.widget.FlashRulesList.*;

/// A rule row's item column: a live preview of the first item the rule's regex selector matches (or an empty-slot
/// placeholder when the pattern is blank/invalid), with a small {@code ×N} badge when the selector matches more than
/// one item. Clicking it opens the regex preview modal for the rule. A hover ring hints at the click affordance.
final class ItemPreviewButton extends AbstractWidget
{
    private static final int HOVER_BG = 0x33FFFFFF;
    private static final int BADGE_ARGB = 0xFFDDDDE2;

    private final Supplier<ItemStack> stack;
    private final IntSupplier matchCount;
    private final Runnable onOpen;
    private final Font font = Minecraft.getInstance().font;

    ItemPreviewButton(Supplier<ItemStack> stack, IntSupplier matchCount, Runnable onOpen)
    {
        super(0, 0, ICON, ICON, Component.translatable("gui.visual-swap.rules.preview_button"));
        this.stack = stack;
        this.matchCount = matchCount;
        this.onOpen = onOpen;
    }

    @Override
    public void onClick(@NonNull MouseButtonEvent event, boolean doubleClick)
    {
        this.onOpen.run();   // AbstractWidget only routes clicks here while active
    }

    @Override
    protected void renderWidget(@NonNull GuiGraphics g, int mouseX, int mouseY, float a)
    {
        int x = getX();
        int y = getY();
        if (isHovered()) g.fill(x - 1, y - 1, x + ICON + 1, y + ICON + 1, HOVER_BG);

        ItemStack item = this.stack.get();
        if (item.isEmpty())
        {
            g.fill(x, y, x + ICON, y + ICON, SLOT_BORDER);
            g.fill(x + 1, y + 1, x + ICON - 1, y + ICON - 1, SLOT_BG);
        }
        else
        {
            g.renderItem(item, x, y);
        }

        int count = this.matchCount.getAsInt();
        if (count > 1)
        {
            String badge = "×" + count;   // ×N
            g.drawString(
                    this.font,
                    badge,
                    x + ICON - this.font.width(badge),
                    y + ICON - this.font.lineHeight,
                    BADGE_ARGB,
                    true
            );
        }
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput output) { }
}

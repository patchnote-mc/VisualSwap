package com.patchnote.visualswap.client.config.screen.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.function.Consumer;

import static com.patchnote.visualswap.client.config.screen.widget.FlashRulesList.*;

/// The fixed table header above the scrolling rules: a search box in the Item column (with a magnifier where the item
/// icons sit), captions over the Flash/Intensity columns, and the Add / Clear / Reset icon buttons on the right —
/// aligned above each row's own duplicate/delete icons. It reuses {@link FlashRulesList}'s right-anchored column maths
/// so everything lines up with the rows below. A container widget so it hosts and routes to its own children.
public final class RuleColumnsHeader extends AbstractContainerWidget
{
    public static final int HEIGHT = 20;

    private static final int CONTENT_PAD = 2;   // matches FlashRuleRow
    private static final int CAPTION_ARGB = 0xFF97979E;
    private static final int SEARCH_ICON_ARGB = 0xFF8A8A90;
    private static final int ACTION_W = 18;     // square icon buttons

    private final Font font = Minecraft.getInstance().font;

    private final EditBox searchBox;
    private final IconButton addButton;
    private final IconButton clearButton;
    private final IconButton resetButton;
    private final List<GuiEventListener> children;

    public RuleColumnsHeader(int width, String initialSearch, Consumer<String> onSearch,
                             Runnable onAdd, Runnable onClear, Runnable onReset,
                             boolean clearEnabled, boolean resetEnabled)
    {
        super(0, 0, width, HEIGHT, Component.empty());

        this.searchBox = new EditBox(this.font, 0, 0, 100, WIDGET_HEIGHT, Component.literal("Search rules"));
        this.searchBox.setHint(Component.literal("Search item id…"));
        this.searchBox.setValue(initialSearch);   // set BEFORE the responder so it doesn't self-trigger a rebuild
        this.searchBox.setResponder(onSearch);
        this.searchBox.moveCursorToEnd(false);

        this.addButton = new IconButton(ACTION_W, Icons.ADD, Component.literal("Add a new rule"), onAdd);
        this.clearButton = new IconButton(ACTION_W, Icons.CLEAR, Component.literal("Clear all rules"), onClear);
        this.resetButton = new IconButton(ACTION_W, Icons.RESET, Component.literal("Reset rules to defaults"), onReset);
        this.clearButton.active = clearEnabled;
        this.resetButton.active = resetEnabled;

        this.children = List.of(this.searchBox, this.addButton, this.clearButton, this.resetButton);
    }

    /// Give keyboard focus to the search box (the screen calls this after a filter-triggered rebuild so typing isn't
    /// interrupted). The screen must also make this header its own focused child.
    public void focusSearch()
    {
        setFocused(this.searchBox);
        this.searchBox.setFocused(true);
        this.searchBox.moveCursorToEnd(false);
    }

    @Override
    public @NonNull List<? extends GuiEventListener> children() { return this.children; }

    @Override
    protected int contentHeight() { return getHeight(); }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a)
    {
        int left = getX() + CONTENT_PAD;
        int right = getX() + getWidth() - CONTENT_PAD;
        int midY = getY() + getHeight() / 2;
        int widgetY = midY - WIDGET_HEIGHT / 2;

        // right-anchored columns (mirror FlashRuleRow): trigger | intensity | colour | up | down | duplicate | delete
        int deleteX = right - DELETE_WIDTH;
        int duplicateX = deleteX - ACTION_GAP - DUPLICATE_WIDTH;
        int downX = duplicateX - ACTION_GAP - MOVE_WIDTH;
        int upX = downX - ACTION_GAP - MOVE_WIDTH;
        int colorX = upX - GAP - COLOR_SWATCH;
        int intensityX = colorX - GAP - INTENSITY_WIDTH;
        int onX = intensityX - GAP - ON_WIDTH;

        // captions over the cycler columns
        g.centeredText(this.font, "Flash", onX + ON_WIDTH / 2, midY - this.font.lineHeight / 2, CAPTION_ARGB);
        g.centeredText(this.font, "Intensity", intensityX + INTENSITY_WIDTH / 2, midY - this.font.lineHeight / 2,
                       CAPTION_ARGB);

        // action icons, right-anchored as a group above the colour/duplicate/delete columns
        this.resetButton.setPosition(right - ACTION_W, widgetY);
        this.clearButton.setPosition(this.resetButton.getX() - ACTION_GAP - ACTION_W, widgetY);
        this.addButton.setPosition(this.clearButton.getX() - ACTION_GAP - ACTION_W, widgetY);

        // search: a magnifier where the item icon sits, then the box across the item column
        int iconY = midY - ICON / 2;
        Icons.blit(g, Icons.SEARCH, left, iconY, ICON, SEARCH_ICON_ARGB);
        int boxX = left + ICON + GAP;
        this.searchBox.setX(boxX);
        this.searchBox.setY(widgetY);
        this.searchBox.setWidth(Math.max(20, (onX - GAP) - boxX));

        this.searchBox.extractRenderState(g, mouseX, mouseY, a);
        this.addButton.extractRenderState(g, mouseX, mouseY, a);
        this.clearButton.extractRenderState(g, mouseX, mouseY, a);
        this.resetButton.extractRenderState(g, mouseX, mouseY, a);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput output) { }
}

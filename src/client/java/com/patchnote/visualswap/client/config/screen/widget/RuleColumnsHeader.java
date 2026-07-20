package com.patchnote.visualswap.client.config.screen.widget;

import com.patchnote.visualswap.client.screen.overlay.ColorPickerOverlay;
import com.patchnote.visualswap.client.screen.overlay.OverlayManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import static com.patchnote.visualswap.client.config.screen.widget.FlashRulesList.*;

/// The fixed table header above the scrolling rules: a search box in the Item column (with a magnifier where the item
/// icons sit), a caption over the Trigger column, a bulk tint swatch in the colour column (click to set every rule's
/// colour at once), and the Add / Clear / Reset icon buttons on the right — aligned above each row's own
/// duplicate/delete icons. It reuses {@link FlashRulesList}'s right-anchored column maths so everything lines up with
/// the rows below. A container widget so it hosts and routes to its own children.
public final class RuleColumnsHeader extends AbstractContainerWidget
{
    public static final int HEIGHT = 20;

    private static final int CONTENT_PAD = 2;   // matches FlashRuleRow
    private static final int CAPTION_ARGB = 0xFF97979E;
    private static final int SEARCH_ICON_ARGB = 0xFF8A8A90;
    private static final int ACTION_W = 18;     // square icon buttons

    private final Font font = Minecraft.getInstance().font;

    private final EditBox searchBox;
    private final ColorSwatch colorSwatch;
    private final IconButton addButton;
    private final IconButton clearButton;
    private final IconButton resetButton;
    private final List<GuiEventListener> children;

    private final OverlayManager overlays;
    private final IntSupplier tintColor;
    private final IntConsumer onSetAllColors;

    public RuleColumnsHeader(int width, String initialSearch, Consumer<String> onSearch, Runnable onAdd,
                             Runnable onClear, Runnable onReset, boolean clearEnabled, boolean resetEnabled,
                             IntSupplier tintColor, boolean colorEditable, Runnable onColorSwatchPressed,
                             IntConsumer onSetAllColors, OverlayManager overlays)
    {
        super(0, 0, width, HEIGHT, Component.empty());

        this.overlays = overlays;
        this.tintColor = tintColor;
        this.onSetAllColors = onSetAllColors;

        this.searchBox = new EditBox(this.font, 0, 0, 100, WIDGET_HEIGHT, Component.literal("Search rules"));
        this.searchBox.setHint(Component.literal("Search item id…"));
        this.searchBox.setValue(initialSearch);   // set BEFORE the responder so it doesn't self-trigger a rebuild
        this.searchBox.setResponder(onSearch);
        this.searchBox.moveCursorToEnd(false);

        // bulk tint swatch — mirrors each row's colour column, click opens the picker to recolour every rule at once
        this.colorSwatch = new ColorSwatch(COLOR_SWATCH, () -> 0xFF000000 | (tintColor.getAsInt() & 0xFFFFFF));
        this.colorSwatch.setOnPress(onColorSwatchPressed);
        this.colorSwatch.setClickable(colorEditable);
        if (colorEditable) this.colorSwatch.setTooltip(Tooltip.create(Component.literal("Set the tint of all rules")));

        this.addButton = new IconButton(ACTION_W, Icons.ADD, Component.literal("Add a new rule"), onAdd);
        this.clearButton = new IconButton(ACTION_W, Icons.CLEAR, Component.literal("Clear all rules"), onClear);
        this.resetButton = new IconButton(ACTION_W, Icons.RESET, Component.literal("Reset rules to defaults"), onReset);
        this.clearButton.active = clearEnabled;
        this.resetButton.active = resetEnabled;

        this.children = List.of(this.searchBox, this.colorSwatch, this.addButton, this.clearButton, this.resetButton);
    }

    /// Open the bulk-tint picker anchored under the colour-column swatch — the screen calls this from its next init
    /// (after the "set all colours?" confirm), so it survives the modal-return rebuild that clears open overlays. The
    /// picker hides alpha (rule tints are RGB-only) and pushes each picked colour live onto every rule.
    public void openColorPicker()
    {
        int swatchX = colorSwatchX();
        int swatchY = getY() + getHeight() / 2 - COLOR_SWATCH / 2;
        ColorPickerOverlay picker = new ColorPickerOverlay(
                this.tintColor.getAsInt(),
                                                           false,
                                                           argb -> this.onSetAllColors.accept(
                                                                   0xFF000000 | (argb & 0xFFFFFF))
        );
        picker.position(swatchX - 8, swatchY + COLOR_SWATCH + 4);
        this.overlays.open(picker);
    }

    /// Grey out the whole rules toolbar — the screen calls this when the Item Flash effect is off (its rules can't be
    /// edited while disabled). Disables the search box, bulk swatch and Add/Clear/Reset icons, and puts {@code tip} on
    /// the action icons so hovering explains why. One-way: the header is rebuilt fresh each init, so there is no
    /// restore.
    public void disableWith(Tooltip tip)
    {
        this.searchBox.setEditable(false);
        this.colorSwatch.setClickable(false);
        this.addButton.active = false;
        this.clearButton.active = false;
        this.resetButton.active = false;
        this.addButton.setTooltip(tip);
        this.clearButton.setTooltip(tip);
        this.resetButton.setTooltip(tip);
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

        // right-anchored columns (mirror FlashRuleRow): trigger | colour | config | up | down | duplicate | delete
        int configX = configX();
        int colorX = configX - GAP - COLOR_SWATCH;
        int onX = colorX - GAP - ON_WIDTH;

        // caption over the trigger column; the config column's gear icons are self-describing
        g.centeredText(this.font, "Trigger", onX + ON_WIDTH / 2, midY - this.font.lineHeight / 2, CAPTION_ARGB);

        // bulk tint swatch, centred in the colour column above each row's own swatch
        this.colorSwatch.setPosition(colorX, midY - COLOR_SWATCH / 2);

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
        this.colorSwatch.extractRenderState(g, mouseX, mouseY, a);
        this.addButton.extractRenderState(g, mouseX, mouseY, a);
        this.clearButton.extractRenderState(g, mouseX, mouseY, a);
        this.resetButton.extractRenderState(g, mouseX, mouseY, a);
    }

    /// The X of the colour-column swatch, derived from the same right-anchored maths as the rows so it lines up above
    /// each {@code FlashRuleRow}'s swatch. Computed from the header's own geometry so it is valid right after layout
    /// (the swatch's rendered position is only set during extract, i.e. one frame behind).
    private int colorSwatchX()
    {
        return configX() - GAP - COLOR_SWATCH;
    }

    private int configX()
    {
        int right = getX() + getWidth() - CONTENT_PAD;
        int deleteX = right - DELETE_WIDTH;
        int duplicateX = deleteX - ACTION_GAP - DUPLICATE_WIDTH;
        int downX = duplicateX - ACTION_GAP - MOVE_WIDTH;
        int upX = downX - ACTION_GAP - MOVE_WIDTH;
        return upX - GAP - CONFIG_WIDTH;
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput output) { }
}

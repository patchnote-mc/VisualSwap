package com.patchnote.visualswap.client.config.screen.widget;

import com.patchnote.visualswap.client.config.models.FlashIntensity;
import com.patchnote.visualswap.client.config.models.FlashRule;
import com.patchnote.visualswap.client.config.models.FlashTrigger;
import com.patchnote.visualswap.client.config.models.PresetType;
import com.patchnote.visualswap.client.utils.ColorHelpers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Button.OnPress;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.NonNull;

import java.util.List;

import static com.patchnote.visualswap.client.config.screen.widget.FlashRulesList.*;

/// One rule's row: item id (with a live icon), flash-input and intensity cyclers, a tint colour (hex box + live
/// swatch), and a delete button. It is a self-contained container widget — it positions and renders its own child
/// widgets and routes events to them — so it can be stacked by a plain {@link net.minecraft.client.gui.layouts.Layout}
/// (and later scrolled by the page) instead of being an entry in a self-scrolling list.
public final class FlashRuleRow extends AbstractContainerWidget
{
    /// Horizontal inset of the row content from its own edges — kept in step with the screen's column headers.
    private static final int CONTENT_PAD = 2;

    private final FlashRulesList list;
    private final FlashRule rule;
    private final List<GuiEventListener> children;

    // widgets
    private final EditBox itemBox;
    private final CycleButton<FlashTrigger> onButton;
    private final CycleButton<FlashIntensity> intensityButton;
    private final ColorSwatch colorSwatch;
    private final EditBox colorBox;
    private final Button deleteButton;

    // state
    private boolean isValid;
    private ItemStack previewItem;
    private boolean refreshingColor;  // true while refreshColor programmatically rewrites the box (not a user edit)

    FlashRuleRow(FlashRulesList list, FlashRule rule)
    {
        super(0, 0, list.getRowWidth(), ROW_HEIGHT, Component.empty());
        this.list = list;
        rule.normalize();  // repair a rule carried over from an older config schema before any widget reads it
        this.rule = rule;
        Item item = resolveItem(rule.item());
        this.isValid = item != Items.AIR;
        this.previewItem = getItemStack(item);

        // widgets
        this.itemBox = createItemInput(rule);
        this.onButton = createTriggerSelector(rule);
        this.intensityButton = createIntensitySelector(rule);
        this.colorSwatch = new ColorSwatch(
                COLOR_SWATCH, SLOT_BORDER,
                () -> 0xFF000000 | (this.rule.colorFor(this.list.preset()) & 0xFFFFFF)
        );
        this.colorBox = createColorInput(rule);
        this.deleteButton = createDeleteButton();

        refreshItemColor();

        this.children = List.of(this.itemBox, this.onButton, this.intensityButton, this.colorBox, this.deleteButton);
    }

    /* WIDGETS */

    private @NonNull EditBox createItemInput(FlashRule rule)
    {
        EditBox input = new EditBox(
                Minecraft.getInstance().font, //
                0, 0, 100, WIDGET_HEIGHT, Component.literal("Item identifier")
        );
        input.setMaxLength(256);
        input.setHint(Component.literal("minecraft:item"));
        input.setValue(rule.item() == null ? "" : rule.item());
        input.setResponder(this::onItemEdited);
        input.moveCursorToStart(false);
        return input;
    }

    private @NonNull CycleButton<FlashTrigger> createTriggerSelector(FlashRule rule)
    {
        FlashTrigger initial = rule.flashesAt() != null ? rule.flashesAt() : FlashTrigger.BOTH;
        return CycleButton.builder(FlashTrigger::getNameComponent, initial)
                .withValues(FlashTrigger.values())
                .displayOnlyValue()
                .create(
                        0,
                        0,
                        ON_WIDTH,
                        WIDGET_HEIGHT,
                        Component.empty(),
                        (button, value) -> this.rule.setFlashesAt(value)
                );
    }

    private @NonNull CycleButton<FlashIntensity> createIntensitySelector(FlashRule rule)
    {
        FlashIntensity initial = rule.intensity() != null ? rule.intensity() : FlashIntensity.LOW;
        return CycleButton.builder(FlashIntensity::getNameComponent, initial)
                .withValues(FlashIntensity.values())
                .displayOnlyValue()
                .create(
                        0,
                        0,
                        INTENSITY_WIDTH,
                        WIDGET_HEIGHT,
                        Component.empty(),
                        (button, value) -> this.rule.setIntensity(value)
                );
    }

    private @NonNull EditBox createColorInput(FlashRule rule)
    {
        PresetType preset = this.list.preset();
        EditBox input = new EditBox(
                Minecraft.getInstance().font, //
                0, 0, COLOR_BOX_WIDTH, WIDGET_HEIGHT, Component.literal("Tint colour")
        );
        input.setMaxLength(9);
        input.setHint(Component.literal("RRGGBB"));
        input.setTextColorUneditable(TEXT_MUTED);
        input.setValue(ColorHelpers.formatRgbHex(rule.colorFor(preset)));
        input.setEditable(preset.isColorEditable());
        input.setResponder(this::onColorEdited);
        input.moveCursorToStart(false);
        return input;
    }

    private @NonNull Button createDeleteButton()
    {
        OnPress onPress = (Button b) -> this.list.removeRule(this);
        return Button.builder(Component.literal("✕"), onPress)
                .bounds(0, 0, DELETE_WIDTH, WIDGET_HEIGHT)
                .build();
    }

    /* GETTERS */

    public FlashRule getRule() { return this.rule; }

    /* OVERRIDES */

    @Override
    public @NonNull List<? extends GuiEventListener> children()
    {
        return this.children;
    }

    @Override
    protected int contentHeight() { return getHeight(); }  // == height, so there is nothing to scroll

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float a)
    {
        int left = getX() + CONTENT_PAD;
        int right = getX() + getWidth() - CONTENT_PAD;
        int midY = getY() + getHeight() / 2;
        int widgetY = midY - WIDGET_HEIGHT / 2;

        // item icon
        int iconY = midY - ICON / 2;
        if (this.previewItem.isEmpty())
        {
            g.fill(left, iconY, left + ICON, iconY + ICON, SLOT_BORDER);
            g.fill(left + 1, iconY + 1, left + ICON - 1, iconY + ICON - 1, SLOT_BG);
        }
        else
        {
            g.item(this.previewItem, left, iconY);
        }

        // right-anchored columns: trigger | intensity | colour | delete
        int deleteX = right - DELETE_WIDTH;
        int colorX = deleteX - GAP - COLOR_WIDTH;
        int intensityX = colorX - GAP - INTENSITY_WIDTH;
        int onX = intensityX - GAP - ON_WIDTH;

        this.deleteButton.setPosition(deleteX, widgetY);
        this.intensityButton.setPosition(intensityX, widgetY);
        this.onButton.setPosition(onX, widgetY);

        // colour swatch (live) + hex box
        int swatchY = midY - COLOR_SWATCH / 2;
        this.colorSwatch.setPosition(colorX, swatchY);
        this.colorBox.setPosition(colorX + COLOR_SWATCH + 4, widgetY);

        // item box fills the middle
        int boxX = left + ICON + GAP;
        int boxW = Math.max(20, onX - GAP - boxX);
        this.itemBox.setX(boxX);
        this.itemBox.setY(widgetY);
        this.itemBox.setWidth(boxW);

        this.itemBox.extractRenderState(g, mouseX, mouseY, a);
        this.onButton.extractRenderState(g, mouseX, mouseY, a);
        this.intensityButton.extractRenderState(g, mouseX, mouseY, a);
        this.colorSwatch.extractRenderState(g, mouseX, mouseY, a);
        this.colorBox.extractRenderState(g, mouseX, mouseY, a);
        this.deleteButton.extractRenderState(g, mouseX, mouseY, a);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput output) { }

    /* HELPERS */

    private void onItemEdited(String value)
    {
        Item item = resolveItem(value);
        this.rule.setItem(value);
        this.isValid = item != Items.AIR;
        this.previewItem = getItemStack(item);
        refreshItemColor();
    }

    private void onColorEdited(String value)
    {
        Integer color = ColorHelpers.parseHexColor(value);
        this.colorBox.setTextColor(color != null ? TEXT_VALID : TEXT_INVALID);
        // setColorFor no-ops for non-editable presets, so a programmatic setValue on preset switch can't corrupt them.
        if (color != null) this.rule.setColorFor(this.list.preset(), color);
        if (!this.refreshingColor) this.list.notifyColorEdited(this.rule);
    }

    /// Point the colour column at {@code preset}: show that preset's stored colour and only allow edits under Custom.
    /// The swatch tracks the list's preset live, so it needs no explicit refresh.
    void refreshColor(PresetType preset)
    {
        this.refreshingColor = true;
        this.colorBox.setEditable(preset.isColorEditable());
        this.colorBox.setValue(ColorHelpers.formatRgbHex(this.rule.colorFor(preset)));
        this.refreshingColor = false;
    }

    private void refreshItemColor() { this.itemBox.setTextColor(this.isValid ? TEXT_VALID : TEXT_INVALID); }

    static Item resolveItem(String id)
    {
        Identifier ident = Identifier.tryParse(id == null ? "" : id.trim());
        if (ident == null) return Items.AIR;
        return BuiltInRegistries.ITEM.getValue(ident);
    }

    static ItemStack getItemStack(Item item)
    {
        if (item == Items.AIR) return ItemStack.EMPTY;

        // if item model already loaded
        if (BuiltInRegistries.ITEM.wrapAsHolder(item)
                .areComponentsBound()) return new ItemStack(item);

        // load item model
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        DataComponentMap components = DataComponentMap.builder()
                .set(DataComponents.ITEM_MODEL, id)
                .build();
        return new ItemStack(Holder.direct(item, components));
    }
}

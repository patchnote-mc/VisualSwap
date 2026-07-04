package com.patchnote.visualswap.client.config.screen;

import com.patchnote.visualswap.client.config.ModConfig;
import com.patchnote.visualswap.client.config.ModConfig.FlashOn;
import com.patchnote.visualswap.client.config.ModConfig.FlashOpacity;
import com.patchnote.visualswap.client.config.ModConfig.FlashRule;
import com.patchnote.visualswap.client.config.ModConfig.IndicatorType;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;

/// Hand-built config screen for Visual Swap. Top: the swap indicator mode (a Vanilla / Practice cycle button) and the
/// Vanilla-mode size slider. Below: a scrollable table of click-flash rules, one row each. All edits land on working
/// copies and are written to {@link ModConfig} only when "Done" commits.
///
/// Everything is stock Minecraft widgets ({@link Button}, {@link CycleButton}, {@link EditBox}) except the two that
/// have no concrete vanilla form: {@link SizeSlider} (a slider needs {@link AbstractSliderButton}) and
/// {@link FlashRulesList} (a scrollable table needs {@link ContainerObjectSelectionList}). Both are nested below.
public final class VisualSwapConfigScreen extends Screen
{
    private static final int TITLE_Y = 13;
    private static final int CHIP_H = 20;
    private static final int FOOTER_H = 40;

    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int LABEL_COLOR = 0xFFB9B9C0;
    private static final int MUTED_COLOR = 0xFF97979E;

    private final Screen parent;

    private IndicatorType workingIndicator;
    private double workingSize;
    private final List<FlashRule> ruleSeed;

    private SizeSlider slider;
    private FlashRulesList list;

    // Geometry shared between the list rows and the drawn column headers, recomputed each init().
    private int rowLeft;
    private int rowWidth;
    private int headerY;

    public VisualSwapConfigScreen(Screen parent)
    {
        super(Component.literal("Visual Swap"));
        this.parent = parent;

        ModConfig cfg = ModConfig.get();
        this.workingIndicator = cfg.indicatorType;
        this.workingSize = cfg.vanillaSizeMultiplier;
        this.ruleSeed = cfg.clickFlashRules;
    }

    @Override
    protected void init()
    {
        // Preserve in-progress rule edits across a window resize (init runs again).
        List<FlashRule> seed = (this.list != null) ? this.list.toRules() : this.ruleSeed;

        int cx = this.width / 2;

        // --- indicator mode ---
        int groupW = 192;
        int gx = cx - groupW / 2;
        int chipsY = TITLE_Y + 16;

        addRenderableWidget(CycleButton.<IndicatorType>builder(
                        type -> Component.literal(type == IndicatorType.VANILLA ? "Vanilla" : "Practice"),
                        this.workingIndicator)
                .withValues(IndicatorType.VANILLA, IndicatorType.PRACTICE)
                .create(gx, chipsY, groupW, CHIP_H, Component.literal("Mode"),
                        (button, value) -> setIndicator(value)));

        // --- size slider ---
        int sliderY = chipsY + CHIP_H + 6;
        this.slider = new SizeSlider(gx, sliderY, groupW, CHIP_H, this.workingSize, v -> this.workingSize = v);
        this.slider.active = this.workingIndicator == IndicatorType.VANILLA;
        addRenderableWidget(this.slider);

        // --- rules table ---
        this.rowWidth = Math.min(360, this.width - 60);
        this.rowLeft = cx - this.rowWidth / 2;
        int listTop = sliderY + CHIP_H + 24;
        this.headerY = listTop - 11;
        int listBottom = this.height - FOOTER_H;
        this.list = new FlashRulesList(this.minecraft, this.width, listBottom - listTop, listTop, this.rowWidth, seed);
        addRenderableWidget(this.list);

        // --- footer ---
        int footerY = this.height - 28;
        int addW = 96;
        int btnW = 90;
        int btnGap = 8;

        addRenderableWidget(Button.builder(Component.literal("+ Add rule"), b -> this.list.addRule())
                .bounds(this.rowLeft, footerY, addW, CHIP_H).build());

        int rightEdge = this.rowLeft + this.rowWidth;
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> commitAndClose())
                .bounds(rightEdge - btnW, footerY, btnW, CHIP_H).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(rightEdge - btnW * 2 - btnGap, footerY, btnW, CHIP_H).build());
    }

    private void setIndicator(IndicatorType type)
    {
        this.workingIndicator = type;
        if (this.slider != null) this.slider.active = type == IndicatorType.VANILLA;
    }

    private void commitAndClose()
    {
        ModConfig cfg = ModConfig.get();
        cfg.indicatorType = this.workingIndicator;
        cfg.vanillaSizeMultiplier = (float) this.workingSize;
        cfg.clickFlashRules = this.list.toRules();
        AutoConfig.getConfigHolder(ModConfig.class).save();
        this.minecraft.setScreenAndShow(this.parent);
    }

    @Override
    public void onClose()
    {
        this.minecraft.setScreenAndShow(this.parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a)
    {
        super.extractRenderState(g, mouseX, mouseY, a);

        g.centeredText(this.font, getTitle(), this.width / 2, TITLE_Y, TITLE_COLOR);

        // Section caption + column headers, aligned to the row columns.
        int contentX = this.rowLeft + 2;
        int contentRight = this.rowLeft + this.rowWidth - 2;
        int deleteX = contentRight - 18;
        int opacityX = deleteX - 6 - 46;
        int onX = opacityX - 6 - 58;
        int boxX = contentX + 16 + 6;

        g.text(this.font, Component.literal("ITEM"), boxX, this.headerY, MUTED_COLOR);
        g.centeredText(this.font, Component.literal("FLASH ON"), onX + 29, this.headerY, MUTED_COLOR);
        g.centeredText(this.font, Component.literal("OPACITY"), opacityX + 23, this.headerY, MUTED_COLOR);
    }

    // ------------------------------------------------------------------------------------------------------------
    // The two widgets with no concrete vanilla form.
    // ------------------------------------------------------------------------------------------------------------

    /// Vanilla-style slider bound to {@code ModConfig.vanillaSizeMultiplier}, snapped to 0.05 steps over [MIN, MAX].
    private static final class SizeSlider extends AbstractSliderButton
    {
        private static final double MIN = 0.10;
        private static final double MAX = 2.00;
        private static final double STEP = 0.05;

        private final DoubleConsumer sink;

        SizeSlider(int x, int y, int width, int height, double initial, DoubleConsumer sink)
        {
            super(x, y, width, height, Component.empty(), toFraction(initial));
            this.sink = sink;
            updateMessage();
        }

        private static double toFraction(double size)
        {
            double clamped = Math.clamp(size, MIN, MAX);
            return (clamped - MIN) / (MAX - MIN);
        }

        private double size()
        {
            double raw = MIN + this.value * (MAX - MIN);
            return Math.round(raw / STEP) * STEP;
        }

        @Override
        protected void updateMessage()
        {
            setMessage(Component.literal(String.format("Vanilla size: %.2f×", size())));
        }

        @Override
        protected void applyValue()
        {
            this.sink.accept(size());
        }
    }

    /// Scrollable table of {@link FlashRule}s — one row per rule. Each row edits the item id (with a live icon), and
    /// cycles its flash input and opacity; a per-row delete button removes it. Operates on independent working copies
    /// of the rules so nothing touches {@link ModConfig} until the screen commits.
    private static final class FlashRulesList extends ContainerObjectSelectionList<FlashRulesList.RuleEntry>
    {
        private static final int ROW_HEIGHT = 26;
        private static final int WIDGET_HEIGHT = 18;
        private static final int ICON = 16;
        private static final int GAP = 6;

        private static final int ON_WIDTH = 58;
        private static final int OPACITY_WIDTH = 46;
        private static final int DELETE_WIDTH = 18;

        private static final int TEXT_VALID = 0xFFE0E0E0;
        private static final int TEXT_INVALID = 0xFFFF5555;

        private static final int SLOT_BG = 0xFF26262B;
        private static final int SLOT_BORDER = 0xFF4A4A52;

        private final int rowWidth;

        FlashRulesList(Minecraft minecraft, int width, int height, int y, int rowWidth, List<FlashRule> rules)
        {
            super(minecraft, width, height, y, ROW_HEIGHT);
            this.rowWidth = rowWidth;
            for (FlashRule rule : rules)
            {
                addEntry(new RuleEntry(new FlashRule(rule.item, rule.flashesAt, rule.opacity)));
            }
        }

        @Override
        public int getRowWidth()
        {
            return this.rowWidth;
        }

        @Override
        protected int scrollBarX()
        {
            return getRowRight() + 10;
        }

        /// Append a fresh, blank rule and scroll it into view.
        void addRule()
        {
            RuleEntry entry = new RuleEntry(new FlashRule("minecraft:", FlashOn.ATTACK, FlashOpacity.HIGH));
            addEntry(entry);
            scrollToEntry(entry);
            setSelected(entry);
        }

        /// The current rules, in row order — the value the screen commits on save.
        ArrayList<FlashRule> toRules()
        {
            ArrayList<FlashRule> out = new ArrayList<>();
            for (RuleEntry entry : children()) out.add(entry.rule);
            return out;
        }

        private static Item resolveItem(String id)
        {
            Identifier ident = Identifier.tryParse(id == null ? "" : id.trim());
            if (ident == null) return Items.AIR;
            return BuiltInRegistries.ITEM.getValue(ident);
        }

        /// A display stack for the item, or EMPTY when the id is unknown.
        ///
        /// Item data-components — including the model reference {@code ITEM_MODEL} the renderer needs — only bind on a
        /// world/data-pack load. When a world has been loaded this session they stay bound and a normal stack works.
        /// At a fresh title menu (ModMenu opened before any world) they are unbound, so we build a minimal stack from
        /// a direct holder whose {@code ITEM_MODEL} points at the item id — the vanilla default
        /// ({@code Item.Properties.model = ResourceKey::identifier}) — letting the icon render from the already-loaded
        /// assets instead of falling back to an empty slot (or crashing on the unbound holder).
        private static ItemStack previewFor(Item item)
        {
            if (item == Items.AIR) return ItemStack.EMPTY;
            if (item.builtInRegistryHolder().areComponentsBound()) return new ItemStack(item);

            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            DataComponentMap components = DataComponentMap.builder().set(DataComponents.ITEM_MODEL, id).build();
            return new ItemStack(Holder.direct(item, components));
        }

        private static Component labelOf(FlashOn on)
        {
            return Component.literal(switch (on)
                                     {
                                         case ATTACK -> "Attack";
                                         case USE -> "Use";
                                         case BOTH -> "Both";
                                     });
        }

        private static Component labelOf(FlashOpacity opacity)
        {
            return Component.literal(opacity == FlashOpacity.LOW ? "Low" : "High");
        }

        final class RuleEntry extends ContainerObjectSelectionList.Entry<RuleEntry>
        {
            private final FlashRule rule;
            private final EditBox itemBox;
            private final CycleButton<FlashOn> onButton;
            private final CycleButton<FlashOpacity> opacityButton;
            private final Button deleteButton;
            private final List<GuiEventListener> children;

            private boolean valid;
            private ItemStack preview;

            RuleEntry(FlashRule rule)
            {
                this.rule = rule;
                Item item = resolveItem(rule.item);
                this.valid = item != Items.AIR;
                this.preview = previewFor(item);

                this.itemBox = new EditBox(
                        Minecraft.getInstance().font,
                        0,
                        0,
                        100,
                        WIDGET_HEIGHT,
                        Component.literal("Item identifier")
                );
                this.itemBox.setMaxLength(256);
                this.itemBox.setHint(Component.literal("minecraft:item"));
                this.itemBox.setValue(rule.item == null ? "" : rule.item);
                this.itemBox.setResponder(this::onItemEdited);
                // setValue parks the cursor at the end and scrolls the view there (against the temporary ctor
                // width); snap back to the start so the id renders from its beginning once the row is laid out.
                this.itemBox.moveCursorToStart(false);
                refreshItemColor();

                this.onButton = CycleButton.<FlashOn>builder(
                                FlashRulesList::labelOf,
                                rule.flashesAt == null ? FlashOn.ATTACK : rule.flashesAt)
                        .withValues(FlashOn.ATTACK, FlashOn.USE, FlashOn.BOTH)
                        .displayOnlyValue()
                        .create(0, 0, ON_WIDTH, WIDGET_HEIGHT, Component.empty(),
                                (button, value) -> this.rule.flashesAt = value);

                this.opacityButton = CycleButton.<FlashOpacity>builder(
                                FlashRulesList::labelOf,
                                rule.opacity == null ? FlashOpacity.HIGH : rule.opacity)
                        .withValues(FlashOpacity.LOW, FlashOpacity.HIGH)
                        .displayOnlyValue()
                        .create(0, 0, OPACITY_WIDTH, WIDGET_HEIGHT, Component.empty(),
                                (button, value) -> this.rule.opacity = value);

                this.deleteButton = Button.builder(Component.literal("✕"), b -> FlashRulesList.this.removeEntry(this))
                        .bounds(0, 0, DELETE_WIDTH, WIDGET_HEIGHT)
                        .build();

                this.children = List.of(this.itemBox, this.onButton, this.opacityButton, this.deleteButton);
            }

            private void onItemEdited(String value)
            {
                Item item = resolveItem(value);
                this.rule.item = value;
                this.valid = item != Items.AIR;
                this.preview = previewFor(item);
                refreshItemColor();
            }

            private void refreshItemColor()
            {
                this.itemBox.setTextColor(this.valid ? TEXT_VALID : TEXT_INVALID);
            }

            @Override
            public @NonNull List<? extends GuiEventListener> children()
            {
                return this.children;
            }

            @Override
            public @NonNull List<? extends NarratableEntry> narratables()
            {
                return List.of(this.itemBox, this.onButton, this.opacityButton, this.deleteButton);
            }

            @Override
            public void extractContent(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, boolean hovered, float a)
            {
                int left = getContentX();
                int right = getContentRight();
                int midY = getContentYMiddle();
                int widgetY = midY - WIDGET_HEIGHT / 2;

                // item icon
                int iconY = midY - ICON / 2;
                if (this.preview.isEmpty())
                {
                    g.fill(left, iconY, left + ICON, iconY + ICON, SLOT_BORDER);
                    g.fill(left + 1, iconY + 1, left + ICON - 1, iconY + ICON - 1, SLOT_BG);
                }
                else
                {
                    g.item(this.preview, left, iconY);
                }

                // right-anchored buttons
                int deleteX = right - DELETE_WIDTH;
                int opacityX = deleteX - GAP - OPACITY_WIDTH;
                int onX = opacityX - GAP - ON_WIDTH;

                this.deleteButton.setPosition(deleteX, widgetY);
                this.opacityButton.setPosition(opacityX, widgetY);
                this.onButton.setPosition(onX, widgetY);

                // item box fills the middle
                int boxX = left + ICON + GAP;
                int boxW = Math.max(20, onX - GAP - boxX);
                this.itemBox.setX(boxX);
                this.itemBox.setY(widgetY);
                this.itemBox.setWidth(boxW);

                this.itemBox.extractRenderState(g, mouseX, mouseY, a);
                this.onButton.extractRenderState(g, mouseX, mouseY, a);
                this.opacityButton.extractRenderState(g, mouseX, mouseY, a);
                this.deleteButton.extractRenderState(g, mouseX, mouseY, a);
            }
        }
    }
}

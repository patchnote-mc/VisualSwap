package com.patchnote.visualswap.client;

import com.patchnote.visualswap.VisualSwap;
import com.patchnote.visualswap.client.config.ModConfig;
import com.patchnote.visualswap.client.hud.HUDHandler;
import com.patchnote.visualswap.client.hud.SwapHotbarHighlight;
import com.patchnote.visualswap.client.hud.SwapWindowState;
import com.patchnote.visualswap.client.particles.ParticlesHandler;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;


public class VisualSwapClient implements ClientModInitializer
{
    public static final int NO_SLOT = -1;

    private final SwapWindowState swapWindowState = new SwapWindowState();

    private State lastState = State.EMPTY;

    // Derived state carried across ticks (not part of the per-tick sensor snapshot).
    private int swapFromSlot = NO_SLOT;
    private int swapToSlot = NO_SLOT;
    private int highlightFromSlot = NO_SLOT;
    private int highlightToSlot = NO_SLOT;
    private boolean attackedLastTick;

    @Override
    public void onInitializeClient()
    {
        VisualSwap.LOGGER.info("Visual Swap initializing ...");

        // registration
        AutoConfig.register(ModConfig.class, Toml4jConfigSerializer::new);
        ParticlesHandler.register();
        HUDHandler.register();

        // event callbacks
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndClientTick);
        AttackEntityCallback.EVENT.register(this::onInteractEntity);
        UseEntityCallback.EVENT.register(this::onInteractEntity);
    }

    public static ModConfig getConfig()
    {
        return AutoConfig.getConfigHolder(ModConfig.class).getConfig();
    }

    /* CLIENT TICK CALLBACK */

    private void onEndClientTick(Minecraft client)
    {
        LocalPlayer player = client.player;
        if (player == null)
        {
            reset();
            return;
        }

        State previous = this.lastState;
        State current = State.capture(player, client);

        detectSwap(previous, current);
        if (inputStarted(current, previous))
        {
            if (!ItemStack.isSameItem(previous.mainHand(), current.mainHand()))
            {
                VisualSwap.LOGGER.info(
                        "AS From {} to {} was {} lunge.",
                        previous.mainHand().getItemName().getString(),
                        current.mainHand().getItemName().getString(),
                        !current.isLungeSpear() ? "not " : ""
                );
            }
            this.swapWindowState.onClick(current.tick());
        }
        updateAttackState(current);

        this.swapWindowState.onTickEnd(current.tick());
        this.lastState = current; // for next tick
    }

    private void reset()
    {
        this.lastState = State.EMPTY;
        this.swapWindowState.clear();
        HUDHandler.GLYPH.updateState(false, false, false);
        SwapHotbarHighlight.INSTANCE.clear();
        this.swapFromSlot = NO_SLOT;
        this.swapToSlot = NO_SLOT;
        this.highlightFromSlot = NO_SLOT;
        this.highlightToSlot = NO_SLOT;
        this.attackedLastTick = false;
    }

    private void detectSwap(State previous, State current)
    {
        // Skip the first post-spawn tick so the empty -> held transition isn't read as a swap.
        if (!previous.initialized()) return;

        if (current.mainHand().isEmpty())
        {
            this.swapWindowState.clear();
        }
        else if (!ItemStack.isSameItem(previous.mainHand(), current.mainHand()))
        {
            // Failed lunge-swap: swapped onto a lunge spear before the previous item's attack-strength bar finished.
            boolean lungeFail = current.isLungeSpear() && previous.attackStrengthScale() < 1.0f;
            this.swapWindowState.onSwap(current.tick(), lungeFail);
            this.swapFromSlot = previous.selectedSlot();
            this.swapToSlot = current.selectedSlot();
        }
    }

    private boolean inputStarted(State current, State previous)
    {
        // for detecting spear jab
        boolean swingStarted =
                current.swinging() && (!previous.swinging() || current.swingTime() < previous.swingTime());
        boolean attackStarted = current.attackDown() && !previous.attackDown();
        boolean useStarted = current.useDown() && !previous.useDown();

        return swingStarted // spear jab
                || attackStarted // left click
                || useStarted; // right click
    }

    private void updateAttackState(State current)
    {
        boolean attacked = this.swapWindowState.attacked(current.tick());
        HUDHandler.GLYPH.updateState(
                this.swapWindowState.visible(current.tick()),
                attacked,
                this.swapWindowState.lungeFailed(current.tick())
        );
        highlightSlot(attacked);
        this.attackedLastTick = attacked;
    }

    private void highlightSlot(boolean attacked)
    {
        if (attacked && !this.attackedLastTick)
        {
            this.highlightFromSlot = this.swapFromSlot;
            this.highlightToSlot = this.swapToSlot;
        }
        SwapHotbarHighlight.INSTANCE.update(attacked, this.highlightFromSlot, this.highlightToSlot);
    }

    /* ENTITY TICK CALLBACK */

    private InteractionResult onInteractEntity(Player player, Level level, InteractionHand hand, Entity entity,
                                               HitResult hitResult)
    {
        Minecraft client = Minecraft.getInstance();
        if (player == client.player)
        {
            // fires before END_CLIENT_TICK, check again
            if (!ItemStack.isSameItem(this.lastState.mainHand(), player.getMainHandItem()) // check item
                    || this.swapWindowState.possible(player.tickCount) // check if possible
            )
            {
                ParticlesHandler.spawnParticles(client, entity, ParticlesHandler.SWAP_ATTACKED);
            }
        }
        return InteractionResult.PASS;
    }

    /* HELPER CLASSES */

    /// Immutable per-tick snapshot of the inputs the swap logic reads. Carried/derived state lives on the client.
    private record State(int tick, boolean initialized, ItemStack mainHand, int selectedSlot, boolean attackDown,
                         boolean useDown, boolean swinging, int swingTime, float attackStrengthScale,
                         boolean isLungeSpear)
    {
        static final State EMPTY = new State(0, false, ItemStack.EMPTY, NO_SLOT, false, false, false, 0, 0.0f, false);

        static State capture(LocalPlayer player, Minecraft client)
        {
            ItemStack mainHand = player.getMainHandItem().copy();
            return new State(
                    player.tickCount,
                    true,
                    mainHand,
                    player.getInventory().getSelectedSlot(),
                    client.options.keyAttack.isDown(),
                    client.options.keyUse.isDown(),
                    player.swinging,
                    player.swingTime,
                    // Charge at end of tick == charge at the next tick's swap instant (the swap resets it before END_CLIENT_TICK).
                    player.getAttackStrengthScale(0.0f),
                    isItemLungeSpear(mainHand, player.level())
            );
        }

        /// Whether {@code stack} is a spear (a piercing weapon) carrying the Lunge enchantment.
        private static boolean isItemLungeSpear(ItemStack stack, Level level)
        {
            if (stack.get(DataComponents.PIERCING_WEAPON) == null) return false;
            Holder<Enchantment> lunge = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT) //
                                             .getOrThrow(Enchantments.LUNGE);
            return EnchantmentHelper.getItemEnchantmentLevel(lunge, stack) > 0;
        }
    }
}

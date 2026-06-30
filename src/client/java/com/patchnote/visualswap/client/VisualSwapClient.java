package com.patchnote.visualswap.client;

import com.patchnote.visualswap.SwapWindow;
import com.patchnote.visualswap.VisualSwap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;


public class VisualSwapClient implements ClientModInitializer
{
    public static final int NO_SLOT = -1;

    private final SwapWindow swapWindow = new SwapWindow();
    private final SwapHitGlyph glyph = new SwapHitGlyph();

    private State lastState = new State();

    @Override
    public void onInitializeClient()
    {
        VisualSwap.LOGGER.info("Visual Swap initializing ...");

        // registration
        VisualSwapParticles.register();
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.HOTBAR,
                Identifier.fromNamespaceAndPath(VisualSwap.MOD_ID, "swap_hit_glyph"),
                this.glyph
        );

        // event callbacks
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndClientTick);
        AttackEntityCallback.EVENT.register(this::onInteractEntity);
        UseEntityCallback.EVENT.register(this::onInteractEntity);
    }

    /* CLIENT TICK CALLBACK */

    private void onEndClientTick(Minecraft client)
    {
        LocalPlayer player = client.player;
        if (player == null)
        {
            this.lastState = new State();
            this.swapWindow.clear();
            this.glyph.updateState(false, false);
            SwapHotbarHighlight.INSTANCE.clear();
            return;
        }

        State previous = this.lastState;
        State current = State.capture(previous, player, client);

        detectSwap(previous, current);
        if (inputStarted(current, previous)) this.swapWindow.onClick(current.tick);
        updateAttackState(previous, current);

        this.swapWindow.endTick(current.tick);
        this.lastState = current; // for next tick
    }

    private void detectSwap(State previous, State current)
    {
        // Skip the first post-spawn tick so the empty -> held transition isn't read as a swap.
        if (!previous.initialized) return;

        if (!ItemStack.isSameItem(previous.mainHand, current.mainHand))
        {
            this.swapWindow.onSwap(current.tick);
            current.swapFromSlot = previous.selectedSlot;
            current.swapToSlot = current.selectedSlot;
        }
    }

    private boolean inputStarted(State current, State previous)
    {
        // for detecting spear jab
        boolean swingStarted = current.swinging && (!previous.swinging || current.swingTime < previous.swingTime);
        boolean attackStarted = current.attackDown && !previous.attackDown;
        boolean useStarted = current.useDown && !previous.useDown;

        return swingStarted // spear jab
                || attackStarted // left click
                || useStarted; // right click
    }

    private void updateAttackState(State previous, State current)
    {
        current.attacked = this.swapWindow.attacked(current.tick);
        this.glyph.updateState(this.swapWindow.visible(current.tick), current.attacked);
        highlightSlot(current, previous);
    }

    private void highlightSlot(State current, State previous)
    {
        if (current.attacked && !previous.attacked)
        {
            current.highlightFromSlot = current.swapFromSlot;
            current.highlightToSlot = current.swapToSlot;
        }
        SwapHotbarHighlight.INSTANCE.update(current.attacked, current.highlightFromSlot, current.highlightToSlot);
    }

    /* ENTITY TICK CALLBACK */

    private InteractionResult onInteractEntity(Player player, Level level, InteractionHand hand, Entity entity,
                                               HitResult hitResult)
    {
        Minecraft client = Minecraft.getInstance();
        if (player == client.player)
        {
            // fires before END_CLIENT_TICK, check live
            if (!ItemStack.isSameItem(this.lastState.mainHand, player.getMainHandItem()) // check item
                    || this.swapWindow.possible(player.tickCount) // check if possible
            )
            {
                spawnParticles(client, entity);
            }
        }
        return InteractionResult.PASS;
    }

    private static void spawnParticles(Minecraft client, Entity target)
    {
        if (client.level == null) return;

        SimpleParticleType particle = VisualSwapParticles.SWAP_ATTACKED;
        int count = 18;
        double spread = 0.45;
        RandomSource random = target.getRandom();
        double cx = target.getX();
        double cy = target.getY(0.5);
        double cz = target.getZ();
        for (int i = 0; i < count; i++)
        {
            double ox = random.nextGaussian() * spread;
            double oy = random.nextGaussian() * spread;
            double oz = random.nextGaussian() * spread;
            client.level.addParticle(particle, cx + ox, cy + oy, cz + oz, 0.0, 0.0, 0.0);
        }
    }

    /* HELPER CLASSES */

    private static final class State
    {
        int tick;
        boolean initialized;

        ItemStack mainHand = ItemStack.EMPTY;
        int selectedSlot;
        boolean attackDown;
        boolean useDown;
        boolean swinging;
        int swingTime;

        boolean attacked;
        int swapFromSlot = NO_SLOT;
        int swapToSlot = NO_SLOT;
        int highlightFromSlot = NO_SLOT;
        int highlightToSlot = NO_SLOT;

        static State next(State previous)
        {
            State state = new State();
            state.swapFromSlot = previous.swapFromSlot;
            state.swapToSlot = previous.swapToSlot;
            state.highlightFromSlot = previous.highlightFromSlot;
            state.highlightToSlot = previous.highlightToSlot;
            return state;
        }

        static State capture(State previous, LocalPlayer player, Minecraft client)
        {
            State state = State.next(previous);
            state.tick = player.tickCount;
            state.initialized = true;
            state.selectedSlot = player.getInventory().getSelectedSlot();
            state.mainHand = player.getMainHandItem().copy();
            state.attackDown = client.options.keyAttack.isDown();
            state.useDown = client.options.keyUse.isDown();
            state.swinging = player.swinging;
            state.swingTime = player.swingTime;
            return state;
        }
    }
}

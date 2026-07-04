package com.patchnote.visualswap.client;

import com.patchnote.visualswap.VisualSwap;
import com.patchnote.visualswap.client.config.ModConfig;
import com.patchnote.visualswap.client.hud.HUDHandler;
import com.patchnote.visualswap.client.hud.SwapHotbarHighlight;
import com.patchnote.visualswap.client.hud.SwapWindowState;
import com.patchnote.visualswap.client.hud.click.ClickFlashHandler;
import com.patchnote.visualswap.client.particles.AttackParticleProps;
import com.patchnote.visualswap.client.particles.ParticlesHandler;
import com.patchnote.visualswap.client.tracker.ClickTickState;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.Toml4jConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
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

    private final SwapWindowState swapWindowState = new SwapWindowState();

    private ClickTickState lastState = ClickTickState.EMPTY;

    // Derived state carried across ticks (not part of the per-tick sensor snapshot).
    private int swapFromSlot = NO_SLOT;
    private int swapToSlot = NO_SLOT;
    /// Ordered hotbar slots touched by the active chain (origin first, latest hit last).
    private final int[] chainTrail = new int[9];
    private int chainTrailLen;
    private boolean attackedLastTick;
    private int lastChainCount;

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
        ClientTickEvents.END_CLIENT_TICK.register(ClickFlashHandler::onEndClientTick);
        AttackEntityCallback.EVENT.register(this::onInteractEntity);
        UseEntityCallback.EVENT.register(this::onInteractEntity);
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

        ClickTickState previous = this.lastState;
        ClickTickState current = ClickTickState.capture(player, client);

        detectSwap(previous, current);

        if (attacked(current, previous))
        {
            if (!ItemStack.isSameItem(previous.mainHand(), current.mainHand()))
            {
                VisualSwap.LOGGER.info(
                        "AS From {} to {} was {}piercing.",
                        previous.mainHand().getItemName().getString(),
                        current.mainHand().getItemName().getString(),
                        !current.hasPiercingComponent() ? "not " : ""
                );
            }
            this.swapWindowState.onClick(current.tick());
        }
        updateAttackState(current);

        this.swapWindowState.onTickEnd(current.tick());
        this.lastState = current; // for next tick
    }

    private boolean attacked(ClickTickState current, ClickTickState previous)
    {
        return (current.swinging() && (!previous.swinging() || current.swingTime() < previous.swingTime())) // for spear
                || current.attackDown() && !previous.attackDown() // left click
                || current.useDown() && !previous.useDown(); // right click
    }

    private void reset()
    {
        this.lastState = ClickTickState.EMPTY;
        this.swapWindowState.clear();
        HUDHandler.GLYPH.updateState(false, false, false, false, 0);
        SwapHotbarHighlight.INSTANCE.clear();
        this.swapFromSlot = NO_SLOT;
        this.swapToSlot = NO_SLOT;
        this.chainTrailLen = 0;
        this.attackedLastTick = false;
        this.lastChainCount = 0;
    }

    private void detectSwap(ClickTickState previous, ClickTickState current)
    {
        // Skip the first post-spawn tick so the empty -> held transition isn't read as a swap.
        if (!previous.initialized()) return;

        if (current.mainHand().isEmpty())
        {
            this.swapWindowState.clear();
        }
        else if (!ItemStack.isSameItem(previous.mainHand(), current.mainHand()))
        {
            boolean piercingFail = current.hasPiercingComponent() && previous.cooldownAtTick() < 1.0f;
            this.swapWindowState.onSwap(current.tick(), piercingFail);
            this.swapFromSlot = previous.selectedSlot();
            this.swapToSlot = current.selectedSlot();
        }
    }

    private void updateAttackState(ClickTickState current)
    {
        int tick = current.tick();
        boolean attacked = this.swapWindowState.attacked(tick);
        int chainCount = this.swapWindowState.chainCount(tick);
        HUDHandler.GLYPH.updateState(
                this.swapWindowState.visible(tick),
                attacked,
                this.swapWindowState.failed(tick),
                this.swapWindowState.consecutive(tick),
                chainCount
        );
        highlightSlot(attacked, chainCount);
        this.attackedLastTick = attacked;
        this.lastChainCount = chainCount;
    }

    private void highlightSlot(boolean attacked, int chainCount)
    {
        if (attacked && !this.attackedLastTick)
        {
            // Fresh chain: seed the trail with this swap's origin and destination.
            this.chainTrailLen = 0;
            addTrailSlot(this.swapFromSlot);
            addTrailSlot(this.swapToSlot);
        }
        else if (attacked && chainCount > this.lastChainCount)
        {
            // Chain extended this tick: append the latest swap's destination.
            addTrailSlot(this.swapToSlot);
        }
        SwapHotbarHighlight.INSTANCE.update(attacked, this.chainTrail, this.chainTrailLen, chainCount);
    }

    private void addTrailSlot(int slot)
    {
        if (slot < 0 || this.chainTrailLen >= this.chainTrail.length) return;
        // dedup consecutive
        if (this.chainTrailLen > 0 && this.chainTrail[this.chainTrailLen - 1] == slot) return;
        this.chainTrail[this.chainTrailLen++] = slot;
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
                // the hit isn't added to the chain until END_CLIENT_TICK, + 1 to include it
                int chainHits = this.swapWindowState.chainCount(player.tickCount) + 1;
                AttackParticleProps props = AttackParticleProps.detectAttackType(player, entity);
                ParticlesHandler.spawnParticles(client, entity, chainHits, props);
            }
        }
        return InteractionResult.PASS;
    }
}

package com.patchnote.visualswap.client.swap;

import com.patchnote.visualswap.client.hud.HUDHandler;
import com.patchnote.visualswap.client.hud.SwapHotbarHighlight;
import com.patchnote.visualswap.client.hud.SwapWindowState;
import com.patchnote.visualswap.client.hud.click.ItemFlash;
import com.patchnote.visualswap.client.particles.AttackParticleProps;
import com.patchnote.visualswap.client.particles.ParticlesHandler;
import com.patchnote.visualswap.client.tracker.ClickTickState;
import com.patchnote.visualswap.client.tracker.ClickTickTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import static com.patchnote.visualswap.client.utils.Constants.HOTBAR_SLOTS;
import static com.patchnote.visualswap.client.utils.Constants.NO_SLOT;

public class SwapHandler
{
    public static final SwapHandler INSTANCE = new SwapHandler();

    private SwapHandler() { }

    private final SwapWindowState swapWindowState = new SwapWindowState();

    // Derived state carried across ticks (not part of the per-tick sensor snapshot).
    private int swapFromSlot = NO_SLOT;
    private int swapToSlot = NO_SLOT;
    /// Ordered hotbar slots touched by the active chain (origin first, latest hit last).
    private final int[] chainTrail = new int[HOTBAR_SLOTS];
    private int chainTrailLen;
    private boolean attackedLastTick;
    private int lastChainCount;

    /* EVENTS */

    public void eventTick()
    {
        detectSwap();

        if (ClickTickTracker.INSTANCE.attacked())
        {
            this.swapWindowState.eventClick(ClickTickTracker.getCurrentState().tick());
        }

        updateAttackState();

        this.swapWindowState.eventTickEnd(ClickTickTracker.getCurrentState().tick());
    }

    public void eventReset()
    {
        this.swapWindowState.clear();
        this.swapFromSlot = NO_SLOT;
        this.swapToSlot = NO_SLOT;
        this.chainTrailLen = 0;
        this.attackedLastTick = false;
        this.lastChainCount = 0;

        HUDHandler.GLYPH.eventReset();
        SwapHotbarHighlight.INSTANCE.eventReset();
        ItemFlash.INSTANCE.reset();
    }

    public InteractionResult eventInteractEntity(Player player, Entity entity)
    {
        Minecraft client = Minecraft.getInstance();
        if (player == client.player)
        {
            // fires before END_CLIENT_TICK, check again
            boolean itemSwitched = !ItemStack.isSameItem(
                    ClickTickTracker.getPreviousState().mainHand(),
                    player.getMainHandItem()
            );
            if (itemSwitched || this.swapWindowState.possible(player.tickCount) // check if possible
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

    /* HELPERS */

    private void detectSwap()
    {
        ClickTickState current = ClickTickTracker.getCurrentState();
        ClickTickState previous = ClickTickTracker.getPreviousState();

        // Skip the first post-spawn tick so the empty -> held transition isn't read as a swap.
        if (!previous.initialized()) return;

        if (current.mainHand().isEmpty())
        {
            this.swapWindowState.clear();
        }
        else if (!ItemStack.isSameItem(previous.mainHand(), current.mainHand()))
        {
            boolean piercingFail = current.hasPiercingComponent() && previous.cooldownAtTick() < 1.0f;
            this.swapWindowState.eventSwap(current.tick(), piercingFail);
            this.swapFromSlot = previous.selectedSlot();
            this.swapToSlot = current.selectedSlot();
        }
    }

    private void updateAttackState()
    {
        int tick = ClickTickTracker.getCurrentState().tick();
        boolean attacked = this.swapWindowState.attacked(tick);
        int chainCount = this.swapWindowState.chainCount(tick);
        HUDHandler.GLYPH.eventUpdate(
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
        SwapHotbarHighlight.INSTANCE.eventUpdate(attacked, this.chainTrail, this.chainTrailLen, chainCount);
    }

    private void addTrailSlot(int slot)
    {
        if (slot < 0 || this.chainTrailLen >= this.chainTrail.length) return;
        // dedup consecutive
        if (this.chainTrailLen > 0 && this.chainTrail[this.chainTrailLen - 1] == slot) return;
        this.chainTrail[this.chainTrailLen++] = slot;
    }
}

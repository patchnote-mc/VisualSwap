package com.patchnote.visualswap.client.particles;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.MaceItem;

public enum AttackParticleProps
{
    NORMAL(0.0, 0.0, 10, 20),
    /// Crit: a light pop upward and slightly outward
    CRIT(0.07, 0.25, 20, 40),
    /// Mace smash: a horizontal shockwave
    SMASH(0.38, 0.04, 40, 60);

    private final double outward;
    private final double up;
    private final int minLifetime;
    private final int maxLifetime;

    AttackParticleProps(double outward, double up, int minLifetime, int maxLifetime)
    {
        this.outward = outward;
        this.up = up;
        this.minLifetime = minLifetime;
        this.maxLifetime = maxLifetime;
    }

    public double outward() { return this.outward; }

    public double up() { return this.up; }

    /// A random lifetime (ticks) within this style's range.
    public int rollLifetime(RandomSource random)
    {
        return this.minLifetime + random.nextInt(this.maxLifetime - this.minLifetime + 1);
    }

    public static AttackParticleProps detectAttackType(Player player, Entity target)
    {
        if (isSmash(player)) return SMASH;
        if (isCrit(player, target)) return CRIT;
        return NORMAL;
    }

    private static boolean isSmash(Player player)
    {
        return player.getMainHandItem().getItem() instanceof MaceItem && player.fallDistance > 1.5 &&
                !player.isFallFlying();
    }

    private static boolean isCrit(Player player, Entity target)
    {
        return player.getAttackStrengthScale(0.5f) > 0.9f && player.fallDistance > 0.0 && !player.onGround() &&
                !player.onClimbable() && !player.isInWater() && !player.isMobilityRestricted() &&
                !player.isPassenger() && !player.isSprinting() && target instanceof LivingEntity;
    }
}

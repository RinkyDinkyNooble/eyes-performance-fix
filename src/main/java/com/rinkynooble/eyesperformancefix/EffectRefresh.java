package com.rinkynooble.eyesperformancefix;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Satellites and trees give the same effect to every mob around them on every tick, and every
 * {@code addEffect} call posts Forge's effect events to every listener in the pack.
 *
 * <p>When the mob already has that effect at the same amplifier and flags, the call can at most
 * reset its timer. Skipping it while enough time is left that the effect can't run out or change
 * before a later call refreshes it keeps the mob's effects the same; in every other case the
 * original call runs.
 */
public final class EffectRefresh {
    private EffectRefresh() {
    }

    /** {@code addEffect}, skipped while the mob's own copy has at least {@code minLeft} ticks left. */
    public static boolean addUnlessFresh(LivingEntity entity, MobEffectInstance effect, int minLeft) {
        if (isFresh(entity, effect, minLeft)) {
            return false;
        }
        return entity.addEffect(effect);
    }

    public static boolean isFresh(LivingEntity entity, MobEffectInstance effect, int minLeft) {
        if (entity instanceof Player) {
            // Players see their effect timers; never change what they get.
            return false;
        }
        MobEffectInstance current = entity.getEffect(effect.getEffect());
        // An infinite effect has duration -1, so it's never fresh here and the original call runs.
        return current != null
                && current.getAmplifier() == effect.getAmplifier()
                && current.isAmbient() == effect.isAmbient()
                && current.isVisible() == effect.isVisible()
                && current.showIcon() == effect.showIcon()
                && current.getDuration() >= minLeft;
    }
}

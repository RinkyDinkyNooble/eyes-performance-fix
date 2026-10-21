package com.rinkynooble.eyesperformancefix.mixin;

import com.rinkynooble.eyesperformancefix.CachedIds;
import com.rinkynooble.eyesperformancefix.EffectRefresh;
import com.rinkynooble.eyesperformancefix.EyesPerformanceFix;
import net.mcreator.theeyesareback.init.TheEyesAreBackModMobEffects;
import net.mcreator.theeyesareback.procedures.EyeTreeStage3OnEntityTickUpdateProcedure;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The third-stage tree's tick gives Eye Sickness (level I, 6,000 ticks, ambient) to every mob within
 * 50 blocks, every tick.
 *
 * <p>Eye Sickness only escalates when its own timer is down to 2 ticks, so while a mob has the level I
 * effect with 5,980 ticks or more left, the call changes nothing that matters before the next one
 * and is skipped. Escalated mobs (level II and up) keep the original call, which refreshes the level I
 * effect stored behind the higher one. Every other effect the tree gives is left alone.
 *
 * <p>Its entity tag keys (`eye:mob`, checked for every entity around it) are built once.
 */
@Mixin(value = EyeTreeStage3OnEntityTickUpdateProcedure.class, remap = false)
public abstract class EyeTreeStage3TickMixin {
    private static final int EYE_SICKNESS_TICKS = 6000;
    private static final int EYE_SICKNESS_MIN_LEFT = 5980;

    @Redirect(method = "execute", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z"))
    private static boolean eyesperformancefix$addEffect(LivingEntity entity, MobEffectInstance effect) {
        if (EyesPerformanceFix.enabled
                && effect.getEffect() == TheEyesAreBackModMobEffects.EYE_SICKNESS.get()
                && effect.getAmplifier() == 0
                && effect.getDuration() == EYE_SICKNESS_TICKS) {
            return EffectRefresh.addUnlessFresh(entity, effect, EYE_SICKNESS_MIN_LEFT);
        }
        return entity.addEffect(effect);
    }

    @Redirect(method = "execute", at = @At(value = "NEW", target = "(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;"))
    @SuppressWarnings("removal") // The constructor the original calls.
    private static ResourceLocation eyesperformancefix$location(String id) {
        return EyesPerformanceFix.enabled ? CachedIds.location(id) : new ResourceLocation(id);
    }

    @Redirect(method = "execute", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/tags/TagKey;create(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/tags/TagKey;"))
    private static <T> TagKey<T> eyesperformancefix$tag(ResourceKey<? extends Registry<T>> registry, ResourceLocation id) {
        return EyesPerformanceFix.enabled ? CachedIds.tag(registry, id) : TagKey.create(registry, id);
    }
}

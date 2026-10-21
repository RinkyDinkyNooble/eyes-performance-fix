package com.rinkynooble.eyesperformancefix.mixin;

import com.rinkynooble.eyesperformancefix.CachedIds;
import com.rinkynooble.eyesperformancefix.EyesPerformanceFix;
import com.rinkynooble.eyesperformancefix.TargetSearch;
import net.mcreator.theeyesareback.procedures.AttackAiThingProcedure;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.eventbus.api.Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.function.Predicate;

/**
 * Every eYe's target search, every tick, on both sides (see {@link TargetSearch}). The original code
 * still makes every decision; this only drops the entities that can't change anything and skips
 * their line-of-sight ray casts.
 */
@Mixin(value = AttackAiThingProcedure.class, remap = false)
public abstract class AttackAiThingProcedureMixin {
    private static final String EXECUTE =
            "execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V";

    @Redirect(method = EXECUTE, at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/level/LevelAccessor;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"))
    private static List<Entity> eyesperformancefix$candidates(LevelAccessor world, Class<Entity> type, AABB box,
                                                              Predicate<? super Entity> filter, Event event,
                                                              LevelAccessor level, double x, double y, double z,
                                                              Entity entity) {
        if (!EyesPerformanceFix.enabled) {
            return world.getEntitiesOfClass(type, box, filter);
        }
        return TargetSearch.candidates(world, type, box, filter, entity);
    }

    @Redirect(method = EXECUTE, at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/entity/LivingEntity;hasLineOfSight(Lnet/minecraft/world/entity/Entity;)Z"))
    private static boolean eyesperformancefix$lineOfSight(LivingEntity eye, Entity other) {
        if (EyesPerformanceFix.enabled && !TargetSearch.canChange(eye, other)) {
            // Seen or not, this entity changes nothing, so the ray cast can be skipped.
            return false;
        }
        return eye.hasLineOfSight(other);
    }

    // The `eye:chip` tag key, built from a string on every call.

    @Redirect(method = EXECUTE, at = @At(value = "NEW", target = "(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;"))
    @SuppressWarnings("removal") // The constructor the original calls.
    private static ResourceLocation eyesperformancefix$location(String id) {
        return EyesPerformanceFix.enabled ? CachedIds.location(id) : new ResourceLocation(id);
    }

    @Redirect(method = EXECUTE, at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/tags/TagKey;create(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/tags/TagKey;"))
    private static <T> TagKey<T> eyesperformancefix$tag(ResourceKey<? extends Registry<T>> registry, ResourceLocation id) {
        return EyesPerformanceFix.enabled ? CachedIds.tag(registry, id) : TagKey.create(registry, id);
    }
}

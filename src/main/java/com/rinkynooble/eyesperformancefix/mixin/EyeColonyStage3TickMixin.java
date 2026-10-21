package com.rinkynooble.eyesperformancefix.mixin;

import com.rinkynooble.eyesperformancefix.CachedIds;
import com.rinkynooble.eyesperformancefix.EffectRefresh;
import com.rinkynooble.eyesperformancefix.EyesPerformanceFix;
import com.rinkynooble.eyesperformancefix.SharedSearch;
import net.mcreator.theeyesareback.procedures.EyeColonyStage3OnEntityTickUpdateProcedure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * The third-stage satellite's tick. Every tick it searches every entity within 125 blocks twice,
 * sorted by distance: once to roll meteors for each player, then to give every eYe mob Speed III,
 * Jump Boost II and Resistance I for 5 ticks.
 *
 * <ul>
 *   <li>The second search reuses the first one's list, unless a meteor spawned in between (meteors
 *       are eYe mobs, so the original gives a new meteor the effects on the tick it spawns).</li>
 *   <li>Neither list is sorted: each meteor roll uses its own new random source and each effect goes
 *       to one mob, so the order changes nothing.</li>
 *   <li>An effect is re-applied only when the mob doesn't have it at that amplifier, or has 2 ticks or
 *       fewer of it left. Whichever of the mob and the satellite ticks first, the effect never runs
 *       out while the mob is in range. A mob that leaves keeps it 2 to 5 more ticks (4 to 5 before).</li>
 * </ul>
 */
@Mixin(value = EyeColonyStage3OnEntityTickUpdateProcedure.class, remap = false)
public abstract class EyeColonyStage3TickMixin {
    private static final String GET_ENTITIES =
            "Lnet/minecraft/world/level/LevelAccessor;getEntitiesOfClass(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;";
    private static final String SORTED = "Ljava/util/stream/Stream;sorted(Ljava/util/Comparator;)Ljava/util/stream/Stream;";

    @Redirect(method = "execute", at = @At(value = "INVOKE", target = GET_ENTITIES, ordinal = 0, remap = true))
    private static List<Entity> eyesperformancefix$firstSearch(LevelAccessor world, Class<Entity> type, AABB box,
                                                               Predicate<? super Entity> filter) {
        List<Entity> found = world.getEntitiesOfClass(type, box, filter);
        SharedSearch.remember(found, box);
        return found;
    }

    @Redirect(method = "execute", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/entity/EntityType;spawn(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/MobSpawnType;)Lnet/minecraft/world/entity/Entity;"))
    private static Entity eyesperformancefix$spawnMeteor(EntityType<?> type, ServerLevel level, BlockPos pos, MobSpawnType reason) {
        SharedSearch.markChanged();
        return type.spawn(level, pos, reason);
    }

    @Redirect(method = "execute", at = @At(value = "INVOKE", target = GET_ENTITIES, ordinal = 1, remap = true))
    private static List<Entity> eyesperformancefix$secondSearch(LevelAccessor world, Class<Entity> type, AABB box,
                                                                Predicate<? super Entity> filter) {
        List<Entity> first = SharedSearch.take(box);
        return EyesPerformanceFix.enabled && first != null ? first : world.getEntitiesOfClass(type, box, filter);
    }

    @Redirect(method = "execute", at = @At(value = "INVOKE", target = SORTED, ordinal = 0))
    private static Stream<Entity> eyesperformancefix$unsortedPlayers(Stream<Entity> stream, Comparator<? super Entity> order) {
        return EyesPerformanceFix.enabled ? stream : stream.sorted(order);
    }

    @Redirect(method = "execute", at = @At(value = "INVOKE", target = SORTED, ordinal = 1))
    private static Stream<Entity> eyesperformancefix$unsortedMobs(Stream<Entity> stream, Comparator<? super Entity> order) {
        return EyesPerformanceFix.enabled ? stream : stream.sorted(order);
    }

    @Redirect(method = "execute", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/entity/LivingEntity;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z"))
    private static boolean eyesperformancefix$addEffect(LivingEntity entity, MobEffectInstance effect) {
        if (EyesPerformanceFix.enabled && effect.getDuration() == 5) {
            // Speed III, Jump Boost II, Resistance I for 5 ticks: refresh at 2 ticks left.
            return EffectRefresh.addUnlessFresh(entity, effect, 3);
        }
        return entity.addEffect(effect);
    }

    // The `eye:mob` tag key (and two more), built from a string for every entity in range.

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

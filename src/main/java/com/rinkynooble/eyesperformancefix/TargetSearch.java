package com.rinkynooble.eyesperformancefix;

import net.mcreator.theeyesareback.init.TheEyesAreBackModAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Every eYe's target search ({@code AttackAiThingProcedure}), made cheap with the same results.
 *
 * <p>Each tick the original goes through every entity within 60 blocks, nearest first, and for each
 * one the eYe can see:
 * <ul>
 *   <li>(A) if the eYe isn't {@code attacking} and the entity is living with an eyeball value of 0,
 *       targets it and sets {@code attacking};</li>
 *   <li>(B) if the entity is a player and the eYe's target isn't a player, targets it and sets
 *       {@code attacking}.</li>
 * </ul>
 * An entity that meets neither condition changes nothing whether or not the eYe can see it, so its
 * line-of-sight ray cast (the main cost) can be skipped. Line of sight has no side effects.
 * The original code still runs and makes every decision; this only removes entities that can't
 * change anything and skips their ray casts.
 */
public final class TargetSearch {
    private TargetSearch() {
    }

    /**
     * The entity list for the search loop: the original list, minus the entities that can't change
     * anything during this call, in the same order. Empty when nothing can change.
     */
    public static List<Entity> candidates(LevelAccessor world, Class<Entity> type, AABB box,
                                          Predicate<? super Entity> filter, Entity eye) {
        if (eye.getPersistentData().getBoolean("attacking")) {
            // `attacking` is only ever set during the loop, never cleared, so (A) can't happen.
            if (targetOf(eye) instanceof Player) {
                // (B) can't happen either, and nothing else in the loop changes anything.
                return new ArrayList<>();
            }
            // Only players can change the result.
            return new ArrayList<>(world.getEntitiesOfClass(Player.class, box, filter));
        }
        // (A) for living entities with eyeball 0, (B) for players: a player may become relevant
        // later in the loop, once (A) has targeted a non-player.
        return new ArrayList<>(world.getEntitiesOfClass(LivingEntity.class, box,
                entity -> filter.test(entity) && (entity instanceof Player || eyeball(entity) == 0.0)));
    }

    /** Whether seeing {@code other} right now would change anything, with the original's conditions. */
    public static boolean canChange(Entity eye, Entity other) {
        boolean a = eyeball(other) == 0.0 && !eye.getPersistentData().getBoolean("attacking")
                && other instanceof LivingEntity;
        return a || other instanceof Player && !(targetOf(eye) instanceof Player);
    }

    /** The eYes' eyeball attribute value, the way their procedure reads it (0 without the attribute). */
    public static double eyeball(Entity entity) {
        Attribute eyeball = TheEyesAreBackModAttributes.EYEBALL.get();
        if (entity instanceof LivingEntity living && living.getAttributes().hasAttribute(eyeball)) {
            return living.getAttribute(eyeball).getBaseValue();
        }
        return 0.0;
    }

    private static LivingEntity targetOf(Entity entity) {
        return entity instanceof Mob mob ? mob.getTarget() : null;
    }
}

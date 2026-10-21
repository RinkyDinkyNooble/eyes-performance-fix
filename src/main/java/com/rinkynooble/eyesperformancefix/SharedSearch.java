package com.rinkynooble.eyesperformancefix;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Lets the satellite's second 125-block search reuse the first one's list within one call.
 * Per thread, since the client runs the satellite's tick too.
 */
public final class SharedSearch {
    private static final ThreadLocal<SharedSearch> CURRENT = ThreadLocal.withInitial(SharedSearch::new);

    private List<Entity> list;
    private AABB box;
    private boolean changed;

    private SharedSearch() {
    }

    public static void remember(List<Entity> list, AABB box) {
        SharedSearch shared = CURRENT.get();
        shared.list = list;
        shared.box = box;
        shared.changed = false;
    }

    /** Something may have joined the world since the first search (a meteor spawned). */
    public static void markChanged() {
        CURRENT.get().changed = true;
    }

    /** The first search's list if it's still exact for this box, else null. Forgets it either way. */
    public static List<Entity> take(AABB box) {
        SharedSearch shared = CURRENT.get();
        List<Entity> list = shared.changed || !box.equals(shared.box) ? null : shared.list;
        shared.list = null;
        shared.box = null;
        return list;
    }
}

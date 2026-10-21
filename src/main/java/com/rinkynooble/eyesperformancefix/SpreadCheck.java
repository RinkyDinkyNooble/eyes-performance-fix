package com.rinkynooble.eyesperformancefix;

import net.mcreator.theeyesareback.entity.EyeSourceStage1Entity;
import net.mcreator.theeyesareback.entity.EyeTreeStage2Entity;
import net.mcreator.theeyesareback.entity.EyeTreeStage3Entity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * eYe Soil's tick ({@code BlockSpreadProcedure}) does three sorted searches of every entity around the
 * ticked block, and only does anything for an eYe Source within 5 blocks, a second-stage tree within 10
 * or a third-stage tree within 20. With none of them there (nearly always) it does nothing at all.
 */
public final class SpreadCheck {
    private SpreadCheck() {
    }

    /** Whether any of the three loops would find its tree, with the original's boxes. Searches by class. */
    public static boolean anyTreeNear(LevelAccessor world, double x, double y, double z) {
        Vec3 center = new Vec3(x, y, z);
        AABB at = new AABB(center, center);
        return !world.getEntitiesOfClass(EyeTreeStage3Entity.class, at.inflate(20.0), entity -> true).isEmpty()
                || !world.getEntitiesOfClass(EyeTreeStage2Entity.class, at.inflate(10.0), entity -> true).isEmpty()
                || !world.getEntitiesOfClass(EyeSourceStage1Entity.class, at.inflate(5.0), entity -> true).isEmpty();
    }
}

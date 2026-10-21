package com.rinkynooble.eyesperformancefix.mixin;

import com.rinkynooble.eyesperformancefix.CachedIds;
import com.rinkynooble.eyesperformancefix.EyesPerformanceFix;
import com.rinkynooble.eyesperformancefix.SpreadCheck;
import net.mcreator.theeyesareback.procedures.BlockSpreadProcedure;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * eYe Soil's tick (see {@link SpreadCheck}): returns at once when no tree is near, which is what the
 * original's three loops would amount to. With a tree near, the original runs, with its block tag
 * keys built once instead of on every check.
 */
@Mixin(value = BlockSpreadProcedure.class, remap = false)
public abstract class BlockSpreadProcedureMixin {
    @Inject(method = "execute", at = @At("HEAD"), cancellable = true)
    private static void eyesperformancefix$skipWithoutTrees(LevelAccessor world, double x, double y, double z, CallbackInfo ci) {
        if (EyesPerformanceFix.enabled && !SpreadCheck.anyTreeNear(world, x, y, z)) {
            ci.cancel();
        }
    }

    @Redirect(method = "execute", at = @At(value = "NEW", target = "(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;"))
    @SuppressWarnings("removal") // The constructor the original calls.
    private static ResourceLocation eyesperformancefix$location(String id) {
        return EyesPerformanceFix.enabled ? CachedIds.location(id) : new ResourceLocation(id);
    }

    // Forge's own method, so not remapped.
    @Redirect(method = "execute", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/tags/BlockTags;create(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/tags/TagKey;"))
    private static TagKey<Block> eyesperformancefix$blockTag(ResourceLocation id) {
        return EyesPerformanceFix.enabled ? CachedIds.blockTag(id) : BlockTags.create(id);
    }
}

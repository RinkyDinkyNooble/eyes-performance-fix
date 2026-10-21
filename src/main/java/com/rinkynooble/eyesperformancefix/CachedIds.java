package com.rinkynooble.eyesperformancefix;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The eYes' procedures build the same resource locations and tag keys from string constants on
 * every call. These are immutable, and tag keys are interned (one instance per id), so building
 * each one once and handing back the same object gives the same results.
 *
 * <p>Only the patched procedures use this, and they only build ids from constants, so the maps
 * stay small. Both the server and the client thread can call in.
 */
public final class CachedIds {
    private static final Map<String, ResourceLocation> LOCATIONS = new ConcurrentHashMap<>();
    private static final Map<ResourceKey<?>, Map<ResourceLocation, TagKey<?>>> TAGS = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, TagKey<Block>> BLOCK_TAGS = new ConcurrentHashMap<>();

    private CachedIds() {
    }

    @SuppressWarnings("removal") // The constructor the original calls.
    public static ResourceLocation location(String id) {
        return LOCATIONS.computeIfAbsent(id, ResourceLocation::new);
    }

    @SuppressWarnings("unchecked")
    public static <T> TagKey<T> tag(ResourceKey<? extends Registry<T>> registry, ResourceLocation id) {
        return (TagKey<T>) TAGS.computeIfAbsent(registry, key -> new ConcurrentHashMap<>())
                .computeIfAbsent(id, key -> TagKey.create(registry, key));
    }

    public static TagKey<Block> blockTag(ResourceLocation id) {
        return BLOCK_TAGS.computeIfAbsent(id, BlockTags::create);
    }
}

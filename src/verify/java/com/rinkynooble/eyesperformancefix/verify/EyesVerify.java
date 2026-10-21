package com.rinkynooble.eyesperformancefix.verify;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.rinkynooble.eyesperformancefix.CachedIds;
import com.rinkynooble.eyesperformancefix.EyesPerformanceFix;
import com.rinkynooble.eyesperformancefix.SpreadCheck;
import com.rinkynooble.eyesperformancefix.TargetSearch;
import net.mcreator.theeyesareback.entity.EyeColonyStage3Entity;
import net.mcreator.theeyesareback.entity.EyeSourceStage1Entity;
import net.mcreator.theeyesareback.entity.EyeTreeStage2Entity;
import net.mcreator.theeyesareback.entity.EyeTreeStage3Entity;
import net.mcreator.theeyesareback.init.TheEyesAreBackModMobEffects;
import net.mcreator.theeyesareback.procedures.AttackAiThingProcedure;
import net.mcreator.theeyesareback.procedures.BlockSpreadProcedure;
import net.mcreator.theeyesareback.procedures.EyeColonyStage3OnEntityTickUpdateProcedure;
import net.mcreator.theeyesareback.procedures.EyeTreeStage3OnEntityTickUpdateProcedure;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.mojang.authlib.GameProfile;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Dev-only checks for eYes Performance Fix, run as server commands. Never shipped.
 *
 * <p>Each check runs The eYes' own procedure twice on the same world state, once with the patch off
 * ({@link EyesPerformanceFix#enabled} false, so every patched call does what the original does) and once
 * with it on, restores the state in between, and compares the results. It prints PASS or FAIL.
 */
@Mod("eyesperformancefix_verify")
public class EyesVerify {
    private static final Logger LOGGER = LogManager.getLogger("eYes Performance Fix checks");
    private static final Field ACTIVE_EFFECTS = ObfuscationReflectionHelper.findField(LivingEntity.class, "f_20945_");

    // What the running procedure did, recorded by the event listeners below.
    private static Entity recordingTargetsOf;
    private static final List<String> targetCalls = new ArrayList<>();
    private static boolean countingEffects;
    private static long effectEvents;

    // The `watch` check, run at the end of each server tick while active.
    private static Watch watch;

    public EyesVerify() {
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(EyesVerify::onChangeTarget);
        MinecraftForge.EVENT_BUS.addListener(EyesVerify::onEffectApplicable);
        MinecraftForge.EVENT_BUS.addListener(EyesVerify::onServerTick);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("eyesverify")
                .then(Commands.literal("targets").executes(safe(ctx -> targets(ctx, false)))
                        .then(Commands.literal("fresh").executes(safe(ctx -> targets(ctx, true)))))
                .then(Commands.literal("effects").executes(safe(EyesVerify::effects)))
                .then(Commands.literal("spread")
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 1000000))
                                .then(Commands.argument("seed", IntegerArgumentType.integer())
                                        .executes(safe(EyesVerify::spread)))))
                .then(Commands.literal("tags").executes(safe(EyesVerify::tags)))
                .then(Commands.literal("watch")
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 100000))
                                .executes(safe(EyesVerify::startWatch))))
                .then(Commands.literal("patch")
                        .then(Commands.literal("on").executes(safe(ctx -> setPatch(ctx, true))))
                        .then(Commands.literal("off").executes(safe(ctx -> setPatch(ctx, false)))))
                .then(Commands.literal("count").executes(safe(EyesVerify::count)))
                .then(Commands.literal("fakeplayer")
                        .then(Commands.literal("add").executes(safe(EyesVerify::addFakePlayer)))
                        .then(Commands.literal("remove").executes(safe(EyesVerify::removeFakePlayer)))));
    }

    @FunctionalInterface
    private interface Check {
        int run(CommandContext<CommandSourceStack> ctx) throws Exception;
    }

    private static com.mojang.brigadier.Command<CommandSourceStack> safe(Check check) {
        return ctx -> {
            try {
                return check.run(ctx);
            } catch (Throwable t) {
                LOGGER.error("Check failed with an exception", t);
                EyesPerformanceFix.enabled = true;
                recordingTargetsOf = null;
                countingEffects = false;
                say(ctx, "FAIL: exception " + t);
                return 0;
            }
        };
    }

    private static void say(CommandContext<CommandSourceStack> ctx, String message) {
        LOGGER.info(message);
        ctx.getSource().sendSuccess(() -> Component.literal(message), false);
    }

    // ---------------------------------------------------------------- listeners

    private static void onChangeTarget(LivingChangeTargetEvent event) {
        if (recordingTargetsOf != null && event.getEntity() == recordingTargetsOf) {
            targetCalls.add(describe(event.getNewTarget()));
        }
    }

    private static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (countingEffects) {
            effectEvents++;
        }
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && watch != null) {
            watch.tick(event.getServer());
        }
    }

    // ---------------------------------------------------------------- patch on/off, count

    private static int setPatch(CommandContext<CommandSourceStack> ctx, boolean on) {
        EyesPerformanceFix.enabled = on;
        say(ctx, "Patch " + (on ? "on" : "off (original code)"));
        return 1;
    }

    private static int count(CommandContext<CommandSourceStack> ctx) {
        Map<String, Integer> counts = new java.util.TreeMap<>();
        int eyes = 0;
        for (ServerLevel level : ctx.getSource().getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                counts.merge(entity.getType().toShortString(), 1, Integer::sum);
                if (isEye(entity)) {
                    eyes++;
                }
            }
        }
        say(ctx, "Entities: " + counts + "; eYes running the target search: " + eyes);
        return 1;
    }

    // ---------------------------------------------------------------- fake player

    private static FakePlayer fakePlayer;

    /**
     * A survival "player" at the command's position, so a headless server can test the eYes' player
     * targeting. Forge's fake players are invulnerable and don't tick.
     */
    private static int addFakePlayer(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        Vec3 at = ctx.getSource().getPosition();
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("6d3c4a8e-0f4b-4c55-9a51-0e1e5a0b7e11"), "EyesVerifyTarget"));
        player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        level.addNewPlayer(player);
        fakePlayer = player;
        say(ctx, "Added fake player " + describe(player) + " at " + BlockPos.containing(at).toShortString());
        return 1;
    }

    private static int removeFakePlayer(CommandContext<CommandSourceStack> ctx) {
        if (fakePlayer != null) {
            fakePlayer.serverLevel().removePlayerImmediately(fakePlayer, Entity.RemovalReason.DISCARDED);
            fakePlayer = null;
        }
        say(ctx, "Removed the fake player");
        return 1;
    }

    // ---------------------------------------------------------------- targets

    /**
     * Every eYe's target search, original and patched, from the same state: the setTarget calls in
     * order, the final target and the `attacking` flag must match. The original clears the flag
     * 1 time in 7,501 at random, so a mismatch is retried before it counts.
     *
     * <p>With {@code fresh}, each eYe is compared as if it had no target and wasn't attacking (the
     * state of a newly spawned eYe, where the search does the most), then put back as it was.
     */
    private static int targets(CommandContext<CommandSourceStack> ctx, boolean fresh) {
        int eyes = 0;
        int failures = 0;
        int retried = 0;
        int withCalls = 0;
        long originalNanos = 0;
        long patchedNanos = 0;
        for (ServerLevel level : ctx.getSource().getServer().getAllLevels()) {
            List<LivingEntity> found = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (isEye(entity)) {
                    found.add((LivingEntity) entity);
                }
            }
            for (int i = 0; i < found.size(); i++) {
                LivingEntity eye = found.get(i);
                if (eye.isRemoved()) {
                    continue;
                }
                eyes++;
                LivingEntity realTarget = eye instanceof Mob mob ? mob.getTarget() : null;
                boolean realHadFlag = eye.getPersistentData().contains("attacking");
                boolean realFlag = eye.getPersistentData().getBoolean("attacking");
                if (fresh) {
                    if (eye instanceof Mob mob) {
                        mob.setTarget(null);
                    }
                    eye.getPersistentData().putBoolean("attacking", false);
                }
                TargetRun original = null;
                TargetRun patched = null;
                boolean same = false;
                for (int attempt = 0; attempt < 5 && !same; attempt++) {
                    if (attempt > 0) {
                        retried++;
                    }
                    // Alternate which one runs first, so neither gets the warm caches every time.
                    if ((i + attempt) % 2 == 0) {
                        original = runTargets(level, eye, false);
                        patched = runTargets(level, eye, true);
                    } else {
                        patched = runTargets(level, eye, true);
                        original = runTargets(level, eye, false);
                    }
                    same = original.sameAs(patched);
                }
                if (fresh) {
                    if (eye instanceof Mob mob) {
                        mob.setTarget(realTarget);
                    }
                    if (realHadFlag) {
                        eye.getPersistentData().putBoolean("attacking", realFlag);
                    } else {
                        eye.getPersistentData().remove("attacking");
                    }
                }
                originalNanos += original.nanos;
                patchedNanos += patched.nanos;
                if (!original.calls.isEmpty()) {
                    withCalls++;
                }
                if (!same) {
                    failures++;
                    if (failures <= 10) {
                        say(ctx, "Mismatch for " + describe(eye) + ": original " + original + ", patched " + patched);
                    }
                }
            }
        }
        say(ctx, String.format("%s targets%s: %d eYes compared (%d where the original called setTarget), %d mismatches, %d retries. "
                        + "Average time per eYe: original %.1f us, patched %.1f us.",
                failures == 0 ? "PASS" : "FAIL", fresh ? " (fresh)" : "", eyes, withCalls, failures, retried,
                eyes == 0 ? 0.0 : originalNanos / 1000.0 / eyes, eyes == 0 ? 0.0 : patchedNanos / 1000.0 / eyes));
        return failures == 0 ? 1 : 0;
    }

    private static boolean isEye(Entity entity) {
        return entity instanceof LivingEntity && TargetSearch.eyeball(entity) == 1.0
                && !entity.getType().is(TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("eye:chip")));
    }

    private static TargetRun runTargets(ServerLevel level, LivingEntity eye, boolean patch) {
        LivingEntity targetBefore = eye instanceof Mob mob ? mob.getTarget() : null;
        boolean hadFlag = eye.getPersistentData().contains("attacking");
        boolean flagBefore = eye.getPersistentData().getBoolean("attacking");

        EyesPerformanceFix.enabled = patch;
        targetCalls.clear();
        recordingTargetsOf = eye;
        long start = System.nanoTime();
        AttackAiThingProcedure.execute(level, eye.getX(), eye.getY(), eye.getZ(), eye);
        long nanos = System.nanoTime() - start;
        recordingTargetsOf = null;
        EyesPerformanceFix.enabled = true;
        TargetRun run = new TargetRun(new ArrayList<>(targetCalls),
                describe(eye instanceof Mob mob ? mob.getTarget() : null),
                eye.getPersistentData().getBoolean("attacking"), nanos);

        // Put the state back for the other run.
        if (eye instanceof Mob mob) {
            mob.setTarget(targetBefore);
        }
        if (hadFlag) {
            eye.getPersistentData().putBoolean("attacking", flagBefore);
        } else {
            eye.getPersistentData().remove("attacking");
        }
        return run;
    }

    private record TargetRun(List<String> calls, String target, boolean attacking, long nanos) {
        boolean sameAs(TargetRun other) {
            return calls.equals(other.calls) && target.equals(other.target) && attacking == other.attacking;
        }

        @Override
        public String toString() {
            return "setTarget " + calls + ", target " + target + ", attacking " + attacking;
        }
    }

    private static String describe(Entity entity) {
        return entity == null ? "none" : entity.getType().toShortString() + "#" + entity.getId();
    }

    // ---------------------------------------------------------------- effects

    /**
     * Every third-stage satellite's and tree's tick, original and patched, from the same state. Every
     * living entity around must end with the same effects, amplifiers, flags and hidden effects. The
     * one allowed difference is the timer of an effect whose refresh the patch put off: the
     * satellite's 5-tick effects with 3 or more ticks left, the tree's level I Eye Sickness with 5,980
     * or more.
     */
    private static int effects(CommandContext<CommandSourceStack> ctx) throws Exception {
        int calls = 0;
        int compared = 0;
        int deferred = 0;
        int failures = 0;
        long originalEvents = 0;
        long patchedEvents = 0;
        for (ServerLevel level : ctx.getSource().getServer().getAllLevels()) {
            List<LivingEntity> sources = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof EyeColonyStage3Entity || entity instanceof EyeTreeStage3Entity) {
                    sources.add((LivingEntity) entity);
                }
            }
            for (LivingEntity source : sources) {
                if (source.isRemoved()) {
                    continue;
                }
                calls++;
                boolean satellite = source instanceof EyeColonyStage3Entity;
                List<LivingEntity> around = level.getEntitiesOfClass(LivingEntity.class,
                        source.getBoundingBox().inflate(satellite ? 130 : 55), e -> true);
                Map<LivingEntity, List<CompoundTag>> before = snapshot(around);

                EffectRun original = runEffects(level, source, satellite, around, false);
                restore(before);
                EffectRun patched = runEffects(level, source, satellite, around, true);
                originalEvents += original.events;
                patchedEvents += patched.events;

                for (LivingEntity entity : around) {
                    if (entity.isRemoved()) {
                        continue;
                    }
                    compared++;
                    String problem = compareEffects(original.after.get(entity), patched.after.get(entity));
                    if (problem == null) {
                        continue;
                    }
                    if (problem.isEmpty()) {
                        deferred++;
                        continue;
                    }
                    failures++;
                    if (failures <= 10) {
                        say(ctx, "Mismatch around " + describe(source) + " on " + describe(entity) + ": " + problem);
                    }
                }
            }
        }
        say(ctx, String.format("%s effects: %d satellite and tree ticks, %d entities compared (%d only had a refresh put off), "
                        + "%d mismatches. Effect events: original %d, patched %d.",
                failures == 0 ? "PASS" : "FAIL", calls, compared, deferred, failures, originalEvents, patchedEvents));
        return failures == 0 ? 1 : 0;
    }

    private record EffectRun(Map<LivingEntity, List<CompoundTag>> after, long events) {
    }

    private static EffectRun runEffects(ServerLevel level, LivingEntity source, boolean satellite,
                                        List<LivingEntity> around, boolean patch) {
        EyesPerformanceFix.enabled = patch;
        effectEvents = 0;
        countingEffects = true;
        try {
            if (satellite) {
                EyeColonyStage3OnEntityTickUpdateProcedure.execute(level, source.getX(), source.getY(), source.getZ(), source);
            } else {
                EyeTreeStage3OnEntityTickUpdateProcedure.execute(level, source.getX(), source.getY(), source.getZ(), source);
            }
        } finally {
            countingEffects = false;
            EyesPerformanceFix.enabled = true;
        }
        return new EffectRun(snapshot(around), effectEvents);
    }

    private static Map<LivingEntity, List<CompoundTag>> snapshot(List<LivingEntity> entities) {
        Map<LivingEntity, List<CompoundTag>> result = new LinkedHashMap<>();
        for (LivingEntity entity : entities) {
            List<CompoundTag> effects = new ArrayList<>();
            for (MobEffectInstance effect : entity.getActiveEffects()) {
                effects.add(effect.save(new CompoundTag()));
            }
            effects.sort((a, b) -> Integer.compare(a.getInt("Id"), b.getInt("Id")));
            result.put(entity, effects);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void restore(Map<LivingEntity, List<CompoundTag>> snapshot) throws IllegalAccessException {
        for (Map.Entry<LivingEntity, List<CompoundTag>> entry : snapshot.entrySet()) {
            Map<MobEffect, MobEffectInstance> active = (Map<MobEffect, MobEffectInstance>) ACTIVE_EFFECTS.get(entry.getKey());
            active.clear();
            for (CompoundTag tag : entry.getValue()) {
                MobEffectInstance effect = MobEffectInstance.load(tag);
                if (effect != null) {
                    active.put(effect.getEffect(), effect);
                }
            }
        }
    }

    /** Null when the same, "" when only a refresh the patch may put off differs, else what differs. */
    private static String compareEffects(List<CompoundTag> original, List<CompoundTag> patched) {
        if (original == null || patched == null) {
            return Objects.equals(original, patched) ? null : "missing snapshot";
        }
        if (original.equals(patched)) {
            return null;
        }
        if (original.size() != patched.size()) {
            return "effects " + original + " vs " + patched;
        }
        boolean deferred = false;
        for (int i = 0; i < original.size(); i++) {
            CompoundTag a = original.get(i);
            CompoundTag b = patched.get(i);
            if (a.equals(b)) {
                continue;
            }
            CompoundTag aNoTime = a.copy();
            CompoundTag bNoTime = b.copy();
            aNoTime.remove("Duration");
            bNoTime.remove("Duration");
            int minLeft = putOffMinimum(a);
            if (!aNoTime.equals(bNoTime) || minLeft < 0
                    || b.getInt("Duration") < minLeft || b.getInt("Duration") > a.getInt("Duration")) {
                return "effect " + a + " vs " + b;
            }
            deferred = true;
        }
        return deferred ? "" : null;
    }

    /** The least time left the patch keeps for this effect before refreshing it, or -1 if it never puts it off. */
    private static int putOffMinimum(CompoundTag effect) {
        MobEffect type = MobEffect.byId(effect.getInt("Id"));
        int amplifier = effect.getByte("Amplifier");
        int duration = effect.getInt("Duration");
        if (duration == 5 && (type == MobEffects.MOVEMENT_SPEED && amplifier == 2
                || type == MobEffects.JUMP && amplifier == 1 || type == MobEffects.DAMAGE_RESISTANCE && amplifier == 0)) {
            return 3;
        }
        if (duration == 6000 && type == TheEyesAreBackModMobEffects.EYE_SICKNESS.get() && amplifier == 0) {
            return 5980;
        }
        return -1;
    }

    // ---------------------------------------------------------------- spread

    /**
     * eYe Soil's early return, at random spots around every tree (and anywhere near the command's
     * position): the patch returns early exactly when none of the original's three loops would find
     * its tree. Then times the procedure at the spots without trees, where it changes nothing.
     */
    private static int spread(CommandContext<CommandSourceStack> ctx) {
        int count = IntegerArgumentType.getInteger(ctx, "count");
        Random random = new Random(IntegerArgumentType.getInteger(ctx, "seed"));
        ServerLevel level = ctx.getSource().getLevel();
        Vec3 origin = ctx.getSource().getPosition();

        List<Entity> trees = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof EyeSourceStage1Entity || entity instanceof EyeTreeStage2Entity || entity instanceof EyeTreeStage3Entity) {
                trees.add(entity);
            }
        }

        int withTree = 0;
        int failures = 0;
        List<BlockPos> empty = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Vec3 around = !trees.isEmpty() && random.nextBoolean()
                    ? trees.get(random.nextInt(trees.size())).position() : origin;
            BlockPos pos = BlockPos.containing(around.add(random.nextInt(61) - 30, random.nextInt(41) - 20, random.nextInt(61) - 30));
            if (!level.isLoaded(pos)) {
                continue;
            }
            boolean original = originalFindsTree(level, pos.getX(), pos.getY(), pos.getZ());
            boolean patched = SpreadCheck.anyTreeNear(level, pos.getX(), pos.getY(), pos.getZ());
            if (original != patched) {
                failures++;
                if (failures <= 10) {
                    say(ctx, "Mismatch at " + pos.toShortString() + ": original finds a tree " + original + ", patch " + patched);
                }
            }
            if (original) {
                withTree++;
            } else {
                empty.add(pos);
            }
        }

        // Time the whole procedure where no tree is near (it changes nothing there, with or without the patch).
        long originalNanos = 0;
        long patchedNanos = 0;
        for (int i = 0; i < empty.size(); i++) {
            BlockPos pos = empty.get(i);
            for (int pass = 0; pass < 2; pass++) {
                boolean patch = (i + pass) % 2 == 0;
                EyesPerformanceFix.enabled = patch;
                long start = System.nanoTime();
                try {
                    BlockSpreadProcedure.execute(level, pos.getX(), pos.getY(), pos.getZ());
                } finally {
                    EyesPerformanceFix.enabled = true;
                }
                long nanos = System.nanoTime() - start;
                if (patch) {
                    patchedNanos += nanos;
                } else {
                    originalNanos += nanos;
                }
            }
        }
        int timed = Math.max(1, empty.size());
        say(ctx, String.format("%s spread: %d spots (%d with a tree in reach), %d mismatches. "
                        + "Time per call without a tree: original %.2f us, patched %.2f us.",
                failures == 0 ? "PASS" : "FAIL", withTree + empty.size(), withTree, failures,
                originalNanos / 1000.0 / timed, patchedNanos / 1000.0 / timed));
        return failures == 0 ? 1 : 0;
    }

    /** The original's three searches, plainly: does any loop find the entity class it acts on? */
    private static boolean originalFindsTree(ServerLevel level, double x, double y, double z) {
        Vec3 center = new Vec3(x, y, z);
        AABB at = new AABB(center, center);
        return level.getEntitiesOfClass(Entity.class, at.inflate(5.0), e -> true).stream().anyMatch(e -> e instanceof EyeSourceStage1Entity)
                || level.getEntitiesOfClass(Entity.class, at.inflate(10.0), e -> true).stream().anyMatch(e -> e instanceof EyeTreeStage2Entity)
                || level.getEntitiesOfClass(Entity.class, at.inflate(20.0), e -> true).stream().anyMatch(e -> e instanceof EyeTreeStage3Entity);
    }

    // ---------------------------------------------------------------- tags

    /**
     * Every id string in the patched procedures: the cached resource location must equal a new one,
     * and the cached tag key must be the very object the original's call returns.
     */
    private static int tags(CommandContext<CommandSourceStack> ctx) throws Exception {
        int checked = 0;
        int failures = 0;
        for (Class<?> procedure : List.of(BlockSpreadProcedure.class, AttackAiThingProcedure.class,
                EyeColonyStage3OnEntityTickUpdateProcedure.class, EyeTreeStage3OnEntityTickUpdateProcedure.class)) {
            for (String id : idStrings(procedure)) {
                ResourceLocation fresh = new ResourceLocation(id);
                ResourceLocation cached = CachedIds.location(id);
                boolean same = fresh.equals(cached) && CachedIds.location(id) == cached;
                if (procedure == BlockSpreadProcedure.class) {
                    same &= CachedIds.blockTag(cached) == BlockTags.create(fresh);
                } else {
                    same &= CachedIds.tag(Registries.ENTITY_TYPE, cached) == TagKey.create(Registries.ENTITY_TYPE, fresh);
                }
                checked++;
                if (!same) {
                    failures++;
                    say(ctx, "Mismatch for " + id + " in " + procedure.getSimpleName());
                }
            }
        }
        say(ctx, String.format("%s tags: %d ids checked, %d mismatches.", failures == 0 ? "PASS" : "FAIL", checked, failures));
        return failures == 0 ? 1 : 0;
    }

    /** The "namespace:path" string constants in a procedure's execute methods. */
    private static Set<String> idStrings(Class<?> procedure) throws Exception {
        Set<String> ids = new LinkedHashSet<>();
        try (InputStream in = procedure.getResourceAsStream(procedure.getSimpleName() + ".class")) {
            ClassNode node = new ClassNode();
            new ClassReader(Objects.requireNonNull(in)).accept(node, 0);
            for (MethodNode method : node.methods) {
                if (!method.name.equals("execute")) {
                    continue;
                }
                for (AbstractInsnNode insn : method.instructions) {
                    if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String s && s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                        ids.add(s);
                    }
                }
            }
        }
        return ids;
    }

    // ---------------------------------------------------------------- watch

    private static int startWatch(CommandContext<CommandSourceStack> ctx) {
        watch = new Watch(IntegerArgumentType.getInteger(ctx, "ticks"), ctx.getSource());
        effectEvents = 0;
        countingEffects = true;
        say(ctx, "Watching for " + watch.ticksLeft + " ticks, patch " + (EyesPerformanceFix.enabled ? "on" : "off"));
        return 1;
    }

    /**
     * Over many ticks of the running game: every eYe mob that stays in a satellite's range keeps
     * Speed III, Jump Boost II and Resistance I, and every mob that stays in a tree's range keeps Eye
     * Sickness, at the end of every tick, with time left for its next tick. Also counts effect events.
     */
    private static final class Watch {
        private final CommandSourceStack source;
        private final int totalTicks;
        private int ticksLeft;
        private long checks;
        private long failures;
        private int minSatelliteLeft = Integer.MAX_VALUE;
        private int minSicknessLeft = Integer.MAX_VALUE;
        private long tickNanos;
        // How many ticks in a row each entity has been in a source's range.
        private Map<Long, Integer> inRange = new HashMap<>();

        Watch(int ticks, CommandSourceStack source) {
            this.totalTicks = ticks;
            this.ticksLeft = ticks;
            this.source = source;
        }

        void tick(MinecraftServer server) {
            // This tick's time, recorded by the server just before the end-of-tick event.
            tickNanos += server.tickTimes[server.getTickCount() % 100];
            Map<Long, Integer> next = new HashMap<>();
            TagKey<net.minecraft.world.entity.EntityType<?>> eyeMob = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("eye:mob"));
            for (ServerLevel level : server.getAllLevels()) {
                for (Entity sourceEntity : level.getAllEntities()) {
                    boolean satellite = sourceEntity instanceof EyeColonyStage3Entity;
                    if (!satellite && !(sourceEntity instanceof EyeTreeStage3Entity)) {
                        continue;
                    }
                    Vec3 center = sourceEntity.position();
                    AABB box = new AABB(center, center).inflate(satellite ? 125.0 : 50.0);
                    for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box, e -> true)) {
                        boolean affected = satellite ? entity.getType().is(eyeMob) : entity instanceof Mob;
                        if (!affected || entity.isRemoved()) {
                            continue;
                        }
                        long key = ((long) sourceEntity.getId() << 32) | (entity.getId() & 0xffffffffL);
                        int streak = inRange.getOrDefault(key, 0) + 1;
                        next.put(key, streak);
                        if (streak < 3) {
                            continue;
                        }
                        checks++;
                        if (satellite) {
                            int left = Math.min(left(entity, MobEffects.MOVEMENT_SPEED, 2),
                                    Math.min(left(entity, MobEffects.JUMP, 1), left(entity, MobEffects.DAMAGE_RESISTANCE, 0)));
                            minSatelliteLeft = Math.min(minSatelliteLeft, left);
                            if (left < 2) {
                                fail(sourceEntity, entity, "satellite effects " + entity.getActiveEffects());
                            }
                        } else {
                            MobEffectInstance sickness = entity.getEffect(TheEyesAreBackModMobEffects.EYE_SICKNESS.get());
                            if (sickness == null) {
                                fail(sourceEntity, entity, "no Eye Sickness");
                            } else if (sickness.getAmplifier() == 0) {
                                minSicknessLeft = Math.min(minSicknessLeft, sickness.getDuration());
                                if (sickness.getDuration() < 5978) {
                                    fail(sourceEntity, entity, "Eye Sickness " + sickness);
                                }
                            }
                        }
                    }
                }
            }
            inRange = next;
            if (--ticksLeft <= 0) {
                countingEffects = false;
                String message = String.format("%s watch: %d ticks, %d mob-ticks checked, %d failures, patch %s. "
                                + "Least time left: satellite effects %s, level I Eye Sickness %s. Effect events: %.1f a tick. "
                                + "Mean tick time: %.2f ms.",
                        failures == 0 ? "PASS" : "FAIL", totalTicks, checks, failures, EyesPerformanceFix.enabled ? "on" : "off",
                        minSatelliteLeft == Integer.MAX_VALUE ? "-" : minSatelliteLeft,
                        minSicknessLeft == Integer.MAX_VALUE ? "-" : minSicknessLeft, effectEvents / (double) totalTicks,
                        tickNanos / 1e6 / totalTicks);
                LOGGER.info(message);
                source.sendSuccess(() -> Component.literal(message), false);
                watch = null;
            }
        }

        private static int left(LivingEntity entity, MobEffect type, int amplifier) {
            MobEffectInstance effect = entity.getEffect(type);
            return effect != null && effect.getAmplifier() == amplifier ? effect.getDuration() : -1;
        }

        private void fail(Entity source, Entity entity, String what) {
            failures++;
            if (failures <= 10) {
                String message = "Watch failure near " + describe(source) + " on " + describe(entity) + ": " + what;
                LOGGER.info(message);
                this.source.sendSuccess(() -> Component.literal(message), false);
            }
        }
    }
}

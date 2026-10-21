package com.rinkynooble.eyesperformancefix;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Logs, for each class this mod patches, which changes took effect.
 *
 * <p>Every injector is optional ({@code defaultRequire: 0}), so an eYes version with different
 * code still loads and the change simply does nothing. To tell the two apart, this checks the
 * patched class for calls to each of the mixin's handler methods.
 */
public class MixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogManager.getLogger("eYes Performance Fix");
    private static final String HANDLER_PREFIX = "eyesperformancefix$";
    private static final Set<String> INJECTOR_ANNOTATIONS = Set.of(
            "Lorg/spongepowered/asm/mixin/injection/Inject;",
            "Lorg/spongepowered/asm/mixin/injection/Redirect;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;"
    );

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        List<String> expected = new ArrayList<>();
        for (MethodNode method : mixinInfo.getClassNode(0).methods) {
            if (method.name.startsWith(HANDLER_PREFIX) && isInjector(method)) {
                expected.add(method.name);
            }
        }
        if (expected.isEmpty()) {
            return;
        }

        Set<String> called = new HashSet<>();
        for (MethodNode method : targetClass.methods) {
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode call && call.name.contains(HANDLER_PREFIX)) {
                    called.add(call.name);
                }
            }
        }

        List<String> missing = new ArrayList<>();
        for (String handler : expected) {
            if (called.stream().noneMatch(name -> name.endsWith(handler))) {
                missing.add(handler.substring(HANDLER_PREFIX.length()));
            }
        }
        String simpleName = targetClassName.substring(targetClassName.lastIndexOf('.') + 1);
        if (missing.isEmpty()) {
            LOGGER.info("Patched {}: {} of {} changes applied.", simpleName, expected.size(), expected.size());
        } else {
            LOGGER.warn("Patched {}: {} of {} changes applied. Not applied, so the original code runs there: {}",
                    simpleName, expected.size() - missing.size(), expected.size(), missing);
        }
    }

    private static boolean isInjector(MethodNode method) {
        return hasInjectorAnnotation(method.visibleAnnotations) || hasInjectorAnnotation(method.invisibleAnnotations);
    }

    private static boolean hasInjectorAnnotation(List<AnnotationNode> annotations) {
        if (annotations == null) {
            return false;
        }
        for (AnnotationNode annotation : annotations) {
            if (INJECTOR_ANNOTATIONS.contains(annotation.desc)) {
                return true;
            }
        }
        return false;
    }
}

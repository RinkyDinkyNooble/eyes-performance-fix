package com.rinkynooble.eyesperformancefix;

import net.minecraftforge.fml.common.Mod;

@Mod(EyesPerformanceFix.MODID)
public class EyesPerformanceFix {
    public static final String MODID = "eyesperformancefix";

    /**
     * When false, every patched call does exactly what The eYes' own code does. Only the dev-only
     * check commands (src/verify) turn it off, to compare the patched and original code on the same world.
     */
    public static volatile boolean enabled = true;

    public EyesPerformanceFix() {
        // Everything happens in the mixins (see MixinPlugin for the startup log line).
    }
}

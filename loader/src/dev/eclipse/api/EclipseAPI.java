package dev.eclipse.api;

import java.util.function.UnaryOperator;
import java.util.logging.Logger;

/** What each mod gets handed. */
public interface EclipseAPI {
    String modId();
    Logger logger();
    EventBus events();

    /**
     * Patch a class's bytecode as it loads. Use the internal name, e.g. "net/minecraft/client/Minecraft".
     * Bring your own bytecode library (ASM, Javassist, ...) inside your mod jar to edit the bytes.
     */
    void registerTransformer(String internalClassName, UnaryOperator<byte[]> transformer);

    /** True if another mod with this id is loaded. */
    boolean isModLoaded(String modId);
}

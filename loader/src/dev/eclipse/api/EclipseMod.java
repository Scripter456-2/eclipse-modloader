package dev.eclipse.api;

/** Implement this in your mod's entrypoint class (must have a public no-arg constructor). */
public interface EclipseMod {
    /** Called once when the mod is loaded, in dependency order. */
    void onInitialize(EclipseAPI api);

    /** Called on JVM shutdown. Optional. */
    default void onShutdown() {}
}

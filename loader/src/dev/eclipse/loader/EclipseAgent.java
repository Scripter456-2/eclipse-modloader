package dev.eclipse.loader;

import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Entry point. Launch Minecraft with:  -javaagent:eclipse.jar
 * Optional: -Declipse.mods=/path/to/mods   (default: ./mods)
 */
public final class EclipseAgent {
    private static EclipseLoader loader;

    public static void premain(String args, Instrumentation inst) {
        Path mods = Paths.get(System.getProperty("eclipse.mods", "mods"));
        loader = new EclipseLoader(inst);
        loader.start(mods);
    }

    public static EclipseLoader getLoader() { return loader; }
}

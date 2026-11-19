package dev.eclipse.loader;

import dev.eclipse.api.*;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.function.UnaryOperator;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.stream.Stream;

public final class EclipseLoader {
    public static final String VERSION = "1.0.0";
    private static final Logger LOG = Logger.getLogger("Eclipse");

    private final Instrumentation inst;
    private final EventBus bus = new EventBus();
    private final Map<String, ModMetadata> discovered = new LinkedHashMap<>();
    private final Map<String, EclipseMod> loaded = new LinkedHashMap<>();
    private final Map<String, List<UnaryOperator<byte[]>>> transformers = new HashMap<>();

    EclipseLoader(Instrumentation inst) { this.inst = inst; }

    public EventBus events() { return bus; }

    void start(Path modsDir) {
        LOG.info("Eclipse " + VERSION + " starting, mods folder: " + modsDir.toAbsolutePath());
        installTransformerDispatcher();
        discover(modsDir);
        for (String id : loadOrder()) loadMod(discovered.get(id));
        LOG.info("Loaded " + loaded.size() + " mod(s): " + loaded.keySet());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (EclipseMod m : loaded.values()) {
                try { m.onShutdown(); } catch (Throwable t) { t.printStackTrace(); }
            }
        }, "Eclipse-Shutdown"));
    }

    // ---------- discovery ----------
    private void discover(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOG.severe("Cannot create mods folder: " + e);
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.toString().endsWith(".jar")).sorted().forEach(jar -> {
                try {
                    ModMetadata md = ModMetadata.read(jar);
                    if (md == null) return;
                    if (discovered.containsKey(md.id)) {
                        LOG.warning("Duplicate mod id '" + md.id + "' in " + jar.getFileName() + ", skipped");
                        return;
                    }
                    discovered.put(md.id, md);
                } catch (Exception e) {
                    LOG.warning("Skipping " + jar.getFileName() + ": " + e.getMessage());
                }
            });
        } catch (IOException e) {
            LOG.severe("Failed to scan mods folder: " + e);
        }
    }

    // ---------- dependency ordering ----------
    private List<String> loadOrder() {
        List<String> order = new ArrayList<>();
        Map<String, Integer> state = new HashMap<>(); // 1 = visiting, 2 = ok, 3 = failed
        for (String id : discovered.keySet()) visit(id, state, order, new ArrayDeque<>());
        return order;
    }

    private boolean visit(String id, Map<String, Integer> state, List<String> order, Deque<String> chain) {
        Integer s = state.get(id);
        if (s != null) {
            if (s == 1) { LOG.severe("Circular dependency: " + chain + " -> " + id); return false; }
            return s == 2;
        }
        ModMetadata md = discovered.get(id);
        if (md == null) return false;
        state.put(id, 1);
        chain.addLast(id);
        boolean ok = true;
        for (String dep : md.dependencies) {
            if (!discovered.containsKey(dep)) {
                LOG.severe("Mod '" + id + "' is missing dependency '" + dep + "'");
                ok = false;
            } else if (!visit(dep, state, order, chain)) {
                ok = false;
            }
        }
        chain.removeLast();
        state.put(id, ok ? 2 : 3);
        if (ok) order.add(id);
        return ok;
    }

    // ---------- loading ----------
    private void loadMod(ModMetadata md) {
        try {
            // Put the jar on the system classpath so mods can see Minecraft classes and vice versa.
            inst.appendToSystemClassLoaderSearch(new JarFile(md.jarPath.toFile()));
            Class<?> cls = Class.forName(md.entrypoint, true, ClassLoader.getSystemClassLoader());
            Object obj = cls.getDeclaredConstructor().newInstance();
            if (!(obj instanceof EclipseMod mod))
                throw new IllegalStateException(md.entrypoint + " does not implement EclipseMod");
            loaded.put(md.id, mod);
            mod.onInitialize(new Api(md));
            LOG.info("Loaded " + md.name + " v" + md.version);
        } catch (Throwable t) {
            loaded.remove(md.id);
            LOG.severe("Failed to load mod '" + md.id + "': " + t);
            t.printStackTrace();
        }
    }

    // ---------- bytecode transformers ----------
    private void installTransformerDispatcher() {
        inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader l, String name, Class<?> c, ProtectionDomain d, byte[] bytes) {
                List<UnaryOperator<byte[]>> list;
                synchronized (transformers) { list = transformers.get(name); }
                if (list == null) return null;
                byte[] out = bytes;
                for (UnaryOperator<byte[]> t : list) {
                    try {
                        byte[] r = t.apply(out);
                        if (r != null) out = r;
                    } catch (Throwable ex) {
                        LOG.severe("Transformer failed on " + name + ": " + ex);
                    }
                }
                return out;
            }
        });
    }

    private final class Api implements EclipseAPI {
        private final ModMetadata md;
        private final Logger logger;
        Api(ModMetadata md) { this.md = md; this.logger = Logger.getLogger("Eclipse/" + md.id); }
        public String modId() { return md.id; }
        public Logger logger() { return logger; }
        public EventBus events() { return bus; }
        public boolean isModLoaded(String id) { return loaded.containsKey(id); }
        public void registerTransformer(String cls, UnaryOperator<byte[]> t) {
            synchronized (transformers) {
                transformers.computeIfAbsent(cls.replace('.', '/'), k -> new ArrayList<>()).add(t);
            }
        }
    }
}

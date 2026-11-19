package dev.eclipse.loader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/** Parsed eclipse.mod.properties from a mod jar. */
final class ModMetadata {
    final String id, name, version, entrypoint;
    final List<String> dependencies;
    final Path jarPath;

    private ModMetadata(Properties p, Path jarPath) {
        this.jarPath = jarPath;
        this.id = require(p, "id");
        this.name = p.getProperty("name", id);
        this.version = p.getProperty("version", "0.0.0");
        this.entrypoint = require(p, "entrypoint");
        String deps = p.getProperty("dependencies", "").trim();
        List<String> d = new ArrayList<>();
        if (!deps.isEmpty()) for (String s : deps.split(",")) if (!s.isBlank()) d.add(s.trim());
        this.dependencies = d;
    }

    private static String require(Properties p, String key) {
        String v = p.getProperty(key);
        if (v == null || v.isBlank()) throw new IllegalArgumentException("missing '" + key + "'");
        return v.trim();
    }

    static ModMetadata read(Path jar) throws IOException {
        try (JarFile jf = new JarFile(jar.toFile())) {
            ZipEntry e = jf.getEntry("eclipse.mod.properties");
            if (e == null) return null; // not an Eclipse mod
            Properties p = new Properties();
            try (InputStream in = jf.getInputStream(e)) { p.load(in); }
            return new ModMetadata(p, jar);
        }
    }
}

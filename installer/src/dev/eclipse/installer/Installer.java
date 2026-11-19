package dev.eclipse.installer;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Eclipse installer: double-click to open the window, or use --install/--uninstall <dir> from a terminal. */
public final class Installer {
    static final String PROFILE_ID = "eclipse-modloader";
    static final String JAVA_ARG = "-javaagent:eclipse/eclipse.jar";

    interface Log { void log(String s); }

    // ------------------------------------------------------------ core logic
    static Path defaultDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home");
        if (os.contains("win")) {
            String appdata = System.getenv("APPDATA");
            return Paths.get(appdata != null ? appdata : home, ".minecraft");
        }
        if (os.contains("mac")) return Paths.get(home, "Library", "Application Support", "minecraft");
        return Paths.get(home, ".minecraft");
    }

    static void install(Path dir, boolean addProfile, boolean exampleMod, Log log) throws IOException {
        if (!Files.isDirectory(dir)) throw new IOException("That folder does not exist: " + dir);
        Path eclipseDir = dir.resolve("eclipse");
        Files.createDirectories(eclipseDir);
        copyResource("/eclipse.jar", eclipseDir.resolve("eclipse.jar"));
        log.log("Installed loader: " + eclipseDir.resolve("eclipse.jar"));

        Path mods = dir.resolve("mods");
        Files.createDirectories(mods);
        log.log("Mods folder: " + mods);
        if (exampleMod) {
            copyResource("/hello_mod.jar", mods.resolve("hello_mod.jar"));
            log.log("Installed example mod: hello_mod.jar");
        }

        if (addProfile) {
            if (!addLauncherProfile(dir, log)) {
                log.log("Add this to your launcher's JVM arguments by hand:  " + JAVA_ARG);
            }
        } else {
            log.log("Skipped launcher profile. Add this JVM argument by hand:  " + JAVA_ARG);
        }
        log.log("DONE. Open the Minecraft Launcher and pick the 'Eclipse' profile.");
    }

    static void uninstall(Path dir, Log log) throws IOException {
        if (!Files.isDirectory(dir)) throw new IOException("That folder does not exist: " + dir);
        Path eclipseDir = dir.resolve("eclipse");
        Path jar = eclipseDir.resolve("eclipse.jar");
        if (Files.deleteIfExists(jar)) log.log("Removed " + jar);
        else log.log("eclipse.jar not found (already removed?)");
        try { Files.deleteIfExists(eclipseDir); } catch (DirectoryNotEmptyException ignored) { }
        removeLauncherProfile(dir, log);
        log.log("DONE. Your mods folder was left untouched.");
    }

    private static void copyResource(String name, Path target) throws IOException {
        try (InputStream in = Installer.class.getResourceAsStream(name)) {
            if (in == null) throw new IOException("Installer is missing its bundled file " + name);
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // ------------------------------------------------------------ launcher_profiles.json
    private static boolean addLauncherProfile(Path dir, Log log) throws IOException {
        Path f = dir.resolve("launcher_profiles.json");
        if (!Files.isRegularFile(f)) {
            log.log("launcher_profiles.json not found. Open the Minecraft Launcher once, then install again.");
            return false;
        }
        String text = Files.readString(f);
        Files.copy(f, dir.resolve("launcher_profiles.json.eclipse-backup"), StandardCopyOption.REPLACE_EXISTING);
        text = stripProfile(text); // replace any older Eclipse profile

        int p = text.indexOf("\"profiles\"");
        int brace = p < 0 ? -1 : text.indexOf('{', p);
        if (brace < 0) {
            log.log("Could not find \"profiles\" in launcher_profiles.json.");
            return false;
        }
        int j = brace + 1;
        while (j < text.length() && Character.isWhitespace(text.charAt(j))) j++;
        boolean empty = j < text.length() && text.charAt(j) == '}';

        String now = Instant.now().toString();
        String entry = "\n    \"" + PROFILE_ID + "\": {"
                + "\"name\": \"Eclipse\", "
                + "\"type\": \"latest-release\", "
                + "\"icon\": \"Furnace\", "
                + "\"created\": \"" + now + "\", "
                + "\"lastUsed\": \"" + now + "\", "
                + "\"javaArgs\": \"-Xmx2G " + JAVA_ARG + "\"}" + (empty ? "" : ",");
        text = text.substring(0, brace + 1) + entry + text.substring(brace + 1);
        Files.writeString(f, text);
        log.log("Added launcher profile 'Eclipse' (backup: launcher_profiles.json.eclipse-backup)");
        return true;
    }

    private static void removeLauncherProfile(Path dir, Log log) throws IOException {
        Path f = dir.resolve("launcher_profiles.json");
        if (!Files.isRegularFile(f)) return;
        String text = Files.readString(f);
        String out = stripProfile(text);
        if (!out.equals(text)) {
            Files.copy(f, dir.resolve("launcher_profiles.json.eclipse-backup"), StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(f, out);
            log.log("Removed 'Eclipse' launcher profile");
        }
    }

    /** Removes the Eclipse profile entry and fixes the commas around it. */
    static String stripProfile(String text) {
        Matcher m = Pattern.compile("\"" + PROFILE_ID + "\"\\s*:\\s*\\{[^{}]*\\}").matcher(text);
        if (!m.find()) return text;
        int start = m.start(), end = m.end();
        int a = end;
        while (a < text.length() && Character.isWhitespace(text.charAt(a))) a++;
        int b = start - 1;
        while (b >= 0 && Character.isWhitespace(text.charAt(b))) b--;
        StringBuilder sb = new StringBuilder(text);
        if (a < text.length() && text.charAt(a) == ',') {
            sb.delete(start, a + 1);                       // entry followed by a comma
        } else if (b >= 0 && text.charAt(b) == ',') {
            sb.delete(b, end);                             // last entry: drop the comma before it
        } else {
            sb.delete(start, end);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------ UI
    public static void main(String[] args) {
        Log out = System.out::println;
        try {
            if (args.length >= 2 && args[0].equals("--install")) { install(Paths.get(args[1]), true, true, out); return; }
            if (args.length >= 2 && args[0].equals("--uninstall")) { uninstall(Paths.get(args[1]), out); return; }
        } catch (IOException e) { System.err.println("Error: " + e.getMessage()); System.exit(1); }
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("Usage: java -jar EclipseInstaller.jar [--install|--uninstall] <minecraft folder>");
            return;
        }
        SwingUtilities.invokeLater(Installer::showWindow);
    }

    private static void showWindow() {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) { }
        JFrame frame = new JFrame("Eclipse Modloader Installer");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JTextField dirField = new JTextField(defaultDir().toString(), 32);
        JButton browse = new JButton("Browse...");
        JCheckBox profileBox = new JCheckBox("Add 'Eclipse' profile to the Minecraft Launcher", true);
        JCheckBox exampleBox = new JCheckBox("Install example mod (hello_mod)", true);
        JButton installBtn = new JButton("Install");
        JButton uninstallBtn = new JButton("Uninstall");
        JTextArea logArea = new JTextArea(9, 50);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        Log log = s -> { logArea.append(s + "\n"); logArea.setCaretPosition(logArea.getDocument().getLength()); };

        browse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(dirField.getText());
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.setDialogTitle("Choose your Minecraft folder (the one with launcher_profiles.json)");
            if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION)
                dirField.setText(fc.getSelectedFile().getAbsolutePath());
        });
        installBtn.addActionListener(e -> run(frame, log, () ->
                install(Paths.get(dirField.getText().trim()), profileBox.isSelected(), exampleBox.isSelected(), log)));
        uninstallBtn.addActionListener(e -> {
            int ok = JOptionPane.showConfirmDialog(frame, "Remove Eclipse from this folder?\n(Your mods folder is kept.)",
                    "Uninstall", JOptionPane.OK_CANCEL_OPTION);
            if (ok == JOptionPane.OK_OPTION) run(frame, log, () -> uninstall(Paths.get(dirField.getText().trim()), log));
        });

        JPanel top = new JPanel(new BorderLayout(6, 6));
        top.add(new JLabel("Minecraft folder:"), BorderLayout.NORTH);
        top.add(dirField, BorderLayout.CENTER);
        top.add(browse, BorderLayout.EAST);

        JPanel options = new JPanel(new GridLayout(0, 1));
        options.add(profileBox);
        options.add(exampleBox);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(uninstallBtn);
        buttons.add(installBtn);

        JPanel north = new JPanel(new BorderLayout(6, 6));
        north.add(top, BorderLayout.NORTH);
        north.add(options, BorderLayout.CENTER);
        north.add(buttons, BorderLayout.SOUTH);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(north, BorderLayout.NORTH);
        root.add(new JScrollPane(logArea), BorderLayout.CENTER);
        frame.setContentPane(root);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private interface Task { void run() throws IOException; }

    private static void run(Component parent, Log log, Task t) {
        try { t.run(); }
        catch (Exception ex) {
            log.log("ERROR: " + ex.getMessage());
            JOptionPane.showMessageDialog(parent, ex.getMessage(), "Eclipse", JOptionPane.ERROR_MESSAGE);
        }
    }
}

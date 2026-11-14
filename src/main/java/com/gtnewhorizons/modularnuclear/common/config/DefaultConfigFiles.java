package com.gtnewhorizons.modularnuclear.common.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/*-
Note [Config files only override the built-in defaults] (generated)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Topic.  Every ModularNuclear setting has a built-in default in the jar.  A config file on disk overrides the
defaults it mentions, for as long as it exists, even after a new build changes them.  A copy of the defaults written
out at first launch would therefore freeze that build's defaults in the instance: later calibrations (the PHE heat
transfer multiplier, for one) would never reach it.

Design.  The mod never writes a config file on its own.  At startup it loads the built-in defaults and applies
whichever files exist; with none, the game runs on the defaults alone, and the log says how to get the files.  Server
admins who want to customize run `/modularnuclear config generate` (op level 3, or the server console), which writes
the whole set from the jar as a starting point.  By default it writes nothing if any of the files exists; `missing`
writes only the missing ones and `force` overwrites them all.  The README it writes tells admins to delete the files
they don't change, so those keep following the defaults.

Moving parts:
  ModularNuclearConfig.init           reads modularnuclear.cfg (the Forge config) only if it exists.
  ReactorDataRegistry.init            applies config/modularnuclear/{physics,tiers,fuels,components}.yaml.
  CoolantLoopDataRegistry.init        applies config/modularnuclear/coolant_loops/*.yaml.
  DefaultConfigFiles.generate         writes the files: the YAML files are copied from
                                      assets/modularnuclear/default_config in the jar, comments and all, and
                                      ModularNuclearConfig.writeDefaults writes the Forge config.
  CommandModularNuclear               the command.

Wrinkles:
  (W1) Forge's Configuration constructor loads its file, and loading creates the file if it is missing.  So
       ModularNuclearConfig.init only constructs one for a file that exists.
  (W2) ModularNuclearConfig declares each setting with its current value as the default, and a Forge config that
       was read has replaced those values.  writeDefaults puts back the values captured when the class was
       initialized (BUILT_IN), writes the file, then restores the settings in use.
  (W3) Writing the files doesn't change the running game: they are read at startup, so changes take effect after a
       restart.  The command says so.

CoolantLoopDataRegistryTest.testDefaultsLoadWithoutWritingConfigFiles and DefaultConfigFilesTest check this.
*/
public final class DefaultConfigFiles {

    private DefaultConfigFiles() {}

    public static final String FORGE_CONFIG = "modularnuclear.cfg";
    public static final String README = "modularnuclear/README.txt";
    private static final String DEFAULTS = "assets/modularnuclear/default_config/";

    public enum Mode {
        /** Write them all, but only if none of them exists. */
        IF_NONE_EXIST,
        /** Write the missing ones, leaving existing files alone. */
        MISSING_ONLY,
        /** Write them all, overwriting existing files. */
        OVERWRITE
    }

    public static final class Result {

        /** Paths relative to the configuration directory. */
        public final List<String> written, existing;

        /** True when nothing was written because some files exist (mode IF_NONE_EXIST). */
        public final boolean refused;

        Result(List<String> written, List<String> existing, boolean refused) {
            this.written = Collections.unmodifiableList(written);
            this.existing = Collections.unmodifiableList(existing);
            this.refused = refused;
        }
    }

    /** Every config file the mod reads, relative to the configuration directory, plus the README. */
    public static List<String> paths() {
        List<String> paths = new ArrayList<>();
        paths.add(FORGE_CONFIG);
        paths.add(README);
        for (String name : ReactorDataRegistry.OVERRIDE_FILES) paths.add("modularnuclear/" + name);
        for (String name : CoolantLoopDataRegistry.FILES) paths.add("modularnuclear/coolant_loops/" + name);
        return paths;
    }

    /** The config files that exist in configDir. */
    public static List<String> existing(File configDir) {
        List<String> existing = new ArrayList<>();
        for (String path : paths()) {
            if (new File(configDir, path).exists()) existing.add(path);
        }
        return existing;
    }

    public static synchronized Result generate(File configDir, Mode mode) throws IOException {
        List<String> existing = existing(configDir);
        List<String> written = new ArrayList<>();
        if (mode == Mode.IF_NONE_EXIST && !existing.isEmpty()) {
            return new Result(written, existing, true);
        }
        for (String path : paths()) {
            if (mode == Mode.MISSING_ONLY && existing.contains(path)) continue;
            File file = new File(configDir, path);
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("cannot create " + parent);
            }
            if (path.equals(FORGE_CONFIG)) {
                ModularNuclearConfig.writeDefaults(file);
            } else if (path.equals(README)) {
                Files.write(file.toPath(), readme().getBytes(StandardCharsets.UTF_8));
            } else {
                copyDefault(path.substring("modularnuclear/".length()), file);
            }
            written.add(path);
        }
        return new Result(written, existing, false);
    }

    private static void copyDefault(String name, File file) throws IOException {
        String resource = DEFAULTS + name;
        try (InputStream in = open(resource)) {
            if (in == null) throw new IOException(resource + " is missing from the jar");
            Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static InputStream open(String resource) {
        InputStream in = DefaultConfigFiles.class.getClassLoader()
            .getResourceAsStream(resource);
        ClassLoader context = Thread.currentThread()
            .getContextClassLoader();
        if (in == null && context != null) in = context.getResourceAsStream(resource);
        return in;
    }

    private static String readme() {
        return """
            Modular Nuclear Reactor - Configuration Directory
            ==================================================

            Every setting has a built-in default in the mod's jar.  The files here override
            those defaults: each file, and each setting in it, only for as long as it exists.

            These files were written by '/modularnuclear config generate', with the defaults
            of the version that wrote them.  A setting left in a file keeps that value even
            after an update changes its default, so delete the files (and the settings) you
            don't mean to change.  Changes take effect when the game or server restarts.

            ../modularnuclear.cfg : client visuals, simulation timing, GUI sync, and the
                                    item and fluid allowlists for the reactor core
            physics.yaml          : global reactor physics constants (e.g. hu_per_degree)
            tiers.yaml            : reactor pressure tube tiers
            fuels.yaml            : fuel parameters
            components.yaml       : moderators, reflectors, control rods, vents, exchangers,
                                    insulators, radiovoltaics, and coolant fluids/items
            coolant_loops/        : coolant loop physics, fluids, pipes and devices

            Overriding rules for the YAML files:
            1. Partial override: specify only the fields you wish to change. All omitted fields
               keep their built-in defaults.
            2. Disabling items : to remove a default fuel or component, specify its id with
               'remove: true' (or 'enabled: false', or map its key to null in YAML).
            3. Adding items    : to add a new fuel or component, specify a unique id and required fields.
            """;
    }
}

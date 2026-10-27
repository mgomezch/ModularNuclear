
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

tasks.test {
    useJUnitPlatform()
}

val teavmClasspath by configurations.creating
val jvmDowngraderConfig by configurations.creating

dependencies {
    teavmClasspath("org.teavm:teavm-cli:0.10.2")
    teavmClasspath("org.teavm:teavm-classlib:0.10.2")
    teavmClasspath("org.teavm:teavm-tooling:0.10.2")
    teavmClasspath("org.teavm:teavm-interop:0.10.2")
    teavmClasspath("org.teavm:teavm-jso:0.10.2")
    jvmDowngraderConfig("xyz.wagyourtail.jvmdowngrader:jvmdowngrader-java-api:1.3.5:downgraded-8") {
        isTransitive = false
    }
}

// The default YAML config is converted to JSON constants at build time, so the WebAssembly simulator (which has no
// YAML parser) loads exactly the same defaults as the game.
val configGen by sourceSets.creating

dependencies {
    "configGenImplementation"("org.yaml:snakeyaml:1.33")
}

val reactorDefaultsSourceDir = layout.buildDirectory.dir("generated/sources/reactorDefaults/java")
val defaultConfigDir = file("src/main/resources/assets/modularnuclear/default_config")
val coolantDefaultConfigDir = file("src/main/resources/assets/coolantloops/default_config")

val generateReactorDefaults by tasks.registering(JavaExec::class) {
    group = "build"
    description = "Embeds the default reactor and coolant loop YAML configs as JSON constants"
    classpath = configGen.runtimeClasspath
    mainClass.set("com.gtnewhorizons.modularnuclear.configgen.ReactorDefaultsGenerator")
    inputs.dir(defaultConfigDir)
    inputs.dir(coolantDefaultConfigDir)
    outputs.dir(reactorDefaultsSourceDir)
    args(
        reactorDefaultsSourceDir.get().asFile.absolutePath,
        defaultConfigDir.absolutePath, "com.gtnewhorizons.modularnuclear.common.config", "ReactorDefaultsJson",
        "physics", "tiers", "fuels", "components", "--",
        coolantDefaultConfigDir.absolutePath, "com.gtnewhorizons.modularnuclear.common.config", "CoolantLoopDefaultsJson",
        "physics", "fluids", "pipes", "devices")
}

sourceSets.main {
    java.srcDir(generateReactorDefaults.map { reactorDefaultsSourceDir.get() })
}

val compileTeavmStubs by tasks.registering(JavaCompile::class) {
    source = fileTree("src/teavm/java")
    destinationDirectory.set(layout.buildDirectory.dir("teavm-stubs"))
    classpath = files()
    options.release.set(8)
}

val generateWasm by tasks.registering(JavaExec::class) {
    dependsOn("downgradeMainClasses", compileTeavmStubs)
    group = "build"
    description = "Compiles core nuclear simulation engine to WebAssembly using TeaVM"

    val javaToolchains = project.extensions.getByType(JavaToolchainService::class.java)
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    })

    mainClass.set("org.teavm.cli.TeaVMRunner")
    classpath = teavmClasspath

    val outputWasmDir = layout.buildDirectory.dir("nuclear-sim-wasm").get().asFile
    val resourceDir = file("src/main/resources/nuclear-sim")
    val distDir = layout.buildDirectory.dir("nuclear-sim-dist").get().asFile
    val iconsSourceDir = file("src/main/resources/assets/modularnuclear/textures/sim/icons")

    val jvmDowngraderJar = jvmDowngraderConfig.singleFile
    val downgradedClassesDir = layout.buildDirectory.dir("tmp/downgradeMainClasses/main").get().asFile
    val teavmStubsDir = layout.buildDirectory.dir("teavm-stubs").get().asFile

    args = listOf(
        "com.gtnewhorizons.modularnuclear.common.nuclear.standalone.NuclearSimWasmBridge",
        "-t", "wasm",
        "-d", outputWasmDir.absolutePath,
        "-f", "nuclear-sim.wasm",
        "-O", "1",
        "--min-heap", "4",
        "--max-heap", "64",
        "-p", downgradedClassesDir.absolutePath,
        "-p", teavmStubsDir.absolutePath,
        "-p", jvmDowngraderJar.absolutePath
    )

    doLast {
        // Generated WebAssembly output lives only under build/; src/main/resources/nuclear-sim holds hand-written
        // sources (bridge and README) that are packaged alongside it.
        copy {
            from(outputWasmDir)
            into(distDir)
            include("nuclear-sim.wasm", "nuclear-sim.wasm-runtime.js")
        }
        copy {
            from(resourceDir)
            into(distDir)
            include("nuclear-sim-bridge.js", "README.md")
        }
        copy {
            from(iconsSourceDir)
            into(file("${distDir.absolutePath}/icons"))
            include("*.png")
        }
        println("WebAssembly compilation complete: ${outputWasmDir.absolutePath}/nuclear-sim.wasm")
        println("Standalone web distribution packaged: ${distDir.absolutePath}")
    }
}

val exportStaticDist by tasks.registering(JavaExec::class) {
    dependsOn(generateWasm)
    group = "build"
    description = "Exports standalone index.html into nuclear-sim-dist"

    val javaToolchains = project.extensions.getByType(JavaToolchainService::class.java)
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
    })
    val downgradedClassesDir = layout.buildDirectory.dir("tmp/downgradeMainClasses/main").get().asFile
    val teavmStubsDir = layout.buildDirectory.dir("teavm-stubs").get().asFile
    val jvmDowngraderJar = jvmDowngraderConfig.singleFile
    val distDir = layout.buildDirectory.dir("nuclear-sim-dist").get().asFile

    mainClass.set("com.gtnewhorizons.modularnuclear.common.nuclear.standalone.NuclearSimulationWebServer")
    classpath = files(downgradedClassesDir, teavmStubsDir, jvmDowngraderJar)
    val presetsFile = file("${project.rootDir}/../../tools/nuclear_ceiling_best.json")
    val altPresetsFile = file("/home/mgomezch/stuff/dev/nh-dev/tools/nuclear_ceiling_best.json")
    val resolvedPresets = if (presetsFile.exists()) presetsFile else altPresetsFile
    args = listOf(
        "--export-html", file("${distDir.absolutePath}/index.html").absolutePath,
        "--presets", resolvedPresets.absolutePath
    )

    doLast {
        if (resolvedPresets.exists()) {
            copy {
                from(resolvedPresets)
                into(distDir)
            }
        }
        val localShareDir = file("${System.getProperty("user.home")}/.local/share/modular-nuclear/dist")
        if (localShareDir.exists() || localShareDir.mkdirs()) {
            copy {
                from(distDir)
                into(localShareDir)
            }
            val localStagingDir = file("${localShareDir.absolutePath}/staging")
            if (localStagingDir.exists() || localStagingDir.mkdirs()) {
                copy {
                    from(distDir)
                    into(localStagingDir)
                }
            }
            println("Updated local share static web distribution (production and staging): ${localShareDir.absolutePath}")
        }
    }
}

generateWasm.configure {
    finalizedBy(exportStaticDist)
}

val wasmSmokeTest by tasks.registering(Exec::class) {
    dependsOn(generateWasm)
    group = "verification"
    description = "Loads the built nuclear-sim.wasm in Node and exercises the bridge entry points used by the web page"
    val distDir = layout.buildDirectory.dir("nuclear-sim-dist").get().asFile
    commandLine("node", file("src/test/wasm/nuclear-sim-smoke.mjs").absolutePath, distDir.absolutePath)
}

val deploySimulatorStaging by tasks.registering(Exec::class) {
    dependsOn(exportStaticDist)
    group = "publishing"
    description = "Pushes latest build/nuclear-sim-dist to /staging/ on the gh-pages branch of ModularNuclear"
    commandLine(
        "bash", "-c",
        """
        set -e
        ROOT_DIR="${'$'}(git rev-parse --show-toplevel)"
        TMP_DIR="${'$'}(mktemp -d)"
        git fetch github gh-pages:gh-pages || git fetch github gh-pages
        git worktree add "${'$'}TMP_DIR" gh-pages
        mkdir -p "${'$'}TMP_DIR/staging"
        cp -r build/nuclear-sim-dist/* "${'$'}TMP_DIR/staging/"
        touch "${'$'}TMP_DIR/.nojekyll"
        cd "${'$'}TMP_DIR"
        git add staging .nojekyll
        if git diff --cached --quiet; then
            echo "No changes to commit for staging."
        else
            git commit -m "deploy(staging): update simulator webapp in /staging/"
            git push github gh-pages
            git push forgejo gh-pages || true
            echo "Successfully deployed simulator to ModularNuclear GitHub Pages (staging)!"
        fi
        cd "${'$'}ROOT_DIR"
        git worktree remove --force "${'$'}TMP_DIR"
        """
    )
}

val deploySimulatorProduction by tasks.registering(Exec::class) {
    dependsOn(exportStaticDist)
    group = "publishing"
    description = "Pushes latest build/nuclear-sim-dist to root / on the gh-pages branch of ModularNuclear"
    commandLine(
        "bash", "-c",
        """
        set -e
        ROOT_DIR="${'$'}(git rev-parse --show-toplevel)"
        TMP_DIR="${'$'}(mktemp -d)"
        git fetch github gh-pages:gh-pages || git fetch github gh-pages
        git worktree add "${'$'}TMP_DIR" gh-pages
        cp -r build/nuclear-sim-dist/* "${'$'}TMP_DIR/"
        mkdir -p "${'$'}TMP_DIR/staging"
        cp -r build/nuclear-sim-dist/* "${'$'}TMP_DIR/staging/"
        touch "${'$'}TMP_DIR/.nojekyll"
        cd "${'$'}TMP_DIR"
        git add -A
        if git diff --cached --quiet; then
            echo "No changes to commit for production."
        else
            git commit -m "deploy(prod): update simulator webapp in root /"
            git push github gh-pages
            git push forgejo gh-pages || true
            echo "Successfully deployed simulator to ModularNuclear GitHub Pages (production)!"
        fi
        cd "${'$'}ROOT_DIR"
        git worktree remove --force "${'$'}TMP_DIR"
        """
    )
}

val publishPages by tasks.registering {
    dependsOn(deploySimulatorStaging)
    group = "publishing"
    description = "Deploys latest simulator build to staging on ModularNuclear GitHub Pages"
}

val generateProceduralTextures by tasks.registering(Exec::class) {
    group = "build"
    description = "Procedurally generates all nuclear textures (atlas, arrows, overlays, fluids, cells, casings, plates) from pure code"
    commandLine("python3", file("tools/textures/generate_all.py").absolutePath)
    inputs.dir(file("tools/textures"))
    outputs.dir(file("src/main/resources/assets/modularnuclear/textures"))
}

tasks.processResources {
    dependsOn(generateProceduralTextures)
}

tasks.named("sourcesJar") {
    dependsOn(generateProceduralTextures)
}

val generateAllTextures by tasks.registering {
    dependsOn(generateProceduralTextures)
    group = "build"
    description = "Regenerates all procedural textures"
}

tasks.test {
    systemProperty("gt.recipebuilder.debug", "false")
}

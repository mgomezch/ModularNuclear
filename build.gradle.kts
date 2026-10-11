
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

val generateWasm by tasks.registering(JavaExec::class) {
    dependsOn("downgradeMainClasses")
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

    args = listOf(
        "com.gtnewhorizons.modularnuclear.common.nuclear.standalone.NuclearSimWasmBridge",
        "-t", "wasm",
        "-d", outputWasmDir.absolutePath,
        "-f", "nuclear-sim.wasm",
        "-O", "1",
        "--min-heap", "4",
        "--max-heap", "64",
        "-p", downgradedClassesDir.absolutePath,
        "-p", jvmDowngraderJar.absolutePath
    )

    doLast {
        copy {
            from(outputWasmDir)
            into(resourceDir)
            include("nuclear-sim.wasm", "nuclear-sim.wasm-runtime.js")
        }
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
        println("WebAssembly compilation complete: ${resourceDir.absolutePath}/nuclear-sim.wasm")
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
    val jvmDowngraderJar = jvmDowngraderConfig.singleFile
    val distDir = layout.buildDirectory.dir("nuclear-sim-dist").get().asFile

    mainClass.set("com.gtnewhorizons.modularnuclear.common.nuclear.standalone.NuclearSimulationWebServer")
    classpath = files(downgradedClassesDir, jvmDowngraderJar)
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

val generateFuelStatsCharts by tasks.registering(JavaExec::class) {
    dependsOn(tasks.compileJava)
    group = "build"
    description = "Generates nuclear fuel stats charts (reactivity and temperature durability damage curves)"

    mainClass.set("com.gtnewhorizons.modularnuclear.common.nuclear.FuelDamageCurveSolver")
    classpath = files(tasks.compileJava.get().destinationDirectory)

    val outputDir = file("src/main/resources/assets/modularnuclear/textures/gui/nei/fuelstats")
    args = listOf(outputDir.absolutePath)
}

val generateProceduralTextures by tasks.registering(Exec::class) {
    group = "build"
    description = "Procedurally generates all nuclear textures (atlas, arrows, overlays, fluids, cells, casings, plates) from pure code"
    commandLine("python3", file("tools/textures/generate_all.py").absolutePath)
}

val generateAllTextures by tasks.registering {
    dependsOn(generateFuelStatsCharts, generateProceduralTextures)
    group = "build"
    description = "Regenerates all procedural textures (fuel stats charts and procedural art assets)"
}




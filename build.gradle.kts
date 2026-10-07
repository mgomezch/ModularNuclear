
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

tasks.test {
    useJUnitPlatform()
}

val teavmClasspath by configurations.creating

dependencies {
    teavmClasspath("org.teavm:teavm-cli:0.10.2")
    teavmClasspath("org.teavm:teavm-classlib:0.10.2")
    teavmClasspath("org.teavm:teavm-tooling:0.10.2")
    teavmClasspath("org.teavm:teavm-interop:0.10.2")
    teavmClasspath("org.teavm:teavm-jso:0.10.2")
}

val generateWasm by tasks.registering(JavaExec::class) {
    dependsOn("downgradeMainClasses")
    group = "build"
    description = "Compiles core nuclear simulation engine to WebAssembly using TeaVM"

    val jdk17 = file("/home/mgomezch/.gradle/jdks/azul_systems__inc_-17-amd64-linux.2/bin/java")
    val jdk21 = file("/home/mgomezch/.gradle/jdks/azul_systems__inc_-21-amd64-linux.2/bin/java")
    if (jdk17.exists()) {
        setExecutable(jdk17.absolutePath)
    } else if (jdk21.exists()) {
        setExecutable(jdk21.absolutePath)
    }

    mainClass.set("org.teavm.cli.TeaVMRunner")
    classpath = teavmClasspath

    val outputWasmDir = layout.buildDirectory.dir("nuclear-sim-wasm").get().asFile
    val resourceDir = file("src/main/resources/nuclear-sim")
    val distDir = layout.buildDirectory.dir("nuclear-sim-dist").get().asFile
    val iconsSourceDir = file("src/main/resources/assets/modularnuclear/textures/sim/icons")

    val jvmDowngraderJar = file("/home/mgomezch/.gradle/caches/modules-2/files-2.1/xyz.wagyourtail.jvmdowngrader/jvmdowngrader-java-api/1.3.5/37ff40ae57b7293af685185c291d0b52d73008a8/jvmdowngrader-java-api-1.3.5-downgraded-8.jar")
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

    val jdk17 = file("/home/mgomezch/.gradle/jdks/azul_systems__inc_-17-amd64-linux.2/bin/java")
    val jdk21 = file("/home/mgomezch/.gradle/jdks/azul_systems__inc_-21-amd64-linux.2/bin/java")
    if (jdk17.exists()) {
        setExecutable(jdk17.absolutePath)
    } else if (jdk21.exists()) {
        setExecutable(jdk21.absolutePath)
    }
    val downgradedClassesDir = layout.buildDirectory.dir("tmp/downgradeMainClasses/main").get().asFile
    val jvmDowngraderJar = file("/home/mgomezch/.gradle/caches/modules-2/files-2.1/xyz.wagyourtail.jvmdowngrader/jvmdowngrader-java-api/1.3.5/37ff40ae57b7293af685185c291d0b52d73008a8/jvmdowngrader-java-api-1.3.5-downgraded-8.jar")
    val distDir = layout.buildDirectory.dir("nuclear-sim-dist").get().asFile

    mainClass.set("com.gtnewhorizons.modularnuclear.common.nuclear.standalone.NuclearSimulationWebServer")
    classpath = files(downgradedClassesDir, jvmDowngraderJar)
    args = listOf("--export-html", file("${distDir.absolutePath}/index.html").absolutePath)

    doLast {
        val localShareDir = file("${System.getProperty("user.home")}/.local/share/modular-nuclear/dist")
        if (localShareDir.exists() || localShareDir.mkdirs()) {
            copy {
                from(distDir)
                into(localShareDir)
            }
            println("Updated local share static web distribution: ${localShareDir.absolutePath}")
        }
    }
}

generateWasm.configure {
    finalizedBy(exportStaticDist)
}

val publishPages by tasks.registering(Exec::class) {
    dependsOn(exportStaticDist)
    group = "publishing"
    description = "Pushes latest build/nuclear-sim-dist to mgomezch/modular-nuclear-simulator on GitHub Pages"
    commandLine(
        "bash", "-c",
        """
        set -e
        TMP_DIR="${'$'}(mktemp -d)"
        git clone https://github.com/mgomezch/modular-nuclear-simulator.git "${'$'}TMP_DIR"
        cp -r build/nuclear-sim-dist/* "${'$'}TMP_DIR/"
        touch "${'$'}TMP_DIR/.nojekyll"
        cd "${'$'}TMP_DIR"
        git config user.name "mgomezch"
        git config user.email "mgomezch@users.noreply.github.com"
        git add -A
        if git diff --cached --quiet; then
            echo "No changes to commit for GitHub Pages."
        else
            git commit -m "deploy: update WebAssembly simulator, incident pause toggle, and fuel logistics"
            git push origin main
            echo "Successfully deployed latest WebAssembly simulator to GitHub Pages!"
        fi
        rm -rf "${'$'}TMP_DIR"
        """
    )
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

tasks.processResources {
    dependsOn(generateFuelStatsCharts)
}



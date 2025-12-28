#!/usr/bin/env kotlin

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

// -------------------- MODE --------------------
enum class Mode { RUN, COMPILE }

val mode = when (args.firstOrNull()?.lowercase()) {
    "run" -> Mode.RUN
    "compile" -> Mode.COMPILE
    else -> {
        println("Usage: kotlin test_runner.kts [run|compile]")
        exitProcess(1)
    }
}

// -------------------- PATHS --------------------
val projectRoot = File(".")
val outJar = File("out/KloX.jar")
val srcDir = File("src")
val loxSrcDir = File("src/lox")
val mainSrc = File("src/Main.kt")
val testDir = File("test")
val exeFile = File("build/out.exe")

data class TestResult(
    val name: String,
    val success: Boolean,
    val error: String? = null
)

// -------------------- HELPER TO RUN COMMANDS --------------------
fun runCommand(
    command: List<String>,
    workingDir: File = projectRoot,
    timeoutSeconds: Long = 180
): Pair<Boolean, String> {
    return try {
        val process = ProcessBuilder(command)
            .directory(workingDir)
            .redirectErrorStream(true)
            .start()

        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        val output = process.inputStream.bufferedReader().readText().trim()

        if (!finished) {
            process.destroyForcibly()
            false to "Timeout after $timeoutSeconds seconds"
        } else {
            val success = process.exitValue() == 0
            success to output
        }
    } catch (e: Exception) {
        false to e.message.orEmpty()
    }
}

// -------------------- LOAD TEST FILES --------------------
val testFiles = testDir.listFiles { f -> f.extension == "lx" }?.sortedBy { it.name }
    ?: emptyList()

if (testFiles.isEmpty()) {
    println("No tests found in 'test/'")
    exitProcess(0)
}

// -------------------- RUN TESTS --------------------
val results = mutableListOf<TestResult>()

for (file in testFiles) {
    println("\nTesting ${file.name} (${mode.name.lowercase()})")

    // Run Kotlin interpreter/compiler
    val (ok, output) = runCommand(listOf("java", "-jar", outJar.path, mode.name.lowercase(), file.path))

    // Front-end failure
    if (!ok) {
        results += TestResult(file.name, false, output)
        println("Failed (front-end)")
        continue
    }

    // C++ compilation failure detection
    if (mode == Mode.COMPILE && output.contains("C++ compilation failed")) {
        results += TestResult(file.name, false, output)
        println("Failed (C++ compile)")
        continue
    }

    // Run exe if compile mode
    if (mode == Mode.COMPILE) {
        val (exeOk, exeOut) = runCommand(listOf(exeFile.path))
        if (!exeOk) {
            results += TestResult(file.name, false, exeOut)
            println("Failed (runtime)")
            continue
        }
    }

    results += TestResult(file.name, true)
    println("Passed")
}

// -------------------- SUMMARY --------------------
println("\n============== TEST SUMMARY ==============")
val namePad = (results.maxOfOrNull { it.name.length } ?: 10) + 2

results.forEach {
    val status = if (it.success) "OK" else "FAIL"
    println(it.name.padEnd(namePad) + status)
}

val failedCount = results.count { !it.success }
println("\nTotal: ${results.size}, Passed: ${results.size - failedCount}, Failed: $failedCount")

if (failedCount > 0) {
    println("\nFailed tests details:\n")
    results.filter { !it.success }.forEach {
        println("---- ${it.name} ----")
        println(it.error)
        println()
    }
    exitProcess(1)
}

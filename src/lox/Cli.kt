package lox

import kotlin.system.exitProcess

enum class Target {
    CEmitter,
    X86_64
}

sealed class Command {
    class Run(val file: String, val printAst: Boolean = false) : Command()
    data object Repl : Command() {
        var printAst: Boolean = false
    }
    class Compile(
        val file: String,
        val target: Target,
        val outputCppFile: String,
        val outputExecutable: String
    ) : Command()
    data object Help : Command()
}

object Cli {
    private const val EXTENSION = ".lx"

    // Global flag accessible from anywhere (e.g., Lox.run())
    var printAst: Boolean = false
        private set

    fun parseArgs(args: Array<String>): Command {
        if (args.isEmpty()) {
            return Command.Repl.also { Command.Repl.printAst = false }
        }

        if (args.any { it == "--help" || it == "-h" }) {
            return Command.Help
        }

        val (positional, printAstFlag) = extractCommonFlags(args)

        printAst = printAstFlag

        if (positional.isEmpty()) {
            return Command.Repl.also { Command.Repl.printAst = printAstFlag }
        }

        val first = positional[0]
        val rest = positional.drop(1).toTypedArray()

        return when {
            first == "run" -> parseRun(rest, printAstFlag)
            first == "repl" -> parseRepl(rest, printAstFlag)
            first == "compile" -> parseCompile(rest)
            first.endsWith(EXTENSION) -> {
                if (rest.isNotEmpty()) {
                    usageError("Direct file execution does not accept additional positional arguments")
                }
                Command.Run(first, printAstFlag)
            }
            else -> usageError("Unknown command or invalid file: $first")
        }
    }

    private fun parseRun(args: Array<String>, printAstFlag: Boolean): Command.Run {
        if (args.size != 1) {
            usageError("Usage: run <file.lx> [--print-ast]")
        }
        val file = args[0]
        requireExtension(file)
        return Command.Run(file, printAstFlag)
    }

    private fun parseRepl(args: Array<String>, printAstFlag: Boolean): Command.Repl {
        if (args.isNotEmpty()) {
            usageError("repl command takes no arguments")
        }
        Command.Repl.printAst = printAstFlag
        return Command.Repl
    }

    private fun parseCompile(args: Array<String>): Command.Compile {
        val (positionalAndOptions, printAstFlag) = extractCommonFlags(args)
        printAst = printAstFlag

        var file: String? = null
        var target = Target.CEmitter
        var outputCppFile: String? = null
        var outputExecutable: String? = null

        val projectDir = System.getProperty("user.dir")
        val defaultBuildDir = "$projectDir/build"
        val defaultCppFile = "$defaultBuildDir/out.cpp"
        val defaultExeFile = "$defaultBuildDir/out${if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""}"


        var i = 0
        while (i < positionalAndOptions.size) {
            when (positionalAndOptions[i]) {
                "--target" -> {
                    i++
                    if (i >= positionalAndOptions.size) {
                        usageError("Missing target name after --target")
                    }
                    val targetName = positionalAndOptions[i]
                    target = try {
                        Target.valueOf(targetName.uppercase())
                    } catch (_: IllegalArgumentException) {
                        usageError(
                            "Invalid target: $targetName\n" +
                                    "Available targets: ${Target.entries.joinToString(", ") { it.name.lowercase() }}"
                        )
                    }
                }
                "--cpp-file" -> {
                    i++
                    if (i >= positionalAndOptions.size) usageError("Missing path after --cpp-file")
                    outputCppFile = positionalAndOptions[i]
                }
                "--exe-file" -> {
                    i++
                    if (i >= positionalAndOptions.size) usageError("Missing path after --exe-file")
                    outputExecutable = positionalAndOptions[i]
                }
                else -> {
                    if (file != null) usageError("Only one source file allowed")
                    file = positionalAndOptions[i]
                    requireExtension(file)
                }
            }
            i++
        }

        if (file == null) {
            usageError("No input file specified for compile")
        }

        return Command.Compile(
            file,
            target,
            outputCppFile ?: defaultCppFile,
            outputExecutable ?: defaultExeFile
        )
    }

    private fun extractCommonFlags(args: Array<String>): Pair<List<String>, Boolean> {
        var printAst = false
        val positional = mutableListOf<String>()

        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--print-ast" -> {
                    printAst = true
                    i++
                }
                else -> {
                    positional.add(args[i])
                    i++
                }
            }
        }

        return positional to printAst
    }

    private fun requireExtension(file: String) {
        if (!file.endsWith(EXTENSION)) {
            usageError("File must have $EXTENSION extension: $file")
        }
    }

    private fun usageError(message: String): Nothing {
        printHelp(message)
        exitProcess(64)
    }

    fun printHelp(prefixMessage: String? = null) {
        val helpText = """
            ${if (prefixMessage != null) "${Ansi.red("Error")}: $prefixMessage\n\n" else ""}${Ansi.bold("KloX")} - A Lox interpreter and compiler (with C++ transpiler)

            ${Ansi.bold("Usage")}:
              ${Ansi.cyan("kloX")}                          ${Ansi.dim("# Start interactive REPL")}
              ${Ansi.cyan("kloX")} <file.lx>                ${Ansi.dim("# Run a script directly")}
              ${Ansi.cyan("kloX")} run <file.lx>            ${Ansi.dim("# Explicitly run a script")}
              ${Ansi.cyan("kloX")} repl                     ${Ansi.dim("# Start REPL")}
              ${Ansi.cyan("kloX")} compile <file.lx>        ${Ansi.dim("# Compile to C++ or native")}

            ${Ansi.bold("Commands")}:
              run <file.lx>        Run a Lox script using the interpreter
              repl                 Start an interactive REPL session
              compile <file.lx>    Transpile to C++ or compile to native executable

            ${Ansi.bold("Global Options")}:
              --print-ast          Print the parsed AST (useful for debugging)
              --help, -h           Show this help message

            ${Ansi.bold("Compile Options")}:
              --target <name>      Target backend: ${Target.entries.joinToString(", ") { Ansi.cyan(it.name.lowercase()) }} (default: cemitter)
              --cpp-file <path>    Output C++ source file (default: build/out.cpp)
              --exe-file <path>    Output executable path (default: build/out[.exe])

            ${Ansi.bold("Examples")}:
              kloX script.lx
              kloX run script.lx --print-ast
              kloX compile script.lx --target cemitter --cpp-file myprog.cpp
              kloX compile script.lx --target x86_64 --exe-file bin/myapp

            Source: https://github.com/erfan4323/KloX
        """.trimIndent()

        println(helpText)
    }
}

object Ansi {
    private const val ENABLED: Boolean = true

    fun bold(text: String) = if (ENABLED) "\u001B[1m$text\u001B[0m" else text
    fun dim(text: String) = if (ENABLED) "\u001B[2m$text\u001B[0m" else text
    fun underline(text: String) = if (ENABLED) "\u001B[4m$text\u001B[0m" else text
    fun strike(text: String) = if (ENABLED) "\u001B[9m$text\u001B[0m" else text

    fun red(text: String) = if (ENABLED) "\u001B[31m$text\u001B[0m" else text
    fun green(text: String) = if (ENABLED) "\u001B[32m$text\u001B[0m" else text
    fun yellow(text: String) = if (ENABLED) "\u001B[33m$text\u001B[0m" else text
    fun cyan(text: String) = if (ENABLED) "\u001B[36m$text\u001B[0m" else text
    fun blue(text: String) = if (ENABLED) "\u001B[34m$text\u001B[0m" else text
    fun magenta(text: String) = if (ENABLED) "\u001B[35m$text\u001B[0m" else text

    val clearScreen = if (ENABLED) "\u001B[H\u001B[2J\u001B[3J" else "\n".repeat(50)
    val prompt = if (ENABLED) "\u001B[32m>\u001B[0m " else "> "
    val secondaryPrompt = if (ENABLED) "\u001B[36m.\u001B[0m " else ". "
}
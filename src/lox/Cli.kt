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
    class Compile(val file: String, val target: Target) : Command()
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

        return Command.Compile(file, target)
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
        println(
            """
            $message

            Usage:
              kloX                                                      # Start REPL
              kloX <file.lx>                                            # Run script directly
              kloX run <file.lx> [--print-ast]                          # Run script
              kloX repl [--print-ast]                                   # Start REPL
              kloX compile <file.lx> [--target <target>] [--print-ast]  # Compile script to target

            Options:
              --print-ast       # Print AST after parsing (for run/repl)

            Available targets: ${Target.entries.joinToString(", ") { it.name.lowercase() }}
            """.trimIndent()
        )
        exitProcess(64)
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
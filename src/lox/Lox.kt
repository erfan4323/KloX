package lox

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.system.exitProcess

class Lox {
    private val interpreter = Interpreter()

    fun runMain(args: Array<String>) {
        when (val command = Cli.parseArgs(args)) {
            is Command.Run -> runFile(command.file, command.printAst)
            is Command.Repl -> runPrompt(Command.Repl.printAst)
            is Command.Compile -> compile(command.file, command.target, command.outputCppFile, command.outputExecutable)
            is Command.Help -> Cli.printHelp()
        }
    }

    private fun compile(path: String, target: Target, outputCppFile: String, outputExecutable: String) {
        val source = File(path).readText(Charsets.UTF_8).trimStart('\uFEFF')
        val tokens = Scanner(source).scanTokens()
        val statements = Parser(tokens).parse()
        if (hadError) exitProcess(65)

        Resolver(interpreter).apply { resolve(statements) }
        if (hadError) exitProcess(65)

        val cppCode = CppCodeGenerator().generate(statements)
        val outputFile = File(outputCppFile).apply { parentFile?.mkdirs() }
        outputFile.writeText(cppCode)

        copyRuntimeFiles(outputFile.parentFile ?: File("."))
        compileCpp(outputCppFile, outputExecutable, outputFile.parentFile ?: File("."))
    }

    private fun copyRuntimeFiles(outputDir: File) {
        val projectDir = System.getProperty("user.dir")
        listOf("lox_runtime.cpp", "lox_runtime.h").forEach { fileName ->
            File("$projectDir/src/runtime/src/$fileName").copyTo(File(outputDir, fileName), overwrite = true)
        }
    }

    private fun compileCpp(outputCppFile: String, outputExecutable: String, outputDir: File) {
        val compileCmd = listOf(
            "g++", "-O3", "-std=c++17", "-march=native", "-flto", "-DNDEBUG",
            outputCppFile, File(outputDir, "lox_runtime.cpp").absolutePath,
            "-o", outputExecutable
        )
        val exitCode = runCmd(compileCmd)
        if (exitCode != 0) {
            println("C++ compilation failed with code $exitCode")
            hadError = true
        }
    }

    fun runCmd(cmd: List<String>, workingDir: File? = null, inheritIO: Boolean = true, env: Map<String, String> = emptyMap()): Int {
        println("command: ${cmd.joinToString(" ")}")
        return ProcessBuilder(cmd).apply {
            workingDir?.let(this::directory)
            if (inheritIO) inheritIO()
            environment().putAll(env)
        }.start().waitFor()
    }

    private fun runPrompt(printAst: Boolean) {
        val reader = BufferedReader(InputStreamReader(System.`in`))
        printWelcome()

        val history = mutableListOf<Pair<String, Boolean>>()
        var pendingPrompt = true

        while (true) {
            if (pendingPrompt) printPrompt()
            val line = reader.readLine() ?: break
            pendingPrompt = true

            when {
                line.isBlank() -> {}
                line.startsWith(":" ) -> handleReplCommand(line.trim(), history)
                else -> {
                    hadError = false
                    hadRuntimeError = false
                    run(line, printAst)
                    history.add(line to !(hadError || hadRuntimeError))
                    pendingPrompt = !(hadError || hadRuntimeError)
                }
            }

            hadError = false
            hadRuntimeError = false
        }
    }

    private fun printWelcome() {
        println(Ansi.blue(Ansi.bold("Welcome to KloX REPL (Kotlin Lox)")))
        println("Type ${Ansi.cyan(":help")} for commands, or start typing Lox code.")
        println("Press Ctrl+D or type ${Ansi.cyan(":quit")} to exit.\n")
    }

    private fun printPrompt() {
        print(Ansi.prompt)
        System.out.flush()
    }

    private fun handleReplCommand(command: String, history: MutableList<Pair<String, Boolean>>) {
        when (command) {
            ":help", ":h" -> printHelp()
            ":quit", ":q" -> exitRepl()
            ":clear", ":c" -> clearScreen()
            ":history" -> printHistory(history)
            else -> println(Ansi.red("Unknown command: $command. Type :help for help."))
        }
    }

    private fun printHelp() {
        println(Ansi.bold("KloX REPL Commands:"))
        println("  :help, :h        Show this help")
        println("  :quit, :q        Exit the REPL")
        println("  :clear, :c       Clear the screen")
        println("  :history         Show command history")
    }

    private fun exitRepl() {
        println(Ansi.yellow("Goodbye!"))
        exitProcess(0)
    }

    private fun clearScreen() {
        print(Ansi.clearScreen)
        System.out.flush()
    }

    private fun printHistory(history: List<Pair<String, Boolean>>) {
        history.forEachIndexed { i, (cmd, executed) ->
            println(if (executed) "${i + 1}: $cmd" else Ansi.strike("${i + 1}: $cmd") + " (not executed)")
        }
    }

    private fun runFile(path: String, printAst: Boolean) {
        val source = File(path).readText(Charsets.UTF_8).trimStart('\uFEFF')
        run(source, printAst)
        if (hadError) exitProcess(65)
        if (hadRuntimeError) exitProcess(70)
    }

    private fun run(source: String, printAst: Boolean) {
        val tokens = Scanner(source).scanTokens()
        val statements = Parser(tokens).parse()
        if (hadError) return

        Resolver(interpreter).resolve(statements)
        if (hadError) return

        interpreter.interpret(statements)
        if (printAst) printAst(statements)
    }

    private fun printAst(statements: List<Stmt>) {
        println("----------------|Ast|----------------")
        println(AstFormatter().print(statements))
        println("-------------------------------------")
    }

    companion object {
        private var hadError = false
        private var hadRuntimeError = false

        fun error(line: Int, message: String) = report(line, "", message)
        fun error(token: Token, message: String) = report(token.line, if (token.type == TokenType.EOF) " at end" else " at '${token.lexeme}'", message)

        private fun report(line: Int, where: String, message: String) {
            eprintln(Ansi.red("[Line $line] Error$where: $message"))
            hadError = true
        }

        fun runtimeError(error: RunTimeError) {
            eprintln(Ansi.red("[line ${error.token.line}] Runtime Error: ${error.message}"))
            hadRuntimeError = true
        }

        private fun eprintln(message: Any?) = System.err.println(message)
    }
}

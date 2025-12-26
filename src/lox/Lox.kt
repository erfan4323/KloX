package lox

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.system.exitProcess

class Lox() {
    private val interpreter = Interpreter()

    fun runMain(args: Array<String>) {
        when (val command = Cli.parseArgs(args)) {
            is Command.Run -> {
                runFile(command.file, command.printAst)
            }
            is Command.Repl -> {
                runPrompt(Command.Repl.printAst)
            }
            is Command.Compile -> compile(command.file, command.target, command.outputCppFile, command.outputExecutable)
        }
    }

    private fun compile(path: String, target: Target, outputCppFile: String, outputExecutable: String) {
        // Read source file and remove BOM
        val source = File(path).readText(Charsets.UTF_8).trimStart('\uFEFF')

        // Scan, parse, and resolve
        val tokens = Scanner(source).scanTokens()
        val statements = Parser(tokens).parse()

        if (hadError) exitProcess(65)

        Resolver(interpreter).apply { resolve(statements) }

        if (hadError) exitProcess(65)

        // Generate C++ code
        val cppCode = CppCodeGenerator().generate(statements)

        // Ensure output folder exists
        val outputFile = File(outputCppFile)
        val outputFolder = outputFile.parentFile ?: File(".")
        outputFolder.mkdirs()
        File(outputCppFile).writeText(cppCode)

        // Copy runtime files to output folder
        val projectDir = System.getProperty("user.dir")
        val runtimeFiles = listOf("lox_runtime.cpp", "lox_runtime.h").map { File("$projectDir/src/runtime/src/$it") }

        runtimeFiles.forEach { src ->
            val dest = File(outputFolder, src.name)
            src.copyTo(dest, overwrite = true)
        }

        // Determine OS and prepare compile command
        val isWindows = System.getProperty("os.name").startsWith("Windows")
        val runtimePathCopied = File(outputFolder, "lox_runtime.cpp").absolutePath

//        val compileCmd = if (isWindows) {
//            listOf("cl", "/std:c++17", outputCppFile, runtimePathCopied, "/Fe:$outputExecutable")
//        } else {
//            listOf("g++", "-std=c++17", outputCppFile, runtimePathCopied, "-o", outputExecutable)
//        }

        val compileCmd = listOf(
            "g++",
            "-std=c++17",
            outputCppFile,
            runtimePathCopied,
            "-o",
            outputExecutable
        )

        // Run the compiler
        val exitCode = runCmd(compileCmd)
        if (exitCode != 0) {
            println("C++ compilation failed with code $exitCode")
            hadError = true
        }

    }

    fun runCmd(
        cmd: List<String>,
        workingDir: File? = null,
        inheritIO: Boolean = true,
        env: Map<String, String> = emptyMap()
    ): Int {
        val builder = ProcessBuilder(cmd)

        workingDir?.let(builder::directory)
        if (inheritIO) builder.inheritIO()
        builder.environment().putAll(env)

        val process = builder.start()
        return process.waitFor()
    }

    private fun runPrompt(printAst: Boolean) {
        val reader = BufferedReader(InputStreamReader(System.`in`))

        println(Ansi.bold("Welcome to KloX REPL (Kotlin Lox)"))
        println("Type ${Ansi.cyan(":help")} for commands, or start typing Lox code.")
        println("Press Ctrl+D or type ${Ansi.cyan(":quit")} to exit.\n")

        val history = mutableListOf<Pair<String, Boolean>>()
        var pendingPrompt = true

        while (true) {
            if (pendingPrompt) {
                print(Ansi.prompt)
                System.out.flush()
            }

            val line = reader.readLine() ?: break

            pendingPrompt = true

            when {
                line.isBlank() -> {}

                line.startsWith(":") -> {
                    handleReplCommand(line.trim(), history)
                }

                else -> {

                    hadError = false
                    hadRuntimeError = false

                    run(line, printAst)

                    if (hadError || hadRuntimeError) {
                        pendingPrompt = false
                        history.add(line to false)
                    }
                    else {
                        history.add(line to true)
                    }
                }
            }

            hadError = false
            hadRuntimeError = false
        }
    }

    private fun handleReplCommand(command: String, history: MutableList<Pair<String, Boolean>>) {
        when (command) {
            ":help", ":h" -> {
                println(
                    """
                ${Ansi.bold("KloX REPL Commands:")}
                  :help, :h        Show this help
                  :quit, :q        Exit the REPL
                  :clear, :c       Clear the screen
                  :history         Show command history
                """.trimIndent()
                )
            }

            ":quit", ":q" -> {
                println(Ansi.yellow("Goodbye!"))
                exitProcess(0)
            }

            ":clear", ":c" -> {
                print(Ansi.clearScreen)
                System.out.flush()
            }

            ":history" -> {
                history.forEachIndexed { i, (cmd, flag) ->
                    if (flag) {
                        println("${i + 1}: $cmd")
                    } else {
                        println("${Ansi.strike("${i + 1}: $cmd")} (not executed)")
                    }
                }
            }

            else -> {
                println(Ansi.red("Unknown command: $command. Type :help for help."))
            }
        }
    }

    private fun runFile(path: String, printAst: Boolean) {
        val source = File(path).readText(Charsets.UTF_8).trimStart('\uFEFF')
        run(source, printAst)

        if (hadError) exitProcess(65)
        if (hadRuntimeError) exitProcess(70)
    }

    private fun run(source: String, printAst: Boolean) {
        val scanner = Scanner(source)
        val tokens = scanner.scanTokens()
        val parser = Parser(tokens)
        val statements = parser.parse()

        if (hadError) return

        val resolver = Resolver(interpreter)
        resolver.resolve(statements)

        if (hadError) return

        interpreter.interpret(statements)
        if (printAst)
        {
            println("----------------|Ast|----------------")
            println(AstFormatter().print(statements))
            println("-------------------------------------")
        }
    }

    companion object {
        private var hadError = false
        private var hadRuntimeError = false

        fun error(line: Int, message: String) = report(line, "", message)

        fun error(token: Token, message: String) = report(
            token.line,
            if (token.type == TokenType.EOF) " at end" else " at '${token.lexeme}'",
            message
        )

        private fun report(line: Int, where: String, message: String) {
            eprintln("[Line $line] Error$where: $message")
            hadError = true
        }

        fun runtimeError(error: RunTimeError) {
            eprintln("[line ${error.token.line}] Runtime Error: ${error.message}")
            hadRuntimeError = true
        }

        private fun eprintln(message: Any?) = System.err.println(message)
    }
}
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
            is Command.Compile -> compile(command.file, command.target)
        }
    }

    private fun compile(path: String, target: Target) {
        val source = File(path).readText(Charsets.UTF_8).trimStart('\uFEFF')

        val scanner = Scanner(source)
        val tokens = scanner.scanTokens()
        val parser = Parser(tokens)
        val statements = parser.parse()

        if (hadError) exitProcess(65)

        val resolver = Resolver(interpreter)
        resolver.resolve(statements)

        if (hadError) exitProcess(65)

        // Placeholder for actual compilation logic
        println("Compiled '$path' to $target (compilation not yet implemented)")
        // In the future, you can emit bytecode, JVM class files, etc. here
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
        println(printAst)
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
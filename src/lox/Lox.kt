package lox

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.system.exitProcess

class Lox() {
    private val interpreter = Interpreter()

    fun runMain(args: Array<String>) {
        when {
            args.size > 1 -> {
                println("Usage: KloX [script]")
                exitProcess(64)
            }
            args.size == 1 -> {
                runFile(args[0])
            }
            else -> {
                runPrompt()
            }
        }
    }

    private fun runPrompt() {
        val reader = BufferedReader(InputStreamReader(System.`in`)).buffered()
        while (true) {
            print("> ")
            System.out.flush()
            val line = reader.readLine() ?: break
            run(line)
            hadError = false
        }
    }

    private fun runFile(path: String) {
        val source = File(path).readText(Charsets.UTF_8).trimStart('\uFEFF')
        run(source)

        if (hadError) exitProcess(65)
        if (hadRuntimeError) exitProcess(70)
    }

    private fun run(source: String) {
        val scanner = Scanner(source)
        val tokens = scanner.scanTokens()
        val parser = Parser(tokens)
        val statements = parser.parse()

        if (hadError) return

        interpreter.interpret(statements)
        val printer = AstPrinter()
        println(printer.print(statements))
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
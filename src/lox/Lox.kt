package lox

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.Charset
import kotlin.io.path.Path
import kotlin.io.path.pathString
import kotlin.system.exitProcess

class Lox() {
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
        val reader = BufferedReader(InputStreamReader(System.`in`))
        while (true) {
            print("> ")
            val line = reader.readLine() ?: break
            run(line)
            hadError = false
        }
    }

    private fun runFile(path: String) {
        val source = File(Path(path).pathString).readText(Charset.defaultCharset())
        run(source)

        if (hadError) exitProcess(65)
    }

    private fun run(source: String) {
        val scanner = Scanner(source)
        val tokens = scanner.scanTokens()
        val parser = Parser(tokens)
        val expression: Expr? = parser.parse()

        if (hadError) return

        println(expression?.let { AstPrinter().print(it) })
    }

    companion object {
        private var hadError = false

        fun error(line: Int, message: String) {
            report(line, "", message)
        }

        fun error(token: Token, message: String) {
            if (token.type == TokenType.EOF) {
                report(token.line, " at end", message)
            }
            else {
                report(token.line, " at '${token.lexeme}'", message)
            }
        }

        private fun report(line: Int, where:String, message: String) {
            println("[Line $line] Error $where: $message")
            hadError = true
        }
    }
}
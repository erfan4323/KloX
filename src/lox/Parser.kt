package lox

import java.util.*


class Parser(val tokens: List<Token>) {
    class ParseError: RuntimeException()

    private var current: Int = 0

    fun parse(): List<Stmt> {
        val statements: MutableList<Stmt> = mutableListOf()

        while (!isAtEnd()) {
            declaration()?.let { statements.add(it) }
        }

        return statements
    }

    private fun declaration(): Stmt? {
        try {
            if (match(TokenType.VAR)) return varDeclaration();
            return statement();
        }
        catch (error: ParseError) {
            synchronize()
            return null
        }
    }

    private fun varDeclaration(): Stmt {
        val name = consume(TokenType.IDENTIFIER, "Expect variable name.")

        val initializer = if (match(TokenType.EQUAL)) expression() else throw ParseError()

        consume(TokenType.SEMICOLON, "Expect ';' after variable declaration.")
        return Stmt.Var(name, initializer)
    }

    private fun statement(): Stmt {
        return when {
            match(TokenType.IF) -> ifStatement()
            match(TokenType.PRINT) -> printStatement()
            match(TokenType.WHILE) -> whileStatement()
            match(TokenType.FOR) -> forStatement()
            match(TokenType.LEFT_BRACE) -> Stmt.Block(block())
            else -> expressionStatement()
        }
    }

    private fun forStatement(): Stmt {
        consume(TokenType.LEFT_PAREN, "Expect '(' after 'for'.")
        val initializer = when {
            match(TokenType.SEMICOLON) -> null
            match(TokenType.VAR) -> varDeclaration()
            else -> expressionStatement()
        }

        var condition = if (!check(TokenType.SEMICOLON)) expression() else null
        consume(TokenType.SEMICOLON, "Expect ';' after loop condition.")

        val increment = if (!check(TokenType.RIGHT_PAREN)) expression() else null
        consume(TokenType.RIGHT_PAREN, "Expect ')' after for clauses.");

        var body = statement()

        if (increment != null) {
            body = Stmt.Block(listOf(body, Stmt.Expression(increment)))
        }

        if (condition == null) {
            condition = Expr.Literal(true)
        }
        body = Stmt.While(condition, body)

        if (initializer != null) {
            body = Stmt.Block(listOf(initializer, body))
        }

        return body
    }

    private fun whileStatement(): Stmt {
        consume(TokenType.LEFT_PAREN, "Expect '(' after 'while'.")
        val condition = expression()
        consume(TokenType.RIGHT_PAREN, "Expect ')' after condition.")
        val body = statement()
        return Stmt.While(condition, body)
    }

    private fun ifStatement(): Stmt {
        consume(TokenType.LEFT_PAREN, "Expect '(' after 'if'.")
        val condition = expression()
        consume(TokenType.RIGHT_PAREN, "Expect ')' after if condition.")

        val thenBranch = statement()

        var elseBranch: Stmt? = null
        if (match(TokenType.ELSE)) {
            elseBranch = statement()
        }

        return Stmt.If(condition, thenBranch, elseBranch)
    }

    private fun expressionStatement(): Stmt {
        val expr = expression()
        consume(TokenType.SEMICOLON, "Expect ';' after value.")
        return Stmt.Expression(expr)
    }

    private fun block(): List<Stmt> {
        val statements = mutableListOf<Stmt>()

        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
            declaration()?.let { statements.add(it) }
        }

        consume(TokenType.RIGHT_BRACE, "Expect '}' after block.")
        return statements
    }

    private fun printStatement(): Stmt {
        val value = expression()
        consume(TokenType.SEMICOLON, "Expect ';' after value.")
        return Stmt.Print(value)
    }

    private fun expression(): Expr = assignment()

    private fun assignment(): Expr {
        val expr = or()

        if (match(TokenType.EQUAL)) {
            val equals = previous()
            val value = assignment()

            if (expr is Expr.Variable) {
                val name = expr.name
                return Expr.Assign(name, value)
            }

            error(equals, "Invalid assignment target.")
        }

        return expr
    }

    private fun or(): Expr {
        var expr: Expr = and()

        while (match(TokenType.OR)) {
            val operator = previous()
            val right = and()
            expr = Expr.Logical(expr, operator, right)
        }

        return expr
    }

    private fun and(): Expr {
        var expr: Expr = equality()

        while (match(TokenType.AND)) {
            val operator = previous()
            val right = equality()
            expr = Expr.Logical(expr, operator, right)
        }

        return expr
    }

    private fun equality(): Expr {
        var expr: Expr = comparison()

        while (match(TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL)) {
            val operator: Token = previous()
            val right: Expr = comparison()
            expr = Expr.Binary(expr, operator, right)
        }
        
        return expr
    }

    private fun comparison(): Expr {
        var expr: Expr = term()

        while (match(TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL)) {
            val operator = previous()
            val right = term()
            expr = Expr.Binary(expr, operator, right)
        }

        return expr
    }

    private fun term(): Expr {
        var expr: Expr = factor()

        while (match(TokenType.PLUS, TokenType.MINUS)) {
            val operator = previous()
            val right = factor()
            expr = Expr.Binary(expr, operator, right)
        }

        return expr
    }

    private fun factor(): Expr {
        var expr: Expr = unary()

        while (match(TokenType.SLASH, TokenType.STAR)) {
            val operator = previous()
            val right = unary()
            expr = Expr.Binary(expr, operator, right)
        }

        return expr
    }

    private fun unary(): Expr =
        if (match(TokenType.BANG, TokenType.MINUS)) {
            val operator = previous()
            val right = unary()
            Expr.Unary(operator, right)
        }
        else {
            primary()
        }

    private fun primary(): Expr {
        return when {
            match(TokenType.FALSE) -> Expr.Literal(false)
            match(TokenType.TRUE)  -> Expr.Literal(true)
            match(TokenType.NIL)   -> Expr.Literal(null)

            match(TokenType.NUMBER, TokenType.STRING) ->
                Expr.Literal(previous().literal)

            match(TokenType.LEFT_PAREN) -> {
                val expr = expression()
                consume(TokenType.RIGHT_PAREN, "Expect ')' after expression.")
                Expr.Grouping(expr)
            }

            match(TokenType.IDENTIFIER) -> Expr.Variable(previous())

            else -> throw error(peek(), "Expect expression.")
        }
    }

    private fun consume(type: TokenType, message: String): Token =
        if (check(type)) advance() else throw error(peek(), message)


    private fun error(token: Token, message: String): ParseError {
        Lox.error(token, message)
        return ParseError()
    }

    private fun synchronize() {
        advance()
        while (!isAtEnd()) {
            if (previous().type == TokenType.SEMICOLON) return
            if (peek().type in listOf(
                    TokenType.CLASS, TokenType.FUN, TokenType.FOR,
                    TokenType.IF, TokenType.PRINT, TokenType.RETURN,
                    TokenType.VAR, TokenType.WHILE
                )
            ) return
            advance()
        }
    }

    private fun match(vararg types: TokenType): Boolean  =
        types.any { check(it) && run { advance(); true } }

    private fun check(type: TokenType): Boolean =
        !isAtEnd() && peek().type == type

    private fun advance(): Token {
        if (!isAtEnd()) current++
        return previous()
    }

    private fun isAtEnd(): Boolean = peek().type == TokenType.EOF

    private fun peek(): Token = tokens[current]

    private fun previous(): Token = tokens[current - 1]
}
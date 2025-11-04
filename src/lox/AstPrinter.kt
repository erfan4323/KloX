package lox

class AstPrinter: Expr.Visitor<String>, Stmt.Visitor<String> {
    fun print(expr: Expr): String = expr.accept(this)

    fun print(statements: List<Stmt>): String =
        statements.joinToString("\n") { it.accept(this) }

    override fun visitAssignExpr(expr: Expr.Assign): String {
        return parenthesize("=", Expr.Variable(expr.name), expr.value)
    }

    override fun visitBinaryExpr(expr: Expr.Binary): String {
        return parenthesize(expr.operator.lexeme, expr.left, expr.right)
    }

    override fun visitGroupingExpr(expr: Expr.Grouping): String {
        return parenthesize("group", expr.expression)
    }

    override fun visitLiteralExpr(expr: Expr.Literal): String {
        return expr.value.toString()
    }

    override fun visitUnaryExpr(expr: Expr.Unary): String {
        return parenthesize(expr.operator.lexeme, expr.right)
    }

    override fun visitVariableExpr(expr: Expr.Variable): String {
        return expr.name.lexeme
    }

    override fun visitBlockStmt(stmt: Stmt.Block): String {
        return buildString {
            append("{ ")
            for (s in stmt.statements) {
                append(s.accept(this@AstPrinter))
                append(" ")
            }
            append("}")
        }
    }

    override fun visitExpressionStmt(stmt: Stmt.Expression): String {
        return stmt.expression.accept(this)
    }

    override fun visitPrintStmt(stmt: Stmt.Print): String {
        return parenthesize("print", stmt.expression)
    }

    override fun visitVarStmt(stmt: Stmt.Var): String {
        return parenthesize("var ${stmt.name.lexeme}", stmt.initializer)
    }

    private fun parenthesize(name: String, vararg exprs: Expr): String {
        return buildString {
            append("(").append(name)
            for (expr in exprs) {
                append(" ")
                append(expr.accept(this@AstPrinter))
            }
            append(")")
        }
    }
}
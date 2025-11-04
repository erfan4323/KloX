package lox

class AstFormatter: Expr.Visitor<String>, Stmt.Visitor<String> {
    fun format(expr: Expr): String = expr.accept(this)

    fun format(statements: List<Stmt>): String =
        statements.joinToString("\n") { it.accept(this) }

    // ===== Expr Visitors =====
    override fun visitAssignExpr(expr: Expr.Assign): String =
        parenthesize("=", Expr.Variable(expr.name), expr.value)

    override fun visitBinaryExpr(expr: Expr.Binary): String =
        parenthesize(expr.operator.lexeme, expr.left, expr.right)

    override fun visitGroupingExpr(expr: Expr.Grouping): String =
        parenthesize("group", expr.expression)

    override fun visitLiteralExpr(expr: Expr.Literal): String =
        expr.value.toString()

    override fun visitUnaryExpr(expr: Expr.Unary): String =
        parenthesize(expr.operator.lexeme, expr.right)

    override fun visitVariableExpr(expr: Expr.Variable): String =
        expr.name.lexeme

    // ===== Stmt Visitors =====
    override fun visitBlockStmt(stmt: Stmt.Block): String =
        buildString {
            append("{\n")
            stmt.statements.forEach { append("  ").append(it.accept(this@AstFormatter)).append("\n") }
            append("}")
        }

    override fun visitExpressionStmt(stmt: Stmt.Expression): String =
        stmt.expression.accept(this)

    override fun visitPrintStmt(stmt: Stmt.Print): String =
        parenthesize("print", stmt.expression)

    override fun visitVarStmt(stmt: Stmt.Var): String =
        parenthesize("var ${stmt.name.lexeme}", stmt.initializer)

    // ===== Helpers =====
    private fun parenthesize(name: String, vararg exprs: Expr): String =
        buildString {
            append("(").append(name)
            for (expr in exprs) append(" ").append(expr.accept(this@AstFormatter))
            append(")")
        }
}

package lox

class AstFormatter : Expr.Visitor<String>, Stmt.Visitor<String> {

    fun print(statements: List<Stmt>): String =
        statements.joinToString("\n") { it.accept(this) }

    fun print(expr: Expr): String = expr.accept(this)

    // ===== Expr Visitors =====

    override fun visitAssignExpr(expr: Expr.Assign): String =
        node("Expr.Assign ${expr.name.lexeme}", expr.value)

    override fun visitBinaryExpr(expr: Expr.Binary): String =
        node("Expr.Binary ${expr.operator.lexeme}", expr.left, expr.right)

    override fun visitCallExpr(expr: Expr.Call): String =
        node("Expr.Call", expr.callee, *expr.arguments.toTypedArray())

    override fun visitGetExpr(expr: Expr.Get): String =
        node("Expr.Get .${expr.name.lexeme}", expr.obj)

    override fun visitGroupingExpr(expr: Expr.Grouping): String =
        node("Expr.Grouping", expr.expression)

    override fun visitLiteralExpr(expr: Expr.Literal): String =
        leaf("Expr.Literal", expr.value?.toString() ?: "nil")

    override fun visitLogicalExpr(expr: Expr.Logical): String =
        node("Expr.Logical ${expr.operator.lexeme}", expr.left, expr.right)

    override fun visitSetExpr(expr: Expr.Set): String =
        node("Expr.Set = ${expr.name.lexeme}", expr.obj, expr.value)

    override fun visitSuperExpr(expr: Expr.Super): String =
        leaf("Expr.Super", expr.method.lexeme)

    override fun visitThisExpr(expr: Expr.This): String =
        leaf("Expr.This")

    override fun visitUnaryExpr(expr: Expr.Unary): String =
        node("Expr.Unary ${expr.operator.lexeme}", expr.right)

    override fun visitVariableExpr(expr: Expr.Variable): String =
        leaf("Expr.Variable", expr.name.lexeme)

    // ===== Stmt Visitors =====

    override fun visitBlockStmt(stmt: Stmt.Block): String =
        node("Stmt.Block", *stmt.statements.toTypedArray())

    override fun visitClassStmt(stmt: Stmt.Class): String =
        node(
            "Stmt.Class ${stmt.name.lexeme}" + (stmt.superclass?.let { " < ${it.name.lexeme}" } ?: ""),
            *stmt.methods.toTypedArray()
        )

    override fun visitExpressionStmt(stmt: Stmt.Expression): String =
        node("Stmt.Expression", stmt.expression)

    override fun visitFunctionStmt(stmt: Stmt.Function): String =
        buildString {
            val name = stmt.name.lexeme
            append("Stmt.Function \"$name\"")

            // Always show parameters as separate lines (if any)
            stmt.params.forEachIndexed { i, param ->
                val isLastParam = i == stmt.params.lastIndex && stmt.body.isEmpty()
                val prefix = if (isLastParam) "└─ " else "├─ "
                append("\n   $prefix param: \"${param.lexeme}\"")
            }

            // Always show body, even if empty
            val bodyPrefix = if (stmt.params.isEmpty()) "└─ " else "├─ "

            append("\n $bodyPrefix body:")
            if (stmt.body.isEmpty()) {
                append(" (empty)")
            } else {
                stmt.body.forEachIndexed { i, bodyStmt ->
                    append("\n")
                    val isLast = i == stmt.body.lastIndex
                    val prefix = if (isLast) "└─ " else "├─ "
                    val continuePrefix = if (isLast) "   " else "│  "

                    val lines = bodyStmt.accept(this@AstFormatter).lines()
                    append("      $prefix${lines.first()}")
                    lines.drop(1).forEach { line ->
                        append("\n      $continuePrefix$line")
                    }
                }
            }
        }

    override fun visitIfStmt(stmt: Stmt.If): String =
        node("Stmt.If", stmt.condition, stmt.thenBranch, stmt.elseBranch)

    override fun visitPrintStmt(stmt: Stmt.Print): String =
        node("Stmt.Print", stmt.expression)

    override fun visitReturnStmt(stmt: Stmt.Return): String =
        if (stmt.value == null) leaf("Stmt.Return") else node("Stmt.Return", stmt.value)

    override fun visitVarStmt(stmt: Stmt.Var): String =
        node("Stmt.Var ${stmt.name.lexeme}", stmt.initializer)

    override fun visitWhileStmt(stmt: Stmt.While): String =
        node("Stmt.While", stmt.condition, stmt.body)

    // ===== Helpers =====

    private fun node(name: String, vararg children: Any?): String {
        val filtered = children.filterNotNull()
        if (filtered.isEmpty()) return name

        return buildString {
            append(name)
            filtered.forEachIndexed { i, child ->
                append("\n")
                val isLast = i == filtered.lastIndex
                val prefix = if (isLast) "└─ " else "├─ "
                val continuePrefix = if (isLast) "   " else "│  "

                val childStr = childToString(child)
                val lines = childStr.lines()

                append("   $prefix${lines.first()}")
                for (line in lines.drop(1)) {
                    append("\n   $continuePrefix$line")
                }
            }
        }
    }

    private fun leaf(name: String, value: String? = null): String =
        if (value == null) name else "$name \"$value\""

    private fun childToString(child: Any): String =
        when (child) {
            is Expr -> child.accept(this)
            is Stmt -> child.accept(this)
            else -> child.toString()
        }
}
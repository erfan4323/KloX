package lox

class Interpreter: Expr.Visitor<Any?> {
    fun interpret(expression: Expr) {
        try {
            val value = evaluate(expression)
            println(stringify(value))
        }
        catch (error: RunTimeError) {
            Lox.runtimeError(error)
        }
    }

    override fun visitBinaryExpr(expr: Expr.Binary): Any? {
        val left = evaluate(expr.left)
        val right = evaluate(expr.right)

        return when (expr.operator.type) {
            TokenType.MINUS -> {
                checkNumberOperands(expr.operator, left, right)
                (left as Double) - (right as Double)
            }
            TokenType.SLASH -> {
                checkNumberOperands(expr.operator, left, right)
                (left as Double) / (right as Double)
            }
            TokenType.STAR -> {
                checkNumberOperands(expr.operator, left, right)
                (left as Double) * (right as Double)
            }
            TokenType.PLUS -> {
                if (left is Double && right is Double) left + right
                else if (left is String && right is String) left + right
                else throw RunTimeError(expr.operator, "Operands must be two numbers or two strings.")
            }
            TokenType.GREATER -> {
                checkNumberOperands(expr.operator, left, right)
                (left as Double) > (right as Double)
            }
            TokenType.GREATER_EQUAL -> {
                checkNumberOperands(expr.operator, left, right)
                (left as Double) >= (right as Double)
            }
            TokenType.LESS -> {
                checkNumberOperands(expr.operator, left, right)
                (left as Double) < (right as Double)
            }
            TokenType.LESS_EQUAL -> {
                checkNumberOperands(expr.operator, left, right)
                (left as Double) <= (right as Double)
            }
            TokenType.BANG_EQUAL -> left != right
            TokenType.EQUAL_EQUAL -> left == right

            else -> null
        }
    }

    override fun visitGroupingExpr(expr: Expr.Grouping): Any? = evaluate(expr.expression)

    override fun visitLiteralExpr(expr: Expr.Literal): Any? = expr.value

    override fun visitUnaryExpr(expr: Expr.Unary): Any? {
        val right = evaluate(expr.right)
        return when (expr.operator.type) {
            TokenType.BANG -> !isTruthy(right)
            TokenType.MINUS -> -(right as Double)
            else -> null
        }
    }

    private fun isTruthy(obj: Any?): Boolean =
        when (obj) {
            null -> false
            is Boolean -> obj
            else -> true
        }

    private fun evaluate(expr: Expr): Any? = expr.accept(this)

    private fun stringify(value: Any?): String {
        return when (value) {
            null -> "nil"
            is Double -> {
                var text = value.toString()
                if (text.endsWith(".0")) {
                    text = text.dropLast(2)
                }
                text
            }
            else -> value.toString()
        }
    }

    private fun checkNumberOperand(operator: Token, operand: Any?) {
        require(operand is Double) {
            throw RunTimeError(operator, "Operand must be a number.")
        }
    }

    private fun checkNumberOperands(operator: Token, vararg operands: Any?) {
        if (operands.all { it is Double }) return
        throw RunTimeError(operator, "Operands must be numbers.")
    }
}
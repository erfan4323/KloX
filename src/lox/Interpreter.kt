package lox

class Interpreter: Expr.Visitor<Any?> {
    fun interpret(expression: Expr) {
        try {
            evaluate(expression)?.let { println(stringify(it)) }
        } catch (error: RunTimeError) {
            Lox.runtimeError(error)
        }
    }

    override fun visitBinaryExpr(expr: Expr.Binary): Any? {
        val left = evaluate(expr.left)
        val right = evaluate(expr.right)

        return when (expr.operator.type) {
            TokenType.MINUS -> (left asDouble expr.operator) - (right asDouble expr.operator)
            TokenType.SLASH -> (left asDouble expr.operator) / (right asDouble expr.operator)
            TokenType.STAR -> (left asDouble expr.operator) * (right asDouble expr.operator)
            TokenType.PLUS -> {
                if (left is Double && right is Double) left + right
                else if (left is String && right is String) left + right
                else throw RunTimeError(expr.operator, "Operands must be two numbers or two strings.")
            }
            TokenType.GREATER -> (left asDouble expr.operator) > (right asDouble expr.operator)
            TokenType.GREATER_EQUAL -> (left asDouble expr.operator) >= (right asDouble expr.operator)
            TokenType.LESS -> (left asDouble expr.operator) < (right asDouble expr.operator)
            TokenType.LESS_EQUAL -> (left asDouble expr.operator) <= (right asDouble expr.operator)
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
            TokenType.MINUS -> -(right asDouble expr.operator)
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

    private fun stringify(value: Any?) =
        when (value) {
            null -> "nil"
            is Double -> value.toString().removeSuffix(".0")
            else -> value.toString()
        }

    private fun checkNumberOperand(operator: Token, operand: Any?) =
        require(operand is Double) { throw RunTimeError(operator, "Operand must be a number.") }

    private fun checkNumberOperands(operator: Token, vararg operands: Any?) =
        require(operands.all { it is Double }) { throw RunTimeError(operator, "Operands must be numbers.") }

    private infix fun Any?.asDouble(operator: Token): Double = this as? Double
        ?: throw RunTimeError(operator, "Operand must be a number.")
}
package lox

class Interpreter: Expr.Visitor<Any?>, Stmt.Visitor<Unit> {
    val globals = Environment()
    private var environment: Environment = globals

    init {
        globals.define("clock", object: LoxCallable {
            override fun arity(): Int = 0
            override fun call(interpreter: Interpreter, arguments: MutableList<Any?>): Any? {
                return System.currentTimeMillis() / 1000.0
            }
            override fun toString(): String = "<native fn>"
        })
    }

    fun interpret(statements: List<Stmt>) {
        try {
            for (statement in statements) {
                execute(statement)
            }
        } catch (error: RunTimeError) {
            Lox.runtimeError(error)
        }
    }

    override fun visitBlockStmt(stmt: Stmt.Block) {
        executeBlock(stmt.statements, Environment(environment))
    }

    fun executeBlock(statements: List<Stmt>, environment: Environment) {
        val previous = this.environment
        try {
            this.environment = environment

            for (statement in statements) {
                execute(statement)
            }
        }
        finally {
            this.environment = previous
        }
    }

    override fun visitExpressionStmt(stmt: Stmt.Expression) {
        evaluate(stmt.expression)
    }

    override fun visitFunctionStmt(stmt: Stmt.Function) {
        val function = LoxFunction(stmt)
        environment.define(stmt.name.lexeme, function)
    }

    override fun visitIfStmt(stmt: Stmt.If) {
        if (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.thenBranch)
        }
        else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch)
        }
    }

    override fun visitPrintStmt(stmt: Stmt.Print) = println(stringify(evaluate(stmt.expression)))

    override fun visitVarStmt(stmt: Stmt.Var) {
        val value: Any? = evaluate(stmt.initializer)
        environment.define(stmt.name.lexeme, value)
    }

    override fun visitWhileStmt(stmt: Stmt.While) {
        while (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.body)
        }
    }

    override fun visitAssignExpr(expr: Expr.Assign): Any? {
        val value = evaluate(expr.value)
        environment.assign(expr.name, value)
        return value
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

    override fun visitCallExpr(expr: Expr.Call): Any? {
        val callee = evaluate(expr.callee)
        val arguments = mutableListOf<Any?>()

        for (arg in expr.arguments) {
            arguments += evaluate(arg)
        }

        if (callee !is LoxCallable) {
            throw RunTimeError(expr.paren,  "Can only call functions and classes.")
        }

        val function: LoxCallable = callee

        if (arguments.size != function.arity()) {
            throw RunTimeError(
                expr.paren,
                "Expected ${function.arity()} arguments but got ${arguments.size}.")
        }

        return function.call(this, arguments)
    }

    override fun visitGroupingExpr(expr: Expr.Grouping): Any? = evaluate(expr.expression)

    override fun visitLiteralExpr(expr: Expr.Literal): Any? = expr.value

    override fun visitLogicalExpr(expr: Expr.Logical): Any? {
        val left = evaluate(expr.left)

        val isOr = expr.operator.type == TokenType.OR
        if (isOr && isTruthy(left)) return left
        if (!isOr && !isTruthy(left)) return left

        return evaluate(expr.right)
    }

    override fun visitUnaryExpr(expr: Expr.Unary): Any? {
        val right = evaluate(expr.right)
        return when (expr.operator.type) {
            TokenType.BANG -> !isTruthy(right)
            TokenType.MINUS -> -(right asDouble expr.operator)
            else -> null
        }
    }

    override fun visitVariableExpr(expr: Expr.Variable): Any? = environment.get(expr.name)

    private fun isTruthy(obj: Any?): Boolean =
        when (obj) {
            null -> false
            is Boolean -> obj
            else -> true
        }

    private fun execute(statement: Stmt) = statement.accept(this)

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
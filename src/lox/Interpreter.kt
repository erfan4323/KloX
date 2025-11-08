package lox

import kotlin.math.exp

class Interpreter: Expr.Visitor<Any?>, Stmt.Visitor<Unit> {
    val globals = Environment()
    private var environment: Environment = globals
    private val locals = HashMap<Expr, Int>()

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
            statements.forEach(::execute)
        } catch (error: RunTimeError) {
            Lox.runtimeError(error)
        }
    }

    fun resolve(expr: Expr, depth: Int) {
        locals[expr] = depth
    }

    override fun visitBlockStmt(stmt: Stmt.Block) {
        executeBlock(stmt.statements, Environment(environment))
    }

    override fun visitClassStmt(stmt: Stmt.Class) {
        environment.define(stmt.name.lexeme, null)

        val methods = mutableMapOf<String, LoxFunction>()
        for (method in  stmt.methods) {
            val function = LoxFunction(method,environment)
            methods[method.name.lexeme] = function
        }

        val klass = LoxClass(stmt.name.lexeme, methods)
        environment.assign(stmt.name, klass)
    }

    fun executeBlock(statements: List<Stmt>, environment: Environment) {
        val previous = this.environment
        try {
            this.environment = environment

            statements.forEach(::execute)
        }
        finally {
            this.environment = previous
        }
    }

    override fun visitExpressionStmt(stmt: Stmt.Expression) {
        evaluate(stmt.expression)
    }

    override fun visitFunctionStmt(stmt: Stmt.Function) {
        val function = LoxFunction(stmt, environment)
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

    override fun visitReturnStmt(stmt: Stmt.Return) {
        val value: Any? = stmt.value?.let { evaluate(it) }
        throw Return(value)
    }

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
        val distance = locals[expr]
        if (distance != null) {
            environment.assignAt(distance, expr.name, value)
        }
        else
        {
            environment.assign(expr.name, value)
        }
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

    override fun visitGetExpr(expr: Expr.Get): Any? {
        val obj = evaluate(expr.obj)
        return if (obj is LoxInstance) {
            obj.get(expr.name)
        } else {
            throw RunTimeError(expr.name, "Only instances have properties.")
        }
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

    override fun visitSetExpr(expr: Expr.Set): Any? {
        val obj = evaluate(expr.obj)
            ?: throw RunTimeError(expr.name, "Only instances have fields.")

        if (obj !is LoxInstance)
            throw RunTimeError(expr.name, "Only instances have fields.")

        val value = evaluate(expr.value)
        obj.set(expr.name, value)

        return value
    }

    override fun visitThisExpr(expr: Expr.This): Any? = lookupVariable(expr.keyword, expr)

    override fun visitUnaryExpr(expr: Expr.Unary): Any? {
        val right = evaluate(expr.right)
        return when (expr.operator.type) {
            TokenType.BANG -> !isTruthy(right)
            TokenType.MINUS -> -(right asDouble expr.operator)
            else -> null
        }
    }

    override fun visitVariableExpr(expr: Expr.Variable): Any? {
        return lookupVariable(expr.name, expr)
    }

    private fun lookupVariable(name: Token, expr: Expr): Any? {
        val distance = locals[expr]
        if (distance != null) {
            return environment.getAt(distance, name.lexeme)
        }
        else {
            return environment.get(name)
        }
    }

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
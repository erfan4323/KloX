package lox

import java.util.*

class Resolver(val interpreter: Interpreter): Expr.Visitor<Unit>, Stmt.Visitor<Unit> {
    private val scopes = Stack<MutableMap<String, Boolean>>()
    private var currentFunction = FunctionType.NONE
    private var currentClass = ClassType.NONE

    fun resolve(statements: List<Stmt>) = statements.forEach { resolve(it) }

    fun resolve(stmt: Stmt) = stmt.accept(this)

    fun resolve(expr: Expr) = expr.accept(this)

    override fun visitAssignExpr(expr: Expr.Assign) {
        resolve(expr.value)
        resolveLocal(expr, expr.name)
    }

    override fun visitBinaryExpr(expr: Expr.Binary) {
        resolve(expr.left)
        resolve(expr.right)
    }

    override fun visitCallExpr(expr: Expr.Call) {
        resolve(expr.callee)
        for (argument in expr.arguments) {
            resolve(argument)
        }
    }

    override fun visitGetExpr(expr: Expr.Get) {
        resolve(expr.obj)
    }

    override fun visitGroupingExpr(expr: Expr.Grouping) {
        resolve(expr.expression)
    }

    override fun visitLiteralExpr(expr: Expr.Literal) {
        return
    }

    override fun visitLogicalExpr(expr: Expr.Logical) {
        resolve(expr.left)
        resolve(expr.right)
    }

    override fun visitSetExpr(expr: Expr.Set) {
        resolve(expr.value)
        resolve(expr.obj)
    }

    override fun visitThisExpr(expr: Expr.This) {
        if (currentClass == ClassType.NONE) {
            Lox.error(expr.keyword, "Can't use 'this' outside of a class.");
        }

        resolveLocal(expr, expr.keyword)
    }

    override fun visitUnaryExpr(expr: Expr.Unary) {
        resolve(expr.right)
    }

    override fun visitVariableExpr(expr: Expr.Variable) {
        if (!scopes.isEmpty() && scopes.peek()[expr.name.lexeme] == false) {
            Lox.error(expr.name, "Can't read local variable in its own initializer.")
        }
        resolveLocal(expr, expr.name)
    }

    override fun visitBlockStmt(stmt: Stmt.Block) {
        beginScope()
        resolve(stmt.statements)
        endScope()
    }

    override fun visitClassStmt(stmt: Stmt.Class) {
        val enclosingClass = currentClass
        currentClass = ClassType.CLASS

        declare(stmt.name)
        define(stmt.name)

        beginScope()
        scopes.peek()["this"] = true

        for (method in stmt.methods) {
            val declaration =
                if (method.name.lexeme == "init")
                    FunctionType.INITIALIZER
                else
                    FunctionType.METHOD

            resolveFunction(method, declaration)
        }

        endScope()

        currentClass = enclosingClass
    }

    override fun visitExpressionStmt(stmt: Stmt.Expression) {
        resolve(stmt.expression)
    }

    override fun visitFunctionStmt(stmt: Stmt.Function) {
        declare(stmt.name)
        define(stmt.name)
        resolveFunction(stmt, FunctionType.FUNCTION)
    }

    override fun visitIfStmt(stmt: Stmt.If) {
        resolve(stmt.condition)
        resolve(stmt.thenBranch)
        stmt.elseBranch?.let { resolve(it) }
    }

    override fun visitPrintStmt(stmt: Stmt.Print) {
        resolve(stmt.expression)
    }

    override fun visitReturnStmt(stmt: Stmt.Return) {
        if (currentFunction == FunctionType.NONE) {
            Lox.error(stmt.keyword, "Can't return from top-level code.")
        }

        stmt.value?.let {
            if (currentFunction == FunctionType.INITIALIZER) {
                Lox.error(stmt.keyword, "Can't return a value from an initializer.")
            }
            resolve(it)
        }
    }

    override fun visitVarStmt(stmt: Stmt.Var) {
        declare(stmt.name)
        resolve(stmt.initializer)
        define(stmt.name)
    }

    override fun visitWhileStmt(stmt: Stmt.While) {
        resolve(stmt.condition)
        resolve(stmt.body)
    }

    private fun declare(name: Token) {
        if (scopes.isEmpty()) return
        val scope = scopes.peek()
        if (scope.containsKey(name.lexeme)) {
            Lox.error(name, "Already variable with this name in this scope.");
        }
        scope[name.lexeme] = false
    }

    private fun define(name: Token) {
        if (scopes.isEmpty()) return
        val scope = scopes.peek()
        scope[name.lexeme] = true
    }

    private fun beginScope() = scopes.push(HashMap<String, Boolean>())


    private fun endScope() = scopes.pop()

    private fun resolveLocal(expr: Expr, name: Token) {
        for (i in scopes.indices.reversed()) {
            if (!scopes[i].containsKey(name.lexeme)) continue
            interpreter.resolve(expr, scopes.size - 1 - i)
            return
        }
    }

    private fun resolveFunction(function: Stmt.Function, type: FunctionType) {
        val enclosingFunction = currentFunction
        currentFunction = type

        beginScope()
        for (param in function.params) {
            declare(param)
            define(param)
        }
        resolve(function.body)
        endScope()

        currentFunction = enclosingFunction
    }
}

private enum class FunctionType {
    NONE,
    FUNCTION,
    INITIALIZER,
    METHOD,
}

private enum class ClassType {
    NONE,
    CLASS,
}
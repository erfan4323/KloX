package lox

import java.util.Stack

class CppCodeGenerator : Expr.Visitor<String>, Stmt.Visitor<Unit> {
    private val code = StringBuilder()
    private var indentLevel = 0
    private val locals = Stack<MutableMap<String, String>>()
    private var currentClass: ClassType = ClassType.NONE
    private var superclassVar: String? = null
    private val varCounter = mutableMapOf<String, Int>()

    init {
        locals.push(HashMap())
    }

    fun generate(statements: List<Stmt>): String {
        code.clear()
        code.appendLine("#include \"lox_runtime.h\"")
        code.appendLine("#include <iostream>")
        code.appendLine("#include <memory>")
        code.appendLine("#include <functional>")
        code.appendLine("#include <variant>")
        code.appendLine("")
        code.appendLine("int main() {")
        indentLevel = 1  // Start with one level of indent

        statements.forEach { it.accept(this) }

        code.appendLine("    return 0;")
        code.appendLine("}")
        return code.toString()
    }

    private fun append(line: String) {
        code.append("    ".repeat(indentLevel)).appendLine(line)
    }

    override fun visitAssignExpr(expr: Expr.Assign): String {
        val value = expr.value.accept(this)
        val name = resolveVar(expr.name.lexeme)
        return "$name = $value"
    }

    override fun visitBinaryExpr(expr: Expr.Binary): String {
        val left = expr.left.accept(this)
        val right = expr.right.accept(this)
        return when (expr.operator.type) {
            TokenType.PLUS -> "add($left, $right)"
            TokenType.MINUS -> "subtract($left, $right)"
            TokenType.STAR -> "multiply($left, $right)"
            TokenType.SLASH -> "divide($left, $right)"
            TokenType.GREATER -> "greater($left, $right)"
            TokenType.GREATER_EQUAL -> "greater_equal($left, $right)"
            TokenType.LESS -> "less($left, $right)"
            TokenType.LESS_EQUAL -> "less_equal($left, $right)"
            TokenType.BANG_EQUAL -> "not_equal($left, $right)"
            TokenType.EQUAL_EQUAL -> "equal($left, $right)"
            else -> throw IllegalStateException("Unsupported binary operator: ${expr.operator.type}")
        }
    }

    override fun visitCallExpr(expr: Expr.Call): String {
        val callee = expr.callee.accept(this)
        val args = expr.arguments.joinToString(", ") { it.accept(this) }

        if (expr.callee is Expr.Get || expr.callee is Expr.Super || expr.callee is Expr.This) {
            // Value wrapper -> extract callable

            // Declare a temporary Value for the callee
            val tmpVar = declareCountedVar("tmpValue")
            val callablePtr = declareCountedVar("callablePtr")

            append("Value $tmpVar = $callee;")
            append("auto* $callablePtr = std::get_if<std::shared_ptr<LoxCallable>>(&$tmpVar);")
            append("if (!$callablePtr) throw std::runtime_error(\"Value is not callable\");")

            // Call the extracted callable
            return "(*$callablePtr)->call({$args})"
        } else {
            // Already a shared_ptr<LoxCallable> -> call directly
            return "$callee->call({$args})"
        }
    }


    override fun visitGetExpr(expr: Expr.Get): String {
        val obj = expr.obj.accept(this)
        val property = expr.name.lexeme

        if (expr.obj is Expr.This) {
            return "self->get(\"$property\")"
        }

        return "std::get<std::shared_ptr<LoxInstance>>($obj)->get(\"$property\")"
    }

    override fun visitGroupingExpr(expr: Expr.Grouping): String {
        return "(${expr.expression.accept(this)})"
    }

    override fun visitLiteralExpr(expr: Expr.Literal): String {
        return when (val value = expr.value) {
            is Number -> value.toString()
            is String -> "\"${value.replace("\"", "\\\"")}\""
            true -> "true"
            false -> "false"
            null -> "nullptr"
            else -> throw IllegalStateException("Unsupported literal: $value")
        }
    }

    override fun visitLogicalExpr(expr: Expr.Logical): String {
        val left = expr.left.accept(this)
        val right = expr.right.accept(this)
        return when (expr.operator.type) {
            TokenType.OR -> "isTruthy($left) ? $left : $right"
            TokenType.AND -> "isTruthy($left) ? $right : $left"
            else -> throw IllegalStateException("Unsupported logical")
        }
    }

    override fun visitSetExpr(expr: Expr.Set): String {
        val obj = expr.obj.accept(this)
        val value = expr.value.accept(this)
        append("std::get<std::shared_ptr<LoxInstance>>($obj)->set(\"${expr.name.lexeme}\", $value);")
        return value
    }

    override fun visitSuperExpr(expr: Expr.Super): String {
        if (currentClass != ClassType.SUBCLASS) throw IllegalStateException("super outside subclass")
        val superVar = superclassVar!!
        val methodName = expr.method.lexeme
        // Directly return the bound method and let the surrounding Call handle .call()
        return "std::make_shared<LoxBoundMethod>($superVar->methods[\"$methodName\"], self)"
    }

    override fun visitThisExpr(expr: Expr.This): String {
        return "self"
    }

    override fun visitUnaryExpr(expr: Expr.Unary): String {
        val right = expr.right.accept(this)
        return when (expr.operator.type) {
            TokenType.MINUS -> "negate($right)"
            TokenType.BANG -> "notOp($right)"
            else -> throw IllegalStateException("Unsupported unary: ${expr.operator.type}")
        }
    }

    override fun visitVariableExpr(expr: Expr.Variable): String {
        return resolveVar(expr.name.lexeme)
    }

    override fun visitBlockStmt(stmt: Stmt.Block) {
        beginScope()
        append("{")
        indentLevel++
        stmt.statements.forEach { it.accept(this) }
        indentLevel--
        append("}")
        endScope()
    }

    override fun visitClassStmt(stmt: Stmt.Class) {
        val oldClass = currentClass
        val oldSuper = superclassVar
        currentClass = if (stmt.superclass != null) ClassType.SUBCLASS else ClassType.CLASS
        superclassVar = stmt.superclass?.let { it.accept(this) }  // e.g., "Doughnut_1"

        try {
            val className = declareCountedVar(stmt.name.lexeme)  // e.g., "BostonCream_2"
            val superRef = superclassVar ?: "std::shared_ptr<LoxClass>(nullptr)"

            append("std::unordered_map<std::string, std::shared_ptr<LoxCallable>> ${className}_methods;")

            stmt.methods.forEach { method ->
                val methodName = method.name.lexeme
                visitFunctionStmt(method)
                val funcVar = resolveVar(methodName)
                append("${className}_methods[\"$methodName\"] = $funcVar;")
            }

            append("auto $className = std::make_shared<LoxClass>(\"${stmt.name.lexeme}\", $superRef, ${className}_methods);")
        } finally {
            currentClass = oldClass
            superclassVar = oldSuper
        }
    }

    override fun visitExpressionStmt(stmt: Stmt.Expression) {
        append("${stmt.expression.accept(this)};")
    }

    override fun visitFunctionStmt(stmt: Stmt.Function) {
        val funcName = declareCountedVar(stmt.name.lexeme)  // e.g., "cook_2"
        val isMethod = currentClass != ClassType.NONE
        val isInitializer = stmt.name.lexeme == "init"

        val paramLines = stmt.params
            .mapIndexed { i, param ->
                val paramName = declareCountedVar(param.lexeme)
                "Value $paramName = args[${i + if (isMethod) 1 else 0}];"
            }
            .joinToString("\n")

        beginScope()

        if (isMethod) {
            // Declare 'this' and force it to use the plain name "this"
            locals.peek()["this"] = "this"
        }

        val arity = if (isMethod) stmt.params.size + 1 else stmt.params.size
        append("std::shared_ptr<LoxFunction> $funcName;")
        append("$funcName = std::make_shared<LoxFunction>(${arity}, [&$funcName](const std::vector<Value>& args) mutable -> Value {")
        indentLevel++

        if (isMethod) {
            append("if (args.size() != ${stmt.params.size + 1}) throw std::runtime_error(\"Wrong arity.\");")
            append("auto self = std::get<std::shared_ptr<LoxInstance>>(args[0]);")
        } else {
            append("if (args.size() != ${stmt.params.size}) throw std::runtime_error(\"Wrong arity.\");")
        }

        if (paramLines.isNotEmpty()) {
            append(paramLines)
        }

        stmt.body.forEach { it.accept(this) }

        if (isMethod && isInitializer) {
            append("return self;")
        } else {
            append("return nullptr;")
        }

        indentLevel--
        append("});")
        endScope()
    }

    override fun visitIfStmt(stmt: Stmt.If) {
        val condition = stmt.condition.accept(this)
        append("if (isTruthy($condition)) {")
        indentLevel++
        stmt.thenBranch.accept(this)
        indentLevel--
        if (stmt.elseBranch != null) {
            append("} else {")
            indentLevel++
            stmt.elseBranch.accept(this)
            indentLevel--
        }
        append("}")
    }

    override fun visitPrintStmt(stmt: Stmt.Print) {
        append("print(Value(${stmt.expression.accept(this)}));")
    }

    override fun visitReturnStmt(stmt: Stmt.Return) {
        if (stmt.value == null) {
            append("return nullptr;")
        } else {
            val valueCode = stmt.value.accept(this)
            append("return Value($valueCode);")
        }
    }

    override fun visitVarStmt(stmt: Stmt.Var) {
        val init = stmt.initializer?.accept(this) ?: "nullptr"
        val name = declareCountedVar(stmt.name.lexeme)
        append("Value $name = $init;")
    }

    override fun visitWhileStmt(stmt: Stmt.While) {
        val condition = stmt.condition.accept(this)
        append("while (isTruthy($condition)) {")
        indentLevel++
        stmt.body.accept(this)
        indentLevel--
        append("}")
    }

    private fun beginScope() {
        locals.push(HashMap())
    }

    private fun endScope() {
        locals.pop()
    }

    private fun declareCountedVar(name: String): String {
        val count = varCounter.getOrDefault(name, 0) + 1
        varCounter[name] = count
        val scopedName = "${name}_$count"
        locals.peek()[name] = scopedName
        return scopedName
    }

    private fun declareVar(name: String): String {
        val scopedName = "${name}_${name.length}"
        locals.peek()[name] = scopedName
        return scopedName
    }

    private fun resolveVar(name: String): String {
        for (i in locals.indices.reversed()) {
            locals[i][name]?.let { return it }
        }
        throw IllegalStateException("Undefined variable $name")
    }
}
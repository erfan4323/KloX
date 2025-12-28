package lox

import java.util.Stack

class CppCodeGenerator : Expr.Visitor<String>, Stmt.Visitor<Unit> {
    private val code = StringBuilder()
    private var indentLevel = 0
    private val locals = Stack<MutableMap<String, String>>()
    private var currentClass: ClassType = ClassType.NONE
    private var superclassVar: String? = null
    private val varCounter = mutableMapOf<String, Int>()
    private var tempCounter = 0

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
        val argsCode = expr.arguments.joinToString(", ") { it.accept(this) }
        when (val callee = expr.callee) {
            is Expr.Get -> {
                val objCode = callee.obj.accept(this)
                val instPtr = valueToInstancePtr(objCode)
                return "CALL_METHOD($instPtr, ${callee.name.lexeme}${if (argsCode.isEmpty()) "" else ", $argsCode"})"
            }

            is Expr.Super -> {
                if (currentClass != ClassType.SUBCLASS)
                    throw RuntimeException("Cannot use 'super' outside a subclass.")

                val superClassVar = superclassVar!!
                val methodName = callee.method.lexeme

                tempCounter++
                val boundVar = "super_bound_$tempCounter"

                append("auto $boundVar = std::make_shared<LoxBoundMethod>($superClassVar->methods[\"$methodName\"], self);")
                return "$boundVar->call({$argsCode});"
            }

            else -> {
                val calleeCode = callee.accept(this)
                return "$calleeCode->call({$argsCode})"
            }
        }
    }

    override fun visitGetExpr(expr: Expr.Get): String {
        val objCode = expr.obj.accept(this)
        val instPtr = valueToInstancePtr(objCode)
        return "GET_FIELD($instPtr, ${expr.name.lexeme})"
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
        val objCode = expr.obj.accept(this)
        val instPtr = valueToInstancePtr(objCode)
        val value = expr.value.accept(this)
        append("SET_FIELD($instPtr, ${expr.name.lexeme}, $value);")
        return value
    }

    override fun visitSuperExpr(expr: Expr.Super): String {
        if (currentClass != ClassType.SUBCLASS) throw IllegalStateException("super outside subclass")
        val superVar = superclassVar!!
        val methodName = expr.method.lexeme
//        return "std::make_shared<LoxFunction>($superVar->methods[\"$methodName\"], self)"
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
        currentClass = if (stmt.superclass != null) ClassType.SUBCLASS else ClassType.CLASS
        superclassVar = stmt.superclass?.let { resolveVar(it.name.lexeme) }

        try {
            val className = declareCountedVar(stmt.name.lexeme)

            append("std::unordered_map<std::string, std::shared_ptr<LoxCallable>> ${className}_methods;")

            stmt.methods.forEach { method ->
                method.accept(this)  // Generates DEFINE_METHOD + assignment to temp var
                val funcVar = locals.peek()[method.name.lexeme]!!
                append("${className}_methods[\"${method.name.lexeme}\"] = $funcVar;")
            }

            val superRef = superclassVar ?: "nullptr"
            append("DEFINE_CLASS($className, $superRef);")
        } finally {
            currentClass = oldClass
            superclassVar = null
        }
    }

    override fun visitExpressionStmt(stmt: Stmt.Expression) {
        val exprCode = stmt.expression.accept(this)

        if (exprCode == "nullptr") {
            return
        }

        if (stmt.expression is Expr.Literal || stmt.expression is Expr.Variable || stmt.expression is Expr.Binary) {
            return
        }

        append("$exprCode;")
    }

    override fun visitFunctionStmt(stmt: Stmt.Function) {
        val funcName = declareCountedVar(stmt.name.lexeme)
        val isMethod = currentClass != ClassType.NONE
        val arity = if (isMethod) stmt.params.size + 1 else stmt.params.size

        beginScope()
        if (isMethod) locals.peek()["this"] = "self"

        append("DEFINE_METHOD($funcName, $arity, [&](const std::vector<Value>& args) mutable -> Value {")
        indentLevel++

        if (isMethod) {
            append("CHECK_ARITY(${stmt.params.size + 1});")
            append("auto self = SELF;")
        } else {
            append("CHECK_ARITY(${stmt.params.size});")
        }

        stmt.params.forEachIndexed { i, param ->
            val paramName = declareCountedVar(param.lexeme)
            val argIndex = if (isMethod) i + 1 else i
            append("Value $paramName = args[$argIndex];")
        }

        stmt.body.forEach { it.accept(this) }

        val returnExpr = if (isMethod && stmt.name.lexeme == "init") "self" else "nullptr"
        append("return $returnExpr;")

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
        append("PRINT(${stmt.expression.accept(this)});")
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
        if (stmt.initializer is Expr.Call && stmt.initializer.callee is Expr.Variable) {
            emitClassInstantiation(stmt.initializer, stmt.name.lexeme)
        } else {
            val init = stmt.initializer.accept(this)
            val name = declareCountedVar(stmt.name.lexeme)
            append("Value $name = $init;")
            locals.peek()[stmt.name.lexeme] = name
        }
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

    private fun emitClassInstantiation(callExpr: Expr.Call, assignTo: String? = null): String {
        require(callExpr.callee is Expr.Variable) { "Expected class name variable" }

        val classVar = resolveVar(callExpr.callee.name.lexeme)

        val valueVar = declareCountedVar(assignTo ?: "instance")

        val argsCode = callExpr.arguments.joinToString(", ") { it.accept(this) }
        append("INSTANCE($valueVar, $classVar${if (argsCode.isEmpty()) "" else ", $argsCode"});")

        if (assignTo != null) {
            val instVar = "${valueVar}_inst"
            locals.peek()[assignTo] = instVar
        }

        return valueVar
    }

    private fun exprToVar(expr: Expr): String {
        when (expr) {
            is Expr.Variable -> return resolveVar(expr.name.lexeme)
            is Expr.This -> return "self"
            else -> {
                val code = expr.accept(this)
                tempCounter++
                val temp = "temp_$tempCounter"
                append("Value $temp = $code;")
                return temp
            }
        }
    }

    private fun valueToInstancePtr(valueCode: String): String {
        if (valueCode == "self" || valueCode.endsWith("_inst")) {
            return valueCode
        }
        tempCounter++
        val tempValue = "temp_val_$tempCounter"
        val tempInst = "temp_inst_$tempCounter"
        append("Value $tempValue = $valueCode;")
        append("auto $tempInst = std::get<std::shared_ptr<LoxInstance>>($tempValue);")
        return tempInst
    }

    private fun resolveVar(name: String): String {
        for (i in locals.indices.reversed()) {
            locals[i][name]?.let { return it }
        }
        throw IllegalStateException("Undefined variable $name")
    }
}
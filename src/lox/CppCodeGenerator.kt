package lox

import java.util.Stack

class CppCodeGenerator : Expr.Visitor<String>, Stmt.Visitor<Unit> {
    private val code = StringBuilder()
    private var indentLevel = 0
    private val locals: ArrayDeque<MutableMap<String, String>> = ArrayDeque()
    private var currentClass: ClassType = ClassType.NONE
    private var superclassVar: String? = null
    private val varCounter = mutableMapOf<String, Int>()
    private var tempId = 0

    init {
        locals.addLast(mutableMapOf())
    }

    fun generate(statements: List<Stmt>): String {
        code.clear()
        emitHeaders()


        appendIndentedLine("int main() {")
        withIndent {
            statements.forEach { it.accept(this@CppCodeGenerator) }
            appendIndentedLine("return 0;")
        }
        appendIndentedLine("}")


        return code.toString()
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

        return when (val callee = expr.callee) {
            is Expr.Get -> {
                val objCode = callee.obj.accept(this)
                val instPtr = valueToInstancePtr(objCode)
                "CALL_METHOD($instPtr, ${callee.name.lexeme}${if (argsCode.isEmpty()) "" else ", $argsCode"})"
            }

            is Expr.Super -> {
                if (currentClass != ClassType.SUBCLASS) throw RuntimeException("Cannot use 'super' outside a subclass.")
                val superClassVar = superclassVar ?: throw IllegalStateException("superclass missing")
                val methodName = callee.method.lexeme
                val boundVar = freshTemp("super_bound")
                appendIndentedLine("auto $boundVar = std::make_shared<LoxBoundMethod>($superClassVar->methods[\"$methodName\"], self);")
                "$boundVar->call({$argsCode});"
            }

            else -> {
                val calleeCode = callee.accept(this)
                "$calleeCode->call({$argsCode})"
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
        appendIndentedLine("SET_FIELD($instPtr, ${expr.name.lexeme}, $value);")
        return value
    }

    override fun visitSuperExpr(expr: Expr.Super): String {
        if (currentClass != ClassType.SUBCLASS) throw IllegalStateException("super outside subclass")
        val superVar = superclassVar ?: throw IllegalStateException("superclass missing")
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
        appendIndentedLine("{")
        withIndent { stmt.statements.forEach { it.accept(this) } }
        appendIndentedLine("}")
        endScope()
    }

    override fun visitClassStmt(stmt: Stmt.Class) {
        val previousClass = currentClass
        currentClass = if (stmt.superclass != null) ClassType.SUBCLASS else ClassType.CLASS
        superclassVar = stmt.superclass?.let { resolveVar(it.name.lexeme) }

        try {
            val className = declareCountedVar(stmt.name.lexeme)
            appendIndentedLine("std::unordered_map<std::string, std::shared_ptr<LoxCallable>> ${className}_methods;")

            stmt.methods.forEach { method ->
                method.accept(this)
                val funcVar = currentScope()[method.name.lexeme] ?: throw IllegalStateException("method var missing")
                appendIndentedLine("${className}_methods[\"${method.name.lexeme}\"] = $funcVar;")
            }

            val superRef = superclassVar ?: "nullptr"
            appendIndentedLine("DEFINE_CLASS($className, $superRef);")
        } finally {
            currentClass = previousClass
            superclassVar = null
        }
    }

    override fun visitExpressionStmt(stmt: Stmt.Expression) {
        val exprCode = stmt.expression.accept(this)

        // ignore no-op literal/variable/binary expressions used as statements
        if (exprCode == "nullptr") return
        if (stmt.expression is Expr.Literal || stmt.expression is Expr.Variable || stmt.expression is Expr.Binary) return

        appendIndentedLine("$exprCode;")
    }

    override fun visitFunctionStmt(stmt: Stmt.Function) {
        val funcName = declareCountedVar(stmt.name.lexeme)
        val isMethod = currentClass != ClassType.NONE
        val arity = if (isMethod) stmt.params.size + 1 else stmt.params.size

        beginScope()
        if (isMethod) currentScope()["this"] = "self"

        appendIndentedLine("DEFINE_METHOD($funcName, $arity, [&](const std::vector<Value>& args) mutable -> Value {")
        withIndent {
            if (isMethod) {
                appendIndentedLine("CHECK_ARITY(${stmt.params.size + 1});")
                appendIndentedLine("auto self = SELF;")
            } else {
                appendIndentedLine("CHECK_ARITY(${stmt.params.size});")
            }

            stmt.params.forEachIndexed { i, param ->
                val paramName = declareCountedVar(param.lexeme)
                val argIndex = if (isMethod) i + 1 else i
                appendIndentedLine("Value $paramName = args[$argIndex];")
            }

            stmt.body.forEach { it.accept(this) }

            val returnExpr = if (isMethod && stmt.name.lexeme == "init") "self" else "nullptr"
            appendIndentedLine("return $returnExpr;")
        }
        appendIndentedLine("});")

        endScope()
    }

    override fun visitIfStmt(stmt: Stmt.If) {
        val condition = stmt.condition.accept(this)
        appendIndentedLine("if (isTruthy($condition)) {")
        withIndent { stmt.thenBranch.accept(this) }
        if (stmt.elseBranch != null) {
            appendIndentedLine("} else {")
            withIndent { stmt.elseBranch.accept(this) }
        }
        appendIndentedLine("}")
    }

    override fun visitPrintStmt(stmt: Stmt.Print) {
        appendIndentedLine("PRINT(${stmt.expression.accept(this)});")
    }

    override fun visitReturnStmt(stmt: Stmt.Return) {
        if (stmt.value == null) {
            appendIndentedLine("return nullptr;")
        } else {
            val valueCode = stmt.value.accept(this)
            appendIndentedLine("return Value($valueCode);")
        }
    }

    override fun visitVarStmt(stmt: Stmt.Var) {
        // Optimize class instantiation patterns into INSTANCE macro
        if (stmt.initializer is Expr.Call && stmt.initializer.callee is Expr.Variable) {
            emitClassInstantiation(stmt.initializer, stmt.name.lexeme)
            return
        }

        val init = stmt.initializer.accept(this)
        val name = declareCountedVar(stmt.name.lexeme)
        appendIndentedLine("Value $name = $init;")
        currentScope()[stmt.name.lexeme] = name
    }

    override fun visitWhileStmt(stmt: Stmt.While) {
        val condition = stmt.condition.accept(this)
        appendIndentedLine("while (isTruthy($condition)) {")
        withIndent { stmt.body.accept(this) }
        appendIndentedLine("}")
    }

    // ---------- Helpers ----------
    private fun emitHeaders() {
        val headers = listOf(
            "#include \"lox_runtime.h\"",
            "#include <iostream>",
            "#include <memory>",
            "#include <functional>",
            "#include <variant>",
            ""
        )
        headers.forEach { appendLine(it) }
    }


    private inline fun withIndent(block: () -> Unit) {
        indentLevel++
        try { block() } finally { indentLevel-- }
    }


    private fun append(line: String) {
        code.append(" ".repeat(indentLevel)).append(line)
    }


    private fun appendLine(line: String) {
        code.appendLine(line)
    }


    private fun appendIndentedLine(line: String) {
        append(line)
        code.appendLine()
    }


    private fun currentScope(): MutableMap<String, String> = locals.last()


    private fun beginScope() {
        locals.addLast(mutableMapOf())
    }


    private fun endScope() {
        if (locals.size > 1) locals.removeLast()
    }


    private fun freshTemp(prefix: String = "temp"): String {
        tempId++
        return "${prefix}_$tempId"
    }

    private fun declareCountedVar(name: String): String {
        val count = (varCounter[name] ?: 0) + 1
        varCounter[name] = count
        val scopedName = "${name}_$count"
        currentScope()[name] = scopedName
        return scopedName
    }

    private fun resolveVar(name: String): String {
        val iter = locals.iterator() // from oldest to newest
        val scopes = locals.toList()
        for (i in scopes.indices.reversed()) {
            scopes[i][name]?.let { return it }
        }
        throw IllegalStateException("Undefined variable $name")
    }

    // Ensure a Value expression becomes a variable when needed
    private fun exprToVar(expr: Expr): String {
        return when (expr) {
            is Expr.Variable -> resolveVar(expr.name.lexeme)
            is Expr.This -> "self"
            else -> {
                val codeStr = expr.accept(this)
                val tmp = freshTemp("val")
                appendIndentedLine("Value $tmp = $codeStr;")
                tmp
            }
        }
    }

    // Convert an arbitrary Value expression to an instance pointer variable
    private fun valueToInstancePtr(valueCode: String): String {
        // If it's already a known instance reference, return it directly
        if (valueCode == "self" || valueCode.endsWith("_inst")) return valueCode


        val tmpVal = freshTemp("val")
        val tmpInst = freshTemp("inst")
        appendIndentedLine("Value $tmpVal = $valueCode;")
        appendIndentedLine("auto $tmpInst = std::get<std::shared_ptr<LoxInstance>>($tmpVal);")
        return tmpInst
    }

    private fun emitClassInstantiation(callExpr: Expr.Call, assignTo: String? = null): String {
        require(callExpr.callee is Expr.Variable) { "Expected class name variable" }

        val classVar = resolveVar(callExpr.callee.name.lexeme)
        val valueVar = declareCountedVar(assignTo ?: "instance")

        val argsCode = callExpr.arguments.joinToString(", ") { it.accept(this) }
        appendIndentedLine("INSTANCE($valueVar, $classVar${if (argsCode.isEmpty()) "" else ", $argsCode"});")

        if (assignTo != null) {
            val instVar = "${valueVar}_inst"
            currentScope()[assignTo] = instVar
        }

        return valueVar
    }
}
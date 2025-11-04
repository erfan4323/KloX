package tools

import java.io.PrintWriter
import java.nio.file.Paths

fun main(args: Array<String>) {
    require(args.size == 1) { "Usage: generate_ast <output directory>" }

    val outputDir = args[0]

    defineAst(outputDir, "Expr", listOf(
        "Assign   : Token name, Expr value",
        "Binary   : Expr left, Token operator, Expr right",
        "Grouping : Expr expression",
        "Literal  : Any? value",
        "Unary    : Token operator, Expr right",
        "Variable : Token name"
    ))

    defineAst(outputDir, "Stmt", listOf(
        "Expression : Expr expression",
        "Print      : Expr expression",
        "Var        : Token name, Expr initializer"
    ))
}

fun defineAst(outputDir: String, baseName: String, types: List<String>) {
    val file = Paths.get(outputDir).resolve("$baseName.kt").toFile()

    file.printWriter().use {writer ->
        writer.println("package lox")
        writer.println()
        writer.println("sealed class $baseName {")

        defineVisitor(writer, baseName, types)
        writer.println()

        for (type in types) {
            val (className, fields) = type.split(":").map { it.trim() }
            defineType(writer, baseName, className, fields)
        }

        writer.println("    abstract fun <R> accept(visitor: Visitor<R>): R")

        writer.println("}")
        writer.close()
    }
}

fun defineVisitor(writer: PrintWriter, baseName: String, types: List<String>) {
    writer.println("    interface Visitor<R> {")

    val paramName = baseName.replaceFirstChar { it.lowercase() }

    for (type in types) {
        val typeName = type.substringBefore(":").trim()
        writer.println("        fun visit${typeName}${baseName}($paramName: $typeName): R")
    }

    writer.println("    }")
}

fun defineType(writer: PrintWriter, baseName: String, className: String, fieldList: String) {
    val fields = fieldList.split(", ").map { it.trim() }

    writer.println("    data class $className(")
    fields.forEachIndexed { index, field ->
        val (type, name) = field.split(" ")
        val comma = if (index < fields.lastIndex) "," else ""
        writer.println("        val $name: $type$comma")
    }
    writer.println("    ) : $baseName() {")
    writer.println("        override fun <R> accept(visitor: Visitor<R>): R =")
    writer.println("            visitor.visit${className}${baseName}(this)")
    writer.println("    }")
    writer.println()
}

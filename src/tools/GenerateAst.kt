package tools

import java.io.File
import java.io.PrintWriter
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if (args.size != 1) {
        error("Usage: generate_ast <output directory>")
        exitProcess(64)
    }
    val outputDir = args[0]
    defineAst(outputDir, "Expr", listOf(
        "Binary   : Expr left, Token operator, Expr right",
        "Grouping : Expr expression",
        "Literal  : Object value",
        "Unary    : Token operator, Expr right"
    ))
}

fun defineAst(outputDir: String, baseName: String, types: List<String>) {
    val path = "$outputDir/$baseName.kt"
    val file = File(path)

    file.printWriter().use {writer ->
        writer.println("package lox")
        writer.println()
        writer.println("abstract class $baseName {")

        defineVisitor(writer, baseName, types)
        writer.println()

        types.forEach { type ->
            val (className, fields) = type.split(":").map { it.trim() }
            defineType(writer, baseName, className, fields)
        }

        writer.println()
        writer.println("    abstract fun <R> accept(visitor: Visitor<R>): R")

        writer.println("}")
        writer.close()
    }
}

fun defineVisitor(writer: PrintWriter, baseName: String, types: List<String>) {
    writer.println("    interface Visitor<R> {")

    types.forEach { type ->
        val typeName = type.substringBefore(":").trim()
        val paramName = baseName.replaceFirstChar { it.lowercase() }
        writer.println("        fun visit${typeName}${baseName}($paramName: $typeName): R")
    }

    writer.println("    }")
}

fun defineType(writer: PrintWriter, baseName: String, className: String, fieldList: String) {
    val fields = fieldList.split(", ").map { it.trim() }

    writer.println("    class $className(")
    fields.forEachIndexed { index, field ->
        val (type, name) = field.split(" ")
        val comma = if (index < fields.lastIndex) "," else ""
        writer.println("        val $name: $type$comma")
    }
    writer.println("    ) : $baseName() {")
    writer.println()

    writer.println("        override fun <R> accept(visitor: Visitor<R>): R {")
    writer.println("            return visitor.visit${className}${baseName}(this)")
    writer.println("        }")
    writer.println()

    writer.println("    }")
    writer.println()
}

package lox

interface LoxCallable {
    fun arity(): Int
    fun call(interpreter: Interpreter, arguments: MutableList<Any?>): Any?
}
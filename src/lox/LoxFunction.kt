package lox

class LoxFunction(val declaration: Stmt.Function): LoxCallable {
    override fun arity(): Int = declaration.params.size

    override fun call(
        interpreter: Interpreter,
        arguments: MutableList<Any?>
    ): Any? {
        val environment = Environment(interpreter.globals)

        for (i in declaration.params.indices) {
            val paramName = declaration.params[i].lexeme
            val argumentValue = arguments[i]
            environment.define(paramName, argumentValue)
        }

        interpreter.executeBlock(declaration.body, environment)
        return null
    }

    override fun toString(): String = "<fn ${declaration.name.lexeme}>"
}
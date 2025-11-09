package lox

class LoxFunction(val declaration: Stmt.Function, val closure: Environment, val isInitializer: Boolean): LoxCallable {
    override fun arity(): Int = declaration.params.size

    override fun call(
        interpreter: Interpreter,
        arguments: MutableList<Any?>
    ): Any? {
        val environment = Environment(closure)

        for (i in declaration.params.indices) {
            val paramName = declaration.params[i].lexeme
            val argumentValue = arguments[i]
            environment.define(paramName, argumentValue)
        }

        try {
            interpreter.executeBlock(declaration.body, environment)
        }
        catch (returnValue: Return) {
            if (isInitializer) return closure.getAt(0, "this")
            return returnValue.value
        }

        if (isInitializer) return closure.getAt(0, "this")

        return null
    }

    fun bind(instance: LoxInstance): LoxFunction {
        val environment = Environment(closure)
        environment.define("this", instance)
        return LoxFunction(declaration, environment, isInitializer)
    }

    override fun toString(): String = "<fn ${declaration.name.lexeme}>"
}
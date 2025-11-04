package lox

class Environment {
    private var values = mutableMapOf<String, Any?>()

    fun define(name: String, value: Any?) {
        values[name] = value
    }

    fun get(name: Token): Any? {
        if (values.containsKey(name.lexeme)) {
            return values[name.lexeme]
        }

        throw RunTimeError(name, "Undefined variable '$name.lexeme'.")
    }
}
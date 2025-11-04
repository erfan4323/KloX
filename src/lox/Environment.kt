package lox

class Environment(private val enclosing: Environment? = null) {
    private var values = mutableMapOf<String, Any?>()

    fun define(name: String, value: Any?) {
        values[name] = value
    }

    fun get(name: Token): Any? {
        if (values.containsKey(name.lexeme)) {
            return values[name.lexeme];
        }

        if (enclosing != null) {
            return enclosing.get(name)
        }

        throw RunTimeError(name,"Undefined variable '${name.lexeme}'.")
    }


    fun assign(name: Token, value: Any?) {
        if (name.lexeme in values) {
            values[name.lexeme] = value
            return
        }

        if (enclosing != null) {
            enclosing.assign(name, value)
            return
        }

        throw RunTimeError(name, "Undefined variable '${name.lexeme}'.")
    }
}
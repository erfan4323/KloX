package lox

class LoxInstance(val klass: LoxClass) {
    val fields = mutableMapOf<String, Any?>()

    fun get(name: Token): Any
    {
        fields[name.lexeme]?.let { return it }

        klass.findMethod(name.lexeme)?.let { method ->
            return method.bind(this)
        }

        throw RunTimeError(name, "Undefined property '${name.lexeme}'.")
    }

    fun set(name: Token, value: Any?) {
        fields[name.lexeme] = value
    }

    override fun toString(): String = "${klass.name} instance"
}

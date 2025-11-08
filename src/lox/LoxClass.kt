package lox

class LoxClass(val name: String, val methods: MutableMap<String, LoxFunction>): LoxCallable {
    override fun arity(): Int = 0

    override fun call(
        interpreter: Interpreter,
        arguments: MutableList<Any?>
    ): Any = LoxInstance(this)

    fun findMethod(name: String): LoxFunction? {
        if (methods.containsKey(name)) {
            return methods[name]
        }
        return null
    }

    override fun toString(): String = name
}

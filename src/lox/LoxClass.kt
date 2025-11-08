package lox

class LoxClass(val name: String): LoxCallable {
    override fun arity(): Int = 0

    override fun call(
        interpreter: Interpreter,
        arguments: MutableList<Any?>
    ): Any = LoxInstance(this)


    override fun toString(): String = name
}

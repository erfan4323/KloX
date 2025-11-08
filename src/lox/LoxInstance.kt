package lox

class LoxInstance(val klass: LoxClass) {
    override fun toString(): String = "${klass.name} instance"
}

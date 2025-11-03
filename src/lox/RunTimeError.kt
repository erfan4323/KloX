package lox

class RunTimeError(val token: Token, message: String): RuntimeException(message)
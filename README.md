# KloX

A Kotlin implementation of the Lox programming language, inspired by Bob Nystrom's *Crafting Interpreters*.  
KloX features a tree-walk interpreter **and** a C++ transpiler, with plans for native code generation.

## Prerequisites

- Kotlin compiler (`kotlinc`)
- Java Runtime (to run the JAR)
- For compiled executables: a C++ compiler (e.g., `g++`) or platform-specific toolchain

## Building

```bash
# Generate AST classes (if needed)
kotlinc src/tools/GenerateAst.kt -include-runtime -d generate-ast.jar
java -jar generate-ast.jar src/lox

# Compile the interpreter/transpiler
kotlinc src/lox/*.kt -include-runtime -d klox.jar
```

The resulting `klox.jar` is the main executable.

## Usage

Run `java -jar klox.jar --help` for the full up-to-date help:

```
KloX - A Lox interpreter and compiler (with C++ transpiler)

Usage:
  kloX                          # Start interactive REPL
  kloX <file.lx>                # Run a script directly
  kloX run <file.lx>            # Explicitly run a script
  kloX repl                     # Start REPL
  kloX compile <file.lx>        # Compile to C++ or native

Commands:
  run <file.lx>        Run a Lox script using the interpreter
  repl                 Start an interactive REPL session
  compile <file.lx>    Transpile to C++ or compile to native executable

Global Options:
  --print-ast          Print the parsed AST (useful for debugging)
  --help, -h           Show this help message

Compile Options:
  --target <name>      Target backend: cemitter, x86_64 (default: cemitter)
  --cpp-file <path>    Output C++ source file (default: build/out.cpp)
  --exe-file <path>    Output executable path (default: build/out[.exe])

Examples:
  kloX script.lx
  kloX run script.lx --print-ast
  kloX compile script.lx --target cemitter --cpp-file myprog.cpp
  kloX compile script.lx --target x86_64 --exe-file bin/myapp

Source: https://github.com/erfan4323/KloX
```
## Lox Language Grammar
```antlr
------------------------------
SYNTAX GRAMMAR
------------------------------

program
    → declaration* EOF ;

declaration
    → classDecl
    | funDecl
    | varDecl
    | statement ;

classDecl
    → "class" IDENTIFIER ( "<" IDENTIFIER )?
      "{" function* "}" ;

funDecl
    → "fun" function ;

varDecl
    → "var" IDENTIFIER ( "=" expression )? ";" ;

statement
    → exprStmt
    | forStmt
    | ifStmt
    | printStmt
    | returnStmt
    | whileStmt
    | block ;

exprStmt
    → expression ";" ;

forStmt
    → "for" "(" ( varDecl | exprStmt | ";" )
               expression? ";"
               expression? ")" statement ;

ifStmt
    → "if" "(" expression ")" statement
      ( "else" statement )? ;

printStmt
    → "print" expression ";" ;

returnStmt
    → "return" expression? ";" ;

whileStmt
    → "while" "(" expression ")" statement ;

block
    → "{" declaration* "}" ;

------------------------------
EXPRESSIONS
------------------------------

expression
    → assignment ;

assignment
    → ( call "." )? IDENTIFIER "=" assignment
    | logic_or ;

logic_or
    → logic_and ( "or" logic_and )* ;

logic_and
    → equality ( "and" equality )* ;

equality
    → comparison ( ( "!=" | "==" ) comparison )* ;

comparison
    → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;

term
    → factor ( ( "-" | "+" ) factor )* ;

factor
    → unary ( ( "/" | "*" ) unary )* ;

unary
    → ( "!" | "-" ) unary
    | call ;

call
    → primary ( "(" arguments? ")" | "." IDENTIFIER )* ;

primary
    → "true"
    | "false"
    | "nil"
    | "this"
    | NUMBER
    | STRING
    | IDENTIFIER
    | "(" expression ")"
    | "super" "." IDENTIFIER ;

------------------------------
FUNCTIONS
------------------------------

function
    → IDENTIFIER "(" parameters? ")" block ;

parameters
    → IDENTIFIER ( "," IDENTIFIER )* ;

arguments
    → expression ( "," expression )* ;

------------------------------
LEXICAL GRAMMAR
------------------------------

NUMBER
    → DIGIT+ ( "." DIGIT+ )? ;

STRING
    → "\"" <any character except "\"">* "\"" ;

IDENTIFIER
    → ALPHA ( ALPHA | DIGIT )* ;

ALPHA
    → "a" … "z"
    | "A" … "Z"
    | "_" ;

DIGIT
    → "0" … "9" ;

```

## Architecture Overview

- **Scanner** → Tokens
- **Parser** → Abstract Syntax Tree (recursive descent with error recovery)
- **Interpreter** → Tree-walk execution (full Lox language support)
- **C++ Emitter** → Transpiles Lox source to readable C++ code
- **Future X86_64 backend** → Direct native code generation (in progress)

The project follows the structure and design principles from *Crafting Interpreters* while extending beyond interpretation with transpilation and compilation capabilities.

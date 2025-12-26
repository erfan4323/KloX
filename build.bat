@echo off
REM ====== Settings ======
SET OUT_JAR=out/KloX.jar
SET SRC_DIR=src
SET LOX_SRC=src/lox
SET MAIN_SRC=src/Main.kt

REM ====== Ensure output directory exists ======
if not exist out (
    mkdir out
)

echo Compiling Kotlin into a runnable fat JAR...
REM Compile everything into a single jar with Kotlin stdlib included
kotlinc %LOX_SRC% %MAIN_SRC% -include-runtime -d %OUT_JAR%
IF ERRORLEVEL 1 (
    echo Compilation failed.
    exit /b 1
)
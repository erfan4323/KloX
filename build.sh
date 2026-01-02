#!/usr/bin/env bash

# ====== Settings ======
OUT_JAR="out/KloX.jar"
SRC_DIR="src"
LOX_SRC="src/lox"
MAIN_SRC="src/Main.kt"

# ====== Ensure output directory exists ======
if [ ! -d "out" ]; then
  mkdir -p "out"
fi

echo "Compiling Kotlin into a runnable fat JAR..."

# Compile everything into a single jar with Kotlin stdlib included
kotlinc "$LOX_SRC" "$MAIN_SRC" -include-runtime -d "$OUT_JAR"
if [ $? -ne 0 ]; then
  echo "Compilation failed."
  exit 1
fi

echo "Done! JAR created at $OUT_JAR"

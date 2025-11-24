.PHONY: all clean run

OUT_DIR = out
TOOLS_DIR = $(OUT_DIR)/tools
LOX_JAR = $(OUT_DIR)/lox.jar
APP_JAR = $(OUT_DIR)/app.jar

all: $(APP_JAR)

$(TOOLS_DIR):
	mkdir -p $(TOOLS_DIR)

generate-ast: $(TOOLS_DIR)
	kotlinc src/tools/GenerateAst.kt -d $(TOOLS_DIR)
	kotlin -cp $(TOOLS_DIR) tools.GenerateAstKt src/lox

$(LOX_JAR): generate-ast
	kotlinc src/lox/*.kt -d $(LOX_JAR)

$(APP_JAR): $(LOX_JAR)
	kotlinc src/Main.kt -cp $(LOX_JAR) -include-runtime -d $(APP_JAR)

run: all
	@echo "------------------------------------------"
	java -cp "$(APP_JAR):$(LOX_JAR)" MainKt $(FILE)

clean:
	rm -rf $(OUT_DIR)

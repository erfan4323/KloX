add_rules("mode.debug", "mode.release")

set_languages("c++17")

target("lox_runtime")
set_kind("static")
add_files("src/lox_runtime.cpp")

target("runtime")
set_kind("binary")
add_deps("lox_runtime")
add_files("src/main.cpp")

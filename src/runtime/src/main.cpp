#include "lox_runtime.h"

int main() {
  // -------- Native function --------
  auto clockFn = std::make_shared<LoxFunction>(
      0, [](const std::vector<Value> &) -> Value { return 42.0; });

  Value clockValue = std::static_pointer_cast<LoxCallable>(clockFn);
  print(clockFn->call({})); // 42

  // -------- Class method --------
  auto sayHello = std::make_shared<LoxFunction>(
      1, // `this`
      [](const std::vector<Value> &args) -> Value {
        auto instance = std::get<std::shared_ptr<LoxInstance>>(args[0]);

        std::cout << "Hello from instance!" << std::endl;
        return nullptr;
      });

  // -------- init method --------
  auto initFn = std::make_shared<LoxFunction>(
      2, // this, value
      [](const std::vector<Value> &args) -> Value {
        auto instance = std::get<std::shared_ptr<LoxInstance>>(args[0]);

        instance->set("value", args[1]);
        return nullptr;
      });

  // -------- Build class --------
  std::unordered_map<std::string, std::shared_ptr<LoxCallable>> methods;
  methods["sayHello"] = sayHello;
  methods["init"] = initFn;

  auto myClass = std::make_shared<LoxClass>("MyClass", nullptr, methods);

  // -------- Call class (construct instance) --------
  Value instanceValue = myClass->call({123.0});
  auto instance = std::get<std::shared_ptr<LoxInstance>>(instanceValue);

  // -------- Access field --------
  print(instance->get("value")); // 123

  // -------- Call bound method --------
  auto helloMethod =
      std::get<std::shared_ptr<LoxCallable>>(instance->get("sayHello"));

  helloMethod->call({}); // prints message

  return 0;
}

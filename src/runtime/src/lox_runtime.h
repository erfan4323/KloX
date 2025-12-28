#ifndef LOX_RUNTIME_H
#define LOX_RUNTIME_H

#include <cstddef>
#include <functional>
#include <iostream>
#include <memory>
#include <string>
#include <unordered_map>
#include <variant>
#include <vector>

struct LoxCallable;
struct LoxInstance;
struct LoxClass;

using Value =
    std::variant<double, std::string, bool, std::nullptr_t,
                 std::shared_ptr<LoxCallable>, std::shared_ptr<LoxInstance>,
                 std::shared_ptr<LoxClass>>;

template <typename T> bool is(const Value &v) {
  return std::holds_alternative<T>(v);
}

double asNumber(const Value &v);
std::string asString(const Value &v);
bool asBool(const Value &v);
bool isNil(const Value &v);
bool isTruthy(const Value &v);

Value add(const Value &a, const Value &b);
Value subtract(const Value &a, const Value &b);
Value multiply(const Value &a, const Value &b);
Value divide(const Value &a, const Value &b);
Value negate(const Value &v);
Value notOp(const Value &v);
bool equal(const Value &a, const Value &b);
bool greater(const Value &a, const Value &b);
bool less(const Value &a, const Value &b);
bool not_equal(const Value &a, const Value &b);
bool greater_equal(const Value &a, const Value &b);
bool less_equal(const Value &a, const Value &b);

void print(const Value &v);

struct LoxCallable {
  virtual ~LoxCallable() = default;
  virtual int arity() const = 0;
  virtual Value call(const std::vector<Value> &args) = 0;
};

struct LoxInstance : std::enable_shared_from_this<LoxInstance> {
  std::shared_ptr<LoxClass> klass;
  std::unordered_map<std::string, Value> fields;

  LoxInstance(std::shared_ptr<LoxClass> k) : klass(k) {}
  Value get(const std::string &name);
  void set(const std::string &name, const Value &value);
};

struct LoxClass : public LoxCallable,
                  public std::enable_shared_from_this<LoxClass> {
  std::string name;
  std::shared_ptr<LoxClass> superclass;
  std::unordered_map<std::string, std::shared_ptr<LoxCallable>> methods;

  LoxClass(
      const std::string &n, std::shared_ptr<LoxClass> sup,
      const std::unordered_map<std::string, std::shared_ptr<LoxCallable>> &m)
      : name(n), superclass(sup), methods(m) {}

  int arity() const override;
  Value call(const std::vector<Value> &args) override;
};

struct LoxBoundMethod : LoxCallable {
  std::shared_ptr<LoxCallable> method;
  std::shared_ptr<LoxInstance> instance;

  LoxBoundMethod(std::shared_ptr<LoxCallable> m, std::shared_ptr<LoxInstance> i)
      : method(m), instance(i) {}

  int arity() const override { return method->arity(); }
  Value call(const std::vector<Value> &args) override {
    std::vector<Value> boundArgs = {
        std::static_pointer_cast<LoxInstance>(instance)};
    boundArgs.insert(boundArgs.end(), args.begin(), args.end());
    return method->call(boundArgs);
  }
};

struct LoxFunction : LoxCallable {
  std::function<Value(const std::vector<Value> &)> body;
  int argCount;

  LoxFunction(int ac, std::function<Value(const std::vector<Value> &)> b)
      : argCount(ac), body(b) {}

  int arity() const override { return argCount; }
  Value call(const std::vector<Value> &args) override {
    if (static_cast<int>(args.size()) != argCount)
      throw std::runtime_error("Wrong arity.");
    return body(args);
  }
};

#define DEFINE_CLASS(name, superclass) \
    auto name = std::make_shared<LoxClass>(#name, superclass, name##_methods);

#define METHOD(class_name, lexeme, var) \
    class_name##_methods[lexeme] = var;

#define DEFINE_METHOD(name, arity, body) \
    std::shared_ptr<LoxFunction> name = std::make_shared<LoxFunction>(arity, body);

#define INSTANCE(var, class_obj, ...) \
    Value var = class_obj->call({__VA_ARGS__}); \
    auto var##_inst = std::get<std::shared_ptr<LoxInstance>>(var);

#define SET_FIELD(inst, field, value) \
    inst->set(#field, value);

#define GET_FIELD(inst, field) \
    inst->get(#field)

#define CALL_METHOD(inst, method, ...) \
    do { \
        Value tmp = inst->get(#method); \
        auto* callable = std::get_if<std::shared_ptr<LoxCallable>>(&tmp); \
        if (!callable) throw std::runtime_error("Not callable: " #method); \
        (*callable)->call({ __VA_ARGS__ }); \
    } while(0)

#define PRINT(expr) print(Value(expr))

#define VAR(name, value) Value name = value;

#define SELF std::get<std::shared_ptr<LoxInstance>>(args[0])

#define CHECK_ARITY(n) \
    if (args.size() != n) throw std::runtime_error("Expected " #n " arguments");

#endif
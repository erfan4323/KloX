#include "lox_runtime.h"

double asNumber(const Value &v) {
  if (is<double>(v))
    return std::get<double>(v);
  throw std::runtime_error("Operand must be a number.");
}

std::string asString(const Value &v) {
  if (is<std::string>(v))
    return std::get<std::string>(v);
  throw std::runtime_error("Operand must be a string.");
}

bool asBool(const Value &v) {
  if (is<bool>(v))
    return std::get<bool>(v);
  throw std::runtime_error("Operand must be a boolean.");
}

bool isNil(const Value &v) { return is<std::nullptr_t>(v); }

bool isTruthy(const Value &v) {
  if (isNil(v))
    return false;
  if (is<bool>(v))
    return asBool(v);
  return true;
}

Value add(const Value &a, const Value &b) {
  if (is<double>(a) && is<double>(b))
    return asNumber(a) + asNumber(b);
  if (is<std::string>(a) && is<std::string>(b))
    return asString(a) + asString(b);
  throw std::runtime_error("Operands must be two numbers or two strings.");
}

Value subtract(const Value &a, const Value &b) {
  if (is<double>(a) && is<double>(b))
    return asNumber(a) - asNumber(b);
  throw std::runtime_error("Operands must be two numbers or two strings.");
}

Value multiply(const Value &a, const Value &b) {
  if (is<double>(a) && is<double>(b))
    return asNumber(a) * asNumber(b);
  throw std::runtime_error("Operands must be two numbers or two strings.");
}

Value divide(const Value &a, const Value &b) {
  if (is<double>(a) && is<double>(b)) {
    double divisor = asNumber(b);
    if (divisor == 0)
      throw std::runtime_error("Division by zero.");
    return asNumber(a) / divisor;
  }
  throw std::runtime_error("Operands must be two numbers or two strings.");
}

Value negate(const Value &v) { return -asNumber(v); }

Value notOp(const Value &v) { return !isTruthy(v); }

bool equal(const Value &a, const Value &b) {
  if (a.index() != b.index())
    return false;
  if (is<double>(a))
    return asNumber(a) == asNumber(b);
  if (is<std::string>(a))
    return asString(a) == asString(b);
  if (is<bool>(a))
    return asBool(a) == asBool(b);
  if (isNil(a))
    return true;

  return false;
}

bool greater(const Value &a, const Value &b) {
  return asNumber(a) > asNumber(b);
}

bool greaterEqual(const Value &a, const Value &b) {
  return asNumber(a) >= asNumber(b);
}

bool less(const Value &a, const Value &b) { return asNumber(a) < asNumber(b); }

bool lessEqual(const Value &a, const Value &b) {
  return asNumber(a) <= asNumber(b);
}

bool notEqual(const Value &a, const Value &b) { return !equal(a, b); }

void print(const Value &v) {
  if (is<double>(v))
    std::cout << asNumber(v);
  else if (is<std::string>(v))
    std::cout << asString(v);
  else if (is<bool>(v))
    std::cout << (asBool(v) ? "true" : "false");
  else if (isNil(v))
    std::cout << "nil";
  else if (is<std::shared_ptr<LoxCallable>>(v))
    std::cout << "<fn>";
  else if (is<std::shared_ptr<LoxInstance>>(v))
    std::cout << "<instance>";
  else
    std::cout << "<unknown>";
  std::cout << std::endl;
}

Value LoxInstance::get(const std::string &name) {
  auto it = fields.find(name);
  if (it != fields.end())
    return it->second;
  auto methodIt = klass->methods.find(name);
  if (methodIt != klass->methods.end()) {
    auto bound =
        std::make_shared<LoxBoundMethod>(methodIt->second, shared_from_this());
    return std::static_pointer_cast<LoxCallable>(bound);
  }
  throw std::runtime_error("Undefined property '" + name + "'.");
}

void LoxInstance::set(const std::string &name, const Value &value) {
  fields[name] = value;
}

int LoxClass::arity() const {
  auto init = methods.find("init");
  return (init != methods.end()) ? init->second->arity() : 0;
}

Value LoxClass::call(const std::vector<Value> &args) {
  auto instance = std::make_shared<LoxInstance>(shared_from_this());

  auto init = methods.find("init");
  if (init != methods.end()) {
    auto boundInit = std::make_shared<LoxBoundMethod>(init->second, instance);
    boundInit->call(args);
  }
  return std::static_pointer_cast<LoxInstance>(instance);
}

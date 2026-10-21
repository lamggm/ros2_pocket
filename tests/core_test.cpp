#include "model.hpp"
#include <iostream>
#include <stdexcept>

static void check(bool value, const char* message) {
  if (!value) throw std::runtime_error(message);
}
int main() {
  try {
    check(pocket::valid_domain(42), "valid domain 42 was rejected");
    check(pocket::valid_domain(0), "domain 0 was rejected");
    check(pocket::valid_domain(101), "domain 101 was rejected");
    check(!pocket::valid_domain(-1), "negative domain accepted");
    check(!pocket::valid_domain(102), "nonportable domain accepted");
    std::cout << "PASS portable domain boundary validation\n";
    pocket::Registry registry;
    registry.put("one", "rt/chatter", "std_msgs::msg::dds_::String_", true);
    registry.put("one", "rt/chatter", "std_msgs::msg::dds_::String_", true);
    registry.put("two", "rt/chatter", "std_msgs::msg::dds_::String_", false);
    registry.put("service", "rq/testRequest", "test", true);
    auto rows = registry.topics();
    check(rows.size() == 1, "non-topic endpoint leaked or duplicate topic");
    check(rows[0].name == "/chatter", "ROS name demangling failed");
    check(rows[0].type == "std_msgs/msg/String", "ROS type demangling failed");
    check(rows[0].publishers == 1 && rows[0].subscribers == 1, "endpoint counts incorrect");
    registry.erase("one");
    check(registry.topics()[0].publishers == 0, "disposed publisher remained visible");
    registry.erase("two");
    check(registry.topics().empty(), "dead topic remained visible");
    check(pocket::json_quote("a\n\"\\\t") == "\"a\\n\\\"\\\\\\t\"", "JSON control escaping failed");
    std::cout << "PASS endpoint discovery, removal, ROS mapping and JSON escaping\n";
  } catch (const std::exception& e) {
    std::cerr << "FAIL " << e.what() << '\n'; return 1;
  }
}

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
    pocket::Registry graph;
    graph.put("w", "rt/graph", "T", true, "p");
    graph.put("r", "rt/graph", "T", false, "p");
    graph.put("other", "rt/graph", "T", true, "other_participant");
    graph.set_graph("p", {{"/ns/node", {"r", "w"}, {"w", "w", "r", "other"}}});
    auto endpoints=graph.topics()[0].endpoints;
    check(endpoints[0].id=="other" && endpoints[0].nodes.empty(), "cross-participant graph alias accepted");
    check(endpoints[1].id=="r" && endpoints[1].nodes==std::vector<std::string>{"/ns/node"}, "reader GID lookup failed");
    check(endpoints[2].id=="w" && endpoints[2].nodes==std::vector<std::string>{"/ns/node"}, "writer GID lookup/dedup failed");
    graph.erase("w");graph.put("w", "rt/graph", "T", true, "p");
    check(graph.topics()[0].endpoints[2].nodes.empty(), "disposed GID inherited stale graph association");
    graph.erase_participant("p");
    check(graph.nodes().empty() && graph.topics()[0].endpoints.size()==1, "participant cleanup removed wrong endpoints or retained graph");
    graph.set_graph("other_participant", {{"/other", {}, {"other"}}});graph.clear();
    check(graph.nodes().empty() && graph.topics().empty(), "registry clear retained graph state");
    std::cout << "PASS portable graph participant/role identity, GID reuse and cleanup\n";
    pocket::Registry bounded;
    for(int i=0;i<4100;i++)bounded.put(std::to_string(i), "rt/bounded", "T", true, "p");
    check(bounded.topics()[0].endpoints.size()==4096, "endpoint cache is not bounded");
    pocket::EndpointQos changed;changed.reliability="reliable";
    bounded.put("0", "rt/bounded", "T", true, "p", changed);
    check(bounded.topics()[0].endpoints[0].qos.reliability=="reliable", "full cache blocked existing endpoint QoS update");
    std::vector<pocket::GraphNode> many_nodes;
    for(int i=0;i<140;i++)many_nodes.push_back({"/node"+std::to_string(i),{}, {"0"}});
    bounded.set_graph("p",many_nodes);
    check(bounded.nodes().size()==128 && bounded.topics()[0].endpoints[0].nodes.size()==128, "graph node cache is not bounded");
    std::vector<std::string> many_gids;
    for(int i=0;i<1100;i++)many_gids.push_back(std::to_string(i));
    bounded.set_graph("p", {{"/bounded",{},many_gids}});
    auto bounded_endpoints=bounded.topics()[0].endpoints;
    size_t linked=0;for(const auto& e:bounded_endpoints)if(!e.nodes.empty())++linked;
    check(linked==1024, "graph GID cache is not bounded");
    pocket::EndpointDetail unknown;
    check(pocket::reader_mismatches(unknown,true,true).empty(), "unknown/subscriber QoS invented incompatibility");
    unknown.writer=true;
    check(pocket::reader_mismatches(unknown,true,true).empty(), "unknown offered QoS invented incompatibility");
    check(pocket::endpoint_json(unknown).find("\"deadlineNs\":-2")!=std::string::npos, "unadvertised duration must differ from infinite duration");
    std::cout << "PASS endpoint/graph bounds, full-cache QoS update and explicit unknown policies\n";
  } catch (const std::exception& e) {
    std::cerr << "FAIL " << e.what() << '\n'; return 1;
  }
}

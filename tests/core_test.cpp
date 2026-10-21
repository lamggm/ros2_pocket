#include "model.hpp"
#include <iostream>
#include <stdexcept>

static void check(bool value, const char* message) {
  if (!value) throw std::runtime_error(message);
}
int main() {
  try {
    pocket::RosoutBuffer logs;
    for(int i=0;i<300;i++) logs.push({i%5, "logger", "sample", "file.cpp", "fn", static_cast<uint32_t>(i), i, static_cast<uint32_t>(i), static_cast<uint64_t>(i)});
    check(logs.size()==200, "rosout ring must retain only its newest 200 entries");
    check(logs.front().source_sec==100, "rosout ring did not evict oldest sample");
    check(logs.bytes()<=256*1024, "rosout ring exceeded byte budget");
    std::cout << "PASS bounded rosout ring count and bytes\n";
    pocket::RosoutBuffer byte_limited;
    for(int i=0;i<100;i++){pocket::RosoutEntry e;e.message=std::string(4096,'x');e.source_sec=i;byte_limited.push(std::move(e));}
    check(byte_limited.size()<100&&byte_limited.bytes()<=256*1024&&byte_limited.evicted()>0,"rosout byte budget did not evict oversized ring history");
    pocket::RosoutBuffer long_logs; pocket::RosoutEntry huge; huge.message.clear();
    for(int i=0;i<3000;i++) huge.message += "🚀";
    long_logs.push(huge);
    check(long_logs.entries_view().front().message.size()<=4096, "rosout message exceeded per-field byte limit");
    check(long_logs.entries_view().front().message.size()%4==0, "UTF-8 truncation split a four-byte code point");
    for(const std::string codepoint:{"é","漢","🚀"}) {
      pocket::RosoutBuffer edge; pocket::RosoutEntry e;e.message=std::string(4095,'x')+codepoint+"tail";edge.push(e);
      check(edge.entries_view().front().message==std::string(4095,'x'),"non-aligned 2/3/4-byte UTF-8 truncation retained a partial code point");
    }
    std::string wire_text(10000,'z');auto copied=pocket::RosoutBuffer::bounded_field(wire_text.c_str(),4096);
    check(copied.size()==4099,"native C-string copy exceeded field limit plus bounded UTF-8 lookahead");
    pocket::RosoutBuffer truncation; pocket::RosoutEntry truncated;truncated.message=std::move(copied);truncation.push(std::move(truncated));
    check(truncation.entries_view().front().message.size()==4096&&truncation.entries_view().front().truncated,"field clipping did not retain its truncation marker");
    std::cout << "PASS rosout long UTF-8 field bound and codepoint boundary\n";
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
    check(pocket::json_quote(std::string("\xf0\x28\x8c\x28",4))=="\"\\ufffd(\\ufffd(\"", "invalid UTF-8 was not made safe for JSON");
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

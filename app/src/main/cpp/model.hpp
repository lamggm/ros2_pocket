#pragma once
#include <cstdint>
#include <string>
#include <vector>
#include <stdexcept>
#include <map>
#include <sstream>
#include <iomanip>
namespace pocket {
inline std::string connection_state(bool running, size_t peers, bool seen_peer, const std::string& error) {
 if (!error.empty()) return "error";
 if (!running) return "offline";
 if (peers) return "connected";
 return seen_peer ? "reconnecting" : "discovering";
}
inline std::string subscription_state(bool reader, uint64_t count, int matched, double age, const std::string& error) {
 if (!error.empty()) return "error";
 if (!reader) return "inactive";
 if (!count) return "waiting";
 return matched > 0 && age >= 0 && age <= 3 ? "receiving" : "stale";
}
inline bool valid_domain(int domain) { return domain >= 0 && domain <= 101; }
struct TopicRow { std::string name, type, wire_name, wire_type; int publishers=0, subscribers=0; };
inline std::string ros_type(std::string type) {
 const std::string marker = "::dds_::";
 auto pos = type.find(marker);
 if (pos != std::string::npos) {
   type.replace(pos, marker.size(), "::");
   if (!type.empty() && type.back() == '_') type.pop_back();
 }
 while ((pos = type.find("::")) != std::string::npos) type.replace(pos, 2, "/");
 return type;
}
class Registry {
 struct Endpoint { std::string topic, type; bool writer; };
 std::map<std::string, Endpoint> endpoints;
 public:
 void put(const std::string& id, const std::string& topic, const std::string& type, bool writer) {
   if (endpoints.size() < 4096 || endpoints.count(id)) endpoints[id] = {topic,type,writer};
 }
 void erase(const std::string& id) { endpoints.erase(id); }
 void clear() { endpoints.clear(); }
 std::vector<TopicRow> topics() const {
   std::map<std::pair<std::string,std::string>, TopicRow> grouped;
   for (const auto& item : endpoints) {
     const auto& e=item.second;
     if (e.topic.rfind("rt/",0) != 0) continue;
     auto& row=grouped[{e.topic,e.type}];
     row.name=e.topic.substr(2); row.type=ros_type(e.type);
     row.wire_name=e.topic; row.wire_type=e.type;
     if (e.writer) ++row.publishers; else ++row.subscribers;
   }
   std::vector<TopicRow> rows;
   for (const auto& row: grouped) rows.push_back(row.second);
   return rows;
 }
};
inline std::string json_quote(const std::string& text) {
 std::ostringstream out; out << '"';
 for (unsigned char c : text) {
   switch (c) {
    case '"': out << "\\\""; break;
    case '\\': out << "\\\\"; break;
    case '\n': out << "\\n"; break;
    case '\r': out << "\\r"; break;
    case '\t': out << "\\t"; break;
    default:
     if(c < 0x20) out << "\\u" << std::hex << std::setw(4) << std::setfill('0') << (int)c << std::dec;
     else out << (char)c;
   }
 }
 out << '"'; return out.str();
}
}

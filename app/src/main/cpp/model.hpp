#pragma once
#include <cstdint>
#include <string>
#include <vector>
#include <stdexcept>
#include <map>
#include <sstream>
#include <iomanip>
#include <cstdint>
#include <set>
#include <algorithm>
#include <deque>
namespace pocket {
struct RosoutEntry {
 int level=0; std::string logger,message,file,function; uint32_t line=0;
 int32_t source_sec=0; uint32_t source_nanosec=0; uint64_t intake_ms=0;
 bool truncated=false;
};
class RosoutBuffer {
 std::deque<RosoutEntry> entries;
 size_t used=0;
 uint64_t evicted_count=0;
 static size_t cost(const RosoutEntry& e) { return sizeof(e)+e.logger.size()+e.message.size()+e.file.size()+e.function.size(); }
 static std::string clipped(const std::string& value,size_t limit) {
  if(value.size()<=limit)return value;
  size_t end=limit;
  while(end>0 && (static_cast<unsigned char>(value[end])&0xc0)==0x80)--end;
  if(end==0)return {};
  const auto first=static_cast<unsigned char>(value[end]);
  const size_t width=first<0x80?1:(first&0xe0)==0xc0?2:(first&0xf0)==0xe0?3:(first&0xf8)==0xf0?4:1;
  if(end+width>limit)return value.substr(0,end);
  return value.substr(0,limit);
 }
 public:
 static constexpr size_t max_entries=200,max_bytes=256*1024;
 static constexpr size_t max_logger_bytes=256,max_message_bytes=4096,max_source_bytes=1024;
 static std::string bounded_field(const char* value,size_t limit) {
  if(!value)return {};
  size_t size=0;while(size<limit+3&&value[size]!='\0')++size;
  return std::string(value,size);
 }
 void push(RosoutEntry e) {
  e.truncated=e.truncated||e.logger.size()>max_logger_bytes||e.message.size()>max_message_bytes||e.file.size()>max_source_bytes||e.function.size()>max_source_bytes;
  e.logger=clipped(e.logger,max_logger_bytes);e.message=clipped(e.message,max_message_bytes);
  e.file=clipped(e.file,max_source_bytes);e.function=clipped(e.function,max_source_bytes);
  const size_t bytes=cost(e);
  if(bytes>max_bytes)return;
  while(!entries.empty()&&(entries.size()>=max_entries||used+bytes>max_bytes)){
   used-=cost(entries.front());entries.pop_front();++evicted_count;
  }
  used+=bytes;entries.push_back(std::move(e));
 }
 void clear(){entries.clear();used=0;evicted_count=0;}
 size_t size() const { return entries.size(); }
 size_t bytes() const { return used; }
 uint64_t evicted() const { return evicted_count; }
 const RosoutEntry& front() const { return entries.front(); }
 const std::deque<RosoutEntry>& entries_view() const { return entries; }
};
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
struct EndpointQos {
 std::string reliability="unknown", durability="unknown", history="unknown";
 int depth=-1;
 // -1 is DDS infinity; -2 means the builtin did not advertise this policy.
 int64_t deadline_ns=-2, lease_duration_ns=-2;
 std::string liveliness="unknown", ownership="unknown", destination_order="unknown";
};
struct EndpointDetail {
 std::string id, participant;
 bool writer=false;
 std::vector<std::string> nodes;
 EndpointQos qos;
};
struct TopicRow {
 std::string name, type, wire_name, wire_type;
 int publishers=0, subscribers=0;
 std::vector<EndpointDetail> endpoints;
};
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
struct GraphNode {
 std::string name;
 std::vector<std::string> readers, writers;
};
class Registry {
 struct Endpoint { std::string topic, type; EndpointDetail detail; };
 struct Graph {
  std::set<std::string> names;
  std::map<std::pair<std::string,bool>,std::set<std::string>> links;
 };
 std::map<std::string, Endpoint> endpoints;
 std::map<std::string, Graph> graphs;
 public:
 void set_graph(const std::string& participant,const std::vector<GraphNode>& nodes) {
  if(graphs.size()>=256 && !graphs.count(participant))return;
  Graph next;size_t references=0;
  for(size_t i=0;i<std::min<size_t>(nodes.size(),128);++i) {
   const auto& node=nodes[i];if(node.name.empty())continue;
   next.names.insert(node.name);
   for(bool writer:{false,true})for(const auto& id:writer?node.writers:node.readers) {
    if(references++>=1024)break;
    next.links[{id,writer}].insert(node.name);
   }
  }
  graphs[participant]=std::move(next);
 }
 std::vector<std::string> nodes() const {
  std::set<std::string> names;
  for(const auto& graph:graphs)names.insert(graph.second.names.begin(),graph.second.names.end());
  return {names.begin(),names.end()};
 }
 void put(const std::string& id, const std::string& topic, const std::string& type, bool writer,
          const std::string& participant="", const EndpointQos& qos={}) {
   if (endpoints.size() < 4096 || endpoints.count(id)) endpoints[id] = {topic,type,{id,participant,writer,{},qos}};
 }
 void erase_graph(const std::string& participant) { graphs.erase(participant); }
 void erase_participant(const std::string& participant) {
  graphs.erase(participant);
  for(auto it=endpoints.begin();it!=endpoints.end();)
   if(it->second.detail.participant==participant)it=endpoints.erase(it);else ++it;
 }
 void erase(const std::string& id) {
  endpoints.erase(id);
  for(auto& graph:graphs){graph.second.links.erase({id,false});graph.second.links.erase({id,true});}
 }
 void clear() { endpoints.clear();graphs.clear(); }
 std::vector<TopicRow> topics() const {
   std::map<std::pair<std::string,std::string>, TopicRow> grouped;
   for (const auto& item : endpoints) {
     const auto& e=item.second;
     if (e.topic.rfind("rt/",0) != 0) continue;
     auto& row=grouped[{e.topic,e.type}];
     row.name=e.topic.substr(2); row.type=ros_type(e.type);
     row.wire_name=e.topic; row.wire_type=e.type;
     if (e.detail.writer) ++row.publishers; else ++row.subscribers;
     auto detail=e.detail;
     auto graph=graphs.find(detail.participant);
     if(graph!=graphs.end()) {
      auto link=graph->second.links.find({detail.id,detail.writer});
      if(link!=graph->second.links.end())detail.nodes.assign(link->second.begin(),link->second.end());
     }
     row.endpoints.push_back(std::move(detail));
   }
   std::vector<TopicRow> rows;
   for (const auto& row: grouped) rows.push_back(row.second);
   return rows;
 }
};
inline std::string json_quote(const std::string& text) {
 std::ostringstream out; out << '"';
 for (size_t i=0;i<text.size();) {
   const auto c=static_cast<unsigned char>(text[i]);
   switch (c) {
    case '"': out << "\\\""; ++i; continue;
    case '\\': out << "\\\\"; ++i; continue;
    case '\n': out << "\\n"; ++i; continue;
    case '\r': out << "\\r"; ++i; continue;
    case '\t': out << "\\t"; ++i; continue;
    default:
     if(c < 0x20) {out << "\\u" << std::hex << std::setw(4) << std::setfill('0') << (int)c << std::dec;++i;continue;}
     if(c<0x80){out<<(char)c;++i;continue;}
     size_t width=0;
     if(c>=0xc2&&c<=0xdf)width=2;else if(c>=0xe0&&c<=0xef)width=3;else if(c>=0xf0&&c<=0xf4)width=4;
     bool valid=width>0&&i+width<=text.size();
     for(size_t j=1;valid&&j<width;j++){const auto next=static_cast<unsigned char>(text[i+j]);if((next&0xc0)!=0x80)valid=false;}
     if(valid&&width==3){const auto second=static_cast<unsigned char>(text[i+1]);if((c==0xe0&&second<0xa0)||(c==0xed&&second>=0xa0))valid=false;}
     if(valid&&width==4){const auto second=static_cast<unsigned char>(text[i+1]);if((c==0xf0&&second<0x90)||(c==0xf4&&second>0x8f))valid=false;}
     if(valid){out.write(text.data()+i,static_cast<std::streamsize>(width));i+=width;}
     else {out<<"\\ufffd";++i;}
   }
 }
 out << '"'; return out.str();
}
// This app requests automatic/infinite liveliness, infinite deadline,
// shared ownership and reception order. Reliability/durability are selectable.
// Missing policies, partitions, type consistency and other DDS constraints keep
// an otherwise non-mismatching endpoint UNKNOWN, never fully compatible.
inline std::vector<std::string> reader_mismatches(const EndpointDetail& e,bool reliable,bool transient) {
 std::vector<std::string> reasons;
 if(!e.writer)return reasons; // A subscription is not an offer to this reader.
 if(reliable && e.qos.reliability=="best_effort")
  reasons.push_back("Reliability: writer offers best_effort; reader requests reliable");
 if(transient && e.qos.durability=="volatile")
  reasons.push_back("Durability: writer offers volatile; reader requests transient_local");
 if(e.qos.ownership=="exclusive")
  reasons.push_back("Ownership: writer offers exclusive; reader requests shared");
 return reasons;
}
inline std::string endpoint_json(const EndpointDetail& e,bool reliable=false,bool transient=false) {
 std::ostringstream out;
 out<<"{\"id\":"<<json_quote(e.id)<<",\"participant\":"<<json_quote(e.participant)
    <<",\"writer\":"<<(e.writer?"true":"false")<<",\"nodes\":[";
 bool first=true;
 for(const auto& node:e.nodes){if(!first)out<<',';first=false;out<<json_quote(node);}
 const auto& q=e.qos;
 out<<"],\"qos\":{\"reliability\":"<<json_quote(q.reliability)
    <<",\"durability\":"<<json_quote(q.durability)<<",\"history\":"<<json_quote(q.history)
    <<",\"depth\":"<<q.depth<<",\"deadlineNs\":"<<q.deadline_ns
    <<",\"liveliness\":"<<json_quote(q.liveliness)<<",\"leaseDurationNs\":"<<q.lease_duration_ns
    <<",\"ownership\":"<<json_quote(q.ownership)<<",\"destinationOrder\":"<<json_quote(q.destination_order)<<"},\"compatibility\":{";
 const auto reasons=reader_mismatches(e,reliable,transient);
 out<<"\"state\":"<<json_quote(reasons.empty()?"unknown":"incompatible")<<",\"reasons\":[";
 first=true;for(const auto& reason:reasons){if(!first)out<<',';first=false;out<<json_quote(reason);}
 out<<"]}}";
 return out.str();
}
}

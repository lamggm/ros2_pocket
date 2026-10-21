#include "inspector.hpp"
#include "model.hpp"
#include "structured.hpp"
#include "PocketTypes.h"
#include <dds/dds.h>
#include <arpa/inet.h>
#include <chrono>
#include <cmath>
#include <cstring>
#include <algorithm>
#include <set>
#include <array>

namespace pocket {
using Clock=std::chrono::steady_clock;
static std::string error(const std::string& message) {return "{\"ok\":false,\"error\":"+json_quote(message)+"}";}
static bool ipv4(const std::string& s) {in_addr a{};return inet_pton(AF_INET,s.c_str(),&a)==1;}
static std::string key(const uint8_t* data) {
 const char* hex="0123456789abcdef"; std::string s; s.reserve(32);
 for(int i=0;i<16;i++){s+=hex[data[i]>>4];s+=hex[data[i]&15];} return s;
}
static std::string key(const dds_guid_t& guid) {return key(guid.v);}
static EndpointQos endpoint_qos(const dds_qos_t* q) {
 EndpointQos out;
 if(!q)return out;
 dds_reliability_kind_t reliability;
 if(dds_qget_reliability(q,&reliability,nullptr)) {
  if(reliability==DDS_RELIABILITY_RELIABLE)out.reliability="reliable";
  else if(reliability==DDS_RELIABILITY_BEST_EFFORT)out.reliability="best_effort";
 }
 dds_durability_kind_t durability;
 if(dds_qget_durability(q,&durability))switch(durability) {
  case DDS_DURABILITY_VOLATILE:out.durability="volatile";break;
  case DDS_DURABILITY_TRANSIENT_LOCAL:out.durability="transient_local";break;
  case DDS_DURABILITY_TRANSIENT:out.durability="transient";break;
  case DDS_DURABILITY_PERSISTENT:out.durability="persistent";break;
 }
 dds_history_kind_t history;int32_t depth;
 if(dds_qget_history(q,&history,&depth)) {
  if(history==DDS_HISTORY_KEEP_LAST)out.history="keep_last";
  else if(history==DDS_HISTORY_KEEP_ALL)out.history="keep_all";
  out.depth=depth;
 }
 dds_duration_t duration;
 if(dds_qget_deadline(q,&duration))out.deadline_ns=duration==DDS_INFINITY?-1:duration;
 dds_liveliness_kind_t liveliness;
 if(dds_qget_liveliness(q,&liveliness,&duration)) {
  switch(liveliness) {
   case DDS_LIVELINESS_AUTOMATIC:out.liveliness="automatic";break;
   case DDS_LIVELINESS_MANUAL_BY_PARTICIPANT:out.liveliness="manual_by_participant";break;
   case DDS_LIVELINESS_MANUAL_BY_TOPIC:out.liveliness="manual_by_topic";break;
  }
  out.lease_duration_ns=duration==DDS_INFINITY?-1:duration;
 }
 dds_ownership_kind_t ownership;
 if(dds_qget_ownership(q,&ownership)) {
  if(ownership==DDS_OWNERSHIP_SHARED)out.ownership="shared";
  else if(ownership==DDS_OWNERSHIP_EXCLUSIVE)out.ownership="exclusive";
 }
 dds_destination_order_kind_t order;
 if(dds_qget_destination_order(q,&order)) {
  if(order==DDS_DESTINATIONORDER_BY_RECEPTION_TIMESTAMP)out.destination_order="by_reception_timestamp";
  else if(order==DDS_DESTINATIONORDER_BY_SOURCE_TIMESTAMP)out.destination_order="by_source_timestamp";
 }
 return out;
}
static const dds_topic_descriptor_t* descriptor(const std::string& type) {
 if(type=="std_msgs::msg::dds_::String_") return &std_msgs_msg_dds__String__desc;
 if(type=="std_msgs::msg::dds_::Float64_") return &std_msgs_msg_dds__Float64__desc;
 if(type=="std_msgs::msg::dds_::Float32_") return &std_msgs_msg_dds__Float32__desc;
 if(type=="std_msgs::msg::dds_::Int32_") return &std_msgs_msg_dds__Int32__desc;
 if(type=="std_msgs::msg::dds_::Int64_") return &std_msgs_msg_dds__Int64__desc;
 if(type=="std_msgs::msg::dds_::Bool_") return &std_msgs_msg_dds__Bool__desc;
 if(type=="geometry_msgs::msg::dds_::Vector3_")return &geometry_msgs_msg_dds__Vector3__desc;
 if(type=="geometry_msgs::msg::dds_::Quaternion_")return &geometry_msgs_msg_dds__Quaternion__desc;
 if(type=="geometry_msgs::msg::dds_::Twist_")return &geometry_msgs_msg_dds__Twist__desc;
 if(type=="geometry_msgs::msg::dds_::Pose_")return &geometry_msgs_msg_dds__Pose__desc;
 if(type=="nav_msgs::msg::dds_::Odometry_")return &nav_msgs_msg_dds__Odometry__desc;
 return nullptr;
}
// Project the typed decoder's path/order/exact JSON tokens, not last/display text.
// Field currently stores nonfinite metadata in its rendered JSON only. Classify
// raw typed numbers here rather than interpreting its localized display strings.
static void plot_states(std::vector<const char*>& states,double value) {
 states.push_back(std::isfinite(value)?"finite":(std::isnan(value)?"nan":(value>0?"positive_infinity":"negative_infinity")));
}
static void plot_states(std::vector<const char*>& s,const geometry_msgs_msg_dds__Vector3_& v) {
 plot_states(s,v.x);plot_states(s,v.y);plot_states(s,v.z);
}
static void plot_states(std::vector<const char*>& s,const geometry_msgs_msg_dds__Quaternion_& v) {
 plot_states(s,v.x);plot_states(s,v.y);plot_states(s,v.z);plot_states(s,v.w);
}
static void plot_states(std::vector<const char*>& s,const geometry_msgs_msg_dds__Pose_& v) {
 plot_states(s,v.position.x);plot_states(s,v.position.y);plot_states(s,v.position.z);plot_states(s,v.orientation);
}
static void plot_states(std::vector<const char*>& s,const geometry_msgs_msg_dds__Twist_& v) {
 plot_states(s,v.linear);plot_states(s,v.angular);
}
static void plot_states(std::vector<const char*>& s,const nav_msgs_msg_dds__Odometry_& v) {
 plot_states(s,v.header.stamp.sec);plot_states(s,v.header.stamp.nanosec);
 plot_states(s,v.pose.pose);for(double n:v.pose.covariance)plot_states(s,n);
 plot_states(s,v.twist.twist);for(double n:v.twist.covariance)plot_states(s,n);
}
static void plot_leaves(const Field& f,const std::vector<const char*>& states,size_t& index,std::string& out) {
 if(f.kind=="number") {
  if(index)out+=',';
  const auto* state=states.at(index++);
  out+="{\"path\":"+json_quote(f.path)+",\"unit\":null,\"state\":"+json_quote(state)+",\"value\":"+f.json_value+"}";
 }
 for(const auto& child:f.children)plot_leaves(child,states,index,out);
}
static std::string plot_fields(const std::string& type,const void* sample,const Field& tree) {
 std::vector<const char*> states;
 if(type=="geometry_msgs::msg::dds_::Vector3_")plot_states(states,*static_cast<const geometry_msgs_msg_dds__Vector3_*>(sample));
 else if(type=="geometry_msgs::msg::dds_::Quaternion_")plot_states(states,*static_cast<const geometry_msgs_msg_dds__Quaternion_*>(sample));
 else if(type=="geometry_msgs::msg::dds_::Twist_")plot_states(states,*static_cast<const geometry_msgs_msg_dds__Twist_*>(sample));
 else if(type=="geometry_msgs::msg::dds_::Pose_")plot_states(states,*static_cast<const geometry_msgs_msg_dds__Pose_*>(sample));
 else if(type=="nav_msgs::msg::dds_::Odometry_")plot_states(states,*static_cast<const nav_msgs_msg_dds__Odometry_*>(sample));
 else if(type=="std_msgs::msg::dds_::Float32_")plot_states(states,static_cast<const std_msgs_msg_dds__Float32_*>(sample)->data);
 else if(type=="std_msgs::msg::dds_::Float64_")plot_states(states,static_cast<const std_msgs_msg_dds__Float64_*>(sample)->data);
 else if(type=="std_msgs::msg::dds_::Int32_" || type=="std_msgs::msg::dds_::Int64_")states.push_back("finite");
 size_t index=0;std::string out="[";plot_leaves(tree,states,index,out);
 if(index!=states.size())throw std::logic_error("Typed plot/schema leaf count mismatch");
 return out+"]";
}
struct Watch {
 dds_entity_t reader=0,topic=0;
 uint64_t generation=0;
 bool requested_reliable=false,requested_transient=false;
 std::string numeric_fields="[]"; int64_t received_ns=0,source_ns=0;
 std::string message="null",value_error,selected,type,last,last_error,mode="best_effort / volatile";
 uint64_t count=0; Clock::time_point last_sample{},rate_time{}; uint64_t rate_count=0; double hz=0;
 void stop_reader() {
  if(reader>0)dds_delete(reader);
  reader=0;
  if(topic>0)dds_delete(topic);
  topic=0;
  selected.clear();type.clear();last.clear();message="null";numeric_fields="[]";received_ns=0;source_ns=0;last_error.clear();value_error.clear();count=0;hz=0;rate_count=0;
 }
 void values();
 std::string plot_json() const {
  return ",\"receivedMonotonicNs\":"+(count?std::to_string(received_ns):"null")+
         ",\"sourceTimestampNs\":"+(count?std::to_string(source_ns):"null")+
         ",\"numericFields\":"+numeric_fields;
 }
 std::string json(int slot) const {
  const double age=count?std::chrono::duration<double>(Clock::now()-last_sample).count():-1;
  dds_subscription_matched_status_t matched{};if(reader>0)dds_get_subscription_matched_status(reader,&matched);
  const auto failure=last_error.empty()?value_error:last_error;
  const auto state=subscription_state(reader>0,count,matched.current_count,age,failure);
  std::ostringstream o;
  o<<"{\"slot\":"<<slot<<",\"generation\":"<<generation<<",\"subscription\":"<<json_quote(selected)
   <<",\"type\":"<<json_quote(type)<<",\"readerActive\":"<<(reader>0?"true":"false")
   <<",\"subscriptionState\":"<<json_quote(state)<<",\"fresh\":"<<(state=="receiving"?"true":"false")
   <<",\"last\":"<<json_quote(last)<<",\"message\":"<<message<<",\"count\":"<<count<<",\"hz\":"<<hz<<",\"age\":"<<age
   <<",\"matched\":"<<matched.current_count<<",\"qos\":"<<json_quote(mode)<<",\"error\":"<<json_quote(failure)<<plot_json()<<"}";
  return o.str();
 }
};
struct Session::Impl : Watch {
 dds_entity_t domain=0,participant=0,pubs=0,subs=0,participants=0,graph=0,rosout_reader=0,rosout_topic=0;
 std::array<Watch,3> extra;
 Watch& slot(int n){return n==0?static_cast<Watch&>(*this):extra[n-1];}
 int domain_id=0; Registry registry; dds_guid_t own{};
 std::set<std::string> peer_ids;
 struct GraphSource { std::string writer, participant; };
 // ros_discovery_info is unkeyed: instance_handle is shared by all writers.
 std::map<dds_instance_handle_t,GraphSource> graph_sources;
 std::map<std::string,std::string> graph_owners;
 void drop_graph_writer(const std::string& writer) {
  for(auto it=graph_sources.begin();it!=graph_sources.end();) {
   if(it->second.writer!=writer){++it;continue;}
   auto owner=graph_owners.find(it->second.participant);
   if(owner!=graph_owners.end() && owner->second==writer) {
    registry.erase_graph(owner->first);graph_owners.erase(owner);
   }
   it=graph_sources.erase(it);
  }
 }
 bool seen_peer=false;
 std::string discovery_error;
 RosoutBuffer rosout; std::string rosout_error;
 void take_rosout(bool discard=false) {
  if(rosout_reader<=0)return;
  for(int batch=0;batch<4;batch++) {
   void* samples[32]{};dds_sample_info_t infos[32]{};
   int n=dds_take(rosout_reader,samples,infos,32,32);
   if(n<0){rosout_error="DDS rosout read: "+std::string(dds_strretcode(n));break;}
   rosout_error.clear();
   for(int i=0;i<n;i++) {
    if(infos[i].valid_data&&!discard) {
     auto* v=static_cast<rcl_interfaces_msg_dds__Log_*>(samples[i]);
     if(v){RosoutEntry e;e.level=v->level;e.logger=RosoutBuffer::bounded_field(v->name,RosoutBuffer::max_logger_bytes);e.message=RosoutBuffer::bounded_field(v->msg,RosoutBuffer::max_message_bytes);e.file=RosoutBuffer::bounded_field(v->file,RosoutBuffer::max_source_bytes);e.function=RosoutBuffer::bounded_field(v->function,RosoutBuffer::max_source_bytes);e.line=v->line;e.source_sec=v->stamp.sec;e.source_nanosec=v->stamp.nanosec;e.intake_ms=std::chrono::duration_cast<std::chrono::milliseconds>(Clock::now().time_since_epoch()).count();rosout.push(std::move(e));}
    }
   }
   if(n>0)dds_return_loan(rosout_reader,samples,n);
   if(n<32)break;
  }
 }
 void stop_rosout() {if(rosout_reader>0)dds_delete(rosout_reader);rosout_reader=0;if(rosout_topic>0)dds_delete(rosout_topic);rosout_topic=0;rosout.clear();rosout_error.clear();}
 void endpoints(dds_entity_t source,bool writer) {
  void* samples[64]{}; dds_sample_info_t infos[64]{};
  int n=dds_take(source,samples,infos,64,64);
  if(n<0){discovery_error="DDS endpoint read: "+std::string(dds_strretcode(n));return;}
  for(int i=0;i<n;i++) {
   auto* e=static_cast<dds_builtintopic_endpoint_t*>(samples[i]);
   if(!e)continue;
   if(!infos[i].valid_data || infos[i].instance_state!=DDS_ALIVE_INSTANCE_STATE) {
    const auto id=key(e->key);registry.erase(id);if(writer)drop_graph_writer(id);
   }
   else if(key(e->participant_key)!=key(own)) registry.put(key(e->key),e->topic_name?e->topic_name:"",e->type_name?e->type_name:"",writer,key(e->participant_key),endpoint_qos(e->qos));
  }
  if(n>0)dds_return_loan(source,samples,n);
 }
 void graph_samples() {
  void* samples[64]{}; dds_sample_info_t infos[64]{};
  int n=dds_take(graph,samples,infos,64,64);
  if(n<0){discovery_error="ROS graph read: "+std::string(dds_strretcode(n));return;}
  for(int i=0;i<n;i++) {
   if(!infos[i].valid_data || infos[i].instance_state!=DDS_ALIVE_INSTANCE_STATE) {
    auto it=graph_sources.find(infos[i].publication_handle);
    if(it!=graph_sources.end()){const auto writer=it->second.writer;drop_graph_writer(writer);}continue;
   }
   auto* p=static_cast<rmw_dds_common_msg_dds__ParticipantEntitiesInfo_*>(samples[i]);
   if(!p)continue;
   auto* source=dds_get_matched_publication_data(graph,infos[i].publication_handle);
   if(!source)continue; // A sample from an already-dead writer must not resurrect a graph.
   const auto id=key(p->gid.data),participant_id=key(source->participant_key),writer_id=key(source->key);
   dds_builtintopic_free_endpoint(source);
   if(id!=participant_id || id==key(own))continue;
   if((graph_sources.size()>=4096 && !graph_sources.count(infos[i].publication_handle)) ||
      (graph_owners.size()>=256 && !graph_owners.count(id)))continue;
   std::vector<GraphNode> graph_nodes;
   auto& seq=p->node_entities_info_seq;size_t references=0;
   for(uint32_t j=0;seq._buffer && j<std::min(seq._length,128u);j++) {
    const auto& entry=seq._buffer[j];
    std::string ns=entry.node_namespace,name=entry.node_name;
    if(name.empty())continue;
    if(ns.empty()||ns.front()!='/')ns='/'+ns;
    if(ns.back()!='/')ns+='/';
    GraphNode node;node.name=ns+name;
    for(bool writer:{false,true}) {
     const auto& gids=writer?entry.writer_gid_seq:entry.reader_gid_seq;
     for(uint32_t k=0;gids._buffer && k<gids._length && references<1024;k++,references++)
      (writer?node.writers:node.readers).push_back(key(gids._buffer[k].data));
    }
    graph_nodes.push_back(std::move(node));
   }
   registry.set_graph(id,graph_nodes);
   graph_sources[infos[i].publication_handle]={writer_id,id};graph_owners[id]=writer_id;
  }
  if(n>0)dds_return_loan(graph,samples,n);
 }
 void participant_samples() {
  void* samples[64]{};dds_sample_info_t infos[64]{};
  int n=dds_take(participants,samples,infos,64,64);
  if(n<0){discovery_error="DDS participant read: "+std::string(dds_strretcode(n));return;}
  for(int i=0;i<n;i++) {
   auto* p=static_cast<dds_builtintopic_participant_t*>(samples[i]);
   if(p && (!infos[i].valid_data || infos[i].instance_state!=DDS_ALIVE_INSTANCE_STATE)) {
    const auto id=key(p->key);registry.erase_participant(id);peer_ids.erase(id);graph_owners.erase(id);
    for(auto it=graph_sources.begin();it!=graph_sources.end();)
     if(it->second.participant==id)it=graph_sources.erase(it);else ++it;
   }
   else if(p && key(p->key)!=key(own) && peer_ids.size()<4096)peer_ids.insert(key(p->key));
  }
  if(n>0)dds_return_loan(participants,samples,n);
  if(!peer_ids.empty())seen_peer=true;
 }
};
 void Watch::values() {
  if(reader<=0)return;
  for(int batch=0;batch<1;batch++) {
   void* samples[64]{};dds_sample_info_t infos[64]{};
   int n=dds_take(reader,samples,infos,64,64);
   if(n>=0)value_error.clear();
   if(n<0){value_error="DDS message read: "+std::string(dds_strretcode(n));break;}
   for(int i=0;i<n;i++) {
    if(!infos[i].valid_data)continue;
    void* p=samples[i]; std::ostringstream out;out<<std::setprecision(10);
    if(type=="std_msgs::msg::dds_::String_") {
     auto* v=static_cast<std_msgs_msg_dds__String_*>(p);last=bounded_utf8(v->data,8192);
    } else {
     if(type=="std_msgs::msg::dds_::Float64_")out<<static_cast<std_msgs_msg_dds__Float64_*>(p)->data;
     else if(type=="std_msgs::msg::dds_::Float32_")out<<static_cast<std_msgs_msg_dds__Float32_*>(p)->data;
     else if(type=="std_msgs::msg::dds_::Int32_")out<<static_cast<std_msgs_msg_dds__Int32_*>(p)->data;
     else if(type=="std_msgs::msg::dds_::Int64_")out<<static_cast<std_msgs_msg_dds__Int64_*>(p)->data;
     else if(type=="std_msgs::msg::dds_::Bool_")out<<(static_cast<std_msgs_msg_dds__Bool_*>(p)->data?"true":"false");
     last=out.str();
    }
    auto tree=decode_fields(type,p);message=message_json(type,tree);numeric_fields=plot_fields(type,p,tree);
    if(type.rfind("std_msgs::",0)!=0)last=field_summary(tree);
    ++count;last_sample=Clock::now();
     received_ns=std::chrono::duration_cast<std::chrono::nanoseconds>(last_sample.time_since_epoch()).count();
     source_ns=infos[i].source_timestamp;
   }
   if(n>0)dds_return_loan(reader,samples,n);
   if(n<64)break;
  }
  auto now=Clock::now();double dt=std::chrono::duration<double>(now-rate_time).count();
  if(dt>=1){hz=(count-rate_count)/dt;rate_count=count;rate_time=now;}
 }
Session::Session():impl(new Impl){}
Session::~Session(){stop();}
void Session::stop() {
 ++session_generation;++reader_generation;
 auto& s=*impl;for(int i=0;i<4;i++)s.slot(i).stop_reader();
 s.stop_rosout();
 if(s.participant>0)dds_delete(s.participant);
 if(s.domain>0)dds_delete(s.domain);
 impl.reset(new Impl);
}
std::string Session::start(int domain,const std::string& address,const std::string& peer) {
 stop();
 auto failed=[this](const std::string& message){stop();impl->last_error=message;return error(message);};
 if(!valid_domain(domain))return failed("Domain ID deve ser inteiro de 0 a 101 nesta versao.");
 if(!ipv4(address))return failed("Nenhum IPv4 Wi-Fi/LAN valido. Conecte a mesma rede do ROS 2.");
 if(!peer.empty()&&!ipv4(peer))return failed("Peer opcional deve ser um IPv4, sem porta.");
 auto& s=*impl;s.domain_id=domain;
 std::string config="<CycloneDDS><Domain Id=\"any\"><General><Interfaces><NetworkInterface address=\""+address+"\" multicast=\"true\"/></Interfaces><AllowMulticast>spdp</AllowMulticast><DontRoute>true</DontRoute></General><Discovery><ParticipantIndex>auto</ParticipantIndex><MaxAutoParticipantIndex>30</MaxAutoParticipantIndex><LeaseDuration>5s</LeaseDuration><SPDPInterval>2s</SPDPInterval>";
 if(!peer.empty())config+="<Peers><Peer Address=\""+peer+"\"/></Peers>";
 // Cyclone 0.10.5 checks this serialized-size ceiling before RTPS fragment
 // reassembly/deserialization; retained UTF-8/tree limits alone are too late.
 config+="</Discovery><Internal><MaxSampleSize>64 KiB</MaxSampleSize></Internal></Domain></CycloneDDS>";
 s.domain=dds_create_domain(domain,config.c_str());
 if(s.domain<0){auto msg=std::string("Falha ao iniciar dominio DDS: ")+dds_strretcode(s.domain);return failed(msg);}
 s.participant=dds_create_participant(domain,nullptr,nullptr);
 if(s.participant<0){auto msg=std::string("Falha no participante DDS: ")+dds_strretcode(s.participant);return failed(msg);}
 dds_get_guid(s.participant,&s.own);
 s.pubs=dds_create_reader(s.participant,DDS_BUILTIN_TOPIC_DCPSPUBLICATION,nullptr,nullptr);
 s.subs=dds_create_reader(s.participant,DDS_BUILTIN_TOPIC_DCPSSUBSCRIPTION,nullptr,nullptr);
 s.participants=dds_create_reader(s.participant,DDS_BUILTIN_TOPIC_DCPSPARTICIPANT,nullptr,nullptr);
 auto graph_topic=dds_create_topic(s.participant,&rmw_dds_common_msg_dds__ParticipantEntitiesInfo__desc,"ros_discovery_info",nullptr,nullptr);
 auto* q=dds_create_qos();dds_qset_reliability(q,DDS_RELIABILITY_RELIABLE,DDS_MSECS(100));dds_qset_durability(q,DDS_DURABILITY_TRANSIENT_LOCAL);dds_qset_history(q,DDS_HISTORY_KEEP_LAST,32);
 s.graph=dds_create_reader(s.participant,graph_topic,q,nullptr);dds_delete_qos(q);
 if(s.pubs<0||s.subs<0||s.participants<0||graph_topic<0||s.graph<0)return failed("Falha ao criar leitores de descoberta DDS.");
 s.rosout_topic=dds_create_topic(s.participant,&rcl_interfaces_msg_dds__Log__desc,"rt/rosout",nullptr,nullptr);
 if(s.rosout_topic<0)s.rosout_error="/rosout anunciado com tipo DDS incompatível: "+std::string(dds_strretcode(s.rosout_topic));
 else {
  auto* log_q=dds_create_qos();dds_qset_reliability(log_q,DDS_RELIABILITY_BEST_EFFORT,0);dds_qset_durability(log_q,DDS_DURABILITY_VOLATILE);dds_qset_history(log_q,DDS_HISTORY_KEEP_LAST,128);dds_qset_resource_limits(log_q,128,4,128);
  s.rosout_reader=dds_create_reader(s.participant,s.rosout_topic,log_q,nullptr);dds_delete_qos(log_q);
  if(s.rosout_reader<0){s.rosout_error="Falha ao criar leitor /rosout: "+std::string(dds_strretcode(s.rosout_reader));s.rosout_reader=0;}
 }
 return "{\"ok\":true,\"domain\":"+std::to_string(domain)+"}";
}
void Session::clear_rosout(){auto& s=*impl;s.take_rosout(true);s.rosout.clear();}
std::string Session::subscribe(const std::string& topic,const std::string& type,bool reliable,bool transient) {
 return watch(0,topic,type,reliable,transient);
}
std::string Session::remove_watch(int slot) {
 if(slot<0||slot>=4)return error("Watch slot deve ser de 0 a 3.");
 auto& w=impl->slot(slot);++w.generation;w.stop_reader();
 if(slot==0)++reader_generation;
 return "{\"ok\":true}";
}
std::string Session::watch(int slot,const std::string& topic,const std::string& type,bool reliable,bool transient) {
 if(slot<0||slot>=4)return error("Limite de quatro watches (slots 0 a 3).");
 auto& s=impl->slot(slot);
 auto rejected=[&s](const std::string& message){s.last_error=message;return error(message);};
 if(impl->participant<=0)return rejected("Inicie um dominio primeiro.");
 const auto* desc=descriptor(type);
 if(!desc)return rejected("Tipo descoberto, mas sem decodificador nesta v0.1: "+ros_type(type));
 if(topic.rfind("rt/",0)!=0||topic.size()>1024)return rejected("Topico ROS invalido.");
 ++s.generation;
 if(slot==0)++reader_generation;
 s.stop_reader();s.selected=topic.substr(2);s.type=type;
 s.requested_reliable=reliable;s.requested_transient=transient;
 s.mode=std::string(reliable?"reliable":"best_effort")+" / "+(transient?"transient_local":"volatile");
 s.topic=dds_create_topic(impl->participant,desc,topic.c_str(),nullptr,nullptr);
 if(s.topic<0){auto msg="Falha ao criar topico: "+std::string(dds_strretcode(s.topic));s.stop_reader();s.last_error=msg;return error(msg);}
 auto* q=dds_create_qos();dds_qset_reliability(q,reliable?DDS_RELIABILITY_RELIABLE:DDS_RELIABILITY_BEST_EFFORT,DDS_MSECS(100));
 dds_qset_durability(q,transient?DDS_DURABILITY_TRANSIENT_LOCAL:DDS_DURABILITY_VOLATILE);dds_qset_history(q,DDS_HISTORY_KEEP_LAST,64);
 dds_qset_resource_limits(q,64,1,64);
 s.reader=dds_create_reader(impl->participant,s.topic,q,nullptr);dds_delete_qos(q);
 if(s.reader<0){auto msg="Falha ao assinar: "+std::string(dds_strretcode(s.reader));s.stop_reader();s.last_error=msg;return error(msg);}
 s.rate_time=Clock::now();
 return "{\"ok\":true}";
}
std::string Session::poll() {
 auto& s=*impl;
 const bool running=s.participant>0;
 if(running){s.discovery_error.clear();s.endpoints(s.pubs,true);s.endpoints(s.subs,false);s.graph_samples();s.participant_samples();for(int i=0;i<4;i++)s.slot(i).values();s.take_rosout();}
 std::ostringstream out;out<<"{\"running\":"<<(running?"true":"false")<<",\"domain\":"<<s.domain_id<<",\"topics\":[";bool first=true;
 for(const auto& row:s.registry.topics()) {
  if(!first)out<<',';
  first=false;
  out<<"{\"name\":"<<json_quote(row.name)<<",\"type\":"<<json_quote(row.type)<<",\"wireName\":"<<json_quote(row.wire_name)<<",\"wireType\":"<<json_quote(row.wire_type)<<",\"publishers\":"<<row.publishers<<",\"subscribers\":"<<row.subscribers<<",\"supported\":"<<(descriptor(row.wire_type)?"true":"false")<<",\"endpoints\":[";
  bool first_endpoint=true;
  for(const auto& endpoint:row.endpoints){if(!first_endpoint)out<<',';first_endpoint=false;out<<endpoint_json(endpoint,s.requested_reliable,s.requested_transient);}
  out<<"]}";
 }
 out<<"],\"nodes\":[";first=true;const auto names=s.registry.nodes();
 for(const auto& name:names){if(!first)out<<',';
  first=false;out<<json_quote(name);}
 double age=s.count?std::chrono::duration<double>(Clock::now()-s.last_sample).count():-1;
 dds_subscription_matched_status_t matched{};if(s.reader>0)dds_get_subscription_matched_status(s.reader,&matched);
 const auto failure=!s.last_error.empty()?s.last_error:(!s.discovery_error.empty()?s.discovery_error:s.value_error);
 const auto state=subscription_state(s.reader>0,s.count,matched.current_count,age,failure);
 out<<"],\"sessionGeneration\":"<<session_generation<<",\"readerGeneration\":"<<reader_generation
    <<",\"connectionState\":"<<json_quote(connection_state(running,s.peer_ids.size(),s.seen_peer,running?s.discovery_error:failure))
    <<",\"subscriptionState\":"<<json_quote(state)<<",\"readerActive\":"<<(s.reader>0?"true":"false")
    <<",\"fresh\":"<<(state=="receiving"?"true":"false")<<",\"rosNodes\":"<<names.size();
  out<<s.plot_json();
 out<<",\"watches\":[";for(int i=0;i<4;i++){if(i)out<<',';out<<s.slot(i).json(i);}out<<']';
 out<<",\"ddsPeers\":"<<s.peer_ids.size()<<",\"subscription\":"<<json_quote(s.selected)<<",\"last\":"<<json_quote(s.last)<<",\"message\":"<<s.message<<",\"count\":"<<s.count<<",\"hz\":"<<s.hz<<",\"age\":"<<age<<",\"matched\":"<<matched.current_count<<",\"qos\":"<<json_quote(s.mode)<<",\"error\":"<<json_quote(failure);
 std::string rosout_status=s.rosout_error;bool rosout_expected_writer=false;std::string rosout_other_type;
 for(const auto& row:s.registry.topics())if(row.name=="/rosout")for(const auto& ep:row.endpoints)if(ep.writer){if(row.wire_type=="rcl_interfaces::msg::dds_::Log_")rosout_expected_writer=true;else rosout_other_type=row.type;}
 if(!rosout_expected_writer&&!rosout_other_type.empty())rosout_status="Tipo /rosout incompatível anunciado: "+rosout_other_type;
 out<<",\"rosout\":{\"active\":"<<(s.rosout_reader>0&&rosout_status.empty()?"true":"false")<<",\"error\":"<<json_quote(rosout_status)<<",\"count\":"<<s.rosout.size()<<",\"bytes\":"<<s.rosout.bytes()<<",\"evicted\":"<<s.rosout.evicted()<<",\"offeredReliability\":[";
 bool first_offer=true;for(const auto& row:s.registry.topics())if(row.name=="/rosout"&&row.wire_type=="rcl_interfaces::msg::dds_::Log_")for(const auto& ep:row.endpoints)if(ep.writer){if(!first_offer)out<<',';first_offer=false;out<<json_quote(ep.qos.reliability);}
 out<<"],\"offeredDurability\":[";first_offer=true;for(const auto& row:s.registry.topics())if(row.name=="/rosout"&&row.wire_type=="rcl_interfaces::msg::dds_::Log_")for(const auto& ep:row.endpoints)if(ep.writer){if(!first_offer)out<<',';first_offer=false;out<<json_quote(ep.qos.durability);}
 out<<"],\"entries\":[";bool first_log=true;const auto intake_now=std::chrono::duration_cast<std::chrono::milliseconds>(Clock::now().time_since_epoch()).count();
 for(const auto& log:s.rosout.entries_view()){if(!first_log)out<<',';first_log=false;out<<"{\"level\":"<<log.level<<",\"logger\":"<<json_quote(log.logger)<<",\"message\":"<<json_quote(log.message)<<",\"file\":"<<json_quote(log.file)<<",\"function\":"<<json_quote(log.function)<<",\"line\":"<<log.line<<",\"sourceSec\":"<<log.source_sec<<",\"sourceNanosec\":"<<log.source_nanosec<<",\"intakeAgeMs\":"<<(intake_now-log.intake_ms)<<",\"truncated\":"<<(log.truncated?"true":"false")<<"}";}
 out<<"]}}";
 return out.str();
}
}

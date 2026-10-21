#include "inspector.hpp"
#include "model.hpp"
#include "PocketTypes.h"
#include <dds/dds.h>
#include <arpa/inet.h>
#include <chrono>
#include <cmath>
#include <cstring>
#include <algorithm>
#include <set>

namespace pocket {
using Clock=std::chrono::steady_clock;
static std::string error(const std::string& message) {return "{\"ok\":false,\"error\":"+json_quote(message)+"}";}
static bool ipv4(const std::string& s) {in_addr a{};return inet_pton(AF_INET,s.c_str(),&a)==1;}
static std::string key(const uint8_t* data) {
 const char* hex="0123456789abcdef"; std::string s; s.reserve(32);
 for(int i=0;i<16;i++){s+=hex[data[i]>>4];s+=hex[data[i]&15];} return s;
}
static std::string key(const dds_guid_t& guid) {return key(guid.v);}
static const dds_topic_descriptor_t* descriptor(const std::string& type) {
 if(type=="std_msgs::msg::dds_::String_") return &std_msgs_msg_dds__String__desc;
 if(type=="std_msgs::msg::dds_::Float64_") return &std_msgs_msg_dds__Float64__desc;
 if(type=="std_msgs::msg::dds_::Float32_") return &std_msgs_msg_dds__Float32__desc;
 if(type=="std_msgs::msg::dds_::Int32_") return &std_msgs_msg_dds__Int32__desc;
 if(type=="std_msgs::msg::dds_::Int64_") return &std_msgs_msg_dds__Int64__desc;
 if(type=="std_msgs::msg::dds_::Bool_") return &std_msgs_msg_dds__Bool__desc;
 return nullptr;
}
struct Session::Impl {
 dds_entity_t domain=0,participant=0,pubs=0,subs=0,participants=0,graph=0,reader=0,topic=0;
 int domain_id=0; Registry registry; dds_guid_t own{};
 std::map<std::string,std::vector<std::string>> nodes;
 std::set<std::string> peer_ids;
 std::map<dds_instance_handle_t,std::string> graph_handles;
 bool seen_peer=false;
 std::string discovery_error,value_error;
 std::string selected,type,last,last_error,mode="best_effort / volatile";
 uint64_t count=0; Clock::time_point last_sample{},rate_time{}; uint64_t rate_count=0; double hz=0;
 void stop_reader() {
  if(reader>0) dds_delete(reader);
  reader=0;
  if(topic>0) dds_delete(topic);
  topic=0;
  selected.clear(); type.clear(); last.clear(); last_error.clear(); value_error.clear(); count=0; hz=0;rate_count=0;
 }
 void endpoints(dds_entity_t source,bool writer) {
  void* samples[64]{}; dds_sample_info_t infos[64]{};
  int n=dds_take(source,samples,infos,64,64);
  if(n<0){discovery_error="DDS endpoint read: "+std::string(dds_strretcode(n));return;}
  for(int i=0;i<n;i++) {
   auto* e=static_cast<dds_builtintopic_endpoint_t*>(samples[i]);
   if(!e)continue;
   if(!infos[i].valid_data || infos[i].instance_state!=DDS_ALIVE_INSTANCE_STATE) registry.erase(key(e->key));
   else if(key(e->participant_key)!=key(own)) registry.put(key(e->key),e->topic_name?e->topic_name:"",e->type_name?e->type_name:"",writer);
  }
  if(n>0)dds_return_loan(source,samples,n);
 }
 void graph_samples() {
  void* samples[64]{}; dds_sample_info_t infos[64]{};
  int n=dds_take(graph,samples,infos,64,64);
  if(n<0){discovery_error="ROS graph read: "+std::string(dds_strretcode(n));return;}
  for(int i=0;i<n;i++) {
   if(!infos[i].valid_data || infos[i].instance_state!=DDS_ALIVE_INSTANCE_STATE) {
    auto it=graph_handles.find(infos[i].instance_handle);
    if(it!=graph_handles.end()){nodes.erase(it->second);graph_handles.erase(it);} continue;
   }
   auto* p=static_cast<rmw_dds_common_msg_dds__ParticipantEntitiesInfo_*>(samples[i]);
   auto id=key(p->gid.data);std::vector<std::string> names;
   auto& seq=p->node_entities_info_seq;
   for(uint32_t j=0;j<std::min(seq._length,4096u);j++) {
    const auto& entry=seq._buffer[j];
    std::string ns=entry.node_namespace,name=entry.node_name;
    if(name.empty())continue;
    if(ns.empty()||ns.back()!='/')ns+='/';
    names.push_back(ns+name);
   }
   if(nodes.size()<4096 || nodes.count(id))nodes[id]=names;
   graph_handles[infos[i].instance_handle]=id;
  }
  if(n>0)dds_return_loan(graph,samples,n);
 }
 void participant_samples() {
  void* samples[64]{};dds_sample_info_t infos[64]{};
  int n=dds_take(participants,samples,infos,64,64);
  if(n<0){discovery_error="DDS participant read: "+std::string(dds_strretcode(n));return;}
  for(int i=0;i<n;i++) {
   auto* p=static_cast<dds_builtintopic_participant_t*>(samples[i]);
   if(p && (!infos[i].valid_data || infos[i].instance_state!=DDS_ALIVE_INSTANCE_STATE)){nodes.erase(key(p->key));peer_ids.erase(key(p->key));}
   else if(p && key(p->key)!=key(own))peer_ids.insert(key(p->key));
  }
  if(n>0)dds_return_loan(participants,samples,n);
  if(!peer_ids.empty())seen_peer=true;
 }
 void values() {
  if(reader<=0)return;
  for(int batch=0;batch<8;batch++) {
   void* samples[64]{};dds_sample_info_t infos[64]{};
   int n=dds_take(reader,samples,infos,64,64);
   if(n>=0)value_error.clear();
   if(n<0){value_error="DDS message read: "+std::string(dds_strretcode(n));break;}
   for(int i=0;i<n;i++) {
    if(!infos[i].valid_data)continue;
    void* p=samples[i]; std::ostringstream out;out<<std::setprecision(10);
    if(type=="std_msgs::msg::dds_::String_") {
     auto* v=static_cast<std_msgs_msg_dds__String_*>(p);last=v->data?std::string(v->data,std::min<size_t>(std::strlen(v->data),8192)):"";
    } else {
     if(type=="std_msgs::msg::dds_::Float64_")out<<static_cast<std_msgs_msg_dds__Float64_*>(p)->data;
     else if(type=="std_msgs::msg::dds_::Float32_")out<<static_cast<std_msgs_msg_dds__Float32_*>(p)->data;
     else if(type=="std_msgs::msg::dds_::Int32_")out<<static_cast<std_msgs_msg_dds__Int32_*>(p)->data;
     else if(type=="std_msgs::msg::dds_::Int64_")out<<static_cast<std_msgs_msg_dds__Int64_*>(p)->data;
     else if(type=="std_msgs::msg::dds_::Bool_")out<<(static_cast<std_msgs_msg_dds__Bool_*>(p)->data?"true":"false");
     last=out.str();
    }
    ++count;last_sample=Clock::now();
   }
   if(n>0)dds_return_loan(reader,samples,n);
   if(n<64)break;
  }
  auto now=Clock::now();double dt=std::chrono::duration<double>(now-rate_time).count();
  if(dt>=1){hz=(count-rate_count)/dt;rate_count=count;rate_time=now;}
 }
};
Session::Session():impl(new Impl){}
Session::~Session(){stop();}
void Session::stop() {
 ++session_generation;++reader_generation;
 auto& s=*impl;s.stop_reader();
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
 config+="</Discovery></Domain></CycloneDDS>";
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
 return "{\"ok\":true,\"domain\":"+std::to_string(domain)+"}";
}
std::string Session::subscribe(const std::string& topic,const std::string& type,bool reliable,bool transient) {
 auto& s=*impl;
 auto rejected=[&s](const std::string& message){s.last_error=message;return error(message);};
 if(s.participant<=0)return rejected("Inicie um dominio primeiro.");
 const auto* desc=descriptor(type);
 if(!desc)return rejected("Tipo descoberto, mas sem decodificador nesta v0.1: "+ros_type(type));
 if(topic.rfind("rt/",0)!=0||topic.size()>1024)return rejected("Topico ROS invalido.");
 ++reader_generation;
 s.stop_reader();s.selected=topic.substr(2);s.type=type;
 s.topic=dds_create_topic(s.participant,desc,topic.c_str(),nullptr,nullptr);
 if(s.topic<0){auto msg="Falha ao criar topico: "+std::string(dds_strretcode(s.topic));s.stop_reader();s.last_error=msg;return error(msg);}
 auto* q=dds_create_qos();dds_qset_reliability(q,reliable?DDS_RELIABILITY_RELIABLE:DDS_RELIABILITY_BEST_EFFORT,DDS_MSECS(100));
 dds_qset_durability(q,transient?DDS_DURABILITY_TRANSIENT_LOCAL:DDS_DURABILITY_VOLATILE);dds_qset_history(q,DDS_HISTORY_KEEP_LAST,100);
 dds_qset_resource_limits(q,1000,10,100);
 s.reader=dds_create_reader(s.participant,s.topic,q,nullptr);dds_delete_qos(q);
 if(s.reader<0){auto msg="Falha ao assinar: "+std::string(dds_strretcode(s.reader));s.stop_reader();s.last_error=msg;return error(msg);}
 s.rate_time=Clock::now();s.mode=std::string(reliable?"reliable":"best_effort")+" / "+(transient?"transient_local":"volatile");
 return "{\"ok\":true}";
}
std::string Session::poll() {
 auto& s=*impl;
 const bool running=s.participant>0;
 if(running){s.discovery_error.clear();s.endpoints(s.pubs,true);s.endpoints(s.subs,false);s.graph_samples();s.participant_samples();s.values();}
 std::ostringstream out;out<<"{\"running\":"<<(running?"true":"false")<<",\"domain\":"<<s.domain_id<<",\"topics\":[";bool first=true;
 for(const auto& row:s.registry.topics()) {
  if(!first)out<<',';
  first=false;
  out<<"{\"name\":"<<json_quote(row.name)<<",\"type\":"<<json_quote(row.type)<<",\"wireName\":"<<json_quote(row.wire_name)<<",\"wireType\":"<<json_quote(row.wire_type)<<",\"publishers\":"<<row.publishers<<",\"subscribers\":"<<row.subscribers<<",\"supported\":"<<(descriptor(row.wire_type)?"true":"false")<<"}";
 }
 out<<"],\"nodes\":[";first=true;std::set<std::string> names;for(auto& entry:s.nodes)for(auto& name:entry.second)names.insert(name);
 for(const auto& name:names){if(!first)out<<',';
  first=false;out<<json_quote(name);}
 double age=s.count?std::chrono::duration<double>(Clock::now()-s.last_sample).count():-1;
 dds_subscription_matched_status_t matched{};if(s.reader>0)dds_get_subscription_matched_status(s.reader,&matched);
 const auto failure=!s.last_error.empty()?s.last_error:(!s.discovery_error.empty()?s.discovery_error:s.value_error);
 const auto state=subscription_state(s.reader>0,s.count,matched.current_count,age,failure);
 out<<"],\"sessionGeneration\":"<<session_generation<<",\"readerGeneration\":"<<reader_generation
    <<",\"connectionState\":"<<json_quote(connection_state(running,s.peer_ids.size(),s.seen_peer,failure))
    <<",\"subscriptionState\":"<<json_quote(state)<<",\"readerActive\":"<<(s.reader>0?"true":"false")
    <<",\"fresh\":"<<(state=="receiving"?"true":"false")<<",\"rosNodes\":"<<names.size();
 out<<",\"ddsPeers\":"<<s.peer_ids.size()<<",\"subscription\":"<<json_quote(s.selected)<<",\"last\":"<<json_quote(s.last)<<",\"count\":"<<s.count<<",\"hz\":"<<s.hz<<",\"age\":"<<age<<",\"matched\":"<<matched.current_count<<",\"qos\":"<<json_quote(s.mode)<<",\"error\":"<<json_quote(failure)<<"}";
 return out.str();
}
}

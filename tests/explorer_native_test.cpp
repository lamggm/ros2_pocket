#include "inspector.hpp"
#include "PocketTypes.h"
#include <dds/dds.h>
#include <chrono>
#include <cstring>
#include <iostream>
#include <stdexcept>
#include <thread>
#include <vector>

struct NodeSpec {
 const char* ns; const char* name;
 std::vector<dds_entity_t> readers, writers;
};

static void check(bool value, const char* message) {
 if(!value) throw std::runtime_error(message);
}
static dds_entity_t entity(dds_entity_t e) { check(e>0,"DDS fixture entity creation failed");return e; }
static std::string guid(dds_entity_t e) {
 dds_guid_t g{};check(dds_get_guid(e,&g)==DDS_RETCODE_OK,"fixture GUID lookup failed");
 const char* h="0123456789abcdef";std::string s;
 for(auto b:g.v){s+=h[b>>4];s+=h[b&15];}return s;
}
struct Fixture {
 pocket::Session session;
 dds_entity_t participant=0, topic=0, fast=0, durable=0, reader=0, graph_writer=0;
 void publish_graph(const std::vector<NodeSpec>& specs,dds_entity_t source=0,dds_entity_t* external_writer=nullptr,dds_entity_t advertised=0) {
  if(source==0)source=participant;
  auto& writer=external_writer?*external_writer:graph_writer;
  if(writer==0) {
   auto t=entity(dds_create_topic(source,&rmw_dds_common_msg_dds__ParticipantEntitiesInfo__desc,"ros_discovery_info",nullptr,nullptr));
   auto* q=dds_create_qos();
   dds_qset_reliability(q,DDS_RELIABILITY_RELIABLE,DDS_MSECS(100));
   dds_qset_durability(q,DDS_DURABILITY_TRANSIENT_LOCAL);
   writer=entity(dds_create_writer(source,t,q,nullptr));dds_delete_qos(q);
  }
  rmw_dds_common_msg_dds__ParticipantEntitiesInfo_ sample{};
  dds_guid_t p{};dds_get_guid(advertised?advertised:source,&p);std::memcpy(sample.gid.data,p.v,16);
  std::vector<rmw_dds_common_msg_dds__NodeEntitiesInfo_> nodes(specs.size());
  std::vector<std::vector<rmw_dds_common_msg_dds__Gid_>> rs(specs.size()),ws(specs.size());
  for(size_t i=0;i<specs.size();i++) {
   std::strncpy(nodes[i].node_namespace,specs[i].ns,256);std::strncpy(nodes[i].node_name,specs[i].name,256);
   auto gids=[](const std::vector<dds_entity_t>& entities){
    std::vector<rmw_dds_common_msg_dds__Gid_> result;
    for(auto e:entities){dds_guid_t g{};check(dds_get_guid(e,&g)==DDS_RETCODE_OK,"graph GID lookup failed");rmw_dds_common_msg_dds__Gid_ gid{};std::memcpy(gid.data,g.v,16);result.push_back(gid);}
    return result;
   };
   rs[i]=gids(specs[i].readers);ws[i]=gids(specs[i].writers);
   nodes[i].reader_gid_seq={static_cast<uint32_t>(rs[i].size()),static_cast<uint32_t>(rs[i].size()),rs[i].data(),false};
   nodes[i].writer_gid_seq={static_cast<uint32_t>(ws[i].size()),static_cast<uint32_t>(ws[i].size()),ws[i].data(),false};
  }
  sample.node_entities_info_seq={static_cast<uint32_t>(nodes.size()),static_cast<uint32_t>(nodes.size()),nodes.data(),false};
  check(dds_write(writer,&sample)==DDS_RETCODE_OK,"graph payload write failed");
 }
 Fixture() {
  check(session.start(69,"127.0.0.1","").find("\"ok\":true")!=std::string::npos,"session start failed");
  participant=entity(dds_create_participant(69,nullptr,nullptr));
  topic=entity(dds_create_topic(participant,&std_msgs_msg_dds__String__desc,"rt/explorer_native/value",nullptr,nullptr));
  auto* q=dds_create_qos();
  dds_qset_reliability(q,DDS_RELIABILITY_BEST_EFFORT,DDS_MSECS(100));
  dds_qset_durability(q,DDS_DURABILITY_VOLATILE);
  dds_qset_history(q,DDS_HISTORY_KEEP_LAST,7);
  dds_qset_deadline(q,DDS_MSECS(250));
  dds_qset_liveliness(q,DDS_LIVELINESS_MANUAL_BY_TOPIC,DDS_SECS(2));
  dds_qset_ownership(q,DDS_OWNERSHIP_EXCLUSIVE);
  dds_qset_destination_order(q,DDS_DESTINATIONORDER_BY_SOURCE_TIMESTAMP);
  fast=entity(dds_create_writer(participant,topic,q,nullptr));
  dds_qset_reliability(q,DDS_RELIABILITY_RELIABLE,DDS_MSECS(100));
  dds_qset_durability(q,DDS_DURABILITY_TRANSIENT_LOCAL);
  dds_qset_history(q,DDS_HISTORY_KEEP_ALL,1);
  dds_qset_deadline(q,DDS_INFINITY);
  dds_qset_liveliness(q,DDS_LIVELINESS_AUTOMATIC,DDS_INFINITY);
  dds_qset_ownership(q,DDS_OWNERSHIP_SHARED);
  dds_qset_destination_order(q,DDS_DESTINATIONORDER_BY_RECEPTION_TIMESTAMP);
  durable=entity(dds_create_writer(participant,topic,q,nullptr));
  reader=entity(dds_create_reader(participant,topic,q,nullptr));
  dds_delete_qos(q);
  std::cout<<"{\"ids\":{\"participant\":\""<<guid(participant)<<"\",\"fast\":\""<<guid(fast)<<"\",\"durable\":\""<<guid(durable)<<"\",\"reader\":\""<<guid(reader)<<"\"}}\n";
 }
 ~Fixture(){if(participant>0)dds_delete(participant);session.stop();}
 void emit(const char* stage) {
  std::string snapshot;
  for(int i=0;i<20;i++){snapshot=session.poll();std::this_thread::sleep_for(std::chrono::milliseconds(25));}
  std::cout<<"{\"stage\":\""<<stage<<"\",\"snapshot\":"<<snapshot<<"}\n";
 }
};
int main(int argc,char** argv) {
 try {
  check(argc==2,"expected test mode");
  Fixture f;
  if(std::string(argv[1])=="qos")f.emit("qos");
  else if(std::string(argv[1])=="graph") {
   f.publish_graph({{"/lab","alpha",{},{f.fast,f.fast}}, {"/lab/","beta",{f.reader},{f.fast}}, {"","wrong_role",{f.fast},{f.reader}}});
   f.emit("graph");
   f.publish_graph({{"/lab","replacement",{f.reader},{f.durable}}});
   f.emit("replacement");
  }
  else if(std::string(argv[1])=="lifecycle") {
   f.publish_graph({{"/lab","start",{f.reader},{f.fast,f.durable}}});f.emit("before");
   rmw_dds_common_msg_dds__ParticipantEntitiesInfo_ disposed{};
   check(dds_dispose(f.graph_writer,&disposed)==DDS_RETCODE_OK,"graph disposal failed");f.emit("graph_disposed");
   f.publish_graph({{"/lab","renew",{f.reader},{f.fast,f.durable}}});f.emit("renewed");
   check(dds_delete(f.fast)==DDS_RETCODE_OK,"endpoint deletion failed");f.fast=0;f.emit("endpoint_removed");
   f.fast=entity(dds_create_writer(f.participant,f.topic,nullptr,nullptr));f.emit("endpoint_restarted");
   f.publish_graph({{"/lab","restarted",{f.reader},{f.fast,f.durable}}});f.emit("restart_graph");
   check(dds_delete(f.graph_writer)==DDS_RETCODE_OK,"graph writer deletion failed");f.graph_writer=0;f.emit("graph_writer_dead");
   f.publish_graph({{"/lab","back",{f.reader},{f.fast,f.durable}}});f.emit("graph_writer_returned");
   check(dds_delete(f.participant)==DDS_RETCODE_OK,"participant deletion failed");f.participant=0;f.graph_writer=0;f.emit("participant_dead");
   f.participant=entity(dds_create_participant(69,nullptr,nullptr));
   f.topic=entity(dds_create_topic(f.participant,&std_msgs_msg_dds__String__desc,"rt/explorer_native/value",nullptr,nullptr));
   f.fast=entity(dds_create_writer(f.participant,f.topic,nullptr,nullptr));f.emit("participant_restarted");
   f.publish_graph({{"/lab","new_process",{},{f.fast}}});f.emit("participant_restart_graph");
  }
  else if(std::string(argv[1])=="identity") {
   f.publish_graph({{"/lab","owner",{f.reader},{f.fast}}});f.emit("owner");
   auto other=entity(dds_create_participant(69,nullptr,nullptr));
   auto t=entity(dds_create_topic(other,&std_msgs_msg_dds__String__desc,"rt/explorer_native/value",nullptr,nullptr));
   auto w=entity(dds_create_writer(other,t,nullptr,nullptr));dds_entity_t graph=0;
   f.publish_graph({{"/lab","spoofed",{},{f.fast}}},other,&graph,f.participant);f.emit("spoofed");
   f.publish_graph({{"/lab","foreign",{f.reader},{f.fast,w}}},other,&graph);f.emit("foreign");
   rmw_dds_common_msg_dds__ParticipantEntitiesInfo_ disposed{};
   check(dds_dispose(f.graph_writer,&disposed)==DDS_RETCODE_OK,"owner graph dispose failed");f.emit("owner_disposed");
   f.publish_graph({{"/lab","owner_again",{f.reader},{f.fast}}});f.emit("owner_again");
   check(dds_delete(graph)==DDS_RETCODE_OK,"other graph writer deletion failed");f.emit("foreign_graph_dead");
   check(dds_delete(other)==DDS_RETCODE_OK,"other participant deletion failed");f.emit("foreign_participant_dead");
  }
  else if(std::string(argv[1])=="compatibility") {
   auto* q=dds_create_qos();dds_qset_reliability(q,DDS_RELIABILITY_BEST_EFFORT,DDS_MSECS(100));dds_qset_durability(q,DDS_DURABILITY_VOLATILE);
   entity(dds_create_writer(f.participant,f.topic,q,nullptr));dds_delete_qos(q);
   f.emit("prospective_default");
   for(int mode=0;mode<4;mode++) {
    check(f.session.subscribe("rt/explorer_native/value","std_msgs::msg::dds_::String_",mode&1,mode&2).find("\"ok\":true")!=std::string::npos,"reader QoS selection failed");
    f.emit((std::string("reader_")+std::to_string(mode)).c_str());
   }
   check(f.session.subscribe("rt/explorer_native/value","std_msgs::msg::dds_::String_",false,false).find("\"ok\":true")!=std::string::npos,"reader QoS recovery failed");f.emit("reader_recovery");
  }
  else throw std::runtime_error("unknown test mode");
 }catch(const std::exception& e){std::cerr<<"FAIL "<<e.what()<<'\n';return 1;}
}

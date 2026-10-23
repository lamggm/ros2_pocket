// Separate-process loopback DDS fixture: exercises RTPS discovery/CDR and lease expiry.
#include "inspector.hpp"
#include "PocketTypes.h"
#include <dds/dds.h>
#include <cstring>
#include <iostream>
#include <stdexcept>
#include <string>

static void check(bool value,const char* message) {if(!value)throw std::runtime_error(message);}
static dds_entity_t entity(dds_entity_t e){check(e>0,"wire fixture entity creation failed");return e;}
static dds_guid_t raw_guid(dds_entity_t entity) {
 dds_guid_t g{};check(dds_get_guid(entity,&g)==DDS_RETCODE_OK,"wire GUID lookup failed");return g;
}
static std::string guid(dds_entity_t e) {
 const char* h="0123456789abcdef";std::string s;
 for(auto b:raw_guid(e).v){s+=h[b>>4];s+=h[b&15];}return s;
}
static void producer() {
 const char* config="<CycloneDDS><Domain Id=\"any\"><General><Interfaces><NetworkInterface address=\"127.0.0.1\"/></Interfaces><AllowMulticast>false</AllowMulticast><DontRoute>true</DontRoute></General><Discovery><ParticipantIndex>auto</ParticipantIndex><MaxAutoParticipantIndex>30</MaxAutoParticipantIndex><LeaseDuration>2s</LeaseDuration><SPDPInterval>200ms</SPDPInterval><Peers><Peer Address=\"127.0.0.1\"/></Peers></Discovery></Domain></CycloneDDS>";
 auto domain=entity(dds_create_domain(70,config));
 auto p=entity(dds_create_participant(70,nullptr,nullptr));
 auto t=entity(dds_create_topic(p,&std_msgs_msg_dds__String__desc,"rt/explorer_native/wire",nullptr,nullptr));
 auto* q=dds_create_qos();
 dds_qset_reliability(q,DDS_RELIABILITY_BEST_EFFORT,DDS_MSECS(100));
 dds_qset_durability(q,DDS_DURABILITY_VOLATILE);dds_qset_history(q,DDS_HISTORY_KEEP_LAST,7);
 dds_qset_deadline(q,DDS_MSECS(750));dds_qset_liveliness(q,DDS_LIVELINESS_MANUAL_BY_PARTICIPANT,DDS_SECS(3));
 auto w=entity(dds_create_writer(p,t,q,nullptr));
 auto gt=entity(dds_create_topic(p,&rmw_dds_common_msg_dds__ParticipantEntitiesInfo__desc,"ros_discovery_info",nullptr,nullptr));
 dds_reset_qos(q);dds_qset_reliability(q,DDS_RELIABILITY_RELIABLE,DDS_MSECS(100));dds_qset_durability(q,DDS_DURABILITY_TRANSIENT_LOCAL);
 auto gw=entity(dds_create_writer(p,gt,q,nullptr));dds_delete_qos(q);
 rmw_dds_common_msg_dds__ParticipantEntitiesInfo_ graph{};
 auto pg=raw_guid(p),wg=raw_guid(w);std::memcpy(graph.gid.data,pg.v,16);
 rmw_dds_common_msg_dds__NodeEntitiesInfo_ node{};
 std::strcpy(node.node_namespace,"/wire");std::strcpy(node.node_name,"producer");
 rmw_dds_common_msg_dds__Gid_ endpoint{};std::memcpy(endpoint.data,wg.v,16);
 node.writer_gid_seq={1,1,&endpoint,false};graph.node_entities_info_seq={1,1,&node,false};
 check(dds_write(gw,&graph)==DDS_RETCODE_OK,"wire graph write failed");
 std::cout<<"{\"participant\":\""<<guid(p)<<"\",\"writer\":\""<<guid(w)<<"\"}"<<std::endl;
 std::string command;
 while(std::getline(std::cin,command)) {
  if(command=="exit")break;
  check(command=="write","unknown wire producer command");
  check(dds_assert_liveliness(p)==DDS_RETCODE_OK,"wire liveliness assertion failed");
  std_msgs_msg_dds__String_ sample{};sample.data=const_cast<char*>("wire sample UTF-8: α");
  check(dds_write(w,&sample)==DDS_RETCODE_OK,"wire sample write failed");
  std::cout<<"{\"ok\":true}"<<std::endl;
 }
 dds_delete(p);dds_delete(domain);
}
static void inspector() {
 pocket::Session session;
 const auto result=session.start(70,"127.0.0.1","127.0.0.1");
 check(result.find("\"ok\":true")!=std::string::npos,"wire inspector session start failed");
 std::cout<<result<<std::endl;
 std::string command;
 while(std::getline(std::cin,command)) {
  if(command=="exit")break;
  if(command=="reliable" || command=="best_effort") {
   const auto result=session.subscribe("rt/explorer_native/wire","std_msgs::msg::dds_::String_",command=="reliable",false);
   check(result.find("\"ok\":true")!=std::string::npos,"wire subscription failed");
  }else if(command=="invalid") {
   check(session.subscribe("rt/explorer_native/invalid topic","std_msgs::msg::dds_::String_",false,false).find("\"ok\":false")!=std::string::npos,"invalid real DDS topic unexpectedly accepted");
  }else check(command=="poll","unknown wire inspector command");
  std::cout<<session.poll()<<std::endl;
 }
 session.stop();session.stop();
}
int main(int argc,char** argv) {
 try {
  check(argc==2,"expected producer or inspector");
  if(std::string(argv[1])=="producer")producer();
  else if(std::string(argv[1])=="inspector")inspector();
  else throw std::runtime_error("unknown wire mode");
 }catch(const std::exception& e){std::cerr<<"FAIL "<<e.what()<<std::endl;return 1;}
}

#include "inspector.hpp"
#include "PocketTypes.h"
#include <dds/dds.h>
#include <chrono>
#include <thread>
#include <iostream>
#include <stdexcept>
#include <cstring>
using namespace std::chrono_literals;
namespace {
void require(bool value,const std::string& why){if(!value)throw std::runtime_error(why);}
bool has(const std::string& s,const std::string& field,const std::string& value){return s.find("\""+field+"\":"+value)!=std::string::npos;}
std::string number(const std::string& s,const std::string& field){auto start=s.find("\""+field+"\":")+field.size()+3;return s.substr(start,s.find_first_of(",}",start)-start);}
bool ok(const std::string& s){return has(s,"ok","true");}
std::string wait(pocket::Session& s,const std::string& field,const std::string& value){
 auto until=std::chrono::steady_clock::now()+12s;std::string snapshot;
 do {snapshot=s.poll();if(has(snapshot,field,value))return snapshot;std::this_thread::sleep_for(50ms);}while(std::chrono::steady_clock::now()<until);
 throw std::runtime_error("timeout "+field+"="+value+": "+snapshot);
}
struct Peer {
 dds_entity_t participant=0, writer=0;
 Peer(){
  participant=dds_create_participant(69,nullptr,nullptr);require(participant>0,"fixture participant");
  auto topic=dds_create_topic(participant,&std_msgs_msg_dds__String__desc,"rt/pocket_diagnostics/chatter",nullptr,nullptr);
  auto q=dds_create_qos();dds_qset_reliability(q,DDS_RELIABILITY_RELIABLE,DDS_MSECS(100));
  writer=dds_create_writer(participant,topic,q,nullptr);dds_delete_qos(q);require(writer>0,"fixture writer");
 }
 void announceNodes(){
  auto topic=dds_create_topic(participant,&rmw_dds_common_msg_dds__ParticipantEntitiesInfo__desc,"ros_discovery_info",nullptr,nullptr);
  auto q=dds_create_qos();dds_qset_reliability(q,DDS_RELIABILITY_RELIABLE,DDS_MSECS(100));dds_qset_durability(q,DDS_DURABILITY_TRANSIENT_LOCAL);
  auto graphWriter=dds_create_writer(participant,topic,q,nullptr);dds_delete_qos(q);require(graphWriter>0,"graph fixture writer");
  dds_guid_t guid{};dds_get_guid(participant,&guid);
  rmw_dds_common_msg_dds__ParticipantEntitiesInfo_ value{};std::memcpy(value.gid.data,guid.v,16);
  rmw_dds_common_msg_dds__NodeEntitiesInfo_ names[2]{};
  std::strcpy(names[0].node_namespace,"/diagnostics");std::strcpy(names[0].node_name,"one");
  std::strcpy(names[1].node_namespace,"/diagnostics");std::strcpy(names[1].node_name,"two");
  value.node_entities_info_seq={2,2,names,false};
  require(dds_write(graphWriter,&value)==DDS_RETCODE_OK,"write graph fixture");
 }
 void sample(){std_msgs_msg_dds__String_ value{};value.data=const_cast<char*>("diagnostics fixture");require(dds_write(writer,&value)==DDS_RETCODE_OK,"write synthetic sample");}
 ~Peer(){if(participant>0)dds_delete(participant);}
};
}
int main(){try{
 pocket::Session s;
 require(ok(s.start(69,"127.0.0.1","")),"loopback session");
 auto initial=s.poll();require(has(initial,"connectionState","\"discovering\""),"initial discovery: "+initial);
 require(has(initial,"ddsPeers","0"),"inspector must exclude itself");
 const auto generation=number(initial,"sessionGeneration");
 const std::string type="std_msgs::msg::dds_::String_";
 require(ok(s.subscribe("rt/pocket_diagnostics/chatter",type,false,false)),"subscribe");
 {
  Peer peer;
  auto connected=wait(s,"ddsPeers","1");
  require(has(connected,"nodes","[]"),"DDS peer is not a ROS node");
  require(has(connected,"connectionState","\"connected\""),"DDS connected");
  require(has(connected,"subscriptionState","\"waiting\""),"discovery is not data");
  peer.announceNodes();auto graph=wait(s,"rosNodes","2");require(has(graph,"ddsPeers","1"),"two ROS nodes still one participant");
  wait(s,"matched","1");peer.sample();
  auto live=wait(s,"subscriptionState","\"receiving\"");require(has(live,"fresh","true"),"fresh live data");
  // Prevalidation leaves the old reader, but surfaces a persistent operation error.
  require(!ok(s.subscribe("rt/pocket_diagnostics/chatter","unknown",false,false)),"unsupported type rejected");
  for(int i=0;i<3;i++){
   auto failed=s.poll();require(has(failed,"readerActive","true"),"prevalidation keeps reader");
   require(has(failed,"subscriptionState","\"error\""),"prevalidation error persists");require(has(failed,"fresh","false"),"error not active telemetry");
  }
  require(ok(s.subscribe("rt/pocket_diagnostics/chatter",type,false,false)),"explicit recovery");
  require(has(s.poll(),"error","\"\""),"successful subscribe clears operation error");
  wait(s,"matched","1");peer.sample();wait(s,"subscriptionState","\"receiving\"");
  require(!ok(s.subscribe("rt/pocket_diagnostics/invalid topic",type,false,false)),"actual DDS failure");
  for(int i=0;i<3;i++){
   auto failed=s.poll();require(has(failed,"readerActive","false"),"failed replacement reader inactive");
   require(has(failed,"count","0"),"failed replacement cleared count");require(has(failed,"last","\"\""),"failed replacement cleared value");
   require(has(failed,"subscriptionState","\"error\""),"DDS error persists");
  }
  require(ok(s.subscribe("rt/pocket_diagnostics/chatter",type,false,false)),"recover from actual failure");
  wait(s,"matched","1");peer.sample();wait(s,"subscriptionState","\"receiving\"");
 }
 auto lost=wait(s,"ddsPeers","0");require(has(lost,"connectionState","\"reconnecting\""),"peer loss reconnecting");
 require(number(lost,"sessionGeneration")==generation,"peer loss did not recreate session");
 require(has(lost,"rosNodes","0"),"departed peer nodes removed");
 require(has(lost,"readerActive","true"),"peer loss preserves reader");require(has(lost,"fresh","false"),"unmatched old value stale");
 {
  Peer returned;wait(s,"ddsPeers","1");wait(s,"matched","1");returned.sample();auto recovered=wait(s,"subscriptionState","\"receiving\"");require(number(recovered,"sessionGeneration")==generation,"peer recovery same session");
  std::this_thread::sleep_for(3100ms);require(has(s.poll(),"subscriptionState","\"stale\""),"old sample stale even while matched");
 }
 s.stop();s.stop();require(has(s.poll(),"connectionState","\"offline\""),"reset offline");
 require(!ok(s.start(69,"invalid","")),"invalid start");require(has(s.poll(),"connectionState","\"error\""),"start failure persists");
 require(ok(s.start(69,"127.0.0.1","")),"restart");require(has(s.poll(),"error","\"\""),"restart clears error");
 std::cout<<"PASS real DDS discovery, peer loss/recovery, stale, persistent prevalidation/creation errors and reset\n";
 return 0;
 }catch(const std::exception& e){std::cerr<<"FAIL "<<e.what()<<'\n';return 1;}}

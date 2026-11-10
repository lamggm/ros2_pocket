#include "inspector.hpp"
#include "graph_humble_aliases.h"
#include "GraphHumble.h"
#include <dds/dds.h>
#include <chrono>
#include <thread>
#include <cstring>
#include <stdexcept>
#include <iostream>
static void require(bool ok,const char* why){if(!ok)throw std::runtime_error(why);}
int main(){
 try{
  const char* xml="<CycloneDDS><Domain Id=\"any\"><General><Interfaces><NetworkInterface address=\"127.0.0.1\" multicast=\"false\"/></Interfaces><AllowMulticast>false</AllowMulticast></General><Discovery><ExternalDomainId>84</ExternalDomainId><ParticipantIndex>auto</ParticipantIndex><MaxAutoParticipantIndex>30</MaxAutoParticipantIndex><Peers><Peer Address=\"127.0.0.1\"/></Peers></Discovery></Domain></CycloneDDS>";
  auto domain=dds_create_domain(2084,xml);require(domain>0,"observer domain failed");auto observer=dds_create_participant(2084,nullptr,nullptr);require(observer>0,"observer participant failed");
  auto topic=dds_create_topic(observer,&rmw_dds_common_msg_dds__ParticipantEntitiesInfo__desc,"ros_discovery_info",nullptr,nullptr);
  auto q=dds_create_qos();dds_qset_reliability(q,DDS_RELIABILITY_RELIABLE,DDS_MSECS(100));dds_qset_durability(q,DDS_DURABILITY_TRANSIENT_LOCAL);dds_qset_history(q,DDS_HISTORY_KEEP_LAST,8);auto reader=dds_create_reader(observer,topic,q,nullptr);dds_delete_qos(q);require(reader>0,"observer graph reader failed");
  auto builtin=dds_create_reader(observer,DDS_BUILTIN_TOPIC_DCPSSUBSCRIPTION,nullptr,nullptr);require(builtin>0,"observer builtin reader failed");
  pocket::Session sensor;require(sensor.start(84,"127.0.0.1","127.0.0.1","humble").find("\"ok\":true")!=std::string::npos,"session startup failed");require(sensor.subscribe("rt/pocket_announcement/value","std_msgs::msg::dds_::Float32_",false,false).find("\"ok\":true")!=std::string::npos,"watch startup failed");
  dds_guid_t endpoint{},participant{};bool discovered=false,announced=false;auto end=std::chrono::steady_clock::now()+std::chrono::seconds(4);
  while(std::chrono::steady_clock::now()<end&&!announced){
   void* samples[16]{};dds_sample_info_t infos[16]{};auto n=dds_take(builtin,samples,infos,16,16);
   for(int i=0;i<n;i++)if(infos[i].valid_data){auto* e=static_cast<dds_builtintopic_endpoint_t*>(samples[i]);if(e&&e->topic_name&&std::strcmp(e->topic_name,"rt/pocket_announcement/value")==0){endpoint=e->key;participant=e->participant_key;discovered=true;}}
   if(n>0)dds_return_loan(builtin,samples,n);
   n=dds_take(reader,samples,infos,16,16);
   for(int i=0;i<n;i++)if(infos[i].valid_data&&discovered){
    auto* m=static_cast<rmw_dds_common_msg_dds__ParticipantEntitiesInfo_*>(samples[i]);if(std::memcmp(m->gid.data,participant.v,16))continue;
    auto* source=dds_get_matched_publication_data(reader,infos[i].publication_handle);require(source!=nullptr,"missing actual announcement writer");require(std::memcmp(source->participant_key.v,m->gid.data,16)==0,"announcement spoofs participant");dds_builtintopic_free_endpoint(source);
    for(int tail=16;tail<24;tail++)require(m->gid.data[tail]==0,"Humble participant GID padding must be zero");require(m->node_entities_info_seq._length==1,"one sensor node expected");auto& node=m->node_entities_info_seq._buffer[0];require(std::strcmp(node.node_namespace,"/ros_pocket")==0,"incorrect sensor namespace");require(node.reader_gid_seq._length==2,"rosout and one telemetry reader expected");bool found=false;for(uint32_t k=0;k<node.reader_gid_seq._length;k++){require(std::memcmp(node.reader_gid_seq._buffer[k].data,participant.v,12)==0,"foreign reader announced");if(std::memcmp(node.reader_gid_seq._buffer[k].data,endpoint.v,16)==0)found=true;}require(found,"actual watch GID missing from announcement");require(node.writer_gid_seq._length==0,"sensor must announce no application publishers");announced=true;
   }
   if(n>0)dds_return_loan(reader,samples,n);std::this_thread::sleep_for(std::chrono::milliseconds(20));
  }
  require(discovered,"sensor endpoint never discovered");require(announced,"No own session readers announced on ros_discovery_info");sensor.stop();
  end=std::chrono::steady_clock::now()+std::chrono::seconds(3);bool removed=false;
  while(std::chrono::steady_clock::now()<end){dds_subscription_matched_status_t status{};require(dds_get_subscription_matched_status(reader,&status)==0,"cannot observe metadata writer removal");if(status.current_count==0){removed=true;break;}std::this_thread::sleep_for(std::chrono::milliseconds(20));}
  require(removed,"sensor metadata writer survived stop");dds_delete(observer);dds_delete(domain);std::cout<<"Sensor announces only its own reader and removes metadata writer on stop\n";return 0;
 }catch(const std::exception& e){std::cerr<<e.what()<<"\n";return 1;}
}

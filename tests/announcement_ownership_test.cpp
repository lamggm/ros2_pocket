#include "ros_reader_announcement.hpp"
#include "PocketTypes.h"
#include <dds/dds.h>
#include <cstring>
#include <iostream>
#include <stdexcept>
#include <chrono>
#include <thread>
static void check(bool ok,const char* why){if(!ok)throw std::runtime_error(why);}
int main(){try{
 const char* xml="<CycloneDDS><Domain Id=\"any\"><General><Interfaces><NetworkInterface address=\"127.0.0.1\" multicast=\"false\"/></Interfaces><AllowMulticast>false</AllowMulticast></General><Discovery><ParticipantIndex>auto</ParticipantIndex><MaxAutoParticipantIndex>30</MaxAutoParticipantIndex><Peers><Peer Address=\"127.0.0.1\"/></Peers></Discovery></Domain></CycloneDDS>";
 auto d=dds_create_domain(84,xml);check(d>0,"domain");auto owner=dds_create_participant(84,nullptr,nullptr),other=dds_create_participant(84,nullptr,nullptr);check(owner>0&&other>0,"participants");
 auto t=dds_create_topic(owner,&std_msgs_msg_dds__Float32__desc,"rt/own",nullptr,nullptr);auto r=dds_create_reader(owner,t,nullptr,nullptr);auto w=dds_create_writer(owner,t,nullptr,nullptr);auto ft=dds_create_topic(other,&std_msgs_msg_dds__Float32__desc,"rt/foreign",nullptr,nullptr);auto foreign=dds_create_reader(other,ft,nullptr,nullptr);
 pocket::RosReaderAnnouncement a;check(a.start(owner,"inspector"),"announcement start");check(a.update({r}),"own reader rejected");check(!a.update({foreign}),"foreign reader accepted");check(!a.update({w}),"application writer accepted");check(!a.update({r,r}),"duplicate reader accepted");check(!a.update(std::vector<dds_entity_t>(9,r)),"reader budget exceeded");check(!a.update({0}),"null entity accepted");
 auto gt=dds_create_topic(other,&rmw_dds_common_msg_dds__ParticipantEntitiesInfo__desc,"ros_discovery_info",nullptr,nullptr);auto q=dds_create_qos();dds_qset_reliability(q,DDS_RELIABILITY_RELIABLE,DDS_MSECS(100));dds_qset_durability(q,DDS_DURABILITY_TRANSIENT_LOCAL);auto observer=dds_create_reader(other,gt,q,nullptr);dds_delete_qos(q);check(observer>0,"observer");
 dds_guid_t id{};dds_get_guid(r,&id);
 auto observe=[&](unsigned expected){auto end=std::chrono::steady_clock::now()+std::chrono::seconds(3);while(std::chrono::steady_clock::now()<end){void* values[8]{};dds_sample_info_t info[8]{};auto n=dds_take(observer,values,info,8,8);bool accepted=false;for(int i=0;i<n;i++)if(info[i].valid_data){auto* m=static_cast<rmw_dds_common_msg_dds__ParticipantEntitiesInfo_*>(values[i]);check(m->node_entities_info_seq._length==1,"node count");auto& node=m->node_entities_info_seq._buffer[0];check(node.writer_gid_seq._length==0,"data publisher declared");if(node.reader_gid_seq._length==expected){if(expected==1)check(std::memcmp(node.reader_gid_seq._buffer[0].data,id.v,16)==0,"invalid update replaced prior own reader");accepted=true;}}if(n>0)dds_return_loan(observer,values,n);if(accepted)return;std::this_thread::sleep_for(std::chrono::milliseconds(20));}throw std::runtime_error("Expected latest own-reader set was not announced");};
 observe(1);check(a.update({}),"empty reader update rejected");observe(0);check(a.update({r}),"reader restore rejected");observe(1);a.stop();check(!a.update({r}),"stopped announcement accepted update");check(!a.start(owner,"inspector","unknown"),"unknown format accepted");check(!a.start(owner,"foreign_role"),"unknown role accepted");dds_delete(other);dds_delete(owner);dds_delete(d);std::cout<<"Ownership, no data publishers, bounded updates, removal and recovery verified\n";return 0;
}catch(const std::exception& e){std::cerr<<e.what()<<"\n";return 1;}}

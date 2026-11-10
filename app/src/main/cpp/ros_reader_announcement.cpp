#include "ros_reader_announcement.hpp"
#include "PocketTypes.h"
#include "graph_humble.hpp"
#include <array>
#include <cstring>
namespace pocket {
void RosReaderAnnouncement::stop(){
 if(writer_>0)dds_delete(writer_);
 if(topic_>0)dds_delete(topic_);
 writer_=0;topic_=0;participant_=0;name_.clear();format_.clear();
}
bool RosReaderAnnouncement::start(dds_entity_t participant,const std::string& role,const std::string& format){
 stop();dds_guid_t guid{};
 if(format!="jazzy"&&format!="humble")return false;
 if((role!="sensor"&&role!="inspector")||dds_get_guid(participant,&guid)<0)return false;
 participant_=participant;format_=format;name_=role+"_";const char hex[]="0123456789abcdef";
 for(auto v:guid.v){name_+=hex[v>>4];name_+=hex[v&15];}
 const auto* descriptor=format=="humble"?humble_graph_descriptor():&rmw_dds_common_msg_dds__ParticipantEntitiesInfo__desc;
 topic_=dds_create_topic(participant_,descriptor,"ros_discovery_info",nullptr,nullptr);
 if(topic_<0){stop();return false;}
 auto* qos=dds_create_qos();dds_qset_reliability(qos,DDS_RELIABILITY_RELIABLE,DDS_MSECS(100));dds_qset_durability(qos,DDS_DURABILITY_TRANSIENT_LOCAL);dds_qset_history(qos,DDS_HISTORY_KEEP_LAST,1);dds_qset_resource_limits(qos,1,1,1);
 if(format=="jazzy"){const std::string hash="typehash=RIHS01_91a0593bacdcc50ea9bdcf849a938b128412cc1ea821245c663bcd26f83c295e;";dds_qset_userdata(qos,hash.data(),hash.size());}
 writer_=dds_create_writer(participant_,topic_,qos,nullptr);dds_delete_qos(qos);
 if(writer_<0){stop();return false;}return true;
}
bool RosReaderAnnouncement::update(const std::vector<dds_entity_t>& readers){
 if(writer_<=0||readers.size()>8)return false;
 dds_guid_t own{};if(dds_get_guid(participant_,&own)<0)return false;
 std::vector<dds_guid_t> ids;
 for(auto reader:readers){
  dds_guid_t guid{};dds_subscription_matched_status_t status{};
  if(reader<=0||dds_get_participant(reader)!=participant_||dds_get_guid(reader,&guid)<0||dds_get_subscription_matched_status(reader,&status)<0)return false;
  for(const auto& id:ids)if(std::memcmp(id.v,guid.v,16)==0)return false;
  ids.push_back(guid);
 }
 if(format_=="humble")return write_humble_reader_announcement(writer_,own,name_,ids);
 std::array<rmw_dds_common_msg_dds__Gid_,8> wire_ids{};for(size_t i=0;i<ids.size();i++)std::memcpy(wire_ids[i].data,ids[i].v,16);
 rmw_dds_common_msg_dds__ParticipantEntitiesInfo_ message{};std::memcpy(message.gid.data,own.v,16);
 rmw_dds_common_msg_dds__NodeEntitiesInfo_ node{};std::strcpy(node.node_namespace,"/ros_pocket");std::strcpy(node.node_name,name_.c_str());
 node.reader_gid_seq={static_cast<uint32_t>(ids.size()),static_cast<uint32_t>(ids.size()),wire_ids.data(),false};
 message.node_entities_info_seq={1,1,&node,false};
 return dds_write(writer_,&message)>=0;
}
}

#include "graph_humble.hpp"
#include "graph_humble_aliases.h"
#include "GraphHumble.h"
#include <array>
#include <cstring>
namespace pocket {
const dds_topic_descriptor_t* humble_graph_descriptor(){return &pocket_humble24_Participant_desc;}
DecodedGraph decode_humble_graph(const void* sample){return decode_graph(static_cast<const pocket_humble24_Participant*>(sample));}
bool write_humble_reader_announcement(dds_entity_t writer,const dds_guid_t& participant,const std::string& name,const std::vector<dds_guid_t>& readers){
 if(readers.size()>8||name.size()>256)return false;
 pocket_humble24_Participant message{};std::memcpy(message.gid.data,participant.v,16);
 pocket_humble24_Node node{};std::strcpy(node.node_namespace,"/ros_pocket");std::strcpy(node.node_name,name.c_str());
 std::array<pocket_humble24_Gid,8> ids{};for(size_t i=0;i<readers.size();i++)std::memcpy(ids[i].data,readers[i].v,16);
 node.reader_gid_seq={static_cast<uint32_t>(readers.size()),static_cast<uint32_t>(readers.size()),ids.data(),false};message.node_entities_info_seq={1,1,&node,false};
 return dds_write(writer,&message)>=0;
}
}

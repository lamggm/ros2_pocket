#pragma once
#include "model.hpp"
#include <algorithm>
#include <cstdint>
#include <string>
#include <vector>
namespace pocket {
struct DecodedGraph {bool valid=false;std::string participant;std::vector<GraphNode> nodes;};
inline std::string graph_guid(const uint8_t* bytes,size_t size){
 if(!bytes||(size!=16&&size!=24))return {};
 for(size_t i=16;i<size;i++)if(bytes[i]!=0)return {};
 static constexpr char hex[]="0123456789abcdef";std::string result;result.reserve(32);
 for(size_t i=0;i<16;i++){result+=hex[bytes[i]>>4];result+=hex[bytes[i]&15];}return result;
}
template<class T> DecodedGraph decode_graph(const T* sample){
 DecodedGraph result;if(!sample)return result;
 result.participant=graph_guid(sample->gid.data,sizeof sample->gid.data);
 if(result.participant.empty())return result;
 const auto& entries=sample->node_entities_info_seq;
 if(entries._length&&!entries._buffer)return result;
 size_t references=0;
 for(uint32_t j=0;j<std::min(entries._length,128u);j++){
  const auto& entry=entries._buffer[j];std::string ns=entry.node_namespace,name=entry.node_name;
  if(name.empty())continue;
  if(ns.empty()||ns.front()!='/')ns='/'+ns;
  if(ns.back()!='/')ns+='/';
  GraphNode node;node.name=ns+name;
  for(bool writer:{false,true}){
   const auto& gids=writer?entry.writer_gid_seq:entry.reader_gid_seq;
   if(gids._length&&!gids._buffer)return result;
   for(uint32_t k=0;k<gids._length&&references<1024;k++,references++){
    auto id=graph_guid(gids._buffer[k].data,sizeof gids._buffer[k].data);
    if(id.empty())return result;
    (writer?node.writers:node.readers).push_back(std::move(id));
   }
  }
  result.nodes.push_back(std::move(node));
 }
 result.valid=true;return result;
}
}

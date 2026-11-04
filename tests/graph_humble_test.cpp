#include "graph_humble.hpp"
#include "inspector.hpp"
#include "graph_humble_aliases.h"
#include "GraphHumble.h"
#include <cstring>
#include <iostream>
#include <stdexcept>
#include <vector>
static void check(bool condition,const char* message){if(!condition)throw std::runtime_error(message);}
int main(){
 try{
  pocket_humble24_Participant sample{};for(int i=0;i<16;i++)sample.gid.data[i]=static_cast<uint8_t>(i+1);
  std::vector<pocket_humble24_Node> nodes(140);std::vector<pocket_humble24_Gid> gids(1100);
  for(auto& gid:gids)gid=sample.gid;
  for(auto& node:nodes){std::strcpy(node.node_namespace,"/known");std::strcpy(node.node_name,"fixture");}
  sample.node_entities_info_seq._length=1;sample.node_entities_info_seq._buffer=nodes.data();
  nodes[0].reader_gid_seq._length=1;nodes[0].reader_gid_seq._buffer=gids.data();
  nodes[0].writer_gid_seq._length=1;nodes[0].writer_gid_seq._buffer=gids.data();
  auto decoded=pocket::decode_humble_graph(&sample);check(decoded.valid,"valid GID24 rejected");
  check(decoded.participant=="0102030405060708090a0b0c0d0e0f10","Humble GUID16 prefix changed");
  check(decoded.nodes.size()==1&&decoded.nodes[0].name=="/known/fixture","node namespace/name changed");
  check(decoded.nodes[0].readers.size()==1&&decoded.nodes[0].writers.size()==1,"reader/writer roles lost");
  sample.gid.data[23]=1;check(!pocket::decode_humble_graph(&sample).valid,"nonzero participant GID24 tail admitted");sample.gid.data[23]=0;
  gids[0].data[16]=1;check(!pocket::decode_humble_graph(&sample).valid,"nonzero endpoint GID24 tail admitted");gids[0].data[16]=0;
  sample.node_entities_info_seq._buffer=nullptr;check(!pocket::decode_humble_graph(&sample).valid,"missing node buffer admitted");sample.node_entities_info_seq._buffer=nodes.data();
  sample.node_entities_info_seq._length=140;nodes[0].reader_gid_seq._length=1100;
  decoded=pocket::decode_humble_graph(&sample);check(decoded.valid&&decoded.nodes.size()==128,"node retention cap changed");
  size_t references=0;for(const auto& node:decoded.nodes)references+=node.readers.size()+node.writers.size();check(references==1024,"global graph-reference cap changed");
  check(pocket::graph_guid(sample.gid.data,20).empty(),"unknown GID size admitted");
  pocket::Session session;
  for(const char* format:{"","auto","HUMBLE","rolling","jazzy16"}){
   check(session.start(86,"127.0.0.1","",format).find("\"ok\":false")!=std::string::npos,"unknown graph mode started DDS");
   check(session.poll().find("\"running\":false")!=std::string::npos,"unknown graph mode left a running session");
  }
  std::cout<<"PASS explicit graph formats, GID24 participant/endpoint zero tails, roles and128/1024caps\n";
 }catch(const std::exception& error){std::cerr<<"FAIL "<<error.what()<<'\n';return 1;}
}

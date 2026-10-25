// Real standalone DDS writer for explicit source timestamps. Never linked into APK.
#include <dds/dds.h>
#include "PocketTypes.h"
#include <chrono>
#include <cstdlib>
#include <iostream>
#include <sstream>
#include <string>
#include <thread>

int main(int argc,char** argv){
 if(argc!=3)return 2;
 int domain=std::atoi(argv[1]);if(domain<0||domain>101)return 2;
 dds_entity_t participant=dds_create_participant(domain,nullptr,nullptr);
 if(participant<0)return 3;
 auto topic=dds_create_topic(participant,&std_msgs_msg_dds__Float64__desc,argv[2],nullptr,nullptr);
 auto writer=dds_create_writer(participant,topic,nullptr,nullptr);
 if(topic<0||writer<0){dds_delete(participant);return 4;}
 std::string line;
 while(std::getline(std::cin,line)){
  std::istringstream input(line);std::string token;long long source;
  if(!(input>>token>>source)){dds_delete(participant);return 5;}
  char* end=nullptr;double value=std::strtod(token.c_str(),&end);
  if(!end||*end||source<0){dds_delete(participant);return 6;}
  std_msgs_msg_dds__Float64_ sample{value};
  if(dds_write_ts(writer,&sample,source)!=DDS_RETCODE_OK){dds_delete(participant);return 7;}
 }
 dds_delete(participant);return 0;
}

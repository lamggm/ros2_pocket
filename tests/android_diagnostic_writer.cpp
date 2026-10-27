// Host-only true Cyclone DDS producer. Run only in its owned network namespace.
#include "PocketTypes.h"
#include <dds/dds.h>
#include <chrono>
#include <thread>
#include <string>
#include <array>
#include <stdexcept>
int main(){
 const auto p=dds_create_participant(67,nullptr,nullptr);if(p<=0)return 2;
 std::array<dds_entity_t,4> writers{};
 for(int i=0;i<4;i++){auto name="rt/pocket_diagnostic/watch"+std::to_string(i);auto t=dds_create_topic(p,&std_msgs_msg_dds__String__desc,name.c_str(),nullptr,nullptr);writers[i]=dds_create_writer(p,t,nullptr,nullptr);if(writers[i]<=0)return 3;}
 auto t=dds_create_topic(p,&rcl_interfaces_msg_dds__Log__desc,"rt/rosout",nullptr,nullptr);auto log=dds_create_writer(p,t,nullptr,nullptr);if(log<=0)return 4;
 for(int seq=1;seq<=600;seq++){
  for(int i=0;i<4;i++){auto text="diagnostic-slot-"+std::to_string(i)+" seq="+std::to_string(seq);std_msgs_msg_dds__String_ sample{};sample.data=const_cast<char*>(text.c_str());if(dds_write(writers[i],&sample)<0)return 5;}
  rcl_interfaces_msg_dds__Log_ sample{};sample.name=const_cast<char*>("pocket_diagnostic.logger");sample.msg=const_cast<char*>("real-host-jni-sequence");sample.file=sample.function=const_cast<char*>("");sample.level=20;sample.line=seq;if(dds_write(log,&sample)<0)return 6;
  std::this_thread::sleep_for(std::chrono::milliseconds(100));
 }
 dds_delete(p);
}

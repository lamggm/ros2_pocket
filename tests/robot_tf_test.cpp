#include "robot_description.hpp"
#include "PocketTypes.h"
#include <dds/dds.h>
#include <cassert>
#include <chrono>
#include <thread>
#include <iostream>
int main(){pocket::RobotDescriptionSession r;assert(r.start(79,"127.0.0.1","","rt/cad_robot/robot_description",false,false).find("true")!=std::string::npos);assert(r.start_tf("rt/cad_robot/tf","rt/cad_robot/tf_static",true,true).find("true")!=std::string::npos);auto p=dds_create_participant(79,nullptr,nullptr);assert(p>0);auto t=dds_create_topic(p,&tf2_msgs_msg_dds__TFMessage__desc,"rt/cad_robot/tf",nullptr,nullptr);assert(t>0);auto q=dds_create_qos();dds_qset_reliability(q,DDS_RELIABILITY_RELIABLE,DDS_MSECS(100));auto w=dds_create_writer(p,t,q,nullptr);dds_delete_qos(q);assert(w>0);geometry_msgs_msg_dds__TransformStamped_ e{};e.header.frame_id=const_cast<char*>("base");e.child_frame_id=const_cast<char*>("wheel");e.header.stamp.sec=42;e.transform.translation.x=1.25;e.transform.rotation.w=1;tf2_msgs_msg_dds__TFMessage_ message{};message.transforms._maximum=message.transforms._length=1;message.transforms._buffer=&e;bool received=false;
for(int i=0;i<60;i++){assert(dds_write(w,&message)==0);auto s=r.poll_tf();if(s.find("\"child\":\"wheel\"")!=std::string::npos){assert(s.find("\"parent\":\"base\"")!=std::string::npos);assert(s.find("1.25")!=std::string::npos);received=true;break;}std::this_thread::sleep_for(std::chrono::milliseconds(50));}assert(received);r.stop_tf();assert(r.poll_tf().find("\"running\":false")!=std::string::npos);assert(r.poll().find("\"running\":true")!=std::string::npos);r.stop();dds_delete(p);std::cout<<"PASS actual native TFMessage receive, nonzero transform, independent teardown\n";}

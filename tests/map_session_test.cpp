#include "sensors.hpp"
#include "robot_description.hpp"
#include <dds/dds.h>
#include <cassert>
#include <iostream>
#include <thread>
#include <chrono>
#include <fstream>
#include <cmath>
#include <limits>
static bool ok(const std::string& value){return value.find("\"ok\":true")!=std::string::npos;}
static void map_pause(){std::this_thread::sleep_for(std::chrono::milliseconds(10));}
int main(int argc,char** argv){
 const char* config="<CycloneDDS><Domain Id=\"any\"><General><Interfaces><NetworkInterface address=\"127.0.0.1\" multicast=\"true\"/></Interfaces><AllowMulticast>spdp</AllowMulticast></General><Discovery><ParticipantIndex>auto</ParticipantIndex></Discovery></Domain></CycloneDDS>";
 auto domain=dds_create_domain(80,config);assert(domain>0);auto participant=dds_create_participant(80,nullptr,nullptr);assert(participant>0);
 auto topic=dds_create_topic(participant,&nav_msgs_msg_dds__OccupancyGrid__desc,"rt/map",nullptr,nullptr);assert(topic>0);
 auto* qos=dds_create_qos();dds_qset_reliability(qos,DDS_RELIABILITY_RELIABLE,DDS_MSECS(100));dds_qset_durability(qos,DDS_DURABILITY_TRANSIENT_LOCAL);dds_qset_history(qos,DDS_HISTORY_KEEP_LAST,1);auto writer=dds_create_writer(participant,topic,qos,nullptr);assert(writer>0);
 int8_t cells[]={-1,0,50,100};nav_msgs_msg_dds__OccupancyGrid_ grid{};grid.header.frame_id=const_cast<char*>("map");grid.header.stamp.sec=8;grid.header.stamp.nanosec=2;grid.info.width=grid.info.height=2;grid.info.resolution=.1f;grid.info.origin.position.x=1;grid.info.origin.position.y=2;grid.info.origin.orientation.w=1;grid.data={4,4,cells,false};
 assert(dds_write(writer,&grid)==0); // Retained map is published BEFORE the reader exists.
 pocket::SensorSession reader;assert(ok(reader.start(80,"127.0.0.1","","rt/map","nav_msgs::msg::dds_::OccupancyGrid_","lan","jazzy",true,true)));
 pocket::SensorFrame first;for(int n=0;n<200;n++){auto value=reader.poll();if(!value.payload.empty()){first=value;break;}map_pause();}
 assert(first.error.empty()&&first.payload==std::vector<uint8_t>({255,0,50,100}));assert(first.metadata.find("\"kind\":\"map\"")!=std::string::npos);
 if(argc==2){std::ofstream file(argv[1],std::ios::binary);uint32_t len=static_cast<uint32_t>(first.metadata.size());for(int i=0;i<4;i++)file.put(static_cast<char>(len>>(8*i)));file<<first.metadata;file.write(reinterpret_cast<const char*>(first.payload.data()),first.payload.size());assert(file.good());}
 // Literal production encoder negatives; no relaxed bounds or valid-zero substitutes.
 auto bad=grid;bad.info.width=2049;assert(!pocket::map_frame(bad).error.empty());bad=grid;bad.data._length=3;assert(!pocket::map_frame(bad).error.empty());bad=grid;bad.info.resolution=std::numeric_limits<float>::quiet_NaN();assert(!pocket::map_frame(bad).error.empty());bad=grid;bad.info.origin.orientation.x=.2;assert(!pocket::map_frame(bad).error.empty());cells[0]=-2;assert(!pocket::map_frame(grid).error.empty());cells[0]=-1;
 // Standalone TF uses no robot_description topic/reader.
 pocket::RobotDescriptionSession tf;assert(ok(tf.start(80,"127.0.0.1","","",false,false,"lan","jazzy",true)));assert(tf.poll().find("\"running\":false")!=std::string::npos);assert(ok(tf.start_tf("rt/tf","",true,true)));
 auto tfTopic=dds_create_topic(participant,&tf2_msgs_msg_dds__TFMessage__desc,"rt/tf",nullptr,nullptr);auto tfWriter=dds_create_writer(participant,tfTopic,nullptr,nullptr);assert(tfWriter>0);
 geometry_msgs_msg_dds__TransformStamped_ transform{};transform.header.frame_id=const_cast<char*>("map");transform.child_frame_id=const_cast<char*>("base_link");transform.header.stamp.sec=9;transform.transform.translation.x=1.5;transform.transform.translation.y=2.5;transform.transform.rotation.w=1;tf2_msgs_msg_dds__TFMessage_ message{};message.transforms={1,1,&transform,false};
 bool received=false;for(int n=0;n<200;n++){assert(dds_write(tfWriter,&message)==0);auto raw=tf.poll_tf();if(raw.find("\"child\":\"base_link\"")!=std::string::npos){received=true;break;}map_pause();}assert(received);tf.stop();
 // A different map publisher must not silently replace the selected map authority.
 auto other=dds_create_writer(participant,topic,qos,nullptr);assert(other>0);grid.header.stamp.sec=10;assert(dds_write(other,&grid)==0);bool rejected=false;for(int n=0;n<200;n++){auto value=reader.poll();if(!value.error.empty()){rejected=true;break;}map_pause();}
 assert(rejected&&"A foreign map writer must revoke the map until explicit reselection");
 reader.stop();dds_delete_qos(qos);assert(dds_delete(participant)==0);assert(dds_delete(domain)==0);std::cout<<"retained map / bounds / standalone TF / writer isolation PASS\n";
}

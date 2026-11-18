#include "sensors.hpp"
#include "inspector.hpp"
#include <dds/dds.h>
#include <iostream>
#include <thread>
#include <chrono>
#include <stdexcept>
static void check(bool b,const char* why){if(!b)throw std::runtime_error(why);}
int main(){try{
 pocket::Session telemetry;pocket::SensorSession visual,camera{pocket::SensorSession::Channel::Camera};
 check(telemetry.start(74,"127.0.0.1","").find("true")!=std::string::npos,"telemetry startup");
 auto answer=visual.start(74,"127.0.0.1","","rt/v04_scan","sensor_msgs::msg::dds_::LaserScan_");
 check(answer.find("true")!=std::string::npos,"visual DDS start must succeed without changing telemetry domain");
 check(camera.start(74,"127.0.0.1","","rt/v04_image","sensor_msgs::msg::dds_::Image_").find("true")!=std::string::npos,"camera start alongside scan");
 auto participant=dds_create_participant(74,nullptr,nullptr);check(participant>0,"publisher participant");
 auto topic=dds_create_topic(participant,&sensor_msgs_msg_dds__LaserScan__desc,"rt/v04_scan",nullptr,nullptr);
 auto q=dds_create_qos();dds_qset_reliability(q,DDS_RELIABILITY_BEST_EFFORT,0);auto writer=dds_create_writer(participant,topic,q,nullptr);dds_delete_qos(q);
 check(writer>0,"scan writer");
 auto image_topic=dds_create_topic(participant,&sensor_msgs_msg_dds__Image__desc,"rt/v04_image",nullptr,nullptr);
 auto image_writer=dds_create_writer(participant,image_topic,nullptr,nullptr);check(image_writer>0,"image writer");
 sensor_msgs_msg_dds__Image_ image{};uint8_t rgb[]={18,35,72};image.header.frame_id=const_cast<char*>("camera");image.width=image.height=1;image.step=3;image.encoding=const_cast<char*>("rgb8");image.data._buffer=rgb;image.data._length=3;
 sensor_msgs_msg_dds__LaserScan_ scan{};float ranges[]={2,3};scan.header.frame_id=const_cast<char*>("laser");scan.angle_max=1;scan.angle_increment=1;scan.range_max=10;scan.ranges._buffer=ranges;scan.ranges._length=2;
 bool both=false;
 for(int i=0;i<60&&!both;i++){
  check(dds_write(writer,&scan)==0&&dds_write(image_writer,&image)==0,"write observations");
  std::this_thread::sleep_for(std::chrono::milliseconds(50));
  auto s=visual.poll(),c=camera.poll();bool got_scan=s.payload.size()==16&&s.metadata.find("\"kind\":\"scan\"")!=std::string::npos;
  bool got_image=c.payload==std::vector<uint8_t>{18,35,72}&&c.metadata.find("\"kind\":\"image\"")!=std::string::npos;
  both=got_scan&&got_image;
 }
 check(both,"both typed samples must arrive in the same polling round");
 camera.stop();check(camera.poll().metadata.find("\"running\":false")!=std::string::npos,"camera stop");
 check(visual.poll().metadata.find("\"running\":true")!=std::string::npos,"camera stop cannot stop scan");
 check(camera.start(74,"127.0.0.1","","rt/v04_scan","sensor_msgs::msg::dds_::LaserScan_").find("false")!=std::string::npos,"wrong role reject");
 check(telemetry.poll().find("\"running\":true")!=std::string::npos,"sensor may not disrupt telemetry");
 visual.stop();check(visual.poll().payload.empty(),"stop must remove retained sensor data");
 check(visual.start(74,"127.0.0.1","","rt/v04_scan","unsupported").find("false")!=std::string::npos,"unknown type reject");
 dds_delete(participant);telemetry.stop();std::cout<<"PASS sensor session lifecycle and domain isolation\n";return 0;
 }catch(const std::exception& e){std::cerr<<e.what()<<"\n";return 1;}}

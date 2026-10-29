#include "sensors.hpp"
#include "inspector.hpp"
#include <dds/dds.h>
#include <iostream>
#include <thread>
#include <chrono>
#include <stdexcept>
static void check(bool b,const char* why){if(!b)throw std::runtime_error(why);}
int main(){try{
 pocket::Session telemetry;pocket::SensorSession visual;
 check(telemetry.start(74,"127.0.0.1","").find("true")!=std::string::npos,"telemetry startup");
 auto answer=visual.start(74,"127.0.0.1","","rt/v04_scan","sensor_msgs::msg::dds_::LaserScan_");
 check(answer.find("true")!=std::string::npos,"visual DDS start must succeed without changing telemetry domain");
 auto participant=dds_create_participant(74,nullptr,nullptr);check(participant>0,"publisher participant");
 auto topic=dds_create_topic(participant,&sensor_msgs_msg_dds__LaserScan__desc,"rt/v04_scan",nullptr,nullptr);
 auto q=dds_create_qos();dds_qset_reliability(q,DDS_RELIABILITY_BEST_EFFORT,0);auto writer=dds_create_writer(participant,topic,q,nullptr);dds_delete_qos(q);
 sensor_msgs_msg_dds__LaserScan_ scan{};float ranges[]={2,3};scan.header.frame_id=const_cast<char*>("laser");scan.angle_max=1;scan.angle_increment=1;scan.range_max=10;scan.ranges._buffer=ranges;scan.ranges._length=2;
 bool got=false;for(int i=0;i<60;i++){dds_write(writer,&scan);std::this_thread::sleep_for(std::chrono::milliseconds(50));auto f=visual.poll();if(f.payload.size()==16){got=true;break;}}
 check(got,"true DDS samples must reach alternate internal domain");
 check(telemetry.poll().find("\"running\":true")!=std::string::npos,"sensor may not disrupt telemetry");
 visual.stop();check(visual.poll().payload.empty(),"stop must remove retained sensor data");
 check(visual.start(74,"127.0.0.1","","rt/v04_scan","unsupported").find("false")!=std::string::npos,"unknown type reject");
 dds_delete(participant);telemetry.stop();std::cout<<"PASS sensor session lifecycle and domain isolation\n";return 0;
 }catch(const std::exception& e){std::cerr<<e.what()<<"\n";return 1;}}

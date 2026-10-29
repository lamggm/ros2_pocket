#include "sensors.hpp"
#include <cmath>
#include <limits>
#include <iostream>
#include <stdexcept>
static void check(bool v,const char* why){if(!v)throw std::runtime_error(why);}
int main(int argc,char** argv){try{
 std::string mode=argc>1?argv[1]:"scan";
 if(mode=="scan"){
  sensor_msgs_msg_dds__LaserScan_ s{};s.header.frame_id=const_cast<char*>("laser");s.angle_min=0;s.angle_max=3.1415927f;s.angle_increment=1.5707963f;s.range_min=.1f;s.range_max=10;
  float ranges[]={2,3,std::numeric_limits<float>::infinity()};s.ranges._buffer=ranges;s.ranges._length=3;
  auto f=pocket::scan_frame(s);check(f.error.empty(),"valid scan rejected");check(f.payload.size()==16,"two real XY points required, not invalid range at origin");
  check(f.metadata.find("laser")!=std::string::npos,"frame identity missing");
  s.angle_increment=0;check(!pocket::scan_frame(s).error.empty(),"zero increment must fail");
  s.angle_increment=1.5707963f;s.ranges._length=8193;check(!pocket::scan_frame(s).error.empty(),"range cap must fail before accessing buffer");
 }else if(mode=="image"){
  sensor_msgs_msg_dds__Image_ s{};s.header.frame_id=const_cast<char*>("camera");s.width=2;s.height=1;s.encoding=const_cast<char*>("rgb8");s.step=8;uint8_t data[]={255,0,0,0,255,0,99,99};s.data._buffer=data;s.data._length=8;
  auto f=pocket::image_frame(s);check(f.error.empty()&&f.payload.size()==8,"raw image with row padding must be retained");
  s.step=5;check(!pocket::image_frame(s).error.empty(),"short row must fail");s.step=8;s.encoding=const_cast<char*>("16UC1");check(!pocket::image_frame(s).error.empty(),"unknown depth encoding must fail");
 }else if(mode=="compressed"){
  sensor_msgs_msg_dds__CompressedImage_ s{};s.header.frame_id=const_cast<char*>("cam");s.format=const_cast<char*>("rgb8; jpeg compressed bgr8");uint8_t data[]={0xff,0xd8,0xff,0xd9};s.data._buffer=data;s.data._length=4;
  check(pocket::compressed_frame(s).payload.size()==4,"compressed ROS image must be retained");s.data._length=1048577;check(!pocket::compressed_frame(s).error.empty(),"compressed cap must reject before buffer access");
 }
 std::cout<<"PASS "<<mode<<"\n";return 0;
 }catch(const std::exception& e){std::cerr<<e.what()<<"\n";return 1;}}

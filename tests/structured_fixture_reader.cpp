#include "inspector.hpp"
#include <chrono>
#include <iostream>
#include <thread>
int main(int argc,char** argv) {
 if(argc!=3)return 2;
 pocket::Session s;
 std::cout<<s.start(67,"127.0.0.1","127.0.0.1")<<std::endl;
 const std::string kind=argv[1],topic="rt/pocket_test/"+(kind=="lifecycle"?std::string("vector3"):kind);
 std::cout<<s.subscribe(topic,argv[2],false,false)<<std::endl;
 for(int i=0;i<40;i++) {
  std::cout<<s.poll()<<std::endl;
  std::this_thread::sleep_for(std::chrono::milliseconds(100));
 }
 if(kind=="lifecycle") {
  std::cout<<s.subscribe("rt/pocket_test/unsupported","geometry_msgs::msg::dds_::Point_",false,false)<<std::endl;
  std::cout<<s.poll()<<std::endl;
  std::cout<<s.subscribe("rt/pocket_test/invalid topic",argv[2],false,false)<<std::endl;
  std::cout<<s.poll()<<std::endl;std::cout<<s.poll()<<std::endl;
  std::cout<<s.subscribe(topic,argv[2],false,false)<<std::endl;
  std::cout<<s.poll()<<std::endl;
  for(int i=0;i<15;i++){std::this_thread::sleep_for(std::chrono::milliseconds(100));std::cout<<s.poll()<<std::endl;}
 }
 s.stop();std::cout<<s.poll()<<std::endl;
}

#include "sensors.hpp"
#include "robot_description.hpp"
#include <iostream>
#include <fstream>
#include <thread>
#include <chrono>
int main(int argc,char** argv){
 if(argc!=3)return 2;
 pocket::SensorSession map;pocket::RobotDescriptionSession tf;
 auto result=map.start(81,"127.0.0.1","","rt/location/map","nav_msgs::msg::dds_::OccupancyGrid_","lan","jazzy",true,true);if(result.find("\"ok\":true")==std::string::npos){std::cerr<<result;return 3;}
 result=tf.start(81,"127.0.0.1","","",false,false,"lan","jazzy",true);if(result.find("\"ok\":true")==std::string::npos)return 4;
 result=tf.start_tf("rt/location/tf","",true,true);if(result.find("\"ok\":true")==std::string::npos)return 5;
 bool haveMap=false,haveTf=false;for(int i=0;i<300;i++){
  auto frame=map.poll();if(!frame.error.empty()){std::cerr<<frame.error;return 6;}if(!frame.payload.empty()&&!haveMap){std::ofstream out(argv[1],std::ios::binary);uint32_t n=static_cast<uint32_t>(frame.metadata.size());for(int k=0;k<4;k++)out.put(static_cast<char>(n>>(8*k)));out<<frame.metadata;out.write(reinterpret_cast<const char*>(frame.payload.data()),frame.payload.size());if(!out.good())return 7;haveMap=true;}
  auto pose=tf.poll_tf();if(pose.find("\"child\":\"base_link\"")!=std::string::npos){std::ofstream out(argv[2]);out<<pose;if(!out.good())return 8;haveTf=true;}
  if(haveMap&&haveTf){map.stop();tf.stop();std::cout<<"ROS map and TF reader PASS\n";return 0;}std::this_thread::sleep_for(std::chrono::milliseconds(20));
 }
 std::cerr<<"Missing ROS map="<<!haveMap<<" tf="<<!haveTf<<"\n";return 9;
}

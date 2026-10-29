#include "sensors.hpp"
#include "inspector.hpp"
#include <iostream>
#include <fstream>
#include <cstdlib>
#include <iomanip>
int main(){pocket::Session telemetry;pocket::SensorSession sensor;
 auto result=telemetry.start(75,"127.0.0.1","");if(result.find("true")==std::string::npos)return 2;
 telemetry.subscribe("rt/v04/numeric","std_msgs::msg::dds_::Float64_",false,false);
 std::string command,mode;
 while(std::getline(std::cin,command)){
  if(command=="quit")break;
  if(command=="poll"){
   auto f=sensor.poll();std::string hex;const char* digits="0123456789abcdef";
   for(size_t i=0;i<std::min<size_t>(32,f.payload.size());i++){hex+=digits[f.payload[i]>>4];hex+=digits[f.payload[i]&15];}
   std::cout<<"{\"sensor\":"<<f.metadata<<",\"bytes\":"<<f.payload.size()<<",\"hex\":\""<<hex<<"\",\"telemetry\":"<<telemetry.poll()<<"}"<<std::endl;
   const char* dir=std::getenv("POCKET_SENSOR_RECORDS");
   if(dir&&!f.payload.empty()){std::ofstream out(std::string(dir)+"/"+mode+".packet",std::ios::binary);uint32_t n=f.metadata.size();for(int i=0;i<4;i++)out.put(static_cast<char>(n>>(8*i)));out<<f.metadata;out.write(reinterpret_cast<const char*>(f.payload.data()),f.payload.size());}
  }else if(command=="stop"){sensor.stop();std::cout<<"{\"ok\":true}"<<std::endl;}
  else {
   mode=command;std::string suffix=mode=="scan"?"scan":mode=="image"?"image":"compressed";
   std::string type=mode=="scan"?"LaserScan_":mode=="image"?"Image_":"CompressedImage_";
   std::cout<<sensor.start(75,"127.0.0.1","","rt/v04/"+suffix,"sensor_msgs::msg::dds_::"+type)<<std::endl;
  }
 }
 sensor.stop();telemetry.stop();return 0;}

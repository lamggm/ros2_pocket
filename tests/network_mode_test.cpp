#include "inspector.hpp"
#include "sensors.hpp"
#include <iostream>
#include <stdexcept>
// The fallback deliberately exercises the old public entry point on RED.
// It is not an accepted emulation of the new mode.
template<class T> auto start(T& s,const std::string& mode,int)->decltype(s.start(84,"127.0.0.1","","jazzy",mode)){return s.start(84,"127.0.0.1","","jazzy",mode);}
template<class T> std::string start(T& s,const std::string&,long){return s.start(84,"127.0.0.1","","jazzy");}
template<class T> auto sensor_start(T& s,const std::string& mode,int)->decltype(s.start(84,"127.0.0.1","","rt/test/scan","sensor_msgs::msg::dds_::LaserScan_",mode)){return s.start(84,"127.0.0.1","","rt/test/scan","sensor_msgs::msg::dds_::LaserScan_",mode);}
template<class T> std::string sensor_start(T& s,const std::string&,long){return s.start(84,"127.0.0.1","","rt/test/scan","sensor_msgs::msg::dds_::LaserScan_");}
int main(){try{
 pocket::Session s;auto result=start(s,"invalid",0);
 if(result.find("\"ok\":false")==std::string::npos)throw std::runtime_error("Unknown network mode was accepted as LAN");
 result=start(s,"vpn",0);
 if(result.find("\"ok\":false")==std::string::npos)throw std::runtime_error("VPN without explicit peer was accepted");
 result=start(s,"lan",0);
 if(result.find("\"ok\":true")==std::string::npos)throw std::runtime_error("Legacy LAN start regressed");
 s.stop();pocket::SensorSession sensor;
 result=sensor_start(sensor,"invalid",0);if(result.find("\"ok\":false")==std::string::npos)throw std::runtime_error("Sensor accepted an unknown mode as LAN");
 result=sensor_start(sensor,"vpn",0);if(result.find("\"ok\":false")==std::string::npos)throw std::runtime_error("VPN sensor without explicit peer was accepted");
 result=sensor_start(sensor,"lan",0);if(result.find("\"ok\":true")==std::string::npos)throw std::runtime_error("Legacy LAN sensor regressed");
 sensor.stop();std::cout<<"Main and sensor network mode validation and legacy LAN passed\n";return 0;
}catch(const std::exception& e){std::cerr<<e.what()<<'\n';return 1;}}

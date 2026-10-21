#include "inspector.hpp"
#include <iostream>
#include <string>

// Interactive real Session in a separate process: no in-process DDS delivery.
int main() {
 pocket::Session session;
 std::cout << session.start(67,"127.0.0.1","127.0.0.1") << std::endl;
 std::cout << session.subscribe("rt/pocket_test/wire_bounds","nav_msgs::msg::dds_::Odometry_",false,false) << std::endl;
 std::string command;
 while(std::getline(std::cin,command)) {
  if(command=="string")
   std::cout << session.subscribe("rt/pocket_test/wire_bounds_string","std_msgs::msg::dds_::String_",false,false) << std::endl;
  else if(command=="poll") std::cout << session.poll() << std::endl;
  else return 2;
 }
 session.stop();
}

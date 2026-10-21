#include "inspector.hpp"
#include <chrono>
#include <thread>
#include <iostream>
int main(int argc,char** argv) {
 if (argc < 5) {std::cerr << "domain address topic seconds [peer]\n";return 2;}
 pocket::Session s;
 std::cout << s.start(std::stoi(argv[1]),argv[2],argc>5?argv[5]:"") << std::endl;
 bool subscribed=false;
 auto until=std::chrono::steady_clock::now()+std::chrono::seconds(std::stoi(argv[4]));
 while (std::chrono::steady_clock::now()<until) {
  auto snapshot=s.poll(); std::cout<<snapshot<<std::endl;
  if (!subscribed && snapshot.find(argv[3])!=std::string::npos) {
   std::cout<<s.subscribe(std::string("rt")+argv[3],"std_msgs::msg::dds_::String_",false,false)<<std::endl;
   subscribed=true;
  }
  std::this_thread::sleep_for(std::chrono::milliseconds(500));
 }
 s.stop();
}

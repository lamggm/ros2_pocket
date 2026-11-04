#include "inspector.hpp"
#include <chrono>
#include <iostream>
#include <string>
#include <thread>
// Keep the rejected pre-format implementation runnable as a behavioral RED.
template<class S> auto begin(S& s,const std::string& format,int)
 -> decltype(s.start(86,"127.0.0.1","",format)) {return s.start(86,"127.0.0.1","",format);}
template<class S> std::string begin(S& s,const std::string&,long) {return s.start(86,"127.0.0.1","");}
int main(int argc,char** argv) {
 if(argc!=2)return 2;
 pocket::Session session;
 const auto started=begin(session,argv[1],0);std::cout<<started<<'\n'<<std::flush;
 if(started.find("\"ok\":true")==std::string::npos){std::cout<<session.poll()<<'\n';return 0;}
 std::cout<<session.subscribe("rt/pocket_test/chatter","std_msgs::msg::dds_::String_",false,false)<<'\n';
 std::cout<<session.watch(1,"rt/pocket_test/value","std_msgs::msg::dds_::Float64_",false,false)<<'\n';
 for(int i=0;i<60;i++){
  std::cout<<session.poll()<<'\n'<<std::flush;
  std::this_thread::sleep_for(std::chrono::milliseconds(200));
 }
 session.stop();
}

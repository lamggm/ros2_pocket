#include "inspector.hpp"
#include <iostream>
#include <sstream>

int main() {
 pocket::Session session;
 std::cout << session.start(71,"127.0.0.1","127.0.0.1") << std::endl;
 std::string line;
 while(std::getline(std::cin,line)) {
  std::istringstream in(line);
  std::string cmd,topic,type;
  int slot=0,reliable=0,transient=0;
  in >> cmd;
  if(cmd=="watch") {
   // Python booleans spell True/False, not the protocol's required 0/1.
   // Never silently apply default QoS after a failed integer extraction.
   if(!(in >> slot >> topic >> type >> reliable >> transient) ||
      (reliable!=0 && reliable!=1) || (transient!=0 && transient!=1))
    std::cout << "{\"ok\":false,\"error\":\"invalid fixture watch command\"}" << std::endl;
   else
    std::cout << session.watch(slot,topic,type,reliable,transient) << std::endl;
  } else if(cmd=="remove") {
   if(!(in >> slot))
    std::cout << "{\"ok\":false,\"error\":\"invalid fixture remove command\"}" << std::endl;
   else
    std::cout << session.remove_watch(slot) << std::endl;
  } else if(cmd=="numeric") {
   uint64_t epoch=0;int count=-1;std::vector<std::pair<int,std::string>> fields;
   bool valid=bool(in>>epoch>>count)&&count>=0&&count<=4;
   for(int i=0;valid&&i<count;i++){std::string path;valid=bool(in>>slot>>path);if(valid)fields.emplace_back(slot,path);}
   if(valid)std::cout<<session.select_numeric(epoch,fields)<<std::endl;
   else std::cout<<"{\"ok\":false,\"error\":\"invalid numeric fixture command\"}"<<std::endl;
  } else if(cmd=="poll") {
   std::cout << session.poll() << std::endl;
  } else if(cmd=="clear_rosout") {
   session.clear_rosout();std::cout << "{\"ok\":true}" << std::endl;
  } else if(cmd=="stop") {
   session.stop();session.stop();std::cout << session.poll() << std::endl;
  } else if(cmd=="exit") {
   break;
  } else {
   std::cout << "{\"ok\":false,\"error\":\"unknown fixture command\"}" << std::endl;
  }
 }
}

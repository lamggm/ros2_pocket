// Host-only protocol adapter to the actual production Session, never a DDS double.
// Built separately: does not change product/JNI/CMake or add a fifth slot.
#include "inspector.hpp"
#include <chrono>
#include <iostream>
#include <sstream>
#include <string>

static long long now_ns() {
 return std::chrono::duration_cast<std::chrono::nanoseconds>(
     std::chrono::steady_clock::now().time_since_epoch()).count();
}
int main(int argc,char** argv) {
 if(argc!=2 || std::string(argv[1])!="67")return 2;
 pocket::Session session;
 uint64_t nonce=0;
 std::cout<<session.start(67,"127.0.0.1","127.0.0.1")<<std::endl;
 std::string line;
 while(std::getline(std::cin,line)) {
  std::istringstream in(line);std::string command,topic,stage,extra;
  int slot=-1,reliable=-1,transient=-1;in>>command;
  if(command=="watch") {
   if(!(in>>slot>>topic>>reliable>>transient) || (in>>extra) ||
      (reliable!=0&&reliable!=1) || (transient!=0&&transient!=1))
    std::cout<<"{\"ok\":false,\"error\":\"protocol\"}"<<std::endl;
   else std::cout<<session.watch(slot,topic,"std_msgs::msg::dds_::String_",reliable,transient)<<std::endl;
  }else if(command=="remove") {
   if(!(in>>slot)||(in>>extra))std::cout<<"{\"ok\":false}"<<std::endl;
   else std::cout<<session.remove_watch(slot)<<std::endl;
  }else if(command=="arm") {
   if(!(in>>slot>>stage)||(in>>extra))std::cout<<"{\"ok\":false}"<<std::endl;
   else {
    const auto id=session.reader_identity_for_test(slot);
    const auto handle=session.arm_reader_failure_for_test(slot,stage,id.session,id.generation,id.request,1,++nonce);
    std::cout<<"{\"ok\":"<<(handle?"true":"false")<<",\"session\":"<<id.session
      <<",\"generation\":"<<id.generation<<",\"request\":"<<id.request<<",\"nonce\":"<<nonce<<"}"<<std::endl;
   }
  }else if(command=="receipt") {
   const auto receipt=session.reader_failure_receipt_for_test();
   std::cout<<(receipt.empty()?"null":receipt)<<std::endl;
  }else if(command=="poll") {
   const auto captured=now_ns();const auto snapshot=session.poll();
   std::cout<<"{\"capturedNs\":"<<captured<<",\"exportedNs\":"<<now_ns()<<",\"snapshot\":"<<snapshot<<"}"<<std::endl;
  }else if(command=="clear_rosout") {
   session.clear_rosout();std::cout<<"{\"ok\":true}"<<std::endl;
  }else if(command=="stop") {
   session.stop();session.stop();std::cout<<session.poll()<<std::endl;
  }else if(command=="exit")break;
  else std::cout<<"{\"ok\":false,\"error\":\"unknown protocol command\"}"<<std::endl;
 }
}

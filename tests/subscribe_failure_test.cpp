#include "inspector.hpp"
#include <chrono>
#include <iostream>
#include <thread>

namespace {
const std::string type="std_msgs::msg::dds_::String_";
const std::string topic="rt/pocket_test/chatter";
bool ok(const std::string& result) { return result.find("\"ok\":true")!=std::string::npos; }
bool live(pocket::Session& session) {
 const auto until=std::chrono::steady_clock::now()+std::chrono::seconds(15);
 while(std::chrono::steady_clock::now()<until) {
  const auto snapshot=session.poll();
  if(snapshot.find("\"subscription\":\"/pocket_test/chatter\"")!=std::string::npos &&
     snapshot.find("\"count\":0,")==std::string::npos) {
   std::cout<<"LIVE "<<snapshot<<std::endl;return true;
  }
  std::this_thread::sleep_for(std::chrono::milliseconds(100));
 }
 std::cerr<<"FAIL no live isolated fixture sample: "<<session.poll()<<std::endl;return false;
}
}
int main(int argc,char** argv) {
 if(argc!=2) {std::cerr<<"usage: subscribe_failure_test LAN_IPv4 (existing domain-67 fixture required)\n";return 2;}
 pocket::Session session;
 const auto started=session.start(67,argv[1],"");
 if(!ok(started)) {std::cerr<<"FAIL start: "<<started<<std::endl;return 1;}
 if(!ok(session.subscribe(topic,type,false,false)) || !live(session))return 1;
 // Pass app prefix validation, then let the real DDS topic-name validator reject a space.
 const auto failed=session.subscribe("rt/pocket_test/invalid topic",type,false,false);
 std::cout<<"SUBSCRIBE_FAILURE "<<failed<<std::endl;
 if(ok(failed) || failed.find("Falha ao criar topico:")==std::string::npos) {
  std::cerr<<"FAIL expected actual DDS topic-creation failure\n";return 1;
 }
 bool passed=true;
 for(int i=0;i<3;i++) {
  const auto snapshot=session.poll();std::cout<<"AFTER_FAILURE "<<snapshot<<std::endl;
  if(snapshot.find("\"subscription\":\"\"")==std::string::npos ||
     snapshot.find("\"last\":\"\"")==std::string::npos ||
     snapshot.find("\"count\":0,")==std::string::npos ||
     snapshot.find("\"age\":-1,")==std::string::npos ||
     snapshot.find("\"matched\":0,")==std::string::npos) {
   std::cerr<<"FAIL inactive subscription retains telemetry\n";passed=false;
  }
  if(snapshot.find("\"error\":\"Falha ao criar topico:")==std::string::npos) {
   std::cerr<<"FAIL subscription error must persist in every poll after failed DDS creation\n";passed=false;
  }
  std::this_thread::sleep_for(std::chrono::milliseconds(100));
 }
 if(!ok(session.subscribe(topic,type,false,false)) || !live(session))return 1;
 if(session.poll().find("\"error\":\"\"")==std::string::npos) {
  std::cerr<<"FAIL successful subscription must clear error\n";passed=false;
 }
 const auto failedAgain=session.subscribe("rt/pocket_test/invalid topic",type,false,false);
 if(ok(failedAgain))return 1;
 session.stop();
 if(session.poll().find("Falha ao criar topico:")!=std::string::npos) {
  std::cerr<<"FAIL session reset must clear error\n";passed=false;
 }
 if(!ok(session.start(67,argv[1],"")))return 1;
 const auto reset=session.poll();std::cout<<"AFTER_RESET "<<reset<<std::endl;
 if(reset.find("\"error\":\"\"")==std::string::npos) {
  std::cerr<<"FAIL new session must not retain subscription error\n";passed=false;
 }
 if(!passed)return 1;
 std::cout<<"PASS failed DDS topic creation clears telemetry, persists error across polls, and recovers/reset cleanly\n";
}

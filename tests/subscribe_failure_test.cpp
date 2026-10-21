#include "inspector.hpp"
#include "PocketTypes.h"
#include <dds/dds.h>
#include <atomic>
#include <cctype>
#include <stdexcept>
#include <memory>
#include <chrono>
#include <iostream>
#include <thread>

namespace {
// Optional self-contained loopback bench on isolated domain 71.
struct LoopbackPublisher {
 dds_entity_t participant=0,writer=0;std::atomic<bool> running{true};std::thread producer;
 explicit LoopbackPublisher(int domain){
  participant=dds_create_participant(domain,nullptr,nullptr);
  auto topic=dds_create_topic(participant,&std_msgs_msg_dds__String__desc,"rt/pocket_test/chatter",nullptr,nullptr);
  writer=dds_create_writer(participant,topic,nullptr,nullptr);
  if(writer<=0)throw std::runtime_error("loopback fixture creation failed");
  producer=std::thread([this]{while(running){std_msgs_msg_dds__String_ value{};value.data=const_cast<char*>("loopback regression");dds_write(writer,&value);std::this_thread::sleep_for(std::chrono::milliseconds(100));}});
 }
 ~LoopbackPublisher(){running=false;if(producer.joinable())producer.join();if(participant>0)dds_delete(participant);}
};
const std::string type="std_msgs::msg::dds_::String_";
const std::string topic="rt/pocket_test/chatter";
bool ok(const std::string& result) { return result.find("\"ok\":true")!=std::string::npos; }
uint64_t root_number(const std::string& json,const std::string& wanted,int wanted_depth=1) {
 int depth=0;
 for(size_t i=0;i<json.size();) {
  if(json[i]=='{'||json[i]=='['){++depth;++i;continue;}
  if(json[i]=='}'||json[i]==']'){--depth;++i;continue;}
  if(json[i]!='"'){++i;continue;}
  size_t end=i+1;for(;end<json.size();++end){if(json[end]=='\\'){++end;continue;}if(json[end]=='"')break;}
  if(end>=json.size())break;
  std::string key=json.substr(i+1,end-i-1);size_t colon=end+1;while(colon<json.size()&&std::isspace(static_cast<unsigned char>(json[colon])))++colon;
  if(depth==wanted_depth&&key==wanted&&colon<json.size()&&json[colon]==':'){size_t value=colon+1;while(value<json.size()&&std::isspace(static_cast<unsigned char>(json[value])))++value;return std::strtoull(json.c_str()+value,nullptr,10);}
  i=end+1;
 }
 throw std::runtime_error("missing JSON numeric field: "+wanted);
}
bool live(pocket::Session& session,bool expect_empty_rosout=false) {
 const auto until=std::chrono::steady_clock::now()+std::chrono::seconds(15);
 while(std::chrono::steady_clock::now()<until) {
  const auto snapshot=session.poll();
  const auto rosout=snapshot.find("\"rosout\":{");
  if(snapshot.find("\"subscription\":\"/pocket_test/chatter\"")!=std::string::npos &&
     root_number(snapshot,"count")>0&&(!expect_empty_rosout||(rosout!=std::string::npos&&root_number(snapshot.substr(rosout+9),"count")==0))) {
   std::cout<<"LIVE "<<snapshot<<std::endl;return true;
  }
  std::this_thread::sleep_for(std::chrono::milliseconds(100));
 }
 std::cerr<<"FAIL no live isolated fixture sample: "<<session.poll()<<std::endl;return false;
}
}
int main(int argc,char** argv) {
 if(argc!=2) {std::cerr<<"usage: subscribe_failure_test LAN_IPv4 | --loopback (otherwise existing domain-67 fixture required)\n";return 2;}
 pocket::Session session;
 const bool loopback=std::string(argv[1])=="--loopback";
 const int test_domain=loopback?71:67;
 const auto started=session.start(test_domain,loopback?"127.0.0.1":argv[1],"");
 if(!ok(started)) {std::cerr<<"FAIL start: "<<started<<std::endl;return 1;}
 std::unique_ptr<LoopbackPublisher> fixture;if(loopback)fixture=std::make_unique<LoopbackPublisher>(test_domain);
 if(!ok(session.subscribe(topic,type,false,false)) || !live(session,loopback))return 1;
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
     root_number(snapshot,"count")!=0 ||
     snapshot.find("\"age\":-1,")==std::string::npos ||
     snapshot.find("\"matched\":0,")==std::string::npos) {
   std::cerr<<"FAIL inactive subscription retains telemetry\n";passed=false;
  }
  if(snapshot.find("\"error\":\"Falha ao criar topico:")==std::string::npos) {
   std::cerr<<"FAIL subscription error must persist in every poll after failed DDS creation\n";passed=false;
  }
  std::this_thread::sleep_for(std::chrono::milliseconds(100));
 }
 if(!ok(session.subscribe(topic,type,false,false)) || !live(session,loopback))return 1;
 if(session.poll().find("\"error\":\"\"")==std::string::npos) {
  std::cerr<<"FAIL successful subscription must clear error\n";passed=false;
 }
 const auto failedAgain=session.subscribe("rt/pocket_test/invalid topic",type,false,false);
 if(ok(failedAgain))return 1;
 fixture.reset();
 session.stop();
 if(session.poll().find("Falha ao criar topico:")!=std::string::npos) {
  std::cerr<<"FAIL session reset must clear error\n";passed=false;
 }
 if(!ok(session.start(test_domain,loopback?"127.0.0.1":argv[1],"")))return 1;
 const auto reset=session.poll();std::cout<<"AFTER_RESET "<<reset<<std::endl;
 if(reset.find("\"error\":\"\"")==std::string::npos) {
  std::cerr<<"FAIL new session must not retain subscription error\n";passed=false;
 }
 if(!passed)return 1;
 std::cout<<"PASS failed DDS topic creation clears telemetry, persists error across polls, and recovers/reset cleanly\n";
}

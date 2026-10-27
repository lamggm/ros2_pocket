#include "inspector.hpp"
#include "PocketTypes.h"
#include <dds/dds.h>
#include <chrono>
#include <stdexcept>
#include <string>
#include <thread>
#include <iostream>
int main() {
  pocket::Session s;
  uint64_t nonce=0;
  pocket::Session::ReaderArmHandle owned{};
  auto arm=[&](int slot,const std::string& stage) {
    const auto id=s.reader_identity_for_test(slot);
    auto handle=s.arm_reader_failure_for_test(slot,stage,id.session,id.generation,id.request,1,++nonce);
    if(handle)owned=handle;
    return handle;
  };
  if (arm(0, "topic")) throw std::runtime_error("armed without live reader");
  if (s.start(71, "127.0.0.1", "").find("\"ok\":true") == std::string::npos) throw std::runtime_error("start failed");
  const auto pub=dds_create_participant(71,nullptr,nullptr);
  const auto topic=dds_create_topic(pub,&std_msgs_msg_dds__String__desc,"rt/pocket_hook/live",nullptr,nullptr);
  const auto writer=dds_create_writer(pub,topic,nullptr,nullptr);
  if(writer<=0) throw std::runtime_error("fixture writer failed");
  if (s.subscribe("rt/pocket_hook/live", "std_msgs::msg::dds_::String_", false, false).find("\"ok\":true") == std::string::npos) throw std::runtime_error("reader setup failed");
  std_msgs_msg_dds__String_ value{}; value.data=const_cast<char*>("live");
  bool received=false;
  for(int i=0;i<50&&!received;i++){dds_write(writer,&value);std::this_thread::sleep_for(std::chrono::milliseconds(100));received=s.poll().find("\"last\":\"live\"") != std::string::npos;}
  if(!received) throw std::runtime_error("reader did not receive fixture sample");
  // Every next request attempt consumes the arm, including prevalidation exits.
  for(int rejected=0;rejected<4;rejected++) {
    if(!arm(0,"reader")) throw std::runtime_error("prevalidation arm failed");
    const auto response=s.watch(rejected==0?-1:0,
        rejected==2?"not-rt":(rejected==3?std::string(1025,'x'):"rt/pocket_hook/live"),
        rejected==1?"unsupported":"std_msgs::msg::dds_::String_",false,false);
    if(response.find("\"ok\":false")==std::string::npos) throw std::runtime_error("rejection expected");
    if(s.subscribe("rt/pocket_hook/live","std_msgs::msg::dds_::String_",false,false).find("\"ok\":true")==std::string::npos) throw std::runtime_error("rejected attempt poisoned next valid request: "+std::to_string(rejected));
    received=false;
    for(int i=0;i<50&&!received;i++){dds_write(writer,&value);std::this_thread::sleep_for(std::chrono::milliseconds(50));received=s.poll().find("\"last\":\"live\"")!=std::string::npos;}
    if(!received) throw std::runtime_error("recovery sample missing");
  }
  const auto identity=s.reader_identity_for_test(0);
  for(int wrong=0;wrong<3;wrong++) for(int offset:{-1,1}) {
    if(s.arm_reader_failure_for_test(0,"reader",identity.session+(wrong==0?offset:0),identity.generation+(wrong==1?offset:0),identity.request+(wrong==2?offset:0),1,++nonce)) throw std::runtime_error("stale/future identity accepted");
  }
  if(arm(-1,"reader")||arm(4,"reader")||arm(0,"unsupported")) throw std::runtime_error("invalid arm accepted");
  if(!arm(0,"reader")) throw std::runtime_error("wrong identities poisoned future arm");
  const auto incumbent=owned;
  if(s.diagnostic_state().find("\"armed\":true")==std::string::npos)throw std::runtime_error("authoritative arm state missing");
  if(arm(0,"reader"))throw std::runtime_error("duplicate acquired ownership");
  pocket::Session::ReaderArmHandle rejected{};
  if(s.cancel_reader_failure_for_test(rejected))throw std::runtime_error("rejected acquisition cancelled incumbent");
  auto wrong=incumbent;wrong.slot=1;
  if(s.cancel_reader_failure_for_test(wrong))throw std::runtime_error("wrong slot cancelled incumbent");
  wrong=incumbent;wrong.lease++;
  if(s.cancel_reader_failure_for_test(wrong))throw std::runtime_error("wrong lease cancelled incumbent");
  wrong=incumbent;wrong.nonce++;
  if(s.cancel_reader_failure_for_test(wrong))throw std::runtime_error("wrong nonce cancelled incumbent");
  if(!s.cancel_reader_failure_for_test(incumbent))throw std::runtime_error("owned cancellation failed");
  if(s.arm_reader_failure_for_test(0,"reader",identity.session,identity.generation,identity.request,incumbent.lease,incumbent.nonce))throw std::runtime_error("reused nonce acquired replacement");
  if(!arm(0,"reader"))throw std::runtime_error("replacement acquisition failed");
  if(s.cancel_reader_failure_for_test(incumbent))throw std::runtime_error("old cleanup cancelled replacement");
  if(!s.cancel_reader_failure_for_test(owned))throw std::runtime_error("replacement cancelled by old cleanup");
  if(s.cancel_reader_failure_for_test(owned))throw std::runtime_error("cancel not one shot");
  auto live=[&](int slot) {
    if(s.watch(slot,"rt/pocket_hook/live","std_msgs::msg::dds_::String_",false,false).find("\"ok\":true")==std::string::npos) throw std::runtime_error("live setup failed");
    for(int i=0;i<2;i++) { dds_write(writer,&value);std::this_thread::sleep_for(std::chrono::milliseconds(50));s.poll(); }
  };
  live(1);
  if(!arm(0,"reader")) throw std::runtime_error("remove arm failed");
  s.remove_watch(0);
  if(!arm(1,"reader")) throw std::runtime_error("target removal did not cancel pending hook");
  // A different slot removal preserves the target arm; a different watch request cancels it.
  s.remove_watch(2);
  if(s.watch(1,"rt/pocket_hook/other","std_msgs::msg::dds_::String_",false,false).find("Falha ao assinar:")==std::string::npos) throw std::runtime_error("other removal lost target hook");
  live(0);
  if(!arm(0,"reader")) throw std::runtime_error("cross-slot arm failed");
  live(1);live(0);
  if(!arm(0,"reader")) throw std::runtime_error("topic failure arm failed");
  if(s.subscribe("rt/invalid topic","std_msgs::msg::dds_::String_",false,false).find("Falha ao criar topico:")==std::string::npos) throw std::runtime_error("topic-before-reader failure missing");
  live(0);
  for(const std::string stage:{"topic","reader"}) {
    const auto before=s.poll();
    const auto id=s.reader_identity_for_test(0);
    if(!arm(0,stage)) throw std::runtime_error("stage arm rejected");
    if(arm(0,"reader")) throw std::runtime_error("duplicate arm accepted");
    const auto failed=s.subscribe(stage=="topic"?"rt/pocket_hook/next":"rt/pocket_hook/reader_fail","std_msgs::msg::dds_::String_",false,false);
    if(failed.find(stage=="topic"?"Falha ao criar topico:":"Falha ao assinar:")==std::string::npos) throw std::runtime_error("actual API failure missing");
    if(s.diagnostic_state().find("\"armed\":false")==std::string::npos)throw std::runtime_error("consumed arm still pending");
    if(s.reader_failure_receipt_for_test().find("\"nonce\":"+std::to_string(owned.nonce))==std::string::npos)throw std::runtime_error("receipt ownership missing");
    for(int poll=0;poll<3;poll++) {
      std::this_thread::sleep_for(std::chrono::milliseconds(30));
      std::cout<<"{\"kind\":\"failure\",\"stage\":\""<<stage<<"\",\"before\":"<<before
               <<",\"request\":"<<id.request<<",\"receipt\":"<<s.reader_failure_receipt_for_test()
               <<",\"snapshot\":"<<s.poll()<<"}\n";
    }
    if(s.subscribe("rt/pocket_hook/live","std_msgs::msg::dds_::String_",false,false).find("\"ok\":true")==std::string::npos) throw std::runtime_error("recovery subscribe failed");
    for(int sequence=1;sequence<=2;sequence++) {
      const auto text="recovered-"+stage+"-"+std::to_string(sequence);
      value.data=const_cast<char*>(text.c_str());
      std::string snapshot;
      bool fresh=false;
      for(int i=0;i<50&&!fresh;i++) {
        if(dds_write(writer,&value)<0) throw std::runtime_error("fixture write failed");
        std::this_thread::sleep_for(std::chrono::milliseconds(50));snapshot=s.poll();
        fresh=snapshot.find("\"last\":\""+text+"\"")!=std::string::npos;
      }
      if(!fresh) throw std::runtime_error("new recovery sequence missing");
      std::cout<<"{\"kind\":\"recovery\",\"stage\":\""<<stage<<"\",\"sequence\":\""<<text
               <<"\",\"receipt\":\""<<s.reader_failure_receipt_for_test()<<"\",\"snapshot\":"<<snapshot<<"}\n";
    }
    value.data=const_cast<char*>("live");
  }
  // A completed receipt must not masquerade as a later successful remove.
  const auto stale=s.reader_identity_for_test(0);
  live(0);
  if(s.arm_reader_failure_for_test(0,"reader",stale.session,stale.generation,stale.request,1,++nonce)) throw std::runtime_error("actual replaced-reader identity accepted");
  if(!arm(0,"reader")) throw std::runtime_error("cleanup arm failed");
  s.subscribe("rt/pocket_hook/cleanup","std_msgs::msg::dds_::String_",false,false);
  s.remove_watch(0);
  if(!s.reader_failure_receipt_for_test().empty()) throw std::runtime_error("successful removal kept stale receipt");
  dds_delete(pub);
  s.stop();
  if(s.subscribe("rt/pocket_hook/live","std_msgs::msg::dds_::String_",false,false).find("Inicie")==std::string::npos) throw std::runtime_error("missing participant not rejected");
  if(s.start(71,"127.0.0.1","").find("\"ok\":true")==std::string::npos || s.subscribe("rt/pocket_hook/live","std_msgs::msg::dds_::String_",false,false).find("\"ok\":true")==std::string::npos) throw std::runtime_error("missing participant recovery failed");
  if (!s.reader_failure_receipt_for_test().empty()) throw std::runtime_error("receipt survived stop");
}

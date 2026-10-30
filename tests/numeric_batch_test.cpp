#include "inspector.hpp"
#include <iostream>
#include <stdexcept>
#include <vector>
#include <utility>
#include "PocketTypes.h"
#include <dds/dds.h>
#include <thread>
#include <chrono>
#include <limits>
using Selection=std::vector<std::pair<int,std::string>>;
template<typename T>auto choose(T& s,uint64_t epoch,const Selection& fields,int)->decltype(s.select_numeric(epoch,fields)){return s.select_numeric(epoch,fields);}
template<typename T>std::string choose(T&,uint64_t,const Selection&,long){return "{\"ok\":false,\"error\":\"numeric selection API missing\"}";}
static void check(bool condition,const char* why){if(!condition)throw std::runtime_error(why);}
int main(){
 try{
  pocket::Session session;
  const auto snapshot=session.poll();
  if(snapshot.find("\"numericBatch\":{")==std::string::npos)
   throw std::runtime_error("numericBatch protocol missing from the real Session snapshot");
  check(choose(session,7,{{0,"data"}},0).find("\"ok\":true")!=std::string::npos,"bounded numeric selection cannot be applied");
  const auto configured=session.poll();
  check(configured.find("\"epoch\":7")!=std::string::npos&&configured.find("\"paths\":[\"data\"]")!=std::string::npos,"selection identity/paths not exposed");
  for(const auto& invalid:std::vector<Selection>{{{4,"data"}},{{0,std::string(257,'x')}},{{0,"data"},{0,"data"}},{{0,"data"},{1,"data"},{2,"data"},{3,"data"},{0,"x"}},{{0,"bad\npath"}}}){
   check(choose(session,8,invalid,0).find("\"ok\":false")!=std::string::npos,"invalid selection admitted");
   check(session.poll()==configured,"invalid selection changed prior state");
  }
  check(choose(session,7,{{0,"data"}},0).find("\"ok\":true")!=std::string::npos,"idempotent selection failed");
  check(choose(session,6,{{0,"data"}},0).find("\"ok\":false")!=std::string::npos,"retired selection admitted");
  check(choose(session,7,{{0,"x"}},0).find("\"ok\":false")!=std::string::npos,"epoch reused for different fields");
  check(session.poll()==configured,"rejected request changed configured batch");
  session.stop();check(session.poll().find("\"epoch\":0")!=std::string::npos,"stop retained numeric selection epoch");
  std::cout<<configured<<'\n';
  check(session.start(83,"127.0.0.1","127.0.0.1").find("\"ok\":true")!=std::string::npos,"burst session start");
  check(session.subscribe("rt/numeric_batch/value","std_msgs::msg::dds_::Float64_",false,false).find("\"ok\":true")!=std::string::npos,"burst subscribe");
  check(choose(session,9,{{0,"data"}},0).find("\"ok\":true")!=std::string::npos,"burst selection");
  auto participant=dds_create_participant(83,nullptr,nullptr);check(participant>0,"writer participant");
  auto topic=dds_create_topic(participant,&std_msgs_msg_dds__Float64__desc,"rt/numeric_batch/value",nullptr,nullptr);check(topic>0,"writer topic");
  auto writer=dds_create_writer(participant,topic,nullptr,nullptr);check(writer>0,"writer create");
  bool matched=false;for(int i=0;i<100;i++){dds_publication_matched_status_t state{};dds_get_publication_matched_status(writer,&state);if(state.current_count){matched=true;break;}std::this_thread::sleep_for(std::chrono::milliseconds(20));}check(matched,"writer not matched");
  for(int i=0;i<40;i++){
   std_msgs_msg_dds__Float64_ value{double(i)};if(i==36)value.data=std::numeric_limits<double>::quiet_NaN();if(i==38)value.data=std::numeric_limits<double>::infinity();
   check(dds_write_ts(writer,&value,1000+i)==0,"burst write");
  }
  std::this_thread::sleep_for(std::chrono::milliseconds(50));
  auto burst=session.poll();check(burst.find("\"samples\":[[")!=std::string::npos,"real DDS samples did not reach numeric batch");std::cout<<burst<<'\n';
  std::cout<<session.poll()<<'\n';dds_delete(participant);session.remove_watch(0);std::cout<<session.poll()<<'\n';session.stop();std::cout<<session.poll()<<'\n';
 }catch(const std::exception& error){std::cerr<<error.what()<<'\n';return 1;}
}

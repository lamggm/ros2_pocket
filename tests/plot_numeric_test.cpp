#include "inspector.hpp"
#include "PocketTypes.h"
#include <dds/dds.h>
#include <chrono>
#include <iostream>
#include <thread>
#include <stdexcept>
#include <limits>
static void check(bool b){if(!b)throw std::runtime_error("numeric fixture failed");}
int main(){try{
 pocket::Session s;check(s.start(71,"127.0.0.1","").find("\"ok\":true")!=std::string::npos);
 std::cout<<s.poll()<<'\n';
 check(s.subscribe("rt/plot_numeric/value","std_msgs::msg::dds_::Float64_",false,false).find("\"ok\":true")!=std::string::npos);
 auto p=dds_create_participant(71,nullptr,nullptr);check(p>0);
 auto t=dds_create_topic(p,&std_msgs_msg_dds__Float64__desc,"rt/plot_numeric/value",nullptr,nullptr);check(t>0);
 auto w=dds_create_writer(p,t,nullptr,nullptr);check(w>0);
 for(int i=0;i<100;i++){dds_publication_matched_status_t m{};dds_get_publication_matched_status(w,&m);if(m.current_count)break;std::this_thread::sleep_for(std::chrono::milliseconds(20));}
 for(double value:{42.123456789012345,std::numeric_limits<double>::quiet_NaN(),std::numeric_limits<double>::infinity(),-std::numeric_limits<double>::infinity()}){
  std_msgs_msg_dds__Float64_ v{value};check(dds_write_ts(w,&v,1234567890)==0);
  std::this_thread::sleep_for(std::chrono::milliseconds(50));
  std::cout<<s.poll()<<'\n';std::cout<<s.poll()<<'\n';
 }
 dds_delete(p);
 check(s.subscribe("rt/plot_numeric/integer","std_msgs::msg::dds_::Int64_",false,false).find("\"ok\":true")!=std::string::npos);
 p=dds_create_participant(71,nullptr,nullptr);check(p>0);
 t=dds_create_topic(p,&std_msgs_msg_dds__Int64__desc,"rt/plot_numeric/integer",nullptr,nullptr);check(t>0);
 w=dds_create_writer(p,t,nullptr,nullptr);check(w>0);
 for(int i=0;i<100;i++){dds_publication_matched_status_t m{};dds_get_publication_matched_status(w,&m);if(m.current_count)break;std::this_thread::sleep_for(std::chrono::milliseconds(20));}
 std_msgs_msg_dds__Int64_ integer{9007199254740993LL};check(dds_write(w,&integer)==0);
 std::this_thread::sleep_for(std::chrono::milliseconds(50));std::cout<<s.poll()<<'\n';
 dds_delete(p);
 check(s.subscribe("rt/plot_numeric/invalid topic","std_msgs::msg::dds_::Float64_",false,false).find("\"ok\":false")!=std::string::npos);
 std::cout<<s.poll()<<'\n';s.stop();std::cout<<s.poll()<<'\n';
}catch(const std::exception& e){std::cerr<<e.what()<<'\n';return 1;}}

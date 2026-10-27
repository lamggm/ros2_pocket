#include "inspector.hpp"
#include "PocketTypes.h"
#include <chrono>
#include <cctype>
#include <cstdlib>
#include <dds/dds.h>
#include <iostream>
#include <stdexcept>
#include <string>
#include <thread>

static void check(bool ok,const std::string& why){if(!ok)throw std::runtime_error(why);}
static std::string poll(pocket::Session& s){return s.poll();}
static bool has(const std::string& s,const std::string& value){return s.find(value)!=std::string::npos;}
static uint64_t rosout_number(const std::string& s,const std::string& key){auto p=s.find("\"rosout\":{");check(p!=std::string::npos,"missing rosout object");auto end=s.find(",\"entries\":",p);check(end!=std::string::npos,"missing rosout entries boundary");auto field=s.find("\""+key+"\":",p);check(field!=std::string::npos&&field<end,"missing rosout numeric field "+key);return std::strtoull(s.c_str()+field+key.size()+3,nullptr,10);}
static uint64_t root_number(const std::string& json,const std::string& wanted){int depth=0;for(size_t i=0;i<json.size();){if(json[i]=='{'||json[i]=='['){++depth;++i;continue;}if(json[i]=='}'||json[i]==']'){--depth;++i;continue;}if(json[i]!='"'){++i;continue;}size_t end=i+1;for(;end<json.size();++end){if(json[end]=='\\'){++end;continue;}if(json[end]=='"')break;}if(end>=json.size())break;std::string key=json.substr(i+1,end-i-1);size_t colon=end+1;while(colon<json.size()&&std::isspace(static_cast<unsigned char>(json[colon])))++colon;if(depth==1&&key==wanted&&colon<json.size()&&json[colon]==':'){size_t value=colon+1;while(value<json.size()&&std::isspace(static_cast<unsigned char>(json[value])))++value;return std::strtoull(json.c_str()+value,nullptr,10);}i=end+1;}throw std::runtime_error("missing root numeric field "+wanted);}
static bool topic_supported(const std::string& s,const std::string& wire_type){auto p=s.find("\"wireType\":\""+wire_type+"\"");if(p==std::string::npos)return false;auto end=s.find('}',p),supported=s.find("\"supported\":",p);return end!=std::string::npos&&supported!=std::string::npos&&supported<end&&s.compare(supported+12,5,"false")==0;}
static void fixture(){
 pocket::Session s;auto started=s.start(67,"127.0.0.1","");check(has(started,"\"ok\":true"),"session start failed: "+started);
 check(has(poll(s),"\"rosout\":{\"active\":true"),"dedicated rosout reader is not active");
 check(has(s.subscribe("rt/pocket_test/chatter","std_msgs::msg::dds_::String_",false,false),"\"ok\":true"),"independent telemetry reader failed");
 const auto until=std::chrono::steady_clock::now()+std::chrono::seconds(28);std::string snapshot;
 while(std::chrono::steady_clock::now()<until){snapshot=poll(s);if(has(snapshot,"FINAL_SENTINEL"))break;std::this_thread::sleep_for(std::chrono::milliseconds(100));}
 check(has(snapshot,"FINAL_SENTINEL"),"final real ROS Log sample not received before timeout: "+snapshot.substr(snapshot.find("\"rosout\":"),220));
 check(has(snapshot,"\"subscriptionState\":\"receiving\""),"rosout reader interfered with selected telemetry reader");
 const auto rosout_pos=snapshot.find("\"rosout\":");const auto rosout_debug=rosout_pos==std::string::npos?snapshot:snapshot.substr(rosout_pos,220);
 check(rosout_number(snapshot,"count")==200&&rosout_number(snapshot,"evicted")>0,"rosout retained-ring count/eviction not reported: "+rosout_debug);
 check(rosout_number(snapshot,"bytes")<=256*1024,"rosout retained-ring byte budget exceeded");
 check(topic_supported(snapshot,"rcl_interfaces::msg::dds_::Log_"),"dedicated Log topic must not be advertised as a generic Explorer decoder");
 check(has(snapshot,"\"level\":10")&&has(snapshot,"\"level\":20")&&has(snapshot,"\"level\":30")&&has(snapshot,"\"level\":40")&&has(snapshot,"\"level\":50"),"not all five ROS severities survived the burst");
 check(has(snapshot,"\"sourceSec\":")&&has(snapshot,"\"sourceNanosec\":")&&has(snapshot,"\"intakeAgeMs\":"),"source timestamp and monotonic intake age are not separate");
 check(has(snapshot,"ROSOUT_UTF8_LONG_")&&has(snapshot,"\xf0\x9f\x9a\x80"),"long UTF-8 ROS message missing or corrupted");
 check(has(snapshot,"\"truncated\":true"),"truncated long Log field was not signaled");
 const auto long_marker=snapshot.find("ROSOUT_UTF8_LONG_");const auto long_field=snapshot.rfind("\"message\":\"",long_marker);const auto long_end=snapshot.find('"',long_field+11);check(long_end!=std::string::npos&&long_end-(long_field+11)<=4096,"native rosout string limit not applied to live Jazzy sample");
 check(has(snapshot,"fixture_ação.py")&&has(snapshot,"\"offeredReliability\"")&&has(snapshot,"\"reliable\"")&&has(snapshot,"\"offeredDurability\"")&&has(snapshot,"\"transient_local\""),"writer offer QoS was not observed from real discovery");
 const auto root_count_before=root_number(snapshot,"count");const auto generic_log=s.subscribe("rt/rosout","rcl_interfaces::msg::dds_::Log_",false,false);check(!has(generic_log,"\"ok\":true")&&has(generic_log,"sem decodificador"),"generic Explorer subscription accepted dedicated-only Log type");
 const auto telemetry_until=std::chrono::steady_clock::now()+std::chrono::seconds(3);do{snapshot=poll(s);if(root_number(snapshot,"count")>root_count_before)break;std::this_thread::sleep_for(std::chrono::milliseconds(50));}while(std::chrono::steady_clock::now()<telemetry_until);
 const auto root_end=snapshot.find("\"rosout\":{");const auto root=snapshot.substr(0,root_end);check(has(root,"\"subscription\":\"/pocket_test/chatter\"")&&has(root,"\"readerActive\":true")&&root_number(snapshot,"count")>root_count_before,"rejected generic Log request stopped the previous telemetry reader");
 s.clear_rosout();snapshot=poll(s);
 check(has(snapshot,"\"rosout\":{\"active\":true,\"error\":\"\"")&&rosout_number(snapshot,"count")==0,"clear replayed queued pre-clear samples");
 s.stop();snapshot=poll(s);check(has(snapshot,"\"rosout\":{\"active\":false"),"stop left rosout reader active");
 check(has(s.start(67,"127.0.0.1", ""),"\"ok\":true"),"session did not restart");snapshot=poll(s);
 check(has(snapshot,"\"rosout\":{\"active\":true,\"error\":\"\"")&&rosout_number(snapshot,"count")==0,"restart retained old session logs");s.stop();
 std::cout<<"PASS real Jazzy rosout, all severities, bounded burst/UTF-8, QoS discovery, telemetry independence, clear and lifecycle\n";
}
static void unsupported(){
 pocket::Session s;auto started=s.start(72,"127.0.0.1","127.0.0.1");check(has(started,"\"ok\":true"),"session start failed: "+started);
 auto p=dds_create_participant(72,nullptr,nullptr);check(p>0,"conflict participant failed");auto t=dds_create_topic(p,&std_msgs_msg_dds__String__desc,"rt/rosout",nullptr,nullptr);check(t>0,"conflict String topic failed");auto w=dds_create_writer(p,t,nullptr,nullptr);check(w>0,"wrong-type rosout writer failed");std_msgs_msg_dds__String_ sample{};sample.data=const_cast<char*>("wrong type");check(dds_write(w,&sample)==DDS_RETCODE_OK,"wrong-type rosout sample failed");
 std::string value;const auto until=std::chrono::steady_clock::now()+std::chrono::seconds(8);do{value=poll(s);if(has(value,"Tipo /rosout incompatível"))break;std::this_thread::sleep_for(std::chrono::milliseconds(100));}while(std::chrono::steady_clock::now()<until);
 check(has(value,"\"rosout\":{\"active\":false")&&has(value,"Tipo /rosout incompatível"),"unsupported rosout type was not reported honestly: "+value);
 check(has(value,"\"offeredReliability\":[],\"offeredDurability\":[]"),"same-name String topic must not be reported as a Log wire-type QoS offer");
 s.stop();dds_delete(w);dds_delete(p);std::cout<<"PASS unsupported rosout topic type is explicit and nonfatal to session\n";
}
int main(int argc,char** argv){try{check(argc==2,"expected fixture or unsupported mode");if(std::string(argv[1])=="fixture")fixture();else if(std::string(argv[1])=="unsupported")unsupported();else throw std::runtime_error("unknown mode");}catch(const std::exception& e){std::cerr<<"FAIL "<<e.what()<<'\n';return 1;}return 0;}

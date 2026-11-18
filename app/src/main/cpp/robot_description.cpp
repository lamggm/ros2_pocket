#include "robot_description.hpp"
#include "model.hpp"
#include "PocketTypes.h"
#include "ros_reader_announcement.hpp"
#include <dds/dds.h>
#include <arpa/inet.h>
#include <cstring>
#include <cmath>
#include <sstream>
#include <iomanip>
namespace pocket {
namespace {
bool ipv4_description(const std::string& s){in_addr a{};return inet_pton(AF_INET,s.c_str(),&a)==1;}
std::string fail_description(const std::string& why){return "{\"ok\":false,\"error\":"+json_quote(why)+"}";}
}
struct RobotDescriptionSession::Impl {
 dds_entity_t domain=0,participant=0,topic=0,reader=0;RosReaderAnnouncement announcement;
 dds_entity_t tf_topic[2]{},tf_reader[2]{};
 uint64_t sequence=0;std::string xml,error;
};
RobotDescriptionSession::RobotDescriptionSession():impl(new Impl){}
RobotDescriptionSession::~RobotDescriptionSession(){stop();}
void RobotDescriptionSession::stop(){auto& s=*impl;s.announcement.stop();if(s.participant>0)dds_delete(s.participant);if(s.domain>0)dds_delete(s.domain);impl.reset(new Impl);}
std::string RobotDescriptionSession::start(int domain,const std::string& address,const std::string& peer,const std::string& topic,bool reliable,bool transient,const std::string& mode,const std::string& format,bool observer_only){
 stop();auto& s=*impl;auto reject=[&](const std::string& why){stop();impl->error=why;return fail_description(why);};
 if(!valid_domain(domain)||!ipv4_description(address)||(!peer.empty()&&!ipv4_description(peer))||(observer_only?!topic.empty():topic.rfind("rt/",0)!=0)||topic.size()>512)return reject("Configuração de descrição inválida");
 if((mode!="lan"&&mode!="vpn")||(mode=="vpn"&&peer.empty())||(format!="jazzy"&&format!="humble"))return reject("Modo, peer ou formato de descrição inválido");
 // Separate internal domain: telemetry keeps its 64 KiB wire ceiling unchanged.
 const int internal=2000+domain;const bool vpn=mode=="vpn";
 std::string config="<CycloneDDS><Domain Id=\"any\"><General><Interfaces><NetworkInterface address=\""+address+"\" multicast=\""+(vpn?"false":"true")+"\"/></Interfaces><AllowMulticast>"+(vpn?"false":"spdp")+"</AllowMulticast><DontRoute>"+(vpn?"false":"true")+"</DontRoute></General><Discovery><ExternalDomainId>"+std::to_string(domain)+"</ExternalDomainId><ParticipantIndex>auto</ParticipantIndex><MaxAutoParticipantIndex>30</MaxAutoParticipantIndex><LeaseDuration>5s</LeaseDuration><SPDPInterval>2s</SPDPInterval>";
 if(!peer.empty())config+="<Peers><Peer Address=\""+peer+"\"/></Peers>";
 config+="</Discovery><Internal><MaxSampleSize>520 KiB</MaxSampleSize></Internal></Domain></CycloneDDS>";
 s.domain=dds_create_domain(internal,config.c_str());if(s.domain<0)return reject("Falha no domínio DDS de descrição");s.participant=dds_create_participant(internal,nullptr,nullptr);if(s.participant<0)return reject("Falha no participante DDS de descrição");
 if(!observer_only){s.topic=dds_create_topic(s.participant,&std_msgs_msg_dds__String__desc,topic.c_str(),nullptr,nullptr);if(s.topic<0)return reject("Falha no tópico DDS de descrição");
 auto* q=dds_create_qos();dds_qset_reliability(q,reliable?DDS_RELIABILITY_RELIABLE:DDS_RELIABILITY_BEST_EFFORT,DDS_MSECS(100));dds_qset_durability(q,transient?DDS_DURABILITY_TRANSIENT_LOCAL:DDS_DURABILITY_VOLATILE);dds_qset_history(q,DDS_HISTORY_KEEP_LAST,1);dds_qset_resource_limits(q,1,1,1);s.reader=dds_create_reader(s.participant,s.topic,q,nullptr);dds_delete_qos(q);if(s.reader<0)return reject("Falha no leitor DDS de descrição");
 }
 if(!s.announcement.start(s.participant,"inspector",format)||!s.announcement.update(observer_only?std::vector<dds_entity_t>{}:std::vector<dds_entity_t>{s.reader}))return reject("Falha ao anunciar leitor de descrição ROS");
 return "{\"ok\":true}";
}
void RobotDescriptionSession::stop_tf(){auto& s=*impl;for(int i=0;i<2;i++){if(s.tf_reader[i]>0)dds_delete(s.tf_reader[i]);if(s.tf_topic[i]>0)dds_delete(s.tf_topic[i]);s.tf_reader[i]=s.tf_topic[i]=0;}if(s.participant>0)s.announcement.update(s.reader>0?std::vector<dds_entity_t>{s.reader}:std::vector<dds_entity_t>{});}
std::string RobotDescriptionSession::start_tf(const std::string& dynamic,const std::string& fixed,bool reliable,bool retained){
 stop_tf();auto& s=*impl;if(s.participant<=0||(dynamic.empty()&&fixed.empty())||(!dynamic.empty()&&dynamic==fixed))return fail_description("Configuração TF inválida");std::vector<dds_entity_t> readers;if(s.reader>0)readers.push_back(s.reader);
 for(int i=0;i<2;i++){const auto& name=i?fixed:dynamic;if(name.empty())continue;if(name.rfind("rt/",0)!=0||name.size()>512){stop_tf();return fail_description("Tópico TF inválido");}s.tf_topic[i]=dds_create_topic(s.participant,&tf2_msgs_msg_dds__TFMessage__desc,name.c_str(),nullptr,nullptr);if(s.tf_topic[i]<0){stop_tf();return fail_description("Falha no tópico TF");}auto* q=dds_create_qos();dds_qset_reliability(q,i&&reliable?DDS_RELIABILITY_RELIABLE:DDS_RELIABILITY_BEST_EFFORT,DDS_MSECS(100));dds_qset_durability(q,i&&retained?DDS_DURABILITY_TRANSIENT_LOCAL:DDS_DURABILITY_VOLATILE);dds_qset_history(q,DDS_HISTORY_KEEP_LAST,8);dds_qset_resource_limits(q,64,1,64);s.tf_reader[i]=dds_create_reader(s.participant,s.tf_topic[i],q,nullptr);dds_delete_qos(q);if(s.tf_reader[i]<0){stop_tf();return fail_description("Falha no leitor TF");}readers.push_back(s.tf_reader[i]);}
 if(!s.announcement.update(readers)){stop_tf();return fail_description("Falha no anúncio dos leitores TF");}return "{\"ok\":true}";
}
std::string RobotDescriptionSession::poll_tf(){auto& s=*impl;std::ostringstream rows;rows<<std::setprecision(17);std::string error;int emitted=0;
 for(int i=0;i<2;i++){if(s.tf_reader[i]<=0)continue;void* samples[8]{};dds_sample_info_t info[8]{};int taken=dds_take(s.tf_reader[i],samples,info,8,8);if(taken<0){error="Falha de leitura TF";continue;}try{for(int k=0;k<taken;k++){if(!info[k].valid_data)continue;auto* message=static_cast<tf2_msgs_msg_dds__TFMessage_*>(samples[k]);if(message->transforms._length>256||(message->transforms._length&&!message->transforms._buffer)){error="TF excede 256 transformações por amostra";continue;}
  for(uint32_t v=0;v<message->transforms._length;v++){auto& e=message->transforms._buffer[v];auto& t=e.transform.translation;auto& q=e.transform.rotation;if(emitted>=512||!e.header.frame_id||!e.child_frame_id||strnlen(e.header.frame_id,257)>256||strnlen(e.child_frame_id,257)>256||e.header.stamp.sec<0||e.header.stamp.nanosec>=1000000000||!std::isfinite(t.x)||!std::isfinite(t.y)||!std::isfinite(t.z)||!std::isfinite(q.x)||!std::isfinite(q.y)||!std::isfinite(q.z)||!std::isfinite(q.w)){error="Transformação TF inválida ou fora dos limites";continue;}if(emitted++)rows<<",";rows<<"{\"parent\":"<<json_quote(e.header.frame_id)<<",\"child\":"<<json_quote(e.child_frame_id)<<",\"stamp\":"<<(int64_t(e.header.stamp.sec)*1000000000+e.header.stamp.nanosec)<<",\"static\":"<<(i?"true":"false")<<",\"writer\":"<<json_quote(std::to_string(info[k].publication_handle))<<",\"translation\":["<<t.x<<","<<t.y<<","<<t.z<<"],\"rotation\":["<<q.x<<","<<q.y<<","<<q.z<<","<<q.w<<"]}";}
 }}catch(...){if(taken>0)dds_return_loan(s.tf_reader[i],samples,taken);throw;}if(taken>0)dds_return_loan(s.tf_reader[i],samples,taken);}
 return "{\"running\":"+std::string(s.tf_reader[0]>0||s.tf_reader[1]>0?"true":"false")+",\"error\":"+json_quote(error)+",\"rows\":["+rows.str()+"]}";
}
std::string RobotDescriptionSession::poll(){auto& s=*impl;dds_subscription_matched_status_t matched{};
 if(s.reader>0){void* sample=nullptr;dds_sample_info_t info{};int taken=dds_take(s.reader,&sample,&info,1,1);if(taken<0){s.xml.clear();s.error="Falha de leitura da descrição DDS";}
  if(taken>0){try{if(info.valid_data){++s.sequence;auto* value=static_cast<std_msgs_msg_dds__String_*>(sample);constexpr size_t MAX_XML=512*1024;size_t n=value->data?strnlen(value->data,MAX_XML+1):0;if(n>MAX_XML){s.xml.clear();s.error="Descrição maior que 512 KiB";}else{s.xml.assign(value->data?value->data:"",n);s.error.clear();}}}catch(...){dds_return_loan(s.reader,&sample,taken);throw;}dds_return_loan(s.reader,&sample,taken);}dds_get_subscription_matched_status(s.reader,&matched);
 }
 return "{\"running\":"+std::string(s.reader>0?"true":"false")+",\"sequence\":"+std::to_string(s.sequence)+",\"matched\":"+std::to_string(matched.current_count)+",\"xml\":"+json_quote(s.xml)+",\"error\":"+json_quote(s.error)+"}";
}
}

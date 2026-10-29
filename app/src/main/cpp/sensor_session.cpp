#include "sensors.hpp"
#include "model.hpp"
#include <dds/dds.h>
#include <chrono>
#include <arpa/inet.h>
namespace pocket {
namespace {
bool ipv4(const std::string& s){in_addr address{};return inet_pton(AF_INET,s.c_str(),&address)==1;}
uint64_t now_ns(){return std::chrono::duration_cast<std::chrono::nanoseconds>(std::chrono::steady_clock::now().time_since_epoch()).count();}
std::string fail(const std::string& s){return "{\"ok\":false,\"error\":"+json_quote(s)+"}";}
}
struct SensorSession::Impl {
 dds_entity_t domain=0,participant=0,topic=0,reader=0;
 uint64_t count=0,received=0;int kind=0;std::string error;
};
SensorSession::SensorSession():impl(new Impl){} SensorSession::~SensorSession(){stop();}
void SensorSession::stop(){auto& s=*impl;if(s.participant>0)dds_delete(s.participant);if(s.domain>0)dds_delete(s.domain);impl.reset(new Impl);}
std::string SensorSession::start(int domain,const std::string& address,const std::string& peer,const std::string& topic,const std::string& type){
 stop();auto& s=*impl;
 auto rejected=[&](const std::string& why){stop();impl->error=why;return fail(why);};
 if(!valid_domain(domain)||!ipv4(address)||(!peer.empty()&&!ipv4(peer))||topic.rfind("rt/",0)!=0||topic.size()>512)return rejected("Configuração de sensor inválida");
 const dds_topic_descriptor_t* descriptor=nullptr;
 if(type=="sensor_msgs::msg::dds_::LaserScan_"){descriptor=&sensor_msgs_msg_dds__LaserScan__desc;s.kind=1;}
 else if(type=="sensor_msgs::msg::dds_::Image_"){descriptor=&sensor_msgs_msg_dds__Image__desc;s.kind=2;}
 else if(type=="sensor_msgs::msg::dds_::CompressedImage_"){descriptor=&sensor_msgs_msg_dds__CompressedImage__desc;s.kind=3;}
 else return rejected("Tipo de sensor não suportado");
 // A private Cyclone domain with the same wire domain avoids raising telemetry's
 // immutable 64KiB admission ceiling. No discovery/readers other than this sensor.
 const int internal=1000+domain;
 std::string config="<CycloneDDS><Domain Id=\"any\"><General><Interfaces><NetworkInterface address=\""+address+"\" multicast=\"true\"/></Interfaces><AllowMulticast>spdp</AllowMulticast><DontRoute>true</DontRoute></General><Discovery><ExternalDomainId>"+std::to_string(domain)+"</ExternalDomainId><ParticipantIndex>auto</ParticipantIndex><MaxAutoParticipantIndex>30</MaxAutoParticipantIndex><LeaseDuration>5s</LeaseDuration><SPDPInterval>2s</SPDPInterval>";
 if(!peer.empty())config+="<Peers><Peer Address=\""+peer+"\"/></Peers>";
 config+="</Discovery><Internal><MaxSampleSize>4 MiB</MaxSampleSize></Internal></Domain></CycloneDDS>";
 s.domain=dds_create_domain(internal,config.c_str());if(s.domain<0)return rejected("Falha no domínio visual DDS: "+std::string(dds_strretcode(s.domain)));
 s.participant=dds_create_participant(internal,nullptr,nullptr);if(s.participant<0)return rejected("Falha no participante visual DDS");
 s.topic=dds_create_topic(s.participant,descriptor,topic.c_str(),nullptr,nullptr);if(s.topic<0)return rejected("Falha no tópico visual DDS");
 auto q=dds_create_qos();dds_qset_reliability(q,DDS_RELIABILITY_BEST_EFFORT,0);dds_qset_durability(q,DDS_DURABILITY_VOLATILE);dds_qset_history(q,DDS_HISTORY_KEEP_LAST,1);dds_qset_resource_limits(q,1,1,1);
 s.reader=dds_create_reader(s.participant,s.topic,q,nullptr);dds_delete_qos(q);if(s.reader<0)return rejected("Falha no leitor visual DDS");
 return "{\"ok\":true}";
}
SensorFrame SensorSession::poll(){
 auto& s=*impl;SensorFrame frame;
 if(s.reader>0){
  void* sample=nullptr;dds_sample_info_t info{};const int taken=dds_take(s.reader,&sample,&info,1,1);
  if(taken<0)s.error="Falha de leitura visual DDS";
  if(taken>0){
   try{
    if(info.valid_data){
     if(s.kind==1)frame=scan_frame(*static_cast<sensor_msgs_msg_dds__LaserScan_*>(sample));
     else if(s.kind==2)frame=image_frame(*static_cast<sensor_msgs_msg_dds__Image_*>(sample));
     else frame=compressed_frame(*static_cast<sensor_msgs_msg_dds__CompressedImage_*>(sample));
     s.error=frame.error;
     if(s.error.empty()){++s.count;s.received=now_ns();}
    }
   }catch(...){dds_return_loan(s.reader,&sample,taken);throw;}
   dds_return_loan(s.reader,&sample,taken);
  }
 }
 frame.error=s.error;
 if(frame.metadata.size()<2)frame.metadata="{}";
 frame.metadata.pop_back();
 const auto age=s.received?static_cast<int64_t>((now_ns()-s.received)/1000000):-1;
 frame.metadata+=(frame.metadata.size()>1?",":"")+std::string("\"running\":")+(s.reader>0?"true":"false")+",\"count\":"+std::to_string(s.count)+",\"ageMs\":"+std::to_string(age)+",\"error\":"+json_quote(s.error)+"}";
 return frame;
}
}

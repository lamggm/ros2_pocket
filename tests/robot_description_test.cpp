#include "robot_description.hpp"
#include "inspector.hpp"
#include "PocketTypes.h"
#include <dds/dds.h>
#include <unistd.h>
#include <sys/wait.h>
#include <csignal>
#include <chrono>
#include <thread>
#include <iostream>
#include <stdexcept>
static void check(bool ok,const char* why){if(!ok)throw std::runtime_error(why);}
static void byte(int fd,char c){check(write(fd,&c,1)==1,"fixture pipe write");}
static char byte(int fd){char c=0;check(read(fd,&c,1)==1,"fixture pipe read");return c;}
int main(){int commands[2],events[2];pid_t child=-1;try{
 check(pipe(commands)==0&&pipe(events)==0,"fixture pipes");child=fork();check(child>=0,"fork");
 if(child==0){close(commands[1]);close(events[0]);try{
  auto domain=dds_create_domain(78,"<CycloneDDS><Domain><General><Interfaces><NetworkInterface address='127.0.0.1'/></Interfaces><AllowMulticast>spdp</AllowMulticast></General><Discovery><ParticipantIndex>auto</ParticipantIndex></Discovery><Internal><MaxSampleSize>1 MiB</MaxSampleSize></Internal></Domain></CycloneDDS>");check(domain>0,"publisher domain");auto p=dds_create_participant(78,nullptr,nullptr);check(p>0,"publisher participant");auto topic=dds_create_topic(p,&std_msgs_msg_dds__String__desc,"rt/test_robot/robot_description",nullptr,nullptr);auto q=dds_create_qos();dds_qset_reliability(q,DDS_RELIABILITY_RELIABLE,DDS_MSECS(100));dds_qset_durability(q,DDS_DURABILITY_TRANSIENT_LOCAL);dds_qset_history(q,DDS_HISTORY_KEEP_LAST,1);auto w=dds_create_writer(p,topic,q,nullptr);dds_delete_qos(q);check(w>0,"publisher writer");
  std::string xml="<robot name='received_robot'><link name='base'><visual><geometry><box size='1 2 3'/></geometry></visual></link><!--"+std::string(100000,'x')+"--></robot>";std_msgs_msg_dds__String_ msg{};msg.data=xml.data();check(dds_write(w,&msg)==0,"publish historical XML");byte(events[1],'A');
  for(;;){char c=byte(commands[0]);if(c=='Q')break;xml=c=='B'?std::string(512*1024+1,'x'):"<robot name='recovered_robot'><link name='base'/></robot>";msg.data=xml.data();check(dds_write(w,&msg)==0,"publish update");byte(events[1],c);}
  dds_delete(p);dds_delete(domain);_exit(0);
 }catch(const std::exception& e){std::cerr<<e.what()<<std::endl;_exit(2);}}
 close(commands[0]);close(events[1]);check(byte(events[0])=='A',"published before reader starts");
 pocket::Session telemetry;pocket::RobotDescriptionSession description;check(telemetry.start(78,"127.0.0.1","").find("\"ok\":true")!=std::string::npos,"telemetry start");
 check(description.start(78,"127.0.0.1","","rt/test_robot/robot_description",true,true).find("\"ok\":true")!=std::string::npos,"description must start in independent bounded domain");
 auto wait=[&](const std::string& needle){for(int i=0;i<120;i++){auto value=description.poll();if(value.find(needle)!=std::string::npos)return value;std::this_thread::sleep_for(std::chrono::milliseconds(50));}throw std::runtime_error("DDS description condition timed out: "+needle);};
 auto retained=wait("received_robot");check(retained.size()>100000,"historical >64 KiB XML must not be truncated");check(retained.find("\"matched\":1")!=std::string::npos,"real matched publisher");
 check(telemetry.poll().find("\"running\":true")!=std::string::npos,"independent telemetry survives");byte(commands[1],'B');check(byte(events[0])=='B',"oversized published");auto rejected=wait("512 KiB");check(rejected.find("\"xml\":\"\"")!=std::string::npos,"oversized update must clear retained XML");
 byte(commands[1],'C');check(byte(events[0])=='C',"recovery published");wait("recovered_robot");description.stop();check(description.poll().find("recovered_robot")==std::string::npos,"stop clears model");check(description.start(78,"127.0.0.1","","rt/test_robot/robot_description",true,true,"vpn").find("false")!=std::string::npos,"VPN requires peer");
 byte(commands[1],'Q');int status=0;check(waitpid(child,&status,0)==child&&WIFEXITED(status)&&WEXITSTATUS(status)==0,"publisher exits cleanly");child=-1;telemetry.stop();std::cout<<"PASS: separate-process retained DDS String >64KiB, oversized rejection, recovery, independent telemetry and teardown\n";return 0;
 }catch(const std::exception& e){if(child>0){kill(child,SIGKILL);waitpid(child,nullptr,0);}std::cerr<<e.what()<<"\n";return 1;}}

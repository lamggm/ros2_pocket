#pragma once
#include <dds/dds.h>
#include <string>
#include <vector>
namespace pocket {
// ROS discovery metadata only: never an application/data/control publisher.
class RosReaderAnnouncement {
 dds_entity_t participant_=0,topic_=0,writer_=0;
 std::string name_,format_;
 public:
 RosReaderAnnouncement()=default;
 RosReaderAnnouncement(const RosReaderAnnouncement&)=delete;
 RosReaderAnnouncement& operator=(const RosReaderAnnouncement&)=delete;
 ~RosReaderAnnouncement(){stop();}
 bool start(dds_entity_t participant,const std::string& role,const std::string& format="jazzy");
 bool update(const std::vector<dds_entity_t>& readers);
 void stop();
};
}

#pragma once
#include "PocketTypes.h"
#include <memory>
#include <string>
#include <vector>
#include <cstdint>
namespace pocket {
struct SensorFrame { std::string metadata="{}"; std::vector<uint8_t> payload; std::string error; };
SensorFrame scan_frame(const sensor_msgs_msg_dds__LaserScan_& scan);
SensorFrame image_frame(const sensor_msgs_msg_dds__Image_& image);
SensorFrame compressed_frame(const sensor_msgs_msg_dds__CompressedImage_& image);
class SensorSession {
 struct Impl; std::unique_ptr<Impl> impl;
 public:
 SensorSession(); ~SensorSession();
 std::string start(int domain,const std::string& address,const std::string& peer,const std::string& topic,const std::string& type);
 SensorFrame poll(); void stop();
};
}

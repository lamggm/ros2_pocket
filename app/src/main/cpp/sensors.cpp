#include "sensors.hpp"
#include "model.hpp"
#include <cmath>
#include <cstring>
#include <algorithm>
#include <limits>
namespace pocket {
namespace {
SensorFrame rejected(const char* reason){SensorFrame f;f.error=reason;return f;}
std::string frame_header(const std_msgs_msg_dds__Header_& h){
 auto frame=RosoutBuffer::bounded_field(h.frame_id,256);
 if(frame.size()>256){frame.resize(256);while(!frame.empty()&&(static_cast<unsigned char>(frame.back())&0x80))frame.pop_back();}
 return "\"frameId\":"+json_quote(frame)+",\"stampSec\":"+std::to_string(h.stamp.sec)+",\"stampNanosec\":"+std::to_string(h.stamp.nanosec);
}
void append_float(std::vector<uint8_t>& out,float value){uint32_t n;std::memcpy(&n,&value,4);for(int i=0;i<4;i++)out.push_back(static_cast<uint8_t>(n>>(8*i)));}
}
SensorFrame scan_frame(const sensor_msgs_msg_dds__LaserScan_& s){
 const auto n=s.ranges._length;
 if(n==0||n>8192||!s.ranges._buffer||s.intensities._length>8192)return rejected("LaserScan excede limite ou não tem ranges");
 if(!std::isfinite(s.angle_min)||!std::isfinite(s.angle_max)||!std::isfinite(s.angle_increment)||s.angle_increment==0||!std::isfinite(s.range_min)||!std::isfinite(s.range_max)||s.range_min<0||s.range_max<=s.range_min)return rejected("Metadados LaserScan inválidos");
 const double last=double(s.angle_min)+double(n-1)*s.angle_increment;
 if(!std::isfinite(last)||std::abs(last-s.angle_max)>std::max(.01,2*std::abs(double(s.angle_increment))))return rejected("Ângulos e quantidade de ranges inconsistentes");
 SensorFrame f;f.payload.reserve(std::min<uint32_t>(n,4096)*8);
 const uint32_t stride=(n+4095)/4096;
 for(uint32_t i=0;i<n;i+=stride){
  const float r=s.ranges._buffer[i];if(!std::isfinite(r)||r<s.range_min||r>s.range_max)continue;
  const double angle=double(s.angle_min)+double(i)*s.angle_increment;
  append_float(f.payload,static_cast<float>(r*std::cos(angle)));append_float(f.payload,static_cast<float>(r*std::sin(angle)));
 }
 f.metadata="{\"kind\":\"scan\","+frame_header(s.header)+",\"rangeMax\":"+std::to_string(s.range_max)+",\"ranges\":"+std::to_string(n)+",\"points\":"+std::to_string(f.payload.size()/8)+",\"stride\":"+std::to_string(stride)+"}";
 return f;
}
SensorFrame image_frame(const sensor_msgs_msg_dds__Image_& s) {
 const std::string encoding=RosoutBuffer::bounded_field(s.encoding,32);
 const unsigned channels=encoding=="mono8"?1:(encoding=="rgb8"||encoding=="bgr8")?3:(encoding=="rgba8"||encoding=="bgra8")?4:0;
 if(!channels)return rejected("Encoding não suportado: use rgb8/bgr8/rgba8/bgra8/mono8");
 if(!s.width||!s.height||s.width>1280||s.height>1280||uint64_t(s.width)*s.height>921600||s.is_bigendian>1)return rejected("Dimensões de imagem fora do orçamento");
 const uint64_t bytes=uint64_t(s.step)*s.height;
 if(s.step<uint64_t(s.width)*channels||bytes!=s.data._length||bytes>4*1024*1024-4096||!s.data._buffer)return rejected("Buffer/step de imagem inválido ou excessivo");
 SensorFrame f;f.payload.assign(s.data._buffer,s.data._buffer+s.data._length);
 f.metadata="{\"kind\":\"image\","+frame_header(s.header)+",\"encoding\":"+json_quote(encoding)+",\"width\":"+std::to_string(s.width)+",\"height\":"+std::to_string(s.height)+",\"step\":"+std::to_string(s.step)+"}";
 return f;
}
SensorFrame compressed_frame(const sensor_msgs_msg_dds__CompressedImage_& s) {
 auto format=RosoutBuffer::bounded_field(s.format,128);
 if(format.find("compressedDepth")!=std::string::npos||((format.find("jpeg")==std::string::npos&&format.find("jpg")==std::string::npos)&&format.find("png")==std::string::npos))return rejected("Formato não suportado: JPEG/PNG de imagem colorida ou mono8");
 if(!s.data._buffer||s.data._length<4||s.data._length>1024*1024)return rejected("Imagem comprimida vazia ou maior que 1MiB");
 SensorFrame f;f.payload.assign(s.data._buffer,s.data._buffer+s.data._length);
 f.metadata="{\"kind\":\"compressed\","+frame_header(s.header)+",\"format\":"+json_quote(format)+"}";return f;
}
}

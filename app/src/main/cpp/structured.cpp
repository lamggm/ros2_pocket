#include "structured.hpp"
#include "model.hpp"
#include "PocketTypes.h"
#include <limits>
#include <cmath>
#include <cstring>
#include <locale>
namespace pocket {
std::string Field::json() const {
 std::string out="{\"path\":"+json_quote(path)+",\"kind\":"+json_quote(kind);
 if(kind=="object" || kind=="array") {
  out+=",\"children\":[";bool first=true;
  for(const auto& child:children){if(!first)out+=",";first=false;out+=child.json();}
  out+="]";
 }else out+=",\"display\":"+json_quote(display)+",\"value\":"+json_value;
 if(kind=="number")out+=",\"numberState\":"+json_quote(json_value!="null"?"finite":(display=="NaN"?"nan":(display=="+Inf"?"positive_infinity":"negative_infinity")));
 if(kind=="string")out+=std::string(",\"truncated\":")+(truncated?"true":"false");
 return out+"}";
}
static Field number(const std::string& path,double value) {
 if(!std::isfinite(value))return {path,"number",std::isnan(value)?"NaN":(value>0?"+Inf":"-Inf"),"null",{}};
 std::ostringstream out;out.imbue(std::locale::classic());out<<std::setprecision(std::numeric_limits<double>::max_digits10)<<value;
 return {path,"number",out.str(),out.str(),{}};
}
static Field vector3(const std::string& path,const geometry_msgs_msg_dds__Vector3_& v) {
 return {path,"object","","",{number(path+".x",v.x),number(path+".y",v.y),number(path+".z",v.z)}};
}
static Field pose(const std::string& path,const geometry_msgs_msg_dds__Pose_& v) {
 const auto p=path.empty()?"":path+".";
 return {path,"object","","",{
  {p+"position","object","","",{number(p+"position.x",v.position.x),number(p+"position.y",v.position.y),number(p+"position.z",v.position.z)}},
  {p+"orientation","object","","",{number(p+"orientation.x",v.orientation.x),number(p+"orientation.y",v.orientation.y),number(p+"orientation.z",v.orientation.z),number(p+"orientation.w",v.orientation.w)}}}};
}
std::string bounded_utf8(const char* value,size_t max_bytes) {
 if(!value)return "";
 size_t bytes=strnlen(value,max_bytes+1);
 if(bytes>max_bytes){bytes=max_bytes;while(bytes>0 && (static_cast<unsigned char>(value[bytes])&0xc0)==0x80)--bytes;}
 return std::string(value,bytes);
}
static Field text(const std::string& path,const char* value) {
 const auto s=bounded_utf8(value,512);
 const bool clipped=value && strnlen(value,513)>512;
 return {path,"string",s,json_quote(s),{},clipped};
}
static Field covariance(const std::string& path,const double (&values)[36]) {
 Field field{path,"array","","",{}};
 for(size_t i=0;i<36;i++)field.children.push_back(number(path+"["+std::to_string(i)+"]",values[i]));
 return field;
}
template<class T> static Field integer(const std::string& path,T value) {
 const auto s=std::to_string(value);return {path,"number",s,s,{}};
}
Field decode_fields(const std::string& type,const void* sample) {
 Field root{"","object","","",{}};
 if(type=="geometry_msgs::msg::dds_::Vector3_") {
  const auto& v=*static_cast<const geometry_msgs_msg_dds__Vector3_*>(sample);
  root.children={number("x",v.x),number("y",v.y),number("z",v.z)};
 }
 if(type=="geometry_msgs::msg::dds_::Quaternion_") {
  const auto& v=*static_cast<const geometry_msgs_msg_dds__Quaternion_*>(sample);
  root.children={number("x",v.x),number("y",v.y),number("z",v.z),number("w",v.w)};
 }
 if(type=="geometry_msgs::msg::dds_::Twist_") {
  const auto& v=*static_cast<const geometry_msgs_msg_dds__Twist_*>(sample);
  root.children={vector3("linear",v.linear),vector3("angular",v.angular)};
 }
 if(type=="geometry_msgs::msg::dds_::Pose_")root=pose("",*static_cast<const geometry_msgs_msg_dds__Pose_*>(sample));
 if(type=="nav_msgs::msg::dds_::Odometry_") {
  const auto& v=*static_cast<const nav_msgs_msg_dds__Odometry_*>(sample);
  root.children={
   {"header","object","","",{
    {"header.stamp","object","","",{number("header.stamp.sec",v.header.stamp.sec),number("header.stamp.nanosec",v.header.stamp.nanosec)}},
    text("header.frame_id",v.header.frame_id)}},
   text("child_frame_id",v.child_frame_id),
   {"pose","object","","",{pose("pose.pose",v.pose.pose),covariance("pose.covariance",v.pose.covariance)}},
   {"twist","object","","",{
    {"twist.twist","object","","",{vector3("twist.twist.linear",v.twist.twist.linear),vector3("twist.twist.angular",v.twist.twist.angular)}},
    covariance("twist.covariance",v.twist.covariance)}}};
 }
 if(type=="std_msgs::msg::dds_::String_")root.children={text("data",static_cast<const std_msgs_msg_dds__String_*>(sample)->data)};
 else if(type=="std_msgs::msg::dds_::Bool_") {
  const std::string s=static_cast<const std_msgs_msg_dds__Bool_*>(sample)->data?"true":"false";
  root.children={{"data","boolean",s,s,{}}};
 }else if(type=="std_msgs::msg::dds_::Int32_")root.children={integer("data",static_cast<const std_msgs_msg_dds__Int32_*>(sample)->data)};
 else if(type=="std_msgs::msg::dds_::Int64_")root.children={integer("data",static_cast<const std_msgs_msg_dds__Int64_*>(sample)->data)};
 else if(type=="std_msgs::msg::dds_::Float32_")root.children={number("data",static_cast<const std_msgs_msg_dds__Float32_*>(sample)->data)};
 else if(type=="std_msgs::msg::dds_::Float64_")root.children={number("data",static_cast<const std_msgs_msg_dds__Float64_*>(sample)->data)};
 if(root.children.empty())throw std::invalid_argument("Unsupported message type: "+type);
 return root;
}
static void numeric_paths(const Field& field,std::vector<std::string>& paths) {
 if(field.kind=="number")paths.push_back(field.path);
 for(const auto& child:field.children)numeric_paths(child,paths);
}
static bool clipped(const Field& field) {
 if(field.truncated)return true;
 for(const auto& child:field.children)if(clipped(child))return true;
 return false;
}
std::string message_json(const std::string& type,const Field& root) {
 std::string out="{\"schemaVersion\":1,\"type\":"+json_quote(ros_type(type))+",\"root\":"+root.json()+",\"numericPaths\":[";
 std::vector<std::string> paths;numeric_paths(root,paths);bool first=true;
 for(const auto& path:paths){if(!first)out+=",";first=false;out+=json_quote(path);}
 return out+"],\"truncated\":"+(clipped(root)?"true":"false")+",\"limits\":{\"maxStringBytes\":512,\"maxNodes\":192,\"maxSummaryBytes\":8192}}";
}
static void summary(const Field& field,std::string& out) {
 if(field.children.empty() && !field.path.empty())out+=field.path+" = "+field.display+"\n";
 for(const auto& child:field.children)summary(child,out);
}
std::string field_summary(const Field& root){std::string out;summary(root,out);return out;}
}

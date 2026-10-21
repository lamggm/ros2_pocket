#include "structured.hpp"
#include "PocketTypes.h"
#include <cassert>
#include <cmath>
#include <limits>
#include <stdexcept>
int main() {
 using pocket::decode_fields;
 std_msgs_msg_dds__Int64_ integer{std::numeric_limits<int64_t>::min()};
 auto root=decode_fields("std_msgs::msg::dds_::Int64_",&integer);
 assert(root.children.size()==1);
 assert(root.children[0].path=="data" && root.children[0].json_value=="-9223372036854775808");
 std_msgs_msg_dds__Int32_ small{-2147483647};
 assert(decode_fields("std_msgs::msg::dds_::Int32_",&small).children[0].json_value=="-2147483647");
 std_msgs_msg_dds__Bool_ boolean{true};
 const auto b=decode_fields("std_msgs::msg::dds_::Bool_",&boolean).children[0];
 assert(b.kind=="boolean" && b.json_value=="true");
 std_msgs_msg_dds__Float32_ f{0.125f};
 assert(decode_fields("std_msgs::msg::dds_::Float32_",&f).children[0].json_value=="0.125");
 std_msgs_msg_dds__Float64_ d{std::numeric_limits<double>::infinity()};
 const auto special=decode_fields("std_msgs::msg::dds_::Float64_",&d).children[0];
 assert(special.json_value=="null" && special.display=="+Inf");
 char unicode[]="ação_世界_🤖";
 std_msgs_msg_dds__String_ s{unicode};
 const auto string=decode_fields("std_msgs::msg::dds_::String_",&s).children[0];
 assert(string.kind=="string" && string.display==unicode);
 assert(pocket::message_json("std_msgs::msg::dds_::Bool_",decode_fields("std_msgs::msg::dds_::Bool_",&boolean)).find("\"numericPaths\":[]")!=std::string::npos);
 bool rejected=false;
 try{decode_fields("geometry_msgs::msg::dds_::Point_",&integer);}catch(const std::invalid_argument&){rejected=true;}
 assert(rejected);
}

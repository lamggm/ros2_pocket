#pragma once
#include <string>
#include <cstddef>
#include <vector>
namespace pocket {
// Schema v1: ROS field names, dots for members, [i] for arrays.
struct Field {
 std::string path, kind, display, json_value;
 std::vector<Field> children;
 bool truncated=false;
 std::string json() const;
};
std::string bounded_utf8(const char* value,size_t max_bytes);
Field decode_fields(const std::string& type,const void* sample);
std::string message_json(const std::string& type,const Field& root);
std::string field_summary(const Field& root);
}

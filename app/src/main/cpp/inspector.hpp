#pragma once
#include <memory>
#include <cstdint>
#include <string>
namespace pocket {
class Session {
 struct Impl; std::unique_ptr<Impl> impl;
 uint64_t session_generation=0, reader_generation=0;
 public:
 Session(); ~Session();
 std::string start(int domain, const std::string& address, const std::string& peer);
 std::string poll();
 std::string subscribe(const std::string& topic, const std::string& type, bool reliable, bool transient);
 void stop();
};
}

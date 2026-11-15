#pragma once
#include <memory>
#include <string>
namespace pocket {
class RobotDescriptionSession {
 struct Impl;std::unique_ptr<Impl> impl;
public:
 RobotDescriptionSession();~RobotDescriptionSession();
 std::string start(int domain,const std::string& address,const std::string& peer,const std::string& topic,bool reliable,bool transient,const std::string& mode="lan",const std::string& format="jazzy");
 std::string start_tf(const std::string& dynamic,const std::string& fixed,bool reliable,bool retained);std::string poll_tf();void stop_tf();
 std::string poll();void stop();
};
}

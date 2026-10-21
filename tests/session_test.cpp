#include "inspector.hpp"
#include <iostream>
int main(int argc,char** argv) {
 if (argc != 2) return 2;
 pocket::Session s;
 auto result=s.start(67,argv[1],"");
 if (result.find("\"ok\":true") == std::string::npos) {
  std::cerr << "FAIL real DDS session creation: " << result << '\n'; return 1;
 }
 s.stop(); s.stop();
 std::cout << "PASS real DDS participant creation and idempotent teardown\n";
}

#include "inspector.hpp"
#include <iostream>

int main() {
 pocket::Session session;
 std::cout << session.poll() << '\n';
 session.stop();
 session.stop();
 std::cout << session.poll() << '\n';
}

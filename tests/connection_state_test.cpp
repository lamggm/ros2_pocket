#include "model.hpp"
#include <cassert>
#include <iostream>
int main(){
 using namespace pocket;
 assert(connection_state(false,0,false,"")=="offline");
 assert(connection_state(true,0,false,"")=="discovering");
 assert(connection_state(true,2,false,"")=="connected");
 assert(connection_state(true,0,true,"")=="reconnecting");
 assert(connection_state(true,2,true,"failed")=="error");
 assert(subscription_state(false,0,0,-1,"")=="inactive");
 assert(subscription_state(false,0,0,-1,"failed")=="error");
 assert(subscription_state(true,0,1,-1,"")=="waiting");
 assert(subscription_state(true,4,1,0.5,"")=="receiving");
 assert(subscription_state(true,4,0,0.5,"")=="stale");
 assert(subscription_state(true,4,1,3.01,"")=="stale");
 assert(subscription_state(true,4,1,-1,"")=="stale");
 std::cout<<"PASS portable connection/freshness classifier\n";
}

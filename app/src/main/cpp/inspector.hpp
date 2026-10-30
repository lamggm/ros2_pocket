#pragma once
#include <memory>
#include <cstdint>
#include <string>
#include <utility>
#include <vector>
namespace pocket {
class Session {
 struct Impl; std::unique_ptr<Impl> impl;
 uint64_t session_generation=0, reader_generation=0, diagnostic_revision=0;
#ifdef POCKET_ENABLE_TEST_HOOKS
 uint64_t request_generation=0,last_arm_nonce=0;
#endif
 public:
 Session(); ~Session();
 std::string start(int domain, const std::string& address, const std::string& peer);
 std::string poll();
 std::string select_numeric(uint64_t epoch,const std::vector<std::pair<int,std::string>>& fields);
 std::string diagnostic_state() const;
 std::string subscribe(const std::string& topic, const std::string& type, bool reliable, bool transient);
 // Stable slots 0..3. Slot zero is the legacy subscribe/poll projection.
 std::string watch(int slot, const std::string& topic, const std::string& type, bool reliable, bool transient);
 std::string remove_watch(int slot);
 void clear_rosout();
 void stop();
#ifdef POCKET_ENABLE_TEST_HOOKS
 struct ReaderTestIdentity { uint64_t session,generation,request; };
 ReaderTestIdentity reader_identity_for_test(int slot) const;
 struct ReaderArmHandle {
  int slot=-1; uint64_t session=0,generation=0,request=0,lease=0,nonce=0;
  explicit operator bool() const { return slot>=0&&lease>0&&nonce>0; }
 };
 ReaderArmHandle arm_reader_failure_for_test(int slot, const std::string& stage,
     uint64_t expected_session, uint64_t expected_generation, uint64_t expected_request,
     uint64_t lease_epoch, uint64_t operation_nonce);
 bool cancel_reader_failure_for_test(const ReaderArmHandle& handle);
 std::string reader_failure_receipt_for_test() const;
#endif
};
}

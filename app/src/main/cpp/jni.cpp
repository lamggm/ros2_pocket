#include <jni.h>
#include "inspector.hpp"
#include "model.hpp"
#include "sensors.hpp"
#include <mutex>
static pocket::Session session;
static pocket::SensorSession sensor;
static std::mutex mutex;
static std::string text(JNIEnv* env,jstring s) {
 if(!s)return "";
 const char* p=env->GetStringUTFChars(s,nullptr);std::string result=p?p:"";
 if(p)env->ReleaseStringUTFChars(s,p);return result;
}
static jbyteArray bytes(JNIEnv* env,const std::string& s) {
 auto result=env->NewByteArray(static_cast<jsize>(s.size()));
 if(result)env->SetByteArrayRegion(result,0,static_cast<jsize>(s.size()),reinterpret_cast<const jbyte*>(s.data()));
 return result;
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_lamggm_rospocket_NativeBridge_start(JNIEnv* e,jclass,jint d,jstring address,jstring peer) {
 std::lock_guard<std::mutex> lock(mutex);
 try{return bytes(e,session.start(d,text(e,address),text(e,peer)));}
 catch(const std::exception& ex){return bytes(e,"{\"ok\":false,\"error\":"+pocket::json_quote(ex.what())+"}");}
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_lamggm_rospocket_NativeBridge_poll(JNIEnv* e,jclass) {
 std::lock_guard<std::mutex> lock(mutex);return bytes(e,session.poll());
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_lamggm_rospocket_NativeBridge_subscribe(JNIEnv* e,jclass,jstring topic,jstring type,jboolean reliable,jboolean transient) {
 std::lock_guard<std::mutex> lock(mutex);return bytes(e,session.subscribe(text(e,topic),text(e,type),reliable,transient));
}
extern "C" JNIEXPORT void JNICALL Java_com_lamggm_rospocket_NativeBridge_stop(JNIEnv*,jclass) {
 std::lock_guard<std::mutex> lock(mutex);sensor.stop();session.stop();
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_lamggm_rospocket_NativeBridge_startSensor(JNIEnv* e,jclass,jint domain,jstring address,jstring peer,jstring topic,jstring type){
 std::lock_guard<std::mutex> lock(mutex);
 try{return bytes(e,sensor.start(domain,text(e,address),text(e,peer),text(e,topic),text(e,type)));}
 catch(const std::exception&){sensor.stop();return bytes(e,"{\"ok\":false,\"error\":\"Falha ao iniciar sensor\"}");}
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_lamggm_rospocket_NativeBridge_pollSensor(JNIEnv* e,jclass){
 std::lock_guard<std::mutex> lock(mutex);
 try{
  auto frame=sensor.poll();std::string packet;uint32_t n=static_cast<uint32_t>(frame.metadata.size());
  for(int i=0;i<4;i++)packet+=static_cast<char>(n>>(8*i));packet+=frame.metadata;
  packet.append(reinterpret_cast<const char*>(frame.payload.data()),frame.payload.size());return bytes(e,packet);
 }catch(const std::exception&){return bytes(e,"");}
}
extern "C" JNIEXPORT void JNICALL Java_com_lamggm_rospocket_NativeBridge_stopSensor(JNIEnv*,jclass){
 std::lock_guard<std::mutex> lock(mutex);sensor.stop();
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_lamggm_rospocket_NativeBridge_watch(JNIEnv* e,jclass,jint slot,jstring topic,jstring type,jboolean reliable,jboolean transient) {
 std::lock_guard<std::mutex> lock(mutex);return bytes(e,session.watch(slot,text(e,topic),text(e,type),reliable,transient));
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_lamggm_rospocket_NativeBridge_removeWatch(JNIEnv* e,jclass,jint slot) {
 std::lock_guard<std::mutex> lock(mutex);return bytes(e,session.remove_watch(slot));
}
extern "C" JNIEXPORT void JNICALL Java_com_lamggm_rospocket_NativeBridge_clearRosout(JNIEnv*,jclass) {
 std::lock_guard<std::mutex> lock(mutex);session.clear_rosout();
}

#ifdef POCKET_ENABLE_TEST_HOOKS
static uint64_t lease_epoch=0,last_lease_epoch=0;
static pocket::Session::ReaderArmHandle acquired;
static bool same(const pocket::Session::ReaderArmHandle& a,const pocket::Session::ReaderArmHandle& b) {
 return a.slot==b.slot&&a.session==b.session&&a.generation==b.generation&&a.request==b.request&&a.lease==b.lease&&a.nonce==b.nonce;
}
static jlongArray longs(JNIEnv* e,const jlong* data,jsize size){auto result=e->NewLongArray(size);if(result)e->SetLongArrayRegion(result,0,size,data);return result;}
extern "C" JNIEXPORT jboolean JNICALL Java_com_lamggm_rospocket_DeviceTestHooks_beginLease(JNIEnv*,jclass,jlong epoch) {
 std::lock_guard<std::mutex> lock(mutex);
 if(epoch<=0||lease_epoch||static_cast<uint64_t>(epoch)<=last_lease_epoch)return false;
 lease_epoch=last_lease_epoch=static_cast<uint64_t>(epoch);return true;
}
extern "C" JNIEXPORT jboolean JNICALL Java_com_lamggm_rospocket_DeviceTestHooks_endLease(JNIEnv*,jclass,jlong epoch) {
 std::lock_guard<std::mutex> lock(mutex);
 if(epoch<=0||static_cast<uint64_t>(epoch)!=lease_epoch||acquired||session.diagnostic_state().find("\"armed\":true")!=std::string::npos)return false;
 lease_epoch=0;return true;
}
extern "C" JNIEXPORT jlongArray JNICALL Java_com_lamggm_rospocket_DeviceTestHooks_readerIdentity(JNIEnv* e,jclass,jint slot) {
 std::lock_guard<std::mutex> lock(mutex);if(!lease_epoch||slot<0||slot>3)return nullptr;
 auto id=session.reader_identity_for_test(slot);jlong data[]={static_cast<jlong>(id.session),static_cast<jlong>(id.generation),static_cast<jlong>(id.request)};return longs(e,data,3);
}
extern "C" JNIEXPORT jlongArray JNICALL Java_com_lamggm_rospocket_DeviceTestHooks_armFailure(JNIEnv* e,jclass,jint slot,jstring stage,jlongArray identity,jlong epoch,jlong nonce) {
 std::lock_guard<std::mutex> lock(mutex);
 if(!identity||e->GetArrayLength(identity)!=3||epoch<=0||static_cast<uint64_t>(epoch)!=lease_epoch||nonce<=0||acquired)return nullptr;
 jlong id[3];e->GetLongArrayRegion(identity,0,3,id);if(e->ExceptionCheck()||id[0]<0||id[1]<0||id[2]<=0)return nullptr;
 auto h=session.arm_reader_failure_for_test(slot,text(e,stage),id[0],id[1],id[2],epoch,nonce);
 if(!h)return nullptr;acquired=h;
 jlong data[]={h.slot,static_cast<jlong>(h.session),static_cast<jlong>(h.generation),static_cast<jlong>(h.request),static_cast<jlong>(h.lease),static_cast<jlong>(h.nonce)};return longs(e,data,6);
}
extern "C" JNIEXPORT jboolean JNICALL Java_com_lamggm_rospocket_DeviceTestHooks_cancelFailure(JNIEnv* e,jclass,jlongArray handle) {
 std::lock_guard<std::mutex> lock(mutex);if(!handle||e->GetArrayLength(handle)!=6)return false;
 jlong data[6];e->GetLongArrayRegion(handle,0,6,data);if(e->ExceptionCheck())return false;
 pocket::Session::ReaderArmHandle h{static_cast<int>(data[0]),static_cast<uint64_t>(data[1]),static_cast<uint64_t>(data[2]),static_cast<uint64_t>(data[3]),static_cast<uint64_t>(data[4]),static_cast<uint64_t>(data[5])};
 if(!h||h.lease!=lease_epoch||!same(h,acquired))return false;
 bool cancelled=session.cancel_reader_failure_for_test(h);acquired={};return cancelled;
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_lamggm_rospocket_DeviceTestHooks_failureReceipt(JNIEnv* e,jclass) {
 std::lock_guard<std::mutex> lock(mutex);return bytes(e,lease_epoch?session.reader_failure_receipt_for_test():"");
}
#endif
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_lamggm_rospocket_NativeBridge_diagnosticState(JNIEnv* e,jclass) {
 std::lock_guard<std::mutex> lock(mutex);auto result=session.diagnostic_state();result.pop_back();
#ifdef POCKET_ENABLE_TEST_HOOKS
 result+=",\"runnerActive\":"+std::string(lease_epoch?"true":"false")+",\"nativeLeaseEpoch\":"+std::to_string(lease_epoch);
#else
 result+=",\"runnerActive\":false,\"nativeLeaseEpoch\":0";
#endif
 return bytes(e,result+"}");
}

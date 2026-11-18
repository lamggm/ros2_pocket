#include <jni.h>
#include "inspector.hpp"
#include "model.hpp"
#include "sensors.hpp"
#include "robot_description.hpp"
#include <mutex>
static pocket::Session session;
static pocket::SensorSession sensor,camera{pocket::SensorSession::Channel::Camera};
static pocket::RobotDescriptionSession description;
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
 description.stop();
 try{return bytes(e,session.start(d,text(e,address),text(e,peer)));}
 catch(const std::exception& ex){return bytes(e,"{\"ok\":false,\"error\":"+pocket::json_quote(ex.what())+"}");}
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_lamggm_rospocket_NativeBridge_startWithGraphFormat(JNIEnv* e,jclass,jint d,jstring address,jstring peer,jstring format) {
 std::lock_guard<std::mutex> lock(mutex);
 description.stop();
 if(!format||e->GetStringUTFLength(format)>6){session.stop();return bytes(e,"{\"ok\":false,\"error\":\"Formato de grafo nao suportado\"}");}
 try{return bytes(e,session.start(d,text(e,address),text(e,peer),text(e,format)));}
 catch(const std::exception& ex){return bytes(e,"{\"ok\":false,\"error\":"+pocket::json_quote(ex.what())+"}");}
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_lamggm_rospocket_NativeBridge_startWithNetworkMode(JNIEnv* e,jclass,jint d,jstring address,jstring peer,jstring format,jstring mode) {
 std::lock_guard<std::mutex> lock(mutex);sensor.stop();camera.stop();description.stop();
 if(!format||e->GetStringUTFLength(format)>6||!mode||e->GetStringUTFLength(mode)>3){session.stop();return bytes(e,"{\"ok\":false,\"error\":\"Formato ou modo de rede nao suportado\"}");}
 try{return bytes(e,session.start(d,text(e,address),text(e,peer),text(e,format),text(e,mode)));}
 catch(const std::exception&){session.stop();return bytes(e,"{\"ok\":false,\"error\":\"Falha ao iniciar rede DDS\"}");}
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_lamggm_rospocket_NativeBridge_poll(JNIEnv* e,jclass) {
 std::lock_guard<std::mutex> lock(mutex);return bytes(e,session.poll());
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_lamggm_rospocket_NativeBridge_selectNumeric(JNIEnv* e,jclass,jlong epoch,jintArray slots,jobjectArray paths) {
 std::lock_guard<std::mutex> lock(mutex);
 auto invalid=[e](){return bytes(e,"{\"ok\":false,\"error\":\"Selecao numerica JNI invalida\"}");};
 if(epoch<=0||!slots||!paths)return invalid();
 const jsize size=e->GetArrayLength(slots);if(size>4||e->GetArrayLength(paths)!=size)return invalid();
 jint ids[4]{};if(size)e->GetIntArrayRegion(slots,0,size,ids);if(e->ExceptionCheck())return nullptr;
 try{
  std::vector<std::pair<int,std::string>> fields;
  for(jsize i=0;i<size;i++){
   auto path=static_cast<jstring>(e->GetObjectArrayElement(paths,i));if(e->ExceptionCheck())return nullptr;
   if(!path)return invalid();
   if(e->GetStringUTFLength(path)>256){e->DeleteLocalRef(path);return invalid();}
   auto value=text(e,path);e->DeleteLocalRef(path);if(e->ExceptionCheck())return nullptr;
   fields.emplace_back(ids[i],std::move(value));
  }
  return bytes(e,session.select_numeric(static_cast<uint64_t>(epoch),fields));
 }catch(const std::exception&){return invalid();}
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_lamggm_rospocket_NativeBridge_subscribe(JNIEnv* e,jclass,jstring topic,jstring type,jboolean reliable,jboolean transient) {
 std::lock_guard<std::mutex> lock(mutex);return bytes(e,session.subscribe(text(e,topic),text(e,type),reliable,transient));
}
extern "C" JNIEXPORT void JNICALL Java_com_lamggm_rospocket_NativeBridge_stop(JNIEnv*,jclass) {
 std::lock_guard<std::mutex> lock(mutex);description.stop();sensor.stop();camera.stop();session.stop();
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_lamggm_rospocket_NativeBridge_startSensor(JNIEnv* e,jclass,jint domain,jstring address,jstring peer,jstring topic,jstring type){
 std::lock_guard<std::mutex> lock(mutex);
 try{return bytes(e,sensor.start(domain,text(e,address),text(e,peer),text(e,topic),text(e,type)));}
 catch(const std::exception&){sensor.stop();return bytes(e,"{\"ok\":false,\"error\":\"Falha ao iniciar sensor\"}");}
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_lamggm_rospocket_NativeBridge_startSensorWithNetworkMode(JNIEnv* e,jclass,jint domain,jstring address,jstring peer,jstring topic,jstring type,jstring mode){
 std::lock_guard<std::mutex> lock(mutex);
 if(!mode||e->GetStringUTFLength(mode)>3){sensor.stop();return bytes(e,"{\"ok\":false,\"error\":\"Modo de rede visual nao suportado\"}");}
 try{return bytes(e,sensor.start(domain,text(e,address),text(e,peer),text(e,topic),text(e,type),text(e,mode)));}
 catch(const std::exception&){sensor.stop();return bytes(e,"{\"ok\":false,\"error\":\"Falha ao iniciar sensor\"}");}
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_lamggm_rospocket_NativeBridge_startSensorConfigured(JNIEnv* e,jclass,jint domain,jstring address,jstring peer,jstring topic,jstring type,jstring mode,jstring format){
 std::lock_guard<std::mutex> lock(mutex);
 if(!mode||e->GetStringUTFLength(mode)>3||!format||e->GetStringUTFLength(format)>6){sensor.stop();return bytes(e,"{\"ok\":false,\"error\":\"Modo ou formato visual nao suportado\"}");}
 try{return bytes(e,sensor.start(domain,text(e,address),text(e,peer),text(e,topic),text(e,type),text(e,mode),text(e,format)));}
 catch(const std::exception&){sensor.stop();return bytes(e,"{\"ok\":false,\"error\":\"Falha ao iniciar sensor\"}");}
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_lamggm_rospocket_NativeBridge_startMap(JNIEnv* e,jclass,jint d,jstring a,jstring p,jstring w,jboolean reliable,jboolean retained,jstring mode,jstring format){
 std::lock_guard<std::mutex> lock(mutex);
 if(!a||!w||!mode||!format||e->GetStringUTFLength(a)>15||(p&&e->GetStringUTFLength(p)>15)||e->GetStringUTFLength(w)>256||e->GetStringUTFLength(mode)>3||e->GetStringUTFLength(format)>6){sensor.stop();return bytes(e,"{\"ok\":false,\"error\":\"Configuração de mapa inválida\"}");}
 try{return bytes(e,sensor.start(d,text(e,a),text(e,p),text(e,w),"nav_msgs::msg::dds_::OccupancyGrid_",text(e,mode),text(e,format),reliable,retained));}catch(const std::exception&){sensor.stop();return bytes(e,"{\"ok\":false,\"error\":\"Falha ao iniciar mapa\"}");}
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_lamggm_rospocket_NativeBridge_startLocationTf(JNIEnv* e,jclass,jint d,jstring a,jstring p,jstring dynamic,jstring fixed,jstring mode,jstring format){
 std::lock_guard<std::mutex> lock(mutex);
 if(!a||!dynamic||!fixed||!mode||!format||e->GetStringUTFLength(a)>15||(p&&e->GetStringUTFLength(p)>15)||e->GetStringUTFLength(dynamic)>256||e->GetStringUTFLength(fixed)>256||e->GetStringUTFLength(mode)>3||e->GetStringUTFLength(format)>6){description.stop();return bytes(e,"{\"ok\":false,\"error\":\"Configuração TF de localização inválida\"}");}
 try{auto opened=description.start(d,text(e,a),text(e,p),"",false,false,text(e,mode),text(e,format),true);if(opened.find("\"ok\":true")==std::string::npos)return bytes(e,opened);auto result=description.start_tf(text(e,dynamic),text(e,fixed),true,true);if(result.find("\"ok\":true")==std::string::npos)description.stop();return bytes(e,result);}catch(const std::exception&){description.stop();return bytes(e,"{\"ok\":false,\"error\":\"Falha TF de localização\"}");}
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_lamggm_rospocket_NativeBridge_startCamera(JNIEnv* e,jclass,jint d,jstring a,jstring p,jstring w,jstring t,jstring mode,jstring format){
 std::lock_guard<std::mutex> lock(mutex);
 if(!a||!w||!t||!mode||!format||e->GetStringUTFLength(a)>15||(p&&e->GetStringUTFLength(p)>15)||e->GetStringUTFLength(w)>512||e->GetStringUTFLength(t)>64||e->GetStringUTFLength(mode)>3||e->GetStringUTFLength(format)>6){camera.stop();return bytes(e,"{\"ok\":false,\"error\":\"Configuração de câmera inválida\"}");}
 try{return bytes(e,camera.start(d,text(e,a),text(e,p),text(e,w),text(e,t),text(e,mode),text(e,format)));}
 catch(const std::exception&){camera.stop();return bytes(e,"{\"ok\":false,\"error\":\"Falha ao iniciar câmera\"}");}
}
static jbyteArray poll_visual(JNIEnv* e,pocket::SensorSession& stream){
 try{
  auto frame=stream.poll();std::string packet;uint32_t n=static_cast<uint32_t>(frame.metadata.size());
  for(int i=0;i<4;i++)packet+=static_cast<char>(n>>(8*i));packet+=frame.metadata;
  packet.append(reinterpret_cast<const char*>(frame.payload.data()),frame.payload.size());return bytes(e,packet);
 }catch(const std::exception&){return bytes(e,"");}
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_lamggm_rospocket_NativeBridge_pollSensor(JNIEnv* e,jclass){
 std::lock_guard<std::mutex> lock(mutex);return poll_visual(e,sensor);
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_lamggm_rospocket_NativeBridge_pollCamera(JNIEnv* e,jclass){
 std::lock_guard<std::mutex> lock(mutex);return poll_visual(e,camera);
}
extern "C" JNIEXPORT void JNICALL Java_com_lamggm_rospocket_NativeBridge_stopCamera(JNIEnv*,jclass){
 std::lock_guard<std::mutex> lock(mutex);camera.stop();
}
extern "C" JNIEXPORT void JNICALL Java_com_lamggm_rospocket_NativeBridge_stopSensor(JNIEnv*,jclass){
 std::lock_guard<std::mutex> lock(mutex);sensor.stop();
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_lamggm_rospocket_NativeBridge_startTf(JNIEnv* e,jclass,jstring d,jstring f,jboolean r,jboolean t){std::lock_guard<std::mutex> lock(mutex);if(!d||!f||e->GetStringUTFLength(d)>512||e->GetStringUTFLength(f)>512){description.stop_tf();return bytes(e,"{\"ok\":false,\"error\":\"Tópico TF inválido\"}");}try{return bytes(e,description.start_tf(text(e,d),text(e,f),r,t));}catch(const std::exception&){description.stop_tf();return bytes(e,"{\"ok\":false,\"error\":\"Falha TF\"}");}}
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_lamggm_rospocket_NativeBridge_pollTf(JNIEnv* e,jclass){std::lock_guard<std::mutex> lock(mutex);try{return bytes(e,description.poll_tf());}catch(const std::exception&){return bytes(e,"{\"running\":false,\"error\":\"Falha TF\"}");}}
extern "C" JNIEXPORT void JNICALL Java_com_lamggm_rospocket_NativeBridge_stopTf(JNIEnv*,jclass){std::lock_guard<std::mutex> lock(mutex);description.stop_tf();}
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_lamggm_rospocket_NativeBridge_startDescription(JNIEnv* e,jclass,jint d,jstring a,jstring p,jstring w,jboolean r,jboolean l,jstring m,jstring f){
 std::lock_guard<std::mutex> lock(mutex);
 if(!a||!w||!m||!f||e->GetStringUTFLength(a)>15||(p&&e->GetStringUTFLength(p)>15)||e->GetStringUTFLength(w)>512||e->GetStringUTFLength(m)>3||e->GetStringUTFLength(f)>6){description.stop();return bytes(e,"{\"ok\":false,\"error\":\"Configuração de descrição inválida\"}");}
 try{return bytes(e,description.start(d,text(e,a),text(e,p),text(e,w),r,l,text(e,m),text(e,f)));}catch(const std::exception&){description.stop();return bytes(e,"{\"ok\":false,\"error\":\"Falha ao iniciar descrição DDS\"}");}
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_lamggm_rospocket_NativeBridge_pollDescription(JNIEnv* e,jclass){
 std::lock_guard<std::mutex> lock(mutex);try{return bytes(e,description.poll());}catch(const std::exception&){description.stop();return bytes(e,"{\"running\":false,\"error\":\"Falha de leitura da descrição\"}");}
}
extern "C" JNIEXPORT void JNICALL Java_com_lamggm_rospocket_NativeBridge_stopDescription(JNIEnv*,jclass){std::lock_guard<std::mutex> lock(mutex);description.stop();}
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

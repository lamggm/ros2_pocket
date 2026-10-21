#include <jni.h>
#include "inspector.hpp"
#include "model.hpp"
#include <mutex>
static pocket::Session session;
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
 std::lock_guard<std::mutex> lock(mutex);session.stop();
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_lamggm_rospocket_NativeBridge_watch(JNIEnv* e,jclass,jint slot,jstring topic,jstring type,jboolean reliable,jboolean transient) {
 std::lock_guard<std::mutex> lock(mutex);return bytes(e,session.watch(slot,text(e,topic),text(e,type),reliable,transient));
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_lamggm_rospocket_NativeBridge_removeWatch(JNIEnv* e,jclass,jint slot) {
 std::lock_guard<std::mutex> lock(mutex);return bytes(e,session.remove_watch(slot));
}

#include <jni.h>
#include "AudioEngine.h"

static AudioEngine engine;

extern "C" {

JNIEXPORT void JNICALL
Java_com_rsa_livemixer_MainActivity_startAudioEngine(JNIEnv *, jobject) {
    engine.start();
}

JNIEXPORT void JNICALL
Java_com_rsa_livemixer_MainActivity_stopAudioEngine(JNIEnv *, jobject) {
    engine.stop();
}

JNIEXPORT void JNICALL
Java_com_rsa_livemixer_MainActivity_setMicGain(JNIEnv *, jobject, jfloat gain) {
    engine.setMicGain(gain);
}

JNIEXPORT void JNICALL
Java_com_rsa_livemixer_MainActivity_setLowEq(JNIEnv *, jobject, jfloat gainDb) {
    engine.setLowEq(gainDb);
}

JNIEXPORT void JNICALL
Java_com_rsa_livemixer_MainActivity_setMidEq(JNIEnv *, jobject, jfloat gainDb) {
    engine.setMidEq(gainDb);
}

JNIEXPORT void JNICALL
Java_com_rsa_livemixer_MainActivity_setHighEq(JNIEnv *, jobject, jfloat gainDb) {
    engine.setHighEq(gainDb);
}

}
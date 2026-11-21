#pragma once
#include <oboe/Oboe.h>
#include <atomic>
#include <memory>
#include "BiquadEQ.h"

class AudioEngine : public oboe::AudioStreamDataCallback {
public:
    void start();
    void stop();
    void setMicGain(float gain);
    void setLowEq(float gainDb);
    void setMidEq(float gainDb);
    void setHighEq(float gainDb);

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *stream, void *audioData, int32_t numFrames) override;

private:
    std::shared_ptr<oboe::AudioStream> inStream;
    std::shared_ptr<oboe::AudioStream> outStream;
    BiquadFilter eqLow, eqMid, eqHigh;
    std::atomic<float> micGain{1.0f};
    float sampleRate = 48000.0f;
    bool isRunning = false;
};
#include "AudioEngine.h"
#include <vector>
#include <algorithm>

void AudioEngine::start() {
    if (isRunning) return;

    oboe::AudioStreamBuilder outBuilder;
    outBuilder.setDirection(oboe::Direction::Output)
              ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
              ->setSharingMode(oboe::SharingMode::Exclusive)
              ->setFormat(oboe::AudioFormat::Float)
              ->setChannelCount(1)
              ->setDataCallback(this)
              ->openStream(outStream);

    if (!outStream) return;
    sampleRate = static_cast<float>(outStream->getSampleRate());

    eqLow.setPeaking(sampleRate, 120.0f, 0.7f, 0.0f);
    eqMid.setPeaking(sampleRate, 2500.0f, 1.0f, 0.0f);
    eqHigh.setPeaking(sampleRate, 10000.0f, 0.7f, 0.0f);

    oboe::AudioStreamBuilder inBuilder;
    inBuilder.setDirection(oboe::Direction::Input)
             ->setInputPreset(oboe::InputPreset::Unprocessed)
             ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
             ->setSharingMode(oboe::SharingMode::Exclusive)
             ->setFormat(oboe::AudioFormat::Float)
             ->setChannelCount(1)
             ->setSampleRate(static_cast<int32_t>(sampleRate))
             ->openStream(inStream);

    if (inStream) inStream->requestStart();
    outStream->requestStart();
    isRunning = true;
}

void AudioEngine::stop() {
    if (!isRunning) return;
    if (inStream) { inStream->stop(); inStream->close(); inStream.reset(); }
    if (outStream) { outStream->stop(); outStream->close(); outStream.reset(); }
    isRunning = false;
}

void AudioEngine::setMicGain(float gain) { micGain.store(gain); }
void AudioEngine::setLowEq(float gainDb) { eqLow.setPeaking(sampleRate, 120.0f, 0.7f, gainDb); }
void AudioEngine::setMidEq(float gainDb) { eqMid.setPeaking(sampleRate, 2500.0f, 1.0f, gainDb); }
void AudioEngine::setHighEq(float gainDb) { eqHigh.setPeaking(sampleRate, 10000.0f, 0.7f, gainDb); }

oboe::DataCallbackResult AudioEngine::onAudioReady(oboe::AudioStream *stream, void *audioData, int32_t numFrames) {
    float *output = static_cast<float *>(audioData);
    std::vector<float> inputBuffer(numFrames, 0.0f);

    int32_t framesRead = 0;
    if (inStream) {
        auto result = inStream->read(inputBuffer.data(), numFrames, 0);
        if (result) framesRead = result.value();
    }

    float currentGain = micGain.load();
    for (int32_t i = 0; i < numFrames; ++i) {
        float sample = (i < framesRead) ? inputBuffer[i] * currentGain : 0.0f;
        sample = eqLow.process(sample);
        sample = eqMid.process(sample);
        sample = eqHigh.process(sample);
        output[i] = std::clamp(sample, -1.0f, 1.0f);
    }
    return oboe::DataCallbackResult::Continue;
}
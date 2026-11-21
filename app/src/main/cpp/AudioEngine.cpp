#include "AudioEngine.h"
#include <vector>
#include <algorithm>
#include <cmath>

namespace {
struct LocalBiquad {
    float b0 = 1.0f;
    float b1 = 0.0f;
    float b2 = 0.0f;
    float a1 = 0.0f;
    float a2 = 0.0f;
    float z1 = 0.0f;
    float z2 = 0.0f;

    void setPeaking(float sr, float freq, float q, float gainDb) {
        if (sr <= 0.0f || q <= 0.0001f) {
            b0 = 1.0f; b1 = 0.0f; b2 = 0.0f; a1 = 0.0f; a2 = 0.0f;
            return;
        }
        const float pi = 3.14159265358979323846f;
        float A = std::pow(10.0f, gainDb / 40.0f);
        float omega = 2.0f * pi * freq / sr;
        float sn = std::sin(omega);
        float cs = std::cos(omega);
        float alpha = sn / (2.0f * q);

        float a0 = 1.0f + alpha / A;
        b0 = (1.0f + alpha * A) / a0;
        b1 = (-2.0f * cs) / a0;
        b2 = (1.0f - alpha * A) / a0;
        a1 = (-2.0f * cs) / a0;
        a2 = (1.0f - alpha / A) / a0;
    }

    float process(float x) {
        float y = b0 * x + z1;
        z1 = b1 * x - a1 * y + z2;
        z2 = b2 * x - a2 * y;
        return y;
    }
};

LocalBiquad subBodyFilter; // Mengangkat getaran dalam (62 Hz)
LocalBiquad boxCutFilter;  // Membersihkan frekuensi kotak/mendem (380 Hz)
} // namespace

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

    // Racikan karakter bass empuk ala Voicemeeter
    subBodyFilter.setPeaking(sampleRate, 62.0f, 1.2f, 6.5f);    // Bobot sub dalam & empuk
    eqLow.setPeaking(sampleRate, 110.0f, 1.4f, 4.0f);           // Pukulan bass/kendang padat
    boxCutFilter.setPeaking(sampleRate, 380.0f, 1.8f, -3.0f);   // Kurangi efek mendem
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

void AudioEngine::setLowEq(float gainDb) {
    // Otomatis membagi dorongan ke sub dalam (62 Hz) & punch (110 Hz)
    subBodyFilter.setPeaking(sampleRate, 62.0f, 1.2f, 6.5f + (gainDb * 0.6f));
    eqLow.setPeaking(sampleRate, 110.0f, 1.4f, 4.0f + (gainDb * 0.4f));
}

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

        // 1. Bentuk karakter bass empuk & bersihkan mid-low
        sample = subBodyFilter.process(sample);
        sample = eqLow.process(sample);
        sample = boxCutFilter.process(sample);
        sample = eqMid.process(sample);
        sample = eqHigh.process(sample);

        // 2. Penguat kepadatan (Drive hangat) + Soft-Knee Limiter (pengganti hard clamp)
        sample *= 1.35f;
        sample = std::tanh(sample); // Menahan puncak dengan lembut tanpa memotong hentakan bass

        output[i] = std::clamp(sample, -0.98f, 0.98f);
    }
    return oboe::DataCallbackResult::Continue;
}
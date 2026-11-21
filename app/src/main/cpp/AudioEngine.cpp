#include "AudioEngine.h"
#include <vector>
#include <algorithm>
#include <cmath>

// Filter tambahan internal di dalam CPP (tanpa ubah AudioEngine.h)
static BiquadEQ subBodyFilter;   // Mengangkat getaran dalam (62 Hz)
static BiquadEQ boxCutFilter;    // Membersihkan frekuensi kotak/mendem (380 Hz)

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
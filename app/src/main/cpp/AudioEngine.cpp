#include "AudioEngine.h"
#include <algorithm>
#include <cmath>
#include <cstring>

using namespace oboe;

void AudioEngine::start() {
    if (isRunning) return;

    AudioStreamBuilder inBuilder;
    inBuilder.setDirection(Direction::Input);
    inBuilder.setPerformanceMode(PerformanceMode::LowLatency);
    inBuilder.setSharingMode(SharingMode::Exclusive);
    inBuilder.setFormat(AudioFormat::Float);
    inBuilder.setChannelCount(ChannelCount::Mono);

    Result inResult = inBuilder.openStream(inStream);
    if (inResult != Result::OK || !inStream) {
        inBuilder.setSharingMode(SharingMode::Shared);
        inResult = inBuilder.openStream(inStream);
        if (inResult != Result::OK || !inStream) {
            return;
        }
    }

    sampleRate = static_cast<float>(inStream->getSampleRate());
    if (sampleRate <= 0.0f) {
        sampleRate = 48000.0f;
    }

    eqLow.setPeaking(sampleRate, 110.0f, 1.0f, 0.0f);
    eqMid.setPeaking(sampleRate, 1200.0f, 1.2f, 0.0f);
    eqHigh.setPeaking(sampleRate, 6500.0f, 1.1f, 0.0f);

    AudioStreamBuilder outBuilder;
    outBuilder.setDirection(Direction::Output);
    outBuilder.setPerformanceMode(PerformanceMode::LowLatency);
    outBuilder.setSharingMode(SharingMode::Shared);
    outBuilder.setFormat(AudioFormat::Float);
    outBuilder.setChannelCount(inStream->getChannelCount());
    outBuilder.setSampleRate(static_cast<int32_t>(sampleRate));
    outBuilder.setDataCallback(this);

    Result outResult = outBuilder.openStream(outStream);
    if (outResult != Result::OK || !outStream) {
        inStream->close();
        inStream.reset();
        return;
    }

    if (inStream->requestStart() != Result::OK) {
        outStream->close();
        inStream->close();
        outStream.reset();
        inStream.reset();
        return;
    }

    if (outStream->requestStart() != Result::OK) {
        inStream->stop();
        outStream->close();
        inStream->close();
        outStream.reset();
        inStream.reset();
        return;
    }

    isRunning = true;
}

void AudioEngine::stop() {
    if (!isRunning) return;
    isRunning = false;

    if (outStream) {
        outStream->requestStop();
        outStream->close();
        outStream.reset();
    }
    if (inStream) {
        inStream->requestStop();
        inStream->close();
        inStream.reset();
    }
}

void AudioEngine::setMicGain(float gain) {
    micGain.store(std::max(0.0f, std::min(gain, 4.0f)), std::memory_order_relaxed);
}

void AudioEngine::setLowEq(float gainDb) {
    eqLow.setPeaking(sampleRate, 110.0f, 1.0f, gainDb);
}

void AudioEngine::setMidEq(float gainDb) {
    eqMid.setPeaking(sampleRate, 1200.0f, 1.2f, gainDb);
}

void AudioEngine::setHighEq(float gainDb) {
    eqHigh.setPeaking(sampleRate, 6500.0f, 1.1f, gainDb);
}

DataCallbackResult AudioEngine::onAudioReady(AudioStream *stream, void *audioData, int32_t numFrames) {
    auto *outBuf = static_cast<float *>(audioData);
    int32_t channels = stream ? stream->getChannelCount() : 1;
    if (channels < 1) channels = 1;

    if (!isRunning || !inStream) {
        std::memset(outBuf, 0, static_cast<size_t>(numFrames) * static_cast<size_t>(channels) * sizeof(float));
        return DataCallbackResult::Continue;
    }

    float gain = micGain.load(std::memory_order_relaxed);

    for (int32_t i = 0; i < numFrames; ++i) {
        float inSample = 0.0f;
        ResultWithValue<int32_t> readRes = inStream->read(&inSample, 1, 0);
        float sample = (readRes.ok() && readRes.value() > 0) ? (inSample * gain) : 0.0f;

        sample = eqLow.process(sample);
        sample = eqMid.process(sample);
        sample = eqHigh.process(sample);

        // Soft limiter to prevent harsh clipping
        sample = std::tanh(sample);

        for (int32_t ch = 0; ch < channels; ++ch) {
            outBuf[i * channels + ch] = sample;
        }
    }

    return DataCallbackResult::Continue;
}
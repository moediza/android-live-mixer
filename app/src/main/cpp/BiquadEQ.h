#pragma once
#include <cmath>
#include <atomic>

class BiquadFilter {
public:
    void setPeaking(float sampleRate, float freq, float q, float gainDb) {
        if (sampleRate <= 0.0f) return;
        float A = std::pow(10.0f, gainDb / 40.0f);
        float w0 = 2.0f * static_cast<float>(M_PI) * freq / sampleRate;
        float alpha = std::sin(w0) / (2.0f * q);

        float b0_raw = 1.0f + alpha * A;
        float b1_raw = -2.0f * std::cos(w0);
        float b2_raw = 1.0f - alpha * A;
        float a0_raw = 1.0f + alpha / A;
        float a1_raw = -2.0f * std::cos(w0);
        float a2_raw = 1.0f - alpha / A;

        b0.store(b0_raw / a0_raw);
        b1.store(b1_raw / a0_raw);
        b2.store(b2_raw / a0_raw);
        a1.store(a1_raw / a0_raw);
        a2.store(a2_raw / a0_raw);
    }

    float process(float in) {
        float out = b0.load() * in + b1.load() * x1 + b2.load() * x2 - a1.load() * y1 - a2.load() * y2;
        x2 = x1; x1 = in;
        y2 = y1; y1 = out;
        return out;
    }

private:
    std::atomic<float> b0{1.0f}, b1{0.0f}, b2{0.0f}, a1{0.0f}, a2{0.0f};
    float x1 = 0.0f, x2 = 0.0f, y1 = 0.0f, y2 = 0.0f;
};
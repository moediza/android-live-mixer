#pragma once
#include <cmath>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

class BiquadFilter {
public:
    void setPeaking(float sampleRate, float freq, float q, float gainDb) {
        if (sampleRate <= 0.0f) {
            sampleRate = 48000.0f;
        }
        float clampedFreq = freq;
        if (clampedFreq < 10.0f) clampedFreq = 10.0f;
        if (clampedFreq > sampleRate * 0.45f) clampedFreq = sampleRate * 0.45f;

        float safeQ = (q <= 0.05f) ? 1.0f : q;
        float a = std::pow(10.0f, gainDb / 40.0f);
        float omega = 2.0f * static_cast<float>(M_PI) * clampedFreq / sampleRate;
        float sinOmega = std::sin(omega);
        float cosOmega = std::cos(omega);
        float alpha = sinOmega / (2.0f * safeQ);

        float b0 = 1.0f + alpha * a;
        float b1 = -2.0f * cosOmega;
        float b2 = 1.0f - alpha * a;
        float a0 = 1.0f + alpha / a;
        float a1 = -2.0f * cosOmega;
        float a2 = 1.0f - alpha / a;

        if (std::abs(a0) < 1e-9f) {
            reset();
            return;
        }

        b0_ = b0 / a0;
        b1_ = b1 / a0;
        b2_ = b2 / a0;
        a1_ = a1 / a0;
        a2_ = a2 / a0;
    }

    void reset() {
        b0_ = 1.0f;
        b1_ = 0.0f;
        b2_ = 0.0f;
        a1_ = 0.0f;
        a2_ = 0.0f;
        z1_ = 0.0f;
        z2_ = 0.0f;
    }

    float process(float in) {
        float out = b0_ * in + z1_;
        z1_ = b1_ * in - a1_ * out + z2_;
        z2_ = b2_ * in - a2_ * out;
        return out;
    }

private:
    float b0_ = 1.0f, b1_ = 0.0f, b2_ = 0.0f;
    float a1_ = 0.0f, a2_ = 0.0f;
    float z1_ = 0.0f, z2_ = 0.0f;
};
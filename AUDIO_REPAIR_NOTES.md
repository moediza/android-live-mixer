# OHO AUDIO MIXER – Audio repair notes

This revision focuses on safer playback DSP defaults for the app's existing output-mix chain.

## Changes

- Flat 10-band EQ is now the default instead of the previous strongly boosted curve.
- EQ band center frequencies now match the displayed labels (31 Hz through 16 kHz).
- DANGDUT, KARAOKE, and CAMPURSARI presets use gentler boosts to preserve headroom.
- Post-EQ is flat by default to avoid stacking additional low-frequency boosts on top of the graphic EQ.
- Multiband compressor defaults use a gentler 2:1 ratio and zero makeup gain.
- Left/right output gain defaults to 0 dB rather than +6 dB.
- Preamp compensation now considers positive EQ/tone boosts across all bands.
- Output limiter settings are faster and use a more conservative -1.5 dB threshold.
- A one-time migration converts known old factory EQ/dynamics settings to safer equivalents while preserving settings that do not match those old factory profiles.
- DynamicsProcessing is still attached to Android audio session 0 (output mix), intended to cover WebView playback, SoundPool and MediaPlayer through the shared output mix. If the device firmware does not support global output effects, the app falls back to the global Equalizer where available.

## Important limitations

- Bluetooth adds buffering and codec latency; an app cannot guarantee zero Bluetooth delay or force every headset to use a particular codec/bitrate.
- Android/OEM audio policy may bypass or limit global output-mix effects on some devices, especially with offloaded playback. This needs real-device testing.
- The native Oboe microphone monitor is not the shared playback DSP path for YouTube, SoundPool and BGM; it was not enabled as a workaround.
- This is a source-code revision. An Android APK build/device playback test was not performed in this environment because the project does not include a Gradle wrapper and an Android SDK/Gradle build environment was not available.

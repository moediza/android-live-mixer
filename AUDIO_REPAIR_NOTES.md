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

## Bass Punch follow-up

- The SUB-BASS PUNCH button now applies a dedicated post-EQ contour at 45/75/120/350 Hz (+1.5/+3.5/+3.0/+0.5 dB). This targets kick and bass definition rather than relying only on compressor makeup gain.
- When Bass Punch is enabled, the SUB and LOW multiband ratios are relaxed to 1.5:1 with a -12 dB threshold and +0.5 dB post gain, reducing excessive transient flattening.
- Smart preamp trim accounts for the +3.5 dB post-EQ peak to preserve headroom.
- Disabling Bass Punch restores the post-EQ bands and SUB/LOW dynamics to their neutral defaults.
- The effect still depends on Android/OEM support for output-mix session 0 effects; Bluetooth headset EQ, codec, and speaker response can change the audible result.

## Subwoofer Engine + Bass Punch V2 integration (source revision)

- Added a Subwoofer Engine toggle in the Crossover tab.
- Added adjustable virtual-bass focus (40–160 Hz), virtual SUB level (0–6 dB), and Deep Bass (0–4 dB).
- Integrated the virtual-sub contour with the existing Bass Punch V2 post-EQ. The combined low-frequency boost is capped per post-EQ band at +6 dB, and smart preamp compensation now accounts for the combined Bass Punch / virtual-sub lift.
- Added automatic output-device enumeration using Android `AudioDeviceCallback` and `AudioManager.getDevices(GET_DEVICES_OUTPUTS)`. The UI reports Bluetooth A2DP, USB, HDMI, wired, and built-in output categories and refreshes when output devices are added/removed.
- Saved the new engine settings in the existing session preferences.
- Important hardware limitation: this project still uses a two-channel `DynamicsProcessing` session-0 chain. The new device detection does **not** split PCM into an independent physical SUB channel, even when a USB/HDMI endpoint advertises more than two channels. Bluetooth A2DP remains stereo. The UI explicitly reports this and uses the virtual stereo-sub path instead. A true dedicated SUB output requires a separate multichannel PCM renderer/router (e.g. Oboe/AAudio or equivalent) and source-specific audio routing; that is not implemented in this revision.
- Build/device verification was not possible in this environment: this source archive has no Gradle wrapper and no Android SDK/Gradle installation was available. Archive integrity and source-level edits only were checked; no APK or playback test is claimed.

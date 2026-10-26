#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
root="$PWD"
media_commit=c35a9d62baec57118ea898e271ac66819399649b
ffmpeg_commit=c41ff724ede7da657762d61097e26fac296c53bf
mkdir -p .native-sources decoder-ffmpeg/upstream
if [ ! -f .native-sources/media.tar.gz ]; then
  curl --fail --location --retry 3 "https://github.com/androidx/media/archive/$media_commit.tar.gz" -o .native-sources/media.tar.gz
fi
if [ ! -f .native-sources/ffmpeg.tar.gz ]; then
  curl --fail --location --retry 3 "https://github.com/FFmpeg/FFmpeg/archive/$ffmpeg_commit.tar.gz" -o .native-sources/ffmpeg.tar.gz
fi
if [ ! -f decoder-ffmpeg/upstream/src/main/jni/build_ffmpeg.sh ]; then
  tar -xzf .native-sources/media.tar.gz --strip-components=3 -C decoder-ffmpeg/upstream "media-$media_commit/libraries/decoder_ffmpeg"
fi
native="$root/decoder-ffmpeg/upstream/src/main"
if [ ! -f "$native/jni/ffmpeg/configure" ]; then
  mkdir -p "$native/jni/ffmpeg"
  tar -xzf .native-sources/ffmpeg.tar.gz --strip-components=1 -C "$native/jni/ffmpeg"
fi
if [ ! -f "$native/jni/ffmpeg/android-libs/x86_64/libavcodec.a" ]; then
  bash "$native/jni/build_ffmpeg.sh" "$native" "$ANDROID_HOME/ndk/26.1.10909125" linux-x86_64 21 aac mp3 ac3 eac3 dca truehd mlp vorbis opus flac alac amrnb amrwb pcm_mulaw pcm_alaw
fi
# LGPL sources and reproducible build instructions accompany the installer.
mkdir -p installer/audio-sources app/src/main/assets/licenses
cp .native-sources/*.tar.gz installer/audio-sources/
cp scripts/build-audio-decoders.sh decoder-ffmpeg/build.gradle.kts installer/audio-sources/
cp "$native/jni/ffmpeg/COPYING.LGPLv2.1" app/src/main/assets/licenses/FFmpeg-LGPL-2.1.txt
cp "$native/jni/ffmpeg/COPYING.LGPLv2.1" installer/audio-sources/
tar -xOf .native-sources/media.tar.gz "media-$media_commit/LICENSE" > app/src/main/assets/licenses/Media3-Apache-2.0.txt
cp THIRD_PARTY_AUDIO.md installer/audio-sources/

#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
root="$PWD"
media_commit=c35a9d62baec57118ea898e271ac66819399649b
vpx_commit=d168454ecd099805c675d4a98c66f4891373302a
gav1_commit=c05bf9be660cf170d7c26bd06bb42b3322180e58
cpu_commit=ba4bffa86cbb5456bdb34426ad22b9551278e2c0
mkdir -p .native-video-sources .native-video-build
fetch() {
  if [ ! -f ".native-video-sources/$1.tar.gz" ]; then
    curl --fail --location --retry 3 --connect-timeout 30 --max-time 300 "$2" -o ".native-video-sources/$1.tar.gz"
  fi
}
fetch vpx "https://github.com/webmproject/libvpx/archive/$vpx_commit.tar.gz"
fetch gav1 "https://chromium.googlesource.com/codecs/libgav1/+archive/$gav1_commit.tar.gz"
fetch cpu "https://github.com/google/cpu_features/archive/$cpu_commit.tar.gz"
for codec in vp9 av1; do
  if [ ! -d "decoder-$codec/upstream/src" ]; then
    mkdir -p "decoder-$codec/upstream"
    tar -xzf .native-sources/media.tar.gz --strip-components=3 -C "decoder-$codec/upstream" "media-$media_commit/libraries/decoder_$codec"
  fi
done
vpx="$root/decoder-vp9/upstream/src/main/jni/libvpx"
if [ ! -f "$vpx/configure" ]; then
  mkdir -p "$vpx"
  tar -xzf .native-video-sources/vpx.tar.gz --strip-components=1 -C "$vpx"
fi
av1="$root/decoder-av1/upstream/src/main/jni"
if [ ! -f "$av1/libgav1/CMakeLists.txt" ]; then
  mkdir -p "$av1/libgav1"
  tar -xzf .native-video-sources/gav1.tar.gz -C "$av1/libgav1"
fi
if [ ! -f "$av1/cpu_features/CMakeLists.txt" ]; then
  mkdir -p "$av1/cpu_features"
  tar -xzf .native-video-sources/cpu.tar.gz --strip-components=1 -C "$av1/cpu_features"
fi
ndk="$ANDROID_HOME/ndk/26.1.10909125"
tools="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"
for abi in armeabi-v7a arm64-v8a x86_64; do
  output="$root/.native-video-build/$abi"
  if [ -f "$output/libvpx.a" ]; then continue; fi
  mkdir -p "$output"
  case "$abi" in
    armeabi-v7a) target=armv7-android-gcc; triple=armv7a-linux-androideabi21; extra=(--enable-neon);;
    arm64-v8a) target=arm64-android-gcc; triple=aarch64-linux-android21; extra=(--enable-neon);;
    x86_64) target=x86_64-linux-gcc; triple=x86_64-linux-android21; extra=(--disable-asm);;
  esac
  (
    cd "$output"
    CC="$tools/$triple-clang" CXX="$tools/$triple-clang++" LD="$tools/$triple-clang" AS="$tools/$triple-clang" AR="$tools/llvm-ar" RANLIB="$tools/llvm-ranlib" STRIP="$tools/llvm-strip" \
      "$vpx/configure" --target="$target" --enable-pic --enable-static --disable-shared \
        --disable-examples --disable-tools --disable-docs --disable-unit-tests \
        --disable-vp8 --disable-vp9-encoder --disable-webm-io --disable-libyuv \
        --enable-vp9-highbitdepth "${extra[@]}"
    make -j2 libvpx.a
  )
done
mkdir -p video-sources app/src/main/assets/licenses
cp .native-video-sources/*.tar.gz video-sources/
cp scripts/build-video-decoders.sh decoder-vp9/CMakeLists.txt THIRD_PARTY_VIDEO.md video-sources/
cp decoder-vp9/build.gradle.kts video-sources/vp9-build.gradle.kts
cp decoder-av1/build.gradle.kts video-sources/av1-build.gradle.kts
cp "$vpx/LICENSE" app/src/main/assets/licenses/libvpx-BSD.txt
cp "$vpx/PATENTS" app/src/main/assets/licenses/libvpx-PATENTS.txt
cp "$av1/libgav1/LICENSE" app/src/main/assets/licenses/libgav1-Apache-2.0.txt
cp "$av1/cpu_features/LICENSE" app/src/main/assets/licenses/cpu-features-Apache-2.0.txt

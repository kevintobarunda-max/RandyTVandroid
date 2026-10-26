# Additional video decoders

Media3 1.4.1 wrappers (Apache 2.0): c35a9d62baec57118ea898e271ac66819399649b. libvpx 1.15.2 (BSD, with PATENTS grant): d168454ecd099805c675d4a98c66f4891373302a. libgav1 0.20.0 (Apache 2.0): c05bf9be660cf170d7c26bd06bb42b3322180e58. Google cpu_features 0.9.0 (Apache 2.0): ba4bffa86cbb5456bdb34426ad22b9551278e2c0. Licenses are packaged in APK assets. Exact additional library archives and build configuration accompany the HY300-video-sources artifact. The Media3 source archive is in the HY300-audio-sources artifact of the same run.

Build audio sources first, then `bash scripts/build-video-decoders.sh` with NDK 26.1.10909125, CMake 3.22.1, JDK 17 and Gradle 8.2. Gradle builds the VP9 and AV1 JNI wrappers for armeabi-v7a, arm64-v8a and x86_64. VP9 statically links libvpx, so the app initializes VpxLibrary with vpxV2JNI as the single library. Library sources are otherwise unmodified; the app caps decoding to two threads and four input/output buffers.

Hardware video stays first. VP9 and AV1 have native software alternatives; H.264/HEVC retain the projector's installed Android decoders. Software decoding is more CPU intensive and cannot guarantee smooth high-resolution playback on a HY300. DRM is not bypassed. Adding these decoders does not add every codec or container, or repair unavailable stream links.

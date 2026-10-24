# RandyTV: Android 12 / 2 GB

## Changes
- One ExoPlayer, one video surface, no background channel playback or preloading.
- 5–15 seconds of sample buffering, target 12 MiB on devices with up to 3 GiB RAM; 24 MiB otherwise. This is not a cap on total app memory.
- On these devices adaptive selection prefers up to 720p, 30 fps and 2.5 Mbps. A single-rendition stream cannot be converted to 720p by the player; use the provider's SD/2MB channel if needed.
- Hardware decoder first, with decoder fallback for initialization failures.
- Artwork memory cache 16 MiB / disk cache 64 MiB; fixed decode sizes, remembered requests, no crossfade.
- Categories compose lazily; stable catalog item keys. Live grid no longer truncates at 150 channels.
- Catalog JSON parsed from response readers on IO; large catalogs load sequentially and publish state on Main.
- Real buffering/error state, retry button, no artificial percentage or repeated forced play.
- Pause is preserved across background/foreground; surface cleared on leaving the player.
- Release rules retain Gson models during shrinking.

## Build
Open with Android Studio, JDK 17, Android SDK 34. GitHub Actions builds debug/release, runs lint and uploads the debug APK.

## Device acceptance checks
Use the same network, channels and catalog before/after. Test a release APK for performance; debug APK is for installation checks.
1. Cold start five times: record time until TV categories are usable.
2. Scroll live, movies and series with the remote for two minutes each; check focus, images and search activation with OK.
3. Change channels quickly 20 times, forward and backward: only the last selected channel should play; no background audio.
4. Play SD/2MB live TV for 30 minutes; then 720p adaptive video for 30 minutes. Record stalls and dropped frames.
5. For VOD: pause, background/foreground (must stay paused), resume, seek, change audio/subtitles, leave and reopen.
6. Disconnect Wi-Fi, reconnect and retry. Check that errors do not leave a fake loading percentage.
7. Compare memory using Android Studio profiler or adb shell dumpsys meminfo com.randytv.mobile at idle, while scrolling and after 30 minutes of playback.

## Validation limits
Local assembleDebug/lintDebug could not start: this environment cannot reach services.gradle.org to download Gradle. No device, FPS, startup or memory measurements have been obtained. Adaptive limits do not guarantee codec compatibility or compensate for an overloaded stream server. The full catalogs are still held in memory; server-side paging is a separate future improvement.

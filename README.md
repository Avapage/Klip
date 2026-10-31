# Klip

Klip is a preview Android app for downloading video and audio directly on your device. It uses yt-dlp and FFmpeg via youtubedl-android. Android 10 (API 29) or newer is required.

## Features

- Video and audio format options, including quality limits and MP3.
- Video title and duration lookup when supported.
- Built-in browser that detects some direct video, audio, HLS and DASH links, prioritizes master playlists, and lets you retry another detected source.
- Cancel downloads and update the yt-dlp engine from the app.
- Downloads are saved to Downloads/Klip.

When you choose a detected media link, the app passes the observed referrer, user agent and cookies matching that media host to the downloader. Cookies are kept temporarily in private app storage and removed when the download completes or is canceled.

## Download and install

Open the repository's Releases page and choose an APK. **ARM64** is suitable for most modern Android phones; **ARMv7** is for compatible 32-bit devices; **x86_64** is for x86-64 devices or emulators. Choose **Universal** if unsure.

Download the APK directly and open it on your phone. Allow installation from that source if Android asks. This is a debug preview. If Android rejects an update because the signing key differs, uninstall the old version first. Existing files in Downloads are not deleted by the app.

## Limitations

The app has not been tested on every Android version or website. Some sites, videos requiring authentication, and DRM-protected videos may not work. Detection depends on how a site delivers its media. Keep Klip open during a download. Engine updates and site changes may affect compatibility. Download only content you are permitted to download.

## Build

Open this folder in Android Studio with JDK 17 and Android SDK 35, or run `gradle :app:assembleDebug` with Gradle 8.10.2. APKs appear under `app/build/outputs/apk/debug/`.

Uses [youtubedl-android](https://github.com/yausername/youtubedl-android), [yt-dlp](https://github.com/yt-dlp/yt-dlp), and FFmpeg. Each component is subject to its own license.

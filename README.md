# Klip - Video Downloader

Klip is an Android video and audio downloader that uses [yt-dlp](https://github.com/yt-dlp/yt-dlp) and FFmpeg to save supported media directly to your device. Paste a link or use the built-in browser to find supported video and stream URLs.

**Android 10 or newer** · No Klip download server · Free to use · Preview build

[Download the Android APK](https://github.com/Avapage/Klip/releases/tag/klip-v0.2.7-r1) · [Installation](#installation) · [Features](#features) · [Troubleshooting](#troubleshooting)

## Download Klip for Android

The three latest app versions are listed below. Choose 0.2.7 R1 for the latest changes.

| Version | Description | Download |
| --- | --- | --- |
| **0.2.7 R1** | Adds download history, a GPL-3.0 license, an English interface, and rotating quotes, with ARM64 and Universal APKs. | [Release and APKs](https://github.com/Avapage/Klip/releases/tag/klip-v0.2.7-r1) |
| **0.2.6** | Background downloads, clearer media-source labels, system-bar spacing, safer browser handoff, and a storage-space check. | [Release and APKs](https://github.com/Avapage/Klip/releases/tag/klip-v0.2.6) |
| **0.2.5** | R4 adds a completed-download card; R3 adds parallel HLS/DASH fragment downloads. | [Release and APKs](https://github.com/Avapage/Klip/releases/tag/klip-v0.2.5) |

For 0.2.7 R1, choose ARM64 for most modern Android phones, or Universal if you are unsure. Older releases may offer additional APKs.

## Features

- Download supported video or extract audio as MP3 or its original format.
- Choose a preferred video quality of 1080p, 720p, or 480p. If a matching format is unavailable, Klip may fall back to another quality.
- Browse websites within the app and detect some direct video, audio, HLS `.m3u8`, and DASH `.mpd` links.
- Reduce some pop-ups and requests to known advertising domains using the built-in browser protection.
- Download up to eight HLS/DASH fragments concurrently when the source and downloader support it.
- Run a user-started download in the background while Android permits the foreground service to run.
- Open a completed download from the app. Files are saved to **Downloads/Klip**.
- View up to 20 recent Klip files saved in Downloads/Klip after restarting the app. Removed files no longer appear.
- See a different short saying from a collection of 100 each time the app is opened.
- Cancel downloads and check for yt-dlp updates automatically or manually.

Klip runs the downloader on the device. Actual download speed depends on the source and your connection. Not every site, media format, or video is supported.

## Installation

1. Download the APK for your device from the links above.
2. Open the APK on Android and allow installation from that source if prompted.
3. Open Klip and paste a supported link, or share a link to the app.
4. Choose a format and start the download. A foreground notification appears while the download runs.
5. Open the completed file from Klip or find it in **Downloads/Klip**.

This is a **debug preview**. If Android rejects an update because its signing key differs, uninstall the previous build and install the new APK. Files already saved in Downloads remain there; app settings and browser sessions are removed when the app is uninstalled.

## Downloading from websites

If a page link is not recognized, open it in Klip's browser, play the video, and check **Detected media** for available media links. Select a detected source, then choose a format and download it. Detection depends on how each website delivers its media.

The optional browser protection uses a small built-in list of advertising domains and blocks pop-up windows. It does not block every ad and can be turned off if a site does not work.

## Troubleshooting

- **Unsupported URL:** Update the yt-dlp engine in Klip, or try detecting a media link in the built-in browser. A site may still be unsupported.
- **Playback fails:** Open the saved file with a compatible video or audio player.
- **Download fails or is slow:** A website may limit connections, require authentication, or change how it serves media.
- **No media detected:** Play the video in the browser first. Some protected or dynamically delivered media cannot be detected.
- **Update will not install:** Debug builds may have different signing keys. Uninstall the previous build before installing the new one.

DRM-protected content and some login-dependent videos may not work. Download only content you are permitted to access and download.

## Privacy

Downloads are processed on your device without a Klip download server. When you select a media link in the browser, Klip can pass the observed referrer, user agent, and cookies applicable to the media host to yt-dlp. A temporary cookie file is stored in the app's private cache and is removed when the download process completes or is canceled. The app contacts source websites and update providers over the internet.

## Build from source

Open this repository in Android Studio with **JDK 17** and **Android SDK 35**, or use the included Gradle wrapper:

```bash
./gradlew :app:assembleDebug
```

APKs are created in `app/build/outputs/apk/arm64/debug/` and `app/build/outputs/apk/universal/debug/`. The project uses [youtubedl-android](https://github.com/yausername/youtubedl-android), [yt-dlp](https://github.com/yt-dlp/yt-dlp), and FFmpeg; each dependency has its own license. See [third-party notices](THIRD_PARTY_NOTICES.md).

## License

Klip's own code is licensed under [GPL-3.0-only](LICENSE). Bundled dependencies have their own licenses and distribution requirements; see [third-party notices](THIRD_PARTY_NOTICES.md).

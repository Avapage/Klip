# Klip - Video Downloader

Klip is an Android video and audio downloader that uses [yt-dlp](https://github.com/yt-dlp/yt-dlp) and FFmpeg to save supported media directly to your device. Paste a link or use the built-in browser to find supported video and stream URLs.

**Android 10 or newer** · No Klip download server · Free to use · Preview build

[Download the Android APK](https://github.com/Avapage/Klip/releases/tag/klip-v0.2.6) · [Installation](#installation) · [Features](#features) · [Troubleshooting](#troubleshooting)

## Download Klip for Android

The three latest app versions are listed below. Choose 0.2.6 for the latest changes.

| Version | Description | Download |
| --- | --- | --- |
| **0.2.6** | Clearer media-source labels, system-bar spacing, safer browser handoff, and a storage-space check. | [Release and APKs](https://github.com/Avapage/Klip/releases/tag/klip-v0.2.6) |
| **0.2.5** | R4 adds a completed-download card; R3 adds parallel HLS/DASH fragment downloads. | [Release and APKs](https://github.com/Avapage/Klip/releases/tag/klip-v0.2.5) |
| **0.2.4** | Earlier version with media detection and download format options. | [Release and APKs](https://github.com/Avapage/Klip/releases/tag/klip-v0.2.4) |

Choose ARM64 for most modern Android phones. ARMv7, x86_64, and a universal APK are also available on the release pages.

## Features

- Download supported video or extract audio as MP3 or its original format.
- Choose a preferred video quality of 1080p, 720p, or 480p. If a matching format is unavailable, Klip may fall back to another quality.
- Browse websites within the app and detect some direct video, audio, HLS `.m3u8`, and DASH `.mpd` links.
- Reduce some pop-ups and requests to known advertising domains using the built-in browser protection.
- Download up to eight HLS/DASH fragments concurrently when the source and downloader support it.
- Open a completed download from the app. Files are saved to **Downloads/Klip**.
- Cancel downloads and check for yt-dlp updates automatically or manually.

Klip runs the downloader on the device. Actual download speed depends on the source and your connection. Not every site, media format, or video is supported.

## Installation

1. Download the APK for your device from the links above.
2. Open the APK on Android and allow installation from that source if prompted.
3. Open Klip and paste a supported link, or share a link to the app.
4. Choose a format and start the download. Keep the app open while downloading.
5. Open the completed file from Klip or find it in **Downloads/Klip**.

This is a **debug preview**. If Android rejects an update because its signing key differs, uninstall the previous build and install the new APK. Files already saved in Downloads remain there; app settings and browser sessions are removed when the app is uninstalled.

## Downloading from websites

If a page link is not recognized, open it in Klip's browser, play the video, and check **Media terdeteksi** for available media links. Select a detected source, then choose a format and download it. Detection depends on how each website delivers its media.

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

Open this repository in Android Studio with **JDK 17** and **Android SDK 35**, or use Gradle 8.10.2:

```bash
gradle :app:assembleDebug
```

APKs are created in `app/build/outputs/apk/debug/`. The project uses [youtubedl-android](https://github.com/yausername/youtubedl-android), [yt-dlp](https://github.com/yt-dlp/yt-dlp), and FFmpeg; each dependency has its own license.

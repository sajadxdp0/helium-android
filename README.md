# Helium for Android

An independent Android browser prototype inspired by Helium. It brings a lightweight, privacy-minded browsing interface to Android using the system WebView engine.

> **Unofficial project:** this is a separate community project and is not affiliated with or endorsed by the Helium maintainers.

## Features

- Multi-tab browsing with session restoration.
- Address bar that opens URLs or searches with DuckDuckGo by default; Kagi, Brave Search, and Startpage are also available.
- Bookmarks, local browsing history, downloads, and page sharing.
- Privacy controls for third-party cookies, Safe Browsing, and a small built-in tracker-host blocklist.
- Secure-only mixed content, an insecure-page indicator, and denied website requests for geolocation and camera/microphone access.
- A clear-browsing-data action for local history, cookies, cache, and site storage.

## Browser engine and privacy

The app uses **Android System WebView** supplied by the device. It does not include Helium's patched desktop Chromium engine or uBlock Origin, and its small tracker blocklist does not provide equivalent filter coverage. Private browsing is not implemented yet. The selected search provider receives searches, and websites receive requests needed to load their pages.

## Requirements

- Android 9 (API 28) or newer.
- JDK 17 or newer.
- Android SDK Platform 37 and Android Build Tools 36.0.0.

## Build

Set `ANDROID_HOME` to your Android SDK location, then run:

```sh
ANDROID_HOME="$HOME/Android/Sdk" ./gradlew assembleDebug
```

The Gradle wrapper downloads the configured Gradle version on the first build. The debug APK is created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Project notes

This repository is independent from the supplied Helium source archive. The Helium branding asset is credited in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). See [LICENSE](LICENSE) for the project license.

## Repository description

> An independent Android browser prototype inspired by Helium, built on Android System WebView.

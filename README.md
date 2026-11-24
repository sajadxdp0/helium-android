<p align="center">
  <img src="helium-mark.svg" alt="Helium geometric H on a red rounded square" width="104">
</p>

<h1 align="center">Helium for Android</h1>

<p align="center">
  A privacy-minded, native Android browser with a Rust-owned interface and Mozilla GeckoView.
</p>

<p align="center">
  <a href="https://github.com/sajadxdp0/helium-android/releases/latest">Download the latest release</a>
</p>

> An independent Android browser project. It uses the Helium name but is not affiliated with the Helium desktop maintainers.

## Overview

Helium for Android is a lightweight browser built around Android's native view system. Rust owns browser state, navigation, address handling, persistence, update logic, and constructs the UI through JNI. A small Kotlin layer connects Android lifecycle and platform APIs to Mozilla GeckoView, the web engine.

## Features

- Tabs, address/search, back and forward, reload, bookmarks, and recent history.
- DuckDuckGo, Kagi, Brave Search, and Startpage search providers.
- Local app preferences with session state restored across launches.
- GeckoView tracking protection, restricted third-party tracking cookies, and Global Privacy Control.
- Android and site permission requests denied by default.
- Clear site data, cookies, cache, and browsing history from settings.
- GitHub release check and repository links from About.
- System-aware light and dark appearance.

## UI/UX research and decisions

This is a pattern review of official Android browser documentation and store listings, not a usability study. Helium adopts task-focused patterns without copying competitors' visual identity or feature sprawl:

- **Chrome:** its Android address bar can move between the top and bottom, and a toolbar shortcut can be customized. This reinforces address/search as the primary action and thumb reach as a real tradeoff. Helium keeps its top address bar without adding another configurable layer.
- **Firefox:** its home screen supports shortcuts, bookmarks, and return-to-browsing sections. Helium surfaces saved sites and recent pages without adding a news feed.
- **Samsung Internet:** its bottom toolbar exposes frequent navigation and tab actions. Helium keeps compact back, forward, home, bookmark, and menu actions at the bottom.
- **Brave:** Shields communicates privacy controls in page context. Helium describes only implemented protections; it does not invent per-site block counts or controls.

The browser keeps search and navigation central, uses a compact address row and bottom actions, and presents user-owned bookmarks and history on its home screen. The address bar uses a monospace typeface for URL scanning.

Primary references: [Chrome Android toolbar position](https://support.google.com/chrome/answer/14181646?hl=en&co=GENIE.Platform%3DAndroid), [Chrome toolbar shortcut](https://support.google.com/chrome/answer/14835450?co=GENIE.Platform%3DAndroid&hl=en), [Firefox home screen](https://support.mozilla.org/en-US/kb/customize-firefox-home-screen), [Samsung Internet toolbar](https://www.samsung.com/us/support/answer/ANS10012955/), and [Brave Shields](https://support.brave.com/hc/en-us/articles/360022806212-How-do-I-use-Shields-while-browsing).

## Browser engine and privacy

This app embeds **Mozilla GeckoView** as its web engine. The app code and interface live in this repository; it does not include Helium desktop source code. GeckoView's built-in tracking protection is not equivalent to Helium desktop's filter set. The app does not include extensions, per-site permission controls, private tabs, or a browser download manager.

Search queries are sent to the selected provider. Websites receive the requests needed to load pages. Location, camera, microphone, and other content permission requests are denied by default.

## Architecture

- **Rust (`native/`):** browser state, address resolution, bookmarks/history, persistence, action handling, app panels, and native-view construction.
- **Kotlin (`app/src/main/java/`):** minimal Android Activity lifecycle and JNI adapters for platform intents, update HTTP requests, and GeckoView sessions/views.
- **GeckoView:** third-party browser engine and Android view/session implementation.

The app is not a zero-Kotlin or zero-third-party-engine port. GeckoView is Android's `View`-based API, so Kotlin remains at the platform and engine boundary while app-facing state and UI behavior run in Rust.

## Build requirements

- Android SDK Platform 37.2, Android NDK 29, and Android Build Tools compatible with AGP 9.4.
- JDK 17 or newer.
- Rust toolchain with `aarch64-linux-android`, `armv7-linux-androideabi`, and `x86_64-linux-android` targets, plus `cargo-ndk` (`cargo install cargo-ndk`).
- Network access to Google's Maven repositories, Mozilla's GeckoView Maven repository, and crates.io on first build.
- The Gradle wrapper downloads Gradle 9.9.2 the first time it runs if that version is not cached.

Gradle builds the Rust `cdylib` for arm64-v8a, armeabi-v7a, and x86_64 and packages it into the APK. The project uses Android Gradle Plugin 9.4.1, built-in Kotlin 2.4.10, GeckoView `160.0.20261116130455`, and the Rust crate in `native/`.

## Build and install

```sh
ANDROID_HOME="$HOME/Android/Sdk" ./gradlew :app:assembleDebug
```

The debug build emits one APK per supported ABI:

| Target device | APK |
| --- | --- |
| 64-bit ARM Android device | `app-arm64-v8a-debug.apk` |
| 32-bit ARM Android device | `app-armeabi-v7a-debug.apk` |
| x86_64 emulator | `app-x86_64-debug.apk` |

Install the x86_64 emulator build with:

```sh
adb install -r app/build/outputs/apk/debug/app-x86_64-debug.apk
```

Run the Rust state tests with `cargo test --manifest-path native/Cargo.toml`. Android debug and release builds run through the Gradle wrapper.

## References

- [Mozilla GeckoView consumer guide](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/geckoview-quick-start.html)
- [Android NDK concepts](https://developer.android.com/ndk/guides/concepts)
- [Android adaptive icon guidance](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive)

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for notices about embedded libraries. See [LICENSE](LICENSE) for the project license.

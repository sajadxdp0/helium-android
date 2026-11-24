<p align="center">
  <img src="osmium-mark.svg" alt="Osmium app icon: white O-ring and S on red" width="104">
</p>

<h1 align="center">Osmium Android</h1>

<p align="center">
  A privacy-minded Android browser with a Material 3 Expressive Jetpack Compose UI,
  a Rust-owned browser core, and Mozilla GeckoView.
</p>

<p align="center">
  <a href="https://github.com/sajadxdp0/osmium-android/releases/latest">Download the latest release</a>
  ·
  <a href="https://github.com/sajadxdp0/osmium-android">Source repository</a>
</p>

## Overview

Osmium Android is a lightweight browser built with a Material 3 Expressive Jetpack Compose interface. Rust owns browser state and browser logic, including navigation, address handling, persistence, and action handling. A small Kotlin layer connects Android lifecycle and platform APIs to Mozilla GeckoView, the web engine.

Osmium keeps the established Android application ID so existing installs can update in place and retain their local browser data; the visible app, namespace, Rust library, and repository are Osmium.

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

This is a pattern review of official Android browser documentation and store listings, not a usability study. Osmium adopts task-focused patterns without copying competitors' visual identity or feature sprawl:

- **Chrome:** its Android New Tab page can show site shortcuts and optionally hide cards or Discover. Osmium keeps the address/search bar and locally saved site shortcuts while omitting feeds and extra cards.
- **Firefox:** its home screen supports shortcuts, bookmarks, and return-to-browsing sections. Osmium keeps saved sites on its start page and leaves history accessible from the menu.
- **Samsung Internet:** its bottom toolbar exposes frequent navigation and tab actions. Osmium keeps compact back, forward, home, bookmark, and menu actions at the bottom.
- **Brave:** Shields communicates privacy controls in page context. Osmium describes only implemented protections; it does not invent per-site block counts or controls.

The new-tab page uses the address bar as the single search entry point, then surfaces the Osmium mark and a small row of saved favorites. Bookmarks and history remain available from the browser menu. The address bar uses a monospace typeface for URL scanning.

Primary references: [Chrome Android New Tab page](https://support.google.com/chrome/answer/11032183?hl=en&co=GENIE.Platform%3DAndroid), [Chrome Android toolbar position](https://support.google.com/chrome/answer/14181646?hl=en&co=GENIE.Platform%3DAndroid), [Chrome toolbar shortcut](https://support.google.com/chrome/answer/14835450?co=GENIE.Platform%3DAndroid&hl=en), [Material 3 icon buttons](https://m3.material.io/components/icon-buttons/guidelines), [Firefox home screen](https://support.mozilla.org/en-US/kb/customize-firefox-home-screen), [Samsung Internet toolbar](https://www.samsung.com/us/support/answer/ANS10012955/), and [Brave Shields](https://support.brave.com/hc/en-us/articles/360022806212-How-do-I-use-Shields-while-browsing).

## Browser engine and privacy

Osmium embeds **Mozilla GeckoView** as its web engine. GeckoView's built-in tracking protection is not equivalent to filter lists or protections in other browsers. The app does not include extensions, per-site permission controls, private tabs, or a browser download manager.

Search queries are sent to the selected provider. Websites receive the requests needed to load pages. Location, camera, microphone, and other content permission requests are denied by default.

## Architecture

- **Rust (`native/`):** browser state and logic, address resolution, bookmarks/history, persistence, action handling, and app behavior.
- **Kotlin (`app/src/main/java/`):** Android Activity lifecycle and JNI adapters for platform intents, update HTTP requests, and GeckoView sessions/views.
- **Jetpack Compose:** Android UI built with Material 3 Expressive.
- **GeckoView:** third-party browser engine and Android view/session implementation.

GeckoView is Android's `View`-based API, so Kotlin remains at the platform and engine boundary while Rust owns browser state and logic.

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

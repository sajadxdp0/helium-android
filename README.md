<p align="center">
  <img src="helium-mark.svg" alt="Helium geometric H on a red rounded square" width="104">
</p>

<h1 align="center">Helium for Android</h1>

<p align="center">
  A privacy-minded Android browser with native Compose UI, Mozilla GeckoView, and a Rust URL resolver.
</p>

<p align="center">
  <a href="https://github.com/sajadxdp0/helium-android/releases/latest">Download the latest release</a>
</p>

> An independent Android browser project; it uses the Helium name but is not affiliated with the Helium desktop maintainers.

## What works

- Native Kotlin/Jetpack Compose browser chrome with saved sites, recent pages, navigation, tabs, menus, and settings.
- Mozilla GeckoView browsing sessions; a small original Rust JNI library resolves URLs and search queries.
- Address bar for URLs and searches, with DuckDuckGo, Kagi, Brave Search, and Startpage options.
- Local bookmarks and recent history.
- Built-in GeckoView tracking protection, restricted third-party tracking cookies, and Global Privacy Control.
- Site requests for Android permissions are denied by default.
- Clear history, cookies, cached files, and site storage from settings.
- Update check against GitHub Releases and direct repository access from About.
- System-aware light and dark themes, dynamic color on Android 12+, edge-to-edge layout, and an expanded-window navigation rail.

## UI/UX research and decisions

This is a pattern review of official Android browser documentation and store listings, not a usability study. Helium adopts task-focused patterns without copying competitors' visual identity or feature sprawl:

- **Chrome:** its Android address bar can move between the top and bottom, and a toolbar shortcut can be customized. This reinforces address/search as the primary action and thumb reach as a real tradeoff. Helium keeps its top address bar and existing bottom navigation rather than adding another configurable layer.
- **Firefox:** its home screen supports shortcuts, bookmarks, and return-to-browsing sections. Helium's home already surfaces saved sites and recent pages; keep these user-owned destinations, without adding a news feed.
- **Samsung Internet:** its bottom toolbar exposes frequent navigation and tab actions. Helium keeps its compact back, forward, home, bookmark, and menu controls at the bottom; tabs remain one tap away beside the address field.
- **Brave:** Shields is reachable from the address bar and communicates privacy controls in page context. Helium keeps its address-bar privacy entry point but reports only protections it actually implements; it does not invent per-site block counts or controls.

The review identified two avoidable costs in the browser chrome: the brand lockup consumed space while a page was open, and focusing the URL left the cursor inside the existing address. The brand lockup is now home-only, the tab switcher shares the address row, and focusing the address selects the complete current URL for quick replacement. The red identity, personalized home, GeckoView content, and bottom navigation remain intact. URLs use monospace text for easier scanning.

Primary references: [Chrome Android toolbar position](https://support.google.com/chrome/answer/14181646?hl=en&co=GENIE.Platform%3DAndroid), [Chrome toolbar shortcut](https://support.google.com/chrome/answer/14835450?co=GENIE.Platform%3DAndroid&hl=en), [Firefox home screen](https://support.mozilla.org/en-US/kb/customize-firefox-home-screen), [Samsung Internet toolbar](https://www.samsung.com/us/support/answer/ANS10012955/), and [Brave Shields](https://support.brave.com/hc/en-us/articles/360022806212-How-do-I-use-Shields-while-browsing).

## Browser engine and privacy

This app embeds **Mozilla GeckoView** as its web engine. The Android app code is authored in this repository; it does not include Helium desktop source code or branding assets. GeckoView's built-in tracking protection is not equivalent to Helium desktop's filter set. The app does not yet include extensions, per-site permission controls, private tabs, or a browser download manager.

Searches are sent to the selected search provider. Websites receive the requests needed to load the pages you open. Website permission requests for location, camera, microphone, and other content permissions are currently denied by default.

## Build requirements

- Android SDK Platform 37.2, Android NDK 29, and Android Build Tools compatible with AGP 9.4.
- JDK 17 or newer.
- Rust toolchain with `aarch64-linux-android`, `armv7-linux-androideabi`, and `x86_64-linux-android` targets, plus `cargo-ndk` (`cargo install cargo-ndk`).
- Network access to Google's Maven repositories, Mozilla's GeckoView Maven repository, and crates.io on first build.
- The Gradle wrapper downloads Gradle 9.9.2 the first time it runs if that version is not cached.

Gradle builds the Rust `cdylib` for arm64-v8a, armeabi-v7a, and x86_64 and packages it into the APK. The project uses Android Gradle Plugin 9.4.1, built-in Kotlin 2.4.10 with the Compose compiler plugin, Compose BOM `2026.11.01`, GeckoView `160.0.20261116130455`, and Rust crate `native/`.

## Build and install

Set `ANDROID_HOME` to your Android SDK directory, then run:

```sh
ANDROID_HOME="$HOME/Android/Sdk" ./gradlew assembleDebug
```

The debug build emits one APK per supported ABI:

| Target device | APK |
| --- | --- |
| 64-bit ARM Android device | `app-arm64-v8a-debug.apk` |
| 32-bit ARM Android device | `app-armeabi-v7a-debug.apk` |
| x86_64 emulator | `app-x86_64-debug.apk` |

Release builds create matching ABI-specific APKs in `app/build/outputs/apk/release/`. Choose the APK matching your device; no universal APK is generated. This avoids downloading GeckoView and Rust native libraries for other processor architectures.

Install the x86_64 emulator build with:

```sh
adb install -r app/build/outputs/apk/debug/app-x86_64-debug.apk
```

## Implementation references

- [Mozilla GeckoView consumer guide](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/geckoview-quick-start.html)
- [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [Compose setup and dependencies](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler)
- [Android adaptive icon guidance](https://developer.android.com/develop/ui/views/launch/icon_design_adaptive)
- [Android edge-to-edge guidance](https://developer.android.com/develop/ui/compose/system/setup-e2e)

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for notices about embedded libraries. See [LICENSE](LICENSE) for the project license.


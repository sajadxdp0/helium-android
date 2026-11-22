<p align="center">
  <img src="helium-mark.svg" alt="Helium snowflake on a red rounded square" width="104">
</p>

<h1 align="center">Helium for Android</h1>

<p align="center">
  A privacy-minded Android browser with native Compose UI, Mozilla GeckoView, and a Rust URL resolver.
</p>

<p align="center">
  <a href="https://github.com/sajadxdp0/helium-android/releases/latest">Download the latest release</a>
</p>

> An independent community project; not affiliated with or endorsed by the Helium maintainers.

## What works

- Native Compose browser chrome, home screen, navigation, tab switcher, menus, and settings.
- GeckoView rendering with a dedicated session for each open tab.
- Address bar for URLs and searches, with DuckDuckGo, Kagi, Brave Search, and Startpage options.
- Local bookmarks and recent history.
- Built-in GeckoView tracking protection, restricted third-party tracking cookies, and Global Privacy Control.
- Site requests for Android permissions are denied by default.
- Clear history, cookies, cached files, and site storage from settings.
- System-aware light and dark themes, dynamic color on Android 12+, edge-to-edge layout, and an expanded-window navigation rail.

## Browser engine and privacy

This app embeds **Mozilla GeckoView**. It does not use Android System WebView, and it does not contain Helium's Chromium patches or Helium's uBlock Origin fork. GeckoView's built-in tracking protection is not equivalent to Helium desktop's filter set. The app does not yet include extensions, per-site permission controls, private tabs, or a browser download manager.

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

The debug APK is created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install it on a connected device with:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Helium references

The supplied `helium-main.zip` is the Helium desktop source repository. Its README describes a Chromium-based desktop browser and points to platform packaging and the Helium uBlock Origin fork. Its patches and tooling informed this project's privacy and product direction; they are not an Android implementation and were not copied into this app.

- [Helium desktop project](https://github.com/imputnet/helium)
- [Mozilla GeckoView consumer guide](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/geckoview-quick-start.html)
- [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [Compose setup and dependencies](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler)
- [Android edge-to-edge guidance](https://developer.android.com/develop/ui/compose/system/setup-e2e)

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for notices about the Helium branding asset and embedded libraries. See [LICENSE](LICENSE) for the project license.


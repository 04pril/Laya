# Laya Android customization port

Android changes live on `android-ios-parity`; this work does not publish an Android release or replace the iOS IPA.

## Behavior

- Korean/English app language, including Android-native settings and shared player resources. All translatable keys in the phone's base string catalogue have Korean entries.
- Shared lyrics preferences: whole text or Korean/Latin size and weight, preview, blur and one combined source/change visibility toggle. Android preferences use `android_` keys; existing iOS `ios_` keys and their migration are preserved.
- Hide Listen Together, hide video and open full player on track selection default to enabled. The output drawer stays available through a background-free device glyph. Android uses the existing Material output glyphs, including earbuds for AirPods and headphones for AirPods Max; SF Symbols remain an Apple platform resource.
- Tablet/large-window full-player layouts reuse the iOS work: portrait cover/compact panel header and landscape deck with a side panel, one-line lyrics retained outside the expanded lyric panel. The original capped mini-player/tab bar remains.
- Downward stop on the standard mini-player and both expanded/inline glass states pauses, stops and clears the Media3 queue; Listen Together locks still apply.
- Queue selection removes upcoming entries by queue-entry ID. Undo is available only while the exact resulting queue/current entry is unchanged and inserts removed entries without restarting the current track.
- Playlist picker search preserves selected playlists while filtering. Explicit library removal is separated from unliking. The playing-track menu resolves the originating owned playlist and exact occurrence before offering deletion.
- Optional automatic download of existing and newly liked tracks. Android 8/9 request the normal storage permission when enabling it. Uses the normal downloader and Wi-Fi policy; unliking keeps existing files. Notifications' successful likes also enqueue when allowed.
- Color 4×2/2×2 widgets are added beside existing widget styles. Cover-derived wash and cover are baked together in an opaque bitmap; no light/dark branch changes their color. Existing playback intents and persisted snapshots are reused.
- Notification/lock-screen media taps explicitly open the player. Android Auto's existing controller detection drives an optional keep-screen-awake setting while the app is visible. iOS re-signing/App Group repair and Dynamic Island have no Android implementation to copy.

## Proxy

- Direct, built-in userspace WireGuard, HTTP, HTTPS (TLS to the proxy) and SOCKS5. The first-run page precedes name entry and has direct `.conf` import and the same settings entry used in the app. An imported/configured profile skips that page after full process restart.
- Adapted from our BitChord Android implementation. `.conf` validation is native; storage uses AndroidKeyStore AES-GCM and no-backup storage. Import and mode changes restart the full process, rather than only recreating an activity.
- App startup fixes one route before creating shared clients. API, Media3 streams, normal downloads, Coil and module HTTP clients inherit that route. TLS-to-proxy verifies the proxy hostname independently of the target origin.
- Chromium is a separate transport. `AppProxyWebView.prepare()` checks that saved and active settings agree and applies AndroidX WebKit `ProxyController` before the Google page is loaded. Unsupported providers, blocked native startup and stale settings prevent login rather than loading directly.
- WireGuard's local proxy remains authenticated. The WebView auth handler only answers the exact loopback host and WireGuard realm, not origin authentication challenges. Provider support for proxy 407 callbacks and live Google authentication/remote peer egress require device verification. There is no system VPN or claim of an OS-wide kill switch.
- Native source/Go/NDK pin and library hashes: `ANDROID-WIREGUARD-PROVENANCE.json`. Rebuild with `ANDROID_HOME=… bash scripts/build-android-wireguard.sh`. GPL and dependency notices ship in app assets. The upstream JNI package name is retained as the ABI, not user-facing branding.

## Build

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME="$HOME/Library/Android/sdk" \
./gradlew :app:assembleProdDebug -PlayaLocalAbi=arm64-v8a
```

Without `layaLocalAbi`, the original ABI split/universal settings remain. The embedded WireGuard payload is supplied for arm64-v8a and x86_64. A 32-bit build fails closed if WireGuard is selected; it is not the delivered APK.

The local APK uses the Android debug signing key, not the fork's release key. Existing installations signed by another key cannot be updated with this APK. The public iOS version/source and release files are unaffected.

## Validation

- Native WireGuard Go tests, including local userspace peers and UDP, pass. Both compiled ELF payloads pass the upstream ABI/16KB/NX/RELRO checks.
- JVM tests cover routes, actual TLS proxy requests to an unresolvable origin, invalid proxy hostname rejection, independent Android/iOS preference persistence and queue removal/undo.
- Android compile/APK assembly and the shared iOS Kotlin compile pass. Thirteen shared JVM tests and seven shared UI tests pass (including 21 rendered tablet scenes).
- Android 11 / WebView 91 device instrumentation passes: actual Chromium CONNECT goes to the configured proxy; target TLS works inside TLS to the proxy; both color widget RemoteViews inflate in light and dark configurations with unchanged opaque media pixels. Korean onboarding and proxy settings have been observed, including changing language from the first page.
- The older Android 9 / WebView 66 provider reports no proxy-override support and login is blocked, as intended. Update WebView on such devices.
- Live Google account login, a real configured WireGuard peer and its egress, Bluetooth device names/icons, Android Auto and launcher widget behavior still need real-device testing.

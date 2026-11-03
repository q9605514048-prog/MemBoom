# MemBoom 0.10.0 Preview — Explosion UI

The website and native Android interface share a dark crimson-and-gold design, clearer navigation, masked account details, dedicated editors, note reading, grouped settings, and English/Russian support. Manga and anime remain website-only; no chibi characters are included.

The website can follow a public YummyAnime profile approximately every 60 seconds while open, visible, unlocked and online. This requires a running MemBoom API. Source statuses, favorites, titles and covers update while local progress and notes are preserved. Removed source entries are retained locally.

## Android installation

Download `MemBoom-Preview-Android.apk`. Android 8.0+ is required. It installs as **MemBoom Preview**, alongside the existing MemBoom app, using a separate application ID. It does not update or read the old app's private data.

The original Android signing key is unavailable, so a compatible update of the previous app cannot currently be distributed. Keep the old app installed. Export an encrypted backup from it or the website, import it into Preview, and verify that accounts and notes are present before making any changes to the old installation. Keep the backup password.

This is a debug preview, not a stable release. The previous v0.9.1 APK remains available separately. No independent security audit has been performed.

Validation included 129 website/API tests, 97 Android tests per debug/release variant, native emulator workflows, encrypted web backup round trips, and responsive browser checks.

Feature requests are welcome: open an issue to suggest something you would like added.

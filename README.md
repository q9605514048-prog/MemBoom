# MemBoom

A dark red-and-gold vault for accounts and notes. Store data locally, keep encrypted backups, and move accounts and notes between the website and Android.

[Русская инструкция](README.ru.md)

## Try it

- **[Open the website](https://q9605514048-prog.github.io/MemBoom/)** — choose **Try without a server**, then create a master password.
- **[Download the Android APK](https://github.com/q9605514048-prog/MemBoom/releases/latest/download/MemBoom-Android.apk)** — Android 8.0 or newer. Choose **Try without a server** after installation.
- [All releases](https://github.com/q9605514048-prog/MemBoom/releases).

The interface supports **English and Russian**. Use the language selector on the website, or **Settings → Language / Язык** on Android. Your choice is remembered. Account names, notes, passwords, and imported content are not translated.

The website remembers entry by default after a successful unlock. Keep **Remember sign-in on this device** enabled to reopen without a password. **Lock** clears remembered entry; uncheck the option on shared devices. Anyone using the same browser profile can open a remembered vault. Clearing browser data also removes this setting.

Read the [Terms of Use in English and Russian](https://q9605514048-prog.github.io/MemBoom/terms.html). Android includes both texts offline on the welcome screen and in Settings.

This is a preview, not an independently audited password manager. Start with fictional data. Lost master passwords cannot be recovered. Export an encrypted backup before clearing browser data, uninstalling the app, or updating.

## Features

- Accounts with categories, custom fields, favorites, search, and a password generator.
- Separate notes with pinning, search, and links to Google Drive or Yandex Disk files.
- Encrypted backup export and import between the website and Android.
- A local mode that needs no account, registration, or server.
- Optional registration and manual sync through your own MemBoom server.
- Website only: manga and anime lists, statuses, progress, and import. Automatic cover loading requires your own API.

Website data belongs to the current browser and origin; Android data belongs to the installed app. The public demo does not provide a shared account server or automatic cross-device sync.

## Feature requests

**Want something added? I'm happy to add new features based on your requests.** [Open a feature request](https://github.com/q9605514048-prog/MemBoom/issues/new?template=feature-request.yml) and tell me what you would like MemBoom to do and how it would help you. Your suggestions will help shape future updates.

For bugs, [open an issue](https://github.com/q9605514048-prog/MemBoom/issues) with the app version and steps to reproduce. Never include real passwords, tokens, or personal backups.

## Run your own server

Install Node.js 24, download the source, and run this from the project root:

```sh
node tools/server/start.mjs
```

Open `http://127.0.0.1:8788/`. On Windows, you can use `Запустить сервер.bat`. The first launch creates `server/.env` with a random secret. This file and `server/data/` are private and excluded from Git.

For other devices, configure your own HTTPS endpoint and enter it in the app. GitHub Pages serves the static website only; it does not run the API.

## Development

| Directory | Contents |
| --- | --- |
| `Application/` | Website without a bundler or external runtime JS dependencies |
| `clients/android/` | Native Kotlin Android client |
| `clients/windows/` | Windows client and .NET core |
| `server/` | Node.js API |
| `locales/` | Shared English interface translations |
| `tools/` | Build, packaging, and verification scripts |

Run website and API checks:

```sh
node tools/i18n/build.mjs
node --test Application/tests/*.test.mjs server/tests/*.test.mjs
```

For Android, install JDK 17 and Android SDK 36, then run:

```sh
cd clients/android
./gradlew testDebugUnitTest assembleDebug
```

On Windows, use `gradlew.bat`. The APK is written to `app/build/outputs/apk/debug/`. The public APK is a debug preview build, not a Google Play release. Signing keys are not included.

Windows sources are included, but the current Windows client requires your own server. A standalone public Windows release is not included.

See [security notes](SECURITY.md), [Android](clients/android/README.md), and [server setup](server/README.md).

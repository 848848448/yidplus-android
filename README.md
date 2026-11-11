# YID PLUS for Android

The YID PLUS app: a fast, full-screen Android app around **https://yidplus.com**, with its own
in-app updater. The app loads the live site, so every change to yidplus.com shows up in the app
right away — an app update is only needed when the app itself changes.

**Download:** https://github.com/848848448/yidplus-android/releases/latest/download/YIDPLUS.apk

## What the app does
- Kotlin, single `WebView` activity (`MainActivity.kt`); the site drives the whole UI.
- Splash screen, no white flash, system bars take the colour of the page's top and bottom edge.
- Stays signed in (persistent cookies). Google sign-in runs in a browser tab (Google blocks it in
  web views) and comes back through `yidplus://auth` + `/api/auth/native-exchange`, the hand-off
  the site already has.
- Pull to refresh (only when the panel under your finger is at its top; off on Shorts, which has its own).
- Offline screen that reloads by itself when the network returns.
- Full-screen video (rotates sideways), camera + microphone for calls, file upload from gallery or
  camera, downloads (DownloadManager) and saving `blob:` files such as music (`assets/app.js`).
- `navigator.share` → Android share sheet. yidplus.com links open in the app (App Links); every
  other link opens in its own app or the browser.
- Launcher shortcuts: Shorts, Chats, Check for updates.

## Updates
`Updater.kt` reads `releases/latest/download/version.json` on every start, when the app comes back
after 12 hours, and once a day in the background (WorkManager, with a notification). If its
`versionCode` is higher it shows **Update available** → downloads `YIDPLUS.apk` → checks the
SHA-256 and that it is signed with the same key → hands it to Android's installer.

### Shipping an update
1. Change the app, describe it in `RELEASE_NOTES.md` (shown in the update dialog).
2. Push a tag: `git tag v1.0.1 && git push origin v1.0.1` (or run **Build & release APK** by hand).
3. GitHub Actions builds and signs the APK, tests it on an Android emulator (screenshots go to the
   `qa-evidence` branch), and publishes the release. Phones see the update next time they open the app.

`versionCode` comes from the version: `1.2.3` → `10203`.

## Signing
The release key lives **only** in this repository's Actions secrets (`KEYSTORE_B64`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS`). It is never in the repo. Every update must be signed with the
same key, so don't delete those secrets.

Certificate SHA-256: `C5:E5:0A:23:52:1C:F2:CB:9B:23:5D:28:19:29:02:EC:B8:BE:05:83:57:2C:7F:0A:D6:76:E1:08:91:14:45:21`

## Not in v1
Push notifications (needs your own Firebase project + `google-services.json`).

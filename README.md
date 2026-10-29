# Aura Island AI

A Dynamic Island-inspired pill for Android. Cordova provides the settings/preview UI; a Kotlin plugin draws a real native overlay (`WindowManager`), listens to notifications and detects charging. Original code and assets; not affiliated with Apple.

**Implemented (v0.1):** compact/expanded pill with animation, real notification previews, charging activity, simple priority (notification > charging), duplicate suppression, hide-preview setting, permission onboarding.
**Behaviour:** the app is locked to portrait, and the island hides itself whenever the screen turns landscape (video, games) and returns in portrait.
**Not yet:** music controls, timer/stopwatch, AI assistant/backend. Planned in later phases.

## Website (GitHub Pages)
The landing page lives in `site/` (black and white, live island demo, Lenis smooth scroll, Instrument Serif + Inter). It has a direct **Download APK** button pointing at `releases/latest/download/AuraIsland.apk`.
1. Repo **Settings → Pages → Build and deployment → Source: GitHub Actions** (one time; the workflow stops with a clear message until this is done, then use *Re-run jobs*).
2. Push to `main`; the *Deploy website* workflow publishes it at `https://<user>.github.io/<repo>/`.
3. The button works once you have published a release (see below).

## Get the APK
- **Every push:** GitHub → *Actions* → latest *Build APK* run → *Artifacts* → `AuraIsland-debug-apk`.
- **Releases:** `git tag v0.1.0 && git push origin v0.1.0` → the *Release* workflow publishes the APK under *Releases*.
- Or run either workflow manually via *Actions → Run workflow*.

The APK is debug-signed, so it installs directly (enable "Install unknown apps"). A Play-Store style signed release needs your own keystore stored in GitHub Secrets.

## Use on POCO M2 Pro (MIUI 14)
1. Open the app, tap **Grant overlay permission**, allow it.
2. Tap **Open notification access**, enable *Aura Island*.
3. Set Autostart on and battery to **No restrictions** (MIUI kills background services otherwise).
4. Turn on **Enable island**, then **Show test notification**.

## Local build (Windows)
Needs Node 20, JDK 17, Android SDK 34.
```
npm install
npm run setup
npm run build:debug
```
APK: `platforms/android/app/build/outputs/apk/debug/app-debug.apk`

## Privacy
Notification text is shown only in the overlay and never stored or uploaded. Dismissing the island never dismisses the Android notification.

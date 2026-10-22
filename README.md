# ClipBoost

Free Android app that improves video quality **on the phone itself** — no server, no account, no internet.

Flow: **Select video → pick a mode → Enhance → compare before/after → save to gallery.**

> Status: **M1 (first working version)**. Classic GPU filters (denoise, sharpen, resize, re-encode) work end to end.
> AI modes (Upscale, Smooth FPS, Restore, Anime, Gaming) are visible but marked *soon* and cannot be started —
> ClipBoost never passes a plain filter off as AI. See [docs/ROADMAP.md](docs/ROADMAP.md).

## Repository layout

```
android/            Gradle project
  core/             pure Kotlin: video facts, quality metrics, planner, job model (+ unit tests)
  app/              Android app: Compose UI, Media3 pipeline, background service
docs/               architecture, roadmap, model notes
.github/workflows/  free CI build that produces an installable APK
```

## Get the APK without installing anything (free)

1. Push this folder to a GitHub repository (public repos get unlimited free Actions minutes).
2. Open **Actions → Android → latest run → Artifacts → `clipboost-debug-apk`** and download it.
3. Copy `app-debug.apk` to the phone and open it (allow "install unknown apps" for your file manager).

Requires Android 10 (API 29) or newer.

## Build locally

Requirements: JDK 17+ and the Android SDK (Android Studio installs both).

```bash
cd android
./gradlew :core:test          # unit tests for the analysis/planner logic
./gradlew :app:assembleDebug  # APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug   # install on a connected phone (USB debugging on)
```

Or open the `android/` folder in Android Studio and press Run.

## What works in this version

| Feature | State |
|---|---|
| Pick from gallery (Photo Picker) or files (MKV, WebM, MOV, MP4…) | ✅ |
| Analysis: resolution, FPS, duration, codec, audio, rotation, aspect, size, HDR | ✅ |
| Measured noise and sharpness (3 sample frames) | ✅ |
| Auto: applies only what the measurements call for, or says "nothing to fix" | ✅ |
| Denoise / Sharpen / Custom (downscale, levels, H.264/HEVC) — Classic GPU filters | ✅ |
| Real progress from Media3 Transformer, cancel, background processing + notification | ✅ |
| Job IDs persisted; a job killed by Android shows as *Interrupted* with *Start again* | ✅ |
| Before/After with draggable divider, full-screen playback, save to Movies/ClipBoost | ✅ |
| AI Upscale, Smooth FPS, Restore, Anime, Gaming | ⏳ roadmap M3–M5 |

## Privacy

The app has **no INTERNET permission**. Videos are read through the system picker and processed locally;
results live in app-private storage until you save or discard them. Logs contain only error types.

More: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) · [docs/ROADMAP.md](docs/ROADMAP.md) · [docs/MODELS.md](docs/MODELS.md)

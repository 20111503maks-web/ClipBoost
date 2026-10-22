# Roadmap

Rule: one stable chain first, then models. Everything stays free (on-device, open source).

| Phase | Scope | Status |
|---|---|---|
| **M0 Foundations** | Gradle project, `:core` + `:app`, theme, Enhancer/plan model, free CI APK build, docs | ✅ done |
| **M1 MVP end-to-end** | Pick → analyze → preview → Classic processing → progress → compare → save | ✅ code done — needs a run on real phones |
| **M2 Robustness** | Test-clip matrix on 3+ phones, thermal pause/resume, Transformer resume after interruption, instrumented tests | next |
| **M3 AI Upscale + Auto** | ncnn + Vulkan via JNI, Real-ESRGAN compact (x4v3), tiling by memory, CPU fallback, caps (input ≤ 720p, ≤ 60 s) | |
| **M4 Smooth FPS** | RIFE (ncnn), 24/30 → 48/60, scene-cut detection, skip low-motion clips | |
| **M5 Profiles + Custom** | Restore, Anime (Real-ESRGAN animevideov3), Gaming presets; Custom upscale targets; 4K for short clips | |
| **M6 Release** | Signed APK on GitHub Releases (free); optional Google Play ($25 one-time) / F-Droid | |

## M1 acceptance — clips to test on a real phone

Portrait 1080×1920 H.264 · landscape 4K HEVC 10-bit (HDR) · square 720p · MOV with rotation tag ·
MKV with Opus · WebM VP9 · variable-frame-rate phone clip · clip without audio · truncated file
(must fail cleanly) · 2 GB file · app swiped away mid-job (must show *Interrupted*).

## Known limits of M1

- Restarting an interrupted job starts from the beginning (no frame checkpoints yet — M2).
- Denoise/sharpen are classic GPU filters; heavy compression artifacts need the AI Restore mode (M5).
- Codecs the phone has no hardware/software decoder for (e.g. old MPEG-4 Part 2 AVI on some phones) are refused with a clear message.
- Analysis thresholds (`Planner.NOISE_*`, `SOFT_SHARPNESS`) are first estimates; tune them on the test-clip matrix.

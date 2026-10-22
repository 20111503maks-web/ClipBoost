# ClipBoost architecture

Written for the next developer or AI agent: read this first, then `android/core`.

## Decision: everything on the phone

The product must be free to run. A GPU server costs money every hour, so all processing happens on the
device. Trade-offs we accept: slower processing, heat on long clips, and codecs limited to what the
phone can decode. Everything is kept behind interfaces so a server backend could be added later
without rewriting the app.

## Modules

```
:core  (pure Kotlin/JVM, no Android)        :app  (Android)
  model/VideoInfo, QualityMetrics             analysis/VideoAnalyzer   MediaExtractor + MediaMetadataRetriever
  analysis/FrameMetrics  (noise, sharpness)   pipeline/ProcessingEngine Media3 Transformer
  enhance/Mode, Level, EnhancementPlan        pipeline/ClassicEffects   GLSL denoise + sharpen (GlEffect)
  enhance/Planner  (measurements → plan)      service/ProcessingService foreground service, job queue
  job/Job, JobState, JobError, estimators     data/JobRepository        JSON store of jobs (survives restarts)
                                              export/GallerySaver       MediaStore → Movies/ClipBoost
                                              ui/…                      Compose screens + MainViewModel
```

`:core` holds every decision that can be unit-tested without a phone. `:app` holds I/O and UI only.

## Data flow

1. **Pick** — Photo Picker (gallery) or `OpenDocument` (files; reaches MKV/WebM the gallery skips).
   Persistable read permission is taken when the provider allows it.
2. **Analyze** (`VideoAnalyzer`, IO thread) — container facts from `MediaExtractor`/`MediaMetadataRetriever`;
   decoder availability via `MediaCodecList.findDecoderForFormat`; three frames at 20/50/80 % scaled to
   ~640 px → `FrameMetrics` (Immerkær noise σ, Laplacian variance). Failures map to `JobError`, never a crash.
3. **Plan** (`Planner`) — pure function of `(mode, VideoInfo, QualityMetrics, CustomSettings)`.
   Returns `Ready(plan)`, `NothingToDo(reason)` or `Unavailable(reason)`. AI modes return `Unavailable`
   while `Capabilities.aiAvailable == false`. Raising resolution without AI is refused.
4. **Job** — `Job` with id `cb_xxxxxxxxxx` is written to `files/jobs.json` before work starts.
5. **Process** (`ProcessingService` → `ProcessingEngine`) — one job at a time, foreground service
   (`mediaProcessing` on Android 15+, `dataSync` before). Checks free space and source access first, then runs
   Transformer: hardware decode → `DenoiseEffect` → `SharpenEffect` → `Presentation.createForShortSide`
   → hardware encode. Audio is passed through when the MP4 muxer accepts it, otherwise Transformer transcodes it.
   Progress comes from `Transformer.getProgress`, polled every 500 ms; when unavailable the UI shows an
   indeterminate bar — never a fake number.
6. **Result** — output in `files/outputs/<id>.mp4`. Compare screen plays source and result in two
   ExoPlayers on `TextureView`s (clippable), kept in sync; the "before" layer is clipped by the divider.
7. **Save** — copied into MediaStore (`Movies/ClipBoost`, `IS_PENDING` while writing). No storage permission.

## Job states

```
CREATED → PREPARING → PROCESSING → FINALIZING → DONE
any active → FAILED(JobError) | CANCELED | INTERRUPTED
FAILED | CANCELED | INTERRUPTED → CREATED   (user taps "Start again")
```

Transitions are validated in `JobState.canMoveTo`. On app start, `JobRepository` turns any job still marked
active into `INTERRUPTED` (the service lives in the app process, so it can't still be running) and deletes
its partial output.

## Errors handled

| Situation | Where | Result |
|---|---|---|
| Damaged file / no video track | analyzer, `ERROR_CODE_DECODING_FAILED` | `DAMAGED_FILE` message |
| Codec the phone can't decode | analyzer (decoder lookup), Transformer | `UNSUPPORTED_CODEC` |
| Not enough storage | estimate before start, ENOSPC during | `NO_SPACE` |
| Encoder refuses settings | Transformer encoder errors | `ENCODER_FAILED` |
| Source no longer readable | open check before start | `SOURCE_UNAVAILABLE` |
| User cancels | service cancels Transformer, deletes partial file | `CANCELED` |
| App killed | repository recovery on next launch | `INTERRUPTED` |
| Android 15 service time limit | `onTimeout` | `INTERRUPTED` |

No internet is used, so network errors can't occur.

## Adding an AI stage (M3+)

1. Add the model runtime in a new module (planned: `:enhance-ncnn`, C++ via JNI, ncnn + Vulkan, CPU fallback).
2. Implement it as a Media3 `GlEffect` (texture in/out) or as a frame-batch stage between decode and encode
   when the model needs CPU/Vulkan buffers.
3. Add an `EnhancementPlan` field (e.g. `aiUpscale: Int?`) and emit a `Stage(…, StageKind.AI)`.
4. Flip `Capabilities.aiAvailable` per detected device support; the Planner and UI already handle the rest.
5. Add checkpointing (every N frames) so a long AI job can resume after interruption.

## Conventions

- Never label a classic filter as AI. `StageKind` is shown to the user next to every stage.
- Never change aspect ratio: resizing is short-side only; players are sized to the display aspect.
- Never log file names, URIs or pixels — only error types and job IDs.

# AI model shortlist (on-device)

Nothing here is integrated yet. Before bundling any model: re-check the license file in its repository and
the license of the weights (they can differ from the code), and measure speed/memory on real phones.

| Task | Candidate | License (code) | Fit for phones | Decision |
|---|---|---|---|---|
| Upscale (general video) | Real-ESRGAN `realesr-general-x4v3` (compact SRVGG net) | BSD-3-Clause | small, designed for speed | **M3 pick** |
| Upscale (anime) | Real-ESRGAN `realesr-animevideov3` | BSD-3-Clause | small | **M5 pick** for Anime |
| Upscale (heavy) | Real-ESRGAN `x4plus` (RRDB) | BSD-3-Clause | slow on phones | Restore mode, short clips only |
| Frame interpolation | RIFE v4 (ncnn port exists) | MIT | workable on mobile GPUs | **M4 pick** |
| Frame interpolation | FILM | Apache-2.0 | too heavy | rejected for phones |
| Video super-resolution | BasicVSR++ | Apache-2.0 | too heavy | rejected for phones |
| Denoise (AI) | SCUNet | Apache-2.0 | heavy | later, only if a mobile port performs |
| Runtime | ncnn + Vulkan | BSD-3-Clause | built for mobile, CPU fallback | **chosen runtime** |
| Paid cloud upscalers | — | commercial | costs money, uploads video | rejected |

Why ncnn: free, Android-first, Vulkan GPU backend, and Real-ESRGAN/RIFE already have ncnn ports, which
removes most conversion risk. LiteRT (TensorFlow Lite) GPU delegate is the fallback option if a model
converts more cleanly there.

Honesty rule: a stage is shown as **AI** only if a neural network actually ran on that frame.

<div align="center">

# ⚡ Exlab TTS
### Offline Neural Text-to-Speech Engine for Android

[![Android](https://img.shields.io/badge/Android-11%2B-brightgreen.svg)](https://android.com)
[![Engine](https://img.shields.io/badge/Powered%20by-Sherpa--ONNX-blue.svg)](https://github.com/k2-fsa/sherpa-onnx)
[![License](https://img.shields.io/badge/License-GPL%20v3-orange.svg)](LICENSE)
[![Privacy](https://img.shields.io/badge/Privacy-100%25%20Offline-success.svg)](#)

**High-fidelity, ultra-low latency voice synthesis directly on your device. Zero telemetry. No cloud required.**

---

</div>

## 🌟 Why Exlab?

Cloud-based TTS platforms charge per character and compromise data privacy. Exlab brings commercial-grade neural speech models directly onto your Android device:

- 🔒 **100% Offline & Private:** Audio synthesis runs completely on your CPU / NNAPI. Text never leaves your phone.
- 🎙️ **Dual Neural Engine:** Powered by state-of-the-art **Kokoro-82M** (studio grade) and **Piper / VITS** (ultra-fast).
- 🎨 **Cyberpunk Neon UI:** High-contrast AMOLED-friendly interface engineered for seamless workflow.
- ⚡ **Audio Export:** Instant high-bitrate `.wav` exports with full pitch and speed customization.

---

## 🎧 Supported Neural Models

| Model | Quality Tier | Synthesis Speed | Best Suited For |
| :--- | :--- | :--- | :--- |
| **Kokoro-82M** | Studio-Grade (Human Realism) | ~20-40s / min audio | Audiobooks, narration, voiceovers |
| **Piper / VITS** | Natural Multi-Speaker | ~5-10s / min audio | Fast previews, daily dialogue, skits |

---

## 🛠️ Technical Architecture

Exlab leverages native C++ inference through the Sherpa-ONNX runtime:

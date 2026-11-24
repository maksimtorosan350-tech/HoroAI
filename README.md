# HoroAI Private — local Android AI

Текстовый приватный AI-клиент для Android. Целевая конфигурация: Redmi Note 13 Pro 4G, 12 GB RAM.

## Local AI runtime
- llama.cpp is built from the upstream `ggml-org/llama.cpp` repository during GitHub Actions build.
- Native inference is compiled for `arm64-v8a`; CPU-only baseline, no cloud API.
- Selected model: `Qwen2.5-1.5B-Instruct` GGUF Q4_K_M from the official Qwen Hugging Face repository.
- Model is downloaded only after the user taps **Скачать нейросеть** in app settings, to app-private storage (`filesDir/models`). Approx. 1.12 GB.
- After download, prompts and chat history are processed on-device. Model is loaded for a reply and released afterwards; no background inference.
- Context is limited to 2048 tokens and generation to 384 tokens to reduce RAM/heat. Native inference uses four CPU threads.

## Privacy and network
The only network operation implemented in the app is the user-initiated download of the model file from Hugging Face. There is no remote inference endpoint in the chat path. Once model download is complete, chat generation does not need internet. The Android INTERNET permission remains solely for that first download. Chat history is encrypted with Android Keystore AES-GCM. Voice/microphone features are absent.

## Build APK
Requires JDK 17, Android SDK 35, NDK 27.2.12479018, CMake 3.22.1 and Gradle 8.9. Open `HoroAI` in Android Studio or use the included GitHub Actions workflow. The workflow clones upstream llama.cpp and builds `app-debug.apk` for `arm64-v8a`.

## Device notes
Qwen2.5-1.5B Q4_K_M is about 1.12 GB. The phone's 12 GB RAM is shared with Android and other apps. This configuration is a starting point, not a measured performance guarantee; speed, thermal behavior and memory use must be tested on the actual handset. Keep at least 2 GB free storage beyond the model download while installing/updating.

## Licenses
Check the upstream llama.cpp license and the Qwen model license in their source repositories before redistribution. This project downloads model weights separately and does not embed them in the APK.

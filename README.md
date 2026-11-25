# FaceLock Alarm (V1 starter project)

An Android alarm-clock starter project with a Material 3-inspired dark UI, daily exact alarm scheduling, foreground alarm playback/vibration, CameraX + ML Kit eye-open/blink challenge, and emergency PIN fallback.

## Build
- Android Studio with JDK 17 and Android SDK 35, or run `gradle assembleDebug`.
- GitHub Actions workflow builds a debug APK on push or manual dispatch.
- APK artifact: Actions → latest successful workflow → Artifacts → `FaceLockAlarm-debug`.

## Important limitations in this V1
- The camera challenge checks for a face and an eyes-closed-to-open blink transition. It is **not** robust biometric identity verification or certified anti-spoofing. A screen replay or sophisticated spoof may bypass it.
- No user face template is stored. The app checks for a live-looking face, not whether it is specifically the phone owner.
- Alarm delivery depends on Android exact-alarm permission, notification/full-screen permissions, OEM battery policies, and device volume settings. Test on the target phone.
- Emergency PIN is a local fallback. Set a private PIN before enabling the alarm.
- This is a starter project and needs device testing, accessibility review, and security hardening before daily reliance.

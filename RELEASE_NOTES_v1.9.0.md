# Fast Track v1.9.0 - Privacy & Biometrics

Version 1.9.0 introduces optional biometric authentication to keep your financial data secure from prying eyes, while maintaining Fast Track's signature sub-3-second entry experience.

## 🌟 Highlights
- **Biometric Lock**: Secure your expenses using your device's Fingerprint, Face Unlock, or PIN. This feature is strictly OPT-IN and can be enabled in settings.
- **Smart Grace Period**: Need to quickly check your bank app? Fast Track includes a 60-second grace period. If you switch out and back within a minute, you won't be bothered with another prompt.
- **Task Switcher Privacy**: When locked, the app's contents are obscured in the Android Recent Apps view, ensuring your financial figures aren't accidentally exposed.

## 🛠 Technical Details
- **Biometric Prompt API**: Migrated core activity to `FragmentActivity` to leverage modern AndroidX Biometrics.
- **Lifecycle Awareness**: The 60-second grace period is driven by `LifecycleEventObserver` responding to `ON_START` and `ON_STOP`.
- **Secure Window Flags**: Applies `FLAG_SECURE` dynamically via Kotlin Flows to protect sensitive data from OS-level screen captures and recent app previews.

---

### 📦 Downloads
- `app-release.apk` (Download below)
- **SHA-256 Checksum**: `7DEB5A385F20FB6A8D4000810DDE07B076573B3F0481B504B3907914C1FA3A29`

*Note: You may need to enable "Install from unknown sources" in your Android settings to install this APK.*

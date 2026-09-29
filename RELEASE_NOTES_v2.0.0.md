# Fast Track v2.0.0 - Production Milestone

Welcome to **Fast Track v2.0.0**! This release marks our official production milestone. We have focused on extreme optimization and OS integration to make Fast Track the fastest expense tracker on Android.

## 🚀 Extreme Performance (Baseline Profiles)
Android’s runtime compiler (JIT) can sometimes cause initial stutter. We’ve added **Baseline Profiles** which pre-compiles the most critical paths (like the main transaction screen, tags lazy row, and analytics charts) upon installation. Result: Lightning-fast cold starts with zero jank.

## ⚡ App Shortcuts
Long-press the Fast Track icon on your home screen to instantly access:
- **Catat Cepat**: Jump straight into the main entry keypad.
- **Analitik**: Open the analytics charts directly.

## 🛡️ Production Hardening
Our release builds are now aggressively minimized and obfuscated using **R8 (ProGuard)**. This significantly reduces the APK size and memory footprint while protecting the code structure.

---

### 📦 Downloads
- `app-release.apk` (Download below)
- **SHA-256 Checksum**: `37300361F84B0AF1743A46FAAC15FC946F113C746AEB4D6CE7998741BAE6BDBC`

*Note: You may need to enable "Install from unknown sources" in your Android settings to install this APK.*

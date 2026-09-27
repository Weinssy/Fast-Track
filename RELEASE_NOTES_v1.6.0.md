# Fast Track v1.6.0 - Glance Widget & Tactile Feedback

Fast Track v1.6.0 brings two of the most requested features: instant home screen visibility and a satisfying, mechanical feel to your data entry.

## 🌟 Highlights
- **Home Screen AppWidget**: We've introduced a dark-themed, sleek 2x1 / 3x1 widget that displays your total daily spending at a glance. No need to open the app to see where you stand for the day. Tapping the "+ Catat" button will instantly launch the keypad.
- **Tactile Micro-Haptics**: Typing expenses now feels distinctly mechanical. We've introduced precise haptic feedback for every digit entry and backspace, along with a distinct, longer haptic confirm when you save your transaction.

## 🛠 Technical Summary
- **Jetpack Glance**: Built with `androidx.glance:glance-appwidget` to modernize Android widget building with Compose-like syntax, while strictly enforcing IPC and RemoteViews constraints under the hood.
- **Zero Battery Drain**: The widget only updates its state when you save or delete a transaction. There are absolutely no background polling services or WakeLocks used, preserving your battery life entirely.
- **Compose Foundation Haptics**: Implemented `LocalHapticFeedback` to inject system-level tactile feedback securely and with zero latency across the UI stack.

---

### 📦 Downloads
- `app-release.apk` (Download below)
- **SHA-256 Checksum**: `[INSERT_CHECKSUM_HERE_BEFORE_PUBLISHING]`

*Note: You may need to enable "Install from unknown sources" in your Android settings to install this APK.*

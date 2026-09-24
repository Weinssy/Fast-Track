# Fast Track v1.5.0 - Custom Tag Management & Stability Polish

Fast Track v1.5.0 introduces much-needed control over your custom categories, bringing tag management to the next level while maintaining our signature zero-friction interface.

## 🌟 Highlights
- **Long-Press Deletion**: You can now easily delete custom tags you no longer need or created by mistake. Simply tap and hold (long-press) any custom tag to reveal the deletion prompt.
- **Defensive Preset Protection**: Our core preset tags (Umum, Makan, Transport, etc.) are permanently guarded. Long-pressing them does nothing, preventing accidental removal of essential categories.
- **State-Reset Safeguards**: If you delete a custom tag that is currently selected, the app will smartly reset your selection back to "Umum" so you can continue logging transactions immediately without a hitch.

## 🛠 Technical Summary
- **Room DAO Interaction**: Tag deletion is executed asynchronously on the background `Dispatchers.IO` thread, avoiding any UI blocking.
- **Compose Gesture Optimization**: We utilized the `combinedClickable` modifier from the Compose Foundation framework, ensuring that routine single-taps remain completely isolated and latency-free from the new long-press handler.
- **Memory Footprint**: Implementation adds negligible weight to the APK, retaining Fast Track's minimal production footprint.

---

### 📦 Downloads
- `app-release.apk` (Download below)
- **SHA-256 Checksum**: `[INSERT_CHECKSUM_HERE_BEFORE_PUBLISHING]`

*Note: You may need to enable "Install from unknown sources" in your Android settings to install this APK.*

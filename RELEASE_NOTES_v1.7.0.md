# Fast Track v1.7.0 - Quick Undo & Spending Limits

Fast Track v1.7.0 focuses on error recovery and passive budgeting, keeping your daily financial tracking lightning fast but now more forgiving and insightful.

## 🌟 Highlights
- **Quick Undo (Urungkan)**: Made a typo and hit save too fast? Don't worry. Immediately after saving an expense, a subtle 3-second popup (Snackbar) will appear. Tapping "Urungkan" will instantly delete the incorrect entry and restore your typed numbers back into the keypad so you can correct them.
- **Daily Spending Cap**: You can now set an optional daily spending limit via the new Settings icon in the top right. 
- **Visual Color Indicators**: Once a limit is set, your total "Hari Ini" text will smoothly transition colors. It stays neutral when you are safe, turns **Amber** when you reach 80% of your limit, and turns **Red** if you exceed it. This color indication automatically syncs with your Home Screen Widget!

## 🛠 Technical Summary
- **Jetpack DataStore**: Migrated settings infrastructure to `androidx.datastore:datastore-preferences`, replacing legacy SharedPreferences for asynchronous, type-safe budget limit storage.
- **State Management**: Undo states are tightly coupled with Room DAO's row-ID returns, ensuring the exact transaction is deleted and its specific amount/tag is seamlessly piped back into the active ViewModel StateFlow.
- **Floating Scaffold Layout**: The Snackbar is integrated cleanly into the Compose Scaffold layer, ensuring it floats directly above the keypad without shifting the touch targets or causing layout jumping.

---

### 📦 Downloads
- `app-release.apk` (Download below)
- **SHA-256 Checksum**: `9BE3F6FED0BE6A10795C3343B2D09990E688B4D83D4A96DA2A27EF0A27B0D832`

*Note: You may need to enable "Install from unknown sources" in your Android settings to install this APK.*

# Fast Track v1.8.0 - Portability & Safe Backups

Version 1.8.0 brings peace of mind and data mobility. Whether you are switching devices or just want a manual safety net, you can now backup and restore your entire Fast Track database offline with zero friction.

## 🌟 Highlights
- **Full Local Backup**: Export all your transactions, custom categories, and daily budget cap into a single, portable JSON file.
- **Safe Restore**: Instantly recover your database from a backup file. Fast Track employs atomic rollback logic, meaning if a backup file is corrupted or incomplete, the restore will cancel safely without wiping your existing data.
- **Modern Security**: By leveraging the modern Android Storage Access Framework, Fast Track doesn't require any invasive read/write permissions to access your storage.

## 🛠 Technical Summary
- **Lightweight Streaming Parser**: Bypassing heavy third-party JSON libraries (like Gson/Moshi), backups are parsed and serialized natively using `android.util.JsonReader` and `JsonWriter`. This keeps the app footprint tiny while enabling fast streaming logic.
- **Room @Transaction Atomicity**: Recovery procedures delete and re-insert thousands of rows within a single SQLite transaction wrapper via `withTransaction`. Any exception thrown during the pipeline immediately rolls back the database to its pristine state.
- **ActivityResultContracts**: Compose UI integrations for `CreateDocument` and `OpenDocument` allow permission-less I/O streams passed safely to `Dispatchers.IO` for execution.

---

### 📦 Downloads
- `app-release.apk` (Download below)
- **SHA-256 Checksum**: `00C5AD57B5D36DF8C2BCA405962505086172B4E21E5EDE72EF12B633C4AE934A`

*Note: You may need to enable "Install from unknown sources" in your Android settings to install this APK.*

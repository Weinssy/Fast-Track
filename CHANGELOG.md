# Changelog

## [1.4.0] - 2026-09-24
### Added
- **Dynamic Custom Tags**: Users can now create their own custom categories directly from the main screen by tapping the "+ Tambah" button.
- **Database Migration**: Smoothly migrated from static tag lists to a fully dynamic database model using Room without any data loss.
- **Legacy Normalization**: Older expenses marked as 'General' are automatically unified into 'Umum' for consistency.

## [1.3.1] - 2026-09-22
### Changed
- **Bahasa Indonesia Localization**: Seluruh UI, format tanggal, dan mata uang (`Rp`) kini sepenuhnya menggunakan standar dan bahasa Indonesia secara konsisten.
- **Kategori Bawaan**: Pilihan kategori yang tersedia kini disesuaikan dengan kebiasaan pengguna lokal (Umum, Makan, Transport, Belanja, Tagihan, Jajan).
- **Format CSV**: Pengaturan *header* CSV dan internalisasi nama file menggunakan format Indonesia.
## [1.3.0] - 2026-09-22
### Added
- **Lightweight Visual Analytics**: A completely new Analytics Screen accessed via the top app bar.
- **7-Day Bar Chart**: Custom-built using Jetpack Compose Canvas (zero third-party libraries) to display your weekly spending trends.
- **Category Breakdown**: View your expenditure grouped by tags with visual progress bars.

### Changed
- Smooth screen transition added between the main input view and analytics view.


## [1.2.0] - 2026-09-22
### Added
- **Fast Inline Tagging**: New horizontally scrollable Tag Selector directly above the keypad for lightning-fast categorization without dialogs or pop-ups.
- **Export Cache Housekeeping**: Automated cleanup routine that securely deletes exported CSV files older than 24 hours to prevent storage bloat.

### Changed
- UI layout adjustments to accommodate the inline tag row seamlessly.
- CSV export flow now triggers the cleanup routine automatically before generating new exports.


All notable changes to this project will be documented in this file.

## [1.1.0] - 2026-09-22
### Added
- **Local Data Backup via CSV Export**: You can now export your local transaction history to a CSV file and share it via any Android application (like Google Drive, Email, or WhatsApp) directly from the new top app bar. This provides a zero-friction data backup mechanism while keeping the app 100% local and offline.

## [1.0.0] - Initial Release
### Added
- Ultra-fast daily expense tracker core feature.
- Local persistence using Room Database.
- Swipe to delete transactions.
- Dark minimalist aesthetic.

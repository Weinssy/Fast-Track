# Changelog

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

# Fast Track ⚡

> **A minimalist, offline-first, sub-3-second expense tracker for Android.**  
> Built with modern native Android architecture: Jetpack Compose, Room Database, Material 3, and Jetpack Glance.

[![Release](https://img.shields.io/github/v/release/yourusername/fast-track?color=2CB67D&style=flat-square)](https://github.com/yourusername/fast-track/releases)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg?style=flat-square)](https://android-arsenal.com/api?level=26)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-7F52FF.svg?style=flat-square)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=flat-square)](LICENSE)

---

## 💡 Philosophy: Zero-Friction & Local-First

Most expense tracking apps fail because logging becomes a chore: waiting for cold cloud startups, closing budget modals, navigating multi-level dropdowns, or waiting for third-party keyboard popups.

**Fast Track** rejects bloat in favor of speed:
- **Sub-3-Second Input Loop:** A dedicated custom numeric keypad integrated into the viewport. Zero soft-keyboard latency, zero layout shifts.
- **Strict Privacy (No Cloud, No Accounts):** 100% offline-first. Your financial data stays on your local hardware in an encrypted/sandboxed SQLite database.
- **Lightweight Footprint:** Zero third-party analytics, zero cloud SDKs, zero heavy charting libraries. Native Compose `Canvas` renders all visualizations.

---

## ✨ Key Features

### ⚡ Rapid Entry & Micro-Interactions
- **Custom Keypad & Instant Haptics:** Calibrated tactile feedback (`TextHandleMove`) for rapid input with zero main-thread jank.
- **Inline Dynamic Categorization:** Fast horizontal chip selector with automatic post-save state resets (`Umum` fallback).
- **Custom Tag Management:** Long-press to delete user-created tags with immutable protection for preset tags.
- **Quick Undo Snackbar:** 3-second non-blocking recovery window to undo typos immediately, restoring previous input into the keypad.

### 📊 Visual Analytics & Budget Control
- **Zero-Dependency 7-Day Canvas Chart:** Custom-drawn weekly expenditure bars and category breakdown with zero external libraries.
- **Daily Spending Cap (DataStore):** Configurable daily budget limits with passive, dynamic color thresholds (Normal Emerald `#2CB67D`, Warning Amber `#F6AD55`, Exceeded Coral `#E53E3E`).
- **Complete Indonesian Localization:** Tailored terminology, Indonesian Rupiah formatting (`Locale("id", "ID")`), and native day naming.

### 📱 Android OS Integration
- **Jetpack Glance AppWidget:** Real-time "Hari Ini" total directly on your launcher. 100% event-driven updates (`updatePeriodMillis="0"`) ensuring zero background battery drain.
- **Launcher App Shortcuts:** Static shortcuts for "Catat Cepat" and "Analitik" straight from the app icon.
- **Biometric App Lock & Task-Switcher Shield:** Optional local authentication (Fingerprint/PIN) equipped with a 60-second smart grace period and `FLAG_SECURE` window obscuring.

### 💾 Backup, Export & Portability
- **Streaming JSON Backup & Restore:** Atomic, memory-safe (`O(1)` space complexity) full database restoration via Android Storage Access Framework (SAF).
- **CSV Data Exporter:** One-click spreadsheet export shared via secure `FileProvider` with automatic 24-hour cache housekeeping.

---

## 🏛 Architecture & Tech Stack

Fast Track strictly adheres to Modern Android Development (MAD) guidelines using a clean, unidirectional data flow (UDF) MVVM pattern:

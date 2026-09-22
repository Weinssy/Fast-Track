# Fast Track ⚡

![Build Status](https://img.shields.io/badge/build-passing-brightgreen)
![License](https://img.shields.io/badge/license-MIT-blue)
![Version](https://img.shields.io/badge/version-1.2.0-orange)

**Fast Track** is an ultra-fast, zero-friction daily expense tracker designed for Android. 

Traditional expense tracking apps often fail because they impose excessive input friction (mandatory category hierarchies, wallet selections, multi-step modals). Fast Track solves this by offering a lightning-fast interaction loop.

## Core Philosophy: Speed & Local-First
- **Zero Friction**: Launch app ➔ enter amount via built-in keypad ➔ tap Confirm ➔ saved in under 3 seconds.
- **Privacy & Local-First**: All data is stored locally on your device. No cloud sync, no tracking, complete privacy.
- **Dark Minimalist Aesthetic**: A dark monochromatic functional design prevents visual distraction so your eyes focus immediately on the numbers.

## Tech Stack
- **Language**: Kotlin
- **UI Toolkit**: Jetpack Compose (Material 3)
- **Local Database**: Room Persistence Library
- **Architecture**: MVVM with Clean Architecture principles
- **Reactive UI**: Coroutines & `StateFlow`

## Architecture Overview
The app follows a modern Android MVVM architecture:
- **`data/`**: Room entities (`Expense`) and DAOs (`ExpenseDao`).
- **`repository/`**: Abstraction layer over the local database.
- **`viewmodel/`**: `ExpenseViewModel` manages state (`FastTrackUiState`) and handles logic.
- **`ui/`**: Jetpack Compose UI screens (`FastTrackScreen`) and theme definitions (`Theme.kt`, `Color.kt`).

## Build & Setup Instructions

To build the project locally, clone the repository and open it in Android Studio (Koala or newer recommended).

### From Command Line:
```bash
# Debug APK
./gradlew assembleDebug

# Release APK (Generates optimized and obfuscated build)
./gradlew assembleRelease
```

## Roadmap
- [x] Add inline tagging/categorization for expenses.
- [ ] Implement daily/weekly analytical charts.
- [x] Add CSV export functionality for backing up data.
- [ ] Light mode support (currently strictly Dark Minimalist).

## Contributing
Please see our [Issue Templates](.github/ISSUE_TEMPLATE) before submitting bugs or feature requests. Pull requests are welcome!

## License
Distributed under the MIT License. See `LICENSE` for more information.

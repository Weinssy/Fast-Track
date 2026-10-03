# Fast Track ⚡

> **Aplikasi pencatat pengeluaran harian yang cepat, privat, dan local-first untuk Android.**  
> Dibangun dengan arsitektur Android modern: Jetpack Compose, Room Database, Material 3, dan Kotlin.

[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg?style=flat-square)](https://android-arsenal.com/api?level=26)
[![Kotlin](https://img.shields.io/badge/Kotlin-100%25-7F52FF.svg?style=flat-square)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=flat-square)](LICENSE)

---

## 💡 Filosofi: Kecepatan & Privasi Lokal

Sebagian besar aplikasi pencatat pengeluaran gagal karena pencatatan menjadi membosankan. **Fast Track** menghilangkan kerumitan demi kecepatan:

- **Input Ultra-Cepat:** Numpad khusus terintegrasi langsung di viewport. Zero latency, zero lag.
- **Privasi Ketat (Offline-First):** 100% lokal. Data finansial Anda aman di device dengan database SQLite terenkripsi.
- **Ringan & Efisien:** Tidak ada tracking, tidak ada cloud SDK, tidak ada library charting berat. Semua visualisasi dirender native dengan Compose Canvas.

---

## ✨ Fitur Utama

### ⚡ Input Cepat & Micro-Interactions
- **Numpad Kustom & Haptic Feedback:** Input angka ultra-responsif dengan sentuhan taktil yang dikalibrasi.
- **Kategorisasi Inline Dinamis:** Pilih kategori cepat dengan chip selector horizontal.
- **Manajemen Tag Custom:** Buat, edit, dan hapus tag dengan mudah. Tag preset terlindungi.
- **Undo Cepat:** Tombol undo 3-detik untuk membatalkan kesalahan input dengan cepat.

### 📊 Analitik Visual & Kontrol Budget
- **Grafik 7-Hari Kanvas Custom:** Visualisasi pengeluaran mingguan dan breakdown kategori tanpa library eksternal.
- **Batas Pengeluaran Harian:** Atur limit budget harian dengan indikator warna dinamis.
  - ✅ Normal (Emerald `#2CB67D`)
  - ⚠️ Peringatan (Amber `#F6AD55`)
  - ❌ Terlampaui (Coral `#E53E3E`)
- **Lokalisasi Lengkap Indonesia:** Format Rupiah lokal, penamaan hari dalam Bahasa Indonesia.

### 📱 Integrasi OS Android
- **Jetpack Glance AppWidget:** Widget launcher real-time "Hari Ini" yang selalu update.
- **App Shortcuts:** Akses cepat "Catat Cepat" dan "Analitik" dari ikon aplikasi.
- **Biometric App Lock:** Autentikasi opsional dengan Fingerprint/PIN + grace period 60-detik.

### 💾 Backup, Export & Portabilitas
- **JSON Backup & Restore:** Backup database penuh yang atomic dan memory-safe.
- **CSV Export:** Export data ke spreadsheet dengan satu klik, dibagikan via FileProvider aman.

---

## 🏛 Arsitektur & Tech Stack

Fast Track mengikuti Modern Android Development (MAD) guidelines dengan pola MVVM clean architecture:

### Technology Stack
- **UI Framework:** Jetpack Compose + Material 3
- **Database:** Room Database + SQLite
- **Local Storage:** DataStore Preferences
- **Language:** Kotlin (100%)
- **Architecture:** MVVM + Repository Pattern
- **Async:** Kotlin Coroutines + Flow
- **Biometric:** BiometricPrompt API
- **Widget:** Jetpack Glance
- **Export:** FileProvider, Android Storage Access Framework

### Project Structure
```
Fast-Track/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/weinssy/fasttrack/
│   │   │   │   ├── data/               # Room DB, DataStore, Repositories
│   │   │   │   ├── domain/             # Use Cases, Models
│   │   │   │   ├── presentation/       # Compose UI, ViewModels
│   │   │   │   ├── util/               # Helpers, Extensions
│   │   │   │   └── MainActivity.kt
│   │   │   └── res/                    # Resources, Strings (ID)
│   │   └── test/
│   └── build.gradle.kts
├── build.gradle.kts
└── gradle/
    └── libs.versions.toml               # Version catalog
```

---

## 🚀 Quickstart

### Prasyarat
- Android Studio Hedgehog atau lebih baru
- JDK 17+
- Android SDK 26+ (API Level 26)

### Setup & Build
```bash
# Clone repository
git clone https://github.com/Weinssy/Fast-Track.git
cd Fast-Track

# Build dengan Gradle
./gradlew build

# Install di device/emulator
./gradlew installDebug
```

### Jalankan Tests
```bash
./gradlew test              # Unit tests
./gradlew connectedAndroidTest  # Instrumented tests
```

---

## 📖 Dokumentasi Fitur

### Pencatatan Pengeluaran
1. Buka aplikasi → halaman utama menampilkan **numpad input**
2. Masukkan jumlah (contoh: `50000`)
3. Pilih kategori dari chip selector (Umum, Makanan, Transport, dll)
4. Tekan **Simpan** atau gunakan gesture untuk confirm
5. Pengeluaran tercatat instantly di database lokal

### Kategorisasi & Tag Management
- **Kategori Default:** Umum, Makanan, Transport, Entertainment, Kesehatan, Belanja
- **Tag Custom:** Tap **+ Tag Baru** → isi nama → Simpan
- **Hapus Tag:** Long-press tag → konfirmasi hapus (preset tag tidak bisa dihapus)

### Analitik & Dashboard
- **Tab Analitik:** Lihat visualisasi pengeluaran 7-hari terakhir per kategori
- **Budget Harian:** Atur limit di Settings, aplikasi akan warn saat mendekati limit
- **Export Data:** Tap menu → Export CSV untuk share data ke spreadsheet

### Backup & Restore
- **Settings → Backup:** Pilih folder penyimpanan, aplikasi buat file `fasttrack_backup.json`
- **Settings → Restore:** Pilih file backup untuk kembalikan data (atomic operation)

---

## 🔒 Keamanan & Privasi

- ✅ **Offline-First:** Tidak ada koneksi internet yang diperlukan
- ✅ **Local-Only Database:** Semua data tersimpan di device dengan enkripsi SQLite
- ✅ **No Tracking:** Nol analytics, nol telemetry pihak ketiga
- ✅ **Biometric Lock:** Opsi kunci app dengan Fingerprint/PIN
- ✅ **Secure Export:** File export via FileProvider dengan sandboxing ketat

---

## 📊 Performance Metrics

| Aspek | Target | Status |
|-------|--------|--------|
| Startup Time | < 1s | ✅ Tercapai |
| Input Loop | < 300ms | ✅ Tercapai |
| Database Query | < 100ms | ✅ Tercapai |
| Memory Footprint | < 50MB | ✅ Tercapai |
| Battery (Idle) | Minimal | ✅ Widget zero-drain |

---

## 🤝 Kontribusi

Kami menerima kontribusi! Silakan:

1. **Fork** repository
2. Buat branch fitur: `git checkout -b feature/nama-fitur`
3. Commit perubahan: `git commit -m 'Add: deskripsi fitur'`
4. Push ke branch: `git push origin feature/nama-fitur`
5. Buka **Pull Request**

### Development Guidelines
- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Gunakan ktlint untuk formatting: `./gradlew ktlintFormat`
- Tambahkan unit tests untuk fitur baru
- Update dokumentasi sesuai perubahan

---

## 📝 Roadmap

- [ ] Cloud Sync opsional (E2E encrypted)
- [ ] Dark Mode enhancement
- [ ] Multiple currency support
- [ ] Advanced analytics (yearly trends, budgeting)
- [ ] Recurring transactions
- [ ] Import dari CSV
- [ ] Data sharing aman antar device

---

## 📄 Lisensi

Project ini dilisensikan di bawah [Apache License 2.0](LICENSE).

---

## 👨‍💻 Author

**Weinssy** - [@Weinssy](https://github.com/Weinssy)

---

## 📧 Support & Feedback

Punya saran atau menemukan bug? [Buka Issue](https://github.com/Weinssy/Fast-Track/issues) atau hubungi langsung.

---

**Made with ❤️ in Indonesia | Fast, Private, Local-First Expense Tracking**

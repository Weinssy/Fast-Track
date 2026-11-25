# Fast Track ⚡

**Fast Track** adalah aplikasi Android untuk mencatat pengeluaran harian dengan cepat, sederhana, dan privat. Cukup masukkan nominal, pilih kategori bila diperlukan, lalu simpan—tanpa formulir panjang atau alur yang rumit.

> **Versi saat ini:** 3.0.0  
> **Platform:** Android 8.0 (API 26) atau lebih baru

## Mengapa Fast Track?

Pencatatan keuangan seharusnya tidak memerlukan banyak langkah. Fast Track v3.0.0 hadir dengan pendekatan **zero-friction** dan **local-first** agar Anda dapat:

- mencatat transaksi dalam hitungan detik menggunakan keypad bawaan (lengkap dengan tombol `000` dan catatan opsional);
- membagi tagihan bareng teman lewat fitur **Split Bill** yang otomatis menyinkronkan jatah Anda ke pengeluaran pribadi;
- mencari, menyaring, dan menyunting riwayat transaksi di layar **Riwayat** khusus;
- memantau tren pengeluaran, perbandingan periode, dan progres anggaran bulanan melalui **Analitik** yang diperluas;
- menjadwalkan pengeluaran rutin bulanan agar tidak ada tagihan yang terlewat;
- memilih tema **Gelap**, **Terang**, atau **Ikuti Sistem** secara konsisten hingga ke widget layar utama;
- mencadangkan seluruh data ke file JSON atau CSV di perangkat, serta memperbarui aplikasi langsung dari GitHub Releases;
- menjaga data keuangan tetap tersimpan secara lokal di perangkat tanpa akun, pelacakan, atau sinkronisasi cloud.

## Fitur

- **Input pengeluaran cepat** dengan keypad khusus (termasuk tombol `000`), catatan opsional (`+ Catatan`), edit transaksi, dan umpan balik haptic.
- **Kategori inline**: Umum, Makan, Transport, Belanja, Tagihan, dan Jajan, plus kategori kustom.
- **Split Bill & Auto-Sync**:
  - Multi-sesi nota patungan tersimpan lokal (Room DB v3).
  - Peserta dengan penanda **"Saya"**, pembagian rata maupun berbasis porsi/bobot desimal, serta pajak/service (%) dan diskon (Rp).
  - Perhitungan transfer minimal (*greedy settlement*), penandaan status **Lunas**, ringkasan siap tempel WhatsApp, serta ekspor/impor JSON & tautan nota (`split-bill-kelompok`).
  - **Sinkron Otomatis ke Fast Track**: Jatah milik **"Saya"** tercatat sebagai satu transaksi pengeluaran pribadi yang selalu terbarui otomatis tanpa pencatatan ganda.
- **Riwayat & Pencarian**: Layar Riwayat terpisah dengan pencarian catatan/kategori/nominal, filter chip kategori, pengelompokan per tanggal lengkap dengan subtotal harian, edit transaksi, dan swipe-to-delete + undo.
- **Analitik Diperluas**: Pilihan rentang **7 Hari**, **Bulan Ini**, dan **Bulan Lalu**, perbandingan persentase terhadap periode sebelumnya, kartu progres **Anggaran Bulanan**, grafik batang interaktif, dan rincian kategori.
- **Pengeluaran Rutin**: Jadwalkan tagihan atau langganan bulanan (tanggal 1–31) yang diterapkan otomatis sekali per bulan saat jatuh tempo maupun secara manual.
- **Batas Anggaran Harian & Bulanan** dengan indikator status visual di layar utama, analitik, dan widget layar utama.
- **Tema Gelap, Terang, & Sistem** yang diterapkan di seluruh layar Compose dan widget Jetpack Glance.
- **Widget layar utama** (Jetpack Glance) untuk memantau total hari ini, sisa anggaran harian, dan pintasan `+ Catat`.
- **Cadangan JSON (v2)** (mencakup pengeluaran, kategori, anggaran harian & bulanan, tema, pengeluaran rutin, dan seluruh sesi Split Bill; kompatibel penuh dengan berkas cadangan v1) serta **ekspor CSV** dengan kolom sumber (`Pribadi` / `Patungan`).
- **Kunci biometrik opsional** (sidik jari, wajah, atau PIN/pola perangkat) dengan masa tenggang 60 detik dan proteksi Task Switcher.
- **Pembaruan Dalam Aplikasi (In-App Updater)** melalui GitHub Releases (pengecekan otomatis terjadwal 12 jam & manual di Pengaturan, tampilan catatan rilis, unduhan berprogres, dan instalasi APK langsung).
- **App shortcut** dari ikon peluncur: Catat Cepat, Split Bill, Riwayat, dan Analitik.
- **Bahasa Indonesia** dengan format tanggal dan mata uang Rupiah (`Rp`).
- **Penyimpanan lokal** menggunakan Room Database (`version = 3` dengan migrasi aman dari versi sebelumnya).

## Unduh APK

APK versi terbaru tersedia di halaman [GitHub Releases](https://github.com/Weinssy/Fast-Track/releases/latest).

> Android mungkin meminta izin untuk memasang aplikasi dari sumber yang tidak dikenal. Pastikan hanya memasang APK dari rilis resmi repositori ini.

Build rilis di repositori ini memakai keystore debug Gradle agar `assembleRelease` bisa dijalankan tanpa file signing pribadi. Pembaruan APK berikutnya harus memakai key yang sama, atau aplikasi lama perlu dihapus terlebih dahulu.

## Teknologi

- **Kotlin**
- **Jetpack Compose** dan Material 3
- **Room Persistence Library**
- **Jetpack Glance**, DataStore, dan Biometric
- **Kotlin Coroutines** dan `StateFlow`
- **MVVM**
- **Gradle Kotlin DSL** (Android Gradle Plugin 8.7.3, Gradle 8.9)

## Struktur Proyek

```text
app/src/main/java/com/wein/fasttrack/
├── data/          # Entity, DAO, migrasi, dan database Room
├── repository/    # Abstraksi akses data, auto-sync Split Bill, dan DataStore
├── splitbill/     # Engine kalkulasi Split Bill & codec share/JSON
├── viewmodel/     # State dan logika layar utama, Split Bill, Riwayat, & Analitik
├── ui/            # Layar Compose, tema Gelap/Terang, dialog update, & komponen UI
├── widget/        # Widget Glance adaptif tema
└── utils/         # Cadangan JSON, ekspor CSV, biometrik, dan updater GitHub
```

## Menjalankan Proyek

### Prasyarat

- Android Studio Ladybug atau yang lebih baru
- JDK 17
- Android SDK dengan compile SDK 35

### Dengan Android Studio

1. Clone repository ini.
2. Buka folder proyek di Android Studio.
3. Tunggu proses Gradle sync selesai.
4. Jalankan aplikasi pada emulator atau perangkat Android dengan API 26+.

### Dari command line

Di Windows gunakan `gradlew.bat` sebagai pengganti `./gradlew`.

```bash
# Menjalankan unit test
./gradlew testDebugUnitTest

# Membuat APK debug
./gradlew assembleDebug

# Membuat APK release
./gradlew assembleRelease
```

File APK hasil build biasanya tersedia di `app/build/outputs/apk/`.

## Privasi dan Data

Fast Track menyimpan seluruh data keuangan secara lokal di perangkat. Aplikasi tidak memerlukan akun dan tidak mengirimkan riwayat transaksi atau data Split Bill ke server. Akses internet hanya digunakan saat memeriksa atau mengunduh pembaruan aplikasi resmi melalui GitHub Releases. Cadangan JSON dan ekspor CSV dibuat langsung di perangkat, lalu dapat disimpan atau dibagikan melalui aplikasi Android pilihan Anda.

Riwayat perubahan ada di [CHANGELOG.md](CHANGELOG.md).

## Roadmap

- [x] Pencatatan pengeluaran cepat (termasuk catatan & edit transaksi)
- [x] Kategori inline dan kategori kustom
- [x] Layar riwayat dengan pencarian, filter, swipe-to-delete, dan undo
- [x] Analitik diperluas (7 hari, bulan ini, bulan lalu) & anggaran bulanan
- [x] Split Bill multi-sesi & sinkron otomatis ke pengeluaran pribadi
- [x] Pengeluaran rutin bulanan
- [x] Dukungan tema Gelap, Terang, dan Sistem (termasuk widget)
- [x] Widget, cadangan JSON v2, kunci biometrik, dan GitHub in-app updater

## Berkontribusi

Bug report, ide fitur, dan pull request sangat diterima. Sebelum berkontribusi, silakan baca [issue templates](.github/ISSUE_TEMPLATE) dan jelaskan perubahan yang diusulkan secara rinci.

## Lisensi

Proyek ini dirilis di bawah [Apache License 2.0](LICENSE).

# Fast Track ⚡

**Fast Track** adalah aplikasi Android untuk mencatat pengeluaran harian dengan cepat, sederhana, dan privat. Cukup masukkan nominal, pilih kategori bila diperlukan, lalu simpan—tanpa formulir panjang atau alur yang rumit.

> **Versi saat ini:** 1.5.0  
> **Platform:** Android 8.0 (API 26) atau lebih baru

## Mengapa Fast Track?

Pencatatan keuangan seharusnya tidak memerlukan banyak langkah. Fast Track dibuat dengan pendekatan **zero-friction** dan **local-first** agar Anda dapat:

- mencatat transaksi dalam hitungan detik menggunakan keypad bawaan;
- memberi kategori secara langsung tanpa dialog yang mengganggu;
- melihat total pengeluaran hari ini dan riwayat transaksi;
- memantau tren pengeluaran mingguan melalui analitik sederhana;
- menjaga data tetap tersimpan di perangkat tanpa akun, pelacakan, atau sinkronisasi cloud.

## Fitur

- **Input pengeluaran cepat** dengan keypad khusus.
- **Kategori inline**: Umum, Makan, Transport, Belanja, Tagihan, dan Jajan.
- **Riwayat transaksi** dengan waktu, kategori, dan fitur swipe-to-delete.
- **Analitik pengeluaran** berupa grafik 7 hari dan ringkasan berdasarkan kategori.
- **Ekspor CSV** untuk mencadangkan atau membagikan riwayat transaksi.
- **Bahasa Indonesia** dengan format tanggal, mata uang Rupiah (`Rp`), dan nama file CSV yang dilokalkan.
- **Tema gelap minimalis** yang berfokus pada angka dan fungsi.
- **Penyimpanan lokal** menggunakan Room Database untuk akses cepat dan privasi.

## Unduh APK

APK versi terbaru tersedia di repository:

[Unduh Fast Track v1.5.0](https://github.com/Weinssy/Fast-Track/raw/main/Fast%20Track%20v1.5.0%20Signed.apk)

> Android mungkin meminta izin untuk memasang aplikasi dari sumber yang tidak dikenal. Pastikan hanya memasang APK dari sumber yang Anda percaya.

## Teknologi

- **Kotlin**
- **Jetpack Compose** dan Material 3
- **Room Persistence Library**
- **Kotlin Coroutines** dan `StateFlow`
- **MVVM** dengan prinsip Clean Architecture
- **Gradle Kotlin DSL**

## Struktur Proyek

```text
app/src/main/java/com/wein/fasttrack/
├── data/          # Entity, DAO, dan database Room
├── repository/    # Abstraksi akses data
├── viewmodel/     # State dan logika aplikasi
└── ui/            # Layar Compose, tema, dan komponen UI
```

## Menjalankan Proyek

### Prasyarat

- Android Studio Koala atau yang lebih baru
- JDK 8 atau kompatibel
- Android SDK dengan compile SDK 35

### Dengan Android Studio

1. Clone repository ini.
2. Buka folder proyek di Android Studio.
3. Tunggu proses Gradle sync selesai.
4. Jalankan aplikasi pada emulator atau perangkat Android dengan API 26+.

### Dari command line

```bash
# Menjalankan unit test
./gradlew test

# Membuat APK debug
./gradlew assembleDebug

# Membuat APK release
./gradlew assembleRelease
```

File APK hasil build biasanya tersedia di `app/build/outputs/apk/`.

## Privasi dan Data

Fast Track menyimpan data transaksi secara lokal di perangkat. Aplikasi tidak memerlukan akun dan tidak mengirimkan riwayat pengeluaran ke server. Saat menggunakan fitur ekspor, file CSV dibuat di perangkat dan dapat dibagikan melalui aplikasi Android pilihan Anda.

## Roadmap

- [x] Pencatatan pengeluaran cepat
- [x] Kategori inline
- [x] Riwayat transaksi dan swipe-to-delete
- [x] Analitik pengeluaran 7 hari
- [x] Ringkasan berdasarkan kategori
- [x] Ekspor CSV
- [x] Lokalisasi Bahasa Indonesia
- [ ] Dukungan tema terang

## Berkontribusi

Bug report, ide fitur, dan pull request sangat diterima. Sebelum berkontribusi, silakan baca [issue templates](.github/ISSUE_TEMPLATE) jika tersedia dan jelaskan perubahan yang diusulkan secara rinci.

## Lisensi

Proyek ini dirilis di bawah [MIT License](LICENSE).

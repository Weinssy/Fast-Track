# Laporan Perkembangan "Fast Track"

Laporan ini merangkum arah perkembangan aplikasi **Fast Track** dari versi pertama hingga versi terbaru. Aplikasi ini dimulai dengan filosofi sederhana: mencatat pengeluaran harian tanpa hambatan (*zero-friction*), dan kini telah berkembang menjadi aplikasi pencatat keuangan yang komprehensif, terlokalisasi, dan memiliki fitur analitik bawaan.

## 📌 Versi 1.0.0 - Fondasi & Kecepatan (Initial Release)
- **Fokus Utama**: Membangun pencatat pengeluaran harian yang sangat cepat (*ultra-fast*).
- **Pencapaian**: 
  - Input pengeluaran langsung dengan *custom keypad* di layar, sehingga tidak terhalang atau mengalami *lag* dari *keyboard* bawaan sistem Android.
  - Tampilan *Dark Minimalist* monokromatik yang fungsional agar mata langsung fokus ke angka nominal.
  - Penyimpanan data 100% lokal dan rahasia menggunakan **Room Database**.
  - Fitur hapus riwayat cepat dengan gestur *swipe-to-delete*.

## 📌 Versi 1.1.0 - Keamanan Data (Backup)
- **Fokus Utama**: Menjawab kebutuhan pengguna untuk mencadangkan (backup) data tanpa harus menggunakan *cloud* atau mengorbankan privasi.
- **Pencapaian**: 
  - Penambahan fitur ekspor riwayat pengeluaran ke format CSV (*Local Data Backup*).
  - Terintegrasi dengan sistem berbagi Android (Intent) sehingga file CSV bisa langsung dikirim ke WhatsApp, Telegram, Google Drive, atau Email melalui tombol *Share* di pojok kanan atas.

## 📌 Versi 1.2.0 - Kategorisasi Cepat & Pemeliharaan Storage
- **Fokus Utama**: Memberikan kemampuan melacak kategori pengeluaran tanpa melanggar batasan waktu "*Sub-3 Seconds Flow*".
- **Pencapaian**: 
  - **Fast Inline Tagging**: *Tag Selector* (pemilih label) diletakkan secara horizontal (*LazyRow*) tepat di atas *keypad*. Pengguna tidak perlu membuka menu *pop-up*, *dropdown*, atau *modal* sama sekali.
  - **Export Cache Housekeeping**: Sistem otomatis yang cerdas bekerja di *background* (*Dispatchers.IO*) untuk menghapus file ekspor CSV lama (di atas 24 jam) agar penyimpanan internal ponsel tidak penuh (*storage bloat*).

## 📌 Versi 1.3.0 - Visualisasi & Analitik
- **Fokus Utama**: Membantu pengguna memahami kebiasaan pengeluaran mereka secara visual tanpa harus berpindah aplikasi.
- **Pencapaian**:
  - Penambahan Layar **Analitik** (*Lightweight Visual Analytics*).
  - Grafik batang mingguan (*7-Day Bar Chart*) yang dibangun secara *native* menggunakan Jetpack Compose Canvas, tanpa bergantung pada *library* pihak ketiga yang berat.
  - Fitur *Category Breakdown* untuk melihat rincian alokasi pengeluaran per kategori/tag.

## 📌 Versi 1.3.1 - Pelokalan & Penyempurnaan (Versi Saat Ini)
- **Fokus Utama**: Membuat aplikasi terasa sangat lokal dan relevan untuk pengguna di Indonesia.
- **Pencapaian**: 
  - **Lokalisasi Penuh**: Semua teks (*strings.xml*), format tanggal, dan nominal (mata uang `Rp`) distandardisasi menggunakan `Locale("id", "ID")`.
  - **Kategori Relevan**: Mengubah *tag* bawaan menjadi istilah yang umum dipakai sehari-hari: `"Umum", "Makan", "Transport", "Belanja", "Tagihan", "Jajan"`.
  - Peningkatan format *header* pada ekspor CSV agar langsung berbahasa Indonesia (Tanggal, Waktu, Nominal, Kategori, Catatan).

## 📌 Versi 1.4.0 - Kategori Dinamis & Ekstensibilitas (Versi Saat Ini)
- **Fokus Utama**: Memberikan kebebasan bagi pengguna untuk membuat dan menambah kategori (*tags*) pengeluaran sendiri secara dinamis.
- **Pencapaian**:
  - **Dynamic Database Model**: Migrasi arsitektur *database* dari yang sebelumnya *hardcoded* (statis) menjadi tabel basis data mandiri menggunakan Room (skema *database* naik ke versi 2).
  - **Add Tag Dialog**: Penambahan tombol "+ Tambah" langsung di layar utama tanpa mengganggu *layout* atau *keypad*. 
  - **Backward Compatibility**: Pembaruan dilakukan dengan migrasi `MIGRATION_1_2` yang mulus tanpa menghilangkan data lama pengguna, bahkan secara otomatis menyeragamkan tag lawas "General" menjadi "Umum".

---
### 📈 Kesimpulan Arah Perkembangan
Perjalanan Fast Track dari **v1.0.0 hingga v1.4.0** menunjukkan komitmen kuat pada filosofi awalnya: **Kecepatan, Privasi (Local-First), dan Zero-Friction**. Semua fitur baru (Tagging dinamis, Backup CSV, Analitik) diimplementasikan sedemikian rupa agar tidak pernah menutupi keypad utama atau memaksa pengguna melakukan klik ekstra. 

Saat ini, struktur *codebase* sudah sangat matang dan teruji secara arsitektur (MVVM), menjadikannya sangat siap dirilis (*Production-Ready*).

# Perbedaan dengan Magisk

Meskipun ada banyak kesamaan antara modul MuSu Next dan modul Magisk, pasti ada beberapa perbedaan karena mekanisme implementasinya yang sangat berbeda. Jika Anda ingin modul Anda berjalan di Magisk dan MuSu Next, Anda harus memahami perbedaan ini.

## Kesamaan

- Format file modul: keduanya menggunakan format zip untuk mengatur modul, dan format modulnya hampir sama
- Direktori pemasangan modul: keduanya terletak di `/data/adb/modules`
- Tanpa sistem: keduanya mendukung modifikasi / sistem dengan cara tanpa sistem melalui modul
- post-fs-data.sh: waktu eksekusi dan semantiknya persis sama
- service.sh: waktu eksekusi dan semantiknya persis sama
- system.prop: sepenuhnya sama
- sepolicy.rule: sama persis
- BusyBox: skrip dijalankan di BusyBox dengan "mode mandiri" diaktifkan di kedua kasus

## Perbedaan

Sebelum memahami perbedaannya, Anda perlu mengetahui cara membedakan apakah modul Anda berjalan di MuSu Next atau Magisk. Anda dapat menggunakan variabel lingkungan `KSU` untuk membedakannya di semua tempat di mana Anda dapat menjalankan skrip modul (`customize.sh`, `post-fs-data.sh`, `service.sh`). Di MuSu Next, variabel lingkungan ini akan disetel ke `true`.

Berikut beberapa perbedaannya:

- Modul MuSu Next tidak dapat diinstal dalam mode Pemulihan.
- Modul MuSu Next tidak memiliki dukungan bawaan untuk Zygisk (tetapi Anda dapat menggunakan modul Zygisk melalui [ZygiskNext](https://github.com/Dr-TSNG/ZygiskNext).
- Metode untuk mengganti atau menghapus file dalam modul MuSu Next sama sekali berbeda dari Magisk. MuSu Next tidak mendukung metode `.replace`. Sebagai gantinya, Anda perlu membuat file dengan nama yang sama dengan `mknod filename c 0 0` untuk menghapus file terkait.
- Direktori untuk BusyBox berbeda. BusyBox bawaan di MuSu Next terletak di `/data/adb/ksu/bin/busybox`, sedangkan di Magisk terletak di `/data/adb/magisk/busybox`. **Perhatikan bahwa ini adalah perilaku internal MuSu Next dan dapat berubah di masa mendatang!**
- MuSu Next tidak mendukung file `.replace`; namun, MuSu Next mendukung variabel `REMOVE` dan `REPLACE` untuk menghapus atau mengganti file dan folder.

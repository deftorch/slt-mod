# Checklist Perbaikan Teknis - Smooth Layered Terrain (SLT) Mod

## ✅ Prioritas 1: Kritis (Timeline: 1-3 hari)
**Kriteria:** Stabilitas sistem, pencegahan regresi, struktur dasar.

- [x] **[TEST]** Buat Unit Test untuk `Smoother.java`
    - *Mengapa:* Algoritma inti mod, risiko visual bug tinggi.
    - *Target:* Test semua `SmoothingType`.
- [x] **[TEST]** Buat Unit Test untuk `CircuitBreakerAdvanced.java`
    - *Mengapa:* Logika state machine (Open/Half-Open/Closed) krusial untuk mencegah crash cascading.
- [x] **[ARCH]** Refactor Struktur Paket (Package Organization)
    - *Mengapa:* Memecah monolit `com.sltmod` menjadi sub-paket (`config`, `processing`, `memory`, `util`) untuk navigasi dan modularitas yang lebih baik.
    - *Action:* Move files & update `package` declarations.

**Definition of Done untuk Priority 1:**
- [x] Semua file dipindahkan ke paket yang sesuai.
- [x] Mod dapat dicompile dan dijalankan tanpa error import.
- [x] Unit test baru passing.

## ✅ Prioritas 2: Fondasi (Timeline: 1-2 minggu)
**Kriteria:** Peningkatan arsitektur, pengurangan *code smells*.

- [x] **[REFACTOR]** Ubah `TieredMemoryPool` menjadi Singleton Pattern
    - *Mengapa:* Menghilangkan ketergantungan pada *static fields* dan memudahkan mocking tanpa refleksi.
- [x] **[TEST]** Buat Unit Test untuk `AsyncProcessor.java`
    - *Mengapa:* Memastikan concurrency logic (timeouts, queue handling) berjalan benar.
- [x] **[QUALITY]** Sentralisasi Konstanta Mod
    - *Mengapa:* String `MOD_ID` dan `VERSION` tersebar. Buat kelas `Reference.java` atau `Constants.java`.
- [x] **[SECURITY]** Tambahkan validasi statis untuk `FMLPaths` di Config
    - *Mengapa:* Memperkuat *defensive coding* pada static initializer `LayeredTerrainConfig`.

**Definition of Done untuk Priority 2:**
- [x] `TieredMemoryPool` diakses via `getInstance()`.
- [x] Test coverage meningkat ke >50%.
- [x] Tidak ada string magic untuk MOD_ID.

## ✅ Prioritas 3: Peningkatan (Timeline: 1-4 minggu)
**Kriteria:** DX, Dokumentasi, Polish.

- [x] **[DX]** Kurangi verbositas logging startup
    - *Mengapa:* Log server terlalu penuh dengan ASCII art. Buat opsi config `quiet_startup`.
- [x] **[DOCS]** Generate Javadoc HTML
    - *Mengapa:* Memudahkan akses dokumentasi API offline.
- [x] **[PERF]** Benchmark Profiling
    - *Mengapa:* Verifikasi klaim <5ms dengan data riil menggunakan JMH (Java Microbenchmark Harness).
- [x] **[CI]** Setup GitHub Actions untuk Auto-Build & Test
    - *Mengapa:* Otomatisasi validasi setiap PR.

**Definition of Done untuk Priority 3:**
- [x] Startup log lebih bersih.
- [x] JMH Benchmark report tersedia.
- [x] CI pipeline aktif (jika repo di-host).

## Progress Tracking
- **Priority 1:** ✅ 3/3 completed (Target: TBD)
- **Priority 2:** ✅ 4/4 completed (Target: TBD)
- **Priority 3:** ✅ 4/4 completed (Target: TBD)

## Notes & Dependencies
- [ ] **Dependencies:** Membutuhkan akses ke repositori git untuk refactoring struktur paket.
- [ ] **Resources:** 1 Senior Java Engineer untuk refactoring arsitektur (Priority 1 & 2).
- [ ] **Risk Mitigation:** Lakukan backup sebelum refactoring struktur paket. Pastikan semua branch lain telah dimerge untuk menghindari konflik merge yang masif (renaming files).

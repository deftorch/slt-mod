# 📝 CHANGELOG

## 2025-12-04 - GitHub Copilot
- Membuat file ROADMAP.md sebagai checklist global fase pengembangan
- Membuat file TODO.md sebagai checklist tugas spesifik yang sedang dikerjakan
- Membuat file INSTRUKSI.md sebagai panduan prosedur agent
- Membuat file CHANGELOG.md sebagai contoh pencatatan perubahan
- File yang diubah/dibuat: ROADMAP.md, TODO.md, INSTRUKSI.md, CHANGELOG.md

## [2025-05-24] - Jules
### Added
- [DX] Config option `quiet_startup` in `LayeredTerrainConfig` to reduce log verbosity.
- [PERF] JMH benchmark support in `build.gradle` and `TerrainProcessingBenchmark.java`.
- [CI] GitHub Actions workflow `.github/workflows/build.yml` for auto-build & test.
- [DOCS] Javadoc configuration in `build.gradle`.

### Changed
- Modified `LayeredTerrainMod` startup logging to respect `quiet_startup` config.
- Updated `build.gradle` to better handle Java toolchain issues (explicit compatibility).

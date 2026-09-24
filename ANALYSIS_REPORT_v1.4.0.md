# Fast Track Technical Analysis Report (v1.4.0)

## 1. Executive Summary
The v1.4.0 update represents a critical architectural transition for Fast Track. We have successfully migrated the tagging system from a static, hardcoded list to a **dynamic database model** backed by Jetpack Room. 
- **APK Footprint Impact**: The impact is negligible (a few extra kilobytes for the new `TagEntity` and `TagDao` classes), maintaining the app's lightweight nature.
- **Database Version Bump**: The Room database schema has been bumped from `1` to `2`. 
- **Backward Compatibility**: Full backward compatibility is guaranteed via the `MIGRATION_1_2` script, ensuring users' historical expense data remains perfectly intact during the upgrade process.

## 2. Database Migration & Schema Integrity Audit
An in-depth verification of `MIGRATION_1_2` executed within `AppDatabase.kt`:
- **SQL Execution Plan**: 
  - `CREATE TABLE IF NOT EXISTS tags` successfully maps to `TagEntity` with `id` (`INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL`), `name` (`TEXT NOT NULL`), and `isPreset` (`INTEGER NOT NULL`).
- **Unique Indexing**: 
  - Executed `CREATE UNIQUE INDEX IF NOT EXISTS index_tags_name ON tags (name)`. This accurately mirrors the `@Index` annotation in `TagEntity` preventing any duplicate tags. SQLite handles lookup performance on this index natively at O(log N).
- **Preset Seeding & Legacy Normalization**: 
  - We execute an `INSERT OR IGNORE` to securely bootstrap the initial preset tags (`Umum`, `Makan`, etc.).
  - The legacy data normalization query (`UPDATE expenses SET tag = 'Umum' WHERE tag = 'General' OR tag IS NULL OR tag = ''`) safely ensures that null, empty, or outdated English fallback tags are strictly unified under `"Umum"`.

## 3. Concurrency & Thread Safety
- **TagDao Operations**: All CRUD operations (`insertTag`, `deleteTag`, `getTagCount`) are structured as `suspend` functions inside `TagDao`. The Room compiler automatically generates the implementation which runs these on background threads (`Dispatchers.IO`), keeping the main thread untouched.
- **UI State Collection**: 
  - `ExpenseViewModel` reads `repository.getAllTags()` as a reactive `Flow`.
  - The `combine` operator merges this stream into the single `FastTrackUiState`. 
  - Since `StateFlow` emissions happen safely via `stateIn(viewModelScope)`, there are no race conditions during concurrent UI reads, and writes (like adding a new tag) are instantly reflected back into the reactive stream.

## 4. UI Performance & Recomposition Benchmarking
- **LazyRow Optimization**: The addition of dynamic tags via `availableTags` integrated smoothly into the existing `LazyRow`. Jetpack Compose handles recomposition locally; switching or adding tags only recomposes the `TagChip`s or the `LazyRow` itself. The numeric keypad and main `HeroDisplay` do not needlessly invalidate, preserving the 0ms latency input flow.
- **Dialog UX Impact**: The new `AddTagDialog` component leverages the Material 3 `AlertDialog` and `OutlinedTextField`. Because it operates within a modal layer, the invocation of the soft keyboard slides the dialog cleanly into view without displacing or clipping the underlying transaction history or custom keypad.

## 5. Risk & Regression Assessment
- **Failure Points & Mitigations**:
  - *Deleting a Preset Tag*: Mitigated. The `deleteTag` method in `ExpenseRepository.kt` strictly checks `if (!tag.isPreset)` before delegating to the DAO.
  - *Duplicate Tag Names*: Mitigated. The unique SQL index (`index_tags_name`) combined with `OnConflictStrategy.IGNORE` gracefully drops duplicate insert attempts without crashing the application.
  - *Database Downgrade*: Not officially supported. Attempting to roll back to v1.3.1 from v1.4.0 will result in a Room `IllegalStateException` unless a destructive fallback is enabled.
- **Roadmap for v1.5.0**:
  - Introduce color-coding for individual tags within the DB model (`colorHex: String`).
  - Introduce long-press interactions on `TagChip`s inside the `LazyRow` to allow renaming or deleting custom tags directly from the main screen.

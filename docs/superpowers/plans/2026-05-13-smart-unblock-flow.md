# Smart Unblock Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the single-screen block overlay with a two-step self-honesty flow (Confirmation → Motivational), add a 5-minute grace period after access is granted, and record three distinct outcomes (`LEGITIMATE`, `BROKE_PLAN_PROCEEDED`, `BACKED_OFF`).

**Architecture:** Single `BlockOverlayActivity` hosts a stateful `BlockOverlayScreen` driven by a sealed `BlockOverlayUiState`. A new `BlockOverlayViewModel` owns the state machine, calls domain use cases, and emits one-shot events that the Activity translates into `finish()`, home-intent, or `AppBlockerAccessibilityService.grantGrace()` calls. The service holds an in-memory `ConcurrentHashMap<String, Long>` of grace expiries (lost on service restart by design).

**Tech Stack:** Kotlin, Jetpack Compose, Room 2.6, ViewModel + StateFlow/SharedFlow, kotlinx-coroutines-test, Turbine, Room MigrationTestHelper.

**Spec:** `docs/superpowers/specs/2026-05-13-smart-unblock-flow-design.md`

---

## File Map

**Domain layer**
- Create: `app/src/main/java/com/appblocker/domain/model/UnblockOutcome.kt`
- Modify: `app/src/main/java/com/appblocker/domain/model/UnblockEvent.kt`
- Modify: `app/src/main/java/com/appblocker/domain/usecase/RecordUsageUseCase.kt`
- Modify: `app/src/main/java/com/appblocker/domain/repository/UsageRepository.kt`

**Data layer**
- Modify: `app/src/main/java/com/appblocker/data/local/entity/UnblockEventEntity.kt`
- Modify: `app/src/main/java/com/appblocker/data/local/dao/UnblockEventDao.kt`
- Modify: `app/src/main/java/com/appblocker/data/local/AppDatabase.kt`
- Modify: `app/src/main/java/com/appblocker/data/repository/UsageRepositoryImpl.kt`

**Presentation layer**
- Create: `app/src/main/java/com/appblocker/presentation/screen/BlockOverlayUiState.kt`
- Rewrite: `app/src/main/java/com/appblocker/presentation/screen/BlockOverlayScreen.kt`
- Create: `app/src/main/java/com/appblocker/presentation/BlockOverlayViewModel.kt`
- Rewrite: `app/src/main/java/com/appblocker/presentation/BlockOverlayActivity.kt`

**Service layer**
- Modify: `app/src/main/java/com/appblocker/service/AppBlockerAccessibilityService.kt`

**Build**
- Modify: `app/build.gradle.kts`

**Tests**
- Create: `app/src/test/java/com/appblocker/data/UnblockOutcomeMappingTest.kt`
- Create: `app/src/test/java/com/appblocker/presentation/BlockOverlayViewModelTest.kt`
- Create: `app/src/test/java/com/appblocker/presentation/fakes/FakeUsageRepository.kt`
- Create: `app/src/test/java/com/appblocker/presentation/fakes/FakeMotivationalMessageRepository.kt`
- Create: `app/src/androidTest/java/com/appblocker/data/local/AppDatabaseMigrationTest.kt`

---

### Task 1: Add test dependencies

**Files:**
- Modify: `app/build.gradle.kts` (dependencies block, lines 75–78)

- [ ] **Step 1: Replace the testing block with the full set we need**

Replace the existing testing block at the bottom of `dependencies { ... }`:

```kotlin
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("app.cash.turbine:turbine:1.1.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
```

- [ ] **Step 2: Verify build still succeeds**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "Add test deps: coroutines-test, turbine, room-testing"
```

---

### Task 2: Create UnblockOutcome enum

**Files:**
- Create: `app/src/main/java/com/appblocker/domain/model/UnblockOutcome.kt`

- [ ] **Step 1: Create the enum**

```kotlin
package com.appblocker.domain.model

enum class UnblockOutcome {
    LEGITIMATE,
    BROKE_PLAN_PROCEEDED,
    BACKED_OFF
}
```

- [ ] **Step 2: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/appblocker/domain/model/UnblockOutcome.kt
git commit -m "Add UnblockOutcome enum"
```

---

### Task 3: Refactor unblock-event types across domain + data + existing caller

This is an atomic refactor. The build will be temporarily broken between steps; it returns to green at Step 7. No tests yet — Task 4 covers the mapping test.

**Files:**
- Modify: `app/src/main/java/com/appblocker/domain/model/UnblockEvent.kt`
- Modify: `app/src/main/java/com/appblocker/domain/usecase/RecordUsageUseCase.kt`
- Modify: `app/src/main/java/com/appblocker/domain/repository/UsageRepository.kt`
- Modify: `app/src/main/java/com/appblocker/data/local/entity/UnblockEventEntity.kt`
- Modify: `app/src/main/java/com/appblocker/data/local/dao/UnblockEventDao.kt`
- Modify: `app/src/main/java/com/appblocker/data/repository/UsageRepositoryImpl.kt`
- Modify: `app/src/main/java/com/appblocker/presentation/BlockOverlayActivity.kt` (lines 66, 72 — temporary fix; full rewrite in Task 10)

- [ ] **Step 1: Update domain model `UnblockEvent`**

Replace the whole file with:

```kotlin
package com.appblocker.domain.model

data class UnblockEvent(
    val id: Long = 0,
    val appPackageName: String,
    val timestamp: Long,
    val outcome: UnblockOutcome
)
```

- [ ] **Step 2: Update `RecordUsageUseCase`**

Replace the file with:

```kotlin
package com.appblocker.domain.usecase

import com.appblocker.domain.model.UnblockEvent
import com.appblocker.domain.model.UnblockOutcome
import com.appblocker.domain.repository.UsageRepository

class RecordUsageUseCase(private val repository: UsageRepository) {
    suspend fun startSession(packageName: String): Long {
        return repository.startSession(packageName, System.currentTimeMillis())
    }

    suspend fun endSession(sessionId: Long) {
        repository.endSession(sessionId, System.currentTimeMillis())
    }

    suspend fun recordUnblock(packageName: String, outcome: UnblockOutcome) {
        val event = UnblockEvent(
            appPackageName = packageName,
            timestamp = System.currentTimeMillis(),
            outcome = outcome
        )
        repository.recordUnblockEvent(event)
    }
}
```

- [ ] **Step 3: Update `UsageRepository` interface — remove the unused `getProceededCountSince`**

Replace the file with:

```kotlin
package com.appblocker.domain.repository

import com.appblocker.domain.model.UnblockEvent
import com.appblocker.domain.model.UsageSession
import kotlinx.coroutines.flow.Flow

interface UsageRepository {
    suspend fun startSession(appPackageName: String, startTime: Long): Long
    suspend fun endSession(sessionId: Long, endTime: Long)
    fun getSessionsForApp(packageName: String, startEpochMs: Long, endEpochMs: Long): Flow<List<UsageSession>>
    suspend fun getTodayTotalDurationMs(packageName: String, dayStartEpochMs: Long): Long
    suspend fun getTodaySessionCount(packageName: String, dayStartEpochMs: Long): Int

    suspend fun recordUnblockEvent(event: UnblockEvent)
    fun getUnblockEventsForApp(packageName: String): Flow<List<UnblockEvent>>
    suspend fun getUnblockEventCountSince(packageName: String, sinceEpochMs: Long): Int
}
```

- [ ] **Step 4: Update `UnblockEventEntity`**

Replace the file with:

```kotlin
package com.appblocker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "unblock_events",
    foreignKeys = [
        ForeignKey(
            entity = BlockedAppEntity::class,
            parentColumns = ["package_name"],
            childColumns = ["app_package_name"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("app_package_name")]
)
data class UnblockEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "app_package_name")
    val appPackageName: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "outcome")
    val outcome: String
)
```

- [ ] **Step 5: Update `UnblockEventDao` — drop the proceeded-count query**

Replace the file with:

```kotlin
package com.appblocker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.appblocker.data.local.entity.UnblockEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UnblockEventDao {

    @Insert
    suspend fun insert(event: UnblockEventEntity)

    @Query(
        """SELECT * FROM unblock_events
        WHERE app_package_name = :packageName
        ORDER BY timestamp DESC"""
    )
    fun getEventsForApp(packageName: String): Flow<List<UnblockEventEntity>>

    @Query(
        """SELECT COUNT(*) FROM unblock_events
        WHERE app_package_name = :packageName
        AND timestamp >= :sinceEpochMs"""
    )
    suspend fun getEventCountSince(packageName: String, sinceEpochMs: Long): Int
}
```

- [ ] **Step 6: Update `UsageRepositoryImpl` — adapt mappers, remove deleted method**

Replace the file with:

```kotlin
package com.appblocker.data.repository

import com.appblocker.data.local.dao.UnblockEventDao
import com.appblocker.data.local.dao.UsageSessionDao
import com.appblocker.data.local.entity.UnblockEventEntity
import com.appblocker.data.local.entity.UsageSessionEntity
import com.appblocker.domain.model.UnblockEvent
import com.appblocker.domain.model.UnblockOutcome
import com.appblocker.domain.model.UsageSession
import com.appblocker.domain.repository.UsageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UsageRepositoryImpl(
    private val usageSessionDao: UsageSessionDao,
    private val unblockEventDao: UnblockEventDao
) : UsageRepository {

    override suspend fun startSession(appPackageName: String, startTime: Long): Long {
        return usageSessionDao.insert(
            UsageSessionEntity(
                appPackageName = appPackageName,
                startTime = startTime
            )
        )
    }

    override suspend fun endSession(sessionId: Long, endTime: Long) {
        usageSessionDao.updateEndTime(sessionId, endTime)
    }

    override fun getSessionsForApp(
        packageName: String,
        startEpochMs: Long,
        endEpochMs: Long
    ): Flow<List<UsageSession>> {
        return usageSessionDao.getSessionsForApp(packageName, startEpochMs, endEpochMs)
            .map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getTodayTotalDurationMs(packageName: String, dayStartEpochMs: Long): Long {
        return usageSessionDao.getTodayTotalDurationMs(packageName, dayStartEpochMs)
    }

    override suspend fun getTodaySessionCount(packageName: String, dayStartEpochMs: Long): Int {
        return usageSessionDao.getTodaySessionCount(packageName, dayStartEpochMs)
    }

    override suspend fun recordUnblockEvent(event: UnblockEvent) {
        unblockEventDao.insert(event.toEntity())
    }

    override fun getUnblockEventsForApp(packageName: String): Flow<List<UnblockEvent>> {
        return unblockEventDao.getEventsForApp(packageName)
            .map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getUnblockEventCountSince(packageName: String, sinceEpochMs: Long): Int {
        return unblockEventDao.getEventCountSince(packageName, sinceEpochMs)
    }

    private fun UsageSessionEntity.toDomain() = UsageSession(
        id = id,
        appPackageName = appPackageName,
        startTime = startTime,
        endTime = endTime,
        durationMs = durationMs
    )

    private fun UnblockEvent.toEntity() = UnblockEventEntity(
        id = id,
        appPackageName = appPackageName,
        timestamp = timestamp,
        outcome = outcome.name
    )

    private fun UnblockEventEntity.toDomain() = UnblockEvent(
        id = id,
        appPackageName = appPackageName,
        timestamp = timestamp,
        outcome = UnblockOutcome.valueOf(outcome)
    )
}
```

- [ ] **Step 7: Temporary fix to `BlockOverlayActivity` callsites** (file will be rewritten in Task 10)

In `BlockOverlayActivity.kt`, change the two `recordUnblock` calls (currently lines ~66 and ~72):

Replace:
```kotlin
                            recordUsage.recordUnblock(blockedPackage, userProceeded = false)
```
with:
```kotlin
                            recordUsage.recordUnblock(blockedPackage, UnblockOutcome.BACKED_OFF)
```

Replace:
```kotlin
                            recordUsage.recordUnblock(blockedPackage, userProceeded = true)
```
with:
```kotlin
                            recordUsage.recordUnblock(blockedPackage, UnblockOutcome.BROKE_PLAN_PROCEEDED)
```

Add the import at the top:
```kotlin
import com.appblocker.domain.model.UnblockOutcome
```

- [ ] **Step 8: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/appblocker/
git commit -m "Refactor UnblockEvent to use UnblockOutcome enum end-to-end"
```

---

### Task 4: Add UnblockOutcome mapping test

Verifies that `UnblockEvent ↔ UnblockEventEntity` round-trips correctly through the mappers in `UsageRepositoryImpl`. Since the mappers are private, we test indirectly through `recordUnblockEvent` + `getUnblockEventsForApp` using a fake DAO.

**Files:**
- Create: `app/src/test/java/com/appblocker/data/UnblockOutcomeMappingTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/appblocker/data/UnblockOutcomeMappingTest.kt`:

```kotlin
package com.appblocker.data

import com.appblocker.data.local.dao.UnblockEventDao
import com.appblocker.data.local.dao.UsageSessionDao
import com.appblocker.data.local.entity.UnblockEventEntity
import com.appblocker.data.repository.UsageRepositoryImpl
import com.appblocker.domain.model.UnblockEvent
import com.appblocker.domain.model.UnblockOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UnblockOutcomeMappingTest {

    @Test
    fun `each outcome round-trips through entity and back`() = runTest {
        UnblockOutcome.values().forEach { outcome ->
            val dao = InMemoryUnblockEventDao()
            val repo = UsageRepositoryImpl(NoopUsageSessionDao(), dao)

            repo.recordUnblockEvent(
                UnblockEvent(
                    appPackageName = "com.example.app",
                    timestamp = 1_700_000_000_000L,
                    outcome = outcome
                )
            )

            val stored = repo.getUnblockEventsForApp("com.example.app").first()
            assertEquals(1, stored.size)
            assertEquals(outcome, stored[0].outcome)
        }
    }

    @Test
    fun `entity stores outcome as enum name string`() = runTest {
        val dao = InMemoryUnblockEventDao()
        val repo = UsageRepositoryImpl(NoopUsageSessionDao(), dao)

        repo.recordUnblockEvent(
            UnblockEvent(
                appPackageName = "com.example.app",
                timestamp = 1L,
                outcome = UnblockOutcome.LEGITIMATE
            )
        )

        assertEquals("LEGITIMATE", dao.events.value.first().outcome)
    }
}

private class InMemoryUnblockEventDao : UnblockEventDao {
    val events = MutableStateFlow<List<UnblockEventEntity>>(emptyList())

    override suspend fun insert(event: UnblockEventEntity) {
        events.value = events.value + event.copy(id = events.value.size + 1L)
    }

    override fun getEventsForApp(packageName: String): Flow<List<UnblockEventEntity>> =
        MutableStateFlow(events.value.filter { it.appPackageName == packageName })

    override suspend fun getEventCountSince(packageName: String, sinceEpochMs: Long): Int =
        events.value.count { it.appPackageName == packageName && it.timestamp >= sinceEpochMs }
}

private class NoopUsageSessionDao : UsageSessionDao {
    override suspend fun insert(session: com.appblocker.data.local.entity.UsageSessionEntity): Long = 0
    override suspend fun updateEndTime(sessionId: Long, endTime: Long) = Unit
    override fun getSessionsForApp(packageName: String, startEpochMs: Long, endEpochMs: Long):
        Flow<List<com.appblocker.data.local.entity.UsageSessionEntity>> = MutableStateFlow(emptyList())
    override suspend fun getTodayTotalDurationMs(packageName: String, dayStartEpochMs: Long): Long = 0L
    override suspend fun getTotalDurationForRange(packageName: String, startEpochMs: Long, endEpochMs: Long): Long = 0L
    override suspend fun getTodaySessionCount(packageName: String, dayStartEpochMs: Long): Int = 0
}
```

> **Heads up:** The `NoopUsageSessionDao` overrides need to match the *exact* method signatures in `UsageSessionDao`. Before writing the file, open `app/src/main/java/com/appblocker/data/local/dao/UsageSessionDao.kt`, copy the abstract methods, and adapt the overrides to match. If signatures differ from what's shown above, adjust accordingly.

- [ ] **Step 2: Run the test, expect it to pass (compile may surface signature mismatches in `NoopUsageSessionDao`)**

Run: `./gradlew :app:testDebugUnitTest --tests "com.appblocker.data.UnblockOutcomeMappingTest"`
Expected: BUILD SUCCESSFUL, both tests pass.

If compile fails on `NoopUsageSessionDao`, fix the override signatures to match `UsageSessionDao` exactly, then re-run.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/appblocker/data/UnblockOutcomeMappingTest.kt
git commit -m "Test UnblockOutcome round-trips through entity mapping"
```

---

### Task 5: Add Room migration 1 → 2 and instrumented test

The schema change (replacing `user_proceeded INTEGER` with `outcome TEXT`) can't be done in-place in SQLite. We drop and recreate `unblock_events`. No production data exists yet (single-user, dev-only), so loss is acceptable. The migration must still be declared so Room doesn't refuse to open.

**Files:**
- Modify: `app/src/main/java/com/appblocker/data/local/AppDatabase.kt`
- Create: `app/src/androidTest/java/com/appblocker/data/local/AppDatabaseMigrationTest.kt`

- [ ] **Step 1: Update `AppDatabase` — bump version, add migration, export schema**

Replace the file with:

```kotlin
package com.appblocker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.appblocker.data.local.dao.BlockedAppDao
import com.appblocker.data.local.dao.MotivationalMessageDao
import com.appblocker.data.local.dao.ScheduleDao
import com.appblocker.data.local.dao.UnblockEventDao
import com.appblocker.data.local.dao.UsageSessionDao
import com.appblocker.data.local.entity.BlockedAppEntity
import com.appblocker.data.local.entity.MotivationalMessageEntity
import com.appblocker.data.local.entity.ScheduleDayEntity
import com.appblocker.data.local.entity.ScheduleEntity
import com.appblocker.data.local.entity.UnblockEventEntity
import com.appblocker.data.local.entity.UsageSessionEntity

@Database(
    entities = [
        BlockedAppEntity::class,
        ScheduleEntity::class,
        ScheduleDayEntity::class,
        UsageSessionEntity::class,
        UnblockEventEntity::class,
        MotivationalMessageEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun usageSessionDao(): UsageSessionDao
    abstract fun unblockEventDao(): UnblockEventDao
    abstract fun motivationalMessageDao(): MotivationalMessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS unblock_events")
                db.execSQL(
                    """
                    CREATE TABLE unblock_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        app_package_name TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        outcome TEXT NOT NULL,
                        FOREIGN KEY(app_package_name) REFERENCES blocked_apps(package_name) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX index_unblock_events_app_package_name ON unblock_events(app_package_name)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_blocker.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
```

- [ ] **Step 2: Configure schema export in build.gradle.kts**

Add to `defaultConfig { ... }` in `app/build.gradle.kts` (after the existing `versionName` line):

```kotlin
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
```

> Note: `ksp { }` here must go inside `defaultConfig { }`. If your KSP version requires a top-level `ksp {}` block, place it outside `android { }` instead: `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`. Both forms work; pick whichever your KSP plugin version accepts. Verify with `./gradlew assembleDebug` after.

- [ ] **Step 3: Verify build emits a schema file**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL, and `app/schemas/com.appblocker.data.local.AppDatabase/2.json` exists.

- [ ] **Step 4: Write the migration test**

Create `app/src/androidTest/java/com/appblocker/data/local/AppDatabaseMigrationTest.kt`:

```kotlin
package com.appblocker.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val testDbName = "migration-test.db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate1To2_dropsAndRecreatesUnblockEvents() {
        // Create v1 schema and seed an unblock_events row using the old column
        helper.createDatabase(testDbName, 1).use { db ->
            db.execSQL(
                "INSERT INTO blocked_apps (package_name, app_name, is_blocking_enabled, created_at) " +
                "VALUES ('com.example.app', 'Example', 1, 0)"
            )
            db.execSQL(
                "INSERT INTO unblock_events (app_package_name, timestamp, user_proceeded) " +
                "VALUES ('com.example.app', 100, 1)"
            )
        }

        // Run migration; helper validates resulting schema against the v2 export
        helper.runMigrationsAndValidate(
            testDbName,
            2,
            true,
            AppDatabase.MIGRATION_1_2
        ).use { db ->
            // Table was recreated, so it's empty
            db.query("SELECT COUNT(*) FROM unblock_events").use { cursor ->
                cursor.moveToFirst()
                assert(cursor.getInt(0) == 0) { "Expected unblock_events to be empty after migration" }
            }
            // And it accepts the new schema
            db.execSQL(
                "INSERT INTO unblock_events (app_package_name, timestamp, outcome) " +
                "VALUES ('com.example.app', 200, 'LEGITIMATE')"
            )
        }
    }
}
```

- [ ] **Step 5: Run the migration test**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.appblocker.data.local.AppDatabaseMigrationTest"`
Expected: BUILD SUCCESSFUL.

> Requires a connected device or running emulator. If no device is connected, document this manual step and proceed; the test can be run later.

- [ ] **Step 6: Commit**

```bash
git add app/build.gradle.kts \
        app/src/main/java/com/appblocker/data/local/AppDatabase.kt \
        app/src/androidTest/java/com/appblocker/data/local/AppDatabaseMigrationTest.kt \
        app/schemas
git commit -m "Add Room migration 1->2 for outcome column"
```

---

### Task 6: Add grace period to AppBlockerAccessibilityService

**Files:**
- Modify: `app/src/main/java/com/appblocker/service/AppBlockerAccessibilityService.kt`

- [ ] **Step 1: Add the grace-period machinery**

Replace the file with:

```kotlin
package com.appblocker.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.appblocker.data.local.AppDatabase
import com.appblocker.data.repository.BlockedAppRepositoryImpl
import com.appblocker.data.repository.UsageRepositoryImpl
import com.appblocker.domain.usecase.IsAppBlockedUseCase
import com.appblocker.domain.usecase.RecordUsageUseCase
import com.appblocker.presentation.BlockOverlayActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class AppBlockerAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var isAppBlocked: IsAppBlockedUseCase
    private lateinit var recordUsage: RecordUsageUseCase

    private var currentTrackedPackage: String? = null
    private var currentSessionId: Long? = null

    private val ignoredPackages = setOf(
        "com.appblocker",
        "com.android.systemui",
        "com.android.launcher",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher"
    )

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getInstance(this)
        val blockedAppRepo = BlockedAppRepositoryImpl(
            database.blockedAppDao(),
            database.scheduleDao()
        )
        val usageRepo = UsageRepositoryImpl(
            database.usageSessionDao(),
            database.unblockEventDao()
        )
        isAppBlocked = IsAppBlockedUseCase(blockedAppRepo, usageRepo)
        recordUsage = RecordUsageUseCase(usageRepo)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return

        if (packageName in ignoredPackages) return

        if (isInGrace(packageName)) return

        if (packageName != currentTrackedPackage) {
            endCurrentSession()
        }

        serviceScope.launch {
            val blocked = isAppBlocked(packageName)
            if (blocked) {
                if (currentTrackedPackage != packageName) {
                    currentTrackedPackage = packageName
                    currentSessionId = recordUsage.startSession(packageName)
                }
                launchBlockOverlay(packageName)
            }
        }
    }

    private fun launchBlockOverlay(packageName: String) {
        val appName = try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName
        }

        val intent = Intent(this, BlockOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(BlockOverlayActivity.EXTRA_PACKAGE_NAME, packageName)
            putExtra(BlockOverlayActivity.EXTRA_APP_NAME, appName)
        }
        startActivity(intent)
    }

    private fun isInGrace(packageName: String): Boolean {
        val expiry = graceUntilMs[packageName] ?: return false
        if (System.currentTimeMillis() < expiry) return true
        graceUntilMs.remove(packageName)
        return false
    }

    private fun endCurrentSession() {
        val sessionId = currentSessionId ?: return
        serviceScope.launch {
            recordUsage.endSession(sessionId)
        }
        currentTrackedPackage = null
        currentSessionId = null
    }

    override fun onInterrupt() {
        endCurrentSession()
    }

    override fun onDestroy() {
        endCurrentSession()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val GRACE_PERIOD_MS: Long = 5 * 60_000L

        private val graceUntilMs = ConcurrentHashMap<String, Long>()

        fun grantGrace(packageName: String) {
            graceUntilMs[packageName] = System.currentTimeMillis() + GRACE_PERIOD_MS
        }
    }
}
```

- [ ] **Step 2: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/appblocker/service/AppBlockerAccessibilityService.kt
git commit -m "Add 5-min grace period to AccessibilityService"
```

---

### Task 7: Create BlockOverlayUiState and BlockOverlayEvent

**Files:**
- Create: `app/src/main/java/com/appblocker/presentation/screen/BlockOverlayUiState.kt`

- [ ] **Step 1: Create the state + event types**

Create the file:

```kotlin
package com.appblocker.presentation.screen

sealed interface BlockOverlayUiState {
    val appName: String

    data class Confirmation(override val appName: String) : BlockOverlayUiState

    data class Motivational(
        override val appName: String,
        val message: String
    ) : BlockOverlayUiState
}

sealed interface BlockOverlayEvent {
    data object GrantGraceAndClose : BlockOverlayEvent
    data object Close : BlockOverlayEvent
    data object GoHome : BlockOverlayEvent
}
```

- [ ] **Step 2: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/appblocker/presentation/screen/BlockOverlayUiState.kt
git commit -m "Add BlockOverlayUiState and BlockOverlayEvent"
```

---

### Task 8: Create BlockOverlayViewModel with TDD tests

The VM is the heart of the state machine. We TDD it with fake repositories so no Android dependencies are pulled in.

**Files:**
- Create: `app/src/test/java/com/appblocker/presentation/fakes/FakeUsageRepository.kt`
- Create: `app/src/test/java/com/appblocker/presentation/fakes/FakeMotivationalMessageRepository.kt`
- Create: `app/src/test/java/com/appblocker/presentation/BlockOverlayViewModelTest.kt`
- Create: `app/src/main/java/com/appblocker/presentation/BlockOverlayViewModel.kt`

- [ ] **Step 1: Create the fake usage repository**

Create `app/src/test/java/com/appblocker/presentation/fakes/FakeUsageRepository.kt`:

```kotlin
package com.appblocker.presentation.fakes

import com.appblocker.domain.model.UnblockEvent
import com.appblocker.domain.model.UsageSession
import com.appblocker.domain.repository.UsageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeUsageRepository : UsageRepository {
    val recordedEvents = mutableListOf<UnblockEvent>()

    override suspend fun startSession(appPackageName: String, startTime: Long): Long = 0L
    override suspend fun endSession(sessionId: Long, endTime: Long) = Unit
    override fun getSessionsForApp(packageName: String, startEpochMs: Long, endEpochMs: Long):
        Flow<List<UsageSession>> = MutableStateFlow(emptyList())
    override suspend fun getTodayTotalDurationMs(packageName: String, dayStartEpochMs: Long): Long = 0L
    override suspend fun getTodaySessionCount(packageName: String, dayStartEpochMs: Long): Int = 0

    override suspend fun recordUnblockEvent(event: UnblockEvent) {
        recordedEvents.add(event)
    }

    override fun getUnblockEventsForApp(packageName: String): Flow<List<UnblockEvent>> =
        MutableStateFlow(emptyList())

    override suspend fun getUnblockEventCountSince(packageName: String, sinceEpochMs: Long): Int = 0
}
```

- [ ] **Step 2: Create the fake motivational repo**

Create `app/src/test/java/com/appblocker/presentation/fakes/FakeMotivationalMessageRepository.kt`:

```kotlin
package com.appblocker.presentation.fakes

import com.appblocker.domain.model.MotivationalMessage
import com.appblocker.domain.repository.MotivationalMessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeMotivationalMessageRepository(
    var nextMessage: MotivationalMessage? = null
) : MotivationalMessageRepository {

    override suspend fun add(message: MotivationalMessage) = Unit
    override suspend fun remove(message: MotivationalMessage) = Unit
    override suspend fun update(message: MotivationalMessage) = Unit
    override fun getMessagesForApp(packageName: String): Flow<List<MotivationalMessage>> =
        MutableStateFlow(emptyList())
    override fun getGeneralMessages(): Flow<List<MotivationalMessage>> =
        MutableStateFlow(emptyList())
    override suspend fun getRandomMessageForApp(packageName: String): MotivationalMessage? =
        nextMessage
}
```

- [ ] **Step 3: Write the VM tests (will fail to compile — VM doesn't exist yet)**

Create `app/src/test/java/com/appblocker/presentation/BlockOverlayViewModelTest.kt`:

```kotlin
package com.appblocker.presentation

import app.cash.turbine.test
import com.appblocker.domain.model.MotivationalMessage
import com.appblocker.domain.model.UnblockOutcome
import com.appblocker.domain.usecase.GetMotivationalMessageUseCase
import com.appblocker.domain.usecase.RecordUsageUseCase
import com.appblocker.presentation.fakes.FakeMotivationalMessageRepository
import com.appblocker.presentation.fakes.FakeUsageRepository
import com.appblocker.presentation.screen.BlockOverlayEvent
import com.appblocker.presentation.screen.BlockOverlayUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BlockOverlayViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val packageName = "com.example.app"
    private val appName = "Example"

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        message: MotivationalMessage? = null,
        usageRepo: FakeUsageRepository = FakeUsageRepository()
    ): Pair<BlockOverlayViewModel, FakeUsageRepository> {
        val motivRepo = FakeMotivationalMessageRepository(nextMessage = message)
        val vm = BlockOverlayViewModel(
            packageName = packageName,
            appName = appName,
            recordUsage = RecordUsageUseCase(usageRepo),
            getMotivationalMessage = GetMotivationalMessageUseCase(motivRepo)
        )
        return vm to usageRepo
    }

    @Test
    fun `initial state is Confirmation with the supplied app name`() = runTest(dispatcher) {
        val (vm, _) = viewModel()
        val state = vm.state.value
        assertEquals(BlockOverlayUiState.Confirmation(appName), state)
    }

    @Test
    fun `onBreakingPlan transitions to Motivational with the loaded message`() = runTest(dispatcher) {
        val (vm, _) = viewModel(
            message = MotivationalMessage(id = 1, appPackageName = packageName, message = "Keep going", createdAt = 0L)
        )
        vm.onBreakingPlan()
        advanceUntilIdle()
        assertEquals(
            BlockOverlayUiState.Motivational(appName, "Keep going"),
            vm.state.value
        )
    }

    @Test
    fun `onBreakingPlan with no message uses the fallback string`() = runTest(dispatcher) {
        val (vm, _) = viewModel(message = null)
        vm.onBreakingPlan()
        advanceUntilIdle()
        val state = vm.state.value as BlockOverlayUiState.Motivational
        assertEquals("You've got this! Stay focused.", state.message)
    }

    @Test
    fun `onLegitimate records LEGITIMATE and emits GrantGraceAndClose`() = runTest(dispatcher) {
        val (vm, usageRepo) = viewModel()
        vm.events.test {
            vm.onLegitimate()
            advanceUntilIdle()
            assertEquals(BlockOverlayEvent.GrantGraceAndClose, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, usageRepo.recordedEvents.size)
        assertEquals(UnblockOutcome.LEGITIMATE, usageRepo.recordedEvents[0].outcome)
        assertEquals(packageName, usageRepo.recordedEvents[0].appPackageName)
    }

    @Test
    fun `onGoBack from Motivational records BACKED_OFF and emits GoHome`() = runTest(dispatcher) {
        val (vm, usageRepo) = viewModel()
        vm.onBreakingPlan()
        advanceUntilIdle()
        vm.events.test {
            vm.onGoBack()
            advanceUntilIdle()
            assertEquals(BlockOverlayEvent.GoHome, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, usageRepo.recordedEvents.size)
        assertEquals(UnblockOutcome.BACKED_OFF, usageRepo.recordedEvents[0].outcome)
    }

    @Test
    fun `onProceed from Motivational records BROKE_PLAN_PROCEEDED and emits GrantGraceAndClose`() = runTest(dispatcher) {
        val (vm, usageRepo) = viewModel()
        vm.onBreakingPlan()
        advanceUntilIdle()
        vm.events.test {
            vm.onProceed()
            advanceUntilIdle()
            assertEquals(BlockOverlayEvent.GrantGraceAndClose, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, usageRepo.recordedEvents.size)
        assertEquals(UnblockOutcome.BROKE_PLAN_PROCEEDED, usageRepo.recordedEvents[0].outcome)
    }

    @Test
    fun `onBackPressedFromMotivational returns state to Confirmation`() = runTest(dispatcher) {
        val (vm, usageRepo) = viewModel()
        vm.onBreakingPlan()
        advanceUntilIdle()
        assertTrue(vm.state.value is BlockOverlayUiState.Motivational)

        vm.onBackPressedFromMotivational()

        assertEquals(BlockOverlayUiState.Confirmation(appName), vm.state.value)
        assertTrue("No event should be recorded on back-to-confirmation", usageRepo.recordedEvents.isEmpty())
    }
}
```

- [ ] **Step 4: Run the test — expect compile failure (VM doesn't exist)**

Run: `./gradlew :app:testDebugUnitTest --tests "com.appblocker.presentation.BlockOverlayViewModelTest"`
Expected: compile error referencing `BlockOverlayViewModel`.

- [ ] **Step 5: Implement `BlockOverlayViewModel`**

Create `app/src/main/java/com/appblocker/presentation/BlockOverlayViewModel.kt`:

```kotlin
package com.appblocker.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.appblocker.domain.model.UnblockOutcome
import com.appblocker.domain.usecase.GetMotivationalMessageUseCase
import com.appblocker.domain.usecase.RecordUsageUseCase
import com.appblocker.presentation.screen.BlockOverlayEvent
import com.appblocker.presentation.screen.BlockOverlayUiState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class BlockOverlayViewModel(
    private val packageName: String,
    private val appName: String,
    private val recordUsage: RecordUsageUseCase,
    private val getMotivationalMessage: GetMotivationalMessageUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<BlockOverlayUiState>(BlockOverlayUiState.Confirmation(appName))
    val state: StateFlow<BlockOverlayUiState> = _state.asStateFlow()

    private val _events = Channel<BlockOverlayEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onBreakingPlan() {
        viewModelScope.launch {
            val message = getMotivationalMessage(packageName)?.message
                ?: FALLBACK_MESSAGE
            _state.value = BlockOverlayUiState.Motivational(appName, message)
        }
    }

    fun onLegitimate() {
        viewModelScope.launch {
            recordUsage.recordUnblock(packageName, UnblockOutcome.LEGITIMATE)
            _events.send(BlockOverlayEvent.GrantGraceAndClose)
        }
    }

    fun onGoBack() {
        viewModelScope.launch {
            recordUsage.recordUnblock(packageName, UnblockOutcome.BACKED_OFF)
            _events.send(BlockOverlayEvent.GoHome)
        }
    }

    fun onProceed() {
        viewModelScope.launch {
            recordUsage.recordUnblock(packageName, UnblockOutcome.BROKE_PLAN_PROCEEDED)
            _events.send(BlockOverlayEvent.GrantGraceAndClose)
        }
    }

    fun onBackPressedFromMotivational() {
        _state.value = BlockOverlayUiState.Confirmation(appName)
    }

    companion object {
        const val FALLBACK_MESSAGE = "You've got this! Stay focused."
    }

    class Factory(
        private val packageName: String,
        private val appName: String,
        private val recordUsage: RecordUsageUseCase,
        private val getMotivationalMessage: GetMotivationalMessageUseCase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BlockOverlayViewModel(packageName, appName, recordUsage, getMotivationalMessage) as T
        }
    }
}
```

- [ ] **Step 6: Run the tests — expect all pass**

Run: `./gradlew :app:testDebugUnitTest --tests "com.appblocker.presentation.BlockOverlayViewModelTest"`
Expected: BUILD SUCCESSFUL, 7 tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/appblocker/presentation/BlockOverlayViewModel.kt \
        app/src/test/java/com/appblocker/presentation/
git commit -m "Add BlockOverlayViewModel with state machine + tests"
```

---

### Task 9: Rewrite BlockOverlayScreen for state-driven rendering

**Files:**
- Rewrite: `app/src/main/java/com/appblocker/presentation/screen/BlockOverlayScreen.kt`

- [ ] **Step 1: Replace the file**

```kotlin
package com.appblocker.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.appblocker.presentation.theme.AppBlockerTheme

@Composable
fun BlockOverlayScreen(
    state: BlockOverlayUiState,
    onLegitimate: () -> Unit,
    onBreakingPlan: () -> Unit,
    onGoBack: () -> Unit,
    onProceed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (state) {
                is BlockOverlayUiState.Confirmation -> ConfirmationContent(
                    appName = state.appName,
                    onLegitimate = onLegitimate,
                    onBreakingPlan = onBreakingPlan
                )
                is BlockOverlayUiState.Motivational -> MotivationalContent(
                    appName = state.appName,
                    message = state.message,
                    onGoBack = onGoBack,
                    onProceed = onProceed
                )
            }
        }
    }
}

@Composable
private fun ConfirmationContent(
    appName: String,
    onLegitimate: () -> Unit,
    onBreakingPlan: () -> Unit
) {
    Text(
        text = "Are you disabling this before your planned time?",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = appName,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(48.dp))

    Button(onClick = onBreakingPlan) {
        Text(text = "Yes")
    }

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedButton(onClick = onLegitimate) {
        Text(text = "No")
    }
}

@Composable
private fun MotivationalContent(
    appName: String,
    message: String,
    onGoBack: () -> Unit,
    onProceed: () -> Unit
) {
    Text(
        text = "This app is blocked",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = appName,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(32.dp))

    Text(
        text = message,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(48.dp))

    Button(onClick = onGoBack) {
        Text(text = "Go Back")
    }

    Spacer(modifier = Modifier.height(16.dp))

    TextButton(onClick = onProceed) {
        Text(
            text = "Proceed Anyway",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ConfirmationPreview() {
    AppBlockerTheme {
        BlockOverlayScreen(
            state = BlockOverlayUiState.Confirmation(appName = "Instagram"),
            onLegitimate = {},
            onBreakingPlan = {},
            onGoBack = {},
            onProceed = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MotivationalPreview() {
    AppBlockerTheme {
        BlockOverlayScreen(
            state = BlockOverlayUiState.Motivational(
                appName = "Instagram",
                message = "Stay focused! You have better things to do."
            ),
            onLegitimate = {},
            onBreakingPlan = {},
            onGoBack = {},
            onProceed = {},
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MotivationalDarkPreview() {
    AppBlockerTheme {
        BlockOverlayScreen(
            state = BlockOverlayUiState.Motivational(
                appName = "Instagram",
                message = "Stay focused! You have better things to do."
            ),
            onLegitimate = {},
            onBreakingPlan = {},
            onGoBack = {},
            onProceed = {},
        )
    }
}
```

- [ ] **Step 2: Verify build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. (Activity will still compile because the function signature is now different — but it currently compiles using `setContent { BlockOverlayScreen(appName, motivationalMessage, onGoBack, onProceedAnyway) }`, which won't match the new signature. So **the build WILL fail at this step until Task 10 lands.** Proceed anyway.)

> If the build fails specifically inside `BlockOverlayActivity.kt`, that is expected. Don't fix it here; that file gets rewritten in Task 10.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/appblocker/presentation/screen/BlockOverlayScreen.kt
git commit -m "Rewrite BlockOverlayScreen to render BlockOverlayUiState"
```

---

### Task 10: Rewrite BlockOverlayActivity to use the VM

**Files:**
- Rewrite: `app/src/main/java/com/appblocker/presentation/BlockOverlayActivity.kt`

- [ ] **Step 1: Replace the file**

```kotlin
package com.appblocker.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.Lifecycle
import com.appblocker.data.local.AppDatabase
import com.appblocker.data.repository.MotivationalMessageRepositoryImpl
import com.appblocker.data.repository.UsageRepositoryImpl
import com.appblocker.domain.usecase.GetMotivationalMessageUseCase
import com.appblocker.domain.usecase.RecordUsageUseCase
import com.appblocker.presentation.screen.BlockOverlayEvent
import com.appblocker.presentation.screen.BlockOverlayScreen
import com.appblocker.presentation.screen.BlockOverlayUiState
import com.appblocker.presentation.theme.AppBlockerTheme
import com.appblocker.service.AppBlockerAccessibilityService
import kotlinx.coroutines.flow.collectLatest

class BlockOverlayActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_NAME = "extra_app_name"
    }

    private lateinit var blockedPackage: String

    private val viewModel: BlockOverlayViewModel by viewModels {
        val database = AppDatabase.getInstance(this)
        val recordUsage = RecordUsageUseCase(
            UsageRepositoryImpl(database.usageSessionDao(), database.unblockEventDao())
        )
        val getMotivationalMessage = GetMotivationalMessageUseCase(
            MotivationalMessageRepositoryImpl(database.motivationalMessageDao())
        )
        BlockOverlayViewModel.Factory(
            packageName = blockedPackage,
            appName = intent.getStringExtra(EXTRA_APP_NAME) ?: blockedPackage,
            recordUsage = recordUsage,
            getMotivationalMessage = getMotivationalMessage
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        blockedPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: run {
            finish()
            return
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (viewModel.state.value) {
                    is BlockOverlayUiState.Confirmation -> goHome()
                    is BlockOverlayUiState.Motivational -> viewModel.onBackPressedFromMotivational()
                }
            }
        })

        setContent {
            AppBlockerTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()

                LaunchedEffect(Unit) {
                    viewModel.events
                        .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                        .collectLatest { event ->
                            when (event) {
                                BlockOverlayEvent.GrantGraceAndClose -> {
                                    AppBlockerAccessibilityService.grantGrace(blockedPackage)
                                    finish()
                                }
                                BlockOverlayEvent.Close -> finish()
                                BlockOverlayEvent.GoHome -> goHome()
                            }
                        }
                }

                BlockOverlayScreen(
                    state = state,
                    onLegitimate = viewModel::onLegitimate,
                    onBreakingPlan = viewModel::onBreakingPlan,
                    onGoBack = viewModel::onGoBack,
                    onProceed = viewModel::onProceed,
                )
            }
        }
    }

    private fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }
}
```

- [ ] **Step 2: Verify build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run all unit tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/appblocker/presentation/BlockOverlayActivity.kt
git commit -m "Wire BlockOverlayActivity to ViewModel + events"
```

---

### Task 11: Manual smoke test

This task isn't automated. Run through it on a device or emulator before opening a PR.

- [ ] **Step 1: Install the debug build on a device with the AccessibilityService enabled**

Run: `./gradlew :app:installDebug`

In device settings, ensure `AppBlockerAccessibilityService` is enabled and at least one app is blocked with a currently-active schedule.

- [ ] **Step 2: Walk through the manual checklist**

For each, verify expected behavior:

- [ ] Open the blocked app → Confirmation screen appears with the prompt and app name
- [ ] Tap NO → returned to the blocked app; wait 6 minutes; reopen → Confirmation appears again
- [ ] Tap NO → returned; reopen within 5 min → no overlay (grace works)
- [ ] Reopen later, tap YES → Motivational screen renders with "You've got this! Stay focused."
- [ ] On Motivational, tap Go Back → home screen; reopen blocked app → Confirmation re-appears (no grace)
- [ ] On Motivational, tap Proceed Anyway → returned to blocked app; reopen within 5 min → no overlay
- [ ] On Confirmation, press system back → home (verify no `unblock_events` row was inserted using `adb shell run-as com.appblocker sqlite3 databases/app_blocker.db 'SELECT * FROM unblock_events ORDER BY id DESC LIMIT 1;'`)
- [ ] On Motivational, press system back → returns to Confirmation (no new event row)
- [ ] On either screen, rotate the device → state preserved (Confirmation stays Confirmation; Motivational stays Motivational with the same message)

- [ ] **Step 3: If everything passed, no commit needed. If something broke, return to the failing task.**

---

## Spec coverage check

| Spec requirement | Implemented in |
|---|---|
| Confirmation prompt with YES / NO | Task 7 (state), Task 9 (UI), Task 8 (VM logic) |
| YES → motivational message | Task 8 (`onBreakingPlan`), Task 9 (`MotivationalContent`) |
| NO → frictionless access (LEGITIMATE) | Task 8 (`onLegitimate`), Task 10 (event handler) |
| Motivational → Go Back records BACKED_OFF, no grace | Task 8 (`onGoBack`), Task 10 |
| Motivational → Proceed records BROKE_PLAN_PROCEEDED, grants grace | Task 8 (`onProceed`), Task 10 |
| 5-min in-memory grace period | Task 6 |
| Static companion grantGrace method | Task 6 |
| UnblockOutcome enum on entity (TEXT column) | Tasks 2, 3 |
| Room migration 1 → 2 | Task 5 |
| Fallback motivational string | Task 8 (`FALLBACK_MESSAGE`) |
| Back from Confirmation → home (untracked) | Task 10 (`OnBackPressedCallback`) |
| Back from Motivational → Confirmation (untracked) | Task 8 (`onBackPressedFromMotivational`), Task 10 |
| Activity recreated → state preserved | Task 10 (`viewModels()` survives) |
| Existing `getProceededCountSince` removed (YAGNI, semantics broken by new enum) | Task 3 |
| VM unit tests | Task 8 |
| Mapping unit test | Task 4 |
| Room migration instrumented test | Task 5 |
| Manual smoke checklist | Task 11 |

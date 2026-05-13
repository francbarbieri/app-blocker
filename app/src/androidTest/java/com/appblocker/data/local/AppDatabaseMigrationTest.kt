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

package com.wein.fasttrack.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Expense::class,
        TagEntity::class,
        RecurringExpenseEntity::class,
        SplitBillSessionEntity::class,
        SplitBillParticipantEntity::class,
        SplitBillItemEntity::class,
        SplitBillSettledEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun tagDao(): TagDao
    abstract fun recurringExpenseDao(): RecurringExpenseDao
    abstract fun splitBillDao(): SplitBillDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create the tags table
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `isPreset` INTEGER NOT NULL)"
                )
                // Add unique index on name
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)"
                )
                // Insert preset tags
                db.execSQL("INSERT OR IGNORE INTO `tags` (`name`, `isPreset`) VALUES ('Umum', 1), ('Makan', 1), ('Transport', 1), ('Belanja', 1), ('Tagihan', 1), ('Jajan', 1)")

                // Normalize legacy tags
                db.execSQL("UPDATE `expenses` SET `tag` = 'Umum' WHERE `tag` = 'General' OR `tag` IS NULL OR `tag` = ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `expenses` ADD COLUMN `splitBillSessionId` INTEGER DEFAULT NULL")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recurring_expenses` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`amount` INTEGER NOT NULL, " +
                        "`tag` TEXT NOT NULL, " +
                        "`note` TEXT, " +
                        "`dayOfMonth` INTEGER NOT NULL, " +
                        "`isEnabled` INTEGER NOT NULL, " +
                        "`lastAppliedYearMonth` TEXT, " +
                        "`createdAt` INTEGER NOT NULL)"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `split_bill_sessions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "`syncToExpenses` INTEGER NOT NULL, " +
                        "`expenseTag` TEXT NOT NULL, " +
                        "`syncedExpenseId` INTEGER)"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `split_bill_participants` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sessionId` INTEGER NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`isSelf` INTEGER NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`sessionId`) REFERENCES `split_bill_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_split_bill_participants_sessionId` ON `split_bill_participants` (`sessionId`)"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `split_bill_items` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sessionId` INTEGER NOT NULL, " +
                        "`description` TEXT NOT NULL, " +
                        "`amount` INTEGER NOT NULL, " +
                        "`subtotal` INTEGER NOT NULL, " +
                        "`taxPercent` REAL NOT NULL, " +
                        "`discount` INTEGER NOT NULL, " +
                        "`payerParticipantId` INTEGER NOT NULL, " +
                        "`splitIdsSerialized` TEXT NOT NULL, " +
                        "`weightsJson` TEXT NOT NULL, " +
                        "FOREIGN KEY(`sessionId`) REFERENCES `split_bill_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_split_bill_items_sessionId` ON `split_bill_items` (`sessionId`)"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `split_bill_settled` (" +
                        "`sessionId` INTEGER NOT NULL, " +
                        "`settlementKey` TEXT NOT NULL, " +
                        "PRIMARY KEY(`sessionId`, `settlementKey`), " +
                        "FOREIGN KEY(`sessionId`) REFERENCES `split_bill_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_split_bill_settled_sessionId` ON `split_bill_settled` (`sessionId`)"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fast_track_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
